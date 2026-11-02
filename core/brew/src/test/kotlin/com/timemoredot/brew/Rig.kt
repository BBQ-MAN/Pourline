package com.timemoredot.brew

import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

fun ms(v: Long): Long = v * 1_000_000L
fun sec(v: Double): Long = Math.round(v * 1_000_000_000.0)

fun reading(atNanos: Long, grams: Double?, flow: Double = 0.0, overload: Boolean = false) =
    BrewInput.Reading(ScaleReading(if (overload) null else grams, flow, 0, overload, WeightUnit.Gram, atNanos))

/** 엔진 래퍼: 매 입력 뒤 공통 불변 조건(유량 0 이상, phase 변화 ↔ PhaseChanged 정확히 하나)을 확인하고 효과를 모은다. */
class Rig(config: BrewConfig = BrewConfig(), settings: BrewSettings = BrewSettings()) {
    val engine = BrewEngine(config, settings)
    val all = mutableListOf<BrewEffect>()
    val samples = SampleBuffer()
    val state get() = engine.state

    fun feed(input: BrewInput): List<BrewEffect> {
        val before = engine.state.phase
        val fx = engine.handle(input)
        val after = engine.state.phase
        assertTrue("flow < 0: ${engine.state.flowGramsPerSec}", engine.state.flowGramsPerSec >= 0.0)
        val changes = fx.filterIsInstance<BrewEffect.PhaseChanged>()
        if (before == after) {
            assertTrue("PhaseChanged without phase change on $input", changes.isEmpty())
        } else {
            assertEquals("exactly one PhaseChanged on $input", 1, changes.size)
            assertEquals(before, changes[0].from)
            assertEquals(after, changes[0].to)
            assertTrue("PhaseChanged must be last", fx.last() is BrewEffect.PhaseChanged)
        }
        fx.forEach {
            when (it) {
                is BrewEffect.AppendSample -> samples.append(it.sample)
                BrewEffect.ClearSamples -> samples.clear()
                else -> {}
            }
        }
        all += fx
        return fx
    }

    fun feedAll(vararg inputs: BrewInput) = inputs.flatMap { feed(it) }

    fun count(predicate: (BrewEffect) -> Boolean) = all.count(predicate)
    fun phaseChanges() = all.filterIsInstance<BrewEffect.PhaseChanged>()
}

fun recipe(vararg steps: Pair<Double, Int>, finishAtSec: Int? = null, dose: Double = 15.0, water: Double = steps.last().first) =
    Recipe(0, "test", dose, water, steps.mapIndexed { i, (g, s) -> RecipeStep("s$i", g, s) }, finishAtSec)
