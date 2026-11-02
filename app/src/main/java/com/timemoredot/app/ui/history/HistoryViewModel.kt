package com.timemoredot.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.data.BrewRepository
import com.timemoredot.app.data.CsvExport
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.data.SessionSummary
import com.timemoredot.app.ui.recipes.ListLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** architecture.md 9.7. S9·S10 공용 내보내기 상태와 결과. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
}

sealed interface ExportEvent {
    data class Exported(val count: Int) : ExportEvent
    data object ExportFailed : ExportEvent
}

data class HistoryUiState(val sessions: ListLoad<SessionSummary>, val export: ExportState)   // Empty면 CSV 비활성

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val brews: BrewRepository, private val zone: ZoneId = ZoneId.systemDefault()) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val export = MutableStateFlow<ExportState>(ExportState.Idle)
    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    private val load: Flow<ListLoad<SessionSummary>> = retry.flatMapLatest {
        brews.observeSessions()
            .map<List<SessionSummary>, ListLoad<SessionSummary>> { if (it.isEmpty()) ListLoad.Empty else ListLoad.Loaded(it) }
            .onStart { emit(ListLoad.Loading) }
            .catch { emit(ListLoad.Error) }
    }

    val uiState: StateFlow<HistoryUiState> = combine(load, export, ::HistoryUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(ListLoad.Loading, ExportState.Idle))

    fun onRetry() { retry.value++ }

    /** 8.4. 화면이 container.csvTarget(uri)로 만들어 넘긴다. 읽기·쓰기 예외는 ExportFailed. */
    fun onExportAll(target: CsvTarget) {
        if (export.value == ExportState.Exporting) return
        export.value = ExportState.Exporting
        viewModelScope.launch {
            val event = try {
                val sessions = brews.observeSessions().first()
                var rows = 0
                target.write { rows = CsvExport.writeSessions(sessions, zone, it) }
                ExportEvent.Exported(rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportEvent.ExportFailed
            }
            export.value = ExportState.Idle
            _events.send(event)
        }
    }
}
