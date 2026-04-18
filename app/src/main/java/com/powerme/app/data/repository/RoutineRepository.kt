package com.powerme.app.data.repository

import androidx.room.withTransaction
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExercise
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.database.SetType
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
     * Creates a new routine from an AI-generated plan.
     * Returns the new routine's id.
     */
    suspend fun createRoutineFromPlan(name: String, exercises: List<PlanExercise>): String {
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val routineId = UUID.randomUUID().toString()
            routineDao.insertRoutine(
                Routine(id = routineId, name = name, isCustom = true, updatedAt = now)
            )
            routineExerciseDao.insertAll(
                exercises.mapIndexed { idx, pe ->
                    RoutineExercise(
                        id = UUID.randomUUID().toString(),
                        routineId = routineId,
                        exerciseId = pe.exerciseId,
                        order = idx,
                        sets = pe.sets,
                        reps = pe.reps,
                        restTime = pe.restSeconds ?: 90,
                        defaultWeight = (pe.weight ?: 0.0).toString()
                    )
                }
            )
            routineId
        }
    }

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
     *  2. Keeps ALL warmup sets; caps work sets (NORMAL/DROP/FAILURE) to a maximum of 2.
     *  3. Selects setTypesJson/setWeightsJson/setRepsJson entries by kept indices.
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
                    val keptIndices = selectExpressIndices(re.setTypesJson, re.sets)
                    val keptCount = keptIndices.size
                    re.copy(
                        id = UUID.randomUUID().toString(),
                        routineId = newRoutineId,
                        order = idx,
                        sets = keptCount,
                        setTypesJson = re.setTypesJson.pickIndices(keptIndices),
                        setWeightsJson = re.setWeightsJson.pickIndices(keptIndices),
                        setRepsJson = re.setRepsJson.pickIndices(keptIndices)
                    )
                }
            )
            newRoutineId
        }
    }
}

/**
 * Returns the indices of sets to keep for express workout:
 * all WARMUP indices + up to 2 work (NORMAL/DROP/FAILURE) indices.
 */
internal fun selectExpressIndices(setTypesJson: String, totalSets: Int): List<Int> {
    val types = if (setTypesJson.isBlank()) List(totalSets) { SetType.NORMAL }
    else setTypesJson.split(",").mapIndexed { i, s ->
        if (i < totalSets) runCatching { SetType.valueOf(s.trim()) }.getOrDefault(SetType.NORMAL) else null
    }.filterNotNull().take(totalSets)

    val warmupIndices = types.indices.filter { types[it] == SetType.WARMUP }
    val workIndices   = types.indices.filter { types[it] != SetType.WARMUP }.take(2)
    return (warmupIndices + workIndices).sorted()
}

/** Picks comma-separated entries at [indices] from this list string. */
internal fun String.pickIndices(indices: List<Int>): String {
    if (isBlank()) return ""
    val parts = split(",")
    return indices.mapNotNull { parts.getOrNull(it)?.trim() }.joinToString(",")
}

/** Takes up to [maxCount] comma-separated entries from a JSON list string. */
internal fun String.truncateJsonList(maxCount: Int): String =
    if (isBlank()) "" else split(",").take(maxCount).joinToString(",")
