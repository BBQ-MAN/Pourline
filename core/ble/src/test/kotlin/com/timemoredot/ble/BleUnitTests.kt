package com.timemoredot.ble

import android.Manifest
import com.timemoredot.protocol.ScaleCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlePermissionsTest {
    @Test fun requiredBySdk() {
        val scanConnect = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        assertArrayEquals(scanConnect, BlePermissions.required(31))
        assertArrayEquals(scanConnect, BlePermissions.required(37))
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), BlePermissions.required(30))
    }

    @Test fun locationService() {
        assertTrue(BlePermissions.USE_NEVER_FOR_LOCATION)
        assertTrue(BlePermissions.needsLocationService(30))
        assertFalse(BlePermissions.needsLocationService(31))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FrameLogFormatTest {
    @Test fun formats() {
        val rx = BleVectors.hex("rx-tare-writeack-003")
        assertEquals("RX FFF1 ${rx.uppercase()}", FrameLogFormat.rx(rx.hexToByteArray()))
        assertEquals("TX FFF2 ${FrameCodec_hex(ScaleCommand.Tare)}", FrameLogFormat.tx(FrameCodec_bytes(ScaleCommand.Tare)))
        assertEquals("EV ADV $ADDR_A name=null mfr=null uuids=", FrameLogFormat.adv(ADDR_A, null, null, emptyList()))
    }

    @Test fun clientLogsRxTx() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        t.notify(BleVectors.hex("rx-tare-writeack-003"))
        runCurrent()
        r.await()
        assertTrue(h.logs.contains("TX FFF2 ${FrameCodec_hex(ScaleCommand.Tare)}"))
        assertTrue(h.logs.contains("RX FFF1 ${BleVectors.hex("rx-tare-writeack-003").uppercase()}"))
    }

    @Test fun noLogCallsWhenDisabled() = runTest {
        val h = Harness(this, debugFrameLog = false)
        h.client.startScan()
        runCurrent()
        h.scanner.hit(ADDR_A, null)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        t.notify(BleVectors.hex("rx-tare-writeack-003"))
        runCurrent()
        r.await()
        t.drop(8)
        runCurrent()
        assertTrue(h.logs.isEmpty())
    }
}

/** 작업 큐 한 칸: 종류가 맞는 콜백만 완료, 늦은 콜백 무시, 닫으면 대기·뒤 작업 실패. */
@OptIn(ExperimentalCoroutinesApi::class)
class GattOpSlotTest {
    @Test fun completesOnlyMatchingKind() = runTest {
        val slot = GattOpSlot()
        val r = async { slot.run(GattOpKind.CharacteristicWrite) { true } }
        runCurrent()
        assertFalse(slot.complete(GattOpKind.DescriptorWrite, true))
        runCurrent()
        assertFalse(r.isCompleted)
        assertTrue(slot.complete(GattOpKind.CharacteristicWrite, true))
        assertEquals(true, r.await())
    }

    @Test fun lateCallbackAfterCancelCompletesNothing() = runTest {
        val slot = GattOpSlot()
        val first = async { slot.run(GattOpKind.CharacteristicWrite) { true } }
        runCurrent()
        first.cancel()
        runCurrent()
        assertFalse(slot.complete(GattOpKind.CharacteristicWrite, true))
        val read = async { slot.run(GattOpKind.CharacteristicRead) { true } }
        runCurrent()
        assertFalse(slot.complete(GattOpKind.CharacteristicWrite, true))   // 앞 작업의 늦은 콜백이 뒤 작업을 완료하지 않는다
        assertFalse(read.isCompleted)
        assertTrue(slot.complete(GattOpKind.CharacteristicRead, byteArrayOf(1)))
        assertArrayEquals(byteArrayOf(1), read.await() as ByteArray)
    }

    @Test fun closeFailsPendingAndLater() = runTest {
        val slot = GattOpSlot()
        val r = async { slot.run(GattOpKind.DiscoverServices) { true } }
        runCurrent()
        slot.close()
        assertNull(r.await())
        var started = false
        assertNull(slot.run(GattOpKind.Mtu) { started = true; true })
        assertFalse(started)
    }

    @Test fun startFailureFailsImmediately() = runTest {
        val slot = GattOpSlot()
        assertNull(slot.run(GattOpKind.CharacteristicWrite) { false })
        assertFalse(slot.complete(GattOpKind.CharacteristicWrite, true))
    }
}
