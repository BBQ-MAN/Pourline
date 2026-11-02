# FIX-4 구현 요약 (단계별 밸브 모델)

## :core:brew
- Recipe.kt: `RecipeStep.valveOpen: Boolean? = null` 추가. `Recipe` 생성자에서 `valveSchedule` 제거, 파생 게터로(단계 시작 = 앞 단계 targetAtSec, 같은 상태 합침, null 하나라도 있으면 빈 목록). 세 인자 `plannedSecFor(weight, elapsed, tolerance)`(평평한 구간 [a, b]에서 elapsed.coerceIn), 최상위 `Recipe.isWaitStep(index)`.
- Valve.kt: `ValveIssueKind`·`ValveIssue`·`validateValveSchedule`·`MAX_VALVE_SEC` 삭제, `ValveEvent`의 `@Serializable` 삭제. `valveStateAt`·`valveUiFor`·`ValveUi` 그대로.
- BrewEngine.kt: `guideAt`의 paceDeltaSec만 세 인자 `plannedSecFor(weight, elapsedSec, config.stepToleranceGrams)`로. valveHaptic·ValveCue·checkValve·valveCheckedNanos 그대로(게터를 읽음). StepGuide 주석만 갱신, 공개 시그니처 불변.

## :app
- Entities.kt·Repositories.kt·AppDatabase.kt(시드): valveJson 열·읽기·쓰기 삭제. 1.json은 빌드 뒤 바이트 단위로 같음(identityHash 6d9f61fa4f808273048039f7ba96e8a3). Migration 없음.
- DefaultRecipes.kt: 8.3 밸브 표(하리오 3단계 finishAtSec null, 악마 6단계 finishAtSec 180).

## 테스트
- 삭제: ValveScheduleValidationTest.
- 새로: RecipeValveJsonTest(다시 씀, 옛 stepsJson·recipeJson, 중단 FIX-4의 "valveSchedule" 키 무시), RecipeValveScheduleTest, RecipePaceTest, BrewEngineWaitStepTest.
- 수정: ValveFixtures(hario·devil을 8.3 단계로, `withoutValve()`·`valveRecipe()`), ValveStateTest·BrewEngineValveTest(빈 일정 → 모든 valveOpen null, 3599 s 레시피 → 단계 (240,10,T)·(240,3599,T)·(240,3600,F)). 시나리오·시각(120·75·105 s) 그대로.
- M6: DefaultRecipesTest 하리오·악마 행 + 누적 g 단언(첫 단계 > 0, 앞 단계 이상) + 밸브 단언. RoomRecipeRepositoryTest·RoomBrewRepositoryTest는 테스트 추가만.
- BrewEngineTest 자동 시작 임계값 두 테스트(threshold2DoesNotStartAt1_4, settingsChangedInIdleIsUsedByLaterTareAndThreshold)는 이미 FIX-1 무장 판독(0.0) 뒤 임계값 아래(1.9·1.4)·위(2.1) 판독으로 가른다. 재영점 뒤 무장 판독을 빼면 2.1이 무장 판독으로 쓰이지 못해(|2.1| > 2.0) Running 단언이 실패하므로 공허 통과가 아니다. 바꾸지 않았다.

## 명세와 다른 점
- 없음. "Recipe 생성자에 valveSchedule 없음"은 직렬화 descriptor와 백킹 필드 부재로 테스트한다.

## verify
- `verify.sh :core:brew:test :app:testDebugUnitTest` 종료 코드 0, 로그 `_workspace/build/verify_20261030_111721.log`.
