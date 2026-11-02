package com.timemoredot.app.ui.history

import com.timemoredot.app.data.CsvExport
import com.timemoredot.app.data.CsvTarget
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.recipes.ListLoad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

/** architecture.md 9.7 HistoryViewModel. FakeBrewRepository와 가짜 CsvTarget, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val store = VmStore()
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private inner class Rig(val scope: TestScope, val repo: FakeBrewRepository = FakeBrewRepository()) {
        val vm = store.create { HistoryViewModel(repo, ZoneOffset.UTC) }
        val events = mutableListOf<ExportEvent>()
        init {
            vm.uiState.launchIn(scope.backgroundScope)
            vm.events.onEach { events += it }.launchIn(scope.backgroundScope)
            scope.runCurrent()
        }
    }

    @Test fun `기록이 없으면 Empty, 있으면 Loaded`() = runTest {
        assertEquals(ListLoad.Empty, Rig(this).vm.uiState.value.sessions)
        val repo = FakeBrewRepository().also { it.seed() }
        assertEquals(1, ((Rig(this, repo).vm.uiState.value.sessions) as ListLoad.Loaded).items.size)
    }

    @Test fun `저장소 예외면 Error, 정상으로 돌아온 뒤 onRetry는 Loaded`() = runTest {
        val repo = FakeBrewRepository().also { it.seed(); it.failNext("observeSessions", IOException("disk")) }
        val rig = Rig(this, repo)
        assertEquals(ListLoad.Error, rig.vm.uiState.value.sessions)
        rig.vm.onRetry(); runCurrent()
        assertTrue(rig.vm.uiState.value.sessions is ListLoad.Loaded)
    }

    @Test fun `Error 뒤 기록이 없는 저장소로 돌아오면 onRetry는 Empty`() = runTest {
        val repo = FakeBrewRepository().also { it.failNext("observeSessions", IOException("disk")) }
        val rig = Rig(this, repo)
        assertEquals(ListLoad.Error, rig.vm.uiState.value.sessions)
        rig.vm.onRetry(); runCurrent()
        assertEquals(ListLoad.Empty, rig.vm.uiState.value.sessions)
    }

    @Test fun `내보내기 성공은 Exported(행 수)이고 쓴 내용이 writeSessions 출력과 같다`() = runTest {
        val repo = FakeBrewRepository().also { r -> r.seed(); r.seed(startedAt = HFx.at(10, 29, 9, 0), recipe = null, note = "쉼표, 따옴표\"") }
        val rig = Rig(this, repo)
        val sb = StringBuilder()
        rig.vm.onExportAll(CsvTarget { block -> block(sb) }); runCurrent()
        val expected = StringBuilder().also { CsvExport.writeSessions(repo.observeSessions().first(), ZoneOffset.UTC, it) }
        assertEquals(listOf<ExportEvent>(ExportEvent.Exported(2)), rig.events)
        assertEquals(expected.toString(), sb.toString())
        assertEquals(ExportState.Idle, rig.vm.uiState.value.export)
    }

    @Test fun `write가 예외를 던지면 ExportFailed이고 Idle로 돌아온다`() = runTest {
        val rig = Rig(this, FakeBrewRepository().also { it.seed() })
        rig.vm.onExportAll(CsvTarget { throw IOException("open failed") }); runCurrent()
        assertEquals(listOf<ExportEvent>(ExportEvent.ExportFailed), rig.events)
        assertEquals(ExportState.Idle, rig.vm.uiState.value.export)
        // 같은 VM에서 새 target으로 다시 하면 성공한다("다시"는 직전 target을 쓰지 않는다)
        rig.vm.onExportAll(CsvTarget { block -> block(StringBuilder()) }); runCurrent()
        assertEquals(ExportEvent.Exported(1), rig.events.last())
    }

    @Test fun `write가 끝나기 전까지 Exporting이고 끝나면 Idle, 그동안 두 번째 호출은 무시한다`() = runTest {
        val rig = Rig(this, FakeBrewRepository().also { it.seed() })
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val target = CsvTarget { block -> writes++; gate.await(); block(StringBuilder()) }
        rig.vm.onExportAll(target); runCurrent()
        assertEquals(ExportState.Exporting, rig.vm.uiState.value.export)
        rig.vm.onExportAll(target); runCurrent()
        assertEquals(1, writes)
        gate.complete(Unit); runCurrent()
        assertEquals(ExportState.Idle, rig.vm.uiState.value.export)
        assertEquals(listOf<ExportEvent>(ExportEvent.Exported(1)), rig.events)
    }

    @Test fun `기록을 읽다 실패해도 ExportFailed`() = runTest {
        val repo = FakeBrewRepository().also { it.seed() }
        val rig = Rig(this, repo)
        repo.failNext("observeSessions", IOException("disk"))
        rig.vm.onExportAll(CsvTarget { block -> block(StringBuilder()) }); runCurrent()
        assertEquals(listOf<ExportEvent>(ExportEvent.ExportFailed), rig.events)
    }
}
