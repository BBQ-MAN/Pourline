# design-review wf_771ab4fa-5a7 후속 항목 (2026-10-28)

## 사용자 결정
- 과부하 중 무게: 마지막 정상 값을 멈춘 값 스타일로 표시(사유 "과부하")
- 기본 레시피: 핸드드립 2~3개를 첫 실행 때 넣는다
- 앱 안 g/oz 전환: MVP에 넣지 않는다. 저울 단위를 따른다
- 처리 방식: 설계자 수정 후 consistency 관점만 재검증. GATT-1은 analyst가 protocol.md 앱 규칙으로 갱신(protocol-verify 전체 재실행 없음)

## postRevision (9)

### p1. Q9 S1 배너 ScanFailed·Disconnected(추출 중 사용자 끊음)가 BrewBanner에 없음
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** architecture.md 9.4 BrewBanner(929-932줄) ↔ ui-design.md 10.3 배너 우선순위(791줄), 5.1 ScanFailed 행(287줄)·Disconnected × Running·Paused 행(284줄)
- **evidence:** ui-design 10.3 우선순위: "`Failed` > `ScanFailed` > `Disconnected`(`Running`·`Paused` 중 사용자 끊음만) > `Reconnecting`". 같은 줄에 "architecture 9.4 `BrewBanner`에 `LocationOff`, `ScanFailed`, `Disconnected`가 더해져야 한다"고 적혀 있다. architecture 9.4 enum은 `BluetoothOff, PermissionMissing, PermissionPermanentlyDenied, LocationOff, Failed, Reconnecting, Connecting, Overload, NoData, UnitUnknownTimedOut, ModelMismatch, WaitingForPour`이고 ScanFailed·Disconnected가 없다. 이번에 LocationOff만 들어갔다. 그래서 S1 ScanFailed 오류 배너 "찾기를 시작하지 못했습니다…"를 상태 모델로 나타낼 수 없다. 추출 중 S3 "연결 끊기"에서 띄울 오류 배너 "타이머는 계속 가지만 무게는 기록되지 않습니다"와 "다시 연결"도 나타낼 수 없다. 사용자는 추출 중 무게 기록이 멈춘 것을 배너로 보지 못한다. M8 배너 우선순위 테스트도 이 둘을 검사하지 않는다.
- **fix:** ui-design 10.3을 기준으로 맞춘다. architecture 9.4 BrewBanner에서 `Failed` 다음에 `ScanFailed`, 그 다음에 `Disconnected`를 넣는다. Disconnected 배너는 ConnectionUi.Disconnected이고 phase가 Running·Paused일 때만 켜진다고 주석에 적는다. milestones M8 BrewViewModelTest 배너 우선순위 조건에도 두 값을 더한다.

### p2. Q9 TalkBack 알림 계약 충돌: BrewAnnouncement의 모양·이름·출처·Assertive 원인이 두 문서에서 다름
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** architecture.md 9.4 BrewAnnouncement(1010-1018줄)·알림 책임(1021줄) ↔ ui-design.md 8.3 신호 출처 규칙(681줄)·S1 알림 표(647-663줄)
- **evidence:** ui-design 8.3: "상태(`connection`, `readout.reading`, `phase` …)의 전이로 알 수 있는 알림은 화면이 이전 값과 비교해 만든다". 같은 절의 `BrewAnnouncement` = `TareSucceeded`, `TimerChanged(source: App | Scale | Auto, to, toSummary)`, `AutoFinished`, `CapReached`이고, 끝에 "(현재 계약에 없음)"이라고 적혀 있다. architecture 9.4: "이벤트성 알림(8.3)은 `BrewViewModel`이 위 `Announce`로 낸다". 그 enum은 `Connected, Reconnected, ReconnectStarted, ConnectFailed, Overload, … TareDone, … BluetoothLostDuringBrew`다. 차이는 다섯 가지다. (1) 연결·과부하 알림을 누가 만드는지 반대다. (2) 이름이 TareSucceeded와 TareDone으로 다르다. (3) CapReached가 architecture에 없다. (4) ui-design의 Assertive 네 원인 중 `Running·Paused 중 Failed(*)`와 `추출 중 사용자 끊음`에 해당하는 값이 architecture에 없다. BluetoothLostDuringBrew 하나가 블루투스 꺼짐과 권한 철회를 함께 덮는다. (5) Idle 끊김 Polite "저울 연결 끊김"에 해당하는 값도 없다. 구현자가 architecture대로 만들면 재연결 10회 소진이나 추출 중 사용자 끊음 때 Assertive 알림이 나가지 않는다. TalkBack 사용자는 무게 수신이 멈춘 것을 모른다(B12). milestones M8은 architecture 이름(Announce(TareDone), Announce(ReconnectStarted))으로 검사한다.
- **fix:** architecture 9.4를 기준으로 하나로 맞춘다. milestones M8이 이미 그 이름을 쓰기 때문이다. architecture enum에 세 값을 더한다: FailedDuringBrew·DisconnectedDuringBrew(Assertive), Disconnected(Idle Polite), CapReached. BluetoothLostDuringBrew는 BluetoothOffDuringBrew와 PermissionLostDuringBrew로 나눈다. ui-design 8.3의 신호 출처 규칙과 BrewAnnouncement 정의는 architecture 9.4 enum을 참조하도록 고친다. '화면이 전이를 비교한다'는 문장은 지운다.

