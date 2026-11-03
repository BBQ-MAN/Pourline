package com.timemoredot.brew

import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction

data class BrewSettings(                         // 사용자 설정 (S4), AppPrefs에 저장
    val autoStartEnabled: Boolean = true,
    val autoStartThresholdGrams: Double = 1.0,   // 0.5 / 1.0 / 2.0 중 하나
    val valveHaptic: Boolean = true,             // 밸브 변경 시각 진동(S4). 엔진은 읽지 않는다(ValveCue는 늘 내고 VM이 거른다)
)

data class BrewConfig(                           // 고정 상수
    val autoStartConsecutive: Int = 2,             // 임계값을 넘는 연속 샘플 수
    val autoStartConfirmNanos: Long = 500_000_000, // 처음 넘은 샘플부터 이 시간 동안 계속 넘어야 시작(F-B4-1 들어 올림 손 압력). 시작 시각은 처음 넘은 샘플로 소급
    val weightDropGrams: Double = 2.0,             // 정상 샘플 사이 무게가 이보다 많이 줄면 유량 창을 비우고(O-B8-1) 자동 시작 확인을 취소한다. 실측 붓기 중 최대 감소 0.1 g(B8)
    val flowWindowNanos: Long = 1_000_000_000,     // 유량 이동 평균 창 (수신 시각 기준)
    val stepToleranceGrams: Double = STEP_TOLERANCE_GRAMS, // 단계 도달 판정 여유(7.1 상수, 요약판 표식과 같은 값)
    val finishStableNanos: Long = 5_000_000_000,   // 레시피 끝 시각(Recipe.finishSec) 뒤의 샘플만으로 이 시간 동안
    val finishStableGrams: Double = 0.5,           // 무게 변화가 이 값 이하면 자동 완료 (레시피가 있을 때만)
    val noDataTimeoutNanos: Long = 3_000_000_000,  // protocol.md C-01-12 — 연결 중 0x01 공백이 이보다 길면 NoData. 전원 버튼 끊김 감지 2.08~2.25 s(C-0B-2, macOS)보다 길게
                                                   // Android 실측 2.00 s(C-0B-2a, B6) + 0.5 s < 3 s라 그대로 둔다
    val maxElapsedNanos: Long = 3_599_900_000_000, // 타이머 상한 59:59.9 = 3599.9 s. 닿으면 자동 완료(CapReached)
)

enum class BrewPhase { Idle, WaitingForPour, Running, Paused, Finished }
enum class StaleReason { Disconnected, Overload, NoData }

data class TimerSnapshot(val accumulatedNanos: Long, val runningSinceNanos: Long?) {
    fun elapsedNanos(nowNanos: Long): Long = accumulatedNanos + (runningSinceNanos?.let { nowNanos - it } ?: 0)
    companion object { val Zero = TimerSnapshot(0, null) }
}

data class StepGuide(
    val index: Int,                 // 현재 단계 (0부터)
    val remainingGrams: Double,     // 현재 단계 목표까지 남은 g, 0 이상
    val paceDeltaSec: Double,       // 경과 초 − recipe.plannedSecFor(무게, 경과 초, stepToleranceGrams)(기다리기 구간은 0). 양수 = 계획보다 늦음
    val lastStepReached: Boolean,
)

data class BrewState(
    val phase: BrewPhase,
    val timer: TimerSnapshot,
    val weightGrams: Double?,       // 마지막 유효 무게. stale이면 멈춘 값. 측정 전 null
    val flowGramsPerSec: Double,    // 평활 뒤, 항상 0 이상
    val stale: StaleReason?,
    val staleSinceNanos: Long?,     // "N초 전" 표시용
    val recipe: Recipe?,
    val guide: StepGuide?,          // recipe가 있고 Running/Paused/Finished일 때
    val startedAtNanos: Long?,      // 첫 Running 진입 시각
    val settings: BrewSettings,
)

