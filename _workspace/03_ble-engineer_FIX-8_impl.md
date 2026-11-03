# FIX-8 구현 요약 (메인 직접 구현, 2026-11-03)

- 근거: 프로브 B F-F5-1(`04_device_b_F5_B1_B10.log` 133 7회), C-MC-1(`04_device_b_B4.log` +192~204 s), protocol 3.13·A-5, architecture 5.3·5.4, run_meta P16·P17.
- 바뀐 파일:
  - `core/protocol/.../Advertising.kt`: `BROADCAST_WHITELIST`, `broadcastTypeOf(adv)`. A-1 `TODO(device)` 주석을 확정 표기로.
  - `core/protocol/.../ScaleClient.kt`: `ScanCandidate.broadcastType: Int? = null`, `FailureReason.GattError(status, whitelistAdvertising = false)`.
  - `core/ble/.../BleScaleClient.kt`: `onScanHit`이 broadcastType 보존. attempt 0 실패 `toFailure(address)` → `gattError()`: 133이고 scanResults의 그 주소 broadcastType == 01이면 true. `PendingResponse.sentAtNanos`(쓰기 직전 `clock.nowNanos()`), `onFrame` 짝짓기 조건에 `atNanos >= sentAtNanos`, 짝 없는 응답(`responseKey() != null`)은 `EV 짝 없는 응답` 로그.
- 테스트: `BleScanTest.firstConnect133FlagsWhitelistAdvertisingOnly`, 기존 `scanResultsKeptUntilConnected…` 기대값(가짜 광고 01 01 → whitelist true), `BleCommandTest.foreignResponseReceivedBeforeWriteIsNotPaired`·`unsolicitedResponseIsLoggedAndReadValueStillApplies`. `Fakes.notifyAt` 추가.
- 남는 한계: 쓰기 뒤 응답 전에 다른 앱의 같은 키 응답이 오면 구별 불가(protocol 3.13).
- verify.sh 종료 코드 0.
