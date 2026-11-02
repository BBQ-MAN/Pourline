package com.timemoredot.app.ui.brew

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.theme.TimemoreTheme
import com.timemoredot.brew.StaleReason
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** ui-design 8.5 읽기 순서: AndroidComposeView의 traversalBefore/After 사슬을 따라 읽은 낭독 노드 순서. */
abstract class TraversalBase {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View

    protected fun show(s: Scene, fontScale: Float = 1f) {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)).setEnabled(true)
        rule.setContent {
            view = LocalView.current
            view.javaClass.getMethod("forceAccessibilityForTesting", Boolean::class.java).invoke(view, true)
            val d = LocalDensity.current
            CompositionLocalProvider(LocalAnnouncer provides Announcer { _, _ -> }, LocalDensity provides Density(d.density, fontScale)) {
                TimemoreTheme(darkTheme = false) { BrewContent(s.ui, s.readout, s.samples, s.clock, emptyList(), NoBrewCallbacks) }
            }
        }
        rule.waitForIdle()
    }

    private fun ids(n: SemanticsNode): List<Int> = listOf(n.id) + n.children.flatMap { ids(it) }

    protected fun spoken(): List<String> {
        val provider = view.accessibilityNodeProvider
        provider.createAccessibilityNodeInfo(-1)
        rule.waitForIdle()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val infos: Map<Int, AccessibilityNodeInfo> = ids(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()).distinct()
            .mapNotNull { id -> provider.createAccessibilityNodeInfo(id)?.let { id to it } }.toMap()
        val key = "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSAL"
        val next = mutableMapOf<Int, Int>()
        for ((id, i) in infos) {
            i.extras.getInt(key + "BEFORE_VAL", -1).let { if (it != -1) next[id] = it }
            i.extras.getInt(key + "AFTER_VAL", -1).let { if (it != -1) next[it] = id }
        }
        val start = next.keys.first { it !in next.values }
        // 버튼·칩 같은 병합 노드는 정보 객체에 글자가 없고 자식에 있다: 병합 트리의 글자를 쓴다
        val merged = mutableMapOf<Int, String>()
        fun walk(n: SemanticsNode) {
            val c = n.config
            val text = c.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } + c.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
            merged[n.id] = text.joinToString(", ")
            n.children.forEach(::walk)
        }
        walk(rule.onRoot().fetchSemanticsNode())
        return generateSequence(start) { next[it] }.take(next.size + 1).toList()
            .map { id -> merged[id]?.takeIf { it.isNotEmpty() } ?: infos[id]?.let { (it.text ?: it.contentDescription)?.toString() }.orEmpty() }
            .filter { it.isNotEmpty() }
    }

    /** 앵커들이 이 순서로 나오는지(각 앵커 = 그 글자를 포함하는 첫 노드). */
    protected fun assertOrder(list: List<String>, vararg anchors: String) {
        var last = -1
        for (a in anchors) {
            val i = (last + 1 until list.size).firstOrNull { list[it].contains(a) } ?: -1   // 앞 앵커 뒤에서 찾는다
            assertTrue("'$a' 없음 또는 순서 틀림: $list", i > last)
            last = i
        }
    }

    protected fun traversalIndexNodes(n: Int) =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TraversalIndex), useUnmergedTree = true).assertCountEquals(n)
}

private val rec = ConnectionUi.Reconnecting(3, 64_000_000_000L)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class TraversalOrderTest : TraversalBase() {
    @Test fun `세로 Idle 위 배너`() {
        show(Scenes.idle(connection = ConnectionUi.Connected(Fx.device.copy(modelNumber = "X")), banner = BrewBanner.ModelMismatch))
        assertOrder(spoken(), "V60 기본", "연결됨", "배터리 82 퍼센트", "TIMEMORE Dot(TES017)", "무게 0.0", "시간", "유량", "영점을 맞추고", "영점", "시작")
        traversalIndexNodes(0)
    }

    @Test fun `세로 Running과 아래 배너`() {
        show(Scenes.running(connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 55.0))
        assertOrder(spoken(), "V60 기본", "재연결 중", "멈춘 값", "시간", "유량", "무게 그래프", "3/3 단계", "다시 연결하는 중", "지금 다시 시도", "일시정지", "완료")
        traversalIndexNodes(0)
    }

    @Test fun `세로 Running 밸브 칸은 무게 다음, 타이머 앞`() {
        show(Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 }))
        assertOrder(spoken(), "연결됨", "무게", "레시피 안내", "시간", "유량")
        traversalIndexNodes(1)   // 밸브 칸 하나뿐
    }

    @Test fun `글꼴 2배 밸브 띠도 같은 순서`() {
        show(Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 }), fontScale = 2f)
        assertOrder(spoken(), "무게", "레시피 안내", "시간", "유량")
        traversalIndexNodes(1)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h480dp-xhdpi")   // 왼쪽 칸 지표까지 화면 안에 들게(화면 밖 노드는 순회에서 빠진다)
class TraversalOrderLandscapeTest : TraversalBase() {
    @Test fun `가로 Running과 아래 배너`() {
        show(Scenes.running(connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 55.0))
        assertOrder(spoken(), "V60 기본", "재연결 중", "멈춘 값", "시간", "유량", "다시 연결하는 중", "일시정지", "완료", "무게 그래프", "3/3 단계")
        traversalIndexNodes(0)
    }
}
