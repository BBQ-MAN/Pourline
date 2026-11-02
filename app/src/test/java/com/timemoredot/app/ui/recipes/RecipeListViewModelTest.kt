package com.timemoredot.app.ui.recipes

import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.brew.BrewPhase
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val DEVICE = ScaleDevice("AA:BB:CC:DD:EE:FF", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)

/** architecture.md 9.6 RecipeListViewModel. FakeRecipeRepository, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModelTest {
    private val store = VmStore()
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private inner class Rig(val scope: TestScope, initial: List<com.timemoredot.brew.Recipe>) {
        val fake = FakeScaleClient(ConnectionState.Connected(DEVICE)).also { it.setStatus(ScaleStatus(WeightUnit.Gram, false, 80, null)) }
        val repo = FakeRecipeRepository(initial)
        val clock = MonotonicClock { scope.testScheduler.currentTime * 1_000_000 }
        val controller = BrewSessionController(fake, FakeBrewRepository(), FakeAppPrefs(), clock, { 0L }, scope.backgroundScope)
        val vm = store.create { RecipeListViewModel(repo, controller) }
        val events = mutableListOf<RecipeListEvent>()
        init {
            vm.uiState.launchIn(scope.backgroundScope)
            vm.events.onEach { events += it }.launchIn(scope.backgroundScope)
            scope.runCurrent()
        }
        suspend fun reading(g: Double) { fake.emit(ScaleReading(g, 0.0, 0, false, WeightUnit.Gram, clock.nowNanos())); scope.runCurrent() }
        suspend fun toRunning() { reading(0.0); controller.tare(); scope.runCurrent(); controller.startTimer(); scope.runCurrent() }
    }

    @Test fun `레시피가 없으면 Empty, 있으면 Loaded`() = runTest {
        assertEquals(ListLoad.Empty, Rig(this, emptyList()).vm.uiState.value.recipes)
        assertEquals(ListLoad.Loaded(RFx.all.sortedBy { it.name }), Rig(this, RFx.all).vm.uiState.value.recipes)
    }

    @Test fun `저장소 Flow 예외면 Error이고 onRetry로 다시 불러온다`() = runTest {
        val r = Rig(this, RFx.all)
        r.repo.failNext("observeAll", RuntimeException("io"))
        r.vm.onRetry(); runCurrent()   // 다시 구독하는 첫 방출에서 예외
        assertEquals(ListLoad.Error, r.vm.uiState.value.recipes)
        r.vm.onRetry(); runCurrent()
        assertEquals(ListLoad.Loaded(RFx.all.sortedBy { it.name }), r.vm.uiState.value.recipes)
    }

    @Test fun `Idle과 WaitingForPour이면 canStartBrew, 추출 선택이 OpenBrew를 낸다`() = runTest {
        val r = Rig(this, RFx.all)
        assertEquals(BrewPhase.Idle, r.controller.state.value.phase)
        assertTrue(r.vm.uiState.value.canStartBrew)
        r.reading(0.0); r.controller.tare(); runCurrent()
        assertEquals(BrewPhase.WaitingForPour, r.controller.state.value.phase)
        assertTrue(r.vm.uiState.value.canStartBrew)
        r.vm.onBrewClick(RFx.hario.id); runCurrent()
        assertEquals(RFx.hario, r.controller.state.value.recipe)
        assertEquals(listOf<RecipeListEvent>(RecipeListEvent.OpenBrew), r.events)
    }

    @Test fun `Running Paused Finished에서는 canStartBrew false이고 선택도 이동도 없다`() = runTest {
        val r = Rig(this, RFx.all)
        r.toRunning()
        for (step in 0..2) {
            when (step) { 1 -> { r.controller.pauseTimer(); runCurrent() }; 2 -> { r.controller.finish(); runCurrent() } }
            val phase = r.controller.state.value.phase
            assertTrue(phase.toString(), phase == listOf(BrewPhase.Running, BrewPhase.Paused, BrewPhase.Finished)[step])
            assertFalse(phase.toString(), r.vm.uiState.value.canStartBrew)
            r.vm.onBrewClick(RFx.v60.id); runCurrent()
            assertEquals(null, r.controller.state.value.recipe)
            assertTrue(r.events.isEmpty())
        }
    }
}
