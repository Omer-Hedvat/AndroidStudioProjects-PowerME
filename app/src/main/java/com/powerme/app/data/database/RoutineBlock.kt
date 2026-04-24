package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "routine_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId"])]
)
data class RoutineBlock(
    @PrimaryKey val id: String,                          // UUID
    val routineId: String,                               // FK → routines.id, CASCADE
    @ColumnInfo(name = "order") val order: Int,          // position within routine (0-based)
    val type: String,                                    // STRENGTH | AMRAP | RFT | EMOM | TABATA
    val name: String? = null,                            // user-editable label, e.g. "Metcon", "Finisher"
    val durationSeconds: Int? = null,                    // AMRAP cap / EMOM total / RFT optional cap
    val targetRounds: Int? = null,                       // RFT target round count
    val emomRoundSeconds: Int? = null,                   // EMOM: duration of one interval in seconds (default 60)
    // Tabata fields
    val tabataWorkSeconds: Int? = null,                  // TABATA work phase duration (default 20)
    val tabataRestSeconds: Int? = null,                  // TABATA rest phase duration (default 10)
    val tabataSkipLastRest: Int? = null,                 // 0 = don't skip, 1 = skip last rest (SQLite bool)
    // Per-block timer overrides (null = use AppSettings defaults)
    val setupSecondsOverride: Int? = null,               // pre-start countdown; null = timedSetSetupSeconds pref
    val warnAtSecondsOverride: Int? = null,              // mid-interval warning; null = resolveWarnAt auto-halftime
    // Sync columns (v35 pattern)
    @ColumnInfo(defaultValue = "") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)
