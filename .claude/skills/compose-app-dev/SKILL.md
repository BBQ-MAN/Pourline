---
name: compose-app-dev
description: "Timemore Dot 저울 앱의 Kotlin·Jetpack Compose 구현 규칙: Gradle 골격과 버전 카탈로그, 모듈 구성, UDF 상태 관리(ViewModel + StateFlow), 추출 엔진(타이머·유량·자동 시작·레시피 단계), 유량 그래프, Room 기록·레시피 저장, CSV 내보내기, 계층별 테스트. 구현자·설계자·리뷰어가 따르는 기준이며, 모듈·계층 결정은 ecc:android-clean-architecture보다 이 스킬 2절을 우선한다. 새 기능·화면 추가 요청 자체는 timemore-app-orchestrator로 시작한다. BLE 연결 코드는 android-ble, 시각 디자인 기준은 ui-design-review를 쓴다."
---

# Compose App Dev — 앱 구현 규칙

추출 중 앱은 100 ms마다 새 무게를 받는다. 이 값이 화면 전체를 다시 그리거나, 회전·백그라운드 전환에 끊기거나, 단위가 계층마다 달라지면 사용자는 커피를 망친다. 아래 규칙은 이 세 가지를 막는 데 맞춰져 있다.

## 1. 프로젝트 골격

- Gradle Kotlin DSL + `gradle/libs.versions.toml` 버전 카탈로그. 모든 의존성은 카탈로그를 거친다.
- 버전(AGP, Kotlin, Compose BOM, Room, Hilt, Roborazzi 등)은 기억으로 적지 않는다. 골격을 만들 때 공식 릴리스 노트에서 현재 안정 버전을 확인해 고정하고, 확인한 날짜를 `architecture.md`에 적는다. 오래된 기억 버전은 컴파일러·플러그인 호환 오류의 흔한 원인이다.
- `./gradlew` 래퍼를 커밋한다. 환경 준비는 `android-build-verify`의 `references/env-setup.md`를 따른다.
- 기본 의존성: Compose + Material 3, Navigation Compose, Lifecycle(`collectAsStateWithLifecycle`), Hilt(작은 앱이라 수동 DI가 더 단순하면 그 이유를 적고 수동 DI), Room + KSP, kotlinx-coroutines, kotlinx-serialization(테스트에서 벡터 JSON 읽기).
- 테스트 의존성: JUnit, kotlinx-coroutines-test, Turbine, Robolectric + Roborazzi(스크린샷).

## 2. 모듈 구성(기본안)

| 모듈 | 종류 | 내용 | 이유 |
|------|------|------|------|
| `:core:protocol` | 순수 Kotlin(JVM) | 프레임 코덱, 재조립기, CRC | Android 없이 수 초 안에 벡터 테스트 |
| `:core:brew` | 순수 Kotlin(JVM) | 추출 엔진, 레시피 모델 | 합성 시계열로 빠르게 테스트 |
| `:core:ble` | Android 라이브러리 | 스캔·연결·작업 큐, `ScaleClient` 구현, 가짜 저울 | Android BLE API 격리 |
| `:app` | Android 앱 | 화면, ViewModel, Room, DI, 내비게이션 | 나머지는 하나로 둔다 |

기능이 늘어 `:app` 빌드가 느려질 때만 `:feature:*`로 나눈다. 나누는 이유가 없으면 나누지 않는다.

## 3. 단위와 시간

- 내부 무게 단위는 g, 유량은 g/s로 통일한다. 저울 raw 값 → g 변환은 `:core:protocol` 경계에서 한 번만 하며, 마지막으로 받은 0x06 단위와 함께 변환한다(oz 모드에서 raw 의미가 바뀔 수 있다. 명세 §3 변환식을 따른다). oz는 표시할 때만 변환한다.
- 시간은 `SystemClock.elapsedRealtimeNanos()` 기반 단조 시계. 벽시계(`Instant.now()`)는 기록의 시작 시각 표기에만 쓴다. 벽시계는 사용자가 시간을 바꾸거나 네트워크 동기화로 뒤로 갈 수 있다.
- 계층 경계의 타입에 단위를 드러낸다(예: `weightGrams: Double`, `atNanos: Long`).

