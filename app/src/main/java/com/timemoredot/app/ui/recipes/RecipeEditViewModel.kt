package com.timemoredot.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.data.RecipeRepository
import com.timemoredot.app.ui.brew.formatMinSec
import com.timemoredot.app.ui.summary.parseDose
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.RecipeStep
import com.timemoredot.brew.ValveEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** architecture.md 9.6. 단계 카드 초안: 글자 그대로 들고 있다가 검증 때 읽는다. */
data class StepDraft(
    val name: String, val targetGramsText: String, val minutesText: String, val secondsText: String,
    val valveOpen: Boolean = false,
)

sealed interface RecipeFieldError {
    data object NameEmpty : RecipeFieldError
    data object NameTooLong : RecipeFieldError
    data object DoseInvalid : RecipeFieldError
    data object WaterInvalid : RecipeFieldError
    data object NoSteps : RecipeFieldError
    data class StepNumberInvalid(val index: Int) : RecipeFieldError
    data class StepGramsDecreasing(val index: Int, val previousGrams: Double) : RecipeFieldError
    data class StepTimeNotIncreasing(val index: Int) : RecipeFieldError
    data class FinishTimeInvalid(val lastStepSec: Int) : RecipeFieldError
}

enum class RecipeEditState { Loading, New, Editing, Saving, SaveFailed, NotFound }

data class RecipeEditUiState(
    val state: RecipeEditState,
    val name: String, val doseText: String, val waterText: String, val steps: List<StepDraft>,
    val finishMinutesText: String, val finishSecondsText: String,
    val valveEnabled: Boolean,
    val valvePreview: List<ValveEvent>,
    val errors: List<RecipeFieldError>,
    val lastStepMismatchesWater: Boolean,
    val ratio: Double?,
    val hasUnsavedChanges: Boolean,
)

sealed interface RecipeEditEvent {
    data object Saved : RecipeEditEvent
    data object Deleted : RecipeEditEvent
    data object SaveFailed : RecipeEditEvent
    data object DeleteFailed : RecipeEditEvent
}

const val NAME_MAX = 30
const val STEP_TIME_MAX_SEC = 3599

