package com.timemoredot.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat

/** 시스템 설정·권한 요청으로 나가는 동작. Intent는 UI 계층에서만 만든다(architecture 9.1). */
interface SystemActions {
    fun requestPermissions()
    fun openAppSettings()
    fun requestEnableBluetooth()
    fun openLocationSettings()
}

val LocalSystemActions = staticCompositionLocalOf<SystemActions> {
    object : SystemActions {   // 미리보기·테스트 기본값: 아무 일도 하지 않는다
        override fun requestPermissions() = Unit
        override fun openAppSettings() = Unit
        override fun requestEnableBluetooth() = Unit
        override fun openLocationSettings() = Unit
    }
}

class ActivitySystemActions(
    private val context: Context,
    private val packageName: String,
    private val launchPermissionRequest: () -> Unit,
    private val launchIntentForResult: (Intent) -> Unit,
) : SystemActions {
    override fun requestPermissions() = launchPermissionRequest()

    override fun openAppSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    // API 31+에서 ACTION_REQUEST_ENABLE은 BLUETOOTH_CONNECT가 없으면 SecurityException으로 끝난다(최종 QA Q10): 권한부터 묻는다.
    override fun requestEnableBluetooth() {
        val needsConnect = Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        if (needsConnect) return launchPermissionRequest()
        runCatching { launchIntentForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
    }

    override fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }
}
