package com.timemoredot.protocol

// ProtocolConfig.kt — 프로토콜 상수와 미확정 값은 이 파일에만 둔다

/** 송신 프레임 CRC 필드 값(2바이트, 상위 바이트 먼저). 수신 CRC는 검사하지 않고 버린다. protocol.md F-8, F-9(v1.0.4 확정). */
const val TX_CRC: Int = 0x0000

/** raw 1 단위가 해당 모드 단위(g 또는 oz)로 얼마인지. */
data class RawScale(val weightPerRaw: Double, val flowPerRawPerSec: Double)

data class ProtocolConfig(
    val gramMode: RawScale,        // protocol.md C-01-3, C-01-5 확정
    val ounceMode: RawScale,       // TODO(device): protocol.md C-01-4, C-01-6
    val maxPayloadLength: Int,     // protocol.md F-10a 앱 방어 규칙
    val responseTimeoutMs: Long,   // protocol.md C-W-2 앱 규칙(실측 128~304 ms). Read·Write 모두
) {
    companion object {
        val Default = ProtocolConfig(
            gramMode = RawScale(weightPerRaw = 0.1, flowPerRawPerSec = 0.1),
            ounceMode = RawScale(weightPerRaw = 0.01, flowPerRawPerSec = 0.01),
            maxPayloadLength = 64,
            responseTimeoutMs = 1_000,
        )
    }
}

// 프레임 형식(protocol.md 2.1, F-1~F-5 확정)
internal const val HEADER_0: Byte = 0xA5.toByte()
internal const val HEADER_1: Byte = 0x5A
internal const val HEADER_SIZE = 6   // header 2 + opcode 1 + cmd 1 + length 2
internal const val CRC_SIZE = 2

internal const val OP_NOTIFY = 0x01
internal const val OP_READ = 0x02
internal const val OP_WRITE = 0x03

internal const val CMD_WEIGHT = 0x01
internal const val CMD_TIMER = 0x02
internal const val CMD_BATTERY = 0x05
internal const val CMD_UNIT = 0x06
internal const val CMD_POWER_OFF = 0x0B
internal const val CMD_NAME = 0x0C
internal const val CMD_TARE = 0x0D
internal const val CMD_FIRMWARE = 0x10
internal const val CMD_FACTORY_RESET = 0x19
internal const val CMD_FORGET = 0x1A

/** Write 응답을 받는 cmd(protocol.md 3절 W 표시). */
internal val WRITE_CMDS = setOf(CMD_TIMER, CMD_UNIT, CMD_POWER_OFF, CMD_NAME, CMD_TARE, CMD_FACTORY_RESET, CMD_FORGET)

internal const val WEIGHT_PAYLOAD_LEN = 9   // C-01-1
internal const val NAME_MAX_BYTES = 16      // C-0C-2
