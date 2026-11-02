package com.timemoredot.protocol.demo

import com.timemoredot.protocol.DecodeResult
import com.timemoredot.protocol.EncodeResult
import com.timemoredot.protocol.FrameCodec
import com.timemoredot.protocol.FrameReassembler
import com.timemoredot.protocol.Op
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleFrame
import com.timemoredot.protocol.ScaleFrame.WeightReport
import com.timemoredot.protocol.ScaleStateReducer
import com.timemoredot.protocol.TimerAction
import com.timemoredot.protocol.hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val MS = 1_000_000L
private const val SEC = 1_000 * MS

/** architecture.md 10.1.2 흉내 규칙. 가상 nanos, 알림은 실제 FrameReassembler·ScaleStateReducer로 푼다. */
class DemoScaleDeviceTest {

    /** 연결된 데모 저울. step()은 100 ms 진행 뒤 poll. */
    private class Sim(scenario: DemoScenario) {
        var now = 0L
        val device = DemoScaleDevice(scenario, now)
        private val reassembler = FrameReassembler()
        val reducer = ScaleStateReducer().apply { onFrame(ScaleFrame.UnitReport(Op.Read, 0), 0) }
        var link: DemoLinkEvent? = null
        val weights = mutableListOf<Pair<Long, WeightReport>>()   // (시각, 프레임) 낸 순서

        init {
            check(device.onConnectAttempt(now))
            device.onConnected(now)
        }

        /** 알림별 프레임 목록. */
        fun step(): List<List<ScaleFrame>> {
            now += 100 * MS
            val p = device.poll(now)
            link = p.link
            return p.notifications.map { n ->
                reassembler.feed(n).map { (it as DecodeResult.Frame).frame }.onEach {
                    if (it is WeightReport) weights += now to it
                    reducer.onFrame(it, now)
                }
            }
        }

        fun stepUntil(atNanos: Long): List<List<List<ScaleFrame>>> = buildList { while (now < atNanos) add(step()) }

        fun write(c: ScaleCommand) = device.onWrite((FrameCodec.encode(c) as EncodeResult.Ok).bytes, now)

        /** 영점을 쓰고 첫 0 g 프레임 시각을 돌려준다. */
        fun tareToZero(): Long {
            write(ScaleCommand.Tare)
            while (true) {
                step()
                weights.lastOrNull()?.takeIf { it.second.weightRaw == 0 }?.let { return it.first }
                check(now < 10 * SEC + weights.first().first)
            }
        }

        fun rawAt(atNanos: Long): Int = weights.single { it.first == atNanos }.second.weightRaw
    }

