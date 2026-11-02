# design-review wf_01c38aae-76b 후속 (2026-10-29)

방식: 남은 항목을 설계자가 고친 뒤 grep으로 반영 확인하고 승인 요청(재검증 워크플로 없음).

## declined (7)

### d1. 영점 성공 응답 뒤에도 영점 전 무게가 2~3프레임 더 와서 자동 시작이 잘못 켜진다
- **reason:** protocol.md 부분만 반영하지 않았다. 새 C-0D-2 항목은 이 단계의 출력(architecture.md, milestones.json) 밖이다. 근거 로그 줄과 함께 architecture.md 4.2와 9.8 차이 표에 적어 analyst에게 넘긴다. architecture와 마일스톤 부분은 반영했다(7.2 무장 규칙, FIX-1, M8 테스트).

### d2. [Q5] 길이가 MTU−3을 넘는 송신의 앱 동작이 protocol.md 2.4(MTU 증가 뒤 나눠 쓰기)와 architecture 4.1(PayloadTooLarge 거부)에서 다름
- **reason:** 기준 문서는 architecture이고 이미 PayloadTooLarge 거부로 맞다. 실제로 고칠 곳(protocol.md 2.4)은 이 단계의 출력 밖이다. architecture 4.1 F-11 행과 9.8 표에 차이를 적었다.

### d3. StatusAnnouncer: 같은 문구가 연달아 오면 TalkBack이 다시 읽지 않을 수 있고, 1 dp 숨은 노드의 조건이 빠져 있음
- **reason:** ui-design.md 8.3 부분만 반영하지 않았다. 지적이 정한 기준 문서는 ui-design 8.3인데 그 규칙이 아직 그 문서에 없다. 같은 규칙을 architecture 9.1에 적고 9.8 표에 'ui-design 8.3이 맞출 곳'으로 남겼다. 테스트는 FIX-3과 M8에 넣었다.

### d4. D10: 정의되지 않은 화면 상태가 있다(S8 기존 레시피 불러오는 중, D1 제목·본문 문구와 파괴 버튼 색)
- **reason:** ui-design.md 부분만 반영하지 않았다: D1 제목·본문 문구, 5.7 Loading 행, 8.6 스크린샷 목록. 화면 문구는 ui-design 소유라 9.8 표에 적었다. architecture 부분(RecipeEditState.Loading)과 마일스톤 부분(M10 Loading, M8 D1 error 색·font200)은 반영했다.

### d5. [Q9] Failed(ServiceNotFound)·Failed(Timeout)의 무게 영역이 architecture 9.3 매핑과 다름(Idle에서 끊은 뒤 다시 연결에 실패하면 옛 저울 무게가 다시 뜸)
- **reason:** ui-design 5.1·10.2 문구 수정은 이 단계의 출력 밖이라 하지 않고 9.8 표에 적었다. 기준 문서인 architecture 9.3 매핑과 M8 테스트는 반영했다.

### d6. [Q8/Q9] 영점 대기 중 버튼 활성 규칙과 canTare 정의가 서로 어긋남
- **reason:** ui-design 10.2의 canTare 정의 수정은 이 단계의 출력 밖이라 하지 않고 9.8 표에 적었다. architecture 9.4와 M8은 반영했다.

### d7. [Q8] 표시 단위의 출처가 끊김 중 멈춘 값과 S6·S10 기록에서 정해져 있지 않음
- **reason:** ui-design 9절·4.4를 g 고정 결정에 맞추는 일은 이 단계의 출력 밖이라 하지 않고 9.8 표에 적었다. architecture 9.3·9.4·8.2(lastUnit, S6·S10 g 고정)와 M8 테스트는 반영했다.

## postRevision (6)

