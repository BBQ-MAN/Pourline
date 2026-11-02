package com.timemoredot.app.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.ui.Shooter
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.app.ui.nav.AppTabsScaffold
import com.timemoredot.app.ui.nav.Dest
import com.timemoredot.app.ui.recipes.ListLoad
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable private fun Hist(ui: HistoryUiState) =
    AppTabsScaffold(Dest.History, true, onSelect = {}) { HistoryContent(ui, remember { SnackbarHostState() }, {}, {}, {}, {}) }

@Composable private fun Detail(ui: SessionDetailUiState) = SessionDetailContent(ui, remember { SnackbarHostState() }, {}, {}, {}, {})

private fun loaded(export: ExportState = ExportState.Idle) = HistoryUiState(ListLoad.Loaded(HFx.many), export)

/** S9·S10 상태별 스크린샷(ui-design 8.6). 360 × 800. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HistoryScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `기록 목록 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.both("history_loading", advanceMs = 600) { Hist(HistoryUiState(ListLoad.Loading, ExportState.Idle)) }
        s.both("history_empty") { Hist(HistoryUiState(ListLoad.Empty, ExportState.Idle)) }
        s.both("history_loaded") { Hist(loaded()) }
        s.both("history_error") { Hist(HistoryUiState(ListLoad.Error, ExportState.Idle)) }
        s.both("history_exporting") { Hist(loaded(ExportState.Exporting)) }
        s.font200("history_loaded") { Hist(loaded()) }
    }

    @Test fun `기록 상세 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.both("session_detail_loading", advanceMs = 600) { Detail(HFx.ui(DetailState.Loading, summary = null, samples = null)) }
        s.both("session_detail_completed") { Detail(HFx.ui(summary = HFx.summary(note = "산미가 좋았다"))) }
        s.both("session_detail_interrupted") { Detail(HFx.ui(summary = HFx.summary(status = SessionStatus.Interrupted))) }
        s.both("session_detail_no_samples") { Detail(HFx.ui(DetailState.NoSamples, samples = null)) }
        s.both("session_detail_not_found") { Detail(HFx.ui(DetailState.NotFound, summary = null, samples = null)) }
        s.both("session_detail_delete_confirm", screen = true) { Detail(HFx.ui()); DeleteSessionDialog({}, {}) }
        s.font200("session_detail_completed") { Detail(HFx.ui(summary = HFx.devilSummary, samples = HFx.devilSamples)) }
    }

    @Test fun `기록 상세 그래프와 스위치 일정`() {
        val s = Shooter(rule).also { it.start() }
        s.both("history_detail") { Detail(HFx.ui()) }
        s.both("history_detail_flow", prepare = { rule.mainClock.advanceTimeBy(100); rule.onNodeWithText("유량").performClick(); rule.mainClock.advanceTimeBy(300) }) { Detail(HFx.ui()) }
        // 악마의 레시피 기록(최종 280 g): 스크롤 없이 그래프 전체와 표식 3개, 이어서 표와 일정 줄
        val devil: @androidx.compose.runtime.Composable () -> Unit = { Detail(HFx.ui(summary = HFx.devilSummary, samples = HFx.devilSamples)) }
        s.both("history_detail_valve_chart", content = devil)
        s.both("history_detail_valve", prepare = { rule.mainClock.advanceTimeBy(100); rule.onNode(hasScrollAction()).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top, durationMillis = 200) }; rule.mainClock.advanceTimeBy(1000) }, content = devil)
    }
}
