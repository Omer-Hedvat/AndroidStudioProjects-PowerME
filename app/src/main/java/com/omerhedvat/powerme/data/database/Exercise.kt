package com.omerhedvat.powerme.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipmentType: String,
    val instructionsUrl: String? = null,
    val committeeNotes: String? = null,
    val restDurationSeconds: Int = 90, // Default 90s for isolation
    val exerciseType: ExerciseType = ExerciseType.STRENGTH,
    val setupNotes: String? = null, // Persistent form cues
    val barType: BarType = BarType.STANDARD,
    val isFavorite: Boolean = false, // User-marked favorites
    val isCustom: Boolean = false, // User-created exercises
    val youtubeVideoId: String? = null, // YouTube demonstration video
    val familyId: String? = null, // Exercise family grouping (e.g., "squat_family")
    @ColumnInfo(defaultValue = "''")
    val searchName: String = "" // Pre-normalized for fuzzy search: lowercase, no hyphens/spaces/parens
)

fun String.toSearchName(): String = lowercase().replace(Regex("[\\s\\-()]"), "")
