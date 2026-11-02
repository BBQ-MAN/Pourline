package com.timemoredot.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValveStateTest {
    private val open120 = ValveEvent(120, true)

    @Test fun valveStateAt() {
        assertNull(valveStateAt(emptyList(), 10.0))
        assertEquals(ValveUi(false, open120, 120), valveStateAt(HARIO_VALVES, 0.0))
        assertEquals(ValveUi(false, open120, 1), valveStateAt(HARIO_VALVES, 119.1))
        assertEquals(ValveUi(false, open120, 4), valveStateAt(HARIO_VALVES, 116.0))
        assertEquals(ValveUi(true, null, null), valveStateAt(HARIO_VALVES, 120.0))
        assertEquals(ValveUi(true, null, null), valveStateAt(HARIO_VALVES, 500.0))
        assertEquals(ValveUi(true, ValveEvent(75, false), 1), valveStateAt(DEVIL_VALVES, 74.9))
        assertEquals(ValveUi(false, ValveEvent(105, true), 30), valveStateAt(DEVIL_VALVES, 75.0))
        assertEquals(ValveUi(true, ValveEvent(60, false), 50), valveStateAt(listOf(ValveEvent(30, true), ValveEvent(60, false)), 10.0))
    }

    @Test fun valveUiFor() {
        val running = TimerSnapshot(0, sec(10.0))
        assertNull(valveUiFor(BrewPhase.Running, null, running, sec(20.0)))
        assertNull(valveUiFor(BrewPhase.Running, hario().withoutValve(), running, sec(20.0)))
        assertNull(valveUiFor(BrewPhase.Finished, hario(), TimerSnapshot(sec(50.0), null), 0))

        val ready = ValveUi(false, open120, 120)
        listOf(BrewPhase.Idle, BrewPhase.WaitingForPour).forEach { p ->
            assertEquals(ready, valveUiFor(p, hario(), TimerSnapshot.Zero, 0))
            assertEquals(ready, valveUiFor(p, hario(), TimerSnapshot(sec(300.0), sec(1.0)), sec(900.0)))
        }

        assertEquals(ValveUi(false, open120, 1), valveUiFor(BrewPhase.Running, hario(), running, sec(10.0 + 119.1)))
        assertEquals(ValveUi(true, null, null), valveUiFor(BrewPhase.Running, hario(), running, sec(10.0 + 120.0)))

        val paused = TimerSnapshot(sec(130.0), null)
        assertEquals(ValveUi(true, null, null), valveUiFor(BrewPhase.Paused, hario(), paused, sec(1.0)))
        assertEquals(ValveUi(true, null, null), valveUiFor(BrewPhase.Paused, hario(), paused, sec(5000.0)))
    }
}
