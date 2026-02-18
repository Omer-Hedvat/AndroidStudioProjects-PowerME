package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medical_ledger")
data class MedicalLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val redListJson: String,           // JSON array of forbidden exercises
    val yellowListJson: String,        // JSON array of YellowEntry
    val injuryHistorySummary: String,  // Free-text injury/medical history summary
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)
