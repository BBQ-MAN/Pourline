package com.timemoredot.app.ui.brew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.timemoredot.app.ui.Shooter
import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.StaleReason
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 한 장면: 상태 + readout + 표본 + 고정 시계. */
class Scene(val ui: BrewUiState, val readout: MutableStateFlow<BrewReadout>, val samples: SampleBuffer, val clock: MonotonicClock)

object Scenes {
    fun idle(
        recipe: Recipe? = Fx.v60, weight: Double? = 0.0, connection: ConnectionUi = Fx.connected, banner: BrewBanner? = null,
        reading: ReadingUi? = null, unit: WeightUnit = WeightUnit.Gram, phase: BrewPhase = BrewPhase.Idle, canStart: Boolean = connection is ConnectionUi.Connected,
    ): Scene {
        val r = reading ?: weight?.let { Fx.live(it, 0.0, unit) } ?: ReadingUi.None
        return Scene(
            Fx.ui(connection, phase, recipe, banner = banner, unit = unit, canStart = canStart, live = r is ReadingUi.Live),
            Fx.readout(r, recipe), SampleBuffer(), Fx.clockAt(0.0),
        )
    }

    fun running(
        recipe: Recipe? = Fx.v60, sec: Double = 60.0, phase: BrewPhase = BrewPhase.Running, connection: ConnectionUi = Fx.connected,
        banner: BrewBanner? = null, stale: StaleReason? = null, staleAt: Double = sec, gaps: List<ClosedFloatingPointRange<Double>> = emptyList(),
        overloadFrom: Double? = null, weight: ((Int) -> Double)? = null, flowOverride: Double? = null,
    ): Scene {
        val samples = if (weight != null) Fx.samples(if (stale != null) staleAt else sec, gaps, overloadFrom, weight, flow = { k -> if (flowOverride != null && k >= (sec * 10).toInt() - 5) flowOverride else 0.0 }) else Fx.samples(if (stale != null && stale != StaleReason.Overload) staleAt else sec, gaps, overloadFrom)
        val k = (sec * 10).toInt()
        val wt = weight?.invoke(k) ?: Fx.weightAt(minOf(k, (staleAt * 10).toInt()))
        val reading = if (stale != null) Fx.stale(if (stale == StaleReason.Overload) Fx.weightAt(((overloadFrom ?: sec) * 10).toInt() - 1) else wt, stale, staleAt)
        else Fx.live(wt, flowOverride ?: Fx.shownFlow(k))
        return Scene(
            Fx.ui(connection, phase, recipe, sec, banner, weight = wt),
            Fx.readout(reading, recipe, sec, samples.size, wt), samples, Fx.clockAt(sec),
        )
    }
}

@Composable fun SceneView(s: Scene, events: MutableSharedFlow<BrewUiEvent> = remember { MutableSharedFlow(replay = 1) }, recipes: List<Recipe> = emptyList()) =
    BrewFrame(s.ui, s.readout, s.samples, s.clock, recipes, events)

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
        Surface(Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = BottomSheetDefaults.ExpandedShape) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { BottomSheetDefaults.DragHandle(); content() }
        }
    }
}

private const val STILL = 1_000L

