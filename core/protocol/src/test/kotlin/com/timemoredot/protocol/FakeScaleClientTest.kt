package com.timemoredot.protocol

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeScaleClientTest {
    private val fake = FakeScaleClient()

    @Test
    fun `SetTimer 성공은 status만 바꾸고 timerEvents는 비어 있다`() = runTest {
        val events = mutableListOf<ScaleTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fake.timerEvents.toList(events) }

        assertEquals(CommandResult.Success, fake.send(ScaleCommand.SetTimer(TimerAction.Start)))
        assertEquals(ScaleTimerState.Running, fake.status.value.scaleTimer)
        assertEquals(emptyList<ScaleTimerState>(), events)

        // 원문 §5.2.1 경로. v1.0.4 실기기에는 없어 단위 테스트로만 확인
        fake.emitTimerNotify(ScaleTimerState.Paused)
        assertEquals(ScaleTimerState.Paused, fake.status.value.scaleTimer)
        assertEquals(listOf(ScaleTimerState.Paused), events)
    }

    @Test
    fun `SetUnit 성공 뒤 unit Ounce`() = runTest {
        fake.send(ScaleCommand.SetUnit(WeightUnit.Ounce))
        assertEquals(WeightUnit.Ounce, fake.status.value.unit)
    }

    @Test
    fun `respondWith 실패는 status를 바꾸지 않고 sent에 남는다`() = runTest {
        val failed = CommandResult.Failed(CommandFailure.Timeout)
        fake.respondWith(ScaleCommand.SetTimer::class, failed)
        assertEquals(failed, fake.send(ScaleCommand.SetTimer(TimerAction.Start)))
        assertEquals(null, fake.status.value.scaleTimer)
        assertEquals(CommandResult.Success, fake.send(ScaleCommand.Tare))
        assertEquals(listOf(ScaleCommand.SetTimer(TimerAction.Start), ScaleCommand.Tare), fake.sent)
    }

    @Test
    fun `setter와 호출 기록`() {
        val connected = ConnectionState.Connected(ScaleDevice("AA:BB", "Dot", null, null, mtu = 247))
        fake.setConnection(connected)
        fake.setStatus(ScaleStatus.Unknown.copy(batteryPercent = 50))
        fake.setScanResults(listOf(ScanCandidate("AA:BB", null, -60, 1)))
        assertEquals(connected, fake.connection.value)
        assertEquals(50, fake.status.value.batteryPercent)
        assertEquals(1, fake.scanResults.value.size)

        assertFalse(fake.connectLastDevice())
        fake.setLastDevice("AA:BB")
        assertEquals("AA:BB", fake.lastDeviceAddress.value)
        assertTrue(fake.connectLastDevice())
        fake.refreshEnvironment(); fake.startScan(); fake.stopScan(); fake.connect("AA:BB"); fake.disconnect()
        assertEquals(
            listOf("connectLastDevice", "connectLastDevice", "refreshEnvironment", "startScan", "stopScan", "connect:AA:BB", "disconnect"),
            fake.calls,
        )
    }

    @Test
    fun `emit과 readings 도우미`() = runTest {
        val got = mutableListOf<ScaleReading>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fake.readings.toList(got) }
        val series = readings(1_000L, 1.0, 2.5, flowGramsPerSec = 2.0)
        series.forEach { fake.emit(it) }
        assertEquals(series, got)
        assertEquals(1_000L + 100_000_000L, series[1].atNanos)
        assertEquals(2.5, series[1].weightGrams!!, 0.0)
        assertEquals(WeightUnit.Gram, series[1].sourceUnit)
    }
}
