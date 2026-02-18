package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MetricLogDao {
    @Query("SELECT * FROM metric_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<MetricLog>>

    @Query("SELECT * FROM metric_log WHERE type = :type ORDER BY timestamp ASC")
    fun getByType(type: String): Flow<List<MetricLog>>

    @Query("SELECT * FROM metric_log WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForType(type: String): MetricLog?

    @Insert
    suspend fun insert(entry: MetricLog): Long

    @Delete
    suspend fun delete(entry: MetricLog)
}
