package com.powerme.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val name: String? = null,
    val age: Int? = null,
    val heightCm: Float? = null,
    val occupationType: String? = null,   // SEDENTARY | ACTIVE | PHYSICAL
    val averageSleepHours: Float? = null,
    val chronotype: String? = null,       // MORNING | NIGHT | NEUTRAL
    val parentalLoad: Int? = null,        // number of children
    val createdAt: Long = System.currentTimeMillis(),
    val weightKg: Float? = null,
    val bodyFatPercent: Float? = null,
    val gender: String? = null,           // MALE | FEMALE | OTHER
    val trainingTargets: String? = null   // comma-separated: Hypertrophy,Strength,...
)
