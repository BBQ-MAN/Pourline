# 05 최종 시각 디자인 검증

- 날짜: 2026-11-02
- 범위: `screenshots/` 289장 가운데 74장을 직접 열어 봄(목록은 맨 아래). 코드는 고치지 않았다.
- 기준: `docs/spec/ui-design.md`(6절 토큰·모양, 7절 대비, 8.6 글꼴 확대·스크린샷 목록), `.claude/skills/ui-design-review/SKILL.md` D1~D11, ECC `make-interfaces-feel-better`·`design-system`(Mode 2 Visual Audit) 원리를 Compose 용어로 바꿔 적용.
- 중점: 화면 사이 일관성(조약돌 행·블롭 빈 상태·배너·대화상자·스낵바), 다크 대비, 글꼴 200% 잘림·겹침, 가로 배치. 마일스톤별 리뷰에서 이미 확인된 항목은 다시 적지 않았다.
- 대비 수치: `python3 -I .claude/skills/ui-design-review/scripts/contrast.py`로 계산했다. 색은 스크린샷 픽셀을 표본으로 뽑아 토큰과 맞는지 확인했다(예: D5 "초기화" = `#FFB4AB` = 다크 `error`).

## 결론

- high(D1~D11 또는 WCAG AA 위반) 없음. 필수 장면에서 숫자·버튼 글자가 잘리지 않는다(D8). 다크 대비는 표본으로 뽑은 글자 쌍이 모두 4.5:1 이상이다. 가로 1.0에서는 타이머·유량이 스크롤 없이 보인다(3.2).
- 남은 것은 화면 사이 일관성 문제다. medium 4건, low 13건이다. medium 4건은 빈 상태 세로 위치, 대화상자 폭, 아주 좁은 창(w220)·글꼴 200%의 낱말 끊김, S8 원두·물 칸 오류 문구의 단위 끊김이다.
- 확인된 일관성(문제없음): 조약돌 행·카드(S2·S7·S9·S6·S10 지표 칸, 면 `surfaceContainerHigh`, 28 dp, 행 사이 8 dp)는 화면마다 같다. 연결 칩 모양·색은 S1·S3·가로에서 같다. 배너(20 dp, 앞 아이콘, TextButton 들여쓰기)도 S1·S2·S6·S7·S10에서 같다. 멈춘 값 점선 상자(D11)는 재연결·과부하·신호 없음에서 같은 위치에 있다. 동작 줄 pill, 64 dp 높이, 두 줄 배치(글꼴 1.3 이상)도 같다. 다크 오류 배너 TextButton `#F0BC94` on `#93000A`는 5.49:1, 진행 배너 `#F0BC94` on `#554338`는 5.49:1로 통과다.

## 지적

심각도 정의는 `ui-design-review` 4절을 따른다: high = D1~D11·WCAG AA 위반, medium = 특정 상태·테마에서만 위반되거나 명세와 다름, low = 다듬기.

### M-1 (medium) 빈 상태의 세로 위치가 화면마다 다르다

- 스크린샷: `recipes_empty_dark.png`, `history_empty_light.png`, `history_error_dark.png`(블롭이 화면 세로 가운데, y 약 300 dp)와 `brew_nodevice_light.png`, `connect_permission_denied_light_font200.png`, `connect_permission_missing_api30_dark.png`(블롭이 위쪽, 상단 바 아래 약 32 dp)
- 기준과 다른 점: 6.3 `space.xxl` 32 dp는 "빈 상태 위 여백"이다. 4.8 `EmptyState`는 하나의 부품인데, S7·S9에서만 가운데 정렬된다. 탭 세 개 중 두 개(레시피·기록)의 빈 화면이 추출·연결 화면과 다른 자리에 블롭을 둔다.
- 고칠 곳(추정): `app/src/main/java/com/timemoredot/app/ui/recipes/RecipeListScreen.kt` 100·106행, `ui/history/HistoryScreen.kt` 138·144행이 `Box(contentAlignment = Alignment.Center)`로 감싼다. 이를 `Alignment.TopCenter`로 바꾼다. `EmptyState` 자체가 위 32 dp를 이미 준다. 가운데 정렬을 의도했다면 4.8에 그 규칙을 적고 S1·S2도 맞춘다.

