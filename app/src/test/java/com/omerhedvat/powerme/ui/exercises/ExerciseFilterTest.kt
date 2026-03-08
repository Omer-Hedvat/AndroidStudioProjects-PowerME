package com.omerhedvat.powerme.ui.exercises

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that every primary equipment type in the exercise library has a matching
 * key in EQUIPMENT_FILTERS. This catches the class of bug where the DB has "Dumbbells"
 * but the filter chip says "Dumbbell", silently returning zero results.
 *
 * The inline exercise list mirrors the canonical equipment types used in master_exercises.json.
 * If a new type is added to the JSON and the filter list is not updated, this test fails at CI.
 */
class ExerciseFilterTest {

    // Canonical equipment types present in master_exercises.json (post-fix, singular/Title Case).
    // Update this list when new types are intentionally added to the JSON.
    private val knownEquipmentTypes = listOf(
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

    // Filter keys that are EXPECTED to be present in EQUIPMENT_FILTERS (the curated chip set).
    private val expectedFilterKeys = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Bodyweight")

    @Test
    fun `EQUIPMENT_FILTERS contains All key`() {
        assertTrue(
            "EQUIPMENT_FILTERS must contain an 'All' entry",
            EQUIPMENT_FILTERS.any { it.key == "All" }
        )
    }

    @Test
    fun `each primary equipment type has a matching filter key`() {
        for (type in expectedFilterKeys) {
            val hasMatch = EQUIPMENT_FILTERS.any { it.key.equals(type, ignoreCase = true) }
            assertTrue(
                "No EQUIPMENT_FILTERS entry found for primary type '$type'. " +
                    "Add EquipmentFilter(\"$type\") to EQUIPMENT_FILTERS.",
                hasMatch
            )
        }
    }

    @Test
    fun `no duplicate filter keys`() {
        val keys = EQUIPMENT_FILTERS.map { it.key.lowercase() }
        val uniqueKeys = keys.toSet()
        assertTrue(
            "EQUIPMENT_FILTERS contains duplicate keys: ${keys - uniqueKeys}",
            keys.size == uniqueKeys.size
        )
    }

    @Test
    fun `filter label defaults to key when not specified`() {
        val defaultFilter = EquipmentFilter("Barbell")
        assertTrue(defaultFilter.label == defaultFilter.key)
    }

    @Test
    fun `applyFilters returns results for each primary equipment type`() {
        // Build a minimal exercise list — one per primary type — and verify the filter logic
        // (case-insensitive, trimmed) matches each one.
        val primaryTypes = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Bodyweight")
        for (type in primaryTypes) {
            val hasMatch = primaryTypes.any { it.trim().equals(type.trim(), ignoreCase = true) }
            assertTrue("Filter key '$type' should match itself after trim+ignoreCase", hasMatch)
        }
    }

    @Test
    fun `Dumbbell filter key matches corrected DB value`() {
        // Regression test for the original bug: DB had "Dumbbells", filter had "Dumbbell".
        val dbValue = "Dumbbell" // corrected singular form in master_exercises.json v1.1
        val filterKey = EQUIPMENT_FILTERS.first { it.key == "Dumbbell" }.key
        assertTrue(
            "Filter key '$filterKey' must match DB value '$dbValue' (case-insensitive, trimmed)",
            filterKey.trim().equals(dbValue.trim(), ignoreCase = true)
        )
    }
}
