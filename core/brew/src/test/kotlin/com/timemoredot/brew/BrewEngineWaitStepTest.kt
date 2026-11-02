package com.timemoredot.brew

import com.timemoredot.brew.BrewInput.SelectRecipe
import com.timemoredot.brew.BrewInput.UserTimer
import com.timemoredot.protocol.TimerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 7.2 "단계": 기다리기 단계는 무게 판정에서 건너뛰고, paceDeltaSec는 평평한 구간에서 0이다. 10 Hz 합성. */
class BrewEngineWaitStepTest {
    private fun started(r: Recipe) = Rig().apply {
        feed(SelectRecipe(r, 0))
        feed(UserTimer(TimerAction.Start, 0))
    }

    @Test fun devilSkipsWaitStepsAndPaceIsFlatWhileWaiting() {
        val r = started(devil())
        var sawIndex3 = false
        for (k in 0..750) {             // 경과 0.0 ~ 75.0 s
            val t = k / 10.0
            val w = when {
                t <= 10 -> 6.0 * t                       // 0 → 60 g
                t <= 40 -> 60.0 + 2.0 * (t - 10)         // 60 → 120 g
                else -> 120.0 + if (k % 2 == 0) 0.5 else -0.5
            }
            r.feed(reading(sec(t), w))
            val g = r.state.guide!!
            if (w >= 119.0) {
                assertEquals("t=$t", 3, g.index)
                sawIndex3 = true
            }
            if (t >= 41.0) assertTrue("t=$t delta=${g.paceDeltaSec}", Math.abs(g.paceDeltaSec) <= 0.01)
        }
        assertTrue(sawIndex3)
        r.feed(reading(sec(80.0), 120.0))
        assertEquals(5.0, r.state.guide!!.paceDeltaSec, 0.1)
        assertFalse(r.state.guide!!.lastStepReached)
        r.feed(reading(sec(85.0), 280.0))
        assertTrue(r.state.guide!!.lastStepReached)
        assertEquals(5, r.state.guide!!.index)
    }

    @Test fun harioLastStepReachedAfterPourAndNoEarlyFinish() {
        val r = started(hario())
        for (k in 0..1499) {            // 경과 0.0 ~ 149.9 s
            val t = k / 10.0
            val w = if (t <= 10) 24.0 * t else 240.0 + if (k % 2 == 0) 0.3 else -0.3
            r.feed(reading(sec(t), w))
            if (t >= 10.0) {
                assertTrue(r.state.guide!!.lastStepReached)
                assertEquals(2, r.state.guide!!.index)
            }
            if (k == 1000) assertEquals(0.0, r.state.guide!!.paceDeltaSec, 0.01)   // 경과 100 s, 240.3 g
        }
        assertEquals(150, hario().finishSec)
        assertEquals(BrewPhase.Running, r.state.phase)
        assertTrue(r.all.none { it is BrewEffect.Completed })
    }
}
