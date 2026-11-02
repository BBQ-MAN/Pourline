package com.timemoredot.app.ui.history

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.brew.formatWeight
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.recipes.ListLoad
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** S9 화면 루트: uiState만 수집한다. 파일 선택기는 화면이 띄우고, 고른 Uri는 csvTarget으로 바꿔 VM에 넘긴다(8.4). */
@Composable
fun HistoryScreen(
    vm: HistoryViewModel, csvTarget: (Uri) -> CsvTarget, onOpen: (Long) -> Unit, onStartBrew: () -> Unit,
    sessionDeleted: Boolean = false, onDeletedConsumed: () -> Unit = {},
) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val touch = rememberTouchExploration()
    val pick = rememberCsvPicker(csvTarget, vm::onExportAll)
    val startExport = { pick(historyCsvName(LocalDate.now())) }
    val latestStart by rememberUpdatedState(startExport)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            scope.launch {
                when (e) {
                    is ExportEvent.Exported -> snackbar.showSnackbar("CSV를 저장했습니다(${e.count}건)")
                    ExportEvent.ExportFailed ->   // "다시" = 선택기를 새로 띄운다. 직전 Uri·target을 쓰지 않는다
                        if (snackbar.showWithAction(touch(), "CSV를 내보내지 못했습니다.", "다시") == SnackbarResult.ActionPerformed) latestStart()
                }
            }
        }
    }
    // session_deleted(S10 삭제 결과)는 한 번 읽고 지운 뒤 스낵바를 띄운다. 효과가 다시 돌아도 스낵바가 끊기지 않게 scope에서 띄운다
    LaunchedEffect(sessionDeleted) {
        if (sessionDeleted) { onDeletedConsumed(); scope.launch { snackbar.showSnackbar("기록을 삭제했습니다") } }
    }
    HistoryContent(ui, snackbar, onExport = startExport, onOpen = onOpen, onStartBrew = onStartBrew, onRetry = vm::onRetry)
}

/** 상태 없는 S9(스크린샷·UI 테스트가 직접 그린다). 하단 탭·레일이 보이는 화면이라 그 쪽 인셋은 탭이 받는다(3절). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    ui: HistoryUiState, snackbar: SnackbarHostState, onExport: () -> Unit, onOpen: (Long) -> Unit, onStartBrew: () -> Unit, onRetry: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val insets = if (wide) WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
    else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    AnnounceExporting(ui.export)
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { Text("기록", Modifier.semantics { heading(); paneTitle = "기록" }) },
                    actions = {
                        IconButton(onExport, Modifier.focusRing(androidx.compose.foundation.shape.CircleShape), enabled = ui.sessions is ListLoad.Loaded && ui.export == ExportState.Idle) {
                            Icon(AppIcons.Download, contentDescription = "CSV 내보내기")
                        }
                    },
                    scrollBehavior = scroll,
                )
                ExportProgress(ui.export)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.small) } },
        contentWindowInsets = insets,
    ) { inner ->
        when (val r = ui.sessions) {
            ListLoad.Loading -> DelayedSpinner("기록 불러오는 중", Modifier.padding(inner))
            ListLoad.Empty -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    AppIcons.History, "추출 기록이 없습니다", Modifier.widthIn(max = 640.dp),
                    description = "추출을 마치고 저장하면 여기에 쌓입니다.", actionLabel = "추출 시작", onAction = onStartBrew,
                )
            }
            ListLoad.Error -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(AppIcons.Error, "기록을 불러오지 못했습니다", Modifier.widthIn(max = 640.dp), actionLabel = "다시 시도", onAction = onRetry)
            }
            is ListLoad.Loaded -> {
                val groups = remember(r.items, zone) { r.items.groupBy { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }.toList() }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = inner.calculateStartPadding(LocalLayoutDirection.current), top = inner.calculateTopPadding(),
                        end = inner.calculateEndPadding(LocalLayoutDirection.current), bottom = inner.calculateBottomPadding() + Space.l,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for ((date, rows) in groups) {
                        item(key = "h-$date") { DateHeader(dateText(date), Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
                        items(rows, key = { it.id }) { SessionRow(it, zone, onOpen, Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
                    }
                }
            }
        }
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREA)
private val TIME_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

fun dateText(d: LocalDate): String = DATE_FORMAT.format(d)

/** 날짜 머리글(배경 없음, ui-design 3.7). */
@Composable
private fun DateHeader(text: String, modifier: Modifier) {
    Text(
        text, modifier.padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xs).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun g1(g: Double) = "${formatWeight(g, WeightUnit.Gram)}${NBSP}g"

/** 셋째 줄 "15.0 g → 250.3 g · 3:04 · 1:16.7"(없는 값은 —). */
fun sessionMetricsLine(s: SessionSummary): String =
    "${s.doseGrams?.let(::g1) ?: "—"} → ${s.finalWeightGrams?.let(::g1) ?: "—"} · ${formatMinSec((s.durationMs / 1000).toInt())} · ${s.ratio?.let { String.format(Locale.ROOT, "1:%.1f", it) } ?: "—"}"

/** 행 낭독(8.4): "10월 28일 8시 12분, V60 기본, 원두 15.0 그램, 물 250.3 그램, 3분 4초, 비율 1 대 16.7". */
fun sessionSpoken(s: SessionSummary, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(s.startedAtEpochMs).atZone(zone)
    return "${at.monthValue}월 ${at.dayOfMonth}일 ${at.hour}시 ${at.minute}분, ${s.recipeName ?: "자유 추출"}, " +
        "${s.doseGrams?.let { "원두 ${formatWeight(it, WeightUnit.Gram)} 그램" } ?: "원두량 없음"}, " +
        "${s.finalWeightGrams?.let { "물 ${formatWeight(it, WeightUnit.Gram)} 그램" } ?: "최종 무게 없음"}, ${spokenMinSec(s.durationMs / 1000)}, " +
        (s.ratio?.let { String.format(Locale.ROOT, "비율 1 대 %.1f", it) } ?: "비율 없음")
}

/** 조약돌 행(6.4, S7과 같은 수식어 순서: padding → focusRing → clip → clickable). */
@Composable
private fun SessionRow(s: SessionSummary, zone: ZoneId, onOpen: (Long) -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    val tnum = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        overlineContent = { Text(TIME_FORMAT.format(Instant.ofEpochMilli(s.startedAtEpochMs).atZone(zone)), Modifier.clearAndSetSemantics { }, style = tnum) },
        headlineContent = { Text(s.recipeName ?: "자유 추출", Modifier.clearAndSetSemantics { contentDescription = sessionSpoken(s, zone) }) },
        supportingContent = { Text(sessionMetricsLine(s), Modifier.clearAndSetSemantics { }, style = tnum) },
        modifier = modifier
            .padding(horizontal = Space.l, vertical = Space.xs)
            .heightIn(min = 72.dp)
            .focusRing(shape)
            .clip(shape)
            .clickable(onClickLabel = "기록 열기", role = Role.Button) { onOpen(s.id) },
    )
}
