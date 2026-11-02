package com.timemoredot.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.SessionStatus
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.gaps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** architecture.md 9.5. */
enum class SummaryState { Ready, Saving, SaveFailed }

data class SummaryUiState(
    val summary: SessionSummary? = null,       // null = 불러오는 중
    val samples: SampleBuffer? = null,         // 리본 요약판(읽기 전용)
    val gaps: List<IntRange> = emptyList(),    // 값 없는 구간(ms)
    val doseText: String = "",
    val doseError: Boolean = false,
    val note: String = "",
    val state: SummaryState = SummaryState.Ready,
    val loadFailed: Boolean = false,           // 요약을 읽지 못함(다시 시도·나가기)
)

sealed interface SummaryEvent {
    data class Saved(val sessionId: Long) : SummaryEvent
    data object Discarded : SummaryEvent
}

const val NOTE_MAX = 200
const val DOSE_MIN = 0.1
const val DOSE_MAX = 100.0

fun parseDose(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in DOSE_MIN..DOSE_MAX }

class SummaryViewModel(
    private val brews: BrewRepository,
    private val controller: BrewSessionController,
    private val sessionId: Long,
    val recovered: Boolean,
) : ViewModel() {
    private val _ui = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _ui.asStateFlow()
    private val _events = Channel<SummaryEvent>(Channel.BUFFERED)
    val events: Flow<SummaryEvent> = _events.receiveAsFlow()

    init { load() }

    fun onRetryLoad() { _ui.value = _ui.value.copy(loadFailed = false); load() }

    private fun load() {
        viewModelScope.launch {
            val s = runCatching { brews.summary(sessionId) }.getOrNull() ?: run { _ui.value = _ui.value.copy(loadFailed = true); return@launch }
            val samples = runCatching { brews.samples(sessionId) }.getOrNull()
            _ui.value = _ui.value.copy(
                summary = s, samples = samples, gaps = samples?.gaps() ?: emptyList(),
                doseText = (s.doseGrams ?: s.recipe?.doseGrams)?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "",
                note = s.note,
            )
        }
    }

    private val busy get() = _ui.value.state == SummaryState.Saving

    fun onDoseChange(v: String) { if (!busy) _ui.value = _ui.value.copy(doseText = v, doseError = false) }
    fun onNoteChange(v: String) { if (!busy) _ui.value = _ui.value.copy(note = v.take(NOTE_MAX)) }

    fun onSave() {
        val cur = _ui.value
        if (busy || cur.summary == null) return
        val dose = if (cur.doseText.isBlank()) null else parseDose(cur.doseText) ?: run { _ui.value = cur.copy(doseError = true); return }
        _ui.value = cur.copy(state = SummaryState.Saving, doseError = false)
        viewModelScope.launch {
            try {
                brews.finish(sessionId, dose, cur.note, if (recovered) SessionStatus.Interrupted else SessionStatus.Completed)
                if (!recovered) controller.newBrew()
                _events.send(SummaryEvent.Saved(sessionId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(state = SummaryState.SaveFailed)   // 입력값은 그대로
            }
        }
    }

    /** D3 확인 뒤. 삭제가 실패해도 남은 in_progress 행은 다음 실행의 D2 복구 대상이라 데이터를 잃지 않는다. */
    fun onDiscardConfirmed() {
        if (busy) return
        viewModelScope.launch {
            try { brews.deleteSession(sessionId) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            if (!recovered) controller.newBrew()
            _events.send(SummaryEvent.Discarded)
        }
    }
}
