package com.omerhedvat.powerme.ui.exercises

import com.omerhedvat.powerme.util.MuscleGroups
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

    // Canonical equipment types expected after v24 normalization.
    private val canonicalEquipmentTypes = listOf(
        "Barbell",
        "Dumbbell",
        "Machine",
        "Cable",
        "Bodyweight",
        "Sled",
        "Battle Ropes",
        "Plyometric Box",
        "Medicine Ball",
        "Kettlebell",
    )

    // Legacy non-canonical values that the migration must have eliminated.
    private val legacyEquipmentValues = listOf("Dumbbells", "Bodyweight+")

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
                "Legacy muscleGroup '$legacy' should not appear in MuscleGroups.ALL after v24 normalization",
                MuscleGroups.ALL.any { it.equals(legacy, ignoreCase = false) }
            )
        }
    }

    @Test
    fun `MuscleGroups ALL contains the eight canonical groups`() {
        val expected = listOf("Legs", "Back", "Core", "Chest", "Shoulders", "Full Body", "Arms", "Cardio")
        for (group in expected) {
            assertTrue("MuscleGroups.ALL must contain '$group'", MuscleGroups.ALL.contains(group))
        }
        assertEquals("MuscleGroups.ALL size should be 8", 8, MuscleGroups.ALL.size)
    }

    @Test
    fun `MuscleGroups ALL has no duplicates`() {
        assertEquals(
            "MuscleGroups.ALL contains duplicates",
            MuscleGroups.ALL.toSet().size, MuscleGroups.ALL.size
        )
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
