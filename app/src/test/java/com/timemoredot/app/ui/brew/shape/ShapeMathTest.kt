package com.timemoredot.app.ui.brew.shape

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** architecture.md 9.1 ShapeMath.kt 경계값(JVM만, Robolectric 없음). */
class ShapeMathTest {
    private val e = 1e-4f

    @Test fun ribbonWidth() {
        assertEquals(2.5f, ribbonWidthDp(0f), e)
        assertEquals(7.0f, ribbonWidthDp(5f), e)
        assertEquals(9.997f, ribbonWidthDp(8.33f), e)
        assertEquals(10.0f, ribbonWidthDp(25f / 3f), e)
        assertEquals(10.0f, ribbonWidthDp(8.34f), e)
        assertEquals(10.0f, ribbonWidthDp(99.9f), e)
        assertEquals(2.5f, ribbonWidthDp(-1f), e)
    }

    @Test fun headTail() {
        assertEquals(6.0f, headTailDp(0f), e)
        assertEquals(16.8f, headTailDp(9f), e)
        assertEquals(18.0f, headTailDp(99.9f), e)
        assertEquals(6.0f, headTailDp(-1f), e)
    }

    @Test fun vessel() {
        assertEquals(1.0f, vesselLevel(250.0, 250.0)!!, e)
        assertEquals(0.5f, vesselLevel(125.0, 250.0)!!, e)
        assertEquals(1.08f, vesselLevel(280.0, 250.0)!!, e)
        assertEquals(0.0f, vesselLevel(-5.0, 250.0)!!, e)
        assertEquals(1.0f, vesselLevel(200.0, null)!!, e)
        assertNull(vesselLevel(null, 250.0))
        assertEquals(110.4f, vesselFillHeightDp(1.0f, 120f), e)
        assertEquals(119.232f, vesselFillHeightDp(1.08f, 120f), e)
    }

    @Test fun ticks() {
        assertEquals(20.0, chooseTickStep(100.0, 200f, 40f, doubleArrayOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0)), 1e-4)
        assertEquals(60.0, chooseTickStep(300.0, 300f, 40f, doubleArrayOf(15.0, 30.0, 60.0, 120.0, 300.0, 600.0)), 1e-4)
    }

    @Test fun labels() {
        assertArrayEquals(intArrayOf(0, 2), visibleLabelIndices(floatArrayOf(0f, 10f, 30f), floatArrayOf(16f, 16f, 16f)))
    }

    @Test fun thinning() {
        val cols = intArrayOf(0, 0, 1, 1, 1, 3)
        assertArrayEquals(intArrayOf(1, 4, 5), thinnedSampleIndices(6) { cols[it] })
        val r = thinnedSampleIndices(36_000) { (it * 300L / 36_000).toInt() }
        assertTrue(r.size <= 300)
    }

    @Test fun resample() {
        val wall = vesselWallPolyline(176f, 164f)
        val s = resampleByArcLength(wall)
        assertEquals(MORPH_POINTS * 2, s.size)
        assertEquals(wall[0], s[0], e); assertEquals(wall[1], s[1], e)
        assertEquals(wall[wall.size - 2], s[s.size - 2], e); assertEquals(wall[wall.size - 1], s[s.size - 1], e)
    }

    @Test fun `서버 벽에서 축 선 L로 전환 끝 점`() {
        val wall = resampleByArcLength(vesselWallPolyline(176f, 164f))
        // 축 선 L: x축 오른쪽 끝 (100, 100) → 원점 (0, 100) → y축 위 끝 (0, 0) (화면 좌표, y 아래로)
        val l = resampleByArcLength(floatArrayOf(100f, 100f, 0f, 100f, 0f, 0f))
        val out = FloatArray(MORPH_POINTS * 2)
        lerpPoints(wall, l, 1f, out)
        for (i in out.indices) assertEquals(l[i], out[i], e)
        assertEquals(100f, out[0], e); assertEquals(100f, out[1], e)
        assertEquals(0f, out[out.size - 2], e); assertEquals(0f, out[out.size - 1], e)
        val nearest = (0 until MORPH_POINTS).minOf { hypot(out[2 * it] - 0f, out[2 * it + 1] - 100f) }
        assertTrue("원점까지 $nearest", nearest <= 0.5f)
        // 중간 진행도는 두 끝 사이
        lerpPoints(wall, l, 0.5f, out)
        assertEquals((wall[0] + l[0]) / 2, out[0], e)
    }

    @Test fun `요약판 표식 도달은 STEP_TOLERANCE_GRAMS를 쓴다`() {
        assertTrue(summaryStepReached(249.0, 250.0))
        org.junit.Assert.assertFalse(summaryStepReached(248.9, 250.0))
        org.junit.Assert.assertFalse(summaryStepReached(null, 250.0))
        assertEquals(1.0, com.timemoredot.brew.STEP_TOLERANCE_GRAMS, 0.0)
        assertEquals(com.timemoredot.brew.STEP_TOLERANCE_GRAMS, com.timemoredot.brew.BrewConfig().stepToleranceGrams, 0.0)
    }
}
