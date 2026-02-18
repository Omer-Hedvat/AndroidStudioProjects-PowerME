package com.omerhedvat.powerme.ui.chat

import com.omerhedvat.powerme.data.database.User
import kotlinx.serialization.Serializable

data class UserContext(
    val user: User,
    val goalDocument: GoalDocument?,
    val medicalDoc: MedicalRestrictionsDoc?
)

@Serializable
data class GoalDocument(
    val phase: String,               // "MASSING" | "CUTTING" | "MAINTENANCE"
    val deadline: String,            // "YYYY-MM-DD"
    val priorityMuscles: List<String>,
    val sessionConstraints: String,
    val weeklySessionCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class MedicalRestrictionsDoc(
    val redList: List<String>,
    val yellowList: List<YellowEntry>,
    val injuryHistorySummary: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class YellowEntry(val exercise: String, val requiredCue: String)

enum class TrainingPhase { MASSING, CUTTING, MAINTENANCE }
