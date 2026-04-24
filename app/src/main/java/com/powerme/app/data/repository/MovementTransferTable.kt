package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import java.util.Locale

internal object MovementTransferTable {

    fun ratio(source: Exercise, target: Exercise): Double {
        if (source.id == target.id) return 1.0
        val fam = source.familyId ?: return 1.0
        if (target.familyId != fam) return 1.0
        val s = factorFor(fam, source.name)
        val t = factorFor(fam, target.name)
        return if (s > 0.0) t / s else 1.0
    }

    fun factorFor(familyId: String?, exerciseName: String): Double {
        if (familyId == null) return 1.0
        val byName = factors[familyId] ?: return 1.0
        return byName[exerciseName.lowercase(Locale.ROOT)] ?: 1.0
    }

    private val factors: Map<String, Map<String, Double>> = mapOf(
        "squat_family" to mapOf(
            "barbell back squat" to 1.00,
            "front squat" to 0.80,
            "overhead squat" to 0.50,
            "dumbbell overhead squat" to 0.45,
            "dumbbell front squat" to 0.55,
            "goblet squat" to 0.50,
            "bulgarian split squat" to 0.40,
            "smith machine bulgarian split squat" to 0.42,
            "pistol squat" to 0.35,
            "hack squat" to 1.10,
            "leg press" to 1.80,
            "single-leg press" to 0.90,
            "belt squat" to 0.90,
            "box squat" to 0.95,
            "pause squat" to 0.90,
            "safety bar squat" to 0.92,
            "zercher squat" to 0.70,
            "smith machine squat" to 1.00,
            "landmine squat" to 0.75,
            "sumo squat" to 0.95,
            "cossack squat" to 0.40,
            "reverse lunge" to 0.45,
            "walking lunge" to 0.45,
            "front-rack reverse lunge" to 0.50,
            "step-up" to 0.50,
            "sissy squat" to 0.35,
        ),
        "bench_family" to mapOf(
            "barbell flat bench press" to 1.00,
            "incline barbell bench press" to 0.85,
            "decline barbell bench press" to 1.05,
            "close-grip bench press" to 0.90,
            "smith machine bench press" to 0.95,
            "dumbbell flat bench press" to 0.80,
            "incline dumbbell bench press" to 0.70,
            "decline dumbbell press" to 0.82,
            "machine chest press" to 0.95,
        ),
        "deadlift_family" to mapOf(
            "conventional deadlift" to 1.00,
            "sumo deadlift" to 0.98,
            "trap bar deadlift" to 1.05,
            "rack pull" to 1.20,
            "deficit deadlift" to 0.90,
            "romanian deadlift (rdl) - bb" to 0.85,
            "romanian deadlift (rdl) - db" to 0.70,
            "stiff-leg deadlift" to 0.80,
            "good morning" to 0.45,
            "single-leg rdl" to 0.35,
            "smith machine romanian deadlift" to 0.85,
            "cable pull-through" to 0.55,
        ),
        "overhead_press_family" to mapOf(
            "standing barbell overhead press" to 1.00,
            "seated barbell overhead press" to 0.95,
            "seated dumbbell overhead press" to 0.65,
            "arnold press" to 0.65,
            "machine shoulder press" to 0.90,
            "smith machine overhead press" to 0.95,
            "landmine press" to 0.80,
            "z-press" to 0.80,
            "kettlebell press" to 0.60,
        ),
        "pullup_family" to mapOf(
            "pull-up" to 1.00,
            "chin-up" to 1.05,
            "neutral grip pull-up" to 1.02,
            "weighted pull-up" to 1.00,
            "wide-grip lat pulldown" to 1.15,
            "lat pulldown" to 1.20,
            "close-grip lat pulldown" to 1.18,
            "assisted pull-up" to 0.60,
            "straight-arm pulldown" to 0.55,
            "cable pullover" to 0.60,
        ),
        "row_family" to mapOf(
            "barbell row" to 1.00,
            "pendlay row" to 0.95,
            "t-bar row" to 0.95,
            "dumbbell row" to 0.45,
            "chest-supported row" to 0.85,
            "seal row" to 0.85,
            "cable row" to 0.90,
            "meadows row" to 0.55,
            "inverted row" to 0.55,
        ),
        "pushup_family" to mapOf(
            "archer push-up" to 1.00,
            "typewriter push-up" to 1.05,
            "single-arm push-up" to 1.50,
            "clap push-up" to 1.10,
            "incline push-up" to 0.70,
        ),
        "olympic_family" to mapOf(
            "clean and jerk" to 1.00,
            "power clean" to 0.85,
            "squat clean" to 0.95,
            "hang power clean" to 0.80,
            "barbell snatch" to 0.75,
            "power snatch" to 0.65,
            "hang power snatch" to 0.60,
            "hang snatch" to 0.70,
            "push jerk" to 0.90,
            "split jerk" to 0.95,
            "dumbbell snatch" to 0.40,
            "dumbbell power clean" to 0.45,
        ),
        "leg_curl_family" to mapOf(
            "lying leg curl" to 1.00,
            "seated leg curl" to 1.05,
            "nordic curl" to 0.90,
            "nordic hamstring curl" to 0.90,
            "leg extension" to 1.00,
            "standing calf raise" to 1.00,
            "seated calf raise" to 0.75,
            "donkey calf raise" to 0.95,
        ),
    )
}
