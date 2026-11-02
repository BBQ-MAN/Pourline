package com.timemoredot.app.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.brew.G_TICKS
import com.timemoredot.app.ui.brew.Legend
import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.SEC_TICKS
import com.timemoredot.app.ui.brew.chartRange
import com.timemoredot.app.ui.brew.formatFlow
import com.timemoredot.app.ui.brew.formatWeight
import com.timemoredot.app.ui.brew.markerCount
import com.timemoredot.app.ui.brew.markerSteps
import com.timemoredot.app.ui.brew.shape.chooseTickStep
import com.timemoredot.app.ui.brew.shape.ribbonWidthDp
import com.timemoredot.app.ui.brew.shape.summaryStepReached
import com.timemoredot.app.ui.brew.shape.thinnedSampleIndices
import com.timemoredot.app.ui.brew.shape.vesselLevel
import com.timemoredot.app.ui.brew.shape.visibleLabelIndices
import com.timemoredot.app.ui.brew.shortGrams
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.app.ui.brew.spokenNumber
import com.timemoredot.app.ui.brew.tickStepWithin
import com.timemoredot.app.ui.brew.validSample
import com.timemoredot.app.ui.theme.DROP_BASE_D
import com.timemoredot.app.ui.theme.dropPath
import com.timemoredot.app.ui.theme.vesselPath
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.gaps
import com.timemoredot.brew.isWaitStep
import com.timemoredot.protocol.WeightUnit
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * S6 요약판 그림(ui-design 4.4·4.10). 정지 그림이라 애니메이션 API와 프레임 루프가 없고 진입 때 재생하지 않는다.
 * 순수 수치는 M8 ShapeMath(vesselLevel, summaryStepReached, thinnedSampleIndices…)와 BrewChart의 chartRange·markerSteps를 그대로 쓴다.
 */

private fun DrawScope.marker(drop: Path, cx: Float, cy: Float, reached: Boolean, surface: Color, muted: Color, tertiary: Color) {
    val w = 10.dp.toPx(); val h = 14.dp.toPx()
    translate(cx - w / 2f, cy - h / 2f) {
        drawPath(drop, surface, style = Stroke(4.5.dp.toPx()))
        if (reached) drawPath(drop, tertiary)
        else { drawPath(drop, surface); drawPath(drop, muted, style = Stroke(1.5.dp.toPx())) }
    }
}

/** 서버 요약판: H 120 dp, 벽 primary, 수위 h = vesselLevel(최종 무게, 물 g). TalkBack에서 뺀다(정보는 지표 칸·그래프 낭독이 가진다). */
@Composable
fun SummaryVessel(summary: SessionSummary, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant, fontFeatureSettings = "tnum")
    Spacer(
        modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics {}.drawWithCache {
            val dp = density
            val vH = size.height
            val vW = vH * 176f / 164f
            val left = (size.width - vW) / 2f
            val bottom = vH
            val wall = Path().also { vesselPath(it, vW / 100f, vH / 100f, closed = false); it.translate(Offset(left, 0f)) }
            val closed = Path().also { vesselPath(it, vW / 100f, vH / 100f, closed = true); it.translate(Offset(left, 0f)) }
            val level = vesselLevel(summary.finalWeightGrams, summary.recipe?.waterGrams)
            val surfaceY = level?.let { bottom - 0.92f * vH * it }
            val drop = Path().also { dropPath(it, DROP_BASE_D, 10.dp.toPx() / 100f, 14.dp.toPx() / 140f) }
            val r = summary.recipe
            val steps = if (r != null) markerSteps(r) else IntArray(0)
            fun y(i: Int) = bottom - 0.92f * vH * min(1.08f, (r!!.steps[i].targetGrams / r.waterGrams).toFloat())
            val order = steps.indices.sortedBy { y(steps[it]) }
            val layouts = order.map { measurer.measure("${shortGrams(r!!.steps[steps[it]].targetGrams)}${NBSP}g", labelStyle) }
            val tops = FloatArray(order.size) { y(steps[order[it]]) - layouts[it].size.height / 2f }
            val labels: List<Pair<Float, TextLayoutResult>> = visibleLabelIndices(tops, FloatArray(order.size) { layouts[it].size.height.toFloat() })
                .map { tops[it] to layouts[it] }
            onDrawBehind {
                if (surfaceY != null) clipPath(closed) {
                    drawRect(cs.primaryContainer, Offset(left, surfaceY), Size(vW, bottom - surfaceY))
                    drawLine(cs.primary, Offset(left, surfaceY), Offset(left + vW, surfaceY), 2.5.dp.toPx(), StrokeCap.Round)
                }
                drawPath(wall, cs.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                for (i in steps) marker(drop, left + vW + 13.dp.toPx(), y(i), summaryStepReached(summary.finalWeightGrams, r!!.steps[i].targetGrams), cs.surface, cs.onSurfaceVariant, cs.tertiary)
                for ((top, t) in labels) drawText(t, topLeft = Offset(left + vW + 26.dp.toPx(), top))
            }
        },
    )
}

