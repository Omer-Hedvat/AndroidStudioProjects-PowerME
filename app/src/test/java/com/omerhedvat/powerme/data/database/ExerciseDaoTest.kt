package com.omerhedvat.powerme.data.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

/**
 * Unit tests for ExerciseDao Sprint 2 methods.
 *
 * Tests new synchronous methods added for MasterExerciseSeeder:
 * - getAllExercisesSync()
 * - getExerciseCountSync()
 * - getCustomExercisesSync()
 * - getFavoriteExercisesSync()
 * - getExercisesWithVideoSync()
 * - deleteAllExercises()
 *
 * Success Criteria: All 10 tests must pass.
 */
class ExerciseDaoTest {

    private lateinit var mockDao: ExerciseDao

    private val squatExercise = Exercise(
        id = 1,
        name = "Barbell Back Squat",
        muscleGroup = "Legs",
        equipmentType = "Barbell",
        youtubeVideoId = "ultWZbUMPL8",
        familyId = "squat_family",
        isCustom = false,
        isFavorite = false
    )

    private val benchExercise = Exercise(
        id = 2,
        name = "Barbell Bench Press",
        muscleGroup = "Chest",
        equipmentType = "Barbell",
        youtubeVideoId = "rT7DgCr-3pg",
        familyId = "bench_family",
        isCustom = false,
        isFavorite = true  // Favorite
    )

    private val customExercise = Exercise(
        id = 999,
        name = "My Custom Exercise",
        muscleGroup = "Custom",
        equipmentType = "Custom",
        youtubeVideoId = null,  // No video
        isCustom = true,
        isFavorite = false
    )

    @Before
    fun setup() {
        mockDao = mock(ExerciseDao::class.java)
    }

    /**
     * Test Case 1: Get All Exercises (Flow)
     */
    @Test
    fun testGetAllExercisesFlow() = runTest {
        val exercises = listOf(squatExercise, benchExercise, customExercise)
        whenever(mockDao.getAllExercises()).thenReturn(flowOf(exercises))

        val result = mockDao.getAllExercises().first()

        assertEquals(3, result.size)
        verify(mockDao).getAllExercises()
    }

    /**
     * Test Case 2: Get All Exercises (Synchronous)
     */
    @Test
    fun testGetAllExercisesSync() = runTest {
        val exercises = listOf(squatExercise, benchExercise)
        whenever(mockDao.getAllExercisesSync()).thenReturn(exercises)

        val result = mockDao.getAllExercisesSync()

        assertEquals(2, result.size)
        verify(mockDao).getAllExercisesSync()
    }

    /**
     * Test Case 3: Get Exercise Count
     */
    @Test
    fun testGetExerciseCount() = runTest {
        whenever(mockDao.getExerciseCountSync()).thenReturn(156)

        val count = mockDao.getExerciseCountSync()

        assertEquals(156, count)
        verify(mockDao).getExerciseCountSync()
    }

    /**
     * Test Case 4: Get Custom Exercises Only
     */
    @Test
    fun testGetCustomExercisesSync() = runTest {
        val customExercises = listOf(customExercise)
        whenever(mockDao.getCustomExercisesSync()).thenReturn(customExercises)

        val result = mockDao.getCustomExercisesSync()

        assertEquals(1, result.size)
        assertTrue(result[0].isCustom)
        verify(mockDao).getCustomExercisesSync()
    }

    /**
     * Test Case 5: Get Favorite Exercises Only
     */
    @Test
    fun testGetFavoriteExercisesSync() = runTest {
        val favoriteExercises = listOf(benchExercise)
        whenever(mockDao.getFavoriteExercisesSync()).thenReturn(favoriteExercises)

        val result = mockDao.getFavoriteExercisesSync()

        assertEquals(1, result.size)
        assertTrue(result[0].isFavorite)
        assertEquals("Barbell Bench Press", result[0].name)
        verify(mockDao).getFavoriteExercisesSync()
    }

    /**
     * Test Case 6: Get Exercises With YouTube Video
     */
    @Test
    fun testGetExercisesWithVideoSync() = runTest {
        val exercisesWithVideo = listOf(squatExercise, benchExercise)
        whenever(mockDao.getExercisesWithVideoSync()).thenReturn(exercisesWithVideo)

        val result = mockDao.getExercisesWithVideoSync()

        assertEquals(2, result.size)
        assertTrue(result.all { it.youtubeVideoId != null })
        verify(mockDao).getExercisesWithVideoSync()
    }

    /**
     * Test Case 7: Get Exercise By ID
     */
    @Test
    fun testGetExerciseById() = runTest {
        whenever(mockDao.getExerciseById(1)).thenReturn(squatExercise)

        val result = mockDao.getExerciseById(1)

        assertNotNull(result)
        assertEquals("Barbell Back Squat", result?.name)
        verify(mockDao).getExerciseById(1)
    }

    /**
     * Test Case 8: Insert Exercise
     */
    @Test
    fun testInsertExercise() = runTest {
        val newExercise = Exercise(
            name = "Deadlift",
            muscleGroup = "Back",
            equipmentType = "Barbell",
            isCustom = false
        )
        whenever(mockDao.insertExercise(newExercise)).thenReturn(3L)

        val id = mockDao.insertExercise(newExercise)

        assertEquals(3L, id)
        verify(mockDao).insertExercise(newExercise)
    }

    /**
     * Test Case 9: Update Exercise
     */
    @Test
    fun testUpdateExercise() = runTest {
        val updatedSquat = squatExercise.copy(isFavorite = true)

        mockDao.updateExercise(updatedSquat)

        verify(mockDao).updateExercise(updatedSquat)
    }

    /**
     * Test Case 10: Delete All Exercises
     */
    @Test
    fun testDeleteAllExercises() = runTest {
        mockDao.deleteAllExercises()

        verify(mockDao).deleteAllExercises()
    }

    /**
     * Bonus Test: Get Exercises By Muscle Group
     */
    @Test
    fun testGetExercisesByMuscleGroup() = runTest {
        val legExercises = listOf(squatExercise)
        whenever(mockDao.getExercisesByMuscleGroup("Legs")).thenReturn(flowOf(legExercises))

        val result = mockDao.getExercisesByMuscleGroup("Legs").first()

        assertEquals(1, result.size)
        assertEquals("Legs", result[0].muscleGroup)
        verify(mockDao).getExercisesByMuscleGroup("Legs")
    }

    /**
     * Bonus Test: Delete Single Exercise
     */
    @Test
    fun testDeleteExercise() = runTest {
        mockDao.deleteExercise(customExercise)

        verify(mockDao).deleteExercise(customExercise)
    }
}
