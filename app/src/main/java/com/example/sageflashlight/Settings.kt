package com.example.sageflashlight

import android.content.Context
import android.content.SharedPreferences

data class Settings(
    val showStatusBar: Boolean = true,
    val soundEffects: Boolean = true,
    val turnOnStartup: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "sage_flashlight_settings"
        private const val KEY_SHOW_STATUS_BAR = "show_status_bar"
        private const val KEY_SOUND_EFFECTS = "sound_effects"
        private const val KEY_TURN_ON_STARTUP = "turn_on_startup"

        fun load(context: Context): Settings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return Settings(
                showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, true),
                soundEffects = prefs.getBoolean(KEY_SOUND_EFFECTS, true),
                turnOnStartup = prefs.getBoolean(KEY_TURN_ON_STARTUP, false)
            )
        }

        fun save(context: Context, settings: Settings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_SHOW_STATUS_BAR, settings.showStatusBar)
                putBoolean(KEY_SOUND_EFFECTS, settings.soundEffects)
                putBoolean(KEY_TURN_ON_STARTUP, settings.turnOnStartup)
                apply()
            }
        }
    }
}
