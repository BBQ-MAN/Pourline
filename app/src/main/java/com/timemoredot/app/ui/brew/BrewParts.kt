package com.timemoredot.app.ui.brew

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.components.AppOutlinedButton
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.theme.AppMotion
import com.timemoredot.app.ui.theme.AppType
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.TimerSnapshot
import com.timemoredot.brew.isWaitStep
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/** 줄 높이는 글자 크기에 비례(36 sp / 44 = 1.22 em): 줄인 크기에서 44 sp 줄 높이를 남기지 않는다(4.2 무게 줄 높이와 같은 방식). */
private fun metricStyle(sp: Int) = AppType.metricValue.copy(fontSize = sp.sp, lineHeight = (44f / 36f).em)

/** 지표 값 크기: 기준 문자열이 칸 너비에 들어가는 20~maxSp(기본 36) sp(4.3). 후보 크기마다 다시 잰다(비선형 글꼴 배율, 4.2와 같은 방식). */
@Composable
private fun metricSize(ref: String, widthDp: Float, maxSp: Int = 36): Int {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    return remember(ref, widthDp, d, maxSp) {
        (maxSp downTo 20).firstOrNull { measurer.measure(ref, metricStyle(it)).size.width / d.density <= widthDp } ?: 20
    }
}

/** 4.3 지표 줄: 타이머 · 유량. 각 칸 너비 < 140 dp면 위아래로 쌓는다. 지표 숫자는 각자 수집한다. */
@Composable
fun MetricsRow(phase: BrewPhase, timer: TimerSnapshot, readout: StateFlow<BrewReadout>, unit: WeightUnit, clock: MonotonicClock, modifier: Modifier = Modifier, maxSp: Int = 36) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = (maxWidth - Space.l) / 2 < 140.dp
        val cellW = if (stacked) maxWidth.value else (maxWidth - Space.l).value / 2
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            TimerMetric(phase, timer, clock, cellW, maxSp = maxSp)
            FlowMetric(readout, unit, cellW, Modifier.fillMaxWidth(), maxSp)   // 쌓을 때도 오른쪽 내용 끝에 맞춘다
        } else Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            TimerMetric(phase, timer, clock, cellW, Modifier.weight(1f), maxSp)
            FlowMetric(readout, unit, cellW, Modifier.weight(1f), maxSp)
        }
    }
}

