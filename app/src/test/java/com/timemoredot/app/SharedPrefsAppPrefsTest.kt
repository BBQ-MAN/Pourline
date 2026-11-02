package com.timemoredot.app

import com.timemoredot.brew.BrewSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SharedPrefsAppPrefsTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun `기본값`() {
        assertEquals(BrewSettings(), SharedPrefsAppPrefs(app).brewSettings.value)
    }

    @Test fun `setBrewSettings 왕복`() {
        val saved = BrewSettings(autoStartEnabled = false, autoStartThresholdGrams = 2.0)
        val prefs = SharedPrefsAppPrefs(app)
        prefs.setBrewSettings(saved)
        assertEquals(saved, prefs.brewSettings.value)
        assertEquals(saved, SharedPrefsAppPrefs(app).brewSettings.value)   // 새 인스턴스가 저장소에서 읽는다
    }

    @Test fun `모델 경고 닫기는 주소별이다`() {
        val prefs = SharedPrefsAppPrefs(app)
        assertFalse(prefs.isModelWarningDismissed("A"))
        prefs.dismissModelWarning("A")
        assertTrue(prefs.isModelWarningDismissed("A"))
        assertFalse(prefs.isModelWarningDismissed("B"))
        prefs.dismissModelWarning("B")
        assertTrue(SharedPrefsAppPrefs(app).isModelWarningDismissed("A"))
        assertTrue(SharedPrefsAppPrefs(app).isModelWarningDismissed("B"))
    }

    @Test fun `valveHaptic false 왕복`() {
        val prefs = SharedPrefsAppPrefs(app)
        prefs.setBrewSettings(BrewSettings(valveHaptic = false))
        assertFalse(SharedPrefsAppPrefs(app).brewSettings.value.valveHaptic)
    }

    @Test fun `valve_haptic 키가 없는 기존 prefs는 켜짐`() {
        app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("auto_start_enabled", false).putFloat("auto_start_threshold_grams", 0.5f).commit()
        val s = SharedPrefsAppPrefs(app).brewSettings.value
        assertTrue(s.valveHaptic)
        assertEquals(BrewSettings(autoStartEnabled = false, autoStartThresholdGrams = 0.5, valveHaptic = true), s)
    }
}
