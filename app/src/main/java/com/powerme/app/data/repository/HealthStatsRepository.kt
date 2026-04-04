package com.powerme.app.data.repository

import com.powerme.app.data.database.HealthStats
import com.powerme.app.data.database.HealthStatsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthStatsRepository @Inject constructor(
    private val healthStatsDao: HealthStatsDao
) {
    suspend fun getLatestHealthStats(): HealthStats? {
        return healthStatsDao.getLatestHealthStats()
    }
}
