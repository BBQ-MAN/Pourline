package com.timemoredot.app.ui.brew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.ui.brew.shape.MORPH_POINTS
import com.timemoredot.app.ui.brew.shape.chooseTickStep
import com.timemoredot.app.ui.brew.shape.headTailDp
import com.timemoredot.app.ui.brew.shape.lerpPoints
import com.timemoredot.app.ui.brew.shape.resampleByArcLength
import com.timemoredot.app.ui.brew.shape.ribbonWidthDp
import com.timemoredot.app.ui.brew.shape.thinnedSampleIndices
import com.timemoredot.app.ui.brew.shape.vesselWallPolyline
import com.timemoredot.app.ui.brew.shape.visibleLabelIndices
import com.timemoredot.app.ui.theme.AppMotion
import com.timemoredot.app.ui.theme.DROP_BASE_D
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.app.ui.theme.dropPath
import com.timemoredot.app.ui.theme.vesselPath
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.TimerSnapshot
import com.timemoredot.brew.gaps
import com.timemoredot.brew.isWaitStep
import com.timemoredot.protocol.GRAMS_PER_OUNCE
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 테스트용 의미 속성: 서버 → 그래프 전환을 시작한 횟수(접근성 낭독과 무관). */
val MorphStartsKey = SemanticsPropertyKey<Int>("MorphStarts")
var SemanticsPropertyReceiver.morphStarts by MorphStartsKey
/** 영점 확인 모션이 재생 중인지(테스트가 계기 없이 다시 재생하지 않는지 확인한다). */
val TareMotionKey = SemanticsPropertyKey<Boolean>("TareMotion")
var SemanticsPropertyReceiver.tareMotion by TareMotionKey

/** 테스트용 의미 속성: 그래프에 그린 단계 표식 수(기다리기 단계 제외, 접근성 낭독과 무관). */
val MarkerCountKey = SemanticsPropertyKey<Int>("MarkerCount")
var SemanticsPropertyReceiver.markerCount by MarkerCountKey

/** 표식을 그리는 단계(7.1 isWaitStep인 기다리기 단계는 빼고). 그래프·서버가 같이 쓴다. */
fun markerSteps(recipe: Recipe): IntArray = recipe.steps.indices.filter { !recipe.isWaitStep(it) }.toIntArray()

/** 모양 자리 최소 높이 dp(3.1 6번·4.4): 범례 + 그림 영역 최소 max(80, y 최소 간격 × 2) + x축 글자 + 여백. */
fun shapeAreaMinDp(fontScale: Float): Float {
    val label = 16f * fontScale                          // labelMedium 줄 높이
    val legendLines = if (fontScale >= 1.5f) 2 else 1    // shortcut: 360 dp 폭에서 범례 두 칸이 한 줄에 드는지 어림, 범례가 바뀌면 재서 정한다
    return legendLines * label + 4f + max(80f, 2 * max(40f, 2 * label)) + label + 6f + 12f
}

internal val G_TICKS = doubleArrayOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0)
private val OZ_TICKS = doubleArrayOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0)
internal val SEC_TICKS = doubleArrayOf(15.0, 30.0, 60.0, 120.0, 300.0, 600.0)

private fun BrewPhase.idleLike() = this == BrewPhase.Idle || this == BrewPhase.WaitingForPour

/**
 * x·y 끝(4.4 축 최소 범위 + 단계형 늘리기). 데이터로 다시 계산해도 늘기만 한다(최대 경과·최대 무게가 줄지 않으므로).
 * 늘리는 기준은 끝의 90%와 레시피 목표의 105% 중 큰 값: 목표대로 따라간 추출이 마지막 순간에 배율이 바뀌지 않게.
 */
fun chartRange(lastSec: Double, maxValue: Double, recipe: Recipe?, ounce: Boolean, yAxisDp: Float, yMinSpacingDp: Float): Pair<Double, Double> {
    val lastTarget = recipe?.steps?.last()?.targetAtSec?.toDouble()
    var xEnd = if (lastTarget != null) max(60.0, lastTarget * 1.1) else 60.0
    while (lastSec > max(xEnd * 0.9, (lastTarget ?: 0.0) * 1.05)) xEnd = ceil(xEnd * 1.5 / 30.0) * 30.0
    val minY = if (ounce) 4.0 else 100.0
    val water = recipe?.waterGrams?.let { if (ounce) it / GRAMS_PER_OUNCE else it }
    var yEnd = if (water != null) max(minY, water * 1.1) else minY
    while (maxValue > max(yEnd * 0.9, (water ?: 0.0) * 1.05)) {
        val step = tickStepWithin(yEnd, yAxisDp, yMinSpacingDp, if (ounce) OZ_TICKS else G_TICKS)
        yEnd = ceil(yEnd * 1.5 / step) * step
    }
    return xEnd to yEnd
}

/** chooseTickStep이 고른 간격이 축 끝보다 크면(그림 영역이 짧을 때) 끝 이하의 가장 큰 후보: 0이 아닌 눈금이 하나는 생긴다. */
fun tickStepWithin(end: Double, axisDp: Float, minSpacingDp: Float, candidates: DoubleArray): Double {
    val s = chooseTickStep(end, axisDp, minSpacingDp, candidates)
    return if (s <= end) s else candidates.lastOrNull { it <= end } ?: candidates.first()
}

