# Timemore Dot 저울 앱 아키텍처

마지막 갱신: 2026-11-02(P2 기본 레시피, 10.1 디버그 데모 저울) · 입력: `docs/spec/protocol.md`(원문 v1.0.3 + 실기기 프로브 A, TES017 펌웨어 v1.0.4, 2026-10-29판), `docs/spec/protocol-vectors.json`(75건, 실기기 31건), `docs/spec/ui-design.md`(2026-10-29 B + A판, 15절 맞춤 요청 반영), MVP 요구사항, Android 공식 스킬(android-skills 1.0.18) 점검.

화면 배치·문구·토큰·TalkBack 문구는 `ui-design.md`가 정한다. 이 문서는 모듈, 계층 계약(Kotlin 시그니처), 단위, 화면 목록·상태 모델·ViewModel 계약을 정한다. 상태 이름은 `ui-design.md` 10절과 맞췄고 남은 차이는 9.8절에 적는다.

규칙 기준: `compose-app-dev` 스킬(골격·모듈·상태·추출 엔진·저장), `android-ble` 스킬(권한·연결·큐·재연결). 기본에서 벗어난 곳은 "기본과 다름"으로 이유를 적는다.

## 0. 핵심 결정 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 모듈 | `:core:protocol`(JVM), `:core:brew`(JVM), `:core:ble`(Android 라이브러리), `:app` | 코덱·엔진을 Android 없이 수 초 안에 테스트한다. 기능 모듈은 나누지 않는다 |
| 저울 계약 위치 | `ScaleClient` 인터페이스와 도메인 타입은 `:core:protocol`에 둔다 | `:core:brew`와 `:app` 테스트가 Android BLE 없이 계약과 가짜 저울을 쓴다 |
| 연결 소유 | `BleScaleClient` 하나를 `AppContainer`(Application 범위)가 소유하고 앱 범위 `CoroutineScope`에서 돈다 | ViewModel이 소유하면 회전·화면 이동마다 연결이 끊긴다 |
| 추출 세션 소유 | `BrewSessionController` 하나를 `AppContainer`가 소유한다. 엔진 입력 수집·샘플 버퍼·임시 저장을 맡는다 | 다른 탭으로 가거나 홈에 갔다 와도(B9) 샘플 수집과 타이머가 계속된다 |
| 타이머 출처 | 경과 시간은 앱 단조 시계(`elapsedRealtimeNanos`)가 정한다. 시작·일시정지·리셋 **상태**만 앱 → 저울로 보낸다(0x02 Write). 저울 상태는 Write 응답 성공만으로 확정한다(v1.0.4는 Write 뒤 0x02 Notify를 보내지 않는다, C-02-3). 저울 → 앱 방향(0x02 Notify, `timerEvents` → `ScaleTimerChanged`)은 원문 §5.2.1 근거로 경로만 남긴다. TES017 v1.0.4는 버튼이 전원 하나뿐이라 이 경로가 발생하지 않는다(C-02-2). 저울 쪽 실패는 앱 타이머를 멈추지 않는다 | 저울 타이머는 1 s 해상도(C-01-8)라 소수 초를 못 준다. 상태를 보내야 저울 화면과 앱이 같다(B5) |
| 유량 출처 | 저울 유량(0x01 flowRaw, g 모드 0.1 g/s, 값 = clamp(w(t) − w(t−1 s), 0, 999), C-01-5·C-01-7)에 앱이 1 s 이동 평균과 음수 0(방어)만 더한다. 무게가 줄어도 앱이 유량을 0으로 만들지 않는다 | 저울 유량이 확정 항목이다. 저울은 감소 중에도 1 s 창 안의 증가분으로 0보다 큰 값을 줄 수 있다(C-01-7). 무게 미분이나 "컵 들면 0" 규칙을 따로 만들면 코드만 늘고 저울 값과 어긋난다. 단, 샘플 사이 2 g 넘는 급감이면 평균 창만 비운다(7.2 유량 급감 규칙, O-B8-1, 2026-11-03) |
| 내부 단위 | 무게 g(`Double`), 유량 g/s, 시간 단조 ns(`Long`). raw → g 변환은 `:core:protocol`의 `ScaleStateReducer` 한 곳 | 계층 경계의 10배·1000배 오류를 막는다 |
| 표시 단위 | 저울의 현재 단위(연결 직후 0x06 Read)를 따른다. oz는 표시할 때만 변환. 앱 안 g/oz 전환은 없고, 0x06 쓰기(`SetUnit`)는 MVP에서 보내지 않는다. 0x06 Notify 경로는 원문 근거로 남긴다 | 사용자 결정(2026-10-28). v1.0.4는 버튼으로 단위를 바꿀 수 없어(C-06-3) 연결 중 단위가 바뀌지 않는다. oz 배율(C-01-4·C-01-6)은 실기기로 확인할 방법이 없어 단위 테스트로만 둔다 |
| 과부하 중 무게 | 마지막 정상 값을 멈춘 값으로 표시한다(`Stale(Overload)`, 9.3) | 사용자 결정(2026-10-28). 과부하 중 raw 값은 쓰지 않는다(C-01-11) |
| 기본 레시피 | 사용자가 지정한 5개(V60 기본, 하리오 스위치 기본, 6888, 테츠 카스야 기본·악마의 레시피)를 DB를 처음 만들 때 넣는다(8.3) | 사용자 결정(2026-10-28). 첫 실행에 S7이 비어 있지 않다 |
| DI | Hilt 대신 수동 DI(`AppContainer`) — **기본과 다름** | 싱글턴 7개, ViewModel 7개 규모다. Hilt는 플러그인·KSP 프로세서·AGP 9 내장 Kotlin 호환 확인이 더 든다 |
| 샘플 저장 | 세션당 BLOB 1행(비압축 고정 폭 레코드) | 샘플은 조회 조건 없이 세션 단위로만 읽고 쓴다. 행 단위면 3분 추출에 1,800행 삽입이 든다 |
| 재연결 상한 | 10회(약 3분 30초) 뒤 `Failed(ConnectFailed)` | 저울이 꺼진 채로 무한 재시도하면 배터리를 쓴다. 사용자는 "다시 시도"로 이어 간다 |
| 백그라운드 | 포그라운드 서비스 없음 | 요구사항에 없다(`android-ble` 11절). 프로세스가 살아 있는 동안만 연결을 유지한다 |
| 외부 BLE 라이브러리 | 쓰지 않음 | `android-ble` 규칙을 직접 지키는 쪽이 작다 |
| CRC | 수신 CRC는 검사하지 않고 2바이트를 읽고 버린다. 송신 CRC 필드는 항상 `00 00`(`TX_CRC`). CRC 계산 코드는 두지 않는다 | v1.0.4는 CRC를 쓰지도 검사하지도 않는다(F-8, F-9). 원문 CRC-16/IBM은 실기기와 충돌(F-7) |
| 스캔 필터 | `ScanFilter` 두 개(OR): 서비스 UUID 0xFFF0, Manufacturer 회사 ID 0xFFFF + 기기 유형 `01`. 이름으로 거르지 않는다 | 전원을 다시 켠 뒤 화이트리스트 방송에는 이름이 없다(A-2, A-3) |
| 디버그 데모 저울 | debug 빌드에만 있는 데모 저울: 바이트 수준 저울 흉내(`:core:protocol` testFixtures)를 가짜 `GattTransport`로 **실제** `BleScaleClient`에 끼워 재조립기·디코더·환원기·명령 응답·재연결·엔진을 그대로 지나게 한다. 진입은 debug 매니페스트의 런처 별칭 `DemoLauncher`. release에는 소스셋·의존성 단위로 들어가지 않는다(10.1) | 블루투스 없는 macOS 에뮬레이터(AVD API 37)에서 전체 흐름과 오류 화면을 확인한다. 하네스 추천(잠정, 사용자 최종 검토 대기, 2026-11-02) |

## 1. 모듈과 의존 방향

```
:app ──▶ :core:ble ──▶ :core:protocol
  │                        ▲
  ├──▶ :core:brew ─────────┘
  └──▶ :core:protocol
```

| 모듈 | 플러그인 | 패키지 | 내용 | 금지 |
|------|----------|--------|------|------|
| `:core:protocol` | `kotlin.jvm`, `java-test-fixtures` | `com.timemoredot.protocol` | 프레임 인코더·디코더(송신 CRC `00 00`, 수신 CRC 검사 없음), 재조립기, `ProtocolConfig`, 광고 판별 규칙과 상수, `ScaleStateReducer`, `ScaleClient` 계약과 도메인 타입, `MonotonicClock`. testFixtures에 `FakeScaleClient` | Android 의존 |
| `:core:brew` | `kotlin.jvm`, `plugin.serialization` | `com.timemoredot.brew` | `BrewEngine`, `Recipe`(`@Serializable`), `SampleBuffer`과 BLOB 코덱, 구간·도달 시각 계산 | Android 의존, Room |
| `:core:ble` | `com.android.library` | `com.timemoredot.ble` | `BleScaleClient`, `GattTransport`(실제·가짜), GATT 작업 큐, 스캐너, 재연결, 블루투스 상태 수신기, 디버그 프레임 로그, BLE 권한 매니페스트 | Compose, Room |
| `:app` | `com.android.application`, `plugin.compose`, `plugin.serialization`, KSP, `androidx.room`, Roborazzi | `com.timemoredot.app` | `AppContainer`, `BrewSessionController`, `AppPrefs`, `PermissionMonitor`, Room, 저장소, CSV, ViewModel, 화면, 내비게이션 | `android.bluetooth.*` 직접 사용(`:core:ble`만 쓴다). 예외: UI 계층 한 파일이 `BluetoothAdapter.ACTION_REQUEST_ENABLE` 상수만 쓴다(9.1) |

- **기본과 다름(가짜 저울 위치):** `compose-app-dev` 2절은 가짜 저울을 `:core:ble`에 둔다. 이 문서는 `:core:protocol`의 testFixtures에 둔다. `FakeScaleClient`는 계약 타입만 쓰므로 순수 JVM으로 만들 수 있고, `:core:brew`·`:app` 테스트가 `testImplementation(testFixtures(project(":core:protocol")))` 한 줄로 쓴다. Android 라이브러리 testFixtures의 Kotlin 지원 설정을 피한다. `GattTransport`의 가짜(`FakeGattTransport`)는 `:core:ble`의 test 소스에 둔다.
- 데모 저울(10.1, debug 전용): `:core:protocol` testFixtures 패키지 `com.timemoredot.protocol.demo`(순수 JVM 저울 흉내), `:core:ble`의 `src/debug` 패키지 `com.timemoredot.ble.demo`(가짜 transport·스캐너·환경과 `DemoScale`), `:app`의 `src/debug`·`src/release` `VariantHooks`와 `src/debug/AndroidManifest.xml`의 `DemoLauncher` 별칭. `:core:ble`은 `debugImplementation(testFixtures(project(":core:protocol")))`로만 testFixtures를 받는다. 위 의존 방향 그림은 그대로다(새 모듈 없음).
- 패키지 접두어 `com.timemoredot`과 applicationId `com.timemoredot.app`은 임시다. 출시 전 사용자가 정한다. 바꾸면 namespace와 패키지만 바꾼다.

## 2. 빌드 설정과 버전

### 2.1 SDK·JDK

| 항목 | 값 | 근거 |
|------|----|------|
| compileSdk / targetSdk | 37 / 37 | 설치된 SDK android-37. AGP 9.4.0 최대 지원 API 37 |
| minSdk | 26 | `java.time` 사용. API 26~30은 위치 권한 분기(`android-ble` 1절)가 필요하다 |
| JDK | Android Studio 내장 JBR 25로 Gradle 실행. `compileOptions`·Kotlin `jvmTarget`은 17 | AGP 9.4.0 최소 JDK 17. toolchain 자동 내려받기를 피하려고 `jvmToolchain`은 쓰지 않는다 |
| Gradle | 래퍼 9.8.2(설치됨) | AGP 9.4.0 최소 Gradle 9.6.0 |
| build-tools | AGP 기본값을 쓴다. 설치된 36과 다르면 M1에서 SDK Manager로 받는다(사용자 승인) | — |

### 2.2 라이브러리 버전 (확인일 2026-10-28, navigation-suite·navigationevent 행은 2026-10-29, 출처는 각 공식 릴리스 페이지)

| 별칭 | 버전 | 출처 |
|------|------|------|
| AGP `com.android.application`/`library` | 9.4.0 | developer.android.com/build/releases/gradle-plugin (2026-10-26 갱신, 9.5·9.6은 preview) |
| Kotlin (`kotlin.jvm`, `plugin.compose`, `plugin.serialization`) | 2.4.21 | kotlinlang.org/docs/releases (2026-10-08) |
| KSP | 2.3.12 | github.com/google/ksp/releases (Latest. AGP 9 내장 Kotlin R 클래스 수정은 2.3.10부터) |
| Compose BOM | 2026.09.00 (ui 1.12.1, material3 1.4.0) | developer.android.com/develop/ui/compose/bom/bom-mapping |
| Room (+ Gradle 플러그인 `androidx.room`) | 2.8.5 | developer.android.com/jetpack/androidx/versions/stable-channel (2026-09-09) |
| Navigation Compose | 2.10.2 | 같은 페이지 (2026-09-23) |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | BOM 2026.09.00을 따른다(기대값 1.4.0, material3와 같은 그룹 판) | developer.android.com/jetpack/androidx/releases/compose-material3 (Stable 1.4.0). `NavigationSuiteScaffoldState.snapTo(NavigationSuiteScaffoldValue)`가 1.4.0 API에 있음을 androidx 저장소 `compose/material3/material3-adaptive-navigation-suite/api/1.4.0-beta03.txt`로 확인(2026-10-29). M1에서 `./gradlew :app:dependencies`로 해석 판을 확인하고, BOM이 이 아티팩트를 관리하지 않으면 카탈로그에 `1.4.0`을 적는다 |
| `androidx.navigationevent:navigationevent-compose` | 1.2.0 | developer.android.com/jetpack/androidx/releases/navigationevent (Stable, 2026-10-07). compileSdk 36 이상 필요(37 충족) |
| Lifecycle (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`) | 2.11.0 | 같은 페이지 (2026-06-17) |
| Activity Compose | 1.13.0 | 같은 페이지 (2026-03-11) |
| kotlinx-coroutines (core, android, test) | 1.11.0 | github.com/Kotlin/kotlinx.coroutines/releases (Latest) |
| kotlinx-serialization-json | 1.12.0 | github.com/Kotlin/kotlinx.serialization/releases (Kotlin 2.4.10 기반) |
| Turbine | 1.2.1 | github.com/cashapp/turbine/releases (Latest) |
| Robolectric | 4.17 | github.com/robolectric/robolectric/releases (SDK 37 지원) |
| Roborazzi (gradle 플러그인, `roborazzi-compose`, `roborazzi-junit-rule`) | 1.77.0 | github.com/takahirom/roborazzi/releases (Latest) |
| JUnit | 4.13.2 | 4.x 최종판. 이번에 웹으로 다시 확인하지 않았다. Robolectric 러너 때문에 JUnit 4를 쓴다 |
| `androidx.test:runner` (계측 테스트, DEMO-3) | 1.7.0 | developer.android.com/jetpack/androidx/releases/test (Stable, 2026-11-02 확인. 페이지 갱신 2026-10-29, 새 판은 1.8.0-alpha뿐) |
| `androidx.test.ext:junit` (DEMO-3) | 1.3.0 | 같은 페이지 (Stable 2025-07-30, 2026-11-02 확인) |
| `androidx.test.espresso:espresso-intents` (DEMO-3, CSV 선택기 결과 흉내) | 3.7.0 | 같은 페이지 espresso 그룹 Stable 3.7.0 (2025-07-30, 2026-11-02 확인). espresso-core를 따로 적지 않는다(전이 의존) |

- AGP 9는 Kotlin을 내장한다. 어떤 모듈에도 `org.jetbrains.kotlin.android`를 적용하지 않고, 카탈로그에도 `kotlin-android` 플러그인 항목을 두지 않는다. kapt를 쓰지 않는다(Room은 KSP). 루트 `build.gradle.kts`에 `kotlin.jvm` 2.4.21을 `apply false`로 선언해 KGP 2.4.21이 클래스패스에 오게 한다. M1에서 `./gradlew buildEnvironment`로 실제 KGP 버전을 확인한다. 다르면 공식 경로(`agp-9-upgrade`: 루트 `buildscript { dependencies { classpath(libs.kotlin.gradle.plugin) } }`)로 바꾸고 이 표를 함께 고친다.
- Android 모듈(`:app`, `:core:ble`)은 `kotlin { compilerOptions { jvmTarget } }`를 따로 쓰지 않는다. 내장 Kotlin은 `compileOptions.targetCompatibility`(17)를 따른다. JVM 모듈은 `java { sourceCompatibility/targetCompatibility = 17 }`와 `kotlin { compilerOptions { jvmTarget = JVM_17 } }`를 같이 쓴다.
- AGP 9 기본값(`agp-9-upgrade` 9.0 동작 변경)을 받아들인다. `gradle.properties`에 `android.builtInKotlin`·`android.newDsl`·`android.uniquePackageNames`·`android.enableAppCompileTimeRClass`·`android.disallowKotlinSourceSets`를 두지 않는다. 그래서 namespace는 모듈마다 달라야 한다(`com.timemoredot.app`, `com.timemoredot.ble`). 단위 테스트는 debug 빌드 유형에만 생긴다(`testDebugUnitTest`). `targetSdk`는 명시한다.
- `BuildConfig`는 AGP 기본값이 꺼짐이다. `:app`만 `buildFeatures { buildConfig = true }`로 켜서 `BuildConfig.DEBUG`를 `debugFrameLog`로 넘긴다(6절). `:core:ble`은 켜지 않는다.
- Compose 테스트 의존성 `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest`(debug)는 BOM 버전을 따른다.
- 아이콘은 ui-design.md 6.5절 목록만 벡터 리소스로 넣는다. `material-icons-extended`는 크기가 커서 쓰지 않는다.

## 3. 단위·시간·스레드 규칙

| 값 | 타입·단위 | 이름 규칙 |
|----|-----------|-----------|
| 무게 | `Double` g (샘플 저장은 `Float` g) | `weightGrams` |
| 유량 | `Double` g/s (샘플 저장은 `Float`) | `flowGramsPerSec` |
| 시각 | `Long` ns, `SystemClock.elapsedRealtimeNanos()` 기준 | `atNanos`, `...Nanos` |
| 세션 안 상대 시각 | `Int` ms, 앱 타이머 경과(일시정지 제외) = `timer.elapsedNanos(atNanos) / 1_000_000`. 그래프 x축·목표선(`targetAtSec`)·S10 단계 표·CSV `t_s`가 모두 이 축을 쓴다 | `offsetMs` |
| 벽시계 | `Long` epoch ms. 기록 시작 시각 표기에만 쓴다 | `startedAtEpochMs` |
| 저울 타이머 | `Int` s (raw 그대로, 진단용) | `scaleTimerSec` |
| 배터리 | `Int` % 0..100 (100 초과는 100) | `batteryPercent` |

- oz 변환: `1 oz = 28.349523125 g`(정확값). 상수 `GRAMS_PER_OUNCE`는 `:core:protocol`의 `WeightUnit.kt`에 둔다. 표시 자릿수는 ui-design.md 9절.
- 스레드: GATT 콜백(바인더 스레드)은 바이트를 `copyOf()`로 복사해 `Channel(UNLIMITED)`에 넣기만 한다. 디코딩·상태 갱신은 앱 범위 스코프에서 한다. 앱 범위 스코프는 `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e("TimemoreApp", "appScope", e) })`다. 처리기는 로그만 남기고 다시 던지지 않는다(잡히지 않은 예외로 프로세스가 죽지 않게 하는 마지막 방어선. 예상되는 실패는 호출부가 `runCatching`으로 잡아 상태로 바꾼다, 8.2). `BrewSessionController`와 `SampleBuffer`는 메인 스레드 전용이다(그래프 `Canvas`가 같은 스레드에서 읽으므로 잠금이 없다). Room·파일 쓰기만 `Dispatchers.IO`로 옮긴다. 10 Hz 디코딩은 메인에서 부담이 없다.
- `SampleBuffer`는 스레드 경계를 넘기지 않는다. 저장할 때는 메인에서 `toBlob()`으로 만든 `ByteArray`(불변 복사본)만 IO로 넘긴다(8.3 `SessionDraft.samplesBlob`). 읽어 온 버퍼(`fromBlob`)는 IO에서 만든 뒤 메인으로 한 번 넘기고 다시 쓰지 않는다.
- `ScaleClient`의 Flow는 어느 디스패처에서 수집해도 안전해야 한다. `send()`도 어느 디스패처에서 불러도 된다. 구현은 환원기(`onCommandSucceeded`·`onUnitReadFailed`)와 대기 키 갱신을 생성자 `scope`의 디스패처(메인)로 옮겨 수신 경로와 같은 스레드에서만 바꾼다(환원기에 잠금이 없다, 5.4).
- 메인 스레드 차단 금지(`anr-audit-and-fix`): `src/main`에 `runBlocking`과 `SharedPreferences.Editor.commit()`을 쓰지 않는다(저장은 `apply()`). Room 쿼리, CSV 쓰기(`ContentResolver.openOutputStream` 포함), BLOB 해석(`fromBlob`)은 `Dispatchers.IO`에서 한다. `BroadcastReceiver.onReceive`와 GATT 콜백은 값을 넘기기만 한다.

## 4. 미확정 프로토콜 항목 격리

남은 미확정 항목은 아래 "고칠 곳" 한 곳과 벡터 파일만 바꾸면 되게 한다. 실기기로 확인할 항목의 상수 줄에 `// TODO(device): protocol.md <ID>` 주석을 단다. 다른 파일은 이 값을 리터럴로 갖지 않는다.

### 4.1 아직 미확정인 항목

| 명세 ID | 내용 | 고칠 곳 | 기본값(후보) | 바뀌면 함께 할 일 |
|---------|------|---------|--------------|-------------------|
| C-01-4, C-01-6 | oz 모드 raw 배율 | `ProtocolConfig.kt`의 `ounceMode`(`RawScale`) | 무게 0.01 oz/raw, 유량 0.01 oz/s/raw | `ScaleStateReducerTest` 기대값. 표시 자릿수가 바뀌면 ui-design.md 9절. v1.0.4는 버튼으로 단위를 못 바꾸고(C-06-3) MVP는 0x06 쓰기를 하지 않아 실기기 확인 경로가 없다. 단위 테스트로만 둔다 |
| G-1b (Android) | CCCD `01 00`로 Android에서도 알림이 오는지 | `:core:ble` `GattSpec.kt`의 `CCCD_ENABLE_NOTIFY` | `01 00`(macOS 확정) | 없음. B1에서 확인 |
| G-6 (Android) | Android에서도 본딩 없이 되는지 | 코드 없음 | 본딩하지 않는다(macOS 확정) | B1에서 본딩이 필요하다고 나오면 새 마일스톤으로 `createBond` 경로와 `Failed(BondRequired)`를 더한다. 미리 만들지 않는다 |
| `android-ble` 1절 neverForLocation 프로브 | `BLUETOOTH_SCAN`의 `neverForLocation`으로 Dot이 스캔되는지 | `:core:ble` `BlePermissions.kt`의 `USE_NEVER_FOR_LOCATION`과 같은 모듈 `AndroidManifest.xml`(둘 다 `:core:ble`) | `true`(neverForLocation 사용) | `false`로 바꾸면 매니페스트의 `neverForLocation` 제거·`ACCESS_FINE_LOCATION` maxSdk 해제를 함께 고친다. 런타임 권한 목록과 위치 서비스 확인은 `BlePermissions.required()`·`needsLocationService()`가 이 상수를 따르므로 `:app`은 고치지 않는다 |
| F-11 | 나뉜 송신 허용 여부 | `BleScaleClient.send()`의 길이 검사 | MTU−3을 넘는 프레임은 `PayloadTooLarge`로 거부. 보낼 때 MTU 증가를 다시 요청하지 않고 나눠 쓰지도 않는다 | MVP 화면은 이름 쓰기를 쓰지 않는다. 필요해지면 마일스톤을 더한다. protocol.md 2.4도 이 행과 같다(나눠 쓰기는 앱 규칙이 아님, 9.8) |
| F-10a, C-01-13, C-W-2, C-W-1a, A-3a, A-4 | 앱 규칙(실기기로 바뀌지 않음) | `ProtocolConfig.maxPayloadLength`(64), `ScaleStateReducer`(0x06 응답 전 0x01 버림), `ProtocolConfig.responseTimeoutMs`(1000, 실측 응답 128~304 ms), 디코더(`00`/`01` 밖 Write 응답 = 실패), 5.4 표시 이름 규칙, `EXPECTED_MODEL` | — | 앱 규칙이라 `TODO(device)`를 달지 않는다 |
| C-01-12 | "active" 정의 | `:core:brew` `BrewConfig.noDataTimeoutNanos` | 3 s 동안 0x01이 없으면 `NoData`(2026-10-29 2 s → 3 s). 실측 주기 100 ms, 빈 저울에서도 계속 전송, 최대 공백 181~243 ms. 전원 버튼 끊김 감지 2.08~2.25 s(macOS, C-0B-2)보다 길게 잡았다 | 없음. Android 감지 시간은 아래 C-0B-2 (Android) 행. 프로브 B(B10) 판정은 알림 줄 수가 아니라 0x01 프레임 수로 한다: logcat `RX FFF1` 줄을 `crc_probe.py`(또는 `FrameReassembler`와 같은 재조립 규칙의 파서)로 재조립해 얻은 cmd `0x01` 프레임 수 ÷ (측정 구간 s × 10) ≥ 0.99. 측정 구간 = 알림 구독 뒤 첫 `RX FFF1` 줄부터 끊김 `EV` 줄 또는 600 s 중 이른 쪽까지. 알림 하나에 프레임이 2~4개 붙고 무게 없는 알림도 있어(F-10) 줄 수는 0x01 수가 아니다 |
| C-0B-2 (Android) — 2026-11-03 B6에서 2.00 s(status 8) 측정, 규칙(2.5 s < 3 s)에 걸리지 않아 할 일 없음. 아래는 원래 규칙 | 전원 버튼으로 끈 뒤 Android가 끊김을 감지하는 시간. macOS 실측 2.08~2.25 s는 저울이 링크를 정상 종료했는지 central의 supervision timeout인지 로그로 구별되지 않는다 | `:core:brew` `BrewConfig.noDataTimeoutNanos` | 3 s(macOS 감지 시간 기준) | 프로브 B(B6) logcat에서 마지막 `RX FFF1`부터 끊김 `EV`까지 시간을 잰다(M4 B6, 측정값 기록만이 판정). 규칙: **측정 최댓값 + 0.5 s ≥ `noDataTimeoutNanos`**이면 오케스트레이터가 FIX 마일스톤(`feature-engineer`)을 새로 만든다. 범위는 `BrewConfig.noDataTimeoutNanos` 값, M5 NoData 테스트 값, ui-design 0절·12절 NoData 문구(ui-designer 요청)다. 새 값이 사용자가 받아들이기 어려울 만큼 길면(추출 중 NoData가 늦게 뜸) 그 FIX 마일스톤을 `needs-decision`으로 둔다. M8 B6은 그 FIX 마일스톤이 생기면 그것에 의존한다. 규칙에 걸리지 않으면 할 일 없음 |

### 4.2 프로브 A(v1.0.4)로 확정되어 격리를 푼 항목

| 명세 ID | 확정 내용 | 설계 반영 |
|---------|-----------|-----------|
| F-6~F-9 | 저울 송신 CRC 항상 `0000`, 저울은 수신 CRC를 검사하지 않음. 앱은 수신 CRC를 검사하지 않고 송신 CRC는 `00 00` | `CrcSpec`·CRC 계산·`DecodeError.CrcMismatch`를 없앴다. `ProtocolConfig.kt`의 `TX_CRC = 0x0000` 한 곳(5.1). 다른 펌웨어 판은 미확정이다. CRC를 쓰는 판이 나오면 analyst가 protocol.md 2.2·벡터를 다시 쓰고 FIX 마일스톤을 더한다 |
| F-10 | 알림 하나에 프레임 2~4개 결합, 순서는 고정 아님(배터리가 앞일 수 있음). MTU 247에서 분할 없음(분할은 미확정이라 재조립기는 그대로) | 재조립기 완료 조건에 실측 결합 벡터(M2) |
| C-04-1 | 원문에 없는 Notify cmd `0x04`, length 5 | `ScaleFrame.Unknown`으로 디코딩하고 버린다. 연결 유지(5.2, 6) |
| C-01-3, C-01-5, C-01-7 | g 모드 무게 0.1 g/raw, 유량 0.1 g/s/raw = clamp(Δw 1 s, 0, 999) | `gramMode` 상수에서 `TODO(device)` 제거. 엔진의 "컵 들면 유량 0" 규칙 삭제(7.2) |
| G-1, G-1a, G-5, C-W-1, C-W-2 | 모든 응답이 FFF1 Notify, Write 응답 opcode 03 length 1, 응답 128~304 ms | `awaitWriteAck` 설정과 로컬 쓰기 완료 성공 경로를 없앴다. Read·Write 모두 응답 프레임을 기다린다(6절) |
| G-7 | 원문에 없는 서비스 `5833ff01-9b8b-5191-6142-22a4536ef123` | 쓰지 않는다. 구독·읽기·쓰기 없음, 저울 판별 근거로도 쓰지 않는다(6절) |
| A-1~A-3 | 회사 ID 0xFFFF, `[방송 유형][기기 유형]`. 화이트리스트 방송에는 이름 없음 | `isScaleCandidate()`는 FFF0 또는 0xFFFF + 기기 유형 `01`. 이름 조건 삭제. `BleScanner`에 `ScanFilter` 두 개(6절) |
| C-02-2, C-02-3, C-06-3 | 버튼은 전원 하나뿐. 0x02·0x06 Notify 0건(앱 쓰기 뒤에도 없음) | 저울 → 앱 타이머·단위 경로는 원문 근거로 남기되 v1.0.4에서 발생하지 않는다. 이 경로의 완료 조건은 단위 테스트로만 둔다. 메아리 거르기(이전 8.2)를 없앴다 |
| C-0D-2 (protocol.md 3.7) | 0x0D 영점 성공 응답 뒤에도 약 300 ms(알림 2개) 동안 영점 전 무게가 온다. 응답과 같은 알림의 무게도 영점 전 값이다. 실측 두 번 모두 같다: `_workspace/04_device_probeA_mac.log:2724-2729`(응답 + 213.4 g, 이어서 213.4 g 두 번, 0은 응답 뒤 약 300 ms의 log:2729부터, 벡터 `rx-tare-writeack-004`), log:13753-13756(145.0 g = weightRaw 1450 같은 모양, 벡터 `rx-tare-preweight-seq-001`: 알림 4개 = 1450 + 영점 응답, 1450, 1450, 0) | 7.2 WaitingForPour 무장 규칙: `Tared` 뒤 영점이 반영된 `Reading`(시각이 `Tared` 뒤이고 \|무게\| ≤ 임계값)을 한 번 본 뒤부터 자동 시작 연속 카운트를 센다 |
| C-0B-2 (macOS) | 전원 버튼으로 끄면 Notify 없이 약 2.1~2.2 s 뒤 끊김(macOS bleak 측정). Android 감지 시간은 4.1 미확정 | 일반 끊김과 구별할 수 없어 재연결 경로를 탄다(6절) |
| A-1 (Android API), C-0B-2a (프로브 B, 2026-11-03) | `getManufacturerSpecificData(0xFFFF)` = 2바이트 `01 01`(Android 16·17). Android 끊김 감지 2.00 s(status 8) | `Advertising.kt`·`BrewConfig`의 `TODO(device)` 제거. 값 변경 없음 |
| A-5 (관찰, 프로브 B Fold5) | 화이트리스트 방송(`01`) 저울은 처음 보는 휴대폰의 연결을 GATT 133으로 거절하고, 페어링 모드(전원을 끈 뒤 버튼 길게)로 켜면 연결된다 | `ScanCandidate.broadcastType` 보존, `FailureReason.GattError.whitelistAdvertising`, `ConnectFailure.PairingModeMaybe` 안내(5.4·9.2, FIX-9) |
| C-MC-1 (protocol 3.13) | 저울은 두 central 동시 연결을 받고 다른 앱 요청의 응답도 FFF1로 보낸다 | 요청-응답 짝짓기에 "쓰기 직전 시각 이후 수신" 조건, 짝 없는 응답은 로그만(5.4·6절, FIX-8) |
| D-1 | Dot에는 숫자 화면이 없다 | 저울 타이머는 사용자에게 보이지 않는다. 앱 타이머가 유일한 표시 타이머라 자동 시작 확인 구간 동안 저울 타이머가 늦게 시작해도 보이는 차이가 없다(7.2) |

- 수신 CRC를 검사하지 않는 것은 우회가 아니라 확정 규칙이다(F-8, protocol.md 2.3). `android-ble` 7·8절도 2026-10-29에 정정됐다(13절).
- 미확정 항목에 기대는 완료 조건은 `milestones.json`에서 `[실기기 …]`로 시작한다. QA는 이 조건을 `unverified`로 판정하고, 해당 마일스톤은 `awaiting-device`가 된다. 의존 마일스톤은 계속 진행할 수 있다.
- v1.0.4에서 일어나지 않는 원문 경로(0x02·0x06 Notify)에 기대는 조건은 `[실기기 …]`로 쓰지 않고 단위 테스트로만 둔다.

## 5. `:core:protocol` 계약

아래 시그니처는 구현자가 그대로 쓴다. 본문·비공개 도우미는 구현자 재량이다.

### 5.1 설정

```kotlin
// ProtocolConfig.kt — 프로토콜 상수와 미확정 값은 이 파일에만 둔다
/** 송신 프레임 CRC 필드 값(2바이트, 상위 바이트 먼저). 수신 CRC는 검사하지 않고 버린다. protocol.md F-8, F-9(v1.0.4 확정). */
const val TX_CRC: Int = 0x0000

/** raw 1 단위가 해당 모드 단위(g 또는 oz)로 얼마인지. */
data class RawScale(val weightPerRaw: Double, val flowPerRawPerSec: Double)

data class ProtocolConfig(
    val gramMode: RawScale,        // protocol.md C-01-3, C-01-5 확정
    val ounceMode: RawScale,       // TODO(device): protocol.md C-01-4, C-01-6
    val maxPayloadLength: Int,     // protocol.md F-10a 앱 방어 규칙
    val responseTimeoutMs: Long,   // protocol.md C-W-2 앱 규칙(실측 128~304 ms). Read·Write 모두
) {
    companion object {
        val Default = ProtocolConfig(
            gramMode = RawScale(weightPerRaw = 0.1, flowPerRawPerSec = 0.1),
            ounceMode = RawScale(weightPerRaw = 0.01, flowPerRawPerSec = 0.01),
            maxPayloadLength = 64,
            responseTimeoutMs = 1_000,
        )
    }
}
```

- CRC 계산 함수, 다항식·초깃값 같은 CRC 파라미터, 수신 CRC 검사 분기를 어떤 소스에도 두지 않는다(F-8). 원문 CRC-16/IBM은 protocol-vectors.json `crc.originalStated`에만 남는다.

### 5.2 명령·프레임

```kotlin
enum class WeightUnit(val raw: Int) { Gram(0x00), Ounce(0x01) }       // C-06-1
const val GRAMS_PER_OUNCE = 28.349523125
enum class TimerAction(val raw: Int) { Start(0x01), Pause(0x02), Reset(0x03) }  // C-02-1
enum class ScaleTimerState { Running, Paused, Reset }                  // stateRaw 01/02/03
enum class Op { Notify, Read }

sealed interface ScaleCommand {
    data object ReadTimer : ScaleCommand
    data class SetTimer(val action: TimerAction) : ScaleCommand
    data object ReadBattery : ScaleCommand
    data object ReadUnit : ScaleCommand
    data class SetUnit(val unit: WeightUnit) : ScaleCommand
    data object PowerOff : ScaleCommand
    data object ReadName : ScaleCommand
    data class SetName(val name: String) : ScaleCommand
    data object Tare : ScaleCommand
    data object ReadFirmware : ScaleCommand
    data object FactoryReset : ScaleCommand
    data object ForgetDevice : ScaleCommand
}

/** 벡터 expect.type과 1:1. 벡터의 "Unit"은 kotlin.Unit과 겹쳐 UnitReport로 부른다. */
sealed interface ScaleFrame {
    data class WeightReport(val weightRaw: Int, val flowRaw: Int, val timerSec: Int, val overload: Boolean) : ScaleFrame
    data class TimerState(val op: Op, val stateRaw: Int) : ScaleFrame
    data class Battery(val op: Op, val bars: Int, val percent: Int) : ScaleFrame
    data class UnitReport(val op: Op, val unitRaw: Int) : ScaleFrame
    data class WriteAck(val cmd: Int, val success: Boolean) : ScaleFrame
    data class DeviceName(val name: String) : ScaleFrame
    data class FirmwareVersion(val version: String) : ScaleFrame
    data class Unknown(val opcode: Int, val cmd: Int, val payloadHex: String) : ScaleFrame
}

/** 수신 CRC를 검사하지 않으므로 CRC 오류는 없다(F-8). */
sealed interface DecodeError {
    data class LengthMismatch(val cmd: Int, val expectedLen: Int, val actualLen: Int) : DecodeError
}

sealed interface DecodeResult {
    data class Frame(val frame: ScaleFrame) : DecodeResult
    data class Error(val error: DecodeError) : DecodeResult
}

sealed interface EncodeResult {
    data class Ok(val bytes: ByteArray) : EncodeResult   // equals/hashCode는 contentEquals로 재정의
    data object InvalidName : EncodeResult               // 빈 이름 (C-0C-1)
}

/** 요청과 응답을 잇는 키. Notify(opcode 01) 프레임과 Unknown은 키가 없다. */
data class ResponseKey(val opcode: Int, val cmd: Int)
fun ScaleCommand.responseKey(): ResponseKey
fun ScaleFrame.responseKey(): ResponseKey?

object FrameCodec {
    /** 프레임 끝에 TX_CRC 2바이트를 항상 붙인다(F-9, F-12: CRC 없는 7바이트 프레임 금지). SetName은 16바이트 초과 시 코드 포인트 경계에서 자름 (C-0C-2) */
    fun encode(command: ScaleCommand): EncodeResult
}

/** protocol.md 2.3 규칙 1~9. 상태를 갖는다. 연결 하나에 하나. CRC 2바이트는 읽고 버린다(규칙 5). */
class FrameReassembler(config: ProtocolConfig = ProtocolConfig.Default) {
    fun feed(chunk: ByteArray): List<DecodeResult>       // 알림 하나 = 호출 하나. 결과 순서 = 알림 안의 프레임 순서
    fun reset()                                          // 연결 끊김 시 (규칙 9)
}
```

- 디코딩 규칙(payload 길이 검사, UTF-8·ASCII 대체 문자, 알 수 없는 상태 값)은 `protocol.md` 3절을 그대로 따른다.
- 알림 하나에 프레임이 여러 개 붙어 오고 순서는 정해져 있지 않다(F-10: 무게+배터리, 배터리+무게, 무게+Write 응답+0x04). 재조립기와 그 위 계층은 프레임 순서에 기대지 않는다.
- 원문에 없는 cmd(예: Notify `0x04` length 5, C-04-1)는 `ScaleFrame.Unknown`으로 내고 버린다. 상태를 바꾸지 않고 연결을 유지한다(규칙 8).