### M-2 (medium) 대화상자 폭이 장면마다 다르다(일부는 화면 끝까지 닿음)

- 스크린샷: `brew_recover_draft_dark.png`, `brew_recover_draft_light_font200.png`, `brew_reset_confirm_dark.png`, `brew_leave_confirm_light.png`, `brew_leave_confirm_light_font200.png`(대화상자가 0~360 dp로 꽉 차고 32 dp 둥근 모서리가 화면 가장자리에 닿음)와 `summary_leave_confirm_dark.png`, `recipe_edit_discard_confirm_light.png`(좌우 40 dp 여백), `session_detail_delete_confirm_dark.png`(좌우 약 28 dp)
- 기준과 다른 점: 6.4 "D1~D6 `AlertDialog` M3 기본(32)". M3 대화상자는 화면 가장자리에서 떨어진 떠 있는 면이다. 본문이 길면 화면 끝까지 붙고 짧으면 줄어서, 같은 부품이 장면마다 다른 크기로 보인다.
- 고칠 곳(추정): 모든 대화상자가 기본 `AlertDialog`(`ui/brew/BrewScreen.kt` 614·637·650행, `ui/history/SessionDetailScreen.kt` 280행, `ui/summary/SummaryScreen.kt` 231·246행, `ui/recipes/RecipeEditScreen.kt` 561·572행)를 쓴다. Robolectric 대화상자 창에 플랫폼 기본 바깥 여백이 없어서 생긴 현상일 수 있다. 먼저 실기기에서 D2(가장 긴 본문)를 열어 확인한다. 실기기에서도 같으면 `Components.kt`에 공용 `AppAlertDialog`를 하나 두고 `Modifier.padding(horizontal = 24.dp)`(또는 `widthIn(max = 560.dp)`)를 준다. 여덟 곳은 그 공용 부품을 쓴다.

### M-3 (medium) 아주 좁은 창 + 글꼴 200%에서 낱말이 음절 중간에서 끊긴다

- 스크린샷: `brew_weight_worst_w220_light_font200.png`
- 보이는 것: 하단 탭 라벨 "레시피"가 "레시 / 피" 두 줄로 감긴다. 그 탭 아이콘만 막대 위쪽으로 밀려 다른 두 탭과 높이가 어긋난다. 상단 바 제목 "V60 기본"도 "V60 기 / 본"으로 끊긴다.
- 기준과 다른 점: 8.6 하단 탭 행("라벨이 커지면 늘어남")과 6.2(title·label 스타일은 `WordBreak.Phrase`로 낱말 단위 줄바꿈)를 지키지 않는다. 글자가 잘리지는 않으므로 D8 위반은 아니다. 하지만 낱말이 끊겨 읽기 어렵고, 탭 아이콘 줄이 어긋난다.
- 고칠 곳(추정): 탭 라벨 `Text`에 `maxLines = 1, softWrap = false`를 주고, 넘치면 `labelSmall`로 줄이거나 그 너비에서는 비선택 라벨을 숨긴다(`alwaysShowLabel = false`). S1 제목 `Text`가 Typography의 `lineBreak`을 물려받는지 확인하고 `LineBreak.Heading`을 명시한다. 220 dp는 Android 최소 창이라 드물다. 그래서 medium이다.

### M-4 (medium) S8 원두·물 칸이 글꼴 200%에서 오류 문구의 숫자와 단위를 갈라 놓는다