    @Test fun tareAckOrderMatchesDevice() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(1 * SEC)
        assertTrue(s.weights.all { it.second.weightRaw == 2134 })
        val t0 = s.now
        s.write(ScaleCommand.Tare)
        assertEquals(listOf(listOf(WeightReport::class)), s.step().map { n -> n.map { it::class } })   // t0 + 100 ms: 아직
        val first = s.step()   // t0 + 200 ms: t0 + 150 ms 뒤 첫 poll
        assertTrue(s.now >= t0 + 150 * MS)
        assertEquals(1, first.size)
        assertEquals(2134, (first[0][0] as WeightReport).weightRaw)
        assertEquals(ScaleFrame.WriteAck(0x0D, true), first[0][1])
        assertEquals(2, first[0].size)
        val next = (1..3).map { (s.step().single().single() as WeightReport).weightRaw }
        assertEquals(listOf(2134, 2134, 0), next)   // C-0D-2, rx-tare-preweight-seq-001 모양
    }

    @Test fun v60PourCurve() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(1 * SEC)
        val zero = s.tareToZero()
        val start = zero + 3 * SEC
        s.stepUntil(start + 100 * SEC)
        assertTrue(s.weights.filter { it.first in zero..start }.all { it.second.weightRaw == 0 })
        assertNear(610, s.rawAt(start + 10 * SEC))
        assertNear(610, s.rawAt(start + 40 * SEC))
        s.weights.filter { it.first >= start + 90 * SEC }.forEach { assertNear(2500, it.second.weightRaw) }
    }

    @Test fun harioPourCurve() {
        val s = Sim(DemoScenario.BrewHario)
        val start = s.tareToZero() + 3 * SEC
        s.stepUntil(start + 20 * SEC)
        s.weights.filter { it.first >= start + 10 * SEC }.forEach { assertNear(2400, it.second.weightRaw) }
        assertTrue(s.rawAt(start + 5 * SEC) in 1..2399)
    }

    @Test fun flowIsClampedTenFrameDifference() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(2 * SEC)
        s.stepUntil(s.tareToZero() + 100 * SEC)
        val raws = s.weights.map { it.second.weightRaw }
        s.weights.forEachIndexed { i, (_, w) ->
            val want = if (i < 10) 0 else (raws[i] - raws[i - 10]).coerceIn(0, 999)
            assertEquals("frame $i", want, w.flowRaw)
        }
        assertTrue(s.weights.any { it.second.flowRaw > 0 })
    }

    @Test fun timerStartPauseReset() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(1 * SEC)
        assertEquals(3, readTimer(s))
        val t0 = s.now
        s.write(ScaleCommand.SetTimer(TimerAction.Start))
        s.step()
        val reply = s.step().single()   // t0 + 200 ms
        assertEquals(listOf(WeightReport::class, ScaleFrame.WriteAck::class, ScaleFrame.Unknown::class), reply.map { it::class })
        assertEquals(ScaleFrame.WriteAck(0x02, true), reply[1])
        assertEquals(0x04, (reply[2] as ScaleFrame.Unknown).cmd)
        s.stepUntil(t0 + 5 * SEC)
        s.weights.filter { it.first >= t0 }.forEach { (at, w) -> assertEquals(((at - t0) / SEC).toInt(), w.timerSec) }
        assertEquals(1, readTimer(s))

        s.write(ScaleCommand.SetTimer(TimerAction.Pause))
        val paused = s.weights.size
        s.stepUntil(s.now + 3 * SEC)
        val after = s.weights.drop(paused).map { it.second.timerSec }
        assertTrue(after.isNotEmpty() && after.all { it == after.first() })
        assertEquals(2, readTimer(s))

        s.write(ScaleCommand.SetTimer(TimerAction.Reset))
        s.stepUntil(s.now + 1 * SEC)
        assertEquals(0, s.weights.last().second.timerSec)
    }

    /** ReadTimer 응답 stateRaw. */
    private fun readTimer(s: Sim): Int {
        s.write(ScaleCommand.ReadTimer)
        return s.stepUntil(s.now + 300 * MS).flatten().flatten().filterIsInstance<ScaleFrame.TimerState>().single().stateRaw
    }

    @Test fun readRepliesAfter150ms() {
        val cases = listOf(
            ScaleCommand.ReadUnit to ScaleFrame.UnitReport(Op.Read, 0),
            ScaleCommand.ReadBattery to ScaleFrame.Battery(Op.Read, 3, 82),
            ScaleCommand.ReadFirmware to ScaleFrame.FirmwareVersion("demo"),
            ScaleCommand.ReadName to ScaleFrame.DeviceName("TIMEMORE_Dot"),
        )
        for ((cmd, frame) in cases) {
            val s = Sim(DemoScenario.BrewV60)
            s.stepUntil(1 * SEC)
            s.write(cmd)
            assertFalse(cmd.toString(), frame in s.step().single())        // +100 ms
            assertEquals(cmd.toString(), frame, s.step().single().last())   // +200 ms: 150 ms 뒤 첫 알림
        }
    }

    @Test fun badHeaderAndUnknownCmdGetNoReply() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(1 * SEC)
        listOf("A55B02060000 0000", "A55A02300000 0000", "A55A0330000101 0000", "A55A020600000000FF").forEach {
            s.device.onWrite(it.replace(" ", "").hex(), s.now)
        }
        val frames = s.stepUntil(s.now + 2 * SEC).flatten().flatten()
        assertTrue(frames.isNotEmpty() && frames.all { it is WeightReport })
    }

    @Test fun batteryNotifyEvery30sBeforeWeight() {
        val s = Sim(DemoScenario.BrewV60)
        val notes = mutableListOf<Pair<Long, List<ScaleFrame>>>()
        while (s.now < 61 * SEC) s.step().forEach { notes += s.now to it }
        val withBattery = notes.filter { (_, f) -> f.any { it is ScaleFrame.Battery } }
        assertEquals(listOf(30 * SEC, 60 * SEC), withBattery.map { it.first })
        withBattery.forEach { (_, f) ->
            assertEquals(ScaleFrame.Battery(Op.Notify, 3, 82), f[0])
            assertTrue(f[1] is WeightReport)
        }
    }

    @Test fun overloadOnlyFrom15To20s() {
        val s = Sim(DemoScenario.Overload)
        val start = s.tareToZero() + 3 * SEC
        s.stepUntil(start + 25 * SEC)
        val pour = s.weights.filter { it.first >= start }
        pour.forEach { (at, w) ->
            val t = (at - start) / SEC.toDouble()
            assertEquals("t=$t", t >= 15 && t < 20, w.overload)
            val reading = ScaleStateReducer().apply { onFrame(ScaleFrame.UnitReport(Op.Read, 0), 0) }.onFrame(w, at)!!
            if (w.overload) assertNull(reading.weightGrams) else assertNotNull(reading.weightGrams)
        }
        assertTrue(pour.any { it.second.overload })
    }

    @Test fun reconnectScenarioGapDropAndAttempts() {
        val s = Sim(DemoScenario.Reconnect)
        val start = s.tareToZero() + 3 * SEC
        var drop: Pair<Long, DemoLinkEvent?>? = null
        while (drop == null) {
            val n = s.step()
            val t = (s.now - start) / SEC.toDouble()
            if (s.link != null) drop = s.now to s.link
            else if (t >= 20.0 && t < 25.0) assertTrue("t=$t", n.isEmpty())
            else assertEquals("t=$t", 1, n.size)
        }
        assertEquals(start + 40 * SEC, drop.first)
        assertEquals(DemoLinkEvent.Drop(8), drop.second)
        assertEquals(listOf(false, false, true), (1..3).map { s.device.onConnectAttempt(s.now) })
    }

    @Test fun connectFailAlwaysFails() {
        val d = DemoScaleDevice(DemoScenario.ConnectFail, 0)
        assertTrue((1..5).none { d.onConnectAttempt(it * SEC) })
    }

    @Test fun bluetoothOffOncePollAfter20s() {
        val s = Sim(DemoScenario.BluetoothOff)
        val events = mutableListOf<Pair<Long, DemoLinkEvent>>()
        while (s.now < 60 * SEC) { s.step(); s.link?.let { events += s.now to it } }
        assertEquals(listOf(20 * SEC to DemoLinkEvent.BluetoothOff(8_000)), events)
    }

    @Test fun powerOffAckThenDrop() {
        val s = Sim(DemoScenario.BrewV60)
        s.stepUntil(1 * SEC)
        s.write(ScaleCommand.PowerOff)
        val frames = s.stepUntil(s.now + 200 * MS).flatten().flatten()
        assertTrue(ScaleFrame.WriteAck(0x0B, true) in frames)
        s.step()
        assertEquals(DemoLinkEvent.Drop(0x13), s.link)
    }

    private fun assertNear(want: Int, got: Int) = assertTrue("$got ≉ $want", abs(got - want) <= 1)
}