### 5.3 저울 도메인 타입과 상태 환원기

```kotlin
fun interface MonotonicClock { fun nowNanos(): Long }    // 앱: SystemClock::elapsedRealtimeNanos

data class ScaleReading(
    val weightGrams: Double?,          // g. 과부하면 null (C-01-11: 과부하 중 값은 쓰지 않음)
    val scaleFlowGramsPerSec: Double,  // g/s. 저울 계산값 clamp(w(t) − w(t−1 s), 0, 99.9 g/s) (C-01-5, C-01-7). 디코딩은 부호 있게 하고 음수도 그대로 둔다(실측 음수 없음)
    val scaleTimerSec: Int,            // 저울 타이머 raw 초 (진단용, 앱 타이머 아님)
    val overload: Boolean,
    val sourceUnit: WeightUnit,        // 변환에 쓴 저울 단위
    val atNanos: Long,                 // 알림 수신 시각 (elapsedRealtimeNanos)
)

data class ScaleStatus(
    val unit: WeightUnit?,             // null = 단위 미상 (C-01-13)
    val unitReadFailed: Boolean,       // 아직 단위를 모르고, 연결 직후 ReadUnit이 실패·시간 초과했거나 UnitReport의 unitRaw가 00/01 밖이었음 → UnitUnknownTimedOut
    val batteryPercent: Int?,          // 0..100, 100 초과는 100
    val scaleTimer: ScaleTimerState?,
) { companion object { val Unknown = ScaleStatus(null, false, null, null) } }

/** 프레임 → 상태·측정값. 순수 JVM, raw → g 변환의 유일한 장소. */
class ScaleStateReducer(config: ProtocolConfig = ProtocolConfig.Default) {
    val status: ScaleStatus
    /** WeightReport면 변환한 측정값, 단위 미상이면 null(버림, 디버그 로그만). 그 밖의 프레임은 status만 갱신하고 null. */
    fun onFrame(frame: ScaleFrame, atNanos: Long): ScaleReading?
    /** Write 성공 응답 뒤 호출. SetUnit이면 단위, SetTimer면 타이머 상태를 바꾼다 (protocol 3.2, 3.4). */
    fun onCommandSucceeded(command: ScaleCommand)
    fun onUnitReadFailed()             // unitReadFailed = true (다음 알려진 UnitReport(00/01)가 오면 false)
    // onFrame(UnitReport): unitRaw 00/01이면 unit 설정·unitReadFailed=false. 그 밖의 값이면 unit=null·unitReadFailed=true
    // (protocol.md 3.4 "그 밖의 값은 단위 미상". Read 응답이라 CommandResult.Response지만 실패와 같게 다룬다)
    fun reset()                        // 연결 끊김: status = Unknown
}

// Advertising.kt — 광고 판별 규칙과 그 상수는 이 파일에만 둔다 (protocol.md 4절 A-1~A-3 확정)
val SCALE_SERVICE_UUID: java.util.UUID = java.util.UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")   // G-1
const val SCALE_MANUFACTURER_ID = 0xFFFF   // A-1
const val DEVICE_TYPE_DOT = 0x01           // A-1 값[1]
data class Advertisement(val name: String?, val serviceUuids: List<java.util.UUID>, val manufacturerData: Map<Int, ByteArray>)
/** (a) serviceUuids에 SCALE_SERVICE_UUID, 또는 (b) manufacturerData[0xFFFF]가 2바이트 이상이고 값[1] == DEVICE_TYPE_DOT. 이름은 보지 않는다(A-2: 화이트리스트 방송은 이름 없음).
 *  값[0](방송 유형 01 화이트리스트 / 02 일반)은 판별에 쓰지 않는다. G-7 서비스(5833ff01-…)만 있는 광고는 후보가 아니다. */
fun isScaleCandidate(adv: Advertisement): Boolean     // A-1 Android 반환 2바이트 확정(프로브 B1)
const val BROADCAST_WHITELIST = 0x01                    // A-1 값[0] 01 = 화이트리스트(02 = 일반·페어링 모드)
fun broadcastTypeOf(adv: Advertisement): Int?          // A-1 값[0]. 2바이트 미만이면 null. 판별에는 쓰지 않고 첫 연결 실패 안내에만 쓴다(5.4)
/** ScanFilter (b)의 manufacturer data·mask(6절). isScaleCandidate (b)와 같은 길이·오프셋 가정이라 같이 고친다. 호출부는 copyOf()로 넘긴다. */
val MANUFACTURER_FILTER_DATA: ByteArray = byteArrayOf(0x00, DEVICE_TYPE_DOT.toByte())   // A-1 확정(프로브 B1)
val MANUFACTURER_FILTER_MASK: ByteArray = byteArrayOf(0x00, 0xFF.toByte())              // A-1 확정(프로브 B1)
const val EXPECTED_MODEL = "TES017"                     // protocol.md A-4 앱 규칙(G-3 확정 값). 다른 파일은 리터럴로 갖지 않는다
```

### 5.4 `ScaleClient` 계약

```kotlin
/** name: 표시 이름 규칙(A-3a, 앱 규칙). 광고 이름 → 없으면 address가 저장 주소와 같을 때 저장된 last_name → 없으면 null.
 *  null이면 화면이 표시할 때만 대체 문구 "TIMEMORE Dot · 주소 끝 5자"(ui-design 3.3)로 바꾼다. 대체 문구는 name·last_name에 저장하지 않는다. 같은 기기라도 방송 유형에 따라 이름이 생기거나 사라지므로, 한 번 본 이름은 스캔 동안 유지한다. */
data class ScanCandidate(val address: String, val name: String?, val rssi: Int, val lastSeenNanos: Long,
                         val broadcastType: Int? = null)   // A-1 값[0](broadcastTypeOf). 2026-11-03 FIX-8

data class ScaleDevice(
    val address: String,
    val name: String?,             // ScanCandidate.name과 같은 규칙. 연결 성공 시 null이 아니면 last_name으로 저장
    val modelNumber: String?,      // 0x2A24. disReadDone=false면 아직 안 읽음, true인데 null이면 읽기 실패
    val firmwareRevision: String?, // 0x2A26. 같은 규칙
    val mtu: Int,
    val disReadDone: Boolean = false,  // 0x2A24·0x2A26 읽기가 (성공·실패 상관없이) 끝남. false면 S3는 "확인 중"(ui-design 5.4)
) { val modelMismatch: Boolean get() = modelNumber != null && modelNumber != EXPECTED_MODEL }  // A-4: 경고만

sealed interface FailureReason {
    data object NoDeviceFound : FailureReason          // 스캔 10 s 동안 후보 없음
    data object ScanFailed : FailureReason             // onScanFailed
    data object ServiceNotFound : FailureReason        // 0xFFF0 또는 FFF1/FFF2 없음
    data object ConnectTimeout : FailureReason         // connectGatt부터 알림 구독까지 10 s 초과
    data class GattError(val status: Int, val whitelistAdvertising: Boolean = false) : FailureReason   // 첫 연결 실패 (예: 133).
        // whitelistAdvertising = status 133이고 scanResults에 있는 그 주소의 broadcastType == BROADCAST_WHITELIST(A-5). 스캔 없이 직접 연결이면 false
    data object ReconnectExhausted : FailureReason     // 재연결 10회 실패
}

sealed interface ConnectionState {
    data object BluetoothOff : ConnectionState
    data object PermissionMissing : ConnectionState    // 영구 거부 여부는 PermissionMonitor가 판단 (8.1)
    data object LocationOff : ConnectionState          // API 30 이하에서 위치 서비스 꺼짐
    data object Idle : ConnectionState                 // 연결·스캔 안 함 (저장 기기 유무는 lastDeviceAddress로 구분)
    data object Scanning : ConnectionState
    data class Connecting(val address: String, val name: String?) : ConnectionState
    data class Connected(val device: ScaleDevice) : ConnectionState
    /** attempt 1..10. 백오프 대기 중과 그 시도의 connectGatt 진행 중 모두 이 상태다(Connecting으로 바꾸지 않는다).
     *  nextRetryAtNanos = 이 attempt의 connectGatt 시작(예정) 시각. now ≥ nextRetryAtNanos면 시도 중이다. 시도가 실패하면 attempt+1의 새 Reconnecting을 낸다 */
    data class Reconnecting(val address: String, val attempt: Int, val nextRetryAtNanos: Long) : ConnectionState
    data class Failed(val reason: FailureReason) : ConnectionState
}

enum class CommandFailure { NotConnected, Rejected, Timeout, Disconnected, InvalidName, PayloadTooLarge }

sealed interface CommandResult {
    data object Success : CommandResult                          // Write 응답(opcode 03) payload 01 (C-W-1, G-5)
    data class Response(val frame: ScaleFrame) : CommandResult   // Read 응답(opcode 02)
    data class Failed(val reason: CommandFailure) : CommandResult
}

interface ScaleClient {
    val connection: StateFlow<ConnectionState>
    val scanResults: StateFlow<List<ScanCandidate>>     // isScaleCandidate 통과분만. startScan 때와 Connected 도달 때만 비운다. connect()·Connecting·Failed 동안은 남긴다(S2가 후보 목록을 계속 그린다, ui-design 5.3)
    val status: StateFlow<ScaleStatus>
    val readings: SharedFlow<ScaleReading>              // 약 10 Hz, replay 0, extraBufferCapacity 64
    /** 저울이 스스로 알린 타이머 변화. TimerState(op = Notify) 프레임에서만 낸다. Read 응답·Write 성공 반영(onCommandSucceeded)은 status만 바꾸고 여기로 내지 않는다. replay 0, extraBufferCapacity 16(빠른 Running→Paused→Running도 잃지 않음).
     *  원문 §5.2.1 근거로 둔 경로다. TES017 v1.0.4는 0x02 Notify를 보내지 않아(C-02-2, C-02-3) 실기기에서는 나오지 않는다. 단위 테스트로만 검증한다. */
    val timerEvents: SharedFlow<ScaleTimerState>
    val lastDeviceAddress: StateFlow<String?>           // 저장된 마지막 연결 주소

    fun refreshEnvironment()                            // 권한 허용·설정 복귀 뒤 UI가 호출. 블루투스·권한·위치 상태 다시 확인. 막힘이 풀릴 때의 전이는 아래 "환경 막힘과 풀림"
    fun startScan()                                     // 10 s 후 자동 정지. 30 s에 5회 제한을 지키려고 직전 시작 6 s 안이면 늦춘다. Connecting·Connected·Reconnecting 중에는 무시한다(상태를 Scanning으로 덮지 않는다)
    fun stopScan()                                      // 스캔을 멈추고 Idle
    fun connect(address: String)                        // 스캔 중이면 먼저 stopScan. Connecting·Connected·Reconnecting 중이면 기존 연결을 사용자 끊기로 close()하고(재연결 취소) 새로 연결. 첫 연결 규칙(실패면 Failed). 성공하면 주소 저장
    fun connectLastDevice(): Boolean                    // Reconnecting이면 대기 중 백오프를 취소하고 지금 시도. Connected·Connecting이면 현재 GATT를 close()하고(사용자 끊기로 보지 않음) 같은 주소로 새 connectGatt, 첫 연결 규칙(실패면 Failed). 환경이 막혀 있으면(BluetoothOff·PermissionMissing·LocationOff) 연결하지 않고 상태를 그대로 두며 true(풀리면 연결한다). 그 밖은 저장 주소로 연결. 저장 주소 없으면 false
    fun disconnect()                                    // 사용자 끊기(연결 중·재연결 중 취소 포함): 재연결하지 않음, Idle
    suspend fun send(command: ScaleCommand): CommandResult   // 한 번에 하나, 다음 호출은 앞 응답까지 대기. 어느 디스패처에서 불러도 된다(환원기·대기 키 갱신은 구현이 생성자 scope 디스패처로 옮긴다, 3절)
}
```

환경 막힘과 풀림(`BleScaleClient`, `FakeScaleClient`는 상태를 테스트가 정한다):
- 생성 때 블루투스·권한·위치를 확인한다. 막혀 있으면 첫 상태가 그 막힘 상태(`BluetoothOff` > `PermissionMissing` > `LocationOff` 순으로 먼저 걸린 것), 아니면 `Idle`이다.
- "연결 의도"는 `connect()`·`connectLastDevice()`가 켜고 `disconnect()`·`PowerOff` 성공이 끈다. 막힘 상태로 바뀌어도 끄지 않는다.
- 막힘이 풀리면(블루투스 켜짐 수신, `refreshEnvironment()`에서 확인) 연결 의도가 있고 저장 주소가 있으면 재연결 경로로 간다: `Reconnecting(address, attempt = 1, nextRetryAtNanos = 풀린 시각)`(대기 없이 바로 시도), 실패하면 6절 재연결 규칙대로 attempt 2부터 백오프, 10회 실패면 `Failed(ReconnectExhausted)`. 첫 연결 규칙(한 번 실패로 `Failed(GattError)`)을 쓰지 않는다. 블루투스 켜짐과 권한 재허용이 같은 경로다(추출 중 권한을 회수했다가 다시 허용해도 한 번 실패로 끝나지 않는다).
- 연결 의도가 없거나 저장 주소가 없으면 풀린 뒤 `Idle`이다.

- `readings`는 `ScaleStateReducer`가 단위를 안 뒤의 값만 낸다(C-01-13).
- `status.scaleTimer`는 Read 응답·Notify·Write 성공 모두로 바뀐다. 엔진 입력(`ScaleTimerChanged`)은 `timerEvents`에서만 만들고, `status.scaleTimer`는 연결 직후 앱 상태와 맞추는 데에만 쓴다(8.2).
- `send()`의 Write가 성공이면 구현이 `ScaleStateReducer.onCommandSucceeded()`를 부른 뒤 반환한다. 호출자는 상태를 직접 고치지 않는다. v1.0.4는 Write 뒤 같은 cmd의 Notify를 보내지 않으므로(C-02-3) Write 응답 성공이 상태를 확정하는 유일한 근거다. Notify를 기다리지 않는다.
- **다른 앱과 동시 연결(protocol 3.13, C-MC-1, 2026-11-03 하네스 추천(잠정))**: 대기 응답은 (opcode, cmd) 키가 같고 **수신 시각 ≥ 요청 쓰기 직전 시각**인 프레임만 받는다. 그 전에 받은 같은 키 프레임은 다른 앱의 응답이다. 대기 요청과 짝이 없는 응답(`responseKey() != null`)은 디버그 로그 `EV 짝 없는 응답 …`만 남기고, Read 응답 값은 저울의 실제 상태라 환원기에 그대로 넣는다. 화면 안내("다른 앱이 연결돼 있을 수 있음")는 두지 않는다: 늦게 온 자기 응답과 구별되지 않아 오탐이 생기고, 다른 앱 응답이 Pourline 동작을 바꾸는 경로가 위 규칙으로 막혀 사용자가 할 일이 없다. 남는 한계(쓰기 뒤 300 ms 안에 다른 앱의 같은 Write 응답이 먼저 오면 그것을 성공으로 받음)는 protocol 3.13.
- **첫 연결 페어링 모드 안내(A-5, 2026-11-03 하네스 추천(잠정))**: attempt 0 실패가 status 133이고 `scanResults`에 있는 그 주소의 마지막 `broadcastType`이 `BROADCAST_WHITELIST`면 `Failed(GattError(133, whitelistAdvertising = true))`. 재연결(attempt ≥ 1) 실패와 `ReconnectExhausted`에는 붙이지 않는다(이미 연결한 휴대폰).
- `PowerOff` 성공 뒤의 끊김은 재연결하지 않고 `Idle`로 간다(protocol 3.5). 저울 전원 버튼으로 끈 끊김(C-0B-2)은 앱이 구별할 수 없어 일반 끊김처럼 재연결한다. `SetUnit`·`ForgetDevice`·`FactoryReset`·`SetName`·`PowerOff`는 MVP 화면에서 부르지 않는다(`SetUnit`: 앱 안 g/oz 전환 없음, 0절). 코덱·`onCommandSucceeded(SetUnit)`은 벡터와 환원기 테스트 때문에 모두 구현한다.

### 5.5 `FakeScaleClient` (testFixtures)

```kotlin
class FakeScaleClient(initial: ConnectionState = ConnectionState.Idle) : ScaleClient {
    val sent: List<ScaleCommand>                          // 받은 명령 기록
    val calls: List<String>                               // "startScan", "connect:<addr>", "refreshEnvironment" 등 호출 기록
    fun setConnection(state: ConnectionState)
    fun setStatus(status: ScaleStatus)
    fun setScanResults(list: List<ScanCandidate>)
    fun setLastDevice(address: String?)
    suspend fun emit(reading: ScaleReading)
    suspend fun emitTimerNotify(state: ScaleTimerState)   // 실제 Notify처럼 status.scaleTimer를 바꾸고 timerEvents로 낸다 (원문 §5.2.1 저울 버튼 흉내, v1.0.4 실기기에는 없음). ReadTimer 응답 흉내는 setStatus(timerEvents로 내지 않음)
    fun respondWith(command: KClass<out ScaleCommand>, result: CommandResult)   // 기본: Success
}

/** 합성 시계열 도우미. 10 Hz, 단위 g. */
fun readings(startNanos: Long, vararg weightsGrams: Double, flowGramsPerSec: Double = 0.0, intervalMs: Long = 100): List<ScaleReading>
```

- `send()` 응답이 `Success`면 실제 클라이언트와 같은 규칙(`ScaleStateReducer.onCommandSucceeded`: `SetUnit` → `unit`, `SetTimer` → `scaleTimer`)으로 `status`를 갱신한 뒤 반환한다. 끄는 옵션은 두지 않는다. `timerEvents`로는 내지 않는다(실제와 같음).

## 6. `:core:ble` 계약

```kotlin
/** BluetoothGatt를 감싼 얇은 경계. 테스트는 FakeGattTransport를 쓴다. suspend 함수는 콜백까지 기다린다. */
interface GattTransport {
    /** 연결 상태·알림 바이트(복사본). transport 생성 시점(connectGatt 직후)부터 Channel(UNLIMITED)에 쌓고 receiveAsFlow()로 낸다.
     *  수집자는 하나뿐(BleScaleClient). 수집 전에 온 ConnectionChanged(빠른 133)도 잃지 않는다. */
    val events: Flow<GattEvent>
    suspend fun discoverServices(): Boolean
    suspend fun requestMtu(mtu: Int): Int?               // 실패면 null
    suspend fun enableNotifications(service: UUID, characteristic: UUID): Boolean  // setCharacteristicNotification + CCCD
    suspend fun writeNoResponse(service: UUID, characteristic: UUID, value: ByteArray): Boolean
    suspend fun read(service: UUID, characteristic: UUID): ByteArray?
    fun hasCharacteristic(service: UUID, characteristic: UUID): Boolean
    fun close()                                          // 멱등. 두 번째부터는 아무 일도 하지 않는다. 뒤의 suspend 작업은 즉시 실패(false/null).
                                                         // events 채널을 닫아 Flow를 끝낸다(이미 쌓인 이벤트는 수집자가 받은 뒤 끝남). 수집 코루틴이 연결마다 남지 않는다.
                                                         // BleScaleClient는 버린 transport의 남은 이벤트를 현재 연결에 반영하지 않는다(수집 코루틴이 자기 transport가 현재 것일 때만 처리)
}
sealed interface GattEvent {
    data class ConnectionChanged(val connected: Boolean, val status: Int) : GattEvent
    data class Notification(val characteristic: UUID, val value: ByteArray, val atNanos: Long) : GattEvent
}
fun interface GattTransportFactory { fun connect(address: String): GattTransport }   // connectGatt(autoConnect=false, TRANSPORT_LE)

class BleScaleClient(
    context: Context,
    scope: CoroutineScope,                 // 앱 범위
    transportFactory: GattTransportFactory,
    clock: MonotonicClock,
    config: ProtocolConfig = ProtocolConfig.Default,
    debugFrameLog: Boolean,                // :app의 BuildConfig.DEBUG를 넘긴다
) : ScaleClient
```

내부 구성(이름은 구현자 재량, 동작은 고정):

- **작업 큐**: `Mutex` + `withTimeout(5_000)`. 서비스 탐색·MTU·디스크립터·쓰기·읽기를 한 번에 하나. 대기 중인 작업은 종류(`DiscoverServices`·`Mtu`·`DescriptorWrite`·`CharacteristicWrite`·`CharacteristicRead`)를 기록하고, 콜백은 종류가 같을 때만 그 작업을 완료한다. 대기 작업이 없거나 종류가 다른 콜백은 디버그 `EV` 로그만 남기고 버린다. 작업이 5 s 시간 초과되면 Android 스택이 아직 바쁜 상태이므로 다음 작업을 보내지 않고 그 연결을 끊김으로 처리한다(`close()` → 아래 끊김·재연결 경로, 첫 연결 중이면 `Failed(ConnectTimeout)`). 그래서 늦게 온 앞 작업의 콜백이 뒤 작업을 완료시키는 일이 없다. 끊기면 대기 작업을 모두 실패로 끝낸다.
- **스캔**: `SCAN_MODE_LOW_LATENCY`, 10 s 상한. `ScanFilter` 두 개(OR)를 `Advertising.kt` 상수로 만든다: (a) `setServiceUuid(ParcelUuid(SCALE_SERVICE_UUID))`, (b) `setManufacturerData(SCALE_MANUFACTURER_ID, MANUFACTURER_FILTER_DATA.copyOf(), MANUFACTURER_FILTER_MASK.copyOf())`(A-3, 기기 유형만 맞추고 방송 유형은 가리지 않는다. 바이트 리터럴은 `Advertising.kt`에만 있다, 4.1 A-1). 필터를 통과한 결과도 `isScaleCandidate()`로 한 번 더 거른다(필터 구현 차이 방어). 이름으로 거르지 않는다. **필터 없는 스캔 전환(F-TAB-1, 2026-11-03 하네스 추천(잠정, 사용자 최종 검토 대기))**: `BluetoothAdapter.isOffloadedFilteringSupported()`가 false면 처음부터 필터 없이(`startScan(null, …)`) 스캔하고, true면 필터 스캔을 시작해 `SCAN_FALLBACK_MS`(6 s, 재시작 간격과 같음) 동안 후보가 0이면 스캐너를 멈추고 필터 없이 다시 시작해 남은 4 s를 돈다(전체 10 s 상한 그대로). 필터 없는 결과는 위 `isScaleCandidate()`가 거른다. 한 번 전환하면 프로세스가 사는 동안 다음 스캔도 처음부터 필터 없이 한다(스캔마다 재시작하면 30 s 5회 제한을 넘길 수 있다). 대가: 필터 없는 스캔은 주변 BLE 광고를 모두 앱으로 올려 CPU·배터리를 조금 더 쓰지만 S2 화면에서 10 s만 돈다. Android 8.1+는 화면이 꺼지면 필터 없는 스캔을 멈추는데, 이 앱의 스캔은 S2 전경에서만 하고 재연결은 스캔이 아니라 `connectGatt`라 영향이 없다. 진단 로그(디버그): `EV scan start filter=on|off offload=<bool>`, `EV scan switch filter=off: …`, `EV scan stop hits=<결과 수, 후보 아님 포함> candidates=<후보 수>`. 근거: Lenovo TB-9707F(Android 12, `mMaxScanFilters: 0`)에서 필터 스캔 8회 결과 0건, 같은 시점 OS 설정은 저울을 찾음(`_workspace/04_device_results.md` F-TAB-1). 대안: (1) 필터 없이만 스캔 — 오프로드 지원 기기에서 배터리 이득을 버림 (2) 시간 전환만 — 미지원 기기에서 6 s를 늘 버림 (3) `bondedDevices`에서 저울을 후보로 — 광고를 보지 않아 저울이 켜져 있는지·RSSI를 모르고 OS 본딩 여부에 기대므로 넣지 않음(전환으로도 못 찾는 기기가 나오면 다시 본다). 디버그 빌드는 후보마다 처음 한 번 `EV ADV <address> name=<이름|null> mfr=<0xFFFF 값 hex|null> uuids=<목록>`을 남긴다(A-1 Android 반환 확인용, 4.1). 표시 이름은 5.4 `ScanCandidate.name` 규칙.
- **연결 순서(GATT-1)**: `connectGatt` → `discoverServices` → FFF0/FFF1/FFF2 확인(없으면 `Failed(ServiceNotFound)`. G-7 서비스 `5833ff01-…`는 있든 없든 무시하고 구독·읽기·쓰기하지 않는다) → `requestMtu(247)`(실패해도 진행, MTU 23으로 기록) → FFF1 알림 켜기 → `Connected` 내보냄 → `ReadUnit`(실패·시간 초과면 `onUnitReadFailed()`. 응답의 unitRaw가 00/01 밖이면 환원기가 `unitReadFailed = true`로 둔다, 5.3) → `ReadBattery` → `ReadTimer` → 0x2A24·0x2A26 읽기(실패해도 연결 유지, `modelMismatch`는 경고만) → 읽은 값으로 `Connected(device.copy(modelNumber, firmwareRevision, disReadDone = true))`를 한 번 더 내보낸다(읽기 실패면 해당 값 null). 이 두 번째 `Connected`는 연결 전이가 아니다: 9.4 `BrewAnnouncement`·9.2 `ConnectEvent.Connected`는 `ConnectionUi` 종류가 바뀔 때만 내므로 다시 나가지 않는다. `connectGatt`부터 알림 구독까지 10 s를 넘으면 `close()` 후 `Failed(ConnectTimeout)`. `ReadTimer` 응답은 `status.scaleTimer`만 채우고 `timerEvents`로 내지 않는다. 컨트롤러는 이 값을 앱 상태와 맞추는 데에만 쓴다(8.2). protocol.md 1절 GATT-1도 같은 순서다(0x05 뒤 0x02 읽기).
- **명령 응답 대기**: `send()`는 명령 `Mutex` 안에서 인코딩 → 길이가 MTU−3을 넘으면 `PayloadTooLarge` → 대기 키 등록 → FFF2 쓰기(로컬 쓰기 완료 `onCharacteristicWrite`는 GATT 큐 진행에만 쓰고 명령 성공으로 보지 않는다) → 응답 대기. Read 명령(opcode 02)과 Write 명령(opcode 03) 모두 `responseKey()`가 같은 FFF1 프레임을 `responseTimeoutMs` 동안 기다린다(G-1a, G-5 확정. 실측 응답 128~304 ms). 응답은 대개 무게 프레임 뒤에 붙어 같은 알림으로 온다(F-10). WriteAck `success=false`는 `Rejected`, 시간 초과는 `Timeout`, 대기 중 끊김은 `Disconnected`. 재시도하지 않는다(C-W-2). 대기 키는 완료·시간 초과·끊김·호출 코루틴 취소(`finally`) 때 제거한다. 대기 키가 없을 때 온 응답 프레임은 어떤 `send()`도 완료하지 않고 수신 경로의 환원기에만 들어간다. 프로토콜에 순번이 없어, 다음 같은 명령의 대기 키가 등록된 뒤 늦게 온 앞 응답은 구분할 수 없다. 이 한계는 받아들인다(명령은 하나씩만 나가므로 값은 같은 종류의 최신 응답이다).
- **수신 경로**: FFF1 알림 → 디버그 `RX` 로그 → `FrameReassembler.feed` → 프레임마다 (1) 대기 중 응답과 키가 맞으면 완료 (2) `ScaleStateReducer.onFrame` → `ScaleReading`이면 `readings`로 낸다 (3) `TimerState(op = Notify)`면 `timerEvents`로 낸다(v1.0.4에서는 오지 않음). 한 알림의 프레임은 들어온 순서대로 처리하고 순서에 기대지 않는다(배터리가 무게보다 앞일 수 있다). `ScaleFrame.Unknown`(예: cmd 0x04)과 `DecodeError`는 디버그 `EV` 로그만 남기고 버린다. 상태·대기 응답·연결을 바꾸지 않는다.
- **끊김**: `STATE_DISCONNECTED` 또는 오류 status면 항상 `close()`하고 객체를 버린다. 재조립기·환원기 `reset()`.
- **재연결**: 사용자 끊기·`PowerOff` 성공·블루투스 꺼짐이 아니면 `Reconnecting(attempt, nextRetryAtNanos)`로 지수 백오프 1, 2, 4, 8, 16, 30, 30, 30, 30, 30 s(최대 10회) 후 새 `connectGatt`. `nextRetryAtNanos`는 그 attempt의 `connectGatt` 시작 시각이다. 백오프가 끝나 시도가 진행되는 동안에도 상태는 같은 `Reconnecting(attempt, nextRetryAtNanos)`로 남고 `Connecting`을 내지 않는다(5.4). 각 시도에도 첫 연결과 같은 10 s 제한(`connectGatt`부터 알림 구독까지)을 둔다. 시도가 실패·시간 초과되면 `close()` 뒤 `Reconnecting(attempt + 1, 새 시각)`, 성공하면 연결 순서(GATT-1)를 거쳐 `Connected`. 10회 실패면 `Failed(ReconnectExhausted)`. 첫 연결(`connect()` 직후) 실패는 재연결하지 않고 `Failed(GattError)`.
- **블루투스 상태**: `ACTION_STATE_CHANGED`로 꺼지면 즉시 `BluetoothOff`, 큐 비움, 재연결 취소. 켜지면 5.4 "환경 막힘과 풀림"대로 연결 의도와 저장 주소가 있을 때 `Reconnecting(attempt = 1)`부터 재연결 경로로 연결한다(`refreshEnvironment()`로 권한·위치 막힘이 풀릴 때와 같은 경로). 수신기는 매니페스트에 선언하지 않고 `ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)`로 앱 범위에서 한 번 등록한다(시스템 보호 브로드캐스트라 NOT_EXPORTED로도 받는다. `android-intent-security`). minSdk가 33 미만이라 AndroidX Core가 병합 매니페스트에 넣는 `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`(signature)을 지우지 않는다.
- **`SecurityException`**: 권한이 필요한 BLE 호출(`startScan`·`stopScan`·`connectGatt`·GATT 작업·`close`)은 `BleGattTransport`·스캐너 안에서 `SecurityException`을 잡아 `PermissionMissing`으로 바꾸고 크래시하지 않는다. 권한 상태를 필드에 캐시해 호출 여부를 정하지 않는다. 호출 직전에 `checkSelfPermission`으로 확인한다(`android-permissions-security` 8절).
- **권한**: API 31 이상 `BLUETOOTH_SCAN`(neverForLocation)·`BLUETOOTH_CONNECT`, 30 이하 `ACCESS_FINE_LOCATION`과 위치 서비스. 없으면 `PermissionMissing`/`LocationOff`. 매니페스트는 `android-ble` 1절 그대로 `:core:ble`의 `AndroidManifest.xml`에 둔다(병합된다). 런타임 권한 목록과 위치 서비스 필요 여부는 아래 `BlePermissions` 한 곳에서만 정한다. `BleScaleClient`의 권한 확인과 `:app` `PermissionMonitor`(8.1)가 둘 다 이것을 호출하고, 어느 쪽도 권한 이름을 따로 갖지 않는다. `neverForLocation`으로 Dot이 안 보이면 `android-ble` 1절의 네 가지를 `BlePermissions.kt`의 상수와 같은 모듈 매니페스트에서 함께 바꾼다(4절 표).

```kotlin
// BlePermissions.kt (:core:ble)
object BlePermissions {
    const val USE_NEVER_FOR_LOCATION = true          // TODO(device): android-ble 1절 neverForLocation 프로브. false면 매니페스트도 함께 바꾼다
    fun required(sdkInt: Int): Array<String>         // 31 이상: SCAN+CONNECT (+ USE_NEVER_FOR_LOCATION=false면 FINE_LOCATION), 30 이하: FINE_LOCATION
    fun needsLocationService(sdkInt: Int): Boolean   // 30 이하, 또는 USE_NEVER_FOR_LOCATION=false
}
```
- **API 33 분기**: `android-ble` 5절. 두 `onCharacteristicChanged` 오버로드를 모두 재정의하고 옛 오버로드는 `SDK_INT >= 33`이면 반환한다. 쓰기 분기는 `BleGattTransport` 한 함수 안에 둔다.
- **마지막 기기 주소**: `SharedPreferences("scale", MODE_PRIVATE)` 키 `last_address`, `last_name`.
- **디버그 프레임 로그**: 태그 `TimemoreBle`, `RX FFF1 <대문자 hex>`(재조립 전 알림 원본), `TX FFF2 <hex>`, `EV <설명>`. `debugFrameLog=false`면 남기지 않는다. 형식 문자열은 순수 함수 `FrameLogFormat`으로 두어 JVM 테스트한다.
- `GattSpec.kt`: 특성 UUID FFF1·FFF2(G-1), DIS(G-2), `CCCD_ENABLE_NOTIFY`. 서비스 UUID는 `:core:protocol`의 `SCALE_SERVICE_UUID`, 모델 값은 `EXPECTED_MODEL`을 참조한다(4절). G-7 서비스 UUID는 어디에도 두지 않는다.
- 매니페스트(`:core:ble`)에는 권한·`uses-feature`만 둔다. 서비스·수신기·provider를 선언하지 않는다.

## 7. `:core:brew` 계약

### 7.1 레시피와 샘플

```kotlin
@Serializable data class RecipeStep(
    val name: String,
    val targetGrams: Double,   // 누적 목표 무게 g (영점 뒤 물 기준)
    val targetAtSec: Int,      // 이 무게에 도달할 목표 경과 초. 기다리기 단계(아래)는 "이 시각까지 기다린다"
    val valveOpen: Boolean? = null,  // 밸브 레시피에서 이 단계 동안의 밸브 상태(true = 열림). null = 밸브 없는 레시피.
                                     // 기본값 null이라 이 필드가 없는 JSON(옛 stepsJson·recipeJson)도 읽히고 밸브 없음이 된다. 사용자 결정 2026-10-30(단계 카드마다 체크)
)

@Serializable data class Recipe(
    val id: Long,              // 0 = 저장 전
    val name: String,          // 1~30자
    val doseGrams: Double,     // 원두 g, 0.1~100.0 (S6 원두량 범위와 같음)
    val waterGrams: Double,    // 물 g, > 0 (비율 = water / dose)
    val steps: List<RecipeStep>,   // 1개 이상. 첫 단계 targetGrams > 0·targetAtSec > 0, 그 뒤 targetGrams는 앞 단계 이상(같으면 기다리기 단계), targetAtSec는 엄격히 오름차순이고 ≤ 3599(타이머 상한 59:59.9 안, 검증은 9.6).
                                   // valveOpen은 모두 null(밸브 없음) 또는 모두 non-null(밸브 레시피). 검증은 9.6
    val finishAtSec: Int? = null,  // 레시피에 적힌 추출 끝 경과 초(예: 4:6 210). null = 마지막 단계 targetAtSec. null이 아니면 마지막 단계 targetAtSec ≤ 값 ≤ 3599 (검증은 9.6).
                                   // 기본값 null이라 이 필드가 없는 JSON(recipeJson)도 읽힌다. 사용자 결정 2026-10-29(자동 완료는 끝 시각 뒤에만)
) {
    /** 자동 완료 판정을 시작하는 경과 초 = finishAtSec ?: steps.last().targetAtSec */
    val finishSec: Int
    /** 밸브 일정(파생, 저장하지 않는다. finishSec처럼 게터라 직렬화되지 않는다). 단계 i의 시작 시각 = i == 0이면 0, 아니면 steps[i − 1].targetAtSec.
     *  단계마다 (시작 시각, valveOpen)을 만들고 바로 앞과 같은 상태는 합친다. 그래서 첫 이벤트 atSec 0, atSec 엄격 오름차순, 열기·닫기 번갈아가 늘 성립한다.
     *  어느 단계든 valveOpen == null이면 빈 목록(= 밸브 없는 레시피, S1 밸브 칸 없음. 일부만 null인 손상 데이터도 밸브 없음으로 읽는다).
     *  "스위치 레시피" 켜짐 여부 = valveSchedule.isNotEmpty(). 따로 저장하지 않는다(단계 값과 어긋날 수 있는 두 번째 사실을 두지 않는다). */
    val valveSchedule: List<ValveEvent>
    /** (0 g, 0 s)에서 시작해 단계 사이를 선형 보간한 계획 무게 g. 마지막 단계 뒤는 마지막 값. 기다리기 단계 구간은 평평하다. */
    fun plannedGramsAt(elapsedSec: Double): Double
    /** 계획상 weightGrams에 도달하는 경과 초. 마지막 단계 무게 이상이면 마지막 단계 시각. (M5 그대로) */
    fun plannedSecFor(weightGrams: Double): Double
    /** 엔진 paceDeltaSec용(7.2 StepGuide). 기다리기 단계가 만드는 평평한 구간마다 [a, b](a = 그 무게 L에 처음 닿는 단계의 targetAtSec,
     *  b = 이어지는 마지막 기다리기 단계의 targetAtSec)를 보고 |weightGrams − L| ≤ toleranceGrams이면 elapsedSec.coerceIn(a, b), 아니면 plannedSecFor(weightGrams).
     *  그래서 기다리는 동안 "제때"(paceDelta 0), 일찍 부어 닿았으면 a 기준으로 앞섬, b를 지나도 다음 붓기를 안 했으면 b 기준으로 늦음이다.
     *  기다리기 단계가 없으면 plannedSecFor(weightGrams)와 같다(M5 레시피 결과 불변). 무게가 L ± 1 g에서 흔들릴 때 L 위아래로 계획 시각이 a ↔ 다음 붓기 시작으로 튀지 않게 여유를 둔다. FIX-4에서 추가 */
    fun plannedSecFor(weightGrams: Double, elapsedSec: Double, toleranceGrams: Double): Double
}

/** 기다리기 단계 = index > 0이고 targetGrams == steps[index − 1].targetGrams(물을 붓지 않고 targetAtSec까지 기다림. 예: 하리오 "2:00까지 담가 두기").
 *  엔진 단계 판정(7.2 "단계")은 무게 기준이라 앞 단계에 닿으면 기다리기 단계도 닿은 것으로 보고 건너뛴다. UI(S1 단계 번호·S8 카드 표시·S6·S10 표식·단계 표)가 구분할 때 이 함수를 쓴다. FIX-4에서 추가 */
fun Recipe.isWaitStep(index: Int): Boolean

/** 밸브 상태 변경 하나. atSec = 앱 타이머 경과 초(일시정지 제외, 3절), open = 이 시각부터의 상태(true = 열림).
 *  Recipe.valveSchedule이 단계에서 만드는 파생 값이라 저장하지 않는다(@Serializable 없음). */
data class ValveEvent(val atSec: Int, val open: Boolean)

/** S1 밸브 칸 값(ui-design이 배치·문구·강조를 정한다). */
data class ValveUi(
    val open: Boolean,              // 지금 상태
    val next: ValveEvent?,          // 다음 변경. 마지막 이벤트를 지났으면 null
    val secondsToNext: Int?,        // ceil(next.atSec − elapsedSec), 1초 단위 올림. next 전에는 1 이상(116.0 s → 4, 119.1 s → 1). next == null이면 null
)
/** 순수 함수. 일정을 다시 검사하지 않는다(Recipe.valveSchedule은 첫 atSec가 0이라 아래 "없으면 0"은 방어). schedule이 비면 null.
 *  i = atSec ≤ elapsedSec인 마지막 이벤트 번호, 없으면 0. open = schedule[i].open, next = schedule.getOrNull(i + 1).
 *  atSec에 정확히 닿은 순간(elapsedSec == atSec)은 이미 그 이벤트 상태다. */
fun valveStateAt(schedule: List<ValveEvent>, elapsedSec: Double): ValveUi?
/** phase 규칙을 더한 화면용 함수: recipe null·Finished → null. Idle·WaitingForPour → valveStateAt(…, 0.0)(붓기 전 준비 상태).
 *  Running·Paused → valveStateAt(…, timer.elapsedNanos(nowNanos) / 1e9)(Paused는 멈춘 경과). 화면이 타이머 글자와 같은 ~100 ms 주기로 부른다(9.4). */
fun valveUiFor(phase: BrewPhase, recipe: Recipe?, timer: TimerSnapshot, nowNanos: Long): ValveUi?

const val FLAG_OVERLOAD = 1
const val FLAG_DISCONNECTED = 2

data class BrewSample(val offsetMs: Int, val weightGrams: Float, val flowGramsPerSec: Float, val flags: Int)
// weightGrams는 과부하면 Float.NaN
// flowGramsPerSec = 같은 입력을 처리한 뒤의 BrewState.flowGramsPerSec(엔진 평활값, 0 이상). 저울 raw 유량(scaleFlowGramsPerSec)이 아니다(7.2 "샘플")

/** 고정 용량 원시 배열 버퍼. 가득 차면 append가 false를 돌려주고 더 쌓지 않는다(컨트롤러가 자동 완료한다, 8.2). 메인 스레드 전용, 스레드 경계를 넘기지 않는다(3절). */
class SampleBuffer(val capacity: Int = 36_000) {   // 10 Hz × 3600 s (저울 타이머 상한 3599 s)
    val size: Int
    fun offsetMs(i: Int): Int
    fun weightGrams(i: Int): Float
    fun flowGramsPerSec(i: Int): Float
    fun flags(i: Int): Int
    fun append(sample: BrewSample): Boolean
    fun clear()
    fun toBlob(): ByteArray
    companion object { fun fromBlob(blob: ByteArray): SampleBuffer }
}

/** 값 없는 구간(ms 범위): 이웃 샘플 간격 > minGapMs, 또는 플래그 샘플이 이어진 구간. S6 HasGaps, 그래프 끊김.
 *  offsetMs가 일시정지를 뺀 타이머 축이라 일시정지 구간은 간격이 생기지 않아 여기에 들지 않는다. */
fun SampleBuffer.gaps(minGapMs: Int = 500): List<IntRange>
/** 단계 도달 판정 여유(g). 엔진 단계 판정(BrewConfig.stepToleranceGrams 기본값), reachTimesMs 기본값,
 *  S6·S10 요약판 표식 "도달"(최종 무게 ≥ targetGrams − 이 값, ui-design 4.4·4.10)이 모두 이 상수를 쓴다. UI에 1.0을 따로 적지 않는다. (M8에서 추가) */
const val STEP_TOLERANCE_GRAMS: Double = 1.0
/** 단계마다 실제로 targetGrams − toleranceGrams에 처음 도달한 offsetMs, 못 했으면 null. S10 단계 표. */
fun Recipe.reachTimesMs(samples: SampleBuffer, toleranceGrams: Double = STEP_TOLERANCE_GRAMS): List<Int?>
```

