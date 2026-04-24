package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineBlockDao {
    @Query("SELECT * FROM routine_blocks WHERE routineId = :routineId ORDER BY `order` ASC")
    fun getBlocksForRoutine(routineId: String): Flow<List<RoutineBlock>>

    @Query("SELECT * FROM routine_blocks WHERE routineId = :routineId ORDER BY `order` ASC")
    suspend fun getBlocksForRoutineOnce(routineId: String): List<RoutineBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: RoutineBlock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<RoutineBlock>)

    @Delete
    suspend fun delete(block: RoutineBlock)

    @Query("DELETE FROM routine_blocks WHERE routineId = :routineId")
    suspend fun deleteAllForRoutine(routineId: String)

    @Query("UPDATE routine_blocks SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)
}
