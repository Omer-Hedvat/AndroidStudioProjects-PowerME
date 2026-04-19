package com.powerme.app.data.database

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
    val syncTimestamp: Long = System.currentTimeMillis(),
    // HR (added v43)
    val avgHeartRateBpm: Int? = null,
    val peakHeartRateBpm: Int? = null,
    val hrZone1Pct: Float? = null,
    val hrZone2Pct: Float? = null,
    val hrZone3Pct: Float? = null,
    val hrZone4Pct: Float? = null,
    val hrZone5Pct: Float? = null,
    // Active calories (added v43)
    val activeCaloriesKcal: Double? = null,
    // VO2 Max (added v43)
    val vo2MaxMlKgMin: Double? = null,
    // Distance (added v43)
    val distanceMetres: Double? = null,
    // SpO2 (added v43)
    val spo2Percent: Double? = null,
    val lowSpO2Flag: Boolean = false,
    // Sleep stages (added v43)
    val deepSleepMinutes: Int? = null,
    val remSleepMinutes: Int? = null,
    val lightSleepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepEfficiency: Float? = null,
    val sleepScore: Int? = null,
    // Respiratory rate (added v43)
    val sleepRespiratoryRate: Double? = null,
    val elevatedRespiratoryRateFlag: Boolean = false
)
