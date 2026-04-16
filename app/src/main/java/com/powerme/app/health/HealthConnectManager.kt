package com.powerme.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
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

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val metricLogRepository: MetricLogRepository,
    private val userSessionManager: UserSessionManager
) {

    companion object {
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
         * Includes WRITE_EXERCISE — best-effort: denied write never blocks "Connected" state.
         */
        val ALL_PERMISSIONS: Set<String> = CORE_PERMISSIONS + setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
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
                ((previousSync.hrv - hrv) / previousSync.hrv) * 100 > 10.0
            } else false
            healthConnectSyncDao.insertSync(HealthConnectSync(
                date = today, sleepDurationMinutes = sleepDuration, hrv = hrv, rhr = rhr, steps = steps,
                highFatigueFlag = highFatigueFlag, anomalousRecoveryFlag = anomalousRecoveryFlag,
                syncTimestamp = System.currentTimeMillis()
            ))
        } catch (e: Exception) { e.printStackTrace() }
    }

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
                    syncTimestamp = System.currentTimeMillis()
                ))
                result.weight?.let { metricLogRepository.upsertTodayIfChanged(MetricType.WEIGHT, it) }
                result.bodyFat?.let { metricLogRepository.upsertTodayIfChanged(MetricType.BODY_FAT, it) }
                result.height?.let { metricLogRepository.upsertTodayIfChanged(MetricType.HEIGHT, it.toDouble()) }
                userSessionManager.updateBodyMetricsFromHc(
                    weightKg = result.weight, bodyFatPercent = result.bodyFat, heightCm = result.height?.toDouble()
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
        return result
    }

    suspend fun getSleepDurationMinutes(): Int? = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) return@withContext null
            val client = HealthConnectClient.getOrCreate(context)
            if (!hasPermission(client, SleepSessionRecord::class)) return@withContext null
            val end = Instant.now(); val start = end.minus(30, ChronoUnit.DAYS)
            client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end)))
                .records.maxByOrNull { it.endTime }
                ?.let { ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt() }
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
