# 개발 환경 준비 (macOS 기준)

2026-10-28 이 컴퓨터 설정 완료: Android Studio(내장 JBR 25), SDK `~/Library/Android/sdk`(android-37, build-tools 36), Gradle 래퍼 9.8.2, `local.properties`. `verify.sh`가 종료 코드 2를 낼 때만 아래를 확인한다. 새 컴퓨터는 아래 순서로 준비한다. 설치는 사용자 컴퓨터를 바꾸는 작업이므로 에이전트는 명령을 제안하고, 실행은 사용자 승인 후에 한다.

## 1. JDK

AGP가 요구하는 JDK 버전(현재 AGP 릴리스 노트에서 확인, 최소 17)을 쓴다.

- Android Studio를 설치하면 내장 JBR을 쓸 수 있다: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- 또는 `brew install --cask temurin@17` (버전은 AGP 요구에 맞춘다)
- 확인: `java -version`. `/usr/bin/java`만 있고 실제 JDK가 없으면 macOS가 설치 안내만 띄운다.

## 2. Android SDK

둘 중 하나:

- Android Studio 설치 후 SDK Manager로 설치. 기본 경로 `~/Library/Android/sdk`
- 명령줄: `brew install --cask android-commandlinetools` 후
  `sdkmanager "platform-tools" "platforms;android-<compileSdk>" "build-tools;<버전>"`

경로 지정(설치 방법에 맞는 경로로, 둘 중 하나):

- Android Studio: `export ANDROID_HOME="$HOME/Library/Android/sdk"` (셸 프로필에 추가)
- Homebrew cask: `export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"`
- 또는 프로젝트 루트 `local.properties`에 `sdk.dir=<위 경로>` — 이 파일은 커밋하지 않는다

`verify.sh`는 경로 아래에 `platforms/`가 있어야 SDK로 인정한다.

## 3. Gradle 래퍼

Gradle이 설치되어 있지 않고 `verify.sh`는 `./gradlew`가 있어야 돈다. 그래서 래퍼는 구현 워크플로 안이 아니라 **3단계 사전 확인에서 메인 에이전트가 사용자 승인을 받아** 다음 중 하나로 만든다.

- Android Studio에서 새 프로젝트를 만든 뒤 래퍼 파일(`gradlew`, `gradle/wrapper/*`)을 가져온다.
- `brew install gradle` 후 프로젝트 루트에서 `gradle wrapper --gradle-version <AGP가 요구하는 버전>`

래퍼가 생긴 뒤에는 `./gradlew`만 쓴다. 시스템 Gradle 버전과 섞으면 빌드 결과가 달라진다.

## 4. adb와 실기기

- `adb`는 `$ANDROID_HOME/platform-tools`에 있다. `PATH`에 추가한다.
- 휴대폰: 개발자 옵션 → USB 디버깅 켜기, 또는 무선 디버깅(`adb pair <ip:port>` 후 `adb connect`).
- 확인: `adb devices`에 기기가 `device` 상태로 보여야 한다.
- BLE 저울 연결은 에뮬레이터로 검증할 수 없다. 실기기가 필요하다.

## 5. 확인 순서

```bash
java -version
echo "$ANDROID_HOME" && ls "$ANDROID_HOME/platform-tools"
adb devices
.claude/skills/android-build-verify/scripts/verify.sh
```
