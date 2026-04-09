package com.powerme.app.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.powerme.app.data.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val workoutsImported: Int = 0,
    val routinesImported: Int = 0,
    val error: String? = null
) {
    fun toUserMessage() = when {
        !success -> "Sync failed: ${error ?: "unknown"}"
        workoutsImported == 0 && routinesImported == 0 -> "Already up to date"
        else -> "Imported $workoutsImported workouts, $routinesImported routines"
    }
}

@Singleton
class FirestoreSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val database: PowerMeDatabase,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Push ────────────────────────────────────────────────────────────────

    fun pushWorkout(workoutId: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val workout = workoutDao.getWorkoutById(workoutId) ?: return@launch
            val sets = workoutSetDao.getSetsForWorkout(workoutId).first()
            // Do NOT call .await() — Firestore SDK queues locally and delivers when network returns
            firestore.collection("users").document(uid)
                .collection("workouts").document(workoutId)
                .set(workout.toFirestoreMap(sets))
        }
    }

    fun pushRoutine(routineId: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val routine = routineDao.getRoutineById(routineId) ?: return@launch
            val exercises = routineExerciseDao.getForRoutine(routineId)
            firestore.collection("users").document(uid)
                .collection("routines").document(routineId)
                .set(routine.toFirestoreMap(exercises))
        }
    }

    // ── Pull ────────────────────────────────────────────────────────────────

    suspend fun pullFromCloud(): SyncResult {
        val uid = auth.currentUser?.uid
            ?: return SyncResult(success = false, error = "Not signed in")
        return try {
            var workoutsImported = 0
            var routinesImported = 0

            // Pull workouts
            val workoutDocs = firestore.collection("users").document(uid)
                .collection("workouts").get().await()
            for (doc in workoutDocs.documents) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val localWorkout = workoutDao.getWorkoutById(doc.id)
                val localUpdatedAt = localWorkout?.updatedAt ?: 0L

                if (remoteUpdatedAt >= localUpdatedAt) {
                    val workout = doc.toWorkout() ?: continue
                    database.withTransaction {
                        workoutDao.insertWorkout(workout)  // REPLACE strategy
                        if (!workout.isArchived) {
                            // Non-archived: restore sets atomically
                            val sets = doc.toWorkoutSets(doc.id)
                            workoutSetDao.deleteSetsForWorkout(doc.id)
                            workoutSetDao.insertSets(sets)
                        }
                        // Archived: tombstone only — no set restoration needed
                    }
                    workoutsImported++
                }
            }

            // Pull routines
            val routineDocs = firestore.collection("users").document(uid)
                .collection("routines").get().await()
            for (doc in routineDocs.documents) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val localRoutine = routineDao.getRoutineById(doc.id)
                val localUpdatedAt = localRoutine?.updatedAt ?: 0L

                if (remoteUpdatedAt >= localUpdatedAt) {
                    val routine = doc.toRoutine() ?: continue
                    database.withTransaction {
                        routineDao.insertRoutine(routine)  // REPLACE strategy
                        if (!routine.isArchived) {
                            // Non-archived: restore exercises atomically
                            val exercises = doc.toRoutineExercises(doc.id)
                            routineExerciseDao.deleteAllForRoutine(doc.id)
                            routineExerciseDao.insertAll(exercises)
                        }
                        // Archived: tombstone only — no exercise restoration needed
                    }
                    routinesImported++
                }
            }

            SyncResult(success = true, workoutsImported = workoutsImported, routinesImported = routinesImported)
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message)
        }
    }
}

// ── Serialization helpers ────────────────────────────────────────────────────

private fun Workout.toFirestoreMap(sets: List<WorkoutSet>): Map<String, Any?> = mapOf(
    "id" to id,
    "routineId" to routineId,
    "timestamp" to timestamp,
    "durationSeconds" to durationSeconds,
    "totalVolume" to totalVolume,
    "notes" to notes,
    "isCompleted" to isCompleted,
    "startTimeMs" to startTimeMs,
    "endTimeMs" to endTimeMs,
    "updatedAt" to updatedAt,
    "isArchived" to isArchived,
    "sets" to sets.map { it.toFirestoreMap() }
)

private fun WorkoutSet.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workoutId" to workoutId,
    "exerciseId" to exerciseId,
    "setOrder" to setOrder,
    "weight" to weight,
    "reps" to reps,
    "rpe" to rpe,
    "setType" to setType.name,
    "setNotes" to setNotes,
    "distance" to distance,
    "timeSeconds" to timeSeconds,
    "startTime" to startTime,
    "endTime" to endTime,
    "restDuration" to restDuration,
    "supersetGroupId" to supersetGroupId,
    "isCompleted" to isCompleted
)

private fun Routine.toFirestoreMap(exercises: List<RoutineExercise>): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "lastPerformed" to lastPerformed,
    "isCustom" to isCustom,
    "isArchived" to isArchived,
    "updatedAt" to updatedAt,
    "exercises" to exercises.map { it.toFirestoreMap() }
)

