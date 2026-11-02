# M11 구현 요약 — S9 기록 목록, S10 기록 상세, CSV 내보내기, D3

## 소스
- `data/CsvExport.kt`: `fun interface CsvTarget { suspend fun write(block: (Appendable) -> Unit) }` 추가.
- `AppContainer.csvTarget(uri)`: IO에서 `openOutputStream(uri, "wt")` → UTF-8 Writer → block → 닫기. 열기 실패(null)는 IOException.
- `ui/history/HistoryViewModel.kt`(ExportState·ExportEvent·HistoryUiState 포함), `SessionDetailViewModel.kt`(DetailState·SessionDetailUiState·SessionDetailEvent): 9.7 계약. 두 파일에 Uri·ContentResolver 없음.
- `ui/history/HistoryScreen.kt`(S9), `SessionDetailScreen.kt`(S10, D3 `DeleteSessionDialog`), `HistoryParts.kt`(CreateDocument 런처 `rememberCsvPicker`, 내보내는 중 알림 `AnnounceExporting`, 진행 막대, 파일 이름).
- `ui/summary/SummaryShapes.kt`: `SummaryChart(flowMode)` 유량 실선 2.5 dp·y축 g/s·20 g/s 자름·범례 "실제"·낭독 `flowChartSpoken`. `Pebble`은 internal.
- `Navigation.kt`: History·SessionDetail 연결, `SESSION_DELETED`. `Placeholders.kt` 삭제(쓰는 곳 없음).
- `Icons.kt`: `download`.

## 테스트(Robolectric은 화면 테스트에만)
HistoryViewModelTest(7), SessionDetailViewModelTest(12), HistoryScreenTest, SessionDetailScreenTest, HistoryCodeContractTest, HistoryScreenshotTest, 픽스처 HistoryFixtures. 가짜 선택기 `FakePickerRegistry`(ActivityResultRegistry)로 "다시" 흐름(새 Uri로 새 CsvTarget, 취소는 호출 없음)을 검증.

## 스크린샷(screenshots/)
history_{loading,empty,loaded,error,exporting}_{light,dark}, history_loaded_light_font200, session_detail_{loading,completed,interrupted,no_samples,not_found,delete_confirm}_{light,dark}, session_detail_completed_light_font200, history_detail_{light,dark}(무게), history_detail_flow_{light,dark}(유량), history_detail_valve_{light,dark}(스크롤해서 일정 줄 + 기다리기 행), history_detail_valve_chart_{light,dark}(그래프 전체, 평평 구간 점선).

## 명세와 다른 점·판단
1. `SessionDetailEvent.DeleteFailed` 추가(명세는 Export·Deleted 둘). 삭제 실패를 삼키지 않으려고 스낵바 "기록을 삭제하지 못했습니다." + "다시"(onDeleteConfirmed)로 처리.
2. "무게/유량" 세그먼트는 M3 `SegmentedButton`이 아니라 같은 모양의 직접 구현(`selectable(role = RadioButton)`, 모양 40 dp·칸 48 dp, 체크 아이콘). M3 버튼은 레이아웃 높이가 40 dp이고 터치 경계만 48 dp라 `assertHeightIsAtLeast(48.dp)`가 40으로 나왔다. 선택 낭독은 `selected` 의미라 M3 기본 "선택됨"과 같다.
3. `Interrupted` 기록은 S10 맨 위에 Info 배너 "저장되지 않은 채 끝난 추출을 복구한 기록입니다."(명세에 문구 없음, 완료 조건의 "Loaded(interrupted)" 장면을 구분하려고 추가). ui-design에 반영 필요.
4. `NoSamples`에서도 CSV 내보내기가 켜져 있다(5.9). 샘플이 없으면 머리글만 있는 파일을 쓴다. 이때 단계 표는 "실제" 없이 목표만 보인다. 샘플 0개 버퍼도 NoSamples로 본다.
5. S10 삭제 결과 키는 `previousBackStackEntry`에 둔다. S10이 Brew의 "보기"에서 열렸으면 이전 항목이 Brew라 S9 스낵바는 없다(History에서 열린 경우만 동작).
6. S10 "CSV를 저장했습니다"에는 건수를 붙이지 않는다(S9만 "(24건)", S10의 count는 샘플 수).
7. 스낵바 동작("다시")은 TalkBack 터치 탐색 중 `Indefinite`, 아니면 `Long`(4.9).
8. 메모 디바운스 중 저장이 실패하면 `pendingNote`가 남아 화면을 떠날 때 appScope에서 한 번 더 시도한다. 취소 경합으로 같은 값이 두 번 저장될 수 있다(무해).

## 검증
- verify.sh 종료 코드 0(`_workspace/build/verify_20261102_044743.log`). verifyRoborazziDebug 통과(기존 스크린샷 변화 없음).
- 병합 매니페스트 `EXTERNAL_STORAGE` grep 0줄.
- 코드 대조(HistoryCodeContractTest): Uri·ContentResolver 0줄, imePadding 0개, 조약돌 행 수식어 순서, 날짜 머리글 배경 없음, STEP_TOLERANCE_GRAMS 판정, session_deleted 읽고 지움, 일정 줄 함수 공용.
- 실기기 B11(CSV 파일이 열리고 헤더·단위 확인)은 하지 않았다. 실기기 TalkBack도 하지 않았다.
- BLE 결함 없음.
