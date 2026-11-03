# FIX-9 구현 요약 (메인 직접 구현, 2026-11-03)

- 근거: 프로브 B F-F5-1, F-B2-1, B2 관찰(deniedOnce 잔류), architecture 8.1·9.2, ui-design 5.1·8.3, run_meta P16·P18.
- 바뀐 파일:
  - `app/.../ui/connect/ConnectionUi.kt`: `ConnectFailure.PairingModeMaybe`, `GattError.whitelistAdvertising` 매핑.
  - `app/.../ui/connect/ConnectScreen.kt` `failureText`, `ConnectAnnouncements.kt`, `ui/brew/BrewText.kt`: 문구·낭독.
  - `app/.../PermissionMonitor.kt`: `PermissionDenial`(deniedOnceAfterResult, isPermanent(500 ms 즉시 반환), deniedOnceOnResume).
  - `app/.../MainActivity.kt`: 요청 직전 `SystemClock.elapsedRealtime()` 기록, 결과에서 PermissionDenial로 판정, onResume에서 허용이면 deniedOnce 지움.
- 테스트: `PermissionDenialTest`(5), `ConnectionUiMappingTest` 페어링 모드 안내 1, 스크린샷 장면 3개(light·dark 6장).
- 스크린샷(새로 기록, 기존 변화 없음 — verifyRoborazziDebug 통과, 리뷰 수정 라운드에서 글꼴 2.0 세 장 추가): screenshots/connect_failed_pairing_mode_light_font200.png, screenshots/brew_failed_pairing_mode_light_font200.png, screenshots/device_sheet_failed_pairing_mode_light_font200.png, screenshots/connect_failed_pairing_mode_light.png, _dark.png, screenshots/brew_failed_pairing_mode_light.png, _dark.png, screenshots/device_sheet_failed_pairing_mode_light.png, _dark.png
- verify.sh 종료 코드 0.
