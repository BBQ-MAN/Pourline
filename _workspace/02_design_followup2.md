# design-review wf_23dc8f07-fc5 후속 7건 (2026-10-29)

사용자 결정: 남은 7건을 고치고 설계 승인. 재검증 워크플로 없이 반영 확인 후 확정.

## p1. Q9 S1 알림 계약 충돌: ui-design 8.3이 AutoStartCancelled를 새로 넣었지만 architecture 9.4와 M8은 WaitingForPour→Idle에 알림을 내지 않는다
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** ui-design.md 8.3 S1 알림 표 'WaitingForPour → Idle' 행(732줄)과 신호 출처 규칙(759줄) ↔ architecture.md 9.4 BrewAnnouncement enum(1126-1144줄), PhaseChanged 표 'WaitingForPour | Idle | User' 행(1152줄), 9.8 맞춤 완료 문단(1302줄)·남은 차이 PhaseChanged 행(1312줄), milestones.json M8 BrewViewModelTest(알림)
- **evidence:** ui-design 8.3(이번 수정, 12절 '`AutoStartCancelled` 추가'): "`WaitingForPour` → `Idle`(cause = User: "취소", S4에서 자동 시작 끔 …) | Polite | "자동 시작 취소" | `AutoStartCancelled`". 같은 절 759줄은 "architecture 9.4의 (from, to, cause) → `BrewAnnouncement` 표와 M8 알림 테스트는 이 행들과 같아야 한다"고 적는다. architecture 9.4 enum에는 AutoStartCancelled가 없다. PhaseChanged 표는 "WaitingForPour | Idle | User (`CancelWaiting`, `SettingsChanged`(자동 시작 꺼짐)) | 없음 (사용자가 방금 누른 동작의 결과다)"다. 9.8 남은 차이 행은 ui-design에 '자동 시작 대기 취소'를 "알림 없음" 전이로 적으라고 요구했다. M8 조건도 "'없음' 행(WaitingForPour→Idle, …)은 0번 나온다"다. 반면 9.8 맞춤 완료 문단은 "S1 알림 출처와 이름(8.3 = 9.4 enum)"이 맞춰졌다고 적고 있어 이제 사실이 아니다. 구현자가 architecture·M8대로 만들면 디자인 검증(ui-design 기준)에서 알림 누락으로, ui-design대로 만들면 QA(M8)에서 0번 조건 위반으로 걸린다. 수정 요청이 두 방향으로 반복된다.
- **fix:** architecture 9.4를 기준으로 맞춘다. 9.8에서 이미 '알림 없음'으로 정했고 M8 테스트도 그 기준이기 때문이다. ui-design 8.3의 732줄 행을 '알림 없음(사용자가 방금 누른 동작의 결과)' 행으로 바꾸고 AutoStartCancelled를 지운다. 이 알림을 원한다면 반대로 바꾼다: architecture 9.4 enum과 PhaseChanged 표에 AutoStartCancelled를 넣고, M8의 '없음' 목록에서 WaitingForPour→Idle을 뺀다. 어느 쪽이든 architecture 9.8 맞춤 완료 문장은 실제 상태에 맞게 고친다.

## p2. Q9 S1 BrewAnnouncer가 화면 안에 자기 liveRegion 노드를 두지만 architecture 9.1·M7은 liveRegion을 StatusAnnouncer.kt 한 곳에만 허용한다
- **severity:** medium
- **targets:** ui-design, architecture
- **location:** ui-design.md 8.2 '알림 노드 BrewAnnouncer' 문단(701줄) ↔ architecture.md 9.1 StatusAnnouncer 아래 항목(923줄)·9.4 알림 책임(1172줄), milestones.json M7 StatusAnnouncer 조건(183줄)·M8 S1 BrewAnnouncer 조건(213줄)
- **evidence:** ui-design 8.2: "알림 노드 `BrewAnnouncer`: S1에 1 dp 크기 보이지 않는 노드 하나를 두고 `Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement }`". architecture 9.1: "앱 안에서 `liveRegion`을 거는 곳은 이 파일뿐이다"(StatusAnnouncer.kt). M7: "grep -rn 'liveRegion' app/src/main 결과가 StatusAnnouncer.kt 줄뿐이다". M8은 ui-design 8.2대로 BrewAnnouncer를 만들라고 요구한다. ui-design 8.2대로 S1에 노드를 두면 M8에서 M7의 grep 조건이 깨진다. 또 8.3이 노드를 앱 루트에 둔 이유(알림 직후 S6으로 넘어가도 노드가 남음)가 이 노드에는 적용되지 않는다. 그러면 '끝 시각' 같은 낭독 직후 자동 완료로 화면이 넘어가면 그 문구가 읽히지 않을 수 있다. architecture 9.4 1172줄은 값 낭독을 화면이 만든다고만 적고, 어떤 노드로 내는지는 정하지 않았다.
- **fix:** architecture 9.1(liveRegion은 한 곳)을 기준으로 맞춘다. ui-design 8.2 문단을 고친다: BrewAnnouncer는 자기 노드를 두지 않는다. S1이 계산한 announcement가 바뀔 때 LocalAnnouncer.current.announce(text, assertive = false)로 넘긴다. '같은 문구가 연속되면 바꾸지 않는다'는 announce 호출을 건너뛰는 규칙으로 바꾼다. architecture 9.4 알림 책임 문장에도 '값 낭독도 LocalAnnouncer로 낸다'를 한 줄 더한다.

