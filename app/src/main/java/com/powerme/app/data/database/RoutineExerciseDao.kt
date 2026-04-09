package com.powerme.app.data.database

import androidx.room.*

data class RoutineExerciseWithName(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val equipmentType: String,
    val sets: Int,
    val reps: Int,
    val order: Int,
    val supersetGroupId: String?,
    val setTypesJson: String = ""
)

/** Working sets = total sets minus WARMUP-typed slots. Falls back to raw [sets] for legacy rows. */
val RoutineExerciseWithName.workingSets: Int get() {
    if (setTypesJson.isBlank()) return sets
    return setTypesJson.split(",").count { type ->
        runCatching { SetType.valueOf(type) }.getOrNull() != SetType.WARMUP
    }
}

@Dao
interface RoutineExerciseDao {
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY `order` ASC")
    suspend fun getForRoutine(routineId: String): List<RoutineExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routineExercise: RoutineExercise)    // returns Unit — caller pre-generates UUID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routineExercises: List<RoutineExercise>)

    @Delete
    suspend fun delete(routineExercise: RoutineExercise)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun deleteByRoutineAndExercise(routineId: String, exerciseId: Long)

    @Query("UPDATE routine_exercises SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: String, order: Int)

    @Query("SELECT stickyNote FROM routine_exercises WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun getStickyNote(routineId: String, exerciseId: Long): String?

    @Query("UPDATE routine_exercises SET stickyNote = :note WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateStickyNote(routineId: String, exerciseId: Long, note: String?)

    @Query("""
        SELECT re.exerciseId, e.name AS exerciseName, e.muscleGroup, e.equipmentType,
               re.sets, re.reps, re.`order`, re.supersetGroupId, re.setTypesJson
        FROM routine_exercises re
        JOIN exercises e ON re.exerciseId = e.id
        WHERE re.routineId = :routineId
        ORDER BY re.`order` ASC
    """)
    suspend fun getExercisesWithNamesForRoutine(routineId: String): List<RoutineExerciseWithName>

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteAllForRoutine(routineId: String)

    @Query("UPDATE routine_exercises SET sets = :sets WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateSets(routineId: String, exerciseId: Long, sets: Int)

    @Query("UPDATE routine_exercises SET reps = :reps, defaultWeight = :weight WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateRepsAndWeight(routineId: String, exerciseId: Long, reps: Int, weight: String)

    @Query("UPDATE routine_exercises SET setTypesJson = :setTypesJson WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateSetTypesJson(routineId: String, exerciseId: Long, setTypesJson: String)

    @Query("UPDATE routine_exercises SET setWeightsJson = :setWeightsJson, setRepsJson = :setRepsJson WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateSetWeightsAndReps(routineId: String, exerciseId: Long, setWeightsJson: String, setRepsJson: String)
}
