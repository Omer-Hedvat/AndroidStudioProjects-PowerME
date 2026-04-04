package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthConnectSyncDao {
    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 30")
    fun getRecentSyncs(): Flow<List<HealthConnectSync>>

    @Query("SELECT * FROM health_connect_sync ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSync(): HealthConnectSync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: HealthConnectSync)

    @Update
    suspend fun updateSync(sync: HealthConnectSync)
}
