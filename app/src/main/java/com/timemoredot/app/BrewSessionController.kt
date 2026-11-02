package com.timemoredot.app

import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.SessionDraft
import com.timemoredot.brew.BrewConfig
import com.timemoredot.brew.BrewEffect
import com.timemoredot.brew.BrewEngine
import com.timemoredot.brew.BrewInput
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewState
import com.timemoredot.brew.BrewSummary
import com.timemoredot.brew.PhaseCause
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.ValveEvent
import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface BrewEvent {
    data class TareFailed(val reason: CommandFailure) : BrewEvent
    data object TareSucceeded : BrewEvent
    data class ScaleTimerSyncFailed(val reason: CommandFailure) : BrewEvent
    data class PhaseChanged(val from: BrewPhase, val to: BrewPhase, val cause: PhaseCause) : BrewEvent
    data class Finished(val sessionId: Long) : BrewEvent
    data object SaveFailed : BrewEvent
    data class ValveCue(val event: ValveEvent) : BrewEvent
}

/**
 * architecture.md 8.2. 앱 범위에서 하나. 엔진 입력 수집·샘플 버퍼·임시 저장을 맡는다. 메인 스레드 전용(잠금 없음, 3절).
 * logError는 Log 대신 주입한다(JVM 단위 테스트에서 android.util.Log를 부르지 않게).
 */
