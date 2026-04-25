package com.powerme.app.util

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.UserSynonymRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExerciseMatcherTest {

    private lateinit var matcher: ExerciseMatcher
    private lateinit var library: List<Exercise>
    private lateinit var userSynonymRepository: UserSynonymRepository

    @Before
    fun setup() {
        userSynonymRepository = mock()
        matcher = ExerciseMatcher(userSynonymRepository)
        library = listOf(
            makeExercise(1, "Barbell Bench Press", "Chest", "Barbell"),
            makeExercise(2, "Dumbbell Row", "Back", "Dumbbell"),
            makeExercise(3, "Barbell Back Squat", "Legs", "Barbell"),
            makeExercise(4, "Pull-Up", "Back", "Bodyweight"),
            makeExercise(5, "Romanian Deadlift", "Legs", "Barbell"),
            makeExercise(6, "Overhead Press", "Shoulders", "Barbell"),
            makeExercise(7, "Lat Pulldown", "Back", "Cable"),
            makeExercise(8, "Cable Fly", "Chest", "Cable"),
            makeExercise(9, "Leg Press", "Legs", "Machine"),
            makeExercise(10, "Incline Dumbbell Bench Press", "Chest", "Dumbbell"),
        )
    }

    // ── Exact match ──────────────────────────────────────────────────────────

    @Test
    fun `exact match — Barbell Bench Press`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Barbell Bench Press", library)
        assertEquals(MatchType.EXACT, result.matchType)
        assertEquals(1L, result.exercise?.id)
        assertEquals(1.0, result.confidence, 0.0001)
    }

    @Test
    fun `exact match — case insensitive`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("barbell bench press", library)
        assertEquals(MatchType.EXACT, result.matchType)
        assertEquals(1L, result.exercise?.id)
    }

    @Test
    fun `exact match — hyphen normalised`() = runTest {
        // "Pull-Up" normalises to "pullup" which equals the searchName
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Pull-Up", library)
        assertEquals(MatchType.EXACT, result.matchType)
        assertEquals(4L, result.exercise?.id)
    }

    // ── Synonym match ─────────────────────────────────────────────────────────

    @Test
    fun `synonym match — bb bench press`() = runTest {
        // "bb" expands to "barbell" via ExerciseSynonyms
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("bb bench press", library)
        assertEquals(MatchType.SYNONYM, result.matchType)
        assertEquals(1L, result.exercise?.id)
        assertEquals(0.95, result.confidence, 0.0001)
    }

    @Test
    fun `synonym match — rdl maps to Romanian Deadlift`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("rdl", library)
        assertEquals(MatchType.SYNONYM, result.matchType)
        assertEquals(5L, result.exercise?.id)
    }

    @Test
    fun `synonym match — ohp maps to Overhead Press`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("ohp", library)
        assertEquals(MatchType.SYNONYM, result.matchType)
        assertEquals(6L, result.exercise?.id)
    }

    @Test
    fun `synonym match — pulldown maps to Lat Pulldown`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("pulldown", library)
        assertEquals(MatchType.SYNONYM, result.matchType)
        assertEquals(7L, result.exercise?.id)
    }

    // ── Fuzzy match ───────────────────────────────────────────────────────────

    @Test
    fun `fuzzy match — typo in barbell bench press`() = runTest {
        // "Barbel Bench Pres" may match via SYNONYM (each token is still a substring)
        // or via FUZZY — either way the correct exercise must be returned with high confidence
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Barbel Bench Pres", library)
        assertTrue(
            "Expected EXACT, SYNONYM, or FUZZY, got ${result.matchType}",
            result.matchType != MatchType.UNMATCHED
        )
        assertEquals(1L, result.exercise?.id)
        assert(result.confidence >= 0.85) { "Expected confidence ≥ 0.85, got ${result.confidence}" }
    }

    @Test
    fun `fuzzy match — overhead pres (missing s)`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Overhead Pres", library)
        assert(result.matchType == MatchType.FUZZY || result.matchType == MatchType.SYNONYM) {
            "Expected FUZZY or SYNONYM, got ${result.matchType}"
        }
        assertEquals(6L, result.exercise?.id)
    }

    // ── Unmatched ─────────────────────────────────────────────────────────────

    @Test
    fun `unmatched — completely unknown exercise`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Underwater Basket Weaving", library)
        assertEquals(MatchType.UNMATCHED, result.matchType)
        assertNull(result.exercise)
        assert(result.confidence < 0.85) { "Confidence should be < 0.85, got ${result.confidence}" }
    }

    @Test
    fun `unmatched — empty library returns UNMATCHED`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("Bench Press", emptyList())
        assertEquals(MatchType.UNMATCHED, result.matchType)
        assertNull(result.exercise)
        assertEquals(0.0, result.confidence, 0.0001)
    }

    @Test
    fun `unmatched — gibberish input`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("xkzqvwm", library)
        assertEquals(MatchType.UNMATCHED, result.matchType)
        assertNull(result.exercise)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty raw name does not crash`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)
        val result = matcher.matchExercise("", library)
        assertNotNull(result)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun makeExercise(
        id: Long, name: String, muscleGroup: String, equipmentType: String
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = muscleGroup,
        equipmentType = equipmentType,
        searchName = name.toSearchName()
    )
}
