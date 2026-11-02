package com.timemoredot.app.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.brew.formatWeight
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.app.ui.brew.spokenNumber
import com.timemoredot.app.ui.components.BannerKind
import com.timemoredot.app.ui.components.StatusBanner
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.theme.AppType
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.delay
import java.util.Locale

const val SAVE_FAILED_ANNOUNCEMENT = "기록을 저장하지 못했습니다. 입력한 내용은 그대로 있습니다"
const val DOSE_ERROR_TEXT = "0.1~100.0 g 사이로 입력하세요"
private const val SAVE_FAILED_BANNER = "기록을 저장하지 못했습니다. 입력한 내용은 그대로 있습니다."

/** S6 화면 루트: uiState만 수집한다. 저장 성공·버리기 결과는 호출한 쪽(내비게이션)이 처리한다. */
@Composable
fun SummaryScreen(vm: SummaryViewModel, onSaved: (Long) -> Unit, onDiscarded: () -> Unit, onExit: () -> Unit = onDiscarded) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is SummaryEvent.Saved -> onSaved(e.sessionId)
                SummaryEvent.Discarded -> onDiscarded()
            }
        }
    }
    SummaryContent(ui, vm::onDoseChange, vm::onNoteChange, vm::onSave, vm::onDiscardConfirmed, vm::onRetryLoad, onExit, canExit = vm.recovered)
}

/** 상태 없는 S6(스크린샷·UI 테스트가 직접 그린다). 뒤로 가기 확인(D6)·버리기 확인(D3)은 이 화면의 로컬 상태다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryContent(
    ui: SummaryUiState, onDoseChange: (String) -> Unit, onNoteChange: (String) -> Unit, onSave: () -> Unit, onDiscardConfirmed: () -> Unit,
    onRetryLoad: () -> Unit = {}, onExit: () -> Unit = {}, canExit: Boolean = true,
) {
    var showLeave by rememberSaveable { mutableStateOf(false) }       // D6
    var showDiscard by rememberSaveable { mutableStateOf(false) }     // D3
    val announcer = LocalAnnouncer.current
    // 저장 실패 알림은 화면이 상태 전이에서 만든다(VM에 알림 이벤트 없음). SaveFailed가 될 때 한 번(Saving 프레임이 합쳐져도 놓치지 않는다)
    val prevState = remember { arrayOf(ui.state) }
    LaunchedEffect(ui.state) {
        if (prevState[0] != SummaryState.SaveFailed && ui.state == SummaryState.SaveFailed) announcer.announce(SAVE_FAILED_ANNOUNCEMENT, false)
        prevState[0] = ui.state
    }
    // 늘 true: 확인이 필요 없을 때도 시스템 미리보기를 지우지 않으려는 규칙(2.6). 제스처가 끝나면 D6, 취소면 아무것도 하지 않는다
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(navState, isBackEnabled = true, onBackCancelled = {}, onBackCompleted = { showLeave = true })

    val saving = ui.state == SummaryState.Saving
    val doseFocus = remember { FocusRequester() }
    // 저장을 누른 횟수: 오류가 그대로여도 다시 누르면 포커스·낭독을 다시 한다(0이면 복원 직후라 하지 않는다)
    var saveAttempts by remember { mutableStateOf(0) }
    LaunchedEffect(ui.doseError, saveAttempts) {
        if (ui.doseError && saveAttempts > 0) { runCatching { doseFocus.requestFocus() }; announcer.announce(DOSE_ERROR_TEXT, false) }
    }
    val onSaveClick = { saveAttempts++; onSave() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = {
                Text("추출 요약", Modifier.semantics { heading(); paneTitle = "추출 요약" }, style = MaterialTheme.typography.titleLarge)
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    // 스크롤 위치와 상관없이 저장 버튼 바로 위에 보인다
                    if (ui.state == SummaryState.SaveFailed) StatusBanner(BannerKind.Error, SAVE_FAILED_BANNER)
                    if (ui.summary != null || !ui.loadFailed) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            { showDiscard = true }, Modifier.heightIn(min = 56.dp).focusRing(CircleShape), enabled = !saving && ui.summary != null,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("버리기") }
                        Button(onSaveClick, Modifier.weight(1f).heightIn(min = 56.dp).focusRing(CircleShape), enabled = !saving && ui.summary != null) {
                            if (saving) CircularProgressIndicator(
                                Modifier.size(24.dp).semantics { contentDescription = "저장 중" }, color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp,
                            ) else Text(if (ui.state == SummaryState.SaveFailed) "다시 저장" else "저장")
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        val s = ui.summary
        if (s == null) {
            if (ui.loadFailed) LoadFailedBody(Modifier.padding(inner).consumeWindowInsets(inner), onRetryLoad, onExit.takeIf { canExit })
            else LoadingBody(Modifier.padding(inner).consumeWindowInsets(inner))
        } else Column(
            Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                val dose = parseDose(ui.doseText)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                    Pebble("최종 무게", s.finalWeightGrams?.let { "${formatWeight(it, WeightUnit.Gram)}${NBSP}g" } ?: "—",
                        s.finalWeightGrams?.let { "최종 무게 ${spokenNumber(formatWeight(it, WeightUnit.Gram))} 그램" } ?: "최종 무게 없음")
                    Pebble("시간", formatMinSec((s.durationMs / 1000).toInt()), "시간 ${spokenMinSec(s.durationMs / 1000)}")
                    val ratio = if (dose != null && s.finalWeightGrams != null) String.format(Locale.ROOT, "%.1f", s.finalWeightGrams / dose) else null
                    Pebble("비율", ratio?.let { "1:$it" } ?: "—", ratio?.let { "비율 1 대 $it" } ?: "비율 없음")
                }
                SummaryVessel(s, Modifier.align(Alignment.CenterHorizontally))
                val samples = ui.samples
                if (samples != null && samples.size > 0) SummaryChart(samples, s.recipe, s.finalWeightGrams)
                else Text("그래프 데이터가 없습니다", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (ui.gaps.isNotEmpty()) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        "이 기록에는 값이 없는 구간이 있습니다: " + ui.gaps.joinToString(", ") { "${formatMinSec(it.first / 1000)}~${formatMinSec(it.last / 1000)}" },
                        Modifier.padding(Space.l), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    ui.doseText, onDoseChange, Modifier.fillMaxWidth().focusRequester(doseFocus), enabled = !saving, singleLine = true,
                    label = { Text("원두량") }, suffix = { Text("g") }, isError = ui.doseError,
                    supportingText = if (ui.doseError) ({ Text(DOSE_ERROR_TEXT) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium,
                )
                OutlinedTextField(
                    ui.note, onNoteChange, Modifier.fillMaxWidth(), enabled = !saving, label = { Text("메모") }, shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("${ui.note.length}/$NOTE_MAX") },
                )
            }
        }
    }

    if (showDiscard) DiscardConfirmDialog(onConfirm = { showDiscard = false; onDiscardConfirmed() }, onDismiss = { showDiscard = false })
    if (showLeave) SaveConfirmDialog(
        canSave = ui.summary != null, onSave = { showLeave = false; onSaveClick() }, onDiscard = { showLeave = false; showDiscard = true }, onCancel = { showLeave = false },
    )
}

@Composable
private fun LoadFailedBody(modifier: Modifier, onRetry: () -> Unit, onExit: (() -> Unit)?) {
    Column(modifier.fillMaxSize().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        StatusBanner(BannerKind.Error, "요약을 불러오지 못했습니다. 기록은 지워지지 않았습니다.", actionLabel = "다시 시도", onAction = onRetry, secondaryLabel = onExit?.let { "나가기" }, onSecondary = onExit)
    }
}

/** 5.6과 같은 규칙: 300 ms 뒤에도 불러오는 중이면 진행 원. */
@Composable
private fun LoadingBody(modifier: Modifier) {
    var spin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); spin = true }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spin) CircularProgressIndicator(Modifier.size(48.dp).semantics { contentDescription = "요약 불러오는 중" })
    }
}

