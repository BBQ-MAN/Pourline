package com.timemoredot.app.ui.summary

import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.brew.BrewSample
import com.timemoredot.brew.FLAG_DISCONNECTED
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.gaps

/** S6 화면 테스트 고정 데이터(합성 V60 시계열 Fx.samples). */
object SFx {
    fun summary(recipe: Recipe? = Fx.v60, finalGrams: Double? = 250.0, durationMs: Long = 95_000, id: Long = 1L) =
        SessionSummary(id, 1_698_480_720_000, recipe?.name, recipe, null, finalGrams, durationMs, SessionStatus.InProgress, "")

    fun ui(
        recipe: Recipe? = Fx.v60, finalGrams: Double? = 250.0, samples: SampleBuffer = Fx.samples(95.0), dose: String? = null,
        state: SummaryState = SummaryState.Ready, doseError: Boolean = false, note: String = "",
    ) = SummaryUiState(
        summary(recipe, finalGrams), samples, samples.gaps(), dose ?: recipe?.doseGrams?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "",
        doseError, note, state,
    )

    /** 0:42~0:51 끊김이 있는 시계열. */
    fun gapSamples(): SampleBuffer = Fx.samples(95.0, gaps = listOf(42.0..51.0))

    /** 최종 280 g(물 250 g 초과)까지 오르는 시계열. */
    fun overfill(): SampleBuffer = Fx.samples(95.0, weight = { k -> Fx.weightAt(k) * 280.0 / 250.0 }, flow = { k -> Fx.shownFlow(k) })

    @Suppress("unused") private val keep = BrewSample(0, 0f, 0f, FLAG_DISCONNECTED)
}
