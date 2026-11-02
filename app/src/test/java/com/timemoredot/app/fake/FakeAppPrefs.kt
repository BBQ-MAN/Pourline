package com.timemoredot.app.fake

import com.timemoredot.app.AppPrefs
import com.timemoredot.brew.BrewSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppPrefs(initial: BrewSettings = BrewSettings()) : AppPrefs {
    private val _settings = MutableStateFlow(initial)
    private val dismissed = mutableSetOf<String>()
    val calls = mutableListOf<String>()

    override val brewSettings: StateFlow<BrewSettings> = _settings.asStateFlow()
    override fun setBrewSettings(settings: BrewSettings) { calls += "setBrewSettings"; _settings.value = settings }
    override fun isModelWarningDismissed(address: String) = address in dismissed
    override fun dismissModelWarning(address: String) { calls += "dismissModelWarning:$address"; dismissed += address }
}
