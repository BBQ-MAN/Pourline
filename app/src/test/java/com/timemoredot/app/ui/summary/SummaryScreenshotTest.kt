package com.timemoredot.app.ui.summary

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.ui.Shooter
import com.timemoredot.app.ui.brew.BrewFrame
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.app.ui.brew.RecoverDraftDialog
import com.timemoredot.app.ui.brew.Scenes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Composable private fun Screen(ui: SummaryUiState) = SummaryContent(ui, {}, {}, {}, {})

/** S6 상태별 스크린샷(ui-design 8.6). 360 × 800, 정지 그림이라 진행 시간은 기본 900 ms. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SummaryScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `요약 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.both("summary_loading") { Screen(SummaryUiState()) }
        s.both("summary_ready") { Screen(SFx.ui()) }
        s.both("summary_ready_free") { Screen(SFx.ui(recipe = null)) }
        s.both("summary_overfill") { Screen(SFx.ui(finalGrams = 280.0, samples = SFx.overfill())) }
        s.both("summary_has_gaps") { Screen(SFx.ui(samples = SFx.gapSamples())) }
        s.both("summary_invalid_dose") { Screen(SFx.ui(dose = "0.05", doseError = true)) }
        s.both("summary_saving") { Screen(SFx.ui(state = SummaryState.Saving, note = "산미가 좋았다")) }
        s.both("summary_load_failed") { Screen(SummaryUiState(loadFailed = true)) }
        s.both("summary_save_failed") { Screen(SFx.ui(state = SummaryState.SaveFailed, note = "산미가 좋았다")) }
        s.font200("summary_save_failed") { Screen(SFx.ui(state = SummaryState.SaveFailed, note = "산미가 좋았다")) }
    }

    @Test fun `대화상자`() {
        val s = Shooter(rule).also { it.start() }
        s.both("summary_leave_confirm", screen = true) { Screen(SFx.ui()); SaveConfirmDialog({}, {}, {}) }
        s.both("summary_discard_confirm", screen = true) { Screen(SFx.ui()); DiscardConfirmDialog({}, {}) }
        val draft = SFx.summary(Fx.v60, 120.0, 184_000).copy(
            startedAtEpochMs = java.time.ZonedDateTime.of(2026, 10, 28, 8, 12, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            status = SessionStatus.InProgress,
        )
        s.both("brew_recover_draft", screen = true) { BrewFrame(Scenes.idle().ui, Scenes.idle().readout); RecoverDraftDialog(draft, {}, {}) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 2f)
class SummaryDialogFont200Test {
    @get:Rule val rule = createComposeRule()

    @Test fun `대화상자 글꼴 200`() {
        val s = Shooter(rule).also { it.start() }
        s.font200("summary_leave_confirm", screen = true) { Screen(SFx.ui()); SaveConfirmDialog({}, {}, {}) }
        s.font200("brew_recover_draft", screen = true) { BrewFrame(Scenes.idle().ui, Scenes.idle().readout); RecoverDraftDialog(SFx.summary(Fx.v60, 120.0, 184_000).copy(startedAtEpochMs = java.time.ZonedDateTime.of(2026, 10, 28, 8, 12, 0, 0, java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), status = SessionStatus.InProgress), {}, {}) }
    }
}
