package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.WarmupLog
import com.omerhedvat.powerme.data.database.WarmupLogDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarmupRepository @Inject constructor(
    private val warmupLogDao: WarmupLogDao
) {
    suspend fun getLast10Warmups(): List<WarmupLog> {
        return warmupLogDao.getLast10Warmups()
    }

    fun getWarmupsForWorkout(workoutId: Long): Flow<List<WarmupLog>> {
        return warmupLogDao.getWarmupsForWorkout(workoutId)
    }

    suspend fun insertWarmup(warmupLog: WarmupLog): Long {
        return warmupLogDao.insertWarmup(warmupLog)
    }

    suspend fun insertWarmups(warmupLogs: List<WarmupLog>) {
        warmupLogDao.insertWarmups(warmupLogs)
    }

    suspend fun deleteOldWarmups(beforeTimestamp: Long) {
        warmupLogDao.deleteOldWarmups(beforeTimestamp)
    }
}
