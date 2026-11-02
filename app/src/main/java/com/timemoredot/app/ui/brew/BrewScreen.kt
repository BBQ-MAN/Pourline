package com.timemoredot.app.ui.brew

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.LocalSystemActions
import com.timemoredot.app.ui.components.BannerKind
import com.timemoredot.app.ui.components.BatteryLabel
import com.timemoredot.app.ui.components.ConnectionChip
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.StatusBanner
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.connect.failureText
import com.timemoredot.app.ui.connect.permissionText
import com.timemoredot.app.ui.device.DeviceSheet
import com.timemoredot.app.ui.device.unitLabelFor
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.protocol.MonotonicClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

/** S1 사용자 동작(architecture.md 9.4). BrewViewModel이 구현한다. */
interface BrewCallbacks {
    fun onTareClick(); fun onStartClick(); fun onPauseClick(); fun onResetClick(); fun onCancelWaitingClick(); fun onFinishClick()
    fun onDiscardClick(); fun onResetConfirmed(); fun onResetDismissed(); fun onDisconnectClick(); fun onReconnectClick()
    fun onFindOtherScaleClick(); fun onOverloadBannerDismissed(); fun onRetrySaveClick(); fun onModelWarningDismissed()
    fun onSettingsChange(settings: BrewSettings); fun onRecipeSelected(id: Long?)
    fun onRecoverOpenClick(); fun onRecoverDiscardClick()
}

object NoBrewCallbacks : BrewCallbacks {
    override fun onTareClick() = Unit; override fun onStartClick() = Unit; override fun onPauseClick() = Unit
    override fun onResetClick() = Unit; override fun onCancelWaitingClick() = Unit; override fun onFinishClick() = Unit
    override fun onDiscardClick() = Unit; override fun onResetConfirmed() = Unit; override fun onResetDismissed() = Unit
    override fun onDisconnectClick() = Unit; override fun onReconnectClick() = Unit; override fun onFindOtherScaleClick() = Unit
    override fun onOverloadBannerDismissed() = Unit; override fun onRetrySaveClick() = Unit; override fun onModelWarningDismissed() = Unit
    override fun onSettingsChange(settings: BrewSettings) = Unit; override fun onRecipeSelected(id: Long?) = Unit
    override fun onRecoverOpenClick() = Unit; override fun onRecoverDiscardClick() = Unit
}

private enum class Sheet { None, Device, Settings, Picker }

/** S1 화면 루트: uiState만 수집하고 readout은 숫자·그래프 컴포저블에 흐름 그대로 넘긴다. */
@Composable
fun BrewScreen(
    vm: BrewViewModel, onOpenConnect: () -> Unit, onOpenSummary: (Long, Boolean) -> Unit, onOpenDetail: (Long) -> Unit,
    onCreateRecipe: () -> Unit, savedSessionId: Long?, onSavedSessionConsumed: () -> Unit, locationPermission: Boolean,
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val recipes by vm.recipesForPicker.collectAsStateWithLifecycle()
    // S1이 컴포지션 밖(다른 목적지)에 있던 동안 쌓인 알림·스낵바는 지난 일이다. 수집이 시작되기 전에 버린다(요약 이동·저장 실패만 남김)
    DisposableEffect(vm) { vm.dropBackloggedEvents(); onDispose { } }
    LaunchedEffect(savedSessionId) { if (savedSessionId != null) { vm.onSessionSaved(savedSessionId); onSavedSessionConsumed() } }
    BrewContent(
        ui, vm.readout, vm.samples, vm.clock, recipes, vm, vm.events,
        onOpenConnect, onOpenSummary, onOpenDetail, onCreateRecipe, locationPermission, vm::currentConnection,
    )
}

