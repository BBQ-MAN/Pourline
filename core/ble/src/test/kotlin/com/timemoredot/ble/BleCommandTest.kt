package com.timemoredot.ble

import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleFrame
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 6절 명령 응답 대기와 수신 경로. 응답은 실측 결합 알림 벡터를 그대로 보낸다. */
@OptIn(ExperimentalCoroutinesApi::class)
class BleCommandTest {
    private val tareAck = BleVectors.hex("rx-tare-writeack-003")   // 무게 + WriteAck(0x0D, true)

    @Test fun tareSucceedsOnlyOnResponseFrame() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        assertTrue(t.writes.last().endsWith(",${FrameCodec_hex(ScaleCommand.Tare)})"))   // 로컬 쓰기 완료
        assertFalse(r.isCompleted)
        t.notify(tareAck)
        runCurrent()
        assertEquals(CommandResult.Success, r.await())
    }

    @Test fun tareRejected() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        t.notify(BleVectors.hex("rx-tare-writeack-002"))   // WriteAck(0x0D, false)
        runCurrent()
        assertEquals(CommandResult.Failed(CommandFailure.Rejected), r.await())
    }

    @Test fun tareTimeoutAfter1000ms() = runTest {
        val h = Harness(this)
        h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        h.advance(999)
        assertFalse(r.isCompleted)
        h.advance(1)
        assertEquals(CommandResult.Failed(CommandFailure.Timeout), r.await())
        assertTrue(h.state is ConnectionState.Connected)
    }

    @Test fun tareDisconnectedWhileWaiting() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        t.drop(8)
        runCurrent()
        assertEquals(CommandResult.Failed(CommandFailure.Disconnected), r.await())
    }

    @Test fun sendNotConnected() = runTest {
        val h = Harness(this)
        assertEquals(CommandResult.Failed(CommandFailure.NotConnected), h.client.send(ScaleCommand.Tare))
    }

    @Test fun secondSendWritesAfterFirstResponse() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val before = t.writes.size
        val a = async { h.client.send(ScaleCommand.Tare) }
        val b = async { h.client.send(ScaleCommand.SetTimer(TimerAction.Start)) }
        runCurrent()
        assertEquals(before + 1, t.writes.size)
        t.notify(tareAck)
        runCurrent()
        assertEquals(CommandResult.Success, a.await())
        assertEquals(before + 2, t.writes.size)
        assertFalse(b.isCompleted)
    }

    @Test fun cancelledSendClearsPendingKey() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val job = launch { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        job.cancel()
        runCurrent()
        t.notify(tareAck)   // 다음 send 전에 온 같은 키 프레임
        runCurrent()
        val next = async { h.client.send(ScaleCommand.Tare) }
        runCurrent()
        assertFalse(next.isCompleted)   // 앞 프레임으로 완료되지 않는다
        t.notify(tareAck)
        runCurrent()
        assertEquals(CommandResult.Success, next.await())
    }

    @Test fun setTimerStartWithMultiFrameResponse() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val events = mutableListOf<ScaleTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.timerEvents.toList(events) }
        val connected = h.state
        val r = async { h.client.send(ScaleCommand.SetTimer(TimerAction.Start)) }
        runCurrent()
        t.notify(BleVectors.hex("rx-multi-frames-003"))   // 무게 + WriteAck(0x02) + Notify 0x04
        runCurrent()
        assertEquals(CommandResult.Success, r.await())   // 0x02 Notify를 기다리지 않는다
        assertEquals(ScaleTimerState.Running, h.client.status.value.scaleTimer)
        assertEquals(connected, h.state)
        assertTrue(events.isEmpty())   // Write 성공은 timerEvents로 내지 않는다
    }

    @Test fun unknown0x04FramesChangeNothing() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val events = mutableListOf<ScaleTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.timerEvents.toList(events) }
        val status = h.client.status.value
        val connected = h.state
        t.notify(BleVectors.hex("rx-multi-frames-002"))
        runCurrent()
        assertEquals(status, h.client.status.value)
        assertEquals(connected, h.state)
        assertTrue(events.isEmpty())
    }

    @Test fun readUnitReturnsResponse() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val r = async { h.client.send(ScaleCommand.ReadUnit) }
        runCurrent()
        t.notify(BleVectors.hex("rx-unit-read-002"))
        runCurrent()
        val frame = (r.await() as CommandResult.Response).frame as ScaleFrame.UnitReport
        assertEquals(BleVectors.expect("rx-unit-read-002", 1).getValue("unitRaw").jsonPrimitive.int, frame.unitRaw)
    }

    @Test fun readUnitTimeout() = runTest {
        val h = Harness(this)
        h.onCreate = { t -> t.connected(); t.responder = { if (FakeGattTransport.keyOf(it) == FakeGattTransport.keyOf(FrameCodec_bytes(ScaleCommand.ReadUnit))) emptyList() else FakeGattTransport.defaultResponse(it) } }
        h.client.connect(ADDR_A)
        h.advance(1_000)   // 연결 직후 ReadUnit도 응답 없음
        val r = async { h.client.send(ScaleCommand.ReadUnit) }
        runCurrent()
        h.advance(1_000)
        assertEquals(CommandResult.Failed(CommandFailure.Timeout), r.await())
        assertTrue(h.client.status.value.unitReadFailed)
    }

    @Test fun timerReadResponseUpdatesStatusOnly() = runTest {
        val h = Harness(this)
        val events = mutableListOf<ScaleTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.timerEvents.toList(events) }
        h.connectA()   // GATT-1 ReadTimer 응답 = rx-timer-read-002
        val raw = BleVectors.expect("rx-timer-read-002", 1).getValue("stateRaw").jsonPrimitive.int
        assertEquals(ScaleTimerState.entries[raw - 1], h.client.status.value.scaleTimer)
        assertTrue(events.isEmpty())
    }

    @Test fun timerNotifyUpdatesStatusAndEmitsOnce() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val events = mutableListOf<ScaleTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.timerEvents.toList(events) }
        t.notify(BleVectors.hex("rx-timer-notify-001"))   // 원문 경로(v1.0.4 실기기에는 없음)
        runCurrent()
        val raw = BleVectors.byId("rx-timer-notify-001").getValue("expect").let { (it as kotlinx.serialization.json.JsonObject).getValue("stateRaw").jsonPrimitive.int }
        val expected = ScaleTimerState.entries[raw - 1]
        assertEquals(expected, h.client.status.value.scaleTimer)
        assertEquals(listOf(expected), events)
    }

    @Test fun readingsAreEmittedAfterUnitKnown() = runTest {
        val h = Harness(this)
        val t = h.connectA()
        val readings = mutableListOf<com.timemoredot.protocol.ScaleReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.readings.toList(readings) }
        h.advance(250)
        t.notify(BleVectors.hex("rx-tare-writeack-004"))   // 무게 + WriteAck(대기 없음)
        runCurrent()
        assertEquals(1, readings.size)
        assertEquals(250 * MS, readings[0].atNanos)
    }

    @Test fun payloadTooLargeWithDefaultMtu() = runTest {
        val h = Harness(this)
        h.onCreate = { it.connected(); it.mtuResult = null }
        h.connectA()
        // 12바이트 이름 + 8 = 20 = MTU 23 − 3 은 허용, 13바이트는 거부(android-ble 3절)
        assertEquals(CommandResult.Failed(CommandFailure.PayloadTooLarge), h.client.send(ScaleCommand.SetName("ABCDEFGHIJKLM")))
    }
}

internal fun FrameCodec_bytes(c: ScaleCommand): ByteArray = com.timemoredot.protocol.FrameCodec.bytes(c)
internal fun FrameCodec_hex(c: ScaleCommand): String = FrameCodec_bytes(c).toHexString(HexFormat.UpperCase)