- 스크린샷: `recipe_edit_invalid_light_font200.png`
- 보이는 것: 반 폭 칸(약 145 dp) 아래 오류 문구가 "0.1~100.0 / ␣g 사이로 / 입력하세요" 3줄로 감긴다. 숫자 뒤 NBSP가 둘째 줄 맨 앞으로 넘어가 " g"가 들여쓰기처럼 보인다. "0보다 / 큰 값을 / 입력하세요"도 3줄이다.
- 기준과 다른 점: 6.2 "숫자와 단위 사이 NBSP"는 단위가 숫자에서 떨어지지 않게 하려는 규칙인데, 여기서는 묶음이 칸보다 넓어 글자 단위로 끊겼다. 8.6은 S8 단계 카드 수치 줄을 배율 1.3 이상에서 두 줄로 바꾸는데, 위쪽 원두·물 줄에는 같은 규칙이 없다.
- 고칠 곳(추정): `ui/recipes/RecipeEditScreen.kt`의 원두·물 `Row`에 단계 카드와 같은 조건(`fontScale >= 1.3f`)을 걸어 세로로 쌓는다. 8.6 S8 행에 "원두·물 줄도 같은 규칙"을 한 줄 더한다.

### L-1 (low) 대화상자의 제목·본문 위계와 파괴 동작 위치가 장면마다 다르다

- 스크린샷: `brew_leave_confirm_light.png`(D1: 제목 + 본문, 파괴 동작 오른쪽 끝), `recipe_edit_discard_confirm_light.png`·`summary_leave_confirm_dark.png`(D4·D6: 제목만), `brew_recover_draft_dark.png`·`session_detail_delete_confirm_dark.png`·`brew_reset_confirm_dark.png`(D2·D3·D5: 본문만, `bodyMedium`), D2는 "버리기"가 왼쪽, D5·D3은 오른쪽, D6은 가운데
- 기준과 다른 점: 6.2 `headlineSmall` = 대화상자 제목. 같은 "되돌릴 수 없음" 질문이 어떤 곳에서는 24 sp 제목, 어떤 곳에서는 14 sp 본문이다. 파괴 동작(error 색)의 위치도 고정되어 있지 않다.
- 고칠 곳: 5절 문구를 "제목 = 질문, 본문 = 결과" 꼴로 맞춘다(예: D3 제목 "이 기록을 삭제할까요?", 본문 "되돌릴 수 없습니다."). 파괴 동작은 늘 `confirmButton`(오른쪽 끝)에 둔다. M-2의 공용 `AppAlertDialog`에 함께 넣으면 한 곳에서 맞춰진다.

### L-2 (low) 다크에서 파괴 동작과 일반 동작의 색이 거의 같다

- 스크린샷: `brew_reset_confirm_dark.png`, `brew_recover_draft_dark.png`, `summary_leave_confirm_dark.png`
- 보이는 것: `error` `#FFB4AB`(분홍)와 `primary` `#F0BC94`(살구)가 나란히 있어 눈으로 거의 구별되지 않는다. 둘 다 `surfaceContainerHigh` 위 8.5:1로 대비는 통과한다.
- 판단: 의미는 글자가 전달하므로 위반은 아니다(D4 취지). L-1처럼 파괴 동작의 자리를 고정하면 위치가 둘째 단서가 된다. 색을 더 바꿀 필요는 없다.

### L-3 (low) 목록 보조 줄에서 글꼴 200%일 때 구분점 "·"이 줄 끝에 남는다

- 스크린샷: `recipes_first_run_light_font200.png`("20 g · 300 g · / 1:15.0"), `history_loaded_light_font200.png`("… · 2:30 · / 1:16.0")
- 고칠 곳(추정): 레시피·기록 행의 보조 줄을 만드는 곳에서 구분자를 `" · "`로 바꾼다. 그러면 점이 다음 조각과 함께 넘어간다.

### L-4 (low) 여러 줄 배너의 앞 아이콘이 첫 줄이 아니라 배너 전체 높이의 가운데에 놓인다

