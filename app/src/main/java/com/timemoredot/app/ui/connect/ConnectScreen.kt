package com.timemoredot.app.ui.connect

import android.os.SystemClock
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.timemoredot.app.TimemoreApp
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.LocalSystemActions
import com.timemoredot.app.ui.components.AppOutlinedButton
import com.timemoredot.app.ui.components.BannerKind
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.StatusBanner
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.protocol.ScanCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val RESORT_DELAY_AFTER_RELEASE_MS = 500L

/** 상태를 가진 S2. 화면이 닫히는 `onClose`는 뒤로 가기와 연결 성공 둘 다에서 부른다. */
@Composable
fun ConnectScreen(onClose: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TimemoreApp).container
    val vm: ConnectViewModel = viewModel(factory = viewModelFactory { initializer { ConnectViewModel(container.scale, container.permission) } })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val actions = LocalSystemActions.current
    val currentClose by rememberUpdatedState(onClose)

    LifecycleResumeEffect(Unit) { vm.onResume(); onPauseOrDispose { } }
    // Reconnecting이 보인 적 있으면 닫기 전에 여기서 알린다(S2 컴포지션이 사라지는 경합을 피함, ui-design 8.3). Announcer는 앱 루트 소유.
    val announcer = LocalAnnouncer.current
    val wasReconnecting = remember { booleanArrayOf(false) }
    // Reconnecting → Connected만 알림 대상. Connected 외 다른 종류로 바뀌면 플래그를 끈다.
    when (state.connection) {
        is ConnectionUi.Reconnecting -> wasReconnecting[0] = true
        is ConnectionUi.Connected -> Unit
        else -> wasReconnecting[0] = false
    }
    LaunchedEffect(vm) {
        vm.events.collect {   // Connected 진입 → S2 닫고 S1로
            if (wasReconnecting[0]) announcer.announce("저울 연결됨", false)
            wasReconnecting[0] = false
            currentClose()
        }
    }

    // 진입 때 한 번 스캔(권한·블루투스가 준비된 뒤). 재구성·회전마다 부르지 않는다.
    var scanStarted by rememberSaveable { mutableStateOf(false) }
    val ready = when (state.connection) {
        // 연결 시도 중에는 startScan이 무시되므로 소진하지 않는다. 시도가 끝나 Failed 등이 되면 진입 스캔이 돈다.
        is ConnectionUi.Connecting, is ConnectionUi.Reconnecting, is ConnectionUi.Connected -> false
        ConnectionUi.BluetoothOff, ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied, ConnectionUi.LocationOff -> false
        else -> true
    }
    LaunchedEffect(ready) { if (ready && !scanStarted) { scanStarted = true; vm.onScanClick() } }

    ConnectContent(
        state = state,
        locationPermission = container.permission.requiredPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION),
        onBack = onClose,
        onScan = vm::onScanClick, onStopScan = vm::onStopScanClick, onCandidate = vm::onCandidateClick,
        onCancel = vm::onCancelConnectClick, onRetry = vm::onRetryClick,
        onRequestPermission = actions::requestPermissions, onOpenSettings = actions::openAppSettings,
        onEnableBluetooth = actions::requestEnableBluetooth, onOpenLocationSettings = actions::openLocationSettings,
    )
}

