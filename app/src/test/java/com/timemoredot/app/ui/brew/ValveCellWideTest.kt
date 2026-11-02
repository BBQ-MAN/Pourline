package com.timemoredot.app.ui.brew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.theme.TimemoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 4.11 "넓은 창 칸 안"(2026-11-02 P1): 높이가 정해진 왼쪽 칸에서만 아이콘·상태 글자 한 줄. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-xhdpi")
class ValveCellWideTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var scene by mutableStateOf(Scenes.running())
    private var fontScale by mutableStateOf(1f)

    private fun show(s: Scene, font: Float = 1f) {
        scene = s; fontScale = font
        if (!started) {
            started = true
            rule.setContent {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalAnnouncer provides Announcer { _, _ -> }) {
                    TimemoreTheme(darkTheme = false) { BrewContent(scene.ui, scene.readout, scene.samples, scene.clock, emptyList(), NoBrewCallbacks, kotlinx.coroutines.flow.emptyFlow()) }
                }
            }
        }
        rule.waitForIdle()
    }
    private var started = false

    private val valve = { Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 }) }
    private fun bounds(m: SemanticsMatcher): DpRect = rule.onNode(m, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun tag(t: String) = bounds(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, t))
    private fun value(prefix: String) = bounds(SemanticsMatcher("contentDescription starts with $prefix") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.firstOrNull()?.startsWith(prefix) == true
    })
    private fun spOf(prefix: String): Int = rule.onNode(hasContentDescription(prefix, substring = true) and SemanticsMatcher.keyIsDefined(FontSpKey), useUnmergedTree = true)
        .fetchSemanticsNode().config[FontSpKey]
    private fun cell() = bounds(hasContentDescription("레시피 안내, 스위치", substring = true))

    @Test fun `첫 화면에 타이머·유량 값이 동작 줄 위에 들고 칸이 없을 때와 같은 자리다`() {
        show(Scenes.running())
        val timer0 = value("시간").bottom.value
        val w0 = spOf("무게")
        show(valve())
        val actionsTop = tag(ACTIONS_TAG).top.value
        val visibleBottom = tag(LEFT_SCROLL_TAG).bottom.value
        val timer = value("시간").bottom.value
        val flow = value("유량").bottom.value
        println("WIDE P1 timer bottom no-valve $timer0 / valve $timer, flow $flow, actions top $actionsTop, visible bottom $visibleBottom")
        for (b in listOf(timer, flow)) {
            assertTrue("값 아래 끝 $b > 동작 줄 위 $actionsTop", b <= actionsTop)
            assertTrue("값 아래 끝 $b > 보이는 끝 $visibleBottom", b <= visibleBottom)
        }
        assertEquals(timer0, timer, 1f)
        assertEquals("s′ = s₀", w0, spOf("무게"))
    }

    @Test fun `글꼴 1·0 칸 안은 아이콘 20 dp + 2 dp + 상태 글자 한 줄, 칸 높이 = max(칸 내용 76, 숫자 줄 + 24)`() {
        show(valve())
        val icon = tag(VALVE_ICON_TAG)
        val state = tag(VALVE_STATE_TAG)
        val c = cell()
        val weight = bounds(hasContentDescription("무게", substring = true) and SemanticsMatcher.keyIsDefined(FontSpKey))   // 무게 노드 = 숫자 줄 + 아래 줄 24 dp
        println("WIDE P1 icon $icon state $state cell $c weight $weight")
        assertEquals(20f, (icon.right - icon.left).value, 0.5f)
        assertEquals(20f, (icon.bottom - icon.top).value, 0.5f)
        assertEquals((icon.top + icon.bottom).value / 2, (state.top + state.bottom).value / 2, 1f)
        assertEquals(2f, (state.left - icon.right).value, 0.5f)
        assertEquals(24f, (state.bottom - state.top).value, 0.5f)   // 한 줄 높이 max(20, 24)
        // 기기 글꼴 52 sp 숫자 줄 56.3 dp면 80.3 dp. Robolectric 글꼴은 같은 52 sp 숫자 줄이 60.5 dp라 칸도 그만큼 높다
        val numberRow = (weight.bottom - weight.top).value
        assertEquals("칸 높이 ${c.bottom - c.top}", maxOf(76f, numberRow), (c.bottom - c.top).value, 1f)
    }

    @Test fun `글꼴 2·0이면 한 줄이 아니다(아이콘 24 dp)`() {
        show(valve(), 2f)
        assertEquals(24f, (tag(VALVE_ICON_TAG).right - tag(VALVE_ICON_TAG).left).value, 0.5f)
        show(valve(), 1f)
        assertEquals(20f, (tag(VALVE_ICON_TAG).right - tag(VALVE_ICON_TAG).left).value, 0.5f)
    }

    @Test fun `한 줄 판정은 20 + 2 + 상태 글자 폭 ≤ 56 dp`() {
        // ui-design 4.11 기기 글꼴 값: 글꼴 1.0 "닫힘" 32.3 dp, 1.15 37.1 dp, 2.0 64.6 dp
        assertTrue(valveRowFits(32.3f))
        assertTrue(!valveRowFits(37.1f))
        assertTrue(!valveRowFits(64.6f))
        assertTrue(valveRowFits(34f))      // 경계: 56 dp 그대로는 한 줄
        assertTrue(!valveRowFits(34.1f))
    }
}

/** 세로 배치(3.1)는 글꼴 1.0이어도 세로 쌓기 그대로다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ValveCellPortraitTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `세로는 글꼴 1·0에서도 세로 쌓기`() {
        val s = Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })
        rule.setContent {
            CompositionLocalProvider(LocalAnnouncer provides Announcer { _, _ -> }) {
                TimemoreTheme(darkTheme = false) { BrewContent(s.ui, s.readout, s.samples, s.clock, emptyList(), NoBrewCallbacks, kotlinx.coroutines.flow.emptyFlow()) }
            }
        }
        rule.waitForIdle()
        fun tag(t: String) = rule.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TestTag, t), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val icon = tag(VALVE_ICON_TAG)
        assertEquals(24f, (icon.right - icon.left).value, 0.5f)
        assertTrue(icon.bottom <= tag(VALVE_STATE_TAG).top)
    }
}