- 스크린샷: `brew_reconnecting_running_light_font200.png`(아이콘이 넷째 줄 높이), `summary_save_failed_light_font200.png`, `session_detail_completed_light_font200.png`, `brew_overload_dark.png`(본문 + 버튼 묶음 가운데)
- 기준: 4.7은 정렬을 정하지 않았다. `make-interfaces-feel-better`의 광학 정렬 원리로 보면 아이콘이 첫 줄과 짝을 이뤄야 읽는 시작점이 분명하다.
- 고칠 곳(추정): `Components.kt` `StatusBanner`의 `Row`를 `verticalAlignment = Alignment.Top`으로 두고, 아이콘에 (첫 줄 높이 − 24 dp) ÷ 2 위 여백을 준다. 4.7에 한 줄 더한다.

### L-5 (low) "정보" 표시가 세 가지 모양으로 나뉜다

- 스크린샷: `summary_has_gaps_light.png`(S6 값 없는 구간: 조약돌 카드 28 dp, 아이콘 없음), `session_detail_completed_light_font200.png`(S10 복구 기록: 배너 20 dp + `info` 아이콘, `surfaceContainerHigh`), `recipes_brewing_light.png`(S7 추출 중: `secondaryContainer` 진행 색 + `info` 아이콘)
- 기준과 다른 점: 4.7은 `info` 아이콘을 정보 종류(`surfaceContainerHigh`)와 짝지었고, 진행 종류는 `sync`·`hourglass_empty`를 쓴다. 5.6(P10)은 S7 배너를 `secondaryContainer`로 정했다. 명세 안에서 두 규칙이 어긋난다. 3.4의 "정보 카드"도 같은 성격의 알림인데 배너가 아니라 카드다.
- 고칠 곳: 명세에서 하나로 정한다. 추천: S7 배너는 정보 종류(`surfaceContainerHigh` + `info`), S6 정보 카드는 정보 배너와 같은 아이콘을 붙인다.

### L-6 (low) 스낵바와 FAB에 그림자가 있다

- 스크린샷: `brew_running_snackbar_light.png`, `brew_finished_save_failed_light.png`(스낵바 아래 그림자), `recipes_first_run_light.png`, `recipes_brewing_light.png`(FAB 그림자)
- 기준과 다른 점: 6.1 "질감: 그라디언트·유리 효과·그림자 없음". 화면에서 그림자가 있는 부품은 이 둘뿐이다.
- 고칠 곳: FAB `elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)`, `Snackbar(…)`에 그림자 없는 래퍼를 쓴다. 떠 있는 부품의 그림자를 예외로 둔다면 6.1에 적는다.

### L-7 (low) S8 왼쪽 정렬선이 둘이다

- 스크린샷: `recipe_edit_valve_light.png`, `recipe_edit_invalid_light_font200.png`
- 보이는 것: 필드·"비율 1:14.0"은 x 16 dp인데, "스위치(밸브) 레시피" 행과 "스위치 일정" 미리보기는 x 32 dp(`ListItem` 기본 안쪽 여백 16 dp)다. 한 열 안에 왼쪽 끝이 두 개 생긴다.
- 고칠 곳(추정): `RecipeEditScreen.kt`의 스위치 `ListItem`·미리보기에서 화면 여백과 겹친 16 dp를 빼거나, `ListItem`을 화면 여백 없이 전체 폭에 둔다.

### L-8 (low) S8 단계 이름 칸에서 숫자와 단위가 갈린다

- 스크린샷: `recipe_edit_valve_steps_dark.png`("280 / g까지"), `recipe_edit_valve_light_font200.png`("60 / g (90°C")
- 기준: 4.5는 S1 단계 카드에서만 표시용 NBSP 치환을 정했다. S10 단계 표는 잘 묶이는데, S8 입력 칸만 다르게 보인다.
- 고칠 곳(추정): 단계 이름 `OutlinedTextField`에 `VisualTransformation`으로 같은 정규식(`(?<=\d) (?=g\b|°C|oz\b)` → NBSP)을 적용한다. 길이가 같아 오프셋 매핑은 `OffsetMapping.Identity`로 충분하다. 저장 값은 바뀌지 않는다.

