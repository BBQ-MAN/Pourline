package com.timemoredot.protocol

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * protocol-vectors.json 벡터 하나 = 테스트 케이스 하나.
 * unconfirmed 벡터는 건너뛰지 않고 이름 접두어 [unconfirmed]로 돌린다. 실패해도 빌드를 막지 않고 stderr에 남긴다(scale-protocol 「벡터 형식」).
 */
@RunWith(Parameterized::class)
class ProtocolVectorsTest(@Suppress("unused") private val name: String, private val v: JsonObject) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun vectors(): List<Array<Any>> = Vectors.all.map { v ->
            val prefix = if (v.str("status") == "unconfirmed") "[unconfirmed] " else ""
            arrayOf("$prefix${v.str("id")}", v)
        }
    }

    @Test
    fun vector() {
        try {
            when (v.str("direction")) {
                "rx" -> checkRx()
                "tx" -> checkTx()
                else -> error("direction ${v.str("direction")}")
            }
        } catch (e: AssertionError) {
            if (v.str("status") != "unconfirmed") throw e
            System.err.println("[unconfirmed] FAIL ${v.str("id")}: ${e.message}")
        }
    }

    private fun checkRx() {
        val r = FrameReassembler()
        // 배열 hex는 조각마다 feed 한 번, 문자열 hex는 feed 한 번
        val results = v.hexChunks().flatMap { r.feed(it.hex()) }
        assertEquals(v.str("id"), v.expectedResults(), results)
        assertEquals("${v.str("id")} 남은 바이트", 0, r.pendingByteCount)
    }

    private fun checkTx() {
        assertEquals(v.str("id"), v.expectedEncode(), FrameCodec.encode(v.command()))
    }
}
