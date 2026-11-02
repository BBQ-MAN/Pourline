package com.timemoredot.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset

/** ui-design 6.6.1 모션 토큰. 모양 겹 애니메이션 값은 이 객체에만 둔다(architecture.md 9.1). */
object AppMotion {
    /** 리본 머리 위치(데이터 좌표: x = 경과 ms, y = g). 임계 감쇠, 램프 지연 51.6 ms. */
    val follow = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium, visibilityThreshold = Offset(1f, 0.01f))
    /** 꼬리 길이의 유량 f, 목표선 현재 구간 굵기. */
    val calm = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
    /** 서버 → 그래프 전환 진행도 p 0 → 1. */
    val morph = spring(dampingRatio = 1f, stiffness = 225f, visibilityThreshold = 0.002f)
    /** 단계 표식 채움 scale 0.6 → 1(크기만 오버슈트). */
    val drop = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)
    /** 영점 확인 물방울 낙하. */
    val fall = tween<Float>(320, easing = CubicBezierEasing(0.3f, 0f, 1f, 1f))
    /** 영점 물결 고리. */
    val ripple = tween<Float>(900, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
    /** 색 숨: 들어감 250 ms, 나옴 350 ms. */
    val tintIn = tween<Float>(250, easing = FastOutSlowInEasing)
    val tintOut = tween<Float>(350, easing = FastOutSlowInEasing)
    /** 화면 전환 fade(들어옴 200 ms, 나감 150 ms). */
    val navEnter = tween<Float>(200)
    val navExit = tween<Float>(150)
}
