package com.timemoredot.protocol.demo

import com.timemoredot.protocol.Op

/** 저울 → 앱 프레임 바이트(protocol.md 2.1, CRC 자리 00 00). 실측 벡터와 바이트가 같다(DemoFramesTest). architecture.md 10.1.2 */
object DemoFrames {
    fun weight(weightRaw: Int, flowRaw: Int, timerSec: Int, overload: Boolean): ByteArray = frame(
        0x01, 0x01,
        weightRaw ushr 24, weightRaw ushr 16, weightRaw ushr 8, weightRaw,
        flowRaw shr 8, flowRaw, timerSec shr 8, timerSec, if (overload) 1 else 0,
    )

    fun battery(op: Op, bars: Int, percent: Int): ByteArray = frame(opcode(op), 0x05, bars, percent)
    fun unit(op: Op, unitRaw: Int): ByteArray = frame(opcode(op), 0x06, unitRaw)
    fun timerRead(stateRaw: Int): ByteArray = frame(0x02, 0x02, stateRaw)
    fun writeAck(cmd: Int, success: Boolean): ByteArray = frame(0x03, cmd, if (success) 1 else 0)
    fun name(name: String): ByteArray = frame(0x02, 0x0C, name.encodeToByteArray())
    fun firmware(version: String): ByteArray = frame(0x02, 0x10, version.toByteArray(Charsets.US_ASCII))
    fun unknown04(first: Int): ByteArray = frame(0x01, 0x04, first, 0, 0, 0, 0)   // C-04-1

    private fun opcode(op: Op) = if (op == Op.Read) 0x02 else 0x01

    private fun frame(opcode: Int, cmd: Int, vararg payload: Int): ByteArray =
        frame(opcode, cmd, ByteArray(payload.size) { payload[it].toByte() })

    private fun frame(opcode: Int, cmd: Int, payload: ByteArray): ByteArray =
        byteArrayOf(0xA5.toByte(), 0x5A, opcode.toByte(), cmd.toByte(), (payload.size shr 8).toByte(), payload.size.toByte()) +
            payload + byteArrayOf(0, 0)
}
