package com.timemoredot.app.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.Shooter
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun loaded(canStart: Boolean = true) = RecipeListUiState(ListLoad.Loaded(RFx.all.sortedBy { it.name }), canStart)

@Composable private fun List(ui: RecipeListUiState) =
    com.timemoredot.app.ui.nav.AppTabsScaffold(com.timemoredot.app.ui.nav.Dest.Recipes, true, onSelect = {}) { RecipeListContent(ui, {}, {}, {}, {}) }

/** S7 상태별 스크린샷(ui-design 8.6). 360 × 800. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RecipeListScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `목록 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.both("recipes_loading", advanceMs = 600) { List(RecipeListUiState(ListLoad.Loading, true)) }
        s.both("recipes_empty") { List(RecipeListUiState(ListLoad.Empty, true)) }
        s.both("recipes_loaded") { List(RecipeListUiState(ListLoad.Loaded(RFx.all.take(2).sortedBy { it.name }), true)) }
        s.both("recipes_error") { List(RecipeListUiState(ListLoad.Error, true)) }
        s.both("recipes_brewing") { List(loaded(canStart = false)) }
        s.both("recipes_first_run") { List(loaded()) }
        s.font200("recipes_first_run") { List(loaded()) }
    }
}

/** 태블릿 가로: 본문 열 640 dp 가운데 정렬. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-xhdpi")
class RecipeListTabletScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `첫 실행 태블릿`() {
        val s = Shooter(rule).also { it.start() }
        s.both("recipes_first_run_tablet") { List(loaded()) }
    }
}

/** S8 상태별 스크린샷. 값은 실제 ViewModel을 거쳐 만든 상태를 그대로 그린다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class RecipeEditScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private val store = VmStore()
    @After fun tearDown() = store.clear()

    private fun vm(id: Long?) = store.create { RecipeEditViewModel(FakeRecipeRepository(RFx.all), id) }
    private fun ui(id: Long?, block: RecipeEditViewModel.() -> Unit = {}): RecipeEditUiState = vm(id).also(block).uiState.value

    @Composable private fun Edit(ui: RecipeEditUiState, canDelete: Boolean = true) = RecipeEditContent(ui, RecipeEditActions(), canDelete, {})
    @Composable private fun Live(vm: RecipeEditViewModel, existing: Boolean = true) = RecipeEditScreen(vm, existing, {}, {})

    /** Shooter가 시계를 멈춰 두므로 조작하는 동안만 자동 진행을 켠다. */
    private fun act(block: () -> Unit) { rule.mainClock.autoAdvance = true; block(); rule.waitForIdle(); rule.mainClock.autoAdvance = false }

    private fun blank(state: RecipeEditState) = RecipeEditUiState(state, "", "", "", emptyList(), "", "", false, emptyList(), emptyList(), false, null, false)

    private fun invalid() = ui(null) {
        onNameChange(""); onDoseChange("0.05"); onWaterChange("abc")
        onAddStep(); onStepChange(0, StepDraft("뜸", "", "0", "10"))
        onAddStep(); onStepChange(1, StepDraft("붓기", "250", "", ""))
        onSave()
    }

    @Test fun `기본 상태`() {
        val s = Shooter(rule).also { it.start() }
        s.both("recipe_edit_loading", advanceMs = 600) { Edit(blank(RecipeEditState.Loading)) }
        s.both("recipe_edit_new") { Edit(ui(null), canDelete = false) }
        s.both("recipe_edit_editing") { Edit(ui(RFx.v60.id)) }
        s.both("recipe_edit_invalid") { Edit(invalid(), canDelete = false) }
        s.font200("recipe_edit_invalid") { Edit(invalid(), canDelete = false) }
        s.both("recipe_edit_saving") { Edit(ui(RFx.v60.id).copy(state = RecipeEditState.Saving)) }
        s.both("recipe_edit_not_found") { Edit(blank(RecipeEditState.NotFound)) }
    }

    @Test fun `대화상자`() {
        val s = Shooter(rule).also { it.start() }
        s.both("recipe_edit_discard_confirm", screen = true) { Edit(ui(RFx.v60.id)); DiscardChangesDialog({}, {}) }
        s.both("recipe_edit_delete_confirm", screen = true) { Edit(ui(RFx.v60.id)); DeleteRecipeDialog("V60 기본", {}, {}) }
    }

    @Test fun `스위치 단계 카드`() {
        val s = Shooter(rule).also { it.start() }
        s.both("recipe_edit_valve") { Edit(ui(RFx.devil.id)) }
        s.font200("recipe_edit_valve", prepare = { act { rule.onNodeWithContentDescription("1단계 뒤에 스위치 바꾸는 단계 넣기").performScrollTo() } }) { Edit(ui(RFx.devil.id)) }
        s.both("recipe_edit_valve_cards", prepare = { act { rule.onNodeWithContentDescription("1단계 뒤에 스위치 바꾸는 단계 넣기").performScrollTo(); rule.onNodeWithContentDescription("2단계 위로 옮기기").performScrollTo() } }) { Edit(ui(RFx.devil.id)) }
        s.both("recipe_edit_valve_steps", prepare = { act { rule.onNodeWithContentDescription("4단계 스위치 닫힘").performScrollTo() } }) { Edit(ui(RFx.devil.id)) }
        // 테마마다 새 VM: 프레임마다 content가 새로 합성되므로 remember가 새 VM을 만든다
        val insert = { act { rule.onNodeWithContentDescription("1단계 뒤에 스위치 바꾸는 단계 넣기").performScrollTo().performClick() } }
        s.both("recipe_edit_valve_inserted", prepare = { insert(); act { rule.onNodeWithText("스위치 일정").performScrollTo() } }) { Live(remember { vm(RFx.hario.id) }) }
        s.both("recipe_edit_valve_inserted_next", prepare = { insert(); act { rule.onNodeWithText("스위치 · 1:05부터", substring = true).performScrollTo() } }) { Live(remember { vm(RFx.hario.id) }) }
        s.both("recipe_edit_valve_off", prepare = { act { rule.onNodeWithText("스위치(밸브) 레시피").performScrollTo().performClick() } }) { Live(remember { vm(RFx.hario.id) }) }
        s.both("recipe_edit_step_error", prepare = { act { rule.onNodeWithText("앞 단계 목표 시각(1:00)보다 늦어야 합니다").performScrollTo() } }) {
            Edit(ui(null) {
                onNameChange("테스트"); onDoseChange("15"); onWaterChange("250")
                onAddStep(); onStepChange(0, StepDraft("뜸", "100", "1", "0"))
                onAddStep(); onStepChange(1, StepDraft("붓기", "90", "0", "50"))
                onSave()
            }, canDelete = false)
        }
    }
}