### p1. [Q9] StatusAnnouncer 300 ms 겹침 규칙이 두 문서에서 반대다: ui-design은 대기 중 Polite를 Assertive 뒤에 붙이고, architecture·FIX-3은 버린다
- **severity:** medium
- **targets:** architecture
- **location:** architecture.md 9.1 StatusAnnouncer 목록 '300 ms 겹침'(962행), milestones.json FIX-3 StatusAnnouncerTest(279행 부근) ↔ ui-design.md 8.3 노드 규칙 3·4(948행 부근)
- **evidence:** ui-design 8.3 규칙 3: "창 안에 Assertive 문구가 오면 창을 바로 닫고 Assertive 노드에 \"Assertive 문구. 대기 중이던 Polite 문구\"를 한 번 낸다(… 대기 중 Polite 문구는 버리지 않고 뒤에 붙인다)". 규칙 4 (3)은 "Assertive 노드 한 번, Assertive 문구가 앞"이라고 한다. architecture 9.1: "300 ms 안에 Assertive가 오면 아직 내지 않은 Polite 문구를 버리고 Assertive만 낸다 … 이 세 규칙이 ui-design에 아직 없어 9.8에 적었다". FIX-3 완료 조건: "Polite 'A' 뒤 Assertive 'C'면 Assertive 노드가 'C'"이다. ui-design 15절 8은 "기준은 이 문서"라고 적는다. 오작동 예: 추출 중 재연결이 시작될 때 Polite "연결 끊김, 다시 연결하는 중"이 나오고 300 ms 안에 블루투스 꺼짐 Assertive가 오면, architecture대로 만든 앱은 첫 알림을 TalkBack에서 영영 읽지 않는다. FIX-3 테스트도 ui-design과 반대인 동작을 통과 기준으로 삼는다.
- **fix:** ui-design 8.3을 기준으로 맞춘다. architecture 9.1 '300 ms 겹침'을 "Assertive 노드에 'Assertive 문구. 대기 중 Polite 문구'를 한 번 낸다(Polite를 버리지 않는다)"로 바꾸고, "ui-design에 아직 없어" 문장을 지운다. FIX-3 완료 조건을 "Assertive 노드 텍스트가 'C. A'이고 Polite 노드는 'A'로 설정되지 않는다"로 바꾼다.

### p2. [Q9] S1 확정 저장 실패(phase Finished, S6로 안 감) 상태에 화면 설계가 없다: 문구, 지속 시간, 동작 줄, 알림 모두 없다
- **severity:** high
- **targets:** ui-design, architecture
- **location:** architecture.md 8.2 retrySave·BrewEvent.SaveFailed(729·738·749행), 9.4 BrewMessage.SaveFailed·onRetrySaveClick(1188·1207행), 9.8 남은 차이 'S1 저장 실패'(1416행) ↔ ui-design.md 5.2 표, 4.6 동작 줄 표, 8.3 S1 알림 표, 10.3 'Finished(→ S6)'
- **evidence:** architecture 8.2: "Completed 확정 저장 실패: BrewEvent.SaveFailed를 내고 … phase는 엔진이 이미 Finished라 그대로다. retrySave()가 … 성공하면 Finished(id)". 9.4: "화면은 SaveFailed 스낵바에 '다시 시도' 동작을 두고 onRetrySaveClick()을 부른다(문구·지속 시간은 ui-design, 9.8)". 그런데 ui-design에는 S1 SaveFailed가 한 곳도 없다(grep 결과 SaveFailed는 S6 502행·S8 545행·S6 알림 990행뿐). 5.2에 행이 없고, 4.6 동작 줄 표에 Finished 행이 없으며, 10.3은 Finished를 'S6로 감'으로만 정의한다. 오작동 예: 완료 뒤 Room 저장이 실패하면 S1이 Finished에 머문다. 이때 showBottomBar·canChangeRecipeOrSettings·canFindOtherScale은 false이고, 동작 줄은 그릴 행이 없으며, isBackEnabled(Running·Paused만)도 꺼져 있다. 스낵바가 기본 시간 뒤 사라지면 '다시 시도'에 닿을 길이 없어 새 추출도 시작할 수 없다. 버퍼에 들고 있던 기록은 프로세스가 끝나면 사라진다.
- **fix:** architecture 8.2·9.4 계약은 그대로 두고 ui-design을 맞춘다. 5.2에 '확정 저장 실패' 행을 넣는다: 스낵바 문구, 동작 "다시 시도"(onRetrySaveClick), 지속 시간 Indefinite(TalkBack 여부와 상관없이, 사라지면 다시 시도할 수 없으므로)를 적는다. 4.6에 Finished(저장 대기·실패) 행을 넣는다: 최소한 '다시 저장'과 '버리기'(D1과 같은 확인)를 두어 막다른 상태가 없게 한다. 8.3 S1 표에 알림을 넣는다(스낵바 기본 낭독 여부). 10.3의 'Finished(→ S6)'에 '저장 실패 시 S1에 남음' 예외를 적는다. 그 뒤 architecture 9.8 남은 차이 행을 지운다.

