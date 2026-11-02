# M8 구현 요약 — S1 추출 화면·S4·S5·D1·D5, BrewSessionController, BrewViewModel

## 재실행(2026-11-02, wf_d0bbcb11-ada 남은 지적 11건)
- verify.sh(test lintDebug assembleDebug) 종료 0: `_workspace/build/verify_20261102_004553.log`. recordRoborazziDebug(`verify_20261102_004445.log`)·verifyRoborazziDebug(`verify_20261102_004631.log`) 통과. 스크린샷 183장 다시 기록.
- (1) 영점: `BrewViewModel.onTareClick`·`BrewSessionController.tare()`가 phase Idle·WaitingForPour가 아니면 보내지 않는다. 테스트: TareFailed(Timeout) 스낵바 → 자동 시작 Running → onTareClick·controller.tare() 뒤 Tare 수 그대로.
- (2) ConnectFailed 이유: VM이 `conn.value`를 알림보다 먼저 갱신하고 `currentConnection()`을 내준다. `BrewContent(currentConnection = vm::currentConnection)`가 알림 처리 시점에 읽는다. 테스트: VM 단위(알림 수신 시 Failed(Timeout)) + BrewScreen(실제 VM·FakeScaleClient Connecting → Failed(ConnectTimeout)) 기록용 Announcer가 '연결 실패, 응답 없음'.
- (3) `rememberNow`가 `remember(clock, active)`로 active가 바뀐 프레임에 시각을 다시 읽는다(타이머·밸브 칸·단계 카드 공용). 테스트: 일시정지 10 s → 재개 첫 프레임 '시간 0분 10초'(옛 코드에서 실패 확인).
- (4) `announcementText` ValveOpen·ValveClose → assertive=true. 다른 알림 모드는 그대로(전체 enum 테스트). StatusAnnouncerTest에 8.3 테스트 (6)(요약 Polite 뒤 300 ms 안 밸브 → Assertive 한 번, 요약 버림).
- (5) 이벤트 수집 지점에서 lifecycle이 STARTED 미만이면 Announce·Snackbar(SaveFailed 제외)를 버린다(Haptic은 기존 1 s 신선도, OpenSummary·OpenConnect는 남김). 기존 dropBackloggedEvents(컴포지션 밖)도 유지. 테스트: LocalLifecycleOwner CREATED 동안 보낸 TareDone·TareTimeout·SaveFailed → RESUMED 뒤 알림 0, TareTimeout 스낵바 없음, SaveFailed 스낵바 있음.
- (6) 상단 바 제목: 글꼴 ≥ 1.3이면 maxLines 2, 두 줄이면 expandedHeight = max(64, 두 줄 + 16)(3.1). 잠겨도 이름은 onSurface 색(TextButton enabled=false·stateDescription 유지, 화살표만 비활성 색). brew_running_valve_light_font200에 '하리오 스위치 기본' 전체.
- (7) focusRing: S4 toggleable 행 둘(RectangleShape), 임계값 SegmentedButton(itemShape), S5 selectable 행, D1 버튼 셋, D5 버튼 둘.
- (8) BrewChart: y 단위 상자(0,0)가 visibleLabelIndices 첫 상자, 겹치는 맨 위 눈금 글자는 빼고 눈금 표시·격자는 남김. x 단위 's'는 x 글자 줄(축 선 아래) 오른쪽 끝이고 첫 상자, x 글자는 오른쪽부터 4 dp 안이면 뺌. 눈금 표시는 gridX 전부.
- (9) 가로 지표 상한 24 sp(밸브 유무 무관, 20 sp 분기 삭제). 무게 s′ = min(높이 축소 뒤 s′, s₀): FillScrollColumn `refMin`이 채움 자식 최소 높이 제약 = 밸브 칸이 없을 때 받을 높이를 주고 WeightArea가 그 높이로 s₀를 구한다. metricSize는 후보 크기마다 다시 잰다(4.3). FontSpKey(테스트용 의미 속성)로 확인: 가로 NATIVE 측정 무게 52 → 52 sp, 지표 24 → 24 sp.
- (10) brew_running_banner_resize_frame: readout 흐름 하나·상태 하나에 값만 바꿔 찍음 → 숫자 겹 점선 상자·'멈춘 값 · 연결 끊김', 유량 '—'.
- (11) brew_running_long_step_light_font200: ScrollBy로 단계 카드까지 스크롤한 장면(performScrollTo는 autoAdvance=false에서 멈춰 OOM). 36자 이름 전체 보임. Shooter.font200에 mid 인자 추가.
- 사용자 판단 항목(ui-design 3.2·8.6 brew_running_valve_landscape): 가로 800 × 360·글꼴 1.0·밸브 칸에서 지표 24 sp를 지키면 타이머 값 아래 끝 282 dp > 왼쪽 칸 보이는 끝(동작 줄 위) 272 dp로 10 dp 밀린다(칸 없음 260 dp). 임의로 줄이지 않았다. 스크린샷에서 타이머 아래가 잘려 보인다.
- BLE 결함 없음. 실기기 항목은 실행하지 않았다.


