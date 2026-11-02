package com.timemoredot.app.ui.brew

import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.StaleReason
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** architecture.md 9.3 매핑 분기. */
class ReadingUiMappingTest {
    private val device = ScaleDevice("AA", "Dot", "TES017", "v1", 247, true)
    private val connected = ConnectionUi.Connected(device)
    private val gram = ScaleStatus(WeightUnit.Gram, false, 80, null)

    private fun map(
        c: ConnectionUi, phase: BrewPhase = BrewPhase.Idle, weight: Double? = 100.0, stale: StaleReason? = null,
        status: ScaleStatus = gram, before: ConnectionUi? = null,
    ) = readingUi(c, before, status, phase, weight, 2.0, stale, 5L, WeightUnit.Gram)

    @Test fun none() {
        assertEquals(ReadingUi.None, map(ConnectionUi.NoDevice, weight = null))
        assertEquals(ReadingUi.None, map(connected, weight = null))
    }

    @Test fun unitUnknown() {
        val unknown = ScaleStatus(null, false, null, null)
        assertEquals(ReadingUi.UnitUnknown, map(connected, status = unknown, stale = StaleReason.Disconnected))
        assertEquals(ReadingUi.UnitUnknownTimedOut, map(connected, status = unknown.copy(unitReadFailed = true), stale = StaleReason.Disconnected))
    }

    @Test fun live() {
        assertEquals(ReadingUi.Live(100.0, 2.0, WeightUnit.Gram), map(connected))
    }

    @Test fun staleCauses() {
        assertEquals(ReadingUi.Stale(100.0, StaleReason.Overload, 5L, WeightUnit.Gram), map(connected, stale = StaleReason.Overload))
        assertEquals(ReadingUi.Stale(100.0, StaleReason.NoData, 5L, WeightUnit.Gram), map(connected, stale = StaleReason.NoData))
        assertEquals(ReadingUi.Stale(100.0, StaleReason.Disconnected, 5L, WeightUnit.Gram), map(connected, stale = StaleReason.Disconnected))
        assertEquals(
            ReadingUi.Stale(100.0, StaleReason.Disconnected, 5L, WeightUnit.Gram),
            map(ConnectionUi.Reconnecting(1, 0), stale = StaleReason.Disconnected),
        )
    }

    @Test fun userDisconnect() {
        assertEquals(
            ReadingUi.Stale(100.0, StaleReason.Disconnected, 5L, WeightUnit.Gram),
            map(ConnectionUi.Disconnected, BrewPhase.Running, stale = StaleReason.Disconnected),
        )
        assertEquals(ReadingUi.None, map(ConnectionUi.Disconnected, BrewPhase.Idle, stale = StaleReason.Disconnected))
        assertEquals(ReadingUi.None, map(ConnectionUi.Connecting("AA", null), BrewPhase.Idle, stale = StaleReason.Disconnected))
        assertEquals(
            ReadingUi.Stale(100.0, StaleReason.Disconnected, 5L, WeightUnit.Gram),
            map(ConnectionUi.Connecting("AA", null), BrewPhase.Running, stale = StaleReason.Disconnected),
        )
    }

    @Test fun failed() {
        assertEquals(ReadingUi.None, map(ConnectionUi.Failed(ConnectFailure.ServiceNotFound)))
        assertEquals(ReadingUi.None, map(ConnectionUi.Failed(ConnectFailure.Timeout)))
        assertEquals(
            ReadingUi.Stale(100.0, StaleReason.Disconnected, 5L, WeightUnit.Gram),
            map(ConnectionUi.Failed(ConnectFailure.ConnectFailed), BrewPhase.Running, stale = StaleReason.Disconnected),
        )
    }

    @Test fun `매핑 1-1 블루투스 꺼짐`() {
        val off = ConnectionUi.BluetoothOff
        val d = StaleReason.Disconnected
        assertEquals(ReadingUi.None, map(off, BrewPhase.Idle, stale = d, before = ConnectionUi.Disconnected))
        assertEquals(ReadingUi.None, map(off, BrewPhase.Idle, stale = d, before = ConnectionUi.Failed(ConnectFailure.Timeout)))
        assertEquals(ReadingUi.Stale(100.0, d, 5L, WeightUnit.Gram), map(off, BrewPhase.Idle, stale = d, before = connected))
        assertEquals(ReadingUi.Stale(100.0, d, 5L, WeightUnit.Gram), map(off, BrewPhase.Running, stale = d, before = ConnectionUi.Disconnected))
        assertEquals(ReadingUi.None, map(off, BrewPhase.Idle, stale = d, before = null))   // VM이 처음부터 BluetoothOff
    }
}
