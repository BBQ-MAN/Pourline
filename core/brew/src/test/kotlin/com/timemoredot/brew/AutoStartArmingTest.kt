package com.timemoredot.brew

import com.timemoredot.brew.BrewInput.ConnectionChanged
import com.timemoredot.brew.BrewInput.Tared
import com.timemoredot.brew.BrewPhase.Running
import com.timemoredot.brew.BrewPhase.WaitingForPour
import com.timemoredot.protocol.TimerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 7.2 WaitingForPour 무장 규칙(C-0D-2)과 7.1·7.2 샘플 유량. */
class AutoStartArmingTest {
    private fun preWeightSequence(preGrams: Double) {
        val r = Rig()
        val t0 = ms(1000)
        r.feed(Tared(t0))   // Idle → WaitingForPour의 PhaseChanged는 이 입력의 것
        val fx = r.feedAll(
            reading(t0 - ms(50), preGrams), reading(t0 + ms(120), preGrams),
            reading(t0 + ms(180), preGrams), reading(t0 + ms(300), 0.0),
        )
        assertEquals(WaitingForPour, r.state.phase)
        assertTrue(fx.none { it is BrewEffect.SendTimer || it is BrewEffect.PhaseChanged })
        // 1.2 g부터 확인 구간 500 ms(F-B4-1)
        val fx2 = r.feedAll(*(0..5).map { reading(t0 + ms(400 + 100L * it), 1.2 + 0.2 * it) }.toTypedArray())
        assertEquals(Running, r.state.phase)
        assertEquals(1, fx2.count { it == BrewEffect.SendTimer(TimerAction.Start) })
        assertEquals(PhaseCause.AutoStart, fx2.filterIsInstance<BrewEffect.PhaseChanged>().single().cause)
    }

    @Test fun probeA_213g_preTareWeightThenZero() = preWeightSequence(213.4)
    @Test fun probeA_145g_rxTarePreweightSeq001() = preWeightSequence(145.0)

    @Test fun noArmingNoCount() {
        val r = Rig()
        r.feed(Tared(0))
        r.feedAll(reading(ms(100), 1.2), reading(ms(200), 1.4), reading(ms(300), 1.5))
        assertEquals(WaitingForPour, r.state.phase)
    }

    @Test fun zeroReadingAtOrBeforeTareDoesNotArm() {
        val r = Rig()
        r.feed(Tared(ms(100)))
        r.feedAll(reading(ms(90), 0.0), reading(ms(100), 0.0), reading(ms(200), 1.2), reading(ms(300), 1.4))
        assertEquals(WaitingForPour, r.state.phase)
    }

    @Test fun secondTareDisarms() {
        val r = Rig()
        r.feedAll(Tared(0), reading(ms(100), 0.0), Tared(ms(200)), reading(ms(300), 1.2), reading(ms(400), 1.4))
        assertEquals(WaitingForPour, r.state.phase)
    }

    @Test fun sampleFlowEqualsStateFlowIncludingFlaggedAndNegativeScaleFlow() {
        val r = Rig()
        r.feed(ConnectionChanged(true, 0))
        r.feed(UserTimerStart)
        val inputs = listOf(
            reading(ms(100), 5.0, 2.0), reading(ms(200), 6.0, -1.0), reading(ms(300), null, 0.0, overload = true),
            reading(ms(400), 7.0, 3.0), ConnectionChanged(false, ms(450)),
        )
        for (i in inputs) {
            val samples = r.feed(i).filterIsInstance<BrewEffect.AppendSample>()
            assertEquals(1, samples.size)
            assertEquals(r.state.flowGramsPerSec, samples[0].sample.flowGramsPerSec.toDouble(), 1e-6)
            assertTrue(samples[0].sample.flowGramsPerSec >= 0f)
        }
        val r2 = Rig()
        r2.feed(UserTimerStart)
        val s = r2.feed(reading(ms(100), 5.0, -1.0)).filterIsInstance<BrewEffect.AppendSample>().single().sample
        assertTrue(s.flowGramsPerSec >= 0f)
    }

    private companion object {
        val UserTimerStart = BrewInput.UserTimer(TimerAction.Start, 0)
    }
}
