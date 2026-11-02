package com.timemoredot.app.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.brew.NBSP
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.brew.spokenMinSec
import com.timemoredot.app.ui.components.AppOutlinedButton
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.ValveEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S8가 VM에 거는 동작 묶음(스크린샷·UI 테스트는 기본 빈 동작). */
class RecipeEditActions(
    val onNameChange: (String) -> Unit = {}, val onDoseChange: (String) -> Unit = {}, val onWaterChange: (String) -> Unit = {},
    val onStepChange: (Int, StepDraft) -> Unit = { _, _ -> }, val onAddStep: () -> Unit = {}, val onRemoveStep: (Int) -> Unit = {},
    val onFinishTimeChange: (String, String) -> Unit = { _, _ -> }, val onValveEnabledChange: (Boolean) -> Unit = {},
    val onInsertValveStep: (Int) -> Unit = {}, val onMoveStepUp: (Int) -> Unit = {}, val onMoveStepDown: (Int) -> Unit = {},
    val onFieldFocusLost: () -> Unit = {}, val onSave: () -> Unit = {}, val onDeleteConfirmed: () -> Unit = {},
)

fun RecipeEditViewModel.actions() = RecipeEditActions(
    ::onNameChange, ::onDoseChange, ::onWaterChange, ::onStepChange, ::onAddStep, ::onRemoveStep, ::onFinishTimeChange,
    ::onValveEnabledChange, ::onInsertValveStep, ::onMoveStepUp, ::onMoveStepDown, ::onFieldFocusLost, ::onSave, ::onDeleteConfirmed,
)

/** S8 화면 루트. 저장·삭제가 끝나면 onFinished, 닫기(확인 뒤)는 onClose. */
@Composable
fun RecipeEditScreen(vm: RecipeEditViewModel, existing: Boolean, onClose: () -> Unit, onFinished: () -> Unit) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                RecipeEditEvent.Saved, RecipeEditEvent.Deleted -> onFinished()
                RecipeEditEvent.SaveFailed -> scope.launch {
                    if (snackbar.showSnackbar("저장하지 못했습니다.", actionLabel = "다시") == SnackbarResult.ActionPerformed) vm.onSave()
                }
                RecipeEditEvent.DeleteFailed -> scope.launch {
                    if (snackbar.showSnackbar("삭제하지 못했습니다.", actionLabel = "다시") == SnackbarResult.ActionPerformed) vm.onDeleteConfirmed()
                }
            }
        }
    }
    RecipeEditContent(ui, remember(vm) { vm.actions() }, canDelete = existing, onClose = onClose, snackbar = snackbar)
}

private const val STEP_NUMBER_MSG = "누적 목표는 0보다 큰 숫자, 시각은 59:59까지의 분과 초(0~59)로 입력하세요"

