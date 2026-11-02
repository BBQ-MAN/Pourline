package com.timemoredot.app.ui.recipes

import com.timemoredot.app.data.DefaultRecipes

/** 기본 레시피 5개에 id 1~5를 준 것(DB가 넣은 뒤의 모양). */
object RFx {
    val all = DefaultRecipes.all.mapIndexed { i, r -> r.copy(id = i + 1L) }
    val v60 = all[0]
    val hario = all[1]
    val devil = all[4]
}
