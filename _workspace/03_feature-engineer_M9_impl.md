# M9 구현 요약 — S6 추출 요약, D2 미저장 추출 복구, D3·D6

## 소스
- `ui/summary/SummaryViewModel.kt`: 9.5 계약(SummaryState, SummaryUiState, SummaryEvent). 원두량 0.1~100.0(빈 칸 허용), 저장 예외면 SaveFailed(입력 유지), 실시간(recovered=false)만 `controller.newBrew()`, 복구는 Interrupted.
- `ui/summary/SummaryScreen.kt`: S6(조약돌 지표 칸, 값 없는 구간 카드, 원두량·메모, 고정 버튼 줄, SaveFailed 배너), D3, D6, 뒤로 가기(`NavigationBackHandler` 하나, 늘 true, 완료 때 D6, 취소는 무시). SaveFailed 알림은 화면이 Saving → SaveFailed 전이에서 한 번.
- `ui/summary/SummaryShapes.kt`: `SummaryVessel`(H 120 dp, `vesselLevel(summary.finalWeightGrams, summary.recipe?.waterGrams)`, 표식 도달 = `summaryStepReached`(= 최종 ≥ 목표 − `STEP_TOLERANCE_GRAMS`), `clearAndSetSemantics {}`), `SummaryChart`(정지 리본, 끝 12 dp 원, 차트 모드 없음, 낭독 한 노드). 애니메이션 API 없음.
- `ui/brew/BrewViewModel.kt` D2: 생성 때 `brews.findDraft(controller.draftId.value)` 한 번 → `recoverableDraft`. `onRecoverOpenClick` = OpenSummary(id, true) 뒤 null, `onRecoverDiscardClick` = deleteSession 뒤 null. `BrewScreen`·`BrewText`: `RecoverDraftDialog`, `recoverText`.
- `nav/Navigation.kt`: Summary 목적지 연결, 저장 성공 시 `saved_session_id`를 Brew 항목에 두고 pop.
- BrewChart: `Legend`·`validSample`·`G_TICKS`·`SEC_TICKS` private → internal(재사용).

## 테스트
SummaryViewModelTest(10), BrewViewModelTest(+4, D2), SummaryScreenTest(Robolectric: 뒤로 가기 완료·취소, D6 버튼, D6·입력·D3 복원, 알림 한 번, 기다리기 단계 낭독·표식 수), SummaryScreenshotTest. FakeBrewRepository에 `findDraftArgs` 추가.

## 스크린샷
summary_{loading, ready, ready_free, overfill, has_gaps, invalid_dose, saving, save_failed, leave_confirm, discard_confirm}_{light,dark}, summary_save_failed_light_font200, summary_leave_confirm_light_font200, brew_recover_draft_{light,dark}, brew_recover_draft_light_font200.

## 명세와 다른 점·판단
1. 값 없는 구간 문구는 표본 ms를 내림해 0:41~0:51(합성 데이터 끊김 시작 42.0 s 직전 표본)로 나온다. 명세 예 0:42와 다른 것은 데이터 때문.
2. D2는 바깥 터치·뒤로 가기로 닫히지 않는다(두 선택 중 하나). 제목 없음(5.5 본문만).
3. SaveFailed 배너는 스크롤 열이 아니라 고정 버튼 줄 위에 둔다(스크롤 위치와 상관없이 보임). 글꼴 2.0에서 고정 줄이 커져 본문이 좁아진다.
4. 입력(원두량·메모)은 ViewModel 상태라 구성 변경은 넘기지만 프로세스 종료 뒤에는 사라진다(9.5 생성자에 SavedStateHandle 없음). 복원 테스트는 실제 ViewModel로 구성 변경을 확인한다.
5. S6 진입 첫 포커스는 제목 `heading`·`paneTitle` 의미만 두었고 `requestFocus`는 하지 않았다(실기기 TalkBack 확인 필요).
6. 서버 요약판 표식은 의미 노드가 없어 수는 `markerSteps` 단위 테스트로, 리본 쪽은 의미 속성 `MarkerCountKey`로 확인했다.
7. 실기기 B8은 하지 않았다. BLE 결함 없음.

## 재실행 2026-11-02: 원두량 오류 재시도 피드백
- SummaryScreen.kt: saveAttempts 카운터(저장 클릭마다 +1)를 LaunchedEffect(doseError, saveAttempts) 키로 써서, 오류가 그대로여도 다시 누르면 원두량 포커스와 DOSE_ERROR_TEXT 낭독(assertive=false)을 반복한다. 0이면(복원 직후) 하지 않는다.
- SummaryScreenTest: 'abc'에서 저장 두 번 → 낭독 2번·포커스 확인.
- verify.sh 종료 코드 0 (_workspace/build/verify_20261102_035857.log). 화면 모양 변화 없음, 스크린샷 그대로.