## p3. Q9 ui-design 5.1·4.7의 S1 연결 상태 Polite 알림 다수에 BrewAnnouncement 값이 없고, 화면이 배너 전이로 알림을 만든다는 문장이 architecture 9.4와 반대다
- **severity:** low
- **targets:** ui-design, architecture
- **location:** ui-design.md 5.1 TalkBack 열의 BluetoothOff·PermissionMissing·PermissionPermanentlyDenied·LocationOff(Idle, Polite)·Connecting(Polite "연결 중")·UnitUnknownTimedOut·ModelMismatch 행(333-343줄), 4.7 첫 문단(301줄) ↔ ui-design.md 8.3 S1 표(720-741줄)·신호 출처 규칙(759줄), architecture.md 9.4 BrewAnnouncement(1124-1144줄)
- **evidence:** ui-design 5.1(이번 수정에서 권한·위치 행을 고침)은 BluetoothOff·PermissionMissing·PermissionPermanentlyDenied·LocationOff를 Idle에서 'Polite'로 적는다. Connecting은 'Polite "연결 중"', UnitUnknownTimedOut·ModelMismatch도 'Polite'다. ui-design 8.3 S1 표와 architecture 9.4 enum에는 이 전이에 맞는 값이 없다(DuringBrew 4종, Connected, Reconnected, ReconnectStarted, ConnectFailed, Disconnected, NoData, Overload만 있다). architecture 9.4: "출처는 모두 BrewViewModel이고, 화면은 이전 상태와 비교해 알림을 만들지 않는다". ui-design 4.7: "배너가 새로 나타나면 그 화면의 알림 문구를 앱 알림 노드(8.3)로 보낸다". 8.3 759줄은 "S1 알림은 모두 BrewViewModel이 내는 Announce로 받는다"고 적는다. 구현자가 5.1·4.7을 따르면 S1 화면이 배너 전이로 직접 announce를 부른다. 그러면 VM의 Announce와 같은 전이(예: Failed)가 두 번 읽힌다. architecture를 따르면 5.1에 적힌 Polite 알림이 나가지 않아 디자인 검증에서 누락으로 잡힌다.
- **fix:** ui-design 8.3 S1 표(= architecture 9.4 enum)를 기준으로 맞춘다. 5.1 TalkBack 열에서 enum 값이 없는 Polite 항목을 '없음(배너·칩으로 표시)'으로 바꾼다. 4.7의 '배너가 새로 나타나면 … 알림 노드로 보낸다'는 'S1은 8.3 표의 Announce만, S2·S6은 architecture 9.2 규칙'으로 바꾼다. 이 알림들이 필요하다고 판단하면 반대로 바꾼다: architecture 9.4 enum에 Connecting·BluetoothOff·PermissionMissing·LocationOff·UnitUnknownTimedOut·ModelMismatch(Polite)를 넣고, 8.3 S1 표와 M8 알림 테스트에 행을 더한다.