sealed interface BrewInput {
    val atNanos: Long
    data class Reading(val reading: ScaleReading) : BrewInput { override val atNanos get() = reading.atNanos }
    data class Tared(override val atNanos: Long) : BrewInput
    /** ScaleClient.timerEvents(Notify)에서만. 원문 경로, v1.0.4에서는 생기지 않음(C-02-2). */
    data class ScaleTimerChanged(val state: ScaleTimerState, override val atNanos: Long) : BrewInput
    data class UserTimer(val action: TimerAction, override val atNanos: Long) : BrewInput
    data class CancelWaiting(override val atNanos: Long) : BrewInput
    data class SelectRecipe(val recipe: Recipe?, override val atNanos: Long) : BrewInput
    data class SettingsChanged(val settings: BrewSettings, override val atNanos: Long) : BrewInput
    data class ConnectionChanged(val connected: Boolean, override val atNanos: Long) : BrewInput
    data class Tick(override val atNanos: Long) : BrewInput
    data class Finish(override val atNanos: Long, val capReached: Boolean = false) : BrewInput
    data class Discard(override val atNanos: Long) : BrewInput
    data class NewBrew(override val atNanos: Long) : BrewInput
}

data class BrewSummary(
    val startedAtNanos: Long,
    val durationMs: Long,           // 타이머 경과
    val finalWeightGrams: Double?,
    val recipe: Recipe?,
)

sealed interface BrewEffect {
    data class SendTimer(val action: TimerAction) : BrewEffect
    data class AppendSample(val sample: BrewSample) : BrewEffect
    data object ClearSamples : BrewEffect
    data class Completed(val summary: BrewSummary) : BrewEffect
    data object Discarded : BrewEffect
    data class PhaseChanged(val from: BrewPhase, val to: BrewPhase, val cause: PhaseCause) : BrewEffect
    data class ValveCue(val event: ValveEvent) : BrewEffect       // 밸브를 바꿀 시각에 닿음(architecture.md 7.2 "밸브 신호")
}

enum class PhaseCause { User, ScaleButton, AutoStart, AutoFinish, CapReached }

/** 상태 기계 (architecture.md 7.2 전이 규칙표). 시간은 입력의 atNanos로만 받는다. 메인 스레드 전용. */
class BrewEngine(private val config: BrewConfig = BrewConfig(), settings: BrewSettings = BrewSettings()) {
    var state = BrewState(BrewPhase.Idle, TimerSnapshot.Zero, null, 0.0, null, null, null, null, null, settings)
        private set

    private var aboveThreshold = 0          // 자동 시작 연속 샘플 수
    private val pendingStart = ArrayList<Triple<Long, Float, Float>>()   // 확인 중인 넘은 샘플 (수신 ns, 무게, 유량). 시작하면 소급 샘플이 된다
    private var lastValidWeight: Double? = null   // 직전 정상 무게(급감 판정)
    private var tareAtNanos = 0L            // 마지막 Tared 시각 (WaitingForPour 무장 기준)
    private var armed = false               // 영점이 반영된 Reading을 본 뒤 true (C-0D-2). BrewState에 노출하지 않는다
    private var sampleCount = 0             // ClearSamples 뒤 낸 AppendSample 수
    private var connected = false
    private var noDataRefNanos: Long? = null                       // max(마지막 Reading, 마지막 ConnectionChanged(true))
    private val flowWindow = ArrayDeque<Pair<Long, Double>>()      // (수신 ns, 저울 유량), 정상 샘플만
    private val finishWindow = ArrayDeque<Pair<Long, Double>>()    // (타이머 경과 ns, 무게), 끝 시각 뒤 정상 샘플만
    private var valveCheckedNanos = 0L      // 밸브 신호를 마지막으로 확인한 타이머 경과(최댓값). BrewState에 노출하지 않는다

    private var effects = mutableListOf<BrewEffect>()
    private var cause = PhaseCause.User

