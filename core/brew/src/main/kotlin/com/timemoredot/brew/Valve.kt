package com.timemoredot.brew

import kotlin.math.ceil

/** 밸브 상태 변경 하나. atSec = 앱 타이머 경과 초(일시정지 제외), open = 이 시각부터의 상태(true = 열림).
 *  Recipe.valveSchedule이 단계에서 만드는 파생 값이라 저장하지 않는다. */
data class ValveEvent(val atSec: Int, val open: Boolean)

/** S1 밸브 칸 값. secondsToNext = ceil(next.atSec − 경과), next == null이면 null. */
data class ValveUi(val open: Boolean, val next: ValveEvent?, val secondsToNext: Int?)

/** 일정을 다시 검사하지 않는다. 정확히 atSec에 닿은 순간은 이미 그 이벤트 상태다. */
fun valveStateAt(schedule: List<ValveEvent>, elapsedSec: Double): ValveUi? {
    if (schedule.isEmpty()) return null
    val i = schedule.indexOfLast { it.atSec <= elapsedSec }.coerceAtLeast(0)
    val next = schedule.getOrNull(i + 1)
    return ValveUi(schedule[i].open, next, next?.let { ceil(it.atSec - elapsedSec).toInt() })
}

/** recipe null·Finished → null, Idle·WaitingForPour → 0 s(붓기 전 준비 상태), Running·Paused → 타이머 경과. */
fun valveUiFor(phase: BrewPhase, recipe: Recipe?, timer: TimerSnapshot, nowNanos: Long): ValveUi? {
    val schedule = recipe?.valveSchedule ?: return null
    return when (phase) {
        BrewPhase.Finished -> null
        BrewPhase.Idle, BrewPhase.WaitingForPour -> valveStateAt(schedule, 0.0)
        BrewPhase.Running, BrewPhase.Paused -> valveStateAt(schedule, timer.elapsedNanos(nowNanos) / 1e9)
    }
}
