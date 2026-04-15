package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Period

enum class ExperienceLevel(val displayName: String, val description: String) {
    NOVICE("Novice", "< 1 year training"),
    TRAINED("Trained", "1–3 years"),
    EXPERIENCED("Experienced", "3–5 years"),
    ATHLETE("Athlete", "5+ years / competitive")
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val name: String? = null,
    /** Legacy integer age — kept for existing rows. Prefer dateOfBirth. */
    val age: Int? = null,
    val dateOfBirth: Long? = null,        // epoch millis (UTC)
    val heightCm: Float? = null,
    val occupationType: String? = null,   // SEDENTARY | ACTIVE | PHYSICAL
    val averageSleepHours: Float? = null,
    val chronotype: String? = null,       // MORNING | NIGHT | NEUTRAL
    val parentalLoad: Int? = null,        // number of children
    val createdAt: Long = System.currentTimeMillis(),
    val weightKg: Float? = null,
    val bodyFatPercent: Float? = null,
    val gender: String? = null,           // MALE | FEMALE | OTHER
    val trainingTargets: String? = null,  // comma-separated: Hypertrophy,Strength,...
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,             // Epoch ms, set on every mutation (v35)
    val experienceLevel: String? = null,  // ExperienceLevel enum name (v39)
    val trainingAgeYears: Int? = null     // 0-30 (v39)
)

/**
 * Returns the user's age in whole years, derived from [User.dateOfBirth] if set,
 * or falling back to the legacy [User.age] integer for existing records.
 */
val User.ageYears: Int?
    get() {
        dateOfBirth?.let { epochMs ->
            val dob = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            return Period.between(dob, LocalDate.now()).years
        }
        return age
    }