### L-9 (low) 두 줄 상단 바 제목에서 드롭다운 화살표가 글자와 떨어진다

- 스크린샷: `brew_running_long_step_light_font200.png`, `brew_running_valve_light_font200.png`, `brew_weight_worst_w220_light_font200.png`
- 보이는 것: 제목 두 줄은 왼쪽 가운데쯤에 있고 `arrow_drop_down`은 오른쪽(x 약 280 dp)에 따로 떠 있다. 글꼴 1.0(`brew_idle_light.png`)에서는 화살표가 제목 바로 옆에 붙는다.
- 고칠 곳(추정): S1 제목 버튼 안의 `Text`가 `weight(1f)`·`fillMaxWidth` 같은 채움 수식어를 갖는 것으로 보인다. 글자 폭만큼만 차지하게(`weight(1f, fill = false)`) 바꾼다.

### L-10 (low) 글꼴 200%에서 아이콘과 글자의 비율·간격이 무너진다

- 스크린샷: `recipe_edit_valve_light_font200.png`("+ 스위치 바꾸는 단계 넣기": 아이콘이 작고 두 줄 가운데에 놓임), `brew_running_long_step_light_font200.png`(`schedule` 아이콘이 "20초"에 붙음), `device_sheet_battery_low_light_font200.png`(`battery_alert` 아이콘이 두 줄 값 옆에 아주 작음)
- 판단: 아이콘이 고정 dp인 것은 M3 기본이다. 다만 위 세 곳은 아이콘이 글자의 절반 크기가 되거나 간격이 사라져 짝이 깨진다.
- 고칠 곳: 글자와 짝인 아이콘은 첫 줄 높이에 맞춰 위 정렬하고, 간격 `space.xs` 이상을 지킨다. 크기는 `24.dp * min(fontScale, 1.5f)` 정도로 늘리는 방안도 있다.

### L-11 (low) 넓은 창 S7에서 제목·FAB가 640 dp 본문 열과 따로 논다

- 스크린샷: `recipes_first_run_tablet_dark.png`
- 보이는 것: 큰 제목 "레시피"는 레일 바로 옆(x 약 75 dp)에, 목록은 가운데 640 dp 열(x 약 293~768 dp)에 있다. FAB는 창 오른쪽 아래 끝이라 열에서 약 230 dp 떨어져 있다. 레일 항목은 창 맨 위에서 시작해 제목 높이와 맞지 않는다.
- 기준: 3절은 상단 바를 창 전체 너비로 두라고 하므로 제목 위치는 명세대로다. FAB 위치는 정하지 않았다.
- 고칠 곳: 3절에 "넓은 창 FAB는 본문 열 오른쪽 끝에 맞춘다"를 더하거나 지금 모양을 승인한다. 사용자 판단 항목이다.

### L-12 (low) 스크롤되는 내용이 고정 아래 묶음 경계에서 반쯤 잘린 글자로 보인다

- 스크린샷: `brew_running_ribbon_light_font200.png`("3/3 단계" 윗부분만 보임), `brew_reconnecting_running_recipe_dark.png`, `brew_overload_dark.png`, `recipes_first_run_light_font200.png`
- 판단: 스크롤 배치(3.1)라서 맞는 동작이다. 다만 경계선이 없어서 잘린 글자가 버그처럼 보인다.
- 고칠 곳: 스크롤 열이 더 내려갈 수 있을 때(`canScrollForward`) 아래 묶음 위쪽에 1 dp `outlineVariant` 구분선을 그린다. 그림자는 쓰지 않는다(6.1).

### L-13 (low) 스크린샷 범위의 빈칸(일관성 검증을 막는 것)

