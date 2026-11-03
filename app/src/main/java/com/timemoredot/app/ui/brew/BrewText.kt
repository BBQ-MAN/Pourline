package com.timemoredot.app.ui.brew

import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.brew.BrewConfig
import com.timemoredot.protocol.GRAMS_PER_OUNCE
import com.timemoredot.protocol.WeightUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 숫자 표시 형식(ui-design 9절). 소수점은 Locale.ROOT, 음수는 U+2212. 숫자와 단위 사이는 NBSP. */
const val MINUS = '−'
const val NBSP = ' '
const val NO_VALUE = "--.-"

private val MAX_ELAPSED_NANOS = BrewConfig().maxElapsedNanos

fun unitSymbol(unit: WeightUnit) = if (unit == WeightUnit.Ounce) "oz" else "g"
fun unitSpoken(unit: WeightUnit) = if (unit == WeightUnit.Ounce) "온스" else "그램"

/** g 값을 표시 단위 문자열로. 반올림해 0이면 부호 없음. */
fun formatWeight(grams: Double, unit: WeightUnit): String {
    val digits = if (unit == WeightUnit.Ounce) 2 else 1
    val v = if (unit == WeightUnit.Ounce) grams / GRAMS_PER_OUNCE else grams
    val scale = if (digits == 2) 100.0 else 10.0
    val r = (v * scale).roundToLong() / scale
    val s = String.format(Locale.ROOT, "%.${digits}f", abs(r))
    return if (r < 0) "$MINUS$s" else s
}

/** 표시 범위 g −9999.9 ~ 9999.9, oz −999.99 ~ 999.99(반올림 뒤, ui-design 4.2). */
fun weightInRange(grams: Double, unit: WeightUnit): Boolean {
    val v = if (unit == WeightUnit.Ounce) grams / GRAMS_PER_OUNCE else grams
    return if (unit == WeightUnit.Ounce) abs((v * 100).roundToLong()) <= 99_999 else abs((v * 10).roundToLong()) <= 99_999
}

fun formatFlow(gps: Double, unit: WeightUnit): String {
    val v = maxOf(0.0, if (unit == WeightUnit.Ounce) gps / GRAMS_PER_OUNCE else gps)
    return String.format(Locale.ROOT, if (unit == WeightUnit.Ounce) "%.2f" else "%.1f", v)
}

/** MM:SS.s, 상한 59:59.9. */
fun formatTimer(elapsedNanos: Long): String {
    val tenths = minOf(elapsedNanos, MAX_ELAPSED_NANOS).coerceAtLeast(0) / 100_000_000
    return String.format(Locale.ROOT, "%02d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10)
}

/** 단계 카드·밸브 칸 시각 M:SS. */
fun formatMinSec(sec: Int) = String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60)
fun spokenMinSec(sec: Long) = "${sec / 60}분 ${sec % 60}초"

/** 임계값 0.5 / 1 / 2(9절 형식 없이). */
fun formatThreshold(g: Double) = if (g % 1.0 == 0.0) g.toLong().toString() else g.toString()

/** 낭독용 숫자(마이너스는 말로). */
fun spokenNumber(display: String) = if (display.startsWith(MINUS)) "마이너스 ${display.drop(1)}" else display

