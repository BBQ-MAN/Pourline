# TimemoreDotScaleApp

TIMEMORE Dot 커피 저울(BLE)용 Android 앱. Kotlin + Jetpack Compose.

## 하네스: Timemore Dot 저울 앱 개발

**목표:** 공식 BLE 프로토콜을 근거로 명세 확정 → 설계와 ECC 설계·디자인 검증 → 마일스톤 구현·QA → 실기기 검증 순서로 앱을 만든다.

**호출 조건:** 이 앱의 개발 작업(프로토콜 명세, 설계, 기능 추가, 구현, 리뷰, 디자인 검증, 실기기 검증, 앱 산출물의 재실행·수정·보완)을 요청받으면 `timemore-app-orchestrator` 스킬을 사용한다. `.claude/` 하네스 파일 자체를 바꾸는 요청은 `harness:harness`·`harness:evolve`를 쓴다. 단순 질문에는 직접 답해도 된다.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
| --- | --- | --- | --- |
| 2026-10-28 | Harness v2로 처음 구성 | 전체 | - |
| 2026-10-28 | ECC 리뷰어로 설계·UI 디자인 검증 단계 추가, ui-designer 추가 | 오케스트레이터, ui-designer, ui-design-review | 사용자 요청("ecc를 이용해 디자인까지 검증") |
| 2026-10-28 | 하네스 검증(7관점·지적별 반박 검증, 확정 62건) 반영: 워크플로 fail-open 판정 수정, 스킬 로딩 경로, crc_probe·verify.sh 보강, BLE·프로토콜·접근성 기준 정정 | 워크플로 3종, 에이전트 6종, 스킬 7종 | 검증 결과 |
| 2026-10-29 | Android 공식 스킬 플러그인(android-skills, 프로젝트 범위) 연결: 주제별 공식 스킬 표와 불러오는 법·우선순위를 프로젝트 스킬에 추가(android-cli와 앱과 무관한 스킬은 제외) | 스킬 4종(compose-app-dev, android-ble, ui-design-review, android-integration-qa) | 사용자 요청("Android 스킬 연결"). 에이전트·워크플로가 모두 이 4개 스킬을 거치므로 한 곳에서 연결 |
| 2026-10-29 | 실기기 확정 사실과 어긋난 프로젝트 스킬 규칙 정정(스캔 필터, 수신 CRC 검사·재동기화, 컵 들어 올림 시 유량 0) + '확정된 protocol.md가 스킬 가정보다 우선' 원칙 추가 | 스킬 2종(android-ble, compose-app-dev) | 프로브 A(펌웨어 v1.0.4) 결과, architect 보고. 실기기 전에 쓴 가정이 구현을 틀리게 이끄는 것을 막는다 |
| 2026-10-29 | 디자인 기준 D6 개정: 측정값 숫자는 즉시, 값을 따라가는 모양 겹은 60 ms 이하 지연·오버슈트 없음·모션 줄이기 시 즉시로 허용 | 스킬 ui-design-review | 사용자 요청(유기적 디자인, 흐르는 움직임). 시안 B+A 선택과 함께 승인. 이전 'D6 값 변화에 애니메이션 없음'을 숫자에만 남기고 모양에는 상한을 둠 |
| 2026-11-03 | 실기기 기준에서 '저울 화면을 본다'는 전제 제거: B1은 공식 앱·알려진 무게와 비교, B4·B5·프로브 A 5번은 0x01 원시 프레임으로 확인, 원칙 한 줄 추가 | 스킬 device-verification | 프로브 B에서 Dot에 숫자 화면이 없음을 확인(사용자). 화면 비교 기준은 판정할 수 없어 B1이 '사용자 확인 대기'로 멈췄다 |
| 2026-11-05 | 경중별 모델 라우팅: 워크플로의 agentType 없는 추출·검증·리뷰 호출을 sonnet으로 고정(이전에는 세션 모델 opus 상속), android-qa-inspector opus→sonnet | protocol-verify.js, design-review.js, implement-milestones.js, android-qa-inspector | 사용자 지시. 주간 한도 96% 소진, `/usage`가 workflow-subagent·android-qa-inspector를 주원인으로 지목 |
