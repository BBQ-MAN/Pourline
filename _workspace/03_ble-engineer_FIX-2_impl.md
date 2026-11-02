# FIX-2 구현 요약 — BLE 계층 보완

## 결과
- `verify.sh :core:protocol:test :core:ble:test` 종료 코드 0 (`_workspace/build/verify_20261029_223443.log`).
- 테스트 191건(두 모듈), 실패 0, 건너뜀 0. 새로 넣은 것: BleEnvironmentTest 6, GattTransportTest 4, AdvertisingTest 1.
- 변이 확인: 풀림 경로를 `attempt = 0`(첫 연결 규칙)으로 바꾸면 BleEnvironmentTest 3건과 BleReconnectTest 1건이 실패한다. 확인한 뒤 원복했다.

## 변경
- `core/protocol/.../Advertising.kt`: `MANUFACTURER_FILTER_DATA`(`[00, 01]`), `MANUFACTURER_FILTER_MASK`(`[00, FF]`)를 추가했다. 두 줄 모두 `// TODO(device): protocol.md A-1`이 붙어 있다.
- `core/ble/.../BleScanner.kt`: ScanFilter (b)는 이제 `MANUFACTURER_FILTER_DATA.copyOf()`·`MANUFACTURER_FILTER_MASK.copyOf()`만 쓴다. `byteArrayOf`는 0줄이다.
- `core/ble/.../BleScaleClient.kt` (5.4 환경 막힘과 풀림):
  - `userStopped` 대신 `connectIntent`를 둔다. 기본값은 false다. `connect()`·`connectLastDevice()`가 켜고 `disconnect()`·PowerOff 성공이 끈다. 막힘 상태로 바뀌어도 끄지 않는다.
  - 생성할 때와 `refreshEnvironment()`, 블루투스 켜짐 때 블루투스·권한·위치를 함께 확인한다. 우선순위는 `BluetoothOff` > `PermissionMissing` > `LocationOff`다. 이전에는 권한을 먼저 봤다.
  - 막힘이 풀리면 `onUnblocked()`를 탄다. 연결 의도와 저장 주소가 있으면 `startLinkLoop(attempt = 1, delay 0)`이 `Reconnecting(attempt=1, nextRetryAtNanos=풀린 시각)`을 내고 6절 백오프로 이어진다. 둘 중 하나가 없으면 `Idle`이다. `refreshEnvironment()`와 블루투스 켜짐이 같은 함수를 쓴다.
  - `connectLastDevice()`: 상태가 막힘이면 의도만 켜고 `refreshEnvironment()`를 부른 뒤 true를 돌려준다. 연결은 하지 않는다.
  - `onEvent`: `link !== l`이면 이벤트를 버린다. 현재 transport에서 온 이벤트만 처리한다.
- `core/ble/.../GattTransport.kt`: `close()` 주석을 6절 계약대로 고쳤다(events 채널을 닫음, 버린 transport의 이벤트는 반영하지 않음). `BleGattTransport.close()`는 이미 `channel.close()`를 부르고 있어 고치지 않았다(코드 대조: `BleGattTransport.kt` 143~152행).
- `send()`: 이미 `withContext(dispatcher)`(scope의 ContinuationInterceptor) 안에서 mutex·대기 키·환원기를 처리하고 있어 코드는 그대로 두고 테스트만 더했다.
- `core/ble/build.gradle.kts`: `testImplementation(libs.turbine)`을 추가했다. 카탈로그에 이미 있는 1.2.1이다.
- `Fakes.kt`: `FakeGattTransport.leakEventsAfterClose`를 추가했다(버린 transport가 늦게 낸 이벤트를 흉내 낸다).

## grep
- `grep -rn 'byteArrayOf' core/ble/src/main/kotlin/com/timemoredot/ble/BleScanner.kt`: 0줄.
- `grep -rn 'TODO(device)' core/protocol/src/main`: `ProtocolConfig.kt:13` ounceMode, `Advertising.kt:15` isScaleCandidate, `:20` MANUFACTURER_FILTER_DATA, `:21` MANUFACTURER_FILTER_MASK. 이 4줄뿐이다.

## 판단한 곳 / 알릴 것
1. 저장 주소가 없을 때 막힘 상태에서 `connectLastDevice()`는 true를 돌려준다. 5.4 문구("환경이 막혀 있으면 … true")를 그대로 따랐다. 풀린 뒤에는 `Idle`이다.
2. 위치 막힘: 5.4대로 생성할 때와 `refreshEnvironment()`에서 위치를 확인한다. 그래서 API 30 이하에서 위치 서비스가 꺼져 있으면 첫 상태가 `LocationOff`이고, `connectLastDevice()`는 위치가 켜질 때까지 연결하지 않는다. M4에서는 위치를 스캔할 때만 봤는데 그 동작이 바뀌었다. API 31 이상(neverForLocation)에서는 영향이 없다.
3. 풀림 때 연결 대상은 저장 주소(`last_address`)다. 막힌 상태에서 `connect(B)`를 불렀고 B가 아직 저장되지 않았다면, 풀린 뒤에는 저장 주소로 연결한다(명세 문구대로).
4. 버린 transport의 이벤트는 두 겹으로 막는다. runLink가 끝날 때 수집 코루틴을 취소하고, `onEvent`가 `link !== l`을 검사한다. 테스트로는 둘을 따로 떼어 검증하지 못한다.
5. architecture.md 계약 시그니처는 바꾸지 않았다. 명세 문제 보고는 없다.
