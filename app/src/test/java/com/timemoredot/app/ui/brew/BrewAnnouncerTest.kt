package com.timemoredot.app.ui.brew

import com.timemoredot.app.data.DefaultRecipes
import com.timemoredot.brew.BrewPhase
import com.timemoredot.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ui-design 8.2 BrewAnnouncer 낭독 문구(순수 함수 JVM 테스트). */
class BrewAnnouncerTest {
    private val v60 = DefaultRecipes.all.first { it.name == "V60 기본" }
    private fun ns(sec: Double) = (sec * 1e9).toLong()
    private fun inp(sec: Double, phase: BrewPhase = BrewPhase.Running, step: Int? = 0, reached: Boolean = false, stale: Boolean = false, w: String = "30.5") =
        BrewAnnouncerLogic.Input(phase, ns(sec), stale, step, reached, v60, w, "4.2", WeightUnit.Gram)

    private fun started() = BrewAnnouncerLogic().also { it.update(inp(0.0, BrewPhase.Idle, null)); it.update(inp(0.0)) }

    @Test fun `5초 경계에서 한 번`() {
        val l = started()
        for (t in 1..49) assertNull(l.update(inp(t / 10.0)))
        assertEquals("0분 5초, 30.5 그램, 초당 4.2 그램", l.update(inp(5.0)))
        assertNull(l.update(inp(5.1)))
    }

    @Test fun `단계가 바뀌면 바로 단계 문구, 5초 주기 다시 시작`() {
        val l = started()
        assertNull(l.update(inp(3.0)))
        assertEquals("3단계, ${v60.steps[2].name}, 250 그램까지", l.update(inp(9.7, step = 2, w = "60.0")))   // 기다리기 단계(index 1)는 엔진이 건너뛴다
        assertNull(l.update(inp(10.0, step = 2)))   // 원래 10 s 경계였지만 9.7 s에서 다시 시작
        assertNull(l.update(inp(14.6, step = 2)))
        assertEquals("0분 14초, 30.5 그램, 초당 4.2 그램", l.update(inp(14.7, step = 2)))
    }

    @Test fun `마지막 단계 도달은 한 번, 끝 시각 넘김도 한 번`() {
        val l = started()
        assertEquals("목표 250 그램 도달, 1분 30초에 끝", l.update(inp(89.0, step = 2, reached = true)))
        assertNull(l.update(inp(89.5, step = 2, reached = true)))
        assertEquals("끝 시각, 무게가 멈추면 끝납니다", l.update(inp(90.0, step = 2, reached = true)))
        assertNull(l.update(inp(90.5, step = 2, reached = true)))
    }

    @Test fun `Paused·Idle·멈춘 값에서는 5초 요약 없음`() {
        val l = started()
        assertNull(l.update(inp(5.0, stale = true)))
        assertNull(l.update(inp(10.0, phase = BrewPhase.Paused)))
        assertNull(l.update(inp(15.0, phase = BrewPhase.Paused)))
        assertNull(l.update(inp(20.0, phase = BrewPhase.Idle, step = null)))
    }

    @Test fun `무게 낭독 문구는 표시 문자열이 500 ms 그대로일 때 안정 문구`() {
        val d = SpokenDebounce("0.0")
        assertEquals("0.0" to false, d.update("0.0", 0))
        assertEquals("0.0" to false, d.update("1.0", 1_000))   // 바뀐 직후: 아직 이전 문구(1초 갱신은 다음 프레임부터)
        assertEquals("0.0" to false, d.update("1.0", 1_400))
        assertEquals("1.0" to false, d.update("1.0", 1_500))   // 500 ms 그대로
        // 계속 바뀌면 1초마다 "변하는 중"
        assertEquals("1.0" to false, d.update("1.1", 1_600))
        assertEquals("1.0" to false, d.update("1.2", 1_700))
        assertEquals("1.2" to false, d.update("1.2", 2_200))
        for (t in 2_300..3_200 step 100) assertEquals("1.2" to false, d.update("v$t", t.toLong()))   // 1초가 안 됨: 그대로
        assertEquals("v3300" to true, d.update("v3300", 3_300))   // 1초 넘게 계속 바뀜: 그때 값 + 변하는 중
    }
}
