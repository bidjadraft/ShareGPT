package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

enum class RetentionPeriod(val label: String, val explanation: String, val durationMs: Long?) {
    DONT_KEEP("Don't keep", "Deleted when you leave the chat.", 0L),
    HOURS_24("24 hours", "Auto-deleted after one day.", 24 * 60 * 60 * 1000L),
    DAYS_7("7 days", "Auto-deleted after one week.", 7 * 24 * 60 * 60 * 1000L),
    DAYS_30("30 days", "Auto-deleted after one month.", 30L * 24 * 60 * 60 * 1000L),
    NEVER("Never delete", "Kept until you delete them manually.", null);

    companion object {
        fun fromLabel(label: String): RetentionPeriod {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: DAYS_7
        }
    }
}

enum class AppThemeMode(val displayName: String) {
    DYNAMIC("Dynamic"),
    CARBON("Carbon"),
    LIGHT("Light"),
    DARK("Dark"),
    WARM("Warm");

    companion object {
        fun fromName(name: String): AppThemeMode {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: WARM
        }
    }
}

enum class AppFontSize(val displayName: String, val scale: Float) {
    SMALL("Small", 0.88f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f);

    companion object {
        fun fromName(name: String): AppFontSize {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: MEDIUM
        }
    }
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_DEFAULT_MODEL = stringPreferencesKey("default_gemini_model")
        val KEY_RETENTION = stringPreferencesKey("conversation_retention")
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_FONT_SIZE = stringPreferencesKey("app_font_size")
        val KEY_AUTO_EXECUTE_ON_FILE = booleanPreferencesKey("auto_execute_prompt_on_file")
    }

    val defaultModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_MODEL] ?: ""
    }

    val retentionPeriodFlow: Flow<RetentionPeriod> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_RETENTION] ?: RetentionPeriod.DAYS_7.label
        RetentionPeriod.fromLabel(raw)
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_THEME] ?: AppThemeMode.WARM.displayName
        AppThemeMode.fromName(raw)
    }

    val fontSizeFlow: Flow<AppFontSize> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_FONT_SIZE] ?: AppFontSize.MEDIUM.displayName
        AppFontSize.fromName(raw)
    }

    val autoExecuteOnFileFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_EXECUTE_ON_FILE] ?: false
    }

    suspend fun setDefaultModel(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_MODEL] = modelName
        }
    }

    suspend fun setRetentionPeriod(period: RetentionPeriod) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RETENTION] = period.label
        }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = themeMode.displayName
        }
    }

    suspend fun setFontSize(fontSize: AppFontSize) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FONT_SIZE] = fontSize.displayName
        }
    }

    suspend fun setAutoExecuteOnFile(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_EXECUTE_ON_FILE] = enabled
        }
    }
}
