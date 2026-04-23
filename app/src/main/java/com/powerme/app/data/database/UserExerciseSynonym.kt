package com.powerme.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Persists user-typed exercise name aliases and maps them to canonical [Exercise] entities.
 *
 * When a user types "flat bench" and selects "Barbell Bench Press", that mapping is stored here
 * so future lookups resolve instantly without fuzzy search.
 *
 * - [rawName] is stored pre-normalised via [toSearchName] (lowercase, no spaces/hyphens/parens).
 * - [useCount] increments on every hit, enabling future frequency-based ranking.
 * - Unique index on [rawName] ensures one canonical mapping per normalised alias.
 */
@Entity(
    tableName = "user_exercise_synonyms",
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseId"),
        Index(value = ["rawName"], unique = true)
    ]
)
data class UserExerciseSynonym(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rawName: String,
    val exerciseId: Long,
    val useCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
