package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getSettings(): Flow<UserSettings?>

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettingsOnce(): UserSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettings)

    @Update
    suspend fun updateSettings(settings: UserSettings)

    @Query("UPDATE user_settings SET availablePlates = :plates WHERE id = 1")
    suspend fun updateAvailablePlates(plates: String)

    @Query("UPDATE user_settings SET restTimerAudioEnabled = :enabled WHERE id = 1")
    suspend fun updateRestTimerAudio(enabled: Boolean)

    @Query("UPDATE user_settings SET restTimerHapticsEnabled = :enabled WHERE id = 1")
    suspend fun updateRestTimerHaptics(enabled: Boolean)

    @Query("UPDATE user_settings SET language = :language WHERE id = 1")
    suspend fun updateLanguage(language: String)
}
