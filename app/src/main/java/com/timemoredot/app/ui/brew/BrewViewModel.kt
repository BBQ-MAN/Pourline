package com.timemoredot.app.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.AppPrefs
import com.timemoredot.app.BrewEvent
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.PermissionMonitor
import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.RecipeRepository
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.connect.connectionUi
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.BrewState
import com.timemoredot.brew.PhaseCause
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.TimerSnapshot
import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** architecture.md 9.3. */
sealed interface ReadingUi {
    data object None : ReadingUi
    data object UnitUnknown : ReadingUi
    data object UnitUnknownTimedOut : ReadingUi
    data class Live(val weightGrams: Double, val flowGramsPerSec: Double, val unit: WeightUnit) : ReadingUi
    data class Stale(val lastWeightGrams: Double, val cause: StaleReason, val sinceNanos: Long, val unit: WeightUnit) : ReadingUi
}

private fun BrewPhase.brewing() = this == BrewPhase.Running || this == BrewPhase.Paused

/**
 * 9.3 매핑(위에서부터 처음 맞는 것). beforeBluetoothOff = BluetoothOff로 바뀌기 직전 ConnectionUi(VM이 기억, 처음부터 BluetoothOff면 null).
 * 연결이 Connected가 아닌데 1·1-1에 걸리지 않으면 엔진 stale이 아직 없어도 멈춘 값(Disconnected)이다.
 */
fun readingUi(
    connection: ConnectionUi, beforeBluetoothOff: ConnectionUi?, status: ScaleStatus, phase: BrewPhase,
    weightGrams: Double?, flowGramsPerSec: Double, stale: StaleReason?, staleSinceNanos: Long?, unit: WeightUnit,
): ReadingUi {
    val noLiveLink = when (connection) {
        ConnectionUi.NoDevice, ConnectionUi.Disconnected, is ConnectionUi.Connecting, is ConnectionUi.Scanning, ConnectionUi.ScanEmpty,
        ConnectionUi.ScanFailed, ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied, ConnectionUi.LocationOff,
        is ConnectionUi.Failed -> true
        else -> false
    }
    if (noLiveLink && (weightGrams == null || !phase.brewing())) return ReadingUi.None
    if (connection == ConnectionUi.BluetoothOff && !phase.brewing() &&
        beforeBluetoothOff !is ConnectionUi.Connected && beforeBluetoothOff !is ConnectionUi.Reconnecting
    ) return ReadingUi.None
    if (connection is ConnectionUi.Connected && status.unit == null) {
        return if (status.unitReadFailed) ReadingUi.UnitUnknownTimedOut else ReadingUi.UnitUnknown
    }
    if (weightGrams == null) return ReadingUi.None
    val cause = stale ?: if (connection !is ConnectionUi.Connected) StaleReason.Disconnected else null
    if (cause != null) return ReadingUi.Stale(weightGrams, cause, staleSinceNanos ?: 0L, unit)
    return ReadingUi.Live(weightGrams, flowGramsPerSec, unit)
}

enum class BrewBanner {
    BluetoothOff, PermissionMissing, PermissionPermanentlyDenied, LocationOff, Failed, ScanFailed, Disconnected,
    Reconnecting, Connecting, Overload, UnitUnknownTimedOut, NoData, ModelMismatch, WaitingForPour,
}

/** 배너 원래 값(10.3 우선순위, NoData 2 s 유지 전). */
fun rawBanner(
    connection: ConnectionUi, phase: BrewPhase, stale: StaleReason?, status: ScaleStatus,
    overloadDismissed: Boolean, modelWarningDismissed: Boolean,
): BrewBanner? = when {
    connection == ConnectionUi.BluetoothOff -> BrewBanner.BluetoothOff
    connection == ConnectionUi.PermissionMissing -> BrewBanner.PermissionMissing
    connection == ConnectionUi.PermissionPermanentlyDenied -> BrewBanner.PermissionPermanentlyDenied
    connection == ConnectionUi.LocationOff -> BrewBanner.LocationOff
    connection is ConnectionUi.Failed -> BrewBanner.Failed
    connection == ConnectionUi.ScanFailed -> BrewBanner.ScanFailed
    connection == ConnectionUi.Disconnected && phase.brewing() -> BrewBanner.Disconnected
    connection is ConnectionUi.Reconnecting -> BrewBanner.Reconnecting
    connection is ConnectionUi.Connecting -> BrewBanner.Connecting
    stale == StaleReason.Overload && !overloadDismissed -> BrewBanner.Overload
    connection is ConnectionUi.Connected && status.unit == null && status.unitReadFailed -> BrewBanner.UnitUnknownTimedOut
    stale == StaleReason.NoData && status.unit != null -> BrewBanner.NoData
    connection is ConnectionUi.Connected && connection.device.modelMismatch && !modelWarningDismissed -> BrewBanner.ModelMismatch
    phase == BrewPhase.WaitingForPour -> BrewBanner.WaitingForPour
    else -> null
}

