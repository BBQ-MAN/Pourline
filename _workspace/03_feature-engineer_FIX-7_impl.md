# FIX-7 구현 요약 (메인 직접 구현, 2026-11-03)

- 근거: 프로브 B F-B4-1(`04_device_b_B4.log` +342.4 s), O-B8-1(`B8_end.png`), architecture 7.2(2026-11-03), run_meta P14·P15.
- 바뀐 파일: `core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt`
  - `BrewConfig.autoStartConfirmNanos = 500 ms`, `weightDropGrams = 2.0`.
  - `onReading`: 직전 정상 무게보다 2 g 넘게 줄면(`dropped`) 유량 창 비움. WaitingForPour(무장됨)에서 넘는 샘플을 `pendingStart`(수신 ns, 무게, 처리 뒤 유량)에 쌓고, 2개 이상·t₀부터 500 ms 이상이면 `autoStart(t₀)`. 임계값 이하·급감·과부하면 `resetAutoStart()`.
  - `autoStart`: `run(t₀, AutoStart, sendStart, fresh)` 뒤 앞 샘플들을 `emitSample`(offset = 수신 − t₀). 지금 샘플은 기존 Running 경로가 낸다.
  - `onConnection(false)`·`toIdle`에서 확인 구간 취소. TODO(device) C-0B-2 주석을 실측 표기로.
- 테스트: 새 `AutoStartConfirmTest`(6개: B4 실측 들어 올림, B8 실측 붓기 시작·소급, 임계값 아래 취소, 급감 취소, 끊김 취소, 서버 들어 올림 유량). 기존 `BrewEngineTest`·`AutoStartArmingTest`·`BrewSessionControllerTest`·`BrewViewModelTest`의 2샘플 자동 시작 시계열을 확인 구간 판으로 고침. `weightDropKeepsScaleFlowInAverage` → `sharpWeightDropClearsFlowWindowKeepingCurrentScaleFlow`(명세 변경).
- verify.sh 종료 코드 0(`_workspace/build/verify_20261103_080328.log`).