/**
 * S1 모양 자리(3.1 6번): Idle·WaitingForPour = 서버 대기판(4.10), Running·Paused = 리본 그래프(4.4). Idle → Running은 motion.morph 전환.
 * readout은 이 컴포저블이 직접 수집하고 그리기 캐시·그리기 단계에서만 읽는다(10 Hz 재구성 없음).
 */
@Composable
fun ShapeArea(
    phase: BrewPhase, recipe: Recipe?, stepIndex: Int?, lastStepReached: Boolean, readout: StateFlow<BrewReadout>, samples: SampleBuffer,
    unit: WeightUnit, tareTrigger: Int, modifier: Modifier = Modifier,
    // 지금 이어지는 끊김 구간 띠의 끝(앱 타이머 경과)을 정하는 기존 계약 값. clock이 없으면 그 띠를 그리지 않는다
    timer: TimerSnapshot = TimerSnapshot.Zero, clock: MonotonicClock? = null,
    // 머리(데이터 좌표 x = ms, y = g). 매개변수는 테스트가 따라가기 지연을 읽게 하려는 것
    head: Animatable<Offset, AnimationVector2D> = remember { Animatable(Offset.Zero, Offset.VectorConverter, Offset(1f, 0.01f)) },
) {
    val rState = readout.collectAsStateWithLifecycle()
    // 캐시는 표본 수가 바뀔 때만, 그리기는 멈춘 값 여부가 바뀔 때만 다시(10 Hz 측정값 자체로는 무효화하지 않는다)
    val countState = remember(rState) { androidx.compose.runtime.derivedStateOf { rState.value.sampleCount } }
    val staleState = remember(rState) { androidx.compose.runtime.derivedStateOf { rState.value.reading is ReadingUi.Stale } }
    // 끊김이 이어지는 동안 마지막 정상 표본 → 지금(앱 타이머 경과)도 값 없는 구간 띠다(4.4). 끊긴 동안에만 1 s마다 바뀐다
    val discState = remember(rState) { androidx.compose.runtime.derivedStateOf { (rState.value.reading as? ReadingUi.Stale)?.cause == StaleReason.Disconnected } }
    val disc = clock != null && discState.value
    val gapNow = if (clock != null) rememberNow(clock, 1_000, disc && timer.runningSinceNanos != null) else 0L
    val openGap = rememberUpdatedState(if (disc) timer.elapsedNanos(gapNow) / 1_000_000 else -1L)
    val running = phase == BrewPhase.Running || phase == BrewPhase.Paused || phase == BrewPhase.Finished   // Finished(저장 중·실패)도 멈춘 그래프
    // 전환 계기: 이전 phase를 기억해 Idle·WaitingForPour → Running일 때만. 처음 구성(회전·복귀)은 재생하지 않는다
    val lastPhase = remember { arrayOf(phase) }
    val morphCount = remember { intArrayOf(0) }
    if (lastPhase[0] != phase) {
        if (lastPhase[0].idleLike() && phase == BrewPhase.Running) morphCount[0]++
        lastPhase[0] = phase
    }
    val startId = morphCount[0]
    val morph = remember(startId) { Animatable(if (startId == 0) 1f else 0f) }
    LaunchedEffect(morph) { if (morph.value < 1f) morph.animateTo(1f, AppMotion.morph) }

    // 꼬리 유량 f. 머리와 함께 새 표본 때만 목표를 바꾼다
    val tail = remember { Animatable(0f) }
    val headAngle = remember { floatArrayOf(Float.NaN) }
    LaunchedEffect(samples) {
        var seeded = false
        snapshotFlow { rState.value.sampleCount }.distinctUntilChanged().collect { n ->
            if (n == 0 || n > samples.size) { seeded = false; head.snapTo(Offset.Zero); tail.snapTo(0f); headAngle[0] = Float.NaN; return@collect }
            // 머리는 마지막 유효 표본에 있다: 과부하·끊김 표본에서는 목표를 바꾸지 않는다(멈춘 값, 4.4·6.6.2 규칙 7)
            val i = if (validSample(samples, n - 1)) n - 1
            else if (seeded) return@collect
            else (n - 2 downTo 0).firstOrNull { validSample(samples, it) } ?: return@collect
            val target = Offset(samples.offsetMs(i).toFloat(), samples.weightGrams(i))
            // 각자 자식 코루틴: 새 표본이 오면 Animatable이 앞 애니메이션을 끊고 속도를 이어 받는다(6.6.2 규칙 3). 수집은 기다리지 않는다
            val first = !seeded
            seeded = true
            launch { launchFollow(!first, head, target) }
            launch { if (first) tail.snapTo(samples.flowGramsPerSec(i)) else tail.animateTo(samples.flowGramsPerSec(i), AppMotion.calm) }
        }
    }
    // 단계 표식 채움(motion.drop)과 목표선 현재 구간 굵기(motion.calm)
    val stepQ = remember { Animatable(1f) }
    val dropScale = remember { Animatable(1f) }
    val lastStep = remember { intArrayOf(stepIndex ?: 0) }
    LaunchedEffect(stepIndex, lastStepReached) {
        val s = stepIndex ?: 0
        if (running && (s > lastStep[0] || lastStepReached)) {
            if (s > lastStep[0]) { stepQ.snapTo(0f) }
            dropScale.snapTo(0.6f)
            coroutineScope {
                launch { stepQ.animateTo(1f, AppMotion.calm) }
                launch { dropScale.animateTo(1f, AppMotion.drop) }
            }
        }
        lastStep[0] = s
    }
    // 영점 확인 물방울·물결 고리(4.10). 계기는 화면이 받은 Announce(TareDone)
    val fall = remember { Animatable(1f) }
    val ripple = remember { Animatable(1f) }
    var tarePlaying by remember { mutableStateOf(false) }
    // 처음 구성될 때의 계기 값은 이미 지난 일이다: 빈 상태 → 다시 연결로 이 자리가 돌아와도 재생하지 않는다
    val seenTare = remember { intArrayOf(tareTrigger) }
    LaunchedEffect(tareTrigger) {
        if (tareTrigger <= seenTare[0]) return@LaunchedEffect
        seenTare[0] = tareTrigger
        tarePlaying = true
        fall.snapTo(0f); ripple.snapTo(0f)
        fall.animateTo(1f, AppMotion.fall)
        ripple.animateTo(1f, AppMotion.ripple)
        tarePlaying = false
    }

    val cs = MaterialTheme.colorScheme
    val colors = ChartColors(cs.primary, cs.outline, cs.outlineVariant, cs.onSurfaceVariant, cs.tertiary, cs.surface, cs.surfaceContainerHighest)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant, fontFeatureSettings = "tnum")
    val guideStyle = MaterialTheme.typography.bodyMedium
    val inputs = rememberUpdatedState(ChartInputs(phase, recipe, stepIndex, lastStepReached, unit))
    val playing = rememberUpdatedState(tarePlaying)
    val morphValue = { morph.value }

    Column(modifier) {
        if (running) Legend(recipe != null, Modifier.fillMaxWidth().graphicsLayer { alpha = fadeIn(morphValue()) })
        val spoken = if (running) chartSpoken(rState, samples, recipe, unit) else null
        Box(Modifier.weight(1f).fillMaxWidth()) {
          Spacer(
            Modifier.fillMaxSize()
                .clearAndSetSemantics {
                    morphStarts = startId
                    tareMotion = tarePlaying
                    if (running && recipe != null) markerCount = markerSteps(recipe).size
                    if (spoken != null) contentDescription = spoken   // 서버는 의미 노드 없음(장식)
                }
                .drawWithCache {
                    val c = buildChartCache(inputs.value, minOf(countState.value, samples.size), samples, measurer, labelStyle, guideStyle, openGap.value)
                    onDrawBehind { drawShapeArea(c, colors, inputs.value, staleState.value, head, tail.value, morph.value, stepQ.value, dropScale.value, fall.value, ripple.value, playing.value, headAngle) }
                },
          )
          // 안내문은 서버 아래 끝 + 8 dp(4.10). 서버 자리는 캐시와 같은 vesselBox로 계산한다
          if (phase == BrewPhase.Idle) Text(
            GUIDE_TEXT,
            Modifier.fillMaxWidth().layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0))
                val box = vesselBox(c.maxWidth.toFloat(), c.maxHeight.toFloat(), p.height.toFloat(), density)
                val y = if (box != null) (box.first + box.second + 8.dp.toPx()).toInt() else 0
                layout(p.width, c.maxHeight) { p.place(0, y.coerceAtMost(c.maxHeight - p.height).coerceAtLeast(0)) }
            },
            style = guideStyle, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          )
        }
    }
}

