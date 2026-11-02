package com.timemoredot.app.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.theme.TimemoreTheme
import com.timemoredot.protocol.ScaleDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S3 배터리 줄 낭독(ui-design 8.4·8.5). */
@RunWith(RobolectricTestRunner::class)
class DeviceSheetSemanticsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `배터리 줄 contentDescription`() {
        val device = ScaleDevice("AA", "Dot", "TES017", "v1", 247, disReadDone = true)
        var battery: Int? by mutableStateOf(82)
        rule.setContent { TimemoreTheme { DeviceSheetContent(ConnectionUi.Connected(device), battery, { "그램(g)" }, canFindOtherScale = true) } }
        rule.onNode(hasContentDescription("배터리 82 퍼센트")).assertExists()
        battery = 12
        rule.onNode(hasContentDescription("배터리 12 퍼센트, 충전 필요")).assertExists()
        battery = null
        rule.onNode(hasContentDescription("배터리 확인 중")).assertExists()
    }
}