    fun handle(input: BrewInput): List<BrewEffect> {
        val from = state.phase
        val at = input.atNanos
        effects = mutableListOf()
        cause = PhaseCause.User
        when (input) {
            is BrewInput.Reading -> onReading(input.reading)
            is BrewInput.Tick -> onTick(at)
            is BrewInput.Tared -> if (from == BrewPhase.Idle || from == BrewPhase.WaitingForPour) {
                toIdle(if (state.settings.autoStartEnabled) BrewPhase.WaitingForPour else BrewPhase.Idle)
                tareAtNanos = at
            }
            is BrewInput.ScaleTimerChanged -> onScaleTimer(input.state, at)
            is BrewInput.UserTimer -> onUserTimer(input.action, at)
            is BrewInput.CancelWaiting -> if (from == BrewPhase.WaitingForPour) toIdle()
            is BrewInput.SelectRecipe -> if (from == BrewPhase.Idle || from == BrewPhase.WaitingForPour) {
                state = state.copy(recipe = input.recipe)
            }
            is BrewInput.SettingsChanged -> {
                state = state.copy(settings = input.settings)
                if (from == BrewPhase.WaitingForPour && !input.settings.autoStartEnabled) toIdle()
            }
            is BrewInput.ConnectionChanged -> onConnection(input.connected, at)
            is BrewInput.Finish -> if (from == BrewPhase.Running || from == BrewPhase.Paused) {
                finish(at, if (input.capReached) PhaseCause.CapReached else PhaseCause.User, sendPause = from == BrewPhase.Running)
            }
            is BrewInput.Discard -> if (from == BrewPhase.Running || from == BrewPhase.Paused) discard()
            is BrewInput.NewBrew -> if (from == BrewPhase.Finished) {
                clearSamples()
                toIdle()
            }
        }
        // Tared·Connection 등은 guide를 건드리지 않는다(Running 중 Tared 불변 조건)
        if (input is BrewInput.Reading || input is BrewInput.Tick || state.phase != from) state = state.copy(guide = guideAt(at))
        if (state.phase != from) effects += BrewEffect.PhaseChanged(from, state.phase, cause)
        return effects
    }

    private fun onReading(r: ScaleReading) {
        val at = r.atNanos
        connected = true
        noDataRefNanos = maxOf(noDataRefNanos ?: at, at)
        val w = r.weightGrams
        val valid = !r.overload && w != null
        // 컵·서버를 들어 올림: 들기 전 손 압력이 만든 저울 유량이 1 s 평균에 남지 않게 창을 비운다(O-B8-1)
        val dropped = valid && lastValidWeight.let { it != null && it - w!! > config.weightDropGrams }
        if (valid) {
            if (dropped) flowWindow.clear()
            lastValidWeight = w
            flowWindow.addLast(at to r.scaleFlowGramsPerSec)
            state = state.copy(weightGrams = w, stale = null, staleSinceNanos = null)
        } else if (state.stale != StaleReason.Overload) {
            state = state.copy(stale = StaleReason.Overload, staleSinceNanos = at)   // weightGrams는 직전 정상 값 유지
        }
        updateFlow(at)

        if (state.phase == BrewPhase.WaitingForPour) {
            val threshold = state.settings.autoStartThresholdGrams
            if (!armed) {
                // 영점 전 무게(시각이 Tared 이전이거나 임계값 초과)는 무장하지 않고, 무장시킨 Reading은 세지 않는다
                armed = valid && at > tareAtNanos && Math.abs(w!!) <= threshold
                resetAutoStart()
            } else if (valid && w!! > threshold && !dropped) {
                // 끊김 없는 공백(NoData 등)도 확인 구간으로 세지 않는다: 이 샘플이 새 t₀
                if (pendingStart.lastOrNull()?.let { at - it.first > FINISH_MAX_GAP_NANOS } == true) resetAutoStart()
                aboveThreshold++
                pendingStart += Triple(at, w.toFloat(), state.flowGramsPerSec.toFloat())
            } else {
                resetAutoStart()   // 확인 구간 안에서 임계값 아래·음수·급감·과부하면 취소(F-B4-1)
            }
            val firstAt = pendingStart.firstOrNull()?.first
            if (firstAt != null && aboveThreshold >= config.autoStartConsecutive && at - firstAt >= config.autoStartConfirmNanos) autoStart(firstAt)
        }
        if (state.phase == BrewPhase.Running && !capReached(at)) {
            val elapsed = state.timer.elapsedNanos(at)
            emitSample(at, if (valid) w!!.toFloat() else Float.NaN, state.flowGramsPerSec.toFloat(), if (valid) 0 else FLAG_OVERLOAD)
            checkValve(at)
            val recipe = state.recipe
            if (valid && recipe != null && elapsed >= recipe.finishSec * NANOS_PER_SEC) {
                // 정상 샘플 공백(끊김·NoData·과부하)이 끼면 안정 창을 처음부터 다시 쌓는다
                if (finishWindow.isNotEmpty() && elapsed - finishWindow.last().first > FINISH_MAX_GAP_NANOS) finishWindow.clear()
                finishWindow.addLast(elapsed to w!!)
            }
            checkAutoFinish(at)
        }
    }

