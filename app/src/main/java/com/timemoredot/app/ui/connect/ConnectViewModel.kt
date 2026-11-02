package com.timemoredot.app.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.PermissionMonitor
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.ScaleClient
import com.timemoredot.protocol.ScanCandidate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectUiState(
    val connection: ConnectionUi,
    val recentAddress: String?,
    val candidates: List<ScanCandidate>,   // = scale.scanResults. Connecting·Failed에서도 채운다(목록 유지)
)

sealed interface ConnectEvent { data object Connected : ConnectEvent }

class ConnectViewModel(private val scale: ScaleClient, private val permission: PermissionMonitor) : ViewModel() {
    private var lastAttempt: String? = null
    private val _events = Channel<ConnectEvent>(Channel.BUFFERED)
    val events: Flow<ConnectEvent> = _events.receiveAsFlow()

    private fun build(state: ConnectionState, scan: List<ScanCandidate>, perm: com.timemoredot.app.PermissionStatus, last: String?) =
        ConnectUiState(connectionUi(state, scan, perm, hasSavedDevice = last != null), last, scan)

    val uiState: StateFlow<ConnectUiState> =
        combine(scale.connection, scale.scanResults, permission.status, scale.lastDeviceAddress, ::build)
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                build(scale.connection.value, scale.scanResults.value, permission.status.value, scale.lastDeviceAddress.value),
            )

    init {
        // Connected 진입 때 한 번(DIS 읽기 뒤 두 번째 Connected는 같은 종류라 다시 내지 않는다).
        viewModelScope.launch {
            scale.connection.map { it is ConnectionState.Connected }.distinctUntilChanged().collect { connected ->
                if (connected) {
                    lastAttempt = null   // 이후 실패(재연결 소진 등)의 "다시 시도"는 저장 기기로 간다
                    _events.send(ConnectEvent.Connected)
                }
            }
        }
    }

    fun onPermissionResult(allGranted: Boolean, permanentlyDenied: Boolean) = permission.onResult(allGranted, permanentlyDenied)
    fun onResume() = permission.onResume()
    fun onScanClick() = scale.startScan()
    fun onStopScanClick() = scale.stopScan()
    fun onCandidateClick(address: String) {
        lastAttempt = address
        scale.connect(address)
    }
    fun onCancelConnectClick() = scale.disconnect()
    fun onRetryClick() {
        val address = lastAttempt
        if (address != null) scale.connect(address)
        else if (!scale.connectLastDevice()) scale.startScan()
    }
}