/** 그래프 낭독(ui-design 8.2 리본 요약판 행). 표식이 없는 기다리기 단계는 단계 수에 세지 않는다. */
fun summaryChartSpoken(samples: SampleBuffer, recipe: Recipe?, finalWeightGrams: Double?): String {
    var maxFlow = 0f
    for (i in 0 until samples.size) if (validSample(samples, i)) maxFlow = max(maxFlow, samples.flowGramsPerSec(i))
    val lastMs = if (samples.size > 0) samples.offsetMs(samples.size - 1) else 0
    val end = finalWeightGrams ?: (samples.size - 1 downTo 0).firstOrNull { validSample(samples, it) }?.let { samples.weightGrams(it).toDouble() } ?: 0.0
    val sb = StringBuilder("무게 그래프. ${spokenMinSec(lastMs / 1000L)} 동안 0에서 ${spokenNumber(formatWeight(end, WeightUnit.Gram))} 그램, 최고 유량 초당 ${formatFlow(maxFlow.toDouble(), WeightUnit.Gram)} 그램")
    if (recipe != null) {
        val steps = markerSteps(recipe)
        sb.append(", 단계 ${steps.size}개 중 ${steps.count { summaryStepReached(finalWeightGrams, recipe.steps[it].targetGrams) }}개 도달")
    }
    for (g in samples.gaps()) sb.append(", 값 없는 구간 ${spokenMinSec(g.first / 1000L)}부터 ${spokenMinSec(g.last / 1000L)}")
    return sb.toString()
}

/** S10 유량 모드 낭독(ui-design 8.2): "유량 그래프. 3분 4초 동안 최고 초당 9.0 그램" + 값 없는 구간. */
fun flowChartSpoken(samples: SampleBuffer): String {
    var maxFlow = 0f
    for (i in 0 until samples.size) if (validSample(samples, i)) maxFlow = max(maxFlow, samples.flowGramsPerSec(i))
    val lastMs = if (samples.size > 0) samples.offsetMs(samples.size - 1) else 0
    val sb = StringBuilder("유량 그래프. ${spokenMinSec(lastMs / 1000L)} 동안 최고 초당 ${formatFlow(maxFlow.toDouble(), WeightUnit.Gram)} 그램")
    for (g in samples.gaps()) sb.append(", 값 없는 구간 ${spokenMinSec(g.first / 1000L)}부터 ${spokenMinSec(g.last / 1000L)}")
    return sb.toString()
}

internal val FLOW_TICKS = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 50.0)
const val FLOW_AXIS_MIN = 10.0
const val FLOW_AXIS_CAP = 20.0

/** 유량 모드 y 끝(ui-design 4.4): 0~10 g/s에서 시작해 최대 유량이 끝의 90%를 넘으면 1.5배를 눈금 배수로 올려 늘리고 20 g/s에서 멈춘다. */
fun flowAxisEnd(maxFlow: Double, yAxisDp: Float, minSpacingDp: Float): Double {
    var end = FLOW_AXIS_MIN
    while (maxFlow > end * 0.9 && end < FLOW_AXIS_CAP) {
        val step = tickStepWithin(end, yAxisDp, minSpacingDp, FLOW_TICKS)
        end = min(FLOW_AXIS_CAP, ceil(end * 1.5 / step) * step)
    }
    return end
}

