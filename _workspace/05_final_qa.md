# 05 최종 통합 QA (2026-11-02)

범위: 마일스톤 경계를 넘는 계약만. 코드는 고치지 않았다.

## 1. 빌드·테스트 (직접 실행)

| 명령 | 종료 코드 | 로그 |
|------|-----------|------|
| `verify.sh --rerun-tasks test lintDebug assembleDebug` | 0 (117 tasks 모두 실행, 캐시 없음) | `_workspace/build/verify_20261102_063607.log` |
| `verify.sh :app:verifyRoborazziDebug` | 0 (`:app:testDebugUnitTest` 다시 실행) | `_workspace/build/verify_20261102_063736.log` |
| `verify.sh :app:assembleRelease` | 0 | `_workspace/build/verify_20261102_063810.log` |

테스트 수(build/test-results XML 합계):

| 모듈 | 결과 디렉터리 | 파일 | tests | failures | errors | skipped |
|------|---------------|------|-------|----------|--------|---------|
| :app | testDebugUnitTest (Roborazzi verify 실행분) | 61 | 380 | 0 | 0 | 0 |
| :core:ble | testDebugUnitTest | 10 | 84 | 0 | 0 | 0 |
| :core:brew | test | 9 | 75 | 0 | 0 | 0 |
| :core:protocol | test | 9 | 129 | 0 | 0 | 0 |
| 합계 | | 89 | 668 | 0 | 0 | 0 |

`test` 태스크는 :app·:core:ble에서 debug 단위 테스트만 돈다(release 단위 테스트 태스크는 로그에 없음). connectedDebugAndroidTest(에뮬레이터)는 이번에 돌리지 않았다.

## 2. 화면 간 흐름 계약

| 연결 | 생산자 | 소비자 | 결과 |
|------|--------|--------|------|
| S7 '추출' → S1 레시피 선택 | `RecipeListViewModel.kt:53-58` (phase 판정 → `controller.selectRecipe` → `OpenBrew`) | `RecipeListScreen.kt:70` → `Navigation.kt:155` `goTab(Brew)` | 일치. 선택은 앱 범위 컨트롤러에 들어가 S1이 같은 값을 본다 |
| S1 완료 → S6 | `BrewSessionController` `Finished(id)` → `BrewViewModel.kt:373` `OpenSummary(id,false)` (Channel UNLIMITED, `dropBackloggedEvents`가 OpenSummary 보존 `:410`) | `BrewScreen.kt:197` → `Navigation.kt:127` `Summary(id, recovered)` | 일치 |
| S6 저장 → saved_session_id | `SummaryViewModel.kt:85-87` finish(Completed/Interrupted) → newBrew(실시간만) → `Saved(id)` | `Navigation.kt:146` previousBackStackEntry(=Brew)에 Long 저장 → `:123` getStateFlow<Long?> → `BrewScreen.kt:147` `onSessionSaved` + 지움. `DisposableEffect`(백로그 버림)가 같은 프레임 LaunchedEffect보다 먼저라 Saved 스낵바가 버려지지 않음 | 일치 |
| S6 저장 → S9 목록 | `Daos.kt:48` status = completed/interrupted | `Daos.kt:27` `status != 'in_progress'` | 일치 |
| S10 삭제 → session_deleted | `Navigation.kt:183-190` (이전이 History면 그쪽에 true, S1 '보기'로 왔으면 History로 이동 후 true) | `Navigation.kt:168-171` getStateFlow(Boolean) → `HistoryScreen.kt:99-100` 스낵바 + false로 지움 | 일치 |
| D2 복구 | `BrewViewModel.kt:356` VM 생성 때 `findDraft(controller.draftId)` (실시간 초안 제외, `Daos.kt:36`) → `:433-437` OpenSummary(recovered=true) | `SummaryViewModel.kt:85` Interrupted, newBrew 안 부름. `Navigation.kt:147` 버리기 → `onSessionGone` | 일치. 프로세스 종료 뒤 S6 복원 + D2 동시 발견도 `onSessionSaved/onSessionGone`이 지움 |
| 데모 분리 | `VariantHooks.kt`(debug):26-30 DB `timemore_demo.db`, `DemoScale.kt:40` 기기 저장 `scale_demo` | 실제: `AppDatabase.kt:33` `timemore.db`, `BleEnvironment.kt:56` `scale` | 명세(10.1.4) 대로 분리. 단 `ui` prefs(S4 설정)는 공유(low) |