data class BrewUiState(
    val connection: ConnectionUi,
    val batteryPercent: Int?,
    val displayUnit: WeightUnit,
    val phase: BrewPhase,
    val timer: TimerSnapshot,
    val recipe: Recipe?,
    val currentStepIndex: Int?,
    val lastStepReached: Boolean,
    val banner: BrewBanner?,
    val tareInFlight: Boolean,
    val canTare: Boolean,
    val canStart: Boolean,
    val canChangeRecipeOrSettings: Boolean,
    val canFindOtherScale: Boolean,
    val showBottomBar: Boolean,
    val keepScreenOn: Boolean,
    val settings: BrewSettings,
    val recoverableDraft: SessionSummary? = null,   // D2
    val showResetConfirm: Boolean = false,
)

data class BrewReadout(
    val reading: ReadingUi,
    val remainingGrams: Double?,
    val paceDeltaSec: Double?,
    val sampleCount: Int,
)

sealed interface BrewUiEvent {
    data class Snackbar(val message: BrewMessage, val retryTare: Boolean, val sessionId: Long? = null) : BrewUiEvent
    data class OpenSummary(val sessionId: Long, val recovered: Boolean) : BrewUiEvent
    data object OpenConnect : BrewUiEvent
    data class Announce(val announcement: BrewAnnouncement) : BrewUiEvent
    data class Haptic(val atNanos: Long) : BrewUiEvent
}

enum class BrewMessage { TareRejected, TareTimeout, TareNotConnected, ScaleTimerSyncFailed, Saved, SaveFailed }

enum class BrewAnnouncement {
    Connected, Reconnected, ReconnectStarted, ConnectFailed, Disconnected,
    BluetoothOffDuringBrew, PermissionLostDuringBrew, FailedDuringBrew, DisconnectedDuringBrew,
    Overload, OverloadCleared, NoData,
    TareDone,
    TimerStarted, TimerPaused, TimerResumed, TimerReset,
    ScaleTimerStarted, ScaleTimerPaused, ScaleTimerResumed, ScaleTimerReset, ScaleTimerResetToSummary,
    AutoStartWaiting, AutoStarted, AutoFinished,
    CapReached,
    ValveOpen, ValveClose,
}

/** 9.4 PhaseChanged → BrewAnnouncement 표. null = 알림 없음. */
fun phaseAnnouncement(from: BrewPhase, to: BrewPhase, cause: PhaseCause): BrewAnnouncement? = when (cause) {
    PhaseCause.User -> when {
        from == BrewPhase.Idle && to == BrewPhase.WaitingForPour -> BrewAnnouncement.AutoStartWaiting
        to == BrewPhase.Running && from == BrewPhase.Paused -> BrewAnnouncement.TimerResumed
        to == BrewPhase.Running -> BrewAnnouncement.TimerStarted
        to == BrewPhase.Paused -> BrewAnnouncement.TimerPaused
        to == BrewPhase.Idle && from.brewing() -> BrewAnnouncement.TimerReset
        else -> null   // WaitingForPour → Idle, 사용자 완료, NewBrew
    }
    PhaseCause.ScaleButton -> when {
        to == BrewPhase.Running && from == BrewPhase.Paused -> BrewAnnouncement.ScaleTimerResumed
        to == BrewPhase.Running -> BrewAnnouncement.ScaleTimerStarted
        to == BrewPhase.Paused -> BrewAnnouncement.ScaleTimerPaused
        to == BrewPhase.Idle -> BrewAnnouncement.ScaleTimerReset
        to == BrewPhase.Finished -> BrewAnnouncement.ScaleTimerResetToSummary
        else -> null
    }
    PhaseCause.AutoStart -> BrewAnnouncement.AutoStarted
    PhaseCause.AutoFinish -> BrewAnnouncement.AutoFinished
    PhaseCause.CapReached -> BrewAnnouncement.CapReached
}

