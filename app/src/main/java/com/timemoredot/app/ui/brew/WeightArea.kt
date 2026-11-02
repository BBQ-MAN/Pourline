package com.timemoredot.app.ui.brew

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.theme.AppMotion
import com.timemoredot.app.ui.theme.AppType
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.TimerSnapshot
import com.timemoredot.brew.ValveUi
import com.timemoredot.brew.valveUiFor
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** 테스트가 고른 글자 크기(sp)를 읽는 의미 속성(무게 숫자·지표 값). 낭독에는 쓰이지 않는다. */
val FontSpKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Int>("FontSp")
var androidx.compose.ui.semantics.SemanticsPropertyReceiver.fontSp by FontSpKey

const val WEIGHT_REF = "−8888.8"   // 기준 문자열(ui-design 4.2). tnum이라 다른 값은 이보다 좁거나 같다
const val WEIGHT_REF_OZ = "−888.88"
private val VALVE_CELL_WIDTH = 64.dp
private val VALVE_GAP = 8.dp
const val VALVE_ICON_TAG = "valve_icon"
const val VALVE_STATE_TAG = "valve_state"

/**
 * 4.2 자동 축소: 96 sp에서 4 sp씩 줄여 "기준 + 간격 + 단위" ≤ 가용 너비인 가장 큰 크기(최소 40). 넘치면 단위를 아래 줄로 내리고
 * 숫자만 40 → 24 sp. refDpPerSp = 기준 문자열 폭(dp) ÷ 글자 크기(sp)(글꼴 배율 포함, tnum 글리프는 크기에 비례). 반환 (sp, 단위 아래 줄).
 */
fun autoSizeWeight(availableDp: Float, refDpPerSp: Float, unitDp: Float, gapDp: Float = 8f): Pair<Int, Boolean> =
    autoSizeWeight(availableDp, { refDpPerSp * it }, unitDp, gapDp)

/** 같은 판정을 크기마다 잰 기준 문자열 폭으로 한다. 비선형 글꼴 배율에서는 작은 글자가 더 크게 늘어 한 크기에서 잰 비율로 넘기면 폭을 덜 잰다. */
fun autoSizeWeight(availableDp: Float, refDpAt: (Int) -> Float, unitDp: Float, gapDp: Float = 8f): Pair<Int, Boolean> {
    for (s in 96 downTo 40 step 4) if (refDpAt(s) + gapDp + unitDp <= availableDp) return s to false
    for (s in 40 downTo 24 step 4) if (refDpAt(s) <= availableDp) return s to true
    return 24 to true
}

/** 무게 영역 표시 값(ReadingUi → 글자·색·아래 줄). 새 VM 상태 없이 화면이 판정한다(범위 밖 포함). */
data class WeightShown(val text: String, val unit: WeightUnit, val live: Boolean, val stale: Boolean, val under: UnderLine?, val spoken: String?)
enum class UnderKind { UnitChecking, UnitUnknown, Disconnected, Overload, NoData, OutOfRange }
data class UnderLine(val kind: UnderKind, val sinceNanos: Long = 0)

fun weightShown(r: ReadingUi, fallbackUnit: WeightUnit): WeightShown = when (r) {
    ReadingUi.None -> WeightShown(NO_VALUE, fallbackUnit, false, false, null, "무게 없음")
    ReadingUi.UnitUnknown -> WeightShown(NO_VALUE, fallbackUnit, false, false, UnderLine(UnderKind.UnitChecking), "무게 없음, 단위 확인 중")
    ReadingUi.UnitUnknownTimedOut -> WeightShown(NO_VALUE, fallbackUnit, false, false, UnderLine(UnderKind.UnitUnknown), "무게 없음, 단위를 알 수 없음")
    is ReadingUi.Live ->
        if (weightInRange(r.weightGrams, r.unit)) WeightShown(formatWeight(r.weightGrams, r.unit), r.unit, true, false, null, null)
        else WeightShown(NO_VALUE, r.unit, false, false, UnderLine(UnderKind.OutOfRange), "무게 표시 범위 밖")
    is ReadingUi.Stale -> {
        val kind = when (r.cause) { StaleReason.Disconnected -> UnderKind.Disconnected; StaleReason.Overload -> UnderKind.Overload; StaleReason.NoData -> UnderKind.NoData }
        val shown = formatWeight(r.lastWeightGrams, r.unit)
        val cause = when (r.cause) { StaleReason.Disconnected -> "연결 끊김"; StaleReason.Overload -> "과부하"; StaleReason.NoData -> "저울 신호 없음" }
        WeightShown(shown, r.unit, false, true, UnderLine(kind, r.sinceNanos), "멈춘 값, $cause, 마지막 무게 ${spokenNumber(shown)} ${unitSpoken(r.unit)}")
    }
}