## 결과
- verify.sh(test lintDebug assembleDebug) 종료 0: `_workspace/build/verify_20261030_122324.log`. recordRoborazziDebug·verifyRoborazziDebug 모두 통과(`verify_20261030_122247.log`, `verify_20261030_122306.log`). :app 단위 테스트 200개 이상 실패 0.
- 스크린샷: `screenshots/` 182장(S1 brew_* 124, settings_valve_haptic, device_sheet_unit_unknown·unit_failed 포함, M7 S2·S3 다시 기록).

## 소스
- :core:brew `Samples.kt` `STEP_TOLERANCE_GRAMS = 1.0`(한 곳), `BrewConfig.stepToleranceGrams`·`reachTimesMs` 기본값으로 연결.
- :app `BrewSessionController.kt`(8.2 전체: 입력 변환, 연결 직후 맞추기 + 4 s 미상 처리, 순서 보장 송신 큐, 10 s 임시 저장과 cancelAndJoin, runCatching 저장 실패·SaveFailed·retrySave, draftId·lastUnit·saveFailed, 버퍼 가득 참 → Finish(capReached)), `AppContainer`(CoroutineExceptionHandler, database·recipes·brews·brewController), `AppPrefs`(valve_haptic, 없으면 true), `Repositories.saveDraft`(in_progress 행만 갱신).
- `ui/brew/`: `BrewViewModel.kt`(ReadingUi·readingUi 매핑, BrewBanner·rawBanner, NoData 2 s 유지, 과부하 접기, 알림 표 phaseAnnouncement·connectionAnnouncement, 밸브 Announce·Haptic), `BrewScreen.kt`(루트·FillScrollColumn·배너·빈 상태·S4·S5·D1·D5·NavigationBackHandler·keepScreenOn·이벤트 수집 지점 하나), `WeightArea.kt`(자동 축소·소수점 기준점·점선 상자·범위 밖, ValveCell 옆 칸/띠), `BrewParts.kt`(지표·단계 카드·동작 줄), `BrewChart.kt`(리본 그래프·서버·전환·영점 모션, drawWithCache), `BrewAnnouncer.kt`(낭독 상태 기계·SpokenDebounce), `BrewText.kt`(형식·문구), `ValveHaptic.kt`, `shape/ShapeMath.kt`.
- `ui/theme/AppShape.kt`(drop·vessel, dropPath·vesselPath), `AppMotion.kt`(6.6.1 토큰, nav fade 포함), `Theme.kt`(weightDisplay 줄 높이 104/96 em).
- `ui/components/Components.kt`(칩 stateDescription에 배터리 없음, BatteryLabel, 배너 둘째 동작), `ui/device/DeviceSheet.kt`(배터리 줄 낭독, 단위 줄 = readout 슬롯), `ui/nav/Navigation.kt`(BrewScreen 연결, AppTabsScaffold 분리, 선택 탭 굵은 라벨), `StatusAnnouncer.kt`(withFrameNanos → withFrameMillis 한 번 대기).
- build.gradle: `navigationevent-compose` 1.2.0(카탈로그에 있던 별칭) 추가.

## 테스트(새로)
BrewSessionControllerTest(31), BrewViewModelTest(26), ReadingUiMappingTest, ShapeMathTest, BrewAnnouncerTest, BrewScreenTest(알림·전환·숫자 겹·지표 위치·배터리·S3 단위 줄·뒤로 가기·D1 복원·범위 밖·64 sp), TraversalOrderTest(세로 3 + 글꼴 2.0 + 가로), DeviceSheetSemanticsTest, FakeBrewRepositoryTest, RoomBrewRepositoryTest·SharedPrefsAppPrefsTest·AppContainerTest 추가 단언, 스크린샷 클래스 7개.

