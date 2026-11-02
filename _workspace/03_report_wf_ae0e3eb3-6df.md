# M7 재검사 wf_ae0e3eb3-6df (2026-10-29)

상태: failed(수정 2라운드 뒤 차단 지적 3건). 사용자 결정: 3건 직접 수정 후 M7 완료(awaiting-device) 처리, 리뷰 워크플로 재실행 없음.

## 남은 차단 지적(직접 수정 대상)

### ScanEmpty에서만 '다시 찾기'가 화면 가운데로 올라간다(다른 상태는 하단 고정 줄)
- file: app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:204
- evidence: connect_scan_empty_light/dark.png에서 '다시 찾기' 버튼은 EmptyState 안 y≈521~633px(260~316 dp)에 있다. connect_no_device·connect_disconnected·connect_scan_failed·connect_scanning_stopped.png에서는 같은 동작이 하단 고정 줄 y≈1456~1568px에 있다. 원인 코드는 두 곳이다. ConnectScreen.kt:204-207은 ScanEmpty의 EmptyState에 actionLabel="다시 찾기"를 넘긴다. :276의 BottomActions는 ScanEmpty를 빠뜨려 null을 돌려준다. 그래서 ScanEmpty에서 다시 찾기를 누르면 Scanning이 되고, 다음 버튼('찾기 멈춤')은 화면 아래로 이동한다. 같은 동작의 위치가 상태마다 바뀌어 컴포넌트 일관성이 깨진다. ui-design.md 3.3:174('아래 고정 버튼 ... 멈춤 상태 "다시 찾기"')와 한 손 조작 기준(D3의 하단 배치 취지)에도 어긋난다.
- fix: BottomActions의 분기에 ConnectionUi.ScanEmpty를 더한다(NoDevice·Disconnected·ScanFailed와 같은 Action("다시 찾기", true, onScan)). ScanEmpty의 EmptyState에는 actionLabel·onAction을 넘기지 않는다. 명세 4.8의 'EmptyState에 버튼 포함'은 S2에서 하단 고정 줄이 있는 상태면 그 줄을 쓴다고 5.3에 한 줄 적는다.
- verdict: I checked the current code and the screenshot, and the finding holds. In ConnectScreen.kt:204-207, the Header passes actionLabel="다시 찾기" and onAction=onScan to ScanEmpty's EmptyState. BottomActions (around line 276) has a when branch for Scanning, for NoDevice/Disconnected/ScanFailed, for Connecting/Reconnecting and for Failed. It has no branch for ScanEmpty, so it falls to else -> null and returns without drawing a bottom row. In connect_scan_empty_light.png, the '다시 찾기' button sits in the middle of the content area (y≈521-633px) and there is no bottom fixed row. NoDevice, Disconnected and ScanFailed all put the same '다시 찾기' in the bottom row. So within S2, the same action moves between states. After a tap in ScanEmpty, the next action ('찾기 멈춤') appears at the bottom, so the touch point jumps. ui-design.md 3.3:174 puts the bottom fixed button '다시 찾기' in the stopped state. The S2 table in section 5 (lines 387-392) gives '다시 찾기' as the action for ScanEmpty, the same as NoDevice and ScanFailed. One rebuttal is possible: 4.8 says EmptyState includes its own Button, and other EmptyState states such as BluetoothOff also place their button inside. Those are different actions with no bottom-row equivalent, though. In ScanEmpty the same action changes position from one state to the next, which is a real consistency and one-hand-use defect. The spec conflict (4.8 vs 3.3) is an ambiguity, which is why the fix proposes adding a line to 5.3. The fix is small: add ScanEmpty to the BottomActions branch and remove the action from its EmptyState. I did not check the dark screenshot or the other states' PNGs pixel by pixel.

