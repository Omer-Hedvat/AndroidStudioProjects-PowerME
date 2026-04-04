package com.powerme.app.data.database

import androidx.room.*

@Dao
interface StateHistoryDao {
    @Insert
    suspend fun insertEntry(entry: StateHistoryEntry): Long

    @Query("SELECT * FROM state_history ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentHistory(): List<StateHistoryEntry>

    @Query("SELECT * FROM state_history WHERE documentType = :type ORDER BY timestamp DESC")
    suspend fun getHistoryForType(type: String): List<StateHistoryEntry>
}
