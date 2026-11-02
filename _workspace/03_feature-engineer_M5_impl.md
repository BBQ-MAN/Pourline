# M5 구현 요약 — 추출 엔진·레시피 모델·샘플 버퍼

## 파일
- `core/brew/src/main/kotlin/com/timemoredot/brew/Recipe.kt` — `RecipeStep`, `Recipe`(`finishSec`, `plannedGramsAt`, `plannedSecFor`)
- `core/brew/src/main/kotlin/com/timemoredot/brew/Samples.kt` — `FLAG_*`, `BrewSample`, `SampleBuffer`(BLOB v1 코덱), `gaps()`, `reachTimesMs()`
- `core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt` — `BrewSettings`, `BrewConfig`, `BrewPhase`, `StaleReason`, `TimerSnapshot`, `StepGuide`, `BrewState`, `BrewInput`, `BrewSummary`, `BrewEffect`, `PhaseCause`, `BrewEngine`
- 테스트: `core/brew/src/test/kotlin/com/timemoredot/brew/{Rig.kt, BrewEngineTest.kt, RecipeAndSamplesTest.kt}` (43건)
- 빈 `Package.kt` 삭제

## 검증
- `verify.sh :core:brew:test` → PASS, 종료 코드 0 (`_workspace/build/verify_20261029_111903.log`)
- `grep -rnE 'import android\.|System\.nanoTime|System\.currentTimeMillis|SystemClock' core/brew/src/main` → 0줄
- `grep -rn cupLift core/brew/src` → 0줄
- `Rig`가 입력마다 공통 불변 조건을 확인한다: `flowGramsPerSec >= 0`, phase가 바뀌면 `PhaseChanged`가 정확히 하나(from/to 일치, 효과 목록 끝), phase가 그대로면 `PhaseChanged` 없음. 모든 엔진 테스트가 `Rig`를 쓴다.

## 명세와 다른 점
1. **`BrewConfig.maxElapsedNanos` 기본값 = `3_599_900_000_000`** (architecture.md 7.2와 M5 완료 조건은 `3_599_900_000`).
   `3_599_900_000` ns는 3.6 s다. 그러면 추출이 3.6 s에 끝난다. 같은 명세의 "59:59.9", "3_599.9 s", `Completed(durationMs 3_599_900)`(= 3_599_900_000_000 ns / 1e6)과도 맞지 않는다. 단위 오기로 보고 의도한 값(3599.9 s)으로 구현했다. 테스트는 `durationMs == 3_599_900`, `elapsedNanos == 3_599_900_000_000`을 확인한다. architecture.md 7.2와 milestones.json M5 해당 문구를 고쳐야 한다(설계자 몫).
2. 명세가 정하지 않은 세부 결정:
   - `BrewSample.flowGramsPerSec`에는 그 시각의 평활 유량(`state.flowGramsPerSec`, 화면 값)을 넣는다. 끊김 샘플은 유량 0.
   - 유량 창은 수신 시각 기준 `now − at < flowWindowNanos`(10 Hz면 샘플 10개). `Tick`에서도 창을 갱신하므로 알림이 1 s 넘게 끊기면 유량이 0이 된다.
   - 자동 완료 안정 창은 수신 시각이 아니라 **타이머 경과** 축으로 잰다(일시정지가 창에 끼어도 맞다). 창 시작 전 샘플 하나를 경계값으로 남기고, 그 샘플이 `경과 − 5 s` 이전에 있을 때만 판정한다. 그래서 가장 이른 자동 완료가 끝 시각 + 5 s다.
   - WaitingForPour에서 자동 시작을 일으킨 `Reading`은 offset 0 샘플로 더한다. 효과 순서: `ClearSamples`, `SendTimer(Start)`, `AppendSample`, `PhaseChanged`.
   - `FLAG_DISCONNECTED` 샘플은 연결 상태에서 끊김으로 바뀔 때 한 번만 낸다(`ConnectionChanged(false)`가 중복으로 와도 하나).
   - `Reading`은 연결 상태로 본다(`ConnectionChanged(true)` 없이 `Reading`만 와도 NoData 판정을 시작한다).
   - guide는 `Reading`·`Tick`·phase 전이 때만 다시 계산한다. 그래서 Running 중 `Tared`가 guide를 바꾸지 않는다.
   - `gaps()`의 플래그 구간은 앞뒤 정상 샘플 offset까지 넓히고, 겹치는 구간은 합친다. 간격이 정확히 `minGapMs`이면 공백이 아니다.
   - `fromBlob`은 빈 배열, 모르는 버전, `1 + 13n`이 아닌 길이를 `IllegalArgumentException`으로 거부한다. 용량은 샘플 수와 같다(읽기 전용).

## 남은 일 / 위험
- 기본 레시피 5개 데이터(`DefaultRecipes`)는 M6(:app) 범위다. 이번 테스트는 architecture.md 8.3 표 값을 테스트 안에서 만들어 썼다.
- BLE 계층 결함 발견 없음.