private const val GUIDE_TEXT = "영점을 맞추고 시작하세요"

/**
 * 서버 상자(4.10) (위, 높이)를 px로: 모양 자리 위쪽 끝 + 8 dp에 고정해 Idle·WaitingForPour에서 y가 같다.
 * 높이는 min(164 dp, h − 16 dp − 안내문 자리)이고 96 dp보다 작으면 null(숨김). 안내문 자리는 대기 중 내내 비워 둔다.
 */
private fun vesselBox(w: Float, h: Float, guideH: Float, dp: Float): Pair<Float, Float>? {
    val vH = min(164f * dp, h - 16f * dp - guideH - 8f * dp)
    if (vH < 96f * dp || w <= 0f) return null
    return 8f * dp to vH
}

/** 한 표본에서 50 g 넘게 바뀌면 순간 이동(6.6.2 규칙 5), 아니면 motion.follow. 모션 줄이기면 Compose가 바로 끝 값으로 보낸다. */
private suspend fun launchFollow(seeded: Boolean, head: Animatable<Offset, *>, target: Offset) {
    if (!seeded || abs(target.y - head.value.y) > 50f) head.snapTo(target)
    else head.animateTo(target, AppMotion.follow)
}

internal fun validSample(samples: SampleBuffer, i: Int) = samples.flags(i) == 0 && !samples.weightGrams(i).isNaN()

private fun fadeIn(p: Float) = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
private fun fadeOut(p: Float) = (1f - p * 2f).coerceIn(0f, 1f)

private data class ChartInputs(val phase: BrewPhase, val recipe: Recipe?, val stepIndex: Int?, val lastStepReached: Boolean, val unit: WeightUnit)
private class ChartColors(val primary: Color, val outline: Color, val grid: Color, val muted: Color, val reached: Color, val surface: Color, val band: Color)

