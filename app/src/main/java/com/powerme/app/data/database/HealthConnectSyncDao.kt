package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Projection for the sleep trend bar chart — date + duration only. */
data class SleepDurationRow(
    val date: LocalDate,
    val sleepDurationMinutes: Int
)

@Dao
interface HealthConnectSyncDao {
    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 30")
    fun getRecentSyncs(): Flow<List<HealthConnectSync>>

    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSync(): HealthConnectSync?

    /** Returns up to 30 nights with recorded sleep, oldest first, for the sleep trend chart. */
    @Query("""
        SELECT date, sleepDurationMinutes
        FROM health_connect_sync
        WHERE sleepDurationMinutes IS NOT NULL
        ORDER BY date ASC
        LIMIT 30
    """)
    suspend fun getSleepHistory(): List<SleepDurationRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: HealthConnectSync)

    @Update
    suspend fun updateSync(sync: HealthConnectSync)
}
