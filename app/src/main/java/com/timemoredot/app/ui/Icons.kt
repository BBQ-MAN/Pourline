package com.timemoredot.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** ui-design.md 6.5 아이콘 중 M7이 쓰는 것. material-icons-extended를 쓰지 않으려고 경로 데이터를 코드로 둔다. arrow_back은 6.5 목록에 없어 더했다. */
object AppIcons {
    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
    }.build()

    val Bluetooth = icon("bluetooth", "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM14.88,16.29L13,18.17v-3.76l1.88,1.88z")
    val BluetoothConnected = icon("bluetooth_connected", "M7,12l-2,-2 -2,2 2,2 2,-2zM17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zM13,18.17v-3.76l1.88,1.88L13,18.17zM19,10l-2,2 2,2 2,-2 -2,-2z")
    val BluetoothSearching = icon("bluetooth_searching", "M14.24,12.01l2.32,2.32c0.28,-0.72 0.44,-1.51 0.44,-2.33 0,-0.82 -0.16,-1.59 -0.43,-2.31l-2.33,2.32zM19.53,6.71l-1.26,1.26c0.63,1.21 0.98,2.57 0.98,4.02s-0.36,2.82 -0.98,4.02l1.2,1.2c0.97,-1.54 1.54,-3.36 1.54,-5.31 -0.01,-1.89 -0.55,-3.67 -1.48,-5.19zM15.71,7.71L10,2L9,2v7.59L4.41,5 3,6.41 8.59,12 3,17.59 4.41,19 9,14.41L9,22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM11,5.83l1.88,1.88L11,9.59L11,5.83zM12.88,16.29L11,18.17v-3.76l1.88,1.88z")
    val BluetoothDisabled = icon("bluetooth_disabled", "M13,5.83l1.88,1.88 -1.6,1.6 1.41,1.41 3.02,-3.02L12,2h-1v5.03l2,2v-3.2zM5.41,4L4,5.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l4.29,-4.29 2.3,2.29L20,18.59 5.41,4zM13,18.17v-3.76l1.88,1.88L13,18.17z")
    val Sync = icon("sync", "M12,4V1L8,5l4,4V6c3.31,0,6,2.69,6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
    val Error = icon("error", "M12,17c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1 -1,0.45 -1,1 0.45,1 1,1zM12,7c-0.55,0 -1,0.45 -1,1v4c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8c0,-0.55 -0.45,-1 -1,-1zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z")
    val Info = icon("info", "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
    val BatteryFull = icon("battery_full", "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4zM15,20H9V6h6z")
    val BatteryAlert = icon("battery_alert", "M15.67,4H14V2h-4v2H8.33C7.6,4 7,4.6 7,5.33v15.33C7,21.4 7.6,22 8.33,22h7.33c0.74,0 1.34,-0.6 1.34,-1.33V5.33C17,4.6 16.4,4 15.67,4zM15,20H9V6h6zM11,16v2h2v-2zM11,9v5h2V9z")
    val ArrowBack = icon("arrow_back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    val History = icon("history", "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z")
    val MenuBook = icon("menu_book", "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5 -1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5 0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5 1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05 0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z")
    val Scale = icon("scale", "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2zM5,5v14h14V5H5zM12,7a5,5 0 0 0 -5,5h10a5,5 0 0 0 -5,-5z")

    // S1(M8)
    val Warning = icon("warning", "M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")
    val Help = icon("help", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,19h-2v-2h2v2zM15.07,11.25l-0.9,0.92C13.45,12.9 13,13.5 13,15h-2v-0.5c0,-1.1 0.45,-2.1 1.17,-2.83l1.24,-1.26c0.37,-0.36 0.59,-0.86 0.59,-1.41 0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2L8,9c0,-2.21 1.79,-4 4,-4s4,1.79 4,4c0,0.88 -0.36,1.68 -0.93,2.25z")
    val LinkOff = icon("link_off", "M17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1 0,1.43 -0.98,2.63 -2.31,2.98l1.46,1.46C20.88,15.61 22,13.95 22,12c0,-2.76 -2.24,-5 -5,-5zM16,11h-2.19l2,2L16,13zM2,4.27l3.11,3.11C3.29,8.12 2,9.91 2,12c0,2.76 2.24,5 5,5h4v-1.9L7,15.1c-1.71,0 -3.1,-1.39 -3.1,-3.1 0,-1.59 1.21,-2.9 2.76,-3.07L8.73,11L8,11v2h2.73L13,15.27L13,17h1.73l4.01,4L20,19.74 3.27,3 2,4.27z")
    val SignalDisconnected = icon("signal_disconnected", "M21,1l-8.59,8.59L21,18.18V1zM4.77,4.5L3.5,5.77l6.36,6.36L1,21h17.73l2,2L22,21.73 4.77,4.5z")
    val HourglassEmpty = icon("hourglass_empty", "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01H6V22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01H18V2H6zM16,16.5V20H8v-3.5l4,-4 4,4zM12,11.5l-4,-4V4h8v3.5l-4,4z")
    val PlayArrow = icon("play_arrow", "M8,5v14l11,-7z")
    val Pause = icon("pause", "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z")
    val Check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
    val RestartAlt = icon("restart_alt", "M12,5V2L8,6l4,4V7c3.31,0 6,2.69 6,6c0,2.97 -2.17,5.43 -5,5.91v2.02c3.95,-0.49 7,-3.85 7,-7.93C20,8.58 16.42,5 12,5zM6,13c0,-1.65 0.67,-3.15 1.76,-4.24L6.34,7.34C4.9,8.79 4,10.79 4,13c0,4.08 3.05,7.44 7,7.93v-2.02C8.17,18.43 6,15.97 6,13z")
    val Tune = icon("tune", "M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9zM21,13v-2L11,11v2h10zM15,9h2L17,7h4L21,5h-4L17,3h-2v6z")
    val ArrowDropDown = icon("arrow_drop_down", "M7,10l5,5 5,-5z")
    val Schedule = icon("schedule", "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z")
    val Save = icon("save", "M17,3L5,3c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -3,1.34 -3,3zM15,9L5,9L5,5h10v4z")

    val Download = icon("download", "M5,20h14v-2H5V20zM19,9h-4V3H9v6H5l7,7L19,9z")
    val Close = icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
    val Add = icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Delete = icon("delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
    val ArrowUpward = icon("arrow_upward", "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z")
    val ArrowDownward = icon("arrow_downward", "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z")
    val MoreVert = icon("more_vert", "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")

    private fun ImageVector.Builder.stroke(path: String, width: Float = 2f) = addPath(
        PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    )
    private const val DRIPPER = "M4,5 H20 L14,13 H10 Z"
    private const val OUTLET = "M10,13 V16 H14 V13"

    /** 앱 전용 밸브 아이콘(ui-design 4.11): 드리퍼 + 출구를 막는 막대 / 출구 아래 물방울. 색 없이 모양으로 갈린다. */
    val ValveClosed = ImageVector.Builder("valve_closed", 24.dp, 24.dp, 24f, 24f).apply {
        stroke(DRIPPER); stroke(OUTLET); stroke("M6.5,18 H17.5", 2.5f)
    }.build()
    val ValveOpen = ImageVector.Builder("valve_open", 24.dp, 24.dp, 24f, 24f).apply {
        stroke(DRIPPER); stroke(OUTLET)
        addPath(
            PathParser().parsePathString("M12,17.5 C12,17.5 13.68,19.82 13.68,21.18 C13.68,22.22 12.92,23.1 12,23.1 C11.08,23.1 10.32,22.22 10.32,21.18 C10.32,19.82 12,17.5 12,17.5 Z").toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}
