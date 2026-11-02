package com.timemoredot.app.ui.summary

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.data.SessionDraft
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.app.ui.brew.MarkerCountKey
import com.timemoredot.app.ui.brew.markerSteps
import com.timemoredot.app.ui.theme.TimemoreTheme
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S6 화면 동작(Compose UI 테스트, Robolectric). 기록용 가짜 Announcer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SummaryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val announced = mutableListOf<Pair<String, Boolean>>()
    private val recorder = Announcer { text, assertive -> announced += text to assertive }

    @Composable private fun Host(ui: SummaryUiState, onSave: () -> Unit = {}) {
        CompositionLocalProvider(LocalAnnouncer provides recorder) {
            TimemoreTheme(darkTheme = false) { SummaryContent(ui, {}, {}, onSave, {}) }
        }
    }

    private val d6 = "이 추출을 저장할까요?"

    @Test fun `뒤로 가기 완료는 D6, Brew로 돌아가지 않는다`() {
        rule.setContent { Host(SFx.ui()) }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(d6).assertExists()
        assertFalse(rule.activity.isFinishing)
        rule.onNodeWithText("추출 요약").assertExists()
    }

    @Test fun `뒤로 가기 취소는 D6을 띄우지 않고 핸들러는 늘 켜져 있다`() {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        rule.setContent { CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { Host(SFx.ui()) } }
        rule.waitForIdle()
        assertTrue(dispatcher.isEnabled)
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCancelled() }
        rule.waitForIdle()
        rule.onAllNodesWithText(d6).assertCountEquals(0)
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCompleted() }
        rule.waitForIdle()
        rule.onNodeWithText(d6).assertExists()
    }

    @Test fun `D6 버튼은 저장, 버리기, 취소`() {
        var saved = 0
        rule.setContent { Host(SFx.ui(), onSave = { saved++ }) }
        Espresso.pressBack(); rule.waitForIdle()
        rule.onNodeWithText("취소").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText(d6).assertCountEquals(0)
        Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("저장").onLast().performClick(); rule.waitForIdle()
        assertEquals(1, saved)
        Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("버리기").onLast().performClick(); rule.waitForIdle()   // D6의 버리기 → D3
        rule.onNodeWithText("이 추출을 버릴까요? 되돌릴 수 없습니다.").assertExists()
    }

    /** 실제 ViewModel(FakeBrewRepository)로 복원 확인: ViewModel은 구성 변경을 넘어 살고 화면 로컬 상태는 rememberSaveable이다. */
    private fun realVm(store: VmStore): SummaryViewModel {
        val repo = FakeBrewRepository()
        val fake = FakeScaleClient(ConnectionState.Connected(ScaleDevice("AA:BB:CC:DD:EE:FF", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)))
        val controller = BrewSessionController(fake, repo, FakeAppPrefs(), MonotonicClock { 0L }, { 0L }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val id = runBlocking { repo.saveDraft(SessionDraft(null, 1_000, Fx.v60, 250.0, 95_000, Fx.samples(95.0).toBlob())) }
        return store.create { SummaryViewModel(repo, controller, id, false) }
    }

    @Test fun `D6이 열린 상태와 원두량·메모 입력은 복원 뒤에도 그대로다`() {
        val store = VmStore()
        val vm = realVm(store)
        val tester = StateRestorationTester(rule)
        tester.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(darkTheme = false) { SummaryScreen(vm, {}, {}) } } }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and hasText("원두량", substring = true)).performTextReplacement("18.5")
        rule.onNode(hasSetTextAction() and hasText("메모", substring = true)).performTextInput("산미")
        Espresso.pressBack(); rule.waitForIdle()
        rule.onNodeWithText(d6).assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText(d6).assertExists()
        rule.onNode(hasText("18.5")).assertExists()
        rule.onNode(hasText("산미")).assertExists()
        store.clear()
    }

    @Test fun `D3이 열린 상태는 복원 뒤에도 그대로다`() {
        val store = VmStore()
        val vm = realVm(store)
        val tester = StateRestorationTester(rule)
        tester.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(darkTheme = false) { SummaryScreen(vm, {}, {}) } } }
        rule.waitForIdle()
        rule.onNodeWithText("버리기").performClick(); rule.waitForIdle()
        rule.onNodeWithText("이 추출을 버릴까요? 되돌릴 수 없습니다.").assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText("이 추출을 버릴까요? 되돌릴 수 없습니다.").assertExists()
        store.clear()
    }

    @Test fun `Saving에서 SaveFailed가 되면 알림이 정확히 한 번, 재구성만으로는 더 없다`() {
        var ui by mutableStateOf(SFx.ui(state = SummaryState.Saving))
        rule.setContent { Host(ui) }
        rule.waitForIdle()
        assertTrue(announced.isEmpty())
        ui = ui.copy(state = SummaryState.SaveFailed); rule.waitForIdle()
        assertEquals(listOf(SAVE_FAILED_ANNOUNCEMENT to false), announced)
        assertEquals("기록을 저장하지 못했습니다. 입력한 내용은 그대로 있습니다", SAVE_FAILED_ANNOUNCEMENT)
        ui = ui.copy(note = "메모 수정"); rule.waitForIdle()
        ui = ui.copy(doseText = "17.0"); rule.waitForIdle()
        assertEquals(1, announced.size)
        ui = ui.copy(state = SummaryState.Ready); rule.waitForIdle()
        ui = ui.copy(state = SummaryState.SaveFailed); rule.waitForIdle()   // Saving 프레임이 합쳐진 빠른 실패도 알린다
        assertEquals(2, announced.size)
    }

    @Test fun `원두량 오류에서 저장을 두 번 누르면 포커스와 낭독이 두 번`() {
        rule.setContent { Host(SFx.ui(dose = "abc", doseError = true)) }
        rule.waitForIdle()
        assertTrue(announced.isEmpty())
        repeat(2) { i ->
            rule.onNodeWithText("저장").performClick(); rule.waitForIdle()
            assertEquals(i + 1, announced.size)
            rule.onNode(hasSetTextAction() and isFocused()).assertExists()
        }
        assertEquals(List(2) { DOSE_ERROR_TEXT to false }, announced)
    }

    @Test fun `처음부터 SaveFailed인 화면은 알리지 않는다`() {
        rule.setContent { Host(SFx.ui(state = SummaryState.SaveFailed)) }
        rule.waitForIdle()
        assertTrue(announced.isEmpty())
        rule.onNodeWithText("기록을 저장하지 못했습니다. 입력한 내용은 그대로 있습니다.").assertExists()
    }

    private fun chartText(): String = rule.onNode(hasContentDescription("무게 그래프", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
    private fun chartMarkers(): Int = rule.onNode(SemanticsMatcher.keyIsDefined(MarkerCountKey), useUnmergedTree = true).fetchSemanticsNode().config[MarkerCountKey]

    @Test fun `기다리기 단계는 낭독에서 세지 않는다`() {
        var ui by mutableStateOf(SFx.ui(Fx.devil, finalGrams = 280.0))
        rule.setContent { Host(ui) }
        rule.waitForIdle()
        assertTrue(chartText(), chartText().contains("단계 3개 중 3개 도달"))
        assertEquals(3, chartMarkers())
        ui = SFx.ui(Fx.devil, finalGrams = 100.0); rule.waitForIdle()
        assertTrue(chartText(), chartText().contains("단계 3개 중 1개 도달"))
        ui = SFx.ui(Fx.hario, finalGrams = 240.0); rule.waitForIdle()
        assertTrue(chartText(), chartText().contains("단계 1개 중 1개 도달"))
        assertEquals(1, chartMarkers())
        ui = SFx.ui(Fx.v60, finalGrams = 250.0); rule.waitForIdle()
        assertTrue(chartText(), chartText().contains("단계 2개 중 2개 도달"))
        assertEquals(2, chartMarkers())
        ui = SFx.ui(null, finalGrams = 250.0); rule.waitForIdle()
        assertFalse(chartText(), chartText().contains("단계"))
    }

    @Test fun `서버 표식 수도 기다리기 단계를 뺀다`() {
        assertEquals(3, markerSteps(Fx.devil).size)
        assertEquals(1, markerSteps(Fx.hario).size)
        assertEquals(2, markerSteps(Fx.v60).size)
    }

    @Test fun `값 없는 구간은 낭독과 정보 카드에 나온다`() {
        rule.setContent { Host(SFx.ui(samples = SFx.gapSamples())) }
        rule.waitForIdle()
        assertTrue(chartText(), chartText().contains("값 없는 구간 0분 41초부터 0분 51초"))
        rule.onNodeWithText("이 기록에는 값이 없는 구간이 있습니다: 0:41~0:51").assertExists()
    }

    @Test fun `지표 칸은 최종 무게·시간·비율이고 원두량이 비면 비율은 줄표`() {
        var ui by mutableStateOf(SFx.ui())
        rule.setContent { Host(ui) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("최종 무게 250.0 그램").assertExists()
        rule.onNodeWithContentDescription("시간 1분 35초").assertExists()
        rule.onNodeWithContentDescription("비율 1 대 16.7").assertExists()
        ui = ui.copy(doseText = ""); rule.waitForIdle()
        rule.onNodeWithContentDescription("비율 없음").assertExists()
        rule.onAllNodesWithText("무게").assertCountEquals(0)   // 무게/유량 세그먼트 없음
    }
}
