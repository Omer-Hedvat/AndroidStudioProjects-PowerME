package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ExerciseRepositoryFunctionalTagTest {

    private lateinit var dao: ExerciseDao
    private lateinit var repository: ExerciseRepository

    @Before
    fun setup() {
        dao = mock()
        repository = ExerciseRepository(dao)
    }

    private fun exercise(id: Long = 1L, tags: String = "[]") = Exercise(
        id = id, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell", tags = tags
    )

    @Test
    fun `toggleFunctionalTag — add tag to empty array`() = runTest {
        val ex = exercise(tags = "[]")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assertEquals("[\"functional\"]", captor.firstValue)
    }

    @Test
    fun `toggleFunctionalTag — add tag to array with existing tag`() = runTest {
        val ex = exercise(tags = """["olympic"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assertEquals("""["olympic","functional"]""", captor.firstValue)
    }

    @Test
    fun `toggleFunctionalTag — remove only functional tag leaves empty array`() = runTest {
        val ex = exercise(tags = """["functional"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assertEquals("[]", captor.firstValue)
    }

    @Test
    fun `toggleFunctionalTag — remove functional tag from multi-tag array (first)`() = runTest {
        val ex = exercise(tags = """["functional","olympic"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assertEquals("""["olympic"]""", captor.firstValue)
    }

    @Test
    fun `toggleFunctionalTag — remove functional tag from multi-tag array (last)`() = runTest {
        val ex = exercise(tags = """["olympic","functional"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assertEquals("""["olympic"]""", captor.firstValue)
    }

    @Test
    fun `toggleFunctionalTag — remove functional tag from middle of array`() = runTest {
        val ex = exercise(tags = """["olympic","functional","gymnastics"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        val result = captor.firstValue
        assert(!result.contains("\"functional\"")) { "functional tag should be removed, got: $result" }
        assert(result.contains("\"olympic\"")) { "olympic tag should remain" }
        assert(result.contains("\"gymnastics\"")) { "gymnastics tag should remain" }
    }

    @Test
    fun `toggleFunctionalTag — result has functional tag after add`() = runTest {
        val ex = exercise(tags = "[]")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assert(captor.firstValue.contains("\"functional\"")) {
            "functional tag should be present after add"
        }
    }

    @Test
    fun `toggleFunctionalTag — result does not have functional tag after remove`() = runTest {
        val ex = exercise(tags = """["functional","olympic"]""")
        val captor = argumentCaptor<String>()
        repository.toggleFunctionalTag(ex)
        verify(dao).updateTags(eq(1L), captor.capture())
        assert(!captor.firstValue.contains("\"functional\"")) {
            "functional tag should be absent after remove"
        }
    }
}