/** 상태 없는 S2(스크린샷·UI 테스트가 직접 그린다). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectContent(
    state: ConnectUiState,
    locationPermission: Boolean,
    onBack: () -> Unit = {}, onScan: () -> Unit = {}, onStopScan: () -> Unit = {}, onCandidate: (String) -> Unit = {},
    onCancel: () -> Unit = {}, onRetry: () -> Unit = {}, onRequestPermission: () -> Unit = {}, onOpenSettings: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {}, onOpenLocationSettings: () -> Unit = {},
) {
    val connection = state.connection
    val announcer = LocalAnnouncer.current
    var previous by remember { mutableStateOf<ConnectionUi?>(null) }
    LaunchedEffect(connection) {
        if (connection !is ConnectionUi.Connected) connectAnnouncement(previous, connection, locationPermission)?.let { announcer.announce(it, false) }   // Connected는 ConnectScreen이 닫기 전에 알린다
        previous = connection
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("저울 연결") },
                    navigationIcon = { IconButton(onBack) { Icon(AppIcons.ArrowBack, contentDescription = "뒤로") } },
                )
                // 4 dp 자리는 항상 남겨 스캔이 멈춰도 목록이 밀리지 않게 한다
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (connection is ConnectionUi.Scanning && !connection.stopped) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "저울 찾는 중" })
                    }
                }
            }
        },
        bottomBar = { BottomActions(connection, onScan, onStopScan, onCancel, onRetry) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        val pointerDown = remember { mutableStateOf(false) }
        val lastReleaseMs = remember { mutableLongStateOf(0L) }
        val stopped = (connection as? ConnectionUi.Scanning)?.stopped == true
        val ordered = rememberOrderedCandidates(state.candidates, state.recentAddress, stopped, pointerDown, lastReleaseMs)
        val connectingAddress = (connection as? ConnectionUi.Connecting)?.address
        val busy = connection is ConnectionUi.Connecting || connection is ConnectionUi.Reconnecting
        val showList = connection is ConnectionUi.Scanning || connection is ConnectionUi.Connecting || connection is ConnectionUi.Reconnecting || connection is ConnectionUi.Failed
        Box(Modifier.fillMaxSize().consumeWindowInsets(inner), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 640.dp).fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                            if (pointerDown.value && !down) lastReleaseMs.longValue = SystemClock.uptimeMillis()
                            pointerDown.value = down
                        }
                    }
                },
                contentPadding = inner,
            ) {
                item { Header(connection, state.candidates.isNotEmpty(), locationPermission, onRequestPermission, onOpenSettings, onEnableBluetooth, onOpenLocationSettings, onScan) }
                if (showList) {
                    items(ordered, key = { it.address }) { c ->
                        CandidateRow(
                            c, ordered, recent = c.address == state.recentAddress,
                            connecting = c.address == connectingAddress,
                            enabled = !busy,
                            onClick = { onCandidate(c.address) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    connection: ConnectionUi, hasCandidates: Boolean, locationPermission: Boolean,
    onRequestPermission: () -> Unit, onOpenSettings: () -> Unit, onEnableBluetooth: () -> Unit, onOpenLocationSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val pad = Modifier.padding(horizontal = Space.l, vertical = Space.s)
    when (connection) {
        ConnectionUi.BluetoothOff -> EmptyState(AppIcons.BluetoothDisabled, "블루투스가 꺼져 있습니다", actionLabel = "블루투스 켜기", onAction = onEnableBluetooth)
        ConnectionUi.PermissionMissing -> EmptyState(
            AppIcons.BluetoothDisabled, emptyStateTitle(connection, locationPermission)!!,
            description = if (locationPermission) "Android 11 이하에서는 블루투스 기기를 찾으려면 위치 권한이 필요합니다. 앱은 위치를 저장하거나 보내지 않습니다."
            else "앱은 저울을 찾고 연결하는 데만 이 권한을 씁니다.",
            actionLabel = "권한 허용", onAction = onRequestPermission,
        )
        ConnectionUi.PermissionPermanentlyDenied -> EmptyState(
            AppIcons.BluetoothDisabled, "권한이 꺼져 있습니다",
            description = if (locationPermission) "설정 > 앱 > 권한에서 '위치'를 허용하세요." else "설정 > 앱 > 권한에서 '근처 기기'를 허용하세요.",
            actionLabel = "설정 열기", onAction = onOpenSettings,
        )
        ConnectionUi.LocationOff -> EmptyState(AppIcons.BluetoothDisabled, "위치 서비스를 켜야 저울을 찾을 수 있습니다", actionLabel = "위치 설정 열기", onAction = onOpenLocationSettings)
        ConnectionUi.ScanEmpty -> EmptyState(
            AppIcons.Bluetooth, "저울을 찾지 못했습니다",
            description = "저울이 켜져 있는지, 다른 휴대폰에 연결돼 있지 않은지 확인하세요.",
        )
        ConnectionUi.ScanFailed -> StatusBanner(BannerKind.Error, "찾기를 시작하지 못했습니다. 블루투스를 껐다 켜 보세요.", pad)
        ConnectionUi.NoDevice, ConnectionUi.Disconnected -> Hint("저울을 켜고 휴대폰 가까이 둔 뒤 찾기를 누르세요.")
        is ConnectionUi.Scanning -> Hint(when {
            connection.stopped -> "저울을 켜고 휴대폰 가까이 두세요."
            !hasCandidates -> "저울을 찾는 중…"
            else -> "저울을 찾는 중… · ${connection.devices.size}대 발견"
        })
        is ConnectionUi.Connecting -> Hint("저울을 켜고 휴대폰 가까이 두세요.")
        is ConnectionUi.Failed -> StatusBanner(BannerKind.Error, failureText(connection.reason), pad)
        is ConnectionUi.Reconnecting -> StatusBanner(BannerKind.Progress, "저장된 저울에 다시 연결하는 중입니다.", pad)
        is ConnectionUi.Connected -> Unit
    }
}

internal fun failureText(reason: ConnectFailure) = when (reason) {
    ConnectFailure.ConnectFailed -> "저울에 연결하지 못했습니다. 저울을 켜고 가까이 둔 뒤 다시 시도하세요."
    ConnectFailure.PairingModeMaybe -> "저울에 연결하지 못했습니다. 이 휴대폰으로 처음 연결한다면 저울을 끈 뒤 버튼을 길게 눌러 페어링 모드로 켜고 다시 시도하세요."
    ConnectFailure.ServiceNotFound -> "이 기기에서 저울 서비스를 찾지 못했습니다. 다른 기기일 수 있습니다."
    ConnectFailure.Timeout -> "연결 응답이 없습니다. 저울이 켜져 있는지 확인하세요."
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = Space.l, vertical = Space.m), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CandidateRow(c: ScanCandidate, all: List<ScanCandidate>, recent: Boolean, connecting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val signal = when {
        c.rssi >= -60 -> "신호 강함"
        c.rssi >= -80 -> "보통"
        else -> "약함"
    }
    val support = when {
        connecting -> "연결 중…"
        recent -> "$signal · 최근 연결"
        else -> signal
    }
    val shape = MaterialTheme.shapes.large
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)   // M3 비활성 기본(38%)
    val dim = !enabled && !connecting
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    ListItem(
        colors = if (dim) ListItemDefaults.colors(containerColor = container, headlineColor = off, supportingColor = off, leadingIconColor = off)
        else ListItemDefaults.colors(containerColor = container),
        headlineContent = {
            Text(
                listName(c, all),
                Modifier.semantics { contentDescription = spokenListName(c, all) },
            )
        },
        supportingContent = { Text(support) },
        leadingContent = { Icon(AppIcons.Bluetooth, contentDescription = null) },
        trailingContent = if (connecting) { { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } } else null,
        modifier = Modifier
            .padding(horizontal = Space.l, vertical = Space.xs)   // 행 사이 8 dp: 포커스 링(밖 3 dp)이 이웃 행에 가려지지 않는다
            .heightIn(min = 72.dp)
            .focusRing(shape)   // clip 앞: clip 뒤에 두면 행 밖 3 dp 링이 잘려 보이지 않는다(connect_scanning_focused로 확인)
            .clip(shape)
            .clickable(enabled = enabled, onClickLabel = "이 저울에 연결", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                val s = when { connecting -> "연결 중"; recent -> "최근 연결"; else -> null }
                if (s != null) stateDescription = s
            },
    )
}

@Composable
private fun BottomActions(connection: ConnectionUi, onScan: () -> Unit, onStopScan: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    data class Action(val label: String, val primary: Boolean, val onClick: () -> Unit)
    val action = when (connection) {
        is ConnectionUi.Scanning -> if (connection.stopped) Action("다시 찾기", true, onScan) else Action("찾기 멈춤", false, onStopScan)
        ConnectionUi.NoDevice, ConnectionUi.Disconnected, ConnectionUi.ScanFailed, ConnectionUi.ScanEmpty -> Action("다시 찾기", true, onScan)
        is ConnectionUi.Connecting, is ConnectionUi.Reconnecting -> Action("취소", false, onCancel)
        is ConnectionUi.Failed -> Action("다시 시도", true, onRetry)
        else -> null
    } ?: return
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.Center,
        ) {
            val modifier = Modifier.padding(Space.l).widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 56.dp).focusRing(androidx.compose.foundation.shape.CircleShape)
            if (action.primary) Button(action.onClick, modifier) { Text(action.label) }
            else AppOutlinedButton(action.onClick, modifier) { Text(action.label) }
        }
    }
}

/** 9.2 `orderCandidates` 적용. 재정렬은 스캔이 멈출 때 한 번이고, 그때 포인터가 눌려 있으면 뗀 뒤 500 ms까지 미룬다. */
@Composable
private fun rememberOrderedCandidates(
    candidates: List<ScanCandidate>, recent: String?, stopped: Boolean, pointerDown: State<Boolean>, lastReleaseMs: LongState,
): List<ScanCandidate> {
    var order by remember { mutableStateOf(emptyList<String>()) }
    val latest by rememberUpdatedState(candidates)
    val latestRecent by rememberUpdatedState(recent)
    val shown = orderCandidates(order, candidates, recent, resort = false)
    SideEffect { order = shown.map { it.address } }
    LaunchedEffect(stopped) {
        if (stopped) {
            // 눌림 중이거나 뗀 지 500 ms 안이면 미룬다. 대기 중 다시 눌러도 다시 확인한다.
            while (true) {
                if (pointerDown.value) { snapshotFlow { pointerDown.value }.first { !it }; continue }
                val left = RESORT_DELAY_AFTER_RELEASE_MS - (SystemClock.uptimeMillis() - lastReleaseMs.longValue)
                if (left <= 0) break
                delay(left)
            }
            order = orderCandidates(order, latest, latestRecent, resort = true).map { it.address }
        }
    }
    return shown
}
