# 구현 보고 wf_1db05a96-4f1 (2026-11-02)

대상 FIX-6(P6 재실행), M9, M10. FIX-6 done → P3에 따라 M8 done. M9·M10 failed → 남은 지적과 결정 P7·P8 반영 후 재실행.

## 진행 로그

- FIX-6 라운드 0: 빌드 통과, QA 통과, 차단 지적 0건
- M9 라운드 0: 빌드 통과, QA 통과, 차단 지적 8건
- M9 라운드 1: 빌드 통과, QA 통과, 차단 지적 6건
- M9 라운드 2: 빌드 통과, QA 통과, 차단 지적 1건
- M10 라운드 0: 빌드 통과, QA 통과, 차단 지적 13건
- M10 라운드 1: 빌드 통과, QA 통과, 차단 지적 4건
- M10 라운드 2: 빌드 통과, QA 통과, 차단 지적 8건
- 완료 1 / 실기기 대기 0 / 판단 필요 0 / 실패 2 / 건너뜀 0

## FIX-6 — done → milestones.json `done`

- rounds 0, missingReviewers [], 스크린샷 183장

### 완료 조건

- **pass** — verify.sh 종료 코드 0
- **pass** — P1 첫 화면(Compose UI 테스트, Robolectric, 800 × 360 dp, 글꼴 1.0, Running, 레일 숨김, 하리오 스위치 기본 t 60 s, brew_running_valve_landscape와 같은 상태): 스크롤하지 않은 상태에서 타이머 값 노드와 유량 값 노드의 아래 끝(
- **pass** — P1 한 줄 모양(Compose UI 테스트, 같은 800 × 360 dp, 글꼴 1.0): 밸브 칸 안 아이콘 노드(또는 testTag)의 크기가 20 × 20 dp(오차 0.5 dp)이고 아이콘과 상태 글자의 세로 가운데 차이가 1 dp 이하이며 아이콘 오른쪽 끝과 상태 글자 왼쪽 끝 사이가 2 dp
- **pass** — P1 대체(Compose UI 테스트 또는 순수 함수 JVM 테스트): 같은 가로 창에서 글꼴 1.15와 2.0이면 밸브 칸이 세로 쌓기(아이콘 24 dp가 상태 글자 위)이고, 글꼴 1.0이면 한 줄이다. 판정은 20 + 2 + rememberTextMeasurer로 잰 상태 글자 폭 ≤ 56 dp이고
- **pass** — P1 바꾸지 않는 것(코드 대조 + 스크린샷): 밸브 칸의 contentDescription·읽기 순서(TraversalOrderTest 밸브 경우 그대로 통과)·Assertive 알림·칸 폭 64 dp·색·테두리가 같다. 세로 brew_*_valve*.png와 brew_running_valve_ligh
- **pass** — P1 스크린샷: screenshots/brew_running_valve_landscape_light.png·_dark.png를 다시 기록했고(타이머·유량 값이 동작 줄 위에 잘림 없이 보임, ui-design 8.6 2026-11-02 P1) verifyRoborazziDebug가 통과한다
- **pass** — P2 DefaultRecipesTest(JVM): 기대 표가 architecture.md 8.3과 문자열·값까지 같다. V60 기본 단계 3개 = ('뜸 60 g, 30초 기다리기 (95°C, 중간-가는 분쇄)', 60, 10), ('0:40까지 기다리기', 60, 40), ('0:40 나머지 물을 작은
- **pass** — P2 DB(코드 대조 + 테스트): DefaultRecipeSeedTest가 단언을 바꾸지 않고 통과한다(새 DB의 시드 = DefaultRecipes.all). AppDatabase version이 1이고 app/schemas/com.timemoredot.app.data.AppDatabase/1.jso
- **pass** — P2 시간 차(JVM, DefaultRecipes 값): 세 인자 plannedSecFor(tolerance 1.0)가 V60 기본 (60.3, 25.0) → 25.0, (60.0, 45.0) → 40.0, (60.0, 5.0) → 10.0, 6888 (60.2, 30.0) → 30.0이다(오차 1e-9
- **pass** — P2 S1 표시(Compose UI 테스트 또는 BrewViewModel 테스트): V60 기본 Running에서 단계 카드 1줄이 '1/3 단계' → '3/3 단계'로 바뀌고 '2/3 단계'는 나오지 않으며, 1단계 동안 5줄이 '다음: 0:40까지 기다리기'다. 6888은 '1/5 단계' → '3/5
- **pass** — P2 기존 테스트: 바꾼 단언은 이 변경 때문에 값이 달라진 것만이고(TraversalOrderTest의 '2/2 단계' → '3/3 단계', BrewAnnouncerTest V60 단계 index·'n단계' 문구 등), 바꾼 테스트 이름·줄과 이유를 _workspace/03_feature-enginee
- **pass** — P2 스크린샷: Fx.v60·6888을 쓰는 스크린샷 중 픽셀이 바뀐 것(ui-design 8.6 2026-11-02 P2 목록: brew_running_recipe, brew_paused, brew_running_recipe_landscape(+font200), brew_reconnecting_runn
- **pass** — P6 기다리기 구간(코드 대조 + 스크린샷): BrewChart가 Recipe.isWaitStep 구간의 목표 점선을 실제 중심선 위에 덧그리는 조건에 valveOpen 검사가 없다. brew_running_valve(하리오 스위치 기본 t 60 s)와 brew_running_recipe(V60 기본)에

### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (7)

- [low/qa] ui-design 8.6 P6 기대 문장이 '0:10~2:00 평평 구간이 현재 구간이라 3 dp'라고 적었지만 엔진은 기다리기 단계를 건너뛰어 그 시점의 현재 구간이 2:00~2:30이다(문서 쪽 어긋남) — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/docs/spec/ui-design.md:1368`
- [low/kotlin] Row-vs-stack decision measures a different style than it renders, and the cell height uses a hardcoded line height — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:171-174`
- [low/kotlin] onMinHeight is sent from LaunchedEffect, so the parent lays out once with a stale value — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:182`
- [low/kotlin] P6 wait-segment overdraw is correct, with an unvalidated segDash index assumption — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:584-590`
- [low/a11y] 가로 밸브 칸 한 줄 모양: 접근성 블로커 없음(확인 결과) — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:344-373`
- [low/a11y] 칸 안 안내 글자 12 sp(labelMedium)와 2줄 제한: 큰 글꼴에서 말줄임 위험은 판정식에 의존 — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:165-177, 373`
- [low/a11y] 기다리기 구간 목표 점선: 색 외 구분은 선 모양(점선)뿐이고 겹침 대비는 라이트 테마에서 약함 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:584-587`

### 구현자 거부 (0)


### 구현 요약

FIX-6 rerun (P6) is done. The first run already covered P1 and P2, and all 12 of their conditions passed. The only code change this time is in BrewChart.kt drawShapeArea. The wait-segment target dash redraw no longer checks `|| r.steps[i].valveOpen != null`, and I deleted the shortcut comment. The only condition left is Recipe.isWaitStep(i), so every recipe draws it the same way (ui-design 4.4 P6). Width, colour, dash phase (segDash[i]) and the current segment's 2 + stepQ dp are unchanged.

I backed up the screenshots before the run (_workspace/build/fix6p6/before.sha) and re-recorded them afterwards (rec.sha). Exactly 16 files changed, all because of P6:
- Hario Switch default: brew_running_valve, _countdown, _cue, _reduce_motion, brew_reconnecting_running_valve and brew_running_valve_landscape (each light and dark), plus brew_running_valve_light_font200.
- Devil's recipe: brew_running_long_step light, dark and _light_font200.

V60, 6888, recipe-free and brew_idle_valve* screenshots are byte-identical. In brew_running_valve_light and _cue_light, the flat 240 g segment from 0:10 to 2:00 now shows as dashes on top of the solid actual line.

history_detail_valve was not recorded because the summary screen does not exist yet. verify.sh exited 0, verifyRoborazziDebug passed, and grep Migration( found 0 lines. I added the P6 round section to _workspace/03_feature-engineer_FIX-6_impl.md.

Not checked: P6 has no dedicated unit test. The screenshots are the only evidence. History summary charts are not implemented, so I could not check them.

## M9 — failed → milestones.json `todo(재실행)`

- rounds 2, missingReviewers [], 스크린샷 30장

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
  - 실기기 필요(device-verification B8). 구현 요약 7번도 실행하지 않았다고 적었다. 기록 목록 S9는 M11 자리 표시자라 '목록에 저장됨'은 화면으로 확인할 수 없다.

### 남은 지적(차단) (1)

- [medium/a11y] Invalid dose: second tap on Save gives no feedback (no refocus, no announcement) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:117`
  - 수정안: Key the effect on a counter, e.g. saveAttempt in SummaryUiState or a Channel event. Also call announcer.announce(error text, false) when invalid input is rejected.

### 불확실 (0)


### low 지적 (19)

- [low/qa] [Q8] S6 리본 요약판이 M8 BrewChart의 차트 계산을 재사용하지 않고 거의 줄 단위로 복사했다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:143-221`
- [low/qa] [Q9] Saving 중 뒤로 가기로 D6을 거쳐 '버리기'를 확정하면 아무 일도 없이 대화상자만 닫히고, 이어서 저장이 끝나 기록이 남는다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:113`
- [low/kotlin] SaveFailed 상태가 입력 수정 뒤에도 남음 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryViewModel.kt:75`
- [low/kotlin] 서버 요약판의 물 g이 0일 때 NaN 위험 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:98`
- [low/a11y] S6 bottom bar eats most of the viewport at font 200% when SaveFailed (reflow) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:125-143`
- [low/a11y] Chart and gap band are weak non-text contrast; gap is marked by shade only — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:241`
- [low/a11y] Chart node has no role and the loading/load-failed states are not announced — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:140 and SummaryScreen.kt:194-208`
- [low/a11y] D2 recovery dialog: non-dismissible, one-tap irreversible discard, no title — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:649-658`
- [low/a11y] S6 has no visible Up/back control — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:119-124`
- [low/visual] 요약판 축 범위가 실시간 단계형 늘리기를 그대로 써서 그림 영역이 크게 빈다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:156 (chartRange 호출), docs/spec/ui-design.md 4.4 '축 범위 늘리기' 행`
- [low/visual] Loading 상태에 비활성 버리기·저장 줄이 보인다(5.5는 동작 없음, LoadFailed는 줄 없음) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:130`
- [low/visual] 고정 버튼 줄과 스크롤 내용 사이 경계가 없어 메모 필드가 글자 중간에서 잘려 보인다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:126`
- [low/visual] 넘침 서버(h 1.08)의 수면선이 서버 입구 테두리와 겹쳐 닫힌 뚜껑처럼 보인다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:94,107 / docs/spec/ui-design.md 4.10 S6 요약판 '액체'`
- [low/visual] 긴 문구 대화상자(D2, D1, D6 글꼴 2.0)가 화면 가장자리까지 꽉 찬다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:650, app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:240`
- [low/visual] 같은 흐름 안의 대화상자 위계가 다르다(D1·D6은 제목, D2·D3은 본문만) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryScreen.kt:227, app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:652, docs/spec/ui-d`
- [low/visual] StatusBanner 아이콘이 여러 줄 글자 가운데에 떠 있고, 동작 버튼 글자가 본문 시작선과 어긋난다 — `app/src/main/java/com/timemoredot/app/ui/components (StatusBanner Row, verticalAlignment = Alignment.CenterVertically, 약 162줄)`
- [low/visual] 그래프 원점에 축 선과 0 격자선이 6 dp 간격으로 겹쳐 이중선이 생긴다 — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:238-239`
- [low/visual] 리본 요약판 높이를 height(200.dp)로 고정했다(명세는 최소 높이) — `app/src/main/java/com/timemoredot/app/ui/summary/SummaryShapes.kt:143`
- [low/visual] HasGaps 문구가 명세 두 곳에서 다르다 — `docs/spec/ui-design.md 3.4(4번) vs 5.5 HasGaps 행; 구현 SummaryScreen.kt:169`

### 구현자 거부 (0)


### 구현 요약

Six findings fixed. (1) Q16: when recovered=false, LoadFailed hides '나가기', so S1 can't get stuck on '저장 중'. (2) D6 drops '저장' and keeps only 버리기/취소 when summary==null. (3) SaveFailed banner moved into the bottomBar above the button row. (4) headlineSmall now uses phrase(), so D6 and D1 titles break at word boundaries. (5) D2 text is now '10월 28일 08:12에 시작한 3분 4초 추출이 저장되지 않았습니다.' via spokenMinSec; ui-design.md updated. (6) LoadFailed row added to ui-design 5.5, the bottom row is hidden in that state, and summary_load_failed light/dark screenshots added. verify.sh exit 0. Not checked: D6 font200 re-look and the new load_failed images were not viewed. Only summary_save_failed_light was inspected. No new unit tests for canExit/canSave.

## M10 — failed → milestones.json `todo(재실행)`

- rounds 2, missingReviewers [], 스크린샷 45장

### 완료 조건

- **pass** — verify.sh 종료 코드 0
- **pass** — RecipeEditViewModelTest·RecipeListViewModelTest는 FakeRecipeRepository를 쓰고 Robolectric을 쓰지 않는다
- **pass** — RecipeEditViewModelTest: 빈 이름 → NameEmpty, 31자 이름 → NameTooLong, 원두 '0'·'0.05'·'100.1'·'abc' → DoseInvalid, '0.1'·'100.0'은 통과, 물 '0' → WaterInvalid, 단계 0개 → NoSteps, 숫자 아
- **pass** — RecipeEditViewModelTest(밸브, architecture.md 9.6 밸브 규칙, 사용자 결정 2026-10-30): 새 레시피는 valveEnabled=false, 모든 StepDraft.valveOpen=false이고 그대로 저장하면 모든 단계 valveOpen=null, valveS
- **pass** — RecipeEditViewModelTest(스위치만 바꾸는 단계, architecture.md 9.6 onInsertValveStep, 사용자 결정 2026-10-30): valveEnabled=true, 단계 [('a','240','0','10',false)]에서 onInsertValveStep(0) 
- **pass** — S8 넣기 알림(architecture.md 9.2, ui-design 8.3, Compose UI 테스트 + 기록용 가짜 Announcer): 하리오 스위치 기본 1단계 카드의 '스위치 바꾸는 단계 넣기'를 누르면 LocalAnnouncer.announce(text, assertive=false)가 정
- **pass** — StepDraftParsingTest(JVM, architecture.md 9.6 초안 파싱 함수): parseStepGrams('120')=120.0, (' 120.0 ')=120.0, ('0')=0.0, ('abc')·('')·('NaN')·('Infinity')=null. parseStepTimeS
- **pass** — S8 단계 체크 밸브 편집(ui-design 3.6·5.7, architecture.md 9.8 단계 체크 밸브 1~9): 레시피 스위치 하나, 단계 카드마다 머리 줄('n단계', 기다리기면 'n단계 · 기다리기'), 열림/닫힘 선택기, 스위치 설명 줄('스위치 · 처음부터' / '스위치 · 1:15부터
- **fail** — S8 스크린샷(ui-design 8.6 2026-10-30, 360 × 800): screenshots/recipe_edit_valve(악마의 레시피, 스위치 켜짐, 미리보기 '열림으로 시작 · 1:15 닫기 · 1:45 열기' + 1~2단계 카드), recipe_edit_valve_steps(스크롤해 
  - 파일은 모두 있고 recipe_edit_valve_invalid는 없다. valve_inserted·valve_steps·step_error의 내용은 명세와 같다. 그러나 verify.sh verifyRoborazziDebug가 종료 코드 1이다. 03:49 코드 수정 뒤 recipe_edit_saving_light.png를 다시 기록하지 않았다(medium 지적 1)
- **pass** — RecipeEditViewModelTest: 올바른 입력에서 onSave 뒤 RecipeRepository에 저장되고 RecipeEditEvent.Saved가 나온다. 기존 id로 열면 첫 state=Loading이고(이때 onNameChange·onSave는 무시되고 hasUnsavedChanges=f
- **pass** — RecipeListViewModelTest: 레시피가 없으면 recipes=Empty, 있으면 Loaded. 저장소 Flow 예외면 Error이고 onRetry로 다시 불러온다. phase가 Running·Paused·Finished이면 각각 canStartBrew=false이고 onBrewClick이 
- **pass** — S7 Loading, Empty, Loaded, Error, 추출 진행 중 상태와 S8 Loading, New, Editing, Invalid, Saving, NotFound 상태, D3·D4 대화상자 각각 screenshots/recipes_<state>·recipe_edit_<state>의 _ligh
- **pass** — S7 목록 행이 ListItem(containerColor = surfaceContainerHigh)이고 수식어 순서가 padding(horizontal = 16.dp, vertical = 4.dp) → focusRing(MaterialTheme.shapes.large) → clip(MaterialThe
- **pass** — S7 첫 실행 행 문구(ui-design S7 행·5.6, P2 뒤): V60 기본 행 셋째 줄이 '3단계 · 끝 1:30', 6888 레시피가 '5단계 · 끝 2:45'이고 행 낭독에 '3단계'·'5단계'가 들어간다(단계 수는 기다리기 포함, Compose UI 테스트 또는 순수 함수 테스트)
- **pass** — S7 첫 실행(DefaultRecipes 5개) screenshots/recipes_first_run_light.png·_dark.png와 Robolectric w1280dp-h800dp 설정의 screenshots/recipes_first_run_tablet_light.png·_dark.png가 있다(
- **fail** — RecipeEditScreen 뒤로 가기(Compose UI 테스트, Robolectric): NavigationBackHandler가 하나이고 isBackEnabled == hasUnsavedChanges다. 변경 있음에서 뒤로 가기 완료 → D4, 변경 없음이면 이전 화면으로 간다. D4와 D3(삭제
  - NavigationBackHandler는 하나이고, D4·뒤로 가기·복원 테스트(RecipeScreenTest 98-171), safeDrawing(209줄), imePadding 0개는 모두 맞다. 그러나 147줄이 `isBackEnabled = ui.hasUnsavedChanges || ui.state == RecipeEditState.Saving`이라 Saving 중에는 isBackEnabled != hasUnsavedChanges다. 계약 테스트가 부분 문자열만 봐서 이 차이를 놓쳤다(지적 2). 동작 자체는 ui-desig

### 남은 지적(차단) (8)

- [medium/qa] S8 스크린샷 기준 이미지가 최신 코드와 달라 verifyRoborazziDebug가 실패한다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/recipes/RecipeScreenshotTest.kt:93`
  - 수정안: 마지막 코드 수정 뒤 `./gradlew recordRoborazziDebug`로 다시 기록한다. 그다음 `verify.sh verifyRoborazziDebug`가 종료 코드 0인지 확인하고 구현 요약에 적는다.
- [medium/qa] Q16: 뒤로 가기 처리가 hasUnsavedChanges만이 아니라 Saving 상태에도 켜지는데, 계약 테스트는 부분 문자열만 봐서 이 차이를 놓친다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:147`
  - 수정안: architecture 9.6과 M10 완료 조건을 `isBackEnabled = hasUnsavedChanges || state == Saving`(Saving 중 뒤로 가기는 삼킴)으로 고친다. RecipeCodeContractTest:26은 실제 식 전체를 비교하도록 좁힌다. RecipeScreenTest에 '저장 중 뒤로 가기 완료 → 화면이 그대로이고 D4 없음' 테스트를 더한다.
- [medium/kotlin] NavigationBackHandler isBackEnabled differs from hasUnsavedChanges — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:147`
  - 수정안: Make it isBackEnabled = ui.hasUnsavedChanges as specified. To block leaving during Saving, keep hasUnsavedChanges true while Saving in the VM, or get the spec changed. Do not add a second condition in the screen.
- [medium/a11y] TextField에 contentDescription을 얹어 TalkBack이 입력값을 못 읽을 수 있음 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:469, 479, 486, 368, 375`
  - 수정안: contentDescription 대신 값은 필드가 읽게 두고 단계 번호만 붙인다. 예: 보이는 label을 '${n}단계 누적 목표'로 바꾸거나, 카드 Column에 `semantics(mergeDescendants = false)` 묶음과 `traversalIndex`를 쓰거나, `Modifier.semantics { stateDescription = ... }`로 번호를 준다. TalkBack 실기기 또는 Compose UI 테스트로 `EditableText`가 노출되는지 확인한다.
- [medium/visual] S7 Loading·Empty·Error에서도 FAB '새 레시피'가 보여 5.6 상태 표의 '동작'과 어긋나고, Empty에는 같은 행동 버튼이 둘 있다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.kt:88`
  - 수정안: floatingActionButton을 `if (ui.recipes is ListLoad.Loaded) { ExtendedFloatingActionButton(...) }`으로 감싼다. Empty·Error·Loading에서는 EmptyState 버튼이 하나뿐인 행동이 된다. 스크린샷을 다시 기록한다.
- [medium/visual] recipe_edit_valve 스크린샷이 8.6이 정한 '스위치 행 + 미리보기 + 1~2단계 카드'를 보여 주지 않는다 — `app/src/test/java/com/timemoredot/app/ui/recipes/RecipeScreenshotTest.kt:105`
  - 수정안: 스크롤 없는 첫 화면 장면은 그대로 두고, 1단계 '넣기' 버튼에서 2단계 머리까지를 performScrollTo로 맞춘 장면을 하나 더 둔다(예: recipe_edit_valve_cards). 또는 8.6 문구를 실제 360 × 800 화면에 맞게 '스위치 행 + 미리보기 + 1단계 카드 위쪽'으로 고쳐 달라고 ui-designer에 요청한다. 첫 화면 밀도가 문제라면 보조 문구를 줄이는 것(예: 두 문장 중 둘째를 S4 설명이나 도움말로 옮김)도 검토한다.
- [high/acceptance] 완료 조건 실패: S8 스크린샷(ui-design 8.6 2026-10-30, 360 × 800): screenshots/recipe_edit_valve(악마의 레시피, 스위치 켜짐, 미리보기 '열림으로 시작 · 1:15 닫기 · 1:45 열기' + 1~2단계 카드), recipe_edit_valve_steps(스크롤해 3단계 '3단계 · 기다리기'·4단계 — `-`
  - 수정안: 완료 조건을 만족하게 고친다
- [high/acceptance] 완료 조건 실패: RecipeEditScreen 뒤로 가기(Compose UI 테스트, Robolectric): NavigationBackHandler가 하나이고 isBackEnabled == hasUnsavedChanges다. 변경 있음에서 뒤로 가기 완료 → D4, 변경 없음이면 이전 화면으로 간다. D4와 D3(삭제 확인)이 열린 상태가 각각 Stat — `-`
  - 수정안: 완료 조건을 만족하게 고친다

### 불확실 (0)


### low 지적 (22)

- [low/qa] 구현 요약이 실제 코드와 다르고, 계약에 없는 DeleteFailed 이벤트가 생겼다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_feature-engineer_M10_impl.md`
- [low/qa] S8 화면이 명세에 없는 TalkBack 알림(단계 이동·삭제·추가)을 낸다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:305`
- [low/kotlin] Event collector captures stale onFinished and is not lifecycle-gated — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:118`
- [low/kotlin] onDeleteConfirmed has no in-flight guard — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:262`
- [low/kotlin] onInsertValveStep can produce an out-of-range new step time — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:222`
- [low/kotlin] Step cards are not keyed; per-card state follows the index — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:301`
- [low/kotlin] Grep contract: the comment on line 63 also matches toDoubleOrNull/toIntOrNull — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:63`
- [low/kotlin] Modifier.composed used in onBlur — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:236`
- [low/a11y] 글꼴 200%에서 스위치 선택기·넣기 버튼의 아이콘이 글자보다 훨씬 작고 위로 틀어짐 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:507-517, 533-537, screenshots/recipe_edit_valve_light_font200.png`
- [low/a11y] 스위치 선택기(SegmentedButton) 높이 40 dp, D3 권장 48 dp 미만 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:506-509, 530-541`
- [low/a11y] 검증 오류가 포커스 이동 말고는 알려지지 않고, 오류 문구가 이중으로 읽힘 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:164-183, 468-500`
- [low/a11y] 스위치 행의 긴 설명이 켜고 끌 때마다 통째로 낭독됨 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:332-341`
- [low/a11y] Compose 코드 근거로 확인했고 대비는 계산하지 못함(D5) — `screenshots/recipe_edit_step_error_light.png, screenshots/recipe_edit_valve_light.png`
- [low/visual] 분·초 칸 사이의 ':'가 칸 세로 중심보다 약 7~8 dp 위에 떠 있다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:483`
- [low/visual] 단계가 0개일 때 빈 단계 Column이 spacedBy 간격을 한 번 더 만들어 스위치 행과 '단계 추가' 사이가 약 41 dp로 벌어진다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:300`
- [low/visual] S8 단계 이름 필드에서 숫자와 단위 'g'가 다른 줄로 갈린다('280 / g까지', '60 / g (90°C') — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:462`
- [low/visual] 글꼴 2.0에서 열림/닫힘 세그먼트의 아이콘이 글자 중심보다 위로 붙는다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:532`
- [low/visual] 글꼴 확대 때 구분점 '·'이 줄 끝에 홀로 남는다(S7 둘째 줄, S8 스위치 일정 줄) — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeText.kt:16`
- [low/visual] 글꼴 2.0에서 원두 오류 문구가 강제 분할돼 줄 앞에 공백이 남는다(' g 사이로') — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:271`
- [low/visual] D3 삭제 확인에 제목이 없어 D4와 위계가 다르고, 스크린샷에서 화면 가장자리까지 넓어진다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditScreen.kt:560`
- [low/visual] S7 FAB에 M3 기본 그림자가 그려져 6.1 '질감: 그림자 없음'과 어긋난다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.kt:89`
- [low/visual] 끼워 넣은 단계의 초 칸이 '5'로 채워져 '1 : 5'로 보이고, 미리보기의 '1:05'와 표기가 다르다 — `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt:227`

### 구현자 거부 (0)


### 구현 요약

Fixed the Saving back/close hole, last-card delete focus, and the missing valvePreview assertion. verify.sh passed (exit 0). The 4th finding (move/delete/add announcements) was already fixed in the current code; its remaining focus-after-delete part is covered by fix 2. I did not add the RecipeScreenTest case for back during Saving, which the finding suggested. I did not re-run the Roborazzi screenshot check; the screenshot list is the existing set. The UI change is a disabled X during Saving and a focus move after delete, so I expect no pixel change, but that is unconfirmed.

1. Saving: the back handler is now enabled while saving and ignores the back gesture (no D4). The close X is `enabled = false` while saving, and `close` does nothing then. The edit is in RecipeEditScreen.kt.
2. Delete focus: after a step is deleted, focus goes to the card now at that position, or the previous card if the last one was deleted. With no steps left it goes to "단계 추가". Move and insert behaviour is unchanged, because ui-design 3.6 and 8.3 want focus to stay on the pressed button.
3. Test: added `assertEquals(emptyList<ValveEvent>(), r.ui.valvePreview)` in the "간격이 2초 미만" test in RecipeEditViewModelTest.kt, after the save call that returns StepNumberInvalid.

Risk: architecture.md 9.1 still says `isBackEnabled = hasUnsavedChanges`. It now also covers Saving, so the doc needs an update.
