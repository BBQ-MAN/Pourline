# DEMO-1 구현 요약 — 데모 저울(바이트 수준 흉내 + 가짜 transport)

## 결과
- `verify.sh :core:protocol:test :core:ble:test` 종료 코드 0 (`_workspace/build/verify_20261102_022554.log`).
- 기본 `verify.sh`(test lintDebug assembleDebug)도 종료 코드 0 (`_workspace/build/verify_20261102_022608.log`).
- 새 테스트: DemoFramesTest 2건, DemoScaleDeviceTest 13건(`:core:protocol` test), DemoScaleClientTest 7건(`:core:ble` testDebugUnitTest). 건너뜀 0, 실패 0.

## 소스
- `core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo/DemoFrames.kt`: 10.1.2 `DemoFrames`. CRC 자리 00 00.
- `core/protocol/src/testFixtures/kotlin/com/timemoredot/protocol/demo/DemoScaleDevice.kt`: `DemoScenario`(6개, `byAddress`), `DemoLinkEvent`, `DemoPoll`, `DemoScaleDevice`. 10.1.2 흉내 규칙 표를 그대로 따른다. 영점 응답은 +150 ms 뒤 첫 알림의 weight 뒤에 붙고, 그 뒤 두 알림까지 영점 전 값을 낸다. 첫 0 g 프레임 3 s 뒤 붓기를 시작한다(V60 계열은 6.4 식, 하리오는 0~10 s 선형 240 g). 유량은 낸 raw 10프레임 전과 비교해 0..999로 자른다. 타이머는 Start/Pause/Reset으로 바뀌고, 리셋 상태에서 Start하면 `unknown04(0)`이 붙는다. 30 s마다 배터리 Notify가 weight 앞에 붙는다. Overload는 15~20 s 동안 raw를 고정한다. Reconnect는 20~23.5 s 동안 알림이 없고 40 s에 `Drop(8)`을 낸 뒤 2번 실패한다. BluetoothOff는 20 s 뒤 `BluetoothOff(8000)`을 내고, ConnectFail은 연결이 늘 실패한다. PowerOff 응답 뒤 다음 poll에서 `Drop(0x13)`을 낸다. 헤더·길이가 틀리거나 모르는 cmd에는 응답하지 않는다.
- `core/ble/src/debug/kotlin/com/timemoredot/ble/demo/DemoScale.kt`: `DemoScale.client`/`clientForTest`, `DemoScanner`, `DemoEnvironment`, `DemoTransportFactory`, `DemoGattTransport`, `DemoDeviceStore`("scale_demo" prefs. prefs가 null이면 메모리. 이름 규칙은 `SharedPrefsDeviceStore`와 같다). 실제 `BleScaleClient` internal 생성자를 쓰고 `debugFrameLog = true`다.
- `core/ble/build.gradle.kts`: `debugImplementation(testFixtures(project(":core:protocol")))` 한 줄만 더했다.

## 테스트
- `core/protocol/src/test/kotlin/com/timemoredot/protocol/demo/DemoFramesTest.kt`: 바이트를 벡터 파일에서 id로 읽는다. 대상 벡터는 rx-weight-positive-002, rx-tare-writeack-001, rx-two-frames-003, rx-unit-read-001, rx-firmware-read-001, rx-name-read-003, rx-unknown-cmd-002이고, 추가로 rx-multi-frames-003, rx-battery-read-002, rx-timer-read-002를 본다. 모두 confirmed다. 벡터를 길이 필드로 프레임 단위로 잘라 포함 여부를 확인한다. 1바이트씩 `feed`했을 때 프레임 하나가 나오고 `pendingByteCount == 0`인지도 확인한다.
- `DemoScaleDeviceTest.kt`: 완료 조건의 영점, 붓기, 유량, 타이머, 읽기, 무응답, 배터리, 시나리오 항목에 PowerOff를 더해 확인한다.
- `core/ble/src/testDebug/kotlin/com/timemoredot/ble/demo/DemoScaleClientTest.kt`: 스캔 6개, 연결·DIS·status, 영점→타이머→붓기, 재연결 R1→R2→R3→Connected(Running 유지), ConnectFail, BluetoothOff→8 s→R1→Connected, 과부하를 확인한다. `DemoScale`이 debug에만 있어서 `src/test`가 아니라 `src/testDebug`에 둔다.

## 격리 확인
- `grep -rnE 'protocol\.demo|ble\.demo|DemoScale|DemoScenario' core/*/src/main app/src/main` → 0줄.
- 데모 클래스는 `core/protocol/src/testFixtures/.../protocol/demo`와 `core/ble/src/debug/.../ble/demo`에만 있다.
- `core/ble/build.gradle.kts`에서 testFixtures를 받는 줄은 `debugImplementation`과 원래 있던 `testImplementation` 둘뿐이다.
- `src/main`은 고치지 않았다. `BleScaleClient` 생성자 둘과 `GattTransport`·`BleEnvironment`·`BleScanner`·`DeviceStore` 시그니처는 그대로다.

## 참고
- 이 환경의 AGP에서는 `:core:ble:test`가 `testDebugUnitTest`만 실행한다(release 단위 테스트 태스크 없음).
- 연결이 끊긴 동안 쌓인 응답은 지우지 않고 다음 연결의 첫 알림에 붙인다. 이전 연결의 쓰기에 대한 응답이라서 새 연결의 같은 키 요청에 맞을 수 있지만, 응답 내용이 같으므로 영향은 없다.
- 명세와 다른 점은 없다.