/** ConnectionUi 종류 전이 알림(9.4 enum 주석). 같은 종류 안의 값 변화는 전이가 아니다. */
fun connectionAnnouncement(prev: ConnectionUi, next: ConnectionUi, phase: BrewPhase): BrewAnnouncement? {
    fun group(c: ConnectionUi): Any = when (c) {
        ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied -> "perm"
        else -> c::class
    }
    if (group(prev) == group(next)) return null
    val brewing = phase.brewing()
    return when (next) {
        is ConnectionUi.Connected -> when (prev) {
            is ConnectionUi.Connecting -> BrewAnnouncement.Connected
            is ConnectionUi.Reconnecting -> BrewAnnouncement.Reconnected
            else -> null
        }
        is ConnectionUi.Reconnecting -> if (prev is ConnectionUi.Connected) BrewAnnouncement.ReconnectStarted else null
        is ConnectionUi.Failed -> if (brewing) BrewAnnouncement.FailedDuringBrew else BrewAnnouncement.ConnectFailed
        ConnectionUi.Disconnected -> when {
            brewing -> BrewAnnouncement.DisconnectedDuringBrew
            prev is ConnectionUi.Connected || prev is ConnectionUi.Connecting || prev is ConnectionUi.Reconnecting -> BrewAnnouncement.Disconnected
            else -> null
        }
        ConnectionUi.BluetoothOff -> if (brewing) BrewAnnouncement.BluetoothOffDuringBrew else null
        ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied -> if (brewing) BrewAnnouncement.PermissionLostDuringBrew else null
        else -> null
    }
}

private data class ConnTrack(val current: ConnectionUi, val beforeBluetoothOff: ConnectionUi?)
private data class Low(
    val phase: BrewPhase, val timer: TimerSnapshot, val recipe: Recipe?, val stepIndex: Int?, val lastStepReached: Boolean, val stale: StaleReason?,
)
private fun BrewState.low() = Low(phase, timer, recipe, guide?.index, guide?.lastStepReached ?: false, stale)

private const val NO_DATA_HOLD_NANOS = 2_000_000_000L