밸브 단계 모델(사용자 결정 2026-10-30, S8에서 단계 카드마다 밸브 체크. 2026-10-29의 단계와 따로 둔 시각 일정 `Recipe.valveSchedule` 필드를 대체한다):

- 밸브가 바뀌는 때 = 단계 시작 = **앱 타이머** 경과가 앞 단계 `targetAtSec`에 닿을 때(첫 단계는 0 s, 붓기 전 준비 상태). 엔진의 단계 진행(`StepGuide.index`, 무게 도달)은 쓰지 않는다. 근거: (1) S1 밸브 칸의 "다음 변경까지 N초"(사용자 결정 2026-10-29, ui-design 4.11)는 변경 시각을 미리 알아야 한다. 무게 도달은 미리 알 수 없다. (2) 기다리기 단계는 무게가 늘지 않아 무게로는 시작을 정할 수 없다. (3) 하리오 2:00 열기처럼 출처의 동작이 시각으로 적혀 있다. (4) 그래서 2026-10-29 시각 일정의 소비자(`valveStateAt`·`valveUiFor`·엔진 `ValveCue`·S10 일정 줄)를 파생 `valveSchedule` 하나로 그대로 쓴다.
- 대가(사용자에게 알림, 사용자가 받아들임): 밸브는 단계 경계에서만 바뀐다. 붓기 단계의 `targetAtSec`은 "붓기 시작 + 붓기 시간"(8.3)이라, 다음 붓기 시작 시각에 바꾸려면 그 앞에 기다리기 단계(같은 누적 g, `targetAtSec` = 다음 붓기 시작)를 넣는다. 기본 레시피 두 개를 이렇게 나눴다(8.3).
- 기다리기 단계(`isWaitStep`)를 허용한다: 누적 g가 앞 단계와 **같아도** 된다(9.6 검증을 "앞 단계보다 작으면 오류"로 바꿈). `targetAtSec` 엄격 오름차순은 그대로라 단계 시작 시각이 겹치지 않고, 파생 일정은 늘 오름차순이다. 엔진 단계 판정(무게 기준, M5)은 바꾸지 않는다(사용자 확정 2026-10-30): 기다리기 단계는 앞 단계에 닿는 순간 함께 닿은 것으로 보고 S1 단계 카드가 건너뛴다. 그래서 기본 레시피는 기다리기 단계의 안내(시각·동작)를 다음 단계 이름에도 적는다(8.3). 남은 기다리기 단계뿐이면 `lastStepReached = true`이고 카드는 마지막 단계 이름과 "기다리는 중"을 보인다(ui-design 4.5). 시간 차(`paceDeltaSec`)만 평평한 구간을 알도록 `plannedSecFor(weight, elapsed, tolerance)`로 바꾼다(위 주석).
- 파생 일정의 변경은 마지막 단계 시작 이하라 늘 끝 시각(`finishSec` ≥ 마지막 단계 `targetAtSec`) 전이다. 그래서 2026-10-29의 "끝 시각 뒤 밸브 변경" 경고와 `validateValveSchedule`(밸브 오류 3종)이 필요 없어 뺀다.

BLOB 형식(버전 1): 1바이트 버전 `0x01`, 이어서 샘플마다 13바이트 little-endian `int32 offsetMs`, `float32 weightGrams`, `float32 flowGramsPerSec`, `uint8 flags`. 압축하지 않는다(3분 추출 약 23 KB). 모르는 버전 바이트나 길이가 맞지 않으면 `IllegalArgumentException`(호출자는 S10 `NoSamples`로 처리).

### 7.2 엔진

```kotlin
data class BrewSettings(                         // 사용자 설정 (S4), AppPrefs에 저장
    val autoStartEnabled: Boolean = true,
    val autoStartThresholdGrams: Double = 1.0,   // 0.5 / 1.0 / 2.0 중 하나
    val valveHaptic: Boolean = true,             // 밸브 변경 시각 진동(S4 스위치). 엔진은 읽지 않는다(ValveCue는 늘 내고 VM이 거른다, 9.4). 사용자 결정 2026-10-29
)

data class BrewConfig(                           // 고정 상수
    val autoStartConsecutive: Int = 2,             // 임계값을 넘는 연속 샘플 수
    val autoStartConfirmNanos: Long = 500_000_000, // 처음 넘은 샘플부터 이 시간 동안 계속 넘어야 자동 시작(확인 구간, F-B4-1). 시작 시각은 처음 넘은 샘플로 소급
    val weightDropGrams: Double = 2.0,             // 연속한 정상 샘플 사이 무게 감소가 이보다 크면 "급감": 유량 창 비움(O-B8-1), 확인 구간 취소(F-B4-1). 실측 붓기 중 최대 감소 0.1 g(B8)
    val flowWindowNanos: Long = 1_000_000_000,     // 유량 이동 평균 창 (수신 시각 기준)
    val stepToleranceGrams: Double = STEP_TOLERANCE_GRAMS, // 단계 도달 판정 여유 (7.1 상수, 요약판 표식과 같은 값)
    val finishStableNanos: Long = 5_000_000_000,   // 레시피 끝 시각(Recipe.finishSec) 뒤의 샘플만으로 이 시간 동안
    val finishStableGrams: Double = 0.5,           // 무게 변화가 이 값 이하면 자동 완료 (레시피가 있을 때만)
    val noDataTimeoutNanos: Long = 3_000_000_000,  // protocol.md C-01-12(주기 100 ms, 실측 최대 공백 181~243 ms) — 연결 중 0x01 공백이 이보다 길면 NoData.
                                                   // 3 s: 전원 버튼으로 끄면 알림이 멈춘 뒤 2.08~2.25 s에 끊김이 감지된다(C-0B-2, macOS 측정). 2 s면 NoData가 먼저 떴다가 Reconnecting으로 바뀐다
                                                   // TODO(device): protocol.md C-0B-2 (Android) — B6 측정 최댓값 + 0.5 s ≥ 이 값이면 FIX 마일스톤으로 올린다(4.1)
    val maxElapsedNanos: Long = 3_599_900_000_000,     // 타이머 상한 59:59.9 (ui-design 9절). 닿으면 자동 완료(CapReached)
)

enum class BrewPhase { Idle, WaitingForPour, Running, Paused, Finished }
enum class StaleReason { Disconnected, Overload, NoData }

data class TimerSnapshot(val accumulatedNanos: Long, val runningSinceNanos: Long?) {
    fun elapsedNanos(nowNanos: Long): Long          // accumulated + (now − runningSince)
    companion object { val Zero = TimerSnapshot(0, null) }
}

data class StepGuide(
    val index: Int,                 // 현재 단계 (0부터)
    val remainingGrams: Double,     // 현재 단계 목표까지 남은 g, 0 이상
    val paceDeltaSec: Double,       // 경과 초 − recipe.plannedSecFor(무게, 경과 초, stepToleranceGrams)(7.1, 기다리기 구간은 0). 양수 = 계획보다 늦음
    val lastStepReached: Boolean,
)

data class BrewState(
    val phase: BrewPhase,
    val timer: TimerSnapshot,
    val weightGrams: Double?,       // 마지막 유효 무게. stale이면 멈춘 값. 측정 전 null
    val flowGramsPerSec: Double,    // 평활 뒤, 항상 0 이상
    val stale: StaleReason?,
    val staleSinceNanos: Long?,     // "N초 전" 표시용
    val recipe: Recipe?,
    val guide: StepGuide?,          // recipe가 있고 Running/Paused/Finished일 때
    val startedAtNanos: Long?,      // 첫 Running 진입 시각 = offsetMs 기준점
    val settings: BrewSettings,
)

sealed interface BrewInput {
    val atNanos: Long
    data class Reading(val reading: ScaleReading) : BrewInput { override val atNanos get() = reading.atNanos }
    data class Tared(override val atNanos: Long) : BrewInput                  // 영점 Write 성공 뒤
    data class ScaleTimerChanged(val state: ScaleTimerState, override val atNanos: Long) : BrewInput  // ScaleClient.timerEvents(Notify)에서만. Read 응답으로는 만들지 않는다. 원문 경로, v1.0.4에서는 생기지 않음(C-02-2)
    data class UserTimer(val action: TimerAction, override val atNanos: Long) : BrewInput
    data class CancelWaiting(override val atNanos: Long) : BrewInput          // WaitingForPour "취소"
    data class SelectRecipe(val recipe: Recipe?, override val atNanos: Long) : BrewInput
    data class SettingsChanged(val settings: BrewSettings, override val atNanos: Long) : BrewInput
    data class ConnectionChanged(val connected: Boolean, override val atNanos: Long) : BrewInput
    data class Tick(override val atNanos: Long) : BrewInput                   // 1 s마다: 자동 완료·타이머 상한·NoData 판정
    data class Finish(override val atNanos: Long, val capReached: Boolean = false) : BrewInput  // "완료". capReached=true는 컨트롤러의 샘플 버퍼 가득 참(8.2)
    data class Discard(override val atNanos: Long) : BrewInput                // D1 "버리기"
    data class NewBrew(override val atNanos: Long) : BrewInput                // S6 저장·버리기 뒤 Finished → Idle
}

data class BrewSummary(
    val startedAtNanos: Long,
    val durationMs: Long,           // 타이머 경과
    val finalWeightGrams: Double?,
    val recipe: Recipe?,
)

sealed interface BrewEffect {
    data class SendTimer(val action: TimerAction) : BrewEffect    // 앱 쪽 변화만 저울로 보냄
    data class AppendSample(val sample: BrewSample) : BrewEffect
    data object ClearSamples : BrewEffect
    data class Completed(val summary: BrewSummary) : BrewEffect   // S6 요약으로 보낼 대상
    data object Discarded : BrewEffect                            // 저장하지 않고 버림
    data class PhaseChanged(val from: BrewPhase, val to: BrewPhase, val cause: PhaseCause) : BrewEffect  // phase가 바뀔 때마다 하나 (TalkBack 상태 알림, 9.4)
    data class ValveCue(val event: ValveEvent) : BrewEffect       // 밸브를 바꿀 시각에 닿음(아래 "밸브 신호"). 컨트롤러가 BrewEvent.ValveCue로 내보낸다. FIX-4에서 추가
}

/** phase 변화의 출처. UserTimer·Finish(capReached=false)·Discard·CancelWaiting·NewBrew·Tared·SettingsChanged → User, ScaleTimerChanged → ScaleButton,
 *  WaitingForPour의 Reading 임계값 → AutoStart, 레시피 끝 시각 뒤 안정 판정 → AutoFinish, 타이머 상한·Finish(capReached=true) → CapReached. */
enum class PhaseCause { User, ScaleButton, AutoStart, AutoFinish, CapReached }

/** 상태 기계. 시간은 입력의 atNanos로만 받는다(시계 직접 호출 금지). */
class BrewEngine(config: BrewConfig = BrewConfig(), settings: BrewSettings = BrewSettings()) {
    val state: BrewState
    fun handle(input: BrewInput): List<BrewEffect>
}
```

전이 규칙:

| 현재 | 입력 | 다음 | 효과 |
|------|------|------|------|
| Idle, WaitingForPour | `Tared` | `autoStartEnabled`면 WaitingForPour, 아니면 Idle (타이머 0, 연속 카운트 0, 무장 풀림, 영점 시각 = `Tared.atNanos`) | — |
| WaitingForPour(무장 전) | `Reading`: `atNanos > 영점 시각`이고 \|무게\| ≤ `autoStartThresholdGrams` | 그대로, 무장됨 (이 `Reading`은 카운트하지 않는다) | — |
| WaitingForPour(무장 전) | 그 밖의 `Reading`(영점 시각 이전 수신, 또는 \|무게\| > 임계값 = 영점 전 무게) | 그대로, 연속 카운트 0 유지 | — |
| Running, Paused, Finished | `Tared` | 그대로. 무게 기준 보정(오프셋 누적)은 하지 않는다. 앱은 Running·Paused에서 영점을 막는다(9.4 `canTare`), 저울 버튼 영점은 막을 수 없어 이후 무게를 그대로 쓴다 | — |
| WaitingForPour(무장됨) | `Reading` 무게 > `autoStartThresholdGrams`(급감 아님)가 `autoStartConsecutive`회 이상 연속이고, 그 연속의 첫 샘플 시각 t₀부터 이 `Reading`까지 ≥ `autoStartConfirmNanos` | Running. 타이머 시작 = t₀(소급), `startedAtNanos = t₀` | `ClearSamples`, `SendTimer(Start)`, 확인 구간의 앞 샘플들 `AppendSample`(offset = 각 수신 시각 − t₀, 무게·유량은 그 샘플 처리 뒤 값), 이 `Reading`의 `AppendSample` |
| WaitingForPour(무장됨) | 확인 구간 중 `Reading`이 임계값 이하(음수 포함)·급감·과부하, 또는 `ConnectionChanged(false)`, 또는 앞 확인 샘플과 1 s(`FINISH_MAX_GAP_NANOS`) 넘게 떨어진 `Reading`(끊김 없는 공백·NoData, 이 샘플이 새 t₀) | 그대로. 연속 카운트·확인 구간 취소(다음에 넘는 샘플이 새 t₀) | — |
| WaitingForPour | `CancelWaiting` | Idle | — |
| WaitingForPour | `SettingsChanged`(자동 시작 꺼짐) | Idle | — |
| Idle, WaitingForPour | `UserTimer(Start)` ("시작"/"지금 시작") | Running | `ClearSamples`, `SendTimer(Start)` |
| Paused | `UserTimer(Start)` ("계속") | Running | `SendTimer(Start)` |
| Idle, WaitingForPour | `ScaleTimerChanged(Running)` | Running | `ClearSamples` |
| Paused | `ScaleTimerChanged(Running)` | Running | — |
| Running | `UserTimer(Pause)` | Paused | `SendTimer(Pause)` |
| Running | `ScaleTimerChanged(Paused)` | Paused | — |
| Paused | `UserTimer(Reset)` ("초기화") | Idle (타이머 0) | `Discarded`, `ClearSamples`, `SendTimer(Reset)` |
| Running, Paused | `ScaleTimerChanged(Reset)` (저울 버튼) | 샘플이 있으면 Finished, 없으면 Idle | 있으면 `Completed`, 없으면 `Discarded` |
| Running, Paused | `Finish` | Finished | `Completed`, Running이었으면 `SendTimer(Pause)` |
| Running, Paused | `Discard` | Idle (타이머 0) | `Discarded`, `ClearSamples`, `SendTimer(Reset)` |
| Running | `Reading`/`Tick`: recipe 있음, `timer.elapsedNanos(atNanos) ≥ recipe.finishSec × 1e9`, 무게 ≥ 마지막 단계 − tolerance, 경과 `finishSec` 이후의 정상 샘플만으로 `finishStableNanos` 동안 변화 ≤ `finishStableGrams` (가장 이른 자동 완료 = 끝 시각 + 5 s) | Finished | `Completed`, `SendTimer(Pause)` |
| Running | `Reading`/`Tick`: `timer.elapsedNanos(atNanos) ≥ maxElapsedNanos` (원인 `CapReached`) | Finished. 타이머는 `maxElapsedNanos`로 고정(`accumulatedNanos = maxElapsedNanos`) | `Completed`, `SendTimer(Pause)`. 이 `Reading`의 샘플은 더하지 않는다 |
| Finished | `NewBrew` | Idle (타이머 0) | `ClearSamples` |
| Idle, WaitingForPour | `SelectRecipe` | 그대로, recipe 반영 | — |

- 자동 시작 무장(protocol.md C-0D-2, 4.2): 저울은 0x0D 성공 응답과 같은 알림, 그리고 그 뒤 약 300 ms(알림 2개) 동안 영점 전 무게를 보낸다. 컵·드리퍼를 올리고 영점을 누르면 이 값(> 임계값)이 연속으로 들어와 바로 잘못 자동 시작한다. 또 `readings`(SharedFlow)와 `send()` 반환은 다른 경로라 응답 앞에 온 프레임이 `Tared` 뒤에 처리될 수 있다. 그래서 WaitingForPour는 무장 전에 연속 카운트를 세지 않고, 영점이 반영된 첫 `Reading`(시각이 `Tared` 뒤, \|무게\| ≤ 임계값)에서 무장한다. 무장 여부와 영점 시각은 엔진 내부 상태이고 `BrewState`에 넣지 않는다(새 화면 상태 없음). 빈 저울에서 영점을 누르면 첫 `Reading`에서 바로 무장한다. `UserTimer(Start)`("지금 시작")는 무장과 상관없다.
- **자동 시작 확인 구간(F-B4-1, 2026-11-03 하네스 추천(잠정))**: 영점 뒤 무장된 상태에서 물건을 들어 올리면 손 압력으로 잠깐 + 무게가 나온다(실측 `04_device_b_B4.log` +342.4 s: 2.3 → 5.2 → 0.3 → 4.0 → −2.5 → −29.8 → −47.7 g, 약 100 ms 간격). 이전 규칙(2샘플 연속)은 2.3·5.2에서 시작했다. 그래서 처음 넘은 샘플부터 500 ms 동안 계속 넘어야 시작하고, 그 사이 임계값 아래·음수·급감(2 g 넘게 감소)이면 취소한다. 실제 붓기는 단조 증가라(`04_device_b_B8.log` +218.9 s: 2.8, 4.4, 5.2, 7.2, 8.0, 9.5 g) 500 ms 뒤 시작하고, 타이머·샘플은 처음 넘은 샘플로 소급하므로 기록에서 잃는 시간이 없다. 화면이 Running으로 바뀌는 것만 최대 약 0.5 s(+ 알림 간격) 늦다. 저울 타이머(`SetTimer(Start)`)는 0.5 s 늦게 시작하지만 저울 타이머는 1 s 해상도이고 Dot에는 화면이 없어(D-1) 보이는 차이가 없다. 앱 타이머가 표시·기록의 기준이다(3절). 비교한 대안: (a) 연속 3샘플 + 단조 증가 — 손 압력이 3샘플 단조로 오르는 경우(B4 +198.6 s 0.8 → 2.9 → 6.0 g)를 막지 못한다, (b) 바로 시작하고 음수가 되면 취소·`SendTimer(Reset)` — 화면이 Running으로 바뀌었다 돌아오고 TalkBack 알림·모양 전환·저울 명령이 두 번 나간다, (c) 임계값 올리기 — 사용자 설정(0.5/1.0/2.0 g)을 무시하고 손 압력(5.2 g)보다 높여야 해 느린 붓기를 놓친다. 되돌리는 비용: `BrewConfig` 상수 두 개와 엔진 분기, 테스트 시계열.
- **유량 급감 규칙(O-B8-1, 2026-11-03 하네스 추천(잠정))**: 연속한 정상 샘플 사이 무게가 `weightDropGrams`(2 g) 넘게 줄면 유량 창을 비우고 그 샘플의 저울 유량부터 다시 평균한다. 원인: 서버를 들 때 손 압력으로 생긴 저울 유량(1 s 순증가, 수십 g/s)이 앱 1 s 평균 창에 남아, 무게가 −245.9 g으로 떨어진 뒤에도 최대 1 s 동안 20.6 g/s가 보였다(B8 저장 뒤 Idle, `B8_end.png`). 천천히 줄 때(샘플당 2 g 이하)는 바꾸지 않는다: 저울 유량이 감소 중에도 0보다 클 수 있다는 C-01-7은 그대로이고, 붓기 중 실측 감소는 샘플당 최대 0.1 g이다. 컵을 올릴 때(증가) 튀는 값은 그대로 평균한다(그래프는 ui-design 4.4가 20 g/s에서 자른다).
- 자동 완료(사용자 결정 2026-10-29): 레시피가 있으면 레시피 끝 시각(`finishSec`)이 지나기 전에는 무게가 멈춰도 끝내지 않는다. 침지(하리오 스위치: 10 s에 다 붓고 2:00에 열기)나 뜸 대기처럼 무게가 멈추는 구간에서 일찍 끝나지 않게 한다. 끝 시각 전 샘플은 안정 판정 창에 넣지 않는다. 레시피 없는 추출은 지금처럼 무게 정지 자동 완료가 없다(사용자 "완료"·타이머 상한만). 타이머 상한 59:59.9 자동 완료는 레시피와 상관없이 그대로다.
- 타이머 상한: Paused는 타이머가 멈춰 있어 상한에 닿지 않으므로 Running만 본다. 끊김·`NoData`로 샘플이 쌓이지 않아도 `Tick`이 상한을 잡는다. 같은 입력에서 상한 판정이 샘플 추가·마지막 단계 판정보다 먼저다. 샘플 버퍼 가득 참(8.2 `Finish(capReached = true)`)과 타이머 상한 중 먼저 오는 쪽이 끝낸다. 둘 다 원인은 `CapReached`다.
- 표에 없는 (상태, 입력) 조합은 상태를 바꾸지 않고 효과도 내지 않는다. 앱이 보낸 `SetTimer`의 Write 성공 반영은 `status`만 바꾸고 `ScaleTimerChanged`가 되지 않는다(5.4 `timerEvents`). v1.0.4는 Write 뒤 Notify를 보내지 않는다(C-02-3). 다른 펌웨어가 같은 상태의 Notify를 되돌려 보내도 이미 같은 상태라 이 규칙으로 무시된다. 그래서 `SendTimer`는 저울에서 온 변화에 대해 나가지 않는다.
- `ScaleTimerChanged` 행(표의 `ScaleTimerChanged`·`PhaseCause.ScaleButton`)은 원문 §5.2.1(저울 버튼 타이머 Notify) 근거로 남긴 경로다. TES017 v1.0.4에서는 일어나지 않으므로 단위 테스트로만 검증하고 실기기 확인 대상이 아니다.
- `SettingsChanged`는 모든 상태에서 `settings`를 갱신한다. 상태 전이는 표대로다(WaitingForPour에서 자동 시작이 꺼질 때만 Idle). 이후 `Tared`·임계값 판정은 새 설정을 쓴다.
- phase가 바뀌는 모든 전이는 효과 목록 끝에 `PhaseChanged(from, to, cause)`를 하나 더한다(표의 효과 열에는 적지 않았다).
- 저울 버튼 리셋을 바로 버리지 않고 요약(S6)으로 보내는 이유: 손이 젖은 채 실수로 누른 버튼 하나로 기록을 잃지 않게 한다. 요약에서 사용자가 버릴 수 있다.
- `Reading`은 모든 상태에서 `weightGrams`·`flowGramsPerSec`·`stale`을 갱신한다.
- **샘플**: Running일 때만 `Reading`마다 `AppendSample`. `offsetMs = timer.elapsedNanos(atNanos) / 1_000_000`(일시정지 제외 앱 타이머 경과, 3절). 그래서 일시정지 뒤 첫 샘플은 일시정지 직전 샘플 바로 다음 offset이고, 그래프·목표선·`reachTimesMs`·`paceDeltaSec`·CSV가 같은 시간 축을 쓴다. 과부하면 무게 NaN + `FLAG_OVERLOAD`. Running 중 `ConnectionChanged(false)`면 마지막 무게로 `FLAG_DISCONNECTED` 샘플 하나. 샘플 유량은 모든 샘플(플래그 샘플 포함)에서 그 입력을 처리한 뒤의 `BrewState.flowGramsPerSec`(평활, 0 이상)다. 플래그 샘플은 유량 창에서 빠지므로 그 값은 창에 남은 정상 샘플의 평균이다. 그래서 S1·S6·S10 리본 굵기, CSV `flow_g_per_s`, S1 유량 숫자가 같은 값을 쓴다.
- **유량**: 과부하·끊김 샘플은 창에서 뺀다. 평활값 = 창(`flowWindowNanos`, 수신 시각 기준) 안 저울 유량의 평균, 음수는 0(방어, 실측 음수 없음). 무게 변화로 유량 값을 고치지 않는다. 다만 급감(위 "유량 급감 규칙")이면 창을 비운다. 저울 유량은 clamp(w(t) − w(t−1 s), 0, 999)라 무게가 줄면 보통 0이지만 감소 중에도 0보다 클 수 있고, 컵을 올리거나 다시 놓으면 1 s 동안 최대 99.9 g/s가 나온다(C-01-5, C-01-7). 앱은 그 값을 그대로 평균한다. 알림이 300 ms 비어도 시간 창이라 보정이 필요 없다.
- **stale**: 끊김 → `Disconnected`, 과부하 → `Overload`(`weightGrams`는 과부하 직전 정상 값을 그대로 둔다. 사용자 결정 2026-10-28), 연결 중 NoData 기준 시각 = max(마지막 `Reading.atNanos`, 마지막 `ConnectionChanged(true).atNanos`)(연결 뒤 `Reading`이 없으면 연결 시각)이고, 이 기준 뒤 `noDataTimeoutNanos` 넘게 `Tick`만 오면 → `NoData`. 그래서 재연결 직후 첫 `Reading` 전(0x06 응답 전 0x01 버림, C-01-13)의 `Tick`은 끊기기 전 `Reading`으로 NoData를 만들지 않는다. `ConnectionChanged(true)`는 `Disconnected`를 풀지 않는다(첫 정상 `Reading`까지 유지, 3 s를 넘으면 `NoData`로 바뀜). 다음 정상 `Reading`에서 해제. stale이 시작된 시각을 `staleSinceNanos`에 둔다(NoData는 위 기준 시각). 끊김 중 타이머는 계속 간다(앱 시계).
- **단계**: 현재 단계 = 무게 < `targetGrams − tolerance`인 첫 단계, 없으면 마지막 단계이고 `lastStepReached = true`. 기다리기 단계(7.1 `isWaitStep`)도 이 규칙 그대로다(앞 단계와 함께 닿아 건너뜀, M5 규칙 불변). `paceDeltaSec`만 7.1 세 인자 `plannedSecFor`로 계산한다(FIX-4).
- **밸브 신호**(사용자 결정 2026-10-29, 일정은 2026-10-30부터 단계에서 파생한 `recipe.valveSchedule`, 7.1): Running에서 `Reading`·`Tick`을 처리할 때 `e = timer.elapsedNanos(atNanos)`, 엔진 내부 `valveCheckedNanos`(직전 확인 경과)로 `valveCheckedNanos < atSec × 1e9 ≤ e`인 `recipe.valveSchedule` 이벤트를 찾는다. 있으면 그중 **마지막 하나만** `ValveCue(event)`로 낸다(공백 뒤 한 입력에 둘을 넘어도 한 번, 지나간 이벤트를 소급해 여러 번 내지 않는다). 그 뒤 `valveCheckedNanos = max(valveCheckedNanos, e)`(늦게 온 이른 시각 `Reading`이 다시 내지 않게). Idle·WaitingForPour에서 Running에 들어갈 때 `valveCheckedNanos = 0`이라 atSec 0 이벤트는 신호가 없다(준비 상태). 그래서 신호는 타이머가 0:00 뒤의 이벤트를 넘을 때만 나고, 시작·자동 시작·재개·초기화(`UserTimer`·`Discard`·`NewBrew` 등 Reading·Tick이 아닌 입력)에는 나지 않는다. Paused는 타이머가 멈춰 신호가 없고, Paused → Running은 값을 그대로 이어 가 재개 뒤 처음 넘을 때 한 번 낸다. `ConnectionChanged` 등 다른 입력도 신호를 내지 않는다. 그래서 끊김 중에는 `Tick`(1 s)이 잡아 최대 1 s 늦다. 타이머 상한에 닿는 입력은 상한 판정이 먼저라 신호를 내지 않는다. 효과 순서는 그 입력의 `AppendSample` 다음, 전이 효과(`Completed` 등) 앞이다. `valveCheckedNanos`는 엔진 내부이고 `BrewState`에 넣지 않는다.
- 테스트 시계열(필수): 실측 들어 올림(`04_device_b_B4.log` +342.478~343.375 s: 2.3, 5.2, 0.3, 4.0, −2.5, −29.8, −46.9, −47.5, −47.7 g → 시작 안 함, `SendTimer` 없음, 유량 0. `AutoStartConfirmTest`), 실측 붓기 시작(`04_device_b_B8.log` +218.819~219.600 s: 0.6, 2.8, 4.4, 5.2, 7.2, 8.0, 9.5, 10.2, 12.1 g → 9.5 g(+543 ms)에서 Running, `startedAtNanos` = 2.8 g 시각, 소급 샘플 offset 0부터), 확인 구간 중 임계값 아래·급감·끊김 취소, 서버 들어 올림 유량(47 g·20 g/s → −245.9 g에서 유량 0), 정상 추출, 영점 직후 ±0.6 g 흔들림(시작 안 함), 실측 영점 순서(`Tared` → 213.4 g × 3 → 0 g, Running 안 됨. 이어서 1.2 g부터 100 ms 간격으로 넘는 샘플이 500 ms 확인 구간을 채우면 Running, C-0D-2·F-B4-1), 컵 들어 올림(무게가 2 g 넘게 급감하면 창을 비우고 그 샘플의 저울 유량 0.5 g/s만 남음. 천천히 줄면 저울 유량 0.4 g/s가 그대로 평균에 들어감), 300 ms 공백, 3 s 넘는 공백(NoData. 2.5 s 공백은 NoData 아님), 끊김 후 재연결(재연결 뒤 Reading 없이 2.5 s Tick은 Disconnected 유지), 과부하, 저울 버튼 타이머(메아리 없음), 저울 버튼 리셋(요약으로), 레시피 단계·`paceDeltaSec` 부호, 기다리기 단계(건너뜀·기다리는 동안 `paceDeltaSec` 0·L ± 1 g 흔들림에 튀지 않음), 밸브 신호(정확히 도달·지나침·Paused 중·재개 뒤 한 번·한 입력에 두 이벤트), 자동 완료(끝 시각 전 정지는 무시, 하리오 스위치 침지 포함), 타이머 상한(Reading 없이 Tick만으로 도달 포함), 자동 시작 꺼짐, 일시정지 10 s 뒤 계속(gaps 없음, offset 연속), Idle에서 설정 변경 뒤 영점·임계값 판정, phase 전이마다 `PhaseChanged` 출처.

## 8. `:app` 계약

### 8.1 DI와 수명

```kotlin
/** container는 첫 접근 때 만든다(DEMO-2부터. 그 전에는 onCreate에서 만들었다). MainActivity.onCreate가 VariantHooks.onActivityCreate를 먼저 불러
 *  debug 데모 모드 여부를 정한 뒤 처음 접근한다(10.1). release VariantHooks.overrideContainer는 늘 null이라 동작이 바뀌지 않는다. */
class TimemoreApp : Application() {
    val container: AppContainer by lazy { VariantHooks.overrideContainer(this) ?: AppContainer(this) }
}

class AppContainer(app: Application) {
    val appScope: CoroutineScope                 // SupervisorJob + Dispatchers.Main.immediate + CoroutineExceptionHandler(로그만, 3절)
    val clock: MonotonicClock                    // SystemClock::elapsedRealtimeNanos
    val scale: ScaleClient                       // BleScaleClient
    val prefs: AppPrefs
    val permission: PermissionMonitor
    val database: AppDatabase
    val recipes: RecipeRepository
    val brews: BrewRepository
    val brewController: BrewSessionController
    fun csvTarget(uri: Uri): CsvTarget           // 8.4. UI 계층이 CreateDocument 결과 Uri로 부르고 결과를 ViewModel에 넘긴다(M11)
}

/** ViewModel·컨트롤러는 이 interface만 받는다. 구현은 SharedPrefsAppPrefs(SharedPreferences("ui")). */
interface AppPrefs {
    val brewSettings: StateFlow<BrewSettings>
    fun setBrewSettings(settings: BrewSettings)
    fun isModelWarningDismissed(address: String): Boolean
    fun dismissModelWarning(address: String)
}
class SharedPrefsAppPrefs(context: Context) : AppPrefs   // BrewSettings 세 필드를 각각 저장한다. valveHaptic(키 "valve_haptic", 없으면 true)는 M8에서 더한다

enum class PermissionStatus { Granted, Denied, PermanentlyDenied }

/** 권한 결과는 Activity만 안다(shouldShowRequestPermissionRationale). S1·S2가 같은 값을 쓰게 앱 범위에 둔다. */
interface PermissionMonitor {
    val status: StateFlow<PermissionStatus>      // 시작 시 checkSelfPermission으로 Granted/Denied
    fun onResult(allGranted: Boolean, permanentlyDenied: Boolean)   // 허용이면 scale.refreshEnvironment()
    fun onResume()                               // 설정에서 돌아옴: 다시 확인 + refreshEnvironment()
    val requiredPermissions: Array<String>       // BlePermissions.required(Build.VERSION.SDK_INT) 그대로(6절). 목록을 따로 만들지 않는다
}
class AndroidPermissionMonitor(context: Context, scale: ScaleClient) : PermissionMonitor

/** 영구 거부 판정(F-B2-1, 2026-11-03 하네스 추천(잠정)). MainActivity가 부르는 순수 함수, PermissionDenialTest */
object PermissionDenial {
    const val INSTANT_RESULT_MS = 500L
    fun deniedOnceAfterResult(granted: Boolean, rationale: Boolean, deniedOnce: Boolean): Boolean  // 허용 → false, rationale true → true, 그 밖 그대로
    fun isPermanent(granted: Boolean, rationale: Boolean, deniedOnce: Boolean, elapsedMs: Long): Boolean
        // !granted && !rationale && (deniedOnce || elapsedMs < INSTANT_RESULT_MS). elapsedMs = 요청 launch → 결과 콜백
    fun deniedOnceOnResume(allGranted: Boolean, deniedOnce: Boolean): Boolean  // 허용돼 있으면 false(설정에서 허용하고 돌아옴)
}
```

- 영구 거부 판정(F-B2-1): 시스템이 이미 '다시 묻지 않음'인데 앱의 `deniedOnce` 기록이 없으면(다른 경로로 거부·백업 복원·adb) 이전 규칙은 Denied로 남아 "권한 허용"이 대화상자 없이 즉시 거부되는 막다른 화면이 됐다. 그래서 요청에서 결과까지 `INSTANT_RESULT_MS`(500 ms)보다 빨리 거부 + rationale false로 돌아오면 대화상자가 뜨지 않은 것으로 보고 영구 거부로 판정한다. 대화상자를 바깥 눌러 닫은 경우는 사람이 보고 누르는 시간(보통 1 s 이상)이 걸려 Denied로 남는다. 아주 빨리 닫아 영구 거부로 잘못 보면 "설정 열기"가 나오고, 설정에서 허용할 수 있어 막히지 않는다. 대안 "Denied 배너에 '설정 열기' 보조 동작"은 S1·S2·S3 세 화면과 문구·스크린샷을 바꾸고 첫 실행부터 버튼 두 개를 보여 버려 택하지 않았다. 되돌리는 비용 낮음(함수 하나).
- `deniedOnce`(SharedPreferences "permission")는 화면 복귀(`onResume`) 때 권한이 모두 허용돼 있으면 지운다(B2 관찰: 설정에서 허용한 뒤에도 남아 나중에 다시 끄면 처음부터 "설정 열기"가 나올 수 있었다).

- 가짜(`FakeAppPrefs`, `FakePermissionMonitor`, 8.3의 `FakeBrewRepository`·`FakeRecipeRepository`)는 `:app`의 test 소스(`app/src/test/.../fake/`)에 둔다. 호출 기록과 예외 주입(`failNext…`)을 갖는다. 가짜는 실제 구현과 같은 의미 규칙을 따른다: `FakeBrewRepository.saveDraft`는 8.3 주석대로 `in_progress`인 행만 갱신하고 완료·삭제된 id면 아무것도 바꾸지 않고 같은 id를 돌려준다. ViewModel·컨트롤러 테스트는 이 가짜와 `FakeScaleClient`만 쓰고 Robolectric을 쓰지 않는다. 실제 구현(`SharedPrefsAppPrefs`, `AndroidPermissionMonitor`, Room 저장소)만 Robolectric으로 테스트한다.
- `AppContainer`는 마일스톤마다 채운다. M7은 `appScope`·`clock`·`scale`·`prefs`·`permission`만 갖는다. M8이 M6 결과(`database`·`recipes`·`brews`)를 연결하고 `brewController`를 더한다. 그 전 마일스톤의 컨테이너에는 해당 `val`이 없다(빈 자리·null 기본값을 두지 않는다).
- `AppContainer`의 생성자 매개변수 `scaleFactory`·`databaseFactory`(M7·M8 코드에 있음)는 테스트와 debug 데모 모드(10.1)만 바꾼다. 실제 앱은 기본값(`BleScaleClient`, `timemore.db`)이다.
- `AppContainer` 초기화에서 한 번 `scale.connectLastDevice()`를 부른다. 이때 환경이 막혀 있으면 상태는 막힘 그대로이고 풀리면 연결한다(5.4 "환경 막힘과 풀림").
- ViewModel은 `viewModelFactory { initializer { XxxViewModel(container…) } }`로 만든다. 연결·세션을 소유하지 않는다.