## 3. 실기기 전용 위험

| 항목 | 결과 |
|------|------|
| 권한 선언 | `core/ble/src/main/AndroidManifest.xml:7-12`: BLUETOOTH·ADMIN·FINE_LOCATION maxSdk 30, SCAN `neverForLocation`, CONNECT. `BlePermissions.kt:9-15`(≤30 FINE_LOCATION, 31+ SCAN·CONNECT, 위치 서비스는 ≤30만)와 일치 |
| 런타임 요청 | `MainActivity.kt:22-30` RequestMultiplePermissions, deniedOnce+rationale로 영구 거부 판정, `PermissionMonitor.kt:41-46` onResume 재확인. 블루투스 켜기 경로에 권한 확인 없음(아래 high) |
| exported | release 병합 매니페스트: MainActivity만 앱 선언(exported=true, LAUNCHER). 라이브러리 병합분: androidx.startup provider(exported=false), Room 서비스(false), profileinstaller 수신기(exported=true, `android.permission.DUMP` 보호) |
| FileProvider | 없음(병합 매니페스트 provider는 androidx.startup 하나). CSV는 CreateDocument Uri + `AppContainer.kt:48-53` |
| release 데모 제외 | release dex grep(`ble/demo`, `protocol/demo`, `FakeScaleClient`, `DemoLauncher`) = 0, debug = 32. `-i demo` 14건은 모두 Emoji/Motion 문자열. 병합 매니페스트 DemoLauncher release 0 / debug 1. releaseRuntimeClasspath에 testFixtures 변형 없음(debug에는 `:core:protocol` 두 번째 변형 있음) |
| allowBackup | `app/src/main/AndroidManifest.xml:5` false, 병합본 유지. `dataExtractionRules` 없음(low) |
| 프레임 로그 | `AppContainer.kt:61` `debugFrameLog = BuildConfig.DEBUG` |

## 4. P1~P11 반영

| ID | 반영 위치 | 일치 |
|----|-----------|------|
| P1 | `WeightArea.kt:171`(bounded && fits → row), `:363-366` 한 줄 배치; ui-design.md:166, 1496; 스크린샷 `screenshots/brew_running_valve_landscape_{light,dark}.png` | 일치 |
| P2 | `DefaultRecipes.kt:13`, `:29` ("0:40까지 기다리기", 60, 40); architecture.md:852, 865, 869, 871; ui-design.md:713, 715, 723. DB 이전 없음(스키마 1) | 일치 |
| P3 | milestones.json:420 M8 done, :447 FIX-6 done | 일치(상태만) |
| P4 | `WeightArea.kt:202` `20f + 2f + stateWidthDp <= 56f`, `:364-365` 아이콘 20 dp·간격 2 dp, 아니면 `:368-369` 세로 24 dp/4 dp | 일치 |
| P5 | `DefaultRecipes.kt:36-43`(테츠 카스야 기본 기다리기 단계 없음, 6888 2·3차 뒤 그대로); architecture.md:898 알려진 문제 | 일치(바꾸지 않음) |
| P6 | `BrewChart.kt:584-590` 모든 레시피 `isWaitStep` 점선 덧그림(밸브 조건 없음); ui-design.md:377, 1497 | 일치 |
| P7 | `RecipeEditScreen.kt:147-149` `isBackEnabled = hasUnsavedChanges || Saving`, Saving이면 D4 없음; architecture.md:1024, 1816 | 일치 |
| P8 | `RecipeScreenshotTest.kt:105-107`, `screenshots/recipe_edit_valve_cards_{light,dark}.png`; ui-design.md:1361 | 일치 |
| P9 | 절차 결정. `_workspace/03_feature-engineer_M10M11_P9_impl.md`, milestones.json:511 M10 done, :541 M11 awaiting-device | 일치(코드 대상 아님) |
| P10 | `RecipeListScreen.kt:118-120` 배너 문구·"추출 화면으로"; `Components.kt:158` Progress = secondaryContainer, `:161` shapes.medium; ui-design.md:707 | 일치 |
| P11 | `DemoScaleDevice.kt:135` `t >= 20.0 && t < 25.0`; architecture.md:1687 | 일치 |

