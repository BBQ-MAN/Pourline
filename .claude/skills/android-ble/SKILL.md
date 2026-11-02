---
name: android-ble
description: "Android(Kotlin)에서 Timemore Dot 저울과 BLE로 통신하는 계층을 구현·수정하는 규칙: 권한(API 31 전후), 스캔, connectGatt, GATT 작업 직렬화 큐, 알림 구독(CCCD), Write without response, API 33 전후 콜백 차이, 재연결과 close(), 프레임 재조립, 미확정 프로토콜 항목 처리, 디버그 프레임 로그, 가짜 저울. BLE 연결 끊김, status 133, 알림 미수신, 스캔 결과 없음 같은 문제 해결에도 사용한다. 프로토콜 바이트 해석 자체는 scale-protocol을 쓴다."
---

# Android BLE — 저울 연결 계층 규칙

Android BLE API는 콜백 기반이고, 동시에 두 GATT 작업을 보내면 조용히 하나를 버리며, 연결 객체를 닫지 않으면 시스템 자원이 새어 몇 번 뒤 연결 자체가 안 된다. 아래 규칙은 이런 "테스트는 통과하고 실기기에서만 실패하는" 결함을 막기 위한 것이다.

## 1. 권한과 매니페스트

```xml
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
<!-- API 30 이하 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<!-- API 31 이상 -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- 런타임 요청은 API 수준으로 분기한다: 31 이상은 `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT`, 30 이하는 `ACCESS_FINE_LOCATION`.
- API 30 이하에서는 위치 서비스가 꺼져 있으면 스캔 결과가 오지 않는다. 오류 없이 빈 결과가 오므로 앱이 먼저 확인하고 안내한다.
- `neverForLocation`은 일부 광고(비컨 형식)를 걸러 낼 수 있다. 실기기 프로브에서 이 플래그로 Dot이 보이는지 확인한다. 안 보여 대체 경로로 바꿀 때는 네 가지를 함께 바꾼다: (1) `BLUETOOTH_SCAN`에서 `neverForLocation` 제거, (2) `ACCESS_FINE_LOCATION`의 `maxSdkVersion` 제거, (3) API 31 이상에서도 `ACCESS_FINE_LOCATION`을 런타임에 함께 요청, (4) API 31 이상에서도 위치 서비스 켜짐 확인. 하나만 바꾸면 권한은 있는데 스캔 결과가 없는 상태가 된다.
- 권한 거부, "다시 묻지 않음", 블루투스 꺼짐을 각각 다른 UI 상태로 내보낸다. 하나로 뭉치면 사용자가 무엇을 해야 할지 모른다.

## 2. 스캔

- 광고에는 서비스 UUID `0xFFF0`과 회사 ID `0xFFFF`의 Manufacturer Data(`[방송 유형][기기 유형 0x01]`)가 실린다(실기기 확정, `protocol.md` A-1~A-3). 전원을 다시 켠 뒤의 화이트리스트 방송(`01 01`)에는 이름이 없으므로 이름으로 거르지 않는다. `ScanFilter`(서비스 UUID `FFF0` 또는 Manufacturer `0xFFFF`)로 짧게(10초 이하) `SCAN_MODE_LOW_LATENCY` 스캔한다. `0xFFF0`·`0xFFFF`는 범용 값이라 다른 기기도 걸리므로, 코드에서 기기 유형 바이트를 보고 연결 후 `0x2A24`로 한 번 더 확인한다.
- 30초 안에 `startScan`을 5번 넘게 호출하면 시스템이 오류 없이 결과를 막는다. 재시도 간격을 두고, 화면 재구성마다 스캔을 다시 시작하지 않는다.
- 찾으면 즉시 `stopScan`한다. 스캔 중 연결은 일부 기기에서 실패율을 높인다.
- 마지막으로 연결한 기기 주소를 저장해 다음 실행에서 바로 연결을 시도한다.

## 3. 연결과 해제

- `device.connectGatt(context, /* autoConnect = */ false, callback, BluetoothDevice.TRANSPORT_LE)`. 첫 연결은 `autoConnect=false`가 빠르다. `TRANSPORT_LE`를 빼면 일부 기기가 클래식 연결을 시도해 실패한다.
- 연결 후 `discoverServices()` → (선택) `requestMtu(247)` → 알림 구독 → 초기 읽기(배터리, 단위, 타이머 상태) 순서로 진행하고, 각 단계를 작업 큐로 보낸다.
- `STATE_DISCONNECTED`나 오류 status(대표적으로 133)를 받으면 **항상** `gatt.close()`하고 객체를 버린다. 끊긴 `BluetoothGatt`를 다시 쓰지 않는다. 닫지 않은 클라이언트가 쌓이면 시스템 한도에 걸려 이후 연결이 모두 실패한다.
- 재연결: 지수 백오프(예: 1, 2, 4, 8초, 최대 30초)로 새 `connectGatt`. 사용자가 직접 끊었거나 블루투스가 꺼졌으면 재연결하지 않는다.
- 블루투스 상태 변화(`BluetoothAdapter.ACTION_STATE_CHANGED`)를 받아 꺼지면 연결 상태를 즉시 바꾸고 작업 큐를 비운다. `STATE_ON`을 받으면 사용자가 직접 끊은 경우가 아니라면 백오프를 초기화하고 저장된 주소로 새 `connectGatt`를 시작한다.
- 20바이트(기본 MTU 23 − 3)를 넘는 프레임을 보내기 전에는 `requestMtu`가 성공했는지 확인한다. 실패했으면 송신 분할이 실기기로 확인될 때까지 해당 명령을 막는다(예: 기기 이름은 12바이트 이하로 제한, 12 + 8 = 20).
- 본딩(`createBond`)은 프로토콜 프로브에서 필요하다고 확인될 때만 호출한다.

## 4. GATT 작업 큐

- 쓰기, 디스크립터 쓰기, 읽기, MTU 요청, 서비스 탐색은 한 번에 하나만 보낸다. 앞 작업의 콜백(또는 시간 제한, 예: 5초)이 와야 다음 작업을 보낸다. 큐 없이 연달아 보내면 두 번째 호출이 `false`/오류 코드를 돌려주거나 조용히 사라진다.
- 큐는 코루틴 `Channel` 또는 `Mutex` + `CompletableDeferred`로 구현하고, 연결이 끊기면 대기 중 작업을 모두 실패로 완료한다.
- 알림 구독: `setCharacteristicNotification(char, true)` 뒤 CCCD(`0x2902`)에 `ENABLE_NOTIFICATION_VALUE`를 쓴다. 둘 다 해야 알림이 온다.
- 하향 특성 `0xFFF2`는 Write without response다(`WRITE_TYPE_NO_RESPONSE`). Android는 이 경우에도 로컬 전송 완료로 `onCharacteristicWrite`를 부르므로 큐 진행에 쓴다. 저울의 응답(opcode `0x03` 프레임)은 별도로 `0xFFF1` 알림으로 오며, 명령별 시간 제한 안에 오지 않으면 실패로 처리한다.

## 5. API 33 전후 차이

| 동작 | API 33 이상 | API 32 이하 |
|------|-------------|-------------|
| 특성 쓰기 | `gatt.writeCharacteristic(char, value, writeType)` → `Int` 상태 코드 | `char.value = …; char.writeType = …; gatt.writeCharacteristic(char)` → `Boolean` |
| 디스크립터 쓰기 | `gatt.writeDescriptor(desc, value)` | `desc.value = …; gatt.writeDescriptor(desc)` |
| 알림 수신 | `onCharacteristicChanged(gatt, char, value: ByteArray)` | `onCharacteristicChanged(gatt, char)` + `char.value` |

- 두 알림 콜백을 모두 재정의한다. API 33 이상에서는 시스템이 새 오버로드를 부르고, 그 기본 구현(super)이 옛 오버로드를 다시 부른다. 그래서 새 오버로드에서는 `super`를 호출하지 않고, 옛 오버로드는 `if (Build.VERSION.SDK_INT >= 33) return`으로 API 32 이하에서만 처리한다. 둘 중 하나라도 빠뜨리면 같은 프레임이 두 번 처리된다.
- 옛 콜백에서는 `char.value`를 즉시 복사한다(`copyOf()`). 다음 알림이 같은 배열을 덮어쓴다.
- 쓰기 분기는 한 함수 안에 숨기고 나머지 코드는 API 수준을 모르게 한다.

## 6. 스레드와 흐름

- GATT 콜백은 바인더 스레드에서 온다. 콜백 안에서 파싱·UI 작업을 하지 말고 바이트를 복사해 `Channel`/`MutableSharedFlow`로 넘긴다. 콜백을 막으면 다음 알림이 늦어진다.
- 연결 객체는 앱 범위(싱글턴 저장소 + application 범위 `CoroutineScope`)가 소유한다. ViewModel이 소유하면 화면 회전마다 연결이 끊긴다.
- 무게 알림은 100 ms마다 온다. 외부로는 `Flow<ScaleReading>`처럼 디코딩된 값만 내보낸다. 시각은 수신 시점의 `SystemClock.elapsedRealtimeNanos()`를 붙인다.

## 7. 프레임 재조립

알림 경계와 프레임 경계가 같다고 가정하지 않는다. 기본 MTU(23, 데이터 20바이트)에서 24바이트 프레임은 쪼개진다.

1. 받은 바이트를 버퍼에 붙인다.
2. `A5 5A`를 찾아 그 앞 바이트를 버린다.
3. 6바이트가 모이면 길이 필드로 전체 길이(6 + len + 2)를 계산하고, 다 모일 때까지 기다린다.
4. CRC는 검사하지 않는다. 펌웨어 v1.0.4는 CRC 자리를 항상 `0000`으로 보내고, 앱이 보낸 CRC도 검사하지 않는다(실기기 확정, `protocol.md` F-8/F-9). 길이로 경계를 정해 프레임을 내보내고, 남은 바이트가 `A5 5A`로 시작하지 않으면 헤더 첫 바이트 하나만 버리고 2로 돌아간다(재동기화). 알림 하나에 프레임 2~3개가 정해진 순서 없이 붙어 온다.
5. 버퍼 크기에 상한(예: 512바이트)을 둔다. 길이 필드가 비정상적으로 크면 그 헤더를 버린다.

재조립기는 코덱 모듈(순수 Kotlin)에 두고 벡터의 분할·결합 사례로 테스트한다.

## 8. 미확정 프로토콜 항목 처리

`docs/spec/protocol.md`에서 `미확정`인 항목(CRC 변형, 무게 배율 등)은:

- 값 하나를 상수 한 곳에 두고 명세 ID를 주석으로 단다: `// TODO(device): protocol.md P-07 무게 배율 미확정`. 같은 가정을 여러 곳에 흩어 두면 확정 후 한 곳만 고쳐 불일치가 생긴다.
- 미확정 가정을 우회하는 코드를 릴리스 빌드에 넣지 않는다. 디버그 빌드에서는 판별 근거가 될 원시 프레임을 로그에 남긴다.
- 실기기로 확정된 `protocol.md` 내용이 이 스킬의 가정과 다르면 `protocol.md`가 우선한다. 이 스킬은 실기기 확인 전에 쓰였고, 명세는 로그를 근거로 갱신되기 때문이다(예: CRC 미사용 확정으로 수신 CRC 검사를 없앴다).

