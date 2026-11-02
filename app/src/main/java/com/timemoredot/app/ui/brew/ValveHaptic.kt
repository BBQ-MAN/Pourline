package com.timemoredot.app.ui.brew

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** 밸브 변경 진동 1회(ui-design 4.11 신호 3, architecture.md 9.4). 시스템 터치 진동 설정을 따른다. 진동 권한·진동기 API 없음. */
fun performValveHaptic(view: View) {
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
}

/** Channel에 쌓였다가 늦게 온 진동(1 s 넘음)은 버린다. */
fun hapticFresh(nowNanos: Long, atNanos: Long) = nowNanos - atNanos <= 1_000_000_000L
