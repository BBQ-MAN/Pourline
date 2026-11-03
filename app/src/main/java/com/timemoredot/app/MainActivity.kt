package com.timemoredot.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.timemoredot.app.ui.ActivitySystemActions
import com.timemoredot.app.ui.LocalSystemActions
import com.timemoredot.app.ui.StatusAnnouncer
import com.timemoredot.app.ui.nav.AppNav
import com.timemoredot.app.ui.theme.TimemoreTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as TimemoreApp).container

    private var permissionRequestedAtMs = 0L
    private val permissionPrefs get() = getSharedPreferences("permission", MODE_PRIVATE)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = container.permission.requiredPermissions.all { result[it] == true }
        val rationale = container.permission.requiredPermissions.any { shouldShowRequestPermissionRationale(it) }
        val deniedOnce = PermissionDenial.deniedOnceAfterResult(granted, rationale, permissionPrefs.getBoolean(KEY_DENIED_ONCE, false))
        permissionPrefs.edit().putBoolean(KEY_DENIED_ONCE, deniedOnce).apply()
        val elapsedMs = SystemClock.elapsedRealtime() - permissionRequestedAtMs
        val permanentlyDenied = PermissionDenial.isPermanent(granted, rationale, deniedOnce, elapsedMs)
        if (BuildConfig.DEBUG) android.util.Log.d("TimemoreBle", "EV 권한 결과 granted=$granted rationale=$rationale deniedOnce=$deniedOnce elapsedMs=$elapsedMs permanent=$permanentlyDenied")   // B2 재확인에서 500 ms 여유를 잰다
        container.permission.onResult(granted, permanentlyDenied)   // 거부 뒤 자동으로 다시 요청하지 않는다
    }
    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        VariantHooks.onActivityCreate(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val actions = ActivitySystemActions(
            context = this, packageName = packageName,
            launchPermissionRequest = {
                permissionRequestedAtMs = SystemClock.elapsedRealtime()
                permissionLauncher.launch(container.permission.requiredPermissions)
            },
            launchIntentForResult = { intent: Intent -> enableBluetoothLauncher.launch(intent) },
        )
        setContent {
            TimemoreTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StatusAnnouncer {
                        CompositionLocalProvider(LocalSystemActions provides actions) { AppNav() }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.permission.onResume()   // 설정에서 돌아옴: 권한 다시 확인 + refreshEnvironment
        val deniedOnce = permissionPrefs.getBoolean(KEY_DENIED_ONCE, false)
        val granted = container.permission.status.value == PermissionStatus.Granted   // 바로 위 onResume()이 동기로 갱신한 값
        val next = PermissionDenial.deniedOnceOnResume(granted, deniedOnce)
        if (next != deniedOnce) permissionPrefs.edit().putBoolean(KEY_DENIED_ONCE, next).apply()
    }

    private companion object {
        const val KEY_DENIED_ONCE = "deniedOnce"
    }
}