// 초안 파싱: VM 검증·저장과 S8 화면이 같이 쓴다. 이 파일들의 toDoubleOrNull·toIntOrNull 호출은 아래 두 함수 안뿐이다(architecture.md 9.6).
internal fun parseStepGrams(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun parseStepTimeSec(minutesText: String, secondsText: String): Int? {
    val m = minutesText.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val s = secondsText.trim().toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return (m.toLong() * 60 + s).takeIf { it <= Int.MAX_VALUE }?.toInt()
}

/** 7.1 Recipe.isWaitStep의 초안판. index > 0일 때만 부른다. */
internal fun isWaitStep(prev: StepDraft, cur: StepDraft): Boolean {
    val a = parseStepGrams(prev.targetGramsText) ?: return false
    val b = parseStepGrams(cur.targetGramsText) ?: return false
    return a == b
}

/** 스위치만 바꾸는 단계 이름 기본값. 사용자 데이터라 리소스가 아니다. */
private fun valveStepName(atSec: Int?, open: Boolean): String {
    val verb = if (open) "스위치 열기" else "스위치 닫기"
    return if (atSec == null) verb else "${formatMinSec(atSec)} $verb"
}

private data class Form(
    val name: String = "", val dose: String = "", val water: String = "", val steps: List<StepDraft> = emptyList(),
    val finishMin: String = "", val finishSec: String = "", val valveEnabled: Boolean = false,
)

private class Parsed(val grams: Double, val sec: Int)

/** 단계 검증(NoSteps·Step*). 오류가 없으면 파싱 결과가 모든 단계에 있다. */
private fun checkSteps(steps: List<StepDraft>): Pair<List<RecipeFieldError>, List<Parsed?>> {
    if (steps.isEmpty()) return listOf<RecipeFieldError>(RecipeFieldError.NoSteps) to emptyList()
    val errors = mutableListOf<RecipeFieldError>()
    val parsed = steps.mapIndexed { i, s ->
        val g = parseStepGrams(s.targetGramsText)?.takeIf { it > 0 }
        val t = parseStepTimeSec(s.minutesText, s.secondsText)?.takeIf { it <= STEP_TIME_MAX_SEC }
        if (g == null || t == null) { errors += RecipeFieldError.StepNumberInvalid(i); null } else Parsed(g, t)
    }
    parsed.forEachIndexed { i, cur ->
        if (cur == null) return@forEachIndexed
        val prev = if (i == 0) null else parsed[i - 1]
        if (i >= 1 && prev == null) return@forEachIndexed   // 앞 단계를 읽지 못하면 비교하지 않는다
        if (prev != null && cur.grams < prev.grams) errors += RecipeFieldError.StepGramsDecreasing(i, prev.grams)
        if (cur.sec <= (prev?.sec ?: 0)) errors += RecipeFieldError.StepTimeNotIncreasing(i)
    }
    return errors to parsed
}

private fun buildRecipe(id: Long, f: Form, name: String, dose: Double, water: Double, parsed: List<Parsed?>, finishAt: Int?) = Recipe(
    id, name, dose, water,
    f.steps.mapIndexed { i, s -> RecipeStep(s.name, parsed[i]!!.grams, parsed[i]!!.sec, if (f.valveEnabled) s.valveOpen else null) },
    finishAt,
)

class RecipeEditViewModel(private val recipes: RecipeRepository, private val recipeId: Long?) : ViewModel() {
    private var loadState = if (recipeId == null) RecipeEditState.New else RecipeEditState.Loading
    private var base = Form()
    private var form = Form()
    private var errors: List<RecipeFieldError> = emptyList()

    private val _ui = MutableStateFlow(build())
    val uiState: StateFlow<RecipeEditUiState> = _ui.asStateFlow()
    private val _events = Channel<RecipeEditEvent>(Channel.BUFFERED)
    val events: Flow<RecipeEditEvent> = _events.receiveAsFlow()

    init {
        if (recipeId != null) viewModelScope.launch {
            val r = try { recipes.get(recipeId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (r == null) loadState = RecipeEditState.NotFound
            else {
                form = Form(
                    r.name, gramsText(r.doseGrams), gramsText(r.waterGrams),
                    r.steps.map { StepDraft(it.name, gramsText(it.targetGrams), (it.targetAtSec / 60).toString(), secText(it.targetAtSec), it.valveOpen ?: false) },
                    r.finishAtSec?.let { (it / 60).toString() } ?: "", r.finishAtSec?.let { secText(it) } ?: "",
                    r.valveSchedule.isNotEmpty(),
                )
                base = form
                loadState = RecipeEditState.Editing
            }
            publish()
        }
    }

    private val editable get() = loadState == RecipeEditState.New || loadState == RecipeEditState.Editing || loadState == RecipeEditState.SaveFailed

    private fun edit(change: (Form) -> Form) {
        if (!editable) return
        form = change(form)
        if (errors.isNotEmpty()) errors = validate(form).first   // 고치는 중이면 오류가 바로 갱신된다
        publish()
    }

    private fun publish() { _ui.value = build() }

    private fun build(): RecipeEditUiState {
        val f = form
        val (stepErrors, parsed) = checkSteps(f.steps)
        val water = parseStepGrams(f.water)
        val dose = parseDose(f.dose)
        val preview = if (f.valveEnabled && stepErrors.isEmpty()) {
            buildRecipe(0, f, "", 1.0, 1.0, parsed, null).valveSchedule
        } else emptyList()
        val lastGrams = parsed.lastOrNull()?.grams
        return RecipeEditUiState(
            loadState, f.name, f.dose, f.water, f.steps, f.finishMin, f.finishSec, f.valveEnabled, preview, errors,
            lastStepMismatchesWater = lastGrams != null && water != null && abs(lastGrams - water) > 1e-9,
            ratio = if (dose != null && water != null && water > 0) water / dose else null,
            hasUnsavedChanges = editable && f != base,
        )
    }

    /** 오류 목록과, 오류가 없으면 저장할 Recipe. */
    private fun validate(f: Form): Pair<List<RecipeFieldError>, Recipe?> {
        val errs = mutableListOf<RecipeFieldError>()
        val name = f.name.trim()
        if (name.isEmpty()) errs += RecipeFieldError.NameEmpty else if (name.length > NAME_MAX) errs += RecipeFieldError.NameTooLong
        val dose = parseDose(f.dose)
        if (dose == null) errs += RecipeFieldError.DoseInvalid
        val water = parseStepGrams(f.water)?.takeIf { it > 0 }
        if (water == null) errs += RecipeFieldError.WaterInvalid
        val (stepErrors, parsed) = checkSteps(f.steps)
        errs += stepErrors
        var finishAt: Int? = null
        if (stepErrors.isEmpty() && (f.finishMin.isNotBlank() || f.finishSec.isNotBlank())) {
            val last = parsed.last()!!.sec
            finishAt = if (f.finishMin.isBlank() || f.finishSec.isBlank()) null else parseStepTimeSec(f.finishMin, f.finishSec)
            if (finishAt == null || finishAt < last || finishAt > STEP_TIME_MAX_SEC) errs += RecipeFieldError.FinishTimeInvalid(last)
        }
        val recipe = if (errs.isEmpty()) buildRecipe(recipeId ?: 0, f, name, dose!!, water!!, parsed, finishAt) else null
        return errs to recipe
    }

    fun onNameChange(v: String) = edit { it.copy(name = v) }
    fun onDoseChange(v: String) = edit { it.copy(dose = v) }
    fun onWaterChange(v: String) = edit { it.copy(water = v) }
    fun onStepChange(index: Int, step: StepDraft) = edit { f -> if (index in f.steps.indices) f.copy(steps = f.steps.toMutableList().also { it[index] = step }) else f }
    fun onAddStep() = edit { f -> f.copy(steps = f.steps + StepDraft("", "", "", "", f.steps.lastOrNull()?.valveOpen ?: false)) }
    fun onRemoveStep(index: Int) = edit { f -> if (index in f.steps.indices) f.copy(steps = f.steps.filterIndexed { i, _ -> i != index }) else f }
    fun onFinishTimeChange(minutes: String, seconds: String) = edit { it.copy(finishMin = minutes, finishSec = seconds) }
    fun onValveEnabledChange(enabled: Boolean) = edit { it.copy(valveEnabled = enabled) }
    fun onMoveStepUp(index: Int) = swap(index - 1, index)
    fun onMoveStepDown(index: Int) = swap(index, index + 1)

    private fun swap(a: Int, b: Int) = edit { f ->
        if (a < 0 || b > f.steps.lastIndex) f
        else f.copy(steps = f.steps.toMutableList().also {
            // 시각 칸은 자리에 남는다(시작 시각이 자리 기준이라 이동 직후에도 일정이 나온다). 이름·누적 g·체크가 옮겨 간다.
            val x = it[a]; val y = it[b]
            it[a] = y.copy(minutesText = x.minutesText, secondsText = x.secondsText)
            it[b] = x.copy(minutesText = y.minutesText, secondsText = y.secondsText)
        })
    }

    fun onInsertValveStep(afterIndex: Int) = edit { f ->
        val p = f.steps.getOrNull(afterIndex)
        if (!f.valveEnabled || p == null) return@edit f
        val a = parseStepTimeSec(p.minutesText, p.secondsText)
        val b = f.steps.getOrNull(afterIndex + 1)?.let { parseStepTimeSec(it.minutesText, it.secondsText) }
        val at = when {
            a == null -> null
            b == null -> a + 30
            else -> ((a + b) / 2).takeIf { it > a }
        }
        val open = !p.valveOpen
        val step = StepDraft(valveStepName(a, open), p.targetGramsText, at?.let { (it / 60).toString() } ?: "", at?.let { secText(it) } ?: "", open)
        f.copy(steps = f.steps.toMutableList().also { it.add(afterIndex + 1, step) })
    }

    fun onFieldFocusLost() {
        if (!editable) return
        errors = validate(form).first
        publish()
    }

    fun onSave() {
        if (!editable) return
        val (errs, recipe) = validate(form)
        errors = errs
        if (recipe == null) { publish(); return }
        loadState = RecipeEditState.Saving
        publish()
        viewModelScope.launch {
            try {
                recipes.upsert(recipe)
                base = form
                loadState = RecipeEditState.Editing
                publish()
                _events.send(RecipeEditEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadState = RecipeEditState.SaveFailed
                publish()
                _events.send(RecipeEditEvent.SaveFailed)
            }
        }
    }

    fun onDeleteConfirmed() {
        val id = recipeId ?: return
        if (!editable) return
        viewModelScope.launch {
            try {
                recipes.delete(id)
                _events.send(RecipeEditEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _events.send(RecipeEditEvent.DeleteFailed)
            }
        }
    }
}

/** 초 칸은 두 자리로 채운다('1 : 5'가 1:50으로 읽히는 것을 막는다). */
private fun secText(sec: Int) = (sec % 60).toString().padStart(2, '0')
