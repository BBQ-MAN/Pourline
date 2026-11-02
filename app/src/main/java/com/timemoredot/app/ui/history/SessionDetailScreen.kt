package com.timemoredot.app.ui.history

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.brew.formatWeight
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.app.ui.brew.spokenNumber
import com.timemoredot.app.ui.brew.stepNameForDisplay
import com.timemoredot.app.ui.components.BannerKind
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.StatusBanner
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.recipes.ValveScheduleLine
import com.timemoredot.app.ui.recipes.gramsText
import com.timemoredot.app.ui.summary.NOTE_MAX
import com.timemoredot.app.ui.summary.Pebble
import com.timemoredot.app.ui.summary.SummaryChart
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.isWaitStep
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** S10 차트 모드: 화면 로컬 상태(rememberSaveable)이고 ViewModel 상태가 아니다(architecture.md 9.1). */
enum class ChartMode { Weight, Flow }

/** S10 화면 루트. 삭제가 끝나면 onDeleted, 뒤로는 onBack. 파일 선택기는 화면이 띄운다(8.4). */
@Composable
fun SessionDetailScreen(vm: SessionDetailViewModel, csvTarget: (Uri) -> CsvTarget, onBack: () -> Unit, onDeleted: () -> Unit) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val touch = rememberTouchExploration()
    val pick = rememberCsvPicker(csvTarget, vm::onExportSamples)
    val latestUi by rememberUpdatedState(ui)
    val startExport = { pick(latestUi.summary?.let { sessionCsvName(it.startedAtEpochMs) } ?: sessionCsvName(System.currentTimeMillis())) }
    val latestStart by rememberUpdatedState(startExport)
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                SessionDetailEvent.Deleted -> onDeleted()
                SessionDetailEvent.DeleteFailed -> scope.launch {
                    if (snackbar.showWithAction(touch(), "기록을 삭제하지 못했습니다.", "다시") == SnackbarResult.ActionPerformed) vm.onDeleteConfirmed()
                }
                is SessionDetailEvent.Export -> scope.launch {
                    when (e.e) {
                        is ExportEvent.Exported -> snackbar.showSnackbar("CSV를 저장했습니다")
                        ExportEvent.ExportFailed ->   // "다시" = 선택기를 새로 띄운다. 직전 Uri·target을 쓰지 않는다
                            if (snackbar.showWithAction(touch(), "CSV를 내보내지 못했습니다.", "다시") == SnackbarResult.ActionPerformed) latestStart()
                    }
                }
            }
        }
    }
    SessionDetailContent(ui, snackbar, vm::onNoteChange, startExport, vm::onDeleteConfirmed, onBack)
}

private val TITLE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

/** 상태 없는 S10. D3(삭제 확인)과 차트 모드는 이 화면의 로컬 상태(rememberSaveable)다. 탭이 없는 화면이라 인셋은 safeDrawing 전체(imePadding 없음). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailContent(
    ui: SessionDetailUiState, snackbar: SnackbarHostState, onNoteChange: (String) -> Unit, onExport: () -> Unit, onDeleteConfirmed: () -> Unit, onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var showDelete by rememberSaveable { mutableStateOf(false) }   // D3
    var menuOpen by remember { mutableStateOf(false) }
    val s = ui.summary
    val shown = s != null && (ui.state == DetailState.Loaded || ui.state == DetailState.NoSamples)
    val title = s?.let { TITLE_FORMAT.format(Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone)) } ?: "기록"
    AnnounceExporting(ui.export)
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, Modifier.semantics { heading(); paneTitle = title }) },
                    navigationIcon = { IconButton(onBack, Modifier.focusRing(CircleShape)) { Icon(AppIcons.ArrowBack, contentDescription = "뒤로") } },
                    actions = {
                        if (shown) {
                            IconButton(onExport, Modifier.focusRing(CircleShape), enabled = ui.export == ExportState.Idle) { Icon(AppIcons.Download, contentDescription = "CSV 내보내기") }
                            Box {
                                IconButton({ menuOpen = true }, Modifier.focusRing(CircleShape)) { Icon(AppIcons.MoreVert, contentDescription = "더보기") }
                                DropdownMenu(menuOpen, { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; showDelete = true })
                                }
                            }
                        }
                    },
                )
                ExportProgress(ui.export)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.small) } },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        val body = Modifier.padding(inner).consumeWindowInsets(inner)
        when {
            ui.state == DetailState.Loading -> DelayedSpinner("기록 불러오는 중", body)
            ui.state == DetailState.NotFound || s == null -> Box(body.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(AppIcons.History, "기록을 찾을 수 없습니다", Modifier.widthIn(max = 640.dp), actionLabel = "목록으로", onAction = onBack)
            }
            else -> DetailBody(ui, s, onNoteChange, body)
        }
    }

    if (showDelete) DeleteSessionDialog(onConfirm = { showDelete = false; onDeleteConfirmed() }, onDismiss = { showDelete = false })
}

@Composable
private fun DetailBody(ui: SessionDetailUiState, s: SessionSummary, onNoteChange: (String) -> Unit, modifier: Modifier) {
    var mode by rememberSaveable { mutableStateOf(ChartMode.Weight) }
    var note by rememberSaveable(s.id) { mutableStateOf(s.note) }
    var atLimit by remember { mutableStateOf(note.length >= NOTE_MAX) }   // 한도 아래로 내려가면 다시 무장
    val announcer = LocalAnnouncer.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            if (s.status == SessionStatus.Interrupted) StatusBanner(BannerKind.Info, "저장되지 않은 채 끝난 추출을 복구한 기록입니다.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                Pebble("최종 무게", s.finalWeightGrams?.let { "${formatWeight(it, WeightUnit.Gram)}${NBSP}g" } ?: "—",
                    s.finalWeightGrams?.let { "최종 무게 ${spokenNumber(formatWeight(it, WeightUnit.Gram))} 그램" } ?: "최종 무게 없음")
                Pebble("시간", formatMinSec((s.durationMs / 1000).toInt()), "시간 ${spokenMinSec(s.durationMs / 1000)}")
                val ratio = s.ratio?.let { String.format(Locale.ROOT, "%.1f", it) }
                Pebble("비율", ratio?.let { "1:$it" } ?: "—", ratio?.let { "비율 1 대 $it" } ?: "비율 없음")
            }
            val samples = ui.samples
            if (samples != null) {
                ChartModePicker(mode) { mode = it }
                SummaryChart(samples, s.recipe, s.finalWeightGrams, flowMode = mode == ChartMode.Flow, chartHeight = 240.dp)
            } else Text("그래프 데이터가 없습니다", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            val recipe = s.recipe
            if (recipe != null) StepTable(recipe, ui.stepReachTimesMs, hasActual = samples != null)
            recipe?.valveSchedule?.takeIf { it.isNotEmpty() }?.let { ValveScheduleLine(it, Modifier.fillMaxWidth()) }   // 옛 기록(valveOpen 없음)은 빈 목록이라 줄이 없다
            OutlinedTextField(
                note, { note = it.take(NOTE_MAX); onNoteChange(note); if (note.length < NOTE_MAX) atLimit = false else if (!atLimit) { atLimit = true; announcer.announce("최대 200자입니다", false) } },
                Modifier.fillMaxWidth(), label = { Text("메모") }, shape = MaterialTheme.shapes.medium,
                isError = note.length >= NOTE_MAX,
                supportingText = {
                    Text(
                        if (note.length >= NOTE_MAX) "최대 200자 · ${note.length}/$NOTE_MAX" else "${note.length}/$NOTE_MAX",
                        Modifier.semantics { contentDescription = "${NOTE_MAX}자 중 ${note.length}자" },
                    )
                },
            )
        }
    }
}

/**
 * "무게"/"유량" 두 칸 단일 선택(ui-design 4.4): 모양 높이 40 dp, 칸(레이아웃·터치) 높이 48 dp.
 * M3 SegmentedButton은 레이아웃 높이가 40 dp이고 터치 경계만 48 dp라서(테스트의 높이 판정이 40으로 나온다) 같은 모양을 직접 그린다.
 * 선택은 `selectable(role = RadioButton)`이라 M3 기본 "선택됨"으로 낭독되고, 모양은 색 외에 체크 아이콘으로도 갈린다.
 */
