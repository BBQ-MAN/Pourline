# 구현 보고 wf_d0bbcb11-ada (2026-10-30)

대상: FIX-4, FIX-5, M8. maxFixRounds 2. 에이전트 94, 오류 0.

메인 재확인: verify.sh(test lintDebug assembleDebug) 종료 0 — _workspace/build/verify_20261030_134833.log, :app:verifyRoborazziDebug 종료 0 — verify_20261030_134834.log (Gradle 최신 상태, 마지막 에이전트 실행 뒤 코드 변경 없음).

## 진행 로그

- FIX-4 라운드 0: 빌드 통과, QA 통과, 차단 지적 0건
- FIX-5 라운드 0: 빌드 통과, QA 통과, 차단 지적 1건
- FIX-5 라운드 1: 빌드 통과, QA 통과, 차단 지적 0건
- M8 라운드 0: 빌드 통과, QA 통과, 차단 지적 18건
- M8 라운드 1: 빌드 통과, QA 통과, 차단 지적 8건
- M8 라운드 2: 빌드 통과, QA 통과, 차단 지적 11건
- 완료 2 / 실기기 대기 0 / 판단 필요 0 / 실패 1 / 건너뜀 0

## FIX-4 — done → milestones.json `done`

- rounds 0, verifyPassed True, qaVerifyPassed True, missingReviewers []
- 스크린샷 0장, 변경 파일 19개

### 완료 조건 판정

