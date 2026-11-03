package com.timemoredot.app

import com.timemoredot.app.data.DefaultRecipes
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.FakePermissionMonitor
import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.ui.brew.BrewViewModel
import com.timemoredot.app.ui.brew.ReadingUi
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.PhaseCause
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.StaleReason
import com.timemoredot.brew.ValveEvent
import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleCommand
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleReading
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.ScaleTimerState
import com.timemoredot.protocol.TimerAction
import com.timemoredot.protocol.WeightUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val DEVICE = ScaleDevice("AA:BB:CC:DD:EE:FF", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)
private val CONNECTED = ConnectionState.Connected(DEVICE)
private val GRAM = ScaleStatus(WeightUnit.Gram, false, 80, null)

/** architecture.md 8.2. FakeScaleClient + FakeBrewRepository + FakeAppPrefs, 가상 시간, Robolectric 없음. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewSessionControllerTest {
    private class Rig(
        val scope: TestScope, val scale: FakeScaleClient, val repo: FakeBrewRepository, val prefs: FakeAppPrefs,
        val c: BrewSessionController, val events: MutableList<BrewEvent>,
    ) {
        val clock = MonotonicClock { scope.testScheduler.currentTime * 1_000_000 }
        val phase get() = c.state.value.phase
        fun starts() = scale.sent.count { it == ScaleCommand.SetTimer(TimerAction.Start) }
        fun timers() = scale.sent.filterIsInstance<ScaleCommand.SetTimer>()

        suspend fun read(w: Double?, unit: WeightUnit = WeightUnit.Gram, atNanos: Long = clock.nowNanos(), flow: Double = 0.0) {
            scale.emit(ScaleReading(w, flow, 0, w == null, unit, atNanos))
            scope.runCurrent()
        }

        /** 10 Hz로 seconds초 동안 같은 무게를 보낸다. */
        suspend fun readFor(seconds: Double, w: Double) {
            repeat((seconds * 10).toInt()) { scope.advanceTimeBy(100); read(w) }
        }
    }

    private fun TestScope.rig(
        initial: ConnectionState = CONNECTED, status: ScaleStatus = GRAM, settings: BrewSettings = BrewSettings(),
        capacity: Int = 36_000,
    ): Rig {
        val scale = FakeScaleClient(initial).also { it.setStatus(status) }
        val repo = FakeBrewRepository()
        val prefs = FakeAppPrefs(settings)
        val clock = MonotonicClock { testScheduler.currentTime * 1_000_000 }
        val c = BrewSessionController(scale, repo, prefs, clock, { 1_700_000_000_000L }, backgroundScope, sampleCapacity = capacity)
        val events = mutableListOf<BrewEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.events.collect { events += it } }
        runCurrent()
        return Rig(this, scale, repo, prefs, c, events)
    }

    private fun TestScope.running(r: Rig) { r.c.startTimer(); runCurrent(); assertEquals(BrewPhase.Running, r.phase) }

    // ── 영점·자동 시작 ──

    @Test fun `tare 중 tareInFlight, 성공이면 WaitingForPour와 TareSucceeded`() = runTest {
        val r = rig()
        r.c.tare()
        assertTrue(r.c.tareInFlight.value)
        r.c.tare()   // 대기 중 다시: 보내지 않는다
        runCurrent()
        assertEquals(1, r.scale.sent.count { it == ScaleCommand.Tare })
        assertFalse(r.c.tareInFlight.value)
        assertEquals(BrewPhase.WaitingForPour, r.phase)
        assertTrue(BrewEvent.TareSucceeded in r.events)
    }

    @Test fun `Tare Rejected와 Timeout은 TareFailed`() = runTest {
        val r = rig()
        r.scale.respondWith(ScaleCommand.Tare::class, CommandResult.Failed(CommandFailure.Rejected))
        r.c.tare(); runCurrent()
        r.scale.respondWith(ScaleCommand.Tare::class, CommandResult.Failed(CommandFailure.Timeout))
        r.c.tare(); runCurrent()
        assertEquals(listOf(BrewEvent.TareFailed(CommandFailure.Rejected), BrewEvent.TareFailed(CommandFailure.Timeout)), r.events)
        assertEquals(BrewPhase.Idle, r.phase)
    }

    @Test fun `자동 시작이면 SetTimer(Start)가 한 번, 실패면 ScaleTimerSyncFailed이고 Running 유지`() = runTest {
        val r = rig()
        r.c.tare(); runCurrent()
        advanceTimeBy(100); r.read(0.0)   // 무장
        advanceTimeBy(100); r.read(1.5)
        r.scale.respondWith(ScaleCommand.SetTimer::class, CommandResult.Failed(CommandFailure.Timeout))
        for (k in 1..5) { advanceTimeBy(100); r.read(1.5 + 0.3 * k) }   // 확인 구간 500 ms(F-B4-1)
        runCurrent()
        assertEquals(BrewPhase.Running, r.phase)
        assertEquals(1, r.starts())
        assertTrue(BrewEvent.ScaleTimerSyncFailed(CommandFailure.Timeout) in r.events)
        assertTrue(BrewEvent.PhaseChanged(BrewPhase.WaitingForPour, BrewPhase.Running, PhaseCause.AutoStart) in r.events)
    }

    private suspend fun TestScope.preweightSequence(w: Double) {
        val r = rig()
        r.c.tare(); runCurrent()
        val taredAt = r.clock.nowNanos()
        r.read(w, atNanos = taredAt - 50_000_000)   // 응답과 같은 알림의 영점 전 무게(Tared보다 이른 시각)
        advanceTimeBy(100); r.read(w)
        advanceTimeBy(100); r.read(w)
        advanceTimeBy(100); r.read(0.0)
        assertEquals(BrewPhase.WaitingForPour, r.phase)
        assertEquals(0, r.starts())
        for (k in 0..5) { advanceTimeBy(100); r.read(1.2 + 0.2 * k) }   // 1.2 g부터 확인 구간 500 ms(F-B4-1)
        runCurrent()
        assertEquals(BrewPhase.Running, r.phase)
        assertEquals(1, r.starts())
    }

    @Test fun `실측 영점 순서 213_4 g`() = runTest { preweightSequence(213.4) }
    @Test fun `실측 영점 순서 145_0 g (rx-tare-preweight-seq-001)`() = runTest { preweightSequence(145.0) }

    // ── 저장 ──

    @Test fun `Running 25 s 동안 saveDraft 2회, finish 뒤 확정 저장과 Finished`() = runTest {
        val r = rig()
        running(r)
        r.readFor(25.0, 10.0)
        assertEquals(2, r.repo.calls.count { it == "saveDraft" })
        val id = r.c.draftId.value!!
        assertEquals(1L, id)
        r.c.finish(); runCurrent()
        assertEquals(3, r.repo.calls.count { it == "saveDraft" })
        assertEquals(id, r.repo.drafts.last().id)
        assertTrue(BrewEvent.Finished(id) in r.events)
    }

    @Test fun `discard면 deleteSession(id), Idle, draftId null`() = runTest {
        val r = rig()
        running(r)
        r.readFor(11.0, 10.0)
        val id = r.c.draftId.value!!
        r.c.discard(); runCurrent()
        assertEquals(listOf(id), r.repo.deletedIds)
        assertEquals(BrewPhase.Idle, r.phase)
        assertNull(r.c.draftId.value)
    }

    @Test fun `sampleCapacity 5면 6번째 Reading에서 CapReached로 끝난다`() = runTest {
        val r = rig(capacity = 5)
        running(r)
        repeat(5) { advanceTimeBy(100); r.read(1.0 + it) }
        assertEquals(BrewPhase.Running, r.phase)
        advanceTimeBy(100); r.read(9.0)
        assertEquals(BrewPhase.Finished, r.phase)
        assertTrue(BrewEvent.PhaseChanged(BrewPhase.Running, BrewPhase.Finished, PhaseCause.CapReached) in r.events)
    }

    @Test fun `주기 저장 실패는 크래시·이벤트 없이 10 s 뒤 다시`() = runTest {
        val r = rig()
        running(r)
        r.events.clear()
        r.repo.failNext("saveDraft", RuntimeException("disk"))
        r.readFor(10.0, 5.0)
        assertEquals(1, r.repo.calls.count { it == "saveDraft" })
        assertTrue(r.events.isEmpty())
        r.readFor(10.0, 5.0)
        assertEquals(2, r.repo.calls.count { it == "saveDraft" })
        assertEquals(1L, r.c.draftId.value)
    }

    @Test fun `확정 저장 실패면 SaveFailed, retrySave 성공이면 Finished`() = runTest {
        val r = rig()
        running(r)
        r.readFor(1.0, 5.0)
        r.repo.failNext("saveDraft", RuntimeException("disk"))
        r.c.finish(); runCurrent()
        assertEquals(1, r.events.count { it == BrewEvent.SaveFailed })
        assertTrue(r.events.none { it is BrewEvent.Finished })
        assertEquals(BrewPhase.Finished, r.phase)
        assertTrue(r.c.saveFailed.value)
        // Finished에서 부르는 버리기 API는 없다: discard()는 엔진이 무시하고 삭제도 없다(9.8 15절 9)
        r.c.discard(); runCurrent()
        assertEquals(BrewPhase.Finished, r.phase)
        assertTrue(r.repo.deletedIds.isEmpty())
        r.repo.failNext("saveDraft", RuntimeException("disk"))
        r.c.retrySave()
        assertFalse(r.c.saveFailed.value)
        runCurrent()
        assertEquals(2, r.events.count { it == BrewEvent.SaveFailed })
        assertTrue(r.c.saveFailed.value)
        r.c.retrySave(); runCurrent()
        assertFalse(r.c.saveFailed.value)
        assertTrue(r.events.any { it is BrewEvent.Finished })
        assertTrue(BrewSessionController::class.java.declaredMethods.none { it.name.contains("discardFinished", ignoreCase = true) })
    }

    @Test fun `discard 뒤 deleteSession 예외여도 Idle, draftId null`() = runTest {
        val r = rig()
        running(r)
        r.readFor(11.0, 5.0)
        r.repo.failNext("deleteSession", RuntimeException("disk"))
        r.c.discard(); runCurrent()
        assertEquals(BrewPhase.Idle, r.phase)
        assertNull(r.c.draftId.value)
    }

    @Test fun `주기 저장이 진행 중이면 finish는 그 저장이 끝난 뒤 확정 저장한다`() = runTest {
        val r = rig()
        running(r)
        val gate = CompletableDeferred<Unit>()
        r.repo.saveDraftGate = gate   // 다음 saveDraft 하나가 멈춘다(첫 주기 저장, draftId 없음)
        r.readFor(10.0, 5.0)
        assertEquals(1, r.repo.calls.count { it == "saveDraft" })
        r.c.finish(); runCurrent()
        assertEquals(listOf("saveDraft"), r.repo.calls)   // DB 호출 중인 주기 저장은 끊지 않고 기다린다
        gate.complete(Unit); runCurrent()
        // 주기 저장이 만든 행(id 1)을 확정 저장이 이어 쓴다: 고아 행·중복 행 없음
        assertEquals(listOf("saveDraft", "saveDraft"), r.repo.calls)
        assertEquals(1L, r.repo.drafts.last().id)
        assertTrue(BrewEvent.Finished(1L) in r.events)
    }

    @Test fun `첫 주기 저장이 진행 중일 때 discard하면 그 저장이 만든 행을 지운다`() = runTest {
        val r = rig()
        running(r)
        val gate = CompletableDeferred<Unit>()
        r.repo.saveDraftGate = gate
        r.readFor(10.0, 5.0)   // 첫 주기 저장(draftId 없음)이 멈춤
        r.c.discard(); runCurrent()
        assertNull(r.c.draftId.value)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("saveDraft", "deleteSession"), r.repo.calls)
        assertEquals(listOf(1L), r.repo.deletedIds)
        assertNull(r.c.draftId.value)
    }

    @Test fun `주기 저장이 진행 중이면 discard는 그 저장이 끝난 뒤 deleteSession`() = runTest {
        val r = rig()
        running(r)
        r.readFor(10.0, 5.0)   // 첫 주기 저장 성공 → draftId
        val id = r.c.draftId.value!!
        val gate = CompletableDeferred<Unit>()
        r.repo.saveDraftGate = gate
        r.readFor(10.0, 5.0)   // 두 번째 주기 저장이 멈춤
        r.c.discard(); runCurrent()
        assertEquals(listOf("saveDraft", "saveDraft"), r.repo.calls)   // 저장이 끝나기 전에는 지우지 않는다
        gate.complete(Unit); runCurrent()
        assertEquals(listOf("saveDraft", "saveDraft", "deleteSession"), r.repo.calls)
        assertEquals(listOf(id), r.repo.deletedIds)
        r.readFor(15.0, 5.0)
        assertEquals("deleteSession", r.repo.calls.last())   // 삭제 뒤 행을 되살리는 저장 없음
    }

    // ── 타이머 상한 ──

    @Test fun `Reading 없이 Tick만으로 3599_9 s에 닿으면 CapReached와 Finished, durationMs 3_599_900`() = runTest {
        val r = rig()
        running(r)
        r.scale.setConnection(ConnectionState.Reconnecting(DEVICE.address, 1, 0)); runCurrent()
        advanceTimeBy(3_600_000); runCurrent()
        assertTrue(BrewEvent.PhaseChanged(BrewPhase.Running, BrewPhase.Finished, PhaseCause.CapReached) in r.events)
        assertTrue(r.events.any { it is BrewEvent.Finished })
        assertEquals(3_599_900L, r.repo.drafts.last().durationMs)
    }

    // ── 저울 타이머 미상·재연결 ──

    private fun TestScope.reconnect(r: Rig) {
        r.scale.setConnection(ConnectionState.Reconnecting(DEVICE.address, 1, 0)); runCurrent()
        r.scale.setStatus(ScaleStatus.Unknown); runCurrent()
        r.scale.setConnection(CONNECTED); runCurrent()
    }

    @Test fun `재연결 뒤 scaleTimer가 4 s 동안 null이면 Running에서 SetTimer(Start) 한 번`() = runTest {
        val r = rig()
        running(r)
        reconnect(r)
        val before = r.starts()
        advanceTimeBy(3_900); runCurrent()
        assertEquals(before, r.starts())
        advanceTimeBy(200); runCurrent()
        assertEquals(before + 1, r.starts())
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Reset)); runCurrent()   // 기한 뒤 늦은 응답
        advanceTimeBy(10_000); runCurrent()
        assertEquals(before + 1, r.starts())
    }

    @Test fun `저울 타이머 미상이어도 Paused면 보내지 않는다`() = runTest {
        val r = rig()
        running(r)
        r.c.pauseTimer(); runCurrent()
        reconnect(r)
        val before = r.timers().size
        advanceTimeBy(5_000); runCurrent()
        assertEquals(before, r.timers().size)
        assertEquals(BrewPhase.Paused, r.phase)
    }

    @Test fun `기한 전에 Reset이 오면 SetTimer(Start) 한 번이고 기한에 다시 보내지 않는다`() = runTest {
        val r = rig()
        running(r)
        reconnect(r)
        val before = r.starts()
        advanceTimeBy(1_000)
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Reset)); runCurrent()
        assertEquals(before + 1, r.starts())
        advanceTimeBy(10_000); runCurrent()
        assertEquals(before + 1, r.starts())
    }

    @Test fun `재연결 뒤 ReadTimer Reset이어도 Running 유지, Finished 없음, SetTimer(Start) 하나 더`() = runTest {
        val r = rig()
        running(r)
        r.readFor(2.0, 20.0)
        val before = r.starts()
        reconnect(r)
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Reset)); runCurrent()
        assertEquals(BrewPhase.Running, r.phase)
        assertTrue(r.events.none { it is BrewEvent.Finished || (it is BrewEvent.PhaseChanged && it.to == BrewPhase.Finished) })
        assertEquals(before + 1, r.starts())
    }

    @Test fun `끊긴 동안 일시정지(SetTimer 실패) 뒤 재연결해 저울이 Running이면 Paused 유지하고 SetTimer(Pause)`() = runTest {
        val r = rig()
        running(r)
        r.scale.setConnection(ConnectionState.Reconnecting(DEVICE.address, 1, 0)); runCurrent()
        r.scale.setStatus(ScaleStatus.Unknown)
        r.scale.respondWith(ScaleCommand.SetTimer::class, CommandResult.Failed(CommandFailure.NotConnected))
        r.c.pauseTimer(); runCurrent()
        assertTrue(BrewEvent.ScaleTimerSyncFailed(CommandFailure.NotConnected) in r.events)
        r.scale.respondWith(ScaleCommand.SetTimer::class, CommandResult.Success)
        val pauses = r.scale.sent.count { it == ScaleCommand.SetTimer(TimerAction.Pause) }
        r.scale.setConnection(CONNECTED); runCurrent()
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Running)); runCurrent()
        assertEquals(BrewPhase.Paused, r.phase)
        assertEquals(pauses + 1, r.scale.sent.count { it == ScaleCommand.SetTimer(TimerAction.Pause) })
    }

    @Test fun `Idle에서 첫 연결의 scaleTimer Running은 phase를 바꾸지 않고 보내지 않는다`() = runTest {
        val r = rig(initial = ConnectionState.Idle, status = ScaleStatus.Unknown)
        r.scale.setConnection(CONNECTED); runCurrent()
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Running)); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(BrewPhase.Idle, r.phase)
        assertTrue(r.scale.sent.isEmpty())
    }

    // ── 원문 경로·Write 응답 ──

    @Test fun `emitTimerNotify(Paused)면 Paused, SetTimer 없음, scaleTimer만 바뀌면 phase 그대로`() = runTest {
        val r = rig()
        running(r)
        val before = r.timers().size
        r.scale.emitTimerNotify(ScaleTimerState.Paused); runCurrent()
        assertEquals(BrewPhase.Paused, r.phase)
        assertEquals(before, r.timers().size)
        r.scale.setStatus(GRAM.copy(scaleTimer = ScaleTimerState.Running)); runCurrent()   // Read 응답 흉내
        assertEquals(BrewPhase.Paused, r.phase)
    }

    @Test fun `Idle에서 설정 바꾸면 컨트롤러 설정에 반영된다`() = runTest {
        val r = rig()
        r.prefs.setBrewSettings(BrewSettings(autoStartThresholdGrams = 2.0)); runCurrent()
        assertEquals(2.0, r.c.state.value.settings.autoStartThresholdGrams, 0.0)
    }

    @Test fun `Write 응답 성공만으로 Running, Notify 메아리는 무시`() = runTest {
        val r = rig()
        r.c.startTimer(); runCurrent()
        assertEquals(BrewPhase.Running, r.phase)
        assertEquals(ScaleTimerState.Running, r.scale.status.value.scaleTimer)
        assertEquals(listOf(ScaleCommand.SetTimer(TimerAction.Start)), r.scale.sent)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(ScaleCommand.SetTimer(TimerAction.Start)), r.scale.sent)
        r.scale.emitTimerNotify(ScaleTimerState.Running); runCurrent()
        assertEquals(BrewPhase.Running, r.phase)
        assertEquals(listOf(ScaleCommand.SetTimer(TimerAction.Start)), r.scale.sent)
    }

    @Test fun `startTimer 직후 응답 전 discard면 Idle, sent는 Start·Reset`() = runTest {
        val r = rig()
        r.c.startTimer()
        r.c.discard()
        runCurrent()
        assertEquals(BrewPhase.Idle, r.phase)
        assertEquals(listOf(ScaleCommand.SetTimer(TimerAction.Start), ScaleCommand.SetTimer(TimerAction.Reset)), r.scale.sent)
        assertTrue(r.events.none { it is BrewEvent.Finished })
    }

    // ── lastUnit ──

    @Test fun `oz 뒤 끊기면 lastUnit Ounce, VM displayUnit Ounce, readout Stale(unit Ounce)`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = com.timemoredot.app.fake.VmStore()
        try {
            val r = rig(status = ScaleStatus(WeightUnit.Ounce, false, 80, null))
            val vm = store.create { BrewViewModel(r.c, r.scale, FakeRecipeRepository(), r.repo, r.prefs, FakePermissionMonitor(), r.clock) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.readout.collect {} }
            runCurrent()
            r.read(56.7, unit = WeightUnit.Ounce)
            r.scale.setConnection(ConnectionState.Reconnecting(DEVICE.address, 1, 0)); runCurrent()
            r.scale.setStatus(ScaleStatus.Unknown); runCurrent()
            assertEquals(WeightUnit.Ounce, r.c.lastUnit.value)
            assertEquals(WeightUnit.Ounce, vm.uiState.value.displayUnit)
            val reading = vm.readout.value.reading as ReadingUi.Stale
            assertEquals(WeightUnit.Ounce, reading.unit)
            assertEquals(StaleReason.Disconnected, reading.cause)
        } finally { store.clear(); runCurrent(); Dispatchers.resetMain() }
    }

    // ── 밸브 ──

    private suspend fun TestScope.valveCues(recipe: Recipe?, seconds: Int): List<BrewEvent.ValveCue> {
        val r = rig()
        r.c.selectRecipe(recipe); runCurrent()
        running(r)
        r.readFor(seconds.toDouble(), 5.0)
        return r.events.filterIsInstance<BrewEvent.ValveCue>()
    }

    private fun recipe(name: String) = DefaultRecipes.all.first { it.name == name }

    @Test fun `하리오 스위치 기본은 120 s에 열기 신호 한 번`() = runTest {
        assertEquals(listOf(BrewEvent.ValveCue(ValveEvent(120, true))), valveCues(recipe("하리오 스위치 기본"), 125))
    }

    @Test fun `악마의 레시피는 75 s·105 s에 한 번씩`() = runTest {
        assertEquals(
            listOf(BrewEvent.ValveCue(ValveEvent(75, false)), BrewEvent.ValveCue(ValveEvent(105, true))),
            valveCues(recipe("테츠 카스야 악마의 레시피"), 110),
        )
    }

    @Test fun `레시피 없음은 밸브 신호 0건`() = runTest {
        assertTrue(valveCues(null, 30).isEmpty())
    }
}

