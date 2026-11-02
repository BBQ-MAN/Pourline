---
name: timemore-app-orchestrator
description: "Timemore Dot 커피 저울 Android 앱(Kotlin + Compose)의 개발을 조율한다. 프로토콜 명세 확정 → 아키텍처·UI 디자인 설계와 ECC 설계·디자인 검증 → 마일스톤별 구현·QA·ECC 코드/디자인 리뷰 → 실기기 검증까지 진행한다. '앱 개발 시작', '다음 마일스톤', 'M3 구현', '기능 추가', '화면 추가', '프로토콜 명세 갱신', '디자인 검증 다시', 'QA 다시', '실기기 로그 반영', 그리고 앱 코드·docs/spec 산출물에 대한 '재실행', '이어서', '수정', '보완' 요청에 사용한다. .claude/ 아래 하네스 파일(에이전트·스킬·워크플로)을 바꾸는 요청은 harness:harness 또는 harness:evolve를 쓴다. 단순 질문이나 한두 줄 수정은 직접 처리한다."
---

# Timemore 앱 오케스트레이터

## 실행 모드: 혼합

| 단계 | 실행 모드 | 이유 |
|------|-----------|------|
| 0 상태 확인 | 메인 직접 | 어디서 시작할지 정하고 실행 동의를 받는다 |
| 1 프로토콜 확정 | 워크플로 `workflows/protocol-verify.js` | 작성자와 분리된 추출자가 뽑은 주장을 코드로 나눠 교차 검증한다 |
| 2 설계 | 서브에이전트 2명 병렬 → 워크플로 `workflows/design-review.js` → 사용자 승인 | 설계는 독립 작업, 검증은 정해진 관점 × 지적 |
| 3 구현 | 워크플로 `workflows/implement-milestones.js` | 구현 → 리뷰 → 검증 → 수정 반복과 통과 조건을 코드로 정한다 |
| 4 실기기 검증 | 메인 + 사용자 (`device-verification` 스킬) | 저울을 사람이 조작해야 한다 |

워크플로 호출은 모두 `Workflow({ scriptPath: ".claude/skills/timemore-app-orchestrator/workflows/<파일>", args })` 형식이다.

## 에이전트 구성

| 역할 | 유형 | 모델 | 스킬 | 산출물 |
|------|------|------|------|--------|
| 프로토콜 명세 | `scale-protocol-analyst` | opus | scale-protocol | `docs/spec/protocol.md`, `protocol-vectors.json` |
| 주장 추출·검증 | 기본 워크플로 에이전트 | 세션 모델 | — | 주장·판정(반환값) |
| 아키텍처 | `android-architect` | opus | compose-app-dev, android-ble | `docs/spec/architecture.md`, `milestones.json` |
| UI 디자인 | `ui-designer` | opus | ui-design-review + ECC 디자인 스킬 | `docs/spec/ui-design.md` |
| BLE·코덱 구현 | `ble-engineer` | opus | android-ble, android-build-verify | 코드, `_workspace/03_ble-engineer_*` |
| 기능·화면 구현 | `feature-engineer` | sonnet(마일스톤 `model`로 상향) | compose-app-dev, ui-design-review, android-build-verify | 코드, 스크린샷, `_workspace/03_feature-engineer_*` |
| 통합 QA | `android-qa-inspector` | opus | android-integration-qa | 지적·완료 조건 판정·`verifyPassed`(반환값) |
| 설계 리뷰(ECC) | `ecc:architect` | ECC 정의 | ecc:android-clean-architecture(참고) | 지적 목록 |
| 접근성 리뷰(ECC) | `ecc:a11y-architect` | ECC 정의 | ecc:accessibility | 지적 목록 |
| 코드 리뷰(ECC) | `ecc:kotlin-reviewer` | ECC 정의 | — | 지적 목록 |
| 시각 디자인 리뷰 | 기본 워크플로 에이전트 | 세션 모델 | ecc:make-interfaces-feel-better, ecc:design-system | 지적 목록 |

ECC 에이전트에는 Skill 도구가 없을 수 있다. 워크플로 프롬프트가 스킬 파일 경로를 함께 주므로 Read로 읽는다.

