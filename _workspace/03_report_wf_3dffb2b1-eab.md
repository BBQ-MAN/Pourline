# 구현 보고 wf_3dffb2b1-eab (2026-10-29)

- M7: failed(수정 2라운드 뒤 차단 지적 3건, 원인 2개) → milestones.json blocked. 사용자 결정: 두 건 고치고 M7 reviewOnly 재검사 후 M8·M9
- M8·M9: skipped(선행 M7 실패) → todo 유지

## M7 남은 차단 지적

- [medium·a11y] Outlined button border fails 3:1 non-text contrast (WCAG 1.4.11, D5) — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:279 — 고칠 방법: Pass border = BorderStroke(1.dp, colorScheme.outline) to the OutlinedButtons (outline is 847469 light and 9F8D82 dark, both at least 3:1). Add these pairs to the section 7 contrast table.
- [medium·visual] 후보 목록 행이 화면 가로 여백보다 16 dp 더 들여 그려져 안내문·배너·버튼과 왼쪽 정렬이 어긋남 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:251 — 고칠 방법: 바깥 가로 여백을 포커스 링 몫인 Space.xs(4 dp)로 줄인다(링은 3 dp만 바깥으로 나감). 또는 행을 Surface(shape = medium)로 감싸고 ListItem의 시작 여백을 그만큼 줄인다. 목표는 아이콘 상자 왼쪽이 16~20 dp에 오는 것이다.
- [medium·visual] OutlinedButton 테두리가 outlineVariant로 그려져 면 대비가 1.6:1(라이트)·2.0:1(다크) — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:279 — 고칠 방법: OutlinedButton에 border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)을 준다. 공용 AppOutlinedButton을 Components.kt에 두고 S2·S3에서 같이 쓴다(뒤 마일스톤 S1 동작 줄도 같은 것을 쓴다). 7절 대비 표에 outline on surfaceContainerLow 행도 더한다.

## M7 판단 보류

- [medium] WordBreak.Phrase가 스크린샷에 반영되지 않아 한글 낱말이 음절 단위로 끊김 — app/src/main/java/com/timemoredot/app/ui/theme/Theme.kt:78
- [medium] 한글 본문이 음절 단위로 줄바꿈돼 낱말이 쪼개짐 — app/src/main/java/com/timemoredot/app/ui/theme/Theme.kt:77 (Typography 기본값 그대로), components/Components.kt:127·148, device/DeviceSheet.kt:94·123

## M7 완료 조건