### p3. Q9 StatusAnnouncer 위치 충돌: 앱 루트 하나 vs 화면마다 하나
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** architecture.md 9.2 TalkBack 상태 알림 항목(900줄) ↔ ui-design.md 8.3 첫 문단(641줄)·8.5(717줄)
- **evidence:** ui-design 8.3: "이 노드는 화면 안이 아니라 앱 루트(`NavHost` 바깥, 최상위 `Box`)에 둔다. 그래야 자동 완료처럼 알림 직후 화면이 바뀌어도 노드가 사라지지 않아 알림이 읽힌다. … `announce(text, assertive)`, 앱 루트가 `CompositionLocal`로 제공". architecture 9.2: "알림 노드는 화면마다 하나 둔다". architecture대로 S1 안에 노드를 두면 문제가 생긴다. 자동 완료·저울 버튼 리셋·최대 시간 도달에서 Announce 직후 S6으로 넘어가면 S1 노드가 사라진다. 그러면 "추출 완료, 요약 화면으로 이동"이 읽히지 않는다.
- **fix:** ui-design 8.3을 기준으로 맞춘다. architecture 9.2 문장을 이렇게 바꾼다: '알림 노드는 앱 루트의 StatusAnnouncer 하나(ui-design 8.3), 화면은 CompositionLocal의 announce(text, assertive)로 넘긴다'. 9.1 내비게이션 절이나 8.1에 그 UI 계층 객체를 적는다.

### p4. Q8 타이머 상한 59:59.9 자동 완료를 엔진·컨트롤러가 만들지 않음
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** ui-design.md 9절 타이머 행(761줄)·5.2 최대 기록 시간 행(319줄) ↔ architecture.md 7.2 전이 규칙표·8.2 효과 처리(679줄)·9.8 타이머 상한 행(1152줄)
- **evidence:** ui-design 9절: "상한 59:59.9: 도달하면(또는 샘플 버퍼 36 000개가 먼저 차면) 자동으로 `Finished` → S6". 5.2: "타이머 59:59.9 또는 샘플 버퍼 가득 참 중 먼저". architecture는 버퍼 가득 참만 다룬다. 해당 문장은 "`AppendSample` → `samples.append`(false = 버퍼 가득 참이면 `Finish(atNanos, auto = true)`"다. 타이머 경과를 보는 전이가 없다. Running 중 끊김이나 NoData 구간에서는 샘플이 쌓이지 않고 타이머는 계속 간다. 그래서 버퍼가 차기 전에 60분을 넘길 수 있다. 그때 `MM:SS.s`가 60:00.0 이상으로 넘쳐 표시된다. 자동 완료가 버퍼 가득 참으로 일어나도 PhaseCause가 AutoFinish라 AutoFinished("추출 완료") 알림만 나간다. ui-design의 "최대 기록 시간 60분에 도달" 문구(CapReached)는 나오지 않는다. architecture 9.8에는 아직 "ui-design 9절 '99:59.9에서 멈춤'"이 남아 있다.
- **fix:** ui-design을 기준으로 맞춘다. architecture 7.2에 행을 더한다: Running·Paused에서 `Tick`일 때 timer.elapsedNanos ≥ 3_599_900_000이면 Finished + Completed. BrewConfig에 maxElapsedNanos를 둔다. 버퍼 가득 참과 타이머 상한을 AutoFinish와 구별되는 원인으로 낸다(예: PhaseCause.CapReached). 그래야 CapReached 알림을 만들 수 있다. 9.8 타이머 상한 행을 지우거나 59:59.9로 고친다. milestones M5·M8에 타이머 상한 테스트 조건을 더한다.

