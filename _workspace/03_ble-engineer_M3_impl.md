# M3 구현 요약 — 저울 도메인 계약·상태 환원기·가짜 저울

## 결과
- `verify.sh :core:protocol:test` 종료 코드 0 (`_workspace/build/verify_20261029_103152.log`).
- 테스트 112건, 건너뜀 0, 실패 0: ProtocolVectorsTest 75, CrcRuleMatchesVectorsTest 3, FrameReassemblerTest 7, FrameCodecTest 5, ScaleStateReducerTest 13(신규), AdvertisingTest 4(신규), FakeScaleClientTest 5(신규). unconfirmed 벡터 실패 기록 없음.
- grep 완료 조건 확인:
  - `TODO(device)` in `core/protocol/src/main`: `ProtocolConfig.kt` ounceMode 줄, `Advertising.kt` isScaleCandidate 줄 두 개뿐.
  - `"TES017"` in `core/*/src/main`: `Advertising.kt` EXPECTED_MODEL 한 줄.
  - `fff0`(대소문자 무시) in `core/*/src/main`: `Advertising.kt` SCALE_SERVICE_UUID 한 줄.

## 소스 (`core/protocol/src/main/kotlin/com/timemoredot/protocol/`)
- `ScaleStateReducer.kt`: `MonotonicClock`, `ScaleReading`, `ScaleStatus`(+`Unknown`), `ScaleStateReducer`(5.3). raw → g 변환은 `ProtocolConfig.gramMode/ounceMode` × (oz면 `GRAMS_PER_OUNCE`). 과부하면 weightGrams null. UnitReport 00/01 밖이면 unit=null·unitReadFailed=true. Battery percent는 0..100으로 자름. 미상 stateRaw는 무시(protocol.md 3.2). WriteAck·DeviceName·FirmwareVersion·Unknown은 상태 불변.
- `Advertising.kt`: `SCALE_SERVICE_UUID`, `SCALE_MANUFACTURER_ID`, `DEVICE_TYPE_DOT`, `Advertisement`, `isScaleCandidate`, `EXPECTED_MODEL`.
- `ScaleClient.kt`: `ScanCandidate`, `ScaleDevice`(`modelMismatch`), `FailureReason`, `ConnectionState`, `CommandFailure`, `CommandResult`, `ScaleClient`(timerEvents 포함). 시그니처는 5.4 그대로.

## testFixtures (`core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/FakeScaleClient.kt`)
- `java-test-fixtures`는 M1부터 `core/protocol/build.gradle.kts`에 적용되어 있었다(변경 없음).
- `FakeScaleClient`: `sent`·`calls`(복사본), `setConnection`·`setStatus`·`setScanResults`·`setLastDevice`, `emit`, `emitTimerNotify`(status.scaleTimer 변경 + timerEvents), `respondWith`(기본 Success). `send()` Success면 환원기와 같은 규칙으로 status 갱신, timerEvents로 내지 않음. `connectLastDevice()`는 저장 주소 유무를 반환. 연결 상태 전이는 하지 않는다(테스트가 `setConnection`으로 정한다).
- `readings(startNanos, vararg weightsGrams, flowGramsPerSec, intervalMs)`: g, 10 Hz 기본.

## 명세와 다른 점 / 보고
- 공개 API 추가 1개: `fun ScaleStatus.afterSuccess(command: ScaleCommand): ScaleStatus`(`ScaleStateReducer.kt`). Write 성공 반영 규칙(SetUnit → unit·unitReadFailed=false, SetTimer → scaleTimer)을 환원기와 `FakeScaleClient`가 한 곳에서 같이 쓰려고 뒀다(5.5 "실제 클라이언트와 같은 규칙"). testFixtures에서 internal 접근을 기대지 않으려고 public으로 했다.
- `onUnitReadFailed()`는 단위를 이미 알면(unit != null) 아무것도 하지 않는다. 5.3 주석은 "unitReadFailed = true"지만 필드 정의가 "아직 단위를 모르고"이므로, ReadUnit 시간 초과 전에 0x06 Notify로 단위를 알게 된 경우 UnitUnknownTimedOut이 잘못 뜨지 않게 했다. 단위 미상 상태에서는 명세대로 true가 된다.
- `SetUnit` 성공은 unitReadFailed도 false로 둔다(단위를 알게 되었으므로).
- architecture.md 계약 변경 요청 없음.
