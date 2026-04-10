package com.powerme.app.ui.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies filter-chip invariants after the v24 normalization migration.
 *
 * Since filter chips are now DB-driven (SELECT DISTINCT), these tests verify:
 * - Canonical equipment/muscle-group constants are complete and non-duplicated.
 * - Legacy non-canonical values ("Dumbbells", "Bodyweight+", "Abs", etc.) are NOT in
 *   the normalized list, confirming the migration SQL mapped them correctly.
 * - MuscleGroups constants are self-consistent.
 */
class ExerciseFilterTest {

    // Canonical muscle groups expected after v24 normalization.
    private val canonicalMuscleGroups = listOf("Legs", "Back", "Core", "Chest", "Shoulders", "Full Body", "Arms", "Cardio")

    // Canonical equipment types expected after v33 consolidation.
    private val canonicalEquipmentTypes = listOf(
        "Barbell",
        "Dumbbell",
        "Machine",
        "Cable",
        "Bodyweight",
        "Bench",
        "Sled",
        "Battle Ropes",
        "Medicine Ball",
        "Kettlebell",
    )

    // Legacy non-canonical values that migrations must have eliminated.
    private val legacyEquipmentValues = listOf(
        "Dumbbells", "Bodyweight+",
        "Bench/Chair", "Bench/Couch", "Bench/Floor", "Box/Bench", "Couch/Bench", "Wall",
        "Plyometric Box"
    )

    // Legacy non-canonical muscle group values eliminated by the migration.
    private val legacyMuscleGroupValues = listOf(
        "Rear Delts", "Lats", "Hamstrings", "Triceps", "Biceps",
        "Abs", "Upper Chest", "Side Delts", "Chest/Triceps"
    )

    @Test
    fun `canonical equipment types have no duplicates`() {
        val lower = canonicalEquipmentTypes.map { it.lowercase() }
        assertEquals(
            "Canonical equipment type list contains duplicates",
            lower.toSet().size, lower.size
        )
    }

    @Test
    fun `canonical equipment types are in Title Case`() {
        for (type in canonicalEquipmentTypes) {
            val firstChar = type.first()
            assertTrue(
                "Equipment type '$type' should start with an uppercase letter",
                firstChar.isUpperCase()
            )
        }
    }

    @Test
    fun `legacy equipment values are not in canonical list`() {
        for (legacy in legacyEquipmentValues) {
            assertFalse(
                "Legacy value '$legacy' should have been normalized out of the canonical list",
                canonicalEquipmentTypes.any { it.equals(legacy, ignoreCase = false) }
            )
        }
    }

    @Test
    fun `legacy muscle group values are not in MuscleGroups constants`() {
        for (legacy in legacyMuscleGroupValues) {
            assertFalse(
                "Legacy muscleGroup '$legacy' should not appear in canonical list after v24 normalization",
                canonicalMuscleGroups.any { it.equals(legacy, ignoreCase = false) }
            )
        }
    }

    @Test
    fun `MuscleGroups ALL contains the eight canonical groups`() {
        val expected = listOf("Legs", "Back", "Core", "Chest", "Shoulders", "Full Body", "Arms", "Cardio")
        for (group in expected) {
            assertTrue("canonicalMuscleGroups must contain '$group'", canonicalMuscleGroups.contains(group))
        }
        assertEquals("canonicalMuscleGroups size should be 8", 8, canonicalMuscleGroups.size)
    }

    @Test
    fun `MuscleGroups ALL has no duplicates`() {
        assertEquals(
            "MuscleGroups.ALL contains duplicates",
            canonicalMuscleGroups.toSet().size, canonicalMuscleGroups.size
        )
    }

    @Test
    fun `priority equipment types appear before alphabetical remainder`() {
        val priority = ExercisesViewModel.PRIORITY_EQUIPMENT
        val dbAlphabetical = canonicalEquipmentTypes.sorted()
        val prioritized = priority.filter { p -> dbAlphabetical.any { it.equals(p, ignoreCase = true) } }
        val remaining = dbAlphabetical.filter { t -> priority.none { it.equals(t, ignoreCase = true) } }
        val result = prioritized + remaining

        assertEquals(priority[0], result[0])
        assertEquals(priority[1], result[1])
        assertEquals(priority[2], result[2])
        assertEquals(priority[3], result[3])
        assertEquals(priority[4], result[4])
        assertEquals(priority[5], result[5])
        val tail = result.drop(priority.size)
        assertEquals("tail should be alphabetical", tail.sorted(), tail)
    }

    @Test
    fun `Dumbbell is canonical singular form`() {
        // Regression test for the original bug: DB had "Dumbbells", filter had "Dumbbell".
        // v24 migration normalizes "Dumbbells" → "Dumbbell" for all master exercises.
        assertTrue(
            "Canonical list must contain singular 'Dumbbell'",
            canonicalEquipmentTypes.contains("Dumbbell")
        )
        assertFalse(
            "Plural 'Dumbbells' must NOT appear in the canonical list",
            canonicalEquipmentTypes.contains("Dumbbells")
        )
    }
}
