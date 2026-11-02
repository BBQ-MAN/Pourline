package com.timemoredot.protocol.demo

import com.timemoredot.protocol.GRAMS_PER_OUNCE
import com.timemoredot.protocol.Op
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** S2 후보 하나 = 시나리오 하나. 주소는 로컬 관리 MAC 모양(실제 저울과 겹치지 않음). architecture.md 10.1.2 */
enum class DemoScenario(val address: String, val displayName: String) {
    BrewV60("02:00:00:00:00:01", "데모 저울 · V60"),
    BrewHario("02:00:00:00:00:02", "데모 저울 · 하리오 스위치"),
    Reconnect("02:00:00:00:00:03", "데모 저울 · 끊김·재연결"),
    Overload("02:00:00:00:00:04", "데모 저울 · 과부하"),
    BluetoothOff("02:00:00:00:00:05", "데모 저울 · 블루투스 꺼짐"),
    ConnectFail("02:00:00:00:00:06", "데모 저울 · 연결 실패");

    companion object {
        fun byAddress(address: String): DemoScenario? = entries.firstOrNull { it.address.equals(address, ignoreCase = true) }
    }
}

/** 시나리오가 transport·환경에 내는 링크 사건(BleScaleClient가 아니라 DemoGattTransport·DemoEnvironment가 해석). */
sealed interface DemoLinkEvent {
    data class Drop(val status: Int) : DemoLinkEvent                 // ConnectionChanged(false, status)
    data class BluetoothOff(val durationMs: Long) : DemoLinkEvent     // 환경 블루투스 꺼짐 → durationMs 뒤 켜짐
}

data class DemoPoll(val notifications: List<ByteArray>, val link: DemoLinkEvent?)

/** 저울 펌웨어 흉내(architecture.md 10.1.2 흉내 규칙 표). 순수 JVM, 시각은 인자(단조 ns). 주소 하나에 하나이고
 *  연결이 끊겨도 영점·타이머·붓기 상태를 유지한다. 메인 스레드 전용. */
class DemoScaleDevice(val scenario: DemoScenario, @Suppress("UNUSED_PARAMETER") startNanos: Long) {
    private class Reply(val dueNanos: Long, val frames: List<ByteArray>, val onSent: () -> Unit)
    private val replies = mutableListOf<Reply>()

    // 무게(g). 물리 무게 = base + pour(붓기 시간), 표시 = 물리 − tare
    private var baseGrams = START_GRAMS
    private var tareGrams = 0.0
    private var pourStartNanos: Long? = null
    private var awaitingZero = false           // 영점 뒤 첫 0 g 프레임을 기다림 → 그 3 s 뒤 붓기 시작
    private var tareFramesLeft: Int? = null    // 영점 응답 뒤 아직 영점 전 값으로 낼 프레임 수(C-0D-2)
    private val history = ArrayDeque<Int>()    // 낸 weightRaw 최근 FLOW_FRAMES개
    private var unitRaw = 0x00

    // 타이머
    private var timerStateRaw = TIMER_RESET
    private var timerAccumNanos = 0L
    private var timerRunningSince: Long? = null

    // 링크
    private var firstConnectedNanos: Long? = null
    private var nextBatteryNanos = Long.MAX_VALUE
    private var failAttemptsLeft = 0
    private var dropped = false
    private var bluetoothOffFired = false
    private var dropNext: Int? = null

    /** FFF2에 쓴 프레임 하나. 헤더·길이가 틀리거나 모르는 cmd면 응답 없음(앱은 Timeout). */
    fun onWrite(bytes: ByteArray, atNanos: Long) {
        if (bytes.size < 8 || bytes[0] != 0xA5.toByte() || bytes[1] != 0x5A.toByte()) return
        val len = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
        if (bytes.size != 8 + len) return
        val opcode = bytes[2].toInt() and 0xFF
        val cmd = bytes[3].toInt() and 0xFF
        val payload = bytes.copyOfRange(6, 6 + len)
        when (opcode) {
            0x02 -> if (len == 0) onRead(cmd, atNanos)
            0x03 -> onWriteCommand(cmd, payload, atNanos)
        }
    }

