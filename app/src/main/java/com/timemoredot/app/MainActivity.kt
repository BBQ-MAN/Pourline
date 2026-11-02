package com.timemoredot.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = container.permission.requiredPermissions.all { result[it] == true }
        // 영구 거부 = 이전에 실제로 한 번 거부(rationale이 true였음)했고 지금 rationale이 false. 대화상자를 거부 없이 닫은 경우는 Denied로 둔다.
        val prefs = getSharedPreferences("permission", MODE_PRIVATE)
        val rationale = container.permission.requiredPermissions.any { shouldShowRequestPermissionRationale(it) }
        if (granted) prefs.edit().putBoolean("deniedOnce", false).apply() else if (rationale) prefs.edit().putBoolean("deniedOnce", true).apply()
        val permanentlyDenied = !granted && !rationale && prefs.getBoolean("deniedOnce", false)
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
            launchPermissionRequest = { permissionLauncher.launch(container.permission.requiredPermissions) },
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
    }
}
