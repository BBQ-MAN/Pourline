package com.timemoredot.brew

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class RecipeStep(
    val name: String,
    val targetGrams: Double,   // 누적 목표 무게 g (영점 뒤 물 기준)
    val targetAtSec: Int,      // 이 무게에 도달할 목표 경과 초. 기다리기 단계는 "이 시각까지 기다린다"
    val valveOpen: Boolean? = null,  // 이 단계 동안의 밸브 상태(true = 열림). null = 밸브 없는 레시피. 기본값이라 옛 JSON도 읽힌다(architecture.md 7.1)
)

@Serializable
data class Recipe(
    val id: Long,              // 0 = 저장 전
    val name: String,          // 1~30자
    val doseGrams: Double,     // 원두 g, 0.1~100.0
    val waterGrams: Double,    // 물 g, > 0
    val steps: List<RecipeStep>,   // 1개 이상. targetGrams는 앞 단계 이상(같으면 기다리기 단계), targetAtSec 엄격히 오름차순. valveOpen은 모두 null 또는 모두 non-null (검증은 architecture.md 9.6)
    val finishAtSec: Int? = null,  // 레시피 끝 경과 초. 기본값 null이라 이 필드가 없는 JSON도 읽힌다
) {
    /** 자동 완료 판정을 시작하는 경과 초. 게터만 있어 직렬화되지 않는다. */
    val finishSec: Int get() = finishAtSec ?: steps.last().targetAtSec

    /** 단계에서 파생한 밸브 일정(architecture.md 7.1). 단계 시작 = 앞 단계 targetAtSec(첫 단계 0), 같은 상태는 합친다.
     *  하나라도 valveOpen == null이면 빈 목록(밸브 없음). 게터라 직렬화되지 않는다. */
    val valveSchedule: List<ValveEvent>
        get() {
            if (steps.any { it.valveOpen == null }) return emptyList()
            return steps.mapIndexed { i, s -> ValveEvent(if (i == 0) 0 else steps[i - 1].targetAtSec, s.valveOpen!!) }
                .filterIndexed { i, e -> i == 0 || e.open != steps[i - 1].valveOpen }
        }

    /** (0 g, 0 s)에서 시작해 단계 사이를 선형 보간한 계획 무게 g. 마지막 단계 뒤는 마지막 값. */
    fun plannedGramsAt(elapsedSec: Double): Double {
        var prevSec = 0.0
        var prevGrams = 0.0
        for (s in steps) {
            if (elapsedSec < s.targetAtSec) {
                return if (elapsedSec <= prevSec) prevGrams
                else prevGrams + (s.targetGrams - prevGrams) * (elapsedSec - prevSec) / (s.targetAtSec - prevSec)
            }
            prevSec = s.targetAtSec.toDouble()
            prevGrams = s.targetGrams
        }
        return prevGrams
    }

    /** 계획상 weightGrams에 도달하는 경과 초. 마지막 단계 무게 이상이면 마지막 단계 시각. */
    fun plannedSecFor(weightGrams: Double): Double {
        var prevSec = 0.0
        var prevGrams = 0.0
        for (s in steps) {
            if (weightGrams < s.targetGrams) {
                return if (weightGrams <= prevGrams) prevSec
                else prevSec + (s.targetAtSec - prevSec) * (weightGrams - prevGrams) / (s.targetGrams - prevGrams)
            }
            prevSec = s.targetAtSec.toDouble()
            prevGrams = s.targetGrams
        }
        return prevSec
    }

    /** 엔진 paceDeltaSec용(architecture.md 7.1). 기다리기 단계가 만드는 평평한 구간 [a, b]의 무게 L ± toleranceGrams 안이면
     *  elapsedSec.coerceIn(a, b), 아니면 plannedSecFor(weightGrams). 기다리기 단계가 없으면 plannedSecFor(weightGrams)와 같다. */
    fun plannedSecFor(weightGrams: Double, elapsedSec: Double, toleranceGrams: Double): Double {
        for (i in 1..steps.lastIndex) {
            if (!isWaitStep(i) || isWaitStep(i - 1)) continue   // 평평한 구간의 첫 기다리기 단계만
            if (abs(weightGrams - steps[i].targetGrams) > toleranceGrams) continue
            var last = i
            while (last < steps.lastIndex && isWaitStep(last + 1)) last++
            return elapsedSec.coerceIn(steps[i - 1].targetAtSec.toDouble(), steps[last].targetAtSec.toDouble())
        }
        return plannedSecFor(weightGrams)
    }
}

/** 기다리기 단계 = index > 0이고 누적 g가 앞 단계와 같다(물을 붓지 않고 targetAtSec까지 기다림, architecture.md 7.1). */
fun Recipe.isWaitStep(index: Int): Boolean = index > 0 && steps[index].targetGrams == steps[index - 1].targetGrams