## 9. 디버그 프레임 로그

디버그 빌드에서 송수신 프레임을 모두 남긴다. 형식은 `device-verification` 스킬과 `crc_probe.py`가 그대로 읽는 형식이다.

```
Log.d("TimemoreBle", "RX FFF1 A55A0101000900...")   // 수신 알림 원본(재조립 전)
Log.d("TimemoreBle", "TX FFF2 A55A030D0000....")    // 송신
Log.d("TimemoreBle", "EV <연결 상태·오류 코드>")
```

릴리스 빌드에서는 남기지 않는다(`BuildConfig.DEBUG` 분기).

## 10. 테스트

- 코덱·재조립기: JVM 단위 테스트, `protocol-vectors.json`을 읽어 모든 벡터를 돈다.
- 연결 상태 기계와 작업 큐: `BluetoothGatt`를 직접 쓰지 말고 얇은 전송 인터페이스 뒤에 두어, 가짜 전송으로 "쓰기 콜백 누락 → 시간 제한", "작업 중 끊김 → 대기 작업 실패", "status 133 → close 후 재연결", "블루투스 끔 → 켬 → 재연결"을 테스트한다.
- 가짜 저울(`FakeScale…`): 상위 계층용. 스크립트된 무게 시퀀스, 연결 끊김, 명령 실패 응답을 낼 수 있게 한다.

