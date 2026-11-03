package com.timemoredot.app.ui.brew

import app.cash.turbine.test
import com.timemoredot.app.BrewSessionController
import com.timemoredot.app.data.DefaultRecipes
import com.timemoredot.app.fake.FakeAppPrefs
import com.timemoredot.app.fake.FakeBrewRepository
import com.timemoredot.app.fake.FakePermissionMonitor
import com.timemoredot.app.fake.FakeRecipeRepository
import com.timemoredot.app.fake.VmStore
import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.brew.BrewPhase
import com.timemoredot.brew.BrewSettings
import com.timemoredot.brew.PhaseCause
import com.timemoredot.brew.Recipe
import com.timemoredot.brew.StaleReason
import com.timemoredot.protocol.CommandFailure
import com.timemoredot.protocol.CommandResult
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.MonotonicClock
import com.timemoredot.protocol.ScaleClient
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val DEVICE = ScaleDevice("AA:BB:CC:DD:EE:FF", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)
private val CONNECTED = ConnectionState.Connected(DEVICE)
private val GRAM = ScaleStatus(WeightUnit.Gram, false, 80, null)

/** Tare 응답을 테스트가 풀 때까지 멈추는 저울(대기 중 상태 확인용). */
private class GatedScale(val fake: FakeScaleClient) : ScaleClient by fake {
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun send(command: ScaleCommand): CommandResult {
        if (command == ScaleCommand.Tare) gate?.await()
        return fake.send(command)
    }
}

