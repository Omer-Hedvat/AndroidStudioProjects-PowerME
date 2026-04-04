package com.powerme.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    val themeMode: Flow<ThemeMode> = ctx.dataStore.data.map {
        it[THEME_MODE_KEY]?.let { name -> ThemeMode.entries.firstOrNull { e -> e.name == name } } ?: ThemeMode.LIGHT
    }
    val language: Flow<String> = ctx.dataStore.data.map { it[LANGUAGE_KEY] ?: "Hebrew" }
    val warRoomModel: Flow<String> = ctx.dataStore.data.map {
        it[WAR_ROOM_MODEL_KEY] ?: "gemini-2.0-flash"
    }
    val enrichmentModel: Flow<String> = ctx.dataStore.data.map {
        it[ENRICHMENT_MODEL_KEY] ?: "gemini-1.5-flash"
    }
    val keepScreenOn: Flow<Boolean> = ctx.dataStore.data.map { it[KEEP_SCREEN_ON_KEY] ?: true }
    val modelsLastFetched: Flow<Long> = ctx.dataStore.data.map { it[MODELS_LAST_FETCHED_KEY] ?: 0L }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    val darkModeEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[DARK_MODE_KEY] ?: true }

    suspend fun setThemeMode(value: ThemeMode) = ctx.dataStore.edit { it[THEME_MODE_KEY] = value.name }
    suspend fun setLanguage(value: String) = ctx.dataStore.edit { it[LANGUAGE_KEY] = value }
    suspend fun setWarRoomModel(value: String) = ctx.dataStore.edit { it[WAR_ROOM_MODEL_KEY] = value }
    suspend fun setEnrichmentModel(value: String) = ctx.dataStore.edit { it[ENRICHMENT_MODEL_KEY] = value }
    suspend fun setKeepScreenOn(value: Boolean) = ctx.dataStore.edit { it[KEEP_SCREEN_ON_KEY] = value }
    suspend fun setModelsLastFetched(ts: Long) = ctx.dataStore.edit { it[MODELS_LAST_FETCHED_KEY] = ts }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    suspend fun setDarkModeEnabled(value: Boolean) = ctx.dataStore.edit { it[DARK_MODE_KEY] = value }

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val WAR_ROOM_MODEL_KEY = stringPreferencesKey("war_room_model")
        val ENRICHMENT_MODEL_KEY = stringPreferencesKey("enrichment_model")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val MODELS_LAST_FETCHED_KEY = longPreferencesKey("models_last_fetched")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")
    }
}
