# FIX-6 구현 보고(feature-engineer) — 가로 밸브 칸 한 줄(P1)·V60·6888 뜸 기다리기 단계(P2)

판단 근거: `_workspace/run_meta.json` P1·P2·P4(하네스 추천, 잠정). M8 done 전환(P3)은 오케스트레이터 몫이라 손대지 않았다.

## 검증
- `verify.sh`(test lintDebug assembleDebug): PASS, 종료 0, `_workspace/build/verify_20261102_015854.log`
- `verify.sh verifyRoborazziDebug`: PASS, 종료 0, `_workspace/build/verify_20261102_015921.log`
- DB: `AppDatabase` version = 1, `1.json` identityHash `6d9f61fa4f808273048039f7ba96e8a3` 그대로, `grep -rn 'Migration(' app/src/main` 0줄

## P1 바꾼 코드
- `app/src/main/java/com/timemoredot/app/ui/brew/WeightArea.kt`
  - `valveRowFits(stateWidthDp) = 20 + 2 + 폭 ≤ 56`(순수 함수). 상태 글자 폭은 `rememberTextMeasurer`로 `titleMedium` "닫힘"·"열림"을 잰 값 중 큰 것. 글꼴 배율 리터럴 비교 없음
  - 한 줄 조건 = 높이가 정해진 경우(`bounded`, 가로 왼쪽 칸) && 옆 칸 && `valveRowFits`. 세로 배치는 높이가 정해지지 않아 늘 세로 쌓기
  - 칸 내용 높이: 한 줄이면 8 + max(20, 상태 글자 줄) + 4 + 안내 2줄 + 8(글꼴 1.0 76 dp), 아니면 이전 값(104 dp). `onMinHeight`가 알리는 값과 칸 높이 `max(칸 내용, 숫자 줄 + 24)`가 이 값을 쓴다
  - `WeightGeo.valveRow` 추가, `ValveCell(row = …)`: 한 줄이면 `Row`(아이콘 20 dp + 2 dp + 상태 글자, 세로 가운데), 아니면 이전 세로 쌓기. 칸 폭 64 dp·여백·색·테두리·색 숨·`clearAndSetSemantics`(낭독)·`traversalIndex`·알림 경로는 그대로
  - 테스트 태그 `VALVE_ICON_TAG`(칸·띠 아이콘), `VALVE_STATE_TAG`(칸 상태 글자). 칸 노드가 `clearAndSetSemantics`라 낭독 트리에는 나오지 않는다(unmerged 트리에서만 보임)
- `app/src/main/java/com/timemoredot/app/ui/brew/BrewScreen.kt`: 가로 왼쪽 칸 스크롤 열 Box에 `LEFT_SCROLL_TAG`, 동작 줄 Box에 `ACTIONS_TAG`(테스트 측정용, 배치 변화 없음)

## P2 바꾼 코드
- `app/src/main/java/com/timemoredot/app/data/DefaultRecipes.kt`: V60 기본·6888 레시피 1단계 다음에 `RecipeStep("0:40까지 기다리기", 60.0, 40)`. 다른 레시피·스키마·시드 경로는 그대로

## 테스트
새로 쓴 것
- `ValveCellWideTest`(800 × 360, Robolectric NATIVE)
  - 첫 화면: 하리오 t 60 s Running에서 타이머 값 아래 끝 260.0, 유량 260.0, 동작 줄 위 272.0, 왼쪽 스크롤 열 보이는 끝 272.0 dp. 칸 없는 V60(brew_running_recipe_landscape 상태) 타이머 아래 끝 260.0 dp(차 0). 무게 sp 칸 없음 = 칸 있음(52 = 52)
  - 한 줄 모양: 아이콘 20 × 20 dp, 세로 가운데 차 0, 아이콘 오른쪽 → 글자 왼쪽 2.0 dp, 상태 글자 줄 24 dp, 칸 높이 = max(76, 숫자 줄 + 24) ± 1
  - 글꼴 2.0이면 아이콘 24 dp(한 줄 아님), 1.0으로 돌아오면 20 dp
  - `valveRowFits`: 기기 글꼴 명세 폭 32.3(1.0) → 한 줄, 37.1(1.15)·64.6(2.0) → 세로 쌓기, 경계 34 → 한 줄, 34.1 → 아님
