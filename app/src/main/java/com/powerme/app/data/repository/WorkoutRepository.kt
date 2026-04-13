package com.powerme.app.data.repository

import androidx.room.withTransaction
import com.powerme.app.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun String.splitCsv(count: Int): List<String> {
    if (isEmpty()) return emptyList()
    val parts = split(",")
    return List(count) { i -> parts.getOrNull(i)?.trim() ?: "" }
}

private fun String.splitCsvTypes(count: Int): List<SetType> {
    if (isEmpty()) return emptyList()
    val parts = split(",")
    return List(count) { i ->
        parts.getOrNull(i)?.let { runCatching { SetType.valueOf(it.trim()) }.getOrNull() } ?: SetType.NORMAL
    }
}

data class WorkoutBootstrap(
    val workoutId: String,
    val ghostMap: Map<Long, List<WorkoutSet>>,
    val workoutSets: List<WorkoutSet>   // carries pre-generated UUID IDs
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val routineDao: RoutineDao,
    private val database: PowerMeDatabase
) {
    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts()
    }

    fun getAllCompletedWorkoutsWithExerciseNames(): Flow<List<WorkoutExerciseNameRow>> {
        return workoutDao.getAllCompletedWorkoutsWithExerciseNames()
    }

    suspend fun getWorkoutById(workoutId: String): Workout? {
        return workoutDao.getWorkoutById(workoutId)
    }

    suspend fun insertWorkout(workout: Workout) {
        workoutDao.insertWorkout(workout)
    }

    suspend fun updateWorkout(workout: Workout) {
        workoutDao.updateWorkout(workout)
    }

    suspend fun deleteWorkout(workout: Workout) {
        workoutDao.deleteWorkout(workout)
    }

    fun getSetsForWorkout(workoutId: String): Flow<List<WorkoutSet>> {
        return workoutSetDao.getSetsForWorkout(workoutId)
    }

    fun getSetsForExerciseInWorkout(workoutId: String, exerciseId: Long): Flow<List<WorkoutSet>> {
        return workoutSetDao.getSetsForExerciseInWorkout(workoutId, exerciseId)
    }

    suspend fun insertSet(workoutSet: WorkoutSet) {
        workoutSetDao.insertSet(workoutSet)
    }

    suspend fun insertSets(workoutSets: List<WorkoutSet>) {
        workoutSetDao.insertSets(workoutSets)
    }

    suspend fun updateSet(workoutSet: WorkoutSet) {
        workoutSetDao.updateSet(workoutSet)
    }

    suspend fun deleteSet(workoutSet: WorkoutSet) {
        workoutSetDao.deleteSet(workoutSet)
    }

    suspend fun createEmptyWorkout(routineId: String?): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        workoutDao.insertWorkout(
            Workout(id = id, routineId = routineId, timestamp = now,
                durationSeconds = 0, totalVolume = 0.0, startTimeMs = now, updatedAt = now)
        )
        return id
    }

    suspend fun createWorkoutSet(ws: WorkoutSet) {
        workoutSetDao.insertSet(ws)
    }

    suspend fun instantiateWorkoutFromRoutine(routineId: String): WorkoutBootstrap {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val routineExercises = routineExerciseDao.getForRoutine(routineId)
            val workoutId = UUID.randomUUID().toString()
            val routineName = routineDao.getRoutineById(routineId)?.name
            workoutDao.insertWorkout(
                Workout(id = workoutId, routineId = routineId, routineName = routineName,
                    timestamp = now, durationSeconds = 0, totalVolume = 0.0,
                    startTimeMs = now, updatedAt = now)
            )
            val sets = routineExercises.flatMap { re ->
                val storedWeights = re.setWeightsJson.splitCsv(re.sets)
                val storedReps = re.setRepsJson.splitCsv(re.sets)
                val storedTypes = re.setTypesJson.splitCsvTypes(re.sets)
                (1..re.sets).map { i ->
                    WorkoutSet(
                        id = UUID.randomUUID().toString(),
                        workoutId = workoutId,
                        exerciseId = re.exerciseId,
                        setOrder = i,
                        weight = storedWeights.getOrNull(i - 1)?.toDoubleOrNull()
                            ?: re.defaultWeight.toDoubleOrNull() ?: 0.0,
                        reps = storedReps.getOrNull(i - 1)?.toIntOrNull() ?: re.reps,
                        setType = storedTypes.getOrNull(i - 1) ?: SetType.NORMAL,
                        supersetGroupId = re.supersetGroupId
                    )
                }
            }
            workoutSetDao.insertSets(sets)
            // Query back to obtain the full inserted set list for Iron Vault wiring
            val workoutSets = workoutSetDao.getSetsForWorkout(workoutId).first()
            val ghostMap = routineExercises.associate { re ->
                re.exerciseId to workoutSetDao.getPreviousSessionSets(re.exerciseId, now)
            }
            WorkoutBootstrap(workoutId = workoutId, ghostMap = ghostMap, workoutSets = workoutSets)
        }
    }
}
