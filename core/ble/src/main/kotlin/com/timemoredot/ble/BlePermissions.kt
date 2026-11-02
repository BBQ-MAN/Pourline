package com.timemoredot.ble

import android.Manifest

/** 런타임 권한 목록과 위치 서비스 필요 여부를 정하는 유일한 곳(architecture.md 6절). BleScaleClient와 :app PermissionMonitor가 같이 쓴다. */
object BlePermissions {
    const val USE_NEVER_FOR_LOCATION = true          // TODO(device): android-ble 1절 neverForLocation 프로브. false면 매니페스트도 함께 바꾼다

    fun required(sdkInt: Int): Array<String> = when {
        sdkInt <= 30 -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        USE_NEVER_FOR_LOCATION -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else -> arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun needsLocationService(sdkInt: Int): Boolean = sdkInt <= 30 || !USE_NEVER_FOR_LOCATION
}
