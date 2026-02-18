package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HealthConnectSyncDao {
    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 30")
    fun getRecentSyncs(): Flow<List<HealthConnectSync>>

    @Query("SELECT * FROM health_connect_sync WHERE date = :date")
    suspend fun getSyncForDate(date: LocalDate): HealthConnectSync?

    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSync(): HealthConnectSync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: HealthConnectSync)

    @Update
    suspend fun updateSync(sync: HealthConnectSync)

    @Query("DELETE FROM health_connect_sync WHERE date < :cutoffDate")
    suspend fun deleteOldSyncs(cutoffDate: LocalDate)

    @Query("""
        SELECT * FROM health_connect_sync
        WHERE highFatigueFlag = 1 OR anomalousRecoveryFlag = 1
        ORDER BY date DESC LIMIT 7
    """)
    suspend fun getRecentFlags(): List<HealthConnectSync>
}
