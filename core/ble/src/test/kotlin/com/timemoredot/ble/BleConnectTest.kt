package com.timemoredot.ble

import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.EXPECTED_MODEL
import com.timemoredot.protocol.EncodeResult
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.FrameCodec
import com.timemoredot.protocol.SCALE_SERVICE_UUID
import com.timemoredot.protocol.ScaleCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 6절 연결 순서(GATT-1), 실패 사유, GATT 작업 큐, close 멱등, SecurityException. */
@OptIn(ExperimentalCoroutinesApi::class)
class BleConnectTest {
    private val GATT_FAILURE = 257   // BluetoothGatt.GATT_FAILURE
    private fun hex(c: ScaleCommand) = (FrameCodec.encode(c) as EncodeResult.Ok).bytes.toHexString(HexFormat.UpperCase)

    @Test fun connectionOrderAndTwoConnectedStates() = runTest {
        val h = Harness(this)
        h.record()
        val t = h.connectA()
        assertEquals(
            listOf(
                "discoverServices",
                "requestMtu(247)",
                "enableNotifications(fff0,fff1)",
                "write(fff0,fff2,${hex(ScaleCommand.ReadUnit)})",
                "write(fff0,fff2,${hex(ScaleCommand.ReadBattery)})",
                "write(fff0,fff2,${hex(ScaleCommand.ReadTimer)})",
                "read(180a,2a24)",
                "read(180a,2a26)",
            ),
            t.calls,
        )
        val connected = h.states.filterIsInstance<ConnectionState.Connected>()
        assertEquals(2, connected.size)
        val first = connected[0].device
        assertFalse(first.disReadDone)
        assertEquals(247, first.mtu)
        assertEquals(first.copy(modelNumber = EXPECTED_MODEL, firmwareRevision = "1.0.4", disReadDone = true), connected[1].device)
        assertFalse(connected[1].device.modelMismatch)
        assertTrue(h.states.first { it !is ConnectionState.Idle } is ConnectionState.Connecting)
    }

