package com.timemoredot.protocol

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** protocol.md 2.2: 수신 CRC 검사 안 함(F-8), 송신 CRC 00 00(F-9), CRC 자리 생략 금지(F-12). */
class CrcRuleMatchesVectorsTest {

    @Test
    fun crcBlockMatchesTxCrc() {
        assertFalse(Vectors.crc.getValue("rxCheck").jsonPrimitive.boolean)
        assertEquals(Vectors.crc.str("txValue").removePrefix("0x").toInt(16), TX_CRC)
        assertEquals(0x0000, TX_CRC)
    }

    @Test
    fun rxCrcIsNotChecked() {
        val bad = Vectors.byId("rx-crc-bad-001").hexChunks().single()
        assertTrue(bad.endsWith("4106"))
        val expected = Vectors.byId("rx-crc-bad-001").expectedResults()
        assertTrue(expected.single().let { it is DecodeResult.Frame && it.frame is ScaleFrame.WeightReport })
        for (crc in listOf("4106", "0000", "FFFF")) {
            val frame = bad.dropLast(4) + crc
            assertEquals(crc, expected, FrameReassembler().feed(frame.hex()))
        }
    }

    @Test
    fun everyTxFrameEndsWithZeroCrc() {
        val tx = Vectors.all.filter { it.str("direction") == "tx" && it.hexChunks().isNotEmpty() }
        assertTrue(tx.isNotEmpty())
        for (v in tx) {
            val bytes = (FrameCodec.encode(v.command()) as EncodeResult.Ok).bytes
            assertTrue(v.str("id"), bytes.size >= 8)
            assertEquals(v.str("id"), "0000", bytes.copyOfRange(bytes.size - 2, bytes.size).hex())
        }
    }
}