/** 상태 없는 S1(스크린샷·UI 테스트가 직접 그린다). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewContent(
    ui: BrewUiState, readout: StateFlow<BrewReadout>, samples: SampleBuffer, clock: MonotonicClock, recipes: List<Recipe>,
    callbacks: BrewCallbacks, events: Flow<BrewUiEvent> = emptyFlow(),
    onOpenConnect: () -> Unit = {}, onOpenSummary: (Long, Boolean) -> Unit = { _, _ -> }, onOpenDetail: (Long) -> Unit = {},
    onCreateRecipe: () -> Unit = {}, locationPermission: Boolean = false,
    currentConnection: (() -> ConnectionUi)? = null,   // 알림을 처리하는 시점의 연결 상태(없으면 ui.connection)
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val announcer = LocalAnnouncer.current
    val view = LocalView.current
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    var tareTrigger by remember { mutableIntStateOf(0) }
    var valveTint by remember { mutableIntStateOf(0) }
    val latestUi by rememberUpdatedState(ui)
    if (ui.phase != BrewPhase.Finished && saveFailed) saveFailed = false
    val brewing = ui.phase == BrewPhase.Running || ui.phase == BrewPhase.Paused
    if (!brewing && showLeave) showLeave = false   // 자동 완료·상한·초기화로 진행이 끝나면 D1을 닫는다(D1은 Running·Paused에서만)

    // 일회성 결과 수집 지점 하나: 알림, 영점 확인 모션·밸브 색 숨 계기, 진동, 스낵바, 화면 이동. 모양 겹은 events를 따로 수집하지 않는다
    LaunchedEffect(events) {
        events.collect { e ->
            // 화면이 보이지 않는 동안(STARTED 아님) 온 알림·스낵바는 돌아왔을 때 지난 일이다. 저장 실패 스낵바와 화면 이동만 남긴다
            val visible = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!visible && (e is BrewUiEvent.Announce || (e is BrewUiEvent.Snackbar && e.message != BrewMessage.SaveFailed))) return@collect
            when (e) {
                is BrewUiEvent.Announce -> {
                    val (text, assertive) = announcementText(e.announcement, currentConnection?.invoke() ?: latestUi.connection)
                    announcer.announce(text, assertive)
                    when (e.announcement) {
                        BrewAnnouncement.TareDone -> tareTrigger++
                        BrewAnnouncement.ValveOpen, BrewAnnouncement.ValveClose -> valveTint++
                        else -> Unit
                    }
                }
                is BrewUiEvent.Haptic -> if (hapticFresh(clock.nowNanos(), e.atNanos)) performValveHaptic(view)
                is BrewUiEvent.OpenSummary -> onOpenSummary(e.sessionId, e.recovered)
                BrewUiEvent.OpenConnect -> onOpenConnect()
                is BrewUiEvent.Snackbar -> {
                    if (e.message == BrewMessage.SaveFailed) saveFailed = true
                    scope.launch {
                        val talkBack = (context.getSystemService(AccessibilityManager::class.java))?.isTouchExplorationEnabled == true
                        val action = when {
                            e.message == BrewMessage.SaveFailed -> "다시 시도"
                            e.retryTare -> "다시"
                            e.message == BrewMessage.Saved && e.sessionId != null -> "보기"
                            e.message == BrewMessage.ScaleTimerSyncFailed -> "확인"
                            else -> null
                        }
                        val duration = when {
                            e.message == BrewMessage.SaveFailed -> SnackbarDuration.Indefinite   // TalkBack과 상관없이(5.2)
                            action != null && talkBack -> SnackbarDuration.Indefinite
                            action != null -> SnackbarDuration.Long
                            else -> SnackbarDuration.Short
                        }
                        val r = snackbar.showSnackbar(messageText(e.message), action, withDismissAction = false, duration = duration)
                        if (r == SnackbarResult.ActionPerformed) when {
                            e.message == BrewMessage.SaveFailed -> { saveFailed = false; callbacks.onRetrySaveClick() }
                            e.retryTare -> callbacks.onTareClick()
                            e.message == BrewMessage.Saved && e.sessionId != null -> onOpenDetail(e.sessionId)
                        }
                    }
                }
            }
        }
    }

    // 화면 켜짐: Running·Paused 동안만
    if (ui.keepScreenOn) DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 뒤로 가기: 진행 중일 때만 가로채 D1(시트가 열려 있으면 시트 창이 먼저 처리한다)
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        navState, isBackEnabled = brewing,
        onBackCancelled = {}, onBackCompleted = { showLeave = true },
    )

    BrewAnnouncer(ui, readout, clock)

    val actions = LocalSystemActions.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val onRetrySave = {
        snackbar.currentSnackbarData?.dismiss()
        saveFailed = false
        callbacks.onRetrySaveClick()
    }
    val bannerSlot: @Composable (Boolean) -> Unit = { short ->
        BrewBannerView(ui, short, clock, callbacks, onOpenConnect, locationPermission)
    }
    val actionRow: @Composable () -> Unit = {
        BrewActions(
            ui, saveFailed, callbacks::onTareClick, callbacks::onStartClick, callbacks::onPauseClick, callbacks::onResetClick,
            callbacks::onCancelWaitingClick, callbacks::onFinishClick, onRetrySave,
        )
    }
    val tabsShown = ui.showBottomBar
    val sideInsets = when {
        !tabsShown -> WindowInsets.safeDrawing
        wide -> WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
        else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    }
    val bottomBarInsets = when {
        !tabsShown -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        wide -> WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)
        else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    }
    val topBanner = ui.phase == BrewPhase.Idle && ui.banner != null
    val empty = emptyStateFor(ui)
    // 고정 묶음(아래 배너 + 동작 줄)이 창(가로는 왼쪽 칸) 높이의 50%를 넘으면 배너를 지표 줄 아래 스크롤 열로 옮긴다(3.1·8.6).
    // 두 높이는 놓인 자리와 상관없이 같은 폭에서 재므로 옮겨도 판정이 뒤집히지 않는다
    var bannerH by remember { mutableIntStateOf(0) }
    var actionH by remember { mutableIntStateOf(0) }
    var leftPaneH by remember { mutableIntStateOf(0) }
    var weightMinDp by remember { mutableStateOf(0f) }   // 가로 왼쪽 칸에서 밸브 칸·띠가 들어갈 무게 영역 최소 높이(WeightArea가 잰다)
    val windowH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    val paneH = if (wide) leftPaneH else windowH
    val lowBanner = !topBanner && ui.banner != null
    val bannerInBody = lowBanner && empty == null && paneH > 0 && bannerH + actionH > paneH / 2
    val bannerBox: @Composable () -> Unit = {
        Box(Modifier.padding(top = Space.m).onSizeChanged { bannerH = it.height }) { bannerSlot(true) }
    }

    // 그래프일 때 모양 자리 최소 높이(그림 영역 최소 포함, 4.4). 모자라면 단계 카드가 스크롤로 밀린다
    val fontScale = LocalDensity.current.fontScale
    val shapeMin = if (ui.phase == BrewPhase.Running || ui.phase == BrewPhase.Paused || ui.phase == BrewPhase.Finished) max(120f, shapeAreaMinDp(fontScale)).dp else 120.dp

    Scaffold(
        topBar = { BrewTopBar(ui, onTitle = { sheet = Sheet.Picker }, onTune = { sheet = Sheet.Settings }) },
        bottomBar = {
            if (!wide) Column(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }.windowInsetsPadding(bottomBarInsets)) {
                if (lowBanner && !bannerInBody) Box(Modifier.padding(horizontal = Space.l)) { bannerBox() }
                Box(Modifier.onSizeChanged { actionH = it.height }) { actionRow() }
            }
        },
        snackbarHost = { if (!wide) SnackbarHost(snackbar) },
        contentWindowInsets = sideInsets,
    ) { inner ->
        val body: @Composable (Modifier) -> Unit = { m ->
            // 가로 왼쪽 칸의 채움 자식은 무게 영역: 최소 = 40 sp 숫자 줄 + 아래 줄(24 dp) 어림
            FillScrollColumn(m, if (wide && empty == null) max(72f, weightMinDp).dp else shapeMin, refMin = if (wide && empty == null) 72.dp else null) {
                StatusRow(ui, onChip = { sheet = Sheet.Device })
                if (topBanner) { Spacer(Modifier.height(Space.s)); bannerSlot(false); Spacer(Modifier.height(Space.m)) } else Spacer(Modifier.height(Space.s))
                if (empty != null) {
                    EmptyStateFor(empty, callbacks, onOpenConnect, Modifier.layoutId(FILL))
                } else {
                    // 가로 왼쪽 칸: 무게 영역이 남은 높이를 받아 글자 크기를 높이로도 줄인다. 지표 줄이 첫 화면에 든다(3.2)
                    WeightArea(readout, ui.displayUnit, ui.phase, ui.recipe, ui.timer, clock, valveTint, if (wide) Modifier.layoutId(FILL) else Modifier,
                        onMinHeight = { weightMinDp = it })
                    Spacer(Modifier.height(Space.s))
                    // 가로 왼쪽 칸은 무게를 높이로 줄이므로 지표도 24 sp까지만 키워 무게(최소 40 sp)가 지표의 1.5배 이상으로 남게 한다(D1).
                    // 밸브 칸 유무와 상관없이 같은 상한이다(3.2·4.3, 2026-11-02)
                    MetricsRow(ui.phase, ui.timer, readout, ui.displayUnit, clock, Modifier.testTag(METRICS_TAG),
                        maxSp = if (wide) WIDE_METRIC_MAX_SP else 36)
                    if (bannerInBody) bannerBox()
                    if (!wide) {
                        Spacer(Modifier.height(Space.m))
                        ShapeArea(ui.phase, ui.recipe, ui.currentStepIndex, ui.lastStepReached, readout, samples, ui.displayUnit, tareTrigger, Modifier.layoutId(FILL).testTag(SHAPE_TAG), ui.timer, clock)
                        ui.recipe?.takeIf { ui.currentStepIndex != null }?.let { r ->   // 단계 카드는 엔진 단계 안내가 있을 때(Running·Paused·Finished)
                            Spacer(Modifier.height(Space.m))
                            RecipeStepCard(r, ui.phase, ui.currentStepIndex, ui.lastStepReached, ui.timer, readout, ui.displayUnit, clock)
                        }
                    }
                }
                Spacer(Modifier.height(Space.m))
            }
        }
        if (!wide) {
            body(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize().semantics { isTraversalGroup = true })
        } else {
            Row(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().onSizeChanged { leftPaneH = it.height }.semantics { isTraversalGroup = true }) {
                    Box(Modifier.weight(1f).testTag(LEFT_SCROLL_TAG)) { body(Modifier.fillMaxSize()) }
                    if (lowBanner && !bannerInBody) Box(Modifier.padding(horizontal = Space.l)) { bannerBox() }
                    Box(Modifier.onSizeChanged { actionH = it.height }.testTag(ACTIONS_TAG)) { actionRow(); SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) }
                }
                if (empty == null) FillScrollColumn(Modifier.weight(1f).fillMaxHeight().semantics { isTraversalGroup = true }, shapeMin) {
                    Spacer(Modifier.height(Space.m))
                    ShapeArea(ui.phase, ui.recipe, ui.currentStepIndex, ui.lastStepReached, readout, samples, ui.displayUnit, tareTrigger,
                        Modifier.layoutId(FILL).testTag(SHAPE_TAG), ui.timer, clock)
                    ui.recipe?.takeIf { ui.currentStepIndex != null }?.let { r ->   // 단계 카드는 엔진 단계 안내가 있을 때(Running·Paused·Finished)
                        Spacer(Modifier.height(Space.m))
                        RecipeStepCard(r, ui.phase, ui.currentStepIndex, ui.lastStepReached, ui.timer, readout, ui.displayUnit, clock)
                    }
                    Spacer(Modifier.height(Space.m))
                }
            }
        }
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Device -> {
            val unitLabel: @Composable () -> String = {
                val rs = readout.collectAsStateWithLifecycle()
                val kind by remember(rs) { derivedStateOf { unitKind(rs.value.reading) } }   // 종류가 바뀔 때만 재구성(10 Hz 아님)
                unitLabelFor(kind.first, kind.second, ui.displayUnit)
            }
            DeviceSheet(
                connection = ui.connection, batteryPercent = ui.batteryPercent, unitLabel = unitLabel, canFindOtherScale = ui.canFindOtherScale,
                onDisconnect = { callbacks.onDisconnectClick(); sheet = Sheet.None },
                onFindOtherScale = { sheet = Sheet.None; callbacks.onFindOtherScaleClick() },
                onFindScale = { sheet = Sheet.None; onOpenConnect() },
                onDismiss = { sheet = Sheet.None },
                onReconnect = { callbacks.onReconnectClick() },
                onEnableBluetooth = actions::requestEnableBluetooth, onRequestPermission = actions::requestPermissions,
                onOpenSettings = actions::openAppSettings, onOpenLocationSettings = actions::openLocationSettings,
                locationPermission = locationPermission,
            )
        }
        Sheet.Settings -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }) { BrewSettingsContent(ui.settings, callbacks::onSettingsChange) }
        Sheet.Picker -> ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }) {
            RecipePickerContent(ui.recipe?.id, recipes, onSelect = { callbacks.onRecipeSelected(it); sheet = Sheet.None }, onCreate = { sheet = Sheet.None; onCreateRecipe() })
        }
    }
    if (showLeave && brewing) LeaveConfirmDialog(
        onContinue = { showLeave = false },
        onFinish = { showLeave = false; callbacks.onFinishClick() },
        onDiscard = { showLeave = false; callbacks.onDiscardClick() },
    )
    if (ui.showResetConfirm) ResetConfirmDialog(callbacks::onResetConfirmed, callbacks::onResetDismissed)
    ui.recoverableDraft?.let { RecoverDraftDialog(it, callbacks::onRecoverOpenClick, callbacks::onRecoverDiscardClick) }
}

private fun unitKind(r: ReadingUi) = Pair(r is ReadingUi.UnitUnknown, r is ReadingUi.UnitUnknownTimedOut)

const val METRICS_TAG = "brew_metrics"
const val SHAPE_TAG = "brew_shape"
const val LEFT_SCROLL_TAG = "brew_left_scroll"   // 가로 왼쪽 칸 스크롤 열(보이는 영역)
const val ACTIONS_TAG = "brew_actions"
const val WIDE_METRIC_MAX_SP = 24
private const val FILL = "fill"

/**
 * 스크롤 열: layoutId(FILL)인 자식이 남은 높이를 채운다(최소 fillMin, 기본 120 dp, 3.1 6번). 높이가 모자라면 그 자식은 fillMin이고 나머지는 스크롤.
 * verticalScroll 안에서는 weight를 쓸 수 없어 창 높이를 BoxWithConstraints로 받아 직접 잰다.
 */