- `ValveCellPortraitTest`(360 × 800, 글꼴 1.0): 아이콘 24 dp가 상태 글자 위(세로 쌓기 그대로)
- `DefaultRecipesTest.waitStepsAndPace`: isWaitStep(1)만 true(두 레시피), V60 finishAtSec null·finishSec 90, 세 인자 plannedSecFor 4개 값(1e-9)
- `DefaultRecipesTest.v60SyntheticBrewHasNoLagDuringWait`: BrewEngine + V60 기본 + Fx.w(10 Hz), 11~40 s 모든 샘플 |paceDeltaSec| ≤ 2.0, 59 g 이후 guide.index = 2
- `BrewScreenTest`「V60 기본·6888의 뜸 기다리기 단계는 카드 번호를 건너뛰고 표식이 없다(P2)」: V60 '1/3' → '3/3'('2/3' 없음), 1단계 동안 '다음: 0:40까지 기다리기', 표식 2개. 6888 '1/5' → '3/5' → '4/5' → '5/5', 표식 4개

이 변경 때문에 값이 바뀐 기존 단언(그 밖의 단언은 그대로)
- `DefaultRecipesTest.expected`: V60·6888 행에 `S("0:40까지 기다리기", 60, 40)` 추가(architecture 8.3 표). matchesSpecTable·finishAndValveSchedules·invariants 단언은 그대로
- `TraversalOrderTest`「세로 Running과 아래 배너」(106줄)·`TraversalOrderLandscapeTest`「가로 Running과 아래 배너」(128줄): "2/2 단계" → "3/3 단계". V60이 3단계가 되어 단계 카드 번호가 바뀜
- `BrewAnnouncerTest`「단계가 바뀌면 바로 단계 문구…」(29~32줄): step 1 → 2, "2단계, steps[1]" → "3단계, steps[2]". 엔진이 기다리기 단계(index 1)를 건너뛰어 다음 붓기 단계는 index 2다
- `BrewAnnouncerTest`「마지막 단계 도달은 한 번…」(37~40줄): step 1 → 2. V60 마지막 index가 2로 바뀜(문구 기대값은 그대로)
- `BrewScreenshotTest` 주석 한 줄(9.7 s는 3단계). 단언 아님
- `DefaultRecipeSeedTest`·core(M5·FIX-4) 테스트: 바꾸지 않고 통과

## 명세와 다른 점(테스트 환경)
1. Robolectric 글꼴은 52 sp 무게 숫자 줄이 60.5 dp(기기 글꼴 명세 56.3 dp)라 칸 높이가 84.5 dp(= 숫자 줄 + 24)다. 명세의 80.3 dp는 기기 글꼴 값이고 규칙(max(칸 내용, 숫자 줄 + 24))은 같다. 그래서 UI 테스트는 80.3 dp 상수 대신 같은 화면의 무게 노드 높이로 이 식을 단언한다. 칸 높이가 무게 영역 높이와 같아서 H = H₀이고, 타이머 아래 끝은 칸 없을 때와 같은 260 dp다
2. Robolectric 글꼴에서 글꼴 1.15의 "닫힘"은 34.0 dp라 20 + 2 + 34 = 56 ≤ 56이므로 한 줄이 된다(기기 글꼴 명세 37.1 dp → 세로 쌓기). 그래서 1.15 판정은 Compose 테스트 대신 순수 함수 테스트로 명세 폭(32.3·37.1·64.6)을 넣어 확인했다. 판정 코드는 잰 폭만 쓰므로 기기에서는 1.15부터 세로 쌓기다(실기기·에뮬레이터 확인은 하지 않음)
3. 가로 글꼴 2.0은 기존 낱말 폭 규칙(> 56 dp)으로 칸 대신 띠가 된다(P1 이전과 같음). "세로 쌓기"가 아니라 띠지만, 한 줄 모양이 아니고 아이콘이 24 dp라는 점은 같다. 테스트는 아이콘 24 dp만 단언한다

