package com.timemoredot.protocol

fun interface MonotonicClock { fun nowNanos(): Long }    // 앱: SystemClock::elapsedRealtimeNanos

data class ScaleReading(
    val weightGrams: Double?,          // g. 과부하면 null (C-01-11: 과부하 중 값은 쓰지 않음)
    val scaleFlowGramsPerSec: Double,  // g/s. 저울 계산값 clamp(w(t) − w(t−1 s), 0, 99.9 g/s) (C-01-5, C-01-7). 음수도 그대로 둔다
    val scaleTimerSec: Int,            // 저울 타이머 raw 초 (진단용, 앱 타이머 아님)
    val overload: Boolean,
    val sourceUnit: WeightUnit,        // 변환에 쓴 저울 단위
    val atNanos: Long,                 // 알림 수신 시각 (elapsedRealtimeNanos)
)

data class ScaleStatus(
    val unit: WeightUnit?,             // null = 단위 미상 (C-01-13)
    val unitReadFailed: Boolean,       // 아직 단위를 모르고, ReadUnit 실패·시간 초과 또는 unitRaw가 00/01 밖 → UnitUnknownTimedOut
    val batteryPercent: Int?,          // 0..100, 100 초과는 100
    val scaleTimer: ScaleTimerState?,
) { companion object { val Unknown = ScaleStatus(null, false, null, null) } }

/** 프레임 → 상태·측정값. 순수 JVM, raw → g 변환의 유일한 장소. 연결 하나에 하나. */
class ScaleStateReducer(private val config: ProtocolConfig = ProtocolConfig.Default) {
    var status: ScaleStatus = ScaleStatus.Unknown
        private set

    /** WeightReport면 변환한 측정값, 단위 미상이면 null(C-01-13). 그 밖의 프레임은 status만 갱신하고 null. */
    fun onFrame(frame: ScaleFrame, atNanos: Long): ScaleReading? {
        when (frame) {
            is ScaleFrame.WeightReport -> return toReading(frame, atNanos)
            is ScaleFrame.UnitReport -> {
                // protocol.md 3.4: 00/01 밖은 단위 미상, 읽기 실패와 같게 다룬다
                val unit = WeightUnit.entries.firstOrNull { it.raw == frame.unitRaw }
                status = status.copy(unit = unit, unitReadFailed = unit == null)
            }
            is ScaleFrame.Battery -> status = status.copy(batteryPercent = frame.percent.coerceIn(0, 100))
            is ScaleFrame.TimerState -> timerState(frame.stateRaw)?.let { status = status.copy(scaleTimer = it) }   // 미상 stateRaw는 무시(3.2)
            else -> Unit   // WriteAck·DeviceName·FirmwareVersion·Unknown(0x04 등)은 상태를 바꾸지 않는다
        }
        return null
    }

    /** Write 성공 응답 뒤 호출 (protocol 3.2, 3.4). */
    fun onCommandSucceeded(command: ScaleCommand) {
        status = status.afterSuccess(command)
    }

    /** 단위를 이미 알면 바꾸지 않는다(필드 정의: "아직 단위를 모르고"). */
    fun onUnitReadFailed() {
        if (status.unit == null) status = status.copy(unitReadFailed = true)
    }

    fun reset() {
        status = ScaleStatus.Unknown
    }

    private fun toReading(f: ScaleFrame.WeightReport, atNanos: Long): ScaleReading? {
        val unit = status.unit ?: return null
        val (scale, gramsPerUnit) = when (unit) {
            WeightUnit.Gram -> config.gramMode to 1.0
            WeightUnit.Ounce -> config.ounceMode to GRAMS_PER_OUNCE
        }
        return ScaleReading(
            weightGrams = if (f.overload) null else f.weightRaw * scale.weightPerRaw * gramsPerUnit,
            scaleFlowGramsPerSec = f.flowRaw * scale.flowPerRawPerSec * gramsPerUnit,
            scaleTimerSec = f.timerSec,
            overload = f.overload,
            sourceUnit = unit,
            atNanos = atNanos,
        )
    }
}

/** Write 성공의 상태 반영 규칙. ScaleStateReducer와 FakeScaleClient가 같이 쓴다. */
fun ScaleStatus.afterSuccess(command: ScaleCommand): ScaleStatus = when (command) {
    is ScaleCommand.SetUnit -> copy(unit = command.unit, unitReadFailed = false)
    is ScaleCommand.SetTimer -> copy(
        scaleTimer = when (command.action) {
            TimerAction.Start -> ScaleTimerState.Running
            TimerAction.Pause -> ScaleTimerState.Paused
            TimerAction.Reset -> ScaleTimerState.Reset
        },
    )
    else -> this
}

private fun timerState(raw: Int): ScaleTimerState? = when (raw) {
    0x01 -> ScaleTimerState.Running
    0x02 -> ScaleTimerState.Paused
    0x03 -> ScaleTimerState.Reset
    else -> null
}
