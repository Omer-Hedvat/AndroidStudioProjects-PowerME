package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

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
    val restDurationSeconds: Int = 90, // Default 90s for work/normal sets
    @ColumnInfo(defaultValue = "30")
    val warmupRestSeconds: Int = 30,   // Rest after warmup sets (v32)
    @ColumnInfo(defaultValue = "0")
    val dropSetRestSeconds: Int = 0,   // Rest after drop sets — 0 = no rest (v32)
    @ColumnInfo(defaultValue = "0")
    val restAfterLastSet: Boolean = false, // When false, skip rest timer after the last set (v38)
    val exerciseType: ExerciseType = ExerciseType.STRENGTH,
    val setupNotes: String? = null, // Persistent form cues
    val barType: BarType = BarType.STANDARD,
    val isFavorite: Boolean = false, // User-marked favorites
    val isCustom: Boolean = false, // User-created exercises
    val youtubeVideoId: String? = null, // YouTube demonstration video
    val familyId: String? = null, // Exercise family grouping (e.g., "squat_family")
    @ColumnInfo(defaultValue = "''")
    val searchName: String = "", // Pre-normalized for fuzzy search: lowercase, no hyphens/spaces/parens
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString(), // Stable cross-device identity for Firestore (v35)
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L, // Epoch ms, set on every mutation (v35)
    @ColumnInfo(defaultValue = "")
    val primaryJoints: String = "", // JSON array of Joint enum names — primary (direct load) joints (v43)
    @ColumnInfo(defaultValue = "")
    val secondaryJoints: String = "", // JSON array of Joint enum names — secondary (stabilising) joints (v43)
    @ColumnInfo(defaultValue = "''")
    val userNote: String = "", // User-authored per-exercise note (v47)
    @ColumnInfo(defaultValue = "[]")
    val tags: String = "[]" // JSON array of tags, e.g. ["functional","olympic"] (v50)
)

fun String.toSearchName(): String = lowercase().replace(Regex("[\\s\\-()'/]"), "")

fun String.toSearchTokens(): List<String> = trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

fun Exercise.matchesSearchTokens(tokens: List<String>): Boolean =
    tokens.isEmpty() || tokens.all { token ->
        ExerciseSynonyms.expandToken(token).any { term ->
            // Check against display name (handles multi-word phrases like "overhead press")
            // and searchName (handles space-collapsed terms like "facepull" → "Face Pull")
            name.contains(term, ignoreCase = true) || searchName.contains(term.toSearchName())
        }
    }
