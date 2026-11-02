package com.timemoredot.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** TalkBack 상태 알림(architecture.md 9.1, ui-design 8.3). 문구는 화면이 만들고 이 인터페이스로 넘긴다. */
fun interface Announcer { fun announce(text: String, assertive: Boolean) }

val LocalAnnouncer = staticCompositionLocalOf<Announcer> { error("StatusAnnouncer 밖에서 쓰였다") }

private const val MERGE_WINDOW_MS = 300L
private const val CLEAR_AFTER_MS = 2000L

/** 같은 모드에서 300 ms 안에 겹친 문구는 이어 붙여 한 번에 낸다. 낼 때마다 노드를 "" → 새 문구로 바꿔 같은 문구도 다시 읽힌다(ui-design 8.3 규칙 2·3). */
private class MergingChannel(private val scope: CoroutineScope, private val publish: (String) -> Unit) {
    private var pending: String? = null
    private var windowOpen = false
    private var windowJob: Job? = null
    private var clearJob: Job? = null

    fun announce(text: String) {
        pending = pending?.let { "$it. $text" } ?: text
        clearJob?.cancel()
        if (windowOpen) return
        windowOpen = true
        windowJob = scope.launch {
            delay(MERGE_WINDOW_MS)
            publish("")
            withFrameMillis { }   // 프레임 한 번 대기(루프 아님). 다음 프레임에 새 문구: 같은 값 쓰기는 무시되므로 먼저 비운다
            // 프레임 대기 중 들어온 문구도 합치도록 대기 뒤에 읽고, 읽은 뒤에는 중단점 없이 게시·타이머까지 간다
            val out = pending.orEmpty()
            pending = null
            windowOpen = false
            publish(out)
            clearJob = scope.launch { delay(CLEAR_AFTER_MS); publish("") }   // 빈 노드는 TalkBack 탐색이 건너뛴다
        }
    }

    /** 아직 내지 않은 문구를 버린다(Assertive가 Polite를 끊는다). */
    fun drop() {
        pending = null
        windowOpen = false
        windowJob?.cancel()
        publish("")   // 지난 Polite 문구가 노드에 남아 Assertive와 어긋나지 않게 비운다
    }
}

/** 앱 루트(MainActivity setContent 바로 안)에서 NavHost를 감싼다. 앱 안에서 이 파일만 알림 노드를 둔다. */
@Composable
fun StatusAnnouncer(content: @Composable () -> Unit) {
    var polite by remember { mutableStateOf("") }
    var assertive by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val announcer = remember {
        val p = MergingChannel(scope) { polite = it }
        val a = MergingChannel(scope) { assertive = it }
        Announcer { text, isAssertive -> if (isAssertive) { p.drop(); a.announce(text) } else p.announce(text) }
    }
    Box(Modifier.fillMaxSize()) {
        // 화면 내용이 먼저 끝까지 읽히고, 알림 묶음은 맨 뒤·오른쪽 아래라 위치 정렬로도 마지막이다(ui-design 8.3 1번·8.5)
        Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
            CompositionLocalProvider(LocalAnnouncer provides announcer, content = content)
        }
        Box(Modifier.align(Alignment.BottomEnd).semantics { isTraversalGroup = true }) {
            // 문구가 빈 동안 contentDescription을 두지 않아 TalkBack 포커스 대상이 아니다
            Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; if (polite.isNotEmpty()) contentDescription = polite })
            Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Assertive; if (assertive.isNotEmpty()) contentDescription = assertive })
        }
    }
}
