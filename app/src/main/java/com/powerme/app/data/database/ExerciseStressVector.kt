package com.powerme.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Maps an exercise to a body region with a stress coefficient (0.0–1.0).
 *
 * Seeded for the top 30 exercises from exercise science literature (Schoenfeld / NSCA).
 * Used by [com.powerme.app.analytics.StressAccumulationEngine] to compute per-region
 * training stress for the Body Heatmap (P6).
 *
 * - [bodyRegion] stores the [BodyRegion] enum name (e.g. "KNEE_JOINT").
 * - [stressCoefficient] is in [0.0, 1.0]: how much of the set's volume loads this region.
 */
@Entity(
    tableName = "exercise_stress_vectors",
    primaryKeys = ["exerciseId", "bodyRegion"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId")]
)
data class ExerciseStressVector(
    val exerciseId: Long,
    val bodyRegion: String,
    val stressCoefficient: Double
)