## 지적

### high 1건

**[Q10][android-skills:android-permissions-security] API 31+에서 BLUETOOTH_CONNECT 없이 "블루투스 켜기"를 누르면 SecurityException으로 앱이 종료될 수 있다**
- 생산자: `core/ble/src/main/kotlin/com/timemoredot/ble/BleScaleClient.kt:160-162`. 막힘 순서는 `!env.isBluetoothOn() -> BluetoothOff`가 `!env.hasPermissions() -> PermissionMissing`보다 먼저다. 그래서 권한이 없고 블루투스도 꺼져 있으면 화면은 BluetoothOff를 보인다.
- 소비자: `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt:515` 배너 "블루투스 켜기"와 `ui/connect/ConnectScreen.kt:124`가 `actions::requestEnableBluetooth`를 부른다. 이 함수(`ui/SystemActions.kt:39`)는 권한 확인과 try/catch 없이 `ACTION_REQUEST_ENABLE`을 띄운다(`MainActivity.kt:31,41`).
- 오작동: 시스템 설정 앱의 RequestPermissionActivity는 Android 12부터 BLUETOOTH_CONNECT를 요구한다. 그래서 권한이 없으면 실행이 "Permission Denial … requires android.permission.BLUETOOTH_CONNECT" SecurityException으로 끝난다(공개 보고 다수). 클릭 처리기에서 잡지 않으므로 프로세스가 죽는다.
- 재현(실기기, API 31+): 앱을 새로 설치하고 권한을 주지 않는다. 시스템 블루투스를 끄고 앱을 연다. S1 배너 또는 S2에서 "블루투스 켜기"를 누르면 크래시가 예상된다. 데모·Robolectric·에뮬레이터 테스트는 이 경로를 지나지 않는다(architecture.md:1731 "확인 대상 아님").
- 고칠 방법: `requestEnableBluetooth`에서 SDK ≥ 31이고 BLUETOOTH_CONNECT가 없으면 권한 요청(`requestPermissions`)을 먼저 띄운다. 또는 31+에서 막힘 순서를 PermissionMissing > BluetoothOff로 바꾸고 ui-design 10.x를 함께 고친다. 어느 쪽이든 `launch`를 `SecurityException`·`ActivityNotFoundException` catch로 감싼다. 프로브 B 권한 시나리오에 "권한 없음 + 블루투스 꺼짐" 행을 넣는다.
- 확신도: 플랫폼 동작은 문서·공개 크래시 보고 근거이고 실기기로 확인하지 않았다.

### medium 0건

### low 5건(개수만)


## 메인 처리 (2026-11-02)

- [Q10] high → 결정 P12로 수정: app/src/main/java/com/timemoredot/app/ui/SystemActions.kt requestEnableBluetooth(API 31+ 권한 없으면 권한 요청, 실행 실패 시 ACTION_BLUETOOTH_SETTINGS). SystemActionsTest 5/5 통과(_workspace/build/verify_20261102_064402.log). 실기기 확인 항목 B2-b '권한 없음 + 블루투스 꺼짐 + 켜기 버튼' 추가 필요.
