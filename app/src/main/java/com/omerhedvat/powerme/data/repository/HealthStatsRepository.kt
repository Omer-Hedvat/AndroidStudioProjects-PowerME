package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.HealthStats
import com.omerhedvat.powerme.data.database.HealthStatsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthStatsRepository @Inject constructor(
    private val healthStatsDao: HealthStatsDao
) {
    suspend fun getLatestHealthStats(): HealthStats? {
        return healthStatsDao.getLatestHealthStats()
    }

    suspend fun insertHealthStats(stats: HealthStats) {
        healthStatsDao.insertHealthStats(stats)
    }

    suspend fun deleteOldStats(beforeDate: Long) {
        healthStatsDao.deleteOldStats(beforeDate)
    }
}
