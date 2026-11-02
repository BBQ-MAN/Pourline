package com.timemoredot.app

import android.content.Context
import com.timemoredot.brew.BrewSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** architecture.md 8.1. ViewModel·컨트롤러는 이 interface만 받는다. */
interface AppPrefs {
    val brewSettings: StateFlow<BrewSettings>
    fun setBrewSettings(settings: BrewSettings)
    fun isModelWarningDismissed(address: String): Boolean
    fun dismissModelWarning(address: String)
}

class SharedPrefsAppPrefs(context: Context) : AppPrefs {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        BrewSettings(
            autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, true),
            autoStartThresholdGrams = prefs.getFloat(KEY_THRESHOLD, 1.0f).toDouble(),
            valveHaptic = prefs.getBoolean(KEY_VALVE_HAPTIC, true),   // 키가 없는 기존 prefs는 켜짐
        ),
    )
    override val brewSettings: StateFlow<BrewSettings> = _settings.asStateFlow()

    override fun setBrewSettings(settings: BrewSettings) {
        _settings.value = settings
        prefs.edit().putBoolean(KEY_AUTO_START, settings.autoStartEnabled)
            .putFloat(KEY_THRESHOLD, settings.autoStartThresholdGrams.toFloat())
            .putBoolean(KEY_VALVE_HAPTIC, settings.valveHaptic).apply()   // commit() 금지(메인 스레드)
    }

    override fun isModelWarningDismissed(address: String): Boolean =
        prefs.getStringSet(KEY_DISMISSED, emptySet())?.contains(address) == true

    override fun dismissModelWarning(address: String) {
        val next = (prefs.getStringSet(KEY_DISMISSED, emptySet()) ?: emptySet()) + address   // getStringSet 결과는 고치지 않고 복사한다
        prefs.edit().putStringSet(KEY_DISMISSED, next).apply()
    }

    private companion object {
        const val KEY_AUTO_START = "auto_start_enabled"
        const val KEY_THRESHOLD = "auto_start_threshold_grams"
        const val KEY_DISMISSED = "model_warning_dismissed"
        const val KEY_VALVE_HAPTIC = "valve_haptic"
    }
}
