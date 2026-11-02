package com.timemoredot.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timemoredot.app.ui.AppIcons
import com.timemoredot.app.ui.connect.ConnectionUi
import com.timemoredot.app.ui.theme.AppShape
import com.timemoredot.app.ui.theme.Space

/** ui-design 8.5: 포커스를 받는 요소에 2 dp primary 링(요소와 2 dp 틈). */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.hasFocus || it.isFocused }.drawWithContent {
        drawContent()
        if (focused) {
            val grow = 3.dp.toPx()   // 틈 2 dp + 선 절반 1 dp
            // 동심 반지름: 바깥 모서리 = 안쪽 모서리 + 틈
            val ringShape = if (shape is CornerBasedShape) shape.copy(
                CornerSize(shape.topStart.toPx(size, this) + grow), CornerSize(shape.topEnd.toPx(size, this) + grow),
                CornerSize(shape.bottomEnd.toPx(size, this) + grow), CornerSize(shape.bottomStart.toPx(size, this) + grow),
            ) else shape
            val outline = ringShape.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
            translate(-grow, -grow) { drawOutline(outline, color, style = Stroke(2.dp.toPx())) }
        }
    }
}

/** ui-design 4.1. 칩 문구·아이콘·색을 `ConnectionUi`에서 고른다. 배터리는 칩 낭독에 넣지 않는다(바로 뒤 배터리 노드가 읽는다, 8.4). */
@Composable
fun ConnectionChip(connection: ConnectionUi, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val style: ChipStyle = when (connection) {
        is ConnectionUi.Connected -> ChipStyle(AppIcons.BluetoothConnected, "연결됨", cs.tertiaryContainer, cs.onTertiaryContainer, "연결됨")
        is ConnectionUi.Connecting -> ChipStyle(AppIcons.BluetoothSearching, "연결 중", cs.secondaryContainer, cs.onSecondaryContainer, "연결 중")
        is ConnectionUi.Reconnecting -> ChipStyle(AppIcons.Sync, "재연결 중 · ${connection.attempt}회", cs.secondaryContainer, cs.onSecondaryContainer, "재연결 중")
        ConnectionUi.BluetoothOff -> ChipStyle(AppIcons.BluetoothDisabled, "블루투스 꺼짐", cs.errorContainer, cs.onErrorContainer, "블루투스 꺼짐")
        ConnectionUi.PermissionMissing, ConnectionUi.PermissionPermanentlyDenied, ConnectionUi.LocationOff ->
            ChipStyle(AppIcons.BluetoothDisabled, "권한 필요", cs.errorContainer, cs.onErrorContainer, "권한 필요")
        is ConnectionUi.Failed -> ChipStyle(AppIcons.Error, "연결 실패", cs.errorContainer, cs.onErrorContainer, "연결 실패")
        ConnectionUi.NoDevice, ConnectionUi.Disconnected, is ConnectionUi.Scanning, ConnectionUi.ScanEmpty, ConnectionUi.ScanFailed ->
            ChipStyle(AppIcons.Bluetooth, "연결 안 됨", cs.surfaceContainerHigh, cs.onSurfaceVariant, "연결 안 됨")
    }
    if (onClick == null) {   // 이미 S3 안: 동작 없는 상태 표시
        Surface(
            modifier.semantics(mergeDescendants = true) { stateDescription = style.state },
            shape = MaterialTheme.shapes.small, color = style.container, contentColor = style.content,
        ) {
            Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(style.icon, contentDescription = null, Modifier.size(18.dp))
                Text(style.label, style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }
    AssistChip(
        onClick = onClick,
        label = { Text(style.label) },
        leadingIcon = { Icon(style.icon, contentDescription = null, Modifier.size(18.dp)) },
        shape = MaterialTheme.shapes.small,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = style.container, labelColor = style.content, leadingIconContentColor = style.content,
        ),
        border = null,
        modifier = modifier
            .focusRing(MaterialTheme.shapes.small)
            .semantics(mergeDescendants = true) {
                stateDescription = style.state
                onClick(label = "저울 정보 열기", action = null)
            },
    )
}

/** 상태 줄 배터리(ui-design 3.1·8.5). null이면 그리지 않는다(노드 없음). 낭독은 이 노드 하나로 "배터리 82 퍼센트". */
@Composable
fun BatteryLabel(batteryPercent: Int?, modifier: Modifier = Modifier) {
    if (batteryPercent == null) return
    val low = batteryPercent <= LOW_BATTERY_PERCENT
    Row(
        modifier.heightIn(min = 48.dp).clearAndSetSemantics { contentDescription = batterySpoken(batteryPercent, sheet = false) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Icon(if (low) AppIcons.BatteryAlert else AppIcons.BatteryFull, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$batteryPercent%", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

const val LOW_BATTERY_PERCENT = 15

/** 배터리 낭독(8.4·8.5). S3 줄은 15% 이하에 ", 충전 필요", null이면 "배터리 확인 중". */
fun batterySpoken(percent: Int?, sheet: Boolean): String = when {
    percent == null -> "배터리 확인 중"
    sheet && percent <= LOW_BATTERY_PERCENT -> "배터리 $percent 퍼센트, 충전 필요"
    else -> "배터리 $percent 퍼센트"
}

private class ChipStyle(val icon: ImageVector, val label: String, val container: Color, val content: Color, val state: String)

enum class BannerKind { Error, Progress, Info }

/** ui-design 4.7. 배너 자체는 낭독 알림을 만들지 않는다(알림은 StatusAnnouncer). */
@Composable
fun StatusBanner(
    kind: BannerKind, text: String, modifier: Modifier = Modifier,
    icon: ImageVector? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null, secondaryEnabled: Boolean = true, secondaryState: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (container, content, iconTint, defaultIcon) = when (kind) {
        BannerKind.Error -> Quad(cs.errorContainer, cs.onErrorContainer, cs.onErrorContainer, AppIcons.Error)
        BannerKind.Progress -> Quad(cs.secondaryContainer, cs.onSecondaryContainer, cs.onSecondaryContainer, AppIcons.Sync)
        BannerKind.Info -> Quad(cs.surfaceContainerHigh, cs.onSurface, cs.onSurfaceVariant, AppIcons.Info)
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon ?: defaultIcon, contentDescription = null, Modifier.size(24.dp), tint = iconTint)
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (actionLabel != null && onAction != null) TextButton(onAction, Modifier.focusRing(CircleShape)) { Text(actionLabel) }
                    if (secondaryLabel != null && onSecondary != null) TextButton(
                        onSecondary,
                        Modifier.focusRing(CircleShape).semantics { if (secondaryState != null) stateDescription = secondaryState },
                        enabled = secondaryEnabled,
                    ) { Text(secondaryLabel) }
                }
            }
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: ImageVector)

/** ui-design 4.8. 아이콘 48 dp, 제목, 설명, 다음 행동 56 dp 버튼. 간격 16/8/24. */
@Composable
fun EmptyState(
    icon: ImageVector, title: String, modifier: Modifier = Modifier,
    description: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(   // 블롭은 장식: 의미 노드 없음(아이콘 contentDescription = null)
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape.blob),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(Space.l))
        Text(title, style = MaterialTheme.typography.titleMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading), textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(Space.s))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            Button(onAction, Modifier.heightIn(min = 56.dp).focusRing(CircleShape).widthIn(min = 160.dp)) { Text(actionLabel) }
        }
    }
}

/** M3 1.4 OutlinedButton 기본 테두리(outlineVariant)는 면 대비 1.6:1(라이트)라 WCAG 1.4.11 미달. outline으로 3:1 이상을 맞춘다. */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.material3.ButtonDefaults.ContentPadding,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = OutlinedButton(onClick, modifier, enabled, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), contentPadding = contentPadding, content = content)
