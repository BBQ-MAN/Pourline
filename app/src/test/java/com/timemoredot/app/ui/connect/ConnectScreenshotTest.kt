package com.timemoredot.app.ui.connect

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.timemoredot.app.ui.Announcer
import com.timemoredot.app.ui.LocalAnnouncer
import com.timemoredot.app.ui.Shooter
import com.timemoredot.app.ui.theme.TimemoreTheme
import com.timemoredot.protocol.ScanCandidate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class ConnectScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val a = ScanCandidate("C8:2B:96:DD:A1:B2", "TIMEMORE_Dot", -52, 0)
    private val b = ScanCandidate("C8:2B:96:DD:C3:D4", "TIMEMORE_Dot", -71, 0)
    private val c = ScanCandidate("C8:2B:96:DD:E5:F6", "Kitchen Dot", -86, 0)
    private val list = listOf(a, b, c)

    private fun state(connection: ConnectionUi, candidates: List<ScanCandidate> = emptyList(), recent: String? = a.address) =
        ConnectUiState(connection, recent, candidates)

    @Test fun `S2 상태별 스크린샷`() {
        val shooter = Shooter(rule).also { it.start() }
        fun shot(name: String, s: ConnectUiState, location: Boolean = false) = shooter.both("connect_$name") { ConnectContent(s, locationPermission = location) }

        shot("bluetooth_off", state(ConnectionUi.BluetoothOff))
        shot("permission_missing", state(ConnectionUi.PermissionMissing))
        shot("permission_permanently_denied", state(ConnectionUi.PermissionPermanentlyDenied))
        shot("location_off", state(ConnectionUi.LocationOff))
        shot("scanning_empty", state(ConnectionUi.Scanning(emptyList(), stopped = false)))
        shot("scanning_results", state(ConnectionUi.Scanning(list, stopped = false), list))
        shot("scanning_stopped", state(ConnectionUi.Scanning(list, stopped = true), list))
        shot("scan_empty", state(ConnectionUi.ScanEmpty))
        shot("scan_failed", state(ConnectionUi.ScanFailed))
        shot("connecting", state(ConnectionUi.Connecting(b.address, b.name), list))
        shot("reconnecting", state(ConnectionUi.Reconnecting(1, 0L), listOf(a, b)))
        shooter.both("connect_scanning_focused") {
            val fm = LocalFocusManager.current
            // 첫 이동은 루트 포커스를 얻는 데 쓰인다 → 둘째 행까지 세 번
            val view = androidx.compose.ui.platform.LocalView.current
            val input = androidx.compose.ui.platform.LocalInputModeManager.current
            LaunchedEffect(Unit) { view.requestFocus(); input.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard); repeat(8) { withFrameNanos { } }; repeat(3) { fm.moveFocus(FocusDirection.Next) } }
            ConnectContent(state(ConnectionUi.Scanning(list, stopped = false), list), locationPermission = false)
        }
        shot("failed", state(ConnectionUi.Failed(ConnectFailure.ConnectFailed), list))
        shot("failed_pairing_mode", state(ConnectionUi.Failed(ConnectFailure.PairingModeMaybe), list))   // F-F5-1
        shot("no_device", state(ConnectionUi.NoDevice, recent = null))
        shot("disconnected", state(ConnectionUi.Disconnected))

        shot("permission_missing_api30", state(ConnectionUi.PermissionMissing), location = true)
        shooter.font200("connect_permission_denied") {
            ConnectContent(state(ConnectionUi.PermissionPermanentlyDenied), locationPermission = false)
        }
        shooter.font200("connect_failed_pairing_mode") { ConnectContent(state(ConnectionUi.Failed(ConnectFailure.PairingModeMaybe), list), locationPermission = false) }
        val unnamed = listOf(ScanCandidate("C8:2B:96:DD:A1:B2", null, -55, 0), ScanCandidate("C8:2B:96:DD:C3:D4", null, -74, 0))
        val dup = listOf(a, b)
        shot("scanning_duplicate_name", state(ConnectionUi.Scanning(dup, stopped = false), dup, recent = null))
        shot("scanning_unnamed", state(ConnectionUi.Scanning(unnamed, stopped = false), unnamed, recent = null))
    }

    @Test fun `S2는 전이에서 알림을 낸다`() {
        val said = mutableListOf<String>()
        var ui by mutableStateOf(state(ConnectionUi.Scanning(emptyList(), false)))
        rule.setContent {
            CompositionLocalProvider(LocalAnnouncer provides Announcer { t, assertive -> said += "$t/$assertive" }) {
                TimemoreTheme { ConnectContent(ui, locationPermission = false) }
            }
        }
        rule.waitForIdle()
        ui = state(ConnectionUi.Scanning(listOf(a), false), listOf(a)); rule.waitForIdle()
        ui = state(ConnectionUi.Scanning(listOf(a, b), false), listOf(a, b)); rule.waitForIdle()   // 개수 변화(1 → 2)는 낭독하지 않는다
        ui = state(ConnectionUi.Scanning(listOf(a, b), true), listOf(a, b)); rule.waitForIdle()
        ui = state(ConnectionUi.Connecting(a.address, a.name), listOf(a, b)); rule.waitForIdle()
        ui = state(ConnectionUi.Failed(ConnectFailure.Timeout), listOf(a, b)); rule.waitForIdle()
        assertEquals(
            listOf("저울 찾는 중/false", "저울 1대 찾음/false", "찾기 끝, 저울 2대/false", "연결 중/false", "연결 실패, 응답 없음/false"),
            said,
        )
    }

    @Test fun `S2 Reconnecting은 배너와 취소를 그리고 후보 행이 비활성이며 알림은 종류가 바뀔 때만`() {
        val said = mutableListOf<String>()
        var ui by mutableStateOf(state(ConnectionUi.Scanning(listOf(a, b), true), listOf(a, b)))
        rule.setContent {
            CompositionLocalProvider(LocalAnnouncer provides Announcer { t, assertive -> said += "$t/$assertive" }) {
                TimemoreTheme { ConnectContent(ui, locationPermission = false) }
            }
        }
        rule.waitForIdle()
        said.clear()
        ui = state(ConnectionUi.Reconnecting(1, 0L), listOf(a, b)); rule.waitForIdle()
        rule.onNodeWithText("저장된 저울에 다시 연결하는 중입니다.").assertExists()
        rule.onNodeWithText("취소").assertExists()
        rule.onNode(hasClickAction() and hasText("TIMEMORE_Dot · A1:B2")).assertIsNotEnabled()
        rule.onNode(hasClickAction() and hasText("TIMEMORE_Dot · C3:D4")).assertIsNotEnabled()
        ui = state(ConnectionUi.Reconnecting(2, 0L), listOf(a, b)); rule.waitForIdle()
        ui = state(ConnectionUi.Connected(com.timemoredot.protocol.ScaleDevice(a.address, "A", null, null, 247))); rule.waitForIdle()
        // '저울 연결됨'은 S2가 닫히는 경합을 피하려고 ConnectScreen(상태 있는 쪽)이 닫기 전에 알린다. ConnectContent는 부르지 않는다.
        assertEquals(listOf("저장된 저울에 다시 연결하는 중/false"), said)
    }
}
