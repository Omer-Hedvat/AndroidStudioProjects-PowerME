package com.powerme.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.powerme.app.data.database.HealthConnectSync
import com.powerme.app.data.database.HealthConnectSyncDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Manager for Health Connect integration.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSyncDao: HealthConnectSyncDao
) {

    /**
     * Check if Health Connect is available on this device.
     */
    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Returns true if the given record type's READ permission is granted.
     */
    private suspend fun hasPermission(client: HealthConnectClient, record: KClass<out Record>): Boolean {
        return HealthPermission.getReadPermission(record) in
            client.permissionController.getGrantedPermissions()
    }

    /**
     * D2: Returns true if READ_WEIGHT, READ_BODY_FAT and READ_HEIGHT permissions are granted.
     */
    suspend fun checkPermissionsGranted(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext false
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            HealthPermission.getReadPermission(WeightRecord::class) in granted &&
                HealthPermission.getReadPermission(BodyFatRecord::class) in granted &&
                HealthPermission.getReadPermission(HeightRecord::class) in granted
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Read the most recent weight entry from Health Connect (last 30 days).
     * Returns value in kg, or null if unavailable/not granted.
     */
    suspend fun getLatestWeight(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, WeightRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read the most recent height entry from Health Connect (last 365 days).
     * Returns value in cm as Float, or null if unavailable/not granted.
     */
    suspend fun getLatestHeight(): Float? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeightRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(365, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    HeightRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records.maxByOrNull { it.time }?.height?.inMeters?.times(100)?.toFloat()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read the most recent body fat entry from Health Connect (last 30 days).
     * Returns percentage value, or null if unavailable/not granted.
     */
    suspend fun getLatestBodyFat(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, BodyFatRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    BodyFatRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sync health data from Health Connect.
     * Reads sleep, HRV, RHR, and steps data for the current day.
     */
    suspend fun syncHealthData() = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext
            }

            val today = LocalDate.now()

            val sleepDuration = getSleepDurationMinutes()
            val hrv = getHeartRateVariability()
            val rhr = getRestingHeartRate()
            val steps = getSteps()

            // Calculate flags
            val previousSync = healthConnectSyncDao.getLatestSync()
            val highFatigueFlag = sleepDuration?.let { it < 420 } ?: false // < 7 hours
            val anomalousRecoveryFlag = if (previousSync?.hrv != null && hrv != null) {
                val hrvDrop = ((previousSync.hrv - hrv) / previousSync.hrv) * 100
                hrvDrop > 10.0
            } else {
                false
            }

            val sync = HealthConnectSync(
                date = today,
                sleepDurationMinutes = sleepDuration,
                hrv = hrv,
                rhr = rhr,
                steps = steps,
                highFatigueFlag = highFatigueFlag,
                anomalousRecoveryFlag = anomalousRecoveryFlag,
                syncTimestamp = System.currentTimeMillis()
            )

            healthConnectSyncDao.insertSync(sync)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Read the most recently completed sleep session from Health Connect (last 30 days).
     * Returns total sleep duration in minutes, or null if unavailable/not granted.
     */
    private suspend fun getSleepDurationMinutes(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, SleepSessionRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records
                .maxByOrNull { it.endTime }
                ?.let { ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read the most recent HRV (RMSSD) reading from Health Connect (last 30 days).
     * Returns value in milliseconds, or null if unavailable/not granted.
     */
    private suspend fun getHeartRateVariability(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeartRateVariabilityRmssdRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read the most recent resting heart rate from Health Connect (last 30 days).
     * Returns value in bpm, or null if unavailable/not granted.
     */
    private suspend fun getRestingHeartRate(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, RestingHeartRateRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    TimeRangeFilter.between(start, end)
                )
            )
            response.records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read today's total step count from Health Connect.
     * Sums all StepsRecord intervals from local midnight to now.
     * Returns null if unavailable, not granted, or no steps recorded today.
     */
    private suspend fun getSteps(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, StepsRecord::class)) return@withContext null
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    TimeRangeFilter.between(todayStart, now)
                )
            )
            val total = response.records.sumOf { it.count }
            if (total > 0) total.toInt() else null
        } catch (e: Exception) {
            null
        }
    }
}