## 스크린샷
방법: 작업 전 `screenshots/` 전체를 `_workspace/build/fix6/screenshots_before`에 복사하고 shasum을 `before.sha`에 저장 → `compareRoborazziDebug`로 픽셀이 바뀐 47장 확인 → `recordRoborazziDebug` → 바뀌지 않은 136장은 이전 파일 바이트로 되돌림 → `verifyRoborazziDebug` PASS. 작업 뒤 shasum은 `after.sha`. 두 목록의 차이가 아래 47장과 정확히 같다.

다시 기록한 47장(P1 2장 + P2 45장)
- screenshots/brew_disconnected_running_dark.png
- screenshots/brew_disconnected_running_light.png
- screenshots/brew_finished_save_failed_dark.png
- screenshots/brew_finished_save_failed_light.png
- screenshots/brew_leave_confirm_dark.png
- screenshots/brew_leave_confirm_light.png
- screenshots/brew_leave_confirm_light_font200.png
- screenshots/brew_morph_frame_dark.png
- screenshots/brew_morph_frame_light.png
- screenshots/brew_nodata_dark.png
- screenshots/brew_nodata_light.png
- screenshots/brew_overload_dark.png
- screenshots/brew_overload_light.png
- screenshots/brew_paused_dark.png
- screenshots/brew_paused_light.png
- screenshots/brew_recipe_picker_dark.png
- screenshots/brew_recipe_picker_light.png
- screenshots/brew_reconnecting_running_recipe_dark.png
- screenshots/brew_reconnecting_running_recipe_light.png
- screenshots/brew_reset_confirm_dark.png
- screenshots/brew_reset_confirm_light.png
- screenshots/brew_running_banner_resize_frame_dark.png
- screenshots/brew_running_banner_resize_frame_light.png
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
- screenshots/brew_running_valve_landscape_dark.png
- screenshots/brew_running_valve_landscape_light.png
- screenshots/brew_running_waiting_end_dark.png
- screenshots/brew_running_waiting_end_light.png

P1 해당: brew_running_valve_landscape_light/dark(칸 한 줄, 타이머·유량 값이 동작 줄 위에 잘림 없이 보임). P2 해당: 나머지 45장(V60 Fx.v60 장면의 목표선이 (0:10, 60 g)~(0:40, 60 g) 평평, 단계 표식 2개, 단계 카드 'n/3 단계'. brew_reconnecting_running_recipe 단계 카드 '3/3 단계'. brew_recipe_picker는 V60·6888 단계 수 표시가 바뀜). 대화상자(brew_leave_confirm·brew_reset_confirm) 장면은 뒤 화면이 V60 Running이라 같이 바뀌었다.

다시 기록하지 않은 세로 밸브 스크린샷(작업 전후 shasum 같음)
```
97ee40303389a774fe7de98b0ec4a06fc8d3cd9e  screenshots/brew_idle_valve_dark.png
3a2db34a00c18ca5bf35b712bd507e6d51c8b35d  screenshots/brew_idle_valve_light.png
30a50088a0069bf14150fea1336205a3bc8f1d09  screenshots/brew_idle_valve_open_dark.png
3880fdd7b47e4dcf8c50eb5cccb29a9d3ec2bb20  screenshots/brew_idle_valve_open_light.png
05e61fa13d0a4244b55427d1c417c58bc3b6c145  screenshots/brew_reconnecting_running_valve_dark.png
2532eb8dea97715ba300912a90479400b34ae170  screenshots/brew_reconnecting_running_valve_light.png
ad436afbd13ab7b911ff62d644c42b23c9b9a046  screenshots/brew_running_valve_countdown_dark.png
c2c476d425e42349929eff028fb5a19efdd1e28c  screenshots/brew_running_valve_countdown_light.png
e992890be9b750cf6544f8df71e67ee60f150dfb  screenshots/brew_running_valve_cue_dark.png
2f56699d3d4c3153f084e32eb7265d5d9a22b7a7  screenshots/brew_running_valve_cue_light.png
50af1e03ae630896fc3ad31674d1a0d456d8fb3b  screenshots/brew_running_valve_dark.png
778f8c24d2e49bf33592d7ab58db7714bfc74d3b  screenshots/brew_running_valve_light.png
726b95987586f4df0860c8974d335925ce1a24c7  screenshots/brew_running_valve_light_font200.png
92a8000dc4b7946aa8c2f0ba461ef33aed07b256  screenshots/brew_running_valve_reduce_motion_dark.png
8d9190aad33a181cf7607283db1aba7574a15610  screenshots/brew_running_valve_reduce_motion_light.png
```
(위 값은 `_workspace/build/fix6/before.sha`와 `after.sha`에서 같다.)

