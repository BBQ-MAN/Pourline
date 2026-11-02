package com.timemoredot.protocol.demo

import com.timemoredot.protocol.DecodeResult
import com.timemoredot.protocol.FrameReassembler
import com.timemoredot.protocol.Op
import com.timemoredot.protocol.ScaleFrame
import com.timemoredot.protocol.Vectors
import com.timemoredot.protocol.hex
import com.timemoredot.protocol.hexChunks
import com.timemoredot.protocol.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DemoFrames 바이트 = 실측(confirmed) 벡터 안의 프레임. 벡터 값은 복사하지 않고 id로 읽는다. architecture.md 10.1.6 */
class DemoFramesTest {
    private class Case(val vectorId: String, val bytes: ByteArray, val expected: ScaleFrame)

    private val cases = listOf(
        Case("rx-weight-positive-002", DemoFrames.weight(1450, 0, 0, false), ScaleFrame.WeightReport(1450, 0, 0, false)),          // 3.1
        Case("rx-multi-frames-003", DemoFrames.weight(-2134, 0, 0, false), ScaleFrame.WeightReport(-2134, 0, 0, false)),
        Case("rx-tare-writeack-001", DemoFrames.writeAck(0x0D, true), ScaleFrame.WriteAck(0x0D, true)),                           // 3.8
        Case("rx-multi-frames-003", DemoFrames.writeAck(0x02, true), ScaleFrame.WriteAck(0x02, true)),
        Case("rx-two-frames-003", DemoFrames.battery(Op.Notify, 3, 100), ScaleFrame.Battery(Op.Notify, 3, 100)),                   // 3.3
        Case("rx-battery-read-002", DemoFrames.battery(Op.Read, 3, 100), ScaleFrame.Battery(Op.Read, 3, 100)),
        Case("rx-unit-read-001", DemoFrames.unit(Op.Read, 0), ScaleFrame.UnitReport(Op.Read, 0)),                                  // 3.4
        Case("rx-timer-read-002", DemoFrames.timerRead(3), ScaleFrame.TimerState(Op.Read, 3)),
        Case("rx-firmware-read-001", DemoFrames.firmware("v1.0.4"), ScaleFrame.FirmwareVersion("v1.0.4")),                         // 3.7
        Case("rx-name-read-003", DemoFrames.name("TIMEMORE_Dot"), ScaleFrame.DeviceName("TIMEMORE_Dot")),                           // 3.6
        Case("rx-unknown-cmd-002", DemoFrames.unknown04(0), ScaleFrame.Unknown(0x01, 0x04, "0000000000")),                         // 3.12
    )

    /** 벡터 hex(알림 하나 또는 여럿)를 길이 필드로 프레임 단위로 자른다. */
    private fun vectorFrames(id: String): List<String> = Vectors.byId(id).hexChunks().flatMap { chunk ->
        val b = chunk.hex()
        val out = mutableListOf<String>()
        var i = 0
        while (i < b.size) {
            val n = 6 + (((b[i + 4].toInt() and 0xFF) shl 8) or (b[i + 5].toInt() and 0xFF)) + 2
            out += b.copyOfRange(i, i + n).hex()
            i += n
        }
        out
    }

    @Test fun bytesEqualConfirmedVectors() {
        for (c in cases) {
            assertEquals(c.vectorId, "confirmed", Vectors.byId(c.vectorId).str("status"))
            assertTrue("${c.vectorId}: ${c.bytes.hex()} ∉ ${vectorFrames(c.vectorId)}", c.bytes.hex() in vectorFrames(c.vectorId))
        }
    }

    @Test fun byteByByteFeedYieldsOneFrameAndNoLeftover() {
        for (c in cases) {
            val r = FrameReassembler()
            val results = c.bytes.flatMap { r.feed(byteArrayOf(it)) }
            assertEquals(c.vectorId, listOf(DecodeResult.Frame(c.expected)), results)
            assertEquals(c.vectorId, 0, r.pendingByteCount)
        }
    }
}