## 0단계: 상태 확인과 실행 동의

1. 확인: `docs/protocol/source/`, `docs/spec/{protocol.md, architecture.md, ui-design.md, milestones.json}`, `_workspace/run_meta.json`, `_workspace/04_device_*`.
2. 시작 단계를 정한다.
   - `protocol.md` 없음 → 1단계
   - `architecture.md` 또는 `ui-design.md` 없음 → 2단계
   - 사용자가 `milestones.json`에 없는 기능·화면을 요청 → 2단계(architect가 마일스톤 추가, 화면이면 ui-designer가 `ui-design.md` 갱신) → `design-review`(`only` 관점) → 승인 → 3단계
   - 이미 `done`/`awaiting-device`인 마일스톤의 "QA 다시", "디자인 검증 다시", 그리고 리뷰어 누락(`missingReviewers`)만으로 `blocked`가 된 마일스톤 → 3단계를 `reviewOnly: true`로(해당 마일스톤만, UI면 `_workspace/03_report_*.md`에 기록한 `screenshots`를, 없으면 `screenshots/` 아래 해당 화면 파일을 마일스톤 객체의 `screenshots`에 넣어 넘긴다)
   - `milestones.json`에 진행 가능한 `todo` 있음 → 3단계(사용자가 특정 마일스톤을 지정하면 그것만)
   - `needs-decision` 마일스톤 있음 → 갈린 지적과 양쪽 근거를 보여 주고 사용자 결정(수정 지시 / 지적 기각 후 `done`)을 받는다
   - `blocked`만 남음 → 원인을 보고하고 재시도·재계획(architect) 중 사용자 결정을 받는다
   - 실기기 로그를 받았거나 실기기 확인 요청 → 4단계
   - "설계 다시", "디자인만 다시" → 2단계의 해당 부분만
