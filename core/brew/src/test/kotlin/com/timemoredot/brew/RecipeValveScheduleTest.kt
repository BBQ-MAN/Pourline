package com.timemoredot.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** architecture.md 7.1 "밸브 단계 모델": 단계 시작 = 앞 단계 targetAtSec, 같은 상태 합침, null 하나라도 있으면 빈 목록. */
class RecipeValveScheduleTest {
    private fun ev(vararg e: Pair<Int, Boolean>) = e.map { ValveEvent(it.first, it.second) }

    @Test fun defaultsDerive() {
        assertEquals(ev(0 to false, 120 to true), hario().valveSchedule)
        assertEquals(ev(0 to true, 75 to false, 105 to true), devil().valveSchedule)
        assertEquals(HARIO_VALVES, hario().valveSchedule)
        assertEquals(DEVIL_VALVES, devil().valveSchedule)
    }

    @Test fun nullMeansNoValve() {
        assertEquals(emptyList<ValveEvent>(), devil().withoutValve().valveSchedule)
        val partial = valveRecipe(Triple(60, 10, true), Triple(120, 40, null), Triple(200, 90, false))
        assertEquals(emptyList<ValveEvent>(), partial.valveSchedule)
    }

    @Test fun mergesAndUsesPreviousTarget() {
        assertEquals(ev(0 to false), valveRecipe(Triple(60, 10, false), Triple(120, 40, false)).valveSchedule)
        assertEquals(ev(0 to true, 10 to false), valveRecipe(Triple(60, 10, true), Triple(150, 45, false)).valveSchedule)
    }

    @Test fun waitSteps() {
        val d = devil()
        assertEquals(listOf(2, 4, 5), d.steps.indices.filter { d.isWaitStep(it) })
        val h = hario()
        assertEquals(listOf(1, 2), h.steps.indices.filter { h.isWaitStep(it) })
        assertFalse(d.isWaitStep(0))
        assertFalse(h.isWaitStep(0))
    }

    @Test fun constructorHasNoValveSchedule() =
        assertFalse(Recipe::class.java.declaredFields.any { it.name == "valveSchedule" })   // 생성자 프로퍼티 = 백킹 필드
}
