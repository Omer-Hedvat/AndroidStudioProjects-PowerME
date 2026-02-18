package com.omerhedvat.powerme.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omerhedvat.powerme.data.database.MetricLog
import com.omerhedvat.powerme.data.database.MetricLogDao
import com.omerhedvat.powerme.data.database.MetricType
import kotlinx.coroutines.flow.Flow
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
