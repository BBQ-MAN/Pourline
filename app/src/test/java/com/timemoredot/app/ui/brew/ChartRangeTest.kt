package com.timemoredot.app.ui.brew

import com.timemoredot.app.data.DefaultRecipes
import org.junit.Assert.assertEquals
import org.junit.Test

/** ui-design 4.4 축 범위 늘리기와 짧은 그림 영역의 눈금 간격(JVM). */
class ChartRangeTest {
    private val v60 = DefaultRecipes.all.first { it.name == "V60 기본" }   // 물 250 g, 마지막 목표 90 s
    private val g = doubleArrayOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0)

    private fun assertRange(x: Double, y: Double, r: Pair<Double, Double>) { assertEquals(x, r.first, 1e-9); assertEquals(y, r.second, 1e-9) }

    @Test fun `목표대로 따라간 추출은 목표 직전에 축이 늘지 않는다`() {
        assertRange(99.0, 275.0, chartRange(89.5, 249.6, v60, false, 200f, 40f))
        assertRange(99.0, 275.0, chartRange(94.0, 262.0, v60, false, 200f, 40f))
    }

    @Test fun `목표의 105 퍼센트를 넘으면 1·5배로 늘린다`() {
        assertEquals(150.0, chartRange(95.0, 0.0, v60, false, 200f, 40f).first, 1e-9)
        assertEquals(500.0, chartRange(0.0, 263.0, v60, false, 200f, 40f).second, 1e-9)   // 275 × 1.5 = 412.5 → 눈금 100 단위 올림
    }

    @Test fun `레시피 없이는 끝의 90 퍼센트에서 늘린다`() {
        assertRange(60.0, 100.0, chartRange(54.0, 90.0, null, false, 200f, 40f))
        assertRange(90.0, 160.0, chartRange(54.1, 90.1, null, false, 200f, 40f))   // 150 → 눈금 20 단위 올림
    }

    @Test fun `짧은 그림 영역에서도 축 끝 이하의 눈금 간격`() {
        assertEquals(200.0, tickStepWithin(264.0, 30f, 64f, g), 1e-9)   // chooseTickStep은 500을 내지만 끝 264를 넘는다
        assertEquals(100.0, tickStepWithin(264.0, 200f, 40f, g), 1e-9)   // 넉넉하면 chooseTickStep 그대로
        assertEquals(10.0, tickStepWithin(5.0, 30f, 64f, g), 1e-9)
    }
}