@Composable
private fun FillScrollColumn(
    modifier: Modifier, fillMin: androidx.compose.ui.unit.Dp = 120.dp,
    // refMin이 있으면 채움 자식의 최소 높이 제약 = max(refMin, 남은 높이)(채움 최소가 refMin일 때 받을 높이). 무게 영역이 이것으로
    // 밸브 칸이 없을 때의 크기 s₀를 구해 s′ ≤ s₀를 지킨다(ui-design 4.11 넓은 창)
    refMin: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxHeight
        Layout(content, Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.l)) { ms, c ->
            val w = c.maxWidth
            val loose = Constraints(minWidth = w, maxWidth = w)
            val fixed = ms.map { if (it.layoutId == FILL) null else it.measure(loose) }
            val used = fixed.sumOf { it?.height ?: 0 }
            val fillH = max(fillMin.roundToPx(), viewport - used)
            val refH = refMin?.let { minOf(fillH, max(it.roundToPx(), viewport - used)) } ?: fillH
            val all = ms.mapIndexed { i, m -> fixed[i] ?: m.measure(Constraints(minWidth = w, maxWidth = w, minHeight = refH, maxHeight = fillH)) }
            layout(w, all.sumOf { it.height }) {
                var y = 0
                all.forEach { it.place(0, y); y += it.height }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrewTopBar(ui: BrewUiState, onTitle: () -> Unit, onTune: () -> Unit) {
    val locked = !ui.canChangeRecipeOrSettings
    val name = ui.recipe?.name ?: "자유 추출"
    // 3.1: 글꼴 ≥ 1.3이면 두 줄까지(잘림 금지), 두 줄이면 상단 바 높이 = max(64, 두 줄 + 16)
    val d = LocalDensity.current
    val twoLines = d.fontScale >= 1.3f
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.titleLarge
    val titleW = LocalConfiguration.current.screenWidthDp - 2 * 64 - 24 - 24   // 양옆 동작 칸·화살표·버튼 안쪽 여백 어림
    val barH = remember(name, twoLines, titleW, d) {
        if (!twoLines) 64.dp else {
            val r = measurer.measure(name, style, maxLines = 2, constraints = Constraints(maxWidth = with(d) { titleW.coerceAtLeast(48).dp.roundToPx() }))
            if (r.lineCount < 2) 64.dp else max(64f, with(d) { r.size.height.toDp().value } + 16f).dp
        }
    }
    CenterAlignedTopAppBar(
        expandedHeight = barH,
        title = {
            TextButton(
                onTitle, enabled = !locked,
                modifier = Modifier.focusRing(CircleShape).semantics {
                    stateDescription = if (locked) "추출 중에는 바꿀 수 없음" else name
                    onClick(label = "레시피 선택", action = null)
                },
            ) {
                // 잠겨도 이름은 읽을 수 있는 색(팔 길이 거리, 4.5:1). 비활성은 화살표 색과 stateDescription이 전한다
                Text(name, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = if (twoLines) 2 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(AppIcons.ArrowDropDown, contentDescription = null)
            }
        },
        actions = {
            IconButton(onTune, enabled = !locked, modifier = Modifier.focusRing(CircleShape).semantics { if (locked) stateDescription = "추출 중에는 바꿀 수 없음" }) {
                Icon(AppIcons.Tune, contentDescription = "추출 설정")
            }
        },
    )
}

@Composable
private fun StatusRow(ui: BrewUiState, onChip: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectionChip(ui.connection, onClick = onChip)
        BatteryLabel(ui.batteryPercent)
    }
}

private enum class EmptyKind { NoDevice, Disconnected, Scanning, ScanEmpty, ScanFailed }

private fun emptyStateFor(ui: BrewUiState): EmptyKind? {
    if (ui.phase != BrewPhase.Idle && ui.phase != BrewPhase.WaitingForPour) return null
    return when (ui.connection) {
        ConnectionUi.NoDevice -> EmptyKind.NoDevice
        ConnectionUi.Disconnected -> EmptyKind.Disconnected
        is ConnectionUi.Scanning -> EmptyKind.Scanning
        ConnectionUi.ScanEmpty -> EmptyKind.ScanEmpty
        ConnectionUi.ScanFailed -> EmptyKind.ScanFailed
        else -> null
    }
}

@Composable
private fun EmptyStateFor(kind: EmptyKind, callbacks: BrewCallbacks, onOpenConnect: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        when (kind) {
            EmptyKind.NoDevice, EmptyKind.ScanFailed -> EmptyState(AppIcons.Bluetooth, "저울이 연결되지 않았습니다", description = "TIMEMORE Dot을 켜고 연결하세요.", actionLabel = "저울 찾기", onAction = onOpenConnect)
            EmptyKind.Scanning -> EmptyState(AppIcons.Bluetooth, "저울이 연결되지 않았습니다", description = "저울 찾기 화면에서 저울을 고르세요.", actionLabel = "저울 찾기", onAction = onOpenConnect)
            EmptyKind.ScanEmpty -> EmptyState(AppIcons.Bluetooth, "저울을 찾지 못했습니다", description = "저울이 켜져 있는지, 다른 휴대폰에 연결돼 있지 않은지 확인하세요.", actionLabel = "저울 찾기", onAction = onOpenConnect)
            EmptyKind.Disconnected -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(AppIcons.Bluetooth, "저울이 연결되어 있지 않습니다", description = "저장된 저울에 다시 연결하거나 다른 저울을 찾으세요.", actionLabel = "다시 연결", onAction = callbacks::onReconnectClick)
                TextButton(callbacks::onFindOtherScaleClick, Modifier.heightIn(min = 48.dp).focusRing(CircleShape)) { Text("다른 저울 찾기") }
            }
        }
    }
}

