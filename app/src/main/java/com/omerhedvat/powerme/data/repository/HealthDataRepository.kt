package com.omerhedvat.powerme.data.repository

/**
 * Abstraction layer for Health Connect data.
 * Implementation reads from HealthConnectSync table (already in DB v9).
 * ContextInjector calls this to build the Coach Carter + Boaz data segments.
 */
interface HealthDataRepository {
    suspend fun getLatestSteps(): Int?
    suspend fun getLatestSleepHours(): Float?
    suspend fun getLatestWeight(): Float?
    suspend fun getLatestHRV(): Double?
    suspend fun getLatestRestingHeartRate(): Int?
    suspend fun isHighFatigueFlag(): Boolean    // sleep < 7h
    suspend fun isAnomalousRecoveryFlag(): Boolean  // HRV drop > 10%
}
