package com.omerhedvat.powerme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    val language: Flow<String> = ctx.dataStore.data.map { it[LANGUAGE_KEY] ?: "Hebrew" }
    val warRoomModel: Flow<String> = ctx.dataStore.data.map {
        it[WAR_ROOM_MODEL_KEY] ?: "gemini-2.0-flash-thinking-exp"
    }
    val enrichmentModel: Flow<String> = ctx.dataStore.data.map {
        it[ENRICHMENT_MODEL_KEY] ?: "gemini-1.5-flash"
    }

    suspend fun setLanguage(value: String) = ctx.dataStore.edit { it[LANGUAGE_KEY] = value }
    suspend fun setWarRoomModel(value: String) = ctx.dataStore.edit { it[WAR_ROOM_MODEL_KEY] = value }
    suspend fun setEnrichmentModel(value: String) = ctx.dataStore.edit { it[ENRICHMENT_MODEL_KEY] = value }

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val WAR_ROOM_MODEL_KEY = stringPreferencesKey("war_room_model")
        val ENRICHMENT_MODEL_KEY = stringPreferencesKey("enrichment_model")
    }
}
