package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WarmupLogDao {
    @Query("SELECT * FROM warmup_log ORDER BY timestamp DESC LIMIT 10")
    suspend fun getLast10Warmups(): List<WarmupLog>

    @Query("SELECT * FROM warmup_log WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    fun getWarmupsForWorkout(workoutId: String): Flow<List<WarmupLog>>

    @Query("SELECT * FROM warmup_log ORDER BY timestamp DESC")
    fun getAllWarmups(): Flow<List<WarmupLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarmup(warmupLog: WarmupLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarmups(warmupLogs: List<WarmupLog>)

    @Delete
    suspend fun deleteWarmup(warmupLog: WarmupLog)

    @Query("DELETE FROM warmup_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldWarmups(beforeTimestamp: Long)
}