### 8.2 `BrewSessionController`

```kotlin
class BrewSessionController(
    scale: ScaleClient,
    brews: BrewRepository,
    prefs: AppPrefs,
    clock: MonotonicClock,
    wallClockMs: () -> Long,                     // System::currentTimeMillis, 시작 시각 표기용
    scope: CoroutineScope,
    config: BrewConfig = BrewConfig(),
    sampleCapacity: Int = 36_000,                // 테스트가 작은 값으로 가득 참을 확인한다
    timerSyncDeadlineNanos: Long = 4_000_000_000, // 연결 직후 맞추기: Connected 뒤 이 시간까지 status.scaleTimer가 null이면 미상으로 본다.
                                                  // GATT-1 ReadUnit·ReadBattery·ReadTimer 각 최대 responseTimeoutMs(1000 ms) + 여유 1 s
) {
    val state: StateFlow<BrewState>
    val samples: SampleBuffer                    // 읽기만 한다
    val sampleCount: StateFlow<Int>              // 그래프 다시 그리기 신호
    val tareInFlight: StateFlow<Boolean>
    val draftId: StateFlow<Long?>                // 실시간 세션의 초안 id. 첫 saveDraft 전·NewBrew·Discarded 뒤 null (D2 판정에서 뺄 id)
    val lastUnit: StateFlow<WeightUnit?>         // 마지막으로 받은 ScaleReading.sourceUnit. 끊김·NewBrew에서 지우지 않는다(끊김 중 멈춘 값의 표시 단위, 9.3·9.4)
    val saveFailed: StateFlow<Boolean>           // 확정 저장 실패로 요약을 들고 있는 동안 true. retrySave() 시작·성공, newBrew()에서 false. 새 BrewViewModel이 놓친 SaveFailed를 다시 알리는 데만 쓴다(9.4)
    val events: Flow<BrewEvent>                  // Channel 기반 일회성

    fun tare()                                   // send(Tare) 성공 → Tared + TareSucceeded, 실패 → TareFailed. tareInFlight 동안 다시 불리면 아무것도 하지 않는다(send 없음)
    fun startTimer()                             // 시작·지금 시작·계속
    fun pauseTimer()
    fun resetTimer()                             // Paused "초기화"
    fun cancelWaiting()
    fun finish()
    fun discard()                                // D1 "버리기"
    fun newBrew()                                // S6 저장·버리기 뒤
    fun selectRecipe(recipe: Recipe?)
    fun retrySave()                              // SaveFailed 뒤 "다시 시도": Completed 확정 저장을 다시 한다. 대기 중인 실패 저장이 없으면 아무것도 하지 않는다
}

sealed interface BrewEvent {
    data class TareFailed(val reason: CommandFailure) : BrewEvent              // BrewViewModel이 Rejected → TareRejected, Timeout → TareTimeout, NotConnected·Disconnected → TareNotConnected(다시 없음)로 바꾼다 (9.4)
    data object TareSucceeded : BrewEvent                                      // TalkBack "영점 맞춤"
    data class ScaleTimerSyncFailed(val reason: CommandFailure) : BrewEvent    // 앱 타이머는 계속 간다
    data class PhaseChanged(val from: BrewPhase, val to: BrewPhase, val cause: PhaseCause) : BrewEvent  // 엔진 효과 그대로 (TalkBack 상태 알림)
    data class Finished(val sessionId: Long) : BrewEvent                       // S6 Summary(sessionId, recovered=false)로 이동
    data object SaveFailed : BrewEvent                                          // Completed 확정 저장 실패. phase는 Finished 그대로, retrySave()로 다시 시도 (9.4 BrewMessage.SaveFailed)
    data class ValveCue(val event: ValveEvent) : BrewEvent                      // 엔진 효과 그대로. VM이 알림·진동으로 바꾼다(9.4). M8에서 추가
}
```

- 입력 변환: `scale.readings` → `Reading`, `scale.timerEvents` → `ScaleTimerChanged`(v1.0.4에서는 오지 않음), `scale.connection`의 Connected 진입·이탈 → `ConnectionChanged`, `prefs.brewSettings` → `SettingsChanged`, 1 s 주기 → `Tick`. `scale.status.scaleTimer`는 엔진 입력으로 쓰지 않는다.
- 메아리 거르기는 두지 않는다. C-02-3이 "Write 뒤 0x02 Notify 없음"으로 확정됐고, 같은 상태의 Notify가 와도 엔진이 무시한다(7.2).
- 연결 직후 맞추기: Connected에 들어갈 때마다 그 연결에서 처음 채워진 `status.scaleTimer`(`ReadTimer` 응답, C-02-4)를 한 번 본다. 엔진 phase가 Running인데 저울이 Running이 아니면 `SetTimer(Start)`, Paused인데 저울이 Running이면 `SetTimer(Pause)`를 보낸다(앱이 기준). 그 밖의 조합(Idle·WaitingForPour·Finished, 또는 Paused인데 저울 Reset)은 보내지 않고 phase도 바꾸지 않는다. 실패면 `ScaleTimerSyncFailed`. 그래서 저울 전원을 껐다 켜 저울 타이머가 Reset이어도, 끊긴 동안 앱에서 일시정지했어도 phase는 그대로다.
- 저울 타이머 미상: Connected 진입 뒤 `timerSyncDeadlineNanos`(4 s)까지 `status.scaleTimer`가 null이면(`ReadTimer`가 `Timeout`으로 끝나 연결은 유지된 경우, 6절) 미상으로 본다. 그때 phase가 Running이면 `SetTimer(Start)`를 한 번 보내고(B6: 전원을 다시 켠 저울은 0에 멈춰 있다), Paused·그 밖이면 보내지 않는다. 실패면 `ScaleTimerSyncFailed`. 맞추기(값으로든 미상으로든)는 연결 하나에 한 번이고, 그 뒤 늦게 채워진 `scaleTimer`(늦은 응답, `SetTimer` 성공 반영)는 다시 보지 않는다. 기한 전에 끊기면 그 연결의 맞추기는 버린다. 기준을 `ReadTimer`의 `send()` 결과로 잡지 않는 이유: 그 호출은 `BleScaleClient` 안(GATT-1)에 있어 컨트롤러가 보지 못하고, 결과를 내보내려면 끝난 M3의 `ScaleStatus` 계약을 바꿔야 한다.
- 효과 처리: `SendTimer` → `scale.send(SetTimer)`(실패면 `ScaleTimerSyncFailed`), `AppendSample` → `samples.append`(false = 버퍼 가득 참이면 `Finish(atNanos, capReached = true)`를 엔진에 넣는다. 타이머 상한은 엔진이 `Tick`·`Reading`에서 직접 판정한다, 7.2), `ClearSamples` → `samples`, `PhaseChanged`·`ValveCue` → 같은 이름의 `BrewEvent`, `Completed` → 주기 저장 Job을 `cancelAndJoin()`한 뒤 `brews.saveDraft()`로 초안을 확정 저장하고 `Finished(id)`, `Discarded` → 주기 저장 Job을 `cancelAndJoin()`한 뒤 초안이 있으면 `brews.deleteSession(id)`. 그래서 진행 중이던 10 s 저장이 확정 저장·삭제 뒤에 끝나 행을 되살리거나 덮어쓰지 않는다(8.3 `saveDraft`의 `in_progress` 조건이 두 번째 방어선). `draftId`는 첫 `saveDraft` 결과로 채우고 `Discarded`·`NewBrew` 뒤 null로 되돌린다.
- 저장 실패: `saveDraft`·`deleteSession` 호출은 모두 `runCatching`으로 감싼다(`CancellationException`은 다시 던진다). 앱이 죽지 않는다.
  - 주기 저장 실패: 로그만 남기고 다음 주기(10 s 뒤)에 다시 시도한다. 이벤트를 내지 않는다.
  - `Completed` 확정 저장 실패: `BrewEvent.SaveFailed`를 내고 그 요약을 들고 있는다. phase는 엔진이 이미 Finished라 그대로다. `retrySave()`가 같은 요약으로 다시 저장해 성공하면 `Finished(id)`, 또 실패하면 다시 `SaveFailed`. 들고 있는 동안 버퍼는 지우지 않고(프로세스가 사는 동안 기록을 잃지 않는다) `saveFailed = true`다. Finished에서 부르는 버리기 API는 두지 않는다(9.8).
  - `Discarded`의 `deleteSession` 실패: 로그만 남긴다. phase는 이미 Idle이다. 남은 `in_progress` 행은 다음 실행에서 D2 복구 대상으로 보인다(데이터를 잃는 쪽이 아니다).
- 임시 저장: Running/Paused 동안 10 s마다 `brews.saveDraft()`(status `in_progress`). `SessionDraft.samplesBlob`은 메인에서 `samples.toBlob()`으로 만든 뒤 넘긴다(3절). 앱이 죽으면 다음 실행에서 D2로 복구한다.
- S6 저장·버리기는 `SummaryViewModel`이 저장소로 하고, 실시간 세션이면 `newBrew()`를 부른다.

### 8.3 Room (스키마 버전 1)

```kotlin
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val doseGrams: Double,
    val waterGrams: Double,
    val stepsJson: String,          // List<RecipeStep> kotlinx-serialization JSON. 단계별 밸브 상태(RecipeStep.valveOpen)도 여기에 들어간다
    val finishAtSec: Int?,          // Recipe.finishAtSec (null = 마지막 단계 목표 초). 스키마 버전 1에 포함(아직 출시 전)
    val createdAtEpochMs: Long,     // (2026-10-29 valveJson 열은 2026-10-30 단계 모델로 뺐다. 아래)
)

@Entity(tableName = "brew_sessions", indices = [Index("startedAtEpochMs"), Index("status")])
data class BrewSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val startedAtEpochMs: Long,
    val recipeId: Long?,            // 외래 키 아님: 레시피를 지워도 기록은 남는다
    val recipeName: String?,        // 저장 당시 이름 (목록·CSV)
    val recipeJson: String?,        // 저장 당시 Recipe 전체 (목표선·단계 표)
    val doseGrams: Double?,         // S6에서 입력 (레시피 값으로 미리 채움)
    val finalWeightGrams: Double?,
    val durationMs: Long,
    val status: String,             // "in_progress" | "completed" | "interrupted"
    val note: String,               // 최대 200자
)

@Entity(tableName = "brew_samples",
    foreignKeys = [ForeignKey(BrewSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)])
data class BrewSamplesEntity(@PrimaryKey val sessionId: Long, val blob: ByteArray)   // SampleBuffer.toBlob()
```

- 단계는 레시피 안에서만 쓰고 따로 조회하지 않아 JSON 열로 둔다(관계 테이블·`@Relation`을 만들지 않는다).
- 스키마 버전 1 유지(`finishAtSec`): 앱이 아직 출시 전이라 버전을 올리지 않고 열을 버전 1에 넣은 뒤 `app/schemas/…/1.json`을 다시 만든다. `Migration`을 더하지 않는다. 그 대가로 이전 빌드의 DB가 있는 개발 기기는 Room 무결성 검사로 열리지 않으므로 앱 데이터를 지우고 설치한다. 출시 뒤에는 이 방식을 쓰지 않는다(버전 올림 + `Migration`).
- 밸브는 열을 더하지 않는다(사용자 결정 2026-10-30, 단계 모델): 밸브 상태가 `RecipeStep.valveOpen`이라 `stepsJson`(레시피)과 `recipeJson`(기록) 안에 들어간다. 2026-10-29 설계의 `valveJson` 열은 필요 없어 뺀다. 중단된 FIX-4 코드가 `Entities.kt`에 그 열을 넣었지만 `1.json`은 아직 다시 만들지 않아 지금 파일(identityHash `6d9f61fa4f808273048039f7ba96e8a3`, `recipes`에 `finishAtSec`까지)이 이 계약과 같다. 열을 빼고 다시 만든 `1.json`은 지금 파일과 같아야 한다. 중단된 코드로 DB를 만든 개발 기기가 있으면 위와 같이 앱 데이터를 지운다.
- 옛 JSON 호환: `valveOpen`이 없는 옛 `stepsJson`·`recipeJson`은 기본값 null로 읽혀 밸브 없는 레시피가 된다. 중단된 FIX-4 코드가 쓴 `"valveSchedule"` 키는 `DataJson`의 `ignoreUnknownKeys = true`로 무시된다(그 일정은 버려진다, 출시 전 개발 데이터만 해당).
- 기록의 밸브 일정: `brew_sessions.recipeJson`은 저장 당시 `Recipe` 전체(`SessionDraft.recipe`)라 단계의 `valveOpen`이 함께 저장된다. 새 열·테이블은 두지 않는다. S10 일정 줄(ui-design 3.7, `history_detail_valve`)은 `SessionSummary.recipe?.valveSchedule`(파생, 7.1)만 읽는다. 레시피를 나중에 고치거나 지워도 기록의 일정은 저장 당시 단계 값이다.
- `room { schemaDirectory("$projectDir/schemas") }`. 스키마 버전을 올릴 때마다 `Migration`과 `MigrationTestHelper` 테스트를 더한다. `fallbackToDestructiveMigration`은 쓰지 않는다.
- 비율은 저장하지 않고 `finalWeightGrams / doseGrams`로 계산한다.
- 기본 레시피(사용자 결정 2026-10-28): `AppDatabase`를 만드는 빌더 함수가 항상 `RoomDatabase.Callback.onCreate`를 단다. 이 콜백이 아래 `DefaultRecipes.all`을 `recipes` 테이블에 넣는다(`stepsJson`은 저장소와 같은 `Json`으로, `createdAtEpochMs`는 생성 시각). `onCreate`는 DB 파일이 처음 생길 때 한 번만 불린다. 그래서 사용자가 지운 기본 레시피는 다시 생기지 않고, 앱 업데이트·마이그레이션도 레시피를 더하지 않는다. 테스트도 같은 빌더를 써서 시드 경로를 함께 검증한다(메모리 DB도 `onCreate`가 불린다). 이름은 사용자 데이터라 문자열 리소스로 번역하지 않는다.
- 시드 값 변경(2026-11-02 P2, 사용자 확정 2026-11-03): V60 기본·6888에 기다리기 단계를 넣었다(아래 표). 코드에서 시드는 `app/src/main/java/com/timemoredot/app/data/AppDatabase.kt`의 `buildAppDatabase`가 다는 `RoomDatabase.Callback.onCreate` → `seedDefaultRecipes`(`DefaultRecipes.all`을 `recipes`에 삽입)뿐이고, `onCreate`는 `timemore.db` 파일이 처음 생길 때 한 번만 불린다. 스키마는 바뀌지 않으므로 `AppDatabase` version 1, `1.json` identityHash `6d9f61fa4f808273048039f7ba96e8a3` 그대로이고 `Migration`을 더하지 않는다(앱 미배포라 설치된 사용자 DB가 없다). 대가: 이미 DB가 있는 개발 기기·에뮬레이터는 옛 V60(2단계)·6888(4단계)을 그대로 갖는다. 새 시드를 보려면 앱 데이터를 지운다(`adb shell pm clear com.timemoredot.app`, 또는 앱 정보 → 저장공간 → 데이터 삭제). 지우면 기록·레시피·저장 기기 주소도 함께 지워진다. debug 데모 모드는 따로 `timemore_demo.db`를 쓰므로 처음 데모를 열 때 새 시드로 만든다(10.1). 배포 뒤에 시드를 바꿀 때는 이 방식을 쓰지 않는다(이미 있는 행을 고치는 `Migration`이 필요).

```kotlin
// :app data/DefaultRecipes.kt — id = 0, 마지막 단계 targetGrams == waterGrams, finishAtSec는 아래 끝 시각 표, 단계 valveOpen은 아래 밸브 표(밸브 없는 레시피는 모두 null)
object DefaultRecipes { val all: List<Recipe> }
```

기본 레시피 5개(사용자 지정 목록 2026-10-28: "V60 기본, 하리오 스위치 기본, 6888 레시피, 테츠 카스야 기본레시피, 테츠 카스야 악마의 레시피"). 값은 아래 출처에서 옮겼다(조사일 2026-10-28).

- 모델에 맞춘 규칙: `Recipe`에는 온도·분쇄도 필드가 없다. 물 온도·분쇄도는 첫 단계 이름에, 주전자 식히기·드리퍼 내리기처럼 무게가 늘지 않는 동작은 그 직전 붓기 단계 이름에 시각과 함께 적는다. 밸브 열고 닫기는 2026-10-30부터 단계 경계에 두고(7.1 밸브 단계 모델) 단계의 `valveOpen`(아래 밸브 표)에 넣는다. 출처의 밸브 동작 시각이 붓기 단계의 `targetAtSec`과 다르면 그 시각까지의 기다리기 단계를 넣는다. 엔진이 기다리기 단계를 건너뛰므로(7.2) 그 동작과 시각은 다음 단계 이름에도 적는다. 단계 이름의 스위치 문구는 S1 밸브 칸 안내와 겹쳐도 단계 카드 설명으로 남긴다(사용자 결정 2026-10-29). 물 온도·분쇄도 열은 참고용이라 `DefaultRecipes`에 넣지 않는다.
- `targetAtSec`은 그 누적 무게에 닿을 목표 시각이다. 출처는 대개 붓기 *시작* 시각만 주므로 `targetAtSec = 출처의 붓기 시작 초 + 붓기 시간`으로 바꾼다. 붓기 시간은 출처에 있으면 그 값(테츠 카스야 기본: 5 s), 없으면 앱 가정 10 s다(아래 표 `*` 표시). 단계 이름에는 출처의 시작 시각을 적어 사용자가 언제 붓기 시작할지 알게 한다.
- 부피로만 적힌 출처(하리오 스위치 설명서 "약 240ml")는 1 ml = 1 g으로 옮긴다(뜨거운 물 밀도 차 약 4%는 무시, 출처 자체가 "약").
- 출처에 없던 값(6888 전체, V60 기본 마지막 단계 목표 초)은 사용자 결정(2026-10-28)으로 채웠고 출처 열에 그렇게 적었다.
- 뜸 기다림(2026-11-02 P2): V60 기본·6888은 뜸 붓기(목표 0:10) 뒤 0:40까지 기다렸다가 다음 붓기를 시작한다. 기다림을 단계로 두지 않으면 목표선이 0:10부터 바로 올라 레시피대로 기다려도 "늦음"이 뜬다(M8 리뷰). 그래서 1단계 다음에 기다리기 단계 `("0:40까지 기다리기", 60, 40)`을 넣었다(7.1 `isWaitStep`: index 1, 누적 g = 앞 단계 60). 목표선은 (0:10, 60 g)~(0:40, 60 g)에서 평평하고, 세 인자 `plannedSecFor`가 그 구간에서 `paceDeltaSec` 0을 준다(7.1). 엔진은 60 g에 닿으면 기다리기 단계를 함께 닿은 것으로 보고 건너뛴다(7.2). 기다림 뒤 동작과 시각 "0:40 …"은 이미 다음 단계 이름에 있다.

| 이름 | 원두 g | 물 g | 물 온도 (참고) | 분쇄도 (참고) | 단계 (이름, 누적 g, 목표 초) | 출처 |
|------|--------|------|----------------|---------------|------------------------------|------|
| V60 기본 | 15.0 | 250.0 | 95°C | 중간-가는 분쇄(medium-fine) | ("뜸 60 g, 30초 기다리기 (95°C, 중간-가는 분쇄)", 60, 10\*), ("0:40까지 기다리기", 60, 40), ("0:40 나머지 물을 작은 원으로 천천히 250 g까지", 250, 90) | HARIO UK Basic V60 brew guide <https://www.hario.co.uk/pages/brew-guides-v60-basic> (원문은 HTTP 429로 못 열어 미러 <https://honestcoffeeguide.com/brew-recipes/basic-hario-v60-recipe/>로 확인). 2차 붓기 끝 시각은 출처에 없다("slowly pour the rest"). 90 s(1:30)는 사용자 결정 2026-10-28. 0:40 = 뜸 붓기 10 s\* + 기다림 30 s |
| 하리오 스위치 기본 | 20.0 | 240.0 | 출처 표기 없음(열탕) | 중간-가는 분쇄(中細挽き) | ("스위치 닫고(올림) 240 g 한 번에 붓기 (중간-가는 분쇄)", 240, 10\*), ("2:00까지 담가 두기", 240, 120), ("2:00 스위치 눌러 열고 내려받기", 240, 150†) | HARIO 浸漬式ドリッパー スイッチ 取扱説明書(SSD-200) <https://www.hario.com/manual_pdf/SSD.pdf>: "コーヒー粉（中細挽き）20ｇ…お湯をいっぱいまで注ぎます。（約 240ml）２分程度、浸漬します" |
| 6888 레시피 | 20.0 | 300.0 | 92°C | 중간 분쇄 | ("뜸 60 g, 0:40까지 기다리기 (92°C, 중간 분쇄)", 60, 10\*), ("0:40까지 기다리기", 60, 40), ("0:40 2차 140 g까지", 140, 50\*), ("1:10 3차 220 g까지", 220, 80\*), ("1:40 4차 300 g까지 · 2:45 전후 추출 끝", 300, 110\*) | 출처 없음, 이름에서 추정(붓기 60/80/80/80 g), 사용자 승인 2026-10-28. 웹 검색에서 같은 이름의 레시피를 찾지 못했다 |
| 테츠 카스야 기본 레시피 | 20.0 | 300.0 | 92°C | 굵은 분쇄(coarse) | ("1차 50 g (92°C, 굵은 분쇄)", 50, 5), ("0:45 2차 120 g까지", 120, 50), ("1:30 3차 180 g까지", 180, 95), ("2:15 4차 240 g까지", 240, 140), ("3:00 5차 300 g까지 · 3:30에 드리퍼 내리기", 300, 185) | 4:6 메소드(2016 WBrC). 붓기 시각·양·5 s 붓기: Barista Magazine <https://www.baristamagazine.com/standing-shoulders-brewing-giant/> (0:00 50 / 0:45 70 / 1:30 60 / 2:15 60 / 3:00 60 g, 3:30 내리기, 198°F). 20 g·300 ml·92°C·coarse·3:30: HARIO Europe <https://www.hario-europe.com/blogs/hario-community/v60-ambassadors-tetsu-kasuya>(검색 발췌로 확인). HARIO UK 인터뷰는 4·5차를 2:10·2:40으로 적는다(원문은 HTTP 429로 못 열어 검색 발췌로만 확인, 이 표는 시각 다섯 개가 모두 있는 Barista Magazine을 따른다) |
| 테츠 카스야 악마의 레시피 | 20.0 | 280.0 | 90°C → 70°C(침지) | 약간 가는 분쇄(relatively fine) | ("스위치 열고 뜸 60 g (90°C, 약간 가는 분쇄)", 60, 10\*), ("0:30 120 g까지 · 이어서 찬물을 섞어 주전자를 70°C로", 120, 40\*), ("1:15까지 기다리기", 120, 75), ("1:15 스위치 닫고 70°C 물로 280 g까지", 280, 85\*), ("1:45까지 담가 두기", 280, 105), ("1:45 스위치 열기 · 3:00 추출 끝", 280, 180) | 테츠 카스야 YouTube "Is it the God Recipe, or the Devil Recipe?" <https://www.youtube.com/watch?v=gC8K40kZ_6E> (영상은 직접 보지 못함). 단계는 영상 요약 두 곳이 일치: <https://klaire.codes/coffee/coffee/tetsu-kasuya-hario-switch-god-devil/>, <https://comoricoffee.com/en/kasuyas-hario-switch-recipe-en/> |

\* 앱 가정 붓기 시간 10 s(출처에 없음).
† 앱 가정 내려받기 30 s(출처에 없음. 하리오 설명서는 "2분 정도 담근 뒤 스위치를 누른다"까지만 적는다). 기다리기 단계 뒤 "열고 내려받기" 단계가 있어야 2:00에 밸브가 바뀌고, 그 단계의 `targetAtSec`은 120보다 커야 한다(엄격 오름차순). 150(내려받기 30 s, 끝 2:30)은 사용자 확정(2026-10-30).

기본 레시피의 추출 끝 시각(`finishAtSec`, 사용자 결정 2026-10-29). 자동 완료는 이 시각 뒤에만 판정한다(7.2). 값은 위 표 단계 이름에 적힌 끝 시각이다.

| 이름 | `finishAtSec` | 근거(단계 이름) |
|------|---------------|-----------------|
| V60 기본 | null(= 마지막 단계 90 s) | 끝 시각이 적혀 있지 않다 |
| 하리오 스위치 기본 | null(= 마지막 단계 150 s†) | 2026-10-29에는 120("열기 뒤에 끝")이었다. 마지막 단계 "2:00 스위치 눌러 열고 내려받기"가 150 s라 `finishAtSec` ≥ 150이어야 해서 마지막 단계 시각을 끝으로 쓴다. 가장 이른 자동 완료가 125 s → 155 s로 바뀐다 |
| 6888 레시피 | 165 | "2:45 전후 추출 끝" |
| 테츠 카스야 기본 레시피 | 210 | "3:30에 드리퍼 내리기" |
| 테츠 카스야 악마의 레시피 | 180 | "3:00 추출 끝"(= 마지막 단계 시각, 값 그대로 둔다) |

기본 레시피의 밸브(단계 `valveOpen`, 사용자 결정 2026-10-30. 출처의 동작 시각: 하리오 0:00 닫고 붓기 → 2:00 열기, 악마 0:00 열고 뜸 60 g → 0:30 120 g → 1:15 닫고 70°C로 280 g → 1:45 열기 → 3:00 끝). 표에 없는 3개(V60 기본, 6888, 테츠 카스야 기본)는 모든 단계 null(밸브 없음). 단계 시작 = 앞 단계 `targetAtSec`(7.1). "기다리기"는 7.1 `isWaitStep`.

| 이름 | 단계 (번호, 이름, 누적 g, `targetAtSec`, 단계 시작, `valveOpen`) | `finishAtSec` | 파생 `valveSchedule` (atSec, 상태) |
|------|-------------------------------------------------------------------|---------------|-----------------------------------|
| 하리오 스위치 기본 | 1 "스위치 닫고(올림) 240 g 한 번에 붓기 (중간-가는 분쇄)" 240 g, 10, 0 s, 닫힘 · 2 "2:00까지 담가 두기" 240 g, 120, 10 s, 닫힘(기다리기) · 3 "2:00 스위치 눌러 열고 내려받기" 240 g, 150†, 120 s, 열림(기다리기) | null(= 150) | (0, 닫힘), (120, 열림) |
| 테츠 카스야 악마의 레시피 | 1 "스위치 열고 뜸 60 g (90°C, 약간 가는 분쇄)" 60 g, 10, 0 s, 열림 · 2 "0:30 120 g까지 · 이어서 찬물을 섞어 주전자를 70°C로" 120 g, 40, 10 s, 열림 · 3 "1:15까지 기다리기" 120 g, 75, 40 s, 열림(기다리기) · 4 "1:15 스위치 닫고 70°C 물로 280 g까지" 280 g, 85, 75 s, 닫힘 · 5 "1:45까지 담가 두기" 280 g, 105, 85 s, 닫힘(기다리기) · 6 "1:45 스위치 열기 · 3:00 추출 끝" 280 g, 180, 105 s, 열림(기다리기) | 180 | (0, 열림), (75, 닫힘), (105, 열림) |

- 파생 일정은 2026-10-29 밸브 일정 표 값과 같다(그래서 M8·M10·M11의 밸브 시각 조건 120 s·75 s·105 s는 그대로다).
- S1 단계 카드에서 보이는 순서(엔진 무게 판정, 7.2): 하리오 1 → (240 g에 닿으면 2·3을 함께 닿아) 3 이름 + "기다리는 중". 악마 1 → 2 → (120 g에 닿으면 3을 건너뛰어) 4 → (280 g에 닿으면 5를 건너뛰어) 6 이름 + "기다리는 중". 건너뛰는 단계 3·5와 하리오 2의 동작·시각은 다음 단계 이름("1:15 …", "1:45 …", "2:00 …")에 있다. P2(2026-11-02) 뒤 V60 기본 1 → 3(S1 "1/3 단계" → "3/3 단계", 리본 표식 2개: 60·250 g), 6888 1 → 3 → 4 → 5("1/5" → "3/5" → "4/5" → "5/5", 표식 4개). 둘 다 1단계 카드 5줄이 "다음: 0:40까지 기다리기"다(ui-design 4.5·5.6).
- P2 확인 값(DefaultRecipesTest·FIX-6): V60 기본 단계 3개, targetAtSec 10·40·90(엄격 오름차순), `isWaitStep(1)` true, `finishSec` 90, `valveSchedule` 빈 목록. 6888 단계 5개, targetAtSec 10·40·50·80·110, `isWaitStep(1)` true, `finishAtSec` 165. 세 인자 `plannedSecFor`(tolerance 1.0): V60 (60.3 g, 25 s) → 25.0, (60.0 g, 45 s) → 40.0, (60.0 g, 5 s) → 10.0. 6888 (60.2 g, 30 s) → 30.0.
- P2 범위 밖 같은 모양(사용자 최종 검토 항목, 바꾸지 않음): 출처의 다음 붓기 시작이 앞 단계 `targetAtSec`보다 늦은 곳이 더 있다. 6888 2차 끝 0:50 → 3차 시작 1:10, 3차 끝 1:20 → 4차 시작 1:40, 테츠 카스야 기본 1차 끝 0:05 → 2차 시작 0:45(그 뒤 4번도 같은 모양). 이 구간에서 레시피대로 기다리면 "늦음"이 최대 약 20 s(6888)·40 s(테츠 카스야 기본) 뜬다. 같은 방식(기다리기 단계)으로 고칠 수 있지만 P2 결정 범위가 V60·6888 뜸뿐이라 넣지 않았다. 넣으면 단계 수가 6888 7개, 테츠 카스야 기본 9개가 되고 ui-design 5.6·S7 행 문구를 함께 고친다.
- 2026-10-29 판에서 하리오 1단계 이름 끝의 "· 2:00에 스위치 눌러 열기"는 3단계로, 악마 3단계 이름 끝의 "· 1:45 스위치 열기 · 3:00 추출 끝"은 6단계로 옮겼다. 그래서 기본 단계 이름 최대 길이가 53자에서 36자(악마 2 "0:30 120 g까지 · 이어서 찬물을 섞어 주전자를 70°C로")로 줄었다.

- `interrupted`: D2에서 복구한 초안을 S6에서 저장하면 이 상태가 된다.

```kotlin
enum class SessionStatus { InProgress, Completed, Interrupted }

data class SessionSummary(
    val id: Long, val startedAtEpochMs: Long, val recipeName: String?, val recipe: Recipe?, val doseGrams: Double?,
    val finalWeightGrams: Double?, val durationMs: Long, val status: SessionStatus, val note: String,
) { val ratio: Double? get() = if (doseGrams != null && doseGrams > 0 && finalWeightGrams != null) finalWeightGrams / doseGrams else null }

data class SessionDraft(
    val id: Long?,                  // null = 새 초안
    val startedAtEpochMs: Long, val recipe: Recipe?, val finalWeightGrams: Double?, val durationMs: Long,
    val samplesBlob: ByteArray,     // 메인에서 만든 SampleBuffer.toBlob() 복사본. 가변 버퍼를 넘기지 않는다(3절)
)

/** ViewModel·컨트롤러는 interface만 받는다. 구현은 RoomRecipeRepository(dao), RoomBrewRepository(database, dao). 가짜는 :app test(8.1). */
interface RecipeRepository {
    fun observeAll(): Flow<List<Recipe>>                       // 이름 순
    suspend fun get(id: Long): Recipe?
    suspend fun upsert(recipe: Recipe): Long
    suspend fun delete(id: Long)
}

interface BrewRepository {
    fun observeSessions(): Flow<List<SessionSummary>>          // in_progress 제외, 최근 시작 순
    suspend fun summary(id: Long): SessionSummary?
    suspend fun samples(id: Long): SampleBuffer?               // 없거나 BLOB 손상이면 null
    suspend fun saveDraft(draft: SessionDraft): Long           // status in_progress, 세션·샘플 한 트랜잭션. id null이면 새 행. id가 있으면 status='in_progress'인 그 행만 갱신하고,
                                                               // 행이 없거나(삭제됨) completed·interrupted면 아무것도 바꾸지 않고 같은 id를 돌려준다(늦은 주기 저장이 행을 되살리거나 dose·note를 지우지 않음, 8.2). M8에서 구현(M6 동작 변경)
    suspend fun findDraft(excludingId: Long?): SessionSummary? // D2: 실시간 세션이 아닌 in_progress 하나(가장 최근)
    suspend fun finish(id: Long, doseGrams: Double?, note: String, status: SessionStatus): Unit   // S6 저장
    suspend fun deleteSession(id: Long)                        // 샘플도 CASCADE로 삭제
    suspend fun updateNote(id: Long, note: String)
}
```

### 8.4 CSV 내보내기

저장소 접근 프레임워크(`ActivityResultContracts.CreateDocument("text/csv")`)로 사용자가 고른 `Uri`에 쓴다. 저장소 권한을 요청하지 않는다. 쓰기는 순수 함수로 두고 JVM 테스트한다. 파일 선택 취소(`Uri` null)는 아무 표시도 하지 않는다. 결과 콜백으로 받은 `Uri`에 한 번만 쓰고 `takePersistableUriPermission`을 하지 않는다. `FileProvider`·공유 Intent·`PendingIntent`를 쓰지 않는다(`android-intent-security`). `openOutputStream`과 쓰기는 `Dispatchers.IO`에서 한다(3절).

```kotlin
object CsvExport {
    fun writeSessions(sessions: List<SessionSummary>, zone: ZoneId, out: Appendable): Int   // 쓴 행 수 (Exported(count))
    fun writeSamples(samples: SampleBuffer, out: Appendable)
}

/** 사용자가 고른 내보내기 대상 하나. ViewModel은 이것만 받는다(Uri·ContentResolver가 VM 경계에 들어오지 않아 VM 테스트가 JVM만으로 된다, 10절).
 *  실제 구현은 AppContainer.csvTarget(uri)가 만든다: Dispatchers.IO로 옮겨 contentResolver.openOutputStream(uri)를 UTF-8 Writer로 열고 block에 넘긴 뒤 닫는다.
 *  열기·쓰기 실패는 예외로 던진다(VM이 ExportFailed로 바꾼다). 테스트는 StringBuilder에 쓰거나 예외를 던지는 람다를 넘긴다. */
fun interface CsvTarget { suspend fun write(block: (Appendable) -> Unit) }
```

공통 형식: UTF-8, 파일 맨 앞 BOM(`EF BB BF`, Excel 한글 표시), 줄 끝 CRLF, RFC 4180 따옴표 처리(쉼표·따옴표·줄바꿈이 있으면 `"`로 감싸고 `"`는 `""`), 소수점 `.`(`Locale.ROOT`), 빈 값은 빈 칸. 단위는 표시 단위와 상관없이 항상 g·g/s·s.

기록 목록 CSV(S9, 기본 파일 이름 `timemore-brews-YYYYMMDD.csv`), 헤더 고정:

```
session_id,started_at,recipe_name,dose_g,final_weight_g,duration_s,ratio,status,note
```

| 열 | 형식 |
|----|------|
| session_id | 정수 |
| started_at | ISO-8601 지역 시각+오프셋, 초 단위 (`2026-10-28T09:15:30+09:00`) |
| recipe_name | 문자열, 자유 추출이면 빈 칸 |
| dose_g | 소수 1자리, 없으면 빈 칸 |
| final_weight_g | 소수 1자리, 없으면 빈 칸 |
| duration_s | 소수 1자리 |
| ratio | 소수 2자리(`final/dose`), 계산 불가면 빈 칸 |
| status | `completed` / `interrupted` (`in_progress`는 내보내지 않는다) |
| note | 문자열 |

기록 하나의 샘플 CSV(S10, 기본 파일 이름 `timemore-brew-YYYYMMDD-HHmm.csv`), 헤더 고정:

```
t_s,weight_g,flow_g_per_s,overload,disconnected
```

| 열 | 형식 |
|----|------|
| t_s | `offsetMs / 1000`, 소수 3자리 |
| weight_g | 소수 1자리, 과부하(NaN)면 빈 칸 |
| flow_g_per_s | 소수 1자리 |
| overload | `0`/`1` (`FLAG_OVERLOAD`) |
| disconnected | `0`/`1` (`FLAG_DISCONNECTED`) |

## 9. 화면·상태·ViewModel 계약

