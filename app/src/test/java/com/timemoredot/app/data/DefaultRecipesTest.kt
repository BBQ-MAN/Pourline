package com.timemoredot.app.data

import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep
import com.timemoredot.brew.BrewEngine
import com.timemoredot.brew.BrewInput
import com.timemoredot.brew.ValveEvent
import com.timemoredot.brew.isWaitStep
import com.timemoredot.app.ui.brew.Fx
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.TimerAction
import com.timemoredot.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRecipesTest {
    private data class S(val name: String, val grams: Int, val atSec: Int, val valveOpen: Boolean? = null)

    private fun r(name: String, dose: Double, water: Double, finish: Int?, vararg s: S) =
        Recipe(0, name, dose, water, s.map { RecipeStep(it.name, it.grams.toDouble(), it.atSec, it.valveOpen) }, finish)

    // 8.3절 단계 표·끝 시각 표·밸브 표를 테스트에 따로 옮겨 적었다(DefaultRecipes와 같은 코드를 공유하지 않는다).
    private val expected = listOf(
        r("V60 기본", 15.0, 250.0, null,
            S("뜸 60 g, 30초 기다리기 (95°C, 중간-가는 분쇄)", 60, 10),
            S("0:40까지 기다리기", 60, 40),
            S("0:40 나머지 물을 작은 원으로 천천히 250 g까지", 250, 90)),
        r("하리오 스위치 기본", 20.0, 240.0, null,
            S("스위치 닫고(올림) 240 g 한 번에 붓기 (중간-가는 분쇄)", 240, 10, false),
            S("2:00까지 담가 두기", 240, 120, false),
            S("2:00 스위치 눌러 열고 내려받기", 240, 150, true)),
        r("6888 레시피", 20.0, 300.0, 165,
            S("뜸 60 g, 0:40까지 기다리기 (92°C, 중간 분쇄)", 60, 10),
            S("0:40까지 기다리기", 60, 40),
            S("0:40 2차 140 g까지", 140, 50),
            S("1:10 3차 220 g까지", 220, 80),
            S("1:40 4차 300 g까지 · 2:45 전후 추출 끝", 300, 110)),
        r("테츠 카스야 기본 레시피", 20.0, 300.0, 210,
            S("1차 50 g (92°C, 굵은 분쇄)", 50, 5),
            S("0:45 2차 120 g까지", 120, 50),
            S("1:30 3차 180 g까지", 180, 95),
            S("2:15 4차 240 g까지", 240, 140),
            S("3:00 5차 300 g까지 · 3:30에 드리퍼 내리기", 300, 185)),
        r("테츠 카스야 악마의 레시피", 20.0, 280.0, 180,
            S("스위치 열고 뜸 60 g (90°C, 약간 가는 분쇄)", 60, 10, true),
            S("0:30 120 g까지 · 이어서 찬물을 섞어 주전자를 70°C로", 120, 40, true),
            S("1:15까지 기다리기", 120, 75, true),
            S("1:15 스위치 닫고 70°C 물로 280 g까지", 280, 85, false),
            S("1:45까지 담가 두기", 280, 105, false),
            S("1:45 스위치 열기 · 3:00 추출 끝", 280, 180, true)),
    )

    @Test fun matchesSpecTable() = assertEquals(expected, DefaultRecipes.all)

    @Test fun finishAndValveSchedules() {
        val byName = DefaultRecipes.all.associateBy { it.name }
        assertEquals(150, byName.getValue("하리오 스위치 기본").finishSec)
        assertEquals(180, byName.getValue("테츠 카스야 악마의 레시피").finishAtSec)
        assertEquals(listOf(ValveEvent(0, false), ValveEvent(120, true)), byName.getValue("하리오 스위치 기본").valveSchedule)
        assertEquals(
            listOf(ValveEvent(0, true), ValveEvent(75, false), ValveEvent(105, true)),
            byName.getValue("테츠 카스야 악마의 레시피").valveSchedule,
        )
        listOf("V60 기본", "6888 레시피", "테츠 카스야 기본 레시피").forEach { n ->
            val r = byName.getValue(n)
            assertTrue(n, r.steps.all { it.valveOpen == null })
            assertEquals(n, emptyList<ValveEvent>(), r.valveSchedule)
        }
    }

    @Test fun invariants() {
        assertEquals(5, DefaultRecipes.all.size)
        DefaultRecipes.all.forEach { r ->
            assertEquals(0L, r.id)
            assertTrue(r.name.length in 1..30)
            assertTrue(r.doseGrams in 0.1..100.0)
            assertTrue(r.steps.isNotEmpty())
            assertTrue(r.steps.first().targetGrams > 0 && r.steps.first().targetAtSec > 0)
            assertTrue(r.steps.zipWithNext().all { (a, b) -> a.targetGrams <= b.targetGrams && a.targetAtSec < b.targetAtSec })
            assertEquals(r.waterGrams, r.steps.last().targetGrams, 0.0)
            assertTrue(r.name, r.steps.all { it.valveOpen == null } || r.steps.all { it.valveOpen != null })
        }
    }

    // 2026-11-02 P2: 뜸 뒤 기다리기 단계(index 1)
    @Test fun waitStepsAndPace() {
        val byName = DefaultRecipes.all.associateBy { it.name }
        val v60 = byName.getValue("V60 기본")
        val r6888 = byName.getValue("6888 레시피")
        assertEquals(90, v60.finishSec)
        assertEquals(null, v60.finishAtSec)
        for (r in listOf(v60, r6888)) r.steps.indices.forEach { assertEquals("${r.name} $it", it == 1, r.isWaitStep(it)) }
        assertEquals(25.0, v60.plannedSecFor(60.3, 25.0, 1.0), 1e-9)
        assertEquals(40.0, v60.plannedSecFor(60.0, 45.0, 1.0), 1e-9)
        assertEquals(10.0, v60.plannedSecFor(60.0, 5.0, 1.0), 1e-9)
        assertEquals(30.0, r6888.plannedSecFor(60.2, 30.0, 1.0), 1e-9)
    }

    // ui-organic-direction 6.4 합성 시계열(10 Hz)을 레시피대로 따르면 기다림 동안 늦음이 뜨지 않는다(P2 전에는 약 22초 늦음)
    @Test fun v60SyntheticBrewHasNoLagDuringWait() {
        val e = BrewEngine()
        e.handle(BrewInput.SelectRecipe(DefaultRecipes.all.first { it.name == "V60 기본" }, 0))
        e.handle(BrewInput.UserTimer(TimerAction.Start, 0))
        var reached = false
        for (k in 1..900) {
            val g = Fx.w(k / 10.0)
            e.handle(BrewInput.Reading(ScaleReading(g, 0.0, 0, false, WeightUnit.Gram, k * 100_000_000L)))
            val guide = e.state.guide!!
            if (k in 110..400) assertTrue("k=$k pace ${guide.paceDeltaSec}", kotlin.math.abs(guide.paceDeltaSec) <= 2.0)
            if (g >= 59.0) reached = true
            if (reached) assertEquals("k=$k", 2, guide.index)
        }
        assertTrue(reached)
    }
}
