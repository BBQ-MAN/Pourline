package com.timemoredot.app.ui.brew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 정수면 소수 없이(240), 아니면 소수 1자리. 낭독·단계 카드 목표 무게. */
fun shortGrams(g: Double): String = if (g % 1.0 == 0.0) g.toLong().toString() else String.format(Locale.ROOT, "%.1f", g)

/**
 * 값 낭독 문구(ui-design 8.2 BrewAnnouncer). 순수 상태 기계: update가 낼 문구를 돌려주고, 없으면 null.
 * 5초 경계(단계 바뀌면 그때부터 다시), 단계 바뀜, 마지막 단계 도달, 끝 시각 넘김. Paused·Idle·멈춘 값에서는 5초 요약을 내지 않는다.
 * 직전에 낸 문구와 같으면 null.
 */
class BrewAnnouncerLogic {
    data class Input(
        val phase: BrewPhase, val elapsedNanos: Long, val stale: Boolean, val stepIndex: Int?, val lastStepReached: Boolean,
        val recipe: Recipe?, val weightText: String?, val flowText: String?, val unit: WeightUnit,
    )

    private var started = false
    private var anchorNanos = 0L
    private var lastBucket = 0L
    private var lastStep: Int? = null
    private var lastReached = false
    private var finishPassed = false
    private var lastText: String? = null

    fun update(i: Input): String? {
        val finishNanos = i.recipe?.let { it.finishSec * 1_000_000_000L }
        if (!started || i.phase == BrewPhase.Idle || i.phase == BrewPhase.WaitingForPour) {
            started = true
            anchorNanos = 0; lastBucket = i.elapsedNanos / PERIOD; lastStep = i.stepIndex; lastReached = i.lastStepReached
            finishPassed = finishNanos != null && i.elapsedNanos >= finishNanos
            return null
        }
        if (i.phase != BrewPhase.Running) {   // Paused·Finished: 값 낭독 없음, 기준만 따라간다
            lastStep = i.stepIndex; lastReached = i.lastStepReached
            lastBucket = (i.elapsedNanos - anchorNanos) / PERIOD
            return null
        }
        var text: String? = null
        val step = i.stepIndex
        val recipe = i.recipe
        if (recipe != null && step != null && lastStep != null && step > lastStep!! && !i.lastStepReached) {
            val s = recipe.steps[step]
            text = "${step + 1}단계, ${s.name}, ${shortGrams(s.targetGrams)} 그램까지"
            anchorNanos = i.elapsedNanos   // 5초 주기 재시작
        }
        if (recipe != null && i.lastStepReached && !lastReached) {
            val target = shortGrams(recipe.steps.last().targetGrams)
            text = if (finishNanos != null && i.elapsedNanos < finishNanos) "목표 $target 그램 도달, ${spokenMinSec(recipe.finishSec.toLong())}에 끝"
            else "목표 $target 그램 도달"
            anchorNanos = i.elapsedNanos
        }
        if (finishNanos != null && !finishPassed && i.elapsedNanos >= finishNanos) {
            finishPassed = true
            if (text == null) text = "끝 시각, 무게가 멈추면 끝납니다"
        }
        val bucket = (i.elapsedNanos - anchorNanos) / PERIOD
        if (text == null && bucket > lastBucket && !i.stale && i.weightText != null) {
            val u = unitSpoken(i.unit)
            text = "${spokenMinSec(i.elapsedNanos / 1_000_000_000L)}, ${spokenNumber(i.weightText)} $u, 초당 ${i.flowText ?: "0.0"} $u"
        }
        lastBucket = bucket
        if (step != null) lastStep = step
        lastReached = i.lastStepReached
        if (text == null || text == lastText) return null
        lastText = text
        return text
    }

    private companion object { const val PERIOD = 5_000_000_000L }
}

/**
 * 숫자 노드 낭독 문구 디바운스(ui-design 8.2): 표시 문자열이 500 ms 그대로면 그 값의 안정 문구,
 * 계속 바뀌는 동안은 1초마다 그때 값의 "변하는 중" 문구. 노드 내용이 1 Hz를 넘게 바뀌지 않는다.
 */
class SpokenDebounce(initial: String) {
    var spoken: Pair<String, Boolean> = initial to false   // (값, 변하는 중)
        private set
    private var display = initial
    private var changedAtMs = Long.MIN_VALUE / 2
    private var publishedAtMs = Long.MIN_VALUE / 2

    fun update(value: String, nowMs: Long): Pair<String, Boolean> {
        if (value != display) {
            if (nowMs - changedAtMs >= 500) publishedAtMs = nowMs   // 안정 상태에서 바뀌기 시작: "변하는 중"은 1초 뒤부터
            display = value; changedAtMs = nowMs
        }
        spoken = when {
            nowMs - changedAtMs >= 500 -> display to false
            nowMs - publishedAtMs >= 1_000 -> { publishedAtMs = nowMs; display to true }
            else -> spoken
        }
        return spoken
    }
}

/** 표시 문자열 → 디바운스된 낭독 문구. 100 ms 주기(delay, 프레임 루프 아님). */
@Composable
fun rememberSpoken(display: String, stable: (String) -> String, changing: (String) -> String): String {
    val current by rememberUpdatedState(display)
    val logic = remember { SpokenDebounce(display) }
    val state: MutableState<Pair<String, Boolean>> = remember { mutableStateOf(display to false) }
    LaunchedEffect(logic) {
        var t = 0L
        while (true) {
            state.value = logic.update(current, t)
            delay(100); t += 100
        }
    }
    val (v, ch) = state.value
    return if (ch) changing(v) else stable(v)
}

/** S1 값 낭독. 자기 노드 없이 LocalAnnouncer로 넘긴다(알림 노드는 앱 루트 StatusAnnouncer 한 곳). */
@Composable
fun BrewAnnouncer(ui: BrewUiState, readout: StateFlow<BrewReadout>, clock: MonotonicClock) {
    val announcer = LocalAnnouncer.current
    val latestUi by rememberUpdatedState(ui)
    val logic = remember { BrewAnnouncerLogic() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // STARTED 동안만 돈다: 백그라운드에서 멈춘 값으로 5초 요약이 쌓였다가 돌아올 때 한꺼번에 읽히지 않게. readout은 루프 안에서 읽는다(재구성 없음)
    LaunchedEffect(logic, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          while (true) {
            val u = latestUi
            val reading = readout.value.reading
            val live = reading as? ReadingUi.Live
            val text = logic.update(
                BrewAnnouncerLogic.Input(
                    u.phase, u.timer.elapsedNanos(clock.nowNanos()), reading !is ReadingUi.Live, u.currentStepIndex, u.lastStepReached, u.recipe,
                    live?.let { formatWeight(it.weightGrams, it.unit) }, live?.let { formatFlow(it.flowGramsPerSec, it.unit) }, u.displayUnit,
                ),
            )
            if (text != null) announcer.announce(text, false)
            delay(100)
          }
        }
    }
}
