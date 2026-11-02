package com.timemoredot.app.ui.connect

import com.timemoredot.protocol.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNamesTest {
    private fun c(addr: String, name: String?) = ScanCandidate(addr, name, -60, 0)
    private val a = c("C8:2B:96:DD:A1:B2", "TIMEMORE_Dot")
    private val b = c("C8:2B:96:DD:C3:D4", "TIMEMORE_Dot")

    @Test fun `같은 이름은 주소 끝 꼬리`() {
        val m = displayNames(listOf(a, b))
        assertEquals("TIMEMORE_Dot · A1:B2", m[a.address])
        assertEquals("TIMEMORE_Dot · C3:D4", m[b.address])
    }

    @Test fun `이름이 다르면 꼬리 없음, null은 대체 이름, 하나 남으면 꼬리 사라짐`() {
        val x = c("C8:2B:96:DD:E5:F6", "Other"); val n = c("C8:2B:96:DD:07:08", null)
        val m = displayNames(listOf(a, x, n))
        assertEquals("TIMEMORE_Dot", m[a.address]); assertEquals("Other", m[x.address])
        assertEquals("TIMEMORE Dot · 07:08", m[n.address])
        assertEquals("TIMEMORE_Dot", displayNames(listOf(b))[b.address])
    }

    @Test fun `행 글자와 낭독은 같은 이름을 쓴다`() {
        assertEquals("TIMEMORE_Dot · A1:B2", listName(a, listOf(a, b)))
        assertEquals("TIMEMORE_Dot, 주소 끝 A1 B2", spokenListName(a, listOf(a, b)))
    }
}
