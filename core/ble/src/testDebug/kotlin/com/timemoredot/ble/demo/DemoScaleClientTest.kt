package com.timemoredot.ble.demo

import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import com.timemoredot.protocol.WeightUnit
import com.timemoredot.protocol.demo.DemoScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

private const val MS = 1_000_000L
private const val SEC = 1_000 * MS

/** 데모 저울 + 실제 BleScaleClient(architecture.md 10.1.6 두 번째 줄). 가상 시간. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoScaleClientTest {

    private class Rig(val test: TestScope) {
        val clock = MonotonicClock { test.testScheduler.currentTime * MS }
        val client: ScaleClient = DemoScale.clientForTest(test.backgroundScope, clock)
        val readings = mutableListOf<ScaleReading>()
        val states = mutableListOf<Pair<Long, ConnectionState>>()   // (ns, 상태)

        init {
            val d = UnconfinedTestDispatcher(test.testScheduler)
            test.backgroundScope.launch(d) { client.readings.collect { readings += it } }
            test.backgroundScope.launch(d) { client.connection.collect { states += clock.nowNanos() to it } }
        }

        val state: ConnectionState get() = client.connection.value

        fun advance(ms: Long) {
            test.advanceTimeBy(ms)
            test.runCurrent()
        }

        fun connect(s: DemoScenario) {
            client.connect(s.address)
            advance(2_000)
            val c = state
            check(c is ConnectionState.Connected && c.device.disReadDone) { "연결 안 됨: $c" }
        }

        /** 영점 Success 뒤 첫 0 g 측정 시각(ns). */
        suspend fun tare(): Long {
            assertEquals(CommandResult.Success, client.send(ScaleCommand.Tare))
            advance(1_000)
            return readings.first { it.weightGrams == 0.0 }.atNanos
        }
    }

    @Test fun scanListsSixScenarios() = runTest {
        val r = Rig(this)
        r.client.startScan()
        r.advance(1_000)
        assertEquals(DemoScenario.entries.map { it.address to it.displayName }, r.client.scanResults.value.map { it.address to it.name })
    }

    @Test fun connectReadsDisAndStatus() = runTest {
        val r = Rig(this)
        r.connect(DemoScenario.BrewV60)
        val d = (r.state as ConnectionState.Connected).device
        assertEquals("TES017", d.modelNumber)
        assertEquals("demo", d.firmwareRevision)
        assertTrue(d.disReadDone)
        assertFalse(d.modelMismatch)
        val st = r.client.status.value
        assertEquals(WeightUnit.Gram, st.unit)
        assertEquals(82, st.batteryPercent)
        assertEquals(ScaleTimerState.Reset, st.scaleTimer)
    }

    @Test fun tareThenTimerThenPour() = runTest {
        val r = Rig(this)
        r.connect(DemoScenario.BrewV60)
        assertEquals(CommandResult.Success, r.client.send(ScaleCommand.Tare))
        val afterAck = r.readings.size
        r.advance(1_000)
        val after = r.readings.drop(afterAck).map { (it.weightGrams!! * 10).roundToInt() }   // 0.1 g 단위
        val pre = after.takeWhile { it == 2134 }
        assertTrue("$after", pre.size <= 3)
        assertEquals(0, after[pre.size])
        val zero = r.readings.first { it.weightGrams == 0.0 }.atNanos

        assertEquals(CommandResult.Success, r.client.send(ScaleCommand.SetTimer(TimerAction.Start)))
        assertEquals(ScaleTimerState.Running, r.client.status.value.scaleTimer)
        val startIdx = r.readings.size
        r.advance(5_000)
        val secs = r.readings.drop(startIdx).map { it.scaleTimerSec }
        assertEquals(secs.sorted(), secs)
        assertTrue(secs.last() > secs.first())
        assertTrue(r.state is ConnectionState.Connected)   // Unknown 0x04는 버려짐
        assertTrue(r.readings.filter { it.atNanos <= zero + 3 * SEC }.all { it.weightGrams == 0.0 || it.atNanos < zero })
        assertTrue(r.readings.single { it.atNanos == zero + 4 * SEC }.weightGrams!! > 0.0)
    }

    @Test fun reconnectScenario() = runTest {
        val r = Rig(this)
        r.connect(DemoScenario.Reconnect)
        val start = r.tare() + 3 * SEC
        assertEquals(CommandResult.Success, r.client.send(ScaleCommand.SetTimer(TimerAction.Start)))
        r.advance((start + 24 * SEC) / MS - testScheduler.currentTime)
        assertTrue(r.readings.none { it.atNanos >= start + 20 * SEC && it.atNanos < start + 25_000 * MS })
        assertTrue(r.states.filter { it.first in start..start + 24 * SEC }.all { it.second is ConnectionState.Connected })
        assertTrue(r.state is ConnectionState.Connected)

        val beforeDrop = r.states.size
        r.advance((start + 50 * SEC) / MS - testScheduler.currentTime)
        val seq = r.states.drop(beforeDrop).map { (_, s) -> (s as? ConnectionState.Reconnecting)?.attempt?.toString() ?: s::class.simpleName }
        assertEquals(listOf("1", "2", "3", "Connected"), seq.distinct())
        assertEquals("Connected", seq.last())
        assertEquals(ScaleTimerState.Running, r.client.status.value.scaleTimer)
    }

    @Test fun connectFail() = runTest {
        val r = Rig(this)
        r.client.connect(DemoScenario.ConnectFail.address)
        r.advance(500)
        assertEquals(ConnectionState.Failed(FailureReason.GattError(133)), r.state)
    }

    @Test fun bluetoothOffThenReconnect() = runTest {
        val r = Rig(this)
        r.connect(DemoScenario.BluetoothOff)
        val connectedAt = r.states.first { it.second is ConnectionState.Connected }.first
        r.advance(40_000)
        val off = r.states.first { it.second == ConnectionState.BluetoothOff }
        assertTrue(off.first - connectedAt in 20 * SEC..(20 * SEC + 200 * MS))
        val after = r.states.dropWhile { it !== off }.drop(1)
        val re = after.first()
        assertEquals(1, (re.second as ConnectionState.Reconnecting).attempt)
        assertEquals(off.first + 8 * SEC, re.first)
        assertTrue(after.drop(1).first().second is ConnectionState.Connected)
        assertTrue(r.state is ConnectionState.Connected)
    }

    @Test fun overloadReadings() = runTest {
        val r = Rig(this)
        r.connect(DemoScenario.Overload)
        val start = r.tare() + 3 * SEC
        r.advance((start + 25 * SEC) / MS - testScheduler.currentTime)
        val over = r.readings.filter { it.overload }
        assertTrue(over.isNotEmpty())
        over.forEach { assertNull(it.weightGrams) }
        assertTrue(r.readings.last().weightGrams != null)
    }
}
