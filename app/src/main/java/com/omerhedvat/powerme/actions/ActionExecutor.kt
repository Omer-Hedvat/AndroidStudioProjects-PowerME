package com.omerhedvat.powerme.actions

import android.util.Log
import com.omerhedvat.powerme.data.database.Routine
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.RoutineExercise
import com.omerhedvat.powerme.data.database.RoutineExerciseDao
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import com.omerhedvat.powerme.ui.chat.GoalDocument
import com.omerhedvat.powerme.ui.chat.MedicalRestrictionsDoc
import com.omerhedvat.powerme.ui.chat.TrainingPhase
import com.omerhedvat.powerme.ui.chat.YellowEntry
import com.omerhedvat.powerme.util.GoalDocumentManager
import kotlinx.coroutines.flow.first
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes ActionBlock instances by performing the corresponding database operations.
 *
 * Uses strategy pattern to handle different action types.
 * Each action type is delegated to a specific execution method.
 */
@Singleton
class ActionExecutor @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val gymProfileRepository: GymProfileRepository,
    private val exerciseRepository: ExerciseRepository,
    private val goalDocumentManager: GoalDocumentManager,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao
) {

    /**
     * Executes an ActionBlock and returns the result.
     *
     * @param action The action to execute
     * @param activeWorkoutId The ID of the currently active workout (required for UpdateWeight)
     * @return ActionResult indicating success or failure
     */
    suspend fun execute(action: ActionBlock, activeWorkoutId: Long?): ActionResult {
        return when (action) {
            is ActionBlock.UpdateWeight -> executeUpdateWeight(action, activeWorkoutId)
            is ActionBlock.UpdateInjury -> executeUpdateInjury(action)
            is ActionBlock.SwitchGym -> executeSwitchGym(action)
            is ActionBlock.UpdateEquipment -> executeUpdateEquipment(action)
            is ActionBlock.SaveUserOnboarding -> executeSaveUserOnboarding(action)
            is ActionBlock.CreateWorkoutRoutine -> executeCreateWorkoutRoutine(action)
        }
    }

    /**
     * Updates the weight for a specific set in the active workout.
     */
    private suspend fun executeUpdateWeight(
        action: ActionBlock.UpdateWeight,
        activeWorkoutId: Long?
    ): ActionResult {
        try {
            if (activeWorkoutId == null) {
                return ActionResult.Failure("No active workout found. Start a workout first.")
            }

            val sets = workoutRepository.getSetsForWorkout(activeWorkoutId).first()

            if (sets.isEmpty()) {
                return ActionResult.Failure("No sets found in active workout")
            }

            val targetSet = if (action.setIndex != null) {
                sets.getOrNull(action.setIndex - 1)
            } else {
                sets.lastOrNull()
            }

            if (targetSet == null) {
                return ActionResult.Failure(
                    if (action.setIndex != null) "Set ${action.setIndex} not found"
                    else "No sets available to update"
                )
            }

            val updatedSet = targetSet.copy(weight = action.weightKg)
            workoutRepository.updateSet(updatedSet)

            val allSets = workoutRepository.getSetsForWorkout(activeWorkoutId).first()
            val totalVolume = allSets.sumOf { it.weight * it.reps }
            val workout = workoutRepository.getWorkoutById(activeWorkoutId)
            if (workout != null) {
                workoutRepository.updateWorkout(workout.copy(totalVolume = totalVolume))
            }

            return ActionResult.Success("Weight updated to ${action.weightKg}kg for set ${targetSet.setOrder}")

        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error updating weight", e)
            return ActionResult.Failure("Failed to update weight: ${e.message}")
        }
    }

    private suspend fun executeUpdateInjury(action: ActionBlock.UpdateInjury): ActionResult {
        return ActionResult.Failure("Injury tracking not yet implemented. Coming in Sprint 6.")
    }

    private suspend fun executeSwitchGym(action: ActionBlock.SwitchGym): ActionResult {
        try {
            val success = gymProfileRepository.setActiveProfileByName(action.gymProfileName)
            return if (success) {
                ActionResult.Success("Switched to ${action.gymProfileName} gym")
            } else {
                ActionResult.Failure("Gym profile '${action.gymProfileName}' not found")
            }
        } catch (e: Exception) {
            return ActionResult.Failure("Failed to switch gym: ${e.message}")
        }
    }

    private suspend fun executeUpdateEquipment(action: ActionBlock.UpdateEquipment): ActionResult {
        try {
            val profile = gymProfileRepository.getProfileByName(action.gymProfileName)
                ?: return ActionResult.Failure("Gym profile '${action.gymProfileName}' not found")

            val equipmentString = action.equipment.joinToString(",")
            gymProfileRepository.updateProfile(profile.copy(equipment = equipmentString))

            return ActionResult.Success("Updated equipment for ${action.gymProfileName}: ${action.equipment.size} items")
        } catch (e: Exception) {
            return ActionResult.Failure("Failed to update equipment: ${e.message}")
        }
    }

    /**
     * Handles save_user_onboarding with strict validation.
     * Invalid phase or deadline returns Failure — interview stays open until valid JSON received.
     */
    private suspend fun executeSaveUserOnboarding(action: ActionBlock.SaveUserOnboarding): ActionResult {
        val raw = action.data.goalDocument

        // Validate phase
        val phase = try {
            TrainingPhase.valueOf(raw.currentPhase.uppercase())
        } catch (e: IllegalArgumentException) {
            return ActionResult.Failure(
                "Invalid phase '${raw.currentPhase}'. Expected: MASSING, CUTTING, or MAINTENANCE"
            )
        }

        // Validate deadline
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.isLenient = false
        try {
            dateFormat.parse(raw.targetDeadline)
                ?: return ActionResult.Failure("Invalid deadline '${raw.targetDeadline}'. Expected format: YYYY-MM-DD")
        } catch (e: ParseException) {
            return ActionResult.Failure("Invalid deadline '${raw.targetDeadline}'. Expected format: YYYY-MM-DD")
        }

        return try {
            val goalDoc = GoalDocument(
                phase = phase.name,
                deadline = raw.targetDeadline,
                priorityMuscles = raw.priorityMuscleGroups,
                sessionConstraints = raw.lifestyleConstraints,
                weeklySessionCount = raw.weeklySessionCount
            )
            goalDocumentManager.saveGoalDocument(goalDoc)

            val medicalDoc = MedicalRestrictionsDoc(
                redList = action.data.medicalRestrictions.redList,
                yellowList = action.data.medicalRestrictions.yellowList.map {
                    YellowEntry(exercise = it.exercise, requiredCue = it.modificationCue)
                },
                injuryHistorySummary = action.data.medicalRestrictions.injuryHistorySummary
            )
            medicalLedgerRepository.saveLedger(medicalDoc)

            Log.d("ActionExecutor", "Onboarding saved: phase=${phase.name}, deadline=${raw.targetDeadline}")
            ActionResult.Success("Onboarding complete — phase: ${phase.name}, deadline: ${raw.targetDeadline}")

        } catch (e: Exception) {
            Log.e("ActionExecutor", "Onboarding save failed", e)
            ActionResult.Failure("Onboarding JSON parse error: ${e.message}")
        }
    }

    /**
     * Handles create_workout_routine with fuzzy exercise matching (Levenshtein distance).
     * Atomic — if any exercise fails to match, the entire routine is NOT saved.
     */
    private suspend fun executeCreateWorkoutRoutine(action: ActionBlock.CreateWorkoutRoutine): ActionResult {
        try {
            val allExercises = exerciseRepository.getAllExercises().first()
            val unmatchedNames = mutableListOf<String>()
            val matchedExercises = action.exercises.map { planned ->
                val match = findBestMatch(planned.exerciseName, allExercises.map { it.name })
                if (match == null) {
                    unmatchedNames.add(planned.exerciseName)
                    null
                } else {
                    planned to match
                }
            }

            if (unmatchedNames.isNotEmpty()) {
                return ActionResult.Failure(
                    "Unknown exercise(s): ${unmatchedNames.joinToString(", ")} — routine not saved"
                )
            }

            // Save routine to DB
            val routineId = routineDao.insertRoutine(
                Routine(name = action.routineName, isCustom = true)
            )

            // Save routine_exercises entries linking routine to each matched exercise
            matchedExercises.filterNotNull().forEachIndexed { index, (_, matchedName) ->
                val exercise = allExercises.firstOrNull { it.name == matchedName }
                if (exercise != null) {
                    routineExerciseDao.insert(RoutineExercise(routineId = routineId, exerciseId = exercise.id, order = index))
                }
            }

            Log.d("ActionExecutor", "Routine '${action.routineName}' saved with id=$routineId, ${matchedExercises.size} exercises")
            return ActionResult.Success(
                "Routine '${action.routineName}' saved — id:$routineId — ${action.exercises.size} exercises"
            )

        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error creating routine", e)
            return ActionResult.Failure("Failed to create routine: ${e.message}")
        }
    }

    /**
     * Finds the best matching exercise name using Levenshtein distance.
     * Returns null if the best similarity is below the 0.8 threshold.
     */
    private fun findBestMatch(query: String, candidates: List<String>): String? {
        val queryLower = query.lowercase()
        var bestMatch: String? = null
        var bestSimilarity = 0.0

        for (candidate in candidates) {
            val similarity = stringSimilarity(queryLower, candidate.lowercase())
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = candidate
            }
        }

        return if (bestSimilarity >= 0.8) bestMatch else null
    }

    /**
     * Computes string similarity as 1 - (levenshtein / maxLen).
     */
    private fun stringSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
            }
        }
        return dp[a.length][b.length]
    }
}
