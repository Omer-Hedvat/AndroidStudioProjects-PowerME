package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "state_history")
data class StateHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentType: String,      // "GOAL" | "MEDICAL" | "PERFORMANCE"
    val operation: String,         // "OVERWRITE" | "APPEND" | "UPDATE" | "REJECTED"
    val previousValueJson: String, // Snapshot of the document BEFORE change
    val newValueJson: String,      // Snapshot AFTER change
    val changeReason: String,      // AI-generated explanation
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString() // Stable cross-device identity for Firestore (v35)
)
