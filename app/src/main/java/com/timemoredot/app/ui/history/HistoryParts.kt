package com.timemoredot.app.ui.history

import android.net.Uri
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.ui.LocalAnnouncer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val EXPORTING_ANNOUNCEMENT = "내보내는 중"

/** 기본 파일 이름(architecture.md 8.4). 시각은 파일 이름 표기에만 쓰는 벽시계다. */
fun historyCsvName(today: LocalDate) = "timemore-brews-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.csv"

fun sessionCsvName(startedAtEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()) =
    "timemore-brew-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).format(Instant.ofEpochMilli(startedAtEpochMs).atZone(zone)) + ".csv"

/**
 * CreateDocument 런처(8.4, 2.5). 고른 Uri를 그 자리에서 CsvTarget으로 바꿔 넘기고 아무것도 보관하지 않는다(다시 하면 선택기를 새로 띄운다).
 * 선택 취소(Uri null)는 아무것도 하지 않는다. 반환한 함수가 선택기를 띄운다.
 */
@Composable
fun rememberCsvPicker(csvTarget: (Uri) -> CsvTarget, onTarget: (CsvTarget) -> Unit): (String) -> Unit {
    val toTarget by rememberUpdatedState(csvTarget)
    val deliver by rememberUpdatedState(onTarget)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) deliver(toTarget(uri)) }
    return remember(launcher) { { name: String -> launcher.launch(name) } }
}

/** Idle → Exporting이 될 때 한 번 알린다(ui-design 8.3). */
@Composable
fun AnnounceExporting(export: ExportState) {
    val announcer = LocalAnnouncer.current
    val prev = remember { arrayOf(export) }
    LaunchedEffect(export) {
        if (prev[0] == ExportState.Idle && export == ExportState.Exporting) announcer.announce(EXPORTING_ANNOUNCEMENT, false)
        prev[0] = export
    }
}

/** 상단 바 아래 4 dp 자리: 내보내는 동안만 진행 막대(본문이 밀리지 않게 자리는 늘 둔다). */
@Composable
fun ExportProgress(export: ExportState) {
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (export == ExportState.Exporting) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = EXPORTING_ANNOUNCEMENT })
    }
}

/** 4.9: 동작이 있는 스낵바는 Long, TalkBack(터치 탐색) 중에는 Indefinite. */
suspend fun SnackbarHostState.showWithAction(touchExploration: Boolean, message: String, action: String): SnackbarResult =
    showSnackbar(message, action, withDismissAction = false, duration = if (touchExploration) SnackbarDuration.Indefinite else SnackbarDuration.Long)

@Composable
fun rememberTouchExploration(): () -> Boolean {
    val context = LocalContext.current
    return { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
}

/** 5.6 Loading: 300 ms 동안 아무것도 그리지 않고, 그 뒤에도 불러오는 중이면 진행 원. */
@Composable
fun DelayedSpinner(description: String, modifier: Modifier = Modifier) {
    var spin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); spin = true }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spin) CircularProgressIndicator(Modifier.size(48.dp).semantics { contentDescription = description })
    }
}
