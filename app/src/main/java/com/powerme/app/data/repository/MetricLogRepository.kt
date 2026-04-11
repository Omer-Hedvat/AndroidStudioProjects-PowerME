package com.powerme.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricLogDao
import com.powerme.app.data.database.MetricType
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricLogRepository @Inject constructor(
    private val dao: MetricLogDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun getByType(type: MetricType): Flow<List<MetricLog>> = dao.getByType(type.name)

    fun getAll(): Flow<List<MetricLog>> = dao.getAll()

    suspend fun log(type: MetricType, value: Double) {
        val entry = MetricLog(type = type, value = value)
        dao.insert(entry)
        // Mirror to Firestore under users/{uid}/metric_log (best-effort)
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .collection("metric_log")
                .add(
                    mapOf(
                        "timestamp" to entry.timestamp,
                        "type" to type.name,
                        "value" to value
                    )
                )
        }
    }

    /**
     * Inserts or replaces today's row for [type] with [value].
     * No-op if today's row already has the exact same value.
     * Replaces today's row if value differs. Appends a new row if the latest row is from a prior day.
     */
    suspend fun upsertTodayIfChanged(type: MetricType, value: Double) {
        val latest = dao.getLatestForType(type.name)
        if (latest == null) {
            log(type, value)
            return
        }
        val cal1 = Calendar.getInstance().apply { timeInMillis = latest.timestamp }
        val cal2 = Calendar.getInstance()
        val sameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        if (sameDay && latest.value == value) return
        if (sameDay) dao.delete(latest)
        log(type, value)
    }

    suspend fun delete(entry: MetricLog) {
        dao.delete(entry)
        // Firestore delete is best-effort: query by timestamp+type then delete
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .collection("metric_log")
                .whereEqualTo("timestamp", entry.timestamp)
                .whereEqualTo("type", entry.type.name)
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot.documents.forEach { it.reference.delete() }
                }
        }
    }
}
