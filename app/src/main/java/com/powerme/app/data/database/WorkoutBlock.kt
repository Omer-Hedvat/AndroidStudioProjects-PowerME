package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "workout_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class WorkoutBlock(
    @PrimaryKey val id: String,
    val workoutId: String,                               // FK → workouts.id, CASCADE
    @ColumnInfo(name = "order") val order: Int,
    val type: String,                                    // STRENGTH | AMRAP | RFT | EMOM | TABATA
    val name: String? = null,
    // Plan fields (copied from RoutineBlock at workout start)
    val durationSeconds: Int? = null,
    val targetRounds: Int? = null,
    val emomRoundSeconds: Int? = null,
    val tabataWorkSeconds: Int? = null,
    val tabataRestSeconds: Int? = null,
    val tabataSkipLastRest: Int? = null,
    val setupSecondsOverride: Int? = null,
    val warnAtSecondsOverride: Int? = null,
    // Result fields (populated at block finish)
    val totalRounds: Int? = null,                        // AMRAP: rounds completed; RFT: rounds completed; TABATA: rounds completed
    val extraReps: Int? = null,                          // AMRAP: reps into next round
    val finishTimeSeconds: Int? = null,                  // RFT: total elapsed; EMOM/TABATA: total elapsed
    val rpe: Int? = null,                                // 1–10 user self-rating (overall; mutually exclusive with perExerciseRpeJson)
    val perExerciseRpeJson: String? = null,              // JSON map exerciseId→rpe (per-exercise; mutually exclusive with rpe)
    val roundTapLogJson: String? = null,                 // JSON: [{round,elapsedMs}, ...] — per-round tap timestamps for analytics
    val blockNotes: String? = null,
    // Lifecycle
    val runStartMs: Long? = null,                        // wall-clock epoch at block start; for resume-from-kill
    // Sync columns (v35 pattern)
    @ColumnInfo(defaultValue = "") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)