### p5. Q18 영점 중 끊김 스낵바: ui-design은 별도 문구에 '다시' 없음, architecture·M8은 TareTimeout으로 합침
- **severity:** low
- **targets:** architecture, ui-design
- **location:** ui-design.md 5.2 영점 중 연결 끊김 행(323줄) ↔ architecture.md 8.2 BrewEvent.TareFailed 주석(668줄)·9.4 BrewMessage(1007줄)·9.8 영점 중 끊김 행(1155줄), milestones.json M8 BrewViewModelTest
- **evidence:** ui-design 5.2: "영점 중 연결 끊김(`TareFailed(NotConnected)`·`TareFailed(Disconnected)`) | 스낵바 '연결이 끊겨 영점을 맞추지 못했습니다.' | 없음(다시 보내도 실패하므로 '다시'를 두지 않는다)". architecture: "`enum class BrewMessage { TareRejected, TareTimeout, ScaleTimerSyncFailed, Saved }` // TareFailed의 NotConnected·Disconnected도 TareTimeout". 9.8에는 "다른 문서 할 일: 없음(5.2 문구 그대로)"이라고 적혀 있다. M8에는 "TareFailed(Disconnected)는 Snackbar(TareTimeout)이다"가 있다. architecture대로 구현하면 끊김 때 "저울이 응답하지 않습니다" 문구와 "다시" 동작이 나온다. 다시 눌러도 NotConnected로 다시 실패한다.
- **fix:** ui-design 5.2를 기준으로 맞춘다. 끊김을 응답 없음과 나눠 안내하는 쪽이 맞다. architecture BrewMessage에 TareNotConnected를 더한다. NotConnected·Disconnected는 Snackbar(TareNotConnected, retryTare=false)로 보낸다. 8.2 주석과 9.8 행을 고친다. milestones M8 조건은 'TareFailed(Disconnected)는 Snackbar(TareNotConnected, retryTare=false)'로 바꾼다.

### p6. Q9 Connecting 중 무게 영역: ui-design은 --.-, ReadingUi 매핑은 Stale(Disconnected)
- **severity:** low
- **targets:** architecture, ui-design
- **location:** architecture.md 9.3 매핑 1번(916줄) ↔ ui-design.md 5.1 Connecting 행(292줄)·4.2 상태별 모양
- **evidence:** architecture 9.3 매핑 1번은 `NoDevice`·`Disconnected`·`Scanning`·`ScanEmpty`·`ScanFailed`·`PermissionMissing`·`PermissionPermanentlyDenied`·`LocationOff`만 None으로 보낸다. Connecting은 이 목록에 없다. 3번은 "엔진 `stale != null`이고 무게 있음 → `Stale`"이다. 재현 경로는 이렇다. Idle에서 S3 "연결 끊기"를 누르면 Disconnected가 되고 1번에 걸려 None(`--.-`)이 된다. 엔진은 stale=Disconnected와 마지막 무게를 갖고 있다. 이때 "다시 연결"을 누르면 Connecting이 되고, 1번에 걸리지 않아 3번 Stale(Disconnected)로 간다. 화면에 "멈춘 값 · 연결 끊김 · N초 전"이 다시 나타난다. ui-design 5.1 Connecting 행의 무게 영역은 `--.-`다. 결과적으로 표시가 `--.-` → 멈춘 값 → `--.-`(UnitUnknown) → 실시간 순서로 튄다.
- **fix:** ui-design 5.1을 기준으로 맞춘다. architecture 9.3 매핑 1번 목록에 `Connecting`을 더한다. Running·Paused가 아닐 때만 None이 되는 조건은 그대로 둔다. milestones M8 ReadingUiMappingTest에 'Idle에서 사용자 끊음 뒤 Connecting + 무게 있음 → None'을 더한다.

### p7. milestones.json이 ui-design 8.6 추가 스크린샷 일부를 받지 않음
- **severity:** low
- **targets:** architecture
- **location:** ui-design.md 8.6 추가 스크린샷 목록(747-750줄) ↔ milestones.json M7(162줄)·M8(190줄)·M9 스크린샷 조건
- **evidence:** ui-design 8.6: "milestones.json M7·M8·M9 스크린샷 목록이 받아야 한다". 목록과 대조하면 다섯 개가 빠졌다. M8에는 `brew_running_snackbar`(스낵바가 동작 줄 위), `brew_failed_timeout`, `brew_running_recipe_landscape_light_font200`이 없다. M8 font200은 `brew_reconnecting_running_light_font200`뿐이다. M7에는 `connect_permission_missing_api30`(API 30 이하 위치 권한 문구)이 없다. M9 상태 목록 'Ready, HasGaps, InvalidDose, Saving, SaveFailed'에는 `summary_loading`이 없다. 이 상태들은 디자인 검증 단계에서 화면 근거가 없어 검사되지 않는다.
- **fix:** ui-design 8.6을 기준으로 맞춘다. milestones.json의 담당은 architect라서 targets를 architecture로 두었다. M8 S1 스크린샷 조건에 brew_running_snackbar, brew_failed_timeout, brew_running_recipe_landscape_light_font200을 더한다. M7 S2 조건에 connect_permission_missing_api30, M9 조건에 Loading(summary_loading)을 더한다.

