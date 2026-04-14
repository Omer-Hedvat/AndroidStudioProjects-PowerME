package com.powerme.app.ui.chat

import kotlinx.serialization.Serializable

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
