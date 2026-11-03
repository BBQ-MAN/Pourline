package com.timemoredot.protocol

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** name: 표시 이름 규칙(A-3a). 광고 이름 → 없으면 저장 주소와 같을 때 last_name → 없으면 null. 대체 문구는 화면이 표시할 때만 만든다. */
/** broadcastType: 광고 manufacturerData[0xFFFF] 값[0](A-1, 01 화이트리스트 / 02 일반·페어링 모드). 없으면 null. 첫 연결 실패 안내에 쓴다(F-F5-1). */
data class ScanCandidate(val address: String, val name: String?, val rssi: Int, val lastSeenNanos: Long, val broadcastType: Int? = null)

data class ScaleDevice(
    val address: String,
    val name: String?,             // ScanCandidate.name과 같은 규칙. 연결 성공 시 null이 아니면 last_name으로 저장
    val modelNumber: String?,      // 0x2A24. disReadDone=false면 아직 안 읽음, true인데 null이면 읽기 실패
    val firmwareRevision: String?, // 0x2A26. 같은 규칙
    val mtu: Int,
    val disReadDone: Boolean = false,  // 0x2A24·0x2A26 읽기가 (성공·실패 상관없이) 끝남
) { val modelMismatch: Boolean get() = modelNumber != null && modelNumber != EXPECTED_MODEL }  // A-4: 경고만

sealed interface FailureReason {
    data object NoDeviceFound : FailureReason          // 스캔 10 s 동안 후보 없음
    data object ScanFailed : FailureReason             // onScanFailed
    data object ServiceNotFound : FailureReason        // SCALE_SERVICE_UUID 또는 FFF1/FFF2 특성 없음
    data object ConnectTimeout : FailureReason         // connectGatt부터 알림 구독까지 10 s 초과
    /** 첫 연결 실패 (예: 133). whitelistAdvertising: status 133이고 그 저울의 마지막 광고 방송 유형이 01(화이트리스트)이었음.
     *  처음 보는 휴대폰은 저울을 페어링 모드로 켜야 붙는 것으로 보인다(F-F5-1, 프로브 B Fold5). 133은 다른 이유일 수도 있어 안내는 단정하지 않는다 */
    data class GattError(val status: Int, val whitelistAdvertising: Boolean = false) : FailureReason
    data object ReconnectExhausted : FailureReason     // 재연결 10회 실패
}

sealed interface ConnectionState {
    data object BluetoothOff : ConnectionState
    data object PermissionMissing : ConnectionState
    data object LocationOff : ConnectionState          // API 30 이하에서 위치 서비스 꺼짐
    data object Idle : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val address: String, val name: String?) : ConnectionState
    data class Connected(val device: ScaleDevice) : ConnectionState
    /** attempt 1..10. 백오프 대기 중과 그 시도의 connectGatt 진행 중 모두 이 상태다. nextRetryAtNanos = 이 attempt의 connectGatt 시작(예정) 시각 */
    data class Reconnecting(val address: String, val attempt: Int, val nextRetryAtNanos: Long) : ConnectionState
    data class Failed(val reason: FailureReason) : ConnectionState
}

enum class CommandFailure { NotConnected, Rejected, Timeout, Disconnected, InvalidName, PayloadTooLarge }

sealed interface CommandResult {
    data object Success : CommandResult                          // Write 응답(opcode 03) payload 01 (C-W-1, G-5)
    data class Response(val frame: ScaleFrame) : CommandResult   // Read 응답(opcode 02)
    data class Failed(val reason: CommandFailure) : CommandResult
}

/** architecture.md 5.4. 계약 세부(비우는 시점, 무시 조건 등)는 그 절을 따른다. */
interface ScaleClient {
    val connection: StateFlow<ConnectionState>
    val scanResults: StateFlow<List<ScanCandidate>>     // isScaleCandidate 통과분만. startScan 때와 Connected 도달 때만 비운다
    val status: StateFlow<ScaleStatus>
    val readings: SharedFlow<ScaleReading>              // 약 10 Hz, replay 0, extraBufferCapacity 64
    /** TimerState(op = Notify)에서만 낸다. Read 응답·Write 성공은 status만 바꾼다. replay 0, extraBufferCapacity 16. v1.0.4 실기기에서는 나오지 않음(C-02-2, C-02-3) */
    val timerEvents: SharedFlow<ScaleTimerState>
    val lastDeviceAddress: StateFlow<String?>           // 저장된 마지막 연결 주소

    fun refreshEnvironment()
    fun startScan()
    fun stopScan()
    fun connect(address: String)
    fun connectLastDevice(): Boolean                    // 저장 주소 없으면 false
    fun disconnect()
    suspend fun send(command: ScaleCommand): CommandResult   // 한 번에 하나, 다음 호출은 앞 응답까지 대기
}
