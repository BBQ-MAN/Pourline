package com.timemoredot.app.ui.history

import com.timemoredot.app.data.CsvExport
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.brew.Fx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** architecture.md 9.7 SessionDetailViewModel. FakeBrewRepository와 가짜 CsvTarget, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
    private val store = VmStore()
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private inner class Rig(val scope: TestScope, val repo: FakeBrewRepository, val id: Long) {
        val vm = store.create { SessionDetailViewModel(repo, id, scope) }   // appScope = TestScope
        val events = mutableListOf<SessionDetailEvent>()
        init {
            vm.uiState.launchIn(scope.backgroundScope)
            vm.events.onEach { events += it }.launchIn(scope.backgroundScope)
            scope.runCurrent()
        }
        val ui get() = vm.uiState.value
    }

    @Test fun `없는 id면 NotFound`() = runTest {
        assertEquals(DetailState.NotFound, Rig(this, FakeBrewRepository(), 99).ui.state)
    }

    @Test fun `samples가 null이면 NoSamples이고 단계 표는 실제 도달 없이 단계 수만큼 있다`() = runTest {
        val repo = FakeBrewRepository()
        val id = repo.seed()
        repo.failNext("samples", IOException("corrupt"))
        val r = Rig(this, repo, id)
        assertEquals(DetailState.NoSamples, r.ui.state)
        assertNull(r.ui.samples)
        assertEquals(List(Fx.v60.steps.size) { null }, r.ui.stepReachTimesMs)
        assertEquals(Fx.v60.name, r.ui.summary?.recipeName)
    }

    @Test fun `레시피가 있으면 stepReachTimesMs 길이가 단계 수와 같고 레시피가 없으면 빈 목록`() = runTest {
        val repo = FakeBrewRepository()
        val devil = Rig(this, repo, repo.seed(recipe = Fx.devil, final = 280.0))
        assertEquals(DetailState.Loaded, devil.ui.state)
        assertEquals(Fx.devil.steps.size, devil.ui.stepReachTimesMs.size)
        assertTrue(devil.ui.stepReachTimesMs.first() != null)
        val free = Rig(this, repo, repo.seed(recipe = null))
        assertEquals(emptyList<Int?>(), free.ui.stepReachTimesMs)
    }

    @Test fun `onNoteChange를 연달아 부르면 500 ms 뒤 마지막 값으로 updateNote 한 번`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onNoteChange("a"); advanceTimeBy(200)
        r.vm.onNoteChange("ab"); advanceTimeBy(200)
        r.vm.onNoteChange("abc"); advanceTimeBy(499); runCurrent()
        assertEquals(emptyList<Pair<Long, String>>(), repo.noteUpdates)
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(r.id to "abc"), repo.noteUpdates)
    }

    @Test fun `onDeleteConfirmed 뒤 Deleted 이벤트`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onDeleteConfirmed(); runCurrent()
        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Deleted), r.events)
        assertEquals(listOf(r.id), repo.deletedIds)
    }

    @Test fun `삭제가 실패하면 DeleteFailed이고 다시 삭제할 수 있다`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        repo.failNext("deleteSession", IOException("disk"))
        r.vm.onDeleteConfirmed(); runCurrent()
        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.DeleteFailed), r.events)
        r.vm.onDeleteConfirmed(); runCurrent()
        assertEquals(SessionDetailEvent.Deleted, r.events.last())
    }

    @Test fun `삭제가 실패하면 대기 중이던 메모를 다시 저장한다`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onNoteChange("abc"); advanceTimeBy(100)
        repo.failNext("deleteSession", IOException("disk"))
        r.vm.onDeleteConfirmed(); runCurrent()
        advanceTimeBy(600); runCurrent()
        assertEquals(listOf(r.id to "abc"), repo.noteUpdates)
    }

    // ---- 떠날 때 메모 저장 ----

    @Test fun `디바운스 대기 중 VM이 지워지면 appScope에서 마지막 값으로 updateNote 한 번`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onNoteChange("abc"); advanceTimeBy(100); runCurrent()
        store.clear()
        advanceUntilIdle()
        assertEquals(listOf(r.id to "abc"), repo.noteUpdates)
    }

    @Test fun `디바운스 저장이 끝났으면 clear 뒤 추가 호출이 없다`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onNoteChange("abc"); advanceTimeBy(600); runCurrent()
        assertEquals(1, repo.noteUpdates.size)
        store.clear(); advanceUntilIdle()
        assertEquals(1, repo.noteUpdates.size)
    }

    @Test fun `삭제 뒤 clear는 updateNote를 부르지 않는다`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onNoteChange("abc")
        r.vm.onDeleteConfirmed(); runCurrent()
        store.clear(); advanceUntilIdle()
        assertEquals(emptyList<Pair<Long, String>>(), repo.noteUpdates)
    }

    // ---- CSV ----

    @Test fun `샘플 내보내기 성공은 Export(Exported)이고 쓴 내용이 writeSamples 출력과 같다`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        val sb = StringBuilder()
        r.vm.onExportSamples(CsvTarget { block -> block(sb) }); runCurrent()
        val expected = StringBuilder().also { CsvExport.writeSamples(r.ui.samples!!, it) }
        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Export(ExportEvent.Exported(r.ui.samples!!.size))), r.events)
        assertEquals(expected.toString(), sb.toString())
        assertEquals(ExportState.Idle, r.ui.export)
    }

    @Test fun `샘플 내보내기 write 예외는 Export(ExportFailed)`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        r.vm.onExportSamples(CsvTarget { throw IOException("open failed") }); runCurrent()
        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Export(ExportEvent.ExportFailed)), r.events)
        assertEquals(ExportState.Idle, r.ui.export)
    }

    @Test fun `write가 끝나기 전까지 Exporting`() = runTest {
        val repo = FakeBrewRepository()
        val r = Rig(this, repo, repo.seed())
        val gate = CompletableDeferred<Unit>()
        r.vm.onExportSamples(CsvTarget { block -> gate.await(); block(StringBuilder()) }); runCurrent()
        assertEquals(ExportState.Exporting, r.ui.export)
        gate.complete(Unit); runCurrent()
        assertEquals(ExportState.Idle, r.ui.export)
    }
}