### p3. [Q9] S2 Reconnecting 행과 S2 알림이 architecture 9.2와 milestones에 없다(ui-design 15절 8 요청이 반영되지 않음)
- **severity:** medium
- **targets:** architecture, ui-design
- **location:** ui-design.md 5.3 Reconnecting 행(472행), 8.3 다른 화면 알림 S2 행(987행), 8.6 connect_reconnecting(1078행), 15절 8 ↔ architecture.md 9.2 ConnectViewModel(onCancelConnectClick 1072행), 9.2 TalkBack 상태 알림 목록(1080행), milestones.json M7R/FIX-3
- **evidence:** ui-design 5.3: "Reconnecting(S2가 열린 채 저장된 저울로 자동 연결 …) … '취소'(disconnect(), 사용자 끊기 → Disconnected 행 …)". 8.3: "S2 | Reconnecting으로 바뀜(5.3) / 그 뒤 Connected | Polite | '저장된 저울에 다시 연결하는 중' / '저울 연결됨'". 15절 8: "6절·9.2: S2가 열린 채 … Reconnecting이 될 수 있음을 적고 … M7R 스크린샷 connect_reconnecting을 더한다". architecture 9.2 S2 알림 목록은 "Scanning 시작, ScanEmpty, ScanFailed, Failed, Connecting, PermissionMissing·BluetoothOff·LocationOff로 바뀜"뿐이고 Reconnecting이 없다. onCancelConnectClick 주석도 "Connecting '취소'"뿐이다. architecture와 milestones에서 connect_reconnecting을 grep하면 0건이다. 또 9.2 매핑에서 disconnect() 뒤 Idle이고 스캔 결과가 남아 있으면 ConnectionUi는 Disconnected가 아니라 Scanning(stopped = true)이다. 그래서 ui-design의 "→ Disconnected 행"은 후보가 있을 때 틀린다. 오작동: M7R·FIX-3 구현자는 architecture와 milestones만 보고 S2 Reconnecting 알림, 비활성 목록, 취소 버튼을 만들지 않는다. 그러면 S2에서 Reconnecting이 오면 그릴 분기가 없다.
- **fix:** ui-design 5.3·8.3을 기준으로 맞춘다. architecture 9.2에 'S2에서 Reconnecting 가능(5.4 환경 풀림)'을 적는다. 알림 목록에 Reconnecting 진입과 Reconnecting→Connected를 넣고, onCancelConnectClick 주석을 'Connecting·Reconnecting 취소 → disconnect()'로 바꾼다. milestones FIX-3(또는 M7R)에 ConnectViewModelTest(Reconnecting에서 onCancelConnectClick → disconnect)와 connect_reconnecting_light·_dark 스크린샷을 더한다. ui-design 5.3 '→ Disconnected 행'은 '후보가 남아 있으면 Scanning(stopped = true) 행, 없으면 Disconnected 행'으로 고친다.

### p4. milestones M8에 ui-design 8.5 읽기 순서 테스트와 8.6 새 스크린샷 일부가 없다
- **severity:** medium
- **targets:** architecture
- **location:** milestones.json M8 acceptance(S1 스크린샷 두 항목, Compose UI 테스트 항목) ↔ ui-design.md 8.5 '테스트(M8 Compose UI 테스트)'(1031행 부근), 8.6 '2026-10-29 설계 검증 반영 추가'(1073~1080행), 15절 8 마지막 줄
- **evidence:** ui-design 8.5: "테스트(M8 Compose UI 테스트): 위 세 순서를 단언한다 … TraversalIndex를 준 노드가 없는지도 확인한다. 경우: 세로 Idle(위 배너), 세로 Running + Reconnecting(7a), 가로 Running + 7a". 8.6은 "milestones.json M7·M8·M9 스크린샷 조건은 이 목록을 따른다"고 한 뒤 brew_reconnecting_running_recipe, brew_running_start_frame, brew_running_banner_resize_frame, brew_idle_negative_light_font200, brew_weight_worst_light_font200, brew_weight_worst_w320_light_font200을 든다. 15절 8: "milestones M8: 8.5 읽기 순서 테스트, 8.6 새 스크린샷". milestones.json에서 traversal·isTraversalGroup·brew_reconnecting_running_recipe·brew_running_start_frame·brew_running_banner_resize_frame·brew_weight_worst를 grep하면 모두 0건이다. 오작동: M8이 이 항목 없이 통과할 수 있다. 그러면 7a 배너가 bottomBar로 옮겨진 뒤의 TalkBack 순서, 첫 화면의 '지금 다시 시도'·'연결 끊기' 노출, 글꼴 200%에서 주 무게가 단위 아래 줄로 내려가는 배치가 검증되지 않는다.
- **fix:** ui-design 8.5·8.6을 기준으로 milestones M8을 고친다(담당: 코디네이터·android-architect). 완료 조건에 TraversalOrderTest(세 경우, TraversalIndex 노드 0개)를 넣는다. S1 스크린샷 조건에 위 여섯 이름을 _light·_dark로(font200 이름은 적힌 그대로) 넣는다.

