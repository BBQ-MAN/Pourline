package com.timemoredot.app.ui.recipes

import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.ValveEvent
import java.util.Locale

/** 그램 글자: 정수면 소수점 없이("250"), 아니면 그대로("60.5"). */
fun gramsText(g: Double): String = if (g == Math.floor(g) && g < 1e9) g.toLong().toString() else g.toString()

fun ratioText(r: Double) = String.format(Locale.ROOT, "1:%.1f", r)

/** S7 둘째 줄 "15 g · 250 g · 1:16.7"(ui-design 3.5). */
fun recipeSummaryLine(r: Recipe) = "${gramsText(r.doseGrams)}${NBSP}g · ${gramsText(r.waterGrams)}${NBSP}g · ${ratioText(r.waterGrams / r.doseGrams)}"

/** S7 셋째 줄 "3단계 · 끝 1:30". 단계 수는 기다리기 포함, 끝 시각은 자동 완료 기준(finishSec). */
fun recipeStepsLine(r: Recipe) = "${r.steps.size}단계 · 끝 ${formatMinSec(r.finishSec)}"

/** S7 행 낭독(ui-design 8.4). */
fun recipeSpoken(r: Recipe) = String.format(Locale.ROOT, "%s, 원두 %s 그램, 물 %s 그램, 비율 1 대 %.1f, %d단계, 끝 %s", r.name, gramsText(r.doseGrams), gramsText(r.waterGrams), r.waterGrams / r.doseGrams, r.steps.size, spokenMinSec(r.finishSec.toLong()))

/** 스위치 일정 줄(ui-design 3.6·3.7, S10과 공용). "닫힘으로 시작 · 2:00 열기", 하나뿐이면 "· 끝까지 그대로". */
fun valveScheduleText(events: List<ValveEvent>): String {
    val head = if (events.first().open) "열림으로 시작" else "닫힘으로 시작"
    if (events.size == 1) return "$head · 끝까지 그대로"
    return head + events.drop(1).joinToString("") { " · ${formatMinSec(it.atSec)} ${if (it.open) "열기" else "닫기"}" }
}
