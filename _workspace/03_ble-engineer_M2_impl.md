# M2 구현 요약 — 프로토콜 코덱

## 결과
- `verify.sh :core:protocol:test` 종료 코드 0 (`_workspace/build/verify_20261029_102536.log`).
- 테스트 90건, 건너뜀 0, 실패 0: ProtocolVectorsTest 75(벡터 전체, 1벡터 = 1케이스), CrcRuleMatchesVectorsTest 3, FrameReassemblerTest 7, FrameCodecTest 5.
- unconfirmed tx 벡터 8건은 이름 접두어 `[unconfirmed]`로 실행, 8건 모두 통과(실패 시 stderr 기록만 하고 빌드는 막지 않는 구조).

## 소스 (`core/protocol/src/main/kotlin/com/timemoredot/protocol/`)
- `ProtocolConfig.kt`: `TX_CRC`, `RawScale`, `ProtocolConfig`(5.1 그대로), 프레임 형식·opcode·cmd 상수(internal). `TODO(device)`는 ounceMode 줄 하나(C-01-4, C-01-6).
- `WeightUnit.kt`: `WeightUnit`, `GRAMS_PER_OUNCE`.
- `ScaleFrame.kt`: `TimerAction`, `ScaleTimerState`, `Op`, `ScaleCommand`, `ScaleFrame`, `DecodeError`, `DecodeResult`, `EncodeResult`(Ok는 contentEquals), `ResponseKey`, `responseKey()` 두 개.
- `FrameCodec.kt`: `encode`(TX_CRC 항상 부착, SetName 16바이트 코드 포인트 경계 자르기, 빈 이름 InvalidName), internal `decode`(protocol.md 3절, 고정 길이 cmd만 LengthMismatch, 알 수 없는 조합은 Unknown, Write 응답 01 밖은 실패, 이름 UTF-8 대체 문자, 펌웨어 비ASCII 대체 문자).
- `FrameReassembler.kt`: protocol.md 2.3 규칙 1~9. CRC 2바이트는 읽고 버림. 테스트용 internal `pendingByteCount`.
- M1의 빈 `Package.kt` 삭제.

## 테스트
- 벡터는 `docs/spec/protocol-vectors.json`을 직접 읽는다(경로는 `build.gradle.kts`의 `systemProperty("protocol.vectors")`, `inputs.file`로 벡터 변경 시 재실행). 파싱은 `kotlinx-serialization-json` testImplementation(JsonElement만 사용, 플러그인 없음, architecture.md 10절).
- rx 벡터: hex 배열은 원소마다 `feed` 한 번, 문자열은 한 번. expect 배열과 종류·값·순서 비교, 끝나면 남은 바이트 0 단언.

## 명세와 다른 점 / 보고
- 없음. 공개 시그니처는 architecture.md 5.1·5.2와 같다. 추가한 것은 internal(`FrameCodec.decode`, `pendingByteCount`, 상수)뿐이다.
- 디버그 프레임 로그(`RX FFF1 …`/`TX FFF2 …`)는 `:core:ble` 범위라 M4에서 한다.