## 남은 것·위험
- P1 한 줄 모양은 Robolectric 글꼴로만 확인했다. 기기 글꼴에서 1.15 → 세로 쌓기 전환은 명세 폭 기준 순수 함수 테스트로만 확인했다
- 개발 기기·에뮬레이터에 이미 DB가 있으면 옛 V60(2단계)·6888(4단계)이 남는다. 새 시드는 앱 데이터를 지워야 보인다(architecture 8.3)

## 수정 라운드: 지적 "V60 기다리기 구간 평평한 목표선이 실제 선에 가려짐"에 대한 반박(코드 안 고침)
- 가림은 ui-design 4.4 목표선 행의 규칙("목표선 위에 실제 중심선을 2.5 dp primary 실선으로 다시 그린다. 목표대로 따라간 구간에서 실제는 끊기지 않은 실선")을 그대로 따른 결과다. 같은 표의 단계 표식 행 "기다림은 평평한 목표선이 보인다"와 명세 안에서 서로 어긋난다. 어느 문장을 고칠지는 ui-designer가 정한다(지적의 fix 항목도 이렇게 적는다).
- 그림 고치기(fix (2))는 이 마일스톤의 P1 완료 조건을 깬다. 하리오 스위치 기본도 0:10~2:00 240 g 평평 구간이 같은 방식으로 가려진다(M8부터, brew_running_valve_light.png). 지나간 기다리기 구간에 점선을 보이게 하면 세로 brew_*_valve*.png(running·countdown·cue·reduce_motion·reconnecting)의 픽셀이 바뀐다. 그런데 완료 조건은 이 파일들의 shasum이 작업 전후 같아야 한다.
- 평평한 목표선은 머리 앞에서는 보인다(brew_running_step_change_frame, start_frame, brew_reconnecting_running_recipe 값 없는 구간). 머리가 지나간 뒤 겹쳐 보이는 것은 실제가 목표와 같았다는 뜻이다.
- verify.sh 종료 0(_workspace/build/verify_20261102_020636.log). 세로 밸브 스크린샷 shasum이 위 목록과 지금도 같다.