### p5. architecture 9.8 '남은 차이' 표와 9.1 문장이 ui-design에서 이미 반영된 항목을 아직 열린 차이로 적는다
- **severity:** low
- **targets:** architecture
- **location:** architecture.md 9.8 첫 '맞춤 완료' 문단(1395행 '뒤에 더한 Failed는 아래 남은 차이'), 9.8 '남은 차이' 표(1406~1418행), 9.1 '300 ms 겹침' 끝 문장(962행)
- **evidence:** 9.8 표는 ui-design에 다음을 고치라고 한다: canTare에서 !tareInFlight 삭제, Failed 무게 영역, 표시 단위 출처, StatusAnnouncer 세 규칙, S8 Loading 행·recipe_edit_loading, D1 문구·error 색·brew_leave_confirm_light_font200. ui-design은 이번 수정에서 모두 반영했다. 10.2 canTare에는 "tareInFlight는 넣지 않는다"가 있다. 5.1 Failed 세 행에는 "Running·Paused면 멈춘 값 …, 아니면 --.-"가 있다. 9절 끝 문단에는 Stale.unit과 S6·S10 g 고정이 있다. 8.3에는 노드 규칙 1~4가 있다. 5.7에는 Loading 행이, 8.6(1079행)에는 recipe_edit_loading이 있다. 5.2와 8.6(1074행)에는 D1 문구와 brew_leave_confirm_light_font200이 있다. 9.1도 "이 세 규칙이 ui-design에 아직 없어"라고 적는다. 오작동: 구현자와 다음 검증자가 닫힌 차이를 열린 것으로 읽는다. 특히 9.8은 '이 문서 열이 계약'이라고 정하므로, 이 표를 근거로 ui-design 기준 규칙(예: 300 ms 겹침)을 덮어쓸 수 있다.
- **fix:** ui-design 현재판을 기준으로 정리한다. 9.8 남은 차이 표에서 canTare·Failed·표시 단위·StatusAnnouncer·S8 Loading·D1 행을 지우고 '맞춤 완료'로 옮긴다. 남기는 행은 S1 저장 실패(ui-design 미반영)와 protocol 두 행뿐이다. 1395행의 괄호와 9.1의 '아직 없어' 문장도 지운다.

### p6. architecture와 milestones가 protocol.md에 없는 C-0D-2를 근거로 인용하고, protocol 2.4의 분할 송신 규칙은 architecture F-11과 반대다
- **severity:** low
- **targets:** protocol, architecture
- **location:** architecture.md 4.2 C-0D-2 행(139행), 7.2 자동 시작 무장(636행), 4.1 F-11(122행), 9.8 마지막 두 행(1417~1418행), milestones FIX-1 scope·M8 BrewSessionControllerTest ↔ protocol.md 3.7(C-0D-2 없음), 2.4(170행)
- **evidence:** architecture 636행: "자동 시작 무장(protocol.md C-0D-2, 4.2)". FIX-1 scope: "protocol.md C-0D-2: 0x0D 성공 응답 뒤 약 300 ms 동안 영점 전 무게가 옴". protocol.md에서 C-0D-2를 grep하면 0건이다(architecture 139행도 '추가 요청'이라고 적는다). protocol 2.4: "앱은 먼저 MTU 증가(requestMtu(247))를 요청한다. 증가가 거부되면 20바이트 단위로 나눠 연속 쓴다". architecture 4.1 F-11: "MTU−3을 넘는 프레임은 PayloadTooLarge로 거부. … 나눠 쓰지도 않는다". 영향: 무장 규칙의 실측 근거를 명세에서 찾을 수 없다. 송신 길이 규칙도 명세와 계약이 서로 다르다. 다만 MVP 화면은 이름 쓰기를 쓰지 않아 지금 앱 동작은 바뀌지 않는다.
- **fix:** architecture를 기준으로 protocol.md를 고친다. 3.7에 C-0D-2를 넣는다(근거 _workspace/04_device_probeA_mac.log:2724-2729·13753-13756, 벡터 rx-tare-writeack-004와 그 뒤 프레임). 2.4 분할 문장은 "앱 규칙 후보(F-11 미확정). MVP 앱은 PayloadTooLarge로 거부(architecture 4.1)"로 바꾼다. 반영 뒤 architecture 9.8의 두 행을 지운다.

