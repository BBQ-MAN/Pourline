package com.timemoredot.app.data

import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep

/** 값 출처와 규칙은 architecture.md 8.3. 이름은 사용자 데이터라 리소스로 번역하지 않는다. */
object DefaultRecipes {
    val all: List<Recipe> = listOf(
        Recipe(
            0, "V60 기본", 15.0, 250.0,
            listOf(
                RecipeStep("뜸 60 g, 30초 기다리기 (95°C, 중간-가는 분쇄)", 60.0, 10),
                RecipeStep("0:40까지 기다리기", 60.0, 40),
                RecipeStep("0:40 나머지 물을 작은 원으로 천천히 250 g까지", 250.0, 90),
            ),
        ),
        Recipe(
            0, "하리오 스위치 기본", 20.0, 240.0,
            listOf(
                RecipeStep("스위치 닫고(올림) 240 g 한 번에 붓기 (중간-가는 분쇄)", 240.0, 10, valveOpen = false),
                RecipeStep("2:00까지 담가 두기", 240.0, 120, valveOpen = false),
                RecipeStep("2:00 스위치 눌러 열고 내려받기", 240.0, 150, valveOpen = true),
            ),
        ),
        Recipe(
            0, "6888 레시피", 20.0, 300.0,
            listOf(
                RecipeStep("뜸 60 g, 0:40까지 기다리기 (92°C, 중간 분쇄)", 60.0, 10),
                RecipeStep("0:40까지 기다리기", 60.0, 40),
                RecipeStep("0:40 2차 140 g까지", 140.0, 50),
                RecipeStep("1:10 3차 220 g까지", 220.0, 80),
                RecipeStep("1:40 4차 300 g까지 · 2:45 전후 추출 끝", 300.0, 110),
            ),
            finishAtSec = 165,
        ),
        Recipe(
            0, "테츠 카스야 기본 레시피", 20.0, 300.0,
            listOf(
                RecipeStep("1차 50 g (92°C, 굵은 분쇄)", 50.0, 5),
                RecipeStep("0:45 2차 120 g까지", 120.0, 50),
                RecipeStep("1:30 3차 180 g까지", 180.0, 95),
                RecipeStep("2:15 4차 240 g까지", 240.0, 140),
                RecipeStep("3:00 5차 300 g까지 · 3:30에 드리퍼 내리기", 300.0, 185),
            ),
            finishAtSec = 210,
        ),
        Recipe(
            0, "테츠 카스야 악마의 레시피", 20.0, 280.0,
            listOf(
                RecipeStep("스위치 열고 뜸 60 g (90°C, 약간 가는 분쇄)", 60.0, 10, valveOpen = true),
                RecipeStep("0:30 120 g까지 · 이어서 찬물을 섞어 주전자를 70°C로", 120.0, 40, valveOpen = true),
                RecipeStep("1:15까지 기다리기", 120.0, 75, valveOpen = true),
                RecipeStep("1:15 스위치 닫고 70°C 물로 280 g까지", 280.0, 85, valveOpen = false),
                RecipeStep("1:45까지 담가 두기", 280.0, 105, valveOpen = false),
                RecipeStep("1:45 스위치 열기 · 3:00 추출 끝", 280.0, 180, valveOpen = true),
            ),
            finishAtSec = 180,
        ),
    )
}
