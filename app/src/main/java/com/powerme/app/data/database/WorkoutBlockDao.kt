package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutBlockDao {
    @Query("SELECT * FROM workout_blocks WHERE workoutId = :workoutId ORDER BY `order` ASC")
    fun getBlocksForWorkout(workoutId: String): Flow<List<WorkoutBlock>>

    @Query("SELECT * FROM workout_blocks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutBlock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: WorkoutBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<WorkoutBlock>)

    @Query(
        """UPDATE workout_blocks
           SET totalRounds = :r, extraReps = :er, finishTimeSeconds = :ft,
               rpe = :rpe, perExerciseRpeJson = :perExJson,
               roundTapLogJson = :tapLog, blockNotes = :notes, updatedAt = :ts
           WHERE id = :id"""
    )
    suspend fun saveResult(
        id: String,
        r: Int?,
        er: Int?,
        ft: Int?,
        rpe: Int?,
        perExJson: String?,
        tapLog: String?,
        notes: String?,
        ts: Long
    )

    @Query("UPDATE workout_blocks SET runStartMs = :ms WHERE id = :id")
    suspend fun setRunStart(id: String, ms: Long)

    @Query("UPDATE workout_blocks SET roundTapLogJson = :json, updatedAt = :ts WHERE id = :id")
    suspend fun setRoundTapLog(id: String, json: String, ts: Long)

    @Query("UPDATE workout_blocks SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)

    @Transaction
    suspend fun appendRoundTap(blockId: String, entry: String, ts: Long) {
        val current = getById(blockId)?.roundTapLogJson
        setRoundTapLog(blockId, appendJsonArrayEntry(current, entry), ts)
    }
}

internal fun appendJsonArrayEntry(arr: String?, entry: String): String {
    val trimmed = arr?.trim()
    if (trimmed.isNullOrEmpty() || trimmed == "[]") return "[$entry]"
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return "[$entry]"
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    return if (inner.isEmpty()) "[$entry]" else "[$inner,$entry]"
}
