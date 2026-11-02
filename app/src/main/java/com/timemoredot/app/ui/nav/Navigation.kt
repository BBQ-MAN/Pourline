package com.timemoredot.app.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.timemoredot.app.ui.history.HistoryScreen
import com.timemoredot.app.ui.history.HistoryViewModel
import com.timemoredot.app.ui.history.SessionDetailScreen
import com.timemoredot.app.ui.history.SessionDetailViewModel
import com.timemoredot.app.ui.recipes.RecipeEditScreen
import com.timemoredot.app.ui.recipes.RecipeEditViewModel
import com.timemoredot.app.ui.recipes.RecipeListScreen
import com.timemoredot.app.ui.recipes.RecipeListViewModel
import com.timemoredot.app.ui.summary.SummaryScreen
import com.timemoredot.app.ui.summary.SummaryViewModel
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.TimemoreApp
import com.timemoredot.app.ui.theme.AppMotion
import com.timemoredot.app.ui.brew.BrewScreen
import com.timemoredot.app.ui.brew.BrewViewModel
import com.timemoredot.app.ui.connect.ConnectScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.serialization.Serializable

/** architecture.md 9.1 타입 안전 경로. */
@Serializable data object Brew
@Serializable data object Connect
@Serializable data class Summary(val sessionId: Long, val recovered: Boolean)
@Serializable data object Recipes
@Serializable data class RecipeEdit(val id: Long?)
@Serializable data object History
@Serializable data class SessionDetail(val id: Long)

/** S6 저장 성공 → Brew로 돌아가며 두는 결과 키(architecture.md 9.1). */
const val SAVED_SESSION_ID = "saved_session_id"

/** S10 삭제 성공 → History로 돌아가며 두는 결과 키(architecture.md 9.1). */
const val SESSION_DELETED = "session_deleted"

enum class Dest { Brew, Connect, Summary, Recipes, RecipeEdit, History, SessionDetail }

/** 창 너비만 본다(가로 휴대폰에서 아래 탭을 골라 높이를 잃지 않게). */
fun tabTypeFor(windowWidthDp: Int): NavigationSuiteType =
    if (windowWidthDp >= 600) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar

/** Recipes·History에서 보이고, Brew는 showBottomBar를 따른다. 나머지는 숨긴다. */
fun tabsVisible(dest: Dest?, showBottomBar: Boolean): Boolean = when (dest) {
    Dest.Recipes, Dest.History -> true
    Dest.Brew -> showBottomBar
    else -> false
}

private class Tab(val label: String, val icon: ImageVector, val route: Any, val dest: Dest)

