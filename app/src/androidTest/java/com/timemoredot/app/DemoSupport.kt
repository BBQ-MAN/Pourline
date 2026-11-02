package com.timemoredot.app

import android.Manifest
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.timemoredot.app.ui.brew.VALVE_STATE_TAG

const val PKG = "com.timemoredot.app"
const val V60 = "데모 저울 · V60"
const val HARIO = "데모 저울 · 하리오 스위치"
const val RECONNECT = "데모 저울 · 끊김·재연결"
const val OVERLOAD = "데모 저울 · 과부하"
const val BT_OFF = "데모 저울 · 블루투스 꺼짐"
const val FAIL = "데모 저울 · 연결 실패"

private val inst get() = InstrumentationRegistry.getInstrumentation()
val container get() = ApplicationProvider.getApplicationContext<TimemoreApp>().container

/** 앱 프로세스는 테스트 사이에 이어지므로 저울 연결과 추출 상태를 처음으로 돌린다. */
fun resetDemoState() {
    inst.runOnMainSync {
        container.brewController.cancelWaiting()
        container.brewController.discard()
        container.brewController.newBrew()
        container.brewController.selectRecipe(null)
        container.scale.disconnect()
    }
}

/** DemoLauncher 별칭으로 시작한다(architecture.md 10.1.6). 권한은 시작 전에 UiAutomation으로 준다. */
fun launchDemo() {
    val ua = inst.uiAutomation
    listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).forEach { ua.grantRuntimePermission(PKG, it) }
    inst.targetContext.startActivity(
        Intent().setClassName(PKG, "$PKG.DemoLauncher").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
}

/** 병합 전 트리의 글자·내용 설명·상태 설명 중 하나라도 조건을 만족하는 노드가 있는가. */
fun ComposeTestRule.seen(pred: (String) -> Boolean): Boolean =
    onAllNodes(
        SemanticsMatcher("seen") { n ->
            val c = n.config
            (c.getOrNull(SemanticsProperties.Text)?.any { pred(it.text) } == true) ||
                (c.getOrNull(SemanticsProperties.ContentDescription)?.any(pred) == true) ||
                (c.getOrNull(SemanticsProperties.StateDescription)?.let(pred) == true)
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

fun ComposeTestRule.seenText(s: String, substring: Boolean = false) = seen { if (substring) it.contains(s) else it == s }

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitText(s: String, ms: Long, substring: Boolean = false) = waitUntil(timeoutMillis = ms) { seenText(s, substring) }

@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.awaitSeen(ms: Long, pred: (String) -> Boolean) = waitUntil(timeoutMillis = ms) { seen(pred) }

/** 추출 중이면 영점 버튼 상태 설명이 "추출 중에는 영점을 맞출 수 없음"이다(BrewParts). */
fun ComposeTestRule.isRunning() = seenText("추출 중에는 영점을 맞출 수 없음")

/** 타이머 낭독 "시간 N분 M초"가 0이 아닌가. */
fun ComposeTestRule.timerNonZero() = seen { it.startsWith("시간 ") && !it.startsWith("시간 0분 0초") }

fun ComposeTestRule.valveState(): String? = runCatching {
    onAllNodes(hasTestTag(VALVE_STATE_TAG), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
}.getOrNull()

/** S1 비연결 화면에서 S2를 열고 데모 후보를 눌러 연결 칩이 "연결됨"이 될 때까지 기다린다(10 s). */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.openFinderAndPick(name: String) {
    waitUntil(timeoutMillis = 10_000) { seenText("저울 찾기") || seenText("다른 저울 찾기") }
    onAllNodesWithText(if (seenText("저울 찾기")) "저울 찾기" else "다른 저울 찾기")[0].performClick()
    waitUntil(timeoutMillis = 10_000) { seen { it == name } }
    onAllNodes(hasText("$name, ", substring = true).or(hasContentDescription(name)))[0].performClick()
}

fun ComposeTestRule.connectDemo(name: String) {
    openFinderAndPick(name)
    awaitText("연결됨", 10_000)
}

/** 영점 버튼이 켜질 때까지(연결 직후엔 "연결 후 사용할 수 있음") 기다려 누르고, zeroMs 안에 무게 0.0을 확인한다. */
fun ComposeTestRule.tare(zeroMs: Long = 8_000) {
    waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("영점") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    onNode(hasText("영점") and isEnabled()).performClick()
    awaitText("무게 0.0 그램", zeroMs)
}

/** 데모 모드로 연 뒤(컨테이너가 데모로 만들어진 다음) 앞 테스트가 남긴 연결·추출을 지우고 비연결 S1에서 시작한다. */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.startFresh() {
    launchDemo()
    waitUntil(timeoutMillis = 15_000) { seenText("추출 설정") }
    resetDemoState()
    waitUntil(timeoutMillis = 10_000) { seenText("연결 안 됨") }
}