- 다크 글꼴 200% 장면이 한 장도 없다(모든 `_font200`이 `_light`). 8.6이 요구하지 않으므로 위반은 아니지만, 다크에서 글자가 커질 때 배너·칩 대비를 볼 수 없다.
- 가로·넓은 창 장면은 S1(`brew_*_landscape*` 7장)과 S7 태블릿 2장뿐이다. 3절이 정한 S2·S6·S8·S9·S10의 `widthIn(max = 640.dp)` 가운데 정렬과 S3~S5 시트의 가로 모양은 스크린샷으로 확인할 수 없다.
- 추가 추천(각 `_light`·`_dark`): `summary_ready_landscape`, `history_loaded_landscape`, `recipe_edit_valve_landscape`, `connect_scanning_results_landscape`, `device_sheet_connected_landscape`, 그리고 `brew_reconnecting_running_dark_font200` 1장.

## 다크 대비 확인 요약

| 쌍 | 화면 | 대비 | 결과 |
|----|------|------|------|
| `primary` `#F0BC94` on `errorContainer` `#93000A` | S1·S2 오류 배너 TextButton | 5.49:1 | 통과 |
| `primary` `#F0BC94` on `secondaryContainer` `#554338` | S1 재연결 배너 TextButton | 5.49:1 | 통과 |
| `error` `#FFB4AB` on `surfaceContainerHigh` `#302824` | 대화상자 "버리기"·"초기화"·"삭제" | 8.50:1 | 통과 |
| `outline` `#9F8D82` on `surface` `#18120E` | 멈춘 값 점선 상자, 테두리 버튼 | 5.84:1 | 통과(UI 3:1) |
| `surfaceContainerHighest` `#3B332E` on `surface` | 값 없는 구간 띠 | 1.50:1 | 정보 아님(6.1·7.3대로 장식). D11은 점선 상자·속 빈 머리·글자가 맡는다 |

문서 빈칸(low): 배너 안 TextButton 글자 쌍(`primary` on `errorContainer`·`secondaryContainer`)은 7.1·7.2에 포커스 링 용도로만 있고 글자 용도로는 없다. 쓰임 칸에 "배너 동작 글자"를 더한다.

## 보고서에 넣을 대표 스크린샷 24장

| # | 파일 | 한 줄 설명 |
|---|------|-----------|
| 1 | `brew_idle_light.png` | S1 대기: 빈 서버 + 표식, 동작 줄 pill, 탭 선택 표시(라이트) |
| 2 | `brew_idle_dark.png` | S1 대기 다크: 같은 배치, 다크 토큰 |
| 3 | `brew_idle_light_font200.png` | S1 대기 글꼴 200%: 동작 줄 두 줄, 서버 축소, 잘림 없음 |
| 4 | `brew_idle_landscape_dark.png` | S1 가로 대기: 레일 + 두 칸(왼쪽 숫자·동작, 오른쪽 서버) |
| 5 | `brew_running_recipe_light.png` | S1 추출 중: 리본 그래프(굵기 = 유량) + 목표 점선 + 단계 카드 |
| 6 | `brew_running_recipe_landscape_light.png` | S1 가로 추출 중: 지표 24 sp 상한, 타이머가 첫 화면에 보임 |
| 7 | `brew_running_recipe_landscape_light_font200.png` | S1 가로 글꼴 200%: 동작 줄 두 줄, 지표는 스크롤로 받음 |
| 8 | `brew_running_valve_dark.png` | 밸브 칸 닫힘 "2:00에 열기" + 기다리기 목표 점선(다크) |
| 9 | `brew_running_valve_cue_light.png` | 밸브 바뀌는 순간: `inversePrimary` 색 숨, "열림 · 끝까지 그대로" |
| 10 | `brew_running_valve_landscape_light.png` | 가로 밸브 칸 한 줄 모양, 무게·지표 위치가 밸브 없을 때와 같음 |
| 11 | `brew_running_valve_light_font200.png` | 글꼴 200% 밸브 띠 배치("닫힘 · 2:00에 열기") |
| 12 | `brew_reconnecting_running_recipe_dark.png` | 재연결 배너(7a) + 멈춘 값 점선 상자 + 값 없는 구간 띠(D11) |
| 13 | `brew_overload_dark.png` | 오류 배너: 과부하, 멈춘 값 · 과부하, 유량 "—" |
| 14 | `brew_permission_permanently_denied_dark.png` | Idle 오류 배너(위 자리) + 권한 칩 + `--.-` |
| 15 | `connect_scanning_results_light.png` | S2 조약돌 후보 행, 이름 겹침 주소 꼬리, 진행 표시줄 |
| 16 | `connect_failed_dark.png` | S2 오류 배너 + 후보 목록 유지 + 아래 고정 "다시 시도" |
| 17 | `device_sheet_connected_dark.png` | S3 저울 정보 시트(32 dp 모서리, 칩, 테두리 버튼) |
| 18 | `summary_ready_light.png` | S6 요약: 조약돌 지표 칸 + 찬 서버 + 리본 요약판 + 원두량 |
| 19 | `summary_save_failed_light_font200.png` | S6 글꼴 200%: 지표 칸 줄바꿈 + 저장 실패 오류 배너 |
| 20 | `recipes_first_run_dark.png` | S7 기본 레시피 5개 조약돌 행 + FAB(다크) |
| 21 | `recipe_edit_valve_light.png` | S8 스위치 레시피: 일정 미리보기 + 단계 카드 열림/닫힘 |
| 22 | `history_loaded_dark.png` | S9 날짜 머리글 + 조약돌 기록 행(다크) |
| 23 | `session_detail_completed_light.png` | S10 지표 칸 + 무게/유량 세그먼트 + 리본 요약판 + 단계 표 |
| 24 | `brew_leave_confirm_light_font200.png` | D1 대화상자 글꼴 200%: 버튼 셋 세로 쌓기, "버리기" error 색 |