class BrewSessionController(
    private val scale: ScaleClient,
    private val brews: BrewRepository,
    private val prefs: AppPrefs,
    private val clock: MonotonicClock,
    private val wallClockMs: () -> Long,
    private val scope: CoroutineScope,
    config: BrewConfig = BrewConfig(),
    sampleCapacity: Int = 36_000,
    private val timerSyncDeadlineNanos: Long = 4_000_000_000,
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val engine = BrewEngine(config, prefs.brewSettings.value)
    private val _state = MutableStateFlow(engine.state)
    val state: StateFlow<BrewState> = _state.asStateFlow()
    val samples = SampleBuffer(sampleCapacity)
    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()
    private val _tareInFlight = MutableStateFlow(false)
    val tareInFlight: StateFlow<Boolean> = _tareInFlight.asStateFlow()
    private val _draftId = MutableStateFlow<Long?>(null)
    val draftId: StateFlow<Long?> = _draftId.asStateFlow()
    private val _lastUnit = MutableStateFlow<WeightUnit?>(null)
    val lastUnit: StateFlow<WeightUnit?> = _lastUnit.asStateFlow()
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()
    private val _events = Channel<BrewEvent>(Channel.UNLIMITED)
    val events: Flow<BrewEvent> = _events.receiveAsFlow()

    private val sendQueue = Channel<ScaleCommand>(Channel.UNLIMITED)   // 효과 순서대로 하나씩 보낸다
    private var connected = false
    private var syncJob: Job? = null
    private var autosaveJob: Job? = null
    private var startedAtEpochMs = 0L
    private var pendingSummary: BrewSummary? = null   // 확정 저장을 기다리는(또는 실패한) 요약
    private var saving = false

    init {
        scope.launch { for (cmd in sendQueue) sendTimer(cmd) }
        scope.launch {
            scale.readings.collect {
                _lastUnit.value = it.sourceUnit
                handle(BrewInput.Reading(it))
            }
        }
        scope.launch { scale.timerEvents.collect { handle(BrewInput.ScaleTimerChanged(it, clock.nowNanos())) } }
        scope.launch { scale.connection.collect(::onConnection) }
        scope.launch {
            prefs.brewSettings.collect { if (it != engine.state.settings) handle(BrewInput.SettingsChanged(it, clock.nowNanos())) }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                handle(BrewInput.Tick(clock.nowNanos()))
            }
        }
    }

    fun tare() {
        if (_tareInFlight.value) return
        val ph = _state.value.phase
        if (ph != BrewPhase.Idle && ph != BrewPhase.WaitingForPour) return   // 추출 중 영점은 기록을 망친다(스낵바 '다시'가 늦게 눌려도)
        _tareInFlight.value = true
        scope.launch {
            val result = try { scale.send(ScaleCommand.Tare) } finally { _tareInFlight.value = false }
            when (result) {
                CommandResult.Success -> {
                    handle(BrewInput.Tared(clock.nowNanos()))
                    _events.trySend(BrewEvent.TareSucceeded)
                }
                is CommandResult.Failed -> _events.trySend(BrewEvent.TareFailed(result.reason))
                is CommandResult.Response -> _events.trySend(BrewEvent.TareFailed(CommandFailure.Rejected))   // Write에 Read 응답은 오지 않는다(방어)
            }
        }
    }

    /**
     * 새 BrewViewModel이 수집을 시작하기 전에 부른다(architecture.md 9.4: Activity를 다시 열면 VM만 새로 생긴다).
     * 이전 VM이 받지 못한 일회성 이벤트(알림·스낵바·진동)는 그때 값이 아니므로 버리고, 확정 저장 결과 Finished만 남긴다.
     * SaveFailed는 VM이 saveFailed 상태로 다시 낸다.
     */
    fun dropStaleEvents() {
        var finished: BrewEvent.Finished? = null
        while (true) { val e = _events.tryReceive().getOrNull() ?: break; if (e is BrewEvent.Finished) finished = e }
        finished?.let { _events.trySend(it) }
    }

    fun startTimer() = handle(BrewInput.UserTimer(TimerAction.Start, clock.nowNanos()))
    fun pauseTimer() = handle(BrewInput.UserTimer(TimerAction.Pause, clock.nowNanos()))
    fun resetTimer() = handle(BrewInput.UserTimer(TimerAction.Reset, clock.nowNanos()))
    fun cancelWaiting() = handle(BrewInput.CancelWaiting(clock.nowNanos()))
    fun finish() = handle(BrewInput.Finish(clock.nowNanos()))
    fun discard() = handle(BrewInput.Discard(clock.nowNanos()))
    fun selectRecipe(recipe: Recipe?) = handle(BrewInput.SelectRecipe(recipe, clock.nowNanos()))

    fun newBrew() {
        handle(BrewInput.NewBrew(clock.nowNanos()))
        if (engine.state.phase == BrewPhase.Idle) {
            pendingSummary = null
            _saveFailed.value = false
            _draftId.value = null
        }
    }

    /** SaveFailed 뒤 "다시 시도". 대기 중인 실패 저장이 없으면 아무것도 하지 않는다. */
    fun retrySave() {
        if (!_saveFailed.value || pendingSummary == null || saving) return
        _saveFailed.value = false
        scope.launch { saveFinal() }
    }

    private fun onConnection(c: ConnectionState) {
        val now = c is ConnectionState.Connected
        if (now == connected) return
        connected = now
        handle(BrewInput.ConnectionChanged(now, clock.nowNanos()))
        syncJob?.cancel()   // 기한 전에 끊기면 그 연결의 맞추기는 버린다
        syncJob = if (now) scope.launch { syncScaleTimer() } else null
    }

    /** 연결 직후 맞추기(8.2): 그 연결에서 처음 채워진 scaleTimer를 한 번 본다. 기한까지 null이면 미상. */
    private suspend fun syncScaleTimer() {
        val scaleTimer = withTimeoutOrNull(timerSyncDeadlineNanos / 1_000_000) { scale.status.first { it.scaleTimer != null }.scaleTimer }
        val action = when (engine.state.phase) {
            BrewPhase.Running -> if (scaleTimer != ScaleTimerState.Running) TimerAction.Start else null   // 미상(null)도 Start(B6)
            BrewPhase.Paused -> if (scaleTimer == ScaleTimerState.Running) TimerAction.Pause else null
            else -> null
        }
        if (action != null) sendQueue.trySend(ScaleCommand.SetTimer(action))
    }

    private suspend fun sendTimer(cmd: ScaleCommand) {
        val r = scale.send(cmd)
        if (r is CommandResult.Failed) _events.trySend(BrewEvent.ScaleTimerSyncFailed(r.reason))
    }

    private fun handle(input: BrewInput) {
        val effects = engine.handle(input)
        var full = false
        for (e in effects) when (e) {
            is BrewEffect.SendTimer -> sendQueue.trySend(ScaleCommand.SetTimer(e.action))
            is BrewEffect.AppendSample -> if (!samples.append(e.sample)) full = true
            BrewEffect.ClearSamples -> samples.clear()
            is BrewEffect.Completed -> onCompleted(e.summary)
            BrewEffect.Discarded -> onDiscarded()
            is BrewEffect.PhaseChanged -> {
                if (e.to == BrewPhase.Running && (e.from == BrewPhase.Idle || e.from == BrewPhase.WaitingForPour)) startedAtEpochMs = wallClockMs()
                _events.trySend(BrewEvent.PhaseChanged(e.from, e.to, e.cause))
            }
            is BrewEffect.ValveCue -> _events.trySend(BrewEvent.ValveCue(e.event))
        }
        _state.value = engine.state
        _sampleCount.value = samples.size
        val phase = engine.state.phase
        if ((phase == BrewPhase.Running || phase == BrewPhase.Paused) && autosaveJob?.isActive != true) {
            autosaveJob = scope.launch { while (isActive) { delay(AUTOSAVE_MS); saveDraftPeriodic() } }
        }
        if (full) handle(BrewInput.Finish(input.atNanos, capReached = true))   // 버퍼 가득 참 = 자동 완료(CapReached)
    }

    private suspend fun saveDraftPeriodic() {
        val s = engine.state
        val draft = SessionDraft(
            _draftId.value, startedAtEpochMs, s.recipe, s.weightGrams,
            s.timer.elapsedNanos(clock.nowNanos()) / 1_000_000, samples.toBlob(),
        )
        // DB 호출은 끊지 않는다: 끝(완료·버리기)의 cancelAndJoin이 삽입을 커밋한 뒤 id만 버려 행이 고아가 되지 않게
        runCatchingSave { withContext(NonCancellable) { brews.saveDraft(draft) } }
            .onSuccess { _draftId.value = it }
            .onFailure { logError("periodic saveDraft", it) }   // 다음 주기에 다시 한다. 이벤트 없음
    }

    private fun onCompleted(summary: BrewSummary) {
        pendingSummary = summary
        val job = autosaveJob
        autosaveJob = null
        scope.launch {
            job?.cancelAndJoin()   // 진행 중인 주기 저장이 확정 저장 뒤에 끝나 행을 덮지 않게
            saveFinal()
        }
    }

    private suspend fun saveFinal() {
        val s = pendingSummary ?: return
        saving = true
        val draft = SessionDraft(_draftId.value, startedAtEpochMs, s.recipe, s.finalWeightGrams, s.durationMs, samples.toBlob())
        val result = runCatchingSave { brews.saveDraft(draft) }
        saving = false
        result.onSuccess { id ->
            _draftId.value = id
            pendingSummary = null
            _saveFailed.value = false
            _events.trySend(BrewEvent.Finished(id))
        }.onFailure {
            logError("final saveDraft", it)
            _saveFailed.value = true   // 버퍼는 지우지 않고 요약을 들고 있는다
            _events.trySend(BrewEvent.SaveFailed)
        }
    }

    private fun onDiscarded() {
        val before = _draftId.value
        _draftId.value = null
        val job = autosaveJob
        autosaveJob = null
        scope.launch {
            job?.cancelAndJoin()
            val id = _draftId.value ?: before   // join 전에 끝난(끊지 않는) 주기 저장이 첫 id를 남겼을 수 있다
            _draftId.value = null
            if (id != null) runCatchingSave { brews.deleteSession(id) }.onFailure { logError("deleteSession", it) }   // 남은 행은 다음 실행 D2 대상
        }
    }

    private inline fun <T> runCatchingSave(block: () -> T): Result<T> =
        try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }

    private companion object {
        const val TICK_MS = 1_000L
        const val AUTOSAVE_MS = 10_000L
    }
}
