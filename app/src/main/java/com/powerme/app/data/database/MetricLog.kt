package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class MetricType { WEIGHT, BODY_FAT, CALORIES, HEIGHT }

@Entity(
    tableName = "metric_log",
    indices = [Index("timestamp"), Index("type")]
)
data class MetricLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MetricType,
    val value: Double,
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString()
)
