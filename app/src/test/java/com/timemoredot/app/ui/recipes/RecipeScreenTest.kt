package com.timemoredot.app.ui.recipes

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.theme.TimemoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S7·S8 화면 동작(Compose UI 테스트, Robolectric). 기록용 가짜 Announcer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RecipeScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val announced = mutableListOf<Pair<String, Boolean>>()
    private val recorder = Announcer { text, assertive -> announced += text to assertive }
    private val store = VmStore()

    private fun List<com.timemoredot.brew.Recipe>.loaded(canStart: Boolean = true) =
        RecipeListUiState(ListLoad.Loaded(sortedBy { it.name }), canStart)

    // ---- S7 ----

    @Test fun `첫 실행 행 셋째 줄과 낭독에 단계 수가 들어간다`() {
        rule.setContent { TimemoreTheme(darkTheme = false) { RecipeListContent(RFx.all.loaded(), {}, {}, {}, {}) } }
        for (line in listOf("3단계 · 끝 1:30", "5단계 · 끝 2:45", "3단계 · 끝 2:30", "5단계 · 끝 3:30", "6단계 · 끝 3:00")) rule.onAllNodesWithText(line, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithContentDescription("V60 기본, 원두 15 그램, 물 250 그램, 비율 1 대 16.7, 3단계, 끝 1분 30초").assertExists()
        rule.onNodeWithContentDescription("6888 레시피, 원두 20 그램, 물 300 그램, 비율 1 대 15.0, 5단계, 끝 2분 45초").assertExists()
    }

    @Test fun `행 줄 순수 함수`() {
        assertEquals("3단계 · 끝 1:30", recipeStepsLine(RFx.v60))
        assertEquals("5단계 · 끝 2:45", recipeStepsLine(RFx.all[2]))
        assertEquals("15 g · 250 g · 1:16.7", recipeSummaryLine(RFx.v60).replace('\u00A0', ' '))
    }

    @Test fun `추출 진행 중이면 추출 버튼이 비활성이고 행은 눌러 편집할 수 있다`() {
        var edited = -1L
        var brewed = -1L
        var canStart by androidx.compose.runtime.mutableStateOf(false)
        rule.setContent { TimemoreTheme(darkTheme = false) { RecipeListContent(RFx.all.loaded(canStart), { brewed = it }, { edited = it }, {}, {}) } }
        rule.onAllNodes(hasContentDescription("이 레시피로 추출")).assertCountEquals(5)
        rule.onAllNodes(hasContentDescription("이 레시피로 추출")).get(0).assertIsNotEnabled()
        rule.onAllNodes(hasText("추출") and hasClickAction()).get(0).performClick()
        assertEquals(-1L, brewed)
        rule.onNodeWithContentDescription("V60 기본, 원두 15 그램, 물 250 그램, 비율 1 대 16.7, 3단계, 끝 1분 30초").performClick()
        assertEquals(RFx.v60.id, edited)
        canStart = true; rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("이 레시피로 추출")).get(0).assertIsEnabled()
        rule.onAllNodes(hasContentDescription("이 레시피로 추출")).get(0).performClick()
        assertEquals(RFx.all.sortedBy { it.name }[0].id, brewed)
    }

    @Test fun `추출 중 배너는 canStartBrew false에만 있고 추출 화면으로가 S1 이동을 부른다`() {
        var opened = 0
        var canStart by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent { TimemoreTheme(darkTheme = false) { RecipeListContent(RFx.all.loaded(canStart), {}, {}, {}, {}, onOpenBrew = { opened++ }) } }
        rule.onAllNodes(hasText("추출 중에는 레시피를 바꿀 수 없습니다")).assertCountEquals(0)
        canStart = false; rule.waitForIdle()
        rule.onAllNodes(hasText("추출 중에는 레시피를 바꿀 수 없습니다")).assertCountEquals(1)
        rule.onNodeWithText("추출 화면으로").performClick()
        assertEquals(1, opened)
    }

    @Test fun `새 레시피 FAB는 Loaded에만 있다`() {
        var state by androidx.compose.runtime.mutableStateOf(RecipeListUiState(ListLoad.Loading, true))
        rule.setContent { TimemoreTheme(darkTheme = false) { RecipeListContent(state, {}, {}, {}, {}) } }
        for (l in listOf(ListLoad.Loading, ListLoad.Empty, ListLoad.Error)) {
            state = RecipeListUiState(l, true); rule.waitForIdle()
            rule.onAllNodes(hasText("새 레시피")).assertCountEquals(0)
        }
        rule.onAllNodes(hasText("레시피 만들기")).assertCountEquals(0)
        state = RecipeListUiState(ListLoad.Empty, true); rule.waitForIdle()
        rule.onAllNodes(hasText("레시피 만들기") and hasClickAction()).assertCountEquals(1)
        state = RFx.all.loaded(); rule.waitForIdle()
        rule.onAllNodes(hasText("새 레시피")).assertCountEquals(1)
    }

    // ---- S8 ----

    private fun vm(id: Long?, repo: FakeRecipeRepository = FakeRecipeRepository(RFx.all)) = store.create { RecipeEditViewModel(repo, id) }

    @Composable private fun Screen(vm: RecipeEditViewModel, existing: Boolean = true, onClose: () -> Unit = {}) {
        CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(darkTheme = false) { RecipeEditScreen(vm, existing, onClose, {}) } }
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    @Test fun `뒤로 가기 처리는 hasUnsavedChanges일 때만 D4를 연다`() {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        val v = vm(RFx.v60.id)
        rule.setContent { CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { Screen(v) } }
        rule.waitForIdle()
        // dispatcher.isEnabled는 바탕 처리기 때문에 늘 true라 동작으로 확인한다: 변경 없음이면 가로채지 않는다
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCompleted() }
        rule.waitForIdle()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
        v.onNameChange("바꿈"); rule.waitForIdle()
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCancelled() }
        rule.waitForIdle()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCompleted() }
        rule.waitForIdle()
        rule.onNodeWithText("변경 내용을 버릴까요?").assertExists()
        rule.onNodeWithText("계속 편집").performClick()
        v.onNameChange("V60 기본"); rule.waitForIdle()
        rule.runOnIdle { input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT)); input.backCompleted() }
        rule.waitForIdle()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
    }

    @Test fun `변경이 있으면 뒤로 가기는 D4, 계속 편집과 버리기`() {
        var closed = 0
        val v = vm(RFx.v60.id)
        rule.setContent { Screen(v, onClose = { closed++ }) }
        rule.waitForIdle()
        v.onNameChange("바꿈"); rule.waitForIdle()
        back()
        rule.onNodeWithText("변경 내용을 버릴까요?").assertExists()
        assertFalse(rule.activity.isFinishing)
        rule.onNodeWithText("계속 편집").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
        assertEquals(0, closed)
        rule.onNodeWithContentDescription("닫기").performClick(); rule.waitForIdle()   // 닫기 버튼도 D4
        rule.onAllNodesWithText("버리기").onFirst().performClick(); rule.waitForIdle()
        assertEquals(1, closed)
    }

    @Test fun `변경이 없으면 뒤로 가기는 이전 화면으로 간다`() {
        val v = vm(RFx.v60.id)
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        back()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
        assertTrue(rule.activity.isFinishing)
    }

    @Test fun `D4가 열린 상태는 복원 뒤에도 그대로다`() {
        val v = vm(RFx.v60.id)
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen(v) }
        rule.waitForIdle()
        v.onNameChange("바꿈"); rule.waitForIdle()
        back()
        rule.onNodeWithText("변경 내용을 버릴까요?").assertExists()
        tester.emulateSavedInstanceStateRestore(); rule.waitForIdle()
        rule.onNodeWithText("변경 내용을 버릴까요?").assertExists()
    }

    @Test fun `D3가 열린 상태는 복원 뒤에도 그대로다`() {
        val v = vm(RFx.v60.id)
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen(v) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("더보기").performClick(); rule.waitForIdle()
        rule.onNodeWithText("삭제").performClick(); rule.waitForIdle()
        rule.onNodeWithText("레시피 'V60 기본'을 삭제할까요? 기록은 남습니다.").assertExists()
        tester.emulateSavedInstanceStateRestore(); rule.waitForIdle()
        rule.onNodeWithText("레시피 'V60 기본'을 삭제할까요? 기록은 남습니다.").assertExists()
    }

    @Test fun `하리오 1단계 카드의 넣기 버튼은 알림을 정확히 한 번 낸다`() {
        val v = vm(RFx.hario.id)
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        val insert = rule.onNodeWithContentDescription("1단계 뒤에 스위치 바꾸는 단계 넣기")
        insert.performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf("2단계로 스위치 바꾸는 단계를 넣었습니다" to false), announced)
        assertEquals(4, v.uiState.value.steps.size)
        rule.onNodeWithText("2단계 · 기다리기").assertExists()
        rule.onNodeWithText("스위치 · 0:10부터 · 여기서 바꿈").assertExists()
    }

    @Test fun `스위치를 끄면 설명 줄 선택기 넣기 미리보기가 없고 기다리기 머리는 남는다`() {
        val v = vm(RFx.hario.id)
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        rule.onNodeWithText("스위치 · 처음부터").assertExists()
        rule.onAllNodesWithContentDescription("1단계 뒤에 스위치 바꾸는 단계 넣기").assertCountEquals(1)
        rule.onNodeWithText("스위치 일정").assertExists()
        rule.onNodeWithText("스위치(밸브) 레시피").performScrollTo().performClick(); rule.waitForIdle()
        assertFalse(v.uiState.value.valveEnabled)
        rule.onAllNodesWithText("스위치 · ", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("스위치 닫힘", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("스위치 열림", substring = true).assertCountEquals(0)
        rule.onAllNodesWithContentDescription("스위치 바꾸는 단계 넣기", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("스위치 일정").assertCountEquals(0)
        rule.onNodeWithText("2단계 · 기다리기").assertExists()
        rule.onNodeWithText("3단계 · 기다리기").assertExists()
    }

    @Test fun `악마의 레시피 카드 문구`() {
        val v = vm(RFx.devil.id)
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        rule.onNodeWithText("열림으로 시작 · 1:15 닫기 · 1:45 열기").assertExists()
        rule.onNodeWithText("3단계 · 기다리기").assertExists()
        rule.onNodeWithText("스위치 · 1:15부터 · 여기서 바꿈").assertExists()
        rule.onNodeWithContentDescription("4단계 스위치, 1분 15초부터, 여기서 바꿈").assertExists()
    }

    @Test fun `저장 중 뒤로 가기는 화면을 떠나지 않고 D4도 없다`() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val fake = FakeRecipeRepository(RFx.all)
        val slow = object : com.timemoredot.app.data.RecipeRepository by fake {
            override suspend fun upsert(recipe: com.timemoredot.brew.Recipe): Long { gate.await(); return fake.upsert(recipe) }
        }
        val v = store.create { RecipeEditViewModel(slow, RFx.v60.id) }
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        v.onNameChange("바꿈"); rule.waitForIdle()
        v.onSave(); rule.waitForIdle()
        assertEquals(RecipeEditState.Saving, v.uiState.value.state)
        back()
        rule.onAllNodesWithText("변경 내용을 버릴까요?").assertCountEquals(0)
        assertFalse(rule.activity.isFinishing)
        assertEquals(RecipeEditState.Saving, v.uiState.value.state)
        gate.complete(Unit)
    }

    @Test fun `단계 입력 칸은 입력값을 노출하고 번호는 상태 설명으로 준다`() {
        val v = vm(RFx.v60.id)
        rule.setContent { Screen(v) }
        rule.waitForIdle()
        val n = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1단계 누적 목표 그램")).fetchSemanticsNodes()
        assertEquals(1, n.size)
        assertTrue(n[0].config.contains(androidx.compose.ui.semantics.SemanticsProperties.EditableText))
        assertFalse(n[0].config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))
    }

    @Test fun `저장 오류는 첫 오류 칸의 의미에 문구가 있다`() {
        val v = vm(null)
        rule.setContent { Screen(v, existing = false) }
        rule.waitForIdle()
        rule.onNodeWithText("저장").performClick(); rule.waitForIdle()
        rule.onNodeWithText("이름을 입력하세요").assertExists()
        rule.onNodeWithText("단계를 하나 이상 추가하세요").assertExists()
    }
}