## 수정 라운드 2: 지적 "V60 기다리기 구간 평평한 목표선이 실제 선에 가려짐" 고침(검증자 confirmed)
- 고친 곳: app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt drawShapeArea. 중심선 다시 그리기(2.5 dp primary) 뒤에 기다리기 단계(Recipe.isWaitStep) 구간 목표 점선(outline 2 dp, 현재 단계면 2 + stepQ dp)을 한 번 더 그린다. 대시 위상은 기존 segDash[i]라 경로 전체 점선과 어긋나지 않는다. 그래서 기다림을 지킨 뒤에도 (0:10, 60 g)~(0:40, 60 g) 평평한 목표선이 점선 마디로 보인다(brew_running_recipe_light.png 확인).
- 명세와 다른 점: 밸브 레시피 단계(valveOpen != null, 하리오 스위치 기본·4:6 하이브리드)는 뺐다. 넣으면 세로 brew_*_valve*.png 픽셀이 바뀌어 FIX-6 P1 조건(작업 전후 shasum 같음)을 깬다. 코드에 shortcut 주석을 남겼다. 하리오 0:10~2:00 240 g 평평 구간은 지금도 실선만 보인다(M8부터). 하리오 픽셀 변경을 허용하는 마일스톤에서 valveOpen 조건을 지우면 된다. ui-design 4.4 목표선 행("목표선 위에 실제 중심선을 다시 그린다")에 "기다리기 단계 목표 점선은 그 위에 다시 그린다"는 예외 문장이 필요하다(ui-designer 몫).
- 다시 기록한 스크린샷 38장(픽셀 변경, 방법: 작업 전 사본 _workspace/build/fix6r/before, shasum before.sha → recordRoborazziDebug → rec.sha): brew_disconnected_running, brew_finished_save_failed, brew_morph_frame, brew_nodata, brew_overload, brew_paused, brew_reconnecting_running_recipe, brew_running_banner_resize_frame, brew_running_on_target, brew_running_peak, brew_running_recipe, brew_running_recipe_landscape, brew_running_reduce_motion, brew_running_snackbar, brew_running_spike, brew_running_start_frame, brew_running_step_change_frame, brew_running_waiting_end(각 _light·_dark), brew_running_recipe_landscape_light_font200, brew_running_ribbon_light_font200. start_frame·step_change_frame은 머리 앞 점선을 같은 위상으로 한 번 더 그려 가장자리 안티에일리어싱만 바뀌었다.
- 세로 밸브 스크린샷 15장 shasum은 위 목록과 지금도 같다(shasum -c 불일치 0).
- verify.sh 종료 0(_workspace/build/verify_20261102_021306.log), verifyRoborazziDebug PASS(_workspace/build/verify_20261102_021334.log).

## 재실행(2026-11-02, P6): 기다리기 구간 목표 점선을 모든 레시피에서 같게 그림
- 고친 곳: app/src/main/java/com/timemoredot/app/ui/brew/BrewChart.kt drawShapeArea. 기다리기 구간 덧그리기 조건에서 `|| r.steps[i].valveOpen != null`과 shortcut 주석을 지웠다. 이제 조건은 `Recipe.isWaitStep(i)`뿐이다(ui-design 4.4 P6). 굵기·색·대시 위상(segDash[i])·현재 구간 2 + stepQ dp는 그대로다.
- 이 덕분에 위 「명세와 다른 점(밸브 레시피 제외)」 항목은 없어졌다.
- 다시 기록한 스크린샷 16장(방법: 작업 전 사본 `_workspace/build/fix6p6/before`, shasum `before.sha` → `recordRoborazziDebug` → `rec.sha`; 두 목록의 차이가 정확히 아래 16장). 이유는 모두 P6이다(밸브 레시피 기다리기 구간의 평평한 목표 점선이 실제 실선 위에 보임). P6 밖의 차이는 없다. V60·6888·레시피 없는 장면, `brew_idle_valve*`(그래프 없음)은 바이트가 같다.
  - 하리오 스위치 기본: brew_running_valve(_light·_dark), brew_running_valve_countdown(_light·_dark), brew_running_valve_cue(_light·_dark), brew_running_valve_reduce_motion(_light·_dark), brew_reconnecting_running_valve(_light·_dark), brew_running_valve_landscape(_light·_dark), brew_running_valve_light_font200
  - 악마의 레시피(Fx.devil, t 30 s, 기다리기 구간 있음): brew_running_long_step(_light·_dark), brew_running_long_step_light_font200
- 앞 라운드의 "세로 밸브 픽셀 같음" 기준은 ui-design 8.6 P6에 따라 이번 기록 뒤 파일(`rec.sha`)로 바뀐다.
- 화면 확인: brew_running_valve_light.png에서 0:10~2:00 240 g 평평 구간이 머리 뒤에서도 점선으로 보인다. brew_running_valve_cue_light.png에서도 지나간 2단계 평평 구간이 점선으로 보인다.
- `history_detail_valve`(요약판)는 아직 화면이 없어 기록하지 않았다(screenshots에 history_* 없음).
- verify.sh 종료 0(_workspace/build/verify_20261102_023636.log), verifyRoborazziDebug PASS(_workspace/build/verify_20261102_023613.log). `grep -rn 'Migration(' app/src/main` 0줄.
