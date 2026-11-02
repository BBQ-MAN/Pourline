package com.timemoredot.app.ui.brew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.timemoredot.app.data.DefaultRecipes
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.nav.AppTabsScaffold
import com.timemoredot.app.ui.nav.Dest
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSample
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.FLAG_DISCONNECTED
import com.timemoredot.brew.FLAG_OVERLOAD
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.TimerSnapshot
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** S1 화면 테스트 고정 데이터. 무게 시계열은 ui-organic-direction.md 6.4 합성 데이터(V60 기본). */
object Fx {
    val device = ScaleDevice("C8:2B:96:DD:A1:B2", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)
    val connected = ConnectionUi.Connected(device)
    fun recipe(name: String): Recipe = DefaultRecipes.all.first { it.name == name }.let { it.copy(id = DefaultRecipes.all.indexOf(it) + 1L) }
    val v60 get() = recipe("V60 기본")
    val hario get() = recipe("하리오 스위치 기본")
    val devil get() = recipe("테츠 카스야 악마의 레시피")

    private const val TAU = 2 * PI
    /** 6.4 w(t): 뜸 붓기 → 61 g, 기다림, 2차 붓기(8번 출렁) → 250 g. */
    fun w(t: Double): Double = when {
        t < 0 -> 0.0
        t <= 10 -> { val x = t / 10; 61 * (x - 0.5 * sin(TAU * x) / TAU) }
        t <= 40 -> 61.0
        t <= 90 -> { val x = (t - 40) / 50; 61 + 189 * (x - 0.5 * sin(TAU * x) / TAU - 0.3 * sin(8 * TAU * x) / (8 * TAU)) }
        else -> 250.0
    }
    private fun raw(k: Int) = (w(k / 10.0) * 10).roundToInt()
    private fun scaleFlow(k: Int) = (raw(k) - raw(k - 10)).coerceIn(0, 999)
    fun shownFlow(k: Int) = (k - 9..k).sumOf { scaleFlow(it) } / 100.0
    fun weightAt(k: Int) = raw(k) / 10.0

    /** 0 ~ untilSec(포함) 표본. gaps = 끊김 구간(초), overloadFrom = 이 초부터 과부하. */
    fun samples(untilSec: Double, gaps: List<ClosedFloatingPointRange<Double>> = emptyList(), overloadFrom: Double? = null, weight: (Int) -> Double = ::weightAt, flow: (Int) -> Double = ::shownFlow): SampleBuffer {
        val b = SampleBuffer()
        val last = (untilSec * 10).roundToInt()
        for (k in 0..last) {
            val t = k / 10.0
            if (gaps.any { t > it.start && t < it.endInclusive }) continue
            if (gaps.any { t == it.start }) { b.append(BrewSample(k * 100, weight(k).toFloat(), flow(k).toFloat(), FLAG_DISCONNECTED)); continue }
            if (overloadFrom != null && t >= overloadFrom) { b.append(BrewSample(k * 100, Float.NaN, 0f, FLAG_OVERLOAD)); continue }
            b.append(BrewSample(k * 100, weight(k).toFloat(), flow(k).toFloat(), 0))
        }
        return b
    }

    fun clockAt(sec: Double) = MonotonicClock { (sec * 1e9).toLong() }

    /** 단계 판정(엔진 규칙과 같음): 무게 < 목표 − 1인 첫 단계, 없으면 마지막 + 도달. */
    fun step(recipe: Recipe?, weight: Double): Pair<Int?, Boolean> {
        recipe ?: return null to false
        val i = recipe.steps.indexOfFirst { weight < it.targetGrams - 1.0 }
        return if (i >= 0) i to false else recipe.steps.lastIndex to true
    }

    fun ui(
        connection: ConnectionUi = connected, phase: BrewPhase = BrewPhase.Idle, recipe: Recipe? = null, sec: Double = 0.0,
        banner: BrewBanner? = null, battery: Int? = 82, unit: WeightUnit = WeightUnit.Gram, weight: Double? = null,
        settings: BrewSettings = BrewSettings(), stepOverride: Int? = null, reachedOverride: Boolean? = null,
        canStart: Boolean = connection is ConnectionUi.Connected, live: Boolean = true, saveFailed: Boolean = false,
    ): BrewUiState {
        val idleLike = phase == BrewPhase.Idle || phase == BrewPhase.WaitingForPour
        val running = phase == BrewPhase.Running || phase == BrewPhase.Paused || phase == BrewPhase.Finished
        val (s, reached) = if (running) step(recipe, weight ?: w(sec)) else null to false   // 대기 중에는 엔진 단계 안내가 없다
        @Suppress("UNUSED_VARIABLE") val unused = saveFailed
        return BrewUiState(
            connection = connection, batteryPercent = if (connection is ConnectionUi.Connected) battery else null, displayUnit = unit,
            phase = phase,
            timer = when (phase) {
                BrewPhase.Running -> TimerSnapshot(0, 0)   // 시계 = sec(clockAt)
                BrewPhase.Paused, BrewPhase.Finished -> TimerSnapshot((sec * 1e9).toLong(), null)
                else -> TimerSnapshot.Zero
            },
            recipe = recipe, currentStepIndex = stepOverride ?: s, lastStepReached = reachedOverride ?: reached, banner = banner,
            tareInFlight = false, canTare = connection is ConnectionUi.Connected && live && idleLike, canStart = canStart,
            canChangeRecipeOrSettings = idleLike, canFindOtherScale = idleLike, showBottomBar = idleLike,
            keepScreenOn = phase == BrewPhase.Running || phase == BrewPhase.Paused, settings = settings,
        )
    }

    fun readout(reading: ReadingUi, recipe: Recipe? = null, sec: Double = 0.0, count: Int = 0, weight: Double? = null): MutableStateFlow<BrewReadout> {
        val wt = weight ?: (reading as? ReadingUi.Live)?.weightGrams ?: (reading as? ReadingUi.Stale)?.lastWeightGrams
        val (s, _) = step(recipe, wt ?: 0.0)
        val remaining = if (recipe != null && wt != null && s != null) maxOf(0.0, recipe.steps[s].targetGrams - wt) else null
        val pace = if (recipe != null && wt != null && count > 0) sec - recipe.plannedSecFor(wt, sec, 1.0) else null
        return MutableStateFlow(BrewReadout(reading, remaining, pace, count))
    }

    fun live(g: Double, flow: Double = 0.0, unit: WeightUnit = WeightUnit.Gram) = ReadingUi.Live(g, flow, unit)
    fun stale(g: Double, cause: StaleReason, sinceSec: Double) = ReadingUi.Stale(g, cause, (sinceSec * 1e9).toLong(), WeightUnit.Gram)
}

/** 화면 + (보일 때) 탭. 스크린샷은 탭이 있는 상태를 그대로 찍는다. */
@Composable
fun BrewFrame(
    ui: BrewUiState, readout: MutableStateFlow<BrewReadout>, samples: SampleBuffer = remember { SampleBuffer() },
    clock: MonotonicClock = Fx.clockAt(0.0), recipes: List<Recipe> = emptyList(), events: Flow<BrewUiEvent> = emptyFlow(),
    callbacks: BrewCallbacks = NoBrewCallbacks,
) {
    AppTabsScaffold(Dest.Brew, ui.showBottomBar, onSelect = {}) {
        BrewContent(ui, readout, samples, clock, recipes, callbacks, events)
    }
}
