package com.powerme.app.util

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.matchesSearchTokens
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.database.toSearchTokens
import com.powerme.app.data.repository.UserSynonymRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Minimum Jaro-Winkler score accepted as a fuzzy match. */
private const val FUZZY_THRESHOLD = 0.85

enum class MatchType { EXACT_USER_SYNONYM, EXACT, SYNONYM, FUZZY, UNMATCHED, MANUAL }

data class MatchResult(
    val exercise: Exercise?,
    val confidence: Double,
    val matchType: MatchType
)

/**
 * Maps a raw exercise name (from AI output) to an exercise in the app's library.
 *
 * Matching cascade (in priority order):
 *  0. User synonym — exact match in [UserSynonymRepository] (user-saved alias)  (confidence 1.0)
 *  1. Exact        — searchName equality after normalisation                      (confidence 1.0)
 *  2. Synonym      — all query tokens match via ExerciseSynonyms                 (confidence 0.95)
 *  3. Fuzzy        — Jaro-Winkler ≥ 0.85 on searchName pairs                    (confidence = score)
 *  4. Unmatched    — best score < threshold                                       (confidence = best score)
 */
@Singleton
class ExerciseMatcher @Inject constructor(
    private val userSynonymRepository: UserSynonymRepository
) {

    suspend fun matchExercise(rawName: String, library: List<Exercise>): MatchResult {
        if (library.isEmpty()) return MatchResult(null, 0.0, MatchType.UNMATCHED)

        userSynonymRepository.findExercise(rawName)?.let {
            return MatchResult(it, 1.0, MatchType.EXACT_USER_SYNONYM)
        }

        val normalised = rawName.toSearchName()
        val tokens = rawName.toSearchTokens()

        // 1. Exact match
        library.firstOrNull { it.searchName == normalised }?.let {
            return MatchResult(it, 1.0, MatchType.EXACT)
        }

        // 2. Synonym / token match (reuses existing matchesSearchTokens with synonym expansion)
        if (tokens.isNotEmpty()) {
            library.firstOrNull { it.matchesSearchTokens(tokens) }?.let {
                return MatchResult(it, 0.95, MatchType.SYNONYM)
            }
        }

        // 3. Fuzzy match via Jaro-Winkler
        var bestScore = 0.0
        var bestExercise: Exercise? = null
        for (exercise in library) {
            val score = JaroWinkler.similarity(normalised, exercise.searchName)
            if (score > bestScore) {
                bestScore = score
                bestExercise = exercise
            }
        }

        return if (bestScore >= FUZZY_THRESHOLD) {
            MatchResult(bestExercise, bestScore, MatchType.FUZZY)
        } else {
            MatchResult(null, bestScore, MatchType.UNMATCHED)
        }
    }
}
