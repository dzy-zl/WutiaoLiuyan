package com.wutiaoliuyan.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString("model", "deepseek-flash") ?: "deepseek-flash"
        set(value) = prefs.edit().putString("model", value.trim()).apply()

    var theme: String
        get() = prefs.getString("theme", "system") ?: "system"
        set(value) = prefs.edit().putString("theme", value).apply()

    var imageRetention: String
        get() = prefs.getString("image_retention", "30d") ?: "30d"
        set(value) = prefs.edit().putString("image_retention", value).apply()

    var aiPreference: String
        get() = prefs.getString("ai_preference", "") ?: ""
        set(value) = prefs.edit().putString("ai_preference", value).apply()

    var defaultReminderMinutes: Int
        get() = prefs.getInt("default_reminder_minutes", 30)
        set(value) = prefs.edit().putInt("default_reminder_minutes", value).apply()

    fun publicSnapshot(): Map<String, Any> = mapOf(
        "model" to model,
        "theme" to theme,
        "imageRetention" to imageRetention,
        "aiPreference" to aiPreference,
        "defaultReminderMinutes" to defaultReminderMinutes
    )
}
