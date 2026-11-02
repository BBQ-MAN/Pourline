package com.timemoredot.app.ui.history

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.app.ui.brew.MarkerCountKey
import com.timemoredot.app.ui.theme.TimemoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** S10 화면 동작(Compose UI 테스트, Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SessionDetailScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val announced = mutableListOf<Pair<String, Boolean>>()
    private val recorder = Announcer { text, assertive -> announced += text to assertive }
    private val store = VmStore()
    @After fun tearDown() = store.clear()

    @Composable private fun Frame(ui: SessionDetailUiState, onDelete: () -> Unit = {}, onNote: (String) -> Unit = {}) =
        CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(false) { SessionDetailContent(ui, SnackbarHostState(), onNote, {}, onDelete, {}) } }

    private val devil get() = HFx.summary(recipe = Fx.devil, dose = 20.0, final = 280.0)
    private fun devilUi(final: Double = 280.0, reach: List<Int?>? = null) =
        HFx.ui(summary = HFx.summary(recipe = Fx.devil, dose = 20.0, final = final), samples = Fx.samples(95.0)).let { if (reach != null) it.copy(stepReachTimesMs = reach) else it }

    @Test fun `차트 모드 필드와 함수는 UiState와 ViewModel에 없다`() {
        val fields = SessionDetailUiState::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(setOf("state", "summary", "samples", "stepReachTimesMs", "export"), fields.filter { !it.startsWith("$") }.toSet())
        val methods = SessionDetailViewModel::class.java.declaredMethods.map { it.name }.filter { it.contains("hart", ignoreCase = true) || it.contains("mode", ignoreCase = true) }
        assertEquals(emptyList<String>(), methods)
    }

    @Test fun `유량을 고르면 유량 그래프이고 복원 뒤에도 유량 모드이며 세그먼트는 48 dp 이상 selected로 낭독된다`() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Frame(HFx.ui()) }
        rule.onNode(hasContentDescription("무게 그래프", substring = true)).assertExists()
        rule.onNode(hasText("무게") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNode(hasText("유량") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("유량").performClick()
        rule.onNode(hasText("유량") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()
        rule.onNode(hasContentDescription("유량 그래프. ", substring = true)).assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.onNode(hasText("유량") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsSelected()
        rule.onNode(hasText("무게") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsNotSelected()
        rule.onNode(hasContentDescription("유량 그래프. ", substring = true)).assertExists()
    }

    @Test fun `D3 삭제 확인이 열린 상태가 복원 뒤에도 그대로이고 삭제를 누르면 onDeleteConfirmed`() {
        var deleted = 0
        val tester = StateRestorationTester(rule)
        tester.setContent { Frame(HFx.ui(), onDelete = { deleted++ }) }
        rule.onNodeWithContentDescription("더보기").performClick()
        rule.onNodeWithText("삭제").performClick()
        rule.onNodeWithText("이 기록을 삭제할까요? 되돌릴 수 없습니다.").assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("이 기록을 삭제할까요? 되돌릴 수 없습니다.").assertExists()
        assertEquals(0, deleted)
        rule.onAllNodesWithText("삭제").onFirst().performClick()
        assertEquals(1, deleted)
        rule.onAllNodesWithText("이 기록을 삭제할까요? 되돌릴 수 없습니다.").assertCountEquals(0)
    }

    @Test fun `스위치 일정 줄은 일정이 있을 때만 머리와 한 줄이 있다`() {
        var ui by mutableStateOf(devilUi())
        rule.setContent { Frame(ui) }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText("스위치 일정")).assertCountEquals(1)
        rule.onNodeWithText("열림으로 시작 · 1:15 닫기 · 1:45 열기").assertExists()
        // 일정 없음: 밸브 없는 레시피, 레시피 없음, valveOpen 키가 없는 옛 recipeJson 기록
        for (summary in listOf(HFx.summary(), HFx.summary(recipe = null), HFx.summary(recipe = Fx.devil.copy(steps = Fx.devil.steps.map { it.copy(valveOpen = null) })))) {
            ui = HFx.ui(summary = summary); rule.waitForIdle()
            rule.onAllNodesWithText("스위치 일정").assertCountEquals(0)
            rule.onAllNodes(hasText("열기", substring = true) and hasText("시작", substring = true)).assertCountEquals(0)
        }
    }

    @Test fun `기다리기 단계 행은 실제 도달 시각 없이 낭독하고 표식은 3개 그래프 낭독은 단계 3개 중 M개`() {
        val reach = listOf(9_800, 38_000, 99_000, 85_000, 99_100, 99_200)   // 기다리기 단계(3·5·6)에도 값이 있지만 쓰지 않는다
        rule.setContent { Frame(devilUi(final = 100.0, reach = reach)) }
        rule.onNodeWithText("기다리기 · 1:15까지 (120 g 그대로)", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("3단계 1:15까지 기다리기, 기다리기 단계, 1분 15초까지, 120 그램 그대로").assertExists()
        rule.onAllNodesWithText("실제 1:39", substring = true, useUnmergedTree = true).assertCountEquals(0)   // 기다리기 단계의 reach(99 s)를 쓰지 않는다
        rule.onNodeWithContentDescription("1단계 스위치 열고 뜸 60 g (90°C, 약간 가는 분쇄), 목표 60 그램 0분 10초, 실제 도달 0분 9초").assertExists()
        rule.onNode(SemanticsMatcher.expectValue(MarkerCountKey, 3)).assertExists()
        rule.onNode(hasContentDescription("단계 3개 중 1개 도달", substring = true)).assertExists()
    }

    @Test fun `도달하지 못한 단계는 실제 도달 없음`() {
        rule.setContent { Frame(HFx.ui(summary = HFx.summary(recipe = Fx.v60, final = 100.0), reach = listOf(9_000, null, null))) }
        rule.onNodeWithContentDescription("2단계", substring = true).assertExists()
        rule.onAllNodes(hasContentDescription("실제 도달 없음", substring = true)).assertCountEquals(1)   // 2단계는 기다리기 단계
    }

    @Test fun `Idle에서 Exporting이 될 때 내보내는 중을 한 번 알린다`() {
        var export by mutableStateOf<ExportState>(ExportState.Idle)
        rule.setContent { Frame(HFx.ui(export = export)) }
        assertEquals(emptyList<Pair<String, Boolean>>(), announced)
        export = ExportState.Exporting; rule.waitForIdle()
        assertEquals(listOf("내보내는 중" to false), announced)
    }

    @Test fun `메모를 입력하면 onNoteChange가 마지막 값으로 불린다`() {
        val notes = mutableListOf<String>()
        rule.setContent { Frame(HFx.ui(summary = HFx.summary(note = "산미")), onNote = { notes += it }) }
        rule.onNodeWithText("산미").performTextReplacement("산미가 좋다")
        assertEquals("산미가 좋다", notes.last())
    }

    @Test fun `메모 200자 한도에 닿으면 오류 표시와 낭독 한 번이고 값은 200자로 남는다`() {
        val notes = mutableListOf<String>()
        rule.setContent { Frame(HFx.ui(summary = HFx.summary(note = "a".repeat(199))), onNote = { notes += it }) }
        rule.onNodeWithText("a".repeat(199)).performTextReplacement("a".repeat(200))
        rule.onNodeWithText("a".repeat(200)).performTextReplacement("a".repeat(201))
        assertEquals(200, notes.last().length)
        assertEquals(listOf("최대 200자입니다" to false), announced)
        rule.onNode(hasContentDescription("200자 중 200자"), useUnmergedTree = true).assertExists()
    }

    @Test fun `내보내기 실패 스낵바 다시는 선택기를 새로 띄워 새 Uri로 onExportSamples를 부른다`() {
        val repo = FakeBrewRepository().also { r -> kotlinx.coroutines.runBlocking { r.seed() } }
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = store.create { SessionDetailViewModel(repo, 1, appScope) }
        val registry = FakePickerRegistry()
        val uris = mutableListOf<Uri>()
        val written = StringBuilder()
        val csvTarget: (Uri) -> CsvTarget = { uri -> uris += uri; if (uris.size == 1) CsvTarget { throw IOException("x") } else CsvTarget { block -> block(written) } }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner, LocalAnnouncer provides recorder) {
                TimemoreTheme(false) { SessionDetailScreen(vm, csvTarget, {}, {}) }
            }
        }
        rule.waitUntil(3000) { runCatching { rule.onNodeWithContentDescription("CSV 내보내기").assertExists() }.isSuccess }
        registry.next = Uri.parse("content://docs/1")
        rule.onNodeWithContentDescription("CSV 내보내기").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("CSV를 내보내지 못했습니다.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("timemore-brew-20261028-0812.csv"), registry.launched)
        registry.next = Uri.parse("content://docs/2")
        rule.onNodeWithText("다시").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("CSV를 저장했습니다").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, registry.launched.size)
        assertEquals(listOf("content://docs/1", "content://docs/2"), uris.map { it.toString() })
        assert(written.startsWith("﻿t_s,weight_g,flow_g_per_s"))
    }

    @Test fun `삭제 이벤트는 onDeleted로 간다`() {
        val repo = FakeBrewRepository().also { r -> kotlinx.coroutines.runBlocking { r.seed() } }
        val vm = store.create { SessionDetailViewModel(repo, 1, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) }
        var deleted = 0
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakePickerRegistry().owner, LocalAnnouncer provides recorder) {
                TimemoreTheme(false) { SessionDetailScreen(vm, { CsvTarget { } }, {}, { deleted++ }) }
            }
        }
        rule.waitUntil(3000) { runCatching { rule.onNodeWithContentDescription("더보기").assertExists() }.isSuccess }
        rule.onNodeWithContentDescription("더보기").performClick()
        rule.onNodeWithText("삭제").performClick()
        rule.onAllNodesWithText("삭제").onFirst().performClick()
        rule.waitUntil(3000) { deleted > 0 }
        assertEquals(1, deleted)
    }

    @Test fun `중단된 기록은 안내가 있고 NoSamples는 그래프 대신 글자`() {
        rule.setContent { Frame(HFx.ui(summary = HFx.summary(status = SessionStatus.Interrupted))) }
        rule.onNodeWithText("저장되지 않은 채 끝난 추출을 복구한 기록입니다.").assertExists()
    }

    @Test fun `NoSamples는 그래프 자리 문구가 있고 세그먼트가 없다`() {
        rule.setContent { Frame(HFx.ui(DetailState.NoSamples, samples = null)) }
        rule.onNodeWithText("그래프 데이터가 없습니다").assertExists()
        rule.onAllNodesWithText("유량").assertCountEquals(0)
    }
}
