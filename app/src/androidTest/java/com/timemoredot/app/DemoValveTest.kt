package com.timemoredot.app

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** ui-design 4.11: 밸브 칸 상태 글자는 2:00 전 "닫힘", 그 뒤 "열림"(자동 시작 뒤 약 120 s). */
class DemoValveTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Before fun setUp() = rule.startFresh()
    @After fun tearDown() = resetDemoState()

    @Test fun valveClosedBefore115sOpenWithin130s() {
        rule.connectDemo(HARIO)
        rule.onNodeWithText("레시피").performClick()
        rule.awaitSeen(5_000) { it.startsWith("하리오 스위치 기본") }
        rule.onAllNodes(hasContentDescription("이 레시피로 추출") and hasAnyAncestor(hasContentDescription("하리오 스위치 기본", substring = true)))[0].performClick()
        rule.tare()
        rule.waitUntil(timeoutMillis = 15_000) { rule.isRunning() && rule.valveState() != null }
        val t0 = SystemClock.elapsedRealtime()
        rule.waitUntil(timeoutMillis = 130_000) {
            val s = rule.valveState()
            val elapsed = SystemClock.elapsedRealtime() - t0
            if (elapsed < 115_000) assertEquals("115 s 전 밸브 글자", "닫힘", s)
            s == "열림"
        }
        assertTrue(SystemClock.elapsedRealtime() - t0 >= 115_000)
    }
}