/** 상태 없는 S8. D4(변경 버리기)·D3(삭제 확인)는 이 화면의 로컬 상태(rememberSaveable)다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditContent(
    ui: RecipeEditUiState, actions: RecipeEditActions, canDelete: Boolean, onClose: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }   // D4
    var showDelete by rememberSaveable { mutableStateOf(false) }    // D3
    var menuOpen by remember { mutableStateOf(false) }
    val navState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(navState, isBackEnabled = ui.hasUnsavedChanges || ui.state == RecipeEditState.Saving, onBackCancelled = {},
        onBackCompleted = { if (ui.state != RecipeEditState.Saving) showDiscard = true })   // Saving 중에는 화면을 떠나지 못한다(저장 취소 방지)
    val close = { if (ui.state == RecipeEditState.Saving) Unit else if (ui.hasUnsavedChanges) showDiscard = true else onClose() }

    val form = ui.state != RecipeEditState.Loading && ui.state != RecipeEditState.NotFound
    val saving = ui.state == RecipeEditState.Saving
    val nameFocus = remember { FocusRequester() }
    val doseFocus = remember { FocusRequester() }
    val waterFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val finishFocus = remember { FocusRequester() }
    val stepFocus = remember(ui.steps.size) { List(ui.steps.size) { FocusRequester() to FocusRequester() } }   // 누적 g 칸, 분 칸
    val latestSteps = rememberUpdatedState(ui.steps)
    var saveTicks by remember { mutableIntStateOf(0) }
    val latest = rememberUpdatedState(ui.errors)
    val latestStepFocus = rememberUpdatedState(stepFocus)
    // 저장을 눌렀을 때만 첫 오류 필드로 포커스를 옮긴다(포커스 해제 검증이 포커스를 빼앗지 않게)
    LaunchedEffect(saveTicks) {
        if (saveTicks == 0) return@LaunchedEffect
        withFrameNanos { }
        val target = when (val e = latest.value.firstOrNull()) {
            null -> null
            RecipeFieldError.NameEmpty, RecipeFieldError.NameTooLong -> nameFocus
            RecipeFieldError.DoseInvalid -> doseFocus
            RecipeFieldError.WaterInvalid -> waterFocus
            RecipeFieldError.NoSteps -> addFocus
            is RecipeFieldError.StepNumberInvalid -> latestStepFocus.value.getOrNull(e.index)?.let { (g, m) ->
                val s = latestSteps.value.getOrNull(e.index)
                // 누적 g는 정상이고 시각만 틀린 카드는 분 칸으로 보내 error 문구가 읽히게 한다
                if (s != null && parseStepGrams(s.targetGramsText)?.let { it > 0 } == true) m else g
            }
            is RecipeFieldError.StepGramsDecreasing -> latestStepFocus.value.getOrNull(e.index)?.first
            is RecipeFieldError.StepTimeNotIncreasing -> latestStepFocus.value.getOrNull(e.index)?.second
            is RecipeFieldError.FinishTimeInvalid -> finishFocus
        }
        runCatching { target?.requestFocus() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (canDelete) "레시피 편집" else "새 레시피", Modifier.semantics { heading(); paneTitle = if (canDelete) "레시피 편집" else "새 레시피" }) },
                    navigationIcon = { IconButton(close, enabled = ui.state != RecipeEditState.Saving) { Icon(AppIcons.Close, contentDescription = "닫기") } },
                    actions = {
                        if (form) {
                            TextButton({ saveTicks++; actions.onSave() }, Modifier.focusRing(CircleShape), enabled = !saving) { Text("저장") }
                            if (canDelete) Box {
                                IconButton({ menuOpen = true }) { Icon(AppIcons.MoreVert, contentDescription = "더보기") }
                                DropdownMenu(menuOpen, { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("삭제") }, onClick = { menuOpen = false; showDelete = true })
                                }
                            }
                        }
                    },
                )
                Box(Modifier.fillMaxWidth().height(4.dp)) {   // 저장 중에도 본문이 밀리지 않게 자리를 남긴다
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "저장 중" })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, shape = MaterialTheme.shapes.small) } },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        val body = Modifier.padding(inner).consumeWindowInsets(inner)
        when (ui.state) {
            RecipeEditState.Loading -> LoadingBody(body)
            RecipeEditState.NotFound -> Box(body.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(AppIcons.MenuBook, "레시피를 찾을 수 없습니다", Modifier.widthIn(max = 640.dp), actionLabel = "목록으로", onAction = onClose)
            }
            else -> EditBody(ui, actions, !saving, body, nameFocus, doseFocus, waterFocus, addFocus, finishFocus, stepFocus)
        }
    }

    if (showDiscard) DiscardChangesDialog(onDiscard = { showDiscard = false; onClose() }, onKeepEditing = { showDiscard = false })
    if (showDelete) DeleteRecipeDialog(ui.name, onConfirm = { showDelete = false; actions.onDeleteConfirmed() }, onDismiss = { showDelete = false })
}

/** 5.7 Loading: 300 ms 동안 아무것도 그리지 않고, 그 뒤에도 읽는 중이면 진행 원. 필드는 그리지 않는다. */
@Composable
private fun LoadingBody(modifier: Modifier) {
    var spin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); spin = true }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spin) CircularProgressIndicator(Modifier.size(48.dp).semantics { contentDescription = "레시피 불러오는 중" })
    }
}

/** 필드가 포커스를 잃을 때 한 번 알린다(검증 실행). */
private fun Modifier.onBlur(onLost: () -> Unit): Modifier = composed {
    var had by remember { mutableStateOf(false) }
    val cb by rememberUpdatedState(onLost)
    onFocusChanged { if (had && !it.isFocused) cb(); had = it.isFocused }
}

