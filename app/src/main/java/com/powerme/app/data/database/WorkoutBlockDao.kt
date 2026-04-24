package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutBlockDao {
    @Query("SELECT * FROM workout_blocks WHERE workoutId = :workoutId ORDER BY `order` ASC")
    fun getBlocksForWorkout(workoutId: String): Flow<List<WorkoutBlock>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: WorkoutBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<WorkoutBlock>)

    @Query("UPDATE workout_blocks SET totalRounds = :r, extraReps = :er, finishTimeSeconds = :ft, rpe = :rpe, blockNotes = :notes, updatedAt = :ts WHERE id = :id")
    suspend fun saveResult(id: String, r: Int?, er: Int?, ft: Int?, rpe: Int?, notes: String?, ts: Long)

    @Query("UPDATE workout_blocks SET runStartMs = :ms WHERE id = :id")
    suspend fun setRunStart(id: String, ms: Long)

    @Query("UPDATE workout_blocks SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)
}
