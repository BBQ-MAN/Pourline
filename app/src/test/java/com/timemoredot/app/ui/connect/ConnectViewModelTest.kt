package com.timemoredot.app.ui.connect

import app.cash.turbine.test
import com.timemoredot.app.PermissionStatus
import com.timemoredot.app.fake.FakePermissionMonitor
import com.timemoredot.protocol.ConnectionState
import com.timemoredot.protocol.FailureReason
import com.timemoredot.protocol.FakeScaleClient
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScanCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    private val a = ScanCandidate("AA:00:00:00:00:0A", "A", -50, 0)
    private val b = ScanCandidate("AA:00:00:00:00:0B", null, -70, 0)
    private val device = ScaleDevice(a.address, "A", null, null, 247)
    private lateinit var scale: FakeScaleClient
    private lateinit var permission: FakePermissionMonitor
    private lateinit var vm: ConnectViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scale = FakeScaleClient()
        permission = FakePermissionMonitor(scale = scale)
        vm = ConnectViewModel(scale, permission)
    }
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `onCandidateClick은 connect를 부른다`() {
        vm.onCandidateClick(b.address)
        assertEquals(listOf("connect:${b.address}"), scale.calls)
    }

    @Test fun `권한 결과와 onResume은 refreshEnvironment로 이어진다`() {
        vm.onPermissionResult(true, false)
        vm.onResume()
        assertEquals(listOf("onResult:true,false", "onResume"), permission.calls)
        assertEquals(2, scale.calls.count { it == "refreshEnvironment" })
    }

    @Test fun `onCancelConnectClick은 disconnect를 부른다`() {
        vm.onCancelConnectClick()
        assertEquals(listOf("disconnect"), scale.calls)
    }

    @Test fun `스캔 시작과 멈춤`() {
        vm.onScanClick(); vm.onStopScanClick()
        assertEquals(listOf("startScan", "stopScan"), scale.calls)
    }

    @Test fun `Connected 진입 때 이벤트가 한 번 나온다`() = runTest {
        vm.events.test {
            scale.setConnection(ConnectionState.Connecting(a.address, "A"))
            expectNoEvents()
            scale.setConnection(ConnectionState.Connected(device))
            assertEquals(ConnectEvent.Connected, awaitItem())
            scale.setConnection(ConnectionState.Connected(device.copy(modelNumber = "TES017", disReadDone = true)))   // DIS 읽은 뒤 두 번째 Connected
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `저장 주소 A, B를 눌러 실패하면 다시 시도는 B로 연결한다`() {
        scale.setLastDevice(a.address)
        vm.onCandidateClick(b.address)
        scale.setConnection(ConnectionState.Failed(FailureReason.GattError(133)))
        vm.onRetryClick()
        assertEquals(listOf("connect:${b.address}", "connect:${b.address}"), scale.calls)
        assertFalse("connectLastDevice" in scale.calls)
    }

    @Test fun `시도한 주소가 없으면 connectLastDevice, 그것이 false면 startScan`() {
        scale.setLastDevice(a.address)
        vm.onRetryClick()
        assertEquals(listOf("connectLastDevice"), scale.calls)

        val scale2 = FakeScaleClient()   // 저장 주소 없음
        val vm2 = ConnectViewModel(scale2, FakePermissionMonitor(scale = scale2))
        vm2.onRetryClick()
        assertEquals(listOf("connectLastDevice", "startScan"), scale2.calls)
    }

    @Test fun `연결에 성공하면 다음 실패의 다시 시도는 저장 기기로 간다`() {
        scale.setLastDevice(a.address)
        vm.onCandidateClick(b.address)
        scale.setConnection(ConnectionState.Connected(device))
        scale.setConnection(ConnectionState.Failed(FailureReason.ReconnectExhausted))
        vm.onRetryClick()
        assertEquals(listOf("connect:${b.address}", "connectLastDevice"), scale.calls)
    }

    @Test fun `Connecting과 Failed에서도 후보 목록이 그대로다`() = runTest {
        scale.setLastDevice(a.address)
        vm.uiState.test {
            awaitItem()
            scale.setScanResults(listOf(a, b))
            scale.setConnection(ConnectionState.Scanning)
            assertEquals(listOf(a, b), expectMostRecentItem().candidates)

            scale.setConnection(ConnectionState.Connecting(a.address, "A"))
            val connecting = expectMostRecentItem()
            assertEquals(listOf(a, b), connecting.candidates)
            assertTrue(connecting.connection is ConnectionUi.Connecting)

            scale.setConnection(ConnectionState.Failed(FailureReason.GattError(133)))
            val failed = expectMostRecentItem()
            assertEquals(listOf(a, b), failed.candidates)
            assertEquals(ConnectionUi.Failed(ConnectFailure.ConnectFailed), failed.connection)
            assertEquals(a.address, failed.recentAddress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `권한 상태가 연결 상태 매핑에 들어간다`() = runTest {
        vm.uiState.test {
            awaitItem()
            scale.setConnection(ConnectionState.PermissionMissing)
            permission.setStatus(PermissionStatus.PermanentlyDenied)
            assertEquals(ConnectionUi.PermissionPermanentlyDenied, expectMostRecentItem().connection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Reconnecting에서도 후보가 그대로고 취소는 disconnect, Connected면 이벤트 한 번`() = runTest {
        vm.uiState.test {
            awaitItem()
            scale.setScanResults(listOf(a, b))
            scale.setConnection(ConnectionState.Reconnecting(a.address, 1, 5L))
            val r = expectMostRecentItem()
            assertTrue(r.connection is ConnectionUi.Reconnecting)
            assertEquals(listOf(a, b), r.candidates)
            vm.onCancelConnectClick()
            assertEquals(listOf("disconnect"), scale.calls.filter { it == "disconnect" })
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            scale.setConnection(ConnectionState.Reconnecting(a.address, 1, 5L))
            expectNoEvents()
            scale.setConnection(ConnectionState.Connected(device))
            assertEquals(ConnectEvent.Connected, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