    private fun onTick(at: Long) {
        updateFlow(at)
        val ref = noDataRefNanos
        if (connected && ref != null && at - ref > config.noDataTimeoutNanos && state.stale != StaleReason.NoData) {
            state = state.copy(stale = StaleReason.NoData, staleSinceNanos = ref)
        }
        if (state.phase == BrewPhase.Running && !capReached(at)) {
            checkValve(at)
            checkAutoFinish(at)
        }
    }

    /** valveCheckedNanos < atSec ≤ 경과인 이벤트 중 마지막 하나만 낸다(공백 뒤에도 한 번, 늦게 온 이른 Reading은 다시 내지 않음). */
    private fun checkValve(at: Long) {
        val e = state.timer.elapsedNanos(at)
        state.recipe?.valveSchedule
            ?.lastOrNull { val t = it.atSec * NANOS_PER_SEC; valveCheckedNanos < t && t <= e }
            ?.let { effects += BrewEffect.ValveCue(it) }
        valveCheckedNanos = maxOf(valveCheckedNanos, e)
    }

    private fun onConnection(isConnected: Boolean, at: Long) {
        if (isConnected) {
            connected = true
            noDataRefNanos = maxOf(noDataRefNanos ?: at, at)   // Disconnected는 첫 정상 Reading까지 유지
            return
        }
        resetAutoStart()   // 끊긴 사이의 공백을 확인 구간으로 세지 않는다
        lastValidWeight = null   // 끊긴 사이 바뀐 무게를 재연결 첫 샘플의 급감으로 보지 않는다
        if (connected && state.phase == BrewPhase.Running) {
            emitSample(at, state.weightGrams?.toFloat() ?: Float.NaN, state.flowGramsPerSec.toFloat(), FLAG_DISCONNECTED)
        }
        connected = false
        if (state.stale != StaleReason.Disconnected) state = state.copy(stale = StaleReason.Disconnected, staleSinceNanos = at)
    }

    private fun onScaleTimer(scale: ScaleTimerState, at: Long) {
        val phase = state.phase
        when (scale) {
            ScaleTimerState.Running -> when (phase) {
                BrewPhase.Idle, BrewPhase.WaitingForPour -> run(at, PhaseCause.ScaleButton, sendStart = false, fresh = true)
                BrewPhase.Paused -> run(at, PhaseCause.ScaleButton, sendStart = false, fresh = false)
                else -> {}
            }
            ScaleTimerState.Paused -> if (phase == BrewPhase.Running) pause(at, PhaseCause.ScaleButton, send = false)
            ScaleTimerState.Reset -> if (phase == BrewPhase.Running || phase == BrewPhase.Paused) {
                // 실수로 누른 버튼으로 기록을 잃지 않게 샘플이 있으면 요약(S6)으로 보낸다
                if (sampleCount > 0) {
                    finish(at, PhaseCause.ScaleButton, sendPause = false)
                } else {
                    effects += BrewEffect.Discarded
                    toIdle(cause = PhaseCause.ScaleButton)
                }
            }
        }
    }

