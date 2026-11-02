package com.timemoredot.app

import android.Manifest
import android.os.Build
import com.timemoredot.ble.BlePermissions
import com.timemoredot.protocol.FakeScaleClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidPermissionMonitorTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun requiredMatches() {
        val monitor = AndroidPermissionMonitor(app, FakeScaleClient())
        assertArrayEquals(BlePermissions.required(Build.VERSION.SDK_INT), monitor.requiredPermissions)
    }

    @Test @Config(sdk = [30]) fun `API 30의 필요 권한은 BlePermissions와 같다`() {
        assertEquals(30, Build.VERSION.SDK_INT)
        requiredMatches()
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), AndroidPermissionMonitor(app, FakeScaleClient()).requiredPermissions)
    }

    @Test @Config(sdk = [31]) fun `API 31의 필요 권한은 BlePermissions와 같다`() {
        assertEquals(31, Build.VERSION.SDK_INT)
        requiredMatches()
    }

    @Test @Config(sdk = [31]) fun `영구 거부 결과는 PermanentlyDenied다`() {
        val monitor = AndroidPermissionMonitor(app, FakeScaleClient())
        assertEquals(PermissionStatus.Denied, monitor.status.value)
        monitor.onResult(false, true)
        assertEquals(PermissionStatus.PermanentlyDenied, monitor.status.value)
        monitor.onResult(false, false)
        assertEquals(PermissionStatus.Denied, monitor.status.value)
    }

    @Test @Config(sdk = [31]) fun `허용 결과와 복귀는 refreshEnvironment를 부른다`() {
        val scale = FakeScaleClient()
        val monitor = AndroidPermissionMonitor(app, scale)
        monitor.onResult(false, true)
        assertEquals(emptyList<String>(), scale.calls)
        monitor.onResult(true, false)
        assertEquals(PermissionStatus.Granted, monitor.status.value)
        monitor.onResume()
        assertEquals(listOf("refreshEnvironment", "refreshEnvironment"), scale.calls)
    }

    @Test @Config(sdk = [31]) fun `설정에서 허용하고 돌아오면 Granted가 된다`() {
        val monitor = AndroidPermissionMonitor(app, FakeScaleClient())
        monitor.onResult(false, true)
        shadowOf(app).grantPermissions(*monitor.requiredPermissions)
        monitor.onResume()
        assertEquals(PermissionStatus.Granted, monitor.status.value)
    }
}
