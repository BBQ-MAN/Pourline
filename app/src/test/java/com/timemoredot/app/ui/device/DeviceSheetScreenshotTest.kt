package com.timemoredot.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.timemoredot.app.ui.Shooter
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.protocol.ScaleDevice
import com.timemoredot.protocol.ScaleStatus
import com.timemoredot.protocol.WeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class DeviceSheetScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val full = ScaleDevice("C8:2B:96:DD:A1:B2", "TIMEMORE_Dot", "TES017", "v1.0.4", 247, disReadDone = true)
    private val status = ScaleStatus(WeightUnit.Gram, false, 82, null)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Frame(connection: ConnectionUi, status: ScaleStatus) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
            Surface(
                Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = BottomSheetDefaults.ExpandedShape,
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BottomSheetDefaults.DragHandle()
                    DeviceSheetContent(
                        connection, status.batteryPercent,
                        { unitLabelFor(status.unit == null && !status.unitReadFailed, status.unit == null && status.unitReadFailed, status.unit ?: WeightUnit.Gram) },
                        canFindOtherScale = true,
                    )
                }
            }
        }
    }

    @Test fun `S3 상태별 스크린샷`() {
        val s = Shooter(rule).also { it.start() }
        s.both("device_sheet_connected") { Frame(ConnectionUi.Connected(full), status) }
        s.both("device_sheet_dis_reading") {
            Frame(ConnectionUi.Connected(full.copy(modelNumber = null, firmwareRevision = null, disReadDone = false)), status)
        }
        s.both("device_sheet_dis_failed") {
            Frame(ConnectionUi.Connected(full.copy(modelNumber = null, firmwareRevision = null, disReadDone = true)), status)
        }
        s.both("device_sheet_battery_low") { Frame(ConnectionUi.Connected(full), status.copy(batteryPercent = 12)) }
        s.both("device_sheet_battery_unknown") { Frame(ConnectionUi.Connected(full), status.copy(batteryPercent = null)) }
        s.font200("device_sheet_battery_low") { Frame(ConnectionUi.Connected(full), status.copy(batteryPercent = 12)) }
        s.both("device_sheet_disconnected") { Frame(ConnectionUi.Disconnected, ScaleStatus.Unknown) }
        // S3 단위 줄(architecture.md 9.4): readout.reading이 UnitUnknown → "확인 중", UnitUnknownTimedOut → "알 수 없음"
        s.both("device_sheet_unit_unknown") { Frame(ConnectionUi.Connected(full), status.copy(unit = null)) }
        s.both("device_sheet_unit_failed") { Frame(ConnectionUi.Connected(full), status.copy(unit = null, unitReadFailed = true)) }
    }
    @Test fun `S3 연결 실패 스크린샷`() {
        val s = Shooter(rule).also { it.start() }
        s.both("device_sheet_failed") { Frame(ConnectionUi.Failed(com.timemoredot.app.ui.connect.ConnectFailure.ConnectFailed), ScaleStatus.Unknown) }
        s.font200("device_sheet_failed_pairing_mode") { Frame(ConnectionUi.Failed(com.timemoredot.app.ui.connect.ConnectFailure.PairingModeMaybe), ScaleStatus.Unknown) }
        s.both("device_sheet_failed_pairing_mode") { Frame(ConnectionUi.Failed(com.timemoredot.app.ui.connect.ConnectFailure.PairingModeMaybe), ScaleStatus.Unknown) }   // F-F5-1
    }
}