    private fun onUserTimer(action: TimerAction, at: Long) {
        val phase = state.phase
        when (action) {
            TimerAction.Start -> when (phase) {
                BrewPhase.Idle, BrewPhase.WaitingForPour -> run(at, PhaseCause.User, sendStart = true, fresh = true)
                BrewPhase.Paused -> run(at, PhaseCause.User, sendStart = true, fresh = false)
                else -> {}
            }
            TimerAction.Pause -> if (phase == BrewPhase.Running) pause(at, PhaseCause.User, send = true)
            TimerAction.Reset -> if (phase == BrewPhase.Paused) discard()
        }
    }

    private fun run(at: Long, cause: PhaseCause, sendStart: Boolean, fresh: Boolean) {
        state = if (fresh) {
            clearSamples()
            resetAutoStart()   // "지금 시작"이 확인 구간 중이어도 남은 샘플을 버린다
            valveCheckedNanos = 0   // atSec 0 이벤트는 준비 상태라 신호가 없다
            state.copy(timer = TimerSnapshot(0, at), startedAtNanos = at)
        } else {
            state.copy(timer = TimerSnapshot(state.timer.accumulatedNanos, at))
        }
        if (sendStart) effects += BrewEffect.SendTimer(TimerAction.Start)
        state = state.copy(phase = BrewPhase.Running)
        this.cause = cause
    }

    /** 처음 넘은 샘플 시각으로 소급해 시작하고, 확인 구간의 앞 샘플을 낸다. 마지막(지금) 샘플은 onReading의 Running 처리가 낸다. */
    private fun autoStart(firstAt: Long) {
        val buffered = pendingStart.dropLast(1)
        run(firstAt, PhaseCause.AutoStart, sendStart = true, fresh = true)
        buffered.forEach { (t, w, f) -> emitSample(t, w, f, 0) }
    }

    private fun resetAutoStart() {
        aboveThreshold = 0
        pendingStart.clear()
    }

    private fun pause(at: Long, cause: PhaseCause, send: Boolean) {
        state = state.copy(phase = BrewPhase.Paused, timer = state.timer.pausedAt(at))
        if (send) effects += BrewEffect.SendTimer(TimerAction.Pause)
        this.cause = cause
    }

    private fun finish(at: Long, cause: PhaseCause, sendPause: Boolean) {
        val timer = state.timer.pausedAt(at)
        state = state.copy(phase = BrewPhase.Finished, timer = timer)
        effects += BrewEffect.Completed(
            BrewSummary(state.startedAtNanos ?: at, timer.elapsedNanos(at) / NANOS_PER_MS, state.weightGrams, state.recipe),
        )
        if (sendPause) effects += BrewEffect.SendTimer(TimerAction.Pause)
        this.cause = cause
    }

    private fun discard() {
        effects += BrewEffect.Discarded
        clearSamples()
        effects += BrewEffect.SendTimer(TimerAction.Reset)
        toIdle()
    }

    /** Idle(또는 Tared 뒤 WaitingForPour)로, 타이머 0. */
    private fun toIdle(phase: BrewPhase = BrewPhase.Idle, cause: PhaseCause = PhaseCause.User) {
        state = state.copy(phase = phase, timer = TimerSnapshot.Zero, startedAtNanos = null)
        resetAutoStart()
        armed = false
        this.cause = cause
    }

    private fun clearSamples() {
        effects += BrewEffect.ClearSamples
        sampleCount = 0
        finishWindow.clear()
    }

