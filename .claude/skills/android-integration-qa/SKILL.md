---
name: android-integration-qa
description: "Timemore Dot 저울 앱의 통합 정합성 검사 목록. 프로토콜 명세 ↔ 벡터 ↔ 코덱, UUID·명령 상수 ↔ 명세, 코덱 출력 단위 ↔ ScaleClient ↔ 추출 엔진 ↔ ViewModel ↔ 화면, 연결 상태 ↔ UI 분기, 매니페스트 권한 ↔ 런타임 요청, GATT 큐 ↔ close 경로, Room 엔티티 ↔ DAO ↔ CSV를 양쪽 코드를 함께 읽어 대조한다. android-qa-inspector가 따르는 검사 기준이며, 통합 버그 조사와 계약 불일치 질문에도 사용한다. 'QA 다시' 같은 마일스톤 재검사 실행 요청 자체는 timemore-app-orchestrator로 시작한다. 시각 디자인 리뷰는 ui-design-review를 쓴다."
---

# Android Integration QA — 연결 지점 대조 목록

빌드와 단위 테스트는 각 부품이 스스로 맞는지만 확인한다. 이 앱에서 실제로 사용자를 괴롭히는 결함은 부품 사이에서 생긴다: 코덱은 0.1 g 단위 정수를 내보내는데 엔진은 g로 읽거나, 연결 상태가 하나 늘었는데 화면 분기에는 없거나. 그래서 검사는 항상 **생산자와 소비자를 함께 열어** 대조한다.

## 절차

1. 마일스톤의 변경 파일 목록을 받는다.
2. 변경 파일이 아래 표의 어느 연결 지점에 속하는지 찾고, 반대편 코드를 `Grep`으로 찾아 함께 읽는다.
3. 표의 대조 항목을 확인하고 결과를 지적 또는 통과로 기록한다.
4. `android-build-verify`의 `scripts/verify.sh`를 실행해 구현자 보고와 비교하고, 종료 코드 0이면 `verifyPassed: true`로 반환한다. 구현자 보고와 다르면 `high` 지적이다.
5. 마일스톤 `acceptance` 항목마다 `pass | fail | unverified`를 판정한다. 실기기가 필요한 항목은 `unverified`로 두고 `device-verification` 시나리오 ID(B1~B12)를 적는다.

설계 단계(코드 없음)에서 호출되면 같은 표를 문서끼리 적용한다: `ui-design.md` 상태 ↔ `architecture.md` 상태 모델, 화면 표시 값 ↔ `protocol.md`가 제공하는 값, `milestones.json` ↔ 모든 화면·계약.

## 연결 지점 대조표

