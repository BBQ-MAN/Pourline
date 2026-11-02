package com.timemoredot.app.ui.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 9.6 초안 파싱 함수. */
class StepDraftParsingTest {
    @Test fun `parseStepGrams`() {
        assertEquals(120.0, parseStepGrams("120")!!, 0.0)
        assertEquals(120.0, parseStepGrams(" 120.0 ")!!, 0.0)
        assertEquals(0.0, parseStepGrams("0")!!, 0.0)
        for (bad in listOf("abc", "", "NaN", "Infinity")) assertNull(bad, parseStepGrams(bad))
    }

    @Test fun `parseStepTimeSec`() {
        assertEquals(75, parseStepTimeSec("1", "15"))
        assertEquals(0, parseStepTimeSec("0", "0"))
        assertEquals(120, parseStepTimeSec(" 2 ", " 0 "))
        assertEquals(3600, parseStepTimeSec("60", "0"))
        for ((m, s) in listOf("" to "5", "1" to "", "1" to "60", "-1" to "0", "1.5" to "0", "a" to "0")) assertNull("$m:$s", parseStepTimeSec(m, s))
    }

    @Test fun `isWaitStep`() {
        fun d(g: String) = StepDraft("", g, "0", "0")
        assertTrue(isWaitStep(d("120"), d("120.0")))
        assertFalse(isWaitStep(d("120"), d("121")))
        assertFalse(isWaitStep(d("abc"), d("abc")))
        assertFalse(isWaitStep(d(""), d("")))
    }
}
