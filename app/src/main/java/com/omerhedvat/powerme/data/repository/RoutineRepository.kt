package com.omerhedvat.powerme.data.repository

import androidx.room.withTransaction
import com.omerhedvat.powerme.data.database.PowerMeDatabase
import com.omerhedvat.powerme.data.database.Routine
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.RoutineExerciseDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val database: PowerMeDatabase
) {
    /**
     * Deep-copies a routine and all its exercises atomically.
     * Saved name: "[Original Name] (Copy)".
     * Returns the new routine's id, or -1 if the source routine was not found.
     */
    suspend fun duplicateRoutine(routineId: Long): Long {
        return database.withTransaction {
            val original = routineDao.getRoutineById(routineId) ?: return@withTransaction -1L
            val newRoutineId = routineDao.insertRoutine(
                Routine(name = "${original.name} (Copy)", isCustom = original.isCustom, lastPerformed = null)
            )
            val exercises = routineExerciseDao.getForRoutine(routineId)
            routineExerciseDao.insertAll(
                exercises.map { it.copy(id = 0, routineId = newRoutineId) }
            )
            newRoutineId
        }
    }

    /**
     * Creates a time-optimised "Express" copy of a routine:
     *  1. Drops the bottom floor(N × 0.4) exercises (by order — typically isolation/accessories).
     *  2. Caps surviving exercises to a maximum of 2 sets each.
     *  3. Truncates setTypesJson/setWeightsJson/setRepsJson to the capped count.
     * Saved name: "[Original Name] - Express".
     * Returns the new routine's id, or -1 if the source routine was not found.
     */
    suspend fun createExpressRoutine(routineId: Long): Long {
        return database.withTransaction {
            val original = routineDao.getRoutineById(routineId) ?: return@withTransaction -1L
            val exercises = routineExerciseDao.getForRoutine(routineId).sortedBy { it.order }
            val dropCount = floor(exercises.size * 0.4).toInt()
            val surviving = exercises.dropLast(dropCount)
            val newRoutineId = routineDao.insertRoutine(
                Routine(name = "${original.name} - Express", isCustom = original.isCustom, lastPerformed = null)
            )
            routineExerciseDao.insertAll(
                surviving.mapIndexed { idx, re ->
                    val cappedSets = re.sets.coerceAtMost(2)
                    re.copy(
                        id = 0,
                        routineId = newRoutineId,
                        order = idx,
                        sets = cappedSets,
                        setTypesJson = re.setTypesJson.truncateJsonList(cappedSets),
                        setWeightsJson = re.setWeightsJson.truncateJsonList(cappedSets),
                        setRepsJson = re.setRepsJson.truncateJsonList(cappedSets)
                    )
                }
            )
            newRoutineId
        }
    }
}

/** Takes up to [maxCount] comma-separated entries from a JSON list string. */
internal fun String.truncateJsonList(maxCount: Int): String =
    if (isBlank()) "" else split(",").take(maxCount).joinToString(",")