/** S1 상태별 스크린샷(ui-design 8.6). 360 × 800, mainClock.autoAdvance = false, 정지 장면은 1 s 진행 뒤. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class BrewScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun Shooter.scene(name: String, s: Scene, ms: Long = STILL, events: MutableSharedFlow<BrewUiEvent>? = null) =
        both(name, advanceMs = ms) { if (events != null) SceneView(s, events) else SceneView(s) }

    @Test fun `연결 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.scene("brew_nodevice", Scenes.idle(connection = ConnectionUi.NoDevice, weight = null))
        s.scene("brew_disconnected", Scenes.idle(connection = ConnectionUi.Disconnected, weight = null))
        s.scene("brew_bluetooth_off", Scenes.idle(connection = ConnectionUi.BluetoothOff, weight = null, banner = BrewBanner.BluetoothOff))
        s.scene("brew_permission_missing", Scenes.idle(connection = ConnectionUi.PermissionMissing, weight = null, banner = BrewBanner.PermissionMissing))
        s.scene("brew_permission_permanently_denied", Scenes.idle(connection = ConnectionUi.PermissionPermanentlyDenied, weight = null, banner = BrewBanner.PermissionPermanentlyDenied))
        s.scene("brew_location_off", Scenes.idle(connection = ConnectionUi.LocationOff, weight = null, banner = BrewBanner.LocationOff))
        s.scene("brew_connecting", Scenes.idle(connection = ConnectionUi.Connecting(Fx.device.address, "TIMEMORE_Dot"), weight = null, banner = BrewBanner.Connecting))
        s.scene("brew_unit_unknown", Scenes.idle(reading = ReadingUi.UnitUnknown, canStart = false))
        s.scene("brew_unit_unknown_timed_out", Scenes.idle(reading = ReadingUi.UnitUnknownTimedOut, banner = BrewBanner.UnitUnknownTimedOut, canStart = false))
        s.scene("brew_model_mismatch", Scenes.idle(connection = ConnectionUi.Connected(Fx.device.copy(modelNumber = "TES009")), banner = BrewBanner.ModelMismatch))
        s.scene("brew_failed", Scenes.idle(connection = ConnectionUi.Failed(ConnectFailure.ConnectFailed), weight = null, banner = BrewBanner.Failed))
        s.scene("brew_failed_timeout", Scenes.idle(connection = ConnectionUi.Failed(ConnectFailure.Timeout), weight = null, banner = BrewBanner.Failed))
        s.scene("brew_scanning_stopped", Scenes.idle(connection = ConnectionUi.Scanning(emptyList(), stopped = true), weight = null))
        s.scene("brew_scan_empty", Scenes.idle(connection = ConnectionUi.ScanEmpty, weight = null))
        s.scene("brew_scan_failed", Scenes.idle(connection = ConnectionUi.ScanFailed, weight = null, banner = BrewBanner.ScanFailed))
    }

    @Test fun `대기 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.scene("brew_idle", Scenes.idle())
        s.scene("brew_idle_free", Scenes.idle(recipe = null))
        s.scene("brew_waiting_for_pour", Scenes.idle(phase = BrewPhase.WaitingForPour, banner = BrewBanner.WaitingForPour))
        s.scene("brew_idle_negative", Scenes.idle(weight = -2.0))
        s.scene("brew_live_oz", Scenes.idle(recipe = null, weight = 235.4, unit = WeightUnit.Ounce))
        s.font200("brew_idle", advanceMs = STILL) { SceneView(Scenes.idle()) }
    }

    @Test fun `추출 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.scene("brew_running_recipe", Scenes.running())
        s.scene("brew_running_free", Scenes.running(recipe = null))
        s.scene("brew_paused", Scenes.running(phase = BrewPhase.Paused))
        s.scene("brew_running_peak", Scenes.running(sec = 6.0))
        s.scene("brew_running_on_target", Scenes.running(sec = 89.5))
        s.scene("brew_running_waiting_end", Scenes.running(sec = 89.5))
        s.scene("brew_running_long_step", Scenes.running(recipe = Fx.devil, sec = 30.0))
        s.scene("brew_running_spike", Scenes.running(sec = 62.0, weight = { k -> Fx.weightAt(k) + if (k >= 620) 60.0 else 0.0 }, flowOverride = 99.9))
        // 단계 카드로 스크롤한 장면: 36자 단계 이름이 생략 없이 보이는지(8.6)
        // performScrollTo는 애니메이션을 기다려 autoAdvance = false에서 멈춘다: ScrollBy를 보내고 스크롤 애니메이션이 끝날 시간을 준다
        s.font200("brew_running_long_step", midMs = STILL, advanceMs = STILL, mid = {
            val card = androidx.compose.ui.test.hasContentDescription("/6 단계", substring = true)
            rule.onNode(androidx.compose.ui.test.hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(card))
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        }) { SceneView(Scenes.running(recipe = Fx.devil, sec = 30.0)) }
        s.font200("brew_running_ribbon", advanceMs = STILL) { SceneView(Scenes.running()) }
    }

    private val rec = ConnectionUi.Reconnecting(3, 64_000_000_000L)

    @Test fun `멈춘 값`() {
        val s = Shooter(rule).also { it.start() }
        val gap = listOf(30.0..36.0)
        s.scene("brew_reconnecting_running", Scenes.running(recipe = null, connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 52.0, gaps = gap))
        s.scene("brew_reconnecting_running_recipe", Scenes.running(connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 52.0, gaps = gap))
        s.scene("brew_disconnected_running", Scenes.running(connection = ConnectionUi.Disconnected, banner = BrewBanner.Disconnected, stale = StaleReason.Disconnected, staleAt = 55.0, gaps = gap))
        s.scene("brew_overload", Scenes.running(banner = BrewBanner.Overload, stale = StaleReason.Overload, staleAt = 55.0, overloadFrom = 55.0))
        s.scene("brew_nodata", Scenes.running(banner = BrewBanner.NoData, stale = StaleReason.NoData, staleAt = 56.0, gaps = gap))
        s.font200("brew_reconnecting_running", advanceMs = STILL) {
            SceneView(Scenes.running(recipe = null, connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 52.0, gaps = gap))
        }
    }

    /** 계기 뒤 적힌 ms의 프레임. 장면 안 LaunchedEffect가 1 s 뒤 계기를 넣는다. */
    @Test fun `모양 겹 프레임`() {
        val s = Shooter(rule).also { it.start() }
        for ((name, at) in listOf("brew_tare_drop_frame" to 160L, "brew_tare_ring_frame" to 770L)) s.both(name, advanceMs = STILL + at) {
            val events = remember { MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 4) }
            val scene = remember { Scenes.idle(weight = 213.4) }
            LaunchedEffect(Unit) {   // 벡터 rx-tare-preweight-seq-001 모양: 응답과 같은 시각·+100·+200 ms 영점 전 무게, +300 ms부터 0
                delay(STILL)
                events.emit(BrewUiEvent.Announce(BrewAnnouncement.TareDone))
                delay(300)
                scene.readout.value = scene.readout.value.copy(reading = Fx.live(0.0))
            }
            SceneView(scene, events)
        }
        // 전환 계기는 테스트 쪽 상태: STILL 진행 → 바꿈 → 적힌 ms 진행(0 ms는 바뀐 첫 프레임 = 한 프레임 16 ms)
        val changed = mutableStateOf(false)
        for ((name, at) in listOf("brew_morph_frame" to 200L, "brew_running_start_frame" to 500L)) s.both(name, prepare = { changed.value = false }, midMs = STILL, mid = { changed.value = true }, advanceMs = at) {
            SceneView(if (changed.value) remember { Scenes.running(sec = at / 1000.0) } else remember { Scenes.idle() })
        }
        s.both("brew_running_step_change_frame", prepare = { changed.value = false }, midMs = STILL, mid = { changed.value = true }, advanceMs = 250) {
            // 9.3 s는 1단계, 9.7 s는 3단계(59 g 넘음, 기다리기 단계 2는 건너뜀): 바뀐 뒤 250 ms의 카드 색 숨
            SceneView(if (changed.value) remember { Scenes.running(sec = 9.7) } else remember { Scenes.running(sec = 9.3) })
        }
        // 실제 VM처럼 readout 흐름 하나와 상태 하나를 두고 값만 바꾼다: 숫자 겹·칩·그래프가 같은 프레임에 멈춘 값이 된다
        val live = Scenes.running()
        val stale = Scenes.running(connection = rec, banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 60.0)
        val liveReadout = live.readout.value
        s.both("brew_running_banner_resize_frame", prepare = { changed.value = false; live.readout.value = liveReadout }, midMs = STILL,
            mid = { changed.value = true; live.readout.value = stale.readout.value }, advanceMs = 16) {
            SceneView(Scene(if (changed.value) stale.ui else live.ui, live.readout, live.samples, live.clock))
        }
        s.scene("brew_running_snackbar", Scenes.running(), events = MutableSharedFlow<BrewUiEvent>(replay = 1).also {
            it.tryEmit(BrewUiEvent.Snackbar(BrewMessage.ScaleTimerSyncFailed, retryTare = false))
        })
        s.scene("brew_finished_save_failed", Scenes.running(sec = 95.0, phase = BrewPhase.Finished), events = MutableSharedFlow<BrewUiEvent>(replay = 1).also {
            it.tryEmit(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, retryTare = false))
        })
    }

    @Test fun `밸브 칸`() {
        val s = Shooter(rule).also { it.start() }
        s.scene("brew_idle_valve", Scenes.idle(recipe = Fx.hario))
        s.scene("brew_idle_valve_open", Scenes.idle(recipe = Fx.devil))
        s.scene("brew_running_valve", Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 }))
        s.scene("brew_running_valve_countdown", Scenes.running(recipe = Fx.hario, sec = 116.0, weight = { 240.0 }))
        s.scene("brew_reconnecting_running_valve", Scenes.running(recipe = Fx.hario, sec = 116.0, weight = { 240.0 }, connection = rec,
            banner = BrewBanner.Reconnecting, stale = StaleReason.Disconnected, staleAt = 110.0))
        s.both("brew_running_valve_cue", advanceMs = STILL + 250) {
            val events = remember { MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 4) }
            LaunchedEffect(Unit) { delay(STILL); events.emit(BrewUiEvent.Announce(BrewAnnouncement.ValveOpen)) }
            SceneView(remember { Scenes.running(recipe = Fx.hario, sec = 120.25, weight = { 240.0 }) }, events)
        }
        s.font200("brew_running_valve", advanceMs = STILL) { SceneView(Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })) }
    }

    @Test fun `시트와 대화상자`() {
        val s = Shooter(rule).also { it.start() }
        s.both("brew_settings") { SheetFrame { BrewSettingsContent(BrewSettings(), {}) } }
        s.both("settings_valve_haptic") { SheetFrame { BrewSettingsContent(BrewSettings(valveHaptic = true), {}) } }
        val all = com.timemoredot.app.data.DefaultRecipes.all.mapIndexed { i, r -> r.copy(id = i + 1L) }
        s.both("brew_recipe_picker") { SheetFrame { RecipePickerContent(1L, all, {}, {}) } }
        s.both("brew_recipe_picker_empty") { SheetFrame { RecipePickerContent(null, emptyList(), {}, {}) } }
        s.both("brew_leave_confirm", screen = true) { SceneView(Scenes.running()); LeaveConfirmDialog({}, {}, {}) }
        s.both("brew_reset_confirm", screen = true) { SceneView(Scenes.running(phase = BrewPhase.Paused)); ResetConfirmDialog({}, {}) }
    }
}

