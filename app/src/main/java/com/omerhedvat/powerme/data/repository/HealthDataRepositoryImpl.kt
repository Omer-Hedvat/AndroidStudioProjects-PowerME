package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.HealthConnectSyncDao
import com.omerhedvat.powerme.data.database.HealthStatsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataRepositoryImpl @Inject constructor(
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val healthStatsDao: HealthStatsDao
) : HealthDataRepository {

    override suspend fun getLatestSteps(): Int? {
        return healthConnectSyncDao.getLatestSync()?.steps
    }

    override suspend fun getLatestSleepHours(): Float? {
        val sync = healthConnectSyncDao.getLatestSync()
        return sync?.sleepDurationMinutes?.let { it / 60f }
    }

    override suspend fun getLatestWeight(): Float? {
        // Weight tracking not yet in HealthStats schema — returns null until integrated
        return null
    }

    override suspend fun getLatestHRV(): Double? {
        return healthConnectSyncDao.getLatestSync()?.hrv
    }

    override suspend fun getLatestRestingHeartRate(): Int? {
        return healthConnectSyncDao.getLatestSync()?.rhr
    }

    override suspend fun isHighFatigueFlag(): Boolean {
        return healthConnectSyncDao.getLatestSync()?.highFatigueFlag ?: false
    }

    override suspend fun isAnomalousRecoveryFlag(): Boolean {
        return healthConnectSyncDao.getLatestSync()?.anomalousRecoveryFlag ?: false
    }
}
