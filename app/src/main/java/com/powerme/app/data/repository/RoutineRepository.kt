package com.powerme.app.data.repository

import androidx.room.withTransaction
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineBlockDao
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
    private val routineBlockDao: RoutineBlockDao,
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
                        defaultWeight = (pe.weight ?: 0.0).toString(),
                        supersetGroupId = pe.supersetGroupId,
                        stickyNote = pe.notes
                    )
                }
            )
            routineId
        }
    }

    /**
     * Deep-copies a routine, its blocks, and all its exercises atomically.
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
            val blockIdMap = copyBlocksForRoutine(routineId, newRoutineId, now)
            val exercises = routineExerciseDao.getForRoutine(routineId)
            routineExerciseDao.insertAll(
                exercises.map { ex ->
                    ex.copy(
                        id = UUID.randomUUID().toString(),
                        routineId = newRoutineId,
                        blockId = ex.blockId?.let { blockIdMap[it] }
                    )
                }
            )
            newRoutineId
        }
    }

    /**
     * Creates a time-optimised "Express" copy of a routine:
     *  1. For STRENGTH exercises (no block or STRENGTH block): drops the bottom floor(N × 0.4)
     *     exercises and caps work sets to 2.
     *  2. Functional blocks are copied intact with all their exercises.
     * Saved name: "[Original Name] - Express".
     * Returns the new routine's id, or "" if the source routine was not found.
     */
    suspend fun createExpressRoutine(routineId: String): String {
        return database.withTransaction {
            val original = routineDao.getRoutineById(routineId) ?: return@withTransaction ""
            val exercises = routineExerciseDao.getForRoutine(routineId).sortedBy { it.order }
            val blocks = routineBlockDao.getBlocksForRoutineOnce(routineId)
            val functionalBlockIds = blocks.filter { it.type != "STRENGTH" }.map { it.id }.toSet()

            val now = System.currentTimeMillis()
            val newRoutineId = UUID.randomUUID().toString()
            routineDao.insertRoutine(
                Routine(id = newRoutineId, name = "${original.name} - Express",
                    isCustom = original.isCustom, lastPerformed = null, updatedAt = now)
            )
            val blockIdMap = copyBlocksForRoutine(routineId, newRoutineId, now)

            val functionalExercises = exercises.filter { it.blockId in functionalBlockIds }
            val strengthExercises = exercises.filter { it.blockId !in functionalBlockIds }
            val dropCount = floor(strengthExercises.size * 0.4).toInt()
            val survivingStrength = strengthExercises.dropLast(dropCount)

            val expressStrength = survivingStrength.map { re ->
                val keptIndices = selectExpressIndices(re.setTypesJson, re.sets)
                re.copy(
                    id = UUID.randomUUID().toString(),
                    routineId = newRoutineId,
                    blockId = re.blockId?.let { blockIdMap[it] },
                    sets = keptIndices.size,
                    setTypesJson = re.setTypesJson.pickIndices(keptIndices),
                    setWeightsJson = re.setWeightsJson.pickIndices(keptIndices),
                    setRepsJson = re.setRepsJson.pickIndices(keptIndices)
                )
            }
            val copiedFunctional = functionalExercises.map { ex ->
                ex.copy(
                    id = UUID.randomUUID().toString(),
                    routineId = newRoutineId,
                    blockId = ex.blockId?.let { blockIdMap[it] }
                )
            }
            routineExerciseDao.insertAll(
                (expressStrength + copiedFunctional).mapIndexed { i, ex -> ex.copy(order = i) }
            )
            newRoutineId
        }
    }

    /** Copies all blocks from [sourceRoutineId] to [targetRoutineId], returning oldId→newId map. */
    private suspend fun copyBlocksForRoutine(
        sourceRoutineId: String,
        targetRoutineId: String,
        now: Long
    ): Map<String, String> {
        val blocks = routineBlockDao.getBlocksForRoutineOnce(sourceRoutineId)
        if (blocks.isEmpty()) return emptyMap()
        val blockIdMap = mutableMapOf<String, String>()
        routineBlockDao.upsertAll(
            blocks.map { b ->
                val newId = UUID.randomUUID().toString()
                blockIdMap[b.id] = newId
                b.copy(id = newId, routineId = targetRoutineId, updatedAt = now)
            }
        )
        return blockIdMap
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