모든 ViewModel은 `StateFlow<XxxUiState>`를 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 초기값)`으로 내보내고, 화면은 `collectAsStateWithLifecycle()`로 수집한다. 일회성 결과(스낵바, 화면 이동)는 `events: Flow<…>`(Channel)로 낸다.

### 9.1 화면 목록과 내비게이션

Navigation Compose 2.10 타입 안전 경로(`@Serializable`). 시작 경로 `Brew`. 최상위 탭 3개(추출·레시피·기록)는 `NavHost`를 감싼 `NavigationSuiteScaffold`(material3-adaptive-navigation-suite, ui-design 1절)가 그린다.
- 탭 모양: 창 너비 ≥ 600 dp면 `NavigationSuiteType.NavigationRail`, 아니면 `NavigationSuiteType.NavigationBar`. 너비만 본다(기본 `calculateFromAdaptiveInfo`는 쓰지 않는다. 가로 휴대폰에서 아래 탭을 골라 높이를 잃는다). 판정 함수 `tabTypeFor(windowWidthDp: Int): NavigationSuiteType`을 순수 함수로 둔다.
- 탭 보임: 현재 목적지가 `Recipes`·`History`이거나, `Brew`이고 `BrewUiState.showBottomBar`가 true일 때만 보인다. 그 밖(S2·S6·S8·S10, S1 Running·Paused)에는 숨긴다. `rememberNavigationSuiteScaffoldState()`를 `state`로 넘기고, 보임 조건이 바뀔 때 `LaunchedEffect(visible) { state.snapTo(if (visible) Visible else Hidden) }`로 애니메이션 없이 바꾼다(`snapTo`는 navigation-suite 1.4.0 API에 있다, 2.2절). 보임 판정은 순수 함수 `tabsVisible(route, showBottomBar): Boolean`으로 둔다.

| ui-design ID | 경로 / 종류 | ViewModel | 마일스톤 |
|--------------|-------------|-----------|----------|
| S1 | `Brew` | `BrewViewModel` | M8 |
| S2 | `Connect` | `ConnectViewModel` | M7 |
| S3 저울 정보 시트 | S1 안의 `ModalBottomSheet` | `BrewViewModel`(연결 정보. 단위 줄은 `readout.reading`, 9.4 "S3 단위 줄") | M7(컴포저블), M8(S1 연결) |
| S4 추출 설정 시트 | S1 안의 시트 | `BrewViewModel` | M8 |
| S5 레시피 선택 시트 | S1 안의 시트 | `BrewViewModel` | M8 |
| S6 | `Summary(sessionId: Long, recovered: Boolean)` | `SummaryViewModel` | M9 |
| S7 | `Recipes` | `RecipeListViewModel` | M10 |
| S8 | `RecipeEdit(id: Long?)` | `RecipeEditViewModel` | M10 |
| S9 | `History` | `HistoryViewModel` | M11 |
| S10 | `SessionDetail(id: Long)` | `SessionDetailViewModel` | M11 |
| D1 추출 중단 확인 | S1 대화상자 (`NavigationBackHandler`, `isBackEnabled` = phase Running·Paused) | 표시: `BrewScreen` 로컬 상태(`rememberSaveable`). 선택: `BrewViewModel.onDiscardClick` | M8 |
| D2 미저장 추출 복구 | S1 대화상자 | `BrewViewModel`(`recoverableDraft`) | M9 |
| D3 삭제 확인 | S6(버리기)·S8·S10 대화상자 | 표시: 각 화면 로컬 상태(`rememberSaveable`). 선택: 각 화면 VM의 `onDiscardConfirmed`·`onDeleteConfirmed` | M9·M10·M11 |
| D4 편집 취소 확인 | S8 대화상자 | 표시: `RecipeEditScreen` 로컬 상태(`rememberSaveable`). 조건: `RecipeEditUiState.hasUnsavedChanges` | M10 |
| D5 초기화 확인 | S1 대화상자 (Paused "초기화", 샘플 있을 때) | `BrewViewModel`(`showResetConfirm`) | M8 |
| D6 요약 떠나기 확인("이 추출을 저장할까요?") | S6 대화상자 (`NavigationBackHandler`, `isBackEnabled` = 항상) | `SummaryScreen` 로컬 상태(`rememberSaveable`). 선택은 `SummaryViewModel.onSave`·D3 | M9 |

M7은 모든 경로를 내비게이션 그래프에 넣고, 뒤 마일스톤 화면은 빈 자리 컴포저블로 둔다. 뒤 마일스톤이 그 자리를 채운다.

앱 셸(M7, `edge-to-edge`·`android-permissions-security`): targetSdk 37이라 edge-to-edge가 강제된다.
- `MainActivity.onCreate`는 첫 줄(`super.onCreate` 앞, `container` 접근 전)에서 `VariantHooks.onActivityCreate(this)`를 부른다(10.1. release는 아무것도 하지 않는다).
- `MainActivity`(`ComponentActivity`)는 `setContent` 전에 `enableEdgeToEdge()`(ComponentActivity 확장)를 부른다. 시스템 바 아이콘 색은 이 함수가 맞추므로 `WindowCompat` 인셋 컨트롤러로 따로 바꾸지 않는다. 하단 탭(`NavigationBar`)이 있으므로 API 29 이상에서 `window.isNavigationBarContrastEnforced = false`.
- 매니페스트의 `MainActivity`는 `android:windowSoftInputMode="adjustResize"`, `android:exported="true"`(런처 intent-filter만). `<application>`에 `android:enableOnBackInvokedCallback="true"`(API 33~35 예측 뒤로 가기. API 36+는 기본값이고 `false`로 두지 않는다). 앱에 다른 activity·service·receiver·provider를 선언하지 않는다. 받은 `Intent`의 extra를 읽거나 다른 Intent로 넘기지 않는다(딥 링크 없음). debug 예외 하나(10.1): `app/src/debug/AndroidManifest.xml`의 런처 별칭 `DemoLauncher`(`activity-alias`, `targetActivity = .MainActivity`). 데모 여부는 extra가 아니라 시작 컴포넌트 이름으로만 정한다. release 병합 매니페스트에는 없다.
- 인셋은 화면마다 한 방법만 쓴다. `NavigationSuiteScaffold`는 내용에 `PaddingValues`를 넘기지 않으므로 각 화면의 `Scaffold`가 받는다. 탭이 보이는 동안은 탭 쪽 면(아래 탭이면 `WindowInsetsSides.Bottom`, 레일이면 `Start`)을 뺀 `WindowInsets.safeDrawing.only(...)`를 `contentWindowInsets`로 주고, 탭이 없으면 `contentWindowInsets = WindowInsets.safeDrawing`(IME 포함)이다(ui-design 3절, 이중 여백 금지). `Scaffold`의 `innerPadding`을 내용에 주고 `consumeWindowInsets(innerPadding)`를 붙인다. 목록은 `contentPadding`으로 받는다. `NavigationSuiteScaffold` 부모에 `safeDrawingPadding()`을 걸지 않는다. 입력 필드 화면(S6, S8, S10)도 같은 `contentWindowInsets = WindowInsets.safeDrawing`으로 키보드를 피하고 `imePadding()`을 쓰지 않는다(`safeDrawing`에 IME가 이미 들어 있어 이중 여백, 공식 `edge-to-edge` WRONG 예). `Scaffold(bottomBar = …)`에 넣은 고정 버튼 줄(S1 동작 줄, S2·S6 고정 버튼 줄)은 `windowInsetsPadding(WindowInsets.safeDrawing.only(Horizontal + Bottom))`로 자기 인셋을 받아 키보드 위로 올라온다. 배치·여백·인셋 값은 ui-design.md 3절.
- 뒤로 가기(`navigation-event`, ui-design 2.6): 가로채는 화면마다 `NavigationBackHandler`(`androidx.navigationevent:navigationevent-compose`) 하나와 `rememberNavigationEventState` 하나를 둔다. 조건은 `isBackEnabled`로만 준다: S1 = phase Running·Paused(→ D1), S6 = 항상 `true`(→ D6), S8 = `hasUnsavedChanges || state == Saving`(변경 있음 → D4, 저장 중에는 뒤로 가기를 삼키고 D4 없음. P7 2026-11-02, 하네스 추천(잠정, 사용자 최종 검토 대기)). S2·S7·S9·S10은 핸들러를 두지 않는다. 대화상자는 `onBackCompleted`에서 띄우고 취소(`onBackCancelled`)에는 아무것도 하지 않는다. 같은 화면에 핸들러를 둘 두거나 `isBackEnabled = true`로 두고 안에서 분기하지 않는다. `androidx.activity.compose.BackHandler`를 새로 쓰지 않는다. 시트·대화상자는 자기 창이 뒤로 가기를 처리하므로 핸들러를 따로 두지 않는다.
- 권한 요청 흐름(`android-permissions-security` 6절)은 확인 → `shouldShowRequestPermissionRationale` → `RequestMultiplePermissions` 순서다. 영구 거부면 다시 요청하지 않고 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`(`package:` Uri)로 보낸다. 거부 뒤 자동 재요청 반복은 하지 않는다. 시스템 설정으로 보내는 Intent(앱 정보, 블루투스 켜기 `BluetoothAdapter.ACTION_REQUEST_ENABLE`, 위치 설정 `Settings.ACTION_LOCATION_SOURCE_SETTINGS`)는 UI 계층에서만 만든다.

TalkBack 상태 알림 노드(ui-design 8.3)는 앱 루트에 하나만 둔다. 화면 안에 두면 자동 완료처럼 알림 직후 S6으로 넘어갈 때 노드가 사라져 알림이 읽히지 않는다. UI 계층 객체이고 ViewModel은 모른다(M7).

```kotlin
// :app ui/StatusAnnouncer.kt
fun interface Announcer { fun announce(text: String, assertive: Boolean) }
val LocalAnnouncer = staticCompositionLocalOf<Announcer> { error("StatusAnnouncer 밖에서 쓰였다") }
/** 앱 루트(MainActivity setContent 바로 안)에서 NavHost를 content로 감싼다. 최상위 Box에 content와 알림 노드
 *  (liveRegion Polite·Assertive 두 개, 1 dp)를 두고 LocalAnnouncer를 제공한다. content(NavHost)를 감싼 Box와 알림 노드 묶음이 둘 다
 *  isTraversalGroup이고, 알림 묶음은 최상위 Box의 마지막 자식(align(BottomEnd))이다. 알림 노드는 문구가 비어 있지 않을 때만 contentDescription을 가진다.
 *  앱 시작·화면 전환의 첫 포커스 후보가 아니다. 규칙은 아래 목록(ui-design 8.3 1번·8.5가 기준). */
@Composable fun StatusAnnouncer(content: @Composable () -> Unit)
```

- 앱 안에서 `liveRegion`을 거는 곳은 이 파일뿐이다. 화면 스크린샷·UI 테스트는 기록용 가짜 `Announcer`를 `LocalAnnouncer`로 넣는다.
- 같은 문구 다시 읽기: liveRegion은 노드 텍스트가 바뀔 때만 읽힌다. 그래서 `announce`마다 해당 노드의 텍스트를 빈 문자열로 비운 뒤 다음 프레임에 새 문구를 넣는다(또는 알림마다 늘어나는 id를 `key`로 노드를 새로 만든다. 구현자가 하나를 고른다). "영점 맞춤"(`TareDone`)을 두 번 연속 내도 두 번 읽힌다. 호출자(VM·`BrewAnnouncer`)의 "직전과 같으면 부르지 않음" 규칙은 5초 값 낭독에만 있고, 상태 알림은 같은 값이라도 매번 `announce`를 부른다.
- 노드 모양: 1 dp 크기의 투명 배경 노드로 접근성 트리에 남긴다. `invisibleToUser`·`alpha(0f)`·크기 0·`clearAndSetSemantics {}`(텍스트 없이)를 쓰지 않는다(Compose가 그런 노드를 트리에서 빼서 liveRegion이 읽히지 않는다). 글자는 화면에 그리지 않는다(`Text` 대신 `semantics { contentDescription = 문구; liveRegion = … }`). `contentDescription`은 문구가 비어 있지 않을 때만 넣는다(ui-design 8.3 1번, 2026-10-30): 빈 노드는 포커스 대상이 아니라 평소에는 스와이프 정거장이 없다. `liveRegion`은 늘 둔다. 위 "같은 문구 다시 읽기"의 빈 문자열 단계는 `contentDescription`이 없는 상태가 된다. 빈 상태에서 문구가 생길 때 알림이 나는지는 FIX-3 `StatusAnnouncerTest`(재낭독)와 M8 실기기 B12로 확인한다.
- 포커스 순서(ui-design 8.3 1번·8.5가 기준, 2026-10-30 설계 검증): 직전 문구가 남은 노드에 TalkBack 스와이프 탐색이 멈출 수 있다. 그래서 두 알림 노드를 최상위 `Box`의 마지막 자식 묶음에 두고 그 묶음에 `semantics { isTraversalGroup = true }`를 준다. 앞 형제인 content(`NavHost`)를 감싼 `Box`에도 `semantics { isTraversalGroup = true }`를 줘 화면 내용이 먼저 끝까지 읽히게 한다. 앱 시작·화면 전환의 첫 포커스 후보가 되지 않게 한다(첫 포커스는 각 화면 규칙, ui-design 8.5. 알림 노드에 `focusRequester`·`requestFocus`를 걸지 않는다). 묶음은 `Modifier.align(Alignment.BottomEnd)`로 둔다. Compose는 형제 노드를 위치(위 → 아래, 왼쪽 → 오른쪽)로 정렬하므로 기본 자리(왼쪽 위, y = 0)면 자식 순서와 상관없이 상단 바보다 먼저 올 수 있어서다. 글자를 그리지 않으므로 자리를 옮겨도 화면은 같다.
- 실기기 기준(M8 TalkBack, ui-design 8.5 3번): 통과 = (가) S1 세로·S2에서 앞뒤 스와이프로 처음부터 끝까지 갈 때 알림 노드가 맨 끝에 한 번만 오고 중간에 끼지 않음, (나) 앱 시작·S6 자동 진입의 첫 포커스가 알림 노드가 아님. 맨 끝에서 직전 문구를 한 번 더 읽는 것은 허용한다. 대체안은 (가)·(나) 중 하나가 깨질 때만 쓴다: 먼저 알림 묶음에 `traversalIndex = 1f`(앞 형제 content는 기본 0)를 주고, 이때 ui-design 8.5의 "`TraversalIndex`는 밸브 칸 하나뿐" 규칙과 M8 TraversalOrderTest를 "밸브 칸과 알림 묶음뿐"으로 함께 고친다. 그래도 깨지면 노드를 없애고 `View.announceForAccessibility`로 내는 대체안으로 바꾼다(ui-design 8.3 2번 아래 대안과 함께 이 절을 고친다).
- 순서 테스트(FIX-5): 진짜 `StatusAnnouncer`로 감싼 화면에서 알림을 낸 뒤 순회 순서를 읽으면 알림 노드가 순서 맨 끝에 한 번만 있고, `announce` 전(문구 빔)에는 낭독 노드 목록에 알림 노드가 없다. 화면 UI 테스트는 가짜 `Announcer`를 쓰므로 이 테스트가 따로 있어야 한다.
- 300 ms 겹침(ui-design 8.3 노드 규칙 3이 기준): 첫 문구가 오면 300 ms 창을 연다. 창 안의 Polite 문구는 차례대로 ". "로 이어 붙여 창이 닫힐 때 Polite 노드에 한 번 낸다. 창 안에 Assertive가 오면 창을 바로 닫고, 아직 내지 않은 Polite 문구를 버린 뒤 Assertive 노드에 Assertive 문구만 한 번 낸다(Assertive가 Polite를 끊는다. 버린 상태는 Assertive 원인에 덮이거나 배너·칩·타이머 라벨에 남는다, ui-design 8.3). Assertive끼리는 이어 붙인다. 낸 뒤에 오는 문구는 새 창을 연다.

화면 사이 결과 전달(내비게이션 결과, `previousBackStackEntry.savedStateHandle`):
- S6 저장 성공 → `Brew`로 돌아가며 키 `saved_session_id`(Long)를 둔다. `BrewScreen`이 한 번 읽고 지운 뒤 `BrewViewModel.onSessionSaved(id)`를 부른다 → 스낵바 "기록을 저장했습니다"와 "보기"(→ `SessionDetail(id)`).
- S10 삭제 성공(`SessionDetailEvent.Deleted`) → `History`로 돌아가며 키 `session_deleted`(Boolean)를 둔다. `HistoryScreen`이 한 번 읽고 지운 뒤 스낵바 "기록을 삭제했습니다"를 띄운다(ViewModel을 거치지 않는다).

모양 겹(ui-design 4.4 리본 그래프 `BrewChart`, 4.10 서버 `BrewVessel`·전환, 6.4 `AppShape`, 6.6 모션 토큰). UI 계층 규칙:
- 모양 겹 움직임은 `Animatable`·`animate*AsState`만 쓴다. 직접 만든 `withFrameNanos` 프레임 루프는 두지 않는다. 그래서 시스템 "애니메이션 삭제"(모션 줄이기)는 Compose `MotionDurationScale`이 처리하고, ViewModel·컨트롤러는 애니메이션 배율이나 접근성 설정을 읽지 않는다(ui-design 6.6.3). 실기기에서 배율 0이 끝 값으로 가지 않는 판이 확인되면 UI 계층 helper 하나에서만 `Settings.Global.ANIMATOR_DURATION_SCALE`을 읽어 `snapTo`로 바꾼다.
- 모양 겹은 새 ViewModel 상태를 만들지 않는다. 입력은 9.8 "맞춤 완료"에 적은 기존 값뿐이다. 숫자 겹(무게·타이머·유량·단계 카드 글자·동작 줄·배너·칩·탭)은 받은 프레임에 바로 그리고 트윈하지 않는다.
- 토큰 위치(:app `ui/theme`): `AppShapes`(M3 `Shapes` 8/12/20/28/32 dp), `object AppShape { val blob: Shape; val drop…; val vessel… }`(blob은 M7R, drop·vessel은 M8), `object AppMotion`(`follow`·`calm`·`morph`·`drop`·`fall`·`ripple`·`tint`·`nav`, 값은 ui-design 6.6.1, M8).
- 경로는 새 표본이 올 때만(`readout.sampleCount` 변화) `drawWithCache` 캐시 블록에서 만든다. 그리기 블록(`onDrawBehind`)에서는 List·Array를 만들지 않고, 프레임마다 바뀌는 머리 위치·꼬리 길이만 읽는다.

모양 겹 수치는 순수 함수로 둔다. Android·Compose 그래픽 타입 없이 `:app` JVM 단위 테스트로 검사한다(10절). 단위: 길이 dp(`Float`, 밀도 변환은 호출하는 컴포저블이 한다), 무게 g, 유량 g/s.

```kotlin
// :app ui/brew/shape/ShapeMath.kt (android.*·androidx.compose.ui.graphics import 없음)
const val MORPH_POINTS = 240              // 서버 벽 → 축 선 L 전환의 호 길이 표본 수 (ui-design 4.10)
const val VESSEL_FILL_FRACTION = 0.92f    // 서버 채움 가능 높이 = 바닥에서 92% (ui-design 6.4 vessel)
const val VESSEL_MAX_LEVEL = 1.08f

/** 리본 굵기 = min(10, 2.5 + 0.9 × max(0, flow)) dp. 표본에 저장된 유량(samples.flowGramsPerSec(i))을 넣는다(S6·S10이 같은 리본을 다시 그림). */
fun ribbonWidthDp(flowGramsPerSec: Float): Float
/** 머리 꼬리 길이 = min(18, 6 + 1.2 × max(0, f)) dp. f = 화면 유량을 motion.calm으로 거른 값(모션 줄이기면 표본의 화면 유량). */
fun headTailDp(filteredFlowGramsPerSec: Float): Float
/** 서버 수위 h(0~1.08). grams null → null(액체 없음), waterGrams null(레시피 없음) → 1.0, 아니면 grams ÷ waterGrams를 0~1.08로 자름.
 *  S6 액체(grams = 최종 무게)와 단계 표식 높이(grams = 단계 목표 g)가 같은 함수를 쓴다. */
fun vesselLevel(grams: Double?, waterGrams: Double?): Float?
/** 수위 h의 채움 높이 = VESSEL_FILL_FRACTION × vesselHeightDp × level (바닥에서 위로, dp). */
fun vesselFillHeightDp(level: Float, vesselHeightDp: Float): Float
/** 눈금 간격: span ÷ 후보 ≤ axisLengthDp ÷ minSpacingDp인 가장 작은 후보(오름차순 candidates). 없으면 마지막 후보. ui-design 4.4 "눈금 간격". */
fun chooseTickStep(span: Double, axisLengthDp: Float, minSpacingDp: Float, candidates: DoubleArray): Double
/** 표식 라벨 건너뛰기: 위에서부터(topsDp 오름차순 입력) 그리고, 마지막으로 그린 라벨 상자와 겹치는 라벨은 뺀다. 그릴 라벨의 인덱스. ui-design 4.10 */
fun visibleLabelIndices(topsDp: FloatArray, heightsDp: FloatArray): IntArray
/** 표본 솎기: columnOf(i)(그림 영역 가로 1 dp 칸 번호, i에 대해 감소하지 않음)마다 마지막 표본 인덱스. 결과 길이 ≤ 칸 수.
 *  값 없는 구간의 끊김은 솎기와 별개로 SampleBuffer.gaps()로 판정한다. drawWithCache 캐시 블록에서 부른다. */
fun thinnedSampleIndices(size: Int, columnOf: (Int) -> Int): IntArray
/** AppShape.vessel 벽(윗변 뺀 열린 경로: 오른쪽 위 → 바닥 → 왼쪽 위)을 3차 곡선 고정 분할로 펼친 점열 [x0, y0, x1, y1, …]. */
fun vesselWallPolyline(widthDp: Float, heightDp: Float): FloatArray
/** 점열을 호 길이 기준 count점으로 다시 표본. 첫·끝 점은 입력의 첫·끝 점과 같다. */
fun resampleByArcLength(polyline: FloatArray, count: Int = MORPH_POINTS): FloatArray
/** 전환 진행도 p(0~1)의 윤곽: 같은 길이의 두 표본 점열을 점끼리 직선 보간해 out에 쓴다(할당 없음). p = 1이면 out == to. */
fun lerpPoints(from: FloatArray, to: FloatArray, p: Float, out: FloatArray)
```

- 전환의 끝 윤곽 `to`는 축 선 L(x축 오른쪽 끝 → 원점 → y축 위 끝, `Running` 배치를 잰 좌표)을 `resampleByArcLength`한 것이다. 전환 끝 프레임이 4.4 축 선과 같은 점이어야 한다(ui-design 4.10, 13절 AB2).
- 차트 모드: S10 "무게"/"유량" 선택은 `SessionDetailScreen`의 로컬 상태(`rememberSaveable`)다. ViewModel 상태로 두지 않는다. S1·S6에는 차트 모드가 없다(무게 리본만).

### 9.2 연결 상태 `ConnectionUi` (ui-design 10.1)

`ConnectionState`(5.4), 스캔 결과, `PermissionStatus`, 저장 기기 유무를 합쳐 하나로 만든다. S1 칩·배너, S2, S3가 같은 값을 쓴다.

```kotlin
enum class ConnectFailure { ConnectFailed, PairingModeMaybe, ServiceNotFound, Timeout }   // PairingModeMaybe: 2026-11-03 FIX-9(A-5)

sealed interface ConnectionUi {
    data object NoDevice : ConnectionUi
    data object Disconnected : ConnectionUi
    data object BluetoothOff : ConnectionUi
    data object PermissionMissing : ConnectionUi
    data object PermissionPermanentlyDenied : ConnectionUi
    data object LocationOff : ConnectionUi
    data class Scanning(val devices: List<ScanCandidate>, val stopped: Boolean) : ConnectionUi   // stopped=true = ui의 ScanStopped
    data object ScanEmpty : ConnectionUi
    data object ScanFailed : ConnectionUi
    data class Connecting(val address: String, val deviceName: String?) : ConnectionUi
    data class Connected(val device: ScaleDevice) : ConnectionUi
    data class Reconnecting(val attempt: Int, val nextRetryAtNanos: Long) : ConnectionUi   // 화면이 1 s 주기로 남은 초 = ceil((nextRetryAtNanos − now) / 1e9) 계산. 0 이하(시도 중)면 "N초 후" 대신 "지금 연결하는 중"(ui-design 5.1·10.1, 이 문서 5.4)
    data class Failed(val reason: ConnectFailure) : ConnectionUi
}

fun connectionUi(
    state: ConnectionState,
    scanResults: List<ScanCandidate>,
    permission: PermissionStatus,
    hasSavedDevice: Boolean,
): ConnectionUi
```

| `ConnectionState` | 조건 | `ConnectionUi` |
|-------------------|------|----------------|
| `PermissionMissing` | permission = `PermanentlyDenied` | `PermissionPermanentlyDenied` |
| `PermissionMissing` | 그 밖 | `PermissionMissing` |
| `BluetoothOff`, `LocationOff` | — | 같은 이름 |
| `Idle` | 스캔 결과 있음 | `Scanning(devices, stopped = true)` |
| `Idle` | 결과 없음, 저장 기기 있음 | `Disconnected` |
| `Idle` | 결과 없음, 저장 기기 없음 | `NoDevice` |
| `Scanning` | — | `Scanning(devices, stopped = false)` |
| `Failed(NoDeviceFound)` | — | `ScanEmpty` |
| `Failed(ScanFailed)` | — | `ScanFailed` |
| `Failed(ServiceNotFound)` | — | `Failed(ServiceNotFound)` |
| `Failed(ConnectTimeout)` | — | `Failed(Timeout)` |
| `Failed(GattError)` | `whitelistAdvertising` = true (5.4, A-5) | `Failed(PairingModeMaybe)` (S1 배너·S2·S3 같은 문구, ui-design 5.1) |
| `Failed(GattError)`, `Failed(ReconnectExhausted)` | 그 밖 | `Failed(ConnectFailed)` |
| `Connecting`, `Connected`, `Reconnecting` | — | 같은 이름 |

연결 칩(ui-design 4.1)은 `ConnectionUi`에서 고른다. `Scanning`·`ScanEmpty`·`ScanFailed`는 `Disconnected` 칩, `PermissionPermanentlyDenied`·`LocationOff`는 `PermissionMissing` 칩을 쓴다.

```kotlin
data class ConnectUiState(
    val connection: ConnectionUi,
    val recentAddress: String?,
    val candidates: List<ScanCandidate>,   // = scale.scanResults. Scanning뿐 아니라 Connecting·Failed에서도 채운다(목록 유지, 연결 중 항목 표시·다른 항목 비활성·실패 뒤 다른 항목 탭, ui-design 2.1·5.3). Connecting 항목은 ConnectionUi.Connecting.address로 찾는다
)

class ConnectViewModel(scale: ScaleClient, permission: PermissionMonitor) : ViewModel() {
    val uiState: StateFlow<ConnectUiState>
    val events: Flow<ConnectEvent>                // Connected 진입 시 한 번 → S2를 닫고 S1로
    fun onPermissionResult(allGranted: Boolean, permanentlyDenied: Boolean)
    fun onResume()
    fun onScanClick(); fun onStopScanClick()
    fun onCandidateClick(address: String)
    fun onCancelConnectClick()                    // Connecting·Reconnecting "취소" → scale.disconnect() (사용자 끊기, 재연결 취소)
    fun onRetryClick()                            // Failed "다시 시도"(같은 기기) → 이 VM이 마지막으로 시도한 주소(onCandidateClick의 address)가 있으면 connect(그 주소),
                                                  // 없으면(재연결 소진 등) connectLastDevice(), 그것도 false면 startScan()
}
sealed interface ConnectEvent { data object Connected : ConnectEvent }
```

- S2 진입 시 권한·블루투스가 준비돼 있으면 `startScan()`을 한 번 부른다(화면 재구성마다 부르지 않는다). S2는 연결이 없는 상태에서만 열린다. S3 "다른 저울 찾기"는 먼저 `scale.disconnect()`를 부른 뒤 S2로 간다(9.4 `onFindOtherScaleClick`). 그래서 S2 진입 때 이미 Connected인 경우는 없고, `startScan()`도 연결 중에는 무시된다(5.4).
- S2가 열린 뒤 `Reconnecting`이 될 수 있다: S2가 열린 채 블루투스를 다시 켜거나 권한을 다시 허용하면, 연결 의도와 저장 주소가 있을 때 5.4 환경 풀림 규칙대로 `Reconnecting(attempt = 1)`이 된다(ui-design 5.3 `Reconnecting` 행). 화면은 후보 목록(`candidates`)을 유지하고 모든 항목을 비활성으로 그리며 "취소"(`onCancelConnectClick` → `disconnect()`)를 둔다. 취소 뒤 `Idle`이므로 9.2 매핑대로 후보가 남아 있으면 `Scanning(stopped = true)`, 없으면 `Disconnected`(저장 기기가 있으므로)다. 성공하면 `Connected` → `ConnectEvent.Connected`로 S2를 닫는다. 새 VM 상태·메서드는 없다.
- TalkBack 상태 알림(S2·S6·S8·S9·S10): 문구는 ui-design이 정하고, 화면(UI 계층)이 `uiState` 전이에서 만든다. S2는 `ConnectionUi` 종류 변화(Scanning 시작, ScanEmpty, ScanFailed, Failed, Connecting, `Reconnecting`으로 바뀜, `Reconnecting` → `Connected`, `PermissionMissing`·`BluetoothOff`·`LocationOff`로 바뀜 = 빈 상태 제목 문구)와 `Scanning.devices` 개수 변화(0 → n), `Scanning.stopped` false → true(결과 있음, "찾기 끝, 저울 N대"), S6는 `state`가 `SaveFailed`가 될 때, S8은 화면이 `onInsertValveStep(afterIndex)`를 부른 직후 한 번(Polite, 새 단계 번호 = `afterIndex + 2`를 문구에 넣음. 넣기 버튼은 `valveEnabled`일 때만 보이므로 무시되는 호출에서는 알리지 않는다. 화면 로컬, VM 이벤트 없음), S9·S10은 `export`가 `Idle` → `Exporting`이 될 때(Polite "내보내는 중", ui-design 8.3). ViewModel은 문구·알림 이벤트를 더 내지 않는다(필요한 정보가 모두 `uiState`에 있다). 알림 노드는 앱 루트의 `StatusAnnouncer` 하나이고(9.1, ui-design 8.3), 화면은 `LocalAnnouncer.current.announce(text, assertive)`로 문구를 넘긴다. S1은 이 규칙이 아니라 9.4 `BrewUiEvent.Announce`를 쓴다.
- 후보 정렬은 화면 쪽 규칙이다(ui-design 3.3): 이미 보인 항목의 자리는 스캔 중 바꾸지 않고, 최근 연결 기기는 나타나면 맨 위, 다른 새 항목은 끝에 붙인다. RSSI 내림차순 재정렬은 `Scanning(stopped = true)`로 바뀔 때 한 번이고, 그때 포인터가 눌려 있으면 뗀 뒤 500 ms까지 미룬다. 순서 계산은 순수 함수 `orderCandidates(previousOrder: List<String>, candidates: List<ScanCandidate>, recentAddress: String?, resort: Boolean): List<ScanCandidate>`로 두어 JVM 테스트한다(누름 지연은 화면이 `resort` 시점을 늦춰 처리).
- 후보 표시 이름(ui-design 3.3 1·1-1, 사용자 결정 2026-10-29)은 순수 함수 `displayNames(candidates: List<ScanCandidate>): Map<String, String>`(키 = address)로 두어 JVM 테스트한다. 규칙: (1) 기본 이름 = `name ?: "TIMEMORE Dot · " + address.takeLast(5)`. (2) 목록 안에서 기본 이름이 같은 항목이 2개 이상이면 그 항목들에 `" · " + address.takeLast(5)`를 붙인다(예: `TIMEMORE_Dot · A1:B2`). S2 행 글자와 TalkBack 행 낭독(ui-design 8.4)이 같은 값을 쓴다. 대체 문구 "TIMEMORE Dot"은 화면 문자열 리소스이고 함수는 그 문자열을 인자로 받아도 된다. S3 이름 줄은 연결된 기기 하나라 (1)만 쓴다. 저장(`last_name`)에는 쓰지 않는다(5.4).

### 9.3 측정 상태 `ReadingUi` (ui-design 10.2)

```kotlin
sealed interface ReadingUi {
    data object None : ReadingUi                    // 연결 전·사용자 끊음: --.-
    data object UnitUnknown : ReadingUi
    data object UnitUnknownTimedOut : ReadingUi
    data class Live(val weightGrams: Double, val flowGramsPerSec: Double, val unit: WeightUnit) : ReadingUi
    data class Stale(val lastWeightGrams: Double, val cause: StaleReason, val sinceNanos: Long, val unit: WeightUnit) : ReadingUi
}
```

매핑(위에서부터 처음 맞는 것):
1. `ConnectionUi`가 `NoDevice`·`Disconnected`·`Connecting`·`Scanning`·`ScanEmpty`·`ScanFailed`·`PermissionMissing`·`PermissionPermanentlyDenied`·`LocationOff`·`Failed`이고, 엔진 무게가 없거나 phase가 Running·Paused가 아님 → `None` (Idle에서 끊은 뒤 "다시 연결"로 `Connecting`이 돼도, 그 연결이 `Failed(Timeout)`·`Failed(ServiceNotFound)`가 돼도 옛 저울의 멈춘 값이 다시 나오지 않는다, ui-design 5.1. 추출 중 재연결 10회 소진 `Failed`는 phase가 Running·Paused라 3번 `Stale(Disconnected)`로 간다. Finished는 Running·Paused가 아니므로 `None`)
1-1. `BluetoothOff`이고 phase가 Running·Paused가 아니며, `BluetoothOff`로 들어오기 직전 `ConnectionUi`가 `Connected`·`Reconnecting`이 아니었음 → `None` (2026-10-30 설계 검증, 기준 ui-design 5.1 "연결 후 꺼짐만 멈춘 값". Idle에서 사용자 끊음·`Failed` 뒤에 블루투스를 꺼도 끊어 둔 저울의 무게가 다시 나오지 않는다. 연결된 채 꺼지면 3번 `Stale(Disconnected)`. "직전 상태"는 `BrewViewModel`이 `ConnectionUi`가 `BluetoothOff`로 바뀔 때 바로 앞 값을 하나 기억한 것이다. VM이 처음부터 `BluetoothOff`로 시작하면 "직전 없음"이라 `None`. 엔진·컨트롤러 계약은 바꾸지 않는다)
2. `Connected`이고 `status.unit == null` → `unitReadFailed`면 `UnitUnknownTimedOut`, 아니면 `UnitUnknown` (재연결 뒤 엔진 stale=Disconnected가 남아 있어도 이쪽이 먼저다)
3. 엔진 `stale != null`이고 무게 있음 → `Stale`
4. 엔진 무게 있음 → `Live`
5. 그 밖 → `None`

연결이 Connected가 아니고(사용자 끊음 포함) phase가 Running·Paused이면 1번·1-1번에 걸리지 않으므로, 마지막 무게가 있으면 `Stale(Disconnected)`다. 추출 중 S3 "연결 끊기"로 끊어도 타이머·그래프·멈춘 값이 남는다.

단위(`Live.unit`·`Stale.unit`) = `BrewUiState.displayUnit`(9.4) = `status.unit ?: controller.lastUnit ?: Gram`. 끊기면 환원기 `reset()`으로 `status.unit`이 null이 되지만 마지막 `Reading`의 단위(`lastUnit`, 8.2)를 쓰므로 oz 저울로 추출하다 끊겨도 멈춘 값·실시간 그래프 축이 g로 바뀌지 않는다. S6·S10 요약판·지표·축과 CSV는 g 고정이다(저장된 기록에는 단위가 없고 S10은 저울 없이 열린다. 세션에 단위 열을 두지 않는다. v1.0.4는 g만 보낸다, C-06-3).

과부하(사용자 결정 2026-10-28)는 3번 `Stale(Overload)`로 과부하 직전 정상 값을 멈춘 값으로 보인다. 연결 뒤 정상 값을 한 번도 받지 못한 채 과부하면 무게가 없어 `None`(`--.-`)이고, 배너는 엔진 `stale`로 `Overload`가 뜬다.

### 9.4 `BrewViewModel` (S1, S3, S4, S5, D1, D2, D5)

고빈도 값(10 Hz)과 저빈도 값을 두 흐름으로 나눈다. 화면 루트는 `uiState`만 수집하고, 무게·유량 숫자·단계 카드 수치·그래프 컴포저블이 `readout`을 각자 수집한다(`compose-app-dev` 5절).

