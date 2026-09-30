package com.attract.attendance.data.theme

import android.content.Context
import android.content.SharedPreferences
import com.attract.attendance.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("attract_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _hasChosenTheme = MutableStateFlow(prefs.getBoolean("has_chosen_theme", false))
    val hasChosenTheme: StateFlow<Boolean> = _hasChosenTheme.asStateFlow()

    private fun getSavedThemeMode(): AppThemeMode {
        val saved = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)
        return runCatching { AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name) }.getOrDefault(AppThemeMode.SYSTEM)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit()
            .putString("theme_mode", mode.name)
            .putBoolean("has_chosen_theme", true)
            .commit()
        _themeMode.value = mode
        _hasChosenTheme.value = true
    }

    fun markThemeChosen() {
        prefs.edit().putBoolean("has_chosen_theme", true).commit()
        _hasChosenTheme.value = true
    }
}
