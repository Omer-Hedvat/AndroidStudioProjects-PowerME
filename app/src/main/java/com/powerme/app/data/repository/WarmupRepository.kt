package com.powerme.app.data.repository

import com.powerme.app.data.database.WarmupLog
import com.powerme.app.data.database.WarmupLogDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarmupRepository @Inject constructor(
    private val warmupLogDao: WarmupLogDao
) {
    suspend fun getLast10Warmups(): List<WarmupLog> {
        return warmupLogDao.getLast10Warmups()
    }

    suspend fun insertWarmups(warmupLogs: List<WarmupLog>) {
        warmupLogDao.insertWarmups(warmupLogs)
    }
}