### 이름이 같은 후보 여러 개를 구별할 수 없다(표시 이름 규칙 1의 빈틈)
- file: docs/spec/ui-design.md:169
- evidence: connect_scanning_results·connect_scanning_stopped·connect_connecting·connect_failed(_light/_dark).png는 모두 'TIMEMORE_Dot' 세 줄을 보여 준다. 차이는 보조 줄의 '신호 강함/보통/약함'뿐이다. 스캔이 멈추면 RSSI 순으로 다시 정렬되고 신호 문구도 계속 바뀐다. 그래서 사용자는 어느 줄이 자기 저울인지 알 수 없다. 카페처럼 Dot이 여러 대 있는 곳에서 잘못 연결하기 쉽다. 규칙 2(이름 null)만 주소 끝 5자를 붙이고, 규칙 1(이름 있음)은 그대로 쓴다(ConnectionUi.kt:57-58 displayName).
- fix: 명세 3.3 규칙 1에 한 줄을 더한다. 화면의 후보 목록에 같은 표시 이름이 2개 이상 있으면 그 항목들에 ' · ' + 주소 끝 5자를 붙인다. 고칠 곳은 displayName 호출부다(목록 전체를 보고 중복 이름을 고른다). S3 이름 줄은 그대로 둔다. 스크린샷 픽스처 connect_scanning_results에도 이 경우를 넣는다.
- verdict: I checked the current files and the finding holds. ConnectionUi.kt:57-58 `displayName` returns the name unchanged whenever it is not blank. It adds the last 5 address characters only when the name is null. ConnectScreen.kt:252 (S2 list) and DeviceSheet.kt:73 (S3) both call it. ui-design.md:169 (rule 1) says to use the name as is. Rule 2 (line 170) adds the address tail only for nameless candidates, and line 170 itself says the tail is there so several candidates stay distinguishable. Rule 1 has no matching rule for duplicate names. The screenshot connect_scanning_results_light.png shows three rows that all read 'TIMEMORE_Dot'. The only differences are '신호 강함 · 최근 연결', '보통' and '약함'. The fixture in ConnectScreenshotTest.kt:27-29 uses the same name for 3 addresses. The signal labels keep changing, and the list is re-sorted by RSSI when the scan stops (3.3). So apart from the one marked '최근 연결', a user with several Dots nearby cannot tell which row is their scale. There is no rename path either: the SetName command exists in the protocol, but no MVP screen uses it (architecture.md:365). The only safeguard after connecting is A-4, which checks the model, so connecting to someone else's Dot is not caught. The code follows the spec as written, so the defect is a gap in the spec that causes a real usability failure. The proposed fix is sound: when names in the list are duplicates, add ' · ' and the last 5 address characters at the list call site, and also update `spokenName`.

### S3 시트의 연결 안 됨 상태 문구를 5.1보다 줄여 다음 행동 안내가 빠진다
- file: app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:129
- evidence: ui-design 5.4는 그 밖의 연결 상태를 '4.1 칩 + 5.1 문구'로 정한다. connectionMessage는 Failed(ConnectFailed)를 '저울에 연결하지 못했습니다.'(5.1: '… 저울을 켜고 가까이 둔 뒤 다시 시도하세요.'), Timeout을 '연결 응답이 없습니다.'(5.1: '… 저울이 켜져 있는지 확인하세요.')로 줄인다. PermissionMissing과 PermanentlyDenied는 '저울을 찾으려면 권한이 필요합니다.' 한 문장으로 합쳐서 API 30 이하 '위치' 문구와 설정 안내가 빠진다. 스크린샷은 Disconnected만 있어서 이 상태들은 검증되지 않았다(D10: 상태마다 문구와 다음 행동).
- fix: S1이 쓸 5.1 문구 함수를 공용으로 만들어 시트도 같은 문구를 쓴다(API 30 분기 포함). 최소 device_sheet_failed_light/dark 스크린샷을 하나 더 만든다.
- verdict: Partly fixed, but some of it still holds. Fixed: DeviceSheet.kt:131 connectionMessage now calls the shared failureText (ConnectScreen.kt:222) and permissionText (ConnectionUi.kt:65). So Failed(ConnectFailed), Failed(Timeout) and Failed(ServiceNotFound) show the full 5.1 sentences. PermissionPermanentlyDenied shows "설정에서 허용하세요." screenshots/device_sheet_failed_light/dark.png exist and show the full ConnectFailed sentence. Still open: (1) The only production call site, Placeholders.kt:58 DeviceSheet(...), does not pass locationPermission. The default is false (DeviceSheet.kt:48). So on API 30 and below, PermissionMissing and PermanentlyDenied in the sheet still show the '근처 기기' wording, not '위치'. ConnectScreen.kt:103 computes the flag, but the sheet path does not. That is a 5.1/5.4 spec violation on API 30 and below. (2) Reconnecting still drops the "(3/10회, 4초 후)" part and "추출 중이면 타이머는 계속 갑니다." The comment at DeviceSheet.kt:130 says this is on purpose, but 5.4 says "5.1 문구". There is still no screenshot of the permission states.

## low

