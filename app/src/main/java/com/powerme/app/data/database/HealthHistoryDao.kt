package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthHistoryDao {

    @Query("SELECT * FROM health_history_entries WHERE isArchived = 0 ORDER BY COALESCE(startDate, createdAt) DESC")
    fun getActiveEntries(): Flow<List<HealthHistoryEntry>>

    @Query("SELECT * FROM health_history_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HealthHistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HealthHistoryEntry)

    @Query("UPDATE health_history_entries SET isArchived = 1, lastModifiedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM health_history_entries WHERE isArchived = 0")
    suspend fun getAllForSync(): List<HealthHistoryEntry>
}
