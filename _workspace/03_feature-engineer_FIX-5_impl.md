# FIX-5 StatusAnnouncer 포커스 순서
- StatusAnnouncer.kt: content를 `Box(fillMaxSize, isTraversalGroup)`로 감쌈. 알림 두 노드는 마지막 자식 `Box(align(BottomEnd), isTraversalGroup)` 안. contentDescription은 문구가 비어 있지 않을 때만. liveRegion·1dp·300 ms 창 규칙 그대로.
- StatusAnnouncerTraversalTest 신규: AndroidComposeView `forceAccessibilityForTesting`(리플렉션) + Shadow AccessibilityManager 켬. 순회 값은 Compose가 node extras에 넣는 EXTRA_DATA_TEST_TRAVERSALBEFORE/AFTER_VAL(시맨틱 id)로 사슬을 따름. (Robolectric에서 AccessibilityNodeInfo.traversalBefore 필드는 쓸 수 없음.)
- traversalIndex 사용 안 함. verify.sh 종료 0.