/** 그래프 낭독(8.2): 5초마다만 바뀐다. */
@Composable
private fun chartSpoken(rState: State<BrewReadout>, samples: SampleBuffer, recipe: Recipe?, unit: WeightUnit): String {
    var bucket by remember { mutableStateOf(-1) }
    LaunchedEffect(samples) {
        snapshotFlow { rState.value.sampleCount }.collect { n -> bucket = if (n == 0 || n > samples.size) -1 else samples.offsetMs(n - 1) / 5_000 }
    }
    val n = samples.size
    if (bucket < 0 || n == 0) return "무게 그래프"
    val i = n - 1
    val w = samples.weightGrams(i).takeIf { !it.isNaN() }?.toDouble() ?: 0.0
    val target = recipe?.let { ", 목표 ${shortGrams(it.steps.last().targetGrams)} 그램" } ?: ""
    return "무게 그래프. ${spokenMinSec(samples.offsetMs(i) / 1000L)}에 ${spokenNumber(formatWeight(w, unit))} ${unitSpoken(unit)}$target"
}

@Composable
internal fun Legend(withTarget: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    FlowRow(modifier.padding(bottom = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.m, Alignment.End)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(24.dp, 12.dp).drawWithCache {   // 굵기 2.5 → 8 dp 리본 견본
                val p = Path().apply {
                    moveTo(0f, size.height / 2 - 1.25.dp.toPx()); lineTo(size.width, size.height / 2 - 4.dp.toPx())
                    lineTo(size.width, size.height / 2 + 4.dp.toPx()); lineTo(0f, size.height / 2 + 1.25.dp.toPx()); close()
                }
                onDrawBehind { drawPath(p, cs.primary) }
            })
            Spacer(Modifier.width(Space.xs))
            Text("실제(굵기 = 유량)", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        if (withTarget) Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(26.dp, 12.dp).drawWithCache {   // 점선 견본(대시 8·간격 6)
                val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                onDrawBehind { drawLine(cs.outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = dash) }
            })
            Spacer(Modifier.width(Space.xs))
            Text("목표", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

/** 캐시 블록에서 만든 그리기 재료. 새 표본·축 범위·크기·phase가 바뀔 때만 다시 만든다. */
private class ChartCache(
    val axisX: Float, val axisY: Float, val w: Float, val h: Float,
    val dataLeft: Float, val dataTop: Float, val dataW: Float, val dataH: Float, val xEnd: Double, val yEnd: Double, val unitDiv: Double,
    val gridX: FloatArray, val gridY: FloatArray, val xLabels: List<Pair<Float, TextLayoutResult>>, val yLabels: List<Pair<Float, TextLayoutResult>>,
    val unitG: TextLayoutResult, val unitS: TextLayoutResult,
    val ribbon: Path, val centerline: Path, val caps: FloatArray, val lastPoint: Offset?, val lastWidth: Float, val prevPoint: Offset?,
    val bands: FloatArray,
    val target: Path, val vertices: FloatArray, val segDash: List<PathEffect>, val markerSteps: IntArray,
    val vessel: VesselGeo?, val morphFrom: FloatArray, val morphTo: FloatArray, val morphOut: FloatArray, val morphPath: Path,
    val headPath: Path, val dropPathS: Path, val dash: PathEffect, val dashLevel: PathEffect,
)

private class VesselGeo(
    val left: Float, val top: Float, val width: Float, val height: Float, val wall: Path, val closed: Path,
    val levelY: Float?, val markers: FloatArray, val markerSteps: IntArray, val labels: List<Pair<Offset, TextLayoutResult>>,
)

private fun androidx.compose.ui.draw.CacheDrawScope.buildChartCache(
    inp: ChartInputs, count: Int, samples: SampleBuffer, measurer: androidx.compose.ui.text.TextMeasurer, labelStyle: TextStyle, guideStyle: TextStyle,
    openGapEndMs: Long,
): ChartCache {
    val ounce = inp.unit == WeightUnit.Ounce
    val unitDiv = if (ounce) GRAMS_PER_OUNCE else 1.0
    val dpx = density
    val w = size.width
    val h = size.height
    // 축 글자 크기(글꼴 배율 반영)로 여백과 눈금 간격을 정한다
    val labelH = measurer.measure("0", labelStyle).size.height.toFloat()
    val maxYLabel = measurer.measure(if (ounce) "88.8" else "8888", labelStyle).size.width.toFloat()
    val axisX = maxYLabel + 8.dp.toPx()
    val axisY = h - (labelH + 6.dp.toPx())
    val pad = 6.dp.toPx()
    val dataLeft = axisX + pad
    val dataTop = pad
    val dataW = max(1f, w - pad - dataLeft)
    val dataH = max(1f, axisY - pad - dataTop)
    var lastSec = 0.0
    var maxV = 0.0
    for (i in 0 until count) {
        val wt = samples.weightGrams(i)
        if (!wt.isNaN()) maxV = max(maxV, wt / unitDiv)
    }
    if (count > 0) lastSec = samples.offsetMs(count - 1) / 1000.0
    val (xEnd, yEnd) = chartRange(lastSec, maxV, inp.recipe, ounce, dataH / dpx, max(40f, labelH * 2 / dpx))
    val xStep = chooseTickStep(xEnd, dataW / dpx, max(40f, (measurer.measure("600", labelStyle).size.width + 8.dp.toPx()) / dpx), SEC_TICKS)
    val yStep = tickStepWithin(yEnd, dataH / dpx, max(40f, labelH * 2 / dpx), if (ounce) OZ_TICKS else G_TICKS)
    fun px(ms: Float) = dataLeft + (ms / 1000f / xEnd.toFloat()) * dataW
    fun py(g: Float) = dataTop + dataH - (max(0f, g / unitDiv.toFloat()) / yEnd.toFloat()) * dataH
    val nx = (xEnd / xStep).toInt()
    val ny = (yEnd / yStep).toInt()
    val gridX = FloatArray(nx + 1) { px((it * xStep * 1000).toFloat()) }
    val gridY = FloatArray(ny + 1) { dataTop + dataH - ((it * yStep) / yEnd).toFloat() * dataH }
    fun num(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    val unitG = measurer.measure(if (ounce) "oz" else "g", labelStyle)
    val unitS = measurer.measure("s", labelStyle)
    // x 글자(4.4 단위 글자 자리): 단위 "s" 상자(x 글자 줄 오른쪽 끝)가 첫 상자. 오른쪽(큰 값)부터 앞 상자와 4 dp 안으로 붙거나 겹치면 뺀다
    val xLabels = ArrayList<Pair<Float, TextLayoutResult>>(nx + 1)
    var prevLeft = w - unitS.size.width
    for (n in nx downTo 0) {
        val t = measurer.measure(num(n * xStep), labelStyle)
        val half = t.size.width / 2f
        if (gridX[n] + half + 4.dp.toPx() <= prevLeft) { xLabels += gridX[n] to t; prevLeft = gridX[n] - half }
    }
    // y 글자: 단위 상자(0, 0)가 첫 상자. 위(큰 값)부터 그리고 앞 상자와 겹치는 글자는 뺀다(눈금 표시·격자선은 남김).
    // 맨 위 글자는 모양 자리 밖(범례)으로 나가지 않게 위 끝을 0에 맞춘다
    val yLayouts = (0..ny).map { measurer.measure(num((ny - it) * yStep), labelStyle) }
    val yTops = FloatArray(ny + 2) { if (it == 0) 0f else max(0f, gridY[ny - it + 1] - yLayouts[it - 1].size.height / 2f) }
    val yHeights = FloatArray(ny + 2) { if (it == 0) unitG.size.height.toFloat() else yLayouts[it - 1].size.height.toFloat() }
    val yLabels = visibleLabelIndices(yTops, yHeights).filter { it > 0 }.map { yTops[it] to yLayouts[it - 1] }

    // 리본: 1 dp 칸마다 마지막 표본, 값 없는 구간·플래그 표본에서 끊는다
    val idx = thinnedSampleIndices(count) { ((px(samples.offsetMs(it).toFloat()) - dataLeft) / dpx).toInt() }
    val ribbon = Path()
    val centerline = Path()
    val caps = ArrayList<Float>()
    var seg = ArrayList<Int>()
    fun flush() {
        if (seg.isEmpty()) return
        val pts = seg.map { Offset(px(samples.offsetMs(it).toFloat()), py(samples.weightGrams(it))) }
        val ws = seg.map { ribbonWidthDp(samples.flowGramsPerSec(it)).dp.toPx() / 2f }
        centerline.moveTo(pts[0].x, pts[0].y)
        for (k in 1 until pts.size) centerline.lineTo(pts[k].x, pts[k].y)
        if (pts.size > 1) {
            val up = ArrayList<Offset>(); val down = ArrayList<Offset>()
            for (k in pts.indices) {
                val a = pts[max(0, k - 1)]; val b = pts[min(pts.lastIndex, k + 1)]
                val dx = b.x - a.x; val dy = b.y - a.y
                val l = max(1e-3f, hypot(dx, dy))
                val nxv = -dy / l * ws[k]; val nyv = dx / l * ws[k]
                up += Offset(pts[k].x + nxv, pts[k].y + nyv); down += Offset(pts[k].x - nxv, pts[k].y - nyv)
            }
            ribbon.moveTo(up[0].x, up[0].y)
            for (k in 1 until up.size) ribbon.lineTo(up[k].x, up[k].y)
            for (k in down.indices.reversed()) ribbon.lineTo(down[k].x, down[k].y)
            ribbon.close()
        }
        caps += listOf(pts.first().x, pts.first().y, ws.first(), pts.last().x, pts.last().y, ws.last())
        seg = ArrayList()
    }
    // 값 없는 구간 판정은 솎기 전 이웃 표본으로 한다(gaps 규칙과 같은 500 ms·플래그): 솎은 표본 간격은 긴 축에서 500 ms를 넘어
    // 리본이 점으로 끊겨 보였다
    var prev = -1
    for (i in idx) {
        if (samples.flags(i) != 0 || samples.weightGrams(i).isNaN()) { flush(); prev = -1; continue }
        if (prev >= 0) {
            var j = prev + 1
            while (j <= i && samples.flags(j) == 0 && !samples.weightGrams(j).isNaN() && samples.offsetMs(j) - samples.offsetMs(j - 1) <= 500) j++
            if (j <= i) flush()
        }
        seg += i
        prev = i
    }
    flush()
    val lastIdx = (count - 1 downTo 0).firstOrNull { samples.flags(it) == 0 && !samples.weightGrams(it).isNaN() }
    val lastPoint = lastIdx?.let { Offset(px(samples.offsetMs(it).toFloat()), py(samples.weightGrams(it))) }
    val prevIdx = lastIdx?.let { li -> (li - 1 downTo 0).firstOrNull { samples.flags(it) == 0 && !samples.weightGrams(it).isNaN() } }
    val prevPoint = prevIdx?.let { Offset(px(samples.offsetMs(it).toFloat()), py(samples.weightGrams(it))) }
    val lastWidth = lastIdx?.let { ribbonWidthDp(samples.flowGramsPerSec(it)).dp.toPx() } ?: 0f

    val bandList = ArrayList<Float>()
    if (count > 0) for (g in samples.gaps()) {
        val l = px(g.first.toFloat()); val r = px(g.last.toFloat())
        if (r - l >= 8.dp.toPx()) { bandList += l; bandList += r }
    }
    if (openGapEndMs >= 0 && lastIdx != null) {   // 지금 이어지는 끊김: 표본이 아직 없어 gaps()에 들지 않는다
        val l = px(samples.offsetMs(lastIdx).toFloat()); val r = min(w, px(openGapEndMs.toFloat()))
        if (r - l >= 8.dp.toPx()) { bandList += l; bandList += r }
    }

    // 목표선과 표식(기다리기 단계 꼭짓점은 표식 없음)
    val target = Path()
    val vertices = ArrayList<Float>()
    val segDash = ArrayList<PathEffect>()   // 구간 i를 따로 그릴 때 경로 전체와 대시가 어긋나지 않게 앞 구간 길이만큼 위상을 준다
    inp.recipe?.let { r ->
        target.moveTo(px(0f), py(0f))
        vertices += px(0f); vertices += py(0f)
        var len = 0f
        r.steps.forEach { s ->
            val x = px(s.targetAtSec * 1000f); val y = py(s.targetGrams.toFloat())
            segDash += PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), len)
            len += hypot(x - vertices[vertices.size - 2], y - vertices[vertices.size - 1])
            target.lineTo(x, y); vertices += x; vertices += y
        }
    }

    // 서버(대기판, 4.10)와 전환 시작 윤곽
    val guideH = measurer.measure(GUIDE_TEXT, guideStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = max(1, w.toInt()))).size.height.toFloat()
    val box = vesselBox(w, h, guideH, dpx)
    val vessel = if (box == null) null else {
        val vH = box.second
        val vW = vH * 176f / 164f
        val left = (w - vW) / 2f
        val top = box.first   // 위쪽 끝 고정(Idle·WaitingForPour 같은 y), 안내문은 이 상자 아래 8 dp
        val bottom = top + vH
        val wall = Path().also { vesselPath(it, vW / 100f, vH / 100f, closed = false); it.translate(Offset(left, top)) }
        val closed = Path().also { vesselPath(it, vW / 100f, vH / 100f, closed = true); it.translate(Offset(left, top)) }
        val r = inp.recipe
        fun levelY(g: Double) = bottom - 0.92f * vH * min(1.08f, (g / r!!.waterGrams).toFloat())
        val vm = ArrayList<Float>(); val vs = ArrayList<Int>(); val labels = ArrayList<Pair<Offset, TextLayoutResult>>()
        if (r != null) {
            for (i in markerSteps(r)) { vm += left + vW + 13.dp.toPx(); vm += levelY(r.steps[i].targetGrams); vs += i }
            // 라벨은 위에서부터 그리고 겹치면 건너뛴다(4.10): 위쪽(높은 목표)부터 정렬
            val order = vs.indices.sortedBy { vm[2 * it + 1] }
            val layouts = order.map { measurer.measure("${shortGrams(r.steps[vs[it]].targetGrams)}${NBSP}g", labelStyle) }
            val tops = FloatArray(order.size) { vm[2 * order[it] + 1] - layouts[it].size.height / 2f }
            val hs = FloatArray(order.size) { layouts[it].size.height.toFloat() }
            for (k in visibleLabelIndices(tops, hs)) labels += Offset(left + vW + 26.dp.toPx(), tops[k]) to layouts[k]
        }
        VesselGeo(left, top, vW, vH, wall, closed, r?.let { levelY(it.steps.first().targetGrams) }, vm.toFloatArray(), vs.toIntArray(), labels)
    }
    val morphFrom = vessel?.let { v ->
        val pts = vesselWallPolyline(v.width, v.height)
        for (k in pts.indices) pts[k] += if (k % 2 == 0) v.left else v.top
        resampleByArcLength(pts)
    } ?: FloatArray(MORPH_POINTS * 2)
    val morphTo = resampleByArcLength(floatArrayOf(w, axisY, axisX, axisY, axisX, 0f))   // 축 선 L: x축 오른쪽 끝 → 원점 → y축 위 끝

    return ChartCache(
        axisX, axisY, w, h, dataLeft, dataTop, dataW, dataH, xEnd, yEnd, unitDiv, gridX, gridY, xLabels, yLabels,
        unitG, unitS,
        ribbon, centerline, caps.toFloatArray(), lastPoint, lastWidth, prevPoint, bandList.toFloatArray(),
        target, vertices.toFloatArray(), segDash, (inp.recipe?.let { markerSteps(it) } ?: IntArray(0)), vessel, morphFrom, morphTo, FloatArray(MORPH_POINTS * 2), Path(),
        Path(), Path().also { dropPath(it, DROP_BASE_D, 10.dp.toPx() / 100f, 14.dp.toPx() / 140f) },
        PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())), PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}