@Composable
private fun ChartModePicker(mode: ChartMode, onChange: (ChartMode) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
        ChartMode.entries.forEachIndexed { i, m ->
            val selected = mode == m
            val shape = SegmentedButtonDefaults.itemShape(i, ChartMode.entries.size)
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).focusRing(shape).selectable(selected, onClick = { onChange(m) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(shape).background(if (selected) cs.secondaryContainer else Color.Transparent).border(1.dp, cs.outline, shape),
                    horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) Icon(AppIcons.Check, contentDescription = null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
                    Text(if (m == ChartMode.Weight) "무게" else "유량", style = MaterialTheme.typography.labelLarge, color = if (selected) cs.onSecondaryContainer else cs.onSurface)
                }
            }
        }
    }
}

/** 단계별 도달 시각 표(3.7). 기다리기 단계 행은 실제 도달 시각을 적지 않는다(reachTimesMs 값이 있어도 쓰지 않는다). */
@Composable
private fun StepTable(recipe: Recipe, reachMs: List<Int?>, hasActual: Boolean) {
    Column {
        Text("단계별 도달 시각", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        recipe.steps.forEachIndexed { i, step ->
            val wait = recipe.isWaitStep(i)
            val reach = reachMs.getOrNull(i)
            val target = "목표 ${gramsText(step.targetGrams)}${NBSP}g · ${formatMinSec(step.targetAtSec)}"
            val line2 = when {
                wait -> "기다리기 · ${formatMinSec(step.targetAtSec)}까지 (${gramsText(step.targetGrams)}${NBSP}g 그대로)"
                !hasActual -> target
                reach != null -> "$target / 실제 ${formatMinSec(reach / 1000)}"
                else -> "$target / 실제 도달 없음"
            }
            val spoken = "${i + 1}단계 ${step.name}, " + when {
                wait -> "기다리기 단계, ${spokenMinSec(step.targetAtSec.toLong())}까지, ${gramsText(step.targetGrams)} 그램 그대로"
                else -> "목표 ${gramsText(step.targetGrams)} 그램 ${spokenMinSec(step.targetAtSec.toLong())}" +
                    if (!hasActual) "" else if (reach != null) ", 실제 도달 ${spokenMinSec(reach / 1000L)}" else ", 실제 도달 없음"
            }
            Column(Modifier.fillMaxWidth().padding(vertical = Space.s).clearAndSetSemantics { contentDescription = spoken }) {
                Text("${i + 1}. ${stepNameForDisplay(step.name)}", style = MaterialTheme.typography.bodyMedium)
                Text(line2, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** D3 기록 삭제 확인. */
@Composable
fun DeleteSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("이 기록을 삭제할까요? 되돌릴 수 없습니다.") },
        dismissButton = { TextButton(onDismiss, Modifier.focusRing(CircleShape)) { Text("취소") } },
        confirmButton = { TextButton(onConfirm, Modifier.focusRing(CircleShape), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("삭제") } },
    )
}