/** 1 s(또는 period) 주기로 바뀌는 시각. 화면 숫자 겹은 이것으로 다시 계산한다(delay, 프레임 루프 아님). */
@Composable
fun rememberNow(clock: MonotonicClock, periodMs: Long, active: Boolean): Long {
    // active가 바뀐 프레임에 시각을 다시 읽는다: 재개 첫 프레임에 새 runningSinceNanos보다 이른 now로 경과가 줄어 보이지 않게
    var now by remember(clock, active) { mutableLongStateOf(clock.nowNanos()) }
    LaunchedEffect(clock, active, periodMs) {
        while (active) { delay(periodMs); now = clock.nowNanos() }
    }
    return now
}

/**
 * S1 무게 영역(3.1 4번): 무게 숫자 + 아래 줄, 밸브 레시피면 왼쪽 밸브 칸(또는 글꼴 확대 때 아래 띠).
 * 무게 영역은 traversalGroup, 밸브 칸만 traversalIndex 1(무게 → 밸브 순, ui-design 8.5).
 */
@Composable
fun WeightArea(
    readout: StateFlow<BrewReadout>, fallbackUnit: WeightUnit, phase: BrewPhase, recipe: Recipe?, timer: TimerSnapshot,
    clock: MonotonicClock, valveTint: Int, modifier: Modifier = Modifier,
    // 높이가 정해져 올 때(가로 왼쪽 칸) 밸브 칸·띠가 들어갈 최소 높이를 부모에게 알린다(dp)
    onMinHeight: (Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val numberStyle = AppType.weightDisplay
    val unitStyle = MaterialTheme.typography.titleLarge
    val stateStyle = MaterialTheme.typography.titleMedium
    val hasValve = recipe?.valveSchedule?.isNotEmpty() == true && phase != BrewPhase.Finished
    // 높이가 정해져 오면(가로 왼쪽 칸, 3.2) 글자 크기를 높이로도 줄여 지표 줄이 첫 화면에 들게 하고, 남는 높이에서는 가운데 둔다
    BoxWithConstraints(modifier.fillMaxWidth().semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
        val w = maxWidth.value
        val maxH = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        // 최소 높이 제약 = 밸브 칸이 없었다면 받았을 높이(부모 FillScrollColumn refMin). s₀는 이 높이로 구한다
        val refH = if (constraints.hasBoundedHeight && constraints.minHeight > 0) minOf(maxH, constraints.minHeight / density.density) else maxH
        val ozRef = fallbackUnit == WeightUnit.Ounce
        val geo = remember(w, maxH, refH, density.fontScale, density.density, ozRef, hasValve, stateStyle) {
            fun dp(px: Int) = px / density.density
            val ref = if (ozRef) WEIGHT_REF_OZ else WEIGHT_REF
            val refAt = HashMap<Int, androidx.compose.ui.text.TextLayoutResult>()
            fun refLayout(s: Int) = refAt.getOrPut(s) { measurer.measure(ref, numberStyle.copy(fontSize = s.sp)) }
            fun refW(s: Int) = dp(refLayout(s).size.width)
            fun lineH(s: Int) = dp(refLayout(s).size.height)
            val refPerSp = refW(100) / 100f
            val unitDp = dp(measurer.measure(unitSymbol(fallbackUnit), unitStyle).size.width)
            val bounded = maxH.isFinite()
            // 띠 높이: 안쪽 여백 24 + 한 줄(넘치면 두 줄) 상태 글자 줄 높이, 최소 48(4.11 띠 배치)
            val bandLine = measurer.measure("닫힘", unitStyle.copy(fontSize = 16.sp, lineHeight = 24.sp))
            val bandText = dp(measurer.measure("닫힘 · 닫기까지 5초", androidx.compose.ui.text.TextStyle(fontSize = 16.sp)).size.width)
            val bandLines = if (bandText > w - 32f - 24f - 8f) 2 else 1
            val bandH = maxOf(48f, 24f + bandLines * dp(bandLine.size.height))
            val bandDp = if (hasValve) 8f + bandH else 0f   // 띠 배치일 때 간격 + 띠 높이
            fun capH(s: Int, extra: Float, h: Float = maxH): Int { var v = s; while (v > 40 && lineH(v) + 24f + extra > h) v -= 4; return v }
            val (sw, below) = autoSizeWeight(w, ::refW, unitDp)
            val sSide = capH(sw, 0f, refH)   // s₀: 칸이 없을 때 고를 크기(높이 축소 포함)
            val lineDp = dp(measurer.measure(ref, numberStyle.copy(fontSize = sSide.sp)).size.height)
            // 밸브 칸 판정(4.11): 가장 넓은 낱말 > 56 dp, 칸 내용 > 무게 영역 높이, 칸을 뺀 폭에서 한 줄 축소 실패 → 띠
            val words = listOf("닫힘", "열림").map { dp(measurer.measure(it, androidx.compose.ui.text.TextStyle(fontSize = 16.sp)).size.width) } +
                listOf("열기까지", "닫기까지", "부으세요", "그대로", "8:88에").map { dp(measurer.measure(it, androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum")).size.width) }
            val (svw, belowV) = autoSizeWeight(w - (VALVE_CELL_WIDTH + VALVE_GAP).value, ::refW, unitDp)
            val sv = minOf(capH(svw, 0f), sSide)   // s′ = min(높이 축소 뒤 s′, s₀)(4.11 넓은 창, 2026-11-02)
            // 높이가 정해진 왼쪽 칸(3.2)에서는 아이콘 20 dp + 2 dp + 상태 글자를 한 줄에 둔다. 그 폭이 내용 폭 56 dp를 넘으면 세로 쌓기(4.11 넓은 창 칸 안)
            val stateW = listOf("닫힘", "열림").maxOf { dp(measurer.measure(it, stateStyle).size.width) }
            val row = bounded && valveRowFits(stateW)
            val stateH = dp(measurer.measure("닫힘", androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp)).size.height)
            val cellContent = 8 + (if (row) maxOf(20f, stateH) else 24 + 4 + stateH) + 4 +
                2 * dp(measurer.measure("열기까지", androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp)).size.height) + 8
            // 높이가 정해진 칸(가로)에서는 숫자를 높이로 줄인 결과로 칸을 띠로 바꾸지 않는다: 판정은 너비로 하고(4.11 넓은 창),
            // 칸 높이는 max(칸 내용, 숫자 줄 + 아래 줄)이며 그 높이를 부모가 최소 높이로 준다
            val side = hasValve && words.max() <= 56f && !belowV && (bounded || cellContent <= lineDp + 24f)
            val s0 = if (hasValve && !side) minOf(capH(sw, bandDp), sSide) else sSide
            val line0 = if (s0 == sSide) lineDp else dp(measurer.measure(ref, numberStyle.copy(fontSize = s0.sp)).size.height)
            val line40 = lineH(40) + 24f
            val minH = when { !hasValve -> 0f; side -> maxOf(cellContent, line40); else -> line40 + bandDp }
            WeightGeo(s0, below, line0, if (side) sv else null, refPerSp, unitDp, if (side) maxOf(cellContent, line0 + 24f) else line0 + 24f, minH, side && row)
        }
        LaunchedEffect(geo.minHeightDp) { onMinHeight(geo.minHeightDp) }
        Column {
            Box(contentAlignment = Alignment.CenterStart) {
                if (geo.valveSp != null) {
                    ValveCell(phase, recipe, timer, clock, valveTint, band = false, row = geo.valveRow,
                        modifier = Modifier.width(VALVE_CELL_WIDTH).height(geo.cellDp.dp).semantics { traversalIndex = 1f })
                }
                WeightNumber(readout, fallbackUnit, clock, geo, ozRef)
            }
            if (hasValve && geo.valveSp == null) {
                Spacer(Modifier.height(Space.s))
                ValveCell(phase, recipe, timer, clock, valveTint, band = true, modifier = Modifier.fillMaxWidth().semantics { traversalIndex = 1f })
            }
        }
    }
}

/** 4.11 넓은 창 칸 안: 아이콘 20 dp + 2 dp + 상태 글자 폭(잰 값)이 칸 내용 폭 56 dp에 들면 한 줄. */
fun valveRowFits(stateWidthDp: Float): Boolean = 20f + 2f + stateWidthDp <= 56f

data class WeightGeo(
    val sizeSp: Int, val unitBelow: Boolean, val lineDp: Float, val valveSp: Int?, val refDpPerSp: Float, val unitDp: Float,
    val cellDp: Float = lineDp + 24f, val minHeightDp: Float = 0f, val valveRow: Boolean = false,
)

@Composable
private fun WeightNumber(readout: StateFlow<BrewReadout>, fallbackUnit: WeightUnit, clock: MonotonicClock, geo: WeightGeo, ozRef: Boolean) {
    val r by readout.collectAsStateWithLifecycle()
    val shown = weightShown(r.reading, fallbackUnit)
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sp = geo.valveSp ?: geo.sizeSp
    val numberStyle = AppType.weightDisplay.copy(fontSize = sp.sp, color = if (shown.live) cs.onSurface else cs.onSurfaceVariant)
    val unitStyle = MaterialTheme.typography.titleLarge.copy(color = if (shown.live) cs.onSurface else cs.onSurfaceVariant)
    val dot = shown.text.indexOf('.')
    val intPart = shown.text.substring(0, dot)
    val fracPart = shown.text.substring(dot)
    val intLayout = remember(intPart, numberStyle) { measurer.measure(intPart, numberStyle) }
    val fracLayout = remember(fracPart, numberStyle) { measurer.measure(fracPart, numberStyle) }
    val unitLayout = remember(shown.unit, unitStyle) { measurer.measure(unitSymbol(shown.unit), unitStyle) }
    // 단위를 모르는 동안 단위 글자를 그리지 않는다(아래 줄 "단위 확인 중"/"단위를 알 수 없음"과 어긋나지 않게). 폭은 재어 기준점은 그대로
    val unitHidden = r.reading is ReadingUi.UnitUnknown || r.reading is ReadingUi.UnitUnknownTimedOut
    val refs = remember(sp, geo.sizeSp, ozRef, density) {
        fun wpx(t: String, s: Int) = measurer.measure(t, AppType.weightDisplay.copy(fontSize = s.sp)).size.width.toFloat()
        val intRef = if (ozRef) "−888" else "−8888"
        val fracRef = if (ozRef) ".88" else ".8"
        WeightRefs(wpx(intRef, sp), wpx(fracRef, sp), wpx("188", geo.sizeSp), wpx(intRef, geo.sizeSp), wpx(fracRef, geo.sizeSp))
    }
    val now = rememberNow(clock, 1_000, shown.stale)
    val outline = cs.outline
    val dash = remember(density) { with(density) { PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) } }   // 그리기 단계에서 만들지 않는다
    val spoken = shown.spoken ?: rememberSpoken(shown.text, { "무게 ${spokenNumber(it)} ${unitSpoken(shown.unit)}" }, { "무게 변하는 중, ${spokenNumber(it)} ${unitSpoken(shown.unit)}" })
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken; fontSp = sp }) {
        Box(
            Modifier.fillMaxWidth().height(geo.lineDp.dp + if (geo.unitBelow && geo.valveSp == null) with(density) { unitLayout.size.height.toDp() } else 0.dp)
                .drawBehind {
                    val gap = 8.dp.toPx()
                    val lineH = geo.lineDp.dp.toPx()
                    val W = size.width
                    val unitW = unitLayout.size.width.toFloat()
                    // 소수점 기준점(4.2 정렬): 칸이 없을 때의 a를 먼저 구하고, 칸이 있으면 단위 오른쪽 끝 E를 맞춘다
                    val r0 = refs.frac0 + if (geo.unitBelow) 0f else gap + unitW
                    val a0 = ((W + refs.w188 - r0) / 2f).coerceIn(refs.int0, (W - r0).coerceAtLeast(refs.int0))
                    val a = if (geo.valveSp == null) a0 else {
                        val e = a0 + r0
                        val rv = refs.fracV + gap + unitW
                        val minA = (VALVE_CELL_WIDTH + VALVE_GAP).toPx() + refs.intV
                        (e - rv).coerceIn(minA, (W - rv).coerceAtLeast(minA))
                    }
                    val top = lineH - intLayout.size.height   // 숫자 상자 아래 끝 = 숫자 줄 아래 끝(칸 있을 때 64 sp도 같은 자리)
                    drawText(intLayout, topLeft = Offset(a - intLayout.size.width, top))
                    drawText(fracLayout, topLeft = Offset(a, top))
                    val baseline = top + intLayout.firstBaseline
                    val unitX: Float
                    val unitY: Float
                    if (geo.unitBelow && geo.valveSp == null) { unitX = a + fracLayout.size.width - unitW; unitY = lineH }
                    else { unitX = a + fracLayout.size.width + gap; unitY = baseline - unitLayout.firstBaseline }
                    if (!unitHidden) drawText(unitLayout, topLeft = Offset(unitX, unitY))
                    if (shown.stale) {   // 멈춘 값 점선 상자(D11): 글자 폭 양옆 12 dp, 줄 상자 위·아래. 배치 변화 없음
                        val pad = 12.dp.toPx()
                        var left = a - intLayout.size.width - pad
                        if (geo.valveSp != null) left = maxOf(left, (VALVE_CELL_WIDTH + 4.dp).toPx())
                        val right = (if (geo.unitBelow) a + fracLayout.size.width else unitX + unitW) + pad
                        drawRoundRect(
                            outline, Offset(left, 0f), Size(right - left, lineH), CornerRadius(20.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = dash),
                        )
                    }
                },
        )
        UnderRow(shown.under, now, geo.valveSp != null)
    }
}

