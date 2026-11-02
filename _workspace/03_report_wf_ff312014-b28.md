# 구현 보고 wf_ff312014-b28 (2026-11-02)

대상 FIX-6, DEMO-1, M9. FIX-6 완료 조건 12/12 pass, 남은 지적 2건은 명세 모순(기다리기 구간 점선) → 결정 P6(하네스 추천 잠정) 뒤 재실행. M9는 선행 실패로 건너뜀(todo 유지).

## 진행 로그

- FIX-6 라운드 0: 빌드 통과, QA 통과, 차단 지적 1건
- FIX-6 라운드 1: 빌드 통과, QA 통과, 차단 지적 1건
- FIX-6 라운드 2: 빌드 통과, QA 통과, 차단 지적 2건
- DEMO-1 라운드 0: 빌드 통과, QA 통과, 차단 지적 0건
- M9 건너뜀: 선행 마일스톤 실패
- 완료 1 / 실기기 대기 0 / 판단 필요 0 / 실패 1 / 건너뜀 1

## FIX-6 — failed → milestones.json `todo(P6 재실행)`

- rounds 2, missingReviewers [], 스크린샷 183장

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

### 남은 지적(차단) (2)

- [medium/visual] In the V60/6888 wait segment (0:10–0:40, 60 g), target dashes are drawn straight onto the actual line, so the actual line looks broken, not solid (D7, spec 4.4 conflict) — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:584-590 (screenshots/brew_running_recipe_light.png, brew_running_recipe_dark.png, brew_over`
  - 수정안: ui-designer first adds an explicit wait-segment rule to spec 4.4. Option (a): keep the actual centerline on top as a solid line. Show the wait some other way: a faint surfaceContainer time band behind the chart from the wait start to its end, or keep the target dash visible only outside the ribbon. Option (b): keep the target dash on top but use a different dash rhythm or a hollow dash, and confirm the actual-vs-target distinction in a screenshot. Re-record the 45 P2 screenshots to match the cho
- [medium/visual] The same wait step looks different in the chart: flat target visible for V60/6888, hidden under the solid line for Hario/Devil — `app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt:585-588 (screenshots/brew_running_valve_landscape_light.png vs brew_running_recipe_light.pn`
  - 수정안: Once finding 1 settles the rule, remove the valveOpen condition in a milestone that allows re-recording the portrait valve screenshots, so all recipes draw wait segments the same way. Until then, record this difference as a known item in ui-design.md section 11 (remaining decisions).

### 불확실 (0)


### low 지적 (7)

- [low/kotlin] remember 키에 measurer와 unitStyle이 빠짐 — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:144`
- [low/kotlin] 한 줄 판정이 상태 글자 폭만 재고, 실제 칸 폭 여유는 따로 재지 않음 — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:170`
- [low/kotlin] stateH를 16sp/24sp 고정 TextStyle로 재서 titleMedium과 어긋날 수 있음 — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:172`
- [low/a11y] 밸브 칸 한 줄 모양은 접근성상 문제 없음 (참고, 확인한 항목) — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:344-374`
- [low/a11y] 기다리기 단계(2번째) 진입·종료에 낭독 알림이 없음 — `app/src/main/java/com/timemoredot/app/ui/brew/BrewAnnouncer.kt:62`
- [low/a11y] 점검 한계: 전체 스크린샷 약 200장 중 일부만 직접 열어 봄, ecc:accessibility는 Glob으로 경로만 확인 — `screenshots/`
- [low/visual] The fallback from one row to a vertical stack at font 1.15 is not rendered in any screenshot, and the one-row case at 0 dp slack is not visually checked — `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt:169-202 (spec ui-design.md 4.11 '넓은 창 칸 안', 8.6)`

### 구현자 거부 (1)

- [/] V60 기다리기 구간(0:10~0:40, 60 g)의 평평한 목표선이 지나간 뒤 실제 선에 완전히 가려져, 다시 기록한 V60 장면 대부분에서 보이지 않는다 — ``
  - 근거: 가림은 명세가 정한 그리기 결과다. ui-design 4.4 목표선 행은 '목표선 위에 실제 중심선을 최소 리본 굵기 2.5 dp primary 실선으로 다시 그린다. 그래서 목표대로 따라간 구간에서 … 실제는 끊기지 않은 실선'이라고 정한다. BrewChart.kt 582-584가 이대로 구현한다. 목표와 실제가 정확히 같은 기다리기 구간에서 목표 점선이 실선 아래에 숨는 것은 이 규칙에서 바로 나온다. 그래서 문제는 명세 안의 모순(목표선 행 vs 단계 표식 행 '기다림은 평평한 목표선이 보인다')이다. 지적의 fix도 'ui-designer가 4.4의 D7 문장을 하나로 정한다'고 적는다. feature-engineer가 명세를 정할 수 없다. 그림을 고치는 방법(fix 2)은 이 마일스톤의 P1 완료 조건을 깬다. 하리오 스위치 기본도 0:10~2:00 240 g 평평 구간이 같은 방식으로 가려진다(M8부터, screenshots/brew_running_valve_light.png에서 실선만 보임). 지나간 기다리기 구간 점선을 보이게 바꾸면 세로 brew_running_valve·_countdown·_cue·_reduce_motion·brew_rec

### 구현 요약

I fixed the finding about the V60 wait-stretch target line being hidden. The fix is in app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt, in drawShapeArea. After the 2.5 dp primary centerline is redrawn, the target dash for each wait step (Recipe.isWaitStep) is now drawn once more on top of it. The dash is outline 2 dp, or 2 + stepQ dp for the current step, and it uses the existing segDash[i] so its phase matches the rest of the dashed line. As a result, the flat (0:10, 60 g)~(0:40, 60 g) target stays visible as dashes even after the head passes. I opened screenshots/brew_running_recipe_light.png and the dashes show over the solid line there.

Difference from the spec: wait steps in valve recipes (valveOpen != null, Hario Switch default and 4:6) are left out. Including them would change the portrait brew_*_valve*.png pixels and break the FIX-6 P1 condition that those shasums stay the same. The code has a shortcut comment explaining this. The Hario 0:10~2:00 240 g flat stretch still shows only the solid line, as it has since M8. The fix for that is to remove the valveOpen condition in a milestone that allows Hario pixel changes. The ui-design 4.4 target-line row also needs a sentence for the exception (wait-step dashes go on top of the centerline). That wording is for ui-designer.

Screenshots: 38 files with changed pixels were re-recorded: brew_disconnected_running, finished_save_failed, morph_frame, nodata, overload, paused, reconnecting_running_recipe, running_banner_resize_frame, running_on_target, running_peak, running_recipe, running_recipe_landscape, running_reduce_motion, running_snackbar, running_spike, running_start_frame, running_step_change_frame, running_waiting_end (light and dark of each), plus running_recipe_landscape_light_font200 and running_ribbon_light_font200. In start_frame and step_change_frame only the anti-aliased edges changed, because the dashes ahead of the head are drawn twice with the same phase. The 15 portrait valve screenshots match the shasum list in the impl report (shasum -c shows 0 mismatches). I appended the details to /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_feature-engineer_FIX-6_impl.md.

verify.sh exited 0, and verifyRoborazziDebug passed (_workspace/build/verify_20261102_021334.log). Not checked: I did not compare pixels numerically, apart from viewing brew_running_recipe_light.png.

## DEMO-1 — done → milestones.json `done`

- rounds 0, missingReviewers [], 스크린샷 0장

### 완료 조건

- **pass** — verify.sh :core:protocol:test :core:ble:test 종료 코드 0
- **pass** — DemoFramesTest(JVM): weight(1450, 0, 0, false) = A55A01010009000005AA00000000000000, writeAck(0x0D, true) = A55A030D0001010000, battery(Notify, 3, 100) = A55A010500020364
- **pass** — DemoScaleDeviceTest(JVM, 가상 nanos, 알림을 FrameReassembler·ScaleStateReducer로 풀어 판정) 영점: 영점 전 무게 213.4 g. onWrite(Tare 프레임, t0) 뒤 첫 응답 알림은 t0 + 150 ms 이후 첫 poll이고 [WeightRep
- **pass** — DemoScaleDeviceTest 유량·타이머: 모든 weight 프레임에서 flowRaw = clamp(weightRaw(t) − weightRaw(10프레임 앞), 0, 999)이고 이력이 모자라면 0이다. 리셋 상태에서 SetTimer(Start) 응답 알림이 [WeightReport, Write
- **pass** — DemoScaleDeviceTest 읽기·그 밖: ReadUnit → UnitReport(Read, 0), ReadBattery → Battery(Read, 3, 82), ReadFirmware → FirmwareVersion('demo'), ReadName → DeviceName('TIMEMORE_Do
- **pass** — DemoScaleDeviceTest 시나리오: Overload는 붓기 15~20 s 프레임만 overload=1이고 환원기 weightGrams가 null, 그 뒤 정상이다. Reconnect는 붓기 20.0~23.5 s에 poll이 빈 알림 목록이고, 붓기 40 s poll의 link가 Drop(8)이
- **pass** — DemoScaleClientTest(:core:ble, DemoScale.clientForTest, 가상 시간) 스캔·연결: startScan 1 s 뒤 scanResults가 DemoScenario 6개(주소·이름이 enum과 같음)다. connect(BrewV60.address) 뒤 Connected
- **pass** — DemoScaleClientTest 명령: send(Tare)가 Success이고, Success 뒤 readings가 213.4 g를 3개 이하 낸 다음 0.0 g를 낸다. send(SetTimer(Start))가 Success이고 status.scaleTimer=Running, 이후 readings의
- **pass** — DemoScaleClientTest 오류 경로: Reconnect 시나리오에서 Tare 뒤 붓기 20~23.5 s 동안 readings가 없고 connection은 Connected다. 붓기 40 s 뒤 connection이 Reconnecting(attempt=1) → Reconnecting(2) → 
- **pass** — 격리(코드 대조 + grep): 데모 클래스는 core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo와 core/ble/src/debug/kotlin/com/timemoredot/ble/demo에만 있다. grep -rnE 'protoco

### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (5)

- [low/qa] DemoScaleClientTest checks only the final connection state after SetTimer(Start), not that it stayed Connected — `/Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/ble/src/testDebug/kotlin/com/timemoredot/ble/demo/DemoScaleClientTest.kt:109`
- [low/kotlin] pollLoop 예외가 그대로 부모 scope로 전파된다 — `core/ble/src/debug/kotlin/com/timemoredot/ble/demo/DemoScale.kt:135`
- [low/kotlin] enableNotifications를 두 번 부르면 poll 루프가 중복된다 — `core/ble/src/debug/kotlin/com/timemoredot/ble/demo/DemoScale.kt:134-135`
- [low/kotlin] DemoEnvironment 꺼짐 타이머가 겹치거나 취소되지 않는다 — `core/ble/src/debug/kotlin/com/timemoredot/ble/demo/DemoScale.kt:83-89`
- [low/kotlin] 사용하지 않는 매개변수와 @Suppress — `core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo/DemoScaleDevice.kt:33,113`

### 구현자 거부 (0)


### 구현 요약

DEMO-1 is done. Both `verify.sh :core:protocol:test :core:ble:test` and the default `verify.sh` (test lintDebug assembleDebug) exited with code 0.

The scale simulation is in `:core:protocol` testFixtures, package `com.timemoredot.protocol.demo`: `DemoFrames`, `DemoScenario` (6), `DemoLinkEvent`, `DemoPoll` and `DemoScaleDevice`. `DemoScaleDevice` follows the 10.1.2 rule table: tare reply order, pour curve, flow, timer, read replies, battery notify, overload, no-data gap, Drop and the 2 failed attempts, BluetoothOff, ConnectFail and PowerOff.

The `:core:ble` `src/debug` package `com.timemoredot.ble.demo` has `DemoScale.client`/`clientForTest`, `DemoScanner`, `DemoEnvironment`, `DemoTransportFactory`, `DemoGattTransport` and `DemoDeviceStore` ("scale_demo"). They use the real `BleScaleClient` internal constructor. `src/main` was not changed, so no signatures changed. The `debugImplementation(testFixtures(project(":core:protocol")))` line was added.

Tests: `DemoFramesTest` 2, `DemoScaleDeviceTest` 13 and `DemoScaleClientTest` 7, with 0 skipped and 0 failed. `DemoFramesTest` reads the expected bytes by id from the confirmed vectors in the vector file and does not copy them. `DemoScaleClientTest` is in `src/testDebug` because `DemoScale` exists only in debug.

Isolation grep on `src/main` returned 0 lines.

Not checked: release build and APK exclusion, which are DEMO-2 scope. In this AGP setup `:core:ble:test` runs only `testDebugUnitTest`.

Files:
- /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo/DemoFrames.kt
- /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo/DemoScaleDevice.kt
- /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/ble/src/debug/kotlin/com/timemoredot/ble/demo/DemoScale.kt
- /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_ble-engineer_DEMO-1_impl.md

## M9 — skipped → milestones.json `todo`

- rounds None, missingReviewers None, 스크린샷 0장

### 완료 조건


### 남은 지적(차단) (0)


### 불확실 (0)


### low 지적 (0)


### 구현자 거부 (0)


### 구현 요약


