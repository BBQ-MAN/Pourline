---
name: android-build-verify
description: "Timemore Dot 저울 앱의 빌드·단위 테스트·lint를 scripts/verify.sh 하나로 실행하고 결과(PASS/FAIL, 로그 경로)를 판정한다. 개발 환경(JDK, Android SDK, Gradle 래퍼, adb) 준비 방법도 담는다. 구현·수정 후 검증, 빌드 실패 재현·확인, 환경 설정 문제에 사용한다. 빌드 오류를 ecc:gradle-build 등으로 고치더라도 재현과 완료 판정은 이 스크립트로 하고 compose-app-dev 1절 버전 규칙을 지킨다. 실기기 동작 확인은 device-verification을 쓴다."
---

# Android Build Verify — 빌드 검증

구현자, QA, 오케스트레이터가 같은 명령으로 검증해야 "제 환경에서는 됐다"는 보고가 사라진다. 그래서 검증은 항상 이 스크립트로 한다.

## 실행

```bash
.claude/skills/android-build-verify/scripts/verify.sh                       # 기본: test lintDebug assembleDebug
.claude/skills/android-build-verify/scripts/verify.sh :core:protocol:test   # 특정 태스크만
.claude/skills/android-build-verify/scripts/verify.sh recordRoborazziDebug  # 스크린샷 기록
```

| 종료 코드 | 뜻 | 할 일 |
|-----------|-----|-------|
| 0 | `PASS` | 결과 줄을 그대로 보고 |
| 1 | `FAIL` | 출력된 첫 오류부터 고친다. 전체 로그는 `_workspace/build/verify_*.log` |
| 2 | `ENV` 환경 없음(JDK, SDK, `./gradlew` 중 하나) | 출력이 가리키는 `references/env-setup.md` 절로 환경을 준비한다. 준비할 수 없으면 실패로 보고. 워크플로에서는 `verifyExit: 2`로 반환해 즉시 멈추게 한다 |

- 종료 코드 0일 때만 통과로 보고한다. 일부 태스크만 돌린 결과를 전체 통과로 적지 않는다. 어떤 태스크를 돌렸는지 결과 줄에 나온다.
- 테스트를 지우거나 `@Ignore`, lint 기준선(baseline)에 새 경고를 밀어 넣어 통과시키지 않는다. 기준선을 바꿔야 하면 이유를 보고서에 적는다.
- 기본 태스크의 `test`는 Android 모듈의 debug·release 단위 테스트와 순수 JVM 모듈 테스트를 함께 돈다. `testDebugUnitTest`만 쓰면 `:core:protocol` 같은 JVM 모듈 테스트가 빠진다.

## 환경

`references/env-setup.md` — JDK, Android SDK, 래퍼, adb, 실기기 연결 준비. `verify.sh`가 종료 코드 2를 낼 때만 읽는다.