@Composable
internal fun Pebble(label: String, value: String, spoken: String) {
    Surface(Modifier.defaultMinSize(minWidth = 104.dp, minHeight = 72.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = Space.s, vertical = Space.m).clearAndSetSemantics { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = AppType.statValue, textAlign = TextAlign.Center)
        }
    }
}

private val errorText @Composable get() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)

/** D3 삭제 확인(5.5). */
@Composable
fun DiscardConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("이 추출을 버릴까요? 되돌릴 수 없습니다.") },
        dismissButton = { TextButton(onDismiss, Modifier.focusRing(CircleShape)) { Text("취소") } },
        confirmButton = { TextButton(onConfirm, Modifier.focusRing(CircleShape), colors = errorText) { Text("버리기") } },
    )
}

/** D6 요약 떠나기 확인(5.5). 글꼴 배율 1.3 이상이면 버튼 셋을 끝 정렬 세로로 쌓는다(D1과 같은 규칙). */
@Composable
fun SaveConfirmDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit, canSave: Boolean = true) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val cancel: @Composable () -> Unit = { TextButton(onCancel, Modifier.focusRing(CircleShape)) { Text("취소") } }
    val discard: @Composable () -> Unit = { TextButton(onDiscard, Modifier.focusRing(CircleShape), colors = errorText) { Text("버리기") } }
    val save: @Composable () -> Unit = { TextButton(onSave, Modifier.focusRing(CircleShape)) { Text("저장") } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("이 추출을 저장할까요?", style = MaterialTheme.typography.headlineSmall) },
        // 요약을 못 읽었으면 저장할 것이 없다: 버리기·취소만
        dismissButton = { if (!canSave) cancel() else if (!stacked) { cancel(); discard() } },
        confirmButton = {
            if (!canSave) discard()
            else if (stacked) Column(horizontalAlignment = Alignment.End) { save(); discard(); cancel() } else save()
        },
    )
}
