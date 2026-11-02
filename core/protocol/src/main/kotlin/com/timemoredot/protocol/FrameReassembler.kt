package com.timemoredot.protocol

/** protocol.md 2.3 규칙 1~9. 상태를 갖는다. 연결 하나에 하나. CRC 2바이트는 읽고 버린다(규칙 5). */
class FrameReassembler(private val config: ProtocolConfig = ProtocolConfig.Default) {
    private var buffer = ByteArray(0)

    /** 버퍼에 남아 다음 알림을 기다리는 바이트 수(테스트용). */
    internal val pendingByteCount: Int get() = buffer.size

    /** 알림 하나 = 호출 하나. 결과 순서 = 알림 안의 프레임 순서 */
    fun feed(chunk: ByteArray): List<DecodeResult> {
        buffer += chunk
        val out = mutableListOf<DecodeResult>()
        while (true) {
            val start = findHeader()
            if (start < 0) {
                // 규칙 1: 헤더가 없으면 끝의 단독 A5만 남긴다
                buffer = if (buffer.lastOrNull() == HEADER_0) byteArrayOf(HEADER_0) else ByteArray(0)
                break
            }
            if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
            if (buffer.size < HEADER_SIZE) break   // 규칙 2
            val length = ((buffer[4].toInt() and 0xFF) shl 8) or (buffer[5].toInt() and 0xFF)
            if (length > config.maxPayloadLength) {   // 규칙 3: 가짜 헤더, A5만 버리고 다시 찾는다
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }
            val frameSize = HEADER_SIZE + length + CRC_SIZE
            if (buffer.size < frameSize) break   // 규칙 4
            val payload = buffer.copyOfRange(HEADER_SIZE, HEADER_SIZE + length)   // 규칙 5: CRC는 보지 않는다
            out += FrameCodec.decode(buffer[2].toInt() and 0xFF, buffer[3].toInt() and 0xFF, payload)
            buffer = buffer.copyOfRange(frameSize, buffer.size)   // 규칙 6
        }
        return out
    }

    /** 연결 끊김 시 (규칙 9) */
    fun reset() {
        buffer = ByteArray(0)
    }

    private fun findHeader(): Int {
        for (i in 0 until buffer.size - 1) {
            if (buffer[i] == HEADER_0 && buffer[i + 1] == HEADER_1) return i
        }
        return -1
    }
}