@Composable
private fun TimerMetric(phase: BrewPhase, timer: TimerSnapshot, clock: MonotonicClock, widthDp: Float, modifier: Modifier = Modifier, maxSp: Int = 36) {
    val now = rememberNow(clock, 100, timer.runningSinceNanos != null)
    val elapsed = timer.elapsedNanos(now)
    val paused = phase == BrewPhase.Paused
    val sec = minOf(elapsed, 3_599_900_000_000L) / 1_000_000_000L
    val spoken = "시간 ${spokenMinSec(sec)}" + if (paused) ", 멈춤" else ""
    val size = metricSize("88:88.8", widthDp, maxSp)
    Column(modifier.clearAndSetSemantics { contentDescription = spoken; fontSp = size }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시간", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (paused) {
                Spacer(Modifier.width(Space.xs))
                Icon(AppIcons.Pause, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("멈춤", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(formatTimer(elapsed), style = metricStyle(size), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun FlowMetric(readout: StateFlow<BrewReadout>, unit: WeightUnit, widthDp: Float, modifier: Modifier = Modifier, maxSp: Int = 36) {
    val r by readout.collectAsStateWithLifecycle()
    val live = r.reading as? ReadingUi.Live
    val shown = live?.let { formatFlow(it.flowGramsPerSec, it.unit) } ?: "—"
    val u = live?.unit ?: unit
    val spoken = if (live == null) "유량 없음" else rememberSpoken(shown, { "유량 초당 $it ${unitSpoken(u)}" }, { "유량 변하는 중, 초당 $it ${unitSpoken(u)}" })
    val size = metricSize("888.8", widthDp, maxSp)
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current
    val boxW = remember(size, d) { measurer.measure("888.8", metricStyle(size)).size.width / d.density }
    Column(modifier.clearAndSetSemantics { contentDescription = spoken }, horizontalAlignment = Alignment.End) {
        Text("유량 ${unitSymbol(u)}/s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shown, Modifier.width(boxW.dp), style = metricStyle(size), textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
}

/** 4.5 단계 카드. 남은 무게·시간 차는 카드가 readout을 직접 수집한다. 단계가 바뀌면 면 색만 한 번 숨 쉰다(motion.tint). */
@Composable
fun RecipeStepCard(
    recipe: Recipe, phase: BrewPhase, stepIndex: Int?, lastStepReached: Boolean, timer: TimerSnapshot,
    readout: StateFlow<BrewReadout>, unit: WeightUnit, clock: MonotonicClock, modifier: Modifier = Modifier,
) {
    val r by readout.collectAsStateWithLifecycle()
    val index = stepIndex ?: 0
    val step = recipe.steps[index]
    val n = recipe.steps.size
    val cs = MaterialTheme.colorScheme
    val tint = remember { Animatable(0f) }
    var shownIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        if (index > shownIndex && phase == BrewPhase.Running) {
            tint.snapTo(0f); tint.animateTo(1f, AppMotion.tintIn); tint.animateTo(0f, AppMotion.tintOut)
        }
        shownIndex = index
    }
    val now = rememberNow(clock, 1_000, timer.runningSinceNanos != null)
    val elapsedSec = timer.elapsedNanos(now) / 1_000_000_000L
    val brewing = phase == BrewPhase.Running || phase == BrewPhase.Paused || phase == BrewPhase.Finished
    val remaining = r.remainingGrams?.takeIf { brewing }
    val pace = r.paceDeltaSec?.takeIf { brewing }
    val remainShown = remaining?.let { formatWeight(it, unit) }
    val paceText = pace?.let { val s = abs(it).roundToInt(); if (abs(it) <= 2.0) "제때" else if (it > 0) "${s}초 늦음" else "${s}초 빠름" }
    val line3 = "${shortGrams(step.targetGrams)}${NBSP}g까지 · ${formatMinSec(step.targetAtSec)}까지"
    val next = index + 1
    val (line5, waiting) = when {
        next <= recipe.steps.lastIndex && recipe.isWaitStep(next) -> "다음: ${formatMinSec(recipe.steps[next].targetAtSec)}까지 기다리기" to false
        next <= recipe.steps.lastIndex -> "다음: ${shortGrams(recipe.steps[next].targetGrams)}${NBSP}g · ${formatMinSec(recipe.steps[next].targetAtSec)}까지" to false
        !lastStepReached -> "마지막 단계" to false
        elapsedSec < recipe.finishSec -> "기다리는 중 · ${formatMinSec(recipe.finishSec)}에 끝" to true
        else -> "무게가 멈추면 끝납니다" to false
    }
    val remainSpoken = remainShown?.let { rememberSpoken(it, { v -> "남은 $v 그램" }, { v -> "남은 변하는 중, $v 그램" }) }
    val spoken = listOfNotNull(
        "${index + 1}/$n 단계", step.name, "${shortGrams(step.targetGrams)} 그램까지", "${spokenMinSec(step.targetAtSec.toLong())}까지",
        remainSpoken, paceText, line5.replace(" · ", ", "),
    ).joinToString(", ")
    Card(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }
            .drawBehind { drawRoundRect(lerp(cs.surfaceContainerHigh, cs.secondaryContainer, tint.value), cornerRadius = CornerRadius(28.dp.toPx())) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = cs.onSurface),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("${index + 1}/$n 단계", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Text(stepNameForDisplay(step.name), style = MaterialTheme.typography.titleMedium)
            Text(line3, style = AppType.statValue)
            if (remainShown != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Text("남은 $remainShown${NBSP}${unitSymbol(unit)}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                if (paceText != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Schedule, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(paceText, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (waiting) { Icon(AppIcons.HourglassEmpty, contentDescription = null, Modifier.size(18.dp), tint = cs.onSurfaceVariant); Spacer(Modifier.width(Space.xs)) }
                Text(line5, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = cs.onSurfaceVariant)
            }
        }
    }
}

/** 4.6 동작 줄 버튼 하나. */
private class Act(val label: String, val icon: ImageVector?, val kind: Int, val onClick: () -> Unit, val enabled: Boolean = true,
                  val state: String? = null, val clickLabel: String? = null, val progress: Boolean = false)

private const val TONAL = 0
private const val FILLED = 1
private const val OUTLINED = 2

/** 4.6 동작 줄. 글꼴 ≥ 1.3 또는 너비 < 328 dp면 두 줄(주 동작 전체 폭, 나머지 반씩). */
@Composable
fun BrewActions(
    ui: BrewUiState, saveFailed: Boolean,
    onTare: () -> Unit, onStart: () -> Unit, onPause: () -> Unit, onReset: () -> Unit, onCancelWaiting: () -> Unit, onFinish: () -> Unit,
    onRetrySave: () -> Unit, modifier: Modifier = Modifier,
) {
    val tareState = when {
        ui.tareInFlight -> "영점 맞추는 중"
        ui.phase == BrewPhase.Running -> "추출 중에는 영점을 맞출 수 없음"
        !ui.canTare -> "연결 후 사용할 수 있음"
        else -> null
    }
    val tare = Act("영점", null, TONAL, onTare, ui.canTare, tareState, "무게를 0으로 맞추기", progress = ui.tareInFlight)
    val finish = Act("완료", AppIcons.Check, OUTLINED, onFinish, clickLabel = "추출 끝내고 요약 보기")
    val (left, main, right) = when (ui.phase) {
        BrewPhase.Idle -> Triple(tare, Act("시작", AppIcons.PlayArrow, FILLED, onStart, ui.canStart, "타이머 멈춤", "타이머 시작"), null)
        BrewPhase.WaitingForPour -> Triple(tare, Act("지금 시작", AppIcons.PlayArrow, FILLED, onStart, ui.canStart, "타이머 멈춤", "타이머 시작"), Act("취소", null, OUTLINED, onCancelWaiting))
        BrewPhase.Running -> Triple(tare.copyDisabled(), Act("일시정지", AppIcons.Pause, FILLED, onPause, true, "타이머 진행 중", "타이머 일시정지"), finish)
        BrewPhase.Paused -> Triple(Act("초기화", AppIcons.RestartAlt, OUTLINED, onReset, clickLabel = "타이머와 그래프 초기화"), Act("계속", AppIcons.PlayArrow, FILLED, onStart, true, "타이머 멈춤", "타이머 계속"), finish)
        BrewPhase.Finished -> Triple(
            null,
            if (saveFailed) Act("다시 저장", AppIcons.Save, FILLED, onRetrySave) else Act("저장 중", null, FILLED, {}, false, "저장하는 중", progress = true),
            null,
        )
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m)) {
        val twoRows = fontScale >= 1.3f || maxWidth < 328.dp
        val side = listOfNotNull(left, right)
        if (!twoRows || side.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                // 프로토타입 폭 96 · 112 · 96 dp(주 동작이 조금 넓다)
                if (left != null) ActionButton(left, Modifier.weight(1f))
                ActionButton(main, Modifier.weight(if (left == null && right == null) 1f else 1.17f))
                if (right != null) ActionButton(right, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                ActionButton(main, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) { side.forEach { ActionButton(it, Modifier.weight(1f)) } }
            }
        }
    }
}

private fun Act.copyDisabled() = Act(label, icon, kind, onClick, false, state, clickLabel, progress)

@Composable
private fun ActionButton(a: Act, modifier: Modifier) {
    val m = modifier.widthIn(min = 96.dp).heightIn(min = 64.dp).focusRing(CircleShape).semantics {
        a.state?.let { stateDescription = it }
        a.clickLabel?.let { onClick(label = it, action = null) }
    }
    val content: @Composable RowScope.() -> Unit = {
        if (a.progress) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
        else if (a.icon != null) Icon(a.icon, contentDescription = null, Modifier.size(24.dp))
        if (a.progress || a.icon != null) Spacer(Modifier.width(Space.s))
        Text(a.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, textAlign = TextAlign.Center)
    }
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.s, vertical = Space.s)   // 96 dp 칸에 아이콘 + 네 글자가 들어가게
    when (a.kind) {
        TONAL -> FilledTonalButton(a.onClick, m, enabled = a.enabled, contentPadding = pad, content = content)
        FILLED -> Button(a.onClick, m, enabled = a.enabled, contentPadding = pad, content = content)
        else -> AppOutlinedButton(a.onClick, m, enabled = a.enabled, contentPadding = pad, content = content)
    }
}
