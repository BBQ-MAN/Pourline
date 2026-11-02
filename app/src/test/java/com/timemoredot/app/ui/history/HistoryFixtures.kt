package com.timemoredot.app.ui.history

import com.timemoredot.app.data.SessionDraft
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.reachTimesMs
import java.time.ZoneId
import java.time.ZonedDateTime

/** S9·S10 테스트 고정 데이터. */
object HFx {
    fun at(month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun summary(
        id: Long = 1, startedAt: Long = at(10, 28, 8, 12), recipe: Recipe? = Fx.v60, dose: Double? = 15.0, final: Double? = 250.3,
        durationMs: Long = 184_000, status: SessionStatus = SessionStatus.Completed, note: String = "",
    ) = SessionSummary(id, startedAt, recipe?.name, recipe, dose, final, durationMs, status, note)

    /** 날짜 둘, 자유 추출 하나, 원두량 없음 하나. */
    val many = listOf(
        summary(4, at(10, 29, 7, 40), Fx.hario, 15.0, 240.2, 150_000),
        summary(3, at(10, 28, 18, 5), null, 18.0, 300.0, 200_000, note = "산미가 좋았다"),
        summary(2, at(10, 28, 8, 12)),
        summary(1, at(10, 27, 9, 0), Fx.devil, null, 280.0, 180_000, SessionStatus.Interrupted),
    )

    /** 악마의 레시피 기록(목록 id 1과 같은 기록). 시계열은 레시피 목표를 따른다: 0:10 60 g, 0:40 120 g, 1:15까지 평평, 1:25 280 g, 3:00까지 평평. */
    val devilSummary get() = many[3]
    private fun devilW(t: Double): Double = when {
        t <= 0 -> 0.0
        t <= 10 -> 60.0 * t / 10
        t <= 40 -> 60 + 60.0 * (t - 10) / 30
        t <= 75 -> 120.0
        t <= 85 -> 120 + 160.0 * (t - 75) / 10
        else -> 280.0
    }
    val devilSamples: SampleBuffer get() = Fx.samples(180.0, weight = { k -> devilW(k / 10.0) }, flow = { k -> maxOf(0.0, devilW(k / 10.0) - devilW(k / 10.0 - 1)) })

    fun ui(
        state: DetailState = DetailState.Loaded, summary: SessionSummary? = summary(), samples: SampleBuffer? = Fx.samples(184.0),
        reach: List<Int?> = summary?.recipe?.let { r -> samples?.let(r::reachTimesMs) } ?: emptyList(),
        export: ExportState = ExportState.Idle,
    ) = SessionDetailUiState(state, summary, samples, reach, export)
}

/** 저장소에 기록 하나를 넣는다(saveDraft → finish). */
suspend fun FakeBrewRepository.seed(
    startedAt: Long = HFx.at(10, 28, 8, 12), recipe: Recipe? = Fx.v60, dose: Double? = 15.0, final: Double? = 250.3, durationMs: Long = 184_000,
    samples: SampleBuffer = Fx.samples(95.0), status: SessionStatus = SessionStatus.Completed, note: String = "",
): Long {
    val id = saveDraft(SessionDraft(null, startedAt, recipe, final, durationMs, samples.toBlob()))
    finish(id, dose, note, status)
    return id
}
