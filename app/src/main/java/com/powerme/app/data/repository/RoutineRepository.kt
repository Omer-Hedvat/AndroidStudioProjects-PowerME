package com.powerme.app.data.repository

import androidx.room.withTransaction
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExerciseDao
import java.util.UUID
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
     * Returns the new routine's id, or "" if the source routine was not found.
     */
    suspend fun duplicateRoutine(routineId: String): String {
        return database.withTransaction {
            val original = routineDao.getRoutineById(routineId) ?: return@withTransaction ""
            val now = System.currentTimeMillis()
            val newRoutineId = UUID.randomUUID().toString()
            routineDao.insertRoutine(
                Routine(id = newRoutineId, name = "${original.name} (Copy)",
                    isCustom = original.isCustom, lastPerformed = null, updatedAt = now)
            )
            val exercises = routineExerciseDao.getForRoutine(routineId)
            routineExerciseDao.insertAll(
                exercises.map { it.copy(id = UUID.randomUUID().toString(), routineId = newRoutineId) }
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
     * Returns the new routine's id, or "" if the source routine was not found.
     */
    suspend fun createExpressRoutine(routineId: String): String {
        return database.withTransaction {
            val original = routineDao.getRoutineById(routineId) ?: return@withTransaction ""
            val exercises = routineExerciseDao.getForRoutine(routineId).sortedBy { it.order }
            val dropCount = floor(exercises.size * 0.4).toInt()
            val surviving = exercises.dropLast(dropCount)
            val now = System.currentTimeMillis()
            val newRoutineId = UUID.randomUUID().toString()
            routineDao.insertRoutine(
                Routine(id = newRoutineId, name = "${original.name} - Express",
                    isCustom = original.isCustom, lastPerformed = null, updatedAt = now)
            )
            routineExerciseDao.insertAll(
                surviving.mapIndexed { idx, re ->
                    val cappedSets = re.sets.coerceAtMost(2)
                    re.copy(
                        id = UUID.randomUUID().toString(),
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
