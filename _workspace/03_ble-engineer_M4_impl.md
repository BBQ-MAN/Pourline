# M4 구현 요약 — BLE 연결 계층

## 결과
- `verify.sh :core:ble:test` 종료 코드 0 (`_workspace/build/verify_20261029_105404.log`).
- 기본 `verify.sh`(test lintDebug assembleDebug)도 종료 코드 0 (`_workspace/build/verify_20261029_105410.log`). `:core:ble:lintDebug` 포함.
- `:core:ble` 테스트 65건, 건너뜀 0, 실패 0: BleConnectTest 17, BleCommandTest 15, BleReconnectTest 13, BleScanTest 11, GattOpSlotTest 4, FrameLogFormatTest 3, BlePermissionsTest 2. 가상 시간(`runTest`), Robolectric 없음. unconfirmed 벡터는 쓰지 않았다(쓴 벡터는 모두 confirmed: rx-unit-read-002, rx-battery-read-002, rx-timer-read-002, rx-tare-writeack-002/003/004, rx-multi-frames-002/003, rx-timer-notify-001, rx-poweroff-writeack-001).
- 변이 확인: GATT 작업 제한을 6 s로 바꾸거나 재연결 시도 중 상태를 바꾸면 해당 테스트 4건이 실패함을 확인하고 원복했다.

## 소스 (`core/ble/src/main/kotlin/com/timemoredot/ble/`)
- `GattTransport.kt`: 6절 `GattTransport`·`GattEvent`·`GattTransportFactory` 그대로. `GattOpKind`, `GattOpSlot`(대기 작업 한 칸: 종류가 같은 콜백만 완료, 종류가 다르거나 대기 없는 콜백은 EV 로그 후 버림, 취소·닫힘 뒤 늦은 콜백 무시, close 시 대기·뒤 작업 실패).
- `BleGattTransport.kt`: 실제 transport. 콜백은 복사 후 Channel(UNLIMITED)·`GattOpSlot`에만 넘김. `onCharacteristicChanged`·`onCharacteristicRead` 두 오버로드 모두 재정의, 옛 오버로드는 `SDK_INT >= 33`이면 반환하고 `c.value.copyOf()`. 쓰기 분기(특성·CCCD)는 `write()` 한 함수. `close()`는 AtomicBoolean으로 멱등, SecurityException은 삼킴. `BleGattTransport.factory(context, debugFrameLog)`를 AppContainer가 쓴다.
- `BleScanner.kt`: `scaleScanFilters()` = ScanFilter 두 개(서비스 UUID `SCALE_SERVICE_UUID`, manufacturer `SCALE_MANUFACTURER_ID` data `[00, 01]` mask `[00, FF]`), `setDeviceName` 없음, `SCAN_MODE_LOW_LATENCY`. 광고 이름은 `scanRecord.deviceName`만 쓴다.
- `BleEnvironment.kt`: 권한(`BlePermissions.required(SDK_INT)` + `checkSelfPermission`, 캐시 없음), 블루투스 켜짐, 위치 서비스(`needsLocationService`), 상태 수신기 `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`(STATE_TURNING_OFF·STATE_OFF = 꺼짐). `SharedPrefsDeviceStore`(`"scale"`, `last_address`, `last_name`, `apply()`).
- `BlePermissions.kt`: 6절 시그니처 그대로. `USE_NEVER_FOR_LOCATION = true // TODO(device)`.
- `GattSpec.kt`: FFF1·FFF2·DIS·CCCD UUID, `CCCD_ENABLE_NOTIFY // TODO(device): G-1b (Android)`, MTU 상수.
- `FrameLogFormat.kt`: `RX FFF1 <대문자 hex>`, `TX FFF2 <hex>`, `EV <설명>`, `EV ADV <addr> name=… mfr=… uuids=…`.
- `BleScaleClient.kt`: 6절 공개 생성자 그대로 + 테스트용 internal 생성자(환경·스캐너·저장소·로그 싱크 주입). 연결 순서·응답 대기·재연결·블루투스 상태·스캔.
- `core/ble/src/main/AndroidManifest.xml`: android-ble 1절 권한 5개 + `uses-feature bluetooth_le required=true`. receiver·service·provider 없음.
- `Package.kt` 삭제.

## 테스트 (`core/ble/src/test/kotlin/com/timemoredot/ble/`)
- `Fakes.kt`: `FakeGattTransport`(실제와 같은 `GattOpSlot` 사용, 호출 기록, `held` 종류는 콜백 보류, `callback()`, `connected()`/`drop()`/`notify()`, `closeCalls`/`releases`, 기본 응답 = 실측 결합 벡터), `FakeEnvironment`, `FakeScanner`, `FakeStore`, `Harness`, `BleVectors`(벡터 JSON을 id로 읽음. 시스템 속성은 `core/ble/build.gradle.kts`가 넘김).
- 완료 조건 대응: 연결 순서·DIS·modelMismatch·MTU 23·G-7 무시·ServiceNotFound·ConnectTimeout·133 선행 이벤트·작업 큐·close 멱등·SecurityException(BleConnectTest), Tare·SetTimer·ReadUnit·타이머 Read/Notify·취소(BleCommandTest), 재연결·백오프·소진·connectLastDevice·disconnect·PowerOff·connect(B)·Connected 중 startScan·블루투스 상태(BleReconnectTest), 스캐너·이름 규칙·6 s 지연·NoDeviceFound·scanResults 유지/비움(BleScanTest).

