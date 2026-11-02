# 구현 보고 wf_0521af98-5ca (2026-11-02)

대상 M9(재실행), M10(재실행), M11. M9 awaiting-device(B8). M10 완료 조건 18/18, M11 14/14(+실기기 1) 통과, 마지막 리뷰의 새 medium 4건만 남음 → 결정 P9(직접 수정 + 대상 QA 확인)로 마무리.

## 진행 로그

- M9 라운드 0: 빌드 통과, QA 통과, 차단 지적 0건
- M10 라운드 0: 빌드 통과, QA 통과, 차단 지적 3건
- M10 라운드 1: 빌드 통과, QA 통과, 차단 지적 1건
- M10 라운드 2: 빌드 통과, QA 통과, 차단 지적 3건
- M11 라운드 0: 빌드 통과, QA 통과, 차단 지적 3건
- M11 라운드 1: 빌드 통과, QA 통과, 차단 지적 4건
- M11 라운드 2: 빌드 통과, QA 통과, 차단 지적 1건
- 완료 0 / 실기기 대기 1 / 판단 필요 0 / 실패 2 / 건너뜀 0

## M9 — awaiting-device → milestones.json `awaiting-device`

- rounds 0, missingReviewers [], 스크린샷 27장

### 완료 조건

- **pass** — verify.sh 종료 코드 0
- **pass** — SummaryViewModelTest·BrewViewModelTest는 FakeBrewRepository와 가짜 prefs를 쓰고 Robolectric을 쓰지 않는다
- **pass** — SummaryViewModelTest: 레시피 dose 15.0이면 doseText '15.0'으로 미리 채운다. 원두량 '0.05'·'100.1'·'abc'에서 onSave는 doseError=true이고 저장하지 않는다. 빈 칸은 저장된다(doseGrams null)
- **pass** — SummaryViewModelTest: recovered=false onSave → brews.finish(..., Completed) 뒤 controller.newBrew() 호출과 SummaryEvent.Saved(id). recovered=true면 status Interrupted이고 newBre
- **pass** — SummaryViewModelTest: onDiscardConfirmed → deleteSession(id), 실시간 세션이면 newBrew(), SummaryEvent.Discarded
- **pass** — BrewViewModelTest: VM 생성 때 FakeBrewRepository.findDraft가 controller.draftId.value(실시간 초안 id, 없으면 null)를 인자로 한 번 호출된다. findDraft가 초안을 돌려주면 recoverableDraft가 채워지고, onRecove
- **pass** — S6 Loading(summary_loading), Ready(summary_ready: 서버 요약판 h = 1.0 + 리본 요약판 + 조약돌 지표 칸, V60 기본 250.0 g), HasGaps(summary_has_gaps: 값 없는 구간 띠·정보 카드), InvalidDose, Saving, Sa
- **pass** — S6 그림 규칙(코드 대조): 서버·리본 요약판은 진입 때 애니메이션을 재생하지 않고(Animatable·animate*AsState 없음), 서버 요약판은 Modifier.clearAndSetSemantics {}로 TalkBack에서 빠진다. 서버 수위는 vesselLevel(summary.final
- **pass** — S6 기다리기 단계(ui-design 4.4·4.10, architecture.md 9.8 단계 체크 밸브 6): 악마의 레시피 요약에서 서버·리본 요약판의 단계 표식이 기다리기 단계(isWaitStep)에는 없고(표식 3개), 리본 요약판 낭독이 '단계 3개 중 M개 도달'로 기다리기 단계를 세지 않는
- **pass** — SummaryScreen 뒤로 가기(Compose UI 테스트, Robolectric): NavigationBackHandler가 하나이고 isBackEnabled가 항상 true다. 뒤로 가기 완료 → D6 표시, Brew로 돌아가지 않는다. 뒤로 가기 취소(onBackCancelled)는 D6을 띄우
- **pass** — S6 Scaffold가 contentWindowInsets = WindowInsets.safeDrawing으로 키보드를 피하고 S6 코드에 imePadding() 호출이 0개다. 고정 버튼 줄(bottomBar)은 windowInsetsPadding(WindowInsets.safeDrawing.only(
- **pass** — S6 D3(버리기 확인)이 열린 상태가 StateRestorationTester 복원 뒤에도 그대로다(Compose UI 테스트, rememberSaveable)
- **pass** — S6 저장 실패 알림(architecture.md 9.2, ui-design 8.3, Compose UI 테스트 + 기록용 가짜 Announcer): SummaryScreen의 state가 Saving → SaveFailed가 되면 LocalAnnouncer.announce(ui-design 8.3 S6
- **unverified** — [실기기 B8] 레시피를 따라 3분 추출하고 완료 → S6 저장하면 단계가 진행되고 그래프가 끊기지 않으며 기록 목록에 저장된다
  - 실기기가 필요하다(device-verification B8). 구현 요약 7번에 실행하지 않았다고 적혀 있다. 기록 목록 S9는 M11에서 만들므로 아직 화면으로 확인할 수 없다.
- **pass** — 원두량 오류 재시도 피드백(ui-design 8.3·8.5, Compose UI 테스트 + 기록용 가짜 Announcer): 원두량 'abc'에서 '저장'을 두 번 누르면 두 번 모두 원두량 칸으로 포커스가 가고 오류 문구 낭독이 한 번씩(총 2번) 불린다. 시도 횟수 같은 계기(카운터·이벤트)로 효과를

### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (19)

- [low/qa] [Q9] 원두량 재시도 테스트의 두 번째 포커스 단언은 다시 포커스했는지 증명하지 못한다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/summary/SummaryScreenTest.kt:176-180`
- [low/qa] ui-design 8.3 알림 표에 S6 InvalidDose 행이 없고, 비슷한 S8 Invalid 행은 '별도 알림 없음'이라 새 낭독 규칙과 어긋난다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/docs/spec/ui-design.md:1238-1240`
- [low/kotlin] Save-failed announcement tracks the previous state in a non-state array — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:107`
- [low/kotlin] onSaveClick is a new lambda on every recomposition — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:123`
- [low/a11y] ecc:accessibility 스킬을 불러오지 못함 — `~/.claude/plugins/cache/ecc/ecc/*/skills/accessibility/SKILL.md`
- [low/a11y] 저장 중 버튼이 disabled로 바뀌며 TalkBack 포커스와 '저장 중' 상태 낭독이 불확실 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:141-145`
- [low/a11y] 다크 테마의 값 없는 구간 띠가 배경과 대비 약 1.3:1 (비텍스트 1.4.11) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt (SummaryChart 구간 띠)`
- [low/a11y] 원두량 오류 재시도 효과가 saveAttempts 증가 시점에 의존 (확인 필요) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:119-123`
- [low/a11y] 원두량 오류 칸에 오류 의미가 supportingText에만 의존하고 입력 힌트가 없음 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:179-184`
- [low/visual] 고정 버튼 줄과 스크롤 내용 사이에 경계가 없어 메모 필드가 글자 중간에서 잘려 보인다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:131-149`
- [low/visual] 넘침 서버(h 1.08)의 수면선이 입구 테두리와 겹쳐 닫힌 뚜껑처럼 보인다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:93-94,105-107`
- [low/visual] 요약판 x·y 범위가 실시간 단계형 늘리기를 그대로 써서 그림 영역 오른쪽·위가 크게 빈다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:156`
- [low/visual] 긴 문구 대화상자(D2 글꼴 1.0, D6 글꼴 2.0)가 화면 좌우 끝까지 꽉 찬다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:650; app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:246`
- [low/visual] 같은 저장·버리기 흐름의 대화상자 위계가 다르다(D6은 제목, D2·D3은 본문만) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:231-235; app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:650-652`
- [low/visual] Loading 상태에 비활성 버리기·저장 줄이 보인다(5.5는 동작 없음, LoadFailed는 줄 없음) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:136`
- [low/visual] 원두량 오류 문구가 나올 때 메모 필드가 약 22 dp 아래로 밀린다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:182`
- [low/visual] 서버 요약판이 서버만 가운데에 두어 표식·라벨까지 합친 묶음이 오른쪽으로 치우쳐 보인다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:88-89,110-111`
- [low/visual] StatusBanner 아이콘이 여러 줄 글자 가운데에 떠 있고, 동작 버튼 글자가 본문 시작선과 어긋난다 — `app/src/main/java/com/timemoredot/app/ui/components/Components.kt:161-168`
- [low/visual] 리본 요약판 원점 근처에 x축 선과 0 g 격자선이 6 dp 간격으로 이중선이 된다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:239,242`

### 구현자 거부 (0)


### 구현 요약

원두량 오류 상태에서 '저장'을 다시 눌러도 피드백이 없던 문제를 고쳤다. SummaryContent에 저장 클릭 횟수 카운터(saveAttempts)를 두고 LaunchedEffect(doseError, saveAttempts)를 키로 썼다. 오류가 그대로여도 다시 누르면 원두량 칸 포커스와 오류 문구 낭독(assertive=false)이 매번 실행된다. 횟수가 0인 복원 직후에는 실행하지 않는다. 이 문구는 DOSE_ERROR_TEXT로 빼서 화면 오류 문구와 같이 쓴다. SummaryScreenTest에 'abc'에서 저장을 두 번 누르는 케이스를 추가했다. 낭독이 총 2번 불리는 것은 단언하고, 매번 원두량 칸에 포커스가 있는지도 단언했다. 다만 버튼을 눌러도 포커스가 원래 칸에 남아 있어서, 포커스를 실제로 다시 옮겼는지는 이 테스트로 구분되지 않는다. [실기기 B8]은 실기기 항목이라 실행하지 않았다. 화면 모양은 바뀌지 않아 스크린샷 파일은 그대로다. 요약은 _workspace/03_feature-engineer_M9_impl.md에 추가했다.

## M10 — failed → milestones.json `todo→P9 마무리`

- rounds 2, missingReviewers [], 스크린샷 47장

### 완료 조건

- **pass** — verify.sh 종료 코드 0
- **pass** — RecipeEditViewModelTest·RecipeListViewModelTest는 FakeRecipeRepository를 쓰고 Robolectric을 쓰지 않는다
- **pass** — RecipeEditViewModelTest: 빈 이름 → NameEmpty, 31자 이름 → NameTooLong, 원두 '0'·'0.05'·'100.1'·'abc' → DoseInvalid, '0.1'·'100.0'은 통과, 물 '0' → WaterInvalid, 단계 0개 → NoSteps, 숫자 아
- **pass** — RecipeEditViewModelTest(밸브, architecture.md 9.6 밸브 규칙, 사용자 결정 2026-10-30): 새 레시피는 valveEnabled=false, 모든 StepDraft.valveOpen=false이고 그대로 저장하면 모든 단계 valveOpen=null, valveS
- **pass** — RecipeEditViewModelTest(스위치만 바꾸는 단계, architecture.md 9.6 onInsertValveStep, 사용자 결정 2026-10-30): valveEnabled=true, 단계 [('a','240','0','10',false)]에서 onInsertValveStep(0) 
- **pass** — S8 넣기 알림(architecture.md 9.2, ui-design 8.3, Compose UI 테스트 + 기록용 가짜 Announcer): 하리오 스위치 기본 1단계 카드의 '스위치 바꾸는 단계 넣기'를 누르면 LocalAnnouncer.announce(text, assertive=false)가 정
- **pass** — StepDraftParsingTest(JVM, architecture.md 9.6 초안 파싱 함수): parseStepGrams('120')=120.0, (' 120.0 ')=120.0, ('0')=0.0, ('abc')·('')·('NaN')·('Infinity')=null. parseStepTimeS
- **pass** — S8 단계 체크 밸브 편집(ui-design 3.6·5.7, architecture.md 9.8 단계 체크 밸브 1~9): 레시피 스위치 하나, 단계 카드마다 머리 줄('n단계', 기다리기면 'n단계 · 기다리기'), 열림/닫힘 선택기, 스위치 설명 줄('스위치 · 처음부터' / '스위치 · 1:15부터
- **pass** — S8 스크린샷(ui-design 8.6 2026-10-30, 360 × 800): screenshots/recipe_edit_valve(악마의 레시피, 스위치 켜짐, 미리보기 '열림으로 시작 · 1:15 닫기 · 1:45 열기' + 1단계 카드 위쪽), recipe_edit_valve_cards(1단계 
- **pass** — RecipeEditViewModelTest: 올바른 입력에서 onSave 뒤 RecipeRepository에 저장되고 RecipeEditEvent.Saved가 나온다. 기존 id로 열면 첫 state=Loading이고(이때 onNameChange·onSave는 무시되고 hasUnsavedChanges=f
- **pass** — RecipeListViewModelTest: 레시피가 없으면 recipes=Empty, 있으면 Loaded. 저장소 Flow 예외면 Error이고 onRetry로 다시 불러온다. phase가 Running·Paused·Finished이면 각각 canStartBrew=false이고 onBrewClick이 
- **pass** — S7 Loading, Empty, Loaded, Error, 추출 진행 중 상태와 S8 Loading, New, Editing, Invalid, Saving, NotFound 상태, D3·D4 대화상자 각각 screenshots/recipes_<state>·recipe_edit_<state>의 _ligh
- **pass** — S7 목록 행이 ListItem(containerColor = surfaceContainerHigh)이고 수식어 순서가 padding(horizontal = 16.dp, vertical = 4.dp) → focusRing(MaterialTheme.shapes.large) → clip(MaterialThe
- **pass** — S7 첫 실행 행 문구(ui-design S7 행·5.6, P2 뒤): V60 기본 행 셋째 줄이 '3단계 · 끝 1:30', 6888 레시피가 '5단계 · 끝 2:45'이고 행 낭독에 '3단계'·'5단계'가 들어간다(단계 수는 기다리기 포함, Compose UI 테스트 또는 순수 함수 테스트)
- **pass** — S7 첫 실행(DefaultRecipes 5개) screenshots/recipes_first_run_light.png·_dark.png와 Robolectric w1280dp-h800dp 설정의 screenshots/recipes_first_run_tablet_light.png·_dark.png가 있다(
- **pass** — RecipeEditScreen 뒤로 가기(Compose UI 테스트, Robolectric): NavigationBackHandler가 하나이고 isBackEnabled == hasUnsavedChanges || state == Saving이다(P7, architecture.md 9.1). 저장 중 뒤로
- **pass** — S8 입력 칸 낭독(Compose UI 테스트): 단계 카드의 누적 g·분·초 TextField 노드가 EditableText(입력값)를 노출하고 단계 번호는 label 또는 stateDescription으로 준다. 입력 TextField에 값을 가리는 contentDescription이 없다(코드 대조
- **pass** — S7 FAB(Compose UI 테스트 + 스크린샷): '새 레시피' ExtendedFloatingActionButton은 recipes가 Loaded일 때만 있고 Loading·Empty·Error에서는 의미 트리에 없다(빈 상태 버튼 하나만). recipes_* 스크린샷 재기록, verifyRobor

### 남은 지적(차단) (3)

- [medium/kotlin] vm.actions()가 매 재구성마다 새 RecipeEditActions를 만들어 모든 단계 카드가 매 키 입력에 재구성됨 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:131`
  - 수정안: `val actions = remember(vm) { vm.actions() }`로 한 번만 만든다. StepErrors는 @Immutable이나 data class로 바꾼다.
- [medium/visual] S7 추출 진행 중 상태에 눈에 보이는 이유 문구가 없음(D10) — `screenshots/recipes_brewing_light.png, screenshots/recipes_brewing_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.kt:149`
  - 수정안: canStartBrew=false이면 목록 위(LazyColumn 첫 item)에 StatusBanner(shapes.medium 20 dp, secondaryContainer)를 둔다. 문구는 '추출 중에는 레시피를 바꿀 수 없습니다'로 하고, '추출 화면으로' TextButton을 붙인다. ui-design 5.6 표에 같은 문구를 더하고 recipes_brewing_* 스크린샷을 다시 기록한다.
- [medium/visual] 글꼴 200%에서 S8 열림/닫힘 세그먼트 아이콘이 작아지고 위로 붙음 — `screenshots/recipe_edit_valve_light_font200.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:547-550`
  - 수정안: 실기기 API 34에서 글꼴 200%로 먼저 재현한다. 재현되면 SegmentedButton의 icon 슬롯을 비우고 label에 Row(verticalAlignment = CenterVertically){ Icon(18.dp); Spacer(8.dp); Text }를 직접 넣는다. 그다음 recipe_edit_valve_light_font200.png를 다시 기록한다.

### 불확실 (0)


### low 지적 (22)

- [low/qa] 구현 요약의 '명세와 다른 점' 3개 항목이 현재 코드와 반대로 적혀 있다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_feature-engineer_M10_impl.md:17`
- [low/qa] grep 계약: 63줄 주석에도 toDoubleOrNull·toIntOrNull 글자가 있어 grep 결과가 파싱 함수 줄만으로 나오지 않는다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:63`
- [low/kotlin] LaunchedEffect(vm)이 첫 구성의 콜백(onFinished, onOpenBrew)을 고정해서 잡음 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:118`
- [low/kotlin] 삭제 중 상태가 없어 확인 버튼을 연타하면 delete가 여러 번 나감 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:261`
- [low/kotlin] Modifier.composed 사용(onBlur) — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:236`
- [low/a11y] SegmentedButton(열림/닫힘) 높이 40 dp로 D3 48 dp 기준 미달 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:544-555`
- [low/a11y] 편집 입력 칸이 stateDescription을 라벨 대용으로 사용 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:379,386,483,493,500`
- [low/a11y] 추출 중 비활성 '추출' 버튼의 이유가 화면에 안 보임(낭독에만 있음) — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.kt:148-154`
- [low/a11y] 저장 중에도 더보기(삭제) 메뉴가 활성 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:194-199`
- [low/a11y] 스위치 켜기·끄기에 따른 내용 변화가 알려지지 않음 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:305-309,343-351`
- [low/a11y] D8 글꼴 200%에서 원두·물 칸이 나란히 남아 긴 값이 가로 스크롤됨 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:282-297`
- [low/a11y] 스크린샷과 명세 대조로 확인하지 못한 것(검증 한계) — `screenshots/recipe_edit_*.png, screenshots/recipes_*.png`
- [low/visual] labelLarge(500)에서 숫자만 굵게 보여 한 줄 안 굵기가 섞임 — `screenshots/recipe_edit_valve_steps_light.png, recipe_edit_valve_inserted_light.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen`
- [low/visual] S8 Saving 상태에서 일부만 흐려져 비활성 표시가 섞임 — `screenshots/recipe_edit_saving_light.png, recipe_edit_saving_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:343-353, 4`
- [low/visual] 스위치 행 블록만 32 dp 들여 써져 왼쪽 정렬선이 둘로 나뉨 — `screenshots/recipe_edit_valve_light.png, recipe_edit_editing_light.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:304-306, `
- [low/visual] 새 레시피(단계 0개)에서 스위치 행과 '단계 추가' 사이 간격이 두 배 — `screenshots/recipe_edit_new_light.png, recipe_edit_new_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:274, 311`
- [low/visual] 분·초 사이 ':'가 값보다 약 7 dp 위에 놓임(광학 정렬) — `screenshots/recipe_edit_valve_light.png, recipe_edit_new_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:383, 497`
- [low/visual] S7 FAB 그림자가 '그림자 없음' 질감 규칙과 어긋남 — `screenshots/recipes_first_run_light.png, recipes_loaded_light.png, recipes_first_run_tablet_light.png; app/src/main/java/com/timemoredot/app/ui/recipe`
- [low/visual] 태블릿(w1280dp)에서 큰 제목과 목록의 왼쪽 정렬이 어긋남 — `screenshots/recipes_first_run_tablet_light.png, recipes_first_run_tablet_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.k`
- [low/visual] D3 삭제 대화상자가 거의 화면 끝까지 넓고 D4와 구조가 다름 — `screenshots/recipe_edit_delete_confirm_light.png, recipe_edit_delete_confirm_dark.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScre`
- [low/visual] S8 단계 이름 필드에서 데이터 이름의 '280 / g까지'가 끊김 — `screenshots/recipe_edit_valve_steps_light.png, recipe_edit_valve_light_font200.png; app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.`
- [low/visual] recipe_edit_step_error 장면이 오류 카드 아래 끝을 자름 — `screenshots/recipe_edit_step_error_light.png, recipe_edit_step_error_dark.png; app/src/test/java/com/timemoredot/app/ui/recipes/RecipeScreenshotTest.k`

### 구현자 거부 (0)


### 구현 요약

단계 위/아래 이동 뒤 포커스를 새 자리 카드의 같은 방향 버튼으로 보내게 고쳤다. 끝 단계로 가서 버튼이 비활성이면 이름 칸으로 보낸다. 파일은 RecipeEditScreen.kt 하나다. 이동 버튼마다 FocusRequester를 달고, 이동 직후 한 프레임 뒤에 포커스를 요청한다. 이 포커스 동작을 확인하는 새 테스트는 만들지 않았고 TalkBack과 실기기 확인도 하지 않았다. 레이아웃은 바뀌지 않아 스크린샷은 다시 찍지 않았고 기존 파일을 그대로 쓴다. verify.sh 종료 코드는 0이다.

## M11 — failed → milestones.json `todo→P9 마무리`

- rounds 2, missingReviewers [], 스크린샷 32장

### 완료 조건

- **pass** — verify.sh 종료 코드 0
- **pass** — HistoryViewModelTest·SessionDetailViewModelTest는 FakeBrewRepository와 가짜 CsvTarget(StringBuilder에 쓰거나 예외를 던지는 람다)을 쓰고 Robolectric을 쓰지 않는다(architecture.md 10절). HistoryView
- **pass** — S10에서 삭제하면 History로 돌아가 내비게이션 결과 session_deleted를 읽고 스낵바 '기록을 삭제했습니다'를 한 번 띄운다(Compose UI 테스트 또는 코드 대조)
- **pass** — HistoryViewModelTest: 세션이 없으면 sessions=Empty, 저장소 예외면 Error이고 저장소가 정상으로 돌아온 뒤 onRetry()를 부르면 Loaded(세션 있음) 또는 Empty가 된다(architecture.md 9.7). onExportAll(가짜 CsvTarget) 성공
- **pass** — ExportFailed 스낵바 '다시'(S9·S10, architecture.md 9.7): 동작이 CreateDocument 런처를 다시 띄우고, 새로 받은 Uri로 container.csvTarget(uri)를 만들어 onExportAll/onExportSamples를 부른다. 선택기를 취소(Uri 
- **pass** — SessionDetailViewModelTest: 없는 id면 NotFound, samples()가 null이면 NoSamples. 레시피가 있으면 stepReachTimesMs 길이가 단계 수와 같다. onNoteChange를 연달아 부르면 500 ms 뒤 updateNote가 마지막 값으로 1회 호출
- **pass** — SessionDetailViewModelTest(떠날 때 메모 저장): onNoteChange('abc') 뒤 500 ms 전에 ViewModelStore.clear()로 VM을 지우면 주입한 appScope(TestScope)에서 updateNote(id, 'abc')가 정확히 1회 호출된다. 디바운스
- **pass** — S9·S10 화면이 export가 Idle → Exporting이 될 때 LocalAnnouncer.announce('내보내는 중', assertive=false)를 한 번 부른다(기록용 가짜 Announcer로 Compose UI 테스트 또는 코드 대조, architecture.md 9.2, ui-de
- **pass** — 병합 매니페스트에 READ_EXTERNAL_STORAGE·WRITE_EXTERNAL_STORAGE·MANAGE_EXTERNAL_STORAGE 권한이 없다(grep 0줄)
- **pass** — S9 Loading, Empty(history_empty, 블롭 빈 상태), Loaded, Error, Exporting 상태와 S10 Loading(300 ms 뒤 진행 원, session_detail_loading), Loaded(completed), Loaded(interrupted), NoSamp
- **pass** — S10 차트 모드(Compose UI 테스트, Robolectric): SessionDetailUiState와 SessionDetailViewModel에 차트 모드 필드·함수가 없고(코드 대조), '유량'을 고른 뒤 StateRestorationTester 복원 뒤에도 유량 모드다. 세그먼트 버튼 두 개
- **pass** — S9 목록 행이 S7과 같은 조약돌 행(ListItem containerColor = surfaceContainerHigh, padding → focusRing(shapes.large) → clip(shapes.large) → clickable)이고 날짜 머리글은 배경이 없다. S10 요약판 표식 도달 
- **pass** — S10 스위치 일정 줄(ui-design 3.7): summary.recipe?.valveSchedule이 비어 있지 않을 때만 머리 '스위치 일정'(heading)과 일정 한 줄이 있고, 비었거나 recipe가 null이면 두 노드가 의미 트리에 없다(Compose UI 테스트). 일정 줄 문자열은 S
- **pass** — S10 기다리기 단계(ui-design 3.7·4.4, architecture.md 9.8 단계 체크 밸브 6, Compose UI 테스트): 악마의 레시피 기록의 단계 표에 기다리기 단계(isWaitStep) 행이 '기다리기 · 1:15까지 (120 g 그대로)' 모양으로 있고 실제 도달 시각이 없다(
- **unverified** — [실기기 B11] 기록 CSV를 내보내면 파일이 열리고 헤더와 단위가 architecture.md 8.4절과 같다
  - 실기기가 필요하다(device-verification B11). 코드 대조로는 CsvExport 헤더 두 개, BOM, CRLF, Locale.ROOT, 소수 자릿수가 8.4와 같다. AppContainer.csvTarget이 IO에서 UTF-8로 쓰고 닫는다. 실제 문서 제공자에서 'wt' 모드로 열리는지, Excel에서 파일이 열리는지는 확인하지 못했다.

### 남은 지적(차단) (1)

- [medium/a11y] S10 메모 200자 제한이 조용히 잘린다(오류·상태 낭독 없음) — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:203-206`
  - 수정안: 한도에 닿으면 TextField semantics에 `error("최대 200자")` 또는 stateDescription을 주고 카운터를 한도 도달 때만 Polite liveRegion으로 알린다. 카운터 낭독은 '200자 중 N자'로 따로 준다.

### 불확실 (0)


### low 지적 (20)

- [low/qa] [Q15] SessionDetailViewModelTest의 'samples()가 null이면 NoSamples'가 null이 아니라 예외를 넣어 검사한다 — `app/src/test/java/com/timemoredot/app/ui/history/SessionDetailViewModelTest.kt:51-60`
- [low/qa] [Q16] 구현 요약의 S10 삭제 결과 설명이 Navigation.kt 코드와 다르고, Brew에서 연 S10 경로는 테스트가 없다 — `app/src/main/java/com/timemoredot/app/ui/nav/Navigation.kt:183-190`
- [low/qa] S10 스위치 일정 줄의 낭독이 ui-design 8.4 표와 다르다(시각을 기호 그대로 읽음) — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:356-361`
- [low/kotlin] onCleared의 appScope 저장이 viewModelScope 취소와 겹쳐 중복 저장 가능 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailViewModel.kt:76-84,129-131`
- [low/kotlin] HistoryViewModel catch에서 e 미사용 — `app/src/main/java/com/timemoredot/app/ui/history/HistoryViewModel.kt:72`
- [low/kotlin] startExport 람다가 매 재구성마다 새로 만들어짐 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:107 및 HistoryScreen.kt:85`
- [low/a11y] S10 차트 모드 칸 높이가 40 dp로 고정이라 큰 글꼴·표시 크기에서 글자가 잘릴 수 있다 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:228`
- [low/a11y] S9 행 낭독이 숫자에 spokenNumber를 쓰지 않아 S10과 다르다 — `app/src/main/java/com/timemoredot/app/ui/history/HistoryScreen.kt:188-194`
- [low/a11y] S10 단계 낭독이 화면 표시 이름이 아닌 원본 step.name을 쓴다 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:254,260`
- [low/a11y] 로딩이 오류·빈 상태·목록으로 바뀔 때 화면 낭독기에 알림이 없다 — `app/src/main/java/com/timemoredot/app/ui/history/HistoryScreen.kt:136-146 및 SessionDetailScreen.kt:170-173`
- [low/a11y] 내보내기 진행 막대 트랙이 다크에서 배경과 대비가 약하다 — `app/src/main/java/com/timemoredot/app/ui/history/HistoryParts.kt:72-75`
- [low/a11y] 더보기 메뉴 버튼에 펼침 상태 의미가 없다 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:154-157`
- [low/a11y] 차트 모드를 바꿔도 그래프 낭독이 바뀐 것을 알리지 않는다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:183`
- [low/visual] Hangul headings look the same as body text: S10 '단계별 도달 시각'/'스위치 일정' and S9 date headers stand out only by weight 500 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:243 (also recipes/RecipeEditScreen.kt:358 ValveScheduleLine, history/HistorySc`
- [low/visual] S10 top bar has no scroll state, so scrolled chart and labels are cut off at a bar of the same color — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:147`
- [low/visual] At font 200%, the S9 row line 3 breaks after the '·' separator and leaves it dangling at the line end — `app/src/main/java/com/timemoredot/app/ui/history/HistoryScreen.kt:184-185`
- [low/visual] S10 NoSamples: the chart slot shrinks to one 14 sp line, the layout jumps, and the message is easy to miss — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:199`
- [low/visual] Chart-mode segment focus ring wraps the 48 dp touch box, not the 40 dp visible shape — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:224`
- [low/visual] S9 row line 1 repeats the date already shown in the date header — `app/src/main/java/com/timemoredot/app/ui/history/HistoryScreen.kt:203 (spec docs/spec/ui-design.md 3.7)`
- [low/visual] The S10 Interrupted state and its banner text are not in ui-design 5.9 — `app/src/main/java/com/timemoredot/app/ui/history/SessionDetailScreen.kt:187 (spec docs/spec/ui-design.md 5.9)`

### 구현자 거부 (0)


### 구현 요약

Fixed 4 findings. (1) history_detail_valve_{light,dark}.png are now taken scrolled, so they show the '스위치 일정' heading with its schedule line and the wait rows. The full-graph shot was renamed history_detail_valve_chart_{light,dark}, and the old _table files were deleted. (2) SessionDetailViewModel.onDeleteConfirmed now clears pendingNote only after deleteSession succeeds. On failure it re-schedules the held note through onNoteChange. Added a test for this. (3) Fixtures now match: the default ui() samples run 184 s to match durationMs 184_000. The Devil series follows the recipe (60 g at 0:10, 120 g at 0:40, flat to 1:15, 280 g at 1:25, flat to 3:00, 180 s). The Devil detail uses the same record as the S9 list (many[3]). verify.sh exit 0. I viewed only the light valve and valve_chart PNGs; I did not open the dark ones. The shared fixture change also regenerated history_detail and session_detail_completed, which I did not view. The full verify.sh run does not regenerate screenshots. I ran verify.sh recordRoborazziDebug with --tests '*HistoryScreenshotTest*' separately to re-record them.

## P9 마무리 (2026-11-02)

- 구현자 수정: _workspace/03_feature-engineer_M10M11_P9_impl.md (4건).
- 대상 QA(android-qa-inspector): 3 fixed, 1 not-fixed(StepErrors 참조 비교) → 메인이 `private data class StepErrors`로 직접 수정.
- 메인 verify: `:app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug` 종료 0 (_workspace/build/verify_20261102_051755.log, compileDebugKotlin·testDebugUnitTest 실제 실행).
- 상태: M10 done, M11 awaiting-device(B11).
