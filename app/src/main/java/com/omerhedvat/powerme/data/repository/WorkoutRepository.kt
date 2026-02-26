package com.omerhedvat.powerme.data.repository

import androidx.room.withTransaction
import com.omerhedvat.powerme.data.database.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class WorkoutBootstrap(
    val workoutId: Long,
    val ghostMap: Map<Long, List<WorkoutSet>>,
    val workoutSets: List<WorkoutSet>   // carries DB-assigned IDs
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val database: PowerMeDatabase
) {
    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts()
    }

    fun getAllCompletedWorkoutsWithExerciseNames(): Flow<List<WorkoutExerciseNameRow>> {
        return workoutDao.getAllCompletedWorkoutsWithExerciseNames()
    }

    suspend fun getWorkoutById(workoutId: Long): Workout? {
        return workoutDao.getWorkoutById(workoutId)
    }

    suspend fun insertWorkout(workout: Workout): Long {
        return workoutDao.insertWorkout(workout)
    }

    suspend fun updateWorkout(workout: Workout) {
        workoutDao.updateWorkout(workout)
    }

    suspend fun deleteWorkout(workout: Workout) {
        workoutDao.deleteWorkout(workout)
    }

    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>> {
        return workoutSetDao.getSetsForWorkout(workoutId)
    }

    fun getSetsForExerciseInWorkout(workoutId: Long, exerciseId: Long): Flow<List<WorkoutSet>> {
        return workoutSetDao.getSetsForExerciseInWorkout(workoutId, exerciseId)
    }

    suspend fun insertSet(workoutSet: WorkoutSet): Long {
        return workoutSetDao.insertSet(workoutSet)
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

    suspend fun createEmptyWorkout(routineId: Long?): Long {
        return workoutDao.insertWorkout(
            Workout(routineId = routineId, timestamp = System.currentTimeMillis(), durationSeconds = 0, totalVolume = 0.0)
        )
    }

    suspend fun createWorkoutSet(ws: WorkoutSet): Long {
        return workoutSetDao.insertSet(ws)
    }

    suspend fun instantiateWorkoutFromRoutine(routineId: Long): WorkoutBootstrap {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val routineExercises = routineExerciseDao.getForRoutine(routineId)
            val workoutId = workoutDao.insertWorkout(
                Workout(routineId = routineId, timestamp = now, durationSeconds = 0, totalVolume = 0.0)
            )
            val sets = routineExercises.flatMap { re ->
                (1..re.sets).map { i ->
                    WorkoutSet(
                        workoutId = workoutId,
                        exerciseId = re.exerciseId,
                        setOrder = i,
                        weight = 0.0,
                        reps = 0,
                        supersetGroupId = re.supersetGroupId
                    )
                }
            }
            workoutSetDao.insertSets(sets)
            // Query back to obtain DB-assigned IDs for Iron Vault wiring
            val workoutSets = workoutSetDao.getSetsForWorkout(workoutId).first()
            val ghostMap = routineExercises.associate { re ->
                re.exerciseId to workoutSetDao.getPreviousSessionSets(re.exerciseId, now)
            }
            WorkoutBootstrap(workoutId = workoutId, ghostMap = ghostMap, workoutSets = workoutSets)
        }
    }
}