## p4. Q9 ui-design 10.2가 ReadingUi.None 조건을 '연결이 Connected가 아니면'으로 풀어 써 architecture 9.3 매핑 1번과 5.1 BluetoothOff·Reconnecting 행과 어긋난다
- **severity:** low
- **targets:** ui-design
- **location:** ui-design.md 10.2 첫 문장(865줄) ↔ architecture.md 9.3 매핑 1·3번(1016-1018줄), ui-design.md 5.1 BluetoothOff 행(333줄)·Reconnecting 행(344줄)·Failed(ConnectFailed) 행(345줄)
- **evidence:** ui-design 10.2(이번 수정 '10.2 None 조건'): "조건은 architecture 9.3 매핑 1번 전체를 따른다: 연결이 `Connected`가 아니고 엔진에 무게가 없거나 phase가 `Running`·`Paused`가 아닐 때". architecture 9.3 매핑 1번은 `NoDevice`·`Disconnected`·`Connecting`·`Scanning`·`ScanEmpty`·`ScanFailed`·`PermissionMissing`·`PermissionPermanentlyDenied`·`LocationOff` 아홉 가지만 든다. `BluetoothOff`·`Reconnecting`·`Failed`는 들지 않아 Idle에서도 무게가 있으면 3번 `Stale`이 된다. 5.1도 BluetoothOff를 '연결 후 꺼짐: 멈춘 값'으로, Reconnecting을 phase와 상관없이 '멈춘 값'으로 적는다. 10.2 문장대로 구현하면 Idle에서 끊겨 Reconnecting·BluetoothOff·Failed(ConnectFailed)가 될 때 무게가 `--.-`로 사라진다. 5.1과 architecture는 멈춘 값을 요구한다.
- **fix:** architecture 9.3 매핑 1번을 기준으로 ui-design 10.2를 고친다. '연결이 Connected가 아니고'를 'ConnectionUi가 NoDevice·Disconnected·Connecting·Scanning·ScanEmpty·ScanFailed·PermissionMissing·PermissionPermanentlyDenied·LocationOff 중 하나이고'로 바꾼다. 그리고 'BluetoothOff·Reconnecting·Failed는 phase와 상관없이 무게가 있으면 Stale(Disconnected)'라는 문장을 더한다.

## p5. S7 '추출 진행 중'과 canFindOtherScale의 Finished 처리가 ui-design에 반영되지 않았다(architecture 9.8이 요구한 남은 항목)
- **severity:** low
- **targets:** ui-design
- **location:** ui-design.md 2.4(87줄), 5.6 '추출 진행 중' 행(437줄), 3.8 S3(200줄), 10.2 canFindOtherScale(865줄) ↔ architecture.md 9.6 RecipeListUiState.canStartBrew(1210줄), 9.4 canFindOtherScale·onFindOtherScaleClick(1058, 1099줄), 9.8 남은 차이 S7 행(1313줄), milestones.json M8 BrewViewModelTest(다른 저울 찾기)
- **evidence:** architecture 9.6: "canStartBrew … controller phase가 Idle·WaitingForPour일 때만 true … Running·Paused·Finished("추출 진행 중")면 "추출" 비활성". 9.8 1313줄은 ui-design 2.4·5.6에 '추출 진행 중' 정의를 'phase가 Idle·WaitingForPour가 아닐 때(Running·Paused·Finished)'로 적으라고 요구한다. ui-design은 이번 수정에서 9.8의 다른 행들은 받았다. 하지만 2.4는 여전히 "진행 중이면 버튼 비활성"이고, 5.6은 정의 없이 "추출 진행 중"이다. 같은 방식으로 ui-design 3.8·10.2는 "다른 저울 찾기"를 "`Running`·`Paused`에서 false"로만 적는다. architecture 9.4는 `Finished`에서도 무시하고, M8은 'phase Running·Paused·Finished에서는 canFindOtherScale=false'로 검사한다. S6가 열린 동안 S7 "추출" 버튼이 Finished에서 비활성인 것을 디자인 검증이 결함으로 볼 수 있다.
- **fix:** architecture 9.6·9.4를 기준으로 ui-design을 고친다. 2.4와 5.6의 '추출 진행 중'을 'phase가 Idle·WaitingForPour가 아닐 때(Running·Paused·Finished)'로 정의한다. 3.8과 10.2의 canFindOtherScale 조건을 'Running·Paused·Finished에서 false'로 바꾼다.

