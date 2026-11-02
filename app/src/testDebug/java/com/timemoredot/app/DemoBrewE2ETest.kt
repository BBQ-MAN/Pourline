package com.timemoredot.app

import com.timemoredot.app.data.DefaultRecipes
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.ble.demo.DemoScale
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.PhaseCause
import com.timemoredot.brew.ValveEvent
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.demo.DemoScenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 10.1.6 엔진 종단: 실제 BleScaleClient + 데모 저울 + BrewSessionController. JVM, 가상 시간. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoBrewE2ETest {
    private class Rig(val scope: TestScope, scenario: DemoScenario) {
        val clock = MonotonicClock { (scope.testScheduler.currentTime + 1_000) * 1_000_000 }
        val scale = DemoScale.clientForTest(scope.backgroundScope, clock)
        val repo = FakeBrewRepository()
        val c = BrewSessionController(scale, repo, FakeAppPrefs(), clock, { 0L }, scope.backgroundScope)
        val events = mutableListOf<BrewEvent>()
        val cueElapsedSec = mutableListOf<Double>()

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
                c.events.collect {
                    events += it
                    if (it is BrewEvent.ValveCue) cueElapsedSec += c.state.value.timer.elapsedNanos(clock.nowNanos()) / 1e9
                }
            }
            scale.connect(scenario.address)
        }

        suspend fun step(ms: Long, each: () -> Unit = {}) {
            var left = ms
            while (left > 0) { scope.testScheduler.advanceTimeBy(100); scope.testScheduler.runCurrent(); each(); left -= 100 }
        }

        suspend fun untilConnected() {
            var n = 0
            while (scale.connection.value !is ConnectionState.Connected && n++ < 100) step(100)
            assertTrue(scale.connection.value is ConnectionState.Connected)
        }
    }

    @Test fun `하리오 스위치 영점 자동 시작 밸브 큐 한 번 그리고 완료 저장`() = runTest {
        val r = Rig(this, DemoScenario.BrewHario)
        r.untilConnected()
        r.step(500)
        r.c.selectRecipe(DefaultRecipes.all.first { it.name.startsWith("하리오") })
        r.c.tare()
        r.step(1_000)
        assertTrue(r.events.contains(BrewEvent.TareSucceeded))
        r.step(200_000)
        val changes = r.events.filterIsInstance<BrewEvent.PhaseChanged>()
        assertEquals(1, changes.count { it.from == BrewPhase.WaitingForPour && it.to == BrewPhase.Running && it.cause == PhaseCause.AutoStart })
        val cues = r.events.filter { it == BrewEvent.ValveCue(ValveEvent(120, true)) }
        assertEquals(1, cues.size)
        assertEquals(1, r.cueElapsedSec.count { it >= 120.0 && it < 121.0 })
        r.c.finish()
        r.step(500)
        assertTrue(r.repo.calls.contains("saveDraft"))
        assertTrue(r.events.any { it is BrewEvent.Finished })
    }

    @Test fun `V60 평평한 구간 페이스 편차 2초 이하, 단계는 0에서 2로 바로`() = runTest {
        val r = Rig(this, DemoScenario.BrewV60)
        r.untilConnected()
        r.step(500)
        r.c.selectRecipe(DefaultRecipes.all.first { it.name == "V60 기본" })
        r.c.tare()
        r.step(1_000)
        val indices = mutableListOf<Int>()
        var checked = 0
        r.step(110_000) {
            val s = r.c.state.value
            val g = s.guide
            if (s.phase == BrewPhase.Running && g != null) {
                if (indices.lastOrNull() != g.index) indices += g.index
                val el = s.timer.elapsedNanos(r.clock.nowNanos()) / 1e9
                if (el in 11.0..40.0) { checked++; assertTrue("el=$el delta=${g.paceDeltaSec}", kotlin.math.abs(g.paceDeltaSec) <= 2.0) }
            }
        }
        assertTrue("checked=$checked", checked > 100)
        assertEquals(listOf(0, 2), indices.take(2))
    }
}