/** 대화상자는 자기 창이라 시스템 글꼴 배율을 따른다: 설정 배율 2.0으로 찍는다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 2f)
class BrewDialogFont200Test {
    @get:Rule val rule = createComposeRule()

    @Test fun `D1 글꼴 200`() {
        val s = Shooter(rule).also { it.start() }
        s.font200("brew_leave_confirm", screen = true) { SceneView(Scenes.running()); LeaveConfirmDialog({}, {}, {}) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-xhdpi")
class BrewLandscapeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `가로`() {
        val s = Shooter(rule).also { it.start() }
        s.both("brew_running_recipe_landscape", advanceMs = STILL) { SceneView(Scenes.running()) }
        s.font200("brew_running_recipe_landscape", advanceMs = STILL) { SceneView(Scenes.running()) }
        s.both("brew_idle_landscape", advanceMs = STILL) { SceneView(Scenes.idle()) }
        s.both("brew_running_valve_landscape", advanceMs = STILL) { SceneView(Scenes.running(recipe = Fx.hario, sec = 60.0, weight = { 240.0 })) }
    }
}

/** 모션 줄이기: 효과 문맥의 MotionDurationScale 0(시스템 "애니메이션 삭제"와 같은 경로). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class BrewReduceMotionScreenshotTest {
    @get:Rule val rule = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    @Test fun `모션 줄이기`() {
        val s = Shooter(rule).also { it.start() }
        s.both("brew_running_reduce_motion", advanceMs = STILL) { SceneView(Scenes.running(sec = 6.05)) }
        s.both("brew_running_valve_reduce_motion", advanceMs = STILL + 250) {
            val events = remember { MutableSharedFlow<BrewUiEvent>(extraBufferCapacity = 4) }
            LaunchedEffect(Unit) { delay(STILL); events.emit(BrewUiEvent.Announce(BrewAnnouncement.ValveOpen)) }
            SceneView(remember { Scenes.running(recipe = Fx.hario, sec = 120.25, weight = { 240.0 }) }, events)
        }
    }
}

/** 주 무게 글꼴 200%(선형 배율 sdk 33). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class BrewWeightFont200Test {
    @get:Rule val rule = createComposeRule()

    @Test fun `무게 글꼴 200`() {
        val s = Shooter(rule).also { it.start() }
        s.single("brew_idle_negative_light_font200", fontScale = 2f, advanceMs = STILL) { SceneView(Scenes.idle(weight = -2.0)) }
        s.single("brew_weight_worst_light_font200", fontScale = 2f, advanceMs = STILL) { SceneView(Scenes.idle(weight = -888.8)) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class BrewWeightW320Test {
    @get:Rule val rule = createComposeRule()

    @Test fun `무게 글꼴 200 w320`() {
        val s = Shooter(rule).also { it.start() }
        s.single("brew_weight_worst_w320_light_font200", fontScale = 2f, advanceMs = STILL) { SceneView(Scenes.idle(weight = -888.8)) }
    }
}

/** 4.2 자동 축소 2번(단위 아래 줄)이 실제로 걸리는 폭. w320·글꼴 2.0은 측정상 1번(한 줄)에서 끝나 2번 그림의 근거가 따로 필요하다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w220dp-h800dp-xhdpi")
class BrewWeightW220Test {
    @get:Rule val rule = createComposeRule()

    @Test fun `무게 글꼴 200 w220 단위 아래 줄`() {
        val s = Shooter(rule).also { it.start() }
        s.single("brew_weight_worst_w220_light_font200", fontScale = 2f, advanceMs = STILL) { SceneView(Scenes.idle(weight = -888.8)) }
    }
}
