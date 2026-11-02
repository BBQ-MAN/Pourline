package com.timemoredot.brew

import org.junit.Assert.assertEquals
import org.junit.Test

/** architecture.md 7.1 세 인자 plannedSecFor: 기다리기 구간 [a, b] 안의 무게 L ± tolerance면 elapsed.coerceIn(a, b). */
class RecipePaceTest {
    @Test fun devilFlatSections() {
        val d = devil()
        assertEquals(50.0, d.plannedSecFor(120.4, 50.0, 1.0), 1e-9)
        assertEquals(50.0, d.plannedSecFor(119.5, 50.0, 1.0), 1e-9)
        assertEquals(40.0, d.plannedSecFor(120.0, 30.0, 1.0), 1e-9)
        assertEquals(75.0, d.plannedSecFor(120.3, 80.0, 1.0), 1e-9)
        assertEquals(150.0, d.plannedSecFor(280.5, 150.0, 1.0), 1e-9)
        assertEquals(180.0, d.plannedSecFor(280.0, 200.0, 1.0), 1e-9)
    }

    @Test fun harioFlatSection() {
        val h = hario()
        assertEquals(100.0, h.plannedSecFor(240.2, 100.0, 1.0), 1e-9)
        assertEquals(150.0, h.plannedSecFor(240.0, 160.0, 1.0), 1e-9)
    }

    @Test fun noWaitStepsEqualsOneArg() {
        val v60 = Recipe(0, "V60 기본", 15.0, 250.0, listOf(RecipeStep("a", 60.0, 10), RecipeStep("b", 250.0, 90)))
        for (k in 0..520) {
            val w = k * 0.5
            for (e in listOf(0.0, 45.0, 200.0)) assertEquals("w=$w e=$e", v60.plannedSecFor(w), v60.plannedSecFor(w, e, 1.0), 1e-9)
        }
    }
}
