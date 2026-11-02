# M10 구현 요약 — S7 레시피 목록, S8 레시피 편집, D3·D4

## 소스 (app/src/main/java/com/timemoredot/app/ui/recipes/)
- `RecipeListViewModel.kt`: ListLoad, RecipeListUiState(canStartBrew = phase Idle·WaitingForPour), onBrewClick(selectRecipe → OpenBrew), onRetry.
- `RecipeEditViewModel.kt`: 9.6 계약 전부(검증 9종, 밸브 규칙, onInsertValveStep, 끝 시각). 파싱 함수 parseStepGrams·parseStepTimeSec·isWaitStep은 이 파일 internal 최상위, toDoubleOrNull·toIntOrNull은 앞 둘 안에만 있다(원두는 S6의 parseDose 재사용).
- `RecipeText.kt`: S7 행 문구·낭독, 스위치 일정 문구(valveScheduleText).
- `RecipeListScreen.kt`(S7), `RecipeEditScreen.kt`(S8, D3 `DeleteRecipeDialog`, D4 `DiscardChangesDialog`, `ValveScheduleLine` S10 공용).
- Navigation.kt: Recipes·RecipeEdit 연결(추출 → goTab(Brew)), Icons.kt에 close·add·delete·arrow_upward·arrow_downward·more_vert 추가.

## 테스트
RecipeEditViewModelTest, RecipeListViewModelTest(FakeRecipeRepository, Robolectric 없음), StepDraftParsingTest, RecipeScreenTest(Robolectric: 뒤로 가기, D3·D4 복원, 넣기 알림 1회, 스위치 끔 노드 없음, S7 행 문구), RecipeCodeContractTest(코드 대조), RecipeScreenshotTest.

## 스크린샷
recipes_{loading,empty,loaded,error,brewing,first_run}_{light,dark}, recipes_first_run_light_font200, recipes_first_run_tablet_{light,dark}, recipe_edit_{loading,new,editing,invalid,saving,not_found,discard_confirm,delete_confirm,valve,valve_steps,valve_inserted,valve_off,step_error}_{light,dark}, recipe_edit_invalid_light_font200, recipe_edit_valve_light_font200. recipe_edit_valve_invalid 없음.

## 명세와 다른 점·판단
1. 순서 이동은 단계 카드 전체(시각 포함)를 맞바꾼다. 그래서 이동 직후 시각 오름차순이 깨져 valvePreview가 빈 목록이다(단계 오류 규칙). 시각을 고치면 새 시작 시각으로 일정이 다시 나온다.
2. errors는 저장·포커스 해제 때 계산하고, 오류가 있는 동안에는 편집마다 다시 계산한다(고치면 바로 사라짐). valvePreview는 항상 실시간 단계 검증을 쓴다.
3. 뒤로 가기 테스트: NavigationEventDispatcher.isEnabled는 바탕 처리기 때문에 늘 true라 isBackEnabled == hasUnsavedChanges를 동작(변경 없음 = D4 안 뜸, 있음 = D4)과 코드 대조로 확인했다.
4. 삭제 실패는 별도 이벤트가 없어 SaveFailed 이벤트를 쓴다(스낵바 "다시"는 onSave).
5. 그램 글자는 정수면 "250", 아니면 "60.5"로 채운다(분·초도 0 채움 없이 "1"·"5").
6. 단계 시각 칸 포커스 이동은 누적 목표 칸으로 한다(저장을 눌렀을 때만).
7. BLE 결함 없음. 실기기 TalkBack 확인은 하지 않았다.

## 재실행 2026-11-02
- P7: S8 저장 중 뒤로 가기 삼킴(isBackEnabled = hasUnsavedChanges || Saving). RecipeScreenTest에 저장 중 뒤로 가기 테스트, RecipeCodeContractTest는 식 전체 비교.
- 입력 칸 contentDescription 제거, stateDescription으로 단계 번호(EditableText 노출 테스트 추가).
- S7 FAB는 Loaded에서만. recipes_* 재기록.
- P8: recipe_edit_valve_cards 장면 추가. recordRoborazziDebug 후 verifyRoborazziDebug 통과.
