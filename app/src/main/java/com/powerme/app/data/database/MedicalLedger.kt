package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "medical_ledger")
data class MedicalLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val redListJson: String,           // JSON array of forbidden exercises
    val yellowListJson: String,        // JSON array of YellowEntry
    val injuryHistorySummary: String,  // Free-text injury/medical history summary
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString(), // Stable cross-device identity for Firestore (v35)
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L // Epoch ms, set on every mutation (v35)
)
