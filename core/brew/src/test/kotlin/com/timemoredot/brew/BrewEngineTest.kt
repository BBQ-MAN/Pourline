package com.timemoredot.brew

import com.timemoredot.brew.BrewEffect.AppendSample
import com.timemoredot.brew.BrewEffect.ClearSamples
import com.timemoredot.brew.BrewEffect.Completed
import com.timemoredot.brew.BrewEffect.Discarded
import com.timemoredot.brew.BrewEffect.PhaseChanged
import com.timemoredot.brew.BrewEffect.SendTimer
import com.timemoredot.brew.BrewInput.CancelWaiting
import com.timemoredot.brew.BrewInput.ConnectionChanged
import com.timemoredot.brew.BrewInput.Discard
import com.timemoredot.brew.BrewInput.Finish
import com.timemoredot.brew.BrewInput.NewBrew
import com.timemoredot.brew.BrewInput.ScaleTimerChanged
import com.timemoredot.brew.BrewInput.SelectRecipe
import com.timemoredot.brew.BrewInput.SettingsChanged
import com.timemoredot.brew.BrewInput.Tared
import com.timemoredot.brew.BrewInput.Tick
import com.timemoredot.brew.BrewInput.UserTimer
import com.timemoredot.brew.BrewPhase.Finished
import com.timemoredot.brew.BrewPhase.Idle
import com.timemoredot.brew.BrewPhase.Paused
import com.timemoredot.brew.BrewPhase.Running
import com.timemoredot.brew.BrewPhase.WaitingForPour
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewEngineTest {

    private fun Rig.start(at: Long = 0) = feed(UserTimer(TimerAction.Start, at))
    private fun List<BrewEffect>.phaseChange() = filterIsInstance<PhaseChanged>().single()

    // ---- 자동 시작 ----

    @Test fun taredGoesToWaitingOnlyWhenAutoStartEnabled() {
        val on = Rig()
        on.feed(Tared(0))
        assertEquals(WaitingForPour, on.state.phase)

        val off = Rig(settings = BrewSettings(autoStartEnabled = false))
        off.feed(Tared(0))
        assertEquals(Idle, off.state.phase)
        assertTrue(off.all.isEmpty())
    }

    @Test fun jitterAfterTareDoesNotStartThenTwoConsecutiveSamplesStartOnce() {
        val r = Rig()
        r.feed(Tared(0))
        repeat(30) { i ->
            r.feed(reading(ms(100L * (i + 1)), if (i % 2 == 0) 0.6 else -0.6))
            assertEquals(WaitingForPour, r.state.phase)
        }
        // 처음 넘은 3.1 s부터 확인 구간 500 ms 동안 계속 넘어야 시작(F-B4-1)
        for (k in 0..4) r.feed(reading(ms(3100 + 100L * k), 1.2 + 0.2 * k))
        assertEquals(WaitingForPour, r.state.phase)
        val fx = r.feed(reading(ms(3600), 2.2))
        assertEquals(Running, r.state.phase)
        assertEquals(ClearSamples, fx[0])
        assertEquals(SendTimer(TimerAction.Start), fx[1])
        assertEquals(PhaseCause.AutoStart, fx.phaseChange().cause)
        assertEquals(ms(3100), r.state.startedAtNanos)
        for (k in 1..20) r.feed(reading(ms(3600 + 100L * k), 2.2 + k))
        assertEquals(1, r.count { it == SendTimer(TimerAction.Start) })
    }

    @Test fun threshold2DoesNotStartAt1_4() {
        val r = Rig(settings = BrewSettings(autoStartThresholdGrams = 2.0))
        r.feed(Tared(0))
        r.feed(reading(ms(100), 0.0))   // 무장(FIX-1)
        r.feedAll(reading(ms(200), 1.2), reading(ms(300), 1.4), reading(ms(400), 1.4), reading(ms(500), 1.9), reading(ms(600), 1.9))
        assertEquals(WaitingForPour, r.state.phase)
        // 다시 영점 → 무장 → 임계값 바로 위 2회로 시작. 무장 판독을 빼면 2.1은 무장 전이라 시작하지 않는다
        r.feed(Tared(ms(700)))
        r.feed(reading(ms(800), 0.0))
        for (k in 0..4) r.feed(reading(ms(900 + 100L * k), 2.1))
        assertEquals(WaitingForPour, r.state.phase)
        r.feed(reading(ms(1400), 2.1))   // 확인 구간 500 ms
        assertEquals(Running, r.state.phase)
    }

    @Test fun waitingCancelAndAutoStartOffGoIdle() {
        val r = Rig()
        r.feed(Tared(0))
        assertEquals(PhaseCause.User, r.feed(CancelWaiting(ms(10))).phaseChange().cause)
        assertEquals(Idle, r.state.phase)

        r.feed(Tared(ms(20)))
        assertEquals(WaitingForPour, r.state.phase)
        r.feed(SettingsChanged(BrewSettings(autoStartEnabled = false), ms(30)))
        assertEquals(Idle, r.state.phase)
    }

    @Test fun settingsChangedInIdleIsUsedByLaterTareAndThreshold() {
        val r = Rig()
        val strict = BrewSettings(autoStartThresholdGrams = 2.0)
        r.feed(SettingsChanged(strict, 0))
        assertEquals(strict, r.state.settings)
        r.feed(Tared(ms(10)))
        r.feed(reading(ms(50), 0.0))    // 무장(FIX-1)
        r.feedAll(reading(ms(100), 1.4), reading(ms(200), 1.4))   // 기본 1.0이면 여기서 시작했다
        assertEquals(WaitingForPour, r.state.phase)
        r.feed(Tared(ms(300)))
        r.feed(reading(ms(400), 0.0))
        for (k in 0..4) r.feed(reading(ms(500 + 100L * k), 2.1))
        assertEquals(WaitingForPour, r.state.phase)
        r.feed(reading(ms(1000), 2.1))   // 확인 구간 500 ms
        assertEquals(Running, r.state.phase)
    }

    @Test fun settingsChangedInRunningKeepsPhase() {
        val r = Rig()
        r.start()
        val s = BrewSettings(autoStartEnabled = false, autoStartThresholdGrams = 0.5)
        assertTrue(r.feed(SettingsChanged(s, ms(100))).isEmpty())
        assertEquals(s, r.state.settings)
        assertEquals(Running, r.state.phase)
    }

    @Test fun taredWhileRunningChangesNothing() {
        val r = Rig()
        r.feed(SelectRecipe(recipe(50.0 to 30, 250.0 to 120), 0))
        r.start()
        r.feed(reading(sec(20.0), 40.0, 2.0))
        val before = r.state
        assertTrue(r.feed(Tared(sec(21.0))).isEmpty())
        assertEquals(before.phase, r.state.phase)
        assertEquals(before.weightGrams, r.state.weightGrams)
        assertEquals(before.guide, r.state.guide)
        r.feed(reading(sec(21.1), 41.0, 2.0))
        assertEquals(41.0, r.state.weightGrams!!, 0.0)   // 오프셋 보정 없음
    }

    // ---- 타이머·샘플 축 ----

    @Test fun pauseTenSecondsKeepsOffsetsContinuousAndNoGaps() {
        val r = Rig()
        r.start()
        var lastBeforePause = -1
        var firstAfterResume = -1
        for (k in 1..300) {
            val at = ms(100L * k)
            if (k == 101) r.feed(UserTimer(TimerAction.Pause, ms(10_050)))
            if (k == 201) r.feed(UserTimer(TimerAction.Start, ms(20_050)))
            val fx = r.feed(reading(at, k * 0.5, 3.0))
            fx.filterIsInstance<AppendSample>().forEach {
                assertEquals((r.state.timer.elapsedNanos(at) / 1_000_000).toInt(), it.sample.offsetMs)
                if (k <= 100) lastBeforePause = it.sample.offsetMs
                if (k > 200 && firstAfterResume < 0) firstAfterResume = it.sample.offsetMs
            }
        }
        assertEquals(200, r.samples.size)
        assertTrue(r.samples.gaps().isEmpty())
        val step = firstAfterResume - lastBeforePause
        assertTrue("step $step", step in 1..200)
        assertTrue(r.phaseChanges().all { it.cause == PhaseCause.User })
    }

    // ---- PhaseChanged 출처 ----

    @Test fun phaseChangeCauses() {
        val r = Rig()
        assertEquals(PhaseCause.User, r.start().phaseChange().cause)
        assertEquals(PhaseCause.User, r.feed(UserTimer(TimerAction.Pause, ms(100))).phaseChange().cause)
        assertEquals(PhaseCause.ScaleButton, r.feed(ScaleTimerChanged(ScaleTimerState.Running, ms(200))).phaseChange().cause)
        assertEquals(PhaseCause.ScaleButton, r.feed(ScaleTimerChanged(ScaleTimerState.Paused, ms(300))).phaseChange().cause)
        assertEquals(PhaseCause.User, r.feed(Finish(ms(400))).phaseChange().cause)
        assertEquals(PhaseCause.User, r.feed(NewBrew(ms(500))).phaseChange().cause)

        r.start(ms(600))
        assertEquals(PhaseCause.CapReached, r.feed(Finish(ms(700), capReached = true)).phaseChange().cause)
        r.feed(NewBrew(ms(800)))
        r.start(ms(900))
        assertEquals(PhaseCause.User, r.feed(Discard(ms(1000))).phaseChange().cause)
    }

    @Test fun inputsThatKeepPhaseEmitNoPhaseChanged() {
        val r = Rig(settings = BrewSettings(autoStartEnabled = false))
        r.feedAll(
            Finish(0), Discard(0), NewBrew(0), CancelWaiting(0), Tared(0),
            UserTimer(TimerAction.Pause, 0), UserTimer(TimerAction.Reset, 0),
            ScaleTimerChanged(ScaleTimerState.Paused, 0), ScaleTimerChanged(ScaleTimerState.Reset, 0),
            Tick(ms(1000)), reading(ms(1100), 3.0), ConnectionChanged(false, ms(1200)),
        )
        assertEquals(Idle, r.state.phase)
        assertTrue(r.phaseChanges().isEmpty())
        r.start(ms(2000))
        r.feedAll(UserTimer(TimerAction.Start, ms(2100)), UserTimer(TimerAction.Reset, ms(2200)), NewBrew(ms(2300)), Tared(ms(2400)))
        assertEquals(1, r.phaseChanges().size)
    }

    // ---- 타이머 상한 ----

    private fun capByTicks(recipe: Recipe?) {
        val r = Rig()
        r.feed(SelectRecipe(recipe, 0))
        r.start()
        for (s in 1..3599) {
            r.feed(Tick(sec(s.toDouble())))
            assertEquals(Running, r.state.phase)
        }
        val fx = r.feed(Tick(sec(3600.0)))
        assertEquals(Finished, r.state.phase)
        assertEquals(3_599_900L, fx.filterIsInstance<Completed>().single().summary.durationMs)
        assertTrue(fx.contains(SendTimer(TimerAction.Pause)))
        assertEquals(PhaseCause.CapReached, fx.phaseChange().cause)
        assertEquals(3_599_900_000_000L, r.state.timer.elapsedNanos(sec(5000.0)))
    }

    @Test fun capReachedByTicksAloneWithoutRecipe() = capByTicks(null)

    @Test fun capReachedByTicksAloneWithRecipe() = capByTicks(recipe(50.0 to 30, 250.0 to 120))

    @Test fun readingAtCapIsNotAppended() {
        val r = Rig()
        r.start()
        assertEquals(1, r.feed(reading(3_599_800_000_000, 300.0)).filterIsInstance<AppendSample>().size)
        val fx = r.feed(reading(3_599_900_000_000, 300.0))
        assertTrue(fx.none { it is AppendSample })
        assertEquals(Finished, r.state.phase)
        assertEquals(PhaseCause.CapReached, fx.phaseChange().cause)
        assertEquals(3_599_900_000_000L, r.state.timer.elapsedNanos(4_000_000_000_000_000))
    }

    @Test fun pausedLongerThanCapDoesNotFinish() {
        val r = Rig()
        r.start()
        r.feed(UserTimer(TimerAction.Pause, sec(1.0)))
        for (s in 60..3700 step 60) r.feed(Tick(sec(s.toDouble())))
        assertEquals(Paused, r.state.phase)
        assertEquals(sec(1.0), r.state.timer.elapsedNanos(sec(3700.0)))
    }

    // ---- 유량 ----

    @Test fun sharpWeightDropClearsFlowWindowKeepingCurrentScaleFlow() {
        val r = Rig()
        r.start()
        for (k in 1..20) r.feed(reading(ms(100L * k), 10.0 + 0.3 * k, 3.0))
        r.feed(reading(ms(2100), 16.0 - 200.0, 0.5))   // 컵 들어 올림: 무게 −200 g(2 g 넘게 급감) → 창을 비우고 이 샘플의 저울 유량만(O-B8-1)
        assertEquals(0.5, r.state.flowGramsPerSec, 1e-9)

        for (k in 22..31) r.feed(reading(ms(100L * k), -184.0, 0.0))
        r.feed(reading(ms(3200), -184.0, -1.0))   // 방어 입력: 평균 −0.1 → 0
        assertEquals(0.0, r.state.flowGramsPerSec, 0.0)
    }

    @Test fun constantFlowAveragesToScaleFlowEvenWithGap() {
        val r = Rig()
        r.start()
        for (k in 0..10) r.feed(reading(ms(100L * k), 3.0 * k / 10, 3.0))
        assertEquals(3.0, r.state.flowGramsPerSec, 1e-9)

        val g = Rig()
        g.start()
        for (k in 1..15) if (k !in 6..7) g.feed(reading(ms(100L * k), 3.0 * k / 10, 3.0))   // 500→800 ms 공백 300 ms
        assertEquals(3.0, g.state.flowGramsPerSec, 1e-9)
    }

    // ---- stale ----

    @Test fun noDataAfterThreeSecondsWithoutReading() {
        assertEquals(3_000_000_000L, BrewConfig().noDataTimeoutNanos)
        val r = Rig()
        r.feed(ConnectionChanged(true, 0))
        r.feed(reading(ms(1000), 5.0))
        r.feedAll(Tick(ms(2000)), Tick(ms(3000)), Tick(ms(3500)))
        assertNull(r.state.stale)
        r.feed(Tick(ms(4500)))
        assertEquals(StaleReason.NoData, r.state.stale)
        assertEquals(ms(1000), r.state.staleSinceNanos)
        r.feed(reading(ms(4600), 5.0))
        assertNull(r.state.stale)
        assertNull(r.state.staleSinceNanos)
    }

    @Test fun powerButtonDisconnectSkipsNoData() {
        val r = Rig()
        r.feed(ConnectionChanged(true, 0))
        r.feed(reading(ms(1000), 5.0))
        r.feedAll(Tick(ms(2000)), Tick(ms(3000)))
        assertNull(r.state.stale)
        r.feed(ConnectionChanged(false, ms(3200)))   // 마지막 Reading 뒤 2.2 s
        assertEquals(StaleReason.Disconnected, r.state.stale)
        assertEquals(ms(3200), r.state.staleSinceNanos)
        for (t in 4..20) {
            r.feed(Tick(sec(t.toDouble())))
            assertEquals(StaleReason.Disconnected, r.state.stale)
        }
    }

    @Test fun disconnectWhileRunningAddsOneFlaggedSampleAndTimerKeepsGoing() {
        val r = Rig()
        r.feed(ConnectionChanged(true, 0))
        r.start()
        for (k in 1..10) r.feed(reading(ms(100L * k), 20.0, 1.0))
        val fx = r.feed(ConnectionChanged(false, ms(1050)))
        val s = fx.filterIsInstance<AppendSample>().single().sample
        assertEquals(FLAG_DISCONNECTED, s.flags)
        assertEquals(20f, s.weightGrams, 0f)
        assertEquals(1050, s.offsetMs)
        assertEquals(StaleReason.Disconnected, r.state.stale)
        assertTrue(r.feed(ConnectionChanged(false, ms(1100))).none { it is AppendSample })

        r.feed(Tick(ms(5000)))
        assertEquals(Running, r.state.phase)
        assertEquals(ms(5000), r.state.timer.elapsedNanos(ms(5000)))
        r.feed(ConnectionChanged(true, ms(6000)))
        r.feed(Tick(ms(7000)))
        assertEquals(StaleReason.Disconnected, r.state.stale)
        r.feed(reading(ms(7100), 21.0, 1.0))
        assertNull(r.state.stale)
        assertEquals(1, r.count { it is AppendSample && it.sample.flags == FLAG_DISCONNECTED })
        assertEquals(listOf(1000..7100), r.samples.gaps())
    }

    @Test fun noDataReferenceIsReconnectTime() {
        val r = Rig()
        r.feed(ConnectionChanged(true, 0))
        r.feed(reading(ms(1000), 5.0))
        r.feed(ConnectionChanged(false, ms(2000)))
        r.feed(ConnectionChanged(true, ms(11_000)))   // 마지막 Reading 10 s 뒤
        r.feedAll(Tick(ms(12_000)), Tick(ms(13_000)), Tick(ms(13_500)))
        assertEquals(StaleReason.Disconnected, r.state.stale)
        r.feed(Tick(ms(14_500)))
        assertEquals(StaleReason.NoData, r.state.stale)
        assertEquals(ms(11_000), r.state.staleSinceNanos)
    }

    @Test fun firstConnectionWithoutReadingWaitsThreeSeconds() {
        val r = Rig()
        r.feed(ConnectionChanged(true, ms(500)))
        r.feedAll(Tick(ms(1500)), Tick(ms(2500)), Tick(ms(3500)))
        assertNull(r.state.stale)
        r.feed(Tick(ms(3600)))
        assertEquals(StaleReason.NoData, r.state.stale)
        assertEquals(ms(500), r.state.staleSinceNanos)
    }

    @Test fun overloadSampleIsNaNFlaggedAndExcludedFromFlow() {
        val r = Rig()
        r.start()
        for (k in 1..10) r.feed(reading(ms(100L * k), 100.0, 3.0))
        val fx = r.feed(reading(ms(1100), null, 50.0, overload = true))
        val s = fx.filterIsInstance<AppendSample>().single().sample
        assertTrue(s.weightGrams.isNaN())
        assertTrue(s.flags and FLAG_OVERLOAD != 0)
        assertEquals(100.0, r.state.weightGrams!!, 0.0)
        assertEquals(StaleReason.Overload, r.state.stale)
        assertEquals(ms(1100), r.state.staleSinceNanos)
        assertEquals(3.0, r.state.flowGramsPerSec, 1e-9)
        r.feed(reading(ms(1200), 101.0, 3.0))
        assertNull(r.state.stale)
    }

    // ---- 저울 버튼 타이머 (원문 경로, 단위 테스트만) ----

    @Test fun scaleTimerRunningAndPausedFromScale() {
        val r = Rig()
        r.start()
        assertTrue(r.feed(ScaleTimerChanged(ScaleTimerState.Running, ms(100))).isEmpty())
        val fx = r.feed(ScaleTimerChanged(ScaleTimerState.Paused, ms(200)))
        assertEquals(Paused, r.state.phase)
        assertTrue(fx.none { it is SendTimer })
        val resume = r.feed(ScaleTimerChanged(ScaleTimerState.Running, ms(300)))
        assertEquals(Running, r.state.phase)
        assertTrue(resume.none { it is SendTimer || it is ClearSamples })

        val idle = Rig()
        val start = idle.feed(ScaleTimerChanged(ScaleTimerState.Running, ms(100)))
        assertEquals(Running, idle.state.phase)
        assertEquals(listOf(ClearSamples, PhaseChanged(Idle, Running, PhaseCause.ScaleButton)), start)
    }

    @Test fun scaleTimerResetGoesToSummaryWhenSamplesExist() {
        val r = Rig()
        r.start()
        r.feed(reading(ms(100), 3.0, 1.0))
        val fx = r.feed(ScaleTimerChanged(ScaleTimerState.Reset, ms(200)))
        assertEquals(Finished, r.state.phase)
        assertEquals(1, fx.filterIsInstance<Completed>().size)
        assertTrue(fx.none { it is SendTimer })
        assertEquals(PhaseCause.ScaleButton, fx.phaseChange().cause)
    }

    @Test fun scaleTimerResetWithoutSamplesDiscards() {
        val r = Rig()
        r.start()
        val fx = r.feed(ScaleTimerChanged(ScaleTimerState.Reset, ms(200)))
        assertEquals(Idle, r.state.phase)
        assertEquals(listOf(Discarded, PhaseChanged(Running, Idle, PhaseCause.ScaleButton)), fx)
    }

    @Test fun userResetFromPaused() {
        val r = Rig()
        r.start()
        r.feed(reading(ms(100), 3.0, 1.0))
        r.feed(UserTimer(TimerAction.Pause, ms(200)))
        val fx = r.feed(UserTimer(TimerAction.Reset, ms(300)))
        assertEquals(Idle, r.state.phase)
        assertEquals(TimerSnapshot.Zero, r.state.timer)
        assertEquals(
            listOf(Discarded, ClearSamples, SendTimer(TimerAction.Reset), PhaseChanged(Paused, Idle, PhaseCause.User)),
            fx,
        )
    }

    @Test fun userFinishFromPausedDoesNotSendPause() {
        val r = Rig()
        r.start()
        r.feed(UserTimer(TimerAction.Pause, ms(200)))
        val fx = r.feed(Finish(ms(300)))
        assertEquals(200L, fx.filterIsInstance<Completed>().single().summary.durationMs)
        assertTrue(fx.none { it is SendTimer })
    }

    // ---- 레시피 단계·자동 완료 ----

    @Test fun guideIndexRemainingAndPace() {
        val rec = Recipe(0, "t", 15.0, 250.0, listOf(RecipeStep("bloom", 50.0, 30), RecipeStep("main", 250.0, 120)))
        val r = Rig()
        r.feed(SelectRecipe(rec, 0))
        r.start()
        r.feed(reading(sec(60.0), 150.0, 3.0))
        val g = r.state.guide!!
        assertEquals(1, g.index)
        assertEquals(100.0, g.remainingGrams, 1e-9)
        assertEquals(-15.0, g.paceDeltaSec, 1e-9)
        assertFalse(g.lastStepReached)
    }

    /** 10 Hz로 weightAt(경과 초)를 넣고 처음 Finished가 된 경과 초(없으면 null)와 그 효과를 돌려준다. */
    private fun runUntilFinished(recipe: Recipe?, untilSec: Double, weightAt: (Double) -> Double): Pair<Double?, List<BrewEffect>> {
        val r = Rig()
        r.feed(SelectRecipe(recipe, 0))
        r.start()
        var k = 1L
        while (k / 10.0 <= untilSec) {
            val at = ms(100L * k)
            val fx = r.feed(reading(at, weightAt(k / 10.0), 0.0))
            if (k % 10 == 0L) r.feed(Tick(at))
            if (r.state.phase == Finished) return k / 10.0 to fx
            k++
        }
        return null to emptyList()
    }

    @Test fun autoFinishAfterLastStepStable() {
        val rec = recipe(50.0 to 30, 250.0 to 120)
        val (t, fx) = runUntilFinished(rec, 200.0) { s -> if (s <= 120) rec.plannedGramsAt(s) else 250.0 + if ((s * 10).toLong() % 2 == 0L) 0.3 else 0.0 }
        assertNotNull(t)
        assertTrue("finished at $t", t!! >= 125.0)
        assertEquals(1, fx.filterIsInstance<Completed>().size)
        assertTrue(fx.contains(SendTimer(TimerAction.Pause)))
        assertEquals(PhaseCause.AutoFinish, fx.phaseChange().cause)
    }

    @Test fun noAutoFinishWhenVariationAboveLimit() {
        val rec = recipe(50.0 to 30, 250.0 to 120)
        val (t, _) = runUntilFinished(rec, 200.0) { s -> if (s <= 120) rec.plannedGramsAt(s) else 250.0 + if ((s * 10).toLong() % 2 == 0L) 0.8 else 0.0 }
        assertNull(t)
    }

    @Test fun defaultRecipesDoNotFinishBeforeFinishTimePlusFive() {
        val recipes = listOf(
            recipe(240.0 to 10, finishAtSec = 120),                                                // 하리오 스위치 기본
            recipe(50.0 to 5, 120.0 to 50, 180.0 to 95, 240.0 to 140, 300.0 to 185, finishAtSec = 210),   // 테츠 카스야 기본
            recipe(60.0 to 10, 120.0 to 40, 280.0 to 85, finishAtSec = 180),                       // 악마의 레시피
            recipe(60.0 to 10, 140.0 to 50, 220.0 to 80, 300.0 to 110, finishAtSec = 165),         // 6888
        )
        for (rec in recipes) {
            val (t, _) = runUntilFinished(rec, rec.finishSec + 20.0) { s -> rec.plannedGramsAt(s) }
            assertNotNull("${rec.finishSec} never finished", t)
            assertTrue("finishAt ${rec.finishSec}: finished at $t", t!! >= rec.finishSec + 5.0)
        }
    }

    /** 레시피 [(250 g, 120 s)]로 0~121.0 s를 10 Hz로 넣는다(120 s부터 250 g). */
    private fun rigStableUntil121(config: BrewConfig = BrewConfig()): Rig {
        val rec = recipe(250.0 to 120)
        val r = Rig(config)
        r.feed(SelectRecipe(rec, 0))
        r.start()
        for (k in 1L..1210L) r.feed(reading(ms(100L * k), rec.plannedGramsAt(k / 10.0), 0.0))
        return r
    }

    @Test fun noAutoFinishAfterDisconnectPastFinishTime() {
        val r = rigStableUntil121()
        r.feed(ConnectionChanged(false, ms(121_050)))
        for (k in 1211L..2000L) r.feed(Tick(ms(100L * k)))
        assertEquals(Running, r.state.phase)
        // 재연결 뒤 정상 샘플 5 s가 새로 쌓여야 끝난다(끊김 전 샘플은 창에서 버림)
        r.feed(ConnectionChanged(true, ms(200_000)))
        var finishedAt: Long? = null
        for (k in 2001L..2200L) {
            r.feed(reading(ms(100L * k), 250.0, 0.0))
            if (r.state.phase == Finished) { finishedAt = k; break }
        }
        assertNotNull(finishedAt)
        assertTrue("finished at $finishedAt", finishedAt!! >= 2051L)
        assertEquals(PhaseCause.AutoFinish, r.phaseChanges().last().cause)
    }

    @Test fun noAutoFinishOnTicksOnlyPastFinishTime() {
        // NoData 판정 전(3 s 이내) 구간도 막히는지 보려고 NoData 시간을 늘린 설정도 확인한다
        for (cfg in listOf(BrewConfig(), BrewConfig(noDataTimeoutNanos = 600_000_000_000))) {
            val r = rigStableUntil121(cfg)
            for (k in 1211L..2000L) r.feed(Tick(ms(100L * k)))
            assertEquals(Running, r.state.phase)
        }
    }

    @Test fun noRecipeNeverAutoFinishesOnStableWeight() {
        val (t, _) = runUntilFinished(null, 70.0) { s -> minOf(s, 10.0) * 20.0 }
        assertNull(t)
    }
}