private val Tabs = listOf(
    Tab("추출", AppIcons.Scale, Brew, Dest.Brew),
    Tab("레시피", AppIcons.MenuBook, Recipes, Dest.Recipes),
    Tab("기록", AppIcons.History, History, Dest.History),
)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val dest: Dest? = when {
        destination == null -> null
        destination.hasRoute<Brew>() -> Dest.Brew
        destination.hasRoute<Connect>() -> Dest.Connect
        destination.hasRoute<Summary>() -> Dest.Summary
        destination.hasRoute<Recipes>() -> Dest.Recipes
        destination.hasRoute<RecipeEdit>() -> Dest.RecipeEdit
        destination.hasRoute<History>() -> Dest.History
        destination.hasRoute<SessionDetail>() -> Dest.SessionDetail
        else -> null
    }
    val container = (LocalContext.current.applicationContext as TimemoreApp).container
    // S1 VM은 Activity 범위: 탭 보임(showBottomBar)을 셸이 읽는다. 연결·세션은 AppContainer가 소유한다
    val brewVm: BrewViewModel = viewModel(factory = viewModelFactory {
        initializer {
            BrewViewModel(container.brewController, container.scale, container.recipes, container.brews, container.prefs, container.permission, container.clock)
        }
    })
    val brewUi by brewVm.uiState.collectAsStateWithLifecycle()
    val showBottomBar = brewUi.showBottomBar
    val visible = tabsVisible(dest ?: Dest.Brew, showBottomBar)   // 첫 구성의 null은 시작 화면(Brew)으로 본다
    val goTab: (Any) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    AppTabsScaffold(dest, visible, onSelect = goTab) {
        NavHost(
            nav, startDestination = Brew,
            enterTransition = { fadeIn(AppMotion.navEnter) }, exitTransition = { fadeOut(AppMotion.navExit) },
            popEnterTransition = { fadeIn(AppMotion.navEnter) }, popExitTransition = { fadeOut(AppMotion.navExit) },
        ) {
            composable<Brew> { backStack ->
                val saved by backStack.savedStateHandle.getStateFlow<Long?>(SAVED_SESSION_ID, null).collectAsStateWithLifecycle()
                BrewScreen(
                    brewVm,
                    onOpenConnect = { nav.navigate(Connect) },
                    onOpenSummary = { id, recovered -> nav.navigate(Summary(id, recovered)) },
                    onOpenDetail = { nav.navigate(SessionDetail(it)) },
                    onCreateRecipe = { nav.navigate(RecipeEdit(null)) },
                    savedSessionId = saved,
                    onSavedSessionConsumed = { backStack.savedStateHandle[SAVED_SESSION_ID] = null },
                    locationPermission = container.permission.requiredPermissions.contains(android.Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
            // Connect가 맨 위일 때만 pop: 이중 호출이 시작 경로까지 빼지 않게
            composable<Connect> { ConnectScreen(onClose = { if (nav.currentDestination?.hasRoute<Connect>() == true) nav.popBackStack() }) }
            composable<Summary> { entry ->
                val route = entry.toRoute<Summary>()
                val vm: SummaryViewModel = viewModel(key = "summary-${route.sessionId}-${route.recovered}", factory = viewModelFactory {
                    initializer { SummaryViewModel(container.brews, container.brewController, route.sessionId, route.recovered) }
                })
                // 맨 위일 때만 pop: 이중 호출이 Brew까지 빼지 않게
                val leave = { if (nav.currentDestination?.hasRoute<Summary>() == true) nav.popBackStack() }
                SummaryScreen(
                    vm,
                    onSaved = { id -> nav.previousBackStackEntry?.savedStateHandle?.set(SAVED_SESSION_ID, id); leave() },
                    onDiscarded = { brewVm.onSessionGone(route.sessionId); leave() },
                    onExit = { leave() },
                )
            }
            composable<Recipes> {
                val vm: RecipeListViewModel = viewModel(factory = viewModelFactory {
                    initializer { RecipeListViewModel(container.recipes, container.brewController) }
                })
                RecipeListScreen(vm, onEdit = { nav.navigate(RecipeEdit(it)) }, onCreate = { nav.navigate(RecipeEdit(null)) }, onOpenBrew = { goTab(Brew) })
            }
            composable<RecipeEdit> { entry ->
                val route = entry.toRoute<RecipeEdit>()
                val vm: RecipeEditViewModel = viewModel(key = "recipe-edit-${route.id}", factory = viewModelFactory {
                    initializer { RecipeEditViewModel(container.recipes, route.id) }
                })
                // 맨 위일 때만 pop: 이중 호출이 이전 화면까지 빼지 않게
                val leave = { if (nav.currentDestination?.hasRoute<RecipeEdit>() == true) nav.popBackStack() }
                RecipeEditScreen(vm, existing = route.id != null, onClose = { leave() }, onFinished = { leave() })
            }
            composable<History> { backStack ->
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container.brews) } })
                val deleted by backStack.savedStateHandle.getStateFlow(SESSION_DELETED, false).collectAsStateWithLifecycle()
                HistoryScreen(
                    vm, container::csvTarget, onOpen = { nav.navigate(SessionDetail(it)) }, onStartBrew = { goTab(Brew) },
                    sessionDeleted = deleted, onDeletedConsumed = { backStack.savedStateHandle[SESSION_DELETED] = false },
                )
            }
            composable<SessionDetail> { entry ->
                val route = entry.toRoute<SessionDetail>()
                val vm: SessionDetailViewModel = viewModel(key = "session-detail-${route.id}", factory = viewModelFactory {
                    initializer { SessionDetailViewModel(container.brews, route.id, container.appScope) }
                })
                // 맨 위일 때만 pop: 이중 호출이 이전 화면까지 빼지 않게
                val leave = { if (nav.currentDestination?.hasRoute<SessionDetail>() == true) nav.popBackStack() }
                SessionDetailScreen(
                    vm, container::csvTarget, onBack = { leave() },
                    onDeleted = {
                        if (nav.previousBackStackEntry?.destination?.hasRoute<History>() == true) {
                            nav.previousBackStackEntry?.savedStateHandle?.set(SESSION_DELETED, true); leave()
                        } else if (nav.currentDestination?.hasRoute<SessionDetail>() == true) {
                            // S1 '보기'로 열었으면 S9로 가서 스낵바를 띄운다(ui-design 5.9)
                            nav.navigate(History) { popUpTo<SessionDetail> { inclusive = true } }
                            nav.currentBackStackEntry?.savedStateHandle?.set(SESSION_DELETED, true)
                        }
                    },
                )
            }
        }
    }
}

/** 최상위 탭(1절). 탭 모양은 창 너비, 보임은 visible로 애니메이션 없이 바꾼다(snapTo). 선택 탭은 굵은 라벨. */
@Composable
fun AppTabsScaffold(selected: Dest?, visible: Boolean, onSelect: (Any) -> Unit, content: @Composable () -> Unit) {
    val state = rememberNavigationSuiteScaffoldState(if (visible) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden)
    LaunchedEffect(visible) {
        state.snapTo(if (visible) NavigationSuiteScaffoldValue.Visible else NavigationSuiteScaffoldValue.Hidden)   // 애니메이션 없이(D6)
    }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tabs.forEach { tab ->
                item(
                    selected = selected == tab.dest,
                    onClick = { onSelect(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label, fontWeight = if (selected == tab.dest) FontWeight.Bold else null) },   // 선택은 굵은 라벨로도 전달(1절)
                )
            }
        },
        // 숨김은 같은 프레임에 자리까지 없앤다: 상태 snapTo만으로는 스캐폴드가 숨김 전환을 몇 프레임 그린다(4.10 탭 숨김은 즉시)
        layoutType = if (visible) tabTypeFor(LocalConfiguration.current.screenWidthDp) else NavigationSuiteType.None,
        state = state,
        content = content,
    )
}