/** 그리기 단계: 프레임마다 머리 위치·꼬리 길이·전환 진행도만 읽는다. 컬렉션·배열·경로를 새로 만들지 않는다. */
private fun DrawScope.drawShapeArea(
    c: ChartCache, k: ChartColors, inp: ChartInputs, stale: Boolean, head: Animatable<Offset, *>, tailFlow: Float,
    morph: Float, stepQ: Float, dropScale: Float, fall: Float, ripple: Float, tarePlaying: Boolean, headAngle: FloatArray,
) {
    val running = inp.phase == BrewPhase.Running || inp.phase == BrewPhase.Paused || inp.phase == BrewPhase.Finished
    if (!running) { drawVessel(c, k, fall, ripple, tarePlaying); return }
    val a = fadeIn(morph)
    // 격자(장식) → 값 없는 구간 띠 → 축 선·눈금 → 리본 → 목표선 → 머리 → 표식
    for (x in c.gridX) drawLine(k.grid, Offset(x, c.dataTop), Offset(x, c.axisY), 1.dp.toPx(), alpha = a)
    for (y in c.gridY) drawLine(k.grid, Offset(c.axisX, y), Offset(c.w, y), 1.dp.toPx(), alpha = a)
    var b = 0
    while (b < c.bands.size) { drawRect(k.band, Offset(c.bands[b], 0f), Size(c.bands[b + 1] - c.bands[b], c.axisY), alpha = a); b += 2 }
    if (morph < 1f) {   // 서버 벽 → 축 선 L(색은 처음부터 끝까지 outline, 굵기 2 → 1 dp)
        lerpPoints(c.morphFrom, c.morphTo, morph, c.morphOut)
        c.morphPath.reset()
        c.morphPath.moveTo(c.morphOut[0], c.morphOut[1])
        var i = 2
        while (i < c.morphOut.size) { c.morphPath.lineTo(c.morphOut[i], c.morphOut[i + 1]); i += 2 }
        drawPath(c.morphPath, k.outline, style = Stroke((2f - morph).dp.toPx(), cap = StrokeCap.Round))
    } else {
        drawLine(k.outline, Offset(c.axisX, c.axisY), Offset(c.w, c.axisY), 1.dp.toPx())
        drawLine(k.outline, Offset(c.axisX, 0f), Offset(c.axisX, c.axisY), 1.dp.toPx())
    }
    for (x in c.gridX) drawLine(k.muted, Offset(x, c.axisY), Offset(x, c.axisY + 4.dp.toPx()), 1.dp.toPx(), alpha = a)
    for (n in c.xLabels.indices) {
        val x = c.xLabels[n].first; val t = c.xLabels[n].second
        drawText(t, topLeft = Offset(x - t.size.width / 2f, c.axisY + 6.dp.toPx()), alpha = a)
    }
    for (y in c.gridY) drawLine(k.muted, Offset(c.axisX - 4.dp.toPx(), y), Offset(c.axisX, y), 1.dp.toPx(), alpha = a)
    for (n in c.yLabels.indices) {
        val top = c.yLabels[n].first; val t = c.yLabels[n].second
        drawText(t, topLeft = Offset(c.axisX - 6.dp.toPx() - t.size.width, top), alpha = a)
    }
    drawText(c.unitG, topLeft = Offset(0f, 0f), alpha = a)
    drawText(c.unitS, topLeft = Offset(c.w - c.unitS.size.width, c.axisY + 6.dp.toPx()), alpha = a)   // x 글자 줄 오른쪽 끝(축 선 아래라 값 없는 구간 띠와 겹치지 않음)

    val hx = c.dataLeft + (head.value.x / 1000f / c.xEnd.toFloat()) * c.dataW
    val hy = c.dataTop + c.dataH - (max(0f, head.value.y / c.unitDiv.toFloat()) / c.yEnd.toFloat()) * c.dataH
    clipRect(c.axisX + 0.5f, 0f, c.w, c.axisY - 0.5f) {
        drawPath(c.ribbon, k.primary, alpha = a)
        var i = 0
        while (i < c.caps.size) { drawCircle(k.primary, c.caps[i + 2], Offset(c.caps[i], c.caps[i + 1]), alpha = a); i += 3 }
        val lp = c.lastPoint
        if (lp != null && !stale) drawLine(k.primary, lp, Offset(hx, hy), c.lastWidth, StrokeCap.Round, alpha = a)   // 몸통 끝 → 머리(틈 없음)
        // 목표선: 같은 대시의 surface 테두리(굵기 + 2 dp) 먼저(대시 사이에서는 리본이 보인다, D7), 현재 구간은 2 ↔ 3 dp
        if (inp.recipe != null) {
            val s = (inp.stepIndex ?: 0) + 1
            val cur = s * 2 + 1 < c.vertices.size
            val p0 = if (cur) Offset(c.vertices[2 * s - 2], c.vertices[2 * s - 1]) else Offset.Zero
            val p1 = if (cur) Offset(c.vertices[2 * s], c.vertices[2 * s + 1]) else Offset.Zero
            // surface 테두리는 리본 밖에만: 리본 안에서 목표선이 중심선 옆을 지날 때 흰 빗금을 남기지 않는다(D7)
            clipPath(c.ribbon, ClipOp.Difference) {
                drawPath(c.target, k.surface, style = Stroke(4.dp.toPx(), pathEffect = c.dash), alpha = a)
                if (cur) drawLine(k.surface, p0, p1, (4f + stepQ).dp.toPx(), pathEffect = c.segDash[s - 1], alpha = a)
            }
            drawPath(c.target, k.outline, style = Stroke(2.dp.toPx(), pathEffect = c.dash), alpha = a)
            if (cur) drawLine(k.outline, p0, p1, (2f + stepQ).dp.toPx(), pathEffect = c.segDash[s - 1], alpha = a)
            // 실제 중심선을 리본 최소 굵기로 목표선 위에 다시 그린다: 목표대로 따라간 평평한 구간에서도 실제는 끊기지 않는 실선이다(D7)
            drawPath(c.centerline, k.primary, style = Stroke(ribbonWidthDp(0f).dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round), alpha = a)
            // 기다리기 단계(7.1 isWaitStep) 목표 점선은 중심선 위에 한 번 더: 기다림을 지킨 뒤에도 평평한 목표선이 점선으로 보인다(4.4·5.6, D7). 밸브 레시피도 같다(P6)
            val r = inp.recipe
            for (i in 1..r.steps.lastIndex) {
                if (!r.isWaitStep(i)) continue
                val wq = if (cur && i == s - 1) 2f + stepQ else 2f
                drawLine(k.outline, Offset(c.vertices[2 * i], c.vertices[2 * i + 1]), Offset(c.vertices[2 * i + 2], c.vertices[2 * i + 3]), wq.dp.toPx(), pathEffect = c.segDash[i], alpha = a)
            }
        }
        // 머리: 끝은 진행 반대쪽(직전 → 최신 표본 방향의 반대), 첫 표본은 225°
        if (lp != null) {
            val pp = c.prevPoint
            if (pp != null && (pp.x != lp.x || pp.y != lp.y)) headAngle[0] = Math.toDegrees(atan2((lp.y - pp.y).toDouble(), (lp.x - pp.x).toDouble())).toFloat() + 270f
            val rot = if (headAngle[0].isNaN()) 225f else headAngle[0]
            val unitPx = 0.14.dp.toPx()
            dropPath(c.headPath, 42f + headTailDp(tailFlow) / 0.14f, unitPx, unitPx)
            translate(hx, hy) {
                rotate(rot, Offset.Zero) {
                    translate(-50f * unitPx, -92f * unitPx) {
                        if (stale) {
                            drawPath(c.headPath, k.surface, alpha = a)
                            drawPath(c.headPath, k.outline, style = Stroke(1.5.dp.toPx()), alpha = a)
                        } else drawPath(c.headPath, k.primary, alpha = a)
                    }
                }
            }
        }
    }
    // 단계 표식: 위 겹, 바깥 surface 테두리. 전환 중에는 서버 옆 자리에서 꼭짓점으로 옮겨 간다
    val vessel = c.vessel
    for (m in c.markerSteps.indices) {
        val step = c.markerSteps[m]
        var x = c.vertices[2 * step + 2]; var y = c.vertices[2 * step + 3]
        if (morph < 1f && vessel != null && m < vessel.markerSteps.size) {
            x = vessel.markers[2 * m] + (x - vessel.markers[2 * m]) * morph
            y = vessel.markers[2 * m + 1] + (y - vessel.markers[2 * m + 1]) * morph
        }
        val last = step == (inp.recipe?.steps?.lastIndex ?: -1)
        val reached = morph >= 1f && (step < (inp.stepIndex ?: 0) || (last && inp.lastStepReached))
        val justReached = reached && (step == (inp.stepIndex ?: 0) - 1 || (last && inp.lastStepReached))
        drawMarker(c, k, x, y, reached, if (justReached) dropScale else 1f)
    }
    if (morph < 1f && vessel != null) for (n in vessel.labels.indices) drawText(vessel.labels[n].second, topLeft = vessel.labels[n].first, alpha = fadeOut(morph))
}

