package com.timemoredot.app.fake

import com.timemoredot.app.PermissionMonitor
import com.timemoredot.app.PermissionStatus
import com.timemoredot.protocol.ScaleClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 실제 구현과 같은 규칙으로 scale.refreshEnvironment()를 부른다(허용 결과, 복귀). */
class FakePermissionMonitor(
    initial: PermissionStatus = PermissionStatus.Granted,
    private val scale: ScaleClient? = null,
    override val requiredPermissions: Array<String> = arrayOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
) : PermissionMonitor {
    private val _status = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    override val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    fun setStatus(s: PermissionStatus) { _status.value = s }

    override fun onResult(allGranted: Boolean, permanentlyDenied: Boolean) {
        calls += "onResult:$allGranted,$permanentlyDenied"
        _status.value = when { allGranted -> PermissionStatus.Granted; permanentlyDenied -> PermissionStatus.PermanentlyDenied; else -> PermissionStatus.Denied }
        if (allGranted) scale?.refreshEnvironment()
    }

    override fun onResume() {
        calls += "onResume"
        scale?.refreshEnvironment()
    }
}