```kotlin
enum class BrewBanner {   // ui-design 10.3 우선순위 순서. 한 번에 하나
    BluetoothOff, PermissionMissing, PermissionPermanentlyDenied, LocationOff, Failed,
    ScanFailed,              // ConnectionUi.ScanFailed
    Disconnected,            // ConnectionUi.Disconnected이고 phase가 Running·Paused일 때만 (추출 중 사용자 끊음, "다시 연결"). Idle은 빈 상태로 보이고 배너 없음
    Reconnecting, Connecting,
    Overload,                // 엔진 stale == Overload. onOverloadBannerDismissed()로 접으면 엔진 stale이 Overload를 벗어날 때까지 내지 않는다(벗어나면 접힘 초기화)
    UnitUnknownTimedOut,     // Connected && status.unit == null && status.unitReadFailed. NoData보다 위: NoData 2 s 유지 중에 재연결·단위 읽기 실패가 오면 이쪽이 이긴다(무게 영역 "단위를 알 수 없음"과 배너가 같은 원인을 말한다, ui-design 10.3)
    NoData,                  // 엔진 stale == NoData이고 status.unit != null일 때만. 단위를 모르면 0x01을 버려 엔진이 NoData가 되므로 내지 않는다(그 자리는 UnitUnknownTimedOut 또는 배너 없음).
                             // 조건이 풀려도 2 s(가상 시간으로 검증) 더 유지한다(ui-design 3.1 깜빡임 방지). 유지 중 더 높은 우선순위 배너가 오면 그쪽이 이긴다
    ModelMismatch,           // Connected && device.modelMismatch && !prefs.isModelWarningDismissed(address)
    WaitingForPour,
}

data class BrewUiState(
    val connection: ConnectionUi,
    val batteryPercent: Int?,
    val displayUnit: WeightUnit,        // status.unit ?: controller.lastUnit ?: Gram (9.3 단위)
    val phase: BrewPhase,
    val timer: TimerSnapshot,           // 화면이 ~100 ms 주기로 timer.elapsedNanos(clock.nowNanos())를 계산하고 BrewConfig.maxElapsedNanos(59:59.9)에서 자른다
    val recipe: Recipe?,                // null = 자유 추출
    val currentStepIndex: Int?,
    val lastStepReached: Boolean,
    val banner: BrewBanner?,
    val tareInFlight: Boolean,
    val canTare: Boolean,               // Connected, ReadingUi가 Live, phase가 Idle·WaitingForPour (7.2 Tared 행). tareInFlight는 넣지 않는다.
                                        // 영점 버튼 enabled = canTare. 대기 중(tareInFlight)에도 enabled를 유지해 키보드·Switch Access 포커스와 색이 바뀌지 않게 하고(ui-design 4.6), 두 번째 누름은 onTareClick이 무시한다
    val canStart: Boolean,              // Connected && status.unit != null (Idle·WaitingForPour 주 동작). UnitUnknown·UnitUnknownTimedOut에서는 false(0x01을 버려 샘플 0개 기록이 생기므로, ui-design 4.6)
    val canChangeRecipeOrSettings: Boolean, // phase가 Idle 또는 WaitingForPour일 때만 true. Running·Paused·Finished에서 false(Finished = S6이 열린 동안, NewBrew로 Idle이 될 때까지)
    val canFindOtherScale: Boolean,     // = canChangeRecipeOrSettings(Running·Paused·Finished에서 false, ui-design 3.8·10.2). S3·Failed 배너의 "다른 저울 찾기"는 false면 비활성(ui-design 3.8, stateDescription "추출 중에는 바꿀 수 없음")
    val showBottomBar: Boolean,         // phase가 Idle 또는 WaitingForPour
    val keepScreenOn: Boolean,          // phase가 Running 또는 Paused
    val settings: BrewSettings,         // S4
    val recoverableDraft: SessionSummary?,  // D2. VM 생성 때 한 번 brews.findDraft(controller.draftId.value)로 확인(실시간 초안을 뺀다)
    val showResetConfirm: Boolean,      // D5
)

data class BrewReadout(
    val reading: ReadingUi,
    val remainingGrams: Double?,        // 현재 단계까지
    val paceDeltaSec: Double?,          // 양수 = 늦음 (±2 s 이내 "제때"는 화면 규칙)
    val sampleCount: Int,               // 그래프 다시 그리기 신호
)

class BrewViewModel(
    controller: BrewSessionController, scale: ScaleClient, recipes: RecipeRepository, brews: BrewRepository,
    prefs: AppPrefs, permission: PermissionMonitor, clock: MonotonicClock,
) : ViewModel() {
    val uiState: StateFlow<BrewUiState>
    val readout: StateFlow<BrewReadout>
    val samples: SampleBuffer           // 리본 그래프 Canvas가 drawWithCache에서 읽음 (새 리스트 만들지 않음). offsetMs·weightGrams·flowGramsPerSec(굵기)
    val recipesForPicker: StateFlow<List<Recipe>>   // S5
    val events: Flow<BrewUiEvent>
    val clock: MonotonicClock           // 타이머·"N초 전"·재연결 남은 초 표시용

    // 동작 줄 (ui-design 4.6)
    fun onTareClick()                   // tareInFlight면 아무것도 하지 않는다(controller.tare() 호출 없음). 아니면 controller.tare()
    fun onStartClick()                  // 시작 / 지금 시작 / 계속
    fun onPauseClick()
    fun onResetClick()                  // Paused 초기화: controller.sampleCount > 0이면 showResetConfirm=true(D5)만, 0이면 바로 controller.resetTimer()
    fun onCancelWaitingClick()
    fun onFinishClick()
    // D1
    fun onDiscardClick()
    // D5
    fun onResetConfirmed()              // showResetConfirm=false → controller.resetTimer()
    fun onResetDismissed()              // showResetConfirm=false
    // S3·배너
    fun onDisconnectClick()             // scale.disconnect()
    fun onReconnectClick()              // scale.connectLastDevice() ("다시 연결", "지금 다시 시도", "다시 시도"). Connected 중이면 같은 주소로 다시 연결(5.4)
    fun onFindOtherScaleClick()         // S3·Failed 배너 "다른 저울 찾기": canFindOtherScale이 false(phase Running·Paused·Finished)면 무시. 아니면 scale.disconnect() → BrewUiEvent.OpenConnect
                                        // (추출 중 다른 저울에 붙으면 다른 영점의 무게가 같은 세션에 섞인다)
    fun onOverloadBannerDismissed()     // Overload 배너 "확인": 이번 과부하 동안 접는다(BrewBanner.Overload 주석)
    // S6 결과 (9.1 내비게이션 결과)
    fun onSessionSaved(sessionId: Long) // → Snackbar(Saved, sessionId = sessionId)
    fun onRetrySaveClick()              // Snackbar(SaveFailed)의 "다시 시도"·Finished 동작 줄 "다시 저장"(ui-design 4.6·5.2) → controller.retrySave()
    fun onModelWarningDismissed()       // prefs.dismissModelWarning(address)
    fun onPermissionResult(allGranted: Boolean, permanentlyDenied: Boolean)
    // S4·S5
    fun onSettingsChange(settings: BrewSettings)
    fun onRecipeSelected(id: Long?)     // null = 자유 추출. canChangeRecipeOrSettings가 false면 무시
    // D2
    fun onRecoverOpenClick()            // → BrewUiEvent.OpenSummary(draftId, recovered = true)
    fun onRecoverDiscardClick()         // brews.deleteSession(draftId)
}

sealed interface BrewUiEvent {
    data class Snackbar(val message: BrewMessage, val retryTare: Boolean, val sessionId: Long? = null) : BrewUiEvent  // Saved면 sessionId로 "보기" → SessionDetail
    data class OpenSummary(val sessionId: Long, val recovered: Boolean) : BrewUiEvent
    data object OpenConnect : BrewUiEvent
    data class Announce(val announcement: BrewAnnouncement) : BrewUiEvent   // 화면이 앱 루트 StatusAnnouncer로 넘긴다 (9.1, ui-design 8.3)
    data class Haptic(val atNanos: Long) : BrewUiEvent   // 밸브 변경 진동 1회. atNanos = VM이 낸 clock.nowNanos(). 화면은 clock.nowNanos() − atNanos ≤ 1 s일 때만 진동한다
                                                         // (Channel이 화면 밖에서 쌓인 진동을 돌아온 뒤 늦게 내지 않게). M8에서 추가
}
/** TareFailed 변환: Rejected → TareRejected(retryTare=true), Timeout → TareTimeout(retryTare=true),
 *  NotConnected·Disconnected → TareNotConnected(retryTare=false: 다시 보내도 실패한다, ui-design 5.2)
 *  BrewEvent.SaveFailed → SaveFailed(retryTare=false). VM 생성 때 controller.saveFailed가 true면 같은 Snackbar(SaveFailed)를 한 번 낸다
 *  (뒤로 가기로 Activity를 닫았다 다시 열면 화면 로컬 saveFailed가 사라지므로. 이 재방출이 없으면 동작 줄이 "저장 중"에 멈춘다).
 *  화면(ui-design 4.6·5.2)은 Snackbar(SaveFailed)를 받으면 로컬 saveFailed(rememberSaveable) = true, Indefinite 스낵바 "다시 시도"와
 *  Finished 동작 줄 "다시 저장"이 모두 onRetrySaveClick()을 부르고 saveFailed = false로 돌린다. phase가 Finished가 아니게 되면 false */
enum class BrewMessage { TareRejected, TareTimeout, TareNotConnected, ScaleTimerSyncFailed, Saved, SaveFailed }

/** S1 알림. ui-design 8.3 S1 표 한 줄 = 값 하나. 출처는 모두 BrewViewModel이고, 화면은 이전 상태와 비교해 알림을 만들지 않는다.
 *  VM은 첫 값(구독 시작·앱 시작 때의 상태)에서는 내지 않고 바뀔 때 한 번 낸다. 모드는 주석대로, 문구(문자열 리소스)·300 ms 이어 붙이기는 UI 계층. */
enum class BrewAnnouncement {
    // ConnectionUi 전이. "DuringBrew"는 phase가 Running·Paused일 때(Assertive), 나머지는 phase와 상관없거나 그 밖일 때(Polite)
    Connected,                 // Connecting → Connected
    Reconnected,               // Reconnecting → Connected
    ReconnectStarted,          // Connected → Reconnecting (끊김 한 번에 처음 한 번)
    ConnectFailed,             // → Failed(*), phase Idle·WaitingForPour
    Disconnected,              // Connected·Connecting·Reconnecting → Disconnected, phase Idle·WaitingForPour ("저울 연결 끊김")
    BluetoothOffDuringBrew,    // → BluetoothOff, Running·Paused (Assertive)
    PermissionLostDuringBrew,  // → PermissionMissing·PermissionPermanentlyDenied, Running·Paused (Assertive)
    FailedDuringBrew,          // → Failed(*) (재연결 10회 소진 등), Running·Paused (Assertive)
    DisconnectedDuringBrew,    // → Disconnected (사용자 끊음), Running·Paused (Assertive)
    // ReadingUi 원인 전이 (readout 기준, 바뀔 때 한 번, Polite)
    Overload, OverloadCleared, NoData,
    TareDone,                                                    // BrewEvent.TareSucceeded. TalkBack 여부와 관계없이 영점 성공마다 한 번 낸다(VM은 접근성을 읽지 않는다).
                                                                 // 화면은 이 이벤트를 알림(LocalAnnouncer)과 S1 서버의 영점 확인 모션(ui-design 4.10) 두 곳에 쓴다
    TimerStarted, TimerPaused, TimerResumed, TimerReset,         // PhaseChanged 표 아래
    ScaleTimerStarted, ScaleTimerPaused, ScaleTimerResumed, ScaleTimerReset, ScaleTimerResetToSummary,  // 원문 경로, v1.0.4에서는 나오지 않음
    AutoStartWaiting, AutoStarted, AutoFinished,
    CapReached,                                                  // 타이머 59:59.9 또는 샘플 버퍼 가득 참
    ValveOpen, ValveClose,                                       // BrewEvent.ValveCue(event)의 event.open이 true·false. Assertive(사용자 결정 2026-11-02, 시각이 정해진 행동 신호, ui-design 8.3). 진동 설정과 상관없이 낸다. M8에서 추가
}
```

`PhaseChanged(from, to, cause)` → `BrewAnnouncement` (7.2 전이표의 phase 변화 전부. 표에 없는 조합은 엔진이 내지 않는다):

| from | to | cause | 알림 |
|------|----|-------|------|
| Idle | WaitingForPour | User (`Tared`, 자동 시작 켜짐) | `AutoStartWaiting` (같은 영점의 `TareDone`과 둘 다 낸다. 300 ms 이어 붙이기는 UI 계층) |
| WaitingForPour | Idle | User (`CancelWaiting`, `SettingsChanged`(자동 시작 꺼짐)) | 없음 (사용자가 방금 누른 동작의 결과다. `AutoStartCancelled` 같은 값은 두지 않는다) |
| Idle, WaitingForPour | Running | User (`UserTimer(Start)`) | `TimerStarted` |
| Paused | Running | User (`UserTimer(Start)`) | `TimerResumed` |
| Running | Paused | User (`UserTimer(Pause)`) | `TimerPaused` |
| Paused | Idle | User (`UserTimer(Reset)`) | `TimerReset` |
| Running, Paused | Idle | User (`Discard`, D1 "버리기") | `TimerReset` |
| Running, Paused | Finished | User (`Finish(capReached = false)`, "완료") | 없음 (곧 S6으로 가고 S6 제목에 포커스가 간다, ui-design 8.5) |
| Finished | Idle | User (`NewBrew`) | 없음 (S6 저장·버리기 결과 스낵바가 알린다) |
| WaitingForPour | Running | AutoStart | `AutoStarted` |
| Running | Finished | AutoFinish | `AutoFinished` |
| Running, Paused | Finished | CapReached (타이머 상한, `Finish(capReached = true)`) | `CapReached` |
| Idle, WaitingForPour | Running | ScaleButton | `ScaleTimerStarted` |
| Paused | Running | ScaleButton | `ScaleTimerResumed` |
| Running | Paused | ScaleButton | `ScaleTimerPaused` |
| Running, Paused | Idle | ScaleButton | `ScaleTimerReset` |
| Running, Paused | Finished | ScaleButton | `ScaleTimerResetToSummary` |

- `ConnectionUi` 전이 알림은 위 enum 주석대로이고 `ConnectionUi` **종류**가 바뀔 때만 낸다. 같은 종류 안의 값 변화(`Connected(device)` → DIS 읽은 뒤 `Connected(device')`, `Reconnecting(attempt)` → `Reconnecting(attempt + 1)`)는 전이가 아니다.
- enum에 없는 전이는 알림이 없다: phase Idle·WaitingForPour에서 `→ Connecting`·`Scanning`·`BluetoothOff`·`PermissionMissing`·`PermissionPermanentlyDenied`·`LocationOff`, 그리고 `UnitUnknownTimedOut`·`ModelMismatch` 배너는 배너·칩으로만 보인다(ui-design 5.1·4.7). S1 화면은 배너·연결 전이를 보고 `announce`를 부르지 않는다. 그러면 VM `Announce`와 같은 전이가 두 번 읽힌다.

- 밸브(사용자 결정 2026-10-29, 배치·문구·강조는 ui-design): VM은 `BrewEvent.ValveCue(event)`마다 `Announce(ValveOpen 또는 ValveClose)`를 한 번 내고, `uiState.settings.valveHaptic`이면 이어서 `Haptic(clock.nowNanos())`를 한 번 낸다. 밸브 칸 값은 새 VM 상태 없이 화면이 `valveUiFor(uiState.phase, uiState.recipe, uiState.timer, clock.nowNanos())`(7.1)로 타이머 글자와 같은 ~100 ms 주기에 계산한다. 값이 null이면(phase Finished, 레시피 없음, `valveSchedule` 빈 목록) 칸·띠를 그리지 않는다. Idle·WaitingForPour는 경과 0의 첫 상태, Paused는 멈춘 경과다. `secondsToNext`는 `ceil(next.atSec − 경과 초)`(1초 단위 올림, 다음 변경 전에는 1 이상)이고 화면의 "까지 N초"·강조(1~5초) 판정이 이 값만 쓴다. 밸브 칸 컴포저블이 이 계산을 스스로 하고 화면 루트는 하지 않는다(타이머 글자와 같은 규칙). `BrewScreen`은 `Announce(ValveOpen | ValveClose)`를 받은 같은 수집 지점에서 칸의 색 숨(ui-design 4.11)을 시작한다(`TareDone`과 같은 방식, 모양 겹이 이벤트를 따로 수집하지 않는다). 진동은 UI 계층 한 파일(`ui/brew/ValveHaptic.kt`)에서 `LocalView.current.performHapticFeedback(HapticFeedbackConstants.CONFIRM)`(API 30+), API 26~29는 `HapticFeedbackConstants.LONG_PRESS`로 한 번 낸다. `Vibrator`·`VibrationEffect`·`VIBRATE` 권한은 쓰지 않는다. 시스템 "터치 진동"을 끈 사용자에게는 진동이 없다(사용자 결정 2026-10-29: 시스템 설정을 따른다, S4 보조 문구에 적음). 앱 쪽 켜고 끄기는 S4 `valveHaptic`이고 VM이 거른다(`Haptic`이 오면 화면은 울리기만 한다). S1이 보이지 않으면(다른 탭·홈) 진동이 없다(포그라운드 서비스 없음, 0절).
- 화면 켜짐: `keepScreenOn`이 true인 동안만 화면이 켜짐 플래그를 건다(`LocalView.current.keepScreenOn`, 다른 화면으로 가거나 false가 되면 해제).
- 알림 책임: S1 상태 알림(ui-design 8.3 S1 표)은 모두 `BrewViewModel`이 위 `Announce`로 낸다. 값 낭독(8.2 `BrewAnnouncer`의 5초 경계·단계 바뀜·목표 도달·끝 시각 넘김, 낭독 문구 500 ms 디바운스)은 화면이 `readout`·`uiState.currentStepIndex`·`lastStepReached`와 타이머 계산값에서 만든다(5초 경계는 화면의 ~100 ms 타이머 계산 기준). 값 낭독도 `LocalAnnouncer.current.announce(text, assertive = false)`로 낸다. `BrewAnnouncer`는 자기 `liveRegion` 노드를 두지 않고(9.1), 직전과 같은 문구면 `announce`를 부르지 않는다. 접근성 서비스 상태(TalkBack 켜짐 → 스낵바 `Indefinite`)는 UI 계층에서만 `AccessibilityManager`로 읽는다. ViewModel은 읽지 않는다.

- `BrewUiEvent`는 `Channel`이라 수집자가 하나다. `BrewScreen`이 `Announce`를 받으면 알림으로 넘기고, 값이 `TareDone`이고 서버(대기판)가 보이면 같은 수집 지점에서 영점 확인 모션을 시작한다(다시 오면 처음부터). 모양 겹이 이벤트를 따로 수집하지 않는다.
- 영점 직후 무게 숫자(2026-10-30 설계 검증, 기준 protocol.md C-0D-2·3.7): 숫자는 받은 `Reading` 무게를 그대로 보인다(보정·강제 `0.0` 없음, 7.2 "무게 기준 보정 없음"). 그래서 `TareDone`(0x0D 성공 응답) 뒤 실측 약 300 ms(macOS 2회 관찰. 상한·Android 미확인, protocol C-0D-2)(알림 2개, 응답과 같은 알림 포함 3개) 동안 영점 전 값이 보이고 그다음 `0.0`이 된다. 알림 "영점 맞춤"과 영점 확인 모션의 계기는 `TareDone`(응답) 그대로다(선택 (a): 영점이 반영된 첫 `Reading`에 맞추려면 7.2 무장 시점을 VM 이벤트로 내보내야 해서 두지 않는다. 모션이 숫자보다 최대 약 300 ms 먼저 시작하는 것을 허용한다). 가짜 저울로 영점 장면(`brew_tare_*` 스크린샷·테스트)을 만들 때는 벡터 `rx-tare-preweight-seq-001` 모양(영점 전 무게 ×3, 첫째는 응답과 같은 시각 → 0)을 100 ms 간격으로 보낸다.
- S3 단위 줄(2026-10-30 설계 검증, 기준 이 절): 출처는 `readout.reading`이다. `UnitUnknown` → "확인 중", `UnitUnknownTimedOut` → "알 수 없음", 그 밖 → `uiState.displayUnit`. 새 VM 상태는 없다. S3 단위 줄 컴포저블이 `readout`을 직접 수집하고 `reading`의 종류만 골라 쓴다(`map { … }.distinctUntilChanged()` 또는 `derivedStateOf`로 10 Hz 재구성 없음). 화면 루트는 `readout`을 수집하지 않는다는 규칙은 그대로다. M7 `DeviceSheet`가 받는 `ScaleStatus`를 S1에서 만들어 넘기지 않는다(`displayUnit`으로 status를 꾸미면 단위를 모르는 동안 S3가 "g"를 보여 S1 "단위를 알 수 없음"과 어긋난다). M8에서 S3 단위 줄 인자를 이 값으로 바꾼다.
- 모양 자리 전환(서버 → 그래프)의 계기는 `uiState.phase`가 `Idle`·`WaitingForPour`에서 `Running`으로 바뀌는 것이고, 화면이 이전 phase를 기억해 판정한다(UI 계층, 새 VM 상태 없음). 처음 구성(회전·복귀 포함) 때는 현재 phase로 시작해 전환을 재생하지 않는다. 반대 방향은 전환 없이 바뀐다(ui-design 4.10).
- `Recipes` 화면의 "추출" 버튼과 S5 선택은 둘 다 `controller.selectRecipe()`로 간다.
- `BrewViewModel` 생성자의 `recipes`·`brews`·`prefs`·`permission`은 8.1·8.3의 interface다. 테스트는 `:app` test의 가짜를 쓴다.
- 화면 상태 이름(스크린샷 `brew_<state>`): 연결 축 `ConnectionUi`(9.2) × 측정 축 `ReadingUi`(9.3) × 브루 축 `BrewPhase`. 스크린샷 목록은 M8 완료 조건에 있다.

### 9.5 `SummaryViewModel` (S6, D2 이후)

```kotlin
enum class SummaryState { Ready, Saving, SaveFailed }
data class SummaryUiState(
    val summary: SessionSummary?,       // null = 불러오는 중
    val samples: SampleBuffer?,         // 리본 요약판 (읽기 전용). 서버 요약판은 summary.finalWeightGrams·summary.recipe(물 g·단계)로 그린다
    val gaps: List<IntRange>,           // HasGaps 정보 카드 (비면 없음)
    val doseText: String,               // 레시피 doseGrams로 미리 채움
    val doseError: Boolean,             // 0.1~100.0 g 밖 (저장 누를 때 판정, InvalidDose)
    val note: String,                   // 최대 200자
    val state: SummaryState,
)
class SummaryViewModel(brews: BrewRepository, controller: BrewSessionController, sessionId: Long, recovered: Boolean) : ViewModel() {
    val uiState: StateFlow<SummaryUiState>
    val events: Flow<SummaryEvent>
    fun onDoseChange(v: String); fun onNoteChange(v: String)
    fun onSave()                        // brews.finish(id, dose, note, if (recovered) Interrupted else Completed) → 실시간이면 controller.newBrew() → Saved(id)
    fun onDiscardConfirmed()            // D3 뒤 brews.deleteSession(id) → 실시간이면 controller.newBrew() → Discarded
}
sealed interface SummaryEvent { data class Saved(val sessionId: Long) : SummaryEvent; data object Discarded : SummaryEvent }
```

- 원두량 빈 칸은 허용(비율 "—"). 저장 실패면 `SaveFailed`이고 입력은 그대로 둔다.
- S6에서 뒤로 가기는 저장·버리기 중 하나를 고르게 한다(Finished 상태가 남지 않게). 대화상자 표시는 `SummaryScreen`의 로컬 상태이고 "저장"은 `onSave`, "버리기"는 D3를 거쳐 `onDiscardConfirmed`로 간다(9.1). 화면 문구는 ui-design.
- `Saved(id)`를 받으면 화면이 `Brew`로 돌아가며 내비게이션 결과 `saved_session_id`를 둔다(9.1).

### 9.6 레시피 ViewModel (S7, S8, D3, D4)

```kotlin
sealed interface ListLoad<out T> { data object Loading : ListLoad<Nothing>; data object Empty : ListLoad<Nothing>; data class Loaded<T>(val items: List<T>) : ListLoad<T>; data object Error : ListLoad<Nothing> }

data class RecipeListUiState(val recipes: ListLoad<Recipe>, val canStartBrew: Boolean)   // controller phase가 Idle·WaitingForPour일 때만 true(= 9.4 canChangeRecipeOrSettings, 7.2 SelectRecipe가 받는 phase). Running·Paused·Finished("추출 진행 중")면 "추출" 비활성
class RecipeListViewModel(recipes: RecipeRepository, controller: BrewSessionController) : ViewModel() {
    val uiState: StateFlow<RecipeListUiState>
    val events: Flow<RecipeListEvent>
    fun onBrewClick(id: Long)            // canStartBrew가 false면 무시. 아니면 controller.selectRecipe → OpenBrew
    fun onRetry()
}
sealed interface RecipeListEvent { data object OpenBrew : RecipeListEvent }

data class StepDraft(
    val name: String, val targetGramsText: String, val minutesText: String, val secondsText: String,
    val valveOpen: Boolean = false,      // 단계 카드의 밸브 체크(true = 열림). valveEnabled일 때만 보이고 저장된다. 사용자 결정 2026-10-30
)
sealed interface RecipeFieldError {
    data object NameEmpty : RecipeFieldError
    data object NameTooLong : RecipeFieldError                       // 30자 초과
    data object DoseInvalid : RecipeFieldError                       // 숫자 아님 또는 0.1~100.0 g 밖 (S6 doseError와 같은 범위)
    data object WaterInvalid : RecipeFieldError
    data object NoSteps : RecipeFieldError
    data class StepNumberInvalid(val index: Int) : RecipeFieldError  // 누적 g·시각 칸 빔·숫자 아님·초 0~59 밖, 단계 시각 > 3599(59:59까지), 누적 g ≤ 0
    data class StepGramsDecreasing(val index: Int, val previousGrams: Double) : RecipeFieldError   // index ≥ 1, 누적 g < 앞 단계(같으면 기다리기 단계로 통과). 2026-10-29 StepGramsNotIncreasing(같은 값도 오류)을 대체
    data class StepTimeNotIncreasing(val index: Int) : RecipeFieldError   // 앞 단계 시각 이하. index 0은 0 s 이하(0:00)
    data class FinishTimeInvalid(val lastStepSec: Int) : RecipeFieldError   // 끝 시각 오류. lastStepSec = 마지막 단계 targetAtSec(오류 문구 "1:25보다 이르면 안 됩니다"용)
    // 밸브 오류는 없다(2026-10-29 ValveTimeInvalid·ValveTimeNotIncreasing·ValveSameState 삭제): 변경 시각이 단계 시각에서 나오고(단계 오류가 이미 막는다),
    // 이웃 단계가 같은 상태면 그 경계에서 바뀌지 않을 뿐이고, 일부 단계만 체크가 빠지는 상태는 StepDraft.valveOpen이 Boolean이라 생기지 않는다
}
// 밸브 규칙(단계 체크, 사용자 결정 2026-10-30. 편집 배치·문구는 ui-design):
// - valveEnabled(S8 "스위치(밸브) 레시피" 스위치): 열 때 recipe.valveSchedule.isNotEmpty()면 true이고 각 StepDraft.valveOpen = step.valveOpen.
//   새 레시피·밸브 없는 레시피는 false이고 StepDraft.valveOpen = false(닫힘). 켜고 끄기는 단계 체크를 지우지 않는다(다시 켜면 그대로).
// - 저장: valveEnabled면 모든 단계 valveOpen = StepDraft.valveOpen(Boolean), 아니면 모든 단계 null. 일부만 null인 Recipe를 만들지 않는다.
// - onAddStep(끝에 추가): 새 단계 valveOpen = 마지막 단계 valveOpen(단계 추가가 밸브 변경을 만들지 않게), 단계가 없으면 false(닫힘). 사용자 확정 2026-10-30
// - onInsertValveStep(afterIndex)(스위치만 바꾸는 단계 끼워 넣기, 사용자 결정 2026-10-30): valveEnabled가 false이거나 afterIndex가 steps 범위 밖이면 무시(화면은 스위치가 꺼지면 이 동작을 숨긴다).
//   p = steps[afterIndex]. afterIndex + 1 자리에 기다리기 단계 하나를 넣는다: targetGramsText = p.targetGramsText(그대로 복사), valveOpen = !p.valveOpen.
//   밸브는 이 단계의 시작 = p의 목표 시각 a에 바뀐다(7.1). 이 단계의 시각 칸은 "기다림이 끝나고 다음 단계가 시작하는 때"다.
//   시각 기본값: a를 파싱할 수 없으면 빈 칸. 뒤 단계가 없거나 뒤 단계 시각 b를 파싱할 수 없으면 a + 30 s. 있으면 (a + b) / 2(정수 나눗셈, 내림)이고 그 값이 a 이하(b − a < 2)면 빈 칸.
//   빈 칸은 저장 때 StepNumberInvalid(그 index)로 막힌다(새 오류 종류 없음).
//   이름 기본값 = valveStepName(a, !p.valveOpen): "M:SS 스위치 열기" / "M:SS 스위치 닫기"(a 파싱 실패면 "스위치 열기" / "스위치 닫기"). 끼워 넣을 때 한 번 만들고, 뒤에 시각을 고쳐도 이름을 고치지 않는다(사용자 데이터).
//   이름은 DefaultRecipes처럼 사용자 데이터라 문자열 리소스로 번역하지 않고 RecipeEditViewModel 파일의 private 함수 하나에 둔다. onMoveStepUp/Down·onRemoveStep은 체크가 단계와 함께 움직인다
//   (단계 시작 시각이 바뀌므로 파생 일정도 바뀐다. valvePreview가 보여 준다).
// - valvePreview: valveEnabled이고 단계 오류(NoSteps·Step*)가 없으면 지금 입력으로 만든 Recipe의 valveSchedule(7.1 파생), 아니면 빈 목록.
//   S8 미리보기 줄은 이 값만 쓴다. 단계 카드의 스위치 설명 줄("스위치 · 0:40부터 · 여기서 바꿈", ui-design 3.6)은 앞 카드 시각 칸을 parseStepTimeSec(아래)로 읽은 값과
//   두 카드의 valveOpen 비교만 쓴다. 화면이 일정을 따로 계산하지 않는다. 단계 카드의 상태 선택기 문구는 "열림/닫힘"(단계 동안의 상태)이다.
// - 기다리기 단계(누적 g = 앞 단계): 오류가 아니다. 밸브를 붓기 시작 시각에 바꾸려면 그 앞에 기다리기 단계를 넣는다(7.1). 카드에 그 뜻을 보이는지는 ui-design.
// 초안 파싱 함수(ui-design 15절 11 요청, 2026-10-30): RecipeEditViewModel.kt 같은 파일의 internal 최상위 순수 함수. VM 검증·저장(StepDraft → RecipeStep, valvePreview,
// onInsertValveStep 시각·이름, 끝 시각 칸)과 S8 화면(머리 "· 기다리기", 스위치 설명 줄의 "M:SS부터", StepTimeNotIncreasing 문구의 앞 단계 시각)이 이 셋만 쓴다.
// 그래서 화면 표시와 저장 검증이 같은 글자를 다르게 읽지 않는다. RecipeEditViewModel.kt·RecipeEditScreen.kt에 이 함수 밖의 toDoubleOrNull·toIntOrNull 호출이 없다.
//   internal fun parseStepGrams(text: String): Double?
//       // text.trim().toDoubleOrNull(), 유한수가 아니면(NaN·Infinity) null. 범위(> 0)는 보지 않는다(StepNumberInvalid는 검증이 판정)
//   internal fun parseStepTimeSec(minutesText: String, secondsText: String): Int?
//       // 두 칸 trim. 분 = 0 이상 정수, 초 = 0~59 정수일 때 분 × 60 + 초, 아니면 null(빈 칸 포함). 상한(3599)·오름차순은 보지 않는다(검증이 판정: > 3599는 StepNumberInvalid, 오름차순은 StepTimeNotIncreasing)
//   internal fun isWaitStep(prev: StepDraft, cur: StepDraft): Boolean
//       // parseStepGrams(prev.targetGramsText)와 parseStepGrams(cur.targetGramsText)가 둘 다 null이 아니고 같으면 true(7.1 Recipe.isWaitStep의 초안판, "120"과 "120.0"은 같다).
//       // 호출자가 index > 0일 때만 부른다
// 끝 시각 규칙(Recipe.finishAtSec):
// - 기본값: 새 레시피는 두 칸 모두 빈 칸 = finishAtSec null = 마지막 단계 목표 초에 끝. 기존 레시피는 저장된 값으로 채운다.
// - 파싱은 단계 시각과 같다(분 0 이상 정수, 초 0~59 정수, finishAtSec = 분 × 60 + 초).
// - FinishTimeInvalid: 한 칸만 비었음, 숫자 아님, 초가 0~59 밖, finishAtSec < 마지막 단계 targetAtSec, finishAtSec > 3599(타이머 상한 59:59.9 안).
//   같은 값(= 마지막 단계 목표 초)은 허용한다. 단계 오류가 있어 마지막 단계 목표 초를 모르면 끝 시각 검증은 건너뛴다.
enum class RecipeEditState { Loading, New, Editing, Saving, SaveFailed, NotFound }
// 초기값: recipeId == null이면 New, 아니면 Loading. Loading 동안 필드는 빈 값이고 on*Change·onSave는 무시하며 hasUnsavedChanges=false다.
// 불러오면 Editing(값 채움), 없으면 NotFound. 화면은 S6·S7처럼 300 ms 뒤 진행 원을 보이고 필드를 그리지 않는다(ui-design 5.7 Loading 행)
data class RecipeEditUiState(
    val state: RecipeEditState,
    val name: String, val doseText: String, val waterText: String, val steps: List<StepDraft>,
    val finishMinutesText: String, val finishSecondsText: String,   // Recipe.finishAtSec. 둘 다 빈 칸 = null(마지막 단계 목표 초). 기존 레시피를 열면 채우고, 저장 때 그대로 돌려 넣는다
    val valveEnabled: Boolean,           // S8 밸브 스위치. false면 저장 때 모든 단계 valveOpen = null. 단계별 체크는 steps[i].valveOpen
    val valvePreview: List<ValveEvent>,  // 위 "밸브 규칙". 지금 입력의 파생 일정(미리보기 줄). 2026-10-29 valves·valveAfterFinish를 대체
    val errors: List<RecipeFieldError>,  // 비어 있지 않으면 ui의 Invalid
    val lastStepMismatchesWater: Boolean, // 경고(오류 아님)
    val ratio: Double?,                  // water / dose, 읽기 전용 표시
    val hasUnsavedChanges: Boolean,      // D4
)
class RecipeEditViewModel(recipes: RecipeRepository, recipeId: Long?) : ViewModel() {
    val uiState: StateFlow<RecipeEditUiState>
    val events: Flow<RecipeEditEvent>
    fun onNameChange(v: String); fun onDoseChange(v: String); fun onWaterChange(v: String)
    fun onStepChange(index: Int, step: StepDraft); fun onAddStep(); fun onRemoveStep(index: Int)
    fun onFinishTimeChange(minutes: String, seconds: String)
    fun onValveEnabledChange(enabled: Boolean)     // 단계 체크는 onStepChange(index, step.copy(valveOpen = …))로 바꾼다. 규칙은 위 "밸브 규칙"
    fun onInsertValveStep(afterIndex: Int)         // afterIndex 단계 뒤에 스위치만 바꾸는 기다리기 단계. 규칙은 위 "밸브 규칙"
    fun onMoveStepUp(index: Int); fun onMoveStepDown(index: Int)
    fun onFieldFocusLost()               // 검증 실행
    fun onSave()                         // 검증 → 저장 → Saved
    fun onDeleteConfirmed()              // D3 → Deleted
}
sealed interface RecipeEditEvent { data object Saved : RecipeEditEvent; data object Deleted : RecipeEditEvent; data object SaveFailed : RecipeEditEvent }
```

### 9.7 기록 ViewModel (S9, S10, D3)

```kotlin
sealed interface ExportState { data object Idle : ExportState; data object Exporting : ExportState }
sealed interface ExportEvent { data class Exported(val count: Int) : ExportEvent; data object ExportFailed : ExportEvent }

data class HistoryUiState(val sessions: ListLoad<SessionSummary>, val export: ExportState)   // Empty면 CSV 비활성
class HistoryViewModel(brews: BrewRepository) : ViewModel() {
    val uiState: StateFlow<HistoryUiState>
    val events: Flow<ExportEvent>
    fun onExportAll(target: CsvTarget)  // 8.4. 화면이 container.csvTarget(uri)로 만들어 넘긴다. 예외면 ExportFailed
    fun onRetry()
}

enum class DetailState { Loading, Loaded, NoSamples, NotFound }
data class SessionDetailUiState(
    val state: DetailState,
    val summary: SessionSummary?,
    val samples: SampleBuffer?,          // 리본 요약판(무게) 또는 유량 실선. 모드는 화면 로컬 상태(9.1 "차트 모드")
    val stepReachTimesMs: List<Int?>,    // 레시피 단계 표 (레시피 없으면 빈 목록)
    val export: ExportState,
)
class SessionDetailViewModel(
    brews: BrewRepository, sessionId: Long,
    appScope: CoroutineScope,            // AppContainer.appScope. onCleared 뒤에도 남은 메모 저장을 끝내려고 받는다
) : ViewModel() {
    val uiState: StateFlow<SessionDetailUiState>
    val events: Flow<SessionDetailEvent>
    fun onNoteChange(note: String)       // 500 ms 디바운스 후 updateNote. 디바운스 대기 중 VM이 지워지면(S10을 떠남, 뒤로 가기 포함)
                                         // onCleared()가 appScope에서 남은 값으로 updateNote를 한 번 부른다. 삭제(Deleted) 뒤에는 부르지 않는다
    fun onDeleteConfirmed()              // D3 → Deleted
    fun onExportSamples(target: CsvTarget)   // 8.4. 예외면 Export(ExportFailed)
}
sealed interface SessionDetailEvent { data class Export(val e: ExportEvent) : SessionDetailEvent; data object Deleted : SessionDetailEvent }
```

- `Loading`은 300 ms 동안 아무것도 그리지 않는 화면 규칙(ui-design 5.6)을 따른다. ViewModel은 상태만 낸다.
- S9 `Error`의 "다시 시도"는 `HistoryViewModel.onRetry()`다. 저장소 Flow를 다시 구독해 `Loading` → `Loaded`/`Empty`로 간다(예외로 끝난 `stateIn`을 그대로 두지 않는다).
- `ExportFailed` 스낵바의 "다시"(S9·S10, ui-design 2.5·5.8·5.9)는 화면이 `CreateDocument`를 다시 띄우는 것이다. 새로 받은 Uri로 `container.csvTarget(uri)`를 만들어 `onExportAll`/`onExportSamples`를 다시 부른다. 직전 `CsvTarget`·Uri를 다시 쓰지 않는다(쓰기 권한이 풀렸거나 문서 제공자가 실패한 Uri일 수 있다). 그래서 ViewModel에 재시도 함수·직전 target 상태를 두지 않는다. 사용자가 선택기를 취소하면 아무것도 하지 않는다. 실패한 문서 파일은 지우지 않는다(사용자가 파일 앱에서 지운다).
- 내보내기 VM은 `Uri`·`ContentResolver`를 받지 않는다(`CsvTarget`, 8.4). 그래서 `HistoryViewModelTest`·`SessionDetailViewModelTest`도 10절 규칙대로 Robolectric 없이 가짜 `CsvTarget`으로 `Exporting`·`Exported`·`ExportFailed`를 판정한다. 실제 `csvTarget(uri)` 구현은 코드 대조로 확인한다.

### 9.8 `ui-design.md`와의 차이

ui-design.md와 대조한 결과다. 두 문서가 다르면 이 표의 "이 문서" 열이 계약이다. 이미 맞춘 항목은 표에서 뺐다(12절 변경 이력).

맞춤 완료(ui-design 2026-10-29 후속2판 확인, 할 일 없음): S1 알림 출처와 이름(8.3 S1 표 = 9.4 enum, `WaitingForPour` → `Idle` 알림 없음, `AutoStartCancelled` 없음, enum 값이 없는 연결·배너 전이는 알림 없음, 값 낭독은 `BrewAnnouncer`가 자기 노드 없이 `LocalAnnouncer`로), `PhaseChanged` 알림(D1 "버리기" = 타이머 초기화, 사용자 완료·새 추출·자동 시작 대기 취소는 알림 없음), `canStart` 단위 조건(4.6)과 `NoData`는 단위를 알 때만(10.3), `canFindOtherScale`·S7 "추출 진행 중"에 `Finished` 포함(2.4·3.8·5.1·5.6·10.2), 재연결 시도 중 "지금 연결하는 중"(5.1·10.1), DIS 읽는 중 "확인 중"(5.4), `ReadingUi.None` 조건 = 9.3 매핑 1번(10.2, `Failed` 포함), 배너 목록과 순서(10.3), 기본 레시피 5개(5.6·11절), 단위 전환 없음·과부하 멈춘 값(11절), D5·D6 소유(1절), 저울 버튼 타이머·단위가 v1.0.4에 없음(0절·5.2 주 ¹·8.3), 유량 규칙과 20 g/s 자르기(4.4), 표시 이름 없음(3.3), `NoData` 3000 ms(0절·11절), 자동 완료 끝 시각, 전원 버튼 끊김 안내(5.1 `Reconnecting` 문구), `NavigationBackHandler`·`NavigationSuiteScaffold`·S10 떠날 때 메모 저장, 인셋 규칙(3절을 이 문서 9.1이 따른다). 그 전 판에서 받은 항목(`BrewBanner` `ScanFailed`·`Disconnected`, 앱 루트 `StatusAnnouncer`, `CapReached`, `TareNotConnected`, `Connecting` 중 `None`)도 같다.

맞춤 완료(ui-design 2026-10-29 B + A판 15절, 할 일 없음):
- 새 ViewModel 상태 없음(15절 1). 붓기 끝 판정은 두지 않는다(ui-design 13절 A1). 모양 겹 입력은 모두 기존 계약에서 온다: `BrewViewModel.samples`(`offsetMs`·`weightGrams`·`flowGramsPerSec`), `readout.sampleCount`(다시 그리기 신호)·`readout.reading`(`Stale`이면 멈춘 머리), `uiState.phase`(서버/그래프·전환 계기), `uiState.recipe`·`currentStepIndex`·`lastStepReached`(표식), `SummaryUiState.summary`(`recipe`·`finalWeightGrams`)·`samples`, `SessionDetailUiState.samples`.
- 단계 허용 오차 상수 공유(15절 2): 7.1 `STEP_TOLERANCE_GRAMS`. `BrewConfig.stepToleranceGrams`·`reachTimesMs` 기본값·요약판 표식이 같은 상수를 쓴다.
- `Announce(TareDone)`을 시각 계기로도 씀(15절 3): 9.4 enum 주석과 `BrewUiEvent` 수집 규칙.
- 모션 줄이기는 UI 계층(15절 4): 9.1 "모양 겹" 규칙(`Animatable`·`animate*AsState`만, `withFrameNanos` 루프 없음, VM은 배율을 읽지 않음).
- S10 "무게/유량"은 화면 로컬 상태, S1·S6 차트 모드 없음(15절 5): 9.1 "차트 모드".
- 순수 함수 테스트(15절 6): 9.1 `ShapeMath.kt` 시그니처, 10절 표. 15절 목록에 더해 `vesselFillHeightDp`(채움 높이)·`chooseTickStep`(눈금 간격)·`lerpPoints`를 순수 함수로 뒀다.
- 마일스톤(15절 7): M7은 `done` 계약을 건드리지 않고 재작업을 새 마일스톤 M7R로 뒀다(milestones.json). M8~M11 범위·완료 조건을 B + A로 고쳤다.

