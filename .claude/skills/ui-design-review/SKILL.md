---
name: ui-design-review
description: "Timemore Dot 저울 앱의 UI 디자인 기준과 디자인 검증 절차. 추출 화면 가독성·조작성 기준, Material 3 토큰, Roborazzi 상태별 스크린샷 만들기, ECC 리뷰어(ecc:a11y-architect, ecc:kotlin-reviewer, ecc:architect)와 ECC 스킬(ecc:accessibility, ecc:make-interfaces-feel-better, ecc:design-system)로 디자인 명세와 스크린샷을 검토하는 방법을 정한다. ui-designer·feature-engineer·디자인 리뷰어가 따르는 기준이며, 디자인 명세 작성, 스크린샷 생성, 디자인 기준 질문에 사용한다. '디자인 검증 다시' 같은 검증 실행 요청 자체는 timemore-app-orchestrator로 시작한다. 코드 통합 정합성 검사는 android-integration-qa를 쓴다."
---

# UI Design Review — 디자인 기준과 검증

이 앱의 화면은 책상 위가 아니라 드리퍼 옆에서, 팔 길이 거리에서, 젖은 손으로 쓴다. 일반 앱 디자인 리뷰 기준을 그대로 쓰면 이 맥락의 결함(숫자 흔들림, 작은 버튼, 늦은 표시)을 놓친다. 그래서 ECC의 범용 기준 위에 아래 앱 전용 기준을 얹는다.

## 1. 앱 전용 디자인 기준

| ID | 기준 | 이유 |
|----|------|------|
| D1 | 추출 화면의 주 무게 숫자가 화면에서 가장 크다(대략 `displayLarge` 이상). 단위 표기는 작게 | 팔 길이(50~70 cm)에서 읽어야 한다 |
| D2 | 무게·타이머·유량 숫자는 고정폭 숫자(`fontFeatureSettings = "tnum"`)와 고정 소수 자릿수 | 자릿수 폭이 바뀌면 100 ms마다 숫자가 좌우로 흔들린다 |
| D3 | 영점·타이머 같은 주요 버튼은 최소 48 dp, 권장 56 dp 이상, 엄지가 닿는 하단 배치 | 젖은 손, 한 손 조작 |
| D4 | 연결 상태가 항상 보이고 색 + 아이콘 + 글자로 표시 | 색만으로 구분하면 색각 이상 사용자와 밝은 조명에서 놓친다 |
| D5 | 라이트·다크 테마 모두 본문 대비 4.5:1, 큰 숫자 3:1 이상 | 어두운 주방과 밝은 창가 모두에서 쓴다 |
| D6 | 측정값(무게·타이머·유량 숫자, 단계 글자)은 받은 프레임에 바로 그리고 애니메이션하지 않는다. 값을 따라가는 모양(물결·리본·채움 같은 모양 겹)은 임계 감쇠 스프링으로 따라가도 되지만 지연 60 ms 이하, 오버슈트·외삽 없음이다. 모션 줄이기 설정이면 모양 겹도 즉시 그린다. 화면 전환 애니메이션은 짧게 | 사용자는 숫자를 보고 붓기를 멈추므로 숫자 지연은 과다 추출로 이어진다. 모양은 판단 근거가 아니지만 숫자와 눈에 띄게 어긋나면 혼동을 준다 |
| D7 | 그래프는 실제 선과 목표선을 색 외에 선 모양(실선/점선)으로도 구분, 축에 단위 | 색각 이상, 단위 혼동 방지 |
| D8 | 글꼴 크기 200%에서 숫자·버튼 글자가 잘리지 않음. D1과 충돌하는 주 무게 숫자는 `maxLines = 1` + 자동 축소처럼 대응 방식을 명세에 적는다 | 시스템 글꼴 확대 사용자 |
| D9a | TalkBack 값 낭독: 화면의 숫자 `Text`에는 `liveRegion`을 걸지 않는다. 숫자 노드는 `clearAndSetSemantics`로 낭독 문구를 따로 주고, 5초 간격·단계 변화 때만 바뀌는 별도 노드에 `liveRegion = LiveRegionMode.Polite`를 건다 | 10 Hz로 읽으면 화면 낭독기가 쓸모없어진다 |
| D9b | TalkBack 상태 알림: 연결 끊김·재연결·과부하·영점·타이머 시작/정지는 `Polite`, 추출을 멈춰야 하는 오류는 `Assertive`. 토글·상태 버튼은 `stateDescription`, 동작 버튼은 `onClickLabel` | 화면을 보지 않는 사용자도 상태 변화를 알아야 한다 |
| D10 | 모든 화면 상태(블루투스 꺼짐, 권한 없음, 스캔 중, 연결 중, 연결됨, 재연결 중, 실패, 기록 없음)에 문구와 다음 행동 버튼 | 정의 안 된 상태는 빈 화면이 된다 |
| D11 | 과부하·연결 끊김 중에는 숫자가 "멈춘 값"임을 시각적으로 표시 | 끊긴 뒤 마지막 무게를 실제 값으로 착각한다 |

## 2. 디자인 명세(`docs/spec/ui-design.md`)에 들어갈 것

