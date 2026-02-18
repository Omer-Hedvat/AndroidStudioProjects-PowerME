package com.omerhedvat.powerme.util

import android.util.Log
import com.omerhedvat.powerme.data.database.StateHistoryEntry
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.StateHistoryRepository
import com.omerhedvat.powerme.ui.chat.GoalDocument
import com.omerhedvat.powerme.ui.chat.MedicalRestrictionsDoc
import com.omerhedvat.powerme.ui.chat.YellowEntry
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

data class GoalPatch(
    val operation: String,
    val reason: String,
    val newValue: GoalDocument?
)

data class MedicalPatch(
    val operation: String,
    val reason: String,
    val redListAdd: List<String> = emptyList(),
    val yellowListAdd: List<YellowEntry> = emptyList()
)

data class StatePatch(
    val deltaSummary: String,
    val goalPatch: GoalPatch?,
    val medicalPatch: MedicalPatch?
)

@Singleton
class StatePatchManager @Inject constructor(
    private val goalDocumentManager: GoalDocumentManager,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val stateHistoryRepository: StateHistoryRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a State Patch JSON string from Gemini's session analysis.
     */
    fun parseStatePatch(jsonString: String): StatePatch? {
        return try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val deltaSummary = root["delta_summary"]?.jsonPrimitive?.content ?: ""

            val goalPatch = root["goal_patch"]?.jsonObject?.let { gp ->
                val op = gp["operation"]?.jsonPrimitive?.content ?: "NO_CHANGE"
                val reason = gp["reason"]?.jsonPrimitive?.content ?: ""
                val newVal = if (op == "OVERWRITE") {
                    gp["new_value"]?.let { parseGoalPatchValue(it.jsonObject) }
                } else null
                GoalPatch(operation = op, reason = reason, newValue = newVal)
            }

            val medicalPatch = root["medical_patch"]?.jsonObject?.let { mp ->
                val op = mp["operation"]?.jsonPrimitive?.content ?: "NO_CHANGE"
                val reason = mp["reason"]?.jsonPrimitive?.content ?: ""
                val additions = mp["additions"]?.jsonObject
                val redAdd = additions?.get("red_list_add")?.jsonArray
                    ?.map { it.jsonPrimitive.content } ?: emptyList()
                val yellowAdd = additions?.get("yellow_list_add")?.jsonArray?.map { entry ->
                    val obj = entry.jsonObject
                    YellowEntry(
                        exercise = obj["exercise"]?.jsonPrimitive?.content ?: "",
                        requiredCue = obj["modification_cue"]?.jsonPrimitive?.content ?: ""
                    )
                } ?: emptyList()
                MedicalPatch(operation = op, reason = reason, redListAdd = redAdd, yellowListAdd = yellowAdd)
            }

            StatePatch(deltaSummary = deltaSummary, goalPatch = goalPatch, medicalPatch = medicalPatch)
        } catch (e: Exception) {
            Log.w("StatePatchManager", "Failed to parse state patch", e)
            null
        }
    }

    private fun parseGoalPatchValue(obj: JsonObject): GoalDocument? {
        return try {
            GoalDocument(
                phase = obj["phase"]?.jsonPrimitive?.content ?: return null,
                deadline = obj["deadline"]?.jsonPrimitive?.content ?: return null,
                priorityMuscles = obj["priorityMuscles"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                sessionConstraints = obj["sessionConstraints"]?.jsonPrimitive?.content ?: "",
                weeklySessionCount = obj["weeklySessionCount"]?.jsonPrimitive?.int ?: 4
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Applies the goal patch — overwrites GoalDocument, archives previous to state_history.
     */
    suspend fun applyGoalPatch(patch: GoalPatch) {
        if (patch.operation == "NO_CHANGE" || patch.newValue == null) return
        val previousDoc = goalDocumentManager.getGoalDocument()
        val previousJson = if (previousDoc != null) json.encodeToString(GoalDocument.serializer(), previousDoc) else "{}"
        val newJson = json.encodeToString(GoalDocument.serializer(), patch.newValue)
        stateHistoryRepository.insertEntry(
            StateHistoryEntry(
                documentType = "GOAL",
                operation = patch.operation,
                previousValueJson = previousJson,
                newValueJson = newJson,
                changeReason = patch.reason
            )
        )
        goalDocumentManager.saveGoalDocument(patch.newValue)
    }

    /**
     * Applies the medical patch — appends to MedicalRestrictionsDoc, archives to state_history.
     */
    suspend fun applyMedicalPatch(patch: MedicalPatch) {
        if (patch.operation == "NO_CHANGE") return
        val currentDoc = medicalLedgerRepository.getRestrictionsDoc()
        val previousJson = if (currentDoc != null) json.encodeToString(MedicalRestrictionsDoc.serializer(), currentDoc) else "{}"

        val updatedDoc = currentDoc?.copy(
            redList = (currentDoc.redList + patch.redListAdd).distinct(),
            yellowList = currentDoc.yellowList + patch.yellowListAdd,
            lastUpdated = System.currentTimeMillis()
        ) ?: MedicalRestrictionsDoc(
            redList = patch.redListAdd,
            yellowList = patch.yellowListAdd,
            injuryHistorySummary = ""
        )

        val newJson = json.encodeToString(MedicalRestrictionsDoc.serializer(), updatedDoc)
        stateHistoryRepository.insertEntry(
            StateHistoryEntry(
                documentType = "MEDICAL",
                operation = patch.operation,
                previousValueJson = previousJson,
                newValueJson = newJson,
                changeReason = patch.reason
            )
        )
        medicalLedgerRepository.saveLedger(updatedDoc)
    }

    /**
     * Logs a medical patch rejection to state_history.
     */
    suspend fun rejectMedicalPatch(patch: MedicalPatch) {
        stateHistoryRepository.insertEntry(
            StateHistoryEntry(
                documentType = "MEDICAL",
                operation = "REJECTED",
                previousValueJson = "{}",
                newValueJson = "{}",
                changeReason = "User rejected: ${patch.reason}"
            )
        )
    }
}
