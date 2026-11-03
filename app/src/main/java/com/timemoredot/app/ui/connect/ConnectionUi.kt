package com.timemoredot.app.ui.connect

import com.timemoredot.app.PermissionStatus
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScanCandidate

/** PairingModeMaybe: 첫 연결 133 + 화이트리스트 방송(F-F5-1). 새 휴대폰이면 저울을 페어링 모드로 켜야 할 수 있다. */
enum class ConnectFailure { ConnectFailed, PairingModeMaybe, ServiceNotFound, Timeout }

/** architecture.md 9.2. S1 칩·배너, S2, S3가 같은 값을 쓴다. */
sealed interface ConnectionUi {
    data object NoDevice : ConnectionUi
    data object Disconnected : ConnectionUi
    data object BluetoothOff : ConnectionUi
    data object PermissionMissing : ConnectionUi
    data object PermissionPermanentlyDenied : ConnectionUi
    data object LocationOff : ConnectionUi
    data class Scanning(val devices: List<ScanCandidate>, val stopped: Boolean) : ConnectionUi
    data object ScanEmpty : ConnectionUi
    data object ScanFailed : ConnectionUi
    data class Connecting(val address: String, val deviceName: String?) : ConnectionUi
    data class Connected(val device: ScaleDevice) : ConnectionUi
    data class Reconnecting(val attempt: Int, val nextRetryAtNanos: Long) : ConnectionUi
    data class Failed(val reason: ConnectFailure) : ConnectionUi
}

fun connectionUi(
    state: ConnectionState,
    scanResults: List<ScanCandidate>,
    permission: PermissionStatus,
    hasSavedDevice: Boolean,
): ConnectionUi = when (state) {
    ConnectionState.PermissionMissing ->
        if (permission == PermissionStatus.PermanentlyDenied) ConnectionUi.PermissionPermanentlyDenied else ConnectionUi.PermissionMissing
    ConnectionState.BluetoothOff -> ConnectionUi.BluetoothOff
    ConnectionState.LocationOff -> ConnectionUi.LocationOff
    ConnectionState.Idle -> when {
        scanResults.isNotEmpty() -> ConnectionUi.Scanning(scanResults, stopped = true)
        hasSavedDevice -> ConnectionUi.Disconnected
        else -> ConnectionUi.NoDevice
    }
    ConnectionState.Scanning -> ConnectionUi.Scanning(scanResults, stopped = false)
    is ConnectionState.Connecting -> ConnectionUi.Connecting(state.address, state.name)
    is ConnectionState.Connected -> ConnectionUi.Connected(state.device)
    is ConnectionState.Reconnecting -> ConnectionUi.Reconnecting(state.attempt, state.nextRetryAtNanos)
    is ConnectionState.Failed -> when (val reason = state.reason) {
        FailureReason.NoDeviceFound -> ConnectionUi.ScanEmpty
        FailureReason.ScanFailed -> ConnectionUi.ScanFailed
        FailureReason.ServiceNotFound -> ConnectionUi.Failed(ConnectFailure.ServiceNotFound)
        FailureReason.ConnectTimeout -> ConnectionUi.Failed(ConnectFailure.Timeout)
        is FailureReason.GattError ->
            ConnectionUi.Failed(if (reason.whitelistAdvertising) ConnectFailure.PairingModeMaybe else ConnectFailure.ConnectFailed)
        FailureReason.ReconnectExhausted -> ConnectionUi.Failed(ConnectFailure.ConnectFailed)
    }
}

/** 표시 이름 규칙(ui-design 3.3): 이름이 있으면 그대로, 없으면 "TIMEMORE Dot · " + 주소 끝 5자. 저장하지 않는다. */
fun displayName(name: String?, address: String): String =
    name?.takeIf { it.isNotBlank() } ?: "TIMEMORE Dot · ${address.takeLast(5)}"

/** 낭독용 이름: 이름 없는 후보는 "TIMEMORE Dot, 주소 끝 A1 B2"(기호를 읽지 않게, ui-design 8.4). */
fun spokenName(name: String?, address: String): String =
    name?.takeIf { it.isNotBlank() } ?: "TIMEMORE Dot, 주소 끝 ${address.takeLast(5).replace(':', ' ')}"

/** S2 후보 표시 이름(ui-design 3.3 1·1-1, architecture 9.2): 키 = address. 기본 이름이 같은 항목이 2개 이상이면 " · " + 주소 끝 5자. */
fun displayNames(candidates: List<ScanCandidate>): Map<String, String> {
    val base = candidates.associate { it.address to displayName(it.name, it.address) }
    val counts = base.values.groupingBy { it }.eachCount()
    return base.mapValues { (addr, n) -> if (counts.getValue(n) > 1) "$n · ${addr.takeLast(5)}" else n }
}

fun listName(c: ScanCandidate, all: List<ScanCandidate>): String =
    displayNames(all)[c.address] ?: displayName(c.name, c.address)

/** listName의 낭독용(기호를 읽지 않게 ':'은 공백). */
fun spokenListName(c: ScanCandidate, all: List<ScanCandidate>): String {
    val base = spokenName(c.name, c.address)
    return if (listName(c, all) != displayName(c.name, c.address)) "$base, 주소 끝 ${c.address.takeLast(5).replace(':', ' ')}" else base
}

/** 5.1 권한 배너 문구(S1·S3 공용). */
fun permissionText(permanent: Boolean, location: Boolean): String = when {
    permanent -> if (location) "'위치' 권한이 꺼져 있습니다. 설정에서 허용하세요." else "'근처 기기' 권한이 꺼져 있습니다. 설정에서 허용하세요."
    location -> "저울을 찾으려면 '위치' 권한이 필요합니다. 위치는 저울을 찾는 데만 씁니다."
    else -> "저울을 찾으려면 '근처 기기' 권한이 필요합니다."
}
