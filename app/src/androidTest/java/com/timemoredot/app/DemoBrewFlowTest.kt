package com.timemoredot.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private val sessionRow = Regex("""\d+월 \d+일 \d+시 \d+분, .*비율.*""")

fun androidx.compose.ui.test.junit4.ComposeTestRule.sessionRows() =
    onAllNodes(SemanticsMatcher("sessionRow") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { sessionRow.matches(it) } == true }, useUnmergedTree = true)
        .fetchSemanticsNodes().size

/** S2 연결 → S7 추출 → S1 영점·자동 시작·단계 → 완료 → S6 저장 → S9 행 +1. 이 흐름을 V60로 한 번 돌리고 저장 전후 행 수를 돌려준다. */
fun androidx.compose.ui.test.junit4.ComposeTestRule.runV60AndSave(): Pair<Int, Int> {
    connectDemo(V60)
    onNodeWithText("기록").performClick()
    awaitText("기록", 5_000)
    waitUntil(timeoutMillis = 5_000) { seenText("추출 기록이 없습니다") || sessionRows() > 0 }
    val before = sessionRows()
    onNodeWithText("레시피").performClick()
    awaitSeen(5_000) { it.startsWith("V60 기본") }
    onAllNodes(hasContentDescription("이 레시피로 추출") and hasAnyAncestor(hasContentDescription("V60 기본", substring = true)))[0].performClick()
    tare(2_000)
    awaitText("1/3 단계", 5_000)
    awaitText("3/3 단계", 15_000)
    check(isRunning() && timerNonZero())
    onNodeWithText("완료").performClick()
    awaitText("추출 요약", 5_000)
    onNodeWithText("저장").performClick()
    waitUntil(timeoutMillis = 10_000) { !seenText("추출 요약") }
    onNodeWithText("기록").performClick()
    waitUntil(timeoutMillis = 10_000) { sessionRows() > before }
    return before to sessionRows()
}

class DemoBrewFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Before fun setUp() = rule.startFresh()
    @After fun tearDown() = resetDemoState()

    @Test fun v60FlowSavesRow() {
        val (before, after) = rule.runV60AndSave()
        assertEquals(before + 1, after)
    }
}
