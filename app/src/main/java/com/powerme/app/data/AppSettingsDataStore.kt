package com.powerme.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.powerme.app.data.UnitSystem

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    val themeMode: Flow<ThemeMode> = ctx.dataStore.data.map {
        it[THEME_MODE_KEY]?.let { name -> ThemeMode.entries.firstOrNull { e -> e.name == name } } ?: ThemeMode.SYSTEM
    }
    val language: Flow<String> = ctx.dataStore.data.map { it[LANGUAGE_KEY] ?: "Hebrew" }
    val keepScreenOn: Flow<Boolean> = ctx.dataStore.data.map { it[KEEP_SCREEN_ON_KEY] ?: true }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    val darkModeEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[DARK_MODE_KEY] ?: true }
    val dailyStepTarget: Flow<Int> = ctx.dataStore.data.map { it[DAILY_STEP_TARGET_KEY] ?: 10_000 }
    val unitSystem: Flow<UnitSystem> = ctx.dataStore.data.map {
        it[UNIT_SYSTEM_KEY]?.let { name -> UnitSystem.entries.firstOrNull { e -> e.name == name } } ?: UnitSystem.METRIC
    }
    /** True once a cloud restore has been attempted on this install. Prevents repeat restores. */
    val hasRestoredOnce: Flow<Boolean> = ctx.dataStore.data.map { it[HAS_RESTORED_ONCE_KEY] ?: false }

    suspend fun setThemeMode(value: ThemeMode) = ctx.dataStore.edit { it[THEME_MODE_KEY] = value.name }
    suspend fun setLanguage(value: String) = ctx.dataStore.edit { it[LANGUAGE_KEY] = value }
    suspend fun setKeepScreenOn(value: Boolean) = ctx.dataStore.edit { it[KEEP_SCREEN_ON_KEY] = value }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    suspend fun setDarkModeEnabled(value: Boolean) = ctx.dataStore.edit { it[DARK_MODE_KEY] = value }
    suspend fun setDailyStepTarget(value: Int) = ctx.dataStore.edit { it[DAILY_STEP_TARGET_KEY] = value }
    suspend fun setUnitSystem(value: UnitSystem) = ctx.dataStore.edit { it[UNIT_SYSTEM_KEY] = value.name }
    suspend fun setHasRestoredOnce(value: Boolean) = ctx.dataStore.edit { it[HAS_RESTORED_ONCE_KEY] = value }

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")
        val DAILY_STEP_TARGET_KEY = intPreferencesKey("daily_step_target")
        val UNIT_SYSTEM_KEY = stringPreferencesKey("unit_system")
        val HAS_RESTORED_ONCE_KEY = booleanPreferencesKey("has_restored_once")
    }
}