- pass: verify.sh 종료 코드 0
- pass: ConnectionUiMappingTest: architecture.md 9.2절 매핑 표의 모든 행이 각각 테스트되고 ConnectionUi 13종이 모두 한 번 이상 나온다
- pass: ConnectViewModelTest(FakeScaleClient + FakePermissionMonitor + Turbine, Robolectric 없음): onCandidateClick(address)가 connect(address)를, onPermissionResult(true, 
- pass: ConnectViewModelTest: 저장 주소 A, onCandidateClick(B) 뒤 Failed(GattError)이면 onRetryClick은 connect(B)를 부르고 connectLastDevice를 부르지 않는다. 시도한 주소가 없으면 connectLastDevice
- pass: ConnectViewModelTest: setScanResults([A, B]) 뒤 setConnection(Connecting(A))와 setConnection(Failed(GattError))에서 uiState.candidates가 [A, B] 그대로다(목록 유지, ui-design
- pass: CandidateOrderTest(JVM, architecture.md 9.2 orderCandidates): 이미 보인 항목의 순서는 resort=false인 동안 RSSI가 바뀌어도 그대로다. 새로 나타난 최근 연결 기기(recentAddress)는 맨 위, 그 밖의 새 항목은 끝에
- pass: AndroidPermissionMonitorTest(Robolectric): requiredPermissions가 BlePermissions.required(Build.VERSION.SDK_INT)와 같다(API 30·31). onResult(false, true) 뒤 status는 P
- pass: SharedPrefsAppPrefsTest(Robolectric): setBrewSettings 왕복, dismissModelWarning(address) 뒤 isModelWarningDismissed(address)=true, 다른 주소는 false
- pass: AppContainer가 앱 시작 시 scale.connectLastDevice()를 한 번 부르고, grep -rn 'import android.bluetooth' app/src/main 결과가 'import android.bluetooth.BluetoothAdapter' 한 줄뿐이다
- pass: 앱 셸(코드·매니페스트 대조): MainActivity.onCreate가 setContent 전에 ComponentActivity.enableEdgeToEdge()를 부르고 API 29 이상에서 window.isNavigationBarContrastEnforced = false를 둔다.
- pass: grep -rnE 'runBlocking|\.commit\(\)' app/src/main core/*/src/main 결과 0줄이고, grep -rn 'androidx.activity.compose.BackHandler' app/src 결과 0줄이다(architecture.md 3절·9
- pass: NavigationTabsTest(JVM): tabTypeFor(599)=NavigationBar, tabTypeFor(600)=NavigationRail. tabsVisible는 Recipes·History에서 true, Brew는 showBottomBar와 같고, Connect·Su
- pass: 권한 흐름(코드 대조 또는 Robolectric): 영구 거부 상태에서 '설정 열기'가 Settings.ACTION_APPLICATION_DETAILS_SETTINGS와 package:com.timemoredot.app Uri로 startActivity하고, 거부 뒤 권한 요청을 자동으
- pass: StatusAnnouncer가 MainActivity setContent 안에서 NavHost를 감싸는 최상위에 하나 있고 LocalAnnouncer를 제공한다(코드 대조). grep -rn 'liveRegion' app/src/main 결과가 StatusAnnouncer.kt 줄뿐이다
- pass: S2 상태 BluetoothOff, PermissionMissing, PermissionPermanentlyDenied, LocationOff, Scanning(결과 0개), Scanning(결과 있음), Scanning(stopped), ScanEmpty, ScanFailed, Con
- pass: S3 시트의 Connected, Connected(DIS 읽는 중, disReadDone=false), Connected(DIS 읽기 실패), 배터리 15% 이하, 연결 안 됨 상태 각각 screenshots/device_sheet_<state>_light.png·_dark.png가 있
- unverified: [실기기 B1] 첫 실행 권한 허용 → 스캔 → 후보 선택 → 연결이 10초 안에 끝나고 S2가 닫힌다
- unverified: [실기기 B2] 권한 거부와 다시 묻지 않음에서 각각 PermissionMissing, PermissionPermanentlyDenied 화면과 설정 이동 버튼이 나온다
- unverified: [실기기 B3] 블루투스 끈 상태 시작 시 BluetoothOff 화면이 나오고, 켜면 자동 재연결된다

## 스크린샷

- screenshots/connect_bluetooth_off_dark.png
- screenshots/connect_bluetooth_off_light.png
- screenshots/connect_connecting_dark.png
- screenshots/connect_connecting_light.png
- screenshots/connect_disconnected_dark.png
- screenshots/connect_disconnected_light.png
- screenshots/connect_failed_dark.png
- screenshots/connect_failed_light.png
- screenshots/connect_location_off_dark.png
- screenshots/connect_location_off_light.png
- screenshots/connect_no_device_dark.png
- screenshots/connect_no_device_light.png
- screenshots/connect_permission_denied_light_font200.png
- screenshots/connect_permission_missing_api30_dark.png
- screenshots/connect_permission_missing_api30_light.png
- screenshots/connect_permission_missing_dark.png
- screenshots/connect_permission_missing_light.png
- screenshots/connect_permission_permanently_denied_dark.png
- screenshots/connect_permission_permanently_denied_light.png
- screenshots/connect_scan_empty_dark.png
- screenshots/connect_scan_empty_light.png
- screenshots/connect_scan_failed_dark.png
- screenshots/connect_scan_failed_light.png
- screenshots/connect_scanning_empty_dark.png
- screenshots/connect_scanning_empty_light.png
- screenshots/connect_scanning_results_dark.png
- screenshots/connect_scanning_results_light.png
- screenshots/connect_scanning_stopped_dark.png
- screenshots/connect_scanning_stopped_light.png
- screenshots/connect_scanning_unnamed_dark.png
- screenshots/connect_scanning_unnamed_light.png
- screenshots/device_sheet_battery_low_dark.png
- screenshots/device_sheet_battery_low_light.png
- screenshots/device_sheet_battery_low_light_font200.png
- screenshots/device_sheet_connected_dark.png
- screenshots/device_sheet_connected_light.png
- screenshots/device_sheet_dis_failed_dark.png
- screenshots/device_sheet_dis_failed_light.png
- screenshots/device_sheet_dis_reading_dark.png
- screenshots/device_sheet_dis_reading_light.png
- screenshots/device_sheet_disconnected_dark.png
- screenshots/device_sheet_disconnected_light.png
