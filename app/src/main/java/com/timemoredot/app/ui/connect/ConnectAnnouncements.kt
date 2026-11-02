package com.timemoredot.app.ui.connect

/** S2 TalkBack 알림 문구(ui-design 8.3, architecture 9.2). 이전 상태 → 현재 상태 전이에서 한 문구 또는 null. 모두 Polite. */
fun connectAnnouncement(previous: ConnectionUi?, current: ConnectionUi, locationPermission: Boolean = false): String? {
    if (previous == null || previous::class != current::class) {
        return when (current) {
            is ConnectionUi.Scanning ->
                if (current.stopped) stoppedText(current) else if (current.devices.isEmpty()) "저울 찾는 중" else foundText(current.devices.size)
            ConnectionUi.ScanEmpty -> "저울을 찾지 못했습니다"
            ConnectionUi.ScanFailed -> "찾기를 시작하지 못했습니다"
            is ConnectionUi.Connecting -> "연결 중"
            is ConnectionUi.Reconnecting -> "저장된 저울에 다시 연결하는 중"
            is ConnectionUi.Connected -> if (previous is ConnectionUi.Reconnecting) "저울 연결됨" else null
            is ConnectionUi.Failed -> "연결 실패, " + when (current.reason) {
                ConnectFailure.ConnectFailed -> "연결하지 못함"
                ConnectFailure.ServiceNotFound -> "저울 서비스 없음"
                ConnectFailure.Timeout -> "응답 없음"
            }
            ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied, ConnectionUi.BluetoothOff, ConnectionUi.LocationOff ->
                emptyStateTitle(current, locationPermission)
            else -> null
        }
    }
    if (previous is ConnectionUi.Scanning && current is ConnectionUi.Scanning) {
        if (previous.stopped && !current.stopped) return if (current.devices.isEmpty()) "저울 찾는 중" else foundText(current.devices.size)   // 다시 찾기
        if (previous.devices.isEmpty() && current.devices.isNotEmpty() && !current.stopped) return foundText(current.devices.size)
        if (!previous.stopped && current.stopped && current.devices.isNotEmpty()) return stoppedText(current)
    }
    return null
}

private fun foundText(n: Int) = "저울 ${n}대 찾음"
private fun stoppedText(s: ConnectionUi.Scanning) = "찾기 끝, 저울 ${s.devices.size}대"

/** 빈 상태 제목. API 30 이하 위치 권한 문구는 locationPermission = true. */
fun emptyStateTitle(ui: ConnectionUi, locationPermission: Boolean): String? = when (ui) {
    ConnectionUi.BluetoothOff -> "블루투스가 꺼져 있습니다"
    ConnectionUi.PermissionMissing -> if (locationPermission) "위치 권한이 필요합니다" else "근처 기기 권한이 필요합니다"
    ConnectionUi.PermissionPermanentlyDenied -> "권한이 꺼져 있습니다"
    ConnectionUi.LocationOff -> "위치 서비스를 켜야 저울을 찾을 수 있습니다"
    else -> null
}
