# 구현 보고 wf_3ccc71c5-d08 (2026-10-29)

| 마일스톤 | 상태 | 수정 라운드 | 완료 조건 | 남은 low |
|---|---|---|---|---|
| FIX-1 | done | 0 | 6/6 | 4 |
| FIX-2 | done | 0 | 7/7 | 4 |
| M7R | done | 2 | 13/13 | 17 |
| FIX-3 | done | 2 | 6/6 | 12 |

메인 재확인: verify.sh 종료 코드 0 (_workspace/build/verify_20261029_232817.log)

## low

- FIX-1 [qa] Two old threshold tests now pass vacuously because no Reading ever arms the engine — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/brew/src/test/kotlin/com/timemoredot/brew/BrewEngineTest.kt:69-74, 88-96
- FIX-1 [kotlin] A pour that starts before the first post-tare reading never auto-starts — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt:157
- FIX-1 [kotlin] Unneeded !! after a validity check and java.lang Math.abs — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt:159
- FIX-1 [kotlin] The unarmed branch swallows the arming reading and mixes arming with counting — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/brew/src/main/kotlin/com/timemoredot/brew/BrewEngine.kt:157
- FIX-2 [qa] [Q10] API 30 이하에서 위치 서비스가 꺼져 있으면 앱 시작 때 저장 기기에 연결하지 않는다(M4 동작이 바뀜. 명세대로이지만 connect()와 다르게 동작한다) — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/ble/src/main/kotlin/com/timemoredot/ble/BleScaleClient.kt:113
- FIX-2 [kotlin] 버린 transport 이벤트 무시 테스트가 onEvent의 link !== l 가드를 검증하지 못한다 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/ble/src/test/kotlin/com/timemoredot/ble/GattTransportTest.kt:49
- FIX-2 [kotlin] MANUFACTURER_FILTER_DATA·MASK가 공개 가변 ByteArray 최상위 val이다 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/protocol/src/main/kotlin/com/timemoredot/protocol/Advertising.kt:20
- FIX-2 [kotlin] onUnblocked가 기존 session을 취소하지 않고 startLinkLoop를 시작한다 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/core/ble/src/main/kotlin/com/timemoredot/ble/BleScaleClient.kt:168
- M7R [qa] 구현 요약이 실제 빌드 구성과 다르다(Roborazzi 미사용이라고 적혀 있다) — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/_workspace/03_feature-engineer_M7R_impl.md:6
- M7R [qa] 워크플로가 넘긴 후보 행 완료 조건 문장이 milestones.json의 현재 M7R 조건과 다르다 — /Users/bbqman/HermesAgents/ETC/TimemoreDotScaleApp/docs/spec/milestones.json
- M7R [kotlin] focusRing uses deprecated composed {} — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:56
- M7R [kotlin] State written in SideEffect during ordering — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:318-322
- M7R [kotlin] Non-null assertion on emptyStateTitle — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:210
- M7R [kotlin] Hardcoded Korean strings; the strings.xml NBSP grep passes vacuously — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt (whole file), ConnectAnnouncements.kt
- M7R [a11y] Candidate row modifier order differs from the stated completion condition — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:278-283
- M7R [a11y] EmptyState title and S3 sheet title are not marked as headings — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:161
- M7R [a11y] Reconnecting-to-Connected announcement flag is written during composition and can be lost — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:92-105
- M7R [a11y] No 200% font screenshot for the longest S2 state — screenshots/connect_permission_denied_light_font200.png
- M7R [a11y] Indeterminate progress indicator ignores reduced-motion setting; progress node has no range or role — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:156
- M7R [a11y] S2 rotation re-announces the scan state; S3 has no live change notification in the sheet itself — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:140-144
- M7R [visual] Centred empty-state descriptions wrap badly and split a quoted term across lines — app/src/main/java/com/timemoredot/app/ui/components/Components.kt:164
- M7R [visual] At 200% font the battery value in S3 breaks inside '충전 필요' and its icon is centred on both lines — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:121-125
- M7R [visual] The empty-state icon for LocationOff and the permission states is bluetooth_disabled, the same as BluetoothOff — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:211-225
- M7R [visual] Address tail in an S2 candidate name has no tnum — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:262-265
- M7R [visual] S2 Reconnecting does not show which saved scale is reconnecting — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:233
- FIX-3 [qa] [Q9] StatusAnnouncer Assertive가 창을 바로 닫지 않고 자기 300 ms 창 뒤에 나간다(명세와 다르고 명세에도 차이 기록이 없다) — app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:78
- FIX-3 [kotlin] displayNames recomputed for every row on every recomposition — app/src/main/java/com/timemoredot/app/ui/connect/ConnectionUi.kt:71
- FIX-3 [kotlin] spokenListName detects duplicates by comparing display strings — app/src/main/java/com/timemoredot/app/ui/connect/ConnectionUi.kt:75
- FIX-3 [kotlin] Assertive drop() clears an already-published Polite node — app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:65
- FIX-3 [a11y] Unnamed candidates with the same last 5 address characters get the address tail twice — app/src/main/java/com/timemoredot/app/ui/connect/ConnectionUi.kt:75-78
- FIX-3 [a11y] Assertive announcements are also delayed by the 300 ms merge window — app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:47-48
- FIX-3 [a11y] The live-region node keeps a non-empty description for 2 s and can take TalkBack focus — app/src/main/java/com/timemoredot/app/ui/StatusAnnouncer.kt:56,82-89
- FIX-3 [a11y] Battery 'checking' row has no end state and no announcement when the value arrives — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:78
- FIX-3 [a11y] Row semantics in the S2 candidate list mostly meet WCAG 2.2 AA; point to verify — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:266-288
- FIX-3 [visual] S2 '스캔 중·결과 있음' 안내문 'N대 발견'이 명세에 없는 문구다(M7부터 있던 차이, FIX-3 스크린샷에서도 보임) — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:227-231
- FIX-3 [visual] 후보 행 제목의 주소 꼬리에 명세가 정한 tnum이 없다 — app/src/main/java/com/timemoredot/app/ui/connect/ConnectScreen.kt:269-273
- FIX-3 [visual] S3 글꼴 200%에서 배터리 값 '12% · 충전 필요'가 낱말 중간에서 두 줄로 갈리고 아이콘이 두 줄 가운데에 뜬다(FIX-3 이전부터 있던 문제) — app/src/main/java/com/timemoredot/app/ui/device/DeviceSheet.kt:121-126
