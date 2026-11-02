package com.timemoredot.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameReassemblerTest {
    private val weightHex = "A55A01010009000004D2000C0005000000"
    private val weight = DecodeResult.Frame(ScaleFrame.WeightReport(1234, 12, 5, false))
    private val battery = DecodeResult.Frame(ScaleFrame.Battery(Op.Notify, 3, 100))

    @Test
    fun dropsLeadingGarbage() {
        val r = FrameReassembler()
        assertEquals(listOf(weight), r.feed("00FF12A5$weightHex".hex()))
        assertEquals(0, r.pendingByteCount)
    }

    @Test
    fun twoFramesInOneNotification() {
        val r = FrameReassembler()
        assertEquals(listOf(weight, battery), r.feed("${weightHex}A55A0105000203640000".hex()))
        assertEquals(0, r.pendingByteCount)
    }

    @Test
    fun resyncsAfterFakeHeaderWithTooLongLength() {
        val r = FrameReassembler()
        // length 0x0041 = 65 > maxPayloadLength(64): A5만 버리고 다음 헤더를 찾는다
        assertEquals(listOf(weight), r.feed("A55A01010041$weightHex".hex()))
        assertEquals(0, r.pendingByteCount)
    }

    @Test
    fun keepsLoneTrailingA5() {
        val r = FrameReassembler()
        assertEquals(emptyList<DecodeResult>(), r.feed("0011A5".hex()))
        assertEquals(1, r.pendingByteCount)
        assertEquals(listOf(weight), r.feed(weightHex.drop(2).hex()))
        assertEquals(0, r.pendingByteCount)
    }

    @Test
    fun dropsBytesWithoutHeader() {
        val r = FrameReassembler()
        assertEquals(emptyList<DecodeResult>(), r.feed("00115A".hex()))
        assertEquals(0, r.pendingByteCount)
    }

    @Test
    fun resetClearsBuffer() {
        val r = FrameReassembler()
        assertEquals(emptyList<DecodeResult>(), r.feed(weightHex.take(20).hex()))
        assertEquals(10, r.pendingByteCount)
        r.reset()
        assertEquals(0, r.pendingByteCount)
        // 끊김 전 반쪽 프레임이 다음 연결의 프레임과 섞이지 않는다
        assertEquals(listOf(weight), r.feed(weightHex.hex()))
    }

    @Test
    fun unknownCmd04DoesNotBlockNeighbours() {
        val r = FrameReassembler()
        val unknown = DecodeResult.Frame(ScaleFrame.Unknown(1, 4, "0100000000"))
        assertEquals(listOf(weight, unknown, battery), r.feed("${weightHex}A55A0104000501000000000000A55A0105000203640000".hex()))
        assertEquals(0, r.pendingByteCount)
    }
}
