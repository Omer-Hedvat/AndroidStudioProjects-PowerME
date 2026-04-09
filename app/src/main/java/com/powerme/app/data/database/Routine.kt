package com.powerme.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey
    val id: String,                        // UUID string — no autoGenerate; caller pre-generates
    val name: String,
    val lastPerformed: Long? = null,
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,       // v20 — soft-archive
    val updatedAt: Long = 0L              // v31 — LWW timestamp for Firestore sync
)