## 4. 상태 관리

- 화면마다 `data class XxxUiState` 하나, ViewModel은 `StateFlow<XxxUiState>`를 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 초기값)`으로 내보낸다.
- 사용자 동작은 ViewModel 함수 호출(`onTareClick()`)로 받는다. 일회성 이벤트(내보내기 완료 알림 등)가 꼭 필요할 때만 `Channel`을 쓴다.
- 화면은 `collectAsStateWithLifecycle()`로 수집한다. 백그라운드에서 수집을 멈춰야 배터리와 CPU를 아낀다.
- 연결 상태 모델은 `ui-design.md`의 화면 상태와 1:1로 맞춘다(예: `BluetoothOff`, `PermissionMissing`, `Scanning`, `Connecting`, `Connected`, `Reconnecting`, `Failed(reason)`).

## 5. 고빈도 값 표시

- 무게·유량·타이머는 가장 작은 컴포저블에서 읽는다. 상위에서 읽어 하위로 값으로 넘기면 100 ms마다 화면 전체가 재구성된다. 람다(`() -> Double`)로 넘기거나 상태 읽기를 아래로 내린다.
- 유량 그래프는 `Canvas`에 직접 그린다. 샘플은 고정 크기 링 버퍼(예: 10 Hz × 10분)에 담고 그릴 때 새 리스트를 만들지 않는다.
- 숫자 변화에 애니메이션을 넣지 않는다. 값이 늦게 보이면 사용자가 목표 무게를 넘겨 붓는다.
- 추출 화면에서는 화면 켜짐을 유지한다(`DisposableEffect`에서 `view.keepScreenOn = true` 설정 후 해제).

## 6. 추출 엔진(`:core:brew`)

입력은 `ScaleReading` 스트림(무게, 저울 유량, 저울 타이머, 과부하, 수신 시각)과 사용자 명령, 출력은 `BrewState`다. 순수 함수 또는 상태 기계로 만들어 시간과 입력을 주입한다.

- **타이머**: 저울 타이머는 1초 해상도라 화면용 소수 초는 앱 단조 시계로 만든다. 시작·정지·초기화를 저울 타이머 명령(`0x02`)과 동기화할지 앱 자체 타이머만 쓸지 `architecture.md`에 결정과 이유를 적는다.
- **유량**: 저울이 주는 유량(0.1 g/s)을 기본으로 쓴다. 표시가 흔들리면 앱에서 짧은 창(약 1초) 평활만 더한다. 저울 유량은 `clamp(w(t) − w(t−1 s), 0, 999)`라 음수가 없고, 무게가 줄어드는 중에도 0이 아닐 수 있다(실기기 확정, `protocol.md` C-01-5/C-01-7). 앱이 무게 감소를 보고 유량을 0으로 덮어쓰지 않는다.
- **자동 시작**: 영점 뒤 무게가 임계값(설정 가능, 기본 예: 1 g)을 넘게 늘면 타이머를 시작한다. 영점 직후 흔들림을 걸러야 하므로 짧은 지속 조건(예: 2회 연속 샘플)을 둔다.
- **레시피 단계**: 단계 = (누적 목표 무게 g, 목표 시각 s, 이름). 현재 단계, 남은 무게, 목표 대비 앞섬·늦음을 계산한다. 마지막 단계 도달 후 일정 시간 무게 변화가 없으면 완료로 본다.
- **과부하·연결 끊김**: 과부하 플래그나 끊김 동안의 샘플은 기록하되 표시하고, 유량 계산에서 뺀다.
- 테스트: 합성 시계열로 정상 추출, 영점 직후 흔들림, 컵 들어 올림(유량을 덮어쓰지 않음), 알림 누락(300 ms 공백), 끊김 후 재연결, 과부하를 각각 검증한다.

## 7. 저장(Room)

- 엔티티 기본안: `Recipe`(+ 단계), `BrewSession`(시작 시각, 레시피, 원두량, 최종 무게, 소요 시간, 비율, 메모), 샘플(시각 오프셋 ms, 무게 g, 유량 g/s). 샘플은 세션 하나에 수천 개이므로 행 단위 저장과 압축 BLOB 중 하나를 골라 이유를 적는다.
- `room.schemaDirectory`로 스키마를 내보내고, 버전을 올릴 때마다 마이그레이션과 마이그레이션 테스트를 쓴다. 릴리스에서 `fallbackToDestructiveMigration`을 쓰지 않는다. 사용자의 추출 기록이 지워진다.
- 추출 중 기록은 끝날 때 한 번에 쓰고, 중간 크래시 대비로 주기적(예: 10초) 임시 저장을 한다.

## 8. CSV 내보내기

- 저장소 접근 프레임워크(`ACTION_CREATE_DOCUMENT`)나 `FileProvider` 공유로 내보낸다. 저장소 권한을 요청하지 않는다.
- 열 이름·순서·단위를 `architecture.md`에 고정하고, 내보내기 테스트가 그 헤더를 확인한다.

## 9. 테스트 배치

| 대상 | 방법 |
|------|------|
| 코덱·재조립 | JVM 테스트, 벡터 파일 |
| 추출 엔진 | JVM 테스트, 합성 시계열, 가짜 시계 |
| ViewModel | `runTest` + Turbine + 가짜 저울 |
| Room DAO·마이그레이션 | Robolectric 또는 계측 테스트 |
| 화면 | Roborazzi 스크린샷(상태별, `ui-design-review` 스킬) |

실기기에서만 확인할 수 있는 항목은 `device-verification` 스킬의 시나리오로 넘기고, 단위 테스트로 흉내 낸 것을 실기기 확인으로 보고하지 않는다.

## 10. Android 공식 스킬(android-skills 플러그인)

AGP 9·edge-to-edge·Navigation 3처럼 최근에 바뀐 영역은 기억으로 구현하면 틀리기 쉽다. 아래 주제를 다룰 때 해당 공식 스킬을 먼저 읽는다.

| 주제 | 공식 스킬 | 언제 |
|------|-----------|------|
| Gradle·AGP 골격 | `agp-9-upgrade` | 1절 골격을 만들거나 AGP·Kotlin 플러그인 버전을 정할 때(빌트인 Kotlin, KSP) |
| 시스템 바·인셋 | `edge-to-edge` | 화면 골격(Scaffold, 인셋 처리)을 만들 때. targetSdk 35 이상은 edge-to-edge가 강제된다 |
| 가로·큰 화면 | `adaptive` | 가로 배치, `NavigationRail` 전환, 창 크기별 레이아웃 |
| 화면 이동 | `navigation-3`, `navigation-event` | 내비게이션을 새로 고르거나 뒤로 가기(Predictive Back)를 구현할 때 |
| 재구성 비용 | `jetpack-compose-performance` | 5절 고빈도 값 표시, 그래프 그리기 |
| 테스트 구성 | `testing-setup` | 9절 테스트 배치를 처음 만들 때 |
| 내보내기 Intent | `android-intent-security` | 8절 CSV 내보내기·공유(`ACTION_CREATE_DOCUMENT`, 공유 Intent, `FileProvider`) |
| 릴리스 축소 | `r8-analyzer` | 릴리스 빌드의 R8·keep 규칙을 다룰 때 |

불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다.

공식 스킬은 Android 일반 모범 사례다. 이 스킬과 충돌하면 이 스킬이 우선한다. 이 스킬에는 실기기로 확인한 저울 제약과 `architecture.md`에서 이미 정한 결정이 들어 있기 때문이다. 공식 스킬 때문에 이미 정한 결정(예: 내비게이션 라이브러리, DI 방식)을 바꾸고 싶으면 코드에서 바꾸지 말고 지적·설계 변경으로 올린다.
