package com.timemoredot.app.ui.summary

import app.cash.turbine.test
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val DEVICE = ScaleDevice("AA:BB:CC:DD:EE:FF", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)

/** architecture.md 9.5. FakeBrewRepository·가짜 prefs, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {
    private val store = VmStore()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private inner class Rig(val scope: TestScope, recipe: Recipe?) {
        val fake = FakeScaleClient(ConnectionState.Connected(DEVICE)).also { it.setStatus(ScaleStatus(WeightUnit.Gram, false, 80, null)) }
        val repo = FakeBrewRepository()
        val clock = MonotonicClock { scope.testScheduler.currentTime * 1_000_000 }
        val controller = BrewSessionController(fake, repo, FakeAppPrefs(), clock, { 0L }, scope.backgroundScope)
        var id = 0L

        /** 실시간 추출 하나를 끝내 controller를 Finished로 만든다(초안은 확정 저장됨). */
        suspend fun finishLive() {
            fake.emit(ScaleReading(0.0, 0.0, 0, false, WeightUnit.Gram, clock.nowNanos())); scope.runCurrent()
            controller.selectRecipe(recipeArg); controller.tare(); scope.runCurrent()
            controller.startTimer(); scope.runCurrent()
            fake.emit(ScaleReading(250.0, 0.0, 0, false, WeightUnit.Gram, clock.nowNanos())); scope.runCurrent()
            controller.finish(); scope.runCurrent()
            id = controller.draftId.value!!
        }
        val recipeArg = recipe
        fun vm(recovered: Boolean = false, sessionId: Long = id): SummaryViewModel =
            store.create { SummaryViewModel(repo, controller, sessionId, recovered) }.also { scope.runCurrent() }
    }

    private val v60 = Recipe(1, "V60 기본", 15.0, 250.0, listOf(com.timemoredot.brew.RecipeStep("붓기", 250.0, 90)))

    private suspend fun TestScope.rig(recipe: Recipe? = v60) = Rig(this, recipe).also { it.finishLive() }

    @Test fun `레시피 dose 15_0이면 doseText를 미리 채우고 요약을 불러온다`() = runTest {
        val r = rig()
        val vm = r.vm()
        assertEquals("15.0", vm.uiState.value.doseText)
        assertEquals(r.id, vm.uiState.value.summary?.id)
        assertEquals(SummaryState.Ready, vm.uiState.value.state)
    }

    @Test fun `요약을 읽지 못하면 loadFailed이고 다시 시도로 불러온다`() = runTest {
        val r = rig()
        r.repo.failNext("summary", RuntimeException("io"))
        val vm = r.vm()
        assertTrue(vm.uiState.value.loadFailed)
        vm.onRetryLoad(); runCurrent()
        assertEquals(false, vm.uiState.value.loadFailed)
        assertEquals(r.id, vm.uiState.value.summary?.id)
    }

    @Test fun `레시피가 없으면 doseText는 빈 칸`() = runTest {
        assertEquals("", rig(null).vm().uiState.value.doseText)
    }

    @Test fun `범위 밖·숫자 아님은 doseError이고 저장하지 않는다`() = runTest {
        val r = rig()
        val vm = r.vm()
        for (bad in listOf("0.05", "100.1", "abc")) {
            vm.onDoseChange(bad); vm.onSave(); runCurrent()
            assertTrue(bad, vm.uiState.value.doseError)
            assertEquals(SummaryState.Ready, vm.uiState.value.state)
        }
        assertTrue(r.repo.finishCalls.isEmpty())
        vm.onDoseChange("18"); assertEquals(false, vm.uiState.value.doseError)
    }

    @Test fun `빈 칸은 저장된다(doseGrams null)`() = runTest {
        val r = rig()
        val vm = r.vm()
        vm.onDoseChange(""); vm.onSave(); runCurrent()
        assertEquals(listOf(Triple(r.id, null, SessionStatus.Completed)), r.repo.finishCalls)
    }

    @Test fun `실시간 저장은 Completed 뒤 newBrew와 Saved(id)`() = runTest {
        val r = rig()
        val vm = r.vm()
        vm.onNoteChange("산미 좋음")
        vm.events.test {
            vm.onSave(); runCurrent()
            assertEquals(SummaryEvent.Saved(r.id), awaitItem())
        }
        assertEquals(listOf(Triple(r.id, 15.0, SessionStatus.Completed)), r.repo.finishCalls)
        assertEquals("산미 좋음", r.repo.summary(r.id)?.note)
        assertEquals(BrewPhase.Idle, r.controller.state.value.phase)
        assertNull(r.controller.draftId.value)
    }

    @Test fun `복구 저장은 Interrupted이고 newBrew를 부르지 않는다`() = runTest {
        val r = rig()
        val vm = r.vm(recovered = true)
        vm.events.test {
            vm.onSave(); runCurrent()
            assertEquals(SummaryEvent.Saved(r.id), awaitItem())
        }
        assertEquals(SessionStatus.Interrupted, r.repo.finishCalls.single().third)
        assertEquals(BrewPhase.Finished, r.controller.state.value.phase)
    }

    @Test fun `저장 예외면 SaveFailed이고 입력은 그대로, 다시 저장하면 성공`() = runTest {
        val r = rig()
        val vm = r.vm()
        vm.onDoseChange("16.5"); vm.onNoteChange("메모")
        r.repo.failNext("finish", java.io.IOException("disk"))
        vm.events.test {
            vm.onSave(); runCurrent()
            assertEquals(SummaryState.SaveFailed, vm.uiState.value.state)
            assertEquals("16.5", vm.uiState.value.doseText); assertEquals("메모", vm.uiState.value.note)
            assertEquals(BrewPhase.Finished, r.controller.state.value.phase)
            expectNoEvents()
            vm.onSave(); runCurrent()
            assertEquals(SummaryEvent.Saved(r.id), awaitItem())
        }
    }

    @Test fun `메모는 200자까지`() = runTest {
        val vm = rig().vm()
        vm.onNoteChange("가".repeat(250))
        assertEquals(200, vm.uiState.value.note.length)
    }

    @Test fun `버리기 확정은 deleteSession, 실시간이면 newBrew, Discarded`() = runTest {
        val r = rig()
        val vm = r.vm()
        vm.events.test {
            vm.onDiscardConfirmed(); runCurrent()
            assertEquals(SummaryEvent.Discarded, awaitItem())
        }
        assertEquals(listOf(r.id), r.repo.deletedIds)
        assertEquals(BrewPhase.Idle, r.controller.state.value.phase)
    }

    @Test fun `복구 요약을 버려도 newBrew를 부르지 않는다`() = runTest {
        val r = rig()
        val vm = r.vm(recovered = true)
        vm.events.test { vm.onDiscardConfirmed(); runCurrent(); assertEquals(SummaryEvent.Discarded, awaitItem()) }
        assertEquals(listOf(r.id), r.repo.deletedIds)
        assertEquals(BrewPhase.Finished, r.controller.state.value.phase)
    }
}
