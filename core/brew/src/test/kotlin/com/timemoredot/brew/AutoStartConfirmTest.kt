package com.timemoredot.brew

import com.timemoredot.brew.BrewInput.ConnectionChanged
import com.timemoredot.brew.BrewInput.Tared
import com.timemoredot.brew.BrewPhase.Running
import com.timemoredot.brew.BrewPhase.WaitingForPour
import com.timemoredot.protocol.TimerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** architecture.md 7.2 자동 시작 확인 구간(F-B4-1)과 무게 급감 시 유량 창 비움(O-B8-1). 시계열은 프로브 B 실측(수신 시각 그대로). */
class AutoStartConfirmTest {
    private fun armed(): Rig = Rig().apply {
        feed(Tared(sec(340.978)))
        feed(reading(sec(341.278), 0.0))   // 무장
    }

    /** 04_device_b_B4.log +342.4~343.1 s: 영점 뒤 47.7 g 물건을 들어 올림. 손 압력 2.3·5.2 g 뒤 음수. */
    private val b4Lift = listOf(   // (수신 s, 무게 g, 저울 유량 g/s) 로그 그대로. 0.3 g에서 감소 중인데 유량 1.7(C-01-7)
        Triple(342.478, 2.3, 0.0), Triple(342.537, 5.2, 5.2), Triple(342.656, 0.3, 1.7), Triple(342.781, 4.0, 4.0),
        Triple(342.839, -2.5, 0.0), Triple(342.957, -29.8, 0.0), Triple(343.077, -46.9, 0.0), Triple(343.135, -47.5, 0.0),
        Triple(343.259, -47.7, 0.0), Triple(343.381, -47.7, 0.0),
    )

    /** 04_device_b_B8.log +218.8~219.6 s: 실제 붓기 시작(단조 증가). */
    private val b8Pour = listOf(
        218.819 to 0.6, 218.879 to 2.8, 218.999 to 4.4, 219.086 to 5.2, 219.179 to 7.2,
        219.304 to 8.0, 219.422 to 9.5, 219.477 to 10.2, 219.600 to 12.1,
    )

    @Test fun b4LiftDoesNotStart() {
        val r = armed()
        b4Lift.forEach { (t, g, f) -> r.feed(reading(sec(t), g, f)) }
        assertEquals(WaitingForPour, r.state.phase)
        assertTrue(r.all.none { it is BrewEffect.SendTimer })
        assertEquals(0.0, r.state.flowGramsPerSec, 0.0)   // 급감 뒤 유량 창을 비워 들기 전 유량이 남지 않는다
    }

    @Test fun b8PourStartsAfterConfirmBackdatedToFirstCrossing() {
        val r = armed()
        var startedAt: Double? = null
        for ((t, g) in b8Pour) {
            val fx = r.feed(reading(sec(t), g, g))
            if (fx.any { it == BrewEffect.SendTimer(TimerAction.Start) }) startedAt = t
        }
        assertEquals(Running, r.state.phase)
        assertEquals(219.422, startedAt!!, 1e-9)                 // 처음 넘음(2.8 g, 218.879) + 500 ms 이상인 첫 샘플
        assertEquals(sec(218.879), r.state.startedAtNanos)       // 시작 시각은 처음 넘은 샘플로 소급
        assertEquals(1, r.count { it == BrewEffect.SendTimer(TimerAction.Start) })
        assertEquals(PhaseCause.AutoStart, r.phaseChanges().last().cause)
        // 소급 샘플: 2.8 g(offset 0)부터 빠짐없이, offset은 처음 넘은 시각 기준
        assertEquals(8, r.samples.size)
        assertEquals(0, r.samples.offsetMs(0))
        assertEquals(2.8f, r.samples.weightGrams(0))
        assertEquals(543, r.samples.offsetMs(5))                  // 219.422 − 218.879
        assertEquals(721, r.samples.offsetMs(7))
    }

    @Test fun dipBelowThresholdInsideConfirmCancelsThenRestarts() {
        val r = armed()
        val t0 = sec(342.0)
        r.feedAll(reading(t0, 2.0), reading(t0 + ms(100), 3.0), reading(t0 + ms(200), 0.8))   // 취소
        r.feedAll(reading(t0 + ms(300), 2.0), reading(t0 + ms(400), 3.0), reading(t0 + ms(500), 4.0), reading(t0 + ms(600), 5.0), reading(t0 + ms(700), 6.0))
        assertEquals(WaitingForPour, r.state.phase)   // 다시 넘은 300 ms부터 400 ms
        r.feed(reading(t0 + ms(800), 7.0))
        assertEquals(Running, r.state.phase)
        assertEquals(t0 + ms(300), r.state.startedAtNanos)
    }

    @Test fun dropAboveThresholdInsideConfirmCancels() {
        val r = armed()
        val t0 = sec(342.0)
        r.feedAll(reading(t0, 3.0), reading(t0 + ms(100), 9.0), reading(t0 + ms(200), 6.5))   // 2.5 g 급감
        r.feedAll(reading(t0 + ms(300), 7.0), reading(t0 + ms(400), 8.0), reading(t0 + ms(500), 9.0), reading(t0 + ms(600), 10.0))
        assertEquals(WaitingForPour, r.state.phase)   // 급감 다음 샘플(300 ms)부터 다시 센다
        r.feed(reading(t0 + ms(800), 11.0))
        assertEquals(Running, r.state.phase)
    }

    @Test fun disconnectInsideConfirmCancels() {
        val r = armed()
        val t0 = sec(342.0)
        r.feedAll(reading(t0, 2.0), reading(t0 + ms(100), 3.0), ConnectionChanged(false, t0 + ms(150)), ConnectionChanged(true, t0 + ms(2000)))
        r.feed(reading(t0 + ms(2100), 4.0))
        r.feed(reading(t0 + ms(2200), 5.0))
        assertEquals(WaitingForPour, r.state.phase)   // 끊기기 전 넘음부터 세지 않는다
    }

    @Test fun silentGapInsideConfirmCancels() {
        val r = armed()
        val t0 = sec(342.0)
        r.feedAll(reading(t0, 5.2), BrewInput.Tick(t0 + ms(3500)))   // 끊김 없이 NoData
        r.feed(reading(t0 + ms(4000), 5.0))
        assertEquals(WaitingForPour, r.state.phase)   // 공백을 확인 구간으로 세지 않는다
        r.feedAll(reading(t0 + ms(4100), 6.0), reading(t0 + ms(4500), 7.0))
        assertEquals(Running, r.state.phase)
        assertEquals(t0 + ms(4000), r.state.startedAtNanos)   // 공백 뒤 첫 샘플이 새 t₀
    }

    @Test fun slowWeightDecreaseKeepsScaleFlowAndSharpDropClearsIt() {
        val r = Rig()
        r.feed(BrewInput.UserTimer(TimerAction.Start, 0))
        for (k in 1..10) r.feed(reading(ms(100L * k), 50.0 - 0.5 * k, 0.4))   // 천천히 줄어도 저울 유량은 그대로(C-01-7)
        assertEquals(0.4, r.state.flowGramsPerSec, 1e-9)
        r.feed(reading(ms(1100), 47.0, 20.0))   // 들기 직전 손 압력
        r.feed(reading(ms(1200), -245.9, 0.0))  // 서버를 들어 올림
        assertEquals(0.0, r.state.flowGramsPerSec, 0.0)
    }
}
