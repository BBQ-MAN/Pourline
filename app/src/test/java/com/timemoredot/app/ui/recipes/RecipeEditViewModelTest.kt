package com.timemoredot.app.ui.recipes

import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.ValveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** architecture.md 9.6 RecipeEditViewModel. FakeRecipeRepository, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditViewModelTest {
    private val store = VmStore()
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private class Rig(val repo: FakeRecipeRepository, val vm: RecipeEditViewModel) {
        val ui get() = vm.uiState.value
    }

    private fun TestScope.open(id: Long?, repo: FakeRecipeRepository = FakeRecipeRepository(RFx.all)): Rig {
        val vm = store.create { RecipeEditViewModel(repo, id) }
        runCurrent()
        return Rig(repo, vm)
    }

    /** 새 레시피를 모든 필드가 맞게 채운다. */
    private fun Rig.fill(
        name: String = "새 레시피", dose: String = "15", water: String = "250",
        steps: List<StepDraft> = listOf(StepDraft("붓기", "250", "1", "30")),
    ) {
        vm.onNameChange(name); vm.onDoseChange(dose); vm.onWaterChange(water)
        steps.forEachIndexed { i, s -> vm.onAddStep(); vm.onStepChange(i, s) }
    }

    private fun TestScope.save(r: Rig): List<RecipeFieldError> { r.vm.onSave(); runCurrent(); return r.ui.errors }

    private fun d(g: String, m: String, s: String, open: Boolean = false, name: String = "s") = StepDraft(name, g, m, s, open)

    // ---- 검증 ----

    @Test fun `이름 검증`() = runTest {
        val r = open(null); r.fill(name = "")
        assertEquals(listOf<RecipeFieldError>(RecipeFieldError.NameEmpty), save(r))
        r.vm.onNameChange("가".repeat(31))
        assertEquals(listOf<RecipeFieldError>(RecipeFieldError.NameTooLong), save(r))
        assertTrue(r.repo.upserted.isEmpty())
        r.vm.onNameChange("가".repeat(30))
        assertEquals(emptyList<RecipeFieldError>(), save(r))
    }

    @Test fun `원두 검증`() = runTest {
        for (bad in listOf("0", "0.05", "100.1", "abc", "")) {
            val r = open(null); r.fill(dose = bad)
            assertEquals(bad, listOf<RecipeFieldError>(RecipeFieldError.DoseInvalid), save(r))
            assertTrue(r.repo.upserted.isEmpty())
        }
        for (ok in listOf("0.1", "100.0")) { val r = open(null); r.fill(dose = ok); assertEquals(ok, emptyList<RecipeFieldError>(), save(r)) }
    }

    @Test fun `물 0은 WaterInvalid, 단계 0개는 NoSteps`() = runTest {
        val r = open(null); r.fill(water = "0", steps = emptyList())
        assertEquals(listOf(RecipeFieldError.WaterInvalid, RecipeFieldError.NoSteps), save(r))
    }

    @Test fun `숫자 아닌 단계 값과 1단계 누적 0은 StepNumberInvalid`() = runTest {
        for (bad in listOf(d("abc", "1", "0"), d("0", "1", "0"), d("50", "x", "0"), d("50", "1", "60"), d("50", "", "")))  {
            val r = open(null); r.fill(steps = listOf(bad), water = "50")
            assertEquals(bad.toString(), listOf<RecipeFieldError>(RecipeFieldError.StepNumberInvalid(0)), save(r))
        }
    }

    @Test fun `앞 단계보다 작은 누적 g는 StepGramsDecreasing, 같으면 기다리기 단계로 저장된다`() = runTest {
        val r = open(null); r.fill(water = "90", steps = listOf(d("100", "0", "10"), d("90", "0", "20")))
        assertEquals(listOf<RecipeFieldError>(RecipeFieldError.StepGramsDecreasing(1, 100.0)), save(r))
        r.vm.onStepChange(1, d("100.0", "0", "20")); r.vm.onWaterChange("100")
        assertEquals(emptyList<RecipeFieldError>(), save(r))
        assertEquals(listOf(100.0, 100.0), r.repo.upserted.single().steps.map { it.targetGrams })
    }

    @Test fun `앞 단계 이하의 시각과 1단계 0시 0분은 StepTimeNotIncreasing`() = runTest {
        val r = open(null); r.fill(water = "100", steps = listOf(d("50", "0", "0"), d("80", "0", "30"), d("100", "0", "30")))
        assertEquals(listOf<RecipeFieldError>(RecipeFieldError.StepTimeNotIncreasing(0), RecipeFieldError.StepTimeNotIncreasing(2)), save(r))
    }

    @Test fun `한 단계에 누적 감소와 시각 오류가 같이 나온다`() = runTest {
        val r = open(null); r.fill(water = "100", steps = listOf(d("100", "1", "0"), d("90", "0", "50")))
        assertEquals(listOf(RecipeFieldError.StepGramsDecreasing(1, 100.0), RecipeFieldError.StepTimeNotIncreasing(1)), save(r))
    }

    @Test fun `단계 시각 3600초는 StepNumberInvalid, 3599초는 통과`() = runTest {
        for ((m, s) in listOf("60" to "0", "100" to "5")) {
            val r = open(null); r.fill(water = "100", steps = listOf(d("50", "0", "10"), d("100", m, s)))
            assertEquals(listOf<RecipeFieldError>(RecipeFieldError.StepNumberInvalid(1)), save(r))
            assertTrue(r.repo.upserted.isEmpty())
        }
        val ok = open(null); ok.fill(water = "100", steps = listOf(d("50", "0", "10"), d("100", "59", "59")))
        assertEquals(emptyList<RecipeFieldError>(), save(ok))
        assertEquals(3599, ok.repo.upserted.single().steps.last().targetAtSec)
    }

    @Test fun `끝 시각 오류와 통과`() = runTest {
        val steps = listOf(d("50", "0", "10"), d("100", "1", "25"))
        for ((m, s) in listOf("1" to "24", "1" to "", "" to "30", "1" to "60", "abc" to "0", "60" to "0")) {
            val r = open(null); r.fill(water = "100", steps = steps); r.vm.onFinishTimeChange(m, s)
            assertEquals("$m:$s", listOf<RecipeFieldError>(RecipeFieldError.FinishTimeInvalid(85)), save(r))
            assertTrue(r.repo.upserted.isEmpty())
        }
        for ((m, s, sec) in listOf(Triple("1", "25", 85), Triple("3", "0", 180))) {
            val r = open(null); r.fill(water = "100", steps = steps); r.vm.onFinishTimeChange(m, s)
            assertEquals(emptyList<RecipeFieldError>(), save(r)); assertEquals(sec, r.repo.upserted.single().finishAtSec)
        }
        val blank = open(null); blank.fill(water = "100", steps = steps)
        assertEquals(emptyList<RecipeFieldError>(), save(blank)); assertNull(blank.repo.upserted.single().finishAtSec)
    }

    @Test fun `finishAtSec 120인 레시피는 이름만 바꿔 저장해도 유지된다`() = runTest {
        val rec = RFx.v60.copy(finishAtSec = 120)
        val r = open(rec.id, FakeRecipeRepository(listOf(rec)))
        assertEquals("2", r.ui.finishMinutesText); assertEquals("00", r.ui.finishSecondsText)
        r.vm.onNameChange("이름만"); assertEquals(emptyList<RecipeFieldError>(), save(r))
        assertEquals(120, r.repo.upserted.single().finishAtSec)
        assertEquals(rec.copy(name = "이름만"), r.repo.upserted.single())
    }

    // ---- 저장·불러오기 ----

    @Test fun `올바른 입력은 저장되고 Saved가 나온다`() = runTest {
        val r = open(null); r.fill()
        assertEquals(RecipeEditState.New, r.ui.state)
        r.vm.onSave(); runCurrent()
        assertEquals(1, r.repo.upserted.size)
        assertEquals(Recipe(0, "새 레시피", 15.0, 250.0, listOf(com.timemoredot.brew.RecipeStep("붓기", 250.0, 90)), null), r.repo.upserted.single())
        assertEquals(RecipeEditEvent.Saved, r.vm.events.first())
    }

    @Test fun `저장 실패는 SaveFailed 이벤트와 상태, 다시 저장할 수 있다`() = runTest {
        val r = open(null); r.fill()
        r.repo.failNext("upsert", RuntimeException("io"))
        r.vm.onSave(); runCurrent()
        assertEquals(RecipeEditState.SaveFailed, r.ui.state)
        assertEquals(RecipeEditEvent.SaveFailed, r.vm.events.first())
        r.vm.onSave(); runCurrent()
        assertEquals(RecipeEditEvent.Saved, r.vm.events.first())
    }

    @Test fun `기존 id는 Loading으로 시작해 무시하다가 불러온 뒤 Editing`() = runTest {
        val repo = FakeRecipeRepository(RFx.all)
        val vm = store.create { RecipeEditViewModel(repo, RFx.v60.id) }
        assertEquals(RecipeEditState.Loading, vm.uiState.value.state)
        vm.onNameChange("x"); vm.onSave()
        assertEquals("", vm.uiState.value.name); assertFalse(vm.uiState.value.hasUnsavedChanges)
        runCurrent()
        assertTrue(repo.upserted.isEmpty())
        val ui = vm.uiState.value
        assertEquals(RecipeEditState.Editing, ui.state)
        assertEquals("V60 기본", ui.name); assertEquals("15", ui.doseText); assertEquals("250", ui.waterText)
        assertEquals(3, ui.steps.size); assertFalse(ui.hasUnsavedChanges)
        assertEquals(15.0, 250.0 / ui.ratio!!, 1e-9)
    }

    @Test fun `없는 id는 NotFound, id가 null이면 첫 state는 New`() = runTest {
        assertEquals(RecipeEditState.NotFound, open(99).ui.state)
        val vm = store.create { RecipeEditViewModel(FakeRecipeRepository(), null) }
        assertEquals(RecipeEditState.New, vm.uiState.value.state)
    }

    @Test fun `변경은 hasUnsavedChanges, 되돌리면 false, 물과 다른 마지막 단계는 경고, 순서 이동`() = runTest {
        val r = open(RFx.v60.id)
        assertFalse(r.ui.hasUnsavedChanges)
        r.vm.onNameChange("다른"); assertTrue(r.ui.hasUnsavedChanges)
        r.vm.onNameChange("V60 기본"); assertFalse(r.ui.hasUnsavedChanges)
        assertFalse(r.ui.lastStepMismatchesWater)
        r.vm.onWaterChange("300"); assertTrue(r.ui.lastStepMismatchesWater)
        val names = r.ui.steps.map { it.name }
        r.vm.onMoveStepDown(0); assertEquals(listOf(names[1], names[0], names[2]), r.ui.steps.map { it.name })
        r.vm.onMoveStepUp(1); assertEquals(names, r.ui.steps.map { it.name })
        r.vm.onMoveStepUp(0); r.vm.onMoveStepDown(2); assertEquals(names, r.ui.steps.map { it.name })   // 끝은 무시
        r.vm.onRemoveStep(1); assertEquals(2, r.ui.steps.size)
    }

    @Test fun `삭제 확인은 삭제하고 Deleted`() = runTest {
        val r = open(RFx.v60.id)
        r.vm.onDeleteConfirmed(); runCurrent()
        assertEquals(listOf(RFx.v60.id), r.repo.deletedIds)
        assertEquals(RecipeEditEvent.Deleted, r.vm.events.first())
    }

    @Test fun `삭제 실패는 DeleteFailed이고 저장하지 않는다`() = runTest {
        val r = open(RFx.v60.id)
        r.repo.failNext("delete", RuntimeException("io"))
        r.vm.onDeleteConfirmed(); runCurrent()
        assertEquals(RecipeEditEvent.DeleteFailed, r.vm.events.first())
        assertTrue(r.repo.upserted.isEmpty())
    }

    // ---- 밸브 ----

    @Test fun `새 레시피는 밸브 꺼짐이고 그대로 저장하면 모든 valveOpen이 null`() = runTest {
        val r = open(null)
        assertFalse(r.ui.valveEnabled)
        r.fill(steps = listOf(d("100", "0", "10"), d("250", "1", "30")))
        assertTrue(r.ui.steps.none { it.valveOpen })
        assertEquals(emptyList<RecipeFieldError>(), save(r))
        val saved = r.repo.upserted.single()
        assertTrue(saved.steps.all { it.valveOpen == null }); assertEquals(emptyList<ValveEvent>(), saved.valveSchedule)
    }

    @Test fun `악마의 레시피는 스위치 켜짐, 체크, 미리보기를 채우고 이름만 바꿔 저장하면 그대로`() = runTest {
        val r = open(RFx.devil.id)
        assertTrue(r.ui.valveEnabled)
        assertEquals(listOf(true, true, true, false, false, true), r.ui.steps.map { it.valveOpen })
        assertEquals(listOf(ValveEvent(0, true), ValveEvent(75, false), ValveEvent(105, true)), r.ui.valvePreview)
        r.vm.onNameChange("이름만"); assertEquals(emptyList<RecipeFieldError>(), save(r))
        assertEquals(RFx.devil.steps.map { it.valveOpen }, r.repo.upserted.single().steps.map { it.valveOpen })
    }

    @Test fun `스위치를 끄고 저장하면 null, 다시 켜면 체크가 그대로`() = runTest {
        val r = open(RFx.devil.id)
        r.vm.onValveEnabledChange(false)
        assertEquals(emptyList<ValveEvent>(), r.ui.valvePreview)
        assertEquals(listOf(true, true, true, false, false, true), r.ui.steps.map { it.valveOpen })
        save(r)
        assertTrue(r.repo.upserted.single().steps.all { it.valveOpen == null })
        val again = open(RFx.devil.id); again.vm.onValveEnabledChange(false); again.vm.onValveEnabledChange(true)
        assertEquals(listOf(true, true, true, false, false, true), again.ui.steps.map { it.valveOpen })
        assertFalse(again.ui.hasUnsavedChanges)
    }

    @Test fun `체크를 바꾸면 미리보기가 바뀌고 저장된 valveOpen은 전부 non-null`() = runTest {
        val r = open(RFx.devil.id)
        r.vm.onStepChange(3, r.ui.steps[3].copy(valveOpen = true))
        assertEquals(listOf(ValveEvent(0, true), ValveEvent(85, false), ValveEvent(105, true)), r.ui.valvePreview)
        assertTrue(r.ui.hasUnsavedChanges)
        save(r)
        assertTrue(r.repo.upserted.single().steps.all { it.valveOpen != null })
    }

    @Test fun `단계 추가는 마지막 단계 체크를 따르고 없으면 닫힘`() = runTest {
        val r = open(RFx.devil.id)
        r.vm.onAddStep(); assertTrue(r.ui.steps.last().valveOpen)
        val empty = open(null); empty.vm.onAddStep(); assertFalse(empty.ui.steps.single().valveOpen)
        val hario = open(RFx.hario.id); hario.vm.onAddStep(); assertTrue(hario.ui.steps.last().valveOpen)
    }

    @Test fun `순서 이동은 체크를 단계와 함께 옮기고, 단계 오류가 있으면 미리보기는 새 시작 시각으로`() = runTest {
        val r = open(RFx.hario.id)   // 닫힘 0:10, 닫힘 2:00, 열림 2:30 (누적 g는 모두 240)
        r.vm.onMoveStepDown(1)
        assertEquals(listOf(false, true, false), r.ui.steps.map { it.valveOpen })
        // 시각 칸은 자리에 남아 이동 직후에도 새 단계 시작 시각으로 일정이 나온다
        assertEquals(listOf("0:10", "2:00", "2:30"), r.ui.steps.map { "${it.minutesText}:${it.secondsText.padStart(2, '0')}" })
        assertEquals(listOf(ValveEvent(0, false), ValveEvent(10, true), ValveEvent(120, false)), r.ui.valvePreview)
        r.vm.onMoveStepUp(2)
        assertEquals(listOf(false, false, true), r.ui.steps.map { it.valveOpen })
    }

    @Test fun `밸브 오류 종류는 없다`() {
        val src = java.io.File("src/main/java/com/timemoredot/app/ui/recipes/RecipeEditViewModel.kt").readText()
        val errors = Regex("""(?:data object|data class)\s+(\w+)\s*(?:\(|:\s*RecipeFieldError)""").findAll(src.substringAfter("sealed interface RecipeFieldError").substringBefore("enum class RecipeEditState")).map { it.groupValues[1] }.toList()
        assertTrue(errors.toString(), errors.size == 9 && errors.none { it.startsWith("Valve") })
    }

    // ---- 스위치만 바꾸는 단계 ----

    @Test fun `하리오 한 단계 뒤에 넣으면 스위치 열기 단계가 생긴다`() = runTest {
        val r = open(null)
        r.fill(steps = listOf(d("240", "0", "10", name = "a")))
        r.vm.onValveEnabledChange(true)
        r.vm.onInsertValveStep(0)
        assertEquals(StepDraft("0:10 스위치 열기", "240", "0", "40", true), r.ui.steps[1])
        assertEquals(listOf(ValveEvent(0, false), ValveEvent(10, true)), r.ui.valvePreview)
        assertTrue(r.ui.hasUnsavedChanges); assertEquals(emptyList<RecipeFieldError>(), r.ui.errors)
    }

    @Test fun `악마 3단계 뒤에 넣으면 가운데 시각이고 뒤 단계가 밀린다`() = runTest {
        val r = open(RFx.devil.id)
        val old = r.ui.steps
        r.vm.onInsertValveStep(3)
        assertEquals(StepDraft("1:25 스위치 열기", "280", "1", "35", true), r.ui.steps[4])
        assertEquals(old[4], r.ui.steps[5]); assertEquals(old[5], r.ui.steps[6])
    }

    @Test fun `하리오 기본 첫 단계 뒤는 1시 05분 열림`() = runTest {
        val r = open(RFx.hario.id)
        r.vm.onInsertValveStep(0)
        assertEquals(StepDraft("0:10 스위치 열기", "240", "1", "05", true), r.ui.steps[1])
        assertEquals(listOf(ValveEvent(0, false), ValveEvent(10, true), ValveEvent(65, false), ValveEvent(120, true)), r.ui.valvePreview)
    }

    @Test fun `간격이 2초 미만이면 시각 칸이 비고 저장은 StepNumberInvalid`() = runTest {
        val r = open(null)
        r.fill(water = "240", steps = listOf(d("240", "0", "10", name = "a"), d("240", "0", "11", name = "b")))
        r.vm.onValveEnabledChange(true)
        r.vm.onInsertValveStep(0)
        assertEquals("", r.ui.steps[1].minutesText); assertEquals("", r.ui.steps[1].secondsText)
        assertEquals(emptyList<ValveEvent>(), r.ui.valvePreview)
        assertEquals(listOf<RecipeFieldError>(RecipeFieldError.StepNumberInvalid(1)), save(r))
        assertTrue(r.repo.upserted.isEmpty())
    }

    @Test fun `앞 단계 시각을 읽을 수 없으면 시각 칸이 비고 이름은 시각 없이`() = runTest {
        val r = open(null)
        r.fill(steps = listOf(StepDraft("a", "240", "", "", false), StepDraft("b", "240", "0", "30", true)))
        r.vm.onValveEnabledChange(true)
        r.vm.onInsertValveStep(0)
        assertEquals(StepDraft("스위치 열기", "240", "", "", true), r.ui.steps[1])
        r.vm.onInsertValveStep(2)   // 앞 단계(b, 열림) 시각은 읽는다 → 닫기, 뒤 단계 없음 → +30 s
        assertEquals(StepDraft("0:30 스위치 닫기", "240", "1", "00", false), r.ui.steps[3])
        val blank = open(null)
        blank.fill(steps = listOf(StepDraft("a", "240", "", "", true))); blank.vm.onValveEnabledChange(true)
        blank.vm.onInsertValveStep(0)
        assertEquals("스위치 닫기", blank.ui.steps[1].name)
    }

    @Test fun `스위치가 꺼져 있거나 범위 밖이면 단계가 바뀌지 않는다`() = runTest {
        val r = open(RFx.hario.id)
        r.vm.onValveEnabledChange(false); r.vm.onInsertValveStep(0)
        assertEquals(RFx.hario.steps.size, r.ui.steps.size)
        r.vm.onValveEnabledChange(true)
        for (bad in listOf(-1, 3, 99)) r.vm.onInsertValveStep(bad)
        assertEquals(RFx.hario.steps.size, r.ui.steps.size); assertFalse(r.ui.hasUnsavedChanges)
    }
}
