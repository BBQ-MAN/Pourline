package com.timemoredot.app.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusAnnouncerTest {
    @get:Rule val rule = createComposeRule()

    private fun node(mode: LiveRegionMode): SemanticsNodeInteraction = rule.onNode(
        SemanticsMatcher("live $mode") { it.config.getOrNull(SemanticsProperties.LiveRegion) == mode }, useUnmergedTree = true,
    )
    private fun text(mode: LiveRegionMode) =
        node(mode).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private lateinit var announcer: Announcer
    private val seen = mutableMapOf(LiveRegionMode.Polite to mutableListOf<String>(), LiveRegionMode.Assertive to mutableListOf<String>())

    private fun start() {
        rule.mainClock.autoAdvance = false
        rule.setContent { StatusAnnouncer { announcer = LocalAnnouncer.current } }
        rule.mainClock.advanceTimeByFrame()
    }
    /** 16 ms씩 전진하며 두 노드 문구 변화를 기록한다. */
    private fun advance(ms: Long) {
        var t = 0L
        while (t < ms) {
            rule.mainClock.advanceTimeBy(16); t += 16
            for (m in seen.keys) text(m).let { if (seen.getValue(m).lastOrNull() != it) seen.getValue(m) += it }
        }
    }

    @Test fun `같은 문구도 비웠다가 다시 설정된다`() {
        start()
        announcer.announce("영점 맞춤", false); advance(500)
        advance(500)
        announcer.announce("영점 맞춤", false); advance(500)
        assertEquals(listOf("영점 맞춤", "", "영점 맞춤"), seen.getValue(LiveRegionMode.Polite).dropWhile { it.isEmpty() }.take(3))
    }

    @Test fun `Polite 둘은 이어 붙여 한 번`() {
        start()
        announcer.announce("A", false); announcer.announce("B", false); advance(600)
        assertEquals(listOf("A. B"), seen.getValue(LiveRegionMode.Polite).filter { it.isNotEmpty() })
    }

    @Test fun `Assertive가 대기 중 Polite를 버린다`() {
        start()
        announcer.announce("A", false); announcer.announce("C", true); advance(600)
        assertEquals(listOf("C"), seen.getValue(LiveRegionMode.Assertive).filter { it.isNotEmpty() })
        assertTrue(seen.getValue(LiveRegionMode.Polite).none { it == "A" })
    }

    @Test fun `5초 요약 Polite 뒤 300 ms 안 밸브 알림은 Assertive로 한 번, 요약은 버린다(8-3 테스트 6)`() {
        start()
        val (valve, assertive) = com.timemoredot.app.ui.brew.announcementText(com.timemoredot.app.ui.brew.BrewAnnouncement.ValveOpen, com.timemoredot.app.ui.connect.ConnectionUi.NoDevice)
        announcer.announce("무게 120.0 그램, 시간 2분 0초", false); advance(100)
        announcer.announce(valve, assertive); advance(600)
        assertEquals(listOf("스위치를 여세요"), seen.getValue(LiveRegionMode.Assertive).filter { it.isNotEmpty() })
        assertTrue(seen.getValue(LiveRegionMode.Polite).none { it.startsWith("무게") })
    }

    @Test fun `Assertive가 Polite를 버리면 지난 Polite 문구도 노드에서 사라진다`() {
        start()
        announcer.announce("A", false); advance(500)
        advance(500)
        announcer.announce("B", false); advance(100)
        announcer.announce("C", true); advance(2500)
        assertEquals("", text(LiveRegionMode.Polite))
        assertEquals(listOf("C"), seen.getValue(LiveRegionMode.Assertive).filter { it.isNotEmpty() })
    }

    @Test fun `프레임 대기 중 들어온 문구도 잃지 않고 조기 삭제되지 않는다`() {
        start()
        announcer.announce("A", false)
        rule.mainClock.advanceTimeBy(300)   // 지연 끝, 프레임 대기 구간
        announcer.announce("B", false)
        advance(2500)
        assertEquals(listOf("A. B"), seen.getValue(LiveRegionMode.Polite).filter { it.isNotEmpty() })
    }

    @Test fun `두 노드는 1dp이고 접근성 트리에 있다`() {
        start()
        val px = rule.density.run { 1.dp.roundToPx() }
        for (m in seen.keys) {
            val n = node(m).assertExists().fetchSemanticsNode()
            assertEquals(px, n.size.width); assertEquals(px, n.size.height)
        }
    }
}
