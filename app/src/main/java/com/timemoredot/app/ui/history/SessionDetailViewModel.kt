package com.timemoredot.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.CsvExport
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.summary.NOTE_MAX
import com.timemoredot.brew.SampleBuffer
import com.timemoredot.brew.reachTimesMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** architecture.md 9.7. 차트 모드(무게/유량)는 화면 로컬 상태라 여기에 없다(9.1). */
enum class DetailState { Loading, Loaded, NoSamples, NotFound }

data class SessionDetailUiState(
    val state: DetailState,
    val summary: SessionSummary?,
    val samples: SampleBuffer?,
    val stepReachTimesMs: List<Int?>,    // 레시피 단계 표 (레시피 없으면 빈 목록)
    val export: ExportState,
)

sealed interface SessionDetailEvent {
    data class Export(val e: ExportEvent) : SessionDetailEvent
    data object Deleted : SessionDetailEvent
    data object DeleteFailed : SessionDetailEvent   // 명세 밖: 삭제 실패를 조용히 삼키지 않으려고 더했다(스낵바 "다시")
}

const val NOTE_SAVE_DEBOUNCE_MS = 500L

class SessionDetailViewModel(
    private val brews: BrewRepository,
    private val sessionId: Long,
    private val appScope: CoroutineScope,   // onCleared 뒤에도 남은 메모 저장을 끝내려고 받는다
) : ViewModel() {
    private val _ui = MutableStateFlow(SessionDetailUiState(DetailState.Loading, null, null, emptyList(), ExportState.Idle))
    val uiState: StateFlow<SessionDetailUiState> = _ui.asStateFlow()
    private val _events = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events: Flow<SessionDetailEvent> = _events.receiveAsFlow()

    private var noteJob: Job? = null
    private var pendingNote: String? = null   // 아직 저장되지 않은 마지막 입력
    private var deleting = false

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val s = try { brews.summary(sessionId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (s == null) { _ui.update { it.copy(state = DetailState.NotFound) }; return@launch }
            val raw = try { brews.samples(sessionId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val samples = raw?.takeIf { it.size > 0 }
            val reach = s.recipe?.let { r -> samples?.let(r::reachTimesMs) ?: List(r.steps.size) { null } } ?: emptyList()
            _ui.update { it.copy(state = if (samples == null) DetailState.NoSamples else DetailState.Loaded, summary = s, samples = samples, stepReachTimesMs = reach) }
        }
    }

    fun onNoteChange(note: String) {
        if (deleting) return
        val v = note.take(NOTE_MAX)
        pendingNote = v
        noteJob?.cancel()
        noteJob = viewModelScope.launch {
            delay(NOTE_SAVE_DEBOUNCE_MS)
            try {
                brews.updateNote(sessionId, v)
                if (pendingNote == v) pendingNote = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }   // pendingNote가 남아 화면을 떠날 때 한 번 더 시도한다
        }
    }

    /** D3 확인 뒤. 성공해야 Deleted. 성공하면 대기 중 메모 저장은 버리고, 실패하면 다시 예약한다. */
    fun onDeleteConfirmed() {
        if (deleting) return
        deleting = true
        val held = pendingNote
        noteJob?.cancel()
        viewModelScope.launch {
            try {
                brews.deleteSession(sessionId)
                pendingNote = null
                _events.send(SessionDetailEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                deleting = false
                if (held != null) onNoteChange(held)   // 삭제 실패: 버렸던 메모 저장을 다시 예약
                _events.send(SessionDetailEvent.DeleteFailed)
            }
        }
    }

    /** 8.4. 샘플이 없는 기록(NoSamples)은 머리글만 있는 파일을 쓴다. */
    fun onExportSamples(target: CsvTarget) {
        val cur = _ui.value
        if (cur.export == ExportState.Exporting || (cur.state != DetailState.Loaded && cur.state != DetailState.NoSamples)) return
        _ui.update { it.copy(export = ExportState.Exporting) }
        viewModelScope.launch {
            val event = try {
                val samples = cur.samples ?: SampleBuffer()
                target.write { CsvExport.writeSamples(samples, it) }
                ExportEvent.Exported(samples.size)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportEvent.ExportFailed
            }
            _ui.update { it.copy(export = ExportState.Idle) }
            _events.send(SessionDetailEvent.Export(event))
        }
    }

    /** S10을 떠날 때(뒤로 가기 포함) 디바운스 대기 중이던 입력을 appScope에서 저장한다. 삭제 뒤에는 저장하지 않는다. */
    override fun onCleared() {
        val note = pendingNote
        if (note != null && !deleting) appScope.launch { try { brews.updateNote(sessionId, note) } catch (e: CancellationException) { throw e } catch (_: Exception) { } }
    }
}