private fun DrawScope.drawMarker(c: ChartCache, k: ChartColors, cx: Float, cy: Float, reached: Boolean, factor: Float) {
    val w = 10.dp.toPx(); val h = 14.dp.toPx()
    translate(cx - w / 2f, cy - h / 2f) {
        scale(factor, Offset(w / 2f, h / 2f)) {
            drawPath(c.dropPathS, k.surface, style = Stroke(4.5.dp.toPx()))
            if (reached) drawPath(c.dropPathS, k.reached)
            else {
                drawPath(c.dropPathS, k.surface)
                drawPath(c.dropPathS, k.muted, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawVessel(c: ChartCache, k: ChartColors, fall: Float, ripple: Float, tarePlaying: Boolean) {
    val v = c.vessel ?: return
    val ly = v.levelY
    if (ly != null) clipPath(v.closed) {
        drawLine(k.muted, Offset(v.left, ly), Offset(v.left + v.width, ly), 1.5.dp.toPx(), pathEffect = c.dashLevel)
    }
    drawPath(v.wall, k.outline, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    var i = 0
    while (i < v.markers.size) { drawMarker(c, k, v.markers[i], v.markers[i + 1], false, 1f); i += 2 }
    for (n in v.labels.indices) drawText(v.labels[n].second, topLeft = v.labels[n].first)
    if (tarePlaying) {   // 영점 확인: 입구 가운데 → 바닥 − 4 dp 낙하, 닿으면 납작한 물결 고리
        val cx = v.left + v.width / 2f
        val bottom = v.top + v.height
        if (fall < 1f) {
            val y = v.top + (bottom - 4.dp.toPx() - v.top) * fall
            translate(cx - 5.dp.toPx(), y - 14.dp.toPx()) { drawPath(c.dropPathS, k.primary) }
        } else if (ripple < 1f) {
            val rx = 40.dp.toPx() * v.height / 164.dp.toPx() * ripple
            drawOval(k.primary, Offset(cx - rx, bottom - 4.dp.toPx() - rx * 0.25f), Size(2 * rx, rx * 0.5f), alpha = 0.6f * (1f - ripple), style = Stroke(2.dp.toPx()))
        }
    }
}