private data class WeightRefs(val intV: Float, val fracV: Float, val w188: Float, val int0: Float, val frac0: Float)

@Composable
private fun UnderRow(under: UnderLine?, nowNanos: Long, valve: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(start = if (valve) VALVE_CELL_WIDTH + VALVE_GAP else 0.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (under == null) return@Row
        val (icon, text) = when (under.kind) {
            UnderKind.UnitChecking -> AppIcons.HourglassEmpty to "단위 확인 중"
            UnderKind.UnitUnknown -> AppIcons.Help to "단위를 알 수 없음"
            UnderKind.Disconnected -> AppIcons.LinkOff to "멈춘 값 · 연결 끊김 · ${((nowNanos - under.sinceNanos) / 1_000_000_000L).coerceAtLeast(0)}초 전"
            UnderKind.Overload -> AppIcons.Warning to "멈춘 값 · 과부하"
            UnderKind.NoData -> AppIcons.SignalDisconnected to "멈춘 값 · 저울 신호 없음"
            UnderKind.OutOfRange -> AppIcons.Warning to "표시 범위 밖 · 올린 것을 확인하세요"
        }
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.s))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 밸브 칸 문구(ui-design 4.11). 화면 문구와 낭독 문구. */
data class ValveTexts(val state: String, val guide: String, val spoken: String, val soon: Boolean)

