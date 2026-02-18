package com.omerhedvat.powerme.actions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Polymorphic sealed class representing actions that can be executed by the War Room AI.
 *
 * Each action type corresponds to a specific database operation that Gemini can trigger
 * through natural language commands.
 */
@Serializable
sealed class ActionBlock {

    /**
     * Updates the weight for a specific set in the active workout.
     *
     * Example user command: "Set current weight to 92kg" or "Change last set to 100kg"
     *
     * @param weightKg The new weight value in kilograms
     * @param setIndex The 1-based index of the set to update (null = last set)
     */
    @Serializable
    @SerialName("update_weight")
    data class UpdateWeight(
        val weightKg: Double,
        val setIndex: Int? = null
    ) : ActionBlock()

    /**
     * Updates or creates an injury tracker entry.
     *
     * Example user command: "My lower back hurts at level 7" or "Elbow pain is at 8 today"
     *
     * @param joint The affected joint (maps to TargetJoint enum)
     * @param severity Pain level on 1-10 scale
     * @param notes Optional description of the injury/pain
     */
    @Serializable
    @SerialName("update_injury")
    data class UpdateInjury(
        val joint: String,
        val severity: Int,
        val notes: String? = null
    ) : ActionBlock()

    /**
     * Switches the active gym profile.
     *
     * Example user command: "Switch to Home gym" or "I'm working out at work today"
     *
     * @param gymProfileName The name of the gym profile to activate ("Home" or "Work")
     */
    @Serializable
    @SerialName("switch_gym")
    data class SwitchGym(
        val gymProfileName: String
    ) : ActionBlock()

    /**
     * Updates the equipment list for a gym profile.
     *
     * Used primarily by Gym Discovery (Vision API) feature.
     *
     * @param gymProfileName The gym profile to update
     * @param equipment List of available equipment types
     */
    @Serializable
    @SerialName("update_equipment")
    data class UpdateEquipment(
        val gymProfileName: String,
        val equipment: List<String>
    ) : ActionBlock()

    /**
     * Saves onboarding data from the Discovery Interview.
     * Emitted by Gemini when it has collected enough information to build
     * GoalDocument + MedicalRestrictionsDoc.
     */
    @Serializable
    @SerialName("save_user_onboarding")
    data class SaveUserOnboarding(
        val data: OnboardingData
    ) : ActionBlock()

    /**
     * Creates a workout routine from the committee's plan.
     * Emitted when Brad and Boris finish designing a session.
     */
    @Serializable
    @SerialName("create_workout_routine")
    data class CreateWorkoutRoutine(
        @SerialName("routine_name") val routineName: String,
        @SerialName("target_date") val targetDate: String,
        val exercises: List<PlannedExercise>
    ) : ActionBlock()
}

@Serializable
data class OnboardingData(
    @SerialName("goal_document") val goalDocument: GoalDocumentRaw,
    @SerialName("medical_restrictions") val medicalRestrictions: MedicalRestrictionsRaw
)

@Serializable
data class GoalDocumentRaw(
    @SerialName("current_phase") val currentPhase: String,
    @SerialName("target_deadline") val targetDeadline: String,
    @SerialName("priority_muscle_groups") val priorityMuscleGroups: List<String>,
    @SerialName("lifestyle_constraints") val lifestyleConstraints: String,
    @SerialName("weekly_session_count") val weeklySessionCount: Int
)

@Serializable
data class MedicalRestrictionsRaw(
    @SerialName("red_list") val redList: List<String>,
    @SerialName("yellow_list") val yellowList: List<YellowEntryRaw>,
    @SerialName("injury_history_summary") val injuryHistorySummary: String
)

@Serializable
data class YellowEntryRaw(
    val exercise: String,
    @SerialName("modification_cue") val modificationCue: String
)

@Serializable
data class PlannedExercise(
    @SerialName("exercise_name") val exerciseName: String,
    @SerialName("target_sets") val targetSets: Int,
    @SerialName("target_reps_min") val targetRepsMin: Int,
    @SerialName("target_reps_max") val targetRepsMax: Int,
    @SerialName("target_rpe") val targetRpe: Int,
    @SerialName("rest_seconds") val restSeconds: Int,
    @SerialName("noa_cue") val noaCue: String? = null
)