## grep·코드 대조 결과
- `grep -rni '5833ff01' core/*/src/main app/src/main`: 0줄. `grep -rn 'awaitWriteAck' core app`: 0줄.
- `BLUETOOTH_SCAN|ACCESS_FINE_LOCATION`(--include='*.kt' core/ble/src/main app/src/main): `BlePermissions.kt` 10~12행뿐.
- `fff0`(core/*/src/main): `Advertising.kt` 한 줄 그대로(M3 조건 유지). `TES017`: `Advertising.kt` 한 줄 그대로.
- `:app:processDebugMainManifest` 병합 결과(`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`): 권한 5개(BLUETOOTH·BLUETOOTH_ADMIN·ACCESS_FINE_LOCATION maxSdkVersion=30, BLUETOOTH_SCAN neverForLocation, BLUETOOTH_CONNECT), `uses-feature android.hardware.bluetooth_le required=true`, `com.timemoredot.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 있음.
- 코드 대조: `BleScanner.kt:27-33` ScanFilter, `BleEnvironment.kt:51` registerReceiver, `BleGattTransport.kt:70-79` 알림 오버로드.

## 명세와 다른 점 / 판단한 곳
1. **SecurityException 위치**: 6절은 "BleGattTransport·스캐너 안에서 잡아 PermissionMissing"이다. 구현은 transport·스캐너가 던지고 `BleScaleClient`가 한 곳(`Link.gatt`, `runLink`, `startScan`)에서 잡아 `PermissionMissing`으로 바꾼다. transport 인터페이스가 Boolean/null만 돌려줘 안에서 잡으면 권한 문제와 GATT 실패를 구별할 수 없기 때문이다. `close()`·`stopScan`만 transport/스캐너 안에서 삼킨다. 크래시 없음은 같다.
2. **작업 큐 위치**: `Mutex + withTimeout(5_000)`은 `BleScaleClient`의 연결별 `Link.gatt`에, 종류 맞추기는 transport의 `GattOpSlot`에 둔다. 시간 초과를 클라이언트가 직접 알아야 첫 연결 중이면 `Failed(ConnectTimeout)`, 연결 뒤면 재연결로 정확히 나눌 수 있다. 시간 초과 뒤 클라이언트가 `close()`하므로 transport는 다음 작업을 보내지 않고 늦은 콜백을 버린다. `GattTransport` 인터페이스는 바꾸지 않았다.
3. **의존성 추가**: `androidx.core:core` 1.18.0(카탈로그 `androidxCore`). 이미 `activity-compose` 1.13.0이 끌어오는 판과 같게 맞췄고 웹으로 다시 확인하지 않았다. architecture.md 2.2 표에 행이 없다 → architect가 표에 더할 것.
4. **로컬 쓰기 실패(끊김 아님)**: 명세에 없음. `CommandFailure.NotConnected`로 돌려준다(끊김이면 `Disconnected`).
5. **블루투스 켜짐 자동 연결**: `Reconnecting(attempt=1, nextRetryAtNanos=지금)`으로 바로 시도한다(1 s 대기 없음). 실패하면 2 s 백오프부터 이어 간다.
6. **refreshEnvironment**: 권한·블루투스 막힘 상태에서 풀리면 `Idle`로 두고, 사용자 끊기가 아니고 저장 주소가 있으면 `connect(저장 주소)`(첫 연결 규칙)를 부른다. `LocationOff`는 스캔할 때만 판단한다(연결에는 위치 서비스가 필요 없다).
7. **재연결 시도 실패 사유**: ServiceNotFound·GATT 작업 실패도 다른 실패와 같이 다음 시도로 넘긴다(6절 "시도가 실패·시간 초과되면").
8. **EV ADV**: 스캔마다 후보 주소별 처음 한 번(전원 재투입 뒤 mfr 01/02 변화를 B1에서 보려고 startScan 때 기록을 비운다).
9. `connectGatt(context, false, cb, TRANSPORT_LE)`가 compileSdk 37에서 deprecated다. android-ble 3절 경로를 유지하고 `@Suppress("DEPRECATION")`만 달았다.
10. architecture.md 계약 시그니처 변경 없음. 명세 문제 보고 없음.

## 실기기 미확인(awaiting-device)
- B1(권한→스캔→연결 10 s, 본딩 없이 FFF1 알림, `EV ADV` mfr 0101/0201), B1 화이트리스트 방송 연결(A-2, A-3), B3(블루투스 끄기·켜기), B6(전원 버튼 끊김 → 재연결, 마지막 `RX FFF1`부터 끊김 `EV`까지 시간 측정 → C-0B-2 Android), B10(10분 RX 99%). 단위 테스트로 대신하지 않았다. 디버그 로그는 `EV 연결 상태 connected=false status=<n>` 줄로 끊김 시각을 남긴다.
- `TODO(device)`: `GattSpec.CCCD_ENABLE_NOTIFY`(G-1b Android), `BlePermissions.USE_NEVER_FOR_LOCATION`.