    private fun emitSample(at: Long, weight: Float, flow: Float, flags: Int) {
        val offsetMs = (state.timer.elapsedNanos(at) / NANOS_PER_MS).toInt()
        effects += BrewEffect.AppendSample(BrewSample(offsetMs, weight, flow, flags))
        sampleCount++
    }

    /** 상한 판정은 샘플 추가·자동 완료보다 먼저다. 닿으면 타이머를 상한에 고정하고 끝낸다. */
    private fun capReached(at: Long): Boolean {
        if (state.timer.elapsedNanos(at) < config.maxElapsedNanos) return false
        state = state.copy(timer = TimerSnapshot(config.maxElapsedNanos, null))
        finish(at, PhaseCause.CapReached, sendPause = true)
        return true
    }

    private fun checkAutoFinish(at: Long) {
        val recipe = state.recipe ?: return
        if (state.stale != null) return                  // 끊김·NoData·과부하 중의 멈춘 값으로 끝내지 않는다
        val weight = state.weightGrams ?: return
        val elapsed = state.timer.elapsedNanos(at)
        if (finishWindow.isEmpty() || elapsed - finishWindow.last().first > FINISH_MAX_GAP_NANOS) return   // 창이 지금까지 이어져야 한다
        if (elapsed < recipe.finishSec * NANOS_PER_SEC) return
        if (weight < recipe.steps.last().targetGrams - config.stepToleranceGrams) return
        val since = elapsed - config.finishStableNanos
        // 창 시작 시각 이전의 샘플은 하나만 남긴다(창 경계의 무게)
        while (finishWindow.size > 1 && finishWindow[1].first <= since) finishWindow.removeFirst()
        if (finishWindow.isEmpty() || finishWindow.first().first > since) return
        val min = finishWindow.minOf { it.second }
        val max = finishWindow.maxOf { it.second }
        if (max - min <= config.finishStableGrams) finish(at, PhaseCause.AutoFinish, sendPause = true)
    }

    private fun updateFlow(now: Long) {
        while (flowWindow.isNotEmpty() && now - flowWindow.first().first >= config.flowWindowNanos) flowWindow.removeFirst()
        val avg = if (flowWindow.isEmpty()) 0.0 else flowWindow.sumOf { it.second } / flowWindow.size
        state = state.copy(flowGramsPerSec = maxOf(0.0, avg))   // 음수는 0(방어). 무게 변화로 유량을 고치지 않는다
    }

    private fun guideAt(at: Long): StepGuide? {
        val recipe = state.recipe ?: return null
        if (state.phase != BrewPhase.Running && state.phase != BrewPhase.Paused && state.phase != BrewPhase.Finished) return null
        val weight = state.weightGrams ?: 0.0
        val firstUnreached = recipe.steps.indexOfFirst { weight < it.targetGrams - config.stepToleranceGrams }
        val index = if (firstUnreached >= 0) firstUnreached else recipe.steps.lastIndex
        val elapsedSec = state.timer.elapsedNanos(at) / NANOS_PER_SEC.toDouble()
        return StepGuide(
            index = index,
            remainingGrams = maxOf(0.0, recipe.steps[index].targetGrams - weight),
            paceDeltaSec = elapsedSec - recipe.plannedSecFor(weight, elapsedSec, config.stepToleranceGrams),
            lastStepReached = firstUnreached < 0,
        )
    }

    private fun TimerSnapshot.pausedAt(at: Long) = TimerSnapshot(elapsedNanos(at), null)

    private companion object {
        const val NANOS_PER_SEC = 1_000_000_000L
        const val NANOS_PER_MS = 1_000_000L
        const val FINISH_MAX_GAP_NANOS = 1_000_000_000L   // 안정 창·자동 시작 확인 구간 샘플 사이 최대 공백. 10 Hz 알림의 300 ms 누락은 허용
    }
}