/** 배너(4.7, 5.1). short = 아래 배너(7a) 문구. */
@Composable
private fun BrewBannerView(ui: BrewUiState, short: Boolean, clock: MonotonicClock, cb: BrewCallbacks, onOpenConnect: () -> Unit, location: Boolean) {
    val actions = LocalSystemActions.current
    val brewing = ui.phase == BrewPhase.Running || ui.phase == BrewPhase.Paused
    val otherState = if (ui.canFindOtherScale) null else "추출 중에는 바꿀 수 없음"
    when (ui.banner ?: return) {
        BrewBanner.BluetoothOff -> StatusBanner(BannerKind.Error, "블루투스가 꺼져 있습니다.", icon = AppIcons.BluetoothDisabled, actionLabel = "블루투스 켜기", onAction = actions::requestEnableBluetooth)
        BrewBanner.PermissionMissing -> StatusBanner(BannerKind.Error, permissionText(false, location), actionLabel = "권한 허용", onAction = actions::requestPermissions)
        BrewBanner.PermissionPermanentlyDenied -> StatusBanner(BannerKind.Error, permissionText(true, location), actionLabel = "설정 열기", onAction = actions::openAppSettings)
        BrewBanner.LocationOff -> StatusBanner(BannerKind.Error, "위치 서비스를 켜야 저울을 찾을 수 있습니다.", actionLabel = "위치 설정 열기", onAction = actions::openLocationSettings)
        BrewBanner.Failed -> {
            val reason = (ui.connection as? ConnectionUi.Failed)?.reason ?: ConnectFailure.ConnectFailed
            if (reason == ConnectFailure.ServiceNotFound && !brewing) StatusBanner(BannerKind.Error, failureText(reason),
                actionLabel = "다른 저울 찾기", onAction = cb::onFindOtherScaleClick)
            else StatusBanner(BannerKind.Error, failureText(reason), actionLabel = "다시 시도", onAction = cb::onReconnectClick,
                secondaryLabel = "다른 저울 찾기", onSecondary = cb::onFindOtherScaleClick, secondaryEnabled = ui.canFindOtherScale, secondaryState = otherState)
        }
        BrewBanner.ScanFailed -> StatusBanner(BannerKind.Error, "찾기를 시작하지 못했습니다. 블루투스를 껐다 켜 보세요.", actionLabel = "저울 찾기", onAction = onOpenConnect)
        BrewBanner.Disconnected -> StatusBanner(BannerKind.Error, "저울 연결을 끊었습니다. 타이머는 계속 가지만 무게는 기록되지 않습니다.", icon = AppIcons.LinkOff, actionLabel = "다시 연결", onAction = cb::onReconnectClick)
        BrewBanner.Reconnecting -> {
            val rec = ui.connection as? ConnectionUi.Reconnecting
            val now = rememberNow(clock, 1_000, true)
            val left = rec?.let { ((it.nextRetryAtNanos - now + 999_999_999) / 1_000_000_000L) } ?: 0
            val when_ = if (left > 0) "${left}초 후" else "지금 연결하는 중"
            val head = "연결이 끊겨 다시 연결하는 중입니다(${rec?.attempt ?: 1}/10회, $when_)."
            val text = if (short) head else "$head 저울 버튼을 눌렀다면 저울이 꺼진 것이니 다시 켜 주세요. 추출 중이면 타이머는 계속 갑니다."
            StatusBanner(BannerKind.Progress, text, actionLabel = "지금 다시 시도", onAction = cb::onReconnectClick, secondaryLabel = "연결 끊기", onSecondary = cb::onDisconnectClick)
        }
        BrewBanner.Connecting -> StatusBanner(BannerKind.Progress, "Dot에 연결하는 중…", icon = AppIcons.BluetoothSearching, actionLabel = "취소", onAction = cb::onDisconnectClick)
        BrewBanner.Overload -> StatusBanner(BannerKind.Error, "과부하: 저울이 잴 수 있는 무게를 넘었습니다. 올린 것을 내리세요.", icon = AppIcons.Warning, actionLabel = "확인", onAction = cb::onOverloadBannerDismissed)
        BrewBanner.UnitUnknownTimedOut -> StatusBanner(BannerKind.Info, "저울 단위를 확인하지 못했습니다. 다시 연결하세요.", icon = AppIcons.Help, actionLabel = "다시 연결", onAction = cb::onReconnectClick)
        BrewBanner.NoData -> StatusBanner(BannerKind.Info, "저울에서 무게가 오지 않습니다. 저울이 켜져 있는지 확인하고 다시 연결하세요.", icon = AppIcons.SignalDisconnected, actionLabel = "다시 연결", onAction = cb::onReconnectClick)
        BrewBanner.ModelMismatch -> StatusBanner(BannerKind.Info, "이 기기는 TIMEMORE Dot(TES017)이 아닐 수 있습니다. 무게가 이상하면 연결을 끊으세요.",
            actionLabel = "확인", onAction = cb::onModelWarningDismissed, secondaryLabel = "연결 끊기", onSecondary = cb::onDisconnectClick)
        BrewBanner.WaitingForPour -> StatusBanner(BannerKind.Info, "물을 부어 ${formatThreshold(ui.settings.autoStartThresholdGrams)}${NBSP}g을 넘으면 타이머가 시작됩니다.", icon = AppIcons.HourglassEmpty)
    }
}

