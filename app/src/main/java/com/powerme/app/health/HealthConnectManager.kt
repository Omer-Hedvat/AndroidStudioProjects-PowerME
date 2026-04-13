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
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

data class HealthConnectReadResult(
    val weight: Double?,
    val height: Float?,
    val bodyFat: Double?,
    val sleepMinutes: Int?,
    val hrv: Double?,
    val rhr: Int?,
    val steps: Int?,
    val lastSyncTimestamp: Long?
)

/**
 * Manager for Health Connect integration.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val metricLogRepository: MetricLogRepository,
    private val userSessionManager: UserSessionManager
) {

    companion object {
        val ALL_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
        )
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    private suspend fun hasPermission(client: HealthConnectClient, record: KClass<out Record>): Boolean {
        return HealthPermission.getReadPermission(record) in
            client.permissionController.getGrantedPermissions()
    }

    suspend fun checkPermissionsGranted(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext false
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            ALL_PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            android.util.Log.w("PowerME_HC", "checkPermissionsGranted failed", e)
            false
        }
    }

    suspend fun getLatestWeight(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, WeightRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLatestHeight(): Float? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeightRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(365, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records.maxByOrNull { it.time }?.height?.inMeters?.times(100)?.toFloat()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLatestBodyFat(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, BodyFatRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncHealthData() = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext
            val today = LocalDate.now()
            val sleepDuration = getSleepDurationMinutes()
            val hrv = getHeartRateVariability()
            val rhr = getRestingHeartRate()
            val steps = getSteps()
            val previousSync = healthConnectSyncDao.getLatestSync()
            val highFatigueFlag = sleepDuration?.let { it < 420 } ?: false
            val anomalousRecoveryFlag = if (previousSync?.hrv != null && hrv != null) {
                val hrvDrop = ((previousSync.hrv - hrv) / previousSync.hrv) * 100
                hrvDrop > 10.0
            } else {
                false
            }
            healthConnectSyncDao.insertSync(
                HealthConnectSync(
                    date = today,
                    sleepDurationMinutes = sleepDuration,
                    hrv = hrv,
                    rhr = rhr,
                    steps = steps,
                    highFatigueFlag = highFatigueFlag,
                    anomalousRecoveryFlag = anomalousRecoveryFlag,
                    syncTimestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reads all 7 data types concurrently and returns the result.
     * Does NOT write to Room — use syncAndRead() for a full sync + read.
     */
    suspend fun readAllData(): HealthConnectReadResult = coroutineScope {
        val weight       = async { getLatestWeight() }
        val height       = async { getLatestHeight() }
        val bodyFat      = async { getLatestBodyFat() }
        val sleepMinutes = async { getSleepDurationMinutes() }
        val hrv          = async { getHeartRateVariability() }
        val rhr          = async { getRestingHeartRate() }
        val steps        = async { getSteps() }
        val lastSync     = async { healthConnectSyncDao.getLatestSync()?.syncTimestamp }
        HealthConnectReadResult(
            weight = weight.await(), height = height.await(), bodyFat = bodyFat.await(),
            sleepMinutes = sleepMinutes.await(), hrv = hrv.await(), rhr = rhr.await(),
            steps = steps.await(), lastSyncTimestamp = lastSync.await()
        )
    }

    /**
     * Full sync: reads all 7 data types once (in parallel), writes the daily Room record,
     * and returns the result.
     */
    suspend fun syncAndRead(): HealthConnectReadResult {
        val result = readAllData()
        withContext(Dispatchers.IO) {
            try {
                val today = LocalDate.now()
                val previousSync = healthConnectSyncDao.getLatestSync()
                val highFatigueFlag = result.sleepMinutes?.let { it < 420 } ?: false
                val anomalousRecoveryFlag = if (previousSync?.hrv != null && result.hrv != null) {
                    ((previousSync.hrv - result.hrv) / previousSync.hrv) * 100 > 10.0
                } else false
                healthConnectSyncDao.insertSync(
                    HealthConnectSync(
                        date = today,
                        sleepDurationMinutes = result.sleepMinutes,
                        hrv = result.hrv, rhr = result.rhr, steps = result.steps,
                        highFatigueFlag = highFatigueFlag,
                        anomalousRecoveryFlag = anomalousRecoveryFlag,
                        syncTimestamp = System.currentTimeMillis()
                    )
                )
                result.weight?.let { metricLogRepository.upsertTodayIfChanged(MetricType.WEIGHT, it) }
                result.bodyFat?.let { metricLogRepository.upsertTodayIfChanged(MetricType.BODY_FAT, it) }
                result.height?.let { metricLogRepository.upsertTodayIfChanged(MetricType.HEIGHT, it.toDouble()) }
                userSessionManager.updateBodyMetricsFromHc(
                    weightKg = result.weight,
                    bodyFatPercent = result.bodyFat,
                    heightCm = result.height?.toDouble()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    suspend fun getSleepDurationMinutes(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, SleepSessionRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records
                .maxByOrNull { it.endTime }
                ?.let { ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getHeartRateVariability(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeartRateVariabilityRmssdRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRestingHeartRate(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, RestingHeartRateRecord::class)) return@withContext null
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val response = client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end))
            )
            response.records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSteps(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, StepsRecord::class)) return@withContext null
            val todayStart = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(todayStart, now))
            )
            val total = response.records.sumOf { it.count }
            if (total > 0) total.toInt() else null
        } catch (e: Exception) {
            null
        }
    }
}
