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

        // Romanian Deadlift
        "rdl"        to listOf("romanian"),
        "romanian"   to listOf("rdl"),
        "stiffleg"   to listOf("romanian deadlift"),

        // Equipment shorthand
        "db"         to listOf("dumbbell"),
        "bb"         to listOf("barbell"),
        "kb"         to listOf("kettlebell"),

        // Rows
        "bentover"   to listOf("barbell row"),

        // Chest / Fly
        "pecdeck"    to listOf("chest fly"),
        "crossover"  to listOf("cable fly"),

        // Glutes / Hips
        "hipthrust"  to listOf("hip thrust"),
        "glute"      to listOf("hip thrust", "glute bridge"),

        // Hamstrings
        "nordic"     to listOf("hamstring curl"),
        "ghd"        to listOf("hamstring"),

        // Triceps
        "skull"      to listOf("tricep extension"),
        "jm"         to listOf("tricep press"),

        // Rear delts
        "facepull"   to listOf("rear delt"),

        // Calves
        "calf"       to listOf("calf raise"),

        // Misc
        "shrug"      to listOf("trap"),
        "chinup"     to listOf("chin-up"),
        "pullup"     to listOf("pull-up"),
        "paused"     to listOf("pause"),
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