3. 원문이 새 판으로 바뀌는 등 처음부터 다시 해야 하면 `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 옮기고 시작한다. `docs/spec/`은 옮기지 않는다(각 에이전트가 기존 문서를 읽고 고친다).
4. **실행 동의:** 시작 단계, 대상 마일스톤, 실행할 워크플로와 대략의 에이전트 수(아래 표)를 사용자에게 알리고 동의를 받는다. 같은 세션에서 동의받은 범위 안의 반복 실행(수정 후 재실행 등)은 다시 묻지 않는다.

| 워크플로 | 대략의 에이전트 수 |
|----------|---------------------|
| protocol-verify | 3 + 주장 수 × 2. 공식 문서 v1.0.3 전체는 주장 약 200건 → 약 420(2026-10-28 실측 419, 약 30분). 실기기 로그 반영처럼 일부만 바뀔 때도 전체를 다시 검증한다 |
| design-review | 4 + 지적 수 + 반영 2 + 재확인 1 |
| implement-milestones | 마일스톤당 (구현 1 + 리뷰 2~4 + 검증 n) × (1 + 수정 라운드) |

### 재개(resume) 주의

`resumeFromRunId`는 프롬프트가 같은 `agent()` 호출의 **캐시된 결과**를 돌려준다. 이 하네스의 프롬프트에는 문서 내용이 아니라 경로만 들어 있으므로, 문서나 코드가 바뀐 뒤 재개하면 바뀌기 전의 결과가 재사용된다. 재개는 같은 실행이 중간에 끊겼고 그 뒤 파일을 고치지 않았을 때만 쓴다. 그 외에는 새로 실행한다.

`_workspace/run_meta.json` 형식: `{ "runs": [ { "workflow": "protocol-verify", "runId": "wf_...", "date": "YYYY-MM-DD", "args": {...} } ] }` — 실행마다 항목을 덧붙인다.

## 1단계: 프로토콜 확정 — 실행 모드: 워크플로

1. `docs/protocol/source/`에 원문이 있는지 확인한다. 없으면 사용자에게 받는다.
2. `Workflow({ scriptPath: ".claude/skills/timemore-app-orchestrator/workflows/protocol-verify.js", args: { feedback?, deviceLogs? } })`
3. 결과 보고: `confirmed / unconfirmed / refuted` 개수, 미확정 항목 목록, `partialVerdicts`(검증자 결과가 빠진 주장 수), `mismatches`(판정과 명세 표시가 다른 항목), `untracked`(검증 목록 밖이라 미확정으로 내린 항목). `error`가 있으면 성공으로 보지 않는다. `mismatches`가 있으면 해당 항목을 직접 확인해 명세를 맞추거나 다시 실행한다.
4. 미확정 항목(특히 CRC 변형, 무게 배율)이 있으면 **4단계 프로브 A를 권한다.** 앱 없이 nRF Connect로 할 수 있고, 코덱 마일스톤 전에 끝내면 재작업이 줄어든다. 사용자가 미루면 2·3단계를 진행하되 코덱 마일스톤의 관련 완료 조건은 `unverified`로 남고 해당 마일스톤은 `awaiting-device`가 된다.

## 2단계: 설계 — 실행 모드: 서브에이전트 → 워크플로 → 사용자 승인

1. **설계 작성(서브에이전트, 한 메시지에서 병렬):**
   - `Agent({ subagent_type: "android-architect", description: "아키텍처·마일스톤 설계", prompt: "<요구사항 요약> docs/spec/protocol.md 를 읽고 architecture.md 와 milestones.json 을 작성하라. 화면 마일스톤은 ui: true, 수치 로직이 큰 feature-engineer 마일스톤은 model: \"opus\"를 붙인다." })`
   - `Agent({ subagent_type: "ui-designer", description: "UI 디자인 명세 작성", prompt: "<요구사항 요약> docs/spec/protocol.md 를 읽고 ui-design.md 를 작성하라." })`
   - 요구사항 요약(MVP): 스캔·연결·재연결, 실시간 무게·영점, 타이머·유량 그래프, 레시피·추출 가이드, 추출 기록 저장·CSV 내보내기. 스택 Kotlin + Jetpack Compose.
2. **ECC 설계 검증(워크플로):** `Workflow({ scriptPath: ".claude/skills/timemore-app-orchestrator/workflows/design-review.js" })`. 일부 관점만 다시 할 때는 `args: { only: ["a11y", "visual"] }`.
3. 결과 처리:
   - `failedReviewers`가 있으면 승인 전에 그 관점만 `only`로 다시 돌린다. 리뷰가 빠진 설계를 "지적 없음"으로 보고하지 않는다.
   - `protocolIssues`는 1단계로 넘긴다(`feedback`에 지적을 넣는다).
   - `failedRevisions`(반영 에이전트 실패)는 해당 설계자를 다시 호출해 반영한다.
   - `postRevisionFailed`가 true면 `only: ["consistency"]`로 정합성만 다시 돌린다.
   - `declined`(설계자가 반영을 거부한 지적), `unapplied`(반영 여부를 보고하지 않은 지적 — 문서를 직접 열어 확인), `uncertain`, `postRevision`(반영 후 새로 생긴 불일치)은 사용자 판단 항목으로 보고한다.
4. **사용자 승인:** 핵심 결정(모듈, 연결 소유, 타이머·유량 출처), 마일스톤 목록, 화면 구성과 토큰·대비 표 요약, 남은 판단 항목을 보여 주고 승인을 받는다. 승인 전에는 3단계로 가지 않는다. 피드백이 오면 해당 설계자를 새로 호출해 반영하고 2번을 다시 실행한다(재개 말고 새로 실행).

## 3단계: 구현 — 실행 모드: 워크플로

1. **환경 사전 확인**(`android-build-verify`의 `references/env-setup.md` 5절 순서):
   - `java -version`, SDK 경로(`ANDROID_HOME` 또는 `local.properties`의 `sdk.dir` 아래 `platforms/`).
   - `./gradlew`가 없으면(첫 실행) `command -v gradle`을 확인하고, 사용자 승인을 받아 env-setup 3절대로 래퍼를 만든다. `verify.sh`는 래퍼 없이 돌지 않으므로 M1 워크플로 전에 해야 한다.
   - 무엇이든 없으면 env-setup을 보여 주고 사용자가 준비하게 한다. 설치 명령은 사용자 승인 없이 실행하지 않는다.
2. **마일스톤 선택:** `milestones.json`에서 `todo`이고 `dependsOn`이 모두 `done`·`awaiting-device`이거나 같은 묶음 앞쪽에 있는 항목을 의존 순서(위상 순서)로 고른다. 기본은 한 번에 최대 3개다. 앞 마일스톤 결과가 뒤 계획을 바꿀 수 있어서다. 사용자가 "전부"라고 하면 모두 넘긴다.
3. `Workflow({ scriptPath: ".claude/skills/timemore-app-orchestrator/workflows/implement-milestones.js", args: { milestones: [...], maxFixRounds: 2 } })`
4. **결과 처리:**
   - 반환값에 `error: "ENV"`가 있으면 1번(환경)으로 돌아간다. 다시 실행하지 않는다. 다른 `error`(예: 완료 조건 없는 마일스톤)는 메시지대로 architect에게 보완을 맡긴다.
   - `milestones.json`의 `status` 갱신: `done` → `done`, `awaiting-device` → `awaiting-device`, `needs-decision` → `needs-decision`, `failed` → `blocked`, `env-missing`·`skipped` → `todo` 유지(환경을 고치거나 선행 마일스톤이 풀리면 다시 선택된다).
   - `_workspace/03_report_{runId}.md`에 마일스톤별 상태, `missingReviewers`, 남은 지적(`residual`, `uncertain`, `lowFindings`), 구현자가 거부한 지적(`rejected`), 완료 조건 판정, `screenshots`를 적고 사용자에게 보고한다. 리뷰어가 빠진 마일스톤은 통과로 보고하지 않는다.
   - `acceptance`의 `unverified` 항목은 4단계 프로브 B 시나리오로 연결해 보고한다.
5. 실패한 마일스톤은 원인(빌드, 확정 지적, 완료 조건 실패, 리뷰어 누락, 에이전트 결과 없음)과 함께 보고하고, 사용자 지시에 따라 같은 마일스톤만 다시 넘기거나 architect에게 재계획을 맡긴다.

## 4단계: 실기기 검증 — 실행 모드: 메인 + 사용자

`device-verification` 스킬을 불러와 따른다.

- **프로브 A**(구현 전 가능): 사용자에게 단계별로 안내하고 캡처를 `_workspace/04_device_probeA_*.log`로 저장한다. `crc_probe.py` 판별 결과와 함께 1단계 워크플로를 `args: { deviceLogs: [...] }`로 새로 실행한다.
- **프로브 B**(화면 마일스톤 후): 시나리오별 logcat을 저장하고 `_workspace/04_device_results.md`에 결과를 적는다. 통과한 시나리오에 해당하는 `awaiting-device` 마일스톤은 `done`으로 바꾼다.
- 결과 분류: 원시 프레임이 명세와 다르면 → 1단계(`deviceLogs`) 후 코덱 마일스톤을 `todo`로 되돌리거나 수정 마일스톤 추가. 프레임은 맞고 앱 동작이 틀리면 → 수정 마일스톤 `{ id: "FIX-<n>", title, owner, dependsOn: [], ui, scope, acceptance, status: "todo" }`를 `milestones.json`에 추가하고 3단계.

## 데이터 전달

| 경로 | 만드는 쪽 | 읽는 쪽 |
|------|-----------|---------|
| `docs/protocol/source/*` | 사용자(원문) | analyst, 추출자, 검증자 |
| `docs/spec/protocol.md`, `protocol-vectors.json` | analyst | architect, ui-designer, ble-engineer, QA, 리뷰어 |
| `docs/spec/architecture.md`, `milestones.json` | architect | 엔지니어, QA, 오케스트레이터(3단계 args) |
| `docs/spec/ui-design.md` | ui-designer | feature-engineer, 디자인 리뷰어 |
| `screenshots/*.png` | feature-engineer | ECC 디자인 리뷰어 |
| `_workspace/01_*`, `03_*_impl.md`, `03_report_*.md` | analyst, 엔지니어, 메인 | 다음 단계, 사후 확인 |
| `_workspace/04_device_*` | 메인 + 사용자 | analyst, 메인 |
| `_workspace/build/verify_*.log` | `verify.sh` | 엔지니어, QA |
| 워크플로 반환값 | 워크플로 | 메인(보고, `milestones.json` 갱신) |

## 오류 처리

| 상황 | 대응 |
|------|------|
| 워크플로 안의 `agent()`가 `null` | 리뷰어는 스크립트가 한 번 재시도하고, 그래도 없으면 `missingReviewers`/`failedReviewers`로 반환하며 해당 마일스톤은 통과하지 않는다. 검증자가 답하지 못한 지적은 `uncertain`으로 남고 high면 차단한다 |
| 워크플로가 `error`를 반환 | 성공으로 보지 않는다. 메시지대로 원인을 고친 뒤 새로 실행한다 |
| 워크플로 결과가 비어 있거나 이상함 | 실행 기록 디렉터리의 `journal.jsonl`에서 각 에이전트의 실제 반환값을 확인한다 |
| `error: "ENV"`(verify.sh 종료 코드 2) | 3단계 1번으로 돌아가 환경부터 준비한다. 다시 시도하지 않는다 |
| Workflow 도구를 쓸 수 없음 | 같은 단계를 Agent 도구로 순차 실행한다(같은 `subagent_type`, 검증자는 주장·지적마다 1명으로 축소). 축소 사실을 보고서에 적는다 |
| 사용량 한도·인증 만료·권한 거부 | 다시 시도하지 않는다. 산출물을 직접 열어 어디까지 됐는지 확인하고 `_workspace/`에 누락을 기록한 뒤 보고한다(한도면 풀리는 시각 포함) |
| 리뷰 지적을 구현자가 거부 | 스크립트가 거부 근거와 함께 검증자에게 다시 판정시킨다. 그래도 갈리면 `needs-decision`으로 사용자에게 넘긴다. 거부 기록은 지우지 않는다 |
| 명세와 실기기 로그 충돌 | 둘 다 출처와 함께 남기고 로그를 우선한다(`scale-protocol` 스킬) |
| 절반 넘는 마일스톤 실패 | 다음 묶음을 진행하지 않고 사용자에게 알린다 |
| 메인이 빈 곳을 채울 때 | 직접 확인한 사실만 쓴다. 에이전트의 판단을 추측해 채우지 않는다 |

## 테스트 시나리오

### 정상 흐름
1. 사용자: "Timemore 앱 개발 시작". 0단계에서 `docs/spec/` 없음 확인 → 1·2단계 계획과 예상 에이전트 수를 알리고 동의를 받는다.
2. `protocol-verify` 실행: 주장 약 30건, 확정 20 / 미확정 10(CRC 변형, 무게 배율 포함), `mismatches` 0. 프로브 A를 권하고 사용자가 캡처 로그 제공 → `crc_probe.py` 종료 코드 0(조합 하나) → `deviceLogs`로 1단계 재실행, 미확정 2건으로 감소.
3. 2단계: architect·ui-designer 병렬 작성 → `design-review`에서 지적 12건 중 확정 5건 반영, `failedReviewers` 없음 → 사용자 승인.
4. 3단계: 사전 확인에서 래퍼 생성(사용자 승인) → M1~M3 넘김 → `done` 2, `awaiting-device` 1, `milestones.json` 갱신, `_workspace/03_report_*.md` 생성.
5. 화면 마일스톤 후 프로브 B 수행, B1~B12 결과 기록, 통과한 `awaiting-device`를 `done`으로.

### 오류 흐름
1. 3단계에서 JDK가 없어 사전 확인 실패 → env-setup 1절 안내, 워크플로 실행 안 함.
2. 환경 준비 후 실행. M4(ui)에서 `ecc:a11y-architect`가 두 번 모두 결과 없음 → `missingReviewers: ["a11y"]`, 상태 `failed` → `blocked`로 기록하고 사용자에게 보고, 같은 마일스톤을 `reviewOnly`로 다시 넘긴다.
3. M5에서 구현자가 high 지적을 거부 → 검증자가 거부 근거를 보고도 confirmed 유지 → 상태 `needs-decision` → 0단계 분기로 사용자 결정을 받는다. 의존하는 M6은 `skipped`(`todo` 유지).