    private fun onRead(cmd: Int, at: Long) {
        val frame = when (cmd) {
            0x02 -> DemoFrames.timerRead(timerStateRaw)
            0x05 -> DemoFrames.battery(Op.Read, BATTERY_BARS, BATTERY_PERCENT)
            0x06 -> DemoFrames.unit(Op.Read, unitRaw)
            0x0C -> DemoFrames.name(DEVICE_NAME)
            0x10 -> DemoFrames.firmware(FIRMWARE)
            else -> return
        }
        reply(at, listOf(frame))
    }

    private fun onWriteCommand(cmd: Int, payload: ByteArray, at: Long) {
        val ack = DemoFrames.writeAck(cmd, true)
        val arg = payload.singleOrNull()?.toInt()?.and(0xFF)
        when (cmd) {
            0x02 -> {   // SetTimer
                val fromReset = timerStateRaw == TIMER_RESET
                when (arg) {
                    0x01 -> if (timerStateRaw != TIMER_RUNNING) { timerRunningSince = at; timerStateRaw = TIMER_RUNNING }
                    0x02 -> { timerAccumNanos = timerNanos(at); timerRunningSince = null; timerStateRaw = TIMER_PAUSED }
                    0x03 -> { timerAccumNanos = 0; timerRunningSince = null; timerStateRaw = TIMER_RESET }
                    else -> return
                }
                // 3.12 표 첫 행: 리셋 상태에서 시작하면 같은 알림에 0x04가 붙는다(rx-multi-frames-003)
                reply(at, if (arg == 0x01 && fromReset) listOf(ack, DemoFrames.unknown04(0)) else listOf(ack))
            }
            0x06 -> if (arg == 0x00 || arg == 0x01) { unitRaw = arg; reply(at, listOf(ack)) }
            0x0D -> if (payload.isEmpty()) reply(at, listOf(ack)) { tareFramesLeft = TARE_PRE_FRAMES }
            0x0B -> if (payload.isEmpty()) reply(at, listOf(ack)) { dropNext = POWER_OFF_STATUS }
            0x0C -> if (payload.size in 1..16) reply(at, listOf(ack))
            0x19, 0x1A -> if (payload.isEmpty()) reply(at, listOf(ack))
        }
    }

    private fun reply(at: Long, frames: List<ByteArray>, onSent: () -> Unit = {}) {
        replies += Reply(at + REPLY_DELAY_NANOS, frames, onSent)
    }

    /** 연결 시도마다 한 번. false면 그 시도는 ConnectionChanged(false, 133)으로 끝난다. */
    fun onConnectAttempt(@Suppress("UNUSED_PARAMETER") atNanos: Long): Boolean = when {
        scenario == DemoScenario.ConnectFail -> false
        failAttemptsLeft > 0 -> { failAttemptsLeft--; false }
        else -> true
    }

    /** 알림 구독까지 끝나 연결된 시각(시나리오 시계 기준점). */
    fun onConnected(atNanos: Long) {
        if (firstConnectedNanos == null) firstConnectedNanos = atNanos
        nextBatteryNanos = atNanos + BATTERY_PERIOD_NANOS
    }

    /** 100 ms마다 부른다. atNanos까지 낼 알림과 링크 사건. */
    fun poll(atNanos: Long): DemoPoll {
        dropNext?.let { dropNext = null; return DemoPoll(emptyList(), DemoLinkEvent.Drop(it)) }
        val t = pourSec(atNanos)
        if (scenario == DemoScenario.Reconnect && t != null) {
            if (!dropped && t >= DROP_AT_SEC) {
                dropped = true
                failAttemptsLeft = 2
                return DemoPoll(emptyList(), DemoLinkEvent.Drop(8))
            }
            if (t >= 20.0 && t < 25.0) return DemoPoll(emptyList(), null)   // 알림 끊김(링크 유지)
        }
        val first = firstConnectedNanos
        if (scenario == DemoScenario.BluetoothOff && !bluetoothOffFired && first != null && atNanos >= first + BLUETOOTH_OFF_AFTER_NANOS) {
            bluetoothOffFired = true
            return DemoPoll(emptyList(), DemoLinkEvent.BluetoothOff(8_000))
        }

        var out = ByteArray(0)
        if (atNanos >= nextBatteryNanos) {   // C-05-3: weight 앞(rx-two-frames-003)
            out += DemoFrames.battery(Op.Notify, BATTERY_BARS, BATTERY_PERCENT)
            nextBatteryNanos += BATTERY_PERIOD_NANOS
        }
        if (tareFramesLeft == 0) {
            applyTare(atNanos)
            tareFramesLeft = null
        }
        out += weightFrame(atNanos)
        tareFramesLeft = tareFramesLeft?.minus(1)
        val due = replies.filter { it.dueNanos <= atNanos }
        replies.removeAll(due)
        for (r in due) {
            r.frames.forEach { out += it }
            r.onSent()
        }
        return DemoPoll(listOf(out), null)
    }