## protocolIssues (2)

### p1. 영점 성공 응답 뒤에도 영점 전 무게가 2~3프레임 더 와서 자동 시작이 잘못 켜진다
- **severity:** high
- **targets:** architecture, protocol
- **location:** architecture.md 7.2 전이표(WaitingForPour Reading 임계값 행, Idle·WaitingForPour Tared 행), 8.2 tare(); protocol.md 3.7 0x0D 영점; milestones.json M5·M8
- **evidence:** 실측 _workspace/04_device_probeA_mac.log:2726에서 WriteAck(0x0D, 성공)과 같은 알림에 weightRaw 2134(213.4 g)가 붙어 온다(벡터 rx-tare-writeack-004). log:2727·2728(+293.081, +293.142)에도 2134가 오고, 0은 log:2729(응답 뒤 약 300 ms)부터다. 7.2는 Tared → WaitingForPour 다음에 오는 Reading이 autoStartThresholdGrams를 2회 연속 넘으면 Running+SendTimer(Start)로 간다. 그래서 컵·드리퍼를 올리고 영점을 누를 때마다 영점 전 무게(>1 g)가 3회 연속 들어와 바로 잘못 자동 시작한다. 게다가 readings(SharedFlow 버퍼)와 send() 반환은 서로 다른 경로라, 응답 앞에 온 프레임도 Tared 뒤에 처리될 수 있다. M5 테스트는 영점 뒤 0 g에서 시작하는 합성 시계열만 쓰므로 이 결함을 못 잡는다.
- **fix:** protocol.md에 새 항목(예: C-0D-2)을 더한다: '0x0D 성공 응답 뒤 최대 약 300 ms(2프레임) 동안 영점 전 무게가 온다', 근거 log:2724-2729, 벡터 rx-tare-writeack-004와 그 뒤 프레임. architecture.md 7.2에는 WaitingForPour 무장 규칙을 더한다: Tared 뒤에는 atNanos > Tared.atNanos이고 |무게| ≤ 임계값인 Reading이 한 번 온 뒤부터 연속 카운트를 센다. 그 전 Reading은 카운트하지 않는다. Done인 M5는 고치지 않는다. 대신 FIX 마일스톤(또는 M8 완료 조건)에 실측 순서 테스트를 더한다: Tared → 213.4 g × 3 → 0 g 시계열에서 Running이 되지 않아야 한다.

### p2. [Q5] 길이가 MTU−3을 넘는 송신의 앱 동작이 protocol.md 2.4(MTU 증가 뒤 나눠 쓰기)와 architecture 4.1(PayloadTooLarge 거부)에서 다름
- **severity:** low
- **targets:** protocol, architecture
- **location:** protocol.md 2.4 송신 / architecture.md 4.1 F-11 행, 6절 명령 응답 대기
- **evidence:** protocol 2.4: "프레임이 현재 MTU−3바이트를 넘으면 … 앱은 먼저 MTU 증가(`requestMtu(247)`)를 요청한다. 증가가 거부되면 20바이트 단위로 나눠 연속 쓴다." architecture 4.1: "F-11 | … | `BleScaleClient.send()`의 길이 검사 | MTU−3을 넘는 프레임은 `PayloadTooLarge`로 거부". 6절도 "길이가 MTU−3을 넘으면 `PayloadTooLarge`"다. MVP 화면은 SetName을 쓰지 않아 지금은 증상이 없다. 하지만 한 문서가 앱 규칙으로 정한 동작을 다른 문서가 다르게 계약한다.
- **fix:** architecture(MVP 결정)를 기준으로 protocol 2.4의 분할 문장을 '앱 규칙 후보(F-11 미확정). MVP 앱은 PayloadTooLarge로 거부(architecture 4.1)'로 고친다. 나눠 쓰기는 이름 쓰기 마일스톤이 생길 때 다시 정한다.
