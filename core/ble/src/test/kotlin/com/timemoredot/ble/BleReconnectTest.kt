package com.timemoredot.ble

import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.ScaleCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 6절 재연결·끊기·블루투스 상태. */
@OptIn(ExperimentalCoroutinesApi::class)
class BleReconnectTest {

    @Test fun lossThenReconnectingStaysDuringAttemptThenAttempt2() = runTest {
        val h = Harness(this)
        h.record()
        val t = h.connectA()
        h.advance(100)
        h.onCreate = {}   // 다음 시도는 연결되지 않는다
        t.drop(133)
        runCurrent()
        assertEquals(1, t.releases)
        val r1 = ConnectionState.Reconnecting(ADDR_A, 1, 1_100 * MS)
        assertEquals(r1, h.state)
        h.advance(999)
        assertEquals(1, h.transports.size)
        h.advance(1)
        assertEquals(2, h.transports.size)
        assertEquals(ADDR_A, h.last.address)
        assertEquals(r1, h.state)   // 시도 중에도 같은 Reconnecting
        assertTrue(h.states.takeLastWhile { it != r1 }.isEmpty())
        h.advance(10_000)   // 시도별 10 s 제한
        assertEquals(1, h.transports[1].releases)
        assertEquals(ConnectionState.Reconnecting(ADDR_A, 2, (11_100 + 2_000) * MS), h.state)
        assertTrue(h.states.none { it is ConnectionState.Connecting && h.states.indexOf(it) > h.states.indexOf(r1) })
    }

    @Test fun backoffSequenceThenExhausted() = runTest {
        val h = Harness(this)
        h.record()
        val t = h.connectA()
        h.onCreate = { it.drop(133) }   // 모든 재시도 즉시 실패
        t.drop(133)
        runCurrent()
        h.advance(200_000)
        val retries = h.states.filterIsInstance<ConnectionState.Reconnecting>()
        assertEquals((1..10).toList(), retries.map { it.attempt })
        val gaps = listOf(0L) + retries.map { it.nextRetryAtNanos / MS }
        assertEquals(listOf(1, 2, 4, 8, 16, 30, 30, 30, 30, 30).map { it * 1_000L }, gaps.zipWithNext { a, b -> b - a })
        assertEquals(ConnectionState.Failed(FailureReason.ReconnectExhausted), h.state)
        assertEquals(11, h.transports.size)
        assertTrue(h.transports.all { it.releases == 1 })
    }

    @Test fun connectLastDeviceWhileReconnectingRetriesNow() = runTest {
        val h = Harness(this)
        h.store.lastAddress = ADDR_A
        val t = h.connectA()
        t.drop(133)
        runCurrent()
        h.advance(300)
        assertTrue(h.client.connectLastDevice())
        runCurrent()
        assertEquals(2, h.transports.size)
        assertEquals(ConnectionState.Connected::class, h.state::class)
    }

    @Test fun connectLastDeviceWhileConnectedReconnectsAsFirstConnect() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        h.onCreate = { it.drop(133) }
        assertTrue(h.client.connectLastDevice())
        runCurrent()
        assertEquals(1, t.closeCalls)
        assertEquals(2, h.transports.size)
        assertEquals(ADDR_A, h.last.address)
        assertEquals(ConnectionState.Failed(FailureReason.GattError(133)), h.state)
    }

    @Test fun userDisconnectDoesNotReconnect() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        h.client.disconnect()
        t.drop(8)
        runCurrent()
        h.advance(60_000)
        assertEquals(ConnectionState.Idle, h.state)
        assertEquals(1, h.transports.size)
        assertEquals(1, t.releases)
    }

    @Test fun powerOffSuccessDoesNotReconnect() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.responder = FakeGattTransport.respond(ScaleCommand.PowerOff, BleVectors.hex("rx-poweroff-writeack-001")) }
        val t = h.connectA()
        assertEquals(CommandResult.Success, h.client.send(ScaleCommand.PowerOff))
        t.drop(19)
        runCurrent()
        h.advance(60_000)
        assertEquals(ConnectionState.Idle, h.state)
        assertEquals(1, h.transports.size)
    }

    @Test fun connectOtherDeviceWhileConnected() = runTest {
        val h = Harness(this)
        val a = h.connectA()
        h.client.connect(ADDR_B)
        runCurrent()
        assertEquals(1, a.releases)
        assertEquals(ADDR_B, h.last.address)
        assertEquals(ADDR_B, (h.state as ConnectionState.Connected).device.address)
        h.advance(60_000)
        assertEquals(2, h.transports.size)   // A로 재연결하지 않는다
    }

    @Test fun startScanWhileConnectedIsIgnored() = runTest {
        val h = Harness(this)
        h.connectA()
        val s = h.state
        h.client.startScan()
        h.advance(7_000)
        assertTrue(h.scanner.startTimesMs.isEmpty())
        assertEquals(s, h.state)
    }

    // ---- 블루투스 상태 ----

    @Test fun bluetoothOffFailsPendingAndCancelsReconnect() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        h.env.setBluetooth(false)
        runCurrent()
        assertEquals(ConnectionState.BluetoothOff, h.state)
        assertEquals(CommandResult.Failed(CommandFailure.Disconnected), r.await())
        assertEquals(1, t.releases)
        h.advance(60_000)
        assertEquals(1, h.transports.size)
        assertEquals(ConnectionState.BluetoothOff, h.state)
    }

    @Test fun bluetoothOffDuringReconnectCancelsIt() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        t.drop(133)
        runCurrent()
        h.env.setBluetooth(false)
        h.advance(60_000)
        assertEquals(1, h.transports.size)
        assertEquals(ConnectionState.BluetoothOff, h.state)
    }

    @Test fun bluetoothOnConnectsSavedDeviceFromAttempt1() = runTest {
        val h = Harness(this)
        h.record()
        h.connectA()
        h.env.setBluetooth(false)
        runCurrent()
        h.advance(5_000)
        h.env.setBluetooth(true)
        runCurrent()
        assertEquals(ConnectionState.Reconnecting(ADDR_A, 1, 5_000 * MS), h.states.last { it is ConnectionState.Reconnecting })
        assertEquals(2, h.transports.size)
        assertTrue(h.state is ConnectionState.Connected)
    }

    @Test fun bluetoothOnAfterUserDisconnectDoesNotConnect() = runTest {
        val h = Harness(this)
        h.connectA()
        h.client.disconnect()
        h.env.setBluetooth(false)
        h.env.setBluetooth(true)
        h.advance(60_000)
        assertEquals(1, h.transports.size)
        assertEquals(ConnectionState.Idle, h.state)
    }

    @Test fun startsInBluetoothOffAndConnectsWhenTurnedOn() = runTest {
        val h = Harness(this)
        h.env.btOn = false
        h.store.lastAddress = ADDR_A
        assertEquals(ConnectionState.BluetoothOff, h.state)
        assertTrue(h.client.connectLastDevice())
        runCurrent()
        assertEquals(ConnectionState.BluetoothOff, h.state)
        h.env.setBluetooth(true)
        runCurrent()
        assertTrue(h.state is ConnectionState.Connected)
    }
}