구성: 라이트 7, 다크 10(가로 1 포함), 가로 4(그중 글꼴 200% 1), 글꼴 200% 5.

## 직접 열어 본 스크린샷(74장)

- S1(`brew_`, 34): idle_light, idle_dark, idle_light_font200, idle_landscape_light, idle_landscape_dark, running_recipe_light, running_recipe_dark, running_recipe_landscape_light, running_recipe_landscape_light_font200, running_valve_dark, running_valve_landscape_light, running_valve_light_font200, running_valve_cue_light, reconnecting_running_light_font200, reconnecting_running_recipe_dark, overload_dark, nodata_light, running_snackbar_light, finished_save_failed_light, nodevice_light, permission_permanently_denied_dark, failed_dark, model_mismatch_light, paused_dark, idle_valve_open_dark, running_long_step_light_font200, running_ribbon_light_font200, weight_worst_w220_light_font200, recipe_picker_dark, leave_confirm_light, leave_confirm_light_font200, recover_draft_dark, recover_draft_light_font200, reset_confirm_dark
- S2(`connect_`, 8): scanning_results_light, scanning_results_dark, scanning_focused_dark, permission_denied_light_font200, permission_missing_api30_dark, no_device_dark, failed_dark, reconnecting_light
- S3(`device_`, 3): sheet_connected_light, sheet_connected_dark, sheet_battery_low_light_font200
- S6(`summary_`, 5): ready_light, ready_dark, save_failed_light_font200, has_gaps_light, leave_confirm_dark
- S7(`recipes_`, 6): first_run_light, first_run_dark, first_run_light_font200, first_run_tablet_dark, empty_dark, brewing_light
- S8(`recipe_edit_`, 5): valve_light, valve_steps_dark, valve_light_font200, invalid_light_font200, discard_confirm_light
- S9·S10(`history_`, 6): empty_light, loaded_light, loaded_dark, loaded_light_font200, error_dark, detail_valve_light
- S10(`session_detail_`, 5): completed_light, completed_dark, completed_light_font200, delete_confirm_dark, no_samples_light
- S4(`settings_`, 2): valve_haptic_light, valve_haptic_dark

가로가 있는 화면은 S1(가로 7장 중 5장을 봄)과 S7(태블릿)뿐이다. 다크 글꼴 200%는 어느 화면에도 없다(L-13).
