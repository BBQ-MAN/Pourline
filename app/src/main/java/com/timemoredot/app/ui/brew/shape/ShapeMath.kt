package com.timemoredot.app.ui.brew.shape

import com.timemoredot.brew.STEP_TOLERANCE_GRAMS
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 모양 겹 수치(architecture.md 9.1, ui-design 4.4·4.10). 길이 dp, 무게 g, 유량 g/s. Android 그래픽 타입 없음. */
const val MORPH_POINTS = 240              // 서버 벽 → 축 선 L 전환의 호 길이 표본 수
const val VESSEL_FILL_FRACTION = 0.92f    // 서버 채움 가능 높이 = 바닥에서 92%
const val VESSEL_MAX_LEVEL = 1.08f

/** 리본 굵기 = min(10, 2.5 + 0.9 × max(0, flow)) dp. */
fun ribbonWidthDp(flowGramsPerSec: Float): Float = min(10f, 2.5f + 0.9f * max(0f, flowGramsPerSec))

/** 머리 꼬리 길이 = min(18, 6 + 1.2 × max(0, f)) dp. */
fun headTailDp(filteredFlowGramsPerSec: Float): Float = min(18f, 6f + 1.2f * max(0f, filteredFlowGramsPerSec))

/** 서버 수위 h(0~1.08). grams null → null, waterGrams null → 1.0. */
fun vesselLevel(grams: Double?, waterGrams: Double?): Float? = when {
    grams == null -> null
    waterGrams == null -> 1f
    else -> (grams / waterGrams).toFloat().coerceIn(0f, VESSEL_MAX_LEVEL)
}

/** 수위 h의 채움 높이(바닥에서 위로, dp). */
fun vesselFillHeightDp(level: Float, vesselHeightDp: Float): Float = VESSEL_FILL_FRACTION * vesselHeightDp * level

/** span ÷ 후보 ≤ axisLengthDp ÷ minSpacingDp인 가장 작은 후보(오름차순). 없으면 마지막 후보. */
fun chooseTickStep(span: Double, axisLengthDp: Float, minSpacingDp: Float, candidates: DoubleArray): Double {
    val allowed = axisLengthDp / minSpacingDp
    return candidates.firstOrNull { span / it <= allowed } ?: candidates.last()
}

/** 위에서부터 그리고, 마지막으로 그린 라벨 상자와 겹치는 라벨은 뺀다. */
fun visibleLabelIndices(topsDp: FloatArray, heightsDp: FloatArray): IntArray {
    val out = ArrayList<Int>(topsDp.size)
    var lastBottom = Float.NEGATIVE_INFINITY
    for (i in topsDp.indices) {
        if (topsDp[i] >= lastBottom) { out += i; lastBottom = topsDp[i] + heightsDp[i] }
    }
    return out.toIntArray()
}

/** columnOf(i)(감소하지 않음)마다 마지막 표본 인덱스. 결과 길이 ≤ 칸 수. */
fun thinnedSampleIndices(size: Int, columnOf: (Int) -> Int): IntArray {
    if (size == 0) return IntArray(0)
    val out = IntArray(size)
    var n = 0
    for (i in 0 until size) {
        if (i == size - 1 || columnOf(i + 1) != columnOf(i)) out[n++] = i
    }
    return out.copyOf(n)
}

// AppShape.vessel 경로(단위 상자 100 × 100)의 벽: 오른쪽 위 (89, 0) → 바닥 → 왼쪽 위 (11, 0). 3차 곡선 4개.
private val VESSEL_WALL_CUBICS = arrayOf(
    floatArrayOf(89f, 0f, 95f, 18f, 100f, 42f, 100f, 61f),
    floatArrayOf(100f, 61f, 100f, 83f, 79f, 100f, 50f, 100f),
    floatArrayOf(50f, 100f, 21f, 100f, 0f, 83f, 0f, 61f),
    floatArrayOf(0f, 61f, 0f, 42f, 5f, 18f, 11f, 0f),
)
private const val CUBIC_SEGMENTS = 32

/** 벽(윗변 뺀 열린 경로)을 3차 곡선 고정 분할로 펼친 점열 [x0, y0, x1, y1, …], dp. */
fun vesselWallPolyline(widthDp: Float, heightDp: Float): FloatArray {
    val sx = widthDp / 100f
    val sy = heightDp / 100f
    val out = FloatArray((VESSEL_WALL_CUBICS.size * CUBIC_SEGMENTS + 1) * 2)
    var n = 0
    out[n++] = VESSEL_WALL_CUBICS[0][0] * sx; out[n++] = VESSEL_WALL_CUBICS[0][1] * sy
    for (c in VESSEL_WALL_CUBICS) {
        for (k in 1..CUBIC_SEGMENTS) {
            val t = k / CUBIC_SEGMENTS.toFloat()
            val u = 1 - t
            val a = u * u * u; val b = 3 * u * u * t; val d = 3 * u * t * t; val e = t * t * t
            out[n++] = (a * c[0] + b * c[2] + d * c[4] + e * c[6]) * sx
            out[n++] = (a * c[1] + b * c[3] + d * c[5] + e * c[7]) * sy
        }
    }
    return out
}

/** 호 길이 기준 count점으로 다시 표본. 첫·끝 점은 입력과 같다. */
fun resampleByArcLength(polyline: FloatArray, count: Int = MORPH_POINTS): FloatArray {
    val pts = polyline.size / 2
    val cum = FloatArray(pts)
    for (i in 1 until pts) {
        cum[i] = cum[i - 1] + hypot(polyline[2 * i] - polyline[2 * i - 2], polyline[2 * i + 1] - polyline[2 * i - 1])
    }
    val total = cum[pts - 1]
    val out = FloatArray(count * 2)
    var seg = 1
    for (k in 0 until count) {
        if (k == count - 1) { out[2 * k] = polyline[2 * pts - 2]; out[2 * k + 1] = polyline[2 * pts - 1]; break }
        val d = total * k / (count - 1)
        while (seg < pts - 1 && cum[seg] < d) seg++
        val l = cum[seg] - cum[seg - 1]
        val t = if (l > 0f) ((d - cum[seg - 1]) / l).coerceIn(0f, 1f) else 0f
        out[2 * k] = polyline[2 * seg - 2] + (polyline[2 * seg] - polyline[2 * seg - 2]) * t
        out[2 * k + 1] = polyline[2 * seg - 1] + (polyline[2 * seg + 1] - polyline[2 * seg - 1]) * t
    }
    return out
}

/** 같은 길이의 두 점열을 점끼리 직선 보간해 out에 쓴다(할당 없음). p = 1이면 out == to. */
fun lerpPoints(from: FloatArray, to: FloatArray, p: Float, out: FloatArray) {
    for (i in out.indices) out[i] = if (p >= 1f) to[i] else from[i] + (to[i] - from[i]) * p
}

/** 요약판(S6·S10) 단계 표식 "도달": 최종 무게 ≥ 단계 목표 − 단계 허용 오차(ui-design 4.4·4.10, 15절 요청 2). 오차는 :core:brew 상수 하나. */
fun summaryStepReached(finalWeightGrams: Double?, stepTargetGrams: Double): Boolean =
    finalWeightGrams != null && finalWeightGrams >= stepTargetGrams - STEP_TOLERANCE_GRAMS