/** architecture.md 9.4. 화면 루트는 uiState만, 숫자·그래프 컴포저블은 readout을 각자 수집한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModel(
    private val controller: BrewSessionController,
    private val scale: ScaleClient,
    private val recipes: RecipeRepository,
    private val brews: BrewRepository,
    private val prefs: AppPrefs,
    private val permission: PermissionMonitor,
    val clock: MonotonicClock,
) : ViewModel(), BrewCallbacks {
    val samples: SampleBuffer get() = controller.samples

    private val _events = Channel<BrewUiEvent>(Channel.UNLIMITED)
    val events: Flow<BrewUiEvent> = _events.receiveAsFlow()

    private val connectionFlow = combine(scale.connection, scale.scanResults, permission.status, scale.lastDeviceAddress) { c, s, p, l ->
        connectionUi(c, s, p, hasSavedDevice = l != null)
    }.distinctUntilChanged()
    private val conn = MutableStateFlow(ConnTrack(currentConnectionUi(), null))
    private val overloadDismissed = MutableStateFlow(false)
    private val modelDismissTick = MutableStateFlow(0)
    private val showResetConfirm = MutableStateFlow(false)
    private val recoverableDraft = MutableStateFlow<SessionSummary?>(null)   // D2

    private fun currentConnectionUi() = connectionUi(
        scale.connection.value, scale.scanResults.value, permission.status.value, scale.lastDeviceAddress.value != null,
    )

    private fun reading(t: ConnTrack, s: BrewState, status: ScaleStatus, lastUnit: WeightUnit?) = readingUi(
        t.current, t.beforeBluetoothOff, status, s.phase, s.weightGrams, s.flowGramsPerSec, s.stale, s.staleSinceNanos,
        status.unit ?: lastUnit ?: WeightUnit.Gram,
    )

    private fun currentReadout() = BrewReadout(
        reading(conn.value, controller.state.value, scale.status.value, controller.lastUnit.value),
        controller.state.value.guide?.remainingGrams, controller.state.value.guide?.paceDeltaSec, controller.sampleCount.value,
    )

    private val readoutFlow: Flow<BrewReadout> =
        combine(conn, controller.state, scale.status, controller.lastUnit, controller.sampleCount) { t, s, st, u, n ->
            BrewReadout(reading(t, s, st, u), s.guide?.remainingGrams, s.guide?.paceDeltaSec, n)
        }.distinctUntilChanged()

    val readout: StateFlow<BrewReadout> = readoutFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentReadout())

    private fun modelDismissed(c: ConnectionUi) = c is ConnectionUi.Connected && prefs.isModelWarningDismissed(c.device.address)

    private var noDataHoldUntil = 0L   // NoData가 풀린 뒤 이 시각까지 NoData를 유지(Long.MAX_VALUE = 지금 NoData)

    private val bannerFlow: Flow<BrewBanner?> = combine(
        conn, controller.state.map { it.phase to it.stale }.distinctUntilChanged(), scale.status, overloadDismissed, modelDismissTick,
    ) { t, (phase, stale), status, od, _ -> rawBanner(t.current, phase, stale, status, od, modelDismissed(t.current)) }
        .distinctUntilChanged()
        .transformLatest { raw ->
            val now = clock.nowNanos()
            if (raw == BrewBanner.NoData) { noDataHoldUntil = Long.MAX_VALUE; emit(raw); return@transformLatest }
            if (noDataHoldUntil == Long.MAX_VALUE) noDataHoldUntil = now + NO_DATA_HOLD_NANOS
            val lower = raw == null || raw.ordinal > BrewBanner.NoData.ordinal
            if (lower && noDataHoldUntil > now) {
                emit(BrewBanner.NoData)   // 깜빡임 방지(ui-design 3.1). 더 높은 우선순위가 오면 그쪽이 이긴다
                delay((noDataHoldUntil - now) / 1_000_000)
            }
            noDataHoldUntil = 0L
            emit(raw)
        }

    private val readingLive = readoutFlow.map { it.reading is ReadingUi.Live }.distinctUntilChanged()

    private data class Part(val t: ConnTrack, val low: Low, val status: ScaleStatus, val lastUnit: WeightUnit?, val live: Boolean)

    private fun build(p: Part, tare: Boolean, settings: BrewSettings, banner: BrewBanner?, reset: Boolean, draft: SessionSummary? = null): BrewUiState {
        val phase = p.low.phase
        val idleLike = phase == BrewPhase.Idle || phase == BrewPhase.WaitingForPour
        val connected = p.t.current is ConnectionUi.Connected
        return BrewUiState(
            connection = p.t.current,
            batteryPercent = p.status.batteryPercent,
            displayUnit = p.status.unit ?: p.lastUnit ?: WeightUnit.Gram,
            phase = phase, timer = p.low.timer, recipe = p.low.recipe,
            currentStepIndex = p.low.stepIndex, lastStepReached = p.low.lastStepReached,
            banner = banner, tareInFlight = tare,
            canTare = connected && p.live && idleLike,
            canStart = connected && p.status.unit != null,
            canChangeRecipeOrSettings = idleLike, canFindOtherScale = idleLike,
            showBottomBar = idleLike, keepScreenOn = phase.brewing(),
            settings = settings, recoverableDraft = draft, showResetConfirm = reset,
        )
    }

    private fun currentState(): BrewUiState {
        val s = controller.state.value
        val t = conn.value
        val st = scale.status.value
        val live = currentReadout().reading is ReadingUi.Live
        return build(
            Part(t, s.low(), st, controller.lastUnit.value, live), controller.tareInFlight.value, prefs.brewSettings.value,
            rawBanner(t.current, s.phase, s.stale, st, overloadDismissed.value, modelDismissed(t.current)), showResetConfirm.value,
            recoverableDraft.value,
        )
    }

    val uiState: StateFlow<BrewUiState> = combine(
        combine(conn, controller.state.map { it.low() }.distinctUntilChanged(), scale.status, controller.lastUnit, readingLive) { t, l, st, u, live ->
            Part(t, l, st, u, live)
        },
        controller.tareInFlight, prefs.brewSettings, bannerFlow, combine(showResetConfirm, recoverableDraft) { r, d -> r to d },
    ) { p, tare, settings, banner, (reset, draft) -> build(p, tare, settings, banner, reset, draft) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentState())

    val recipesForPicker: StateFlow<List<Recipe>> =
        recipes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        controller.dropStaleEvents()   // 앞 VM 몫의 지난 알림·스낵바를 다시 내지 않는다(Finished만 남김)
        if (controller.saveFailed.value) _events.trySend(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, retryTare = false))
        viewModelScope.launch {
            connectionFlow.collect { c ->
                val prev = conn.value
                if (c == prev.current) return@collect
                val before = if (c == ConnectionUi.BluetoothOff) (if (prev.current == ConnectionUi.BluetoothOff) prev.beforeBluetoothOff else prev.current) else null
                conn.value = ConnTrack(c, before)   // 알림보다 먼저: 화면이 알림을 처리할 때 currentConnection()이 이 값을 읽는다(ConnectFailed 이유)
                connectionAnnouncement(prev.current, c, controller.state.value.phase)?.let(::announce)
            }
        }
        viewModelScope.launch {
            var prevCause: StaleReason? = (currentReadout().reading as? ReadingUi.Stale)?.cause
            readoutFlow.map { (it.reading as? ReadingUi.Stale)?.cause }.distinctUntilChanged().collect { cause ->
                when {
                    cause == StaleReason.Overload && prevCause != StaleReason.Overload -> announce(BrewAnnouncement.Overload)
                    prevCause == StaleReason.Overload && cause != StaleReason.Overload -> announce(BrewAnnouncement.OverloadCleared)
                }
                if (cause == StaleReason.NoData && prevCause != StaleReason.NoData) announce(BrewAnnouncement.NoData)
                prevCause = cause
            }
        }
        viewModelScope.launch {
            controller.state.map { it.stale == StaleReason.Overload }.distinctUntilChanged().collect { if (!it) overloadDismissed.value = false }
        }
        viewModelScope.launch { controller.events.collect(::onControllerEvent) }
        // D2: VM 생성 때 한 번. 실시간 초안은 뺀다(10 s 임시 저장이 이미 만든 행)
        viewModelScope.launch { recoverableDraft.value = runCatching { brews.findDraft(controller.draftId.value) }.getOrNull() }
    }

    private fun announce(a: BrewAnnouncement) { _events.trySend(BrewUiEvent.Announce(a)) }

    private fun onControllerEvent(e: BrewEvent) {
        when (e) {
            is BrewEvent.TareFailed -> _events.trySend(
                when (e.reason) {
                    CommandFailure.Rejected -> BrewUiEvent.Snackbar(BrewMessage.TareRejected, retryTare = true)
                    CommandFailure.Timeout -> BrewUiEvent.Snackbar(BrewMessage.TareTimeout, retryTare = true)
                    else -> BrewUiEvent.Snackbar(BrewMessage.TareNotConnected, retryTare = false)
                },
            )
            BrewEvent.TareSucceeded -> announce(BrewAnnouncement.TareDone)
            is BrewEvent.ScaleTimerSyncFailed -> _events.trySend(BrewUiEvent.Snackbar(BrewMessage.ScaleTimerSyncFailed, retryTare = false))
            is BrewEvent.PhaseChanged -> phaseAnnouncement(e.from, e.to, e.cause)?.let(::announce)
            is BrewEvent.Finished -> _events.trySend(BrewUiEvent.OpenSummary(e.sessionId, recovered = false))
            BrewEvent.SaveFailed -> _events.trySend(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, retryTare = false))
            is BrewEvent.ValveCue -> {
                announce(if (e.event.open) BrewAnnouncement.ValveOpen else BrewAnnouncement.ValveClose)
                if (prefs.brewSettings.value.valveHaptic) _events.trySend(BrewUiEvent.Haptic(clock.nowNanos()))
            }
        }
    }

    private val phase get() = controller.state.value.phase
    private val idleLike get() = phase == BrewPhase.Idle || phase == BrewPhase.WaitingForPour

    override fun onTareClick() { if (!controller.tareInFlight.value && idleLike) controller.tare() }   // 스낵바 '다시'가 추출 중에 눌려도 영점하지 않는다

    /** 지금 연결 상태(알림 문구가 이유를 고를 때 읽는다. uiState는 화면에 한 박자 늦게 닿는다). */
    fun currentConnection(): ConnectionUi = conn.value.current
    override fun onStartClick() = controller.startTimer()
    override fun onPauseClick() = controller.pauseTimer()
    override fun onResetClick() { if (controller.sampleCount.value > 0) showResetConfirm.value = true else controller.resetTimer() }
    override fun onCancelWaitingClick() = controller.cancelWaiting()
    override fun onFinishClick() = controller.finish()
    override fun onDiscardClick() = controller.discard()
    override fun onResetConfirmed() { showResetConfirm.value = false; controller.resetTimer() }
    override fun onResetDismissed() { showResetConfirm.value = false }
    override fun onDisconnectClick() = scale.disconnect()
    override fun onReconnectClick() { scale.connectLastDevice() }
    override fun onFindOtherScaleClick() {
        if (!idleLike) return   // 추출 중 다른 저울에 붙으면 다른 영점의 무게가 같은 세션에 섞인다
        scale.disconnect()
        _events.trySend(BrewUiEvent.OpenConnect)
    }
    override fun onOverloadBannerDismissed() { overloadDismissed.value = true }
    /** S1이 다시 구성될 때(다른 목적지에서 돌아옴) 그동안 쌓인 일회성 결과를 버린다. 요약 이동·저장 실패 스낵바만 남긴다. */
    fun dropBackloggedEvents() {
        val keep = ArrayList<BrewUiEvent>()
        while (true) {
            val e = _events.tryReceive().getOrNull() ?: break
            if (e is BrewUiEvent.OpenSummary || (e is BrewUiEvent.Snackbar && e.message == BrewMessage.SaveFailed)) keep += e
        }
        keep.forEach { _events.trySend(it) }
    }

    /** 저장·버리기로 끝난 세션은 D2 대상이 아니다(프로세스 종료 뒤 S6에서 저장한 행을 다시 묻지 않는다). */
    fun onSessionGone(sessionId: Long) { if (recoverableDraft.value?.id == sessionId) recoverableDraft.value = null }
    fun onSessionSaved(sessionId: Long) { onSessionGone(sessionId); _events.trySend(BrewUiEvent.Snackbar(BrewMessage.Saved, retryTare = false, sessionId = sessionId)) }
    override fun onRetrySaveClick() = controller.retrySave()
    override fun onModelWarningDismissed() {
        val c = conn.value.current as? ConnectionUi.Connected ?: return
        prefs.dismissModelWarning(c.device.address)
        modelDismissTick.value++
    }
    fun onPermissionResult(allGranted: Boolean, permanentlyDenied: Boolean) = permission.onResult(allGranted, permanentlyDenied)
    override fun onSettingsChange(settings: BrewSettings) = prefs.setBrewSettings(settings)   // 컨트롤러가 prefs를 구독해 엔진에 넣는다
    override fun onRecipeSelected(id: Long?) {
        if (!idleLike) return
        viewModelScope.launch {
            val r = id?.let { recipes.get(it) }
            if (idleLike) controller.selectRecipe(r)
        }
    }
    override fun onRecoverOpenClick() {
        val d = recoverableDraft.value ?: return
        _events.trySend(BrewUiEvent.OpenSummary(d.id, recovered = true))
        recoverableDraft.value = null   // 요약에서 돌아와도 같은 초안을 다시 묻지 않는다(저장·버리기 뒤 행이 바뀐다)
    }
    override fun onRecoverDiscardClick() {
        val d = recoverableDraft.value ?: return
        viewModelScope.launch {
            runCatching { brews.deleteSession(d.id) }   // 실패하면 다음 실행에서 다시 묻는다
            recoverableDraft.value = null
        }
    }
}
