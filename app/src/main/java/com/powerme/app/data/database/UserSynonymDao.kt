package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserSynonymDao {

    @Query("SELECT * FROM user_exercise_synonyms WHERE rawName = :rawName LIMIT 1")
    suspend fun findByRawName(rawName: String): UserExerciseSynonym?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(synonym: UserExerciseSynonym)

    @Query("UPDATE user_exercise_synonyms SET useCount = useCount + 1 WHERE rawName = :rawName")
    suspend fun incrementUseCount(rawName: String)
}
