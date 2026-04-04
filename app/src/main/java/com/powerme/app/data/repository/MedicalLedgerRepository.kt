package com.powerme.app.data.repository

import com.powerme.app.data.database.MedicalLedger
import com.powerme.app.data.database.MedicalLedgerDao
import com.powerme.app.ui.chat.MedicalRestrictionsDoc
import com.powerme.app.ui.chat.YellowEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalLedgerRepository @Inject constructor(
    private val medicalLedgerDao: MedicalLedgerDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getRestrictionsDoc(): MedicalRestrictionsDoc? {
        val ledger = medicalLedgerDao.getLatestLedger() ?: return null
        return try {
            val redList = json.decodeFromString(ListSerializer(String.serializer()), ledger.redListJson)
            val yellowList = json.decodeFromString(ListSerializer(YellowEntry.serializer()), ledger.yellowListJson)
            MedicalRestrictionsDoc(
                redList = redList,
                yellowList = yellowList,
                injuryHistorySummary = ledger.injuryHistorySummary,
                createdAt = ledger.createdAt,
                lastUpdated = ledger.lastUpdated
            )
        } catch (e: Exception) {
            android.util.Log.w("MedicalLedgerRepository", "Failed to deserialize medical ledger", e)
            null
        }
    }

    suspend fun saveLedger(doc: MedicalRestrictionsDoc) {
        val redListJson = json.encodeToString(ListSerializer(String.serializer()), doc.redList)
        val yellowListJson = json.encodeToString(ListSerializer(YellowEntry.serializer()), doc.yellowList)
        val ledger = MedicalLedger(
            redListJson = redListJson,
            yellowListJson = yellowListJson,
            injuryHistorySummary = doc.injuryHistorySummary,
            createdAt = doc.createdAt,
            lastUpdated = System.currentTimeMillis()
        )
        medicalLedgerDao.insertLedger(ledger)
    }

    suspend fun clearLedger() {
        medicalLedgerDao.clearLedger()
    }
}
