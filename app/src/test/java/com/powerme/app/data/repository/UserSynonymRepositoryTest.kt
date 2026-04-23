package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.UserExerciseSynonym
import com.powerme.app.data.database.UserSynonymDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserSynonymRepositoryTest {

    private lateinit var dao: UserSynonymDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var repo: UserSynonymRepository

    private val stubExercise = Exercise(
        id = 42L,
        name = "Barbell Bench Press",
        muscleGroup = "Chest",
        equipmentType = "Barbell",
        searchName = "barbellbenchpress"
    )

    @Before
    fun setup() {
        dao = mock()
        exerciseDao = mock()
        repo = UserSynonymRepository(dao, exerciseDao)
    }

    // ── findExercise ─────────────────────────────────────────────────────────

    @Test
    fun `findExercise returns null when no synonym exists`() = runTest {
        whenever(dao.findByRawName(any())).thenReturn(null)

        val result = repo.findExercise("flat bench")

        assertNull(result)
        verify(dao, never()).incrementUseCount(any())
        verify(exerciseDao, never()).getExerciseById(any())
    }

    @Test
    fun `findExercise normalises input before DAO lookup`() = runTest {
        // "Flat Bench" → toSearchName → "flatbench"
        whenever(dao.findByRawName("flatbench")).thenReturn(null)

        repo.findExercise("Flat Bench")

        verify(dao).findByRawName("flatbench")
    }

    @Test
    fun `findExercise increments use count and returns exercise on hit`() = runTest {
        val synonym = UserExerciseSynonym(rawName = "flatbench", exerciseId = 42L)
        whenever(dao.findByRawName("flatbench")).thenReturn(synonym)
        whenever(exerciseDao.getExerciseById(42L)).thenReturn(stubExercise)

        val result = repo.findExercise("Flat Bench")

        verify(dao).incrementUseCount("flatbench")
        assertEquals(stubExercise, result)
    }

    @Test
    fun `findExercise returns null when exercise not found despite synonym`() = runTest {
        val synonym = UserExerciseSynonym(rawName = "flatbench", exerciseId = 99L)
        whenever(dao.findByRawName("flatbench")).thenReturn(synonym)
        whenever(exerciseDao.getExerciseById(99L)).thenReturn(null)

        val result = repo.findExercise("flat bench")

        assertNull(result)
    }

    @Test
    fun `findExercise strips hyphens from input`() = runTest {
        // "bench-press" → "benchpress"
        whenever(dao.findByRawName("benchpress")).thenReturn(null)

        repo.findExercise("bench-press")

        verify(dao).findByRawName("benchpress")
    }

    // ── saveSynonym ──────────────────────────────────────────────────────────

    @Test
    fun `saveSynonym normalises name before inserting`() = runTest {
        repo.saveSynonym("Flat Bench", 42L)

        verify(dao).insertOrReplace(argThat { rawName == "flatbench" })
    }

    @Test
    fun `saveSynonym passes exerciseId through unchanged`() = runTest {
        repo.saveSynonym("bench", 123L)

        verify(dao).insertOrReplace(argThat { exerciseId == 123L })
    }

    @Test
    fun `saveSynonym strips parentheses from raw name`() = runTest {
        // "Overhead Press (Barbell)" → "overheadpressbarbell"
        repo.saveSynonym("Overhead Press (Barbell)", 10L)

        verify(dao).insertOrReplace(argThat { rawName == "overheadpressbarbell" })
    }

    @Test
    fun `saveSynonym strips spaces and hyphens`() = runTest {
        // "bench-press" → "benchpress"
        repo.saveSynonym("bench-press", 1L)

        verify(dao).insertOrReplace(argThat { rawName == "benchpress" })
    }

    @Test
    fun `find and save use the same normalisation`() = runTest {
        // Both sides normalise to "benchpress" — round-trip consistency check
        val synonymInDb = UserExerciseSynonym(rawName = "benchpress", exerciseId = 1L)
        whenever(dao.findByRawName("benchpress")).thenReturn(synonymInDb)
        whenever(exerciseDao.getExerciseById(1L)).thenReturn(stubExercise.copy(id = 1L))

        repo.saveSynonym("Bench-Press", 1L)
        val result = repo.findExercise("bench press")

        verify(dao).insertOrReplace(argThat { rawName == "benchpress" })
        verify(dao).findByRawName("benchpress")
        assertEquals(stubExercise.copy(id = 1L), result)
    }
}
