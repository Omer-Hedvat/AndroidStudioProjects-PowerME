package com.powerme.app.data.repository

import com.powerme.app.data.database.HealthHistoryDao
import com.powerme.app.data.database.HealthHistoryEntry
import com.powerme.app.data.database.HealthHistorySeverity
import com.powerme.app.ui.chat.MedicalRestrictionsDoc
import com.powerme.app.ui.chat.YellowEntry
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthHistoryRepository @Inject constructor(
    private val healthHistoryDao: HealthHistoryDao,
    private val medicalLedgerRepository: MedicalLedgerRepository
) {

    fun getActiveEntries(): Flow<List<HealthHistoryEntry>> =
        healthHistoryDao.getActiveEntries()

    suspend fun save(entry: HealthHistoryEntry) {
        val now = System.currentTimeMillis()
        healthHistoryDao.insert(entry.copy(lastModifiedAt = now))
        rebuildMedicalLedger()
    }

    suspend fun archive(id: String) {
        healthHistoryDao.softDelete(id)
        rebuildMedicalLedger()
    }

    /**
     * Rebuilds the MedicalLedger from all active health history entries.
     * Mapping rules (per spec):
     *  - SEVERE + not RESOLVED → redList (exercise completely forbidden)
     *  - MODERATE + not RESOLVED → yellowList (modification cue required)
     *  - RESOLVED / MILD → removed from both lists
     */
    suspend fun rebuildMedicalLedger() {
        val entries = healthHistoryDao.getAllForSync()
        val redList = mutableListOf<String>()
        val yellowList = mutableListOf<YellowEntry>()
        val summaryParts = mutableListOf<String>()

        for (entry in entries) {
            val severity = runCatching { HealthHistorySeverity.valueOf(entry.severity) }
                .getOrNull() ?: continue

            summaryParts.add("${entry.title} (${severity.displayName})")

            if (severity == HealthHistorySeverity.RESOLVED || severity == HealthHistorySeverity.MILD) {
                continue
            }

            val affectedExercises = entry.affectedExerciseIds
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            if (affectedExercises.isEmpty()) continue

            when (severity) {
                HealthHistorySeverity.SEVERE -> {
                    redList.addAll(affectedExercises)
                }
                HealthHistorySeverity.MODERATE -> {
                    val cue = entry.notes?.takeIf { it.isNotBlank() }
                        ?: "Use caution — ${entry.title}"
                    affectedExercises.forEach { exercise ->
                        yellowList.add(YellowEntry(exercise = exercise, requiredCue = cue))
                    }
                }
                else -> { /* MILD/RESOLVED already handled above */ }
            }
        }

        val summary = if (summaryParts.isNotEmpty())
            summaryParts.joinToString("; ")
        else ""

        val existing = medicalLedgerRepository.getRestrictionsDoc()
        medicalLedgerRepository.saveLedger(
            MedicalRestrictionsDoc(
                redList = redList.distinct(),
                yellowList = yellowList.distinctBy { it.exercise },
                injuryHistorySummary = summary,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