fun valveTexts(v: ValveUi, phase: BrewPhase): ValveTexts {
    val state = if (v.open) "열림" else "닫힘"
    val head = "레시피 안내, 스위치 $state"
    if (phase == BrewPhase.Idle || phase == BrewPhase.WaitingForPour) {
        val g = if (v.open) "열고 부으세요" else "닫고 부으세요"
        return ValveTexts(state, g, "$head, $g", false)
    }
    val next = v.next ?: return ValveTexts(state, "끝까지 그대로", "$head, 끝까지 그대로", false)
    val verb = if (next.open) "열기" else "닫기"
    val left = v.secondsToNext ?: 0
    return if (left in 1..5) ValveTexts(state, "${verb}까지 ${left}초", "$head, ${left}초 뒤 $verb", true)
    else ValveTexts(state, "${formatMinSec(next.atSec)}에 $verb", "$head, ${spokenMinSec(next.atSec.toLong())}에 $verb", false)
}

/** 밸브 칸·띠(4.11). valveUiFor를 타이머 글자와 같은 ~100 ms 주기로 스스로 계산한다. null이면 아무 노드도 두지 않는다. */
@Composable
fun ValveCell(
    phase: BrewPhase, recipe: Recipe?, timer: TimerSnapshot, clock: MonotonicClock, tintTrigger: Int, band: Boolean, modifier: Modifier = Modifier,
    row: Boolean = false,   // 옆 칸 안 아이콘·상태 글자 한 줄(4.11 넓은 창 칸 안)
) {
    val now = rememberNow(clock, 100, timer.runningSinceNanos != null)
    val v = valveUiFor(phase, recipe, timer, now) ?: return
    val t = valveTexts(v, phase)
    val cs = MaterialTheme.colorScheme
    val face = if (v.open) cs.tertiaryContainer else cs.surfaceContainerHigh
    val content = if (v.open) cs.onTertiaryContainer else cs.onSurface
    // 바꾸는 순간 색 숨(motion.tint): 이전 면 → inversePrimary → 새 면. 계기는 화면이 받은 Announce(ValveOpen|ValveClose)
    val tint = remember { Animatable(0f) }
    val peak = cs.inversePrimary
    // 처음 구성될 때의 계기 값은 지난 일이다: Finished → Idle이나 빈 상태 뒤 칸이 돌아와도 색 숨을 다시 내지 않는다
    val seenTint = remember { intArrayOf(tintTrigger) }
    LaunchedEffect(tintTrigger) {
        if (tintTrigger <= seenTint[0]) return@LaunchedEffect
        seenTint[0] = tintTrigger
        tint.snapTo(0f)
        tint.animateTo(1f, AppMotion.tintIn)
        tint.animateTo(0f, AppMotion.tintOut)
    }
    val shape = MaterialTheme.shapes.large
    val base = modifier.clearAndSetSemantics { contentDescription = t.spoken }
        .drawBehind { drawRoundRect(lerp(face, peak, tint.value), cornerRadius = CornerRadius(28.dp.toPx())) }
        .then(if (t.soon) Modifier.border(2.dp, cs.primary, shape) else Modifier)
    val icon = if (v.open) AppIcons.ValveOpen else AppIcons.ValveClosed
    if (band) {
        Row(base.heightIn(min = 48.dp).padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp).testTag(VALVE_ICON_TAG), tint = content)
            Spacer(Modifier.width(Space.s))
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text(t.state, style = MaterialTheme.typography.titleMedium, color = content)
                Text(" · ", style = MaterialTheme.typography.titleMedium, color = content)
                Text(t.guide, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = content, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    } else {
        Column(base.padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val state: @Composable () -> Unit = {
                Text(t.state, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, modifier = Modifier.testTag(VALVE_STATE_TAG))
            }
            if (row) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, Modifier.size(20.dp).testTag(VALVE_ICON_TAG), tint = content)
                Spacer(Modifier.width(2.dp))
                state()
            } else {
                Icon(icon, contentDescription = null, Modifier.size(24.dp).testTag(VALVE_ICON_TAG), tint = content)
                Spacer(Modifier.height(4.dp))
                state()
            }
            Spacer(Modifier.height(4.dp))
            Text(t.guide, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = content, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
