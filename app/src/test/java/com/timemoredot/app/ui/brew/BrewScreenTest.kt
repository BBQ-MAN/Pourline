package com.timemoredot.app.ui.brew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.VectorConverter
import androidx.test.espresso.Espresso
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.theme.TimemoreTheme
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.TimerSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S1 화면 동작(Compose UI 테스트, Robolectric). 기록용 가짜 Announcer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class BrewScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val announced = mutableListOf<String>()
    private val recorder = Announcer { text, _ -> announced += text }

    @Composable private fun Host(s: Scene, events: MutableSharedFlow<BrewUiEvent> = MutableSharedFlow(extraBufferCapacity = 8)) {
        CompositionLocalProvider(LocalAnnouncer provides recorder) {
            TimemoreTheme(darkTheme = false) { BrewContent(s.ui, s.readout, s.samples, s.clock, emptyList(), NoBrewCallbacks, events) }
        }
    }

    private fun morphStarts(): Int = rule.onNode(SemanticsMatcher.keyIsDefined(MorphStartsKey), useUnmergedTree = true).fetchSemanticsNode().config[MorphStartsKey]

    @Test fun `Announce(TareDone)이면 알림 한 번`() {
        val events = MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 8)
        rule.setContent { Host(Scenes.idle(), events) }
        rule.waitForIdle()
        events.tryEmit(BrewUiEvent.Announce(BrewAnnouncement.TareDone))
        rule.waitForIdle()
        assertEquals(listOf("영점 맞춤"), announced.filter { it == "영점 맞춤" })
    }

    @Test fun `영점 모션은 계기 없이 다시 재생하지 않는다(빈 상태 뒤 다시 연결)`() {
        rule.mainClock.autoAdvance = false
        val events = MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 8)
        var scene by mutableStateOf(Scenes.idle())
        rule.setContent { Host(scene, events) }
        rule.mainClock.advanceTimeByFrame()
        fun tareMotion() = rule.onNode(SemanticsMatcher.keyIsDefined(TareMotionKey), useUnmergedTree = true).fetchSemanticsNode().config[TareMotionKey]
        events.tryEmit(BrewUiEvent.Announce(BrewAnnouncement.TareDone))
        rule.mainClock.advanceTimeBy(32)
        assertTrue(tareMotion())
        rule.mainClock.advanceTimeBy(3_000)
        assertEquals(false, tareMotion())
        scene = Scenes.idle(connection = com.timemoredot.app.ui.connect.ConnectionUi.Disconnected)   // 빈 상태: 모양 자리가 컴포지션을 떠난다
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag(SHAPE_TAG).assertDoesNotExist()
        scene = Scenes.idle()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(false, tareMotion())
    }

    @Test @Config(qualifiers = "w800dp-h360dp-xhdpi") fun `가로 Running 밸브 레시피는 밸브 칸이 의미 트리에 있다`() {
        rule.setContent { Host(Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })) }
        rule.waitForIdle()
        rule.onNode(hasContentDescription("레시피 안내, 스위치", substring = true), useUnmergedTree = true).assertExists()
        val cell = rule.onNode(hasContentDescription("레시피 안내, 스위치", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("밸브 칸 높이 ${cell.bottom - cell.top}", cell.bottom - cell.top >= 48.dp)
    }

    @Test fun `Idle에서 Running이면 전환 시작, 되돌아가면 전환 없음, Running으로 처음 구성하면 재생하지 않음`() {
        var scene by mutableStateOf(Scenes.idle())
        rule.setContent { Host(scene) }
        rule.waitForIdle()
        assertEquals(0, morphStarts())
        scene = Scenes.running(sec = 0.5)
        rule.waitForIdle()
        assertEquals(1, morphStarts())
        scene = Scenes.idle()
        rule.waitForIdle()
        assertEquals(1, morphStarts())
    }

    @Test fun `Running으로 처음 구성·복원하면 전환을 재생하지 않는다`() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(Scenes.running()) }
        rule.waitForIdle()
        assertEquals(0, morphStarts())
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals(0, morphStarts())
    }

    @Test fun `숫자 겹은 상태를 넣은 프레임에 바로 바뀐다`() {
        rule.mainClock.autoAdvance = false
        val base = Scenes.running(phase = BrewPhase.Paused, sec = 60.0, stale = StaleReason.Disconnected)
        var ui by mutableStateOf(base.ui)
        rule.setContent { Host(Scene(ui, base.readout, base.samples, base.clock)) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(hasContentDescription("시간 1분 0초, 멈춤")).assertExists()
        ui = base.ui.copy(timer = TimerSnapshot(75_000_000_000L, null))
        base.readout.value = base.readout.value.copy(reading = Fx.stale(208.3, StaleReason.Disconnected, 60.0))
        rule.mainClock.advanceTimeByFrame()   // 애니메이션 시간 없이 바로 다음 프레임(트윈 없음)
        rule.onNode(hasContentDescription("시간 1분 15초, 멈춤")).assertExists()
        rule.onNode(hasContentDescription("마지막 무게 208.3 그램", substring = true)).assertExists()
    }

    @Test fun `지표 줄 위치는 Idle·WaitingForPour·Running에서 같다(밸브 칸 포함)`() {
        var scene by mutableStateOf(Scenes.idle())
        rule.setContent { Host(scene) }
        fun top(): Float { rule.waitForIdle(); return rule.onNodeWithTag(METRICS_TAG).getUnclippedBoundsInRoot().top.value }
        val idle = top()
        scene = Scenes.idle(phase = BrewPhase.WaitingForPour, banner = BrewBanner.WaitingForPour)
        val waiting = top()
        scene = Scenes.running()
        val running = top()
        scene = Scenes.idle(recipe = Fx.hario)
        val idleValve = top()
        scene = Scenes.running(recipe = Fx.hario, sec = 116.0, weight = { 240.0 })
        val countdown = top()
        for (v in listOf(waiting, running, idleValve, countdown)) assertEquals("waiting/running/valve/countdown = $waiting/$running/$idleValve/$countdown", idle, v, 1f)
        // 절대 y(ui-design 3.1의 267 dp)는 기기 글꼴로 정해지는 무게 글자 크기(84 sp)에 달려 있다. Robolectric 글꼴은 기준 문자열이 좁아
        // 96 sp가 골라지므로 상태 사이 같음만 단언한다: 상단 바 64 + 상태 줄 48 + 8 + 무게 줄(글자 × 104/96) + 24 + 8
        assertTrue("지표 줄 y $idle", idle in 64f + 48f + 8f + 91f + 24f + 8f - 1f..64f + 48f + 8f + 104f + 24f + 8f + 1f)
    }

    @Test fun `배터리 미상이면 배터리 노드 없음, 칩은 연결됨, null 글자 없음`() {
        val s = Scenes.idle()
        rule.setContent { Host(Scene(s.ui.copy(batteryPercent = null), s.readout, s.samples, s.clock)) }
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("배터리", substring = true)).assertCountEquals(0)
        rule.onAllNodesWithText("null", substring = true).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("null", substring = true)).assertCountEquals(0)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "연결됨")).assertExists()
    }

    @Test fun `배터리 82면 배터리 노드 하나, 칩에는 없음`() {
        rule.setContent { Host(Scenes.idle()) }
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("배터리 82 퍼센트")).assertCountEquals(1)
        rule.onAllNodes(hasText("82", substring = true) or hasContentDescription("82", substring = true)).assertCountEquals(1)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "연결됨")).assertExists()
    }

    @Test fun `S3 단위 줄은 readout을 따른다`() {
        val s = Scenes.idle(reading = ReadingUi.UnitUnknown, canStart = false)
        rule.setContent { Host(s) }
        rule.waitForIdle()
        rule.onNodeWithText("연결됨").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("저울 정보").assertExists()
        rule.onAllNodesWithText("확인 중").assertCountEquals(1)
        rule.onAllNodesWithText("그램", substring = true).assertCountEquals(0)
        s.readout.value = s.readout.value.copy(reading = ReadingUi.UnitUnknownTimedOut)
        rule.waitForIdle()
        rule.onNodeWithText("알 수 없음").assertExists()
        s.readout.value = s.readout.value.copy(reading = Fx.live(10.0))
        rule.waitForIdle()
        rule.onNodeWithText("그램(g)").assertExists()
    }

    @Test fun `Running에서 뒤로 가기는 D1, 복원 뒤에도 열려 있다`() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(Scenes.running()) }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("추출을 끝낼까요?").assertExists()
        assertTrue(!rule.activity.isFinishing)
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText("추출을 끝낼까요?").assertExists()
    }

    @Test fun `D1이 열린 채 자동 완료로 Finished가 되면 D1을 닫고 다시 열지 않는다`() {
        var scene by mutableStateOf(Scenes.running())
        rule.setContent { Host(scene) }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("추출을 끝낼까요?").assertExists()
        scene = Scenes.running(phase = BrewPhase.Finished, sec = 95.0)
        rule.waitForIdle()
        rule.onAllNodesWithText("추출을 끝낼까요?").assertCountEquals(0)
        scene = Scenes.running()
        rule.waitForIdle()
        rule.onAllNodesWithText("추출을 끝낼까요?").assertCountEquals(0)
    }

    private fun markerCount(): Int = rule.onNode(SemanticsMatcher.keyIsDefined(MarkerCountKey), useUnmergedTree = true).fetchSemanticsNode().config[MarkerCountKey]

    @Test fun `기다리기 단계는 번호만 차지하고 카드에 나오지 않으며 표식도 없다`() {
        var scene by mutableStateOf(Scenes.running(recipe = Fx.devil, sec = 5.0, weight = { 0.0 }))
        rule.setContent { Host(scene) }
        val seen = mutableListOf<String>()
        fun card(w: Double) {
            scene = Scenes.running(recipe = Fx.devil, sec = 5.0, weight = { w })
            rule.waitForIdle()
            val n = rule.onNode(hasContentDescription("/6 단계", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
            seen += n.substringBefore(",")
            if (n.startsWith("2/6")) assertTrue(n, n.contains("다음: 1:15까지 기다리기"))
        }
        for (w in listOf(0.0, 70.0, 125.0, 280.0)) card(w)
        assertEquals(listOf("1/6 단계", "2/6 단계", "4/6 단계", "6/6 단계"), seen)
        assertEquals(3, markerCount())
        scene = Scenes.running(recipe = Fx.hario, sec = 5.0, weight = { 0.0 })
        rule.waitForIdle()
        rule.onNode(hasContentDescription("1/3 단계", substring = true)).assertExists()
        assertEquals(1, markerCount())
        scene = Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })
        rule.waitForIdle()
        rule.onNode(hasContentDescription("3/3 단계", substring = true)).assertExists()
    }

    @Test fun `V60 기본·6888의 뜸 기다리기 단계는 카드 번호를 건너뛰고 표식이 없다(P2)`() {
        var scene by mutableStateOf(Scenes.running(recipe = Fx.v60, sec = 5.0, weight = { 0.0 }))
        rule.setContent { Host(scene) }
        fun card(r: com.timemoredot.brew.Recipe, n: Int, w: Double): String {
            scene = Scenes.running(recipe = r, sec = 5.0, weight = { w })
            rule.waitForIdle()
            return rule.onNode(hasContentDescription("/$n 단계", substring = true)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
        }
        val v60 = listOf(0.0, 30.0, 58.5, 59.5, 70.0, 200.0).map { w -> card(Fx.v60, 3, w).also { if (w < 59.0) assertTrue(it, it.contains("다음: 0:40까지 기다리기")) } }
        assertEquals(listOf("1/3 단계", "3/3 단계"), v60.map { it.substringBefore(",") }.distinct())
        assertEquals(2, markerCount())
        val r6888 = Fx.recipe("6888 레시피")
        val seen = listOf(0.0, 59.5, 100.0, 150.0, 230.0).map { card(r6888, 5, it).substringBefore(",") }.distinct()
        assertEquals(listOf("1/5 단계", "3/5 단계", "4/5 단계", "5/5 단계"), seen)
        assertEquals(4, markerCount())
    }

    @Test fun `리본 머리는 10 Hz 표본마다 최신 표본을 60 ms 안으로 따라간다`() {
        rule.mainClock.autoAdvance = false
        val samples = com.timemoredot.brew.SampleBuffer()
        val readout = kotlinx.coroutines.flow.MutableStateFlow(BrewReadout(Fx.live(0.0), null, null, 0))
        val head = androidx.compose.animation.core.Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter, androidx.compose.ui.geometry.Offset(1f, 0.01f))
        rule.setContent {
            TimemoreTheme(darkTheme = false) {
                ShapeArea(BrewPhase.Running, Fx.v60, 0, false, readout, samples, com.timemoredot.protocol.WeightUnit.Gram, 0,
                    androidx.compose.ui.Modifier.size(300.dp, 200.dp), head = head)
            }
        }
        rule.mainClock.advanceTimeByFrame()
        for (k in 0 until 20) {
            samples.append(com.timemoredot.brew.BrewSample(k * 100, k * 3f, 30f, 0))
            readout.value = readout.value.copy(sampleCount = samples.size)
            rule.mainClock.advanceTimeBy(100)
        }
        val newest = samples.offsetMs(samples.size - 1).toFloat()
        assertTrue("머리 x ${head.value.x}, 최신 $newest", newest - head.value.x in 0f..60f)
    }

    @Test fun `Idle에서는 뒤로 가기를 가로채지 않는다`() {
        rule.setContent { Host(Scenes.idle()) }
        rule.waitForIdle()
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onAllNodesWithText("추출을 끝낼까요?").assertCountEquals(0)
        assertTrue(rule.activity.isFinishing)
    }

    @Test fun `Finished에서는 뒤로 가기를 가로채지 않는다`() {
        rule.setContent { Host(Scenes.running(phase = BrewPhase.Finished, sec = 95.0)) }
        rule.waitForIdle()
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onAllNodesWithText("추출을 끝낼까요?").assertCountEquals(0)
    }

    @Test fun `시트가 열린 채 Running이면 뒤로 가기는 시트를 먼저 닫는다`() {
        rule.setContent { Host(Scenes.running()) }
        rule.waitForIdle()
        rule.onNodeWithText("연결됨").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("저울 정보").assertExists()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onAllNodesWithText("추출을 끝낼까요?").assertCountEquals(0)
    }

    @Test fun `무게 표시 범위`() {
        val g = com.timemoredot.protocol.WeightUnit.Gram
        val oz = com.timemoredot.protocol.WeightUnit.Ounce
        assertEquals(NO_VALUE, weightShown(Fx.live(10000.0), g).text)
        assertEquals(UnderKind.OutOfRange, weightShown(Fx.live(10000.0), g).under?.kind)
        assertTrue(!weightShown(Fx.live(10000.0), g).stale)
        assertEquals("9999.9", weightShown(Fx.live(9999.9), g).text)
        assertEquals("−9999.9", weightShown(Fx.live(-9999.9), g).text)
        assertEquals(NO_VALUE, weightShown(Fx.live(-10000.0), g).text)
        assertEquals("999.99", weightShown(ReadingUi.Live(999.99 * com.timemoredot.protocol.GRAMS_PER_OUNCE, 0.0, oz), oz).text)
        assertEquals(NO_VALUE, weightShown(ReadingUi.Live(1000.0 * com.timemoredot.protocol.GRAMS_PER_OUNCE, 0.0, oz), oz).text)
    }

    @Test fun `밸브 칸이 있으면 360 dp에서 64 sp, 기준 문자열이 256 dp 안`() {
        // Roboto 어림(ui-design 4.2): 기준 문자열 3.625 em, 단위 12 dp
        assertEquals(64 to false, autoSizeWeight(256f, 3.625f, 12f))
        assertTrue(3.625f * 64 + 8 + 12 <= 256f)
        assertEquals(84 to false, autoSizeWeight(328f, 3.625f, 12f))   // 칸 없음: 84 sp(3.1 좌표)
    }

    @Test fun `자동 축소 2번 - 40 sp 한 줄이 넘치면 단위를 아래 줄로 내리고 숫자만 줄인다`() {
        // 합성 값: 기준 문자열 6.33 dp/sp, 단위 24 dp. 40 sp 한 줄 = 253 + 8 + 24 = 285 dp
        assertEquals(40 to false, autoSizeWeight(288f, 6.33f, 24f))
        assertEquals(40 to true, autoSizeWeight(268f, 6.33f, 24f))    // 가용 268 dp: 단위 아래 줄, 숫자 40 sp(253 ≤ 268)
        assertEquals(36 to true, autoSizeWeight(240f, 6.33f, 24f))
        assertEquals(24 to true, autoSizeWeight(100f, 6.33f, 24f))    // 최소 24 sp
        // 크기마다 잰 폭(비선형 배율): 작은 크기일수록 sp당 폭이 크다. 100 sp 비율로 넘기면 40 sp 폭을 덜 잰다
        val nonLinear = { sp: Int -> sp * (3.7f + (100 - sp) * 0.04f) }
        assertEquals(40 to true, autoSizeWeight(248f, nonLinear, 19.5f))   // 40 sp = 244 dp + 8 + 19.5 > 248
        assertEquals(56 to false, autoSizeWeight(248f, 3.7f, 19.5f))       // 100 sp 비율이면 56 sp 한 줄로 잘못 들어간다
    }

    private fun spOf(prefix: String): Int = rule.onNode(hasContentDescription(prefix, substring = true) and SemanticsMatcher.keyIsDefined(FontSpKey), useUnmergedTree = true)
        .fetchSemanticsNode().config[FontSpKey]

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w800dp-h360dp-xhdpi") fun `가로 지표는 밸브 유무와 상관없이 24 sp, 밸브 칸이 있으면 무게 크기는 칸 없을 때 이하`() {
        var scene by mutableStateOf(Scenes.running(sec = 60.0))
        rule.setContent { Host(scene) }
        rule.waitForIdle()
        val w0 = spOf("무게"); val t0 = spOf("시간")
        fun timerBottom() = rule.onNode(hasContentDescription("시간", substring = true) and SemanticsMatcher.keyIsDefined(FontSpKey), useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value
        fun actionsTop() = rule.onNode(hasText("일시정지")).getUnclippedBoundsInRoot().top.value - 12f   // 동작 줄 상자 위 = 버튼 위 − 세로 여백 12
        val tb0 = timerBottom(); val at0 = actionsTop()
        scene = Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })
        rule.waitForIdle()
        val wv = spOf("무게"); val tv = spOf("시간")
        val tbv = timerBottom(); val atv = actionsTop()
        println("LANDSCAPE weight $w0 -> $wv sp, timer $t0 -> $tv sp, timer bottom $tb0 / $tbv dp, action row top $at0 / $atv dp")
        // 측정 기록용(사용자 판단 항목, ui-design 3.2): 밸브 칸에서 지표 24 sp면 타이머가 첫 화면 아래로 밀릴 수 있다. 크기 관계만 단언한다
        assertEquals(WIDE_METRIC_MAX_SP, t0)
        assertEquals(t0, tv)
        assertTrue("밸브 $wv > 칸 없음 $w0", wv <= w0)
    }

    @Test fun `일시정지에서 재개한 첫 프레임의 경과는 재개 직전 값 이상이다`() {
        rule.mainClock.autoAdvance = false
        var t = 20_000_000_000L
        val clock = com.timemoredot.protocol.MonotonicClock { t }
        val base = Scenes.running(recipe = Fx.hario, phase = BrewPhase.Paused, sec = 10.0, weight = { 30.0 })
        var ui by mutableStateOf(base.ui)
        rule.setContent { Host(Scene(ui, base.readout, base.samples, clock)) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(hasContentDescription("시간 0분 10초, 멈춤")).assertExists()
        t = 100_000_000_000L
        ui = base.ui.copy(phase = BrewPhase.Running, timer = TimerSnapshot(10_000_000_000L, t))
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeByFrame()   // 재개 첫 프레임
        rule.onNode(hasContentDescription("시간 0분 10초")).assertExists()
    }

    @Test fun `화면이 STARTED가 아닌 동안 온 알림·스낵바는 돌아와도 내지 않는다(저장 실패만 남김)`() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val reg = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply { currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
            override val lifecycle get() = reg
        }
        val events = MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 8)
        rule.setContent {
            CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) { Host(Scenes.idle(), events) }
        }
        rule.waitForIdle()
        owner.reg.currentState = androidx.lifecycle.Lifecycle.State.CREATED
        rule.waitForIdle()
        events.tryEmit(BrewUiEvent.Announce(BrewAnnouncement.TareDone))
        events.tryEmit(BrewUiEvent.Snackbar(BrewMessage.TareTimeout, retryTare = true))
        events.tryEmit(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, retryTare = false))
        rule.waitForIdle()
        owner.reg.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        rule.waitForIdle()
        assertTrue(announced.toString(), announced.isEmpty())
        rule.onAllNodesWithText(messageText(BrewMessage.TareTimeout)).assertCountEquals(0)
        rule.onNodeWithText(messageText(BrewMessage.SaveFailed)).assertExists()
    }

    @Test fun `연결 실패 알림은 그 시점의 이유를 읽는다(응답 없음)`() {
        val fake = com.timemoredot.protocol.FakeScaleClient(com.timemoredot.protocol.ConnectionState.Connecting(Fx.device.address, null))
            .also { it.setLastDevice(Fx.device.address) }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val clock = com.timemoredot.protocol.MonotonicClock { System.nanoTime() }
        val prefs = com.timemoredot.app.fake.FakeAppPrefs()
        val repo = com.timemoredot.app.fake.FakeBrewRepository()
        val controller = com.timemoredot.app.BrewSessionController(fake, repo, prefs, clock, { 0L }, scope)
        val store = com.timemoredot.app.fake.VmStore()
        val vm = store.create {
            BrewViewModel(controller, fake, com.timemoredot.app.fake.FakeRecipeRepository(), repo, prefs,
                com.timemoredot.app.fake.FakePermissionMonitor(scale = fake), clock)
        }
        rule.setContent {
            CompositionLocalProvider(LocalAnnouncer provides recorder) {
                TimemoreTheme(darkTheme = false) { BrewScreen(vm, {}, { _, _ -> }, {}, {}, null, {}, false) }
            }
        }
        rule.waitForIdle()
        fake.setConnection(com.timemoredot.protocol.ConnectionState.Failed(com.timemoredot.protocol.FailureReason.ConnectTimeout))
        rule.waitForIdle()
        assertTrue(announced.toString(), "연결 실패, 응답 없음" in announced)
        store.clear()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Suppress("unused") private val unusedDp = 0.dp
}
