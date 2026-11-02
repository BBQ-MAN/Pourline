package com.timemoredot.app.ui

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ui-design 8.5 "알림 노드가 순서 맨 끝, 한 번만" / 문구가 빈 동안에는 낭독 노드 목록에 없다. 진짜 StatusAnnouncer를 쓴다. */
@RunWith(RobolectricTestRunner::class)
class StatusAnnouncerTraversalTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var announcer: Announcer
    private lateinit var view: View

    private fun start() {
        // 시스템 매니저도 켜야 Compose가 보내는 접근성 이벤트가 거부되지 않는다
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)).setEnabled(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            StatusAnnouncer {
                announcer = LocalAnnouncer.current
                view = LocalView.current
                view.javaClass.getMethod("forceAccessibilityForTesting", Boolean::class.java).invoke(view, true)   // Compose는 접근성이 켜졌을 때만 순회 값을 계산한다
                Screen()
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Composable private fun Screen() {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("제목")
            Button(onClick = {}) { Text("하나") }
            Button(onClick = {}) { Text("둘") }
        }
    }

    private fun ids(n: SemanticsNode): List<Int> = listOf(n.id) + n.children.flatMap { ids(it) }

    /** traversalBefore/After 사슬을 따라 읽은 낭독 노드(글자·설명이 있는 노드)의 문구 목록. */
    private fun spoken(): List<String> = chain().map { (it.text ?: it.contentDescription)?.toString().orEmpty() }.filter { it.isNotEmpty() }

    /** 사슬을 따라 읽은 노드 전체(걸러내기 전). */
    private fun chain(): List<AccessibilityNodeInfo> {
        val provider = view.accessibilityNodeProvider
        provider.createAccessibilityNodeInfo(-1)   // 호스트 뷰 노드를 먼저 만들어야 순회 값이 계산된다
        rule.mainClock.advanceTimeBy(100)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val infos = ids(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()).distinct().mapNotNull { id -> provider.createAccessibilityNodeInfo(id)?.let { id to it } }.toMap()
        // Compose가 순회 값을 노드 extras에 시맨틱 id로 함께 넣어 준다(테스트용 키)
        val key = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSAL"
        val next = mutableMapOf<Int, Int>()
        for ((id, i) in infos) {
            i.extras.getInt(key + "BEFORE_VAL", -1).let { if (it != -1) next[id] = it }
            i.extras.getInt(key + "AFTER_VAL", -1).let { if (it != -1) next[it] = id }
        }
        val start = next.keys.first { it !in next.values }
        val order = generateSequence(start) { next[it] }.take(next.size + 1).toList()
        return order.mapNotNull { infos[it] }
    }

    @Test fun `알림 노드는 맨 끝에 한 번만 있고 첫 노드는 제목이다`() {
        start()
        announcer.announce("영점 맞춤", false)
        rule.mainClock.advanceTimeBy(300 + 16 * 3)   // 창이 닫혀 문구가 노드에 게시된다
        val list = spoken()
        assertEquals("제목", list.first())
        assertEquals("영점 맞춤", list.last())
        assertEquals(1, list.count { it == "영점 맞춤" })
    }

    @Test fun `문구가 비어 있으면 낭독 노드 목록에 알림 노드가 없다`() {
        start()
        val list = spoken()
        assertEquals("제목", list.first())
        assertTrue(list.all { it in listOf("제목", "하나", "둘") })
        // 걸러내기 전 사슬: 알림 노드(liveRegion)가 없어야 한다. contentDescription을 조건 없이 넣는 회귀를 잡는다
        val raw = chain()
        assertTrue(raw.none { it.liveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE })
        assertTrue(raw.none { it.text == null && it.contentDescription != null })
    }
}
