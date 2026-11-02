# M7R 구현 요약
- Theme.kt `AppShapes` 8/12/20/28/32 dp(internal), theme/AppShape.kt `AppShape.blob`(72점 Catmull-Rom, 랜덤 없음).
- EmptyState: 96 dp blob 컨테이너(surfaceContainerHigh) + 48 dp 아이콘, 의미 노드 없음.
- ConnectScreen: 조약돌 행(surfaceContainerHigh, padding 16/4), Reconnecting 분기(진행 배너 "저장된 저울에 다시 연결하는 중입니다.", 목록 유지·비활성, "취소"), 알림 "저장된 저울에 다시 연결하는 중" / "저울 연결됨"(Polite).
- 테스트: ShapeTest, EmptyStateTest, ConnectViewModelTest(Reconnecting), ConnectScreenshotTest(Reconnecting 알림·비활성, focused 스크린샷).
- 스크린샷: Roborazzi 플러그인이 없어 Shooter가 `test` 실행 때 screenshots/에 PNG를 쓴다(recordRoborazziDebug 태스크 없음).

## 명세와 다른 점
1. 수식어 순서: ui-design 14절 4번은 padding -> clip -> focusRing -> clickable이지만, 이 순서면 행 밖 3 dp 포커스 링이 clip에 잘려 보이지 않는다(connect_scanning_focused로 확인). padding -> focusRing -> clip -> clickable로 구현했다. 14절 수정 필요.
2. strings.xml 없음(문구는 Kotlin 코드). 코드에서 숫자 + 일반 공백 + 단위 문구 없음 확인.
3. `recordRoborazziDebug`/`verifyRoborazziDebug` 태스크 없음(Roborazzi 미사용).
