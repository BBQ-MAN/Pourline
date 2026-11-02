# DEMO-3 구현 요약 (에뮬레이터 흐름 확인)

## 변경
- gradle/libs.versions.toml: androidx.test:runner 1.7.0, androidx.test.ext:junit 1.3.0, espresso-intents 3.7.0 추가.
- app/build.gradle.kts: testInstrumentationRunner = AndroidJUnitRunner, androidTestImplementation(BOM 플랫폼, ui-test-junit4, runner, ext-junit, espresso-intents). 버전 문자열 없음.
- app/src/androidTest/java/com/timemoredot/app/: DemoSupport.kt(도우미), DemoBrewFlowTest(1), DemoCsvExportTest(1), DemoValveTest(1), DemoFaultsTest(4) = 7개. 앱 코드는 바꾸지 않았다(testTag 추가도 필요 없었다: 기존 VALVE_STATE_TAG와 의미 정보로 충분).
- DemoLauncher 별칭 Intent로 시작, createEmptyComposeRule, 권한은 UiAutomation.grantRuntimePermission, 대기는 waitUntil(timeoutMillis 명시), Thread.sleep 없음.

## 설계 메모
- 계측 테스트는 앱과 같은 프로세스라 컨테이너(저울 연결, 추출 단계)가 테스트 사이에 이어진다. startFresh()가 DemoLauncher로 연 뒤(컨테이너가 데모로 만들어진 뒤) brewController.discard/newBrew와 scale.disconnect()로 초기화한다. 컨테이너를 DemoLauncher보다 먼저 만들면 일반(실제 BLE) 모드로 굳는다.
- 영점은 버튼이 enabled일 때만 누른다(연결 직후 누르면 무시되어 플레이키했음).
- 무게 0.0은 "무게 0.0 그램" 낭독, 타이머는 "시간 N분 M초" 낭독, 단계 카드는 "1/3 단계" 글자, 밸브는 VALVE_STATE_TAG.

## 결과
- verify.sh :app:connectedDebugAndroidTest PASS, 7 tests, 0 failures, 0 skipped (보고서 app/build/reports/androidTests/connected/debug).
- 테스트 시간: V60 흐름 20 s, CSV 18 s, 하리오 밸브 126 s(115 s 전 닫힘, 130 s 안 열림 확인), 과부하 24 s, 끊김·재연결 52 s(NoData 배너 -> 재연결 문구 -> 연결됨, 추출 중 유지, S6 아님), 블루투스 꺼짐 29 s, 연결 실패 2 s.
- 단계 카드는 영점 전에는 안 보이고(안내 문구 "영점을 맞추고 시작하세요"), 영점 뒤 "1/3 단계"가 나온다. 완료 조건 문구의 순서와 달라 테스트는 영점 -> 0.0 -> 1/3 단계 순으로 쓴다.

## 권한 거부(adb, architecture.md 10.1.4)
installDebug 뒤:
```
adb shell pm revoke com.timemoredot.app android.permission.BLUETOOTH_SCAN
adb shell pm revoke com.timemoredot.app android.permission.BLUETOOTH_CONNECT
adb shell am start -S -W -n com.timemoredot.app/.DemoLauncher
(3 s) adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -c '근처 기기'   -> 1  (칩 "권한 필요", "저울을 찾으려면 '근처 기기' 권한이 필요합니다.", "권한 허용")
adb shell pm grant ... SCAN / CONNECT; am start -S -W 같은 명령; 같은 grep -> 0
```
(첫 시도에서 에뮬레이터 "System UI isn't responding" 대화상자가 떠 grep이 0이었다. 대화상자를 닫고 다시 하자 1.)

## 프레임 로그
테스트 실행 뒤 adb logcat -d -s TimemoreBle:V: 'RX FFF1 A55A0101' 2873줄, 'TX FFF2 A55A030D' 7줄.

## 계약과 다른 점 / BLE 보고
- 없음. BLE 계층 결함 발견 없음.

## 수정 라운드 (flaky 해소)
- 원인 3개: (1) resetDemoState가 WaitingForPour·선택 레시피를 남김 → cancelWaiting, discard, newBrew, selectRecipe(null), disconnect 순으로 정리. (2) NoData는 1 s 틱에서만 판정되는데 데모 끊김 창 3.5 s 중 감지 창이 0.5 s라 약 절반은 배너가 안 뜸 → 데모 끊김 창을 20.0~25.0 s로 늘림(DemoScaleDevice, DemoScaleDeviceTest, DemoScaleClientTest, architecture.md 10.1.2, milestones.json). 앱 코드 변경 아님(testFixtures). 계약 차이이므로 architect 확인 필요. (3) tare() 0.0 확인 한도를 인자로 두고 기본 8 s, DemoBrewFlowTest만 2 s.
- 과부하 테스트는 0.0 확인 뒤 첫 관측이 15~24 s 안인지 단언(낭독 지연 d 때문에 하한 15).
- 결과: verify.sh :core:protocol:test :core:ble:test PASS. :app:connectedDebugAndroidTest 연속 2회 PASS(verify_20261102_061517.log, verify_20261102_061958.log), 실행 7, 실패 0, 건너뜀 0. 그 앞 시도 1회는 과부하 하한 17 s 가정 오류로 실패해 15 s로 고침.