### p8. 서로 반영한 뒤에도 남은 '현재 계약에 없음'·'다른 문서 할 일' 주석이 실제 계약과 반대
- **severity:** low
- **targets:** ui-design, architecture
- **location:** ui-design.md 1절(45줄), 4.4(227줄), 5.1 주(304줄), 5.3(348줄), 8.3(681줄) ↔ architecture.md 9.8 표(1141-1156줄), 9.1 S6 뒤로 가기 행(825줄)
- **evidence:** ui-design에 이미 architecture에 반영된 내용을 아직 없다고 적은 주석이 남아 있다. 4.4는 "architecture 7.2의 `offsetMs` 정의(현재 Paused 구간 포함)를 … 바꾸는 것이 전제"라고 적지만, architecture 3절·7.2는 이미 일시정지를 뺀다. 5.1 주는 "`connectLastDevice()`가 … (현재 계약에 없음)"이라고 적지만, 5.4에 이미 있다. 5.3은 "(현재 계약은 저장 주소를 쓴다)"라고 적지만, 9.2 onRetryClick은 이미 마지막 시도 주소를 쓴다. 8.3은 "(현재 계약에 없음)"이라고 적지만, 9.4에 Announce가 있다. 1절은 D6을 "ViewModel에 대화상자 표시 상태와 확인 함수가 있어야 한다 … architecture 9.1·9.4·9.5가 받아야 한다"고 적는다. 반면 architecture 9.1·9.5는 "`SummaryScreen` 로컬 상태(`rememberSaveable`)"로 정했다. architecture 9.8도 이미 해결된 항목(시간 축, 추출 중 영점, S1 스캔 상태, S3, 원두 범위, D3 진입점)을 '다른 문서 할 일'로 남겼다. 타이머 행은 없어진 "99:59.9"를 인용한다. 구현자나 다음 검증자가 해결된 항목을 다시 수정 요청할 수 있다. D6은 VM 상태를 따로 만들 수도 있다.
- **fix:** architecture 계약을 기준으로 맞춘다. ui-design의 다섯 주석에서 '현재 계약에 없음/전제' 표현을 지우고 해당 architecture 절 참조로 바꾼다. 1절 D6 문장은 'SummaryScreen 로컬 상태(rememberSaveable), 선택은 onSave·D3'으로 고친다. architecture 9.8에서 반영된 행을 지운다. 남은 행만 두고, 타이머 상한 행은 59:59.9 기준으로 다시 쓴다.

### p9. GATT-1 연결 순서: architecture·M4는 ReadTimer를 포함, protocol.md 1절에는 없음
- **severity:** low
- **targets:** protocol, architecture
- **location:** protocol.md 1절 연결 순서(89줄) ↔ architecture.md 6절 연결 순서(395줄)·9.8 마지막 행(1156줄), milestones.json M4 연결 순서 조건
- **evidence:** protocol.md: "연결 → 서비스 탐색 → FFF1 CCCD 구독 → 0x06 단위 읽기 → 0x05 배터리 읽기 → (선택) 0x2A24·0x2A26 읽기". architecture 6절: "… `ReadUnit` → `ReadBattery` → `ReadTimer` → 0x2A24·0x2A26". 이 ReadTimer 응답은 이번 수정에서 8.2 '연결 직후 맞추기'의 근거가 됐다. M4는 호출 순서 '… ReadUnit → ReadBattery → ReadTimer'로 검사한다. 명세와 구현 기준이 서로 다르다. 프로브 A와 B6이 protocol.md 순서로 캡처를 판정하면 0x02 읽기 프레임을 예상 밖 프레임으로 볼 수 있다.
- **fix:** architecture를 기준으로 맞춘다. protocol.md 1절 GATT-1 순서를 고친다. 0x05 배터리 읽기 뒤에 '0x02 타이머 읽기(결과는 앱 상태와 맞추는 데에만 씀)'를 더한다. 0x06 앞의 requestMtu(247)도 2.4와 함께 적는다. 0절 GATT-1 행과 변경 이력도 갱신한다. architecture 9.8 마지막 행은 반영 뒤 지운다.

