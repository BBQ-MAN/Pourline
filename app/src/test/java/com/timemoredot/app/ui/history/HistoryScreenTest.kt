package com.timemoredot.app.ui.history

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.recipes.ListLoad
import com.timemoredot.app.ui.theme.TimemoreTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

/** 파일 선택기를 흉내 낸다: 띄우면 곧바로 next(Uri 또는 null 취소)를 돌려준다. */
class FakePickerRegistry : ActivityResultRegistry() {
    val launched = mutableListOf<String>()
    var next: Uri? = null
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        launched += input as String
        @Suppress("UNCHECKED_CAST") dispatchResult(requestCode, next as O)
    }
    val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry get() = this@FakePickerRegistry }
}

/** S9 화면 동작(Compose UI 테스트, Robolectric). 기록용 가짜 Announcer와 가짜 파일 선택기. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HistoryScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val announced = mutableListOf<Pair<String, Boolean>>()
    private val recorder = Announcer { text, assertive -> announced += text to assertive }
    private val store = VmStore()
    @After fun tearDown() = store.clear()

    private fun loaded(export: ExportState = ExportState.Idle) = HistoryUiState(ListLoad.Loaded(HFx.many), export)

    @Test fun `행 낭독은 한 노드이고 머리글은 heading이다`() {
        rule.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(false) { HistoryContent(loaded(), SnackbarHostState(), {}, {}, {}, {}) } } }
        rule.onNodeWithContentDescription("10월 28일 8시 12분, V60 기본, 원두 15.0 그램, 물 250.3 그램, 3분 4초, 비율 1 대 16.7").assertExists()
        rule.onNodeWithContentDescription("10월 27일 9시 0분, 테츠 카스야 악마의 레시피, 원두량 없음, 물 280.0 그램, 3분 0초, 비율 없음").assertExists()
        rule.onNodeWithContentDescription("10월 28일 18시 5분, 자유 추출, 원두 18.0 그램, 물 300.0 그램, 3분 20초, 비율 1 대 16.7").assertExists()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(3 + 1)   // 날짜 머리글 3 + 상단 바 제목
        assertEquals("15.0\u00A0g → 250.3\u00A0g · 3:04 · 1:16.7", sessionMetricsLine(HFx.many[2]))
        assertEquals("— → 280.0\u00A0g · 3:00 · —", sessionMetricsLine(HFx.many[3]))
    }

    @Test fun `Empty는 CSV 동작이 비활성이고 추출 시작이 있다`() {
        var started = 0
        rule.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(false) { HistoryContent(HistoryUiState(ListLoad.Empty, ExportState.Idle), SnackbarHostState(), {}, {}, { started++ }, {}) } } }
        rule.onNodeWithContentDescription("CSV 내보내기").assertIsNotEnabled()
        rule.onNodeWithText("추출 기록이 없습니다").assertExists()
        rule.onNodeWithText("추출 시작").performClick()
        assertEquals(1, started)
    }

    @Test fun `Exporting 중에는 CSV 동작이 비활성이고 진행 막대가 있다`() {
        rule.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(false) { HistoryContent(loaded(ExportState.Exporting), SnackbarHostState(), {}, {}, {}, {}) } } }
        rule.onNodeWithContentDescription("CSV 내보내기").assertIsNotEnabled()
        rule.onNodeWithContentDescription("내보내는 중").assertExists()
    }

    @Test fun `Idle에서 Exporting이 될 때 내보내는 중을 한 번 알린다`() {
        var export by mutableStateOf<ExportState>(ExportState.Idle)
        rule.setContent { CompositionLocalProvider(LocalAnnouncer provides recorder) { TimemoreTheme(false) { HistoryContent(loaded(export), SnackbarHostState(), {}, {}, {}, {}) } } }
        assertEquals(emptyList<Pair<String, Boolean>>(), announced)
        export = ExportState.Exporting; rule.waitForIdle()
        assertEquals(listOf("내보내는 중" to false), announced)
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertEquals(1, announced.size)
    }

    @Test fun `삭제 결과는 스낵바를 한 번 띄우고 키를 한 번 지운다`() {
        val vm = store.create { HistoryViewModel(FakeBrewRepository().also { r -> kotlinx.coroutines.runBlocking { r.seed() } }) }
        var deleted by mutableStateOf(true)
        var consumed = 0
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakePickerRegistry().owner, LocalAnnouncer provides recorder) {
                TimemoreTheme(false) { HistoryScreen(vm, { CsvTarget { } }, {}, {}, sessionDeleted = deleted, onDeletedConsumed = { consumed++; deleted = false }) }
            }
        }
        rule.waitUntil(3000) { rule.onAllNodesWithText("기록을 삭제했습니다").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("기록을 삭제했습니다").assertCountEquals(1)
        assertEquals(1, consumed)
    }

    @Test fun `내보내기 실패 스낵바 다시는 선택기를 새로 띄우고 새 Uri로 다시 시도하며 취소는 아무것도 하지 않는다`() {
        val repo = FakeBrewRepository().also { r -> kotlinx.coroutines.runBlocking { r.seed() } }
        val vm = store.create { HistoryViewModel(repo) }
        val registry = FakePickerRegistry()
        val uris = mutableListOf<Uri>()
        val written = StringBuilder()
        val csvTarget: (Uri) -> CsvTarget = { uri ->
            uris += uri
            if (uris.size <= 2) CsvTarget { throw IOException("open failed") } else CsvTarget { block -> block(written) }
        }
        rule.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry.owner, LocalAnnouncer provides recorder) { TimemoreTheme(false) { HistoryScreen(vm, csvTarget, {}, {}) } } }
        rule.waitUntil(3000) { runCatching { rule.onNodeWithContentDescription("CSV 내보내기").assertIsEnabled() }.isSuccess }

        registry.next = Uri.parse("content://docs/1")
        rule.onNodeWithContentDescription("CSV 내보내기").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("CSV를 내보내지 못했습니다.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("timemore-brews-${LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)}.csv"), registry.launched)

        // 선택기를 취소(Uri null)하면 VM 함수도 csvTarget도 부르지 않는다
        registry.next = null
        rule.onNodeWithText("다시").performClick()
        rule.waitForIdle()
        assertEquals(2, registry.launched.size)
        assertEquals(1, uris.size)
        assertEquals(ExportState.Idle, vm.uiState.value.export)

        // 같은 흐름을 다시 하면 새 Uri로 새 CsvTarget을 만든다(직전 것을 쓰지 않는다). 이번에도 실패
        registry.next = Uri.parse("content://docs/2")
        rule.onNodeWithContentDescription("CSV 내보내기").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("다시").fetchSemanticsNodes().isNotEmpty() }
        // 다시: 선택기를 한 번 더 띄우고 새 Uri(3)로 성공
        registry.next = Uri.parse("content://docs/3")
        rule.onNodeWithText("다시").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("CSV를 저장했습니다(1건)").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(4, registry.launched.size)
        assertEquals(listOf("content://docs/1", "content://docs/2", "content://docs/3"), uris.map { it.toString() })
        assertTrue(written.startsWith("﻿session_id,started_at"))
    }
}
