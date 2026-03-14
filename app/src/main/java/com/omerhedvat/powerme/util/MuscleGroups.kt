package com.omerhedvat.powerme.util

/**
 * Canonical major muscle group constants.
 * Valid values for exercises.muscleGroup and exercise_muscle_groups.majorGroup.
 * Glutes is a subGroup under Legs (e.g. Hip Thrust: majorGroup=Legs, subGroup=Glutes).
 */
object MuscleGroups {
    const val LEGS = "Legs"
    const val BACK = "Back"
    const val CORE = "Core"
    const val CHEST = "Chest"
    const val SHOULDERS = "Shoulders"
    const val FULL_BODY = "Full Body"
    const val ARMS = "Arms"
    const val CARDIO = "Cardio"

    val ALL = listOf(LEGS, BACK, CORE, CHEST, SHOULDERS, FULL_BODY, ARMS, CARDIO)
}
