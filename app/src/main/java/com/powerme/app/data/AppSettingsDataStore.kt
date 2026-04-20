package com.powerme.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.powerme.app.data.UnitSystem
import com.powerme.app.util.TimerSound

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
    val useRpeAutoPop: Flow<Boolean> = ctx.dataStore.data.map { it[USE_RPE_AUTO_POP_KEY] ?: false }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    val darkModeEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[DARK_MODE_KEY] ?: true }
    val dailyStepTarget: Flow<Int> = ctx.dataStore.data.map { it[DAILY_STEP_TARGET_KEY] ?: 10_000 }
    val unitSystem: Flow<UnitSystem> = ctx.dataStore.data.map {
        it[UNIT_SYSTEM_KEY]?.let { name -> UnitSystem.entries.firstOrNull { e -> e.name == name } } ?: UnitSystem.METRIC
    }
    /** True once a cloud restore has been attempted on this install. Prevents repeat restores. */
    val hasRestoredOnce: Flow<Boolean> = ctx.dataStore.data.map { it[HAS_RESTORED_ONCE_KEY] ?: false }
    /** True once the HC workout backfill has been triggered. Prevents re-running on permission revoke + re-grant. */
    val hcWorkoutBackfillDone: Flow<Boolean> = ctx.dataStore.data.map { it[HC_WORKOUT_BACKFILL_DONE_KEY] ?: false }
    /** Seconds of "Get Ready" countdown before a timed exercise timer starts. 0 = disabled (default 3). */
    val timedSetSetupSeconds: Flow<Int> = ctx.dataStore.data.map { it[TIMED_SET_SETUP_SECONDS_KEY] ?: 3 }
    /** True once the user has dismissed the post-login HC offer (skip or connect). Never shown again after set. */
    val hcOfferDismissed: Flow<Boolean> = ctx.dataStore.data.map { it[HC_OFFER_DISMISSED_KEY] ?: false }
    /** Selected alert tone for rest timers and clocks. Default: BEEP. */
    val timerSound: Flow<TimerSound> = ctx.dataStore.data.map {
        it[TIMER_SOUND_KEY]?.let { name -> TimerSound.entries.firstOrNull { e -> e.name == name } } ?: TimerSound.BEEP
    }
    /** Whether to post watch/lock-screen notifications for rest timer events. Default: true. */
    val notificationsEnabled: Flow<Boolean> = ctx.dataStore.data.map { it[NOTIFICATIONS_ENABLED_KEY] ?: true }
    /** Epoch ms of the last successful body stress map computation. 0 = never computed. */
    val lastStressComputedAt: Flow<Long> = ctx.dataStore.data.map { it[LAST_STRESS_COMPUTED_AT_KEY] ?: 0L }

    suspend fun setThemeMode(value: ThemeMode) = ctx.dataStore.edit { it[THEME_MODE_KEY] = value.name }
    suspend fun setLanguage(value: String) = ctx.dataStore.edit { it[LANGUAGE_KEY] = value }
    suspend fun setKeepScreenOn(value: Boolean) = ctx.dataStore.edit { it[KEEP_SCREEN_ON_KEY] = value }
    suspend fun setUseRpeAutoPop(value: Boolean) = ctx.dataStore.edit { it[USE_RPE_AUTO_POP_KEY] = value }
    @Deprecated("App is permanently light-mode. DataStore key retained for schema stability.")
    suspend fun setDarkModeEnabled(value: Boolean) = ctx.dataStore.edit { it[DARK_MODE_KEY] = value }
    suspend fun setDailyStepTarget(value: Int) = ctx.dataStore.edit { it[DAILY_STEP_TARGET_KEY] = value }
    suspend fun setUnitSystem(value: UnitSystem) = ctx.dataStore.edit { it[UNIT_SYSTEM_KEY] = value.name }
    suspend fun setHasRestoredOnce(value: Boolean) = ctx.dataStore.edit { it[HAS_RESTORED_ONCE_KEY] = value }
    suspend fun setHcWorkoutBackfillDone(value: Boolean) = ctx.dataStore.edit { it[HC_WORKOUT_BACKFILL_DONE_KEY] = value }
    suspend fun setTimedSetSetupSeconds(value: Int) = ctx.dataStore.edit { it[TIMED_SET_SETUP_SECONDS_KEY] = value.coerceIn(0, 10) }
    suspend fun setHcOfferDismissed(value: Boolean) = ctx.dataStore.edit { it[HC_OFFER_DISMISSED_KEY] = value }
    suspend fun setTimerSound(value: TimerSound) = ctx.dataStore.edit { it[TIMER_SOUND_KEY] = value.name }
    suspend fun setNotificationsEnabled(value: Boolean) = ctx.dataStore.edit { it[NOTIFICATIONS_ENABLED_KEY] = value }
    suspend fun setLastStressComputedAt(value: Long) = ctx.dataStore.edit { it[LAST_STRESS_COMPUTED_AT_KEY] = value }

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        val USE_RPE_AUTO_POP_KEY = booleanPreferencesKey("use_rpe_auto_pop")
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode_enabled")
        val DAILY_STEP_TARGET_KEY = intPreferencesKey("daily_step_target")
        val UNIT_SYSTEM_KEY = stringPreferencesKey("unit_system")
        val HAS_RESTORED_ONCE_KEY = booleanPreferencesKey("has_restored_once")
        val HC_WORKOUT_BACKFILL_DONE_KEY = booleanPreferencesKey("hc_workout_backfill_done")
        val TIMED_SET_SETUP_SECONDS_KEY = intPreferencesKey("timed_set_setup_seconds")
        val HC_OFFER_DISMISSED_KEY = booleanPreferencesKey("hc_offer_dismissed")
        val TIMER_SOUND_KEY = stringPreferencesKey("timer_sound")
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val LAST_STRESS_COMPUTED_AT_KEY = longPreferencesKey("last_stress_computed_at")
    }
}
