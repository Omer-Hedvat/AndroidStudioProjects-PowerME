package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_stats")
data class HealthStats(
    @PrimaryKey
    val date: Long,
    val sleepDurationMinutes: Int? = null,
    val heartRateVariability: Double? = null,
    val restingHeartRate: Int? = null,
    val steps: Int? = null
)