/** 유량 모드 범례: 실선 견본 + "실제"(ui-design 4.4). */
@Composable
internal fun FlowLegend(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(24.dp, 12.dp).drawWithCache { onDrawBehind { drawLine(cs.primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.5.dp.toPx(), StrokeCap.Round) } })
        Spacer(Modifier.width(4.dp))
        Text("실제", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
}

/** 리본 요약판: 정지 그림, 머리 대신 마지막 표본 자리에 지름 12 dp primary 원. flowMode(S10만)면 유량 실선 2.5 dp(굵기 변화 없음, 단계 표식·목표선 없음). */
@Composable
fun SummaryChart(samples: SampleBuffer, recipe: Recipe?, finalWeightGrams: Double?, modifier: Modifier = Modifier, flowMode: Boolean = false, chartHeight: androidx.compose.ui.unit.Dp = 200.dp) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant, fontFeatureSettings = "tnum")
    val spoken = remember(samples, recipe, finalWeightGrams, flowMode) { if (flowMode) flowChartSpoken(samples) else summaryChartSpoken(samples, recipe, finalWeightGrams) }
    val markers = if (flowMode) null else recipe?.let { markerSteps(it).size }
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken; if (markers != null) markerCount = markers }) {
        if (flowMode) FlowLegend(Modifier.fillMaxWidth()) else Legend(recipe != null, Modifier.fillMaxWidth())
        Spacer(
            Modifier.fillMaxWidth().height(chartHeight).drawWithCache {   // 스크롤 열 안이라 heightIn(min)은 높이 0(Spacer는 고정 높이만 채운다)
                val dp = density
                val w = size.width; val h = size.height
                val labelH = measurer.measure("0", labelStyle).size.height.toFloat()
                val axisX = measurer.measure("8888", labelStyle).size.width + 8.dp.toPx()
                val axisY = h - (labelH + 6.dp.toPx())
                val pad = 6.dp.toPx()
                val dataLeft = axisX + pad; val dataTop = pad
                val dataW = max(1f, w - pad - dataLeft); val dataH = max(1f, axisY - pad - dataTop)
                val n = samples.size
                var maxV = 0.0
                fun value(i: Int) = if (flowMode) samples.flowGramsPerSec(i) else samples.weightGrams(i)
                for (i in 0 until n) if (validSample(samples, i)) maxV = max(maxV, value(i).toDouble())
                val lastSec = if (n > 0) samples.offsetMs(n - 1) / 1000.0 else 0.0
                val ySpacing = max(40f, labelH * 2 / dp)
                val (xEnd, yWeightEnd) = chartRange(lastSec, if (flowMode) 0.0 else maxV, recipe, false, dataH / dp, ySpacing)
                val yEnd = if (flowMode) flowAxisEnd(maxV, dataH / dp, ySpacing) else yWeightEnd
                val xStep = chooseTickStep(xEnd, dataW / dp, max(40f, (measurer.measure("600", labelStyle).size.width + 8.dp.toPx()) / dp), SEC_TICKS)
                val yStep = tickStepWithin(yEnd, dataH / dp, ySpacing, if (flowMode) FLOW_TICKS else G_TICKS)
                fun px(ms: Float) = dataLeft + (ms / 1000f / xEnd.toFloat()) * dataW
                fun py(g: Float) = dataTop + dataH - (max(0f, g) / yEnd.toFloat()) * dataH
                val gridX = FloatArray((xEnd / xStep).toInt() + 1) { px((it * xStep * 1000).toFloat()) }
                val gridY = FloatArray((yEnd / yStep).toInt() + 1) { dataTop + dataH - ((it * yStep) / yEnd).toFloat() * dataH }
                fun num(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                val unitG = measurer.measure(if (flowMode) "g/s" else "g", labelStyle); val unitS = measurer.measure("s", labelStyle)
                val xLabels = ArrayList<Pair<Float, TextLayoutResult>>()
                var prevLeft = w - unitS.size.width
                for (k in gridX.indices.reversed()) {   // 오른쪽(큰 값)부터 앞 상자와 4 dp 안이면 뺀다
                    val t = measurer.measure(num(k * xStep), labelStyle)
                    if (gridX[k] + t.size.width / 2f + 4.dp.toPx() <= prevLeft) { xLabels += gridX[k] to t; prevLeft = gridX[k] - t.size.width / 2f }
                }
                val ny = gridY.size - 1
                val yLay = (0..ny).map { measurer.measure(num((ny - it) * yStep), labelStyle) }
                val yTops = FloatArray(ny + 2) { if (it == 0) 0f else max(0f, gridY[ny - it + 1] - yLay[it - 1].size.height / 2f) }
                val yHs = FloatArray(ny + 2) { if (it == 0) unitG.size.height.toFloat() else yLay[it - 1].size.height.toFloat() }
                val yLabels = visibleLabelIndices(yTops, yHs).filter { it > 0 }.map { yTops[it] to yLay[it - 1] }

                // 리본: 1 dp 칸마다 마지막 표본, 값 없는 구간에서 끊는다(솎기 전 이웃 표본으로 판정)
                val idx = thinnedSampleIndices(n) { ((px(samples.offsetMs(it).toFloat()) - dataLeft) / dp).toInt() }
                val ribbon = Path(); val centerline = Path()
                val caps = ArrayList<Float>()
                var seg = ArrayList<Int>()
                fun flush() {
                    if (seg.isEmpty()) return
                    val pts = seg.map { Offset(px(samples.offsetMs(it).toFloat()), py(value(it))) }
                    val ws = seg.map { if (flowMode) 1.25.dp.toPx() else ribbonWidthDp(samples.flowGramsPerSec(it)).dp.toPx() / 2f }
                    centerline.moveTo(pts[0].x, pts[0].y)
                    for (k in 1 until pts.size) centerline.lineTo(pts[k].x, pts[k].y)
                    if (flowMode && pts.size == 1) centerline.lineTo(pts[0].x, pts[0].y)   // 한 점도 둥근 끝으로 보이게
                    if (!flowMode && pts.size > 1) {
                        val up = ArrayList<Offset>(); val down = ArrayList<Offset>()
                        for (k in pts.indices) {
                            val a = pts[max(0, k - 1)]; val b = pts[min(pts.lastIndex, k + 1)]
                            val l = max(1e-3f, hypot(b.x - a.x, b.y - a.y))
                            val nx = -(b.y - a.y) / l * ws[k]; val ny2 = (b.x - a.x) / l * ws[k]
                            up += Offset(pts[k].x + nx, pts[k].y + ny2); down += Offset(pts[k].x - nx, pts[k].y - ny2)
                        }
                        ribbon.moveTo(up[0].x, up[0].y)
                        for (k in 1 until up.size) ribbon.lineTo(up[k].x, up[k].y)
                        for (k in down.indices.reversed()) ribbon.lineTo(down[k].x, down[k].y)
                        ribbon.close()
                    }
                    caps += listOf(pts.first().x, pts.first().y, ws.first(), pts.last().x, pts.last().y, ws.last())
                    seg = ArrayList()
                }
                var prev = -1
                for (i in idx) {
                    if (!validSample(samples, i)) { flush(); prev = -1; continue }
                    if (prev >= 0) {
                        var j = prev + 1
                        while (j <= i && validSample(samples, j) && samples.offsetMs(j) - samples.offsetMs(j - 1) <= 500) j++
                        if (j <= i) flush()
                    }
                    seg += i; prev = i
                }
                flush()
                val lastIdx = (n - 1 downTo 0).firstOrNull { validSample(samples, it) }
                val lastPoint = if (flowMode) null else lastIdx?.let { Offset(px(samples.offsetMs(it).toFloat()), py(samples.weightGrams(it))) }
                // 유량 모드: y 끝을 넘어 위에서 잘린 자리(가장자리에 짧은 가로 표시)
                val clippedX = if (flowMode) (0 until n).filter { validSample(samples, it) && samples.flowGramsPerSec(it) > yEnd }.map { px(samples.offsetMs(it).toFloat()) } else emptyList()
                val bands = ArrayList<Float>()
                if (n > 0) for (g in samples.gaps()) {
                    val l = px(g.first.toFloat()); val r = px(g.last.toFloat())
                    if (r - l >= 8.dp.toPx()) { bands += l; bands += r }
                }
                // 목표선과 표식(기다리기 단계 꼭짓점은 표식 없음)
                val target = Path(); val vx = ArrayList<Float>(); val segDash = ArrayList<PathEffect>()
                recipe?.takeIf { !flowMode }?.let { r ->
                    target.moveTo(px(0f), py(0f)); vx += px(0f); vx += py(0f)
                    var len = 0f
                    r.steps.forEach { s ->
                        val x = px(s.targetAtSec * 1000f); val y = py(s.targetGrams.toFloat())
                        segDash += PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), len)
                        len += hypot(x - vx[vx.size - 2], y - vx[vx.size - 1])
                        target.lineTo(x, y); vx += x; vx += y
                    }
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                val drop = Path().also { dropPath(it, DROP_BASE_D, 10.dp.toPx() / 100f, 14.dp.toPx() / 140f) }
                val mSteps = recipe?.takeIf { !flowMode }?.let { markerSteps(it) } ?: IntArray(0)
                onDrawBehind {
                    for (x in gridX) drawLine(cs.outlineVariant, Offset(x, dataTop), Offset(x, axisY), 1.dp.toPx())
                    for (y in gridY) drawLine(cs.outlineVariant, Offset(axisX, y), Offset(w, y), 1.dp.toPx())
                    var b = 0
                    while (b < bands.size) { drawRect(cs.surfaceContainerHighest, Offset(bands[b], 0f), Size(bands[b + 1] - bands[b], axisY)); b += 2 }
                    drawLine(cs.outline, Offset(axisX, axisY), Offset(w, axisY), 1.dp.toPx())
                    drawLine(cs.outline, Offset(axisX, 0f), Offset(axisX, axisY), 1.dp.toPx())
                    for (x in gridX) drawLine(cs.onSurfaceVariant, Offset(x, axisY), Offset(x, axisY + 4.dp.toPx()), 1.dp.toPx())
                    for ((x, t) in xLabels) drawText(t, topLeft = Offset(x - t.size.width / 2f, axisY + 6.dp.toPx()))
                    for (y in gridY) drawLine(cs.onSurfaceVariant, Offset(axisX - 4.dp.toPx(), y), Offset(axisX, y), 1.dp.toPx())
                    for ((top, t) in yLabels) drawText(t, topLeft = Offset(axisX - 6.dp.toPx() - t.size.width, top))
                    drawText(unitG, topLeft = Offset(0f, 0f))
                    drawText(unitS, topLeft = Offset(w - unitS.size.width, axisY + 6.dp.toPx()))
                    clipRect(axisX + 0.5f, 0f, w, axisY - 0.5f) {
                        if (flowMode) {
                            drawPath(centerline, cs.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            for (x in clippedX) drawLine(cs.outline, Offset(x - 3.dp.toPx(), 1.dp.toPx()), Offset(x + 3.dp.toPx(), 1.dp.toPx()), 2.dp.toPx())
                        } else drawPath(ribbon, cs.primary)
                        var i = 0
                        while (i < caps.size && !flowMode) { drawCircle(cs.primary, caps[i + 2], Offset(caps[i], caps[i + 1])); i += 3 }
                        if (recipe != null && !flowMode) {
                            clipPath(ribbon, ClipOp.Difference) { drawPath(target, cs.surface, style = Stroke(4.dp.toPx(), pathEffect = dash)) }
                            drawPath(target, cs.outline, style = Stroke(2.dp.toPx(), pathEffect = dash))
                            drawPath(centerline, cs.primary, style = Stroke(ribbonWidthDp(0f).dp.toPx(), join = StrokeJoin.Round))
                            for (k in 1..recipe.steps.lastIndex) if (recipe.isWaitStep(k))   // 기다리기 구간 덧그림
                                drawLine(cs.outline, Offset(vx[2 * k], vx[2 * k + 1]), Offset(vx[2 * k + 2], vx[2 * k + 3]), 2.dp.toPx(), pathEffect = segDash[k])
                        }
                        if (lastPoint != null) drawCircle(cs.primary, 6.dp.toPx(), lastPoint)   // 머리 대신 지름 12 dp 원
                    }
                    for (k in mSteps) marker(drop, vx[2 * k + 2], vx[2 * k + 3], summaryStepReached(finalWeightGrams, recipe!!.steps[k].targetGrams), cs.surface, cs.onSurfaceVariant, cs.tertiary)
                }
            },
        )
    }
}