- [qa] Q10 영구 거부 판정의 deniedOnce 플래그가 런처 밖에서 허용되면 지워지지 않음(구현 요약 8번과도 다름) — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/MainActivity.kt:25-28
- [qa] Q14 BrewPlaceholder가 collectAsState를 쓰고, 연결된 상태에서 '저울 찾기'를 누르면 S2가 열리자마자 닫힘 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/nav/Placeholders.kt:42-54
- [qa] 설정에서 돌아올 때 refreshEnvironment가 두 번 불림(MainActivity.onResume과 S2 LifecycleResumeEffect) — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:88
- [kotlin] S2 안내 이전 상태가 remember라 회전·재진입마다 같은 안내가 다시 나온다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:124
- [kotlin] 탭 보임 전환이 LaunchedEffect(snapTo)라 화면 전환 직후 한 프레임 탭이 보였다 사라진다 — app/src/main/java/com/timemoredot/app/ui/nav/Navigation.kt:75
- [kotlin] 후보 정렬 순서(order)가 remember라 회전하면 정렬 결과와 눌림 기록을 잃는다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:298
- [kotlin] SideEffect 안에서 Compose 상태를 쓴다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:302
- [kotlin] startActivity 예외 처리가 없어 설정 화면이 없는 기기에서 앱이 죽는다 — app/src/main/java/com/timemoredot/app/ui/SystemActions.kt:36
- [kotlin] 앱 시작 직후 영구 거부 상태를 알지 못해 첫 탭이 헛돈다 — app/src/main/java/com/timemoredot/app/PermissionMonitor.kt:25
- [kotlin] SharedPrefs 임계값을 Float로 저장해 재시작 뒤 값이 달라진다 — app/src/main/java/com/timemoredot/app/AppPrefs.kt:22
- [kotlin] StatusAnnouncer의 보이지 않는 문자가 소스에 그대로 들어 있다 — app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:50
- [kotlin] MainActivity와 S2가 onResume마다 refreshEnvironment를 중복 호출한다 — app/src/main/java/com/timemoredot/app/MainActivity.kt:55
- [a11y] Signal text '보통' / '약함' has no noun; the TalkBack row name is ambiguous — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:235-244
- [a11y] The row being connected is announced as disabled — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:173,263
- [a11y] The LinearProgressIndicator is an extra TalkBack stop that repeats the hint text — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:138-142
- [a11y] S2 announcement history is lost on rotation and resets the announcement — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:124-128
- [a11y] No heading or pane title in S2 empty states or in the S3 sheet — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:155; app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:68
- [a11y] Font 200% coverage misses the longest S2 states, and the fixed bottom bar may squeeze reflow — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:145,271-291; screenshots/connect_permission_denied_light_font200.png
- [a11y] Visible label and accessible name differ for unnamed candidates — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:252-254
- [a11y] The reason why '다른 저울 찾기' is disabled is only in stateDescription — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:89-94
- [a11y] Hidden navigation tabs may stay in the accessibility tree on non-tab screens (unverified) — app/src/main/java/com/timemoredot/app/ui/nav/Navigation.kt:75-77
- [a11y] Placeholder 'Find scale' button is below the 56 dp rule, and LocalAnnouncer default crashes without a provider — app/src/main/java/com/timemoredot/app/ui/nav/Placeholders.kt:54; app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:29
- [visual] 후보 목록 내용이 화면의 다른 요소보다 4 dp 안쪽에서 시작한다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:260
- [visual] 후보 행의 누름 리플은 직사각형인데 포커스 링은 12 dp 둥근 모양이다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:262
- [visual] S2 안내문이 Scanning·멈춤·연결 중에서 매번 바뀌고, 명세 3.3 문구와도 다르다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:210
- [visual] API 30 권한 설명이 가운데 정렬 3줄이고, 마지막 줄에 '않습니다.' 한 낱말만 남는다 — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:158
- [visual] BluetoothOff·권한·LocationOff 네 상태가 모두 같은 bluetooth_disabled 아이콘이다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:191
- [visual] S3 시트 안 상태 칩의 컨테이너가 시트 면과 거의 구별되지 않는다(테두리 없음) — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:107
- [visual] S3 '확인 중'·'알 수 없음' 자리 값이 실제 값과 같은 스타일이다 — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:125
- [visual] S3 연결 안 됨·실패 상태에서 설명과 주 버튼 사이가 12 dp로, 다른 곳보다 좁다 — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:66
- [visual] 명세 3.8과 5.4가 S3 연결 안 됨 상태의 동작을 다르게 정한다 — docs/spec/ui-design.md:200
- [visual] S3 스크린샷이 실제 ModalBottomSheet가 아니라 흉내 낸 틀로 찍혀 손잡이·여백·비활성 상태를 검증할 수 없다 — app/src/test/java/com/timemoredot/app/ui/device/DeviceSheetScreenshotTest.kt:45
- [visual] 이름 없는 후보의 주소 꼬리가 tnum이 아니고, 배터리 오류색 대비가 명세 대비 표에 없다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:251