| ID | 생산자 | 소비자 | 대조할 것 |
|----|--------|--------|-----------|
| Q1 | `docs/spec/protocol.md` | `protocol-vectors.json` | 명세의 모든 명령·방향에 벡터가 있는가, 벡터 `expect` 필드가 명세의 필드 정의와 같은가 |
| Q2 | `protocol-vectors.json` | 코덱 테스트 | 테스트가 파일을 직접 읽는가(값 복사 금지), 모든 `id`가 실행되는가, `unconfirmed` 벡터 처리 방식이 명세와 맞는가 |
| Q3 | 명세 오프셋·엔디안·부호 | 코덱 디코더 | 무게 Int32 부호, 유량 Int16 부호, 타이머 uint16 무부호, big-endian, 길이 = payload만 |
| Q4 | 명세 CRC 정의 | 코덱 CRC 구현 | 변형 파라미터, 범위(헤더 포함 여부), 바이트 순서가 한 곳에 정의되고 명세와 같은가 |
| Q5 | 명세 UUID·opcode·cmd | `:core:ble` 상수 | 128비트 UUID 문자열 일치, 명령 바이트 일치, 하향 쓰기 타입이 Write without response |
| Q6 | 코덱 출력(raw) | 변환 계층 | raw → g 배율이 한 곳에만 있고 명세 변환식과 같은가, 미확정 배율에 명세 ID 주석이 있는가 |
| Q7 | `ScaleClient` 흐름 타입 | 추출 엔진 입력 | 단위(g, g/s), 시각(단조 나노초) 일치 |
| Q8 | 추출 엔진 `BrewState` | ViewModel `UiState` | 모든 필드가 전달되는가, 단위 변환이 이중으로 일어나지 않는가 |
| Q9 | 연결 상태 sealed 타입 | 화면 `when` 분기, `ui-design.md` 상태 표 | 모든 상태가 화면에 그려지는가, 분기의 상태가 실제로 도달 가능한가(상태를 만드는 코드가 있는가) |
| Q10 | 매니페스트 권한 | 런타임 요청 코드 | API 31 전후 분기, `maxSdkVersion`, 거부·영구 거부 처리, 블루투스 꺼짐 처리. `neverForLocation` 유무 ↔ `ACCESS_FINE_LOCATION`의 `maxSdkVersion` ↔ API 31 이상 위치 권한 요청·위치 서비스 확인이 서로 맞는가(`android-ble` 1절) |
| Q11 | GATT 작업 | 작업 큐 | 모든 쓰기·디스크립터·읽기·MTU가 큐를 거치는가, 큐 밖 직접 호출이 없는가 |
| Q12 | 연결 끊김·오류 경로 | `close()` | 모든 끊김·오류·사용자 해제 경로에서 `gatt.close()`가 불리는가, 끊긴 객체를 재사용하지 않는가 |
| Q13 | 알림 콜백 | API 33 전후 오버로드 | 두 오버로드 모두 재정의, 새 오버로드에 `super` 호출 없음, 옛 오버로드에 API 33 이상 조기 반환(중복 수신 방지), 옛 콜백에서 배열 복사 |
| Q14 | 연결 소유 범위 | ViewModel·화면 수명주기 | 연결이 앱 범위에 있는가, 화면 수집이 `collectAsStateWithLifecycle`인가 |
| Q15 | Room 엔티티 | DAO 쿼리, CSV 헤더, 화면 | 열 이름·단위·null 처리 일치, 스키마 내보내기와 마이그레이션 테스트 존재 |
| Q16 | 내비게이션 경로 정의 | 화면 이동 호출 | 모든 이동 대상이 등록돼 있고 인자 타입이 맞는가 |
| Q17 | 디버그 프레임 로그 | `device-verification` 형식 | 태그 `TimemoreBle`, `RX FFF1 <hex>` / `TX FFF2 <hex>` 형식, 릴리스 빌드에서 꺼짐 |
| Q18 | 저울 명령 응답 | 호출자 | 응답 `0x00` 실패와 시간 초과가 호출자에게 오류로 전달되는가(조용히 무시하지 않는가) |

## Android 공식 스킬 점검

대조표는 부품 사이의 계약을 본다. Android 플랫폼 규칙 위반(권한, Intent, 메인 스레드, 인셋)은 계약이 맞아도 생기므로 해당 파일이 바뀐 마일스톤에서 공식 스킬로 따로 점검한다.

| 바뀐 것 | 공식 스킬 | 보는 것 |
|---------|-----------|---------|
| 매니페스트, 권한 요청 코드 | `android-permissions-security` | 선언·런타임 요청·`exported`가 최소이고 맞는지 |
| CSV 내보내기·공유 | `android-intent-security` | Intent 대상·URI 권한·`FileProvider` 노출 범위 |
| BLE 콜백, Room·파일 I/O | `anr-audit-and-fix` | 메인 스레드에서 막히는 호출 |
| 화면 골격 | `edge-to-edge` | 인셋 누락으로 가려지는 내용 |
| 고빈도 갱신 화면 | `jetpack-compose-performance` | 재구성 범위(`compose-app-dev` 5절과 함께) |

불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다.

공식 스킬에서 나온 지적은 제목 앞에 `[android-skills:<이름>]`을 붙인다. 이 스킬의 대조표나 프로젝트 스킬과 충돌하면 프로젝트 쪽이 우선한다.

## 정적 검사 보조 명령

```bash
grep -rn "writeCharacteristic\|writeDescriptor\|readCharacteristic\|requestMtu" --include=*.kt .   # Q11: 큐 밖 호출 찾기
grep -rn "\.close()" --include=*.kt core/ble                                                        # Q12
grep -rn "TODO(device)" --include=*.kt .                                                             # Q6: 미확정 가정 위치
grep -rn "collectAsState(" --include=*.kt app                                                        # Q14: 수명주기 미인식 수집
```

명령 결과는 출발점일 뿐이다. 찾은 위치마다 실제 코드를 읽고 판단한다.

## 지적 작성

- 파일:줄, 생산자 쪽 인용, 소비자 쪽 인용, 실제로 생길 오작동(예: "100 g을 올리면 화면에 1000 g 표시"), 고칠 방법.
- 대조표 ID(Q1~Q18)를 제목 앞에 붙인다. 같은 원인은 한 건으로 묶는다.
