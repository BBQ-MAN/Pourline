package com.timemoredot.brew

/** 파생 일정의 기대값(architecture.md 8.3 밸브 표). 레시피에는 넣지 않는다. */
val HARIO_VALVES = listOf(ValveEvent(0, false), ValveEvent(120, true))
val DEVIL_VALVES = listOf(ValveEvent(0, true), ValveEvent(75, false), ValveEvent(105, true))

private fun st(g: Int, at: Int, open: Boolean?) = RecipeStep("${g}g@$at", g.toDouble(), at, open)

/** 8.3 밸브 표의 하리오 스위치 기본 단계 값(이름은 짧게). finishAtSec null → finishSec 150. */
fun hario() = Recipe(0, "하리오", 20.0, 240.0, listOf(st(240, 10, false), st(240, 120, false), st(240, 150, true)))

/** 8.3 밸브 표의 테츠 카스야 악마의 레시피 단계 값(이름은 짧게). */
fun devil() = Recipe(
    0, "악마", 20.0, 280.0,
    listOf(st(60, 10, true), st(120, 40, true), st(120, 75, true), st(280, 85, false), st(280, 105, false), st(280, 180, true)),
    180,
)

/** 같은 단계에서 밸브만 뺀 레시피(모든 valveOpen null). */
fun Recipe.withoutValve() = copy(steps = steps.map { it.copy(valveOpen = null) })

/** (누적 g, targetAtSec, valveOpen) 단계로 만든 레시피. */
fun valveRecipe(vararg s: Triple<Int, Int, Boolean?>) =
    Recipe(0, "v", 15.0, s.last().first.toDouble(), s.map { st(it.first, it.second, it.third) })
