# M7 구현 요약 — 앱 셸·연결 화면(S2)·저울 정보 시트(S3)

## 결과
- verify.sh(test lintDebug assembleDebug) 종료 코드 0. 로그 경로는 반환값 verifyLog.
- 스크린샷 41장 `screenshots/`: S2 상태 13종 × {light, dark}, `connect_permission_missing_api30_*`, `connect_scanning_unnamed_*`, `connect_permission_denied_light_font200`, S3 5종 × {light, dark}(`device_sheet_{connected,dis_reading,dis_failed,battery_low,disconnected}`).

## 소스 (app/src/main/java/com/timemoredot/app/)
- `AppContainer`(appScope·clock·scale·prefs·permission, init에서 `connectLastDevice()` 한 번), `AppPrefs`(+`SharedPrefsAppPrefs`), `PermissionMonitor`(+`AndroidPermissionMonitor`), `TimemoreApp`, `MainActivity`(enableEdgeToEdge, 권한·블루투스 켜기 launcher, onResume).
- `ui/nav/`: 경로, `tabTypeFor`, `tabsVisible`, `AppNav`(NavigationSuiteScaffold + `snapTo`, fade 전환), 빈 자리·`BrewPlaceholder`.
- `ui/connect/`: `ConnectionUi`·`connectionUi`·`displayName`, `ConnectViewModel`, `orderCandidates`, `ConnectAnnouncements`, `ConnectScreen`/`ConnectContent`.
- `ui/device/DeviceSheet.kt`(S3), `ui/components/Components.kt`(연결 칩, 배너, 빈 상태, 포커스 링), `ui/StatusAnnouncer.kt`, `ui/SystemActions.kt`, `ui/Icons.kt`, `ui/theme/Theme.kt`.

## 명세와 다른 점
1. `BrewPlaceholder`는 빈 화면이 아니다. 연결 칩(→ S3 시트)과 "저울 찾기"(→ S2)만 둔다. 이게 없으면 M8 전에 S2에 갈 길이 없어 실기기 B1~B3를 못 한다. M8이 BrewScreen으로 바꾼다. `showBottomBar`는 M8 전까지 상수 true.
2. 스크린샷은 Roborazzi 플러그인 대신 Compose UI 테스트 `captureToImage` + `Bitmap.compress`로 쓴다(`ui/ScreenshotSupport.kt`). 플러그인의 record/verify 태스크가 없다. 이유: AGP 9.4와의 호환 확인 비용. 화면 추가 시 `verify.sh`(test)만 돌리면 PNG가 다시 써진다. 비교 검증(회귀 감지)은 아직 없다.
3. 아이콘은 `AppIcons`의 경로 데이터 코드로 둔다(벡터 리소스 아님). 6.5 목록에 없는 `arrow_back`을 더했다(S2 뒤로 가기).
4. 문자열은 리소스가 아니라 코드 상수다(한국어 단일 로케일).
5. `ui-test-manifest`(debug)가 debug 병합 매니페스트에 `ComponentActivity`를 넣는다. androidx 항목이고 release에는 없다.
6. `robolectric.properties`에 `application=android.app.Application`을 더했다. 매니페스트의 `TimemoreApp`이 Robolectric에서 실제 `BleScaleClient`를 만들지 않게 한다.
7. S2 Reconnecting(명세 5.3 표에 없음)은 진행 배너 + "취소"로 둔다.
8. 권한 영구 거부 판정: 요청 결과가 거부이고 필요한 권한 모두에서 `shouldShowRequestPermissionRationale`이 false이면 영구 거부. 시스템 대화상자를 띄우지 않고 닫은 경우도 같은 값이 될 수 있다.

## 코드 대조·한계
- 재정렬 미룸(포인터 눌림 → 뗀 뒤 500 ms): `ConnectScreen.kt`의 `rememberOrderedCandidates`와 LazyColumn `pointerInput`. 순서 계산은 `CandidateOrderTest`. 눌림 지연 자체의 UI 테스트는 없다.
- S2 알림 문구는 `connectAnnouncement`(JVM 테스트)와 `ConnectScreenshotTest`의 기록용 Announcer로 확인했다. `StatusAnnouncer` 노드(300 ms 이어 붙이기)는 테스트가 없다.
- 실기기 B1~B3는 하지 않았다(아직 확인 안 됨).