private fun RoutineExercise.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "routineId" to routineId,
    "exerciseId" to exerciseId,
    "sets" to sets,
    "reps" to reps,
    "restTime" to restTime,
    "order" to order,
    "supersetGroupId" to supersetGroupId,
    "stickyNote" to stickyNote,
    "defaultWeight" to defaultWeight,
    "setTypesJson" to setTypesJson,
    "setWeightsJson" to setWeightsJson,
    "setRepsJson" to setRepsJson
)

// ── Deserialization helpers ──────────────────────────────────────────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toWorkout(): Workout? {
    val id = getString("id") ?: return null
    return Workout(
        id = id,
        routineId = getString("routineId"),
        timestamp = getLong("timestamp") ?: return null,
        durationSeconds = getLong("durationSeconds")?.toInt() ?: 0,
        totalVolume = getDouble("totalVolume") ?: 0.0,
        notes = getString("notes"),
        isCompleted = getBoolean("isCompleted") ?: false,
        startTimeMs = getLong("startTimeMs") ?: 0L,
        endTimeMs = getLong("endTimeMs") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L,
        isArchived = getBoolean("isArchived") ?: false
    )
}

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.toWorkoutSets(workoutId: String): List<WorkoutSet> {
    val rawSets = get("sets") as? List<Map<String, Any?>> ?: return emptyList()
    return rawSets.mapNotNull { map ->
        val setId = map["id"] as? String ?: return@mapNotNull null
        WorkoutSet(
            id = setId,
            workoutId = workoutId,
            exerciseId = (map["exerciseId"] as? Long) ?: (map["exerciseId"] as? Number)?.toLong() ?: return@mapNotNull null,
            setOrder = (map["setOrder"] as? Long)?.toInt() ?: (map["setOrder"] as? Number)?.toInt() ?: 0,
            weight = (map["weight"] as? Double) ?: (map["weight"] as? Number)?.toDouble() ?: 0.0,
            reps = (map["reps"] as? Long)?.toInt() ?: (map["reps"] as? Number)?.toInt() ?: 0,
            rpe = (map["rpe"] as? Long)?.toInt() ?: (map["rpe"] as? Number)?.toInt(),
            setType = runCatching { SetType.valueOf(map["setType"] as? String ?: "") }.getOrDefault(SetType.NORMAL),
            setNotes = map["setNotes"] as? String,
            distance = (map["distance"] as? Double) ?: (map["distance"] as? Number)?.toDouble(),
            timeSeconds = (map["timeSeconds"] as? Long)?.toInt() ?: (map["timeSeconds"] as? Number)?.toInt(),
            startTime = (map["startTime"] as? Long) ?: (map["startTime"] as? Number)?.toLong(),
            endTime = (map["endTime"] as? Long) ?: (map["endTime"] as? Number)?.toLong(),
            restDuration = (map["restDuration"] as? Long)?.toInt() ?: (map["restDuration"] as? Number)?.toInt(),
            supersetGroupId = map["supersetGroupId"] as? String,
            isCompleted = map["isCompleted"] as? Boolean ?: false
        )
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toRoutine(): Routine? {
    val id = getString("id") ?: return null
    return Routine(
        id = id,
        name = getString("name") ?: return null,
        lastPerformed = getLong("lastPerformed"),
        isCustom = getBoolean("isCustom") ?: false,
        isArchived = getBoolean("isArchived") ?: false,
        updatedAt = getLong("updatedAt") ?: 0L
    )
}

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.toRoutineExercises(routineId: String): List<RoutineExercise> {
    val rawExercises = get("exercises") as? List<Map<String, Any?>> ?: return emptyList()
    return rawExercises.mapNotNull { map ->
        val id = map["id"] as? String ?: return@mapNotNull null
        RoutineExercise(
            id = id,
            routineId = routineId,
            exerciseId = (map["exerciseId"] as? Long) ?: (map["exerciseId"] as? Number)?.toLong() ?: return@mapNotNull null,
            sets = (map["sets"] as? Long)?.toInt() ?: (map["sets"] as? Number)?.toInt() ?: 3,
            reps = (map["reps"] as? Long)?.toInt() ?: (map["reps"] as? Number)?.toInt() ?: 10,
            restTime = (map["restTime"] as? Long)?.toInt() ?: (map["restTime"] as? Number)?.toInt() ?: 90,
            order = (map["order"] as? Long)?.toInt() ?: (map["order"] as? Number)?.toInt() ?: 0,
            supersetGroupId = map["supersetGroupId"] as? String,
            stickyNote = map["stickyNote"] as? String,
            defaultWeight = map["defaultWeight"] as? String ?: "",
            setTypesJson = map["setTypesJson"] as? String ?: "",
            setWeightsJson = map["setWeightsJson"] as? String ?: "",
            setRepsJson = map["setRepsJson"] as? String ?: ""
        )
    }
}
