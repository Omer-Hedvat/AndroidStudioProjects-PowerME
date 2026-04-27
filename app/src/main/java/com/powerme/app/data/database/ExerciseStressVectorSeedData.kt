package com.powerme.app.data.database

/**
 * Manual seed data for [ExerciseStressVector] covering the top 30 exercises.
 *
 * Coefficients (0.0–1.0) are sourced from exercise science literature
 * (Schoenfeld, NSCA Essentials of Strength Training) and reflect the relative
 * mechanical load each movement places on each body region's connective tissue.
 *
 * Keys are exact canonical exercise names from master_exercises.json.
 */
object ExerciseStressVectorSeedData {

    const val SEED_VERSION = "2.0"

    /**
     * Map of canonical exercise name -> list of (BodyRegion, coefficient) pairs.
     * Only regions with a non-trivial load (>= 0.3) are listed; unlisted regions
     * are implicitly 0.0 and contribute nothing to the stress accumulation.
     */
    val vectors: Map<String, List<Pair<BodyRegion, Double>>> = mapOf(

        "Barbell Back Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.CORE to 0.5
        ),

        "Conventional Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.9,
            BodyRegion.HIP_JOINT to 0.9,
            BodyRegion.KNEE_JOINT to 0.6,
            BodyRegion.HAMSTRINGS to 0.8,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.UPPER_BACK to 0.6,
            BodyRegion.WRIST_JOINT to 0.4
        ),

        "Barbell Flat Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.PECS to 0.9
        ),

        "Standing Barbell Overhead Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.9,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.CORE to 0.4
        ),

        "Barbell Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8,
            BodyRegion.LOWER_BACK to 0.7
        ),

        "Pull-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.9
        ),

        "Chin-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.LATS to 0.8
        ),

        "Romanian Deadlift (RDL) - BB" to listOf(
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.WRIST_JOINT to 0.3
        ),

        "Leg Press" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.5
        ),

        "Walking Lunge" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.CORE to 0.3
        ),

        "Incline Barbell Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.PECS to 0.8
        ),

        "Dips" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.PECS to 0.7
        ),

        "Barbell Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),

        "Tricep Pushdown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.5
        ),

        "Lateral Raise" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.POSTERIOR_DELTOID to 0.4
        ),

        "Face Pull" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.8,
            BodyRegion.UPPER_BACK to 0.5
        ),

        "Hip Thrust" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.GLUTES to 0.9,
            BodyRegion.HAMSTRINGS to 0.5
        ),

        "Cable Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.7,
            BodyRegion.LOWER_BACK to 0.4
        ),

        "Lying Leg Curl" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HAMSTRINGS to 0.9
        ),

        "Leg Extension" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.QUADS to 0.9
        ),

        "Plank" to listOf(
            BodyRegion.LOWER_BACK to 0.4,
            BodyRegion.CORE to 0.8
        ),

        "Incline Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.PECS to 0.6
        ),

        "Arnold Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.9,
            BodyRegion.POSTERIOR_DELTOID to 0.4
        ),

        "Front Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.QUADS to 0.9,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.CORE to 0.5
        ),

        "Sumo Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.9,
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.7,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),

        "Good Morning" to listOf(
            BodyRegion.LOWER_BACK to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.8
        ),

        "Bulgarian Split Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.7
        ),

        "Nordic Curl" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.LOWER_BACK to 0.3
        ),

        "Barbell Shrug" to listOf(
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.NECK_CERVICAL to 0.7,
            BodyRegion.UPPER_BACK to 0.5
        ),

        "Farmer's Walk" to listOf(
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.LOWER_BACK to 0.6,
            BodyRegion.CORE to 0.5,
            BodyRegion.HIP_JOINT to 0.4
        ),

        // --- Gemini-generated vectors (v2.0) ---

        // Squat variants
        "Goblet Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.LOWER_BACK to 0.4,
            BodyRegion.CORE to 0.5,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Hack Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.QUADS to 0.9,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Safety Bar Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.6,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.UPPER_BACK to 0.4,
            BodyRegion.CORE to 0.4
        ),
        "Box Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.CORE to 0.4
        ),
        "Pause Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.CORE to 0.5
        ),
        "Sumo Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.HAMSTRINGS to 0.4,
            BodyRegion.LOWER_BACK to 0.4
        ),

        // Deadlift variants
        "Trap Bar Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.7,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.QUADS to 0.5,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Single-Leg RDL" to listOf(
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.CORE to 0.4
        ),
        "Deficit Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.9,
            BodyRegion.HIP_JOINT to 0.9,
            BodyRegion.KNEE_JOINT to 0.6,
            BodyRegion.HAMSTRINGS to 0.8,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.UPPER_BACK to 0.5,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Rack Pull" to listOf(
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.HAMSTRINGS to 0.5,
            BodyRegion.GLUTES to 0.5
        ),
        "Stiff-Leg Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.9,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.WRIST_JOINT to 0.3
        ),

        // Bench press variants
        "Decline Barbell Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.PECS to 0.9
        ),
        "Dumbbell Flat Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.PECS to 0.9
        ),
        "Incline Dumbbell Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.PECS to 0.7
        ),
        "Cable Chest Fly" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.PECS to 0.7
        ),
        "Machine Chest Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.6
        ),
        "Close-Grip Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Push-Ups" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.PECS to 0.7,
            BodyRegion.CORE to 0.4
        ),

        // Rows
        "Dumbbell Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Chest-Supported Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.8
        ),
        "T-Bar Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.8,
            BodyRegion.LOWER_BACK to 0.5
        ),
        "Seal Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.8
        ),
        "Pendlay Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.8,
            BodyRegion.LOWER_BACK to 0.5
        ),
        "Inverted Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.7,
            BodyRegion.CORE to 0.4
        ),

        // Overhead press variants
        "Seated Dumbbell Overhead Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.9
        ),
        "Machine Shoulder Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Landmine Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.PECS to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Pike Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.PECS to 0.4
        ),

        // Pull-up variants
        "Neutral Grip Pull-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.9
        ),
        "Lat Pulldown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.LATS to 0.9,
            BodyRegion.UPPER_BACK to 0.7
        ),
        "Close-Grip Lat Pulldown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.LATS to 0.9,
            BodyRegion.UPPER_BACK to 0.6
        ),
        "Straight-Arm Pulldown" to listOf(
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.LATS to 0.8,
            BodyRegion.UPPER_BACK to 0.5,
            BodyRegion.ELBOW_JOINT to 0.3
        ),
        "Assisted Pull-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.LATS to 0.8,
            BodyRegion.WRIST_JOINT to 0.3
        ),

        // Leg isolation
        "Seated Leg Curl" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HAMSTRINGS to 0.9
        ),
        "Standing Calf Raise" to listOf(
            BodyRegion.CALVES to 0.9,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Seated Calf Raise" to listOf(
            BodyRegion.CALVES to 0.9,
            BodyRegion.KNEE_JOINT to 0.4
        ),

        // Curl / tricep isolation
        "Dumbbell Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Hammer Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Cable Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Overhead Tricep Extension" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Skull Crusher" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5
        ),

        // Shoulder isolation
        "Rear Delt Fly" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.8,
            BodyRegion.UPPER_BACK to 0.4
        ),
        "Shrugs" to listOf(
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.NECK_CERVICAL to 0.7,
            BodyRegion.UPPER_BACK to 0.5
        ),

        // Core
        "Ab Wheel Rollout" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.CORE to 0.9,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.4
        ),
        "Cable Crunch" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Russian Twist" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.4,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Hanging Leg Raise" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.4
        ),

        // Functional / conditioning
        "Sled Push" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Sled Pull" to listOf(
            BodyRegion.KNEE_JOINT to 0.6,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.HAMSTRINGS to 0.6,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Battle Ropes" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.5,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Box Jump" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.5,
            BodyRegion.CALVES to 0.5
        ),
        "Medicine Ball Slam" to listOf(
            BodyRegion.CORE to 0.7,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.4,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Broad Jump" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.CALVES to 0.5
        ),
        "Russian Kettlebell Swing" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HAMSTRINGS to 0.7,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.CORE to 0.5,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Turkish Get-Up" to listOf(
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.CORE to 0.7,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.KNEE_JOINT to 0.4
        ),
        "Glute Bridge" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.HAMSTRINGS to 0.5,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Wall Sit" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.QUADS to 0.7,
            BodyRegion.HIP_JOINT to 0.5
        ),
        "Step-Up" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.6
        ),
        "Reverse Lunge" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.6
        ),
        "Sissy Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.QUADS to 0.9
        ),

        // Curl / isolation continued
        "Preacher Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Concentration Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Incline Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.3
        ),
        "Tricep Kickback" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Diamond Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.4
        ),
        "Bench Dips" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Upright Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.POSTERIOR_DELTOID to 0.4,
            BodyRegion.NECK_CERVICAL to 0.3,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "Front Raise" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.8
        ),
        "Cable Lateral Raise" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.4
        ),
        "Reverse Pec Deck" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.8,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "Adductor Machine" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.KNEE_JOINT to 0.4
        ),
        "Abductor Machine" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.KNEE_JOINT to 0.4
        ),
        "Donkey Calf Raise" to listOf(
            BodyRegion.CALVES to 0.9,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Wrist Curl" to listOf(
            BodyRegion.WRIST_JOINT to 0.9,
            BodyRegion.ELBOW_JOINT to 0.3
        ),
        "Dead Hang" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.UPPER_BACK to 0.5,
            BodyRegion.LATS to 0.5
        ),
        "Decline Dumbbell Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.9,
            BodyRegion.ANTERIOR_DELTOID to 0.4
        ),
        "Pec Deck Fly" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.4
        ),
        "Cable Crossover" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Ring Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Weighted Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Smith Machine Bench Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.6
        ),
        "Dumbbell Pullover" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.LATS to 0.7,
            BodyRegion.PECS to 0.6,
            BodyRegion.UPPER_BACK to 0.4
        ),
        "Decline Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.CORE to 0.4
        ),
        "Dumbbell Chest Fly" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Dead Bug" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Pallof Press" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.3,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Dragon Flag" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.ELBOW_JOINT to 0.4
        ),
        "Landmine Rotation" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.HIP_JOINT to 0.4
        ),
        "Hollow Body Hold" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.HIP_JOINT to 0.4
        ),
        "Side Plank" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.4,
            BodyRegion.HIP_JOINT to 0.3
        ),
        "Bicycle Crunch" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.HIP_JOINT to 0.4,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "L-Sit" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Landmine Anti-Rotation Press" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ELBOW_JOINT to 0.4
        ),
        "Suitcase Carry" to listOf(
            BodyRegion.CORE to 0.7,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.HIP_JOINT to 0.4
        ),

        // EZ bar / cable tricep
        "EZ Bar Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "EZ Bar Skullcrusher" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Cable Overhead Tricep Extension" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Reverse Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.7
        ),
        "Rope Tricep Pushdown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Zottman Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.6
        ),
        "Tate Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Machine Preacher Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Cable Hammer Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Spider Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "JM Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.4
        ),

        // DB RDL and weighted rows
        "Romanian Deadlift (RDL) - DB" to listOf(
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Weighted Pull-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.9
        ),
        "Incline Dumbbell Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.7
        ),
        "Single-Arm Cable Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.7,
            BodyRegion.CORE to 0.3
        ),
        "Meadows Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Wide-Grip Lat Pulldown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.LATS to 0.9,
            BodyRegion.UPPER_BACK to 0.6
        ),
        "Cable Pullover" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.LATS to 0.8,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "Kneeling Single-Arm Lat Pulldown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.LATS to 0.8,
            BodyRegion.UPPER_BACK to 0.6,
            BodyRegion.CORE to 0.3
        ),
        "Seated Barbell Overhead Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.9,
            BodyRegion.UPPER_BACK to 0.3
        ),
        "Z-Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.9,
            BodyRegion.CORE to 0.5,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Cable Front Raise" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.8
        ),
        "Machine Lateral Raise" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.ANTERIOR_DELTOID to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.4
        ),
        "Cable Upright Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.NECK_CERVICAL to 0.3,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "Band Pull-Apart" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.POSTERIOR_DELTOID to 0.7,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "Pistol Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.5,
            BodyRegion.CORE to 0.4
        ),

        // Machine / cable lower body
        "Single-Leg Press" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.5
        ),
        "Belt Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.QUADS to 0.9,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Leg Press Calf Raise" to listOf(
            BodyRegion.CALVES to 0.9,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Cable Pull-Through" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.HAMSTRINGS to 0.7,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Landmine Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.LOWER_BACK to 0.3
        ),

        // Kettlebell
        "Kettlebell Clean" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.LOWER_BACK to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.GLUTES to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Kettlebell Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.8
        ),
        "Kettlebell Snatch" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.GLUTES to 0.5,
            BodyRegion.CORE to 0.5
        ),
        "Kettlebell Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.LATS to 0.7,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Kettlebell Romanian Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.WRIST_JOINT to 0.4
        ),

        // Band exercises
        "Band Bicep Curl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Band Tricep Pushdown" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.3
        ),
        "Band Hip Thrust" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.HAMSTRINGS to 0.4,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Band Lateral Walk" to listOf(
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.KNEE_JOINT to 0.4
        ),
        "Band Pull-Apart (Resistance Band)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.POSTERIOR_DELTOID to 0.7,
            BodyRegion.UPPER_BACK to 0.5
        ),

        // Smith machine
        "Smith Machine Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.9,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.CORE to 0.3
        ),
        "Smith Machine Overhead Press" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.8
        ),
        "Smith Machine Romanian Deadlift" to listOf(
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.GLUTES to 0.6
        ),
        "Smith Machine Bulgarian Split Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.7
        ),

        // Olympic lifts
        "Power Clean" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.KNEE_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.HAMSTRINGS to 0.6,
            BodyRegion.CORE to 0.4
        ),
        "Barbell Snatch" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.KNEE_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.CORE to 0.5
        ),
        "Clean and Jerk" to listOf(
            BodyRegion.HIP_JOINT to 0.9,
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.GLUTES to 0.7,
            BodyRegion.CORE to 0.5
        ),

        // Advanced calisthenics
        "Muscle-Up (Rings)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8,
            BodyRegion.PECS to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.CORE to 0.4
        ),
        "Handstand Push-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.9,
            BodyRegion.NECK_CERVICAL to 0.4
        ),
        "Burpee (Standard)" to listOf(
            BodyRegion.KNEE_JOINT to 0.5,
            BodyRegion.HIP_JOINT to 0.5,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.CORE to 0.5
        ),
        "Toes-to-Bar" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.LATS to 0.4
        ),
        "Wall Ball Shot" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Kettlebell Thruster" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Overhead Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.CORE to 0.6,
            BodyRegion.LOWER_BACK to 0.5
        ),
        "Arch-Body Hold (Superman)" to listOf(
            BodyRegion.LOWER_BACK to 0.8,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.UPPER_BACK to 0.5,
            BodyRegion.POSTERIOR_DELTOID to 0.4
        ),
        "Chest-to-Bar Pull-Up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.9,
            BodyRegion.PECS to 0.4
        ),
        "Ring Dip" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.PECS to 0.7
        ),

        // Complex / functional movements
        "Front-Rack Reverse Lunge" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.CORE to 0.4
        ),
        "Box Step-Over" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.6,
            BodyRegion.GLUTES to 0.5
        ),
        "Kettlebell Swing" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HAMSTRINGS to 0.6,
            BodyRegion.GLUTES to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.CORE to 0.4
        ),
        "Bear Crawl" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.CORE to 0.7,
            BodyRegion.HIP_JOINT to 0.4,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Dumbbell Devil Press" to listOf(
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.5
        ),
        "Tuck Sit" to listOf(
            BodyRegion.CORE to 0.7,
            BodyRegion.HIP_JOINT to 0.5,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Handstand Hold" to listOf(
            BodyRegion.ELBOW_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.CORE to 0.5
        ),
        "Pseudo-Planche Hold" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.8,
            BodyRegion.PECS to 0.5,
            BodyRegion.CORE to 0.5
        ),
        "Horse Stance" to listOf(
            BodyRegion.KNEE_JOINT to 0.5,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.5
        ),
        "Duck Walk" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.6,
            BodyRegion.GLUTES to 0.4
        ),
        "Bird-Dog" to listOf(
            BodyRegion.CORE to 0.7,
            BodyRegion.LOWER_BACK to 0.4,
            BodyRegion.HIP_JOINT to 0.4
        ),
        "Pallof Press (Iso-Band)" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.LOWER_BACK to 0.3,
            BodyRegion.WRIST_JOINT to 0.3
        ),

        // Push-up progressions
        "Archer Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Typewriter Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Pseudo-Planche Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.8,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.PECS to 0.6
        ),
        "Clap Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Single-Arm Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.CORE to 0.5
        ),
        "Staggered-Hand Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.PECS to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.5
        ),
        "Tiger Bend Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.7,
            BodyRegion.ANTERIOR_DELTOID to 0.7,
            BodyRegion.PECS to 0.5
        ),
        "Bench Dip (Feet Elevated)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.9,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.PECS to 0.5
        ),
        "Hindu Push-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.PECS to 0.6,
            BodyRegion.ANTERIOR_DELTOID to 0.6,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "DB Around the World" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.5,
            BodyRegion.PECS to 0.5
        ),
        "Around the World (Pull-up Bar)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.6,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.LATS to 0.7
        ),
        "Negative Pull-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8
        ),
        "Commando Pull-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8
        ),
        "Archer Pull-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.9
        ),
        "Australian Pull-up (Inverted Row)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.7,
            BodyRegion.CORE to 0.4
        ),
        "Face Pull (Resistance Band)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.POSTERIOR_DELTOID to 0.7,
            BodyRegion.UPPER_BACK to 0.5
        ),
        "L-Sit Pull-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.8,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.9,
            BodyRegion.CORE to 0.6,
            BodyRegion.HIP_JOINT to 0.5
        ),
        "Explosive Pull-up (Chest-to-Bar)" to listOf(
            BodyRegion.ELBOW_JOINT to 0.7,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.9,
            BodyRegion.LATS to 0.9
        ),
        "Single-Arm Australian Row" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.UPPER_BACK to 0.8,
            BodyRegion.LATS to 0.8,
            BodyRegion.CORE to 0.4
        ),
        "Scapular Pull-up" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.3,
            BodyRegion.UPPER_BACK to 0.7,
            BodyRegion.LATS to 0.5
        ),

        // Lower body progressions
        "Shrimp Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.8,
            BodyRegion.GLUTES to 0.5
        ),
        "Skater Lunge" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.6,
            BodyRegion.CORE to 0.3
        ),
        "Curtsy Lunge" to listOf(
            BodyRegion.KNEE_JOINT to 0.7,
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.7
        ),
        "Sissy Squat (Bodyweight)" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.QUADS to 0.9
        ),
        "Reverse Nordic" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.QUADS to 0.9,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Nordic Hamstring Curl" to listOf(
            BodyRegion.KNEE_JOINT to 0.9,
            BodyRegion.HAMSTRINGS to 0.9,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Cossack Squat" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.5
        ),
        "Single-Leg Glute Bridge" to listOf(
            BodyRegion.HIP_JOINT to 0.7,
            BodyRegion.GLUTES to 0.9,
            BodyRegion.HAMSTRINGS to 0.5,
            BodyRegion.LOWER_BACK to 0.3
        ),
        "Hip Thrust (Couch/Bench)" to listOf(
            BodyRegion.HIP_JOINT to 0.8,
            BodyRegion.GLUTES to 0.9,
            BodyRegion.HAMSTRINGS to 0.5,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Calf Raise (Single-Leg)" to listOf(
            BodyRegion.CALVES to 0.9,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Tibialis Raise (Wall)" to listOf(
            BodyRegion.CALVES to 0.4,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Tuck Jump" to listOf(
            BodyRegion.KNEE_JOINT to 0.8,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.QUADS to 0.7,
            BodyRegion.GLUTES to 0.5,
            BodyRegion.CALVES to 0.4
        ),

        // Advanced core
        "Hollow Rock" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.LOWER_BACK to 0.5,
            BodyRegion.HIP_JOINT to 0.4
        ),
        "V-Up" to listOf(
            BodyRegion.CORE to 0.9,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Windshield Wiper (Floor)" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.HIP_JOINT to 0.5,
            BodyRegion.LOWER_BACK to 0.4
        ),
        "Windshield Wiper (Bar)" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.HIP_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ELBOW_JOINT to 0.3
        ),
        "Knee-to-Elbow (Bar)" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.HIP_JOINT to 0.6,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ELBOW_JOINT to 0.3
        ),
        "Mountain Climber" to listOf(
            BodyRegion.CORE to 0.7,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.HIP_JOINT to 0.4,
            BodyRegion.KNEE_JOINT to 0.3
        ),
        "Spiderman Plank" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.HIP_JOINT to 0.3
        ),
        "Plank Shoulder Tap" to listOf(
            BodyRegion.CORE to 0.8,
            BodyRegion.ELBOW_JOINT to 0.3,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.ANTERIOR_DELTOID to 0.3
        ),

        // Cardio / conditioning
        "Double Under (Jump Rope)" to listOf(
            BodyRegion.CALVES to 0.7,
            BodyRegion.KNEE_JOINT to 0.5,
            BodyRegion.WRIST_JOINT to 0.5
        ),
        "Single Under" to listOf(
            BodyRegion.CALVES to 0.6,
            BodyRegion.KNEE_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4
        ),
        "Burpee" to listOf(
            BodyRegion.KNEE_JOINT to 0.5,
            BodyRegion.HIP_JOINT to 0.5,
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.4,
            BodyRegion.CORE to 0.5
        ),
        "Shadow Boxing" to listOf(
            BodyRegion.ELBOW_JOINT to 0.4,
            BodyRegion.WRIST_JOINT to 0.5,
            BodyRegion.ANTERIOR_DELTOID to 0.4,
            BodyRegion.CORE to 0.4
        ),
        "Farmer's Walk (Heavy Bags)" to listOf(
            BodyRegion.WRIST_JOINT to 0.8,
            BodyRegion.LOWER_BACK to 0.7,
            BodyRegion.CORE to 0.5,
            BodyRegion.HIP_JOINT to 0.4
        ),
        "Jumping Jacks" to listOf(
            BodyRegion.KNEE_JOINT to 0.4,
            BodyRegion.HIP_JOINT to 0.3,
            BodyRegion.CALVES to 0.4
        )
    )
}