## declined (2)

### d1. GATT-1 연결 순서가 protocol.md와 다르다(ReadTimer 유무)
- **reason:** fix는 architecture를 기준으로 두고 protocol.md GATT-1만 고치라고 한다. protocol.md는 이 단계의 출력 파일이 아니고 analyst가 관리한다. architecture 쪽에는 이미 ReadTimer가 있다. §6에는 'ReadTimer 결과는 status만 채우고 맞추기에만 쓴다'는 주석을 더했고, §9.8에는 protocol.md 할 일을 적었다. 실제 protocol.md 수정은 analyst가 해야 한다.

### d2. Q8 자동 시작 임계값 비교(>)와 화면 문구('1 g 이상', 고정값)가 다르다
- **reason:** fix는 architecture의 '>'를 기준으로 두고 ui-design 5.2 문구만 바꾸라고 한다. architecture에는 고칠 곳이 없다. BrewUiState.settings가 이미 임계값을 넘긴다. ui-design 쪽 할 일은 §9.8에 적었다. ui-design.md 수정은 ui-designer가 해야 한다.

## protocolIssues (2)

### p1. GATT-1 연결 순서가 protocol.md와 다르다(ReadTimer 유무)
- **severity:** low
- **targets:** architecture, protocol
- **location:** docs/spec/architecture.md §6 연결 순서 / docs/spec/protocol.md 1절 GATT-1(89줄) / milestones.json M4
- **evidence:** architecture와 M4 테스트는 ReadUnit → ReadBattery → ReadTimer 순서이다. protocol.md GATT-1은 0x06 → 0x05 → (선택) DIS뿐이고 0x02 읽기가 없다. android-ble 3절은 초기 읽기에 타이머 상태를 넣는다.
- **fix:** android-ble 규칙을 따르는 architecture를 기준으로 한다. protocol.md GATT-1에 '0x02 타이머 읽기'를 더한다. 첫 번째 지적의 해결처럼, 이 읽기 결과는 맞추는 데에만 쓴다는 주석도 함께 단다.
- **lens:** architecture

### p2. Q9 재연결 직후 ReadTimer 응답이 ScaleTimerChanged로 엔진에 들어가 추출이 저절로 끝나거나 다시 시작된다
- **severity:** medium
- **targets:** architecture, protocol
- **location:** architecture.md 6절 연결 순서·끊김, 8.2절 입력 변환, 7.2절 전이 규칙표 / protocol.md 1절 GATT-1
- **evidence:** architecture 6절: 끊김 시 환원기를 reset()하고(status = Unknown, scaleTimer=null), 재연결 순서는 ReadUnit → ReadBattery → ReadTimer다. 8.2절: `scale.status.scaleTimer 변화 → ScaleTimerChanged`. 7.2 표: `Running, Paused | ScaleTimerChanged(Reset) | 샘플이 있으면 Finished … Completed`, `Paused | ScaleTimerChanged(Running) | Running`. 반면 ui-design 2.2·5.1 Reconnecting 문구는 "추출 중이면 타이머는 계속 갑니다"이고, M8 B6은 '추출 중 저울 전원 끄기 → 켜기에서 … 자동 재연결된다'를 요구한다. 오작동 1: 추출 중 저울 전원을 껐다 켜면 저울 타이머는 초기 상태(03 리셋)로 읽힌다. 그러면 Running이 Finished로 바뀌고 S6으로 넘어가 추출이 끊긴다. 오작동 2: 끊긴 동안 앱에서 일시정지를 누르면(ui 4.6에서 허용) SetTimer가 NotConnected로 실패한다. 재연결 뒤 ReadTimer가 Running을 돌려주면 앱이 스스로 다시 Running이 된다.
- **fix:** 기준은 architecture.md로 잡는다. 8.2절에서 연결 직후 ReadTimer의 Read 응답(Op.Read)은 ScaleTimerChanged로 넘기지 않는다고 정한다. Read 응답 상태가 엔진 phase와 다르면 앱 상태를 SetTimer로 저울에 다시 보낸다(앱이 기준). ScaleTimerChanged는 Notify(Op.Notify)에서 온 변화에만 쓴다. ScaleStatus에 출처를 담거나 reducer가 Read와 Notify를 구분하게 한다. protocol.md GATT-1에 ReadTimer 단계를 넣고 이 규칙을 적는다. M8 BrewSessionControllerTest에 '재연결 뒤 Read 응답 Reset이면 phase 유지' 조건을 더한다.
- **lens:** consistency
