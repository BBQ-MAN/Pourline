package com.timemoredot.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemActionsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val activity by lazy { Robolectric.buildActivity(ComponentActivity::class.java).setup().get() }   // 앱 Context의 startActivity는 NEW_TASK가 필요하다
    private var permissionRequests = 0
    private var forResult: Intent? = null
    private val actions get() = ActivitySystemActions(activity, "com.timemoredot.app", { permissionRequests++ }, { forResult = it })

    @Test fun `설정 열기는 앱 정보 화면으로 보낸다`() {
        actions.openAppSettings()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.timemoredot.app", intent.data.toString())
        assertEquals(0, permissionRequests)   // 영구 거부에서 권한을 다시 요청하지 않는다
    }

    @Test fun `위치 설정과 블루투스 켜기`() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        actions.openLocationSettings()
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        actions.requestEnableBluetooth()
        assertEquals(BluetoothAdapter.ACTION_REQUEST_ENABLE, forResult?.action)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun `API 31 이상 CONNECT 권한 없이 블루투스 켜기는 권한부터 묻는다`() {
        shadowOf(app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        actions.requestEnableBluetooth()
        assertEquals(1, permissionRequests)
        assertNull(forResult)   // ACTION_REQUEST_ENABLE을 띄우지 않아 SecurityException이 없다
    }

    @Test fun `켜기 화면 실행이 실패하면 블루투스 설정으로 보낸다`() {
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        ActivitySystemActions(activity, "com.timemoredot.app", { permissionRequests++ }, { throw SecurityException("denied") })
            .requestEnableBluetooth()
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test fun `권한 허용은 요청 한 번`() {
        actions.requestPermissions()
        assertEquals(1, permissionRequests)
    }
}
