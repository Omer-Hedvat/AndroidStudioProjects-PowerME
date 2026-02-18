package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MetricType { WEIGHT, BODY_FAT, CALORIES }

@Entity(
    tableName = "metric_log",
    indices = [Index("timestamp"), Index("type")]
)
data class MetricLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MetricType,
    val value: Double    // kg for weight, % for body fat, kcal for calories
)
