package com.timemoredot.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.components.AppOutlinedButton
import com.timemoredot.app.ui.components.ConnectionChip
import com.timemoredot.app.ui.components.LOW_BATTERY_PERCENT
import com.timemoredot.app.ui.components.batterySpoken
import com.timemoredot.app.ui.components.focusRing
import com.timemoredot.app.ui.connect.ConnectFailure
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.connect.displayName
import com.timemoredot.app.ui.connect.failureText
import com.timemoredot.app.ui.connect.permissionText
import com.timemoredot.app.ui.theme.Space
import com.timemoredot.protocol.WeightUnit

/** S3(ui-design 3.8, 5.4)를 담는 시트. M8이 S1에서 연다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    connection: ConnectionUi, batteryPercent: Int?, unitLabel: @Composable () -> String, canFindOtherScale: Boolean,
    onDisconnect: () -> Unit, onFindOtherScale: () -> Unit, onFindScale: () -> Unit, onDismiss: () -> Unit,
    onReconnect: () -> Unit = {}, onEnableBluetooth: () -> Unit = {}, onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {}, onOpenLocationSettings: () -> Unit = {}, locationPermission: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DeviceSheetContent(
            connection, batteryPercent, unitLabel, canFindOtherScale, onDisconnect, onFindOtherScale, onFindScale,
            onReconnect, onEnableBluetooth, onRequestPermission, onOpenSettings, onOpenLocationSettings, locationPermission,
        )
    }
}

/** 시트 안쪽(스크린샷이 이것을 그린다). unitLabel은 단위 줄 안에서 불려 readout 수집이 그 줄에 머문다(architecture.md 9.4 "S3 단위 줄"). */
@Composable
fun DeviceSheetContent(
    connection: ConnectionUi, batteryPercent: Int?, unitLabel: @Composable () -> String, canFindOtherScale: Boolean,
    onDisconnect: () -> Unit = {}, onFindOtherScale: () -> Unit = {}, onFindScale: () -> Unit = {},
    onReconnect: () -> Unit = {}, onEnableBluetooth: () -> Unit = {}, onRequestPermission: () -> Unit = {},
    onOpenSettings: () -> Unit = {}, onOpenLocationSettings: () -> Unit = {}, locationPermission: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.l).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("저울 정보", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ConnectionChip(connection, onClick = null)
        }
        if (connection is ConnectionUi.Connected) {
            val d = connection.device
            InfoRow("이름", displayName(d.name, d.address))
            InfoRow("모델", d.modelNumber ?: if (d.disReadDone) "알 수 없음" else "확인 중")
            InfoRow("펌웨어", d.firmwareRevision ?: if (d.disReadDone) "알 수 없음" else "확인 중")
            val battery = batteryPercent
            val spoken = batterySpoken(battery, sheet = true)
            when {
                battery == null -> InfoRow("배터리", "확인 중", spoken = spoken)
                battery <= LOW_BATTERY_PERCENT -> InfoRow("배터리", "$battery% · 충전 필요", AppIcons.BatteryAlert, MaterialTheme.colorScheme.error, spoken)
                else -> InfoRow("배터리", "$battery%", spoken = spoken)
            }
            UnitRow(unitLabel)
            Spacer(Modifier.size(Space.xs))
            AppOutlinedButton(onDisconnect, Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRing(androidx.compose.foundation.shape.CircleShape)) { Text("연결 끊기") }
            TextButton(
                onFindOtherScale,
                Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRing(androidx.compose.foundation.shape.CircleShape)
                    .semantics { if (!canFindOtherScale) stateDescription = "추출 중에는 바꿀 수 없음" },
                enabled = canFindOtherScale,
            ) { Text("다른 저울 찾기") }
        } else {
            Text(connectionMessage(connection, locationPermission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 5.4: 동작은 5.1을 따른다.
            val (primary, secondary) = when (connection) {
                ConnectionUi.Disconnected -> Pair("다시 연결" to onReconnect, "다른 저울 찾기" to onFindScale)
                ConnectionUi.BluetoothOff -> Pair("블루투스 켜기" to onEnableBluetooth, null)
                ConnectionUi.PermissionMissing -> Pair("권한 허용" to onRequestPermission, null)
                ConnectionUi.PermissionPermanentlyDenied -> Pair("설정 열기" to onOpenSettings, null)
                ConnectionUi.LocationOff -> Pair("위치 설정 열기" to onOpenLocationSettings, null)
                is ConnectionUi.Connecting -> Pair("취소" to onDisconnect, null)
                is ConnectionUi.Reconnecting -> Pair("지금 다시 시도" to onReconnect, "연결 끊기" to onDisconnect)
                is ConnectionUi.Failed ->
                    if (connection.reason == ConnectFailure.ServiceNotFound) Pair("다른 저울 찾기" to onFindScale, null)
                    else Pair("다시 시도" to onReconnect, "다른 저울 찾기" to onFindScale)
                else -> Pair("저울 찾기" to onFindScale, null)
            }
            Button(primary.second, Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRing(androidx.compose.foundation.shape.CircleShape)) { Text(primary.first) }
            if (secondary != null) {
                TextButton(secondary.second, Modifier.fillMaxWidth().heightIn(min = 48.dp).focusRing(androidx.compose.foundation.shape.CircleShape)) { Text(secondary.first) }
            }
        }
    }
}

@Composable
private fun UnitRow(unitLabel: @Composable () -> String) = InfoRow("단위", unitLabel())

/** 단위 줄 문구(5.4): readout.reading 종류가 먼저다. 단위를 모르는 동안 "g"를 보이지 않는다. */
fun unitLabelFor(unknown: Boolean, timedOut: Boolean, unit: WeightUnit): String = when {
    timedOut -> "알 수 없음"
    unknown -> "확인 중"
    unit == WeightUnit.Ounce -> "온스(oz)"
    else -> "그램(g)"
}

@Composable
private fun InfoRow(
    label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified, spoken: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).then(
            if (spoken != null) Modifier.clearAndSetSemantics { contentDescription = spoken } else Modifier.semantics(mergeDescendants = true) {},
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(0.35f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.weight(0.65f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = tint)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = if (icon != null) tint else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** 연결 안 됨 상태의 시트 문구(5.1 문구. 재연결 회차·초는 S1 배너만 보인다). */
internal fun connectionMessage(c: ConnectionUi, location: Boolean = false): String = when (c) {
    ConnectionUi.NoDevice -> "저울이 연결되지 않았습니다. TIMEMORE Dot을 켜고 연결하세요."
    ConnectionUi.Disconnected -> "저울이 연결되어 있지 않습니다. 저장된 저울에 다시 연결하거나 다른 저울을 찾으세요."
    ConnectionUi.BluetoothOff -> "블루투스가 꺼져 있습니다."
    ConnectionUi.PermissionMissing -> permissionText(false, location)
    ConnectionUi.PermissionPermanentlyDenied -> permissionText(true, location)
    ConnectionUi.LocationOff -> "위치 서비스를 켜야 저울을 찾을 수 있습니다."
    is ConnectionUi.Scanning, ConnectionUi.ScanEmpty, ConnectionUi.ScanFailed -> "저울이 연결되지 않았습니다."
    is ConnectionUi.Connecting -> "Dot에 연결하는 중…"
    is ConnectionUi.Reconnecting -> "연결이 끊겨 다시 연결하는 중입니다. 저울 버튼을 눌렀다면 저울이 꺼진 것이니 다시 켜 주세요."
    is ConnectionUi.Failed -> failureText(c.reason)
    is ConnectionUi.Connected -> ""
}
