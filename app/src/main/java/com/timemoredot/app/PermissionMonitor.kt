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

/** 권한 요청 결과의 영구 거부 판정(architecture.md 8.1). Activity가 부르는 순수 함수. */
object PermissionDenial {
    /** 이보다 빨리 돌아온 거부는 대화상자 없이 시스템이 바로 거부한 것으로 본다(F-B2-1). 사람이 대화상자를 보고 닫는 데는 이보다 오래 걸린다 */
    const val INSTANT_RESULT_MS = 500L

    /** deniedOnce 다음 값: 허용이면 지우고, 실제 거부(rationale true)면 기록한다. 그 밖(대화상자를 거부 없이 닫음 등)은 그대로. */
    fun deniedOnceAfterResult(granted: Boolean, rationale: Boolean, deniedOnce: Boolean): Boolean = when {
        granted -> false
        rationale -> true
        else -> deniedOnce
    }

    /** 영구 거부 = 거부 + rationale false이고, (전에 실제로 거부했거나 대화상자 없이 즉시 돌아옴).
     *  앱이 모르는 경로로 이미 '다시 묻지 않음'이 된 경우(F-B2-1)를 즉시 반환으로 잡고, 대화상자를 바깥 눌러 닫은 경우는 시간이 걸려 Denied로 남는다. */
    fun isPermanent(granted: Boolean, rationale: Boolean, deniedOnce: Boolean, elapsedMs: Long): Boolean =
        !granted && !rationale && (deniedOnce || elapsedMs < INSTANT_RESULT_MS)

    /** 화면 복귀(onResume) 때 deniedOnce: 설정에서 허용하고 돌아왔으면 지운다(B2 관찰). */
    fun deniedOnceOnResume(allGranted: Boolean, deniedOnce: Boolean): Boolean = deniedOnce && !allGranted
}

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
