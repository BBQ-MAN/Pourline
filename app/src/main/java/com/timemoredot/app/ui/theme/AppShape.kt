package com.timemoredot.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** ui-design 6.4. 이 앱이 더하는 모양. */
object AppShape {
    /** 물방울 기본형(끝이 위, 단위 상자 100 × 140)을 상자 크기에 맞춘 Shape. */
    val drop: Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            Outline.Generic(Path().also { dropPath(it, DROP_BASE_D, size.width / 100f, size.height / 140f) })
    }

    /** 위가 열린 둥근 서버(단위 상자 100 × 100) 닫힌 경로. 벽은 윗변을 뺀 열린 선(vesselWallPath). */
    val vessel: Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            Outline.Generic(Path().also { vesselPath(it, size.width / 100f, size.height / 100f, closed = true) })
    }

    /** 정지 블롭: 원을 72점으로 표본해 바깥 법선(반지름) 방향으로 δ만큼 밀고 Catmull-Rom → 3차 베지어로 잇는다. 랜덤 없음. */
    val blob: Shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val n = 72
            val r = min(size.width, size.height) / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val p = List(n) { i ->
                val t = 2.0 * PI * i / n
                val d = 0.06 * r * (sin(3 * t + 0.6) + 0.5 * sin(5 * t - 0.8))
                val rr = (r + d).toFloat()
                Offset(cx + rr * cos(t).toFloat(), cy + rr * sin(t).toFloat())
            }
            val path = Path().apply {
                moveTo(p[0].x, p[0].y)
                for (i in 0 until n) {
                    val p0 = p[(i + n - 1) % n]; val p1 = p[i]; val p2 = p[(i + 1) % n]; val p3 = p[(i + 2) % n]
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y,
                    )
                }
                close()
            }
            return Outline.Generic(path)
        }
    }
}

/** 기본형 물방울의 꼬리 길이 D(단위). */
const val DROP_BASE_D = 92f

/** AppShape.drop 일반형을 path에 다시 쓴다(reset 후). 단위 좌표 × (sx, sy), 원점 = 단위 상자 왼쪽 위. 그리기 단계에서 새 Path를 만들지 않으려고 받은 path를 고친다. */
fun dropPath(path: Path, d: Float, sx: Float, sy: Float) {
    path.reset()
    fun x(v: Float) = v * sx
    fun y(v: Float) = v * sy
    path.moveTo(x(50f), y(92f - d))
    path.cubicTo(x(50f), y(92f - d), x(92f), y(92f - 0.37f * d), x(92f), y(92f))
    path.cubicTo(x(92f), y(118f), x(73f), y(140f), x(50f), y(140f))
    path.cubicTo(x(27f), y(140f), x(8f), y(118f), x(8f), y(92f))
    path.cubicTo(x(8f), y(92f - 0.37f * d), x(50f), y(92f - d), x(50f), y(92f - d))
    path.close()
}

/** AppShape.vessel. closed = false면 윗변을 뺀 벽(오른쪽 위 → 바닥 → 왼쪽 위). */
fun vesselPath(path: Path, sx: Float, sy: Float, closed: Boolean) {
    path.reset()
    if (closed) { path.moveTo(11f * sx, 0f); path.lineTo(89f * sx, 0f) } else path.moveTo(89f * sx, 0f)
    path.cubicTo(95f * sx, 18f * sy, 100f * sx, 42f * sy, 100f * sx, 61f * sy)
    path.cubicTo(100f * sx, 83f * sy, 79f * sx, 100f * sy, 50f * sx, 100f * sy)
    path.cubicTo(21f * sx, 100f * sy, 0f, 83f * sy, 0f, 61f * sy)
    path.cubicTo(0f, 42f * sy, 5f * sx, 18f * sy, 11f * sx, 0f)
    if (closed) path.close()
}
