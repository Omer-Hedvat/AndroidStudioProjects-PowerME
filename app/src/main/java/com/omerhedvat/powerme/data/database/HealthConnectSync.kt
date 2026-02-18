package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "health_connect_sync")
data class HealthConnectSync(
    @PrimaryKey
    val date: LocalDate,
    val sleepDurationMinutes: Int? = null,
    val hrv: Double? = null,              // Heart Rate Variability (ms)
    val rhr: Int? = null,                 // Resting Heart Rate (bpm)
    val steps: Int? = null,
    val highFatigueFlag: Boolean = false, // Sleep < 7h triggers Coach Carter adjustment
    val anomalousRecoveryFlag: Boolean = false, // HRV drop > 10% triggers Boaz alert
    val syncTimestamp: Long = System.currentTimeMillis()
)