    private fun applyTare(at: Long) {
        baseGrams = physicalGrams(at)
        tareGrams = baseGrams
        pourStartNanos = null
        awaitingZero = true
    }

    private fun weightFrame(at: Long): ByteArray {
        val t = pourSec(at)
        val overload = scenario == DemoScenario.Overload && t != null && t >= 15.0 && t < 20.0
        val raw = if (overload) history.lastOrNull() ?: 0 else toRaw(physicalGrams(at) - tareGrams)
        if (awaitingZero && raw == 0) {
            awaitingZero = false
            pourStartNanos = at + POUR_DELAY_NANOS
        }
        val flow = if (history.size >= FLOW_FRAMES) (raw - history.first()).coerceIn(0, 999) else 0   // C-01-5, C-01-7
        history.addLast(raw)
        if (history.size > FLOW_FRAMES) history.removeFirst()
        return DemoFrames.weight(raw, flow, (timerNanos(at) / NANOS_PER_SEC).toInt(), overload)
    }

    private fun toRaw(grams: Double): Int =
        if (unitRaw == 0x01) (grams / GRAMS_PER_OUNCE * 100).roundToInt() else (grams * 10).roundToInt()

    private fun pourSec(at: Long): Double? = pourStartNanos?.let { (at - it) / NANOS_PER_SEC.toDouble() }

    private fun physicalGrams(at: Long): Double = baseGrams + (pourSec(at)?.let(::pour) ?: 0.0)

    private fun timerNanos(at: Long): Long = timerAccumNanos + (timerRunningSince?.let { at - it } ?: 0L)

    /** 붓기 t초 뒤 더해진 물(g). V60 계열은 ui-organic-direction 6.4 합성 식(:app test BrewFixtures.Fx.w와 같음). */
    private fun pour(t: Double): Double = when {
        t < 0 -> 0.0
        scenario == DemoScenario.ConnectFail -> 0.0
        scenario == DemoScenario.BrewHario -> if (t < 10) 24.0 * t else 240.0
        t <= 10 -> { val x = t / 10; 61 * (x - 0.5 * sin(TAU * x) / TAU) }
        t <= 40 -> 61.0
        t <= 90 -> { val x = (t - 40) / 50; 61 + 189 * (x - 0.5 * sin(TAU * x) / TAU - 0.3 * sin(8 * TAU * x) / (8 * TAU)) }
        else -> 250.0
    }

    private companion object {
        const val START_GRAMS = 213.4   // 빈 드리퍼·서버, 실측 log:2724
        const val NANOS_PER_SEC = 1_000_000_000L
        const val REPLY_DELAY_NANOS = 150_000_000L          // C-W-2 실측 128~304 ms 안
        const val POUR_DELAY_NANOS = 3 * NANOS_PER_SEC
        const val BATTERY_PERIOD_NANOS = 30 * NANOS_PER_SEC
        const val BLUETOOTH_OFF_AFTER_NANOS = 20 * NANOS_PER_SEC
        const val DROP_AT_SEC = 40.0
        const val TARE_PRE_FRAMES = 2
        const val FLOW_FRAMES = 10
        const val POWER_OFF_STATUS = 0x13
        const val BATTERY_BARS = 3
        const val BATTERY_PERCENT = 82
        const val DEVICE_NAME = "TIMEMORE_Dot"
        const val FIRMWARE = "demo"
        const val TIMER_RUNNING = 0x01
        const val TIMER_PAUSED = 0x02
        const val TIMER_RESET = 0x03
        const val TAU = 2 * PI
    }
}
