package com.powerme.app.data.database

/**
 * Query-side synonym expansion for exercise search.
 *
 * When a user searches for a token, it is expanded to a list of phrases that are
 * checked as substrings against the exercise name. An exercise matches a token if its
 * name contains the original token OR any of its synonym phrases.
 *
 * Keys   — normalized tokens (lowercase, no spaces/hyphens). Single words only.
 * Values — full phrases to also search for (lowercase, spaces OK).
 *          Use phrases, not individual words, to avoid over-matching.
 *
 * Example: "ohp" → ["overhead press"] matches "Standing Barbell Overhead Press"
 *           but NOT "Overhead Squat" or "Plank Shoulder Tap".
 *
 * To add new synonyms: add entries here and bump nothing — no DB migration needed.
 */
object ExerciseSynonyms {

    private val SYNONYMS: Map<String, List<String>> = mapOf(
        // Overhead / Shoulder Press
        "military"   to listOf("overhead press"),
        "ohp"        to listOf("overhead press"),
        "arnold"     to listOf("arnold press"),

        // Deadlifts
        "rdl"        to listOf("romanian deadlift"),
        "romanian"   to listOf("rdl", "romanian deadlift"),
        "stiffleg"   to listOf("stiff-leg deadlift", "romanian deadlift"),
        "sldl"       to listOf("stiff-leg deadlift"),
        "goodmorning" to listOf("good morning"),

        // Equipment shorthand
        "db"         to listOf("dumbbell"),
        "bb"         to listOf("barbell"),
        "kb"         to listOf("kettlebell"),
        "ez"         to listOf("ez bar"),
        "sm"         to listOf("smith machine"),
        "smith"      to listOf("smith machine"),

        // Rows
        "bentover"   to listOf("barbell row"),
        "meadows"    to listOf("meadows row"),
        "pendlay"    to listOf("pendlay row"),

        // Chest / Fly
        "pecdeck"    to listOf("pec deck fly", "chest fly"),
        "crossover"  to listOf("cable crossover", "cable fly"),
        "cgbp"       to listOf("close-grip bench press"),
        "flyes"      to listOf("chest fly", "rear delt fly"),

        // Glutes / Hips
        "hipthrust"  to listOf("hip thrust"),
        "glute"      to listOf("hip thrust", "glute bridge"),

        // Hamstrings
        "nordic"     to listOf("nordic curl", "hamstring curl"),
        "ghd"        to listOf("hamstring curl", "hamstring"),
        "legcurl"    to listOf("leg curl"),

        // Triceps
        "skull"      to listOf("skull crusher"),
        "skullcrusher" to listOf("skull crusher"),
        "jm"         to listOf("jm press"),
        "pushdown"   to listOf("tricep pushdown"),
        "pressdown"  to listOf("tricep pushdown"),
        "kickback"   to listOf("tricep kickback"),

        // Rear delts / Shoulders
        "facepull"   to listOf("face pull", "rear delt"),
        "laterals"   to listOf("lateral raise"),

        // Calves
        "calf"       to listOf("calf raise"),

        // Misc / Movement Nicknames & Spelling Variants
        "deads"      to listOf("deadlift"),
        "chinup"     to listOf("chin-up"),
        "pullup"     to listOf("pull-up"),
        "pushup"     to listOf("push-up"),
        "stepup"     to listOf("step-up"),
        "muscleup"   to listOf("muscle-up"),
        "pulldown"   to listOf("lat pulldown"),
        "paused"     to listOf("pause squat", "pause"),

        // Apostrophe edge-cases (apostrophe is not stripped by toSearchName)
        "farmers"    to listOf("farmer's walk"),
        "farmerwalk" to listOf("farmer's walk"),

        // CrossFit / shorthand abbreviations
        "hspu"       to listOf("handstand push-up"),
        "ttb"        to listOf("toes-to-bar"),
        "t2b"        to listOf("toes-to-bar"),
        "c2b"        to listOf("chest-to-bar"),
        "ohs"        to listOf("overhead squat"),

        // Other compound-word entries that searchName handles for most but not all users
        "abwheel"    to listOf("ab wheel")
    )

    /**
     * Returns the input token plus all its synonym phrases.
     * The token is normalized before lookup so callers don't need to pre-normalize.
     */
    fun expandToken(token: String): List<String> {
        val normalized = token.toSearchName()
        return listOf(token) + (SYNONYMS[normalized] ?: emptyList())
    }
}