## 11. 하지 않는 것

- 백그라운드 연결 유지(포그라운드 서비스)는 요구사항에 없으면 만들지 않는다. 필요해지면 targetSdk 34 이상 기준으로 `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_CONNECTED_DEVICE` 권한, `<service android:foregroundServiceType="connectedDevice">`가 필요하고, `startForeground` 전에 `BLUETOOTH_CONNECT`가 허용됐는지 확인한다(아니면 `SecurityException`). Play Console에 서비스 유형도 신고한다.
- 외부 BLE 라이브러리는 이 규칙을 지키기 어려운 이유가 생길 때만 검토한다. 도입하면 `architecture.md`에 이유를 적는다.

## 12. Android 공식 스킬(android-skills 플러그인)

| 주제 | 공식 스킬 | 언제 |
|------|-----------|------|
| 권한·매니페스트 | `android-permissions-security` | 1절 권한 선언·런타임 요청, `exported` 컴포넌트를 다룰 때 |
| 메인 스레드 차단 | `anr-audit-and-fix` | 6절 GATT 콜백·흐름에서 메인 스레드로 넘기는 작업을 정할 때 |
| 테스트 구성 | `testing-setup` | 10절 테스트를 처음 구성할 때 |

불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다.

공식 스킬은 Android 일반 규칙이다. BLE 연결·GATT 큐·프레임 처리는 이 스킬이 우선한다. 이 스킬에는 실기기(펌웨어 v1.0.4)로 확인한 저울 동작이 반영되어 있기 때문이다.
