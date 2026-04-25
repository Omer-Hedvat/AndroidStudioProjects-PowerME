package com.powerme.app.util

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.UserSynonymRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExerciseMatcherSynonymTest {

    private lateinit var matcher: ExerciseMatcher
    private lateinit var userSynonymRepository: UserSynonymRepository
    private lateinit var library: List<Exercise>

    private val benchPress = makeExercise(1, "Barbell Bench Press", "Chest", "Barbell")
    private val squat = makeExercise(2, "Barbell Back Squat", "Legs", "Barbell")

    @Before
    fun setup() {
        userSynonymRepository = mock()
        matcher = ExerciseMatcher(userSynonymRepository)
        library = listOf(benchPress, squat)
    }

    @Test
    fun `synonym hit returns EXACT_USER_SYNONYM with confidence 1_0`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(benchPress)

        val result = matcher.matchExercise("flat bench", library)

        assertEquals(MatchType.EXACT_USER_SYNONYM, result.matchType)
        assertEquals(benchPress, result.exercise)
        assertEquals(1.0, result.confidence, 0.0001)
    }

    @Test
    fun `synonym tier takes priority over EXACT match`() = runTest {
        // Even though "Barbell Bench Press" would match EXACT, a user synonym overrides it
        whenever(userSynonymRepository.findExercise(any())).thenReturn(squat)

        val result = matcher.matchExercise("Barbell Bench Press", library)

        assertEquals(MatchType.EXACT_USER_SYNONYM, result.matchType)
        assertEquals(squat, result.exercise)
    }

    @Test
    fun `synonym miss falls through to normal matching`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)

        val result = matcher.matchExercise("Barbell Bench Press", library)

        // Falls through to EXACT tier
        assertEquals(MatchType.EXACT, result.matchType)
        assertEquals(benchPress, result.exercise)
    }

    @Test
    fun `findExercise is called on every matchExercise invocation`() = runTest {
        whenever(userSynonymRepository.findExercise(any())).thenReturn(null)

        matcher.matchExercise("some exercise", library)

        verify(userSynonymRepository).findExercise("some exercise")
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
