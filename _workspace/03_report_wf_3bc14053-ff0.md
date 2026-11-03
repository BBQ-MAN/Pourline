# 리뷰 보고 wf_3bc14053-ff0 (2026-11-03, implement-milestones reviewOnly, maxFixRounds 1)

대상: FIX-7·FIX-8·FIX-9(프로브 B 고칠 거리). 메인이 직접 구현한 뒤 리뷰 전용으로 실행(에이전트 24, 실패 0, missingReviewers 없음).

| 마일스톤 | 상태 | 라운드 | 차단 지적 | 수정 라운드에서 바뀐 것 |
|---|---|---|---|---|
| FIX-7 | awaiting-device | 1 | 1 → 0 | 끊김 없는 공백(NoData 등)이 확인 구간에 들어가던 문제: 확인 샘플 사이 1 s 넘으면 새 t₀(`silentGapInsideConfirmCancels` 추가) |
| FIX-8 | awaiting-device | 0 | 0 | — |
| FIX-9 | awaiting-device | 1 | 2 → 0 | 글꼴 2.0 스크린샷 3장(`*_failed_pairing_mode_light_font200`), ui-design 8.6 목록 |

완료 조건: 실기기 항목만 unverified(아래 재확인 시나리오), 나머지 모두 pass.

## 리뷰 뒤 메인이 반영한 남은 지적
- FIX-8 uncertain(medium): 제조사 데이터 없는 광고가 앞서 본 broadcastType을 null로 지움 → 이전 값 이월 + 테스트.
- FIX-8 low: sentAtNanos를 GATT 큐 대기 전이 아니라 실제 쓰기 직전(gatt 블록 안)에 찍음.
- FIX-7 low: 끊김 때 lastValidWeight 지움, "지금 시작" 때 확인 구간 버림, B4 시계열을 로그의 실제 유량·시각으로, 문서의 옛 "연속 2샘플" 문구.
- FIX-9 low: onResume deniedOnce 정리 단순화, 권한 결과 디버그 로그(elapsedMs, B2 재확인용), 구현 요약 스크린샷 목록.

## 남긴 low 지적(반영 안 함)
- pendingStart의 Triple 가독성(선택 사항).
- '짝 없는 응답' 로그가 늦은 자기 응답과 다른 앱 응답을 구분하지 않음(구분할 근거가 프레임에 없음, protocol 3.13).
- a11y: 실패 낭독은 이유만 알리고 방법은 배너 읽기에 의존, S2에서 Failed→Failed 이유만 바뀌면 알림 없음(같은 저울 재시도라 이유가 바뀌는 경우 드묾).
- visual: S1 글꼴 2.0에서 7줄 배너가 무게를 첫 화면 밖으로 밀어냄, 배너 아이콘이 여러 줄 본문 가운데 정렬, 긴 문구 줄바꿈('이' 끝남). 공용 StatusBanner·문구 다듬기라 별도 결정 대상.
- permissionRequestedAtMs는 Activity 재생성 때 0(결과가 아주 큰 elapsed가 돼 영구 거부로 보지 않음 = 안전한 쪽).

검증: verify.sh(test·lintDebug·assembleDebug) 종료 0 `_workspace/build/verify_20261103_082428.log`, verifyRoborazziDebug 종료 0 `verify_20261103_082454.log`. 단위 테스트 686개 실패 0.
