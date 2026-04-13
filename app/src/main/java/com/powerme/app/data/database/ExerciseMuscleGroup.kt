package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Normalized multi-muscle-group mapping for exercises (v24).
 *
 * Exactly one row per exercise has isPrimary=true (the dominant group).
 * Secondary rows (isPrimary=false) enable cross-group filtering
 * (e.g. Deadlift appears under both Back and Legs).
 *
 * Partial UNIQUE index on (name, equipmentType) WHERE isCustom=0 lives
 * in MIGRATION_23_24 SQL — not expressible via @Index annotation.
 */
@Entity(
    tableName = "exercise_muscle_groups",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"])]
)
data class ExerciseMuscleGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val majorGroup: String,
    val subGroup: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isPrimary: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString() // Stable cross-device identity for Firestore (v35)
)