- **pass** — verify.sh :core:brew:test :app:testDebugUnitTest 종료 코드 0
- **pass** — M5 완료 조건의 테스트가 단언을 바꾸지 않고 통과한다. M6 테스트는 DefaultRecipesTest의 하리오·악마 행과 누적 g 단언(엄격 오름차순 → 첫 단계 > 0·앞 단계 이상)만 바꾸고, RoomRecipeRepositoryTest·RoomBrewRepositoryTest는
- **pass** — RecipeValveJsonTest(JVM, Json과 Json { ignoreUnknownKeys = true; encodeDefaults = true } 둘 다): valveOpen 키가 없는 단계 JSON(M6 테스트의 옛 recipeJson·stepsJson 문자열 포함)이 역직
- **pass** — RecipeValveScheduleTest(JVM, 7.1 파생 규칙): hario().valveSchedule = [(0,false),(120,true)], devil().valveSchedule = [(0,true),(75,false),(105,true)]. 모든 valveOpen 
- **pass** — RecipePaceTest(JVM, 세 인자 plannedSecFor, tolerance 1.0): devil (120.4, 50.0) → 50.0, (119.5, 50.0) → 50.0, (120.0, 30.0) → 40.0, (120.3, 80.0) → 75.0, (280.5, 15
- **pass** — ValveStateTest(JVM, valveStateAt): 빈 일정 → null. 하리오 [(0,false),(120,true)]: 0.0 → open=false, next=(120,true), secondsToNext=120. 119.1 → secondsToNext=1. 120.0
- **pass** — ValveStateTest(JVM, valveUiFor): recipe=null → null, 모든 valveOpen null 레시피 → null, Finished → null. hario()에서 Idle·WaitingForPour는 timer 값과 상관없이 open=false, nex
- **pass** — BrewEngineValveTest(JVM, 10 Hz 합성 시계열, hario()): Running 진입 뒤 경과 0~119.9 s에 ValveCue가 없고 atSec 0 이벤트로도 없다. 경과 정확히 120.0 s Reading에서 ValveCue((120,true))가 정확히 한 
- **pass** — BrewEngineValveTest: 경과 119.5 s에 UserTimer(Pause) → 30 s 동안 Reading·Tick(벽시계는 흐름)에 ValveCue 없음 → UserTimer(Start) 자체에 ValveCue 없음 → 재개 뒤 경과가 120 s를 처음 넘는 입력에서 한
- **pass** — BrewEngineValveTest(devil()): Tick만 오다가 마지막 확인 경과 70 s 뒤 다음 Tick이 경과 110 s면 ValveCue((105,true)) 하나만 나오고 (75,false)는 나오지 않는다. 그 뒤 atNanos가 더 이른 Reading(경과 100 s
- **pass** — BrewEngineValveTest: 레시피 없음·모든 valveOpen null 레시피는 전체 시계열에서 ValveCue 0건. 단계 (240,10,열림)·(240,3599,열림)·(240,3600,닫힘) 레시피(파생 변경 3599 s)에서 마지막 확인 경과 3598.5 s 뒤 Rea
- **pass** — BrewEngineWaitStepTest(JVM, 10 Hz 합성 시계열, architecture.md 7.2 '단계'): devil()에서 무게가 0 → 60 g(경과 10 s) → 120 g(경과 40 s)로 오르고 75 s까지 120.0 ± 0.5 g로 흔들리면, 119 g에 닿은
- **pass** — BrewSettings()의 valveHaptic이 true다. BrewState·BrewInput·BrewConfig·StepGuide의 공개 필드·시그니처가 바뀌지 않는다(코드 대조)
- **pass** — RoomRecipeRepositoryTest(Robolectric): devil()(단계 valveOpen 있음)과 모든 valveOpen null 레시피가 각각 upsert → get 왕복되고 get 결과의 valveSchedule이 [(0,true),(75,false),(105,tr
- **pass** — app/schemas/com.timemoredot.app.data.AppDatabase/1.json의 version이 1, identityHash가 6d9f61fa4f808273048039f7ba96e8a3이고 valveJson 글자가 없다(빌드 뒤 파일이 지금과 같다). AppData
- **pass** — DefaultRecipesTest: 하리오 스위치 기본·테츠 카스야 악마의 레시피의 단계(이름, 누적 g, targetAtSec, valveOpen)가 architecture.md 8.3 밸브 표와 문자열·값까지 같고, 하리오 finishAtSec = null(finishSec 150)
- **pass** — grep -rnE 'validateValveSchedule|ValveIssue|ValveScheduleValidationTest' core app 결과 0줄. core/brew/src/main에 android. import가 없다(grep 0줄)
- **pass** — BrewEngineTest의 자동 시작 임계값 테스트 두 개(현재 69-74행, 88-96행 부근)가 FIX-1 무장 규칙(영점 뒤 |w| ≤ 임계값 판독)을 먼저 거쳐 WaitingForPour에 들어간 뒤 임계값 바로 아래·위 판독으로 시작 여부를 가른다. 무장 판독을 빼면 테스트가

### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (1)

- [low/kotlin] valveSchedule 게터가 Reading·Tick마다 목록을 새로 만든다 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt:198`

### 구현자 거부 (0)


### 구현 요약

FIX-4 is done. `verify.sh :core:brew:test :app:testDebugUnitTest` exited with code 0.

**core:brew**
- `RecipeStep.valveOpen: Boolean? = null` is added.
- `Recipe.valveSchedule` is now a derived getter, not a constructor field. Each step starts at the previous step's targetAtSec, matching states are merged, and any null valveOpen gives an empty list.
- Added `Recipe.isWaitStep` and a three-argument `plannedSecFor(weight, elapsed, tolerance)`. On a flat stretch made by wait steps, a weight within tolerance returns `elapsed.coerceIn(a, b)`.
- `Valve.kt`: the validation functions, `ValveIssue`, `MAX_VALVE_SEC` and `@Serializable` on `ValveEvent` are deleted.
- `BrewEngine` changed in one line: `paceDeltaSec` now calls the three-argument `plannedSecFor`. The valve cue logic is unchanged.

**app**
- `valveJson` is removed from `RecipeEntity`, the repository and the seed.
- `DefaultRecipes` now follows the 8.3 valve table: Hario has 3 steps with finishAtSec null, Devil has 6 steps with finishAtSec 180.
- `1.json` is byte-identical after the build: version 1, identityHash 6d9f61fa4f808273048039f7ba96e8a3. There is no Migration.

**Tests**
- Deleted: `ValveScheduleValidationTest`.
- Rewritten: `RecipeValveJsonTest`.
- New: `RecipeValveScheduleTest`, `RecipePaceTest`, `BrewEngineWaitStepTest`.
- `ValveFixtures`: `hario()` and `devil()` now use the 8.3 table steps. Added `withoutValve()` and `valveRecipe()`.
- `ValveStateTest` and `BrewEngineValveTest` keep their scenarios and times (120, 75, 105 s). Only the empty-schedule and 3599 s recipes are now built from steps.
- M6: `DefaultRecipesTest` changes only the Hario and Devil rows and the cumulative-gram assertion (first step > 0, then at least the previous step). Valve assertions are added. `RoomRecipeRepositoryTest` and `RoomBrewRepositoryTest` only gained new tests.

**Check results**
- All greps that must return 0 lines do: `valveJson`, `Migration(`, the validation names, and android imports in `core/brew/src/main`.
- The two auto-start threshold tests in `BrewEngineTest` already go through the FIX-1 arming read before testing just below (1.9, 1.4) and above (2.1) the threshold. If the arming read after the second tare is removed, 2.1 cannot arm (|2.1| > 2.0), so the Running assertion fails. They do not pass vacuously, so I did not change them.

Not checked: I did not run a mutation test that deletes the arming read; that claim is from reading the code. "No valveSchedule constructor parameter" is tested indirectly, through the serial descriptor and the absence of a backing field.

## FIX-5 — done → milestones.json `done`

- rounds 1, verifyPassed True, qaVerifyPassed True, missingReviewers []
- 스크린샷 0장, 변경 파일 3개

### 완료 조건 판정

- **pass** — verify.sh 종료 코드 0
- **pass** — StatusAnnouncerTraversalTest(Compose UI 테스트, Robolectric): 진짜 StatusAnnouncer로 감싼 시험용 content(상단 제목 Text 1개 + 버튼 2개)에서 announce('영점 맞춤', false) 뒤 창이 닫히도록 시간을 넘기
- **pass** — StatusAnnouncer.kt에서 content(NavHost)를 감싼 쪽 Box에 semantics { isTraversalGroup = true }가 있고, 알림 노드의 contentDescription은 문구가 비어 있지 않을 때만 설정된다(코드 대조)
- **pass** — StatusAnnouncer.kt에서 알림 노드 묶음이 최상위 Box의 content 뒤(마지막 자식)에 있고 그 묶음에 semantics { isTraversalGroup = true }와 align(Alignment.BottomEnd)가 있다(코드 대조). 이 구조로 위 순서 테스트
- **pass** — FIX-3의 StatusAnnouncerTest와 노드 크기·grep 조건(invisibleToUser·alpha(0 0줄, liveRegion은 StatusAnnouncer.kt뿐)이 그대로 통과한다

### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (4)

- [low/qa] StatusAnnouncerTraversalTest가 두 isTraversalGroup을 지켜 주지 못함(위치만으로도 통과) — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/StatusAnnouncerTraversalTest.kt:50-56`
- [low/kotlin] Test 부수효과가 컴포지션 본문에서 매 재구성마다 실행됨 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/StatusAnnouncerTraversalTest.kt:41`
- [low/kotlin] 사슬 읽기가 Compose 내부 extras 키 문자열에 의존함 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/StatusAnnouncerTraversalTest.kt:71`
- [low/kotlin] 첫 테스트가 announce 전 상태를 검증하지 않고 두 번째 테스트는 게시된 상태를 검증하지 않음 — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/test/java/com/timemoredot/app/ui/StatusAnnouncerTraversalTest.kt:82`

### 구현자 거부 (0)


### 구현 요약

StatusAnnouncerTraversalTest.kt를 고쳤다. 사슬 노드를 읽는 `chain()`을 spoken()에서 따로 뗐다. 빈 문구 테스트는 걸러내기 전 사슬에서 두 가지를 확인한다. liveRegion 노드가 없어야 하고, text가 null인데 contentDescription만 있는 노드도 없어야 한다. StatusAnnouncer.kt는 건드리지 않았다. 확인하지 못한 것은 둘이다. 첫째, StatusAnnouncer.kt의 contentDescription 가드를 일부러 빼서 새 단언이 실제로 실패하는지는 돌려 보지 않았다. 둘째, verify.sh 종료 코드는 직접 읽지 못했다(zsh에서 PIPESTATUS가 비어 나왔다). verify.sh가 낸 PASS 줄을 근거로 0으로 적었다.

## M8 — failed → milestones.json `blocked`

- rounds 2, verifyPassed True, qaVerifyPassed True, missingReviewers []
- 스크린샷 183장, 변경 파일 60개

### 완료 조건 판정

- **pass** — verify.sh 종료 코드 0
- **pass** — BrewSessionControllerTest(FakeScaleClient + FakeBrewRepository + FakeAppPrefs, 가상 시간, Robolectric 없음): tare() 중 tareInFlight=true, Tare 성공 → WaitingForPour와 Bre
- **pass** — BrewSessionControllerTest(실측 영점 순서, protocol.md C-0D-2·architecture.md 7.2 무장 규칙): Tare 성공 뒤 FakeScaleClient.emit으로 213.4 g Reading 3개(그중 첫째는 Tared보다 이른 atNanos
- **pass** — BrewSessionControllerTest(저장 실패, FakeBrewRepository 예외 주입): Running 중 주기 saveDraft가 한 번 예외를 던지면 크래시 없이 이벤트가 없고 10 s 뒤 saveDraft가 다시 호출된다. finish() 뒤 확정 saveDraf
- **pass** — BrewSessionControllerTest(저울 타이머 미상, 가상 시간): Running 중 재연결로 Connected가 되고 status.scaleTimer가 null인 채 4 s(timerSyncDeadlineNanos)가 지나면 sent에 SetTimer(Start)가 정확히
- **pass** — BrewSessionControllerTest(lastUnit): oz Reading을 받은 뒤 setConnection(Reconnecting)·setStatus(ScaleStatus.Unknown)이면 controller.lastUnit=Ounce이고, BrewViewModel ui
- **pass** — BrewSessionControllerTest(원문 경로, 단위 테스트만): emitTimerNotify(Paused)면 phase가 Paused가 되고 sent에 SetTimer가 추가되지 않는다. setStatus로 scaleTimer만 바꾸면(Read 응답 흉내) phase가 바뀌
- **pass** — BrewSessionControllerTest(Write 응답만으로 확정): Idle에서 startTimer() 뒤 SetTimer(Start) 성공(FakeScaleClient가 status.scaleTimer=Running 반영)으로 phase=Running이고 sent가 [SetT
- **pass** — BrewSessionControllerTest(재연결): Running 중 setConnection(Reconnecting) → setStatus(scaleTimer=null) → setConnection(Connected) → setStatus(scaleTimer=Reset)이면 ph
- **pass** — BrewSessionControllerTest: Running 25 s 동안 FakeBrewRepository.saveDraft가 2회 호출되고(10 s 주기) 첫 호출 뒤 draftId가 그 id다. finish() 뒤 saveDraft 1회 후 BrewEvent.Finished(id
- **pass** — BrewSessionControllerTest(타이머 상한, 가상 시간): 기본 BrewConfig로 Running 중 setConnection(Reconnecting)이 되어 Reading 없이 Tick만 올 때 경과 3_599.9 s에 닿은 뒤 첫 Tick에서 BrewEvent.Ph
- **pass** — ReadingUiMappingTest: architecture.md 9.3절 매핑의 각 분기(None, UnitUnknown, UnitUnknownTimedOut, Live, Stale×3 원인, Reconnecting 중 Stale(Disconnected))가 테스트된다. Connec
- **pass** — BrewViewModelTest(가짜 저장소·가짜 prefs·FakePermissionMonitor): 10 Hz 측정값이 readout만 바꾸고 uiState는 방출하지 않는다(Turbine으로 1 s 동안 uiState 방출 0건). banner가 ui-design 10.3 우선순위
- **pass** — BrewViewModelTest(배터리 미상): Connected + batteryPercent=null이면 uiState.batteryPercent=null이고, S1 상태 줄에 배터리 텍스트 노드가 없으며 연결 칩 stateDescription이 '연결됨'이다(배터리 생략, ui-d
- **pass** — BrewViewModelTest(단위 미상): Connected + status.unit=null이면 canStart=false이고(unitReadFailed 여부와 상관없이), unit=Gram이면 canStart=true다. Connected + unit=null + unitRead
- **pass** — BrewViewModelTest(배너 유지·접기, 가상 시간): banner=NoData에서 정상 Reading으로 엔진 stale이 풀려도 1.9 s 뒤 banner=NoData이고 2.1 s 뒤에는 NoData가 아니다. 그 2 s 안에 BluetoothOff가 되면 바로 banne
- **pass** — BrewViewModelTest(다른 저울 찾기): phase Idle·WaitingForPour에서 canFindOtherScale=true이고 onFindOtherScaleClick이 disconnect와 OpenConnect를 낸다. phase Running·Paused·Finis
- **pass** — BrewViewModelTest: keepScreenOn이 phase Running·Paused에서만 true다. BrewScreen은 keepScreenOn이 true인 동안만 LocalView.current.keepScreenOn을 켠다(코드 대조)
- **pass** — S1 BrewAnnouncer(ui-design 8.2, Compose UI 테스트(기록용 가짜) 또는 낭독 문구 계산 순수 함수의 JVM 테스트): Running 중 경과 4.9 s → 5.0 s 경계에서 요약 문구가 한 번 바뀌고 4.9 s까지는 바뀌지 않는다. currentStep
- **pass** — BrewViewModelTest(D5): Paused이고 sampleCount>0이면 onResetClick은 showResetConfirm=true만 하고 sent에 SetTimer(Reset)이 없으며 phase는 Paused다. onResetConfirmed 뒤 phase Idle
- **pass** — BrewViewModelTest: phase Idle에서 onFindOtherScaleClick이 disconnect 뒤 BrewUiEvent.OpenConnect를 낸다. onSessionSaved(7)이 Snackbar(Saved, sessionId=7)을 낸다. TareFailed
- **pass** — BrewViewModelTest(알림): architecture.md 9.4 PhaseChanged → BrewAnnouncement 표의 행마다 알림이 있는 행은 그 값이 정확히 한 번, '없음' 행(WaitingForPour→Idle, 사용자 완료 Running→Finished, N
- **pass** — grep -rn 'SetUnit' app/src/main 결과 0줄(앱 안 g/oz 전환 없음, architecture.md 0절·5.4절)
- **pass** — BrewScreen의 무게·유량 숫자, 단계 카드 수치, 리본 그래프가 readout을 각자 수집하고 화면 루트 컴포저블은 readout을 수집하지 않는다(코드 대조)
- **pass** — 리본 경로·목표선·표식 경로는 drawWithCache 캐시 블록에서 readout.sampleCount(또는 축 범위·크기)가 바뀔 때만 만들고, 그리기 블록(onDrawBehind/onDrawWithContent) 안에 listOf·map·toList·Array(·IntArray(·
- **pass** — ShapeMathTest(JVM, Robolectric 없음, architecture.md 9.1 ShapeMath.kt): ribbonWidthDp(0f)=2.5, (5f)=7.0, (8.33f)=9.997, (25f/3f)=10.0, (8.34f)=10.0, (99.9f)=10.0,
- **pass** — 단계 허용 오차 공유: :core:brew에 const val STEP_TOLERANCE_GRAMS = 1.0이 한 번 정의되고 BrewConfig.stepToleranceGrams와 Recipe.reachTimesMs의 기본값이 이 상수다. grep -rnE 'toleranceGram
- **pass** — 모션 규칙(architecture.md 9.1): grep -rn 'withFrameNanos' app/src/main 결과 0줄. grep -rn 'ANIMATOR_DURATION_SCALE\|MotionDurationScale\|AccessibilityManager' 결과가 Brew
- **pass** — BrewScreen(Compose UI 테스트, Robolectric, mainClock.autoAdvance=false): BrewUiEvent.Announce(TareDone)을 받으면 기록용 가짜 Announcer가 한 번 불리고, 영점 확인 모션은 같은 이벤트 수집 지점에서 시작
- **pass** — S1 지표 줄 위치 고정: 360 × 800 dp에서 brew_idle·brew_waiting_for_pour·brew_running_recipe 상태의 지표 줄 top이 같다(Compose UI 테스트 positionInRoot, ui-design 3.1 y 268 dp, 오차 1 d
- **pass** — S1 스크린샷: brew_nodevice, brew_disconnected, brew_bluetooth_off, brew_permission_missing, brew_connecting, brew_unit_unknown, brew_unit_unknown_timed_out, brew_id
- **pass** — S1 B + A 새 스크린샷(ui-design 8.6, Compose 테스트 mainClock.autoAdvance=false, 정지 장면은 advanceTimeBy(1_000) 뒤, _frame은 계기 뒤 적힌 ms, 가짜 저울 시퀀스는 ui-organic-direction.md 6.
- **fail** — S1 설계 검증 반영 스크린샷(ui-design 8.6 '2026-10-29 설계 검증 반영 추가'): brew_reconnecting_running_recipe(V60 기본 2단계 카드 + Reconnecting 7a 배너, 글꼴 1.0, 360 × 800, '지금 다시 시도'·'연결
  - 파일은 모두 있고 verifyRoborazziDebug도 통과한다. 그러나 brew_weight_worst_w320_light_font200.png는 단위를 숫자와 같은 줄에 그린다. 조건의 '단위 아래 줄'이 아니며 구현자도 인정했다(low 지적 참고). ui-design을 고치거나 구현을 고친 뒤 다시 판정한다.
- **pass** — TraversalOrderTest(Compose UI 테스트, Robolectric, ui-design 8.5): AndroidComposeView의 AccessibilityNodeInfo traversalBefore/traversalAfter 사슬을 따라 읽은 낭독 노드 목록이 8.5
- **pass** — BrewScreen 뒤로 가기(Compose UI 테스트, Robolectric): NavigationBackHandler가 화면에 하나이고 isBackEnabled가 phase Running·Paused일 때만 true다. Running에서 뒤로 가기 완료 → D1 표시, 화면 유지.
- **pass** — S4 설정 시트, S5 레시피 선택 시트(레시피 있음·없음), D1 대화상자, D5 대화상자(brew_reset_confirm) 각각 _light.png·_dark.png 스크린샷이 있다. D1 '버리기'는 D3·D5처럼 error 색 TextButton이고(코드 대조), 글꼴 2.0에
- **unverified** — [실기기 B4] 앱에서 영점을 누르면 저울이 0을 표시하고 디버그 로그에 TX FFF2 A55A030D0000…와 성공 응답 RX가 있다. C-0D-2 확인: 무게를 올린 채 영점 5회 이상, 성공 응답 뒤 0 g 첫 알림까지 영점 전 무게 프레임 수·간격을 logcat으로 기록한다(3
  - 실기기가 필요하다(device-verification B4).
- **unverified** — [실기기 B5] 앱에서 타이머 시작·일시정지·초기화를 하면 각각 Write 응답 성공 RX(03 02 0001 01) 뒤 0x01 timerSec가 1 s마다 늘고, 멈추고, 0이 된다(디버그 로그). 0x02 Notify 없이 앱 phase와 저울 타이머 상태가 같다(C-02-3). 
  - 실기기가 필요하다(device-verification B5).
- **unverified** — [실기기 B6] (M4 B6 측정으로 architecture.md 4.1 규칙의 FIX 마일스톤이 생기면 그 마일스톤 뒤에 판정한다) 추출 중 저울 전원 버튼으로 끄기 → 켜기에서 NoData 배너를 거치지 않고 Reconnecting으로 가고(C-0B-2 Android 감지 시간 < 
  - 실기기가 필요하다(device-verification B6). BrewConfig.noDataTimeoutNanos에 TODO(device)가 남아 있다.
- **unverified** — [실기기 B9] 추출 중 화면 회전과 홈 갔다 오기 뒤 연결과 타이머가 유지된다
  - 실기기가 필요하다(device-verification B9). 코드상 연결·세션은 appScope의 AppContainer가, VM은 Activity 범위가 가진다.
- **unverified** — [실기기 모션 줄이기] 개발자 옵션 애니메이터 배율 0(또는 접근성 '애니메이션 삭제')에서 추출하면 ui-design 6.6.3 표대로 리본 머리가 10 Hz 계단으로 최신 표본 점에 있고, 서버 → 그래프 전환·영점 물방울·카드 색 숨이 없다. 안 되면 architecture.md 
  - 실기기가 필요하다.
- **unverified** — [실기기 B12] TalkBack을 켜고 추출할 때 숫자를 알림마다 읽지 않고 끊김·영점·타이머 상태 변화를 알린다
  - 실기기가 필요하다(device-verification B12).
- **unverified** — [실기기 TalkBack 알림 노드] (ui-design 8.5 3번, architecture.md 9.1 '실기기 기준') 알림('영점 맞춤')을 한 번 낸 뒤 통과 = (가) S1 세로·S2에서 TalkBack 앞뒤 스와이프로 처음부터 끝까지 갈 때 알림 노드가 맨 끝에 한 번만 오
  - 실기기 TalkBack이 필요하다.
- **pass** — 배터리 낭독(ui-design 8.4·8.5, architecture.md 9.8, Compose UI 테스트 또는 코드 대조): Connected + batteryPercent=82이면 S1 연결 칩 stateDescription이 '연결됨'(배터리 없음)이고 배터리 노드 하나가 '배
- **pass** — S3 단위 줄(architecture.md 9.4 'S3 단위 줄', Compose UI 테스트): S1에서 연 S3의 단위 줄이 readout.reading = UnitUnknown이면 '확인 중', UnitUnknownTimedOut이면 '알 수 없음', Live(unit=Gram)
- **pass** — 무게 표시 범위(ui-design 4.2, architecture.md 9.8, 순수 함수 JVM 테스트 또는 Compose UI 테스트, 새 VM 상태 없음): ReadingUi.Live 10000.0 g면 WeightReadout이 '--.-'와 '표시 범위 밖' 줄을 보이고 점선 
- **pass** — 임시 저장 경합 방지(M6 판단 보류 지적, wf_487a9e30-192): discard()·finish 경로는 진행 중인 10 s saveDraft 작업을 cancelAndJoin한 뒤 deleteSession/finish를 부르고, RoomBrewRepository.saveDraf
- **pass** — BrewSessionControllerTest(밸브, 가상 시간): 하리오 스위치 기본(DefaultRecipes, 단계 valveOpen에서 파생한 일정) 선택 뒤 Running 10 Hz Reading에서 경과 120 s에 BrewEvent.ValveCue(ValveEvent(120
- **pass** — BrewViewModelTest(기다리기 단계): 테츠 카스야 악마의 레시피 Running에서 무게가 120 g에 닿으면 uiState.currentStepIndex가 1에서 3으로 바뀌고(기다리기 단계 2 건너뜀) 280 g에 닿으면 lastStepReached=true, curren
- **pass** — S1 기다리기 단계 표시(ui-design 4.4·4.5, architecture.md 9.8 단계 체크 밸브 6, Compose UI 테스트): 악마의 레시피 Running에서 단계 카드 1줄이 기다리기 포함 전체 번호로 '1/6 단계' → '2/6 단계' → '4/6 단계' → '6
- **pass** — BrewViewModelTest(밸브): BrewEvent.ValveCue((120,true)) → Announce(ValveOpen) 한 번 뒤 Haptic 한 번(atNanos = 가짜 시계 값), ValveCue((75,false)) → Announce(ValveClose). Fa
- **pass** — SharedPrefsAppPrefsTest(Robolectric): valveHaptic false 왕복, 키 'valve_haptic'이 없는 기존 prefs에서 brewSettings.valveHaptic=true다. M7 SharedPrefsAppPrefsTest 단언은 그대로 통
- **pass** — 밸브 칸(코드 대조 또는 Compose UI 테스트, ui-design 4.11): 밸브 칸 컴포저블이 valveUiFor(uiState.phase, uiState.recipe, uiState.timer, clock.nowNanos())를 타이머 글자와 같은 주기로 스스로 계산하고 화면
- **pass** — 진동(코드 대조): BrewScreen이 Haptic을 받으면 clock.nowNanos() − atNanos ≤ 1_000_000_000일 때만 ui/brew/ValveHaptic.kt 함수를 한 번 부르고, 1 s 넘은 Haptic은 버린다(Compose UI 테스트에서 가짜 진동기
- **pass** — 밸브 칸 무게 크기: 360 dp·글꼴 1.0에서 밸브 칸이 있으면 무게 숫자가 64 sp이고, '−8888.8' g가 숫자 영역 256 dp(x 88–344) 안에 한 줄로 들어간다(Compose UI 테스트 또는 ui-design 4.2 자동 축소 순수 함수 테스트, 사용자 결정 2
- **pass** — S1 밸브 위치 고정: 360 × 800 dp에서 brew_idle_valve·brew_running_valve_countdown 상태의 지표 줄 top이 brew_idle과 같다(Compose UI 테스트 positionInRoot, 오차 1 dp, ui-design 3.1). 밸브 
- **pass** — BrewScreen(Compose UI 테스트 또는 코드 대조): Announce(ValveOpen|ValveClose)를 받은 같은 수집 지점에서 밸브 칸 색 숨(motion.tint)이 시작되고 밸브 칸이 events를 따로 수집하지 않는다
- **pass** — S1·S4 밸브 스크린샷(ui-design 8.6 2026-10-29 밸브 칸 추가, 가짜 시계로 앱 타이머 고정, 360 × 800): screenshots/brew_idle_valve, brew_idle_valve_open, brew_running_valve(t 60 s), brew
- **unverified** — [실기기 밸브] 하리오 스위치 기본으로 추출하면 S1 밸브 칸이 시작 전 닫힘, 2:00에 열림으로 바뀌고 시스템 '터치 진동'이 켜져 있으면 그 시각에 짧은 진동 1회가 온다(꺼져 있으면 진동 없음이 정상, 사용자 결정). S4에서 진동을 끄면 같은 추출에서 진동이 없고 TalkBac
  - 실기기가 필요하다.

### 남은 지적(차단) (11)

- [medium/qa] [Q9] ConnectFailed 낭독에 실패 이유가 거의 빠진다: 이벤트를 처리할 때 아직 이전 연결 상태를 읽는다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewViewModel.kt:333`
  - 수정안: 이유를 이벤트를 처리하는 시점의 최신 값에서 읽게 한다. 예 1: BrewContent에 `connection: () -> ConnectionUi`(BrewScreen에서는 `{ vm.uiState.value.connection }`)를 넘긴다. 이때 VM은 announce 전에 conn.value를 먼저 갱신하고, uiState 대신 conn 흐름의 최신 값을 쓴다. 예 2: 이유를 Announce에 싣는다. ConnectFailed를 이유별 값으로 나누려면 architecture.md 9.4 enum을 함께 고친다. 회귀 테스트도 둔다. FakeScaleClient로 Connecting → Failed(Timeout)을 일으키고, BrewScreen에서 기록용 가짜 Announcer가 "연결 실패, 응답 없음"을 받는지 확인한다.
- [medium/kotlin] onTareClick does not check phase, so the Tare retry snackbar can zero the scale during a brew — `app/src/main/java/com/timemoredot/app/ui/brew/BrewViewModel.kt:381`
  - 수정안: In onTareClick, return unless canTare conditions hold (idleLike and connected and Live). Or guard controller.tare() with a phase check. Optionally dismiss the retry snackbar when phase leaves Idle/WaitingForPour.
- [medium/kotlin] rememberNow keeps a stale `now` when it goes from inactive to active, so one frame can show a negative timer — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:103-111`
  - 수정안: Compute the displayed value as `max(now, timer.runningSinceNanos)`, or read `clock.nowNanos()` directly in the first composition after `active` flips. Clamp elapsed to >= 0 in the display helpers.
- [medium/a11y] 추출 중 레시피 이름(상단 제목)이 비활성 색이라 거의 안 보이고 글꼴 200%에서 말줄임으로 잘림 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:411-423`
  - 수정안: 잠금 상태에서는 제목을 TextButton(enabled=false) 대신 onSurface 색의 일반 Text로 그리고, 아래쪽 화살표만 숨긴다. TalkBack의 stateDescription '추출 중에는 바꿀 수 없음'은 유지한다. 글꼴 ≥1.3이면 maxLines를 2로 늘린다.
- [medium/a11y] 자체 focusRing(SC 2.4.7·2.4.11 기준)이 일부 대화형 요소에만 붙어 있다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:520,541,561,527,576,584,606`
  - 수정안: toggleable·selectable 행과 SegmentedButton, 대화상자 TextButton에도 Modifier.focusRing(...)을 붙인다. 이렇게 하면 앱 안에서 포커스 표시 방식이 하나로 맞는다.
- [medium/a11y] 밸브 열기·닫기 알림이 Polite라 5초 무게 요약 낭독에 밀리거나 늦어질 수 있다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewText.kt:91-92 (StatusAnnouncer.kt:79)`
  - 수정안: ValveOpen·ValveClose만 Assertive로 보낸다. 추출을 멈춰야 하는 오류용 Assertive와 의미가 다르다는 점을 ui-design 8.3 표에 적는다. 또는 밸브 알림이 나가는 시각 앞뒤로는 5초 요약을 건너뛴다.
- [medium/visual] 리본 그래프 y축 단위 'g'가 맨 위 눈금 글자와 겹치거나 붙어서 'g 160'처럼 읽힌다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:546 (screenshots/brew_running_free_light.png·_dark.png, brew_reconnecting_running_light.png·_dark.png)`
  - 수정안: y 눈금 글자 겹침 판정의 첫 상자로 unitG 상자(0,0,w,h)를 넣어 겹치는 맨 위 눈금 글자를 빼라. 아니면 그림 영역 위쪽(dataTop)을 단위 글자 줄 높이 이상 아래로 내려 단위 줄을 따로 잡아라. 's'도 같은 방식으로 마지막 x 눈금 글자·띠와 겹치지 않는 자리에 둔다.
- [medium/visual] 가로 배치 지표 크기가 4.3과 다르고, 밸브 유무에 따라 무게와 타이머의 크기 관계가 뒤집힌다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:306, :380 (screenshots/brew_running_recipe_landscape_light.png, brew_running_valve_landscape_light.png, brew_idle_landscape_dark.png)`
  - 수정안: 밸브 칸이 있을 때도 3.2 높이 기준 무게 축소를 똑같이 적용해 s′ ≤ s₀를 지켜라. 지표 상한(24/20 sp)이 의도라면 ui-design 3.2·4.3에 값과 근거(D1 비율)를 적어 명세와 코드를 맞춰라. 의도가 아니라면 상한을 지우고 4.3대로 너비 기준 20~36 sp를 쓴다. 그리고 밸브·비밸브 가로 스크린샷의 타이머 크기가 같은지 확인한다.
- [medium/visual] brew_running_banner_resize_frame에서 숫자 겹은 실시간 값인데 칩·그래프는 멈춘 값이라 같은 프레임 안에서 서로 어긋난다 — `app/src/test/java/com/timemoredot/app/ui/brew/BrewScreenshotTest.kt:178-179 (screenshots/brew_running_banner_resize_frame_light.png·_dark.png)`
  - 수정안: 고정 장면에서 하나의 MutableStateFlow<BrewReadout>과 하나의 상태를 두고 값만 바꿔 실제 VM처럼 흐름 인스턴스를 유지하라. 다시 찍은 뒤에도 숫자 겹이 늦으면 WeightArea·FlowMetric 수집 경로가 같은 프레임에 갱신되게 고친다.
- [medium/visual] brew_running_long_step_light_font200에 단계 카드가 아예 안 보여서 36자 단계 이름이 생략 없이 표시되는지 판정할 수 없다 — `app/src/test/java/com/timemoredot/app/ui/brew/BrewScreenshotTest.kt:136 (screenshots/brew_running_long_step_light_font200.png, brew_running_valve_light_font200.png)`
  - 수정안: 이 장면은 카드로 스크롤한 뒤(onNodeWithTag(card).performScrollTo()) 한 장 더 찍거나, 스크롤 열 전체 높이를 담는 캡처를 써서 카드가 보이게 하라. x축 글자가 첫 화면에서 잘리지 않게 shapeAreaMinDp의 글꼴 확대 최소 높이에 x 눈금 글자 줄을 넣었는지도 확인한다.
- [medium/kotlin] 일회성 이벤트 수집이 수명주기와 무관해, 화면을 떠나 있던 동안의 Announce가 돌아올 때 한꺼번에 낭독된다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:172`
  - 수정안: 수집을 repeatOnLifecycle(STARTED)로 감싸거나, 컴포지션 밖에서 쌓인 Announce를 버린다(Haptic처럼 신선도 검사). Finished·SaveFailed처럼 잃으면 안 되는 이벤트만 남긴다.

### 불확실 (0)


### low 지적 (18)

- [low/qa] [8.6] brew_weight_worst_w320_light_font200 스크린샷이 완료 조건의 기대(단위 아래 줄)와 다르다 — `app/src/test/java/com/timemoredot/app/ui/brew/BrewScreenshotTest.kt:285`
- [low/kotlin] Side effects written during composition in ShapeArea (morphCount / lastPhase) — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:166-173`
- [low/kotlin] Event-collecting LaunchedEffect captures navigation/callback lambdas and the clock only at first composition — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:174-216`
- [low/kotlin] State written during composition to reset showLeave/saveFailed — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:169-171`
- [low/kotlin] Conditional composable call to rememberSpoken resets its debounce state — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:219 (also BrewParts.kt:123, 169)`
- [low/kotlin] noDataHoldUntil is a plain VM field mutated inside the flow, so it survives WhileSubscribed teardown — `app/src/main/java/com/timemoredot/app/ui/brew/BrewViewModel.kt:263-280`
- [low/kotlin] toBlob() runs on Main every 10 s (up to about 470 KB) — `app/src/main/java/com/timemoredot/app/BrewSessionController.kt:219, 240`
- [low/kotlin] Unused constructor dependency in BrewViewModel — `app/src/main/java/com/timemoredot/app/ui/brew/BrewViewModel.kt:222`
- [low/a11y] S4 설정에 5초 값 낭독을 끄는 방법이 없다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewAnnouncer.kt:141-157`
- [low/a11y] 레시피 선택 칩·제목 버튼에서 텍스트와 stateDescription이 같은 문구라 중복 낭독 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:416 and app/src/main/java/com/timemoredot/app/ui/components/Components.kt:93,115`
- [low/a11y] 시트·대화상자·빈 상태 제목에 heading 시맨틱이 없다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:515,555 and app/src/main/java/com/timemoredot/app/ui/components/Components.kt:193`
- [low/a11y] 동작 줄 버튼의 onClick(label, action=null)이 비활성 버튼에도 클릭 동작을 남길 수 있다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewParts.kt:258-261`
- [low/visual] 목표선과 리본이 겹치는 구간에서 surface 테두리가 흰 빗금처럼 보이고, 회색 대시 자체는 리본 위에서 거의 안 보인다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:557 이하 목표선 그리기 (screenshots/brew_running_recipe_light.png 0~10 s, brew_running_waiting_end_light.png 75~90 s, brew_running_step_change_frame_light.png)`
- [low/visual] 리본 외곽 다각형이 급하게 꺾이는 곳에서 가시·톱니가 생긴다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:407-419 (screenshots/brew_running_waiting_end_light.png 85~90 s, brew_running_spike_light.png 62 s)`
- [low/visual] 좁은 창·글꼴 확대에서 지표를 세로로 쌓으면 유량 칸이 어중간하게 들여 쓰여 시간 칸과 줄이 맞지 않는다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewParts.kt:85-90, :125 (screenshots/brew_weight_worst_w320_light_font200.png, brew_weight_worst_w220_light_font200.png)`
- [low/visual] D1·D5 대화상자가 스크린샷에서 화면 좌우 끝까지 붙어 그려진다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:578, :601 (screenshots/brew_leave_confirm_light.png, brew_reset_confirm_dark.png, brew_leave_confirm_light_font200.png)`
- [low/visual] 낱말 단위 줄바꿈이 일부 문구에서 지켜지지 않는다 — `app/src/main/java/com/timemoredot/app/ui/theme/Theme.kt:87과 서버 안내문·배너·하단 탭 라벨 호출부 (screenshots/brew_weight_worst_w220_light_font200.png, brew_reconnecting_running_light_font200.png)`
- [low/visual] 추출 중 상단 바 레시피 이름이 비활성 색이라 팔 길이 거리에서 읽기 어렵다 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:415-426 (screenshots/brew_running_recipe_light.png, brew_running_long_step_light.png)`

### 구현자 거부 (1)

- [/] 완료 조건 실패: S1 설계 검증 반영 스크린샷(ui-design 8.6 '2026-10-29 설계 검증 반영 추가'): brew_reconnecting_running_recipe(V60 기본 2단계 카드 + Reconnecting 7a 배너, 글꼴 1.0, 360 × 800, '지금 다시 시도'·'연결 끊기'가 스크롤 없이 보임), brew_running_start_frame(Running 진입 뒤 t 0.5 s), brew_running_banner_resize_frame(Running 중 7a 배너가 나타난 뒤 0 ms), brew_finished_save_failed(5.2 확정 저장 실패: Indefinite 스낵바 '다시 시도' + 동작 줄 '다시 저장', 하단 탭 숨김, FakeBrewRepository가 확정 saveDraft에서 예외) 각각 _light.png·_dark.png와 Robolectric sdk = 33의 brew_idle_negative_light_font200.png(w360dp, −2.0), brew_weight_worst_light_font200.png(w360dp, −888.8), brew_weight_worst_w320_light_font200.png(w320dp, 단위 아래 줄)가 screenshots/에 있고 verifyRoborazziDebug가 통과한다 — ``
  - 근거: Under the ui-design 4.2 rule itself, the w320 / font 2.0 scene cannot reach path 2, so the expectation is wrong for this setup. 4.2 says to choose the layout by measurement with rememberTextMeasurer at the current fontScale, with no fixed width condition. Faking a unit-below layout at w320 would break that rule.

Measured in this Robolectric sdk=33 setup (debug print in the WeightArea geo block): fontScale=2.0, but the reference string measures 3.695 dp/sp. That is Compose's non-linear scaling, not the linear ~6.3 dp/sp that 8.6 assumes. So '−8888.8' + 8 dp + 'g' fits on one line in the 288 dp

### 구현 요약

I fixed 7 of the 8 findings and rejected the w320 one. verify.sh exit 0 (_workspace/build/verify_20261030_132728.log), verifyRoborazziDebug PASS (verify_20261030_132759.log), and I re-recorded every screenshot.

What changed:
1. [Q9] Tare and valve-tint replay: ShapeArea and ValveCell now remember the trigger value from their first composition and only animate when the counter goes above it (BrewChart.kt seenTare, WeightArea.kt seenTint). I added a TareMotionKey semantics property. A new BrewScreenTest case runs TareDone, then the empty state, then reconnect, and checks the motion does not replay. Without the guard this test fails; I checked.
2. Backlogged events: new BrewViewModel.dropBackloggedEvents() keeps only OpenSummary and Snackbar(SaveFailed). BrewScreen calls it from DisposableEffect(vm) before collection starts. I did not use repeatOnLifecycle, as the verdict advised. New BrewViewModelTest case.
3. Valve cell missing in landscape: when the height is bounded, the side-cell decision now uses width only (4.11). Cell height = max(cell content, number line + under line). WeightArea reports its minimum height through onMinHeight, and BrewScreen uses that as the FILL minimum. The band, if needed, gets its height the same way. New BrewScreenTest case (w800dp-h360dp) asserts the valve node exists and is at least 48 dp tall. The brew_running_valve_landscape screenshot now shows the cell, and the timer is visible without scrolling.
4. Band for a disconnect still in progress: when the reading is Stale(Disconnected), the chart adds a band from the last normal sample to the app-timer elapsed time. ShapeArea now gets timer and clock (contract values). The cache rebuilds only once per second while disconnected.
5. D7 target line:
   - The current segment's border is now target width + 2 dp.
   - The current segment's dash phase now matches the full path.
   - The actual centerline is redrawn on top of the target at the minimum ribbon width (2.5 dp).
   - Related bug I also fixed: segment breaks were checked on thinned sample spacing, which is over 500 ms on long axes. The ribbon was drawn as broken dots. Breaks are now checked on the raw neighbouring samples, and the flat Hario section is a solid line.
6. Landscape D1 hierarchy: the left-pane metrics are capped at 24 sp, or 20 sp when a valve cell is present (the 4.3 minimum). Metric line height is now proportional to size (1.22 em). Weight is now about 56 sp against 24 sp metrics.
7. 'g' shown while the unit is unknown: the unit glyph is no longer drawn for UnitUnknown and UnitUnknownTimedOut. It is still measured, so the decimal anchor does not move.
8. Extra fix: autoSizeWeight now measures the reference string at each candidate size. Before, it measured once at 100 sp and scaled. With non-linear font scaling, small sizes grow more, so the old method under-measured them and the unit ran past the right gutter. The Float overload is kept for existing tests. New tests cover path 2 and the non-linear case.

Where I depart from the spec (ui-design needs updating):
- 3.2/4.3: landscape metric caps of 24/20 sp, and proportional metric line height.
- 4.4: the actual centerline drawn on top of the target line.
- 4.11: wide-window cell height = max(cell content, weight area), with the parent minimum height.
- 8.6: the w320 'unit on the line below' expectation cannot be met as measured.

New screenshot: brew_weight_worst_w220_light_font200 shows path 2. At w220 the 'step guide' text is also clipped; that was there before this change and I did not fix it.

Not done: metricSize still uses the single 100 sp ratio (the same non-linear under-measure, out of scope). I did not run the device checks. No BLE defects found.

### 스크린샷

- screenshots/brew_bluetooth_off_dark.png
- screenshots/brew_bluetooth_off_light.png
- screenshots/brew_connecting_dark.png
- screenshots/brew_connecting_light.png
- screenshots/brew_disconnected_dark.png
- screenshots/brew_disconnected_light.png
- screenshots/brew_disconnected_running_dark.png
- screenshots/brew_disconnected_running_light.png
- screenshots/brew_failed_dark.png
- screenshots/brew_failed_light.png
- screenshots/brew_failed_timeout_dark.png
- screenshots/brew_failed_timeout_light.png
- screenshots/brew_finished_save_failed_dark.png
- screenshots/brew_finished_save_failed_light.png
- screenshots/brew_idle_dark.png
- screenshots/brew_idle_free_dark.png
- screenshots/brew_idle_free_light.png
- screenshots/brew_idle_landscape_dark.png
- screenshots/brew_idle_landscape_light.png
- screenshots/brew_idle_light.png
- screenshots/brew_idle_light_font200.png
- screenshots/brew_idle_negative_dark.png
- screenshots/brew_idle_negative_light.png
- screenshots/brew_idle_negative_light_font200.png
- screenshots/brew_idle_valve_dark.png
- screenshots/brew_idle_valve_light.png
- screenshots/brew_idle_valve_open_dark.png
- screenshots/brew_idle_valve_open_light.png
- screenshots/brew_leave_confirm_dark.png
- screenshots/brew_leave_confirm_light.png
- screenshots/brew_leave_confirm_light_font200.png
- screenshots/brew_live_oz_dark.png
- screenshots/brew_live_oz_light.png
- screenshots/brew_location_off_dark.png
- screenshots/brew_location_off_light.png
- screenshots/brew_model_mismatch_dark.png
- screenshots/brew_model_mismatch_light.png
- screenshots/brew_morph_frame_dark.png
- screenshots/brew_morph_frame_light.png
- screenshots/brew_nodata_dark.png
- screenshots/brew_nodata_light.png
- screenshots/brew_nodevice_dark.png
- screenshots/brew_nodevice_light.png
- screenshots/brew_overload_dark.png
- screenshots/brew_overload_light.png
- screenshots/brew_paused_dark.png
- screenshots/brew_paused_light.png
- screenshots/brew_permission_missing_dark.png
- screenshots/brew_permission_missing_light.png
- screenshots/brew_permission_permanently_denied_dark.png
- screenshots/brew_permission_permanently_denied_light.png
- screenshots/brew_recipe_picker_dark.png
- screenshots/brew_recipe_picker_empty_dark.png
- screenshots/brew_recipe_picker_empty_light.png
- screenshots/brew_recipe_picker_light.png
- screenshots/brew_reconnecting_running_dark.png
- screenshots/brew_reconnecting_running_light.png
- screenshots/brew_reconnecting_running_light_font200.png
- screenshots/brew_reconnecting_running_recipe_dark.png
- screenshots/brew_reconnecting_running_recipe_light.png
- screenshots/brew_reconnecting_running_valve_dark.png
- screenshots/brew_reconnecting_running_valve_light.png
- screenshots/brew_reset_confirm_dark.png
- screenshots/brew_reset_confirm_light.png
- screenshots/brew_running_banner_resize_frame_dark.png
- screenshots/brew_running_banner_resize_frame_light.png
- screenshots/brew_running_free_dark.png
- screenshots/brew_running_free_light.png
- screenshots/brew_running_long_step_dark.png
- screenshots/brew_running_long_step_light.png
- screenshots/brew_running_long_step_light_font200.png
- screenshots/brew_running_on_target_dark.png
- screenshots/brew_running_on_target_light.png
- screenshots/brew_running_peak_dark.png
- screenshots/brew_running_peak_light.png
- screenshots/brew_running_recipe_dark.png
- screenshots/brew_running_recipe_landscape_dark.png
- screenshots/brew_running_recipe_landscape_light.png
- screenshots/brew_running_recipe_landscape_light_font200.png
- screenshots/brew_running_recipe_light.png
- screenshots/brew_running_reduce_motion_dark.png
- screenshots/brew_running_reduce_motion_light.png
- screenshots/brew_running_ribbon_light_font200.png
- screenshots/brew_running_snackbar_dark.png
- screenshots/brew_running_snackbar_light.png
- screenshots/brew_running_spike_dark.png
- screenshots/brew_running_spike_light.png
- screenshots/brew_running_start_frame_dark.png
- screenshots/brew_running_start_frame_light.png
- screenshots/brew_running_step_change_frame_dark.png
- screenshots/brew_running_step_change_frame_light.png
- screenshots/brew_running_valve_countdown_dark.png
- screenshots/brew_running_valve_countdown_light.png
- screenshots/brew_running_valve_cue_dark.png
- screenshots/brew_running_valve_cue_light.png
- screenshots/brew_running_valve_dark.png
- screenshots/brew_running_valve_landscape_dark.png
- screenshots/brew_running_valve_landscape_light.png
- screenshots/brew_running_valve_light.png
- screenshots/brew_running_valve_light_font200.png
- screenshots/brew_running_valve_reduce_motion_dark.png
- screenshots/brew_running_valve_reduce_motion_light.png
- screenshots/brew_running_waiting_end_dark.png
- screenshots/brew_running_waiting_end_light.png
- screenshots/brew_scan_empty_dark.png
- screenshots/brew_scan_empty_light.png
- screenshots/brew_scan_failed_dark.png
- screenshots/brew_scan_failed_light.png
- screenshots/brew_scanning_stopped_dark.png
- screenshots/brew_scanning_stopped_light.png
- screenshots/brew_settings_dark.png
- screenshots/brew_settings_light.png
- screenshots/brew_tare_drop_frame_dark.png
- screenshots/brew_tare_drop_frame_light.png
- screenshots/brew_tare_ring_frame_dark.png
- screenshots/brew_tare_ring_frame_light.png
- screenshots/brew_unit_unknown_dark.png
- screenshots/brew_unit_unknown_light.png
- screenshots/brew_unit_unknown_timed_out_dark.png
- screenshots/brew_unit_unknown_timed_out_light.png
- screenshots/brew_waiting_for_pour_dark.png
- screenshots/brew_waiting_for_pour_light.png
- screenshots/brew_weight_worst_light_font200.png
- screenshots/brew_weight_worst_w220_light_font200.png
- screenshots/brew_weight_worst_w320_light_font200.png
- screenshots/connect_bluetooth_off_dark.png
- screenshots/connect_bluetooth_off_light.png
- screenshots/connect_connecting_dark.png
- screenshots/connect_connecting_light.png
- screenshots/connect_disconnected_dark.png
- screenshots/connect_disconnected_light.png
- screenshots/connect_failed_dark.png
- screenshots/connect_failed_light.png
- screenshots/connect_location_off_dark.png
- screenshots/connect_location_off_light.png
- screenshots/connect_no_device_dark.png
- screenshots/connect_no_device_light.png
- screenshots/connect_permission_denied_light_font200.png
- screenshots/connect_permission_missing_api30_dark.png
- screenshots/connect_permission_missing_api30_light.png
- screenshots/connect_permission_missing_dark.png
- screenshots/connect_permission_missing_light.png
- screenshots/connect_permission_permanently_denied_dark.png
- screenshots/connect_permission_permanently_denied_light.png
- screenshots/connect_reconnecting_dark.png
- screenshots/connect_reconnecting_light.png
- screenshots/connect_scan_empty_dark.png
- screenshots/connect_scan_empty_light.png
- screenshots/connect_scan_failed_dark.png
- screenshots/connect_scan_failed_light.png
- screenshots/connect_scanning_duplicate_name_dark.png
- screenshots/connect_scanning_duplicate_name_light.png
- screenshots/connect_scanning_empty_dark.png
- screenshots/connect_scanning_empty_light.png
- screenshots/connect_scanning_focused_dark.png
- screenshots/connect_scanning_focused_light.png
- screenshots/connect_scanning_results_dark.png
- screenshots/connect_scanning_results_light.png
- screenshots/connect_scanning_stopped_dark.png
- screenshots/connect_scanning_stopped_light.png
- screenshots/connect_scanning_unnamed_dark.png
- screenshots/connect_scanning_unnamed_light.png
- screenshots/device_sheet_battery_low_dark.png
- screenshots/device_sheet_battery_low_light.png
- screenshots/device_sheet_battery_low_light_font200.png
- screenshots/device_sheet_battery_unknown_dark.png
- screenshots/device_sheet_battery_unknown_light.png
- screenshots/device_sheet_connected_dark.png
- screenshots/device_sheet_connected_light.png
- screenshots/device_sheet_dis_failed_dark.png
- screenshots/device_sheet_dis_failed_light.png
- screenshots/device_sheet_dis_reading_dark.png
- screenshots/device_sheet_dis_reading_light.png
- screenshots/device_sheet_disconnected_dark.png
- screenshots/device_sheet_disconnected_light.png
- screenshots/device_sheet_failed_dark.png
- screenshots/device_sheet_failed_light.png
- screenshots/device_sheet_unit_failed_dark.png
- screenshots/device_sheet_unit_failed_light.png
- screenshots/device_sheet_unit_unknown_dark.png
- screenshots/device_sheet_unit_unknown_light.png
- screenshots/settings_valve_haptic_dark.png
- screenshots/settings_valve_haptic_light.png

