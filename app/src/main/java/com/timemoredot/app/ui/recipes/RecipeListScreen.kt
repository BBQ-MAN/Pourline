package com.timemoredot.app.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.components.BannerKind
import com.timemoredot.app.ui.components.EmptyState
import com.timemoredot.app.ui.components.StatusBanner
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.brew.Recipe
import kotlinx.coroutines.delay

/** S7 화면 루트: uiState만 수집한다. 이동은 호출한 쪽(내비게이션)이 한다. */
@Composable
fun RecipeListScreen(vm: RecipeListViewModel, onEdit: (Long) -> Unit, onCreate: () -> Unit, onOpenBrew: () -> Unit) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.events.collect { if (it == RecipeListEvent.OpenBrew) onOpenBrew() } }
    RecipeListContent(ui, onBrew = vm::onBrewClick, onEdit = onEdit, onCreate = onCreate, onRetry = vm::onRetry, onOpenBrew = onOpenBrew)
}

/** 상태 없는 S7(스크린샷·UI 테스트가 직접 그린다). 하단 탭·레일이 보이는 화면이라 그 쪽 인셋은 탭이 받는다(3절). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListContent(ui: RecipeListUiState, onBrew: (Long) -> Unit, onEdit: (Long) -> Unit, onCreate: () -> Unit, onRetry: () -> Unit, onOpenBrew: () -> Unit = {}) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val insets = if (wide) WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
    else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("레시피", Modifier.semantics { heading(); paneTitle = "레시피" }) },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            if (ui.recipes is ListLoad.Loaded) ExtendedFloatingActionButton(onClick = onCreate, Modifier.focusRing(MaterialTheme.shapes.large)) {
                Icon(AppIcons.Add, contentDescription = null)
                Text("새 레시피", Modifier.padding(start = Space.s))
            }
        },
        contentWindowInsets = insets,
    ) { inner ->
        when (val r = ui.recipes) {
            ListLoad.Loading -> Loading(Modifier.padding(inner))
            ListLoad.Empty -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(
                    AppIcons.MenuBook, "저장된 레시피가 없습니다", Modifier.widthIn(max = 640.dp),
                    description = "단계별 목표 무게와 시간을 정해 두면 추출 중에 안내합니다.", actionLabel = "레시피 만들기", onAction = onCreate,
                )
            }
            ListLoad.Error -> Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                EmptyState(AppIcons.Error, "레시피를 불러오지 못했습니다", Modifier.widthIn(max = 640.dp), actionLabel = "다시 시도", onAction = onRetry)
            }
            is ListLoad.Loaded -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = inner.calculateStartPadding(LocalLayoutDirection.current), top = inner.calculateTopPadding(),
                    end = inner.calculateEndPadding(LocalLayoutDirection.current), bottom = inner.calculateBottomPadding() + FAB_CLEARANCE,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!ui.canStartBrew) item(key = "brewing_banner") {
                    StatusBanner(
                        BannerKind.Progress, "추출 중에는 레시피를 바꿀 수 없습니다", Modifier.widthIn(max = 640.dp).padding(horizontal = Space.l, vertical = Space.xs),
                        icon = AppIcons.Info, actionLabel = "추출 화면으로", onAction = onOpenBrew,
                    )
                }
                items(r.items, key = { it.id }) { RecipeRow(it, ui.canStartBrew, onBrew, onEdit, Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
            }
        }
    }
}

/** FAB 56 dp + 위아래 16 dp(3절): 마지막 행의 "추출"이 FAB에 가리지 않게. */
private val FAB_CLEARANCE = 88.dp

/** 5.6 Loading: 300 ms 동안 아무것도 그리지 않고, 그 뒤에도 불러오는 중이면 진행 원. */
@Composable
private fun Loading(modifier: Modifier) {
    var spin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(300); spin = true }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spin) CircularProgressIndicator(Modifier.size(48.dp).semantics { contentDescription = "레시피 불러오는 중" })
    }
}

/** 조약돌 행(6.4, M7R S2 후보 행과 같은 수식어 순서). 행은 편집, 안의 "추출" 버튼은 S1로. */
@Composable
private fun RecipeRow(r: Recipe, canStart: Boolean, onBrew: (Long) -> Unit, onEdit: (Long) -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        headlineContent = { Text(r.name, Modifier.clearAndSetSemantics { contentDescription = recipeSpoken(r) }) },
        supportingContent = {
            Column(Modifier.clearAndSetSemantics { }) {
                Text(recipeSummaryLine(r), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                Text(recipeStepsLine(r), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            }
        },
        trailingContent = {
            FilledTonalButton(
                onClick = { onBrew(r.id) }, enabled = canStart,
                modifier = Modifier.heightIn(min = 48.dp).focusRing(CircleShape).semantics {
                    contentDescription = "이 레시피로 추출"
                    if (!canStart) stateDescription = "추출 중에는 바꿀 수 없음"
                },
            ) { Text("추출") }
        },
        modifier = modifier
            .padding(horizontal = Space.l, vertical = Space.xs)   // 행 사이 8 dp: 포커스 링(밖 3 dp)이 이웃 행에 가려지지 않는다
            .heightIn(min = 72.dp)
            .focusRing(shape)   // clip 앞: clip 뒤에 두면 행 밖 3 dp 링이 잘린다
            .clip(shape)
            .clickable(onClickLabel = "레시피 편집", role = Role.Button) { onEdit(r.id) },
    )
}
