package com.timemoredot.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.data.RecipeRepository
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.Recipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** architecture.md 9.6. S7·S9 공용 목록 상태. */
sealed interface ListLoad<out T> {
    data object Loading : ListLoad<Nothing>
    data object Empty : ListLoad<Nothing>
    data class Loaded<T>(val items: List<T>) : ListLoad<T>
    data object Error : ListLoad<Nothing>
}

/** canStartBrew = controller phase가 Idle·WaitingForPour일 때만 true(9.4 canChangeRecipeOrSettings). */
data class RecipeListUiState(val recipes: ListLoad<Recipe>, val canStartBrew: Boolean)

sealed interface RecipeListEvent { data object OpenBrew : RecipeListEvent }

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModel(recipes: RecipeRepository, private val controller: BrewSessionController) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val _events = Channel<RecipeListEvent>(Channel.BUFFERED)
    val events: Flow<RecipeListEvent> = _events.receiveAsFlow()

    private val load: Flow<ListLoad<Recipe>> = retry.flatMapLatest {
        recipes.observeAll()
            .map<List<Recipe>, ListLoad<Recipe>> { if (it.isEmpty()) ListLoad.Empty else ListLoad.Loaded(it) }
            .onStart { emit(ListLoad.Loading) }
            .catch { emit(ListLoad.Error) }
    }

    val uiState: StateFlow<RecipeListUiState> = combine(load, controller.state.map { canStart(it.phase) }.distinctUntilChanged(), ::RecipeListUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeListUiState(ListLoad.Loading, canStart(controller.state.value.phase)))

    fun onBrewClick(id: Long) {
        if (!canStart(controller.state.value.phase)) return   // 구독 여부와 상관없이 지금 phase로 판정
        val recipe = (uiState.value.recipes as? ListLoad.Loaded)?.items?.firstOrNull { it.id == id } ?: return
        controller.selectRecipe(recipe)
        _events.trySend(RecipeListEvent.OpenBrew)
    }

    fun onRetry() { retry.value++ }

    private fun canStart(phase: BrewPhase) = phase == BrewPhase.Idle || phase == BrewPhase.WaitingForPour
}
