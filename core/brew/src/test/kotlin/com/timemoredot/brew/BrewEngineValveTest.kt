package com.timemoredot.brew

import com.timemoredot.brew.BrewEffect.AppendSample
import com.timemoredot.brew.BrewEffect.ValveCue
import com.timemoredot.brew.BrewInput.Discard
import com.timemoredot.brew.BrewInput.SelectRecipe
import com.timemoredot.brew.BrewInput.SettingsChanged
import com.timemoredot.brew.BrewInput.Tared
import com.timemoredot.brew.BrewInput.Tick
import com.timemoredot.brew.BrewInput.UserTimer
import com.timemoredot.protocol.TimerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 7.2 "밸브 신호". 시계열은 10 Hz 합성, 타이머 시작 벽시계 T0 ≠ 0으로 경과와 atNanos를 구분한다. */
class BrewEngineValveTest {
    private val t0 = sec(5.0)
    private fun el(ms: Long) = t0 + ms(ms)     // 첫 Running 구간에서 경과 ms → atNanos
    private fun List<BrewEffect>.cues() = filterIsInstance<ValveCue>()
    private fun Rig.cues() = all.filterIsInstance<ValveCue>()

    private fun started(r: Recipe?): Rig = Rig().apply {
        feed(SelectRecipe(r, 0))
        assertTrue(feed(UserTimer(TimerAction.Start, t0)).cues().isEmpty())
    }

    @Test fun harioCueExactlyOnceAt120AfterAppendSample() {
        val r = started(hario())
        for (k in 0L..2000L) {          // 경과 0.0 ~ 200.0 s
            val fx = r.feed(reading(el(100 * k), 50.0, 2.0))
            if (k == 1200L) {
                assertEquals(listOf(ValveCue(ValveEvent(120, true))), fx.cues())
                assertTrue(fx.indexOfFirst { it is ValveCue } > fx.indexOfFirst { it is AppendSample })
            } else {
                assertTrue("k=$k", fx.cues().isEmpty())
            }
        }
        assertEquals(1, r.cues().size)
        assertEquals(BrewPhase.Running, r.state.phase)
    }

    @Test fun pausedNoCueResumeCuesOnceOnFirstCrossing() {
        val r = started(hario())
        for (k in 0L..1195L) r.feed(reading(el(100 * k), 50.0))
        r.feed(UserTimer(TimerAction.Pause, el(119_500)))
        for (k in 1L..300L) {           // 일시정지 30 s, 벽시계는 흐름
            r.feed(reading(el(119_500 + 100 * k), 50.0))
            if (k % 10 == 0L) r.feed(Tick(el(119_500 + 100 * k)))
        }
        assertTrue(r.cues().isEmpty())
        val resumeAt = el(149_500)
        assertTrue(r.feed(UserTimer(TimerAction.Start, resumeAt)).cues().isEmpty())
        for (k in 1L..10L) {            // 경과 119.6 ~ 120.5 s
            val fx = r.feed(reading(resumeAt + ms(100 * k), 50.0))
            assertEquals("k=$k", if (k == 5L) 1 else 0, fx.cues().size)   // 경과 120.0 s
        }
        assertEquals(1, r.cues().size)
    }

    @Test fun ticksOnlyCueOnTickThatCrosses() {
        val r = started(hario())
        for (k in 0..118) r.feed(Tick(el(500L + 1000L * k)))   // 경과 0.5 ~ 118.5
        assertTrue(r.feed(Tick(el(119_500))).cues().isEmpty())
        assertEquals(listOf(ValveCue(ValveEvent(120, true))), r.feed(Tick(el(120_500))).cues())
        assertEquals(1, r.cues().size)
    }

    @Test fun devilGapCuesOnlyLastAndLateReadingDoesNotRepeatAndDiscardRearms() {
        val r = started(devil())
        for (s in 10L..70L step 10) r.feed(Tick(el(1000 * s)))
        assertTrue(r.cues().isEmpty())
        assertEquals(listOf(ValveCue(ValveEvent(105, true))), r.feed(Tick(el(110_000))).cues())
        assertTrue(r.feed(reading(el(100_000), 100.0)).cues().isEmpty())   // 늦게 온 이른 시각
        assertEquals(1, r.cues().size)

        r.feed(Discard(el(111_000)))
        assertEquals(BrewPhase.Idle, r.state.phase)
        val t1 = sec(300.0)
        assertTrue(r.feed(UserTimer(TimerAction.Start, t1)).cues().isEmpty())
        val cueAtMs = mutableListOf<Long>()
        for (k in 0L..1200L) if (r.feed(reading(t1 + ms(100 * k), 100.0)).cues().isNotEmpty()) cueAtMs += 100 * k
        assertEquals(listOf(75_000L, 105_000L), cueAtMs)
        assertEquals(listOf(ValveEvent(105, true), ValveEvent(75, false), ValveEvent(105, true)), r.cues().map { it.event })
    }

    @Test fun noRecipeOrEmptyScheduleNeverCues() {
        listOf(null, hario().withoutValve()).forEach { recipe ->
            val r = started(recipe)
            for (k in 0L..2000L) {
                r.feed(reading(el(100 * k), 50.0))
                if (k % 10 == 0L) r.feed(Tick(el(100 * k + 50)))
            }
            assertTrue(r.cues().isEmpty())
        }
    }

    @Test fun capReachedInputHasNoCue() {
        val recipe = valveRecipe(Triple(240, 10, true), Triple(240, 3599, true), Triple(240, 3600, false))
        assertEquals(listOf(ValveEvent(0, true), ValveEvent(3599, false)), recipe.valveSchedule)
        val r = started(recipe)
        for (k in 0..3598) r.feed(Tick(el(500L + 1000L * k)))   // 마지막 확인 경과 3598.5 s
        val fx = r.feed(Tick(el(3_600_000)))
        assertEquals(BrewPhase.Finished, r.state.phase)
        assertEquals(PhaseCause.CapReached, fx.filterIsInstance<BrewEffect.PhaseChanged>().single().cause)
        assertTrue(fx.cues().isEmpty())
        assertTrue(r.cues().isEmpty())
    }

    @Test fun valveHapticOffKeepsPhaseAndDoesNotBlockCue() {
        val r = Rig()
        r.feed(SelectRecipe(hario(), 0))
        r.feed(Tared(ms(10)))
        assertEquals(BrewPhase.WaitingForPour, r.state.phase)
        val off = BrewSettings(valveHaptic = false)
        assertTrue(r.feed(SettingsChanged(off, ms(20))).isEmpty())
        assertEquals(BrewPhase.WaitingForPour, r.state.phase)
        assertEquals(off, r.state.settings)

        r.feed(UserTimer(TimerAction.Start, t0))
        assertTrue(r.feed(SettingsChanged(off, el(1000))).isEmpty())
        assertEquals(BrewPhase.Running, r.state.phase)
        for (k in 0L..1210L) r.feed(reading(el(100 * k), 50.0))
        assertEquals(listOf(ValveEvent(120, true)), r.cues().map { it.event })
    }

    @Test fun settingsDefaultValveHapticOn() = assertTrue(BrewSettings().valveHaptic)
}