    @Test fun modelMismatchIsWarningOnly() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.reads[GattSpec.MODEL_NUMBER] = "TES018".encodeToByteArray() }
        h.connectA()
        val d = (h.state as ConnectionState.Connected).device
        assertEquals("TES018", d.modelNumber)
        assertTrue(d.modelMismatch)
    }

    @Test fun disReadFailureKeepsConnection() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.reads[GattSpec.MODEL_NUMBER] = null; it.reads[GattSpec.FIRMWARE_REVISION] = null }
        h.connectA()
        val d = (h.state as ConnectionState.Connected).device
        assertTrue(d.disReadDone)
        assertNull(d.modelNumber)
        assertNull(d.firmwareRevision)
        assertFalse(d.modelMismatch)
    }

    @Test fun mtuFailureUsesDefault23() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.mtuResult = null }
        h.connectA()
        assertEquals(23, (h.state as ConnectionState.Connected).device.mtu)
    }

    @Test fun g7ServiceIsIgnored() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.services[G7_SERVICE] = mutableSetOf(G7_SERVICE) }
        val t = h.connectA()
        assertEquals(1, t.calls.count { it.startsWith("enableNotifications") })
        assertTrue(t.calls.none { it.contains(G7_SERVICE.short()) })
    }

    @Test fun missingCharacteristicIsServiceNotFound() = runTest {
        for (missing in listOf(GattSpec.UPLINK, GattSpec.DOWNLINK)) {
            val h = Harness(this)
            h.onCreate = { it.connected(); it.services.getValue(SCALE_SERVICE_UUID).remove(missing) }
            h.client.connect(ADDR_A)
            runCurrent()
            assertEquals(ConnectionState.Failed(FailureReason.ServiceNotFound), h.state)
            assertEquals(1, h.last.closeCalls)
        }
    }

    @Test fun connectTimeoutAfter10s() = runTest {
        val h = Harness(this)
        h.onCreate = {}   // 연결 이벤트가 오지 않는다
        h.client.connect(ADDR_A)
        h.advance(9_999)
        assertTrue(h.state is ConnectionState.Connecting)
        h.advance(1)
        assertEquals(ConnectionState.Failed(FailureReason.ConnectTimeout), h.state)
        assertEquals(1, h.last.releases)
    }

    @Test fun earlyDisconnectBeforeCollectionIsHandled() = runTest {
        val h = Harness(this)
        h.onCreate = { it.drop(133) }   // 수집 시작 전에 쌓인 이벤트
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.Failed(FailureReason.GattError(133)), h.state)
        assertEquals(1, h.last.releases)
    }

    @Test fun unitReadTimeoutMarksUnitReadFailedAndStaysConnected() = runTest {
        val h = Harness(this)
        h.onCreate = { t ->
            t.connected()
            t.responder = { if (FakeGattTransport.keyOf(it) == FakeGattTransport.keyOf(FrameCodec.bytes(ScaleCommand.ReadUnit))) emptyList() else FakeGattTransport.defaultResponse(it) }
        }
        h.client.connect(ADDR_A)
        runCurrent()
        h.advance(999)
        assertFalse(h.client.status.value.unitReadFailed)
        h.advance(1)
        assertTrue(h.client.status.value.unitReadFailed)
        assertTrue(h.state is ConnectionState.Connected)
    }

    // ---- GATT 작업 큐 ----

    @Test fun writeCallbackTimeoutClosesAndReconnects() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.held += GattOpKind.CharacteristicWrite }
        h.client.connect(ADDR_A)
        runCurrent()
        val t = h.last
        assertTrue(h.state is ConnectionState.Connected)
        assertEquals(1, t.writes.size)   // ReadUnit 쓰기 콜백 대기
        h.onCreate = {}
        h.advance(4_999)
        assertTrue(h.state is ConnectionState.Connected)
        h.advance(1)
        assertEquals(1, t.releases)
        assertEquals(ConnectionState.Reconnecting(ADDR_A, 1, (5_000 + 1_000) * MS), h.state)
        assertEquals(1, t.writes.size)   // 다음 작업(ReadBattery)을 보내지 않았다
        assertFalse(t.callback(GattOpKind.CharacteristicWrite, true))   // 늦은 콜백은 아무것도 완료하지 않는다
        assertEquals(1, t.writes.size)
    }

    @Test fun callbackOfOtherKindIsDropped() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.held += GattOpKind.CharacteristicWrite }
        h.client.connect(ADDR_A)
        runCurrent()
        val t = h.last
        assertFalse(t.callback(GattOpKind.DescriptorWrite, true))
        runCurrent()
        assertEquals(1, t.writes.size)
        assertTrue(t.callback(GattOpKind.CharacteristicWrite, true))
        runCurrent()
        assertEquals(2, t.writes.size)   // ReadUnit 완료 뒤 ReadBattery
        assertTrue(h.state is ConnectionState.Connected)
    }

    @Test fun disconnectWhileOperationPendingFailsIt() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        t.held += GattOpKind.CharacteristicWrite
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        assertFalse(r.isCompleted)
        t.drop(8)
        runCurrent()
        assertEquals(CommandResult.Failed(CommandFailure.Disconnected), r.await())
    }

    @Test fun closeReleasesOnceWhenTimeoutDisconnectAndUserOverlap() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        t.held += GattOpKind.CharacteristicWrite
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        h.advance(5_000)        // 작업 시간 초과 → close
        t.drop(133)              // 끊김 이벤트
        h.client.disconnect()    // 사용자 끊기
        runCurrent()
        assertEquals(1, t.releases)
        assertTrue(r.await() is CommandResult.Failed)
        assertEquals(ConnectionState.Idle, h.state)
    }

    // ---- SecurityException → PermissionMissing ----

    @Test fun securityExceptionFromConnectGatt() = runTest {
        val h = Harness(this)
        h.throwOnConnect = true
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.PermissionMissing, h.state)
    }

    @Test fun securityExceptionFromGattOperation() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.securityOn = "discoverServices" }
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.PermissionMissing, h.state)
        assertEquals(1, h.last.releases)
    }

    // ---- 그 밖의 RuntimeException → 크래시 없이 Failed(GattError) ----

    @Test fun runtimeExceptionFromConnectGatt() = runTest {
        val h = Harness(this)
        h.connectError = IllegalArgumentException("bad address")
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.Failed(FailureReason.GattError(GATT_FAILURE)), h.state)
    }

    @Test fun runtimeExceptionFromGattOperation() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.errorOn = "enableNotifications" to RuntimeException("binder died") }
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.Failed(FailureReason.GattError(GATT_FAILURE)), h.state)
        assertEquals(1, h.last.releases)
    }

    @Test fun securityExceptionFromScanner() = runTest {
        val h = Harness(this)
        h.scanner.throwSecurity = true
        h.client.startScan()
        runCurrent()
        assertEquals(ConnectionState.PermissionMissing, h.state)
    }

    @Test fun missingPermissionBlocksConnect() = runTest {
        val h = Harness(this)
        h.env.permissions = false
        h.client.connect(ADDR_A)
        runCurrent()
        assertEquals(ConnectionState.PermissionMissing, h.state)
        assertTrue(h.transports.isEmpty())
    }
}

internal fun FrameCodec.bytes(c: ScaleCommand): ByteArray = (encode(c) as EncodeResult.Ok).bytes
