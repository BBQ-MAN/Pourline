package com.timemoredot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.timemoredot.ble.BlePermissions
import com.timemoredot.protocol.ScaleClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionStatus { Granted, Denied, PermanentlyDenied }

/** architecture.md 8.1. 권한 결과는 Activity만 안다(shouldShowRequestPermissionRationale). S1·S2가 같은 값을 쓰게 앱 범위에 둔다. */
interface PermissionMonitor {
    val status: StateFlow<PermissionStatus>
    fun onResult(allGranted: Boolean, permanentlyDenied: Boolean)
    fun onResume()
    val requiredPermissions: Array<String>
}

class AndroidPermissionMonitor(private val context: Context, private val scale: ScaleClient) : PermissionMonitor {
    override val requiredPermissions: Array<String> = BlePermissions.required(Build.VERSION.SDK_INT)
    private val _status = MutableStateFlow(if (allGranted()) PermissionStatus.Granted else PermissionStatus.Denied)
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    private fun allGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResult(allGranted: Boolean, permanentlyDenied: Boolean) {
        _status.value = when {
            allGranted -> PermissionStatus.Granted
            permanentlyDenied -> PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.Denied
        }
        if (allGranted) scale.refreshEnvironment()
    }

    override fun onResume() {
        // 설정에서 허용하고 돌아온 경우와 철회된 경우를 다시 확인한다. 거부 상태의 종류(영구 여부)는 Activity 결과만 안다.
        if (allGranted()) _status.value = PermissionStatus.Granted
        else if (_status.value == PermissionStatus.Granted) _status.value = PermissionStatus.Denied
        scale.refreshEnvironment()
    }
}
