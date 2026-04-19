package com.powerme.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.HealthConnectSync
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.ui.workout.ExerciseWithSets
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

data class SleepData(
    val minutes: Int,
    val deepMinutes: Int?,
    val remMinutes: Int?,
    val lightMinutes: Int?,
    val awakeMinutes: Int?,
    val efficiency: Float?,
    val score: Int?
)

data class HeartRateData(
    val avgBpm: Int,
    val peakBpm: Int,
    val zones: HrZoneDistribution
)

data class HealthConnectReadResult(
    val weight: Double?,
    val height: Float?,
    val bodyFat: Double?,
    val sleepMinutes: Int?,
    val hrv: Double?,
    val rhr: Int?,
    val steps: Int?,
    val lastSyncTimestamp: Long?,
    // Extended reads (v43+)
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepEfficiency: Float? = null,
    val sleepScore: Int? = null,
    val sleepRespiratoryRate: Double? = null,
    val elevatedRespiratoryRateFlag: Boolean = false,
    val avgHeartRateBpm: Int? = null,
    val peakHeartRateBpm: Int? = null,
    val hrZone1Pct: Float? = null,
    val hrZone2Pct: Float? = null,
    val hrZone3Pct: Float? = null,
    val hrZone4Pct: Float? = null,
    val hrZone5Pct: Float? = null,
    val activeCaloriesKcal: Double? = null,
    val vo2MaxMlKgMin: Double? = null,
    val distanceMetres: Double? = null,
    val spo2Percent: Double? = null,
    val lowSpO2Flag: Boolean = false
)

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val metricLogRepository: MetricLogRepository,
    private val userSessionManager: UserSessionManager
) {

    companion object {
        private const val TAG = "PowerME_HC"

        /** The 7 read permissions that determine "Connected" status in the UI. */
        val CORE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
        )

        /**
         * Full permission set requested in the Connect flow.
         * Exercise read/write and all extended reads are optional — denied permissions never block "Connected" state.
         */
        val ALL_PERMISSIONS: Set<String> = CORE_PERMISSIONS + setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        )
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private suspend fun hasPermission(client: HealthConnectClient, record: KClass<out Record>): Boolean =
        HealthPermission.getReadPermission(record) in client.permissionController.getGrantedPermissions()

    suspend fun checkPermissionsGranted(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext false
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            CORE_PERMISSIONS.all { it in granted }
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
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.weight?.inKilograms
        } catch (e: Exception) { null }
    }

    suspend fun getLatestHeight(): Float? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeightRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(365, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.height?.inMeters?.times(100)?.toFloat()
        } catch (e: Exception) { null }
    }

    suspend fun getLatestBodyFat(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, BodyFatRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Exception) { null }
    }

    /** Returns all weight readings in [sinceMs, now] as (epochMs, kg) pairs. */
    suspend fun getWeightHistory(sinceMs: Long): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext emptyList()
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, WeightRecord::class)) return@withContext emptyList()
            val start = Instant.ofEpochMilli(sinceMs)
            val end = Instant.now()
            client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end)))
                .records
                .map { it.time.toEpochMilli() to it.weight.inKilograms }
        } catch (e: Exception) { emptyList() }
    }

    /** Returns all body-fat readings in [sinceMs, now] as (epochMs, percent) pairs. */
    suspend fun getBodyFatHistory(sinceMs: Long): List<Pair<Long, Double>> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext emptyList()
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, BodyFatRecord::class)) return@withContext emptyList()
            val start = Instant.ofEpochMilli(sinceMs)
            val end = Instant.now()
            client.readRecords(ReadRecordsRequest(BodyFatRecord::class, TimeRangeFilter.between(start, end)))
                .records
                .map { it.time.toEpochMilli() to it.percentage.value }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun syncHealthData() = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext
            val today = LocalDate.now()
            val sleep = getSleepData()
            val hrv = getHeartRateVariability()
            val rhr = getRestingHeartRate()
            val steps = getSteps()
            val previousSync = healthConnectSyncDao.getLatestSync()
            val highFatigueFlag = sleep?.minutes?.let { it < 420 } ?: false
            val anomalousRecoveryFlag = if (previousSync?.hrv != null && hrv != null) {
                ((previousSync.hrv - hrv) / previousSync.hrv) * 100 > 10.0
            } else false
            healthConnectSyncDao.insertSync(HealthConnectSync(
                date = today, sleepDurationMinutes = sleep?.minutes, hrv = hrv, rhr = rhr, steps = steps,
                highFatigueFlag = highFatigueFlag, anomalousRecoveryFlag = anomalousRecoveryFlag,
                syncTimestamp = System.currentTimeMillis(),
                deepSleepMinutes = sleep?.deepMinutes, remSleepMinutes = sleep?.remMinutes,
                lightSleepMinutes = sleep?.lightMinutes, awakeMinutes = sleep?.awakeMinutes,
                sleepEfficiency = sleep?.efficiency, sleepScore = sleep?.score
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun readAllData(): HealthConnectReadResult = coroutineScope {
        val weight       = async { getLatestWeight() }
        val height       = async { getLatestHeight() }
        val bodyFat      = async { getLatestBodyFat() }
        val sleep        = async { getSleepData() }
        val hrv          = async { getHeartRateVariability() }
        val rhr          = async { getRestingHeartRate() }
        val steps        = async { getSteps() }
        val lastSync     = async { healthConnectSyncDao.getLatestSync()?.syncTimestamp }
        val respRate     = async { getSleepRespiratoryRate() }
        val hrData       = async { getHeartRateToday() }
        val calories     = async { getActiveCaloriesToday() }
        val vo2Max       = async { getLatestVo2Max() }
        val distance     = async { getDistanceToday() }
        val spo2         = async { getLatestSpO2() }

        val sleepResult = sleep.await()
        val respRateResult = respRate.await()
        val hrDataResult = hrData.await()
        val spo2Result = spo2.await()

        HealthConnectReadResult(
            weight = weight.await(), height = height.await(), bodyFat = bodyFat.await(),
            sleepMinutes = sleepResult?.minutes, hrv = hrv.await(), rhr = rhr.await(),
            steps = steps.await(), lastSyncTimestamp = lastSync.await(),
            deepSleepMinutes = sleepResult?.deepMinutes, remSleepMinutes = sleepResult?.remMinutes,
            lightSleepMinutes = sleepResult?.lightMinutes, awakeMinutes = sleepResult?.awakeMinutes,
            sleepEfficiency = sleepResult?.efficiency, sleepScore = sleepResult?.score,
            sleepRespiratoryRate = respRateResult,
            elevatedRespiratoryRateFlag = respRateResult != null && respRateResult > 20.0,
            avgHeartRateBpm = hrDataResult?.avgBpm, peakHeartRateBpm = hrDataResult?.peakBpm,
            hrZone1Pct = hrDataResult?.zones?.z1Pct, hrZone2Pct = hrDataResult?.zones?.z2Pct,
            hrZone3Pct = hrDataResult?.zones?.z3Pct, hrZone4Pct = hrDataResult?.zones?.z4Pct,
            hrZone5Pct = hrDataResult?.zones?.z5Pct,
            activeCaloriesKcal = calories.await(),
            vo2MaxMlKgMin = vo2Max.await(),
            distanceMetres = distance.await(),
            spo2Percent = spo2Result,
            lowSpO2Flag = spo2Result != null && spo2Result < 92.0
        ).also { Log.d(TAG, "readAllData: $it") }
    }

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
                healthConnectSyncDao.insertSync(HealthConnectSync(
                    date = today, sleepDurationMinutes = result.sleepMinutes,
                    hrv = result.hrv, rhr = result.rhr, steps = result.steps,
                    highFatigueFlag = highFatigueFlag, anomalousRecoveryFlag = anomalousRecoveryFlag,
                    syncTimestamp = System.currentTimeMillis(),
                    avgHeartRateBpm = result.avgHeartRateBpm, peakHeartRateBpm = result.peakHeartRateBpm,
                    hrZone1Pct = result.hrZone1Pct, hrZone2Pct = result.hrZone2Pct,
                    hrZone3Pct = result.hrZone3Pct, hrZone4Pct = result.hrZone4Pct, hrZone5Pct = result.hrZone5Pct,
                    activeCaloriesKcal = result.activeCaloriesKcal,
                    vo2MaxMlKgMin = result.vo2MaxMlKgMin,
                    distanceMetres = result.distanceMetres,
                    spo2Percent = result.spo2Percent, lowSpO2Flag = result.lowSpO2Flag,
                    deepSleepMinutes = result.deepSleepMinutes, remSleepMinutes = result.remSleepMinutes,
                    lightSleepMinutes = result.lightSleepMinutes, awakeMinutes = result.awakeMinutes,
                    sleepEfficiency = result.sleepEfficiency, sleepScore = result.sleepScore,
                    sleepRespiratoryRate = result.sleepRespiratoryRate,
                    elevatedRespiratoryRateFlag = result.elevatedRespiratoryRateFlag
                ))
                result.weight?.let { metricLogRepository.upsertTodayIfChanged(MetricType.WEIGHT, it) }
                result.bodyFat?.let { metricLogRepository.upsertTodayIfChanged(MetricType.BODY_FAT, it) }
                result.height?.let { metricLogRepository.upsertTodayIfChanged(MetricType.HEIGHT, it.toDouble()) }
                result.vo2MaxMlKgMin?.let { metricLogRepository.upsertTodayIfChanged(MetricType.VO2_MAX, it) }
                result.spo2Percent?.let { metricLogRepository.upsertTodayIfChanged(MetricType.SPO2, it) }
                userSessionManager.updateBodyMetricsFromHc(
                    weightKg = result.weight, bodyFatPercent = result.bodyFat, heightCm = result.height?.toDouble()
                )
                Log.d(TAG, "syncAndRead: DB write succeeded for ${LocalDate.now()}")
            } catch (e: Exception) {
                Log.e(TAG, "syncAndRead: DB write FAILED — Metrics screen will use live HC values as fallback", e)
            }
        }
        return result
    }

    /** Returns duration + stage breakdown for the most recent sleep session in the last 30 days. */
    suspend fun getSleepData(): SleepData? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, SleepSessionRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            val record = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.endTime } ?: return@withContext null
            val minutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt()
            val breakdown = SleepStageCalculator.extractStages(record.stages)
            val efficiency = breakdown.awakeMinutes?.let { SleepStageCalculator.computeEfficiency(minutes, it) }
            val score = SleepStageCalculator.computeSleepScore(minutes, breakdown)
            SleepData(
                minutes = minutes,
                deepMinutes = breakdown.deepMinutes, remMinutes = breakdown.remMinutes,
                lightMinutes = breakdown.lightMinutes, awakeMinutes = breakdown.awakeMinutes,
                efficiency = efficiency, score = score
            )
        } catch (e: Exception) { null }
    }

    /** Kept for backward compatibility — callers that only need duration can still use this. */
    suspend fun getSleepDurationMinutes(): Int? = getSleepData()?.minutes

    /** Returns average breaths/min during the most recent sleep session (or last 24h fallback). */
    suspend fun getSleepRespiratoryRate(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, RespiratoryRateRecord::class)) return@withContext null
            // Use most recent sleep window; fall back to last 24h
            val end = Instant.now()
            val start30 = end.minus(30, ChronoUnit.DAYS)
            val sleepStart = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start30, end))
            ).records.maxByOrNull { it.endTime }?.startTime ?: end.minus(24, ChronoUnit.HOURS)
            val records = client.readRecords(
                ReadRecordsRequest(RespiratoryRateRecord::class, TimeRangeFilter.between(sleepStart, end))
            ).records
            if (records.isEmpty()) return@withContext null
            records.map { it.rate }.average()
        } catch (e: Exception) { null }
    }

    /** Returns avg and peak bpm + HR zone distribution for today (midnight to now). */
    suspend fun getHeartRateToday(): HeartRateData? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeartRateRecord::class)) return@withContext null
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val records = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(todayStart, Instant.now()))
            ).records
            val allSamples = records.flatMap { it.samples }
            if (allSamples.isEmpty()) return@withContext null
            val age = userSessionManager.getCurrentUser()?.age ?: 30
            val maxHr = HrZoneCalculator.maxHr(age)
            val avgBpm = allSamples.map { it.beatsPerMinute }.average().toInt()
            val peakBpm = allSamples.maxOf { it.beatsPerMinute }.toInt()
            HeartRateData(avgBpm = avgBpm, peakBpm = peakBpm, zones = HrZoneCalculator.aggregateZones(allSamples, maxHr))
        } catch (e: Exception) { null }
    }

    /** Returns active calories burned today (midnight to now), in kcal. */
    suspend fun getActiveCaloriesToday(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, ActiveCaloriesBurnedRecord::class)) return@withContext null
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, Instant.now())
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        } catch (e: Exception) { null }
    }

    /** Returns the most recent VO₂ Max reading in the last 90 days (mL/kg/min). */
    suspend fun getLatestVo2Max(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, Vo2MaxRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(90, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(Vo2MaxRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.vo2MillilitersPerMinuteKilogram
        } catch (e: Exception) { null }
    }

    /** Returns total distance travelled today (midnight to now), in metres. */
    suspend fun getDistanceToday(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, DistanceRecord::class)) return@withContext null
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, Instant.now())
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inMeters
        } catch (e: Exception) { null }
    }

    /** Returns the most recent SpO₂ reading in the last 30 days (percentage 0–100). */
    suspend fun getLatestSpO2(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, OxygenSaturationRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Exception) { null }
    }

    suspend fun getHeartRateVariability(): Double? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, HeartRateVariabilityRmssdRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        } catch (e: Exception) { null }
    }

    suspend fun getRestingHeartRate(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, RestingHeartRateRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        } catch (e: Exception) { null }
    }

    suspend fun getSteps(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, StepsRecord::class)) return@withContext null
            val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(todayStart, Instant.now())
                )
            )
            val total = response[StepsRecord.COUNT_TOTAL]
            if (total != null && total > 0) total.toInt() else null
        } catch (e: Exception) { null }
    }

    /**
     * Writes a completed workout as an [ExerciseSessionRecord] to Health Connect.
     * Fire-and-forget: failures are logged but never surfaced to the user.
     * Uses [Workout.id] as [clientRecordId] to prevent duplicate writes on retry.
     */
    suspend fun writeWorkoutSession(workout: Workout, exercises: List<ExerciseWithSets>) {
        if (!isAvailable()) return
        if (workout.startTimeMs == 0L || workout.endTimeMs == 0L) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in granted) return

            val startInstant = Instant.ofEpochMilli(workout.startTimeMs)
            val endInstant = Instant.ofEpochMilli(workout.endTimeMs)
            val zoneRules = ZoneId.systemDefault().rules

            val record = ExerciseSessionRecord(
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = zoneRules.getOffset(startInstant),
                endZoneOffset = zoneRules.getOffset(endInstant),
                exerciseType = deriveHcExerciseType(exercises),
                title = workout.routineName,
                notes = workout.notes,
                metadata = Metadata.manualEntry(workout.id)
            )
            client.insertRecords(listOf(record))
        } catch (e: Exception) {
            android.util.Log.w("PowerME_HC", "writeWorkoutSession failed", e)
        }
    }

    private fun deriveHcExerciseType(exercises: List<ExerciseWithSets>): Int =
        deriveHcExerciseTypeFromEnums(exercises.map { it.exercise.exerciseType })

    private fun deriveHcExerciseTypeFromEnums(exerciseTypes: List<ExerciseType>): Int = when {
        exerciseTypes.all { it == ExerciseType.STRETCH } -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
        exerciseTypes.any { it == ExerciseType.CARDIO }  -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        else                                              -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
    }

    /**
     * Writes a completed workout as an [ExerciseSessionRecord] to Health Connect.
     * Backfill path — accepts exercise types directly (resolved from WorkoutSetWithExercise).
     * Fire-and-forget: failures are logged but never surfaced to the user.
     * Uses [Workout.id] as [clientRecordId] to prevent duplicate writes on retry.
     */
    private suspend fun writeWorkoutSessionByType(workout: Workout, exerciseTypes: List<ExerciseType>) {
        if (!isAvailable()) return
        if (workout.startTimeMs == 0L || workout.endTimeMs == 0L) return
        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in granted) return

            val startInstant = Instant.ofEpochMilli(workout.startTimeMs)
            val endInstant   = Instant.ofEpochMilli(workout.endTimeMs)
            val zoneRules    = ZoneId.systemDefault().rules

            val record = ExerciseSessionRecord(
                startTime       = startInstant,
                endTime         = endInstant,
                startZoneOffset = zoneRules.getOffset(startInstant),
                endZoneOffset   = zoneRules.getOffset(endInstant),
                exerciseType    = deriveHcExerciseTypeFromEnums(exerciseTypes),
                title           = workout.routineName,
                notes           = workout.notes,
                metadata        = Metadata.manualEntry(workout.id)
            )
            client.insertRecords(listOf(record))
        } catch (e: Exception) {
            android.util.Log.w("PowerME_HC", "writeWorkoutSession (backfill) failed for ${workout.id}", e)
        }
    }

    /**
     * One-time background push of all completed workouts from the last [daysBack] days to Health Connect.
     * Each workout is written individually so a single failure does not abort the loop.
     * [clientRecordId] = [Workout.id] guarantees HC upserts on repeated push (no duplicates).
     */
    suspend fun backfillWorkoutSessions(
        workoutDao: WorkoutDao,
        workoutSetDao: WorkoutSetDao,
        daysBack: Int = 90
    ) {
        if (!isAvailable()) return
        val sinceMs = System.currentTimeMillis() - daysBack.toLong() * 24 * 60 * 60 * 1000
        val workouts = workoutDao.getCompletedWorkoutsSince(sinceMs)
        for (workout in workouts) {
            val exerciseTypes = workoutSetDao
                .getSetsWithExerciseForWorkout(workout.id)
                .map { it.exerciseType }
                .distinct()
            writeWorkoutSessionByType(workout, exerciseTypes)
        }
    }
}
