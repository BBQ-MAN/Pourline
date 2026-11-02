package com.timemoredot.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class VariantHooksTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var container: AppContainer? = null

    @Before fun setUp() = VariantHooks.resetForTest()

    @After fun tearDown() { container?.database?.close(); VariantHooks.resetForTest() }

    private fun activity(cls: String): Activity =
        Robolectric.buildActivity(Activity::class.java, Intent().setComponent(ComponentName(app.packageName, cls))).get()

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun `DemoLauncher면 데모 컨테이너, 스캔에 데모 저울 V60, DB와 prefs가 따로다`() {
        shadowOf(app).grantPermissions("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        VariantHooks.onActivityCreate(activity("com.timemoredot.app.DemoLauncher"))
        val c = VariantHooks.overrideContainer(app)
        assertNotNull(c)
        container = c
        c!!.scale.startScan()
        idle(1_000)
        assertTrue(c.scale.scanResults.value.any { it.name == "데모 저울 · V60" })
        assertEquals("timemore_demo.db", c.database.openHelper.databaseName)
        assertEquals(5, runBlocking { c.recipes.observeAll().first() }.size)
        c.scale.connect("02:00:00:00:00:01")
        idle(3_000)
        assertEquals("02:00:00:00:00:01", app.getSharedPreferences("scale_demo", 0).getString("last_address", null))
        assertFalse(app.getSharedPreferences("scale", 0).contains("last_address"))
    }

    @Test fun `MainActivity면 override는 null`() {
        VariantHooks.onActivityCreate(activity("com.timemoredot.app.MainActivity"))
        assertNull(VariantHooks.overrideContainer(app))
    }

    @Test fun `컨테이너를 만든 뒤 다른 모드로 열면 모드는 그대로이고 Toast 한 번`() {
        VariantHooks.onActivityCreate(activity("com.timemoredot.app.MainActivity"))
        assertNull(VariantHooks.overrideContainer(app))
        VariantHooks.onActivityCreate(activity("com.timemoredot.app.DemoLauncher"))
        assertNull(VariantHooks.overrideContainer(app))
        assertEquals(1, ShadowToast.shownToastCount())
        assertEquals("다른 모드로 실행 중입니다. 앱을 완전히 닫고 다시 여세요", ShadowToast.getTextOfLatestToast())
    }
}
