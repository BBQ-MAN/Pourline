# FIX-1 구현 요약
- BrewEngine: 내부 `armed`·`tareAtNanos` 추가. Tared가 영점 시각을 기록하고 toIdle이 무장을 푼다. WaitingForPour에서 무장 전 Reading(valid, atNanos > 영점 시각, |무게| <= 임계값)은 무장만 하고 카운트하지 않는다. 그 밖은 카운트 0.
- 샘플 유량: FLAG_DISCONNECTED 샘플의 유량을 0f 대신 state.flowGramsPerSec로 통일(정상·OVERLOAD는 이미 같았다).
- BrewState·BrewInput·BrewEffect·BrewConfig 공개 필드·시그니처 변경 없음.
- 바꾼 기존 테스트: 없음(jitter 30샘플 테스트 포함 그대로 통과).
- 새 테스트: AutoStartArmingTest (probeA 213.4 g, 145.0 g 실측 순서, 무장 전 카운트 없음, atNanos<=t0 0 g 무장 안 함, 재Tared 무장 해제, 샘플 유량 일치).
- verify.sh 전체 PASS: _workspace/build/verify_20261029_222743.log
