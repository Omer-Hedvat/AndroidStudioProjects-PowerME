package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = Routine::class,  parentColumns = ["id"], childColumns = ["routineId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["routineId"]), Index(value = ["exerciseId"])]
)
data class RoutineExercise(
    @PrimaryKey
    val id: String,                        // UUID string — no autoGenerate; caller pre-generates
    val routineId: String,
    val exerciseId: Long,                  // exercises table not migrated — stays Long
    val sets: Int = 3,
    val reps: Int = 10,
    val restTime: Int = 90,          // seconds
    @ColumnInfo(name = "order")
    val order: Int = 0,              // display order within routine
    val supersetGroupId: String? = null,
    val stickyNote: String? = null,
    @ColumnInfo(defaultValue = "''")
    val defaultWeight: String = "",  // default weight for routine sync (v23)
    @ColumnInfo(defaultValue = "''")
    val setTypesJson: String = "",   // comma-separated SetType names per set in order, e.g. "NORMAL,WARMUP,NORMAL" (v28)
    @ColumnInfo(defaultValue = "''")
    val setWeightsJson: String = "", // comma-separated weights per set in order, e.g. "80,85,90" (v29)
    @ColumnInfo(defaultValue = "''")
    val setRepsJson: String = "",    // comma-separated reps per set in order, e.g. "10,8,6" (v29)
    @ColumnInfo(defaultValue = "NULL")
    val blockId: String? = null,     // FK → routine_blocks.id (v51)
    @ColumnInfo(defaultValue = "NULL")
    val holdSeconds: Int? = null     // per-prescription hold duration for non-STRENGTH blocks (v51); see FUNCTIONAL_TRAINING_SPEC.md §10
)
