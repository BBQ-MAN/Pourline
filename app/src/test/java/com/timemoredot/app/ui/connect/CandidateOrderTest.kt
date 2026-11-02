package com.timemoredot.app.ui.connect

import com.timemoredot.protocol.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateOrderTest {
    private fun c(addr: String, rssi: Int) = ScanCandidate(addr, null, rssi, 0)
    private fun List<ScanCandidate>.addrs() = map { it.address }

    @Test fun `resort=false 동안 RSSI가 바뀌어도 순서가 그대로다`() {
        val first = orderCandidates(emptyList(), listOf(c("A", -80), c("B", -50)), null, false)
        assertEquals(listOf("A", "B"), first.addrs())
        val later = orderCandidates(first.addrs(), listOf(c("A", -40), c("B", -90)), null, false)
        assertEquals(listOf("A", "B"), later.addrs())
        assertEquals(-40, later[0].rssi)   // 값은 최신
    }

    @Test fun `새로 나타난 최근 연결 기기는 맨 위, 그 밖의 새 항목은 끝`() {
        val prev = listOf("A", "B")
        val out = orderCandidates(prev, listOf(c("A", -50), c("B", -60), c("N", -30), c("R", -90)), recentAddress = "R", resort = false)
        assertEquals(listOf("R", "A", "B", "N"), out.addrs())
    }

    @Test fun `이미 보인 최근 연결 기기는 자리를 지킨다`() {
        val out = orderCandidates(listOf("A", "R"), listOf(c("A", -50), c("R", -60)), "R", false)
        assertEquals(listOf("A", "R"), out.addrs())
    }

    @Test fun `resort=true는 최근 연결 기기 다음을 RSSI 내림차순으로`() {
        val out = orderCandidates(listOf("A", "B", "C", "R"), listOf(c("A", -80), c("B", -50), c("C", -60), c("R", -95)), "R", true)
        assertEquals(listOf("R", "B", "C", "A"), out.addrs())
    }

    @Test fun `사라진 항목은 빠지고 첫 목록은 들어온 순서`() {
        assertEquals(listOf("B"), orderCandidates(listOf("A", "B"), listOf(c("B", -50)), null, false).addrs())
    }
}
