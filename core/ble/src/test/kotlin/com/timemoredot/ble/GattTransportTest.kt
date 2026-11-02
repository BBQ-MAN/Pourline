package com.timemoredot.ble

import app.cash.turbine.test
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 6절 GattTransport.close()의 이벤트 종료, 버린 transport 무시, 3절 send() 디스패처. */
@OptIn(ExperimentalCoroutinesApi::class)
class GattTransportTest {

    @Test fun eventsCompleteAfterClose() = runTest {
        val t = FakeGattTransport(ADDR_A, MonotonicClock { 0 })
        t.events.test {
            t.connected()
            assertEquals(GattEvent.ConnectionChanged(true, 0), awaitItem())
            t.close()
            awaitComplete()
        }
    }

    @Test fun queuedEventsDeliveredThenComplete() = runTest {
        val t = FakeGattTransport(ADDR_A, MonotonicClock { 0 })
        t.drop(133)
        t.close()
        t.events.test {
            assertEquals(GattEvent.ConnectionChanged(false, 133), awaitItem())
            awaitComplete()
        }
    }

    @Test fun discardedTransportEventsAreIgnored() = runTest {
        val h = Harness(this)
        val old = h.connectA()
        old.leakEventsAfterClose = true
        old.drop(133)
        runCurrent()
        h.advance(1_000)   // 재연결 attempt 1
        assertNotEquals(old, h.last)
        val connected = h.state
        assertTrue(connected is ConnectionState.Connected)
        val status = h.client.status.value
        val readings = mutableListOf<ScaleReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { h.client.readings.toList(readings) }

        old.drop(8)
        old.notify(BleVectors.hex("rx-weight-positive-002"))
        old.notify(BleVectors.hex("rx-battery-read-002"))
        h.advance(60_000)
        assertEquals(connected, h.state)
        assertEquals(status, h.client.status.value)
        assertTrue(readings.isEmpty())
        assertEquals(2, h.transports.size)

        h.last.notify(BleVectors.hex("rx-weight-positive-002"))   // 현재 transport는 반영된다
        runCurrent()
        assertEquals(1, readings.size)
    }

    @Test fun sendFromDefaultDispatcherRunsOnScopeDispatcher() = runTest {
        val h = Harness(this)
        val testThread = Thread.currentThread()
        var writeThread: Thread? = null
        val start = ScaleCommand.SetTimer(TimerAction.Start)
        h.onCreate = {
            it.connected()
            val respond = FakeGattTransport.respond(start, BleVectors.hex("rx-multi-frames-003"))
            it.responder = { frame -> writeThread = Thread.currentThread(); respond(frame) }
        }
        h.connectA()
        assertNotEquals(ScaleTimerState.Running, h.client.status.value.scaleTimer)
        val r = withContext(Dispatchers.Default) { h.client.send(start) }
        assertEquals(CommandResult.Success, r)
        assertEquals(ScaleTimerState.Running, h.client.status.value.scaleTimer)
        assertSame(testThread, writeThread)   // 대기 키 등록·쓰기·환원기가 scope 디스패처(테스트 스레드)에서 돈다
    }
}