맞춤 완료(ui-design 2026-10-29 설계 검증 반영판, design-review wf_01c38aae-76b 후속3 확인, 할 일 없음): `canTare`에 `tareInFlight` 없음(10.2 = 9.4), `Failed` 세 행의 무게 영역 "Running·Paused면 멈춘 값, 아니면 `--.-`"(5.1 = 9.3 매핑 1번), 표시 단위 = `Stale.unit`·S6·S10 g 고정(9절 = 9.3·9.5·9.7), `StatusAnnouncer` 노드 규칙 1~4(8.3 = 9.1. 300 ms 겹침은 Assertive가 대기 중 Polite를 버린다), S8 `Loading` 행과 `recipe_edit_loading`(5.7·8.6 = 9.6), D1 제목·본문·error 색 "버리기"와 `brew_leave_confirm_light_font200`(5.2·8.6), S2 `Reconnecting` 행·알림과 `connect_reconnecting`(5.3·8.3·8.6 = 9.2), S1 확정 저장 실패(5.2 `Indefinite` 스낵바 "다시 시도", 4.6 `Finished` 동작 줄 "저장 중"/"다시 저장", 화면 로컬 `saveFailed`, 8.3 알림 없음(스낵바 기본 낭독), 10.3 예외, `brew_finished_save_failed` = 8.2·9.4 `onRetrySaveClick`).

ui-design 15절 9 판단(S1 확정 저장 실패 중 "버리기"와 뒤로 가기): 둘 다 넣지 않는다. 근거: (1) 버리기 API를 두면 저장소가 실패하는 상태에서 되돌릴 수 없는 삭제 경로가 하나 더 생긴다. 실패 원인(디스크·IO)이 풀리면 "다시 저장"으로 끝나고, 프로세스가 끝나도 마지막 주기 저장(10 s 이내)의 `in_progress` 행이 다음 실행에서 D2 복구 대상이라 버리기는 D2에서 할 수 있다. (2) 뒤로 가기를 가로채면 대화상자가 하나 더 필요하다. 가로채지 않으면 앱을 나가지만 컨트롤러가 `appScope`에 있어 요약을 계속 들고 있고, 다시 열면 `BrewViewModel`이 `controller.saveFailed`로 `Snackbar(SaveFailed)`를 다시 내 동작 줄 "다시 저장"이 돌아온다(9.4). `isBackEnabled`는 phase Running·Paused 그대로다. 실기기에서 반복 저장 실패가 보고되면 이 판단을 다시 연다.

맞춤 완료(protocol.md, scale-protocol-analyst 반영): 영점 직후 영점 전 무게 = protocol.md 3.7 C-0D-2(근거 log:2724-2729·13753-13756, 벡터 `rx-tare-writeack-004`·`rx-tare-preweight-seq-001`) = 4.2 C-0D-2·7.2 WaitingForPour 무장 규칙. 나뉜 송신 = protocol.md 2.4(F-11 미확정, 앱은 나눠 쓰지 않고 `PayloadTooLarge`로 거부) = 4.1 F-11.

남은 차이. 화면 문구는 ui-design 소유라 이 문서의 문구 인용이 다르면 ui-design을 따른다.

맞춤 완료(2026-10-30, ui-design 2.3·3.8·4.10·5.2·5.4·8.6 확인, 할 일 없음):
- 영점 직후 무게(기준 protocol.md C-0D-2): ui-design 2.3 2번 "성공 시 무게 `0.0`", 4.10 "무게 숫자 `0.0`은 응답 프레임에 이미 바뀌어 있다", 5.2 "영점 성공 | 무게 `0.0`(즉시)"는 실측과 다르다. 이 문서 9.4 "영점 직후 무게 숫자"대로 "숫자는 받은 무게를 그대로 보이며, 성공 응답 뒤 실측 약 300 ms(알림 2개) 동안 영점 전 값이 보일 수 있다(C-0D-2). 알림·모션은 `TareDone`(응답)에 맞춘다"로 고친다. `brew_tare_drop_frame`(`TareDone` 뒤 160 ms)의 숫자는 영점 전 값, `brew_tare_ring_frame`(770 ms)은 `0.0`이다.
- S3 단위 줄(기준 이 문서 9.4 "S3 단위 줄"): ui-design 5.4 단위 행 두 개와 3.8의 조건을 `status.unit == null`·`unitReadFailed` 대신 `ReadingUi.UnitUnknown`("확인 중")·`ReadingUi.UnitUnknownTimedOut`("알 수 없음")으로 적는다. 문구는 그대로다. 8.6에 `device_sheet_unit_unknown`·`device_sheet_unit_failed`(라이트·다크)를 더한다.

맞춤 완료(2026-10-30 설계 검증 반영, design-review wf_c0c42c6a-ba7 뒤 ui-design 8.3·8.4·8.5 확인, 할 일 없음):
- `StatusAnnouncer` 포커스 순서 = ui-design 8.3 1번·8.5: 9.1을 그쪽 문장으로 고쳤다(문구가 빈 동안 `contentDescription` 없음, 알림 묶음 = 루트 `Box` 마지막 자식 `align(BottomEnd)` + `isTraversalGroup`, content(`NavHost`) 쪽 `Box`도 `isTraversalGroup`, 첫 포커스 후보 아님, 실기기 기준 (가)·(나)와 맨 끝 다시 읽기 허용, 대체안 `traversalIndex = 1f` → `announceForAccessibility`는 (가)·(나)가 깨질 때만, 테스트 "맨 끝 한 번만 + 문구 빔이면 없음"). 마일스톤 FIX-5·M8.
- `ReadingUi` 매핑 1-1 = ui-design 10.2(Idle에서 끊음·`Failed` 뒤 꺼짐은 `None`).
- `StepNumberInvalid` 문구 "59:59까지" = ui-design 3.6.
- `ExportFailed` "다시" = 파일 선택기를 다시 띄움 = ui-design 2.5·5.8(9.7).
- 배터리 낭독(ui-design 8.4·8.5, 2026-10-30): 연결 칩 `stateDescription`에 배터리를 넣지 않는다("연결됨" 등). 바로 뒤 배터리 노드 하나가 "배터리 82 퍼센트"로 읽는다(`batteryPercent == null`이면 노드 없음). S3 배터리 줄 `contentDescription`은 "배터리 82 퍼센트" / 15% 이하 "배터리 12 퍼센트, 충전 필요" / null "배터리 확인 중". 값은 기존 `BrewUiState.batteryPercent`이고 새 VM 상태 없음. 현재 코드(`ui/components/Components.kt`·`ui/device/DeviceSheet.kt`)는 옛 동작이라 M8에서 맞춘다.
- 무게 표시 범위(ui-design 4.2, 2026-10-30): `ReadingUi.Live`인데 g −9999.9 ~ 9999.9(oz −999.99 ~ 999.99) 밖이면 `--.-`, `warning` "표시 범위 밖 · 올린 것을 확인하세요" 줄, 점선 상자 없음(멈춘 값이 아니다). 화면 판정이고 새 VM 상태 없음. 저울 용량은 protocol.md(코디네이터가 행 추가).

그 전 차이는 없다(2026-10-30 ui-design판이 아래 "단계 체크 밸브" 1~9를 맞춤. 그 전: 2026-10-29 ui-design 5.3 S2 `Reconnecting` 취소 행을 9.2에 맞춤).

밸브 안내(사용자 결정 2026-10-29, ui-design 동시 갱신. S8 편집 모델은 2026-10-30 단계 체크로 바뀜, 아래 "단계 체크 밸브"): 이 문서가 주는 계약은 7.1 `ValveUi`(`open`·`next`·`secondsToNext`)와 `valveUiFor`의 phase 규칙(Idle·WaitingForPour = 경과 0 상태, Finished·레시피 없음·빈 일정 = 칸 없음), 9.4 `ValveOpen`·`ValveClose`(Assertive, 2026-11-02)·`Haptic`, `BrewSettings.valveHaptic`(S4), 9.6 단계 체크(`StepDraft.valveOpen`·`valveEnabled`·`valvePreview`)다. 칸 배치·문구·강조·카운트다운 형식·S8 편집 모양·스크린샷 이름은 ui-design이 정한다. ui-design이 이 계약에 없는 값(예: 변경 직전 강조 1~5초)을 쓰면 `secondsToNext`로 화면에서 계산하고 VM 상태를 더하지 않는다.

ui-design 3.1·3.6·3.7·3.8·4.11·8.2~8.6·15절 10번(2026-10-29 밸브 칸판)과 맞은 것: 보이는 때(레시피 없음·빈 일정·Finished = 없음, Idle·WaitingForPour = 첫 상태, Paused = 멈춤), `secondsToNext` 올림, atSec 0·시작·재개·초기화에 신호 없음, 끊김 중에도 앱 타이머로 바뀜(7.2 `Tick`), `ValveOpen`·`ValveClose` Assertive(2026-11-02, 처음엔 Polite), S8 스위치·끄면 행 숨김(시작 행·"바꾸기 추가"·끝 시각 뒤 경고는 2026-10-30 단계 체크로 없어짐), S10 일정 줄 = 기록의 `recipeJson`에서 파생한 `valveSchedule`(8.3, `history_detail_valve` 유지), 기본 일정 값(8.3, 파생 값이 같다), 밸브 칸 있을 때 무게 64 sp(사용자 결정 2026-10-29, '−8888.8'이 숫자 영역 256 dp에 들어가는 크기), 스크린샷 13종(M8·M10·M11).

맞춤 완료(사용자 결정 2026-10-29, 진동 방식): `View.performHapticFeedback(CONFIRM)`(API 30 미만 `LONG_PRESS`), `VIBRATE` 권한·`Vibrator` 없음, 시스템 터치 진동 설정을 따름(ui-design 4.11 신호 3·3.8 S4 보조 문구 = 9.4).

맞춤 완료(2026-10-30): 밸브 칸 값은 칸 컴포저블이 `valveUiFor(…)`로 계산(`uiState.valve` 없음), 한 입력에서 둘 이상을 넘으면 마지막 하나만 신호(ui-design 4.11·15절 10번). 같은 날 맞춘 S8 바꾸기 행 규칙(0:00은 `ValveTimeNotIncreasing`, 범위 오류)은 아래 단계 체크로 없어졌다.

맞춤 완료 — 단계 체크 밸브(사용자 결정 2026-10-30, ui-design 2026-10-30판 3.6·3.7·4.4·4.5·4.10·5.6·8.2~8.6·15절 11 확인, 할 일 없음). 계약:
1. S8 입력 = 레시피 단위 스위치 하나(`valveEnabled`, "스위치(밸브) 레시피") + 단계 카드마다 열림/닫힘 체크 하나(`StepDraft.valveOpen`, `onStepChange`로 바꿈). 스위치가 꺼지면 체크를 숨기고 값은 지우지 않는다. 밸브 시각 칸·시작 행·"바꾸기 추가"·행 삭제가 없다(3.6 스위치 일정 편집 대체).
2. 시작 상태 = 1단계 체크(경과 0 s의 상태, 붓기 전 준비 상태). 따로 "시작 행"을 두지 않는다. S1 Idle·WaitingForPour 칸은 지금처럼 `valveUiFor`의 경과 0 값이다.
3. 단계 i의 밸브는 앞 단계 목표 시각(1단계는 0:00)부터다. 카드 스위치 설명 줄 "스위치 · 1:15부터 · 여기서 바꿈"(1단계 "스위치 · 처음부터")의 시각은 앞 카드 시각 칸을 9.6 `parseStepTimeSec`으로 읽은 값, "여기서 바꿈"은 앞 카드와 `valveOpen`이 다를 때다. 머리 "· 기다리기"는 9.6 `isWaitStep(prev, cur)`. 선택기 문구는 "열림/닫힘". 미리보기 줄은 `valvePreview`(파생 `List<ValveEvent>`, 같은 상태가 이어지면 합쳐짐)만 쓴다. 미리보기 줄과 S10 일정 줄은 `List<ValveEvent>`를 받는 같은 UI 함수 하나다(M10·M11 조건 그대로).
4. 밸브 오류 문구 3종(`ValveTimeInvalid`·`ValveTimeNotIncreasing`·`ValveSameState`)과 끝 시각 뒤 경고(`valveAfterFinish`)를 뺀다. `recipe_edit_valve_invalid` 스크린샷도 뺀다(밸브만의 오류 상태가 없다).
5. 단계 누적 g 오류는 `StepGramsDecreasing(index, previousGrams)`(앞 단계보다 작을 때만)이다. 앞 단계와 같은 누적 g는 기다리기 단계로 통과한다. 문구는 "앞 단계(… g)보다 적으면 안 됩니다" 뜻으로 바꾼다. 1단계 0 g 이하는 `StepNumberInvalid`, 1단계 0:00은 `StepTimeNotIncreasing(0)`.
6. 기다리기 단계(7.1 `isWaitStep`)는 S1 단계 카드가 건너뛴다(엔진 무게 판정). ui-design이 정한 표시(새 VM 상태 없이 `isWaitStep`으로 판정): S1 "n/N 단계"는 기다리기 포함 전체 번호(n = `currentStepIndex` + 1, 건너뜀: 악마 1/6 → 2/6 → 4/6 → 6/6, 하리오 1/3 → 3/3), 다음 단계가 기다리기면 5줄 "다음: 1:15까지 기다리기"(4.5). S1·S6·S10 그래프와 서버 요약판은 기다리기 단계에 표식을 그리지 않고(4.4·4.10), 요약판 그래프 낭독 "단계 N개 중 M개 도달"에서 뺀다(ui-design 8절 리본 요약판 행, 악마는 "단계 3개 중 …"). S10 단계 표는 기다리기 행 "기다리기 · 1:15까지 (120 g 그대로)"를 두고 실제 도달 시각을 적지 않는다(`reachTimesMs` 값은 그대로 받고 화면이 쓰지 않는다, 3.7).
7. S1 밸브 칸 문구·카운트다운·`secondsToNext`·알림·진동은 바뀌지 않는다(파생 일정이 같은 `ValveEvent`라서). 하리오 기본의 변경 시각 120 s, 악마 75 s·105 s도 같다.
8. 기본 레시피 단계가 바뀌었다(8.3 밸브 표: 하리오 3단계, 악마 6단계, 하리오 끝 시각 120 → 150 s†). ui-design 4.5·5.6의 "가장 긴 단계 이름 53자" 예와 `brew_running_long_step`(악마 3단계)은 새 최대 36자(악마 2단계)로 맞춘다. 5.6 S7 "끝" 줄의 하리오 값은 2:30이다(사용자 확정 2026-10-30).
9. S8 단계 카드마다 "스위치만 바꾸는 단계 넣기" 동작(`onInsertValveStep(afterIndex)`, 9.6). 스위치(`valveEnabled`)가 꺼지면 숨긴다. 끼워 넣은 단계는 누적 g = 앞 단계, 체크 = 앞 단계 반대, 이름 "M:SS 스위치 열기/닫기", 시각 = 앞·뒤 가운데(뒤 없으면 앞 + 30 s, 간격 2 s 미만이면 빈 칸 → 시각 칸 오류). 끝의 "단계 추가"는 마지막 단계 체크를 따른다(첫 단계는 닫힘). 동작의 위치·아이콘·문구·접근성 이름과, 밸브가 바뀌는 때가 "앞 단계 목표 시각"임을 보이는 방법은 ui-design이 정한다.

`BrewBanner` 순서(`UnitUnknownTimedOut` > `NoData`)와 S2 같은 이름 꼬리(9.2 `displayNames`)는 ui-design 10.3·3.3 1-1이 기준이고 이 문서를 그쪽에 맞췄다(할 일 없음).

## 10. 테스트 배치

| 대상 | 위치 | 방법 |
|------|------|------|
| 코덱·재조립 | `:core:protocol` test | `protocol-vectors.json`을 kotlinx-serialization으로 읽어 전부(현재 76건, 실기기 32건. `rx-tare-preweight-seq-001`처럼 `hex`가 알림 순서열인 벡터도 원소마다 `feed`). 분할 벡터(`hex` 배열)는 원소마다 `feed` 한 번, 결합 벡터(`expect` 배열)는 `feed` 한 번에 순서대로 |
| CRC 규칙 = 벡터 | `:core:protocol` test `CrcRuleMatchesVectorsTest` | 벡터 `crc.rxCheck == false`, `crc.txValue`와 `TX_CRC` 비교 |
| 환원기·광고 판별 | `:core:protocol` test | 단위 미상 버림, g·oz 변환, 과부하, 배터리 상한 |
| 엔진·샘플 버퍼 | `:core:brew` test | 합성 시계열(7.2 목록), 가짜 시계 |
| BLE 상태 기계·큐 | `:core:ble` test | `FakeGattTransport`, 가상 시간(필요하면 Robolectric) |
| 컨트롤러·ViewModel·상태 매핑 | `:app` test | `runTest` + Turbine + `FakeScaleClient` + `:app` test의 `FakeAppPrefs`·`FakePermissionMonitor`·`FakeBrewRepository`·`FakeRecipeRepository`(8.1). Robolectric 없음 |
| Room DAO·저장소, `SharedPrefsAppPrefs`, `AndroidPermissionMonitor` | `:app` test | Robolectric, 메모리 DB |
| CSV | `:app` test | 헤더·BOM·CRLF·따옴표 문자열 비교 |
| 모양 겹 순수 함수 | `:app` test `ShapeMathTest` | JVM만(Robolectric 없음). 9.1 `ShapeMath.kt`의 경계값(리본 굵기, 꼬리 길이, 서버 수위·채움 높이, 눈금 간격, 라벨 건너뛰기, 표본 솎기, 전환 끝 점 = 축 선 L) |
| 화면 | `:app` test | Roborazzi, `screenshots/{screen}_{state}_{light|dark}[_font200].png` (`ui-design-review` 3절). 모양 겹이 있는 장면은 `mainClock.autoAdvance = false`: 정지 장면은 상태를 넣고 `advanceTimeBy(1_000)` 뒤, `_frame` 장면은 계기 뒤 적힌 ms만 진행한 프레임(ui-design 8.6) |

| 화면 동작·뒤로 가기 | `:app` test | Compose UI 테스트(Robolectric). D1·D6 `NavigationBackHandler`, 탭 보임·모양 순수 함수(`tabsVisible`, `tabTypeFor`), `rememberSaveable` 상태 복원(`StateRestorationTester`) (`testing-setup` 9·10단계) |
| 데모 저울 흉내·프레임 | `:core:protocol` test | `DemoFramesTest`(실측 벡터와 바이트 같음), `DemoScaleDeviceTest`(가상 nanos, `FrameReassembler`·`ScaleStateReducer`로 풀어 확인). 10.1 |
| 데모 저울 + 실제 BLE 클라이언트 | `:core:ble` test | `DemoScale.clientForTest` + 가상 시간. 스캔 후보·연결·영점 순서·재연결·블루투스 꺼짐·연결 실패·과부하. 10.1 |
| 데모 저울 + 엔진 종단 | `:app` test | `DemoScale.clientForTest` + `BrewSessionController` + `:app` 가짜 저장소·prefs, 가상 시간, Robolectric 없음. 자동 시작·단계·밸브 신호. 10.1 |
| 에뮬레이터 흐름 | `:app` androidTest | `connectedDebugAndroidTest`, AVD `Medium_Phone_API_37.0`, 데모 저울로. 10.1.6 |

실기기에서만 확인할 수 있는 항목은 `device-verification` 프로브 B(B1~B12)로 넘긴다. 단위 테스트로 흉내 낸 결과를 실기기 확인으로 보고하지 않는다. 데모 저울(10.1)로 확인한 결과도 실기기 확인이 아니다. 데모 저울은 이 문서와 protocol.md에 적힌 동작만 흉내 내므로, 실기기와 다른 점(미확정 항목)은 데모로 드러나지 않는다. v1.0.4에서 일어나지 않는 원문 경로(0x02·0x06 Notify, 저울 버튼 타이머·단위)는 단위 테스트로만 확인하고 프로브 B 대상에 넣지 않는다.

### 10.1 디버그 데모 저울 (debug 빌드 전용, 2026-11-02)

사용자 지시(2026-11-02): "디버그 빌드 전용 '데모 저울' 모드를 추가한다(기존 core:protocol testFixtures의 FakeScaleClient를 기반으로, 녹화된/합성 프레임 시퀀스를 프로토콜 계층에 흘려 실제 디코더·상태 리듀서·브루 엔진을 거치게 한다). release 빌드에는 포함되지 않게 하고, 하네스 설계 문서에 기록한다." 목적: 블루투스가 없는 macOS 에뮬레이터(AVD `Medium_Phone_API_37.0`, `google_apis_playstore` arm64)에서 연결 → 무게·유량·타이머 → 레시피 단계·밸브 안내 → 기록 저장·CSV 흐름과 권한 거부·블루투스 꺼짐 같은 오류 화면을 확인한다. 이 절의 선택은 모두 **하네스 추천(잠정, 사용자 최종 검토 대기)**이다(10.1.7 결정 표).

#### 10.1.1 경로

```
DemoScaleDevice(:core:protocol testFixtures, 순수 JVM, 저울 펌웨어 흉내)
   ▲ onWrite(FFF2 바이트)        │ poll() → FFF1 알림 바이트(프레임 1~3개 결합)
   │                              ▼
DemoGattTransport(:core:ble src/debug) ── GattEvent.Notification ──▶ BleScaleClient(실제, 내부 생성자)
                                                                      FrameReassembler → 디코더 → ScaleStateReducer
                                                                      → readings·status·send() 응답·재연결·블루투스 상태
                                                                         ▼
                                                     AppContainer.scale → BrewSessionController → BrewEngine → VM → 화면
```

- 데모 저울은 `ScaleClient`를 새로 만들지 않는다. 실제 `BleScaleClient`의 `internal` 생성자(`scope, transportFactory, clock, config, debugFrameLog, env, scanner, store, logSink`, M4·FIX-2 코드)에 가짜 `GattTransportFactory`·`BleEnvironment`·`BleScanner`·`DeviceStore`를 넣는다. 그래서 바이트는 실기기와 같은 재조립·디코딩·환원·명령 응답 대기(`send()`)·GATT-1 연결 순서(ReadUnit → ReadBattery → ReadTimer → DIS)·재연결 백오프·블루투스 꺼짐 처리를 지난다. `BleScaleClient`·`GattTransport`·`BleEnvironment`·`BleScanner`·`DeviceStore`의 시그니처는 바꾸지 않는다.
- 디버그 프레임 로그(`TimemoreBle`, `RX FFF1 …`·`TX FFF2 …`·`EV …`)도 실제 경로로 남는다(`debugFrameLog = true`).
- 사용자 지시의 "FakeScaleClient를 기반으로"와 다른 점: `FakeScaleClient`는 `ScaleReading`을 직접 내고 `send()`가 바이트를 만들지 않아, 그 위에 프레임을 흘리면 `BleScaleClient`의 명령 응답·재연결·블루투스 상태 경로를 따로 흉내 내야 한다(그 경로가 에뮬레이터에서 확인하려는 대상이다). 그래서 testFixtures 소스셋은 그대로 debug에 재사용하되(`FakeScaleClient`와 같은 곳, 같은 `debugImplementation(testFixtures(...))` 경로) 흉내는 바이트 수준 저울로 둔다. 결정 표 D1.

#### 10.1.2 `:core:protocol` testFixtures — 저울 흉내 (`com.timemoredot.protocol.demo`)

```kotlin
/** 저울 → 앱 프레임 바이트(protocol.md 2.1, CRC 자리 00 00). 실측 벡터와 바이트가 같다(DemoFramesTest). */
object DemoFrames {
    fun weight(weightRaw: Int, flowRaw: Int, timerSec: Int, overload: Boolean): ByteArray   // A55A 01 01 0009 …
    fun battery(op: Op, bars: Int, percent: Int): ByteArray                                   // A55A 01|02 05 0002 …
    fun unit(op: Op, unitRaw: Int): ByteArray                                                 // A55A 01|02 06 0001 …
    fun timerRead(stateRaw: Int): ByteArray                                                   // A55A 02 02 0001 …
    fun writeAck(cmd: Int, success: Boolean): ByteArray                                       // A55A 03 cmd 0001 00|01
    fun name(name: String): ByteArray                                                         // A55A 02 0C n UTF-8
    fun firmware(version: String): ByteArray                                                  // A55A 02 10 n ASCII
    fun unknown04(first: Int): ByteArray                                                      // A55A 01 04 0005 first 00000000 (C-04-1)
}

/** S2 후보 하나 = 시나리오 하나. 주소는 로컬 관리 MAC 모양(실제 저울과 겹치지 않음). */
enum class DemoScenario(val address: String, val displayName: String) {
    BrewV60("02:00:00:00:00:01", "데모 저울 · V60"),
    BrewHario("02:00:00:00:00:02", "데모 저울 · 하리오 스위치"),
    Reconnect("02:00:00:00:00:03", "데모 저울 · 끊김·재연결"),
    Overload("02:00:00:00:00:04", "데모 저울 · 과부하"),
    BluetoothOff("02:00:00:00:00:05", "데모 저울 · 블루투스 꺼짐"),
    ConnectFail("02:00:00:00:00:06", "데모 저울 · 연결 실패");
    companion object { fun byAddress(address: String): DemoScenario? }
}

/** 시나리오가 transport·환경에 내는 링크 사건(BleScaleClient가 아니라 DemoGattTransport·DemoEnvironment가 해석). */
sealed interface DemoLinkEvent {
    data class Drop(val status: Int) : DemoLinkEvent                 // ConnectionChanged(false, status)
    data class BluetoothOff(val durationMs: Long) : DemoLinkEvent     // 환경 블루투스 꺼짐 → durationMs 뒤 켜짐
}
data class DemoPoll(val notifications: List<ByteArray>, val link: DemoLinkEvent?)

/** 저울 펌웨어 흉내. 순수 JVM, 시각은 인자(단조 ns). 주소 하나에 하나이고 연결이 끊겨도 영점·타이머·붓기 상태를 유지한다. 메인 스레드 전용. */
class DemoScaleDevice(val scenario: DemoScenario, startNanos: Long) {
    /** FFF2에 쓴 프레임 하나. 헤더·길이가 틀리거나 모르는 cmd면 응답 없음(앱은 Timeout). */
    fun onWrite(bytes: ByteArray, atNanos: Long)
    /** 연결 시도마다 한 번. false면 그 시도는 ConnectionChanged(false, 133)으로 끝난다. */
    fun onConnectAttempt(atNanos: Long): Boolean
    /** 알림 구독까지 끝나 연결된 시각(시나리오 시계 기준점). */
    fun onConnected(atNanos: Long)
    /** 100 ms마다 부른다. atNanos까지 낼 알림과 링크 사건. */
    fun poll(atNanos: Long): DemoPoll
}
```

흉내 규칙(단위: 무게 raw = 0.1 g 정수, 시간 = 단조 ns. 출처가 protocol.md인 값은 그 절):

| 항목 | 규칙 |
|------|------|
| 알림 주기 | 100 ms마다 `weight` 프레임 하나(3.1, C-01-12). 알림 하나 = 그 프레임 + 붙는 프레임(아래). 링크가 끊긴 동안(transport 없음)에는 `poll`을 부르지 않지만 붓기 시간은 흐른다 |
| 물리 무게 | 처음 213.4 g(빈 드리퍼·서버, 실측 log:2724 값). 붓기 중이면 `기준 + pour(t)`. 표시 무게 = 물리 무게 − 영점 오프셋, `weightRaw = round(표시 g × 10)` |
| 붓기 시작 | 영점이 반영된 첫 0 g 프레임 뒤 3 s. 영점마다 기준 = 그때 물리 무게, 붓기를 처음부터 다시 한다(다음 추출). 영점 없이 `SetTimer(Start)`만 하면 붓지 않는다 |
| `pour(t)` | `BrewV60`·`Reconnect`·`Overload`·`BluetoothOff`: ui-organic-direction.md 6.4 합성 식(`:app` test `BrewFixtures.Fx.w`와 같은 식: 0~10 s 뜸 61 g, 40 s까지 61 g, 90 s에 250 g, 그 뒤 250 g). `BrewHario`: 0~10 s 선형 0 → 240 g, 그 뒤 240 g. `ConnectFail`: 붓지 않음 |
| 유량 | `flowRaw = clamp(raw(t) − raw(t − 1 s), 0, 999)`, 자기 표시 raw 이력 10프레임 앞과 비교(C-01-5, C-01-7). 이력이 모자라면 0 |
| 영점(0x0D W) | 응답 `writeAck(0x0D, true)`를 쓰기 시각 + 150 ms 뒤 첫 알림의 `weight` 뒤에 붙인다(그 `weight`는 영점 전 값). 이어지는 두 알림의 `weight`도 영점 전 값, 그다음 알림부터 오프셋 적용(C-0D-2, 벡터 `rx-tare-preweight-seq-001` 모양: 영점 전 3프레임, 응답 뒤 약 300 ms에 0 g) |
| 타이머(0x02) | `SetTimer` W: 상태 바꾸고 `writeAck(0x02, true)`. 리셋 상태에서 시작이면 같은 알림에 `unknown04(0)`을 덧붙인다(3.12 표 첫 행, 벡터 `rx-multi-frames-003` 모양). `timerSec` = 시작 뒤 누적 Running 초(내림), Pause는 멈춤, Reset은 0. Read 응답 `timerRead(01/02/03)`, 처음 03 |
| Read 응답 | ReadUnit → `unit(Read, 0x00)`(g). ReadBattery → `battery(Read, 3, 82)`. ReadTimer → 위. ReadName → `name("TIMEMORE_Dot")`. ReadFirmware → `firmware("demo")`. 응답 시각 = 요청 + 150 ms(C-W-2 실측 128~304 ms 안) |
| 그 밖의 W | SetUnit → 응답 성공, 단위 바꿈(MVP는 보내지 않음). SetName·FactoryReset·ForgetDevice → 응답 성공, 효과 없음. PowerOff → 응답 성공 뒤 다음 `poll`에서 `Drop(0x13)` |
| 배터리 Notify | 연결 뒤 30 s마다 `battery(Notify, 3, 82)`를 그 알림의 `weight` **앞**에 붙인다(C-05-3, 벡터 `rx-two-frames-003` 순서) |
| 과부하 | `Overload`: 붓기 15~20 s 동안 `overload = true`, `weightRaw`는 직전 값 고정(C-01-10. 앱은 쓰지 않음, C-01-11) |
| 알림 끊김(NoData) | `Reconnect`: 붓기 20.0~25.0 s 동안 알림 없음(링크 유지, `noDataTimeoutNanos` 3 s 초과, 1 s 틱이 창 2 s 안에 들도록). 그동안 생긴 응답은 끝난 뒤 첫 알림에 붙는다 |
| 링크 끊김 | `Reconnect`: 붓기 40 s에 `Drop(8)`. 그 뒤 연결 시도 2번은 `onConnectAttempt` false, 3번째 성공(백오프 1 s·2 s 뒤 → `Reconnecting(3)`에서 연결). 저울 타이머·영점·붓기는 유지(전원 재투입이 아니라 범위 이탈 흉내. 전원 재투입 동작은 C-0B-2 미확정이라 흉내 내지 않는다) |
| 블루투스 꺼짐 | `BluetoothOff`: 첫 `onConnected` 20 s 뒤 한 번 `BluetoothOff(8_000)` |
| 연결 실패 | `ConnectFail`: `onConnectAttempt` 늘 false → 첫 연결이면 `Failed(GattError(133))` |

- 단위 미상(`UnitUnknownTimedOut`)·영점 거부·응답 없음 시나리오는 두지 않는다. 필요해지면 `DemoScenario` 항목 하나와 규칙 한 줄로 더한다.
- 녹화 재생을 쓰지 않는 이유(결정 표 D3): 재생 프레임은 앱의 영점·타이머 쓰기에 응답하지 못해 흐름이 이어지지 않는다. 대신 `DemoFrames`가 실측 벡터와 바이트가 같음을 테스트로 고정한다.

#### 10.1.3 `:core:ble` src/debug — 가짜 transport와 `DemoScale` (`com.timemoredot.ble.demo`)

```kotlin
/** debug 소스셋에만 있다. :app debug가 VariantHooks에서만 부른다. */
object DemoScale {
    /** 데모 모드 ScaleClient: 실제 BleScaleClient(내부 생성자) + DemoTransportFactory·DemoScanner·DemoEnvironment·데모 기기 저장소("scale_demo" SharedPreferences). debugFrameLog = true. */
    fun client(context: Context, scope: CoroutineScope, clock: MonotonicClock): ScaleClient
    /** Context 없는 JVM 테스트용(:core:ble·:app test): 권한 허용·위치 켜짐 고정, 기기 저장은 메모리, 로그는 버림. */
    fun clientForTest(scope: CoroutineScope, clock: MonotonicClock): ScaleClient
}
```

내부(이름은 구현자 재량, 동작은 고정):
- `DemoScanner : BleScanner`: `start`는 `scope`에서 300 ms 뒤 `DemoScenario` 6개를 `ScanHit(address, rssi = −40 − 5 × 순번, Advertisement(name = displayName, serviceUuids = [], manufacturerData = {0xFFFF: [02, 01]}))`로 한 번씩 낸다(실제 `isScaleCandidate` (b)를 지난다). `stop`은 그 작업을 취소한다. 콜백은 `scope` 디스패처(메인).
- `DemoTransportFactory : GattTransportFactory`: 주소마다 `DemoScaleDevice`를 하나 만들어 프로세스 동안 들고 있다. `connect(address)`마다 새 `DemoGattTransport`. `DemoScenario`에 없는 주소는 `ConnectionChanged(false, 133)`만 내는 transport.
- `DemoGattTransport : GattTransport`: 생성 때 `onConnectAttempt`가 true면 `ConnectionChanged(true, 0)`, false면 `ConnectionChanged(false, 133)`을 채널에 넣는다. `discoverServices` true, `hasCharacteristic`는 `SCALE_SERVICE_UUID`의 FFF1·FFF2만 true, `requestMtu` 247, `enableNotifications` true 뒤 `onConnected`와 100 ms 주기 `poll` 작업 시작(알림 = `Notification(FFF1, bytes, clock.nowNanos())`). `writeNoResponse` → `onWrite`, true. `read(DIS, 0x2A24)` → `"TES017"`, `read(DIS, 0x2A26)` → `"demo"`(S3에서 데모임이 보인다). `Drop(s)` → `ConnectionChanged(false, s)`. `BluetoothOff(d)` → 환경에 넘김. `close()`는 6절 규칙(멱등, 주기 작업 취소, 채널 닫음).
- `DemoEnvironment : BleEnvironment`: `hasPermissions()`·`isLocationOffForScan()`은 실제 `AndroidBleEnvironment`에 맡긴다(`clientForTest`는 true·false 고정). `isBluetoothOn()`은 데모 값(처음 true). `observeBluetooth`는 콜백을 저장하고, `BluetoothOff(d)`를 받으면 값을 false로 바꿔 콜백(false)을 부르고 `d` 뒤 true로 바꿔 콜백(true)을 부른다. 그래서 `BleScaleClient`의 실제 꺼짐·켜짐 처리(6절 블루투스 상태, 5.4 환경 풀림 → `Reconnecting(attempt = 1)`)를 지난다.

#### 10.1.4 `:app` — 진입과 컨테이너

```kotlin
// app/src/debug/java/com/timemoredot/app/VariantHooks.kt 와 app/src/release/java/com/timemoredot/app/VariantHooks.kt (시그니처 같음)
internal object VariantHooks {
    /** MainActivity.onCreate 첫 줄. debug: 시작 컴포넌트가 DemoLauncher면 이 프로세스를 데모 모드로 요청한다.
     *  컨테이너가 이미 다른 모드로 만들어졌으면 모드를 바꾸지 않고 Toast("다른 모드로 실행 중입니다. 앱을 완전히 닫고 다시 여세요")만 띄운다. release: 아무것도 하지 않는다. */
    fun onActivityCreate(activity: Activity)
    /** debug 데모 모드면 AppContainer(app, scaleFactory = { a, s, c -> DemoScale.client(a, s, c) }, databaseFactory = { buildAppDatabase(Room.databaseBuilder(it, AppDatabase::class.java, "timemore_demo.db")) }).
     *  아니면 null(기본 AppContainer). release: 늘 null. release 파일에는 "demo"·"Demo" 글자가 없다. */
    fun overrideContainer(app: Application): AppContainer?
}
```

- 진입: debug 매니페스트(`app/src/debug/AndroidManifest.xml`)의 `activity-alias` `com.timemoredot.app.DemoLauncher`(`targetActivity = .MainActivity`, 라벨 "Timemore 데모", MAIN/LAUNCHER). debug 빌드를 설치하면 런처에 아이콘이 둘 생긴다. 명령: `adb shell am start -S -W -n com.timemoredot.app/.DemoLauncher`(`-S`로 앞 프로세스를 끝내 모드가 새로 정해진다). 일반 아이콘·`.MainActivity`로 열면 실제 BLE다. 모드는 프로세스 하나에 하나이고 저장하지 않는다.
- 데모 모드에서 S2 스캔 목록에 `DemoScenario` 6개가 후보로 나온다(이름은 광고 이름 = `displayName`, ui-design 3.3 표시 규칙 그대로). 후보를 고르는 것이 시나리오 선택이다. S2·ui-design에 새 화면·문구가 없다.
- 데모 모드는 기기 주소를 `"scale_demo"` SharedPreferences에, DB를 `timemore_demo.db`에 따로 둔다. 실제 저울 주소·기록과 섞이지 않고, 데모를 처음 열 때 기본 레시피 시드가 새로 들어간다(8.3). 다음 데모 실행은 마지막 데모 저울에 자동 연결한다(8.1 `connectLastDevice`).
- 권한 거부 화면: 실제 시스템 권한을 쓴다(데모 환경의 `hasPermissions`가 실제 확인을 부르므로 `AndroidPermissionMonitor`와 같은 값). 에뮬레이터에서 `adb shell pm revoke com.timemoredot.app android.permission.BLUETOOTH_SCAN`·`…BLUETOOTH_CONNECT` 뒤 데모를 열면 `PermissionMissing`, 권한 대화상자에서 두 번 거부하면 `PermissionPermanentlyDenied`, `pm grant` 또는 설정에서 허용하면 풀린다. 런타임 권한 회수는 프로세스를 끝내므로 실행 중 회수는 확인하지 않는다. 위치(`LocationOff`)는 API 30 이하만이라 API 37 에뮬레이터에서 보이지 않는다.
- 블루투스 꺼짐 화면: `데모 저울 · 블루투스 꺼짐` 시나리오(연결 20 s 뒤 8 s 꺼짐 → 자동 재연결). 에뮬레이터의 실제 블루투스 유무와 상관없다. 꺼짐 중 "켜기" 버튼은 시스템 `ACTION_REQUEST_ENABLE`을 띄우므로 데모 상태를 바꾸지 않는다(확인 대상 아님).
- 보안: 별칭은 debug에서만 export된다. 다른 앱이 debug 빌드를 데모 모드로 열 수 있지만 debug 빌드 한정이고 실제 데이터에 닿지 않는다(따로 둔 prefs·DB).

#### 10.1.5 release 제외

- 소스셋·의존성으로 뺀다(실행 시 플래그 없음): `:core:protocol` testFixtures는 `:core:ble`의 `debugImplementation`으로만, 데모 transport는 `:core:ble` `src/debug`, 진입은 `:app` `src/debug`. `:app` release는 `:core:ble` release 변형을 받아 셋 다 없다. `src/main`은 데모 타입을 참조하지 않는다(`VariantHooks`만 부른다).
- 확인 명령(DEMO-2 완료 조건):
  - `verify.sh :app:assembleRelease` 종료 코드 0(release 소스셋이 컴파일된다. 기본 `verify.sh`는 debug만 만든다).
  - `unzip -p app/build/outputs/apk/release/app-release-unsigned.apk 'classes*.dex' | grep -a -c -e 'com/timemoredot/ble/demo' -e 'com/timemoredot/protocol/demo' -e 'FakeScaleClient' -e 'DemoLauncher'` 결과 0. 같은 명령을 `app/build/outputs/apk/debug/app-debug.apk`에 하면 1 이상(검사가 공허하지 않음).
  - `./gradlew :app:processReleaseMainManifest` 병합 매니페스트에 `DemoLauncher` 0줄, `processDebugMainManifest`에는 1줄 이상.
  - `./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -c 'test-fixtures'` 결과 0.
