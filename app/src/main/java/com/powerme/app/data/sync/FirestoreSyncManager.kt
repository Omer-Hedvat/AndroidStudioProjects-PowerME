package com.powerme.app.data.sync

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val workoutsImported: Int = 0,
    val routinesImported: Int = 0,
    val profileImported: Boolean = false,
    val settingsImported: Boolean = false,
    val error: String? = null
) {
    fun toUserMessage() = when {
        !success -> "Sync failed: ${error ?: "unknown"}"
        workoutsImported == 0 && routinesImported == 0 && !profileImported && !settingsImported -> "Already up to date"
        else -> buildString {
            if (profileImported) append("Profile restored")
            if (settingsImported) {
                if (profileImported) append(", ")
                append("Settings restored")
            }
            if (workoutsImported > 0 || routinesImported > 0) {
                if (profileImported || settingsImported) append(", ")
                append("$workoutsImported workouts, $routinesImported routines imported")
            }
        }
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
    private val routineExerciseDao: RoutineExerciseDao,
    private val exerciseDao: ExerciseDao,
    private val userDao: UserDao,
    private val userSettingsDao: UserSettingsDao,
    private val appSettingsDataStore: AppSettingsDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun userRef(uid: String) = firestore.collection("users").document(uid)

    // ── Push ────────────────────────────────────────────────────────────────

    fun pushProfile() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val user = userDao.getCurrentUser() ?: return@launch
            userRef(uid).collection("profile").document("data").set(user.toFirestoreMap())
        }
    }

    fun pushWorkout(workoutId: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val workout = workoutDao.getWorkoutById(workoutId) ?: return@launch
            val sets = workoutSetDao.getSetsForWorkout(workoutId).first()
            val exerciseIds = sets.map { it.exerciseId }.toSet().toList()
            val exerciseMap = exerciseDao.getByIds(exerciseIds).associateBy { it.id }
            userRef(uid).collection("workouts").document(workoutId)
                .set(workout.toFirestoreMap(sets, exerciseMap))
        }
    }

    fun pushRoutine(routineId: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val routine = routineDao.getRoutineById(routineId) ?: return@launch
            val exercises = routineExerciseDao.getForRoutine(routineId)
            val exerciseIds = exercises.map { it.exerciseId }.toSet().toList()
            val exerciseMap = exerciseDao.getByIds(exerciseIds).associateBy { it.id }
            userRef(uid).collection("routines").document(routineId)
                .set(routine.toFirestoreMap(exercises, exerciseMap))
        }
    }

    fun pushSettings() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val settings = userSettingsDao.getSettingsOnce() ?: return@launch
            userRef(uid).collection("settings").document("data")
                .set(settings.toFirestoreMap())
        }
    }

    fun pushAppPreferences() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            val map = mapOf(
                "themeMode"      to appSettingsDataStore.themeMode.first().name,
                "unitSystem"     to appSettingsDataStore.unitSystem.first().name,
                "dailyStepTarget" to appSettingsDataStore.dailyStepTarget.first(),
                "keepScreenOn"        to appSettingsDataStore.keepScreenOn.first(),
                "useRpeAutoPop"       to appSettingsDataStore.useRpeAutoPop.first(),
                "timedSetSetupSeconds" to appSettingsDataStore.timedSetSetupSeconds.first(),
                "language"            to appSettingsDataStore.language.first()
            )
            userRef(uid).collection("appPreferences").document("data").set(map)
        }
    }

    /** Pushes all local workouts, routines, profile, settings, and preferences to Firestore. */
    suspend fun pushAllToCloud(): SyncResult {
        val uid = auth.currentUser?.uid
            ?: return SyncResult(success = false, error = "Not signed in")
        return try {
            val workouts = workoutDao.getAllWorkouts().first().filter { !it.isArchived }
            val routines = routineDao.getAllRoutines().first().filter { !it.isArchived }
            for (workout in workouts) {
                val sets = workoutSetDao.getSetsForWorkout(workout.id).first()
                val exerciseIds = sets.map { it.exerciseId }.toSet().toList()
                val exerciseMap = exerciseDao.getByIds(exerciseIds).associateBy { it.id }
                userRef(uid).collection("workouts").document(workout.id)
                    .set(workout.toFirestoreMap(sets, exerciseMap)).await()
            }
            for (routine in routines) {
                val exercises = routineExerciseDao.getForRoutine(routine.id)
                val exerciseIds = exercises.map { it.exerciseId }.toSet().toList()
                val exerciseMap = exerciseDao.getByIds(exerciseIds).associateBy { it.id }
                userRef(uid).collection("routines").document(routine.id)
                    .set(routine.toFirestoreMap(exercises, exerciseMap)).await()
            }
            userDao.getCurrentUser()?.let { user ->
                userRef(uid).collection("profile").document("data").set(user.toFirestoreMap()).await()
            }
            userSettingsDao.getSettingsOnce()?.let { settings ->
                userRef(uid).collection("settings").document("data").set(settings.toFirestoreMap()).await()
            }
            val prefsMap = mapOf(
                "themeMode"       to appSettingsDataStore.themeMode.first().name,
                "unitSystem"      to appSettingsDataStore.unitSystem.first().name,
                "dailyStepTarget" to appSettingsDataStore.dailyStepTarget.first(),
                "keepScreenOn"         to appSettingsDataStore.keepScreenOn.first(),
                "useRpeAutoPop"        to appSettingsDataStore.useRpeAutoPop.first(),
                "timedSetSetupSeconds" to appSettingsDataStore.timedSetSetupSeconds.first(),
                "language"             to appSettingsDataStore.language.first()
            )
            userRef(uid).collection("appPreferences").document("data").set(prefsMap).await()
            SyncResult(success = true, workoutsImported = workouts.size, routinesImported = routines.size, profileImported = true, settingsImported = true)
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message)
        }
    }

    // ── Pull ────────────────────────────────────────────────────────────────

    /**
     * Pulls only the user profile from Firestore. Used on first sign-in so navigation
     * can decide needsProfileSetup before workouts/routines finish syncing in background.
     * Returns true if a profile was imported.
     */
    suspend fun pullProfileOnly(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val doc = userRef(uid).collection("profile").document("data").get().await()
            if (!doc.exists()) return false
            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
            val localUpdatedAt = userDao.getCurrentUser()?.updatedAt ?: 0L
            if (remoteUpdatedAt >= localUpdatedAt) {
                val user = doc.toUser() ?: return false
                userDao.insertUser(user)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /** Pulls UserSettings from Firestore. Returns true if settings were imported. */
    suspend fun pullSettings(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val doc = userRef(uid).collection("settings").document("data").get().await()
            if (!doc.exists()) return false
            val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
            val localUpdatedAt = userSettingsDao.getSettingsOnce()?.updatedAt ?: 0L
            if (remoteUpdatedAt >= localUpdatedAt) {
                val settings = doc.toUserSettings() ?: return false
                userSettingsDao.insertSettings(settings)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    /** Pulls AppSettingsDataStore preferences (theme, unit system, etc.) from Firestore. */
    suspend fun pullAppPreferences(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            val doc = userRef(uid).collection("appPreferences").document("data").get().await()
            if (!doc.exists()) return false
            doc.getString("themeMode")?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
                    ?.let { appSettingsDataStore.setThemeMode(it) }
            }
            doc.getString("unitSystem")?.let { name ->
                UnitSystem.entries.firstOrNull { it.name == name }
                    ?.let { appSettingsDataStore.setUnitSystem(it) }
            }
            doc.getLong("dailyStepTarget")?.let { appSettingsDataStore.setDailyStepTarget(it.toInt()) }
            doc.getBoolean("keepScreenOn")?.let { appSettingsDataStore.setKeepScreenOn(it) }
            doc.getBoolean("useRpeAutoPop")?.let { appSettingsDataStore.setUseRpeAutoPop(it) }
            doc.getLong("timedSetSetupSeconds")?.let { appSettingsDataStore.setTimedSetSetupSeconds(it.toInt()) }
            doc.getString("language")?.let { appSettingsDataStore.setLanguage(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Fire-and-forget full sync — used after profile is already pulled on first sign-in. */
    fun launchBackgroundSync() {
        scope.launch { pullFromCloud() }
    }

    suspend fun pullFromCloud(): SyncResult {
        val uid = auth.currentUser?.uid
            ?: return SyncResult(success = false, error = "Not signed in")
        return try {
            // Fetch all collections in parallel to minimise round-trip latency
            val profileDoc: com.google.firebase.firestore.DocumentSnapshot
            val settingsDoc: com.google.firebase.firestore.DocumentSnapshot
            val appPrefsDoc: com.google.firebase.firestore.DocumentSnapshot
            val workoutDocs: com.google.firebase.firestore.QuerySnapshot
            val routineDocs: com.google.firebase.firestore.QuerySnapshot
            coroutineScope {
                val profileDeferred  = async { userRef(uid).collection("profile").document("data").get().await() }
                val settingsDeferred = async { userRef(uid).collection("settings").document("data").get().await() }
                val appPrefsDeferred = async { userRef(uid).collection("appPreferences").document("data").get().await() }
                val workoutsDeferred = async { userRef(uid).collection("workouts").get().await() }
                val routinesDeferred = async { userRef(uid).collection("routines").get().await() }
                profileDoc  = profileDeferred.await()
                settingsDoc = settingsDeferred.await()
                appPrefsDoc = appPrefsDeferred.await()
                workoutDocs = workoutsDeferred.await()
                routineDocs = routinesDeferred.await()
            }

            var workoutsImported = 0
            var routinesImported = 0
            var profileImported = false
            var settingsImported = false

            if (profileDoc.exists()) {
                val remoteUpdatedAt = profileDoc.getLong("updatedAt") ?: 0L
                val localUpdatedAt = userDao.getCurrentUser()?.updatedAt ?: 0L
                if (remoteUpdatedAt >= localUpdatedAt) {
                    val user = profileDoc.toUser()
                    if (user != null) {
                        userDao.insertUser(user)
                        profileImported = true
                    }
                }
            }

            if (settingsDoc.exists()) {
                val remoteUpdatedAt = settingsDoc.getLong("updatedAt") ?: 0L
                val localUpdatedAt = userSettingsDao.getSettingsOnce()?.updatedAt ?: 0L
                if (remoteUpdatedAt >= localUpdatedAt) {
                    val settings = settingsDoc.toUserSettings()
                    if (settings != null) {
                        userSettingsDao.insertSettings(settings)
                        settingsImported = true
                    }
                }
            }

            // App preferences are not versioned with updatedAt — always apply if present
            if (appPrefsDoc.exists()) {
                appPrefsDoc.getString("themeMode")?.let { name ->
                    ThemeMode.entries.firstOrNull { it.name == name }
                        ?.let { appSettingsDataStore.setThemeMode(it) }
                }
                appPrefsDoc.getString("unitSystem")?.let { name ->
                    UnitSystem.entries.firstOrNull { it.name == name }
                        ?.let { appSettingsDataStore.setUnitSystem(it) }
                }
                appPrefsDoc.getLong("dailyStepTarget")?.let { appSettingsDataStore.setDailyStepTarget(it.toInt()) }
                appPrefsDoc.getBoolean("keepScreenOn")?.let { appSettingsDataStore.setKeepScreenOn(it) }
                appPrefsDoc.getBoolean("useRpeAutoPop")?.let { appSettingsDataStore.setUseRpeAutoPop(it) }
                appPrefsDoc.getLong("timedSetSetupSeconds")?.let { appSettingsDataStore.setTimedSetSetupSeconds(it.toInt()) }
                appPrefsDoc.getString("language")?.let { appSettingsDataStore.setLanguage(it) }
            }

            // Routines must be restored before workouts — workouts FK-reference routines via routineId
            for (doc in routineDocs.documents) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val localRoutine = routineDao.getRoutineById(doc.id)
                val localUpdatedAt = localRoutine?.updatedAt ?: 0L

                if (remoteUpdatedAt >= localUpdatedAt) {
                    val routine = doc.toRoutine() ?: continue
                    try {
                        database.withTransaction {
                            routineDao.insertRoutine(routine)  // REPLACE strategy
                            if (!routine.isArchived) {
                                val exercises = doc.toRoutineExercisesResolved(doc.id)
                                routineExerciseDao.deleteAllForRoutine(doc.id)
                                if (exercises.isNotEmpty()) routineExerciseDao.insertAll(exercises)
                            }
                        }
                        routinesImported++
                    } catch (e: Exception) {
                        android.util.Log.w("PowerME_Sync", "Skipping routine ${doc.id}: ${e.message}")
                    }
                }
            }

            for (doc in workoutDocs.documents) {
                val remoteUpdatedAt = doc.getLong("updatedAt") ?: 0L
                val localWorkout = workoutDao.getWorkoutById(doc.id)
                val localUpdatedAt = localWorkout?.updatedAt ?: 0L

                if (remoteUpdatedAt >= localUpdatedAt) {
                    val workout = doc.toWorkout() ?: continue
                    // Preserve local routineName if remote lacks it (pre-v36 Firestore docs)
                    val mergedWorkout = if (workout.routineName == null && localWorkout?.routineName != null) {
                        workout.copy(routineName = localWorkout.routineName)
                    } else workout
                    try {
                        database.withTransaction {
                            workoutDao.insertWorkout(mergedWorkout)  // REPLACE strategy
                            if (!mergedWorkout.isArchived) {
                                val sets = doc.toWorkoutSetsResolved(doc.id)
                                workoutSetDao.deleteSetsForWorkout(doc.id)
                                if (sets.isNotEmpty()) workoutSetDao.insertSets(sets)
                            }
                        }
                        workoutsImported++
                    } catch (e: Exception) {
                        android.util.Log.w("PowerME_Sync", "Skipping workout ${doc.id}: ${e.message}")
                    }
                }
            }

            SyncResult(success = true, workoutsImported = workoutsImported, routinesImported = routinesImported, profileImported = profileImported, settingsImported = settingsImported)
        } catch (e: Exception) {
            SyncResult(success = false, error = e.message)
        }
    }

    /**
     * Resolves exercise identity (name + equipment or syncId) back to a local Long PK.
     * Falls back to the raw exerciseId if identity fields are absent (legacy push format).
     * Returns null if the exercise cannot be resolved — callers should skip the item.
     */
    private suspend fun resolveExerciseId(
        rawId: Long?,
        name: String?,
        equipment: String?,
        syncId: String?
    ): Long? {
        // Custom exercises: resolve by syncId
        if (!syncId.isNullOrBlank()) {
            val ex = exerciseDao.getBySyncId(syncId)
            if (ex != null) return ex.id
        }
        // Master exercises: resolve by name + equipmentType
        if (!name.isNullOrBlank() && !equipment.isNullOrBlank()) {
            val ex = exerciseDao.getByNameAndEquipment(name, equipment)
            if (ex != null) return ex.id
        }
        // Legacy format (no identity fields): verify the raw ID exists locally before using it
        if (rawId != null && exerciseDao.getByIds(listOf(rawId)).isNotEmpty()) return rawId
        return null
    }

    // ── Private pull helpers (suspend, can call exerciseDao) ────────────────

    @Suppress("UNCHECKED_CAST")
    private suspend fun com.google.firebase.firestore.DocumentSnapshot.toWorkoutSetsResolved(
        workoutId: String
    ): List<WorkoutSet> {
        val rawSets = get("sets") as? List<Map<String, Any?>> ?: return emptyList()
        return rawSets.mapNotNull { map ->
            val setId = map["id"] as? String ?: return@mapNotNull null
            val rawExerciseId = (map["exerciseId"] as? Long) ?: (map["exerciseId"] as? Number)?.toLong()
            val exerciseId = resolveExerciseId(
                rawId = rawExerciseId,
                name = map["exerciseName"] as? String,
                equipment = map["exerciseEquipment"] as? String,
                syncId = map["exerciseSyncId"] as? String
            ) ?: return@mapNotNull null  // skip set if exercise cannot be resolved
            WorkoutSet(
                id = setId,
                workoutId = workoutId,
                exerciseId = exerciseId,
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

    @Suppress("UNCHECKED_CAST")
    private suspend fun com.google.firebase.firestore.DocumentSnapshot.toRoutineExercisesResolved(
        routineId: String
    ): List<RoutineExercise> {
        val rawExercises = get("exercises") as? List<Map<String, Any?>> ?: return emptyList()
        return rawExercises.mapNotNull { map ->
            val id = map["id"] as? String ?: return@mapNotNull null
            val rawExerciseId = (map["exerciseId"] as? Long) ?: (map["exerciseId"] as? Number)?.toLong()
            val exerciseId = resolveExerciseId(
                rawId = rawExerciseId,
                name = map["exerciseName"] as? String,
                equipment = map["exerciseEquipment"] as? String,
                syncId = map["exerciseSyncId"] as? String
            ) ?: return@mapNotNull null  // skip exercise if it cannot be resolved
            RoutineExercise(
                id = id,
                routineId = routineId,
                exerciseId = exerciseId,
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
}

// ── Serialization helpers ────────────────────────────────────────────────────

private fun Workout.toFirestoreMap(sets: List<WorkoutSet>, exerciseMap: Map<Long, Exercise>): Map<String, Any?> = mapOf(
    "id" to id,
    "routineId" to routineId,
    "routineName" to routineName,
    "timestamp" to timestamp,
    "durationSeconds" to durationSeconds,
    "totalVolume" to totalVolume,
    "notes" to notes,
    "isCompleted" to isCompleted,
    "startTimeMs" to startTimeMs,
    "endTimeMs" to endTimeMs,
    "updatedAt" to updatedAt,
    "isArchived" to isArchived,
    "sets" to sets.map { it.toFirestoreMap(exerciseMap[it.exerciseId]) }
)

private fun WorkoutSet.toFirestoreMap(exercise: Exercise?): Map<String, Any?> = mapOf(
    "id" to id,
    "workoutId" to workoutId,
    "exerciseId" to exerciseId,
    "exerciseName" to exercise?.name,
    "exerciseEquipment" to exercise?.equipmentType,
    "exerciseSyncId" to exercise?.syncId?.takeIf { exercise.isCustom },
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

private fun Routine.toFirestoreMap(exercises: List<RoutineExercise>, exerciseMap: Map<Long, Exercise>): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "lastPerformed" to lastPerformed,
    "isCustom" to isCustom,
    "isArchived" to isArchived,
    "updatedAt" to updatedAt,
    "exercises" to exercises.map { it.toFirestoreMap(exerciseMap[it.exerciseId]) }
)

private fun RoutineExercise.toFirestoreMap(exercise: Exercise?): Map<String, Any?> = mapOf(
    "id" to id,
    "routineId" to routineId,
    "exerciseId" to exerciseId,
    "exerciseName" to exercise?.name,
    "exerciseEquipment" to exercise?.equipmentType,
    "exerciseSyncId" to exercise?.syncId?.takeIf { exercise.isCustom },
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

// ── UserSettings serialization ────────────────────────────────────────────────

private fun UserSettings.toFirestoreMap(): Map<String, Any?> = mapOf(
    "availablePlates"          to availablePlates,
    "restTimerAudioEnabled"    to restTimerAudioEnabled,
    "restTimerHapticsEnabled"  to restTimerHapticsEnabled,
    "language"                 to language,
    "updatedAt"                to updatedAt
)

// ── User serialization ───────────────────────────────────────────────────────

private fun User.toFirestoreMap(): Map<String, Any?> = mapOf(
    "email"             to email,
    "name"              to name,
    "age"               to age,
    "dateOfBirth"       to dateOfBirth,
    "heightCm"          to heightCm,
    "weightKg"          to weightKg,
    "bodyFatPercent"    to bodyFatPercent,
    "occupationType"    to occupationType,
    "averageSleepHours" to averageSleepHours,
    "chronotype"        to chronotype,
    "parentalLoad"      to parentalLoad,
    "createdAt"         to createdAt,
    "gender"            to gender,
    "trainingTargets"   to trainingTargets,
    "updatedAt"         to updatedAt
)

private fun com.google.firebase.firestore.DocumentSnapshot.toUser(): User? {
    val email = getString("email") ?: return null
    return User(
        email = email,
        name = getString("name"),
        age = getLong("age")?.toInt(),
        dateOfBirth = getLong("dateOfBirth"),
        heightCm = getDouble("heightCm")?.toFloat(),
        weightKg = getDouble("weightKg")?.toFloat(),
        bodyFatPercent = getDouble("bodyFatPercent")?.toFloat(),
        occupationType = getString("occupationType"),
        averageSleepHours = getDouble("averageSleepHours")?.toFloat(),
        chronotype = getString("chronotype"),
        parentalLoad = getLong("parentalLoad")?.toInt(),
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
        gender = getString("gender"),
        trainingTargets = getString("trainingTargets"),
        updatedAt = getLong("updatedAt") ?: 0L
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserSettings(): UserSettings? {
    return UserSettings(
        id                    = 1,
        availablePlates       = getString("availablePlates") ?: "0.5,1.25,2.5,5,10,15,20,25",
        restTimerAudioEnabled = getBoolean("restTimerAudioEnabled") ?: true,
        restTimerHapticsEnabled = getBoolean("restTimerHapticsEnabled") ?: true,
        language              = getString("language") ?: "Hebrew",
        updatedAt             = getLong("updatedAt") ?: 0L
    )
}

// ── Deserialization helpers (non-suspend, used for simple fields) ────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toWorkout(): Workout? {
    val id = getString("id") ?: return null
    return Workout(
        id = id,
        routineId = getString("routineId"),
        routineName = getString("routineName"),
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