@Composable
private fun EditBody(
    ui: RecipeEditUiState, a: RecipeEditActions, enabled: Boolean, modifier: Modifier,
    nameFocus: FocusRequester, doseFocus: FocusRequester, waterFocus: FocusRequester, addFocus: FocusRequester,
    finishFocus: FocusRequester, stepFocus: List<Pair<FocusRequester, FocusRequester>>,
) {
    val shape = MaterialTheme.shapes.medium
    val announcer = LocalAnnouncer.current
    var pendingBring by remember { mutableStateOf<Int?>(null) }
    var focusAfterRemove by remember { mutableStateOf<Int?>(null) }
    val latestFocus = rememberUpdatedState(stepFocus)
    val moveFocus = remember(ui.steps.size) { List(ui.steps.size) { FocusRequester() to FocusRequester() } }   // 위, 아래 버튼
    var focusAfterMove by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }   // 새 자리, 위로였는지
    val latestMove = rememberUpdatedState(moveFocus)
    LaunchedEffect(focusAfterMove) {
        val (i, up) = focusAfterMove ?: return@LaunchedEffect
        focusAfterMove = null
        withFrameNanos { }
        val m = latestMove.value
        // 끝 단계에서는 그 방향 버튼이 비활성이라 이름 칸으로 보낸다
        runCatching { (if (i !in m.indices || (up && i == 0) || (!up && i == m.size - 1)) nameFocus else if (up) m[i].first else m[i].second).requestFocus() }
    }
    LaunchedEffect(focusAfterRemove) {
        val i = focusAfterRemove ?: return@LaunchedEffect
        focusAfterRemove = null
        withFrameNanos { }
        val f = latestFocus.value
        runCatching { (if (f.isEmpty()) addFocus else f[minOf(i, f.size - 1)].first).requestFocus() }
    }

    val fs = LocalDensity.current.fontScale
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            val nameErr = ui.errors.firstOrNull { it == RecipeFieldError.NameEmpty || it == RecipeFieldError.NameTooLong }
            OutlinedTextField(
                ui.name, a.onNameChange, Modifier.fillMaxWidth().focusRequester(nameFocus).onBlur(a.onFieldFocusLost), enabled = enabled, singleLine = true,
                label = { Text("이름") }, isError = nameErr != null, shape = shape,
                supportingText = nameErr?.let { { Text(if (it == RecipeFieldError.NameEmpty) "이름을 입력하세요" else "30자 이하로 입력하세요") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            val doseErr = RecipeFieldError.DoseInvalid in ui.errors
            val waterErr = RecipeFieldError.WaterInvalid in ui.errors
            val doseField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(
                    ui.doseText, a.onDoseChange, m.focusRequester(doseFocus).onBlur(a.onFieldFocusLost), enabled = enabled, singleLine = true,
                    label = { Text("원두") }, suffix = { Text("g") }, isError = doseErr, shape = shape,
                    supportingText = if (doseErr) ({ Text("0.1~100.0${NBSP}g 사이로 입력하세요") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }
            val waterField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(
                    ui.waterText, a.onWaterChange, m.focusRequester(waterFocus).onBlur(a.onFieldFocusLost), enabled = enabled, singleLine = true,
                    label = { Text("물") }, suffix = { Text("g") }, isError = waterErr, shape = shape,
                    supportingText = if (waterErr) ({ Text("0보다 큰 값을 입력하세요") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }
            if (fs >= 1.3f) {   // 글꼴 확대: 단계 카드와 같은 규칙으로 세로 쌓기
                doseField(Modifier.fillMaxWidth())
                waterField(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                doseField(Modifier.weight(1f))
                waterField(Modifier.weight(1f))
            }
            val ratio = ui.ratio
            Text(
                if (ratio != null) "비율 ${ratioText(ratio)}" else "비율 —",
                Modifier.semantics { contentDescription = if (ratio != null) "비율 1 대 ${ratioText(ratio).removePrefix("1:")}" else "비율 없음" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                ValveSwitchRow(ui.valveEnabled, enabled, a.onValveEnabledChange)
                if (ui.valveEnabled) Box(Modifier.padding(horizontal = Space.l)) {
                    if (ui.valvePreview.isNotEmpty()) ValveScheduleLine(ui.valvePreview)
                    else Text("단계 값을 고치면 일정이 보입니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                ui.steps.forEachIndexed { i, step ->
                    StepCard(
                        i, ui.steps.size, step, ui.steps.getOrNull(i - 1), ui.valveEnabled, stepFieldErrors(i, ui.steps, ui.errors), enabled, fs, stepFocus[i].first, stepFocus[i].second, moveFocus[i].first, moveFocus[i].second,
                        a.onFieldFocusLost, pendingBring == i, { pendingBring = null },
                        onChange = { a.onStepChange(i, it) }, onUp = { a.onMoveStepUp(i); focusAfterMove = (i - 1) to true; announcer.announce("${i + 1}단계를 ${i}단계 자리로 옮겼습니다", false) },
                        onDown = { a.onMoveStepDown(i); focusAfterMove = (i + 1) to false; announcer.announce("${i + 1}단계를 ${i + 2}단계 자리로 옮겼습니다", false) },
                        onRemove = { a.onRemoveStep(i); focusAfterRemove = i; announcer.announce("${i + 1}단계를 삭제했습니다", false) },
                        onInsert = {
                            a.onInsertValveStep(i)
                            announcer.announce("${i + 2}단계로 스위치 바꾸는 단계를 넣었습니다", false)
                            pendingBring = i + 1
                        },
                    )
                }
            }
            if (RecipeFieldError.NoSteps in ui.errors) Text("단계를 하나 이상 추가하세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            AppOutlinedButton({ a.onAddStep(); announcer.announce("${ui.steps.size + 1}단계를 추가했습니다", false) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRequester(addFocus).focusRing(CircleShape), enabled = enabled) {
                Icon(AppIcons.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Text("단계 추가")
            }
            FinishTime(ui, a, enabled, fs, finishFocus)
            if (ui.lastStepMismatchesWater) Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Warning, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("마지막 단계의 누적 목표가 물 양과 다릅니다", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ValveSwitchRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text("스위치(밸브) 레시피") },
        supportingContent = {
            Text("하리오 스위치처럼 밸브를 여닫는 레시피면 켜세요. 단계마다 열림·닫힘을 고르면 그 단계가 시작할 때(앞 단계 목표 시각) 추출 화면이 알려 줍니다.", style = MaterialTheme.typography.bodyMedium)
        },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.heightIn(min = 48.dp).focusRing(MaterialTheme.shapes.medium).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/** 스위치 일정 줄(ui-design 3.6·3.7). S8 미리보기와 S10이 같이 쓴다. 입력은 파생 일정뿐이다. */
@Composable
fun ValveScheduleLine(events: List<ValveEvent>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("스위치 일정", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        Text(valveScheduleText(events), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishTime(ui: RecipeEditUiState, a: RecipeEditActions, enabled: Boolean, fs: Float, focus: FocusRequester) {
    val shape = MaterialTheme.shapes.medium
    val err = ui.errors.filterIsInstance<RecipeFieldError.FinishTimeInvalid>().firstOrNull()
    val msg = err?.let {
        val t = parseStepTimeSec(ui.finishMinutesText, ui.finishSecondsText)
        if (t != null && t < it.lastStepSec) "마지막 단계 목표 시각(${formatMinSec(it.lastStepSec)})보다 이르면 안 됩니다"
        else "59:59까지의 분·초를 입력하세요. 비워 두면 마지막 단계 목표 시각에 끝납니다"
    }
    Column {
        Text("추출 끝 시각", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Top) {
            val w = 64.dp * fs
            OutlinedTextField(
                ui.finishMinutesText, { a.onFinishTimeChange(it, ui.finishSecondsText) },
                Modifier.width(w).focusRequester(focus).onBlur(a.onFieldFocusLost).semantics { stateDescription = "추출 끝 시각 분"; if (msg != null) error(msg) },
                enabled = enabled, singleLine = true, label = { Text("분") }, isError = err != null, shape = shape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            Text(":", Modifier.width(12.dp).padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedTextField(
                ui.finishSecondsText, { a.onFinishTimeChange(ui.finishMinutesText, it) },
                Modifier.width(w).onBlur(a.onFieldFocusLost).semantics { stateDescription = "추출 끝 시각 초"; if (msg != null) error(msg) },
                enabled = enabled, singleLine = true, label = { Text("초") }, isError = err != null, shape = shape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Text(
            msg ?: "비워 두면 마지막 단계 목표 시각에 끝납니다", Modifier.padding(top = Space.xs),
            style = MaterialTheme.typography.bodySmall, color = if (msg != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@androidx.compose.runtime.Immutable
private data class StepErrors(val grams: Boolean, val time: Boolean, val messages: List<String>, val gramsMsg: String?, val timeMsg: String?)

/** 한 카드의 오류 칸과 문구(3.6 검증 표). 판정은 VM과 같은 파싱 함수로만 한다. */
private fun stepFieldErrors(i: Int, steps: List<StepDraft>, errors: List<RecipeFieldError>): StepErrors {
    var g = false
    var t = false
    val msgs = mutableListOf<String>()
    val gm = mutableListOf<String>()
    val tm = mutableListOf<String>()
    for (e in errors) when (e) {
        is RecipeFieldError.StepNumberInvalid -> if (e.index == i) {
            val s = steps[i]
            g = g || (parseStepGrams(s.targetGramsText)?.let { it <= 0 } ?: true)
            t = t || (parseStepTimeSec(s.minutesText, s.secondsText)?.let { it > STEP_TIME_MAX_SEC } ?: true)
            msgs += STEP_NUMBER_MSG; gm += STEP_NUMBER_MSG; tm += STEP_NUMBER_MSG
        }
        is RecipeFieldError.StepGramsDecreasing -> if (e.index == i) {
            g = true
            val m = "앞 단계(${gramsText(e.previousGrams)}${NBSP}g)보다 적으면 안 됩니다. 같게 두면 기다리기 단계가 됩니다"
            msgs += m; gm += m
        }
        is RecipeFieldError.StepTimeNotIncreasing -> if (e.index == i) {
            t = true
            val prev = steps.getOrNull(i - 1)?.let { parseStepTimeSec(it.minutesText, it.secondsText) }
            val m = if (i == 0) "0:00보다 늦어야 합니다" else if (prev != null) "앞 단계 목표 시각(${formatMinSec(prev)})보다 늦어야 합니다" else "앞 단계 목표 시각보다 늦어야 합니다"
            msgs += m; tm += m
        }
        else -> {}
    }
    return StepErrors(g, t, msgs, gm.joinToString(". ").ifEmpty { null }, tm.joinToString(". ").ifEmpty { null })
}

/** 스위치 설명 줄의 화면 문구와 낭독(3.6·8.4). 앞 카드 시각은 저장 검증과 같은 파싱으로 읽는다. */
internal fun valveLineTexts(n: Int, prev: StepDraft?, step: StepDraft): Pair<String, String> {
    if (prev == null) return "스위치 · 처음부터" to "${n}단계 스위치, 처음부터"
    val sec = parseStepTimeSec(prev.minutesText, prev.secondsText)
    val change = prev.valveOpen != step.valveOpen
    val shown = (if (sec != null) "스위치 · ${formatMinSec(sec)}부터" else "스위치 · 앞 단계 목표 시각부터") + if (change) " · 여기서 바꿈" else ""
    val spoken = "${n}단계 스위치, " + (if (sec != null) "${spokenMinSec(sec.toLong())}부터" else "앞 단계 목표 시각부터") + if (change) ", 여기서 바꿈" else ""
    return shown to spoken
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(
    index: Int, count: Int, step: StepDraft, prev: StepDraft?, valveEnabled: Boolean, err: StepErrors, enabled: Boolean, fs: Float, gramsFocus: FocusRequester, minutesFocus: FocusRequester, upFocus: FocusRequester, downFocus: FocusRequester,
    onBlur: () -> Unit, bringIntoView: Boolean, onBrought: () -> Unit,
    onChange: (StepDraft) -> Unit, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit, onInsert: () -> Unit,
) {
    val n = index + 1
    val wait = prev != null && isWaitStep(prev, step)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(bringIntoView) {
        if (bringIntoView) { withFrameNanos { }; runCatching { requester.bringIntoView() }; onBrought() }
    }
    val small = MaterialTheme.shapes.small
    Card(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.s, bottom = if (valveEnabled) Space.s else Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (wait) "${n}단계 · 기다리기" else "${n}단계",
                    Modifier.weight(1f).semantics { heading(); contentDescription = if (wait) "${n}단계, 기다리기 단계" else "${n}단계" },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.s))
                IconButton(onUp, Modifier.focusRequester(upFocus).semantics { if (index == 0) stateDescription = "맨 위 단계" }, enabled = enabled && index > 0) { Icon(AppIcons.ArrowUpward, contentDescription = "${n}단계 위로 옮기기") }
                Spacer(Modifier.width(Space.s))
                IconButton(onDown, Modifier.focusRequester(downFocus).semantics { if (index == count - 1) stateDescription = "맨 아래 단계" }, enabled = enabled && index < count - 1) { Icon(AppIcons.ArrowDownward, contentDescription = "${n}단계 아래로 옮기기") }
                Spacer(Modifier.width(Space.s))
                IconButton(onRemove, enabled = enabled) { Icon(AppIcons.Delete, contentDescription = "${n}단계 삭제") }
            }
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                step.name, { onChange(step.copy(name = it)) }, Modifier.fillMaxWidth().onBlur(onBlur), enabled = enabled, singleLine = false,
                label = { Text("${n}단계 이름") }, shape = small, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Space.s))
            val gramsField: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(
                    step.targetGramsText, { onChange(step.copy(targetGramsText = it)) },
                    m.focusRequester(gramsFocus).onBlur(onBlur).semantics { stateDescription = "${n}단계 누적 목표 그램"; if (err.grams && err.gramsMsg != null) error(err.gramsMsg) },
                    enabled = enabled, singleLine = true, label = { Text("누적 목표") }, suffix = { Text("g") }, isError = err.grams, shape = small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }
            val timeFields: @Composable (Modifier) -> Unit = { m ->
                val w = 64.dp * fs
                Row(m, verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        step.minutesText, { onChange(step.copy(minutesText = it)) },
                        Modifier.width(w).focusRequester(minutesFocus).onBlur(onBlur).semantics { stateDescription = "${n}단계 목표 시각 분"; if (err.time && err.timeMsg != null) error(err.timeMsg) },
                        enabled = enabled, singleLine = true, label = { Text("분") }, isError = err.time, shape = small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    Text(":", Modifier.width(12.dp).padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    OutlinedTextField(
                        step.secondsText, { onChange(step.copy(secondsText = it)) },
                        Modifier.width(w).onBlur(onBlur).semantics { stateDescription = "${n}단계 목표 시각 초"; if (err.time && err.timeMsg != null) error(err.timeMsg) },
                        enabled = enabled, singleLine = true, label = { Text("초") }, isError = err.time, shape = small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (fs >= 1.3f) {   // 글꼴 확대: 누적 목표 한 줄, 분·초 다음 줄
                gramsField(Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.s))
                timeFields(Modifier)
            } else Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.Top) {
                gramsField(Modifier.weight(1f))
                timeFields(Modifier)
            }
            for (m in err.messages) Text(m, Modifier.padding(top = Space.xs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (valveEnabled) {
                Spacer(Modifier.height(Space.m))
                val (shown, spoken) = valveLineTexts(n, prev, step)
                Text(shown, Modifier.semantics { contentDescription = spoken }, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.xs))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ValveChoice(0, false, step, n, enabled, onChange)
                    ValveChoice(1, true, step, n, enabled, onChange)
                }
                Spacer(Modifier.height(Space.xs))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onInsert, Modifier.heightIn(min = 48.dp).focusRing(CircleShape).semantics { contentDescription = "${n}단계 뒤에 스위치 바꾸는 단계 넣기" }, enabled = enabled) {
                        Icon(AppIcons.Add, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s))
                        Text("스위치 바꾸는 단계 넣기")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.ValveChoice(
    position: Int, open: Boolean, step: StepDraft, n: Int, enabled: Boolean, onChange: (StepDraft) -> Unit,
) {
    val selected = step.valveOpen == open
    val label = if (open) "열림" else "닫힘"
    SegmentedButton(
        selected = selected, onClick = { onChange(step.copy(valveOpen = open)) }, shape = SegmentedButtonDefaults.itemShape(position, 2), enabled = enabled,
        icon = {},   // 아이콘 슬롯은 글꼴 200%에서 위로 붙어 label 안 Row로 직접 가운데 맞춘다
        label = {   // 선택은 색이 아니라 check 아이콘으로도 알린다(7.5)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) { Icon(AppIcons.Check, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)) }
                Text(label)
            }
        },
        modifier = Modifier.semantics { contentDescription = "${n}단계 스위치 $label" },
    )
}

/** D4 변경 내용 버리기 확인. */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("변경 내용을 버릴까요?", style = MaterialTheme.typography.headlineSmall) },
        dismissButton = { TextButton(onKeepEditing, Modifier.focusRing(CircleShape)) { Text("계속 편집") } },
        confirmButton = { TextButton(onDiscard, Modifier.focusRing(CircleShape), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("버리기") } },
    )
}

/** D3 레시피 삭제 확인. */
@Composable
fun DeleteRecipeDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("레시피 '$name'을 삭제할까요? 기록은 남습니다.") },
        dismissButton = { TextButton(onDismiss, Modifier.focusRing(CircleShape)) { Text("취소") } },
        confirmButton = { TextButton(onConfirm, Modifier.focusRing(CircleShape), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("삭제") } },
    )
}