/** architecture.md 9.4. 가짜 저장소·가짜 prefs·FakePermissionMonitor, 가상 시간. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModelTest {
    private val store = VmStore()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    private inner class Rig(val scope: TestScope, initial: ConnectionState, status: ScaleStatus, settings: BrewSettings, lastDevice: String?) {
        val fake = FakeScaleClient(initial).also { it.setStatus(status); it.setLastDevice(lastDevice) }
        val scale = GatedScale(fake)
        val repo = FakeBrewRepository()
        val prefs = FakeAppPrefs(settings)
        val permission = FakePermissionMonitor(scale = fake)
        val recipes = FakeRecipeRepository(DefaultRecipes.all.mapIndexed { i, r -> r.copy(id = i + 1L) })
        val clock = MonotonicClock { scope.testScheduler.currentTime * 1_000_000 }
        val controller = BrewSessionController(scale, repo, prefs, clock, { 0L }, scope.backgroundScope)
        val events = mutableListOf<BrewUiEvent>()
        lateinit var vm: BrewViewModel
        val ui get() = vm.uiState.value
        val phase get() = controller.state.value.phase
        fun announces() = events.filterIsInstance<BrewUiEvent.Announce>().map { it.announcement }

        fun newVm(): BrewViewModel {
            vm = store.create { BrewViewModel(controller, scale, recipes, repo, prefs, permission, clock) }
            val v = vm
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { v.uiState.collect {} }
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { v.readout.collect {} }
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { v.events.collect { events += it } }
            scope.runCurrent()
            return v
        }

        suspend fun read(w: Double?, unit: WeightUnit = WeightUnit.Gram, overload: Boolean = false) {
            fake.emit(ScaleReading(w, 0.0, 0, overload, unit, clock.nowNanos()))
            scope.runCurrent()
        }
        fun conn(s: ConnectionState) { fake.setConnection(s); scope.runCurrent() }
        fun run() { vm.onStartClick(); scope.runCurrent() }
    }

    private fun TestScope.rig(
        initial: ConnectionState = CONNECTED, status: ScaleStatus = GRAM, settings: BrewSettings = BrewSettings(), lastDevice: String? = DEVICE.address,
    ): Rig = Rig(this, initial, status, settings, lastDevice).also { runCurrent(); it.newVm() }

    // ── 고빈도 분리 ──

    @Test fun `10 Hz 측정값은 readout만 바꾸고 uiState는 방출하지 않는다`() = runTest {
        val r = rig()
        r.read(1.0)
        r.vm.uiState.test {
            awaitItem()
            repeat(10) { advanceTimeBy(100); r.read(2.0 + it) }
            expectNoEvents()
        }
        assertEquals(11.0, (r.vm.readout.value.reading as ReadingUi.Live).weightGrams, 0.0)
    }

    // ── 배너 ──

    @Test fun `배너 우선순위`() {
        val p = BrewPhase.Idle
        assertEquals(BrewBanner.BluetoothOff, rawBanner(ConnectionUi.BluetoothOff, p, StaleReason.Overload, GRAM, false, false))
        assertTrue(BrewBanner.LocationOff.ordinal == BrewBanner.PermissionPermanentlyDenied.ordinal + 1)
        assertEquals(BrewBanner.ScanFailed, rawBanner(ConnectionUi.ScanFailed, p, null, GRAM, false, false))
        assertEquals(BrewBanner.Disconnected, rawBanner(ConnectionUi.Disconnected, BrewPhase.Running, StaleReason.Disconnected, GRAM, false, false))
        assertEquals(BrewBanner.Disconnected, rawBanner(ConnectionUi.Disconnected, BrewPhase.Paused, StaleReason.Disconnected, GRAM, false, false))
        assertNotEquals(BrewBanner.Disconnected, rawBanner(ConnectionUi.Disconnected, BrewPhase.Idle, StaleReason.Disconnected, GRAM, false, false))
        assertTrue(BrewBanner.Overload < BrewBanner.UnitUnknownTimedOut && BrewBanner.UnitUnknownTimedOut < BrewBanner.NoData)
    }

    @Test fun `ScanFailed 연결 상태면 banner ScanFailed`() = runTest {
        val r = rig(initial = ConnectionState.Failed(FailureReason.ScanFailed))
        assertEquals(BrewBanner.ScanFailed, r.ui.banner)
    }

    @Test fun `모델 경고를 닫으면 같은 주소에서 다시 나오지 않는다`() = runTest {
        val odd = DEVICE.copy(modelNumber = "XYZ")
        val r = rig(initial = ConnectionState.Connected(odd))
        r.read(1.0)
        assertEquals(BrewBanner.ModelMismatch, r.ui.banner)
        r.vm.onModelWarningDismissed(); runCurrent()
        assertNull(r.ui.banner)
        r.newVm()
        assertNotEquals(BrewBanner.ModelMismatch, r.ui.banner)
    }

    @Test fun `단위 미상이면 canStart false, 단위 알면 true, 단위 미상 NoData는 NoData 배너가 아니다`() = runTest {
        val r = rig(status = ScaleStatus(null, false, 80, null))
        assertFalse(r.ui.canStart)
        advanceTimeBy(4_000); runCurrent()   // 엔진 NoData
        assertNotEquals(BrewBanner.NoData, r.ui.banner)
        r.fake.setStatus(ScaleStatus(null, true, 80, null)); runCurrent()
        assertFalse(r.ui.canStart)
        assertEquals(BrewBanner.UnitUnknownTimedOut, r.ui.banner)
        r.fake.setStatus(GRAM); runCurrent()
        assertTrue(r.ui.canStart)
    }

    @Test fun `NoData 배너는 풀린 뒤 2 s 유지, 그 안에 더 높은 배너가 이긴다`() = runTest {
        val r = rig()
        r.read(1.0)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(BrewBanner.NoData, r.ui.banner)
        r.read(1.0)
        advanceTimeBy(1_900); runCurrent()
        assertEquals(BrewBanner.NoData, r.ui.banner)
        advanceTimeBy(200); runCurrent()
        assertNotEquals(BrewBanner.NoData, r.ui.banner)
    }

    @Test fun `NoData 유지 중 BluetoothOff면 바로 BluetoothOff`() = runTest {
        val r = rig()
        r.read(1.0)
        advanceTimeBy(4_000); runCurrent()
        r.read(1.0)
        advanceTimeBy(500); runCurrent()
        assertEquals(BrewBanner.NoData, r.ui.banner)
        r.conn(ConnectionState.BluetoothOff)
        assertEquals(BrewBanner.BluetoothOff, r.ui.banner)
    }

    @Test fun `NoData 유지 중 단위 읽기 실패면 바로 UnitUnknownTimedOut`() = runTest {
        val r = rig()
        r.read(1.0)
        advanceTimeBy(4_000); runCurrent()
        r.read(1.0)
        advanceTimeBy(500); runCurrent()
        r.fake.setStatus(ScaleStatus(null, true, 80, null)); runCurrent()
        assertEquals(BrewBanner.UnitUnknownTimedOut, r.ui.banner)
    }

    @Test fun `과부하 배너를 접으면 과부하가 이어지는 동안 안 나오고, 풀렸다 다시 오면 나온다`() = runTest {
        val r = rig()
        r.read(10.0)
        r.read(null, overload = true)
        assertEquals(BrewBanner.Overload, r.ui.banner)
        r.vm.onOverloadBannerDismissed(); runCurrent()
        r.read(null, overload = true)
        assertNotEquals(BrewBanner.Overload, r.ui.banner)
        r.read(10.0)
        r.read(null, overload = true)
        assertEquals(BrewBanner.Overload, r.ui.banner)
        assertEquals(listOf(BrewAnnouncement.Overload, BrewAnnouncement.OverloadCleared, BrewAnnouncement.Overload), r.announces())
    }

    // ── 동작 가능 여부 ──

    @Test fun `레시피·설정 변경과 다른 저울 찾기는 Idle·WaitingForPour에서만`() = runTest {
        val r = rig()
        r.read(0.5)
        assertTrue(r.ui.canChangeRecipeOrSettings && r.ui.canFindOtherScale)
        r.vm.onTareClick(); runCurrent()
        assertEquals(BrewPhase.WaitingForPour, r.ui.phase)
        assertTrue(r.ui.canChangeRecipeOrSettings && r.ui.canFindOtherScale)
        r.vm.onFindOtherScaleClick(); runCurrent()
        assertEquals(listOf("disconnect"), r.fake.calls.filter { it == "disconnect" })
        assertTrue(BrewUiEvent.OpenConnect in r.events)
        r.fake.setConnection(CONNECTED); runCurrent()
        r.run()
        assertFalse(r.ui.canChangeRecipeOrSettings || r.ui.canFindOtherScale)
        assertTrue(r.ui.keepScreenOn); assertFalse(r.ui.showBottomBar)
        val before = r.fake.calls.size
        r.vm.onFindOtherScaleClick(); runCurrent()
        assertEquals(before, r.fake.calls.size)
        r.vm.onPauseClick(); runCurrent()
        assertFalse(r.ui.canChangeRecipeOrSettings || r.ui.canFindOtherScale)
        assertTrue(r.ui.keepScreenOn)
        r.vm.onFinishClick(); runCurrent()
        assertEquals(BrewPhase.Finished, r.ui.phase)
        assertFalse(r.ui.canChangeRecipeOrSettings || r.ui.canFindOtherScale || r.ui.keepScreenOn)
        r.conn(ConnectionState.Failed(FailureReason.ReconnectExhausted))
        r.vm.onFindOtherScaleClick(); runCurrent()
        assertEquals(1, r.fake.calls.count { it == "disconnect" })
        assertEquals(1, r.events.count { it == BrewUiEvent.OpenConnect })
    }

    @Test fun `Running 중 onRecipeSelected는 무시된다`() = runTest {
        val r = rig()
        r.run()
        r.vm.onRecipeSelected(1); runCurrent()
        assertNull(r.ui.recipe)
    }

    @Test fun `영점 - Running·Paused에서 canTare false, 대기 중에도 canTare true이고 두 번째 누름은 보내지 않는다`() = runTest {
        val r = rig()
        r.read(0.0)
        assertTrue(r.ui.canTare)
        r.scale.gate = CompletableDeferred()
        r.vm.onTareClick(); runCurrent()
        assertTrue(r.ui.tareInFlight)
        assertTrue(r.ui.canTare)
        r.vm.onTareClick(); runCurrent()
        r.scale.gate!!.complete(Unit); runCurrent()
        assertEquals(1, r.fake.sent.count { it == ScaleCommand.Tare })
        r.run()
        assertFalse(r.ui.canTare)
        r.vm.onPauseClick(); runCurrent()
        assertFalse(r.ui.canTare)
    }

    @Test fun `TareFailed(Timeout) 스낵바 뒤 자동 시작으로 Running이면 다시 눌러도 Tare를 보내지 않는다`() = runTest {
        val r = rig()
        r.vm.onTareClick(); runCurrent()                       // 성공 → WaitingForPour
        advanceTimeBy(100); r.read(0.0)                         // 무장
        r.fake.respondWith(ScaleCommand.Tare::class, CommandResult.Failed(CommandFailure.Timeout))
        r.vm.onTareClick(); runCurrent()
        assertEquals(BrewUiEvent.Snackbar(BrewMessage.TareTimeout, true), r.events.filterIsInstance<BrewUiEvent.Snackbar>().last())
        for (k in 0..5) { advanceTimeBy(100); r.read(1.5 + 0.3 * k) }   // 확인 구간 500 ms(F-B4-1)
        assertEquals(BrewPhase.Running, r.phase)
        val before = r.fake.sent.count { it == ScaleCommand.Tare }
        r.vm.onTareClick(); runCurrent()                        // 스낵바 '다시'
        r.controller.tare(); runCurrent()                       // 컨트롤러도 막는다
        assertEquals(before, r.fake.sent.count { it == ScaleCommand.Tare })
    }

    @Test fun `currentConnection은 알림을 내기 전에 새 연결 상태다`() = runTest {
        val r = Rig(this, ConnectionState.Connecting(DEVICE.address, null), GRAM, BrewSettings(), DEVICE.address).also { runCurrent() }
        val v = store.create { BrewViewModel(r.controller, r.scale, r.recipes, r.repo, r.prefs, r.permission, r.clock) }
        var seen: ConnectionUi? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            v.events.collect { if (it == BrewUiEvent.Announce(BrewAnnouncement.ConnectFailed)) seen = v.currentConnection() }
        }
        runCurrent()
        r.conn(ConnectionState.Failed(FailureReason.ConnectTimeout))
        assertEquals(ConnectionUi.Failed(ConnectFailure.Timeout), seen)
        assertEquals("연결 실패, 응답 없음" to false, announcementText(BrewAnnouncement.ConnectFailed, seen!!))
    }

    @Test fun `밸브 알림만 Assertive이고 다른 알림 모드는 그대로`() {
        val assertive = BrewAnnouncement.entries.filter { announcementText(it, ConnectionUi.NoDevice).second }.toSet()
        assertEquals(
            setOf(
                BrewAnnouncement.ValveOpen, BrewAnnouncement.ValveClose, BrewAnnouncement.BluetoothOffDuringBrew,
                BrewAnnouncement.PermissionLostDuringBrew, BrewAnnouncement.FailedDuringBrew, BrewAnnouncement.DisconnectedDuringBrew,
            ),
            assertive,
        )
    }

    @Test fun `TareSucceeded 두 번이면 TareDone 두 번`() = runTest {
        val r = rig()
        r.vm.onTareClick(); runCurrent()
        r.vm.onTareClick(); runCurrent()
        assertEquals(2, r.announces().count { it == BrewAnnouncement.TareDone })
    }

    @Test fun `TareFailed 변환과 그 밖의 일회성 결과`() = runTest {
        val r = rig()
        fun tareWith(f: CommandFailure) { r.fake.respondWith(ScaleCommand.Tare::class, CommandResult.Failed(f)); r.vm.onTareClick(); runCurrent() }
        tareWith(CommandFailure.Disconnected); tareWith(CommandFailure.NotConnected); tareWith(CommandFailure.Timeout); tareWith(CommandFailure.Rejected)
        assertEquals(
            listOf(
                BrewUiEvent.Snackbar(BrewMessage.TareNotConnected, false), BrewUiEvent.Snackbar(BrewMessage.TareNotConnected, false),
                BrewUiEvent.Snackbar(BrewMessage.TareTimeout, true), BrewUiEvent.Snackbar(BrewMessage.TareRejected, true),
            ),
            r.events.filterIsInstance<BrewUiEvent.Snackbar>(),
        )
        r.vm.onSessionSaved(7)
        runCurrent()
        assertEquals(BrewUiEvent.Snackbar(BrewMessage.Saved, false, sessionId = 7), r.events.last())
    }

    @Test fun `화면 밖에서 쌓인 알림·스낵바는 버리고 저장 실패만 남긴다`() = runTest {
        val r = Rig(this, CONNECTED, GRAM, BrewSettings(), DEVICE.address).also { runCurrent() }
        val v = store.create { BrewViewModel(r.controller, r.scale, r.recipes, r.repo, r.prefs, r.permission, r.clock) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { v.uiState.collect {} }
        runCurrent()
        // S1이 컴포지션 밖에 있는 동안(수집 없음): 시작 알림·저장 스낵바·저장 실패
        v.onStartClick(); runCurrent()
        v.onSessionSaved(7)
        r.repo.failNext("saveDraft", RuntimeException("disk"))
        v.onFinishClick(); runCurrent()
        v.dropBackloggedEvents()
        val got = mutableListOf<BrewUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { v.events.collect { got += it } }
        runCurrent()
        assertEquals(listOf<BrewUiEvent>(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, false)), got)
    }

    @Test fun `저장 실패 스낵바와 다시 저장, 새 VM의 재방출`() = runTest {
        val r = rig()
        r.run()
        r.repo.failNext("saveDraft", RuntimeException("disk"))
        r.vm.onFinishClick(); runCurrent()
        assertEquals(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, false), r.events.last())
        val saves = r.repo.calls.count { it == "saveDraft" }
        r.events.clear()
        r.newVm()
        assertEquals(listOf<BrewUiEvent>(BrewUiEvent.Snackbar(BrewMessage.SaveFailed, false)), r.events.filterIsInstance<BrewUiEvent.Snackbar>())
        r.vm.onRetrySaveClick(); runCurrent()
        assertEquals(saves + 1, r.repo.calls.count { it == "saveDraft" })
        assertFalse(r.controller.saveFailed.value)
        r.events.clear()
        r.newVm()
        assertTrue(r.events.none { it is BrewUiEvent.Snackbar })
    }

    @Test fun `VM이 없을 때 쌓인 지난 알림은 새 VM이 내지 않고 Finished만 남긴다`() = runTest {
        val r = rig()
        r.read(0.0)
        store.clear(); runCurrent()   // VM이 사라진 동안(Activity 종료) 컨트롤러는 산다
        r.controller.tare(); runCurrent()
        r.controller.startTimer(); runCurrent()
        r.controller.finish(); runCurrent()
        r.events.clear()
        r.newVm()
        assertTrue(BrewAnnouncement.TareDone !in r.announces())
        assertEquals(1, r.events.count { it is BrewUiEvent.OpenSummary })
    }

    @Test fun `배터리 미상이면 batteryPercent null`() = runTest {
        val r = rig(status = GRAM.copy(batteryPercent = null))
        assertNull(r.ui.batteryPercent)
    }

    @Test fun `D5 초기화 확인`() = runTest {
        val r = rig()
        r.run()
        advanceTimeBy(100); r.read(5.0)
        r.vm.onPauseClick(); runCurrent()
        r.vm.onResetClick(); runCurrent()
        assertTrue(r.ui.showResetConfirm)
        assertEquals(BrewPhase.Paused, r.ui.phase)
        assertTrue(ScaleCommand.SetTimer(TimerAction.Reset) !in r.fake.sent)
        r.vm.onResetDismissed(); runCurrent()
        assertFalse(r.ui.showResetConfirm); assertEquals(BrewPhase.Paused, r.ui.phase)
        r.vm.onResetClick(); r.vm.onResetConfirmed(); runCurrent()
        assertEquals(BrewPhase.Idle, r.ui.phase)
        assertTrue(ScaleCommand.SetTimer(TimerAction.Reset) in r.fake.sent)
        // 샘플 0개면 바로 Idle
        r.run(); r.vm.onPauseClick(); runCurrent()
        r.vm.onResetClick(); runCurrent()
        assertFalse(r.ui.showResetConfirm)
        assertEquals(BrewPhase.Idle, r.ui.phase)
    }

    // ── 알림 ──

    @Test fun `PhaseChanged 알림 표`() {
        val I = BrewPhase.Idle; val W = BrewPhase.WaitingForPour; val R = BrewPhase.Running; val P = BrewPhase.Paused; val F = BrewPhase.Finished
        val u = PhaseCause.User; val s = PhaseCause.ScaleButton
        assertEquals(BrewAnnouncement.AutoStartWaiting, phaseAnnouncement(I, W, u))
        assertNull(phaseAnnouncement(W, I, u))
        assertEquals(BrewAnnouncement.TimerStarted, phaseAnnouncement(I, R, u))
        assertEquals(BrewAnnouncement.TimerStarted, phaseAnnouncement(W, R, u))
        assertEquals(BrewAnnouncement.TimerResumed, phaseAnnouncement(P, R, u))
        assertEquals(BrewAnnouncement.TimerPaused, phaseAnnouncement(R, P, u))
        assertEquals(BrewAnnouncement.TimerReset, phaseAnnouncement(P, I, u))
        assertEquals(BrewAnnouncement.TimerReset, phaseAnnouncement(R, I, u))   // D1 버리기
        assertNull(phaseAnnouncement(R, F, u)); assertNull(phaseAnnouncement(P, F, u))
        assertNull(phaseAnnouncement(F, I, u))
        assertEquals(BrewAnnouncement.AutoStarted, phaseAnnouncement(W, R, PhaseCause.AutoStart))
        assertEquals(BrewAnnouncement.AutoFinished, phaseAnnouncement(R, F, PhaseCause.AutoFinish))
        assertEquals(BrewAnnouncement.CapReached, phaseAnnouncement(R, F, PhaseCause.CapReached))
        assertEquals(BrewAnnouncement.CapReached, phaseAnnouncement(P, F, PhaseCause.CapReached))
        assertEquals(BrewAnnouncement.ScaleTimerStarted, phaseAnnouncement(I, R, s))
        assertEquals(BrewAnnouncement.ScaleTimerResumed, phaseAnnouncement(P, R, s))
        assertEquals(BrewAnnouncement.ScaleTimerPaused, phaseAnnouncement(R, P, s))
        assertEquals(BrewAnnouncement.ScaleTimerReset, phaseAnnouncement(R, I, s))
        assertEquals(BrewAnnouncement.ScaleTimerResetToSummary, phaseAnnouncement(P, F, s))
    }

    @Test fun `연결 전이 알림 표`() {
        val c = ConnectionUi.Connected(DEVICE)
        val rec = ConnectionUi.Reconnecting(1, 0)
        val R = BrewPhase.Running; val I = BrewPhase.Idle
        assertEquals(BrewAnnouncement.Connected, connectionAnnouncement(ConnectionUi.Connecting("A", null), c, I))
        assertEquals(BrewAnnouncement.Reconnected, connectionAnnouncement(rec, c, R))
        assertEquals(BrewAnnouncement.ReconnectStarted, connectionAnnouncement(c, rec, R))
        assertNull(connectionAnnouncement(c, ConnectionUi.Connected(DEVICE.copy(modelNumber = "X")), R))
        assertNull(connectionAnnouncement(rec, ConnectionUi.Reconnecting(2, 0), R))
        assertEquals(BrewAnnouncement.DisconnectedDuringBrew, connectionAnnouncement(c, ConnectionUi.Disconnected, R))
        assertEquals(BrewAnnouncement.FailedDuringBrew, connectionAnnouncement(rec, ConnectionUi.Failed(ConnectFailure.ConnectFailed), R))
        assertEquals(BrewAnnouncement.BluetoothOffDuringBrew, connectionAnnouncement(c, ConnectionUi.BluetoothOff, R))
        assertEquals(BrewAnnouncement.PermissionLostDuringBrew, connectionAnnouncement(c, ConnectionUi.PermissionMissing, R))
        assertEquals(BrewAnnouncement.Disconnected, connectionAnnouncement(c, ConnectionUi.Disconnected, I))
        assertEquals(BrewAnnouncement.ConnectFailed, connectionAnnouncement(ConnectionUi.Connecting("A", null), ConnectionUi.Failed(ConnectFailure.ConnectFailed), I))
        assertNull(connectionAnnouncement(ConnectionUi.Disconnected, ConnectionUi.BluetoothOff, I))
    }

    @Test fun `VM 알림은 전이마다 한 번, 첫 상태에서는 없음`() = runTest {
        val r = rig(initial = ConnectionState.Idle)   // 처음부터 Disconnected(저장 기기 있음)
        assertTrue(r.announces().isEmpty())
        r.conn(ConnectionState.Connecting(DEVICE.address, null))
        r.conn(CONNECTED)
        r.conn(ConnectionState.Connected(DEVICE.copy(modelNumber = "TES017-2")))
        assertEquals(listOf(BrewAnnouncement.Connected), r.announces())
        r.run()
        r.conn(ConnectionState.Reconnecting(DEVICE.address, 1, 0))
        r.conn(ConnectionState.Reconnecting(DEVICE.address, 2, 0))
        r.conn(ConnectionState.Failed(FailureReason.ReconnectExhausted))
        r.conn(ConnectionState.BluetoothOff)
        r.conn(ConnectionState.PermissionMissing)
        r.conn(ConnectionState.Idle)
        assertEquals(
            listOf(
                BrewAnnouncement.Connected, BrewAnnouncement.TimerStarted, BrewAnnouncement.ReconnectStarted, BrewAnnouncement.FailedDuringBrew,
                BrewAnnouncement.BluetoothOffDuringBrew, BrewAnnouncement.PermissionLostDuringBrew, BrewAnnouncement.DisconnectedDuringBrew,
            ),
            r.announces(),
        )
        r.vm.onDiscardClick(); runCurrent()
        assertEquals(BrewAnnouncement.TimerReset, r.announces().last())
        r.conn(ConnectionState.Connecting(DEVICE.address, null))
        r.conn(ConnectionState.Failed(FailureReason.GattError(133)))
        assertEquals(BrewAnnouncement.ConnectFailed, r.announces().last())
    }

    @Test fun `저울 버튼 일시정지는 ScaleTimerPaused`() = runTest {
        val r = rig()
        r.run()
        r.fake.emitTimerNotify(ScaleTimerState.Paused); runCurrent()
        assertEquals(BrewAnnouncement.ScaleTimerPaused, r.announces().last())
    }

    // ── 밸브·기다리기 ──

    private fun recipe(name: String) = DefaultRecipes.all.first { it.name == name }

    private suspend fun TestScope.valveRig(recipe: Recipe, seconds: Long, settings: BrewSettings = BrewSettings()): Rig {
        val r = rig(settings = settings)
        r.controller.selectRecipe(recipe); runCurrent()
        r.run()
        advanceTimeBy(seconds * 1_000); runCurrent()
        return r
    }

    @Test fun `ValveCue 열기는 Announce 뒤 Haptic(atNanos = 시계)`() = runTest {
        val r = valveRig(recipe("하리오 스위치 기본"), 121)
        val i = r.events.indexOf(BrewUiEvent.Announce(BrewAnnouncement.ValveOpen))
        assertTrue(i >= 0)
        val h = r.events[i + 1] as BrewUiEvent.Haptic
        assertEquals(1, r.events.count { it is BrewUiEvent.Haptic })
        assertEquals(120_000_000_000L + 0L, (h.atNanos / 1_000_000_000L) * 1_000_000_000L)   // 120 s 뒤 첫 Tick 시각
    }

    @Test fun `ValveCue 닫기는 ValveClose, 진동 끄면 Haptic 없음`() = runTest {
        val r = valveRig(recipe("테츠 카스야 악마의 레시피"), 76, BrewSettings(valveHaptic = false))
        assertTrue(BrewAnnouncement.ValveClose in r.announces())
        assertTrue(r.events.none { it is BrewUiEvent.Haptic })
    }

    @Test fun `onSettingsChange는 prefs에 저장하고 uiState에 반영된다`() = runTest {
        val r = rig()
        r.vm.onSettingsChange(r.ui.settings.copy(valveHaptic = false)); runCurrent()
        assertTrue("setBrewSettings" in r.prefs.calls)
        assertFalse(r.ui.settings.valveHaptic)
    }

    @Test fun `악마의 레시피는 기다리기 단계를 건너뛴다`() = runTest {
        val r = rig()
        r.controller.selectRecipe(recipe("테츠 카스야 악마의 레시피")); runCurrent()
        r.run()
        advanceTimeBy(100); r.read(60.0)
        assertEquals(1, r.ui.currentStepIndex)
        advanceTimeBy(100); r.read(120.0)
        assertEquals(3, r.ui.currentStepIndex)
        advanceTimeBy(100); r.read(280.0)
        assertEquals(5, r.ui.currentStepIndex)
        assertTrue(r.ui.lastStepReached)
    }

    // ── D2 미저장 추출 복구 ──

    private fun draft(startedAt: Long = 1_000L) = com.timemoredot.app.data.SessionDraft(null, startedAt, null, 12.0, 30_000, com.timemoredot.brew.SampleBuffer().toBlob())

    @Test fun `VM 생성 때 findDraft를 draftId 인자로 한 번 부른다, 초안이 없으면 null`() = runTest {
        val r = rig()
        assertEquals(listOf<Long?>(null), r.repo.findDraftArgs)
        assertNull(r.ui.recoverableDraft)
    }

    @Test fun `findDraft가 초안을 주면 recoverableDraft가 채워진다`() = runTest {
        val r = rig()
        val id = r.repo.saveDraft(draft()); runCurrent()
        r.newVm()
        assertEquals(id, r.ui.recoverableDraft?.id)
    }

    @Test fun `실시간 초안 id를 인자로 넘겨 그 초안은 복구 대상이 아니다`() = runTest {
        val r = rig()
        r.read(0.0); r.run()
        advanceTimeBy(10_500); runCurrent()
        val live = r.controller.draftId.value
        assertTrue(live != null)
        r.newVm()
        assertEquals(live, r.repo.findDraftArgs.last())
        assertEquals(2, r.repo.findDraftArgs.size)
        assertNull(r.ui.recoverableDraft)
    }

    @Test fun `저장된 세션은 onSessionSaved 뒤 D2에서 빠진다`() = runTest {
        val r = rig()
        val id = r.repo.saveDraft(draft()); runCurrent()
        r.newVm()
        assertEquals(id, r.ui.recoverableDraft?.id)
        r.vm.onSessionSaved(id); runCurrent()
        assertNull(r.ui.recoverableDraft)
    }

    @Test fun `D2 요약 열기는 OpenSummary(id, recovered), 버리기는 deleteSession 뒤 null`() = runTest {
        val r = rig()
        val id = r.repo.saveDraft(draft()); runCurrent()
        r.newVm()
        r.vm.onRecoverOpenClick(); runCurrent()
        assertTrue(BrewUiEvent.OpenSummary(id, recovered = true) in r.events)
        r.newVm()
        assertEquals(id, r.ui.recoverableDraft?.id)
        r.vm.onRecoverDiscardClick(); runCurrent()
        assertEquals(listOf(id), r.repo.deletedIds)
        assertNull(r.ui.recoverableDraft)
    }
}