- release는 R8을 켜지 않았다(`isMinifyEnabled` 기본 false). 그래서 위 검사는 축소에 기대지 않는다.

#### 10.1.6 테스트와 에뮬레이터 확인

| 층 | 어디서 | 무엇을 |
|----|--------|--------|
| 흉내 바이트 | `:core:protocol` test (JVM) | `DemoFrames` = 실측 벡터 바이트, `DemoScaleDevice` 규칙(10.1.2 표)을 `FrameReassembler`·`ScaleStateReducer`로 풀어 확인 |
| 실제 BLE 클라이언트 경유 | `:core:ble` test (JVM, 가상 시간) | `DemoScale.clientForTest`: 후보 6개, 연결·DIS, 영점 순서, 타이머, 재연결 `Reconnecting(1~3)`, 블루투스 꺼짐 → 재연결, 연결 실패, 과부하 |
| 엔진 종단 | `:app` test (JVM, 가상 시간, Robolectric 없음) | `DemoScale.clientForTest` + `BrewSessionController`: 하리오 자동 시작·`ValveCue((120, true))` 한 번, V60 P2 평평한 구간 `|paceDeltaSec|` ≤ 2 |
| 진입·release 제외 | `:app` test(Robolectric) + gradle·unzip 명령 | `VariantHooks` 모드 결정, 10.1.5 명령 |
| 에뮬레이터 | `:app` androidTest `connectedDebugAndroidTest` | 아래 |

에뮬레이터(DEMO-3, 조건 앞 `[에뮬레이터]`):
- 준비: `$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-boot-anim &` → `adb wait-for-device shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 1; done'`. 그 뒤 `verify.sh :app:connectedDebugAndroidTest`. AGP가 테스트 뒤 앱을 지우므로 매번 새 설치 상태다.
- 계측 테스트는 데모 별칭으로 시작한다(`Intent().setClassName(패키지, "com.timemoredot.app.DemoLauncher")` + `FLAG_ACTIVITY_NEW_TASK`로 `startActivity`, `createEmptyComposeRule()`. `ActivityScenario`가 별칭을 다루지 못하면 이 방법만 쓴다). 권한은 시작 전에 `UiAutomation.grantRuntimePermission`으로 준다.
- 범위: V60 흐름(S7 "추출" → S1 → 영점 → 자동 시작 → 단계 → 완료 → S6 저장 → S9 행), CSV(S9 내보내기, `CreateDocument` 결과를 espresso-intents로 앱이 만든 `MediaStore.Downloads` Uri로 돌려주고 첫 줄 헤더 확인), 하리오 밸브 칸(2:00 전 닫힘 → 뒤 열림, 약 130 s), 과부하·끊김·재연결·블루투스 꺼짐·연결 실패 화면 문구. 권한 거부 화면은 계측 안에서 권한을 회수할 수 없어 adb 명령으로 확인한다(DEMO-3 조건).
- 판정: 에뮬레이터를 띄울 수 없으면 `[에뮬레이터]` 조건은 `unverified`(4.2의 `[실기기]`와 같은 처리)다.

#### 10.1.7 결정 표 (하네스 추천(잠정, 사용자 최종 검토 대기), 2026-11-02)

| ID | 선택지 | 채택안 | 근거 | 되돌리는 비용 |
|----|--------|--------|------|---------------|
| D1 주입 계층 | (1) `FakeScaleClient`를 감싸 프레임을 재조립기·환원기에 흘리는 펌프 (2) 가짜 `GattTransport`를 실제 `BleScaleClient`에 끼움 | (2) | (1)은 `send()`가 바이트를 만들지 않고 연결 상태를 스크립트로 정해, 에뮬레이터에서 보려는 명령 응답·재연결·블루투스 처리 코드를 지나지 않는다. (2)는 그 코드를 실기기와 같게 지나고 `BleScaleClient` 시그니처를 바꾸지 않는다. testFixtures 재사용(사용자 지시)은 저울 흉내를 같은 소스셋에 두는 것으로 지킨다 | 중간: `:core:ble` debug 파일 3~4개를 버리고 펌프를 새로 쓴다. `DemoScaleDevice`·`DemoFrames`는 그대로 쓴다 |
| D2 코드 위치·release 제외 | (1) 흉내 = testFixtures, transport = `:core:ble` `src/debug`, 진입 = `:app` `src/debug`·`src/release` (2) 전부 `:app` `src/debug` (3) 새 모듈 `:core:demo`(`debugImplementation`) | (1) | 흉내는 순수 JVM이라 `:core:protocol` test에서 수 초에 검증된다. transport는 `BleScaleClient` `internal` 생성자를 써야 해서 `:core:ble` 안이어야 한다((2)·(3)은 `internal`을 못 쓴다). AGP 변형 소스셋이라 release에서 코드와 의존성이 함께 빠진다 | 낮음: 파일 이동 |
| D3 프레임 출처 | (1) 합성(바이트를 실측 벡터와 같게 고정) (2) 실측 로그 재생 (3) 둘 다 | (1) | 재생은 앱의 영점·타이머 쓰기에 응답할 수 없어 흐름이 끊긴다. 합성 바이트를 벡터와 같게 테스트로 묶어 실측과의 거리를 없앤다. 영점 순서는 실측 모양(C-0D-2) 그대로 | 낮음: `DemoScenario`에 재생 항목 하나 추가 |
| D4 진입 | (1) debug 런처 별칭 `DemoLauncher`(시작 컴포넌트로 판정) (2) adb intent extra (3) S2에 디버그 전용 토글 UI (4) 에뮬레이터 자동 감지 | (1) | extra를 읽지 않아 9.1 Intent 규칙을 debug에서도 지킨다. 사람은 아이콘, 자동화는 `am start -S -n …/.DemoLauncher`. S2 화면·ui-design 변경 없음(후보 목록이 곧 시나리오 메뉴). 자동 감지는 가상 블루투스가 있는 AVD에서 갈리고 실기기 debug(프로브 B)를 흐릴 수 있다 | 낮음: 매니페스트 별칭과 판정 한 줄 |
| D5 오류 화면 | 블루투스: (1) 데모 환경이 꺼짐 사건을 냄 (2) 실제 시스템 블루투스. 권한: (1) 실제 시스템 권한 (2) 데모 환경이 거부를 흉내 | 블루투스 (1), 권한 (1) | 에뮬레이터 블루투스 유무가 AVD·판마다 다르다. 데모 사건도 실제 `observeBluetooth` 콜백 경로를 지난다. 권한은 하드웨어와 상관없이 에뮬레이터에서 실제로 동작하고, `AndroidPermissionMonitor`와 `BleScaleClient`가 같은 값을 봐야 한다 | 낮음 |
| D6 계측 테스트 | (1) 없음(수동만) (2) `connectedDebugAndroidTest`로 V60 흐름·CSV·밸브·오류 화면 + JVM 가상 시간 종단 테스트 (3) 모든 화면 계측 | (2) | 에뮬레이터 확인을 명령 하나의 통과·실패로 판정한다. 시간 많이 드는 밸브 판정은 JVM 가상 시간으로 먼저 고정하고 에뮬레이터에서는 한 번만 본다. 화면 모양은 Roborazzi가 이미 맡는다 | 낮음: androidTest 소스와 의존성 3줄 |
| D7 컨테이너 생성 시점 | (1) `TimemoreApp.container`를 `by lazy`로 (2) 모드가 다르면 프로세스를 다시 시작 | (1) | Application `onCreate`가 Activity보다 먼저라 시작 컴포넌트를 보려면 첫 접근을 늦춰야 한다. 재시작 코드는 기기마다 동작이 달라 두지 않는다(모드가 다르면 Toast) | 낮음: 한 줄 |

- 실기기와의 관계: 데모 저울은 실기기 확인을 대신하지 않는다(10절 머리). 실기기 프로브 B는 일반 아이콘(실제 BLE)으로 한다.

## 11. 하지 않는 것 (MVP 밖)과 남은 위험

- 앱 쪽 단위 전환 버튼(0x06 쓰기 포함), 기기 이름 바꾸기·전원 끄기·공장 초기화·페어링 잊기 UI, 본딩 경로, 나뉜 송신, 포그라운드 서비스, 클라우드 동기화, 레시피 공유, CRC 계산(v1.0.4는 쓰지 않음), G-7 서비스(`5833ff01-…`, OTA 추정) 사용.

### 11.1 미확인 위험

| 위험 | 근거 | 앱 동작 | 확인 |
|------|------|---------|------|
| 화이트리스트 방송(`01`) 중에는 이전에 연결하지 않은 휴대폰이 연결하지 못할 수 있다 | A-5 미확정. 전원을 다시 켠 뒤 방송이 `01`이 되고, 실측은 같은 Mac 재연결뿐(log:6739, 10770) | 후보는 보이지만 연결이 `Failed(GattError)`·`Failed(ConnectTimeout)`이 된다. 별도 상태를 만들지 않는다 | 프로브 B: 다른 휴대폰으로 전원 재투입 뒤 연결. 막히면 안내 문구(저울을 다른 기기에서 끊거나 다시 켜기)를 더하는 마일스톤 |
| Android에서 본딩·CCCD·Manufacturer Data 반환이 macOS와 다를 수 있다 | G-6·G-1b는 macOS만, A-1 Android 반환 미확정 | 본딩하지 않고, CCCD `01 00`, `getManufacturerSpecificData(0xFFFF)` 값[1]로 판별 | B1(`EV ADV` 로그, 알림 수신) |
| 0xFFF0·회사 ID 0xFFFF가 Timemore 전용이 아닐 수 있다 | protocol.md 4절 비고 | 다른 BLE 기기가 후보로 보일 수 있다. 연결 뒤 0x2A24 ≠ `TES017`이면 `ModelMismatch` 경고(A-4) | 해당 없음 |
| 에뮬레이터 확인이 실기기 동작을 보장하지 않는다 | 데모 저울(10.1)은 protocol.md에 적힌 동작만 흉내 낸다. 미확정 항목(A-1 Android 반환, G-1b·G-6, C-0B-2 Android, 전원 재투입 뒤 타이머·영점)은 흉내 값이다 | 데모 결과를 `[실기기]` 조건 판정에 쓰지 않는다 | 프로브 B |
| 다른 펌웨어 판은 CRC·0x02 Notify·0x04 동작이 다를 수 있다 | F-8, C-02-1 "다른 펌웨어 판은 미확정" | 0x0000이 아닌 수신 CRC도 버리지 않는다. 0x02 Notify가 오면 원문 경로로 처리 | 새 판을 만나면 프로브 A 반복 |

## 12. 변경 이력

| 날짜 | 절 | 변경 | 근거 |
|------|----|------|------|
| 2026-10-28 | 전체 | 최초 작성. ui-design.md 10절 상태 이름에 맞춤, 차이는 9.8절 | protocol.md(원문 v1.0.3), ui-design.md(2026-10-28판), MVP 요구사항 |
| 2026-10-28 | 3, 4, 5.1, 5.3~5.5, 6, 7.1, 7.2, 8.1~8.3, 9.1~9.5, 9.6, 9.8, 10 | 설계 검증 반영: `timerEvents`(Notify만)로 `ScaleTimerChanged`를 만들고 연결 직후 맞추기·메아리 거르기 규칙, `SessionDraft.samplesBlob`, Read는 항상 응답 대기, GattTransport 이벤트 버퍼·시간 초과=끊김·close 멱등, 저장소·prefs·권한 interface와 가짜, FakeScaleClient 성공 반영, `BlePermissions` 한 곳, `offsetMs` 타이머 축, 추출 중 영점 비활성, `SettingsChanged` 전 상태 반영, `PhaseChanged`·`BrewAnnouncement`, D5·S6 뒤로 가기, Connected 중 `connectLastDevice`·`connect`·`startScan` 규칙, `EXPECTED_MODEL` 이동, `draftId`, 내비게이션 결과, `ReadingUi` 매핑 순서, 원두량 범위, 버퍼 가득 참 자동 완료, 9.8 갱신 | ECC 설계 검증(확정 지적) |
| 2026-10-28 | 0, 5.4, 6, 7.2, 8.2, 8.3, 9.1~9.4, 9.8, 11 | 사용자 결정 반영(기본 레시피 3개 시드, 앱 안 g/oz 전환 없음·`SetUnit` 미사용, 과부하 중 멈춘 값). `BrewBanner` `ScanFailed`·`Disconnected`, `BrewAnnouncement` 통일(`Disconnected`, `*DuringBrew` 4종, `CapReached`), 앱 루트 `StatusAnnouncer`·`LocalAnnouncer`, `BrewConfig.maxElapsedNanos`·`PhaseCause.CapReached`·`Finish(capReached)`, `BrewMessage.TareNotConnected`, `ReadingUi` 매핑 1번에 `Connecting`, `canTare` 문구를 ui-design과 같게, 6절 GATT-1 주석, 9.8 정리 | design-review wf_771ab4fa-5a7 후속(_workspace/02_design_followup.md) |
| 2026-10-28 | 0, 8.3, 9.8 | 기본 레시피를 사용자 지정 5개로 교체(V60 기본, 하리오 스위치 기본, 6888, 테츠 카스야 기본·악마의 레시피). 출처 열, 온도·분쇄도·밸브 동작을 단계 이름에 적는 규칙, 붓기 시작 시각 → `targetAtSec` 변환 규칙 추가. 6888 전체와 V60 기본 2단계 목표 초는 `미확인`. ui-design.md는 아직 3개 기준(ui-designer가 맞춘다) | 사용자 요청, 웹 조사(8.3 출처 열) |
| 2026-10-28 | 0, 8.3 | `미확인` 칸 채움: 6888 레시피(20 g / 300 g / 92°C / 중간 분쇄, 60·140·220·300 g @10·50·80·110 s, 출처 없음·이름에서 추정), V60 기본 2단계 목표 90 s. "미확인이 남으면 구현하지 않는다" 규칙 삭제 | 사용자 결정(2026-10-28) |
| 2026-10-29 | 0, 1, 4, 5.1~5.5, 6, 7.2, 8.2, 8.4, 9.4, 9.8, 10, 11 | 프로브 A(TES017 v1.0.4) 반영: `CrcSpec`·CRC 계산·`CrcMismatch` 삭제, `TX_CRC = 0x0000`, `FrameCodec` object. `awaitWriteAck` 삭제(G-5 확정, Read·Write 모두 응답 대기). 스캔 필터 FFF0 또는 0xFFFF + 기기 유형 `01`, 이름 조건 삭제, 표시 이름 규칙(A-3a), `EV ADV` 로그. 결합 프레임 순서 비고정·Notify 0x04 `Unknown` 버림. G-7 서비스 미사용. 저울 버튼 타이머·단위 경로는 v1.0.4에서 발생 안 함(단위 테스트만), 메아리 거르기 삭제. 유량 "컵 들면 0" 규칙(`cupLiftDropGrams`) 삭제. 4절을 4.1 미확정·4.2 확정으로 나눔. 11.1 미확인 위험(A-5 화이트리스트 연결 등) | protocol.md 2026-10-29판(§0, §5), `_workspace/04_device_probeA_result.md` |
| 2026-10-29 | 4.2, 7.1, 7.2, 8.3, 9.6, 9.8, 13 | 사용자 결정: 자동 완료는 레시피 끝 시각 뒤에만. `Recipe.finishAtSec`(null = 마지막 단계 목표 초)·`finishSec`, 전이표 조건(끝 시각 뒤 샘플만 5 s 안정), `RecipeEntity.finishAtSec`, 기본 레시피 끝 시각 표(120·165·210·180, V60 null), S8 끝 시각 입력·`FinishTimeInvalid`. 레시피 없는 추출·상한 규칙은 그대로. 13절 스킬 충돌 3건을 "스킬 정정됨" | 사용자 결정(2026-10-29), 코디네이터 |
| 2026-10-29 | 2.2, 4.1, 7.2, 9.1, 9.7, 9.8, 10 | ui-design 2026-10-29판 맞춤: `NavigationBackHandler`(navigationevent-compose 1.2.0)·화면당 하나·`isBackEnabled` 조건, `enableOnBackInvokedCallback`, `NavigationSuiteScaffold`(600 dp 너비, `snapTo` 숨김, 1.4.0 API 확인), 탭 쪽 인셋 빼기, `SessionDetailViewModel(appScope)` 떠날 때 메모 저장, `noDataTimeoutNanos` 2 s → 3 s(전원 버튼 끊김 감지 2.08~2.25 s) | ui-design.md 11절 architect 행, protocol.md C-0B-2 |
| 2026-10-29 | 머리말, 4.1, 4.2, 5.3, 5.4, 6, 7.2, 9.1, 9.2, 9.4, 9.6, 9.8, 13 | 설계 검증 반영: `scanResults`는 startScan·Connected 때만 비움과 `ConnectUiState.candidates`, NoData 기준 시각 = max(마지막 Reading, 연결 시각)과 `BrewBanner.NoData`는 단위를 알 때만, 알 수 없는 unitRaw → `unitReadFailed`, `canStart`에 `status.unit != null`, 인셋을 ui-design 3절(`safeDrawing`, `imePadding()` 없음)로, C-0B-2를 macOS 한정으로 하고 4.1에 Android 행, `canFindOtherScale`·`onFindOtherScaleClick` phase 가드, 재연결 시도 중 `Reconnecting` 유지와 시도별 10 s 제한, DIS 읽기 뒤 `Connected` 재방출·`disReadDone`, `onOverloadBannerDismissed`·NoData 배너 2 s 유지·D1·D3·D4 표시 상태 위치, `PhaseChanged` → 알림 표, S9·S10 Exporting 알림 출처, 후보 정렬 순수 함수, `canStartBrew` = Idle·WaitingForPour, 9.8 정리 | ECC 설계 검증(확정 지적) |
| 2026-10-29 | 5.4, 9.2, 9.4, 9.8 | 디자인 검증 후속2 반영: `WaitingForPour` → `Idle` 알림 없음 유지(`AutoStartCancelled` 두지 않음), enum 밖 연결·배너 전이는 알림 없음, `BrewAnnouncer` 값 낭독은 `LocalAnnouncer`로(자기 liveRegion 없음), `canChangeRecipeOrSettings`·`canFindOtherScale`이 Finished에서 false임을 명시, S2 알림 전이에 `PermissionMissing`·`BluetoothOff`·`LocationOff`·스캔 멈춤 추가, 화면 문구 인용을 ui-design에 맞춤("지금 연결하는 중", "확인 중"), 9.8 남은 차이 표 비움. M7·M8 조건 갱신 | `_workspace/02_design_followup2.md` p1·p2·p3·p6, 코디네이터 추가 요청 |
| 2026-10-29 | 2.2, 3, 6, 8.4, 9.1, 10, 13 | Android 공식 스킬 점검 반영: AGP 9 기본값·`BuildConfig` 켜기·jvmTarget 규칙(`agp-9-upgrade`), `enableEdgeToEdge`·`adjustResize`·인셋 한 방법(`edge-to-edge`), `RECEIVER_NOT_EXPORTED`·Intent 처리 범위(`android-intent-security`), `SecurityException` 처리·권한 요청 흐름(`android-permissions-security`), `runBlocking`·`commit()` 금지와 IO 작업 목록(`anr-audit-and-fix`), 뒤로 가기·상태 복원 UI 테스트(`testing-setup`). 13절에 점검 결과와 바꾸지 않은 결정 | android-skills 1.0.18 |
| 2026-10-29 | 머리말, 7.1, 7.2, 9.1, 9.4, 9.5, 9.7, 9.8, 10 | ui-design B + A판 15절 맞춤: `STEP_TOLERANCE_GRAMS`(엔진·`reachTimesMs`·요약판 표식 공유), 9.1 모양 겹 UI 규칙(`Animatable`만·`MotionDurationScale`·토큰 위치·`drawWithCache`)과 `ShapeMath.kt` 순수 함수, S10 차트 모드 = 화면 로컬, `TareDone` 시각 계기와 `BrewUiEvent` 단일 수집, 전환 계기 = phase 변화(UI 계층), 9.8 맞춤 완료, 10절 `ShapeMathTest`·스크린샷 시계. 새 VM 상태 없음. 마일스톤 M7R 추가, M8~M11 갱신 | ui-design.md 14·15절(2026-10-29 B + A) |
| 2026-10-29 | 3, 4.1, 4.2, 5.3, 5.4, 6, 7.1, 7.2, 8.1~8.4, 9.1~9.4, 9.6~9.8 | 설계 검증 반영: WaitingForPour 무장 규칙(C-0D-2 영점 전 무게 2프레임), B6 판정 = 측정 기록·`측정 최댓값 + 0.5 s ≥ noDataTimeout`이면 FIX 마일스톤, B10 = 재조립한 0x01 프레임 수와 측정 구간, `appScope` 예외 처리기·저장 실패 경로(`SaveFailed`·`retrySave`), 주기 저장 `cancelAndJoin`·`saveDraft` `in_progress` 조건(가짜 포함), 환경 막힘·풀림 전이(풀리면 `Reconnecting(attempt = 1)`), A-1 필터 상수 `Advertising.kt`로, 저울 타이머 미상(4 s) 맞추기, `send()` 디스패처·`close()`가 events를 끝냄, `CsvTarget`(VM에서 `Uri`·`ContentResolver` 제거), `StatusAnnouncer` 재낭독·노드·겹침 규칙, `RecipeEditState.Loading`, `canTare`에서 `tareInFlight` 제외, `displayNames`(같은 이름 꼬리), `ReadingUi` 매핑 1번에 `Failed`, `BrewBanner` `UnitUnknownTimedOut` > `NoData`, 표시 단위 = `lastUnit`·S6·S10 g 고정, 샘플 유량 = 엔진 평활값, F-11 protocol 2.4 차이 표기. 9.8 차이 표. 마일스톤 FIX-1~FIX-3 추가, M4·M8·M10·M11 조건 갱신 | ECC 설계 검증(확정 지적) |
| 2026-10-29 | 7.1, 7.2, 8.1, 8.2, 8.3, 9.4, 9.6, 9.8 | 사용자 결정 밸브 안내(ui-design 밸브 칸판 맞춤, M11 S10 일정 줄 포함): `ValveEvent`·`Recipe.valveSchedule`(기본 빈 목록, 옛 JSON 읽힘)·`validateValveSchedule`·`ValveUi`·`valveStateAt`·`valveUiFor`, `BrewSettings.valveHaptic`, 엔진 효과 `ValveCue`(Running의 Reading·Tick에서 넘은 이벤트 중 마지막 하나, atSec 0·Paused 신호 없음), `BrewEvent.ValveCue`, `BrewAnnouncement.ValveOpen`·`ValveClose`, `BrewUiEvent.Haptic(atNanos)`(1 s 넘은 진동 버림, `performHapticFeedback` CONFIRM/LONG_PRESS·권한 없음, 사용자 결정), 기록 일정 = `recipeJson` 안 `valveSchedule`, 첫 이벤트 atSec 0(`FirstNotAtZero`), `RecipeEntity.valveJson`(스키마 버전 1 유지·1.json 재생성, `finishAtSec`와 같은 근거), 기본 레시피 밸브 일정 표(하리오 스위치·악마의 레시피), S8 `ValveDraft`·밸브 오류 3종. 마일스톤 FIX-4 추가, M8·M10 조건 갱신, FIX-1 시그니처 비교에서 `ValveCue` 제외 | 사용자 결정(2026-10-29), 코디네이터 |
| 2026-10-29 | 4.1, 4.2, 8.2, 9.1, 9.2, 9.4, 9.8, 10 | 디자인 검증 후속3: 9.1 300 ms 겹침을 ui-design 8.3 규칙 3(Assertive가 대기 중 Polite를 버림)과 같게 확정, 9.2 S2 `Reconnecting`(환경 풀림)·취소 = `disconnect()`·알림 전이 추가, 8.2 `BrewSessionController.saveFailed`와 9.4 VM 생성 때 `Snackbar(SaveFailed)` 재방출(다시 연 Activity의 "저장 중" 멈춤 방지), 15절 9 판단(Finished 버리기 API·뒤로 가기 가로채기 없음, 9.8), C-0D-2 두 번째 실측 145.0 g·벡터 `rx-tare-preweight-seq-001`, 벡터 76건, 9.8 맞춤 완료 정리(남은 차이: ui-design 5.3 취소 뒤 행). 마일스톤 M7R·FIX-1·FIX-3·M8 조건 갱신 | `_workspace/02_design_followup3.md` p1·p3~p6, 코디네이터 추가 요청 |
| 2026-10-30 | 7.1, 7.2, 8.3, 9.6, 9.8 | 사용자 결정 S8 밸브 = 단계 카드마다 체크: `RecipeStep.valveOpen: Boolean?`(기본 null, 옛 JSON = 밸브 없음), `Recipe.valveSchedule`을 저장 필드에서 단계 시작 시각(앞 단계 `targetAtSec`, 앱 타이머 기준)으로 만드는 파생 게터로, "스위치 레시피" = `valveSchedule.isNotEmpty()`(따로 저장 안 함), `validateValveSchedule`·`ValveIssue*` 삭제, `ValveEvent` 저장 안 함. 기다리기 단계(누적 g = 앞 단계) 허용과 `isWaitStep`, 엔진 단계 판정 불변(건너뜀), `paceDeltaSec`용 `plannedSecFor(weight, elapsed, tolerance)`(평평한 구간 0). `RecipeEntity.valveJson` 삭제(1.json은 지금 파일 그대로). 기본 레시피 하리오 3단계·악마 6단계로 나눔(파생 일정 값은 2026-10-29와 같음), 하리오 마지막 단계 150 s(앱 가정 내려받기 30 s)·`finishAtSec` 120 → null. S8 `ValveDraft`·밸브 오류 3종·`valveAfterFinish` 삭제, `StepDraft.valveOpen`·`valvePreview`, `StepGramsNotIncreasing` → `StepGramsDecreasing`. 9.8 ui-design 맞출 계약 8항. 마일스톤 FIX-4·M8·M10·M11 갱신 | 사용자 결정(2026-10-30), 코디네이터 |
| 2026-10-30 | 7.1, 8.3, 9.6, 9.8 | 사용자 답 반영: 하리오 내려받기 30 s·마지막 단계 150 s·끝 2:30 확정, 기다리기 단계는 S1 카드에서 건너뛰고 지시를 다음 단계 이름에(M5 규칙 유지) 확정, 끝 "단계 추가" = 마지막 단계 체크(첫 단계 닫힘) 확정. S8 `onInsertValveStep(afterIndex)`(스위치만 바꾸는 기다리기 단계: g 복사·체크 반대·시각 가운데/+30 s/빈 칸·이름 "M:SS 스위치 열기/닫기", 스위치 꺼지면 무시·숨김). 9.8 계약 9항. M10 조건 추가 | 사용자 답(2026-10-30), 코디네이터 |
| 2026-10-30 | 9.6, 9.8 | ui-design 2026-10-30판(15절 11) 맞춤: 9.6 초안 파싱 순수 함수 `parseStepGrams`·`parseStepTimeSec`·`isWaitStep(prev, cur)`(VM 검증과 S8 머리 "· 기다리기"·설명 줄 "M:SS부터"·`StepTimeNotIncreasing` 앞 시각이 공유), 설명 줄·선택기 문구 인용을 ui-design에 맞춤("스위치 · 0:40부터 · 여기서 바꿈", "열림/닫힘"), 9.8 항목 6을 ui-design 판단으로 채우고 "단계 체크 밸브"를 맞춤 완료로. 마일스톤 M8(S1 번호·5줄·표식), M9(S6 표식·낭독), M10(파싱 함수 테스트·스크린샷 목록), M11(S10 기다리기 행·표식·낭독) 갱신 | ui-design.md 15절 11, 코디네이터 |
| 2026-10-30 | 7.1, 9.1, 9.3, 9.6, 9.7 | 설계 검증 반영: 9.1 `StatusAnnouncer` 알림 노드 포커스 순서(루트 `Box` 마지막 자식·`isTraversalGroup`, 첫 포커스 후보 아님, 실기기 확인 뒤 `traversalIndex`·`announceForAccessibility` 대체안, 순서 테스트), 9.3 매핑 1-1(Idle에서 끊음·실패 뒤 `BluetoothOff` → `None`, 직전 `ConnectionUi` 하나 기억), 7.1·9.6 단계 `targetAtSec` ≤ 3599(`StepNumberInvalid`), 9.7 S9 "다시 시도" = `onRetry`, `ExportFailed` "다시" = `CreateDocument` 다시. 마일스톤 FIX-5 추가(M8이 의존), M7R scope 수식어 순서(focusRing → clip), M8·M10·M11 조건 갱신 | 설계 검증(a11y·consistency) |
| 2026-10-30 | 9.1, 9.8 | 정합성 검사(design-review wf_c0c42c6a-ba7 뒤) 반영, 기준 ui-design 8.3·8.4·8.5: `StatusAnnouncer` 문구가 빈 동안 `contentDescription` 없음, content(`NavHost`) 쪽 `Box` `isTraversalGroup`, 실기기 기준 (가)·(나)와 대체안 조건, 순서 테스트에 "문구 빔이면 노드 없음". 9.8 "남은 차이(설계 검증 반영)" 4건을 맞춤 완료로, 배터리 낭독·무게 표시 범위 항목 추가. 마일스톤 FIX-5·M8 갱신 | 정합성 검사, ui-design.md 8.3·8.4·8.5·4.2, 코디네이터 |
| 2026-10-30 | 9.1, 9.2, 9.4, 9.8 | 설계 검증 반영(consistency 3건): 9.4 영점 직후 숫자는 받은 무게 그대로(C-0D-2, 응답 뒤 최대 약 300 ms 영점 전 값), 알림·모션 계기는 `TareDone` 유지(선택 (a)), 가짜 저울 영점 시퀀스 = `rx-tare-preweight-seq-001` 모양. 9.4·9.1 S3 단위 줄 출처 = `readout.reading`(`UnitUnknown` "확인 중", `UnitUnknownTimedOut` "알 수 없음", 그 밖 `displayUnit`), 새 VM 상태 없음. 9.2 화면 알림에 S8 넣기(화면 로컬, Polite). 9.8 ui-design 남은 차이 2건. 마일스톤 M8·M9·M10 조건 갱신 | 설계 검증(확정 지적), protocol.md C-0D-2·3.7, ui-design.md 8.3 |
| 2026-10-30 | 9.4, 9.8 | 영점 전 무게 시간을 "최대 약 300 ms" → "실측 약 300 ms(상한·Android 미확인, C-0D-2)"로 낮춤. 9.8 설계 검증 남은 차이 2건을 맞춤 완료로 옮김. M8 B4에 C-0D-2 프레임 수 기록 추가 | design-review wf_d23ceb63-8a5 postRevision(코디네이터) |
| 2026-11-02 | 9.4 | 사용자 결정(M8 리뷰 남은 지적): `ValveOpen`·`ValveClose` 알림을 Polite → Assertive. 시각이 정해진 행동 신호이고 터치 진동이 꺼진 TalkBack 사용자에게 유일한 비시각 신호라서(ui-design 8.3 동시 갱신) |
| 2026-11-02 | 0, 1, 2.2, 8.1, 8.3, 9.1, 10, 10.1 | P2(하네스 추천(잠정, 사용자 최종 검토 대기)): V60 기본·6888에 기다리기 단계 `("0:40까지 기다리기", 60, 40)`을 1단계 다음에 넣음(V60 3단계, 6888 5단계), 시드만 바꾸고 DB 버전 1·identityHash 그대로, 개발 기기는 `pm clear` 뒤 새 시드, P2 범위 밖 같은 모양(6888 2·3차 뒤, 테츠 카스야 기본) 기록. 10.1 디버그 데모 저울 새 절(사용자 지시): testFixtures 바이트 수준 저울 흉내 + `:core:ble` debug 가짜 transport를 실제 `BleScaleClient`에, 진입 = debug 런처 별칭 `DemoLauncher`, 데모 prefs·DB 분리, release 제외 확인 명령, 계측 테스트 의존성(2.2), 결정 표 D1~D7(하네스 추천(잠정)). 8.1 `TimemoreApp.container` `by lazy`, 9.1 debug 별칭 예외. P1(가로 밸브 칸 한 줄)은 ui-design 4.11만 바뀌고 이 문서 계약은 그대로. 마일스톤 FIX-6·DEMO-1~3 추가, M8·M9·M10 조건 문구, M9·M10·M11 의존에 FIX-6 | `_workspace/run_meta.json` P1·P2·P3, `_workspace/03_report_wf_f65760f7-857.md`, 사용자 지시(2026-11-02) |
| 2026-11-02 | 9.1 | P7(하네스 추천(잠정, 사용자 최종 검토 대기)): S8 `isBackEnabled` = `hasUnsavedChanges || state == Saving`. 저장 중 뒤로 가기로 화면을 떠나 저장 결과를 잃지 않게 한다(M10 리뷰, wf_1db05a96-4f1). 되돌리는 비용 낮음(조건 한 줄) |
| 2026-11-03 | 4.1, 4.2, 5.3, 5.4, 7.2, 8.1, 9.2 | 프로브 B(`_workspace/04_device_results.md`) 반영, 결정은 모두 하네스 추천(잠정, 사용자 최종 검토 대기): F-B4-1 자동 시작 확인 구간 500 ms·t₀ 소급·급감 취소(`autoStartConfirmNanos`·`weightDropGrams`), O-B8-1 무게 급감 시 유량 창 비움, C-MC-1 응답 짝짓기 "쓰기 직전 시각 이후 수신"·짝 없는 응답 로그·화면 안내 없음, A-5 첫 연결 133 + 화이트리스트 방송 → `PairingModeMaybe` 안내(`ScanCandidate.broadcastType`, `GattError.whitelistAdvertising`), F-B2-1 즉시 거부(500 ms 미만)도 영구 거부·`onResume`에서 `deniedOnce` 지움. A-1 Android·C-0B-2a 확정으로 `TODO(device)` 제거, 4.1에서 A-1·A-5 행 삭제(4.2로). 마일스톤 FIX-7·FIX-8·FIX-9 | `_workspace/run_meta.json` P14~P18, `04_device_b_B4.log`·`B8.log`·`F5_B1_B10.log` |
| 2026-11-03 | 8.3 | P2(V60 기본·6888 기다리기 단계, 시드만 수정) 사용자 확정. 잠정 표시를 확정으로 바꿈. 내용 변경 없음 |
| 2026-11-03 | 6 | F-TAB-1(Lenovo TB-9707F 스캔 결과 0건): 오프로드 필터 미지원이면 처음부터, 지원이면 필터 스캔 6 s 후보 0일 때 필터 없는 스캔으로 전환(프로세스 동안 유지), 스캔 진단 `EV` 로그 3종. 하네스 추천(잠정, 사용자 최종 검토 대기). 마일스톤 FIX-10 | `_workspace/04_device_results.md` F-TAB-1·F-TAB-2 |

## 13. Android 공식 스킬 점검 (android-skills 1.0.18, 2026-10-29)

프로젝트 스킬(`compose-app-dev`, `android-ble`)이 공식 스킬보다 우선한다. 공식 스킬 때문에 이미 정한 결정을 바꾸지 않았고, 바꿀 만한 것은 "제안"으로 남겼다.

| 공식 스킬 | 결과 | 내용 |
|-----------|------|------|
| `agp-9-upgrade` | 고침 | 2.2절: `kotlin-android` 카탈로그 항목 금지, kapt 금지, KGP 고정 대체 경로(`buildscript` classpath), Android 모듈 jvmTarget 생략, AGP 9 기본값 유지(`gradle.properties` 옵트아웃 금지, 모듈별 namespace 고유), `:app` `buildConfig = true`. KSP 2.3.12 ≥ 2.3.6 요구 충족 |
| `edge-to-edge` | 고침 | 9.1 앱 셸: `enableEdgeToEdge()`, `adjustResize`, `isNavigationBarContrastEnforced = false`, 인셋 한 방법(`contentWindowInsets = safeDrawing` + `innerPadding` + `consumeWindowInsets`, 입력 화면도 `imePadding()` 없음). ui-design.md 3절과 같다 |
| `navigation-3` | 제안(바꾸지 않음) | Navigation 3(`NavDisplay`, 상태로 든 back stack, 시트 Scene, 결과를 상태로 전달)이 Compose용 새 권장이다. 이미 정한 Navigation Compose 2.10 타입 안전 경로는 그대로 둔다. 바꾸면 9.1 경로 표·`savedStateHandle` 결과 전달(`saved_session_id`, `session_deleted`)·M7 내비게이션 조건을 다시 써야 한다. 화면 10개·탭 3개 규모에서 2.10으로 막히는 요구가 없다 |
| `testing-setup` | 일부 고침, 일부 제안 | 고침: Compose UI 동작 테스트로 뒤로 가기(D1·D6)·상태 복원(10절, M8·M9). 바꾸지 않음: DI 없으면 Hilt 설치 권장 → 수동 DI 유지(0절 결정). 스크린샷 도구로 Compose Preview Screenshot Testing 권장 → Roborazzi 유지(`compose-app-dev` 1절·`ui-design-review`, AGP 9.4는 test suite 방식 9.5.0-alpha03 미만이라 공식 쪽도 구식 플러그인). Room 계측 테스트 권장 → Robolectric 메모리 DB 유지(`compose-app-dev` 9절 허용, 기기 없이 verify.sh). JaCoCo·9개 창 크기 스크린샷은 요구사항에 없어 넣지 않는다 |
| `android-permissions-security` | 고침 | 6절 `SecurityException` 처리·권한 캐시 금지, 9.1 권한 요청 3단계와 영구 거부 → 앱 정보 설정. 내보낸 컴포넌트는 런처 `MainActivity` 하나 |
| `anr-audit-and-fix` | 고침 | 3절: `runBlocking`·`commit()` 금지, Room·CSV·`fromBlob`은 IO. 10 Hz 디코딩·`toBlob()`(최대 약 468 KB 복사, 10 s마다)은 메인에 둔다(측정 근거 없이 옮기지 않음, 3절 결정) |
| `navigation-event`, `adaptive`(ui-design이 읽음) | 고침 | 9.1: `BackHandler` → `NavigationBackHandler`, `NavigationSuiteScaffold` 너비 규칙. 의존성 2.2절 |
| `android-intent-security` | 고침 | 6절 블루투스 상태 수신기 `RECEIVER_NOT_EXPORTED`와 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, 8.4 CSV Uri 범위(persist 안 함, `FileProvider`·`PendingIntent` 없음), 9.1 받은 Intent extra 무시 |

프로젝트 스킬과 v1.0.4 실측이 달랐던 곳(3건 모두 스킬 정정됨(2026-10-29)):
- `compose-app-dev` 6절 컵 들어 올림 유량 0 → C-01-7(무게가 줄어도 0을 가정하지 않음). 스킬 정정됨(2026-10-29).
- `android-ble` 2절 스캔 필터 → A-2·A-3 확정으로 `ScanFilter` 사용(6절). 스킬 정정됨(2026-10-29).
- `android-ble` 7절 4단계·8절 CRC 검사 → F-8로 CRC 검사 없음. 스킬 정정됨(2026-10-29).