- 화면 목록과 이동 흐름
- 화면 × 상태 표(D10 상태 전부), 상태마다 문구와 행동
- 토큰: `ColorScheme` 역할별 라이트·다크 값(브랜드 색은 대비 계산 결과와 함께), `Typography` 스타일(크기 sp, 굵기, `tnum` 여부), 간격 단위(dp), 모서리, 아이콘 세트
- 컴포넌트: 무게 표시, 타이머, 유량 표시, 그래프, 레시피 단계 카드, 연결 상태 칩
- 접근성 요구사항: 화면별 TalkBack 문구, 알림 빈도, 글꼴 확대 시 배치

## 3. 스크린샷 만들기(구현 단계)

디자인 리뷰어는 코드가 아니라 스크린샷을 보고 판단한다. 스크린샷이 없으면 디자인 검증을 할 수 없으므로 마일스톤이 실패 처리된다.

1. Robolectric + Roborazzi로 JVM 스크린샷 테스트를 쓴다(Roborazzi 설정 방법은 현재 공식 README로 확인한다).
2. 화면 × 상태 × {라이트, 다크}마다 한 장. 모든 화면에서 가장 긴 문구가 나오는 상태에 글꼴 2.0배 한 장을 더 만든다(D8).
3. 결정적으로 만든다: 가짜 저울 고정 시퀀스, 고정 시계, 애니메이션 끔. 실행마다 달라지면 리뷰와 회귀 비교가 무의미해진다.
4. 파일 이름: `screenshots/{screen}_{state}_{light|dark}[_font200].png` (프로젝트 루트 기준). 예: `screenshots/brew_running_dark.png`
5. `verify.sh recordRoborazziDebug`로 기록하고, 이후 변경에서는 `verify.sh verifyRoborazziDebug`로 의도하지 않은 변화를 잡는다.
6. 구현 결과의 `screenshots` 필드에 만든 파일 경로를 모두 적는다.

## 4. 디자인 검증 관점과 ECC 연결

| 관점 | 실행 주체 | 불러올 ECC 스킬 | 입력 | 보는 것 |
|------|-----------|-----------------|------|---------|
| 접근성 | `ecc:a11y-architect` | `ecc:accessibility` | 명세 또는 스크린샷 + Compose 코드 | D3·D4·D5·D8·D9a·D9b, semantics(`contentDescription`, `stateDescription`, `liveRegion`, `role`, `onClickLabel`), 터치 영역 |
| 시각 디테일 | 기본 워크플로 에이전트 | `ecc:make-interfaces-feel-better`, `ecc:design-system` | 명세 또는 스크린샷 | D1·D2·D6·D7·D10·D11, 간격·위계·정렬·토큰 일관성 |
| Compose 코드 | `ecc:kotlin-reviewer` | — | 변경 코드 | 재구성 범위(`compose-app-dev` 5절), 상태 끌어올리기, 수명주기 수집 |
| 아키텍처 | `ecc:architect` | `ecc:android-clean-architecture`, `ecc:kotlin-coroutines-flows` | `architecture.md` | 계층 의존, 계약, 수명주기 |

- ECC 스킬은 Skill 도구로 불러오고, 도구가 없으면 `~/.claude/plugins/cache/ecc/ecc/*/skills/<이름>/SKILL.md`를 Glob으로 찾아 Read로 읽는다.
- ECC 스킬은 웹(CSS, ARIA) 예시가 많다. 원리만 가져와 Compose 용어로 바꿔 판단한다(ARIA live region → `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`, CSS 토큰 → `MaterialTheme` 역할). 이 스킬의 기준과 충돌하면 이 스킬이 우선한다.
- Android 공식 스킬(android-skills 플러그인)은 아래 판단의 근거로 쓴다: 인셋·시스템 바는 `edge-to-edge`, 가로·큰 화면 배치는 `adaptive`, Compose 코드 관점의 재구성 비용은 `jetpack-compose-performance`. 불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다. 이 스킬의 기준 D1~D11과 충돌하면 이 스킬이 우선한다.
- 리뷰어는 파일을 수정하지 않는다. 지적만 반환한다. Write 권한이 있는 ECC 에이전트(`ecc:a11y-architect`)에도 프롬프트로 읽기 전용을 지시한다.
- 지적 형식: 제목, 심각도(`high` = 기준 D1~D11 위반이나 WCAG AA 위반, `medium` = 특정 상태·테마에서만 위반, `low` = 다듬기), 위치(스크린샷 파일 또는 파일:줄, 명세 절), 근거(무엇이 보이는지), 고칠 방법.
- 스크린샷을 근거로 할 때는 `Read`로 PNG를 직접 열어 본다. 파일 이름만 보고 판단하지 않는다.
- 한 관점 안에서는 같은 원인을 한 건으로 묶는다. 관점 사이 중복은 검증 단계에서 각각 판정되므로 신경 쓰지 않는다.

## 5. 대비 계산

색 대비는 눈으로 판단하지 않는다. 토큰의 hex 값으로 계산하고 결과 수치를 근거에 적는다(명세 단계와 스크린샷 단계 모두).

```bash
python3 -I .claude/skills/ui-design-review/scripts/contrast.py 1C1B1F:FFFBFE E6E1E5:1C1B1F   # 전경:배경 쌍
```

`ui-design.md`에는 라이트·다크 각각 주요 전경·배경 쌍의 대비 표를 이 스크립트 출력으로 남긴다. Bash가 없는 리뷰어(`ecc:a11y-architect`)는 이 표를 근거로 판단하고, 검증 단계가 다시 계산한다.
