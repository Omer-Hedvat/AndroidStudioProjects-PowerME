package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthStatsDao {
    @Query("SELECT * FROM health_stats ORDER BY date DESC LIMIT 1")
    suspend fun getLatestHealthStats(): HealthStats?

    @Query("SELECT * FROM health_stats ORDER BY date DESC")
    fun getAllHealthStats(): Flow<List<HealthStats>>

    @Query("SELECT * FROM health_stats WHERE date >= :startDate ORDER BY date DESC")
    fun getHealthStatsSince(startDate: Long): Flow<List<HealthStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthStats(stats: HealthStats)

    @Query("DELETE FROM health_stats WHERE date < :beforeDate")
    suspend fun deleteOldStats(beforeDate: Long)
}