## p6. architecture 9.8과 계약 주석이 ui-design 이번 판과 맞지 않는다: 반영된 항목이 '남은 차이'로 남았고, 인용한 화면 문구가 다르다
- **severity:** low
- **targets:** architecture
- **location:** architecture.md 9.8 남은 차이 표 1~5행(1308-1312줄)과 맞춤 완료 문단(1302줄), 9.2 ConnectionUi.Reconnecting 주석(948줄), 5.4 ScaleDevice.disReadDone 주석(308줄) ↔ ui-design.md 4.6(296줄), 10.3(871줄), 5.1 Failed 3행(345-347줄)·Reconnecting 행(344줄), 10.1(858줄), 5.4 DIS 읽는 중 행(406줄), 8.3(732-735줄)
- **evidence:** ui-design 12절의 이번 판 행은 다섯 가지를 반영했다고 적는다: canStart(4.6), NoData는 단위를 알 때만(10.3), Failed 행의 canFindOtherScale(5.1), 시도 중 문구(5.1·10.1), DIS 읽는 중(5.4). architecture 9.8 표에는 이 다섯 행이 아직 'ui-design.md가 맞출 곳'으로 남아 있다. 문구도 다르다. architecture 9.2 948줄은 "0 이하(시도 중)면 "N초 후" 대신 "다시 연결하는 중""이고, ui-design 5.1·10.1은 "지금 연결하는 중"이다. architecture 5.4 308줄 "false면 S3는 "읽는 중""과 9.8 1311줄 "읽는 중"은 ui-design 5.4 "확인 중"과 다르다. 9.8 머리말은 "두 문서가 다르면 이 표의 '이 문서' 열이 계약이다"라고 한다. 그래서 문구 소유자(ui-design)와 다른 architecture 인용이 계약처럼 읽힌다. 남은 행은 구현자나 다음 검증자가 이미 끝난 항목을 다시 수정 요청하게 만든다. PhaseChanged 행은 위 AutoStartCancelled 충돌 때문에 아직 미완이다.
- **fix:** ui-design을 기준으로 architecture를 고친다(화면 문구는 ui-design 소유, architecture 머리말). 9.8 남은 차이 표에서 반영된 1~5행을 지운다. PhaseChanged 행은 AutoStartCancelled 결정에 따라 지우거나 남긴다. S7 행은 ui-design이 반영할 때까지 둔다. 948줄 주석은 '"지금 연결하는 중"(ui-design 5.1)', 308줄 주석은 'S3는 "확인 중"(ui-design 5.4)'로 바꾼다. 맞춤 완료 문단의 '8.3 = 9.4 enum' 표현은 충돌이 풀린 뒤에 둔다.

## p7(protocol). C-0B-2 '끊김 감지 약 2.1~2.2 s'는 macOS 측정인데 확정으로 적혀 NoData 3 s 근거가 됐다
- **location:** protocol.md 0절 C-0B-2, C-02-2, 3.5 / architecture.md 7.2 `noDataTimeoutNanos` 주석, 4.1 C-01-12 행 / ui-design.md 0절 C-01-12·C-0B-2 행
- **evidence:** protocol C-0B-2의 상태는 "확정"이고 근거는 log:6736-6737·10765-10766(macOS bleak)뿐이다. 같은 표의 G-6은 "확정(macOS)"과 "Android에서는 프로브 B로 재확인"을 적지만 C-0B-2에는 플랫폼 한정이 없다. 전원 버튼을 누른 뒤 저울이 링크를 정상 종료하지 않으면 끊김 감지는 central이 정하는 supervision timeout에 달려 Android에서 달라질 수 있다. architecture 7.2는 "3 s: 전원 버튼으로 끄면 … 2.08~2.25 s에 끊김이 감지된다. 2 s면 NoData가 먼저 떴다가 Reconnecting으로 바뀐다"고 이 값에 기대어 3 s를 골랐다. Android 감지가 3 s를 넘으면 M8 B6 조건("NoData 배너를 거치지 않고 Reconnecting")이 깨진다.
- **fix:** protocol을 기준으로 C-0B-2를 '확정(macOS)'으로 바꾸고 'Android 감지 시간은 프로브 B(B6)로 확인'을 적는다. architecture 4.1 미확정 표에 'C-0B-2 (Android)'를 넣는다. 고칠 곳은 BrewConfig.noDataTimeoutNanos이고, 바뀌면 ui-design 0절 문구를 함께 고친다.