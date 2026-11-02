# M10·M11 P9 수정 (medium 4건)
verify 로그: 종합 _workspace/build/verify_20261102_051412.log (exit 0), verifyRoborazziDebug _workspace/build/verify_20261102_051414.log (exit 0)

1. RecipeEditScreen.kt:131 `remember(vm) { vm.actions() }`, StepErrors(:398)에 @Immutable. 테스트 없음(성능), 스크린샷 변화 없음.
2. RecipeListScreen.kt(LazyColumn 첫 item, StatusBanner Progress=secondaryContainer + "추출 화면으로"). RecipeListContent에 `onOpenBrew: () -> Unit = {}` 인자 추가, 루트가 연결.
   테스트: RecipeScreenTest `추출 중 배너는 canStartBrew false에만 있고 추출 화면으로가 S1 이동을 부른다`
   재기록: recipes_brewing_light/dark.png
3. RecipeEditScreen.kt ValveChoice: icon 슬롯 비움, label Row(CenterVertically){check 18dp, Spacer 8dp, Text}.
   재기록: recipe_edit_valve{,_cards,_inserted,_inserted_next,_steps}_{light,dark}.png 중 바뀐 11개(valve_dark/light, cards 2, inserted 2, inserted_next 2, steps 2) + recipe_edit_valve_light_font200.png. 선택 아이콘이 있는 화면 전부 바뀜.
4. SessionDetailScreen.kt:203~ isError, 카운터 "최대 200자 · N/200", 낭독 "200자 중 N자", 한도 첫 도달 시 announce("최대 200자입니다", false), 아래로 내려가면 재무장.
   테스트: SessionDetailScreenTest `메모 200자 한도에 닿으면 오류 표시와 낭독 한 번이고 값은 200자로 남는다`
   스크린샷: 메모가 200자인 상태 샷 없어 변화 없음.

명세 차이: ui-design 5.6 S7 배너 아이콘은 Info로 지정함(문구·버튼은 지시대로).
