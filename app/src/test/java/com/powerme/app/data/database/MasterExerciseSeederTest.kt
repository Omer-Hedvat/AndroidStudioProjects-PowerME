package com.powerme.app.data.database

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream

/**
 * Tests that MasterExerciseSeeder writes exercise_muscle_groups rows correctly.
 *
 * Covers:
 * 1. Fresh seed: new exercise insert also creates an EMG primary row
 * 2. Update path: existing exercise with no EMG row gets one inserted
 * 3. Update path: existing exercise that already has an EMG row is NOT re-inserted
 * 4. Backfill: exercises not touched in the main loop (e.g. custom) still get backfilled if missing
 */
class MasterExerciseSeederTest {

    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var mockExerciseDao: ExerciseDao
    private lateinit var mockEmgDao: ExerciseMuscleGroupDao
    private lateinit var seeder: MasterExerciseSeeder

    private val minimalJson = """
        {
          "version": "1.8",
          "lastUpdated": "2026-04-17",
          "totalExercises": 2,
          "exercises": [
            {
              "id": 0,
              "name": "Barbell Back Squat",
              "muscleGroup": "Legs",
              "equipmentType": "Barbell",
              "exerciseType": "STRENGTH",
              "isCustom": false,
              "isFavorite": false,
              "searchName": "barbell back squat"
            },
            {
              "id": 0,
              "name": "Bench Press",
              "muscleGroup": "Chest",
              "equipmentType": "Barbell",
              "exerciseType": "STRENGTH",
              "isCustom": false,
              "isFavorite": false,
              "searchName": "bench press"
            }
          ]
        }
    """.trimIndent()

    @Before
    fun setup() {
        mockContext = mock()
        mockResources = mock()
        mockPrefs = mock()
        mockPrefsEditor = mock()
        mockExerciseDao = mock()
        mockEmgDao = mock()

        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockPrefs.getString(any(), any())).thenReturn(null) // not yet seeded
        whenever(mockPrefs.edit()).thenReturn(mockPrefsEditor)
        whenever(mockPrefsEditor.putString(any(), any())).thenReturn(mockPrefsEditor)

        whenever(mockResources.openRawResource(any())).thenAnswer {
            ByteArrayInputStream(minimalJson.toByteArray())
        }

