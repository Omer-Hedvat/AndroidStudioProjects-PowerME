package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao
) {
    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts()
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
}