## 명세와 다른 점·판단
1. 단계 카드는 엔진 단계 안내가 있을 때(Running·Paused·Finished)만 보인다. Idle·WaitingForPour에 카드를 두면 360 × 800에서 모양 자리가 112 dp 아래로 줄어 서버(H ≥ 96 dp)가 숨는다. 대기판 서버 표식·라벨이 같은 단계 정보를 보인다. ui-design 3.1 7번 "레시피 선택 시만"을 "추출 중 레시피가 있을 때"로 고쳐야 한다(ui-designer 확인 필요).
2. 7a 배너 50% 규칙(고정 묶음이 창 높이 절반을 넘으면 스크롤 열 안으로)은 구현하지 않았다.
3. 서버 → 그래프 전환: 벽 → 축 선 L 점 보간·표식 자리 보간·라벨 페이드아웃·격자·리본 페이드인은 했고, 목표 수위선 → 목표 꺾은선의 꼭짓점별 보간 대신 목표선은 페이드인이다.
4. D2(recoverableDraft·onRecover*)는 M9 범위라 필드는 null, 메서드는 빈 구현.
5. 하단 탭 선택 표시는 굵은 라벨만(FILL 1 아이콘 별도 벡터 없음).
6. 지표 줄 절대 y: Robolectric 글꼴은 기준 문자열이 좁아 360 dp에서 96 sp가 골라져 지표 줄이 256 dp(상단 바 64 기준)다. 기기 글꼴(84 sp)에서 267 dp가 된다. 테스트는 상태 사이 같음(1 dp)과 허용 범위만 단언한다. 같은 이유로 brew_weight_worst_w320_light_font200도 단위가 한 줄에 들어간다(측정 판정이라 기기에서 달라진다).
7. "숫자 겹 advanceTimeBy 0": autoAdvance=false에서 Compose는 프레임 없이 재구성하지 않아 advanceTimeByFrame()(애니메이션 시간 0, 다음 프레임)으로 단언했다.
8. 그래프 축 단계형 늘리기는 명세 규칙(90% 넘으면 ×1.5)대로라 V60 250 g 끝에서 y 끝이 400 g이 된다(275 → 400). 의도와 다르면 ui-design 4.4 규칙을 고친다.
9. 스크린샷 기록은 Roborazzi 태스크(recordRoborazziDebug)로만 써진다(plain test는 쓰지 않음). 대화상자 글꼴 200은 별도 클래스 `@Config(fontScale = 2f)`(대화상자 창이 시스템 배율을 따름). 모션 줄이기 장면은 `createComposeRule(effectContext = MotionDurationScale 0)`.

## 실기기(미확인)
B4·B5·B6·B9·B12, 모션 줄이기, TalkBack 알림 노드, 밸브 진동은 하지 않았다.

## 수정 라운드(2026-10-30, 리뷰 지적 8건)
- Q9 영점 모션·밸브 색 숨 재생: ShapeArea·ValveCell이 처음 구성될 때의 계기 값을 기억하고 그보다 클 때만 재생(BrewChart.kt seenTare, WeightArea.kt seenTint). BrewScreenTest '영점 모션은 계기 없이 다시 재생하지 않는다'(TareMotionKey 의미 속성)로 확인, 가드를 빼면 실패함을 확인.
- 쌓인 일회성 결과: BrewViewModel.dropBackloggedEvents()(OpenSummary·Snackbar(SaveFailed)만 남김)를 BrewScreen이 DisposableEffect(vm)로 수집 시작 전에 부른다. repeatOnLifecycle은 쓰지 않음(검증 판정대로). BrewViewModelTest 추가.
- 가로 밸브 칸: 높이가 정해진 칸에서는 옆 칸 판정을 너비로만 하고, 칸 높이 = max(칸 내용, 숫자 줄 + 아래 줄). WeightArea가 onMinHeight로 최소 높이를 알려 FillScrollColumn 채움 최소로 쓴다(띠도 같은 방식으로 높이 확보). BrewScreenTest 가로 밸브 칸 의미 노드 단언 추가.
- 진행 중 끊김 띠: Stale(Disconnected)이면 [마지막 정상 표본, 앱 타이머 경과]를 띠로 더한다(ShapeArea에 timer·clock 전달, 끊긴 동안 1 s마다만 캐시 무효화).
- D7 목표선: 현재 구간 테두리를 목표 굵기 + 2 dp로, 구간 대시 위상을 경로와 맞춤, 목표선 위에 실제 중심선을 리본 최소 굵기(2.5 dp)로 다시 그림. 함께 고친 결함: 리본 끊김 판정이 솎은 표본 간격(긴 축에서 > 500 ms)으로 되어 있어 리본이 점으로 끊겨 그려졌다 → 솎기 전 이웃 표본으로 판정.
- 가로 D1 위계: 가로 왼쪽 칸 지표 최대 24 sp(밸브 칸 있으면 20 sp, 4.3 최소), 지표 줄 높이를 글자 크기 비례(1.22 em)로.
- 단위 미상 'g': UnitUnknown·UnitUnknownTimedOut에서 단위 글자를 그리지 않음(폭은 재어 기준점 유지).
- 자동 축소: 100 sp 한 번 잰 비율 대신 크기마다 잰 폭으로 판정(비선형 글꼴 배율에서 작은 크기 폭을 덜 재어 단위가 오른쪽 여백을 넘던 것). 
- 반박: w320·글꼴 2.0은 측정상 1번(한 줄)에서 끝난다(이 환경의 Compose 글꼴 배율은 큰 글자에 비선형, 측정 refPerSp 3.7 dp/sp). 2번 그림 근거로 brew_weight_worst_w220_light_font200(최소 창 220 dp)을 더했다.

명세와 다른 점(ui-design 고침 필요): 3.2/4.3 가로 지표 24·20 sp와 비례 줄 높이, 4.4 목표선 위 실제 중심선 겹, 4.11 넓은 창 칸 높이 = max(칸 내용, 무게 영역)과 부모 최소 높이, 8.6 w320 '단위 아래 줄' 기대(측정상 불가).
