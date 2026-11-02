# Pourline (TimemoreDotScaleApp)

TIMEMORE Dot 커피 저울(BLE)용 Android 앱. Kotlin + Jetpack Compose. 앱 이름은 **Pourline**(2026-11-02 사용자 결정)이고, "Timemore Dot"은 지원 저울 기기 이름이다. 저장소 폴더·패키지(`com.timemoredot.app`)·내부 클래스/테마 이름·로그 태그는 그대로 둔다.

> **상태(2026-11-02):** 마일스톤 21개 중 16개 완료, 나머지 5개(M4·M7·M8·M9·M11)는 실기기 확인 대기. 결정 대기 20건과 검증 결과는 `docs/review/TimemoreDotScaleApp-review-20261102.html` 참고.

## MVP 범위(계획)

- 저울 스캔·연결·자동 재연결
- 실시간 무게, 앱에서 영점(tare)
- 추출 타이머, 유량(g/s) 그래프
- 레시피와 단계별 추출 가이드
- 추출 기록 저장(Room), CSV 내보내기

## 프로토콜

- 원문: TIMEMORE 공식 공개 문서 [Timemore Black Mirror SCALE BLE Protocol v1.0.3](https://github.com/TIMEMORE-COFFEE/open-scale-protocol). 사본이 `docs/protocol/source/`에 있다.
- 요약: GATT 서비스 `0xFFF0`, 알림 `0xFFF1`(저울 → 앱), 쓰기 `0xFFF2`(앱 → 저울, Write without response). 프레임 = 헤더 `A5 5A` + opcode + cmd + 길이(2바이트, big-endian) + payload + CRC-16(2바이트). 무게·유량·타이머 알림은 100 ms마다 온다.
- 원문에 빠져 있어 실기기로 확인해야 하는 항목:
  - CRC의 반사(reflect) 여부와 xorout
  - 무게 Int32의 단위와 oz 모드에서의 의미
  - 광고 데이터의 회사 ID
  - 20바이트를 넘는 프레임의 분할 방식
- 앱이 따르는 명세는 `docs/spec/protocol.md`, 테스트 벡터는 `docs/spec/protocol-vectors.json`이다. 둘 다 개발 1단계에서 생성된다.

## 개발 환경

| 항목 | 요구 사항 | 이 프로젝트에서 찾는 위치 |
|------|-----------|---------------------------|
| JDK | AGP가 요구하는 버전(최소 17) | `JAVA_HOME`. 없으면 Android Studio 내장 JBR(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) |
| Android SDK | compileSdk 플랫폼, build-tools, platform-tools | `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir` |
| Gradle | 래퍼 사용 | `./gradlew` (시스템 Gradle 쓰지 않음) |
| 실기기 | BLE 지원 Android 폰, USB/무선 디버깅 | `adb devices` |

BLE 저울 연결은 에뮬레이터로 검증할 수 없다. 자세한 설치 방법은 [env-setup.md](.claude/skills/android-build-verify/references/env-setup.md)에 있다.

`local.properties`는 컴퓨터마다 경로가 다르므로 커밋하지 않는다.

## 빌드와 검증

검증은 항상 아래 스크립트 하나로 한다. 사람이 돌릴 때와 에이전트가 돌릴 때 결과가 같아야 하기 때문이다.

```bash
.claude/skills/android-build-verify/scripts/verify.sh                       # test + lintDebug + assembleDebug
.claude/skills/android-build-verify/scripts/verify.sh :core:protocol:test   # 특정 태스크만
```

| 종료 코드 | 뜻 |
|-----------|-----|
| 0 | 통과 |
| 1 | 빌드·테스트·lint 실패. 로그는 `_workspace/build/verify_*.log` |
| 2 | 환경 없음(JDK, SDK, `./gradlew` 중 하나) |

앱 코드가 생긴 뒤에는 `./gradlew installDebug`로 실기기에 설치한다.

## 개발 방식: Claude Code 하네스

이 저장소는 Claude Code 에이전트 팀이 개발한다. Claude Code에서 "Timemore 앱 개발 시작", "다음 마일스톤", "디자인 검증 다시"처럼 요청하면 `timemore-app-orchestrator` 스킬이 아래 순서로 진행한다. 각 워크플로를 실행하기 전에는 예상 에이전트 수를 알리고 동의를 받는다.

| 단계 | 내용 | 산출물 |
|------|------|--------|
| 1. 프로토콜 확정 | 원문을 명세·테스트 벡터로 옮기고, 주장마다 독립 검증자 2명이 교차 검증 | `docs/spec/protocol.md`, `protocol-vectors.json` |
| 2. 설계 | 아키텍처·UI 디자인 작성 → ECC 리뷰어(`ecc:architect`, `ecc:a11y-architect`, 디자인 스킬)로 검증 → 사용자 승인 | `docs/spec/architecture.md`, `milestones.json`, `ui-design.md` |
| 3. 구현 | 마일스톤마다 구현 → 빌드 → QA·ECC 코드/디자인 리뷰 → 지적 검증 → 수정(최대 2회) | 코드, 테스트, `screenshots/` |
| 4. 실기기 검증 | 사용자와 함께 nRF Connect 캡처(프로브 A), 디버그 앱 시나리오(프로브 B) | `_workspace/04_device_*` |

마일스톤 상태는 `docs/spec/milestones.json`에 기록된다.

| 상태 | 뜻 |
|------|-----|
| `todo` | 아직 안 함 |
| `done` | 완료 |
| `awaiting-device` | 코드는 완료됐고 실기기 확인을 기다림 |
| `needs-decision` | 구현자와 검증자의 판단이 갈려 사람이 정해야 함 |
| `blocked` | 실패 |

하네스 구성:

| 위치 | 내용 |
|------|------|
| `.claude/agents/` | 전문 에이전트 6종: 프로토콜 분석, 아키텍처, UI 디자인, BLE 구현, 기능 구현, 통합 QA |
| `.claude/skills/` | 작업 규칙 스킬 8종과 워크플로 스크립트 3종 |
| `CLAUDE.md` | 하네스 호출 조건과 변경 이력 |

하네스 자체를 고칠 때는 `harness:harness` 또는 `harness:evolve` 스킬을 쓴다. 워크플로 판정 로직을 바꿨다면 회귀 테스트를 돌린다.

```bash
node .claude/skills/timemore-app-orchestrator/workflows/implement-milestones.test.cjs
```

## 실기기 도구

```bash
# 캡처한 저울 알림으로 CRC 방식 판별 (RX 줄만 사용)
python3 -I .claude/skills/scale-protocol/scripts/crc_probe.py --file _workspace/04_device_probeA.log
# 판별된 방식으로 명령 프레임 생성 (예: 영점)
python3 -I .claude/skills/scale-protocol/scripts/crc_probe.py --build 03 0D "" --variant <방식> --order <순서> --start <범위>
# 디자인 토큰 색 대비 계산
python3 -I .claude/skills/ui-design-review/scripts/contrast.py 1C1B1F:FFFBFE
```

디버그 빌드는 송수신 프레임을 `TimemoreBle` 태그로 남긴다(`RX FFF1 <hex>`, `TX FFF2 <hex>`). 수집 명령: `adb logcat -d -s TimemoreBle:V`

## 디렉터리

```
docs/protocol/source/   공식 프로토콜 원문 (수정하지 않음)
docs/spec/              명세·설계 문서 (개발 중 생성)
screenshots/            화면 상태별 스크린샷 (구현 중 생성)
_workspace/             에이전트 중간 산출물, 빌드 로그, 실기기 로그
.claude/                개발 하네스
```