/** S4 추출 설정(3.8). */
@Composable
fun BrewSettingsContent(settings: BrewSettings, onChange: (BrewSettings) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Space.xl)) {
        Text("추출 설정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s))
        ListItem(
            headlineContent = { Text("물을 부으면 타이머 자동 시작") },
            trailingContent = { Switch(settings.autoStartEnabled, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.focusRing(RectangleShape).toggleable(settings.autoStartEnabled, role = Role.Switch) { onChange(settings.copy(autoStartEnabled = it)) },
        )
        Text("자동 시작 임계값", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.l))
        val options = listOf(0.5, 1.0, 2.0)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
            options.forEachIndexed { i, g ->
                SegmentedButton(
                    selected = settings.autoStartThresholdGrams == g, onClick = { onChange(settings.copy(autoStartThresholdGrams = g)) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size), enabled = settings.autoStartEnabled,
                    modifier = Modifier.focusRing(SegmentedButtonDefaults.itemShape(i, options.size)).semantics { contentDescription = "자동 시작 임계값 ${formatThreshold(g)} 그램" },   // 그룹 이름(1.3.1)
                ) { Text("${formatThreshold(g)}${NBSP}g") }
            }
        }
        ListItem(
            headlineContent = { Text("스위치 바꿀 때 진동") },
            supportingContent = {
                Text("밸브가 있는 레시피에서 열거나 닫을 때 짧게 한 번 진동합니다. 휴대폰의 터치 진동이 꺼져 있으면 울리지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = { Switch(settings.valveHaptic, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.focusRing(RectangleShape).toggleable(settings.valveHaptic, role = Role.Switch) { onChange(settings.copy(valveHaptic = it)) },
        )
        Text(
            "레시피로 추출하면 레시피의 끝 시각이 지난 뒤 무게가 5초 동안 멈추면 자동으로 끝납니다. 레시피 없이 추출하면 '완료'를 눌러 끝냅니다. 60분이 되면 어느 경우든 끝납니다.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
        )
    }
}

/** S5 레시피 선택(3.8). */
@Composable
fun RecipePickerContent(selectedId: Long?, recipes: List<Recipe>, onSelect: (Long?) -> Unit, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Space.xl)) {
        Text("레시피 선택", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s))
        @Composable fun row(id: Long?, title: String, sub: String?) = ListItem(
            headlineContent = { Text(title) },
            supportingContent = sub?.let { { Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")) } },
            leadingContent = { RadioButton(selected = selectedId == id, onClick = null) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.heightIn(min = 72.dp).focusRing(RectangleShape).selectable(selectedId == id, role = Role.RadioButton) { onSelect(id) },
        )
        row(null, "자유 추출", "레시피 없이 무게와 시간만 기록")
        recipes.forEach { r -> row(r.id, r.name, "원두 ${shortGrams(r.doseGrams)}${NBSP}g · 물 ${shortGrams(r.waterGrams)}${NBSP}g · ${r.steps.size}단계") }
        TextButton(onCreate, Modifier.padding(horizontal = Space.s).heightIn(min = 48.dp).focusRing(CircleShape)) { Text("레시피 만들기") }
    }
}

/**
 * D1 추출 중단 확인(5.2). 글꼴 배율 1.3 이상이면 버튼 셋을 끝 정렬 세로로 쌓는다(위에서 계속 추출 → 완료하고 저장 → 버리기).
 * 대화상자는 자기 창이라 LocalDensity가 시스템 글꼴 배율이다.
 */
@Composable
fun LeaveConfirmDialog(onContinue: () -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit) {
    val discard: @Composable () -> Unit = {
        TextButton(onDiscard, Modifier.focusRing(CircleShape), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("버리기") }
    }
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("추출을 끝낼까요?", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("버리면 이 추출 기록이 사라집니다. 되돌릴 수 없습니다.") },
        dismissButton = {
            if (LocalDensity.current.fontScale < 1.3f) {
                TextButton(onContinue, Modifier.focusRing(CircleShape)) { Text("계속 추출") }
                TextButton(onFinish, Modifier.focusRing(CircleShape)) { Text("완료하고 저장") }
            }
        },
        confirmButton = {
            if (LocalDensity.current.fontScale >= 1.3f) Column(horizontalAlignment = Alignment.End) {
                TextButton(onContinue, Modifier.focusRing(CircleShape)) { Text("계속 추출") }
                TextButton(onFinish, Modifier.focusRing(CircleShape)) { Text("완료하고 저장") }
                discard()
            } else discard()
        },
    )
}

/** D5 초기화 확인(5.2). */
@Composable
fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("이 추출을 버리고 초기화할까요? 되돌릴 수 없습니다.") },
        dismissButton = { TextButton(onDismiss, Modifier.focusRing(CircleShape)) { Text("취소") } },
        confirmButton = {
            TextButton(onConfirm, Modifier.focusRing(CircleShape), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("초기화") }
        },
    )
}

/** D2 미저장 추출 복구(5.5). 두 선택 중 하나를 골라야 닫힌다(바깥 터치·뒤로 가기로 닫지 않는다). */
@Composable
fun RecoverDraftDialog(draft: com.timemoredot.app.data.SessionSummary, onOpen: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        text = { Text(recoverText(draft)) },
        dismissButton = {
            TextButton(onDiscard, Modifier.focusRing(CircleShape), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("버리기") }
        },
        confirmButton = { TextButton(onOpen, Modifier.focusRing(CircleShape)) { Text("요약 열기") } },
    )
}
