package com.timemoredot.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleStateReducerTest {
    private val r = ScaleStateReducer()

    private fun weight(raw: Int, flow: Int = 0, overload: Boolean = false) =
        ScaleFrame.WeightReport(weightRaw = raw, flowRaw = flow, timerSec = 7, overload = overload)

    private fun unit(raw: Int) = ScaleFrame.UnitReport(Op.Read, raw)

    /** 벡터 hex를 재조립기로 디코딩한 프레임(디코딩 순서 그대로). */
    private fun vectorFrames(id: String): List<ScaleFrame> {
        val re = FrameReassembler()
        return Vectors.byId(id).hexChunks().flatMap { re.feed(it.hex()) }.map { (it as DecodeResult.Frame).frame }
    }

    @Test
    fun `UnitReport 전 WeightReport는 버린다 C-01-13`() {
        assertNull(r.onFrame(weight(1234), 1))
        assertEquals(ScaleStatus.Unknown, r.status)
    }

    @Test
    fun `g 모드 변환`() {
        r.onFrame(unit(0x00), 0)
        val reading = r.onFrame(weight(1234, flow = 12), 42)!!
        assertEquals(123.4, reading.weightGrams!!, 1e-9)
        assertEquals(1.2, reading.scaleFlowGramsPerSec, 1e-9)
        assertEquals(7, reading.scaleTimerSec)
        assertEquals(WeightUnit.Gram, reading.sourceUnit)
        assertEquals(42L, reading.atNanos)
        assertFalse(reading.overload)
    }

    @Test
    fun `실측 벡터 g 변환`() {
        r.onFrame(unit(0x00), 0)
        val positive = vectorFrames("rx-weight-positive-002").single()
        assertEquals(145.0, r.onFrame(positive, 1)!!.weightGrams!!, 1e-9)
        val flowMax = vectorFrames("rx-weight-flowmax-001").single()
        assertEquals(99.9, r.onFrame(flowMax, 2)!!.scaleFlowGramsPerSec, 1e-9)
    }

    @Test
    fun `실측 결합 알림 배터리가 앞`() {
        r.onFrame(unit(0x00), 0)
        val out = vectorFrames("rx-two-frames-003").mapNotNull { r.onFrame(it, 5) }
        assertEquals(100, r.status.batteryPercent)
        assertEquals(1, out.size)
        assertEquals(0.0, out.single().weightGrams!!, 1e-9)
    }

    @Test
    fun `Unknown 0x04는 null이고 상태 불변`() {
        r.onFrame(unit(0x00), 0)
        val before = r.status
        assertNull(r.onFrame(ScaleFrame.Unknown(0x01, 0x04, "0000000000"), 1))
        assertEquals(before, r.status)
    }

    @Test
    fun `oz 모드 변환`() {
        r.onFrame(unit(0x01), 0)
        val reading = r.onFrame(weight(1234, flow = 12), 1)!!
        assertEquals(12.34 * GRAMS_PER_OUNCE, reading.weightGrams!!, 1e-6)
        assertEquals(0.12 * GRAMS_PER_OUNCE, reading.scaleFlowGramsPerSec, 1e-6)
        assertEquals(WeightUnit.Ounce, reading.sourceUnit)
    }

    @Test
    fun `과부하는 무게 null, 음수 유량은 그대로`() {
        r.onFrame(unit(0x00), 0)
        val over = r.onFrame(weight(5000, overload = true), 1)!!
        assertNull(over.weightGrams)
        assertTrue(over.overload)
        assertEquals(-1.5, r.onFrame(weight(10, flow = -15), 2)!!.scaleFlowGramsPerSec, 1e-9)
    }

    @Test
    fun `SetUnit 성공 뒤 oz 배율`() {
        r.onFrame(unit(0x00), 0)
        r.onCommandSucceeded(ScaleCommand.SetUnit(WeightUnit.Ounce))
        assertEquals(WeightUnit.Ounce, r.status.unit)
        assertEquals(12.34 * GRAMS_PER_OUNCE, r.onFrame(weight(1234), 1)!!.weightGrams!!, 1e-6)
    }

    @Test
    fun `SetTimer 성공은 scaleTimer를 바꾼다`() {
        r.onCommandSucceeded(ScaleCommand.SetTimer(TimerAction.Start))
        assertEquals(ScaleTimerState.Running, r.status.scaleTimer)
        r.onCommandSucceeded(ScaleCommand.SetTimer(TimerAction.Pause))
        assertEquals(ScaleTimerState.Paused, r.status.scaleTimer)
        r.onFrame(ScaleFrame.TimerState(Op.Read, 0x03), 0)
        assertEquals(ScaleTimerState.Reset, r.status.scaleTimer)
        r.onFrame(ScaleFrame.TimerState(Op.Notify, 0x09), 0)   // 미상 stateRaw는 무시
        assertEquals(ScaleTimerState.Reset, r.status.scaleTimer)
    }

    @Test
    fun `배터리 150은 100`() {
        r.onFrame(ScaleFrame.Battery(Op.Notify, bars = 3, percent = 150), 0)
        assertEquals(100, r.status.batteryPercent)
    }

    @Test
    fun `onUnitReadFailed 뒤 UnitReport가 오면 해제`() {
        r.onUnitReadFailed()
        assertTrue(r.status.unitReadFailed)
        r.onFrame(unit(0x00), 0)
        assertFalse(r.status.unitReadFailed)
        assertEquals(WeightUnit.Gram, r.status.unit)
    }

    @Test
    fun `reset 뒤 Unknown`() {
        r.onFrame(unit(0x00), 0)
        r.onFrame(ScaleFrame.Battery(Op.Read, 3, 80), 0)
        r.reset()
        assertEquals(ScaleStatus.Unknown, r.status)
    }

    @Test
    fun `unitRaw 0x02는 단위 미상과 실패`() {
        r.onFrame(unit(0x02), 0)
        assertNull(r.status.unit)
        assertTrue(r.status.unitReadFailed)
        assertNull(r.onFrame(weight(1234), 1))
        r.onFrame(unit(0x00), 2)
        assertEquals(WeightUnit.Gram, r.status.unit)
        assertFalse(r.status.unitReadFailed)
        assertNotNull(r.onFrame(weight(1234), 3))
    }
}