        seeder = MasterExerciseSeeder(mockContext, mockExerciseDao, mockEmgDao)
    }

    /**
     * Test 1: Fresh install — both exercises are new, both get EMG rows inserted.
     */
    @Test
    fun `fresh seed inserts EMG primary row for each new exercise`() = runTest {
        // No existing exercises
        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(emptyList())
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(emptyList())
        whenever(mockExerciseDao.insertExercise(any())).thenReturn(1L, 2L)
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(2)

        seeder.seedIfNeeded()

        // Verify EMG rows flushed in a single insertAll() batch (not per-exercise insert())
        verify(mockEmgDao, never()).insert(any())
        val captor = argumentCaptor<List<ExerciseMuscleGroup>>()
        verify(mockEmgDao, times(1)).insertAll(captor.capture())

        val inserted = captor.firstValue
        assertEquals(2, inserted.size)
        assertEquals(1L, inserted[0].exerciseId)
        assertEquals("Legs", inserted[0].majorGroup)
        assertEquals(true, inserted[0].isPrimary)
        assertEquals(2L, inserted[1].exerciseId)
        assertEquals("Chest", inserted[1].majorGroup)
        assertEquals(true, inserted[1].isPrimary)
    }

    /**
     * Test 2: Upgrade path — exercise exists in exercises table but has no EMG row.
     * Seeder should insert the missing EMG row on the update path.
     */
    @Test
    fun `update path inserts EMG row when exercise has none`() = runTest {
        val existingSquat = Exercise(
            id = 10L, name = "Barbell Back Squat", muscleGroup = "Legs",
            equipmentType = "Barbell", isCustom = false
        )
        val existingBench = Exercise(
            id = 11L, name = "Bench Press", muscleGroup = "Chest",
            equipmentType = "Barbell", isCustom = false
        )
        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(existingSquat, existingBench))
        // Neither exercise has an EMG row yet
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(emptyList())
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(2)

        seeder.seedIfNeeded()

        // EMG rows for update path now flushed in a single insertAll() batch
        verify(mockEmgDao, never()).insert(any())
        val captor = argumentCaptor<List<ExerciseMuscleGroup>>()
        verify(mockEmgDao, times(1)).insertAll(captor.capture())

        val inserted = captor.firstValue
        assertEquals(10L, inserted[0].exerciseId)
        assertEquals("Legs", inserted[0].majorGroup)
        assertEquals(true, inserted[0].isPrimary)
        assertEquals(11L, inserted[1].exerciseId)
        assertEquals("Chest", inserted[1].majorGroup)
        assertEquals(true, inserted[1].isPrimary)
    }

    /**
     * Test 3: Update path — exercise already has an EMG row.
     * Seeder must NOT insert a duplicate.
     */
    @Test
    fun `update path skips EMG insert when row already exists`() = runTest {
        val existingSquat = Exercise(
            id = 10L, name = "Barbell Back Squat", muscleGroup = "Legs",
            equipmentType = "Barbell", isCustom = false
        )
        val existingBench = Exercise(
            id = 11L, name = "Bench Press", muscleGroup = "Chest",
            equipmentType = "Barbell", isCustom = false
        )
        whenever(mockExerciseDao.getAllExercisesSync()).thenReturn(listOf(existingSquat, existingBench))
        // Both exercises already have EMG rows
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(listOf(10L, 11L))
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(2)

        seeder.seedIfNeeded()

        // No individual inserts in main loop; backfill also finds nothing missing
        verify(mockEmgDao, never()).insert(any())
        verify(mockEmgDao, never()).insertAll(any())
    }

    /**
     * Test 3b: Reseed preserves isFavorite from existing exercise and does NOT bump updatedAt.
     *
     * This is critical for v2.1 — the seed adds tags to 27 existing exercises. We must not
     * bump updatedAt on those rows (would trigger a Firestore push storm for all 265 exercises).
     *
     * The minimalJson has 2 exercises: "Barbell Back Squat" (in DB) + "Bench Press" (new insert).
     */
    @Test
    fun `reseed preserves isFavorite and updatedAt from existing exercise`() = runTest {
        val existingSquat = Exercise(
            id = 10L, name = "Barbell Back Squat", muscleGroup = "Legs",
            equipmentType = "Barbell", isCustom = false,
            isFavorite = true,
            updatedAt = 1714000000000L // non-zero timestamp from a prior sync
        )
        whenever(mockExerciseDao.getAllExercisesSync())
            .thenReturn(listOf(existingSquat))      // main loop lookup
            .thenReturn(listOf(existingSquat))      // backfill sweep
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(listOf(10L))
        whenever(mockExerciseDao.insertExercise(any())).thenReturn(11L) // "Bench Press" is new
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(2)

        seeder.seedIfNeeded()

        // Updates are now flushed via updateAll() in a single batch
        val captor = argumentCaptor<List<Exercise>>()
        verify(mockExerciseDao).updateAll(captor.capture())
        val updated = captor.firstValue[0]

        assertTrue("isFavorite must be preserved", updated.isFavorite)
        assertEquals("updatedAt must not be bumped", 1714000000000L, updated.updatedAt)
    }

    /**
     * Test 3c: Reseed does NOT update isFavorite to false if user has favorited the exercise.
     */
    @Test
    fun `reseed never clobbers user-set isFavorite`() = runTest {
        // JSON says isFavorite=false for "Barbell Back Squat", but user set it to true
        val existingSquat = Exercise(
            id = 10L, name = "Barbell Back Squat", muscleGroup = "Legs",
            equipmentType = "Barbell", isCustom = false,
            isFavorite = true
        )
        whenever(mockExerciseDao.getAllExercisesSync())
            .thenReturn(listOf(existingSquat))      // main loop lookup
            .thenReturn(listOf(existingSquat))      // backfill sweep
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(listOf(10L))
        whenever(mockExerciseDao.insertExercise(any())).thenReturn(11L) // "Bench Press" is new
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(2)

        seeder.seedIfNeeded()

        val captor = argumentCaptor<List<Exercise>>()
        verify(mockExerciseDao).updateAll(captor.capture())
        assertTrue("isFavorite must remain true after reseed", captor.firstValue[0].isFavorite)
    }

    /**
     * Test 4: Backfill covers exercises that exist in the DB but were not touched by the
     * main loop (e.g. custom exercises). They should get a primary EMG row via insertAll().
     *
     * Setup: JSON has "Barbell Back Squat" + "Bench Press". DB has "Barbell Back Squat" (master)
     * + "My Custom Move" (custom) + "Bench Press" (master). Both masters have no EMG rows.
     * "My Custom Move" is skipped by the main loop (isCustom) and has no EMG row either.
     * After the main loop inserts EMG for the two masters, the backfill should find
     * "My Custom Move" (id=99) still missing and insert it via insertAll().
     */
    @Test
    fun `backfill inserts EMG rows for exercises not touched by main loop`() = runTest {
        val masterSquat = Exercise(
            id = 10L, name = "Barbell Back Squat", muscleGroup = "Legs",
            equipmentType = "Barbell", isCustom = false
        )
        val masterBench = Exercise(
            id = 11L, name = "Bench Press", muscleGroup = "Chest",
            equipmentType = "Barbell", isCustom = false
        )
        val customExercise = Exercise(
            id = 99L, name = "My Custom Move", muscleGroup = "Arms",
            equipmentType = "Bodyweight", isCustom = true
        )
        whenever(mockExerciseDao.getAllExercisesSync())
            // First call: existingByName lookup at start of performSeed
            .thenReturn(listOf(masterSquat, masterBench, customExercise))
            // Second call: allExercisesAfterSeed for the backfill sweep
            .thenReturn(listOf(masterSquat, masterBench, customExercise))
        // No exercises have EMG rows yet
        whenever(mockEmgDao.getAllExerciseIds()).thenReturn(emptyList())
        whenever(mockExerciseDao.getExerciseCountSync()).thenReturn(3)

        seeder.seedIfNeeded()

        // Main loop now uses insertAll() batch, not individual insert() calls
        verify(mockEmgDao, never()).insert(any())

        // insertAll() is called twice: once for main-loop flush (10L + 11L), once for backfill (99L)
        val listCaptor = argumentCaptor<List<ExerciseMuscleGroup>>()
        verify(mockEmgDao, times(2)).insertAll(listCaptor.capture())

        val allInserted = listCaptor.allValues.flatten()
        assertEquals(3, allInserted.size)

        val mainInserts = allInserted.filter { it.exerciseId in setOf(10L, 11L) }
        assertEquals(2, mainInserts.size)
        assertEquals(setOf(10L, 11L), mainInserts.map { it.exerciseId }.toSet())

        val backfilled = allInserted.filter { it.exerciseId == 99L }
        assertEquals(1, backfilled.size)
        assertEquals("Arms", backfilled[0].majorGroup)
        assertEquals(true, backfilled[0].isPrimary)
    }
}