/** S1 상태 알림 문구(ui-design 8.3 S1 표). second = Assertive 여부. */
fun announcementText(a: BrewAnnouncement, connection: ConnectionUi): Pair<String, Boolean> = when (a) {
    BrewAnnouncement.Connected -> "저울 연결됨" to false
    BrewAnnouncement.Reconnected -> "다시 연결됨" to false
    BrewAnnouncement.ReconnectStarted -> "연결 끊김, 다시 연결하는 중" to false
    BrewAnnouncement.ConnectFailed -> when ((connection as? ConnectionUi.Failed)?.reason) {
        ConnectFailure.Timeout -> "연결 실패, 응답 없음"
        ConnectFailure.ServiceNotFound -> "연결 실패, 저울 서비스 없음"
        ConnectFailure.PairingModeMaybe -> "연결 실패, 페어링 모드가 필요할 수 있음"
        else -> "연결 실패"
    } to false
    BrewAnnouncement.Disconnected -> "저울 연결 끊김" to false
    BrewAnnouncement.BluetoothOffDuringBrew -> "블루투스가 꺼졌습니다. 무게를 받을 수 없습니다" to true
    BrewAnnouncement.PermissionLostDuringBrew -> "블루투스 권한이 꺼졌습니다. 무게를 받을 수 없습니다" to true
    BrewAnnouncement.FailedDuringBrew -> "다시 연결하지 못했습니다. 무게를 받을 수 없습니다" to true
    BrewAnnouncement.DisconnectedDuringBrew -> "저울 연결을 끊었습니다. 무게를 받을 수 없습니다" to true
    BrewAnnouncement.Overload -> "과부하. 무게를 표시할 수 없습니다" to false
    BrewAnnouncement.OverloadCleared -> "과부하 해제" to false
    BrewAnnouncement.NoData -> "저울 신호 없음" to false
    BrewAnnouncement.TareDone -> "영점 맞춤" to false
    BrewAnnouncement.TimerStarted -> "타이머 시작" to false
    BrewAnnouncement.TimerPaused -> "타이머 일시정지" to false
    BrewAnnouncement.TimerResumed -> "타이머 계속" to false
    BrewAnnouncement.TimerReset -> "타이머 초기화" to false
    BrewAnnouncement.ScaleTimerStarted -> "저울에서 타이머 시작" to false
    BrewAnnouncement.ScaleTimerPaused -> "저울에서 타이머 일시정지" to false
    BrewAnnouncement.ScaleTimerResumed -> "저울에서 타이머 계속" to false
    BrewAnnouncement.ScaleTimerReset -> "저울에서 타이머 초기화" to false
    BrewAnnouncement.ScaleTimerResetToSummary -> "저울에서 타이머 초기화, 요약 화면으로 이동" to false
    BrewAnnouncement.AutoStartWaiting -> "자동 시작 대기" to false
    BrewAnnouncement.AutoStarted -> "붓기 감지, 타이머 시작" to false
    BrewAnnouncement.AutoFinished -> "추출 완료, 요약 화면으로 이동" to false
    BrewAnnouncement.CapReached -> "최대 기록 시간 60분에 도달해 추출을 끝냈습니다. 요약 화면으로 이동" to false
    // 시각이 정해진 행동 신호라 Assertive(ui-design 8.3 표 밑 주, 오류 Assertive와 뜻이 다름)
    BrewAnnouncement.ValveOpen -> "스위치를 여세요" to true
    BrewAnnouncement.ValveClose -> "스위치를 닫으세요" to true
}

fun messageText(m: BrewMessage): String = when (m) {
    BrewMessage.TareRejected -> "영점을 맞추지 못했습니다."
    BrewMessage.TareTimeout -> "저울이 응답하지 않습니다."
    BrewMessage.TareNotConnected -> "연결이 끊겨 영점을 맞추지 못했습니다."
    BrewMessage.ScaleTimerSyncFailed -> "저울 타이머를 맞추지 못했습니다. 앱 타이머로 계속합니다."
    BrewMessage.Saved -> "기록을 저장했습니다"
    BrewMessage.SaveFailed -> "추출 기록을 저장하지 못했습니다. 앱을 닫으면 기록이 사라집니다."
}

/** 단계 이름 표시: 숫자 뒤 단위 앞 공백을 NBSP로(저장 값은 바꾸지 않는다, ui-design 4.5). */
fun stepNameForDisplay(name: String) = name.replace(Regex("(?<=\\d) (?=g\\b|°C|oz\\b)"), NBSP.toString())

/** D2 본문: "10월 28일 08:12에 시작한 3분 4초 추출이 저장되지 않았습니다." (라벨 있는 문구라 시각·길이를 구분해 읽는다) */
fun recoverText(draft: com.timemoredot.app.data.SessionSummary, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    val at = java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA).format(java.time.Instant.ofEpochMilli(draft.startedAtEpochMs).atZone(zone))
    return "${at}에 시작한 ${spokenMinSec(draft.durationMs / 1000)} 추출이 저장되지 않았습니다."
}
