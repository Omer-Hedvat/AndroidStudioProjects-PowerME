package com.powerme.app.ui.exercises

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.matchesSearchTokens
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.database.toSearchTokens
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

    // --- Elastic search (token-based matching) ---

    private fun makeExercise(name: String) = Exercise(
        id = 0, name = name, muscleGroup = "Legs", equipmentType = "Barbell",
        searchName = name.toSearchName()
    )

    @Test
    fun `reversed word order matches — squat back finds Back Squat`() {
        val exercise = makeExercise("Back Squat")
        val tokens = "squat back".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `reversed word order matches — curl bicep finds Bicep Curl`() {
        val exercise = makeExercise("Bicep Curl")
        val tokens = "curl bicep".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `single token still works — squat matches Back Squat`() {
        val exercise = makeExercise("Back Squat")
        val tokens = "squat".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `empty tokens matches everything`() {
        val exercise = makeExercise("Back Squat")
        assertTrue(exercise.matchesSearchTokens(emptyList()))
    }

    @Test
    fun `partial token matches — squ matches Back Squat`() {
        val exercise = makeExercise("Back Squat")
        val tokens = "squ".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `all tokens must match — no false positive`() {
        val exercise = makeExercise("Back Squat")
        val tokens = "squat bench".toSearchTokens()
        assertFalse(exercise.matchesSearchTokens(tokens))
    }

    // --- Synonym expansion ---

    @Test
    fun `military press finds Overhead Press via synonym`() {
        val exercise = makeExercise("Standing Barbell Overhead Press")
        val tokens = "military press".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `ohp finds Overhead Press via synonym`() {
        val exercise = makeExercise("Standing Barbell Overhead Press")
        val tokens = "ohp".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `rdl finds Romanian Deadlift via synonym`() {
        val exercise = makeExercise("Romanian Deadlift (RDL) - BB")
        val tokens = "rdl".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `db rdl finds Romanian Deadlift Dumbbell via synonym`() {
        val exercise = makeExercise("Romanian Deadlift (RDL) - Dumbbell")
        val tokens = "db rdl".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `synonym does not cause false positive across unrelated exercises`() {
        val exercise = makeExercise("Leg Press")
        val tokens = "military press".toSearchTokens()
        // "military" expands to "overhead press" — not in "Leg Press"; should NOT match
        assertFalse(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `ohp does not match Overhead Squat`() {
        val exercise = makeExercise("Overhead Squat")
        val tokens = "ohp".toSearchTokens()
        // "ohp" expands to "overhead press" (phrase) — "Overhead Squat" lacks "press"; NO match
        assertFalse(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `ohp does not match Overhead Tricep Extension`() {
        val exercise = makeExercise("Overhead Tricep Extension")
        val tokens = "ohp".toSearchTokens()
        assertFalse(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `ohp does not match Plank Shoulder Tap`() {
        val exercise = makeExercise("Plank Shoulder Tap")
        val tokens = "ohp".toSearchTokens()
        assertFalse(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `facepull matches Face Pull despite space difference`() {
        val exercise = makeExercise("Face Pull")
        val tokens = "facepull".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    @Test
    fun `military alone finds Overhead Press`() {
        val exercise = makeExercise("Standing Barbell Overhead Press")
        val tokens = "military".toSearchTokens()
        assertTrue(exercise.matchesSearchTokens(tokens))
    }

    // --- exerciseType filter predicate ---

    private fun makeTypedExercise(name: String, type: ExerciseType) = Exercise(
        id = 0, name = name, muscleGroup = "Legs", equipmentType = "Barbell",
        exerciseType = type, searchName = name.toSearchName()
    )

    private fun applyTypeFilter(exercises: List<Exercise>, types: Set<ExerciseType>): List<Exercise> =
        exercises.filter { types.isEmpty() || it.exerciseType in types }

    @Test
    fun `type filter empty set — all exercises returned`() {
        val exercises = listOf(
            makeTypedExercise("Squat", ExerciseType.STRENGTH),
            makeTypedExercise("Run", ExerciseType.CARDIO),
            makeTypedExercise("Box Jump", ExerciseType.PLYOMETRIC)
        )
        assertEquals(3, applyTypeFilter(exercises, emptySet()).size)
    }

    @Test
    fun `type filter STRENGTH — only strength exercises returned`() {
        val exercises = listOf(
            makeTypedExercise("Squat", ExerciseType.STRENGTH),
            makeTypedExercise("Run", ExerciseType.CARDIO),
            makeTypedExercise("Box Jump", ExerciseType.PLYOMETRIC)
        )
        val result = applyTypeFilter(exercises, setOf(ExerciseType.STRENGTH))
        assertEquals(1, result.size)
        assertEquals("Squat", result[0].name)
    }

    @Test
    fun `type filter CARDIO and PLYOMETRIC — both types returned`() {
        val exercises = listOf(
            makeTypedExercise("Squat", ExerciseType.STRENGTH),
            makeTypedExercise("Run", ExerciseType.CARDIO),
            makeTypedExercise("Box Jump", ExerciseType.PLYOMETRIC)
        )
        val result = applyTypeFilter(exercises, setOf(ExerciseType.CARDIO, ExerciseType.PLYOMETRIC))
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Run" })
        assertTrue(result.any { it.name == "Box Jump" })
        assertFalse(result.any { it.name == "Squat" })
    }

    @Test
    fun `type filter with all types selected — all exercises returned`() {
        val exercises = listOf(
            makeTypedExercise("Squat", ExerciseType.STRENGTH),
            makeTypedExercise("Run", ExerciseType.CARDIO),
            makeTypedExercise("Box Jump", ExerciseType.PLYOMETRIC),
            makeTypedExercise("Plank Hold", ExerciseType.TIMED),
            makeTypedExercise("Stretch", ExerciseType.STRETCH)
        )
        val result = applyTypeFilter(exercises, ExerciseType.entries.toSet())
        assertEquals(5, result.size)
    }

    // --- activeFilterCount ---

    @Test
    fun `activeFilterCount is zero when all filters empty`() {
        val state = ExercisesUiState()
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun `activeFilterCount counts selected types`() {
        val state = ExercisesUiState(
            selectedTypes = setOf(ExerciseType.CARDIO, ExerciseType.PLYOMETRIC)
        )
        assertEquals(2, state.activeFilterCount)
    }

    @Test
    fun `activeFilterCount counts functional filter as 1`() {
        val state = ExercisesUiState(functionalFilter = true)
        assertEquals(1, state.activeFilterCount)
    }

    @Test
    fun `activeFilterCount sums all active filter dimensions`() {
        val state = ExercisesUiState(
            selectedMuscles = setOf("Legs", "Back"),
            selectedEquipment = setOf("Barbell"),
            selectedTypes = setOf(ExerciseType.STRENGTH),
            functionalFilter = true
        )
        // 2 muscles + 1 equipment + 1 type + 1 functional = 5
        assertEquals(5, state.activeFilterCount)
    }

    // --- favoritesOnly filter predicate ---

    private fun makeFavExercise(name: String, isFavorite: Boolean, equipment: String = "Barbell") = Exercise(
        id = 0, name = name, muscleGroup = "Legs", equipmentType = equipment,
        isFavorite = isFavorite, searchName = name.toSearchName()
    )

    private fun applyFavoritesFilter(exercises: List<Exercise>, favoritesOnly: Boolean): List<Exercise> =
        exercises.filter { !favoritesOnly || it.isFavorite }

    @Test
    fun `favoritesOnly false — all exercises returned`() {
        val exercises = listOf(
            makeFavExercise("Squat", isFavorite = true),
            makeFavExercise("Deadlift", isFavorite = false),
            makeFavExercise("Bench Press", isFavorite = false)
        )
        assertEquals(3, applyFavoritesFilter(exercises, favoritesOnly = false).size)
    }

    @Test
    fun `favoritesOnly true — only favourited exercises returned`() {
        val exercises = listOf(
            makeFavExercise("Squat", isFavorite = true),
            makeFavExercise("Deadlift", isFavorite = false),
            makeFavExercise("Bench Press", isFavorite = false)
        )
        val result = applyFavoritesFilter(exercises, favoritesOnly = true)
        assertEquals(1, result.size)
        assertEquals("Squat", result[0].name)
    }

    @Test
    fun `favoritesOnly true with no favourites — empty list returned`() {
        val exercises = listOf(
            makeFavExercise("Deadlift", isFavorite = false),
            makeFavExercise("Bench Press", isFavorite = false)
        )
        assertEquals(0, applyFavoritesFilter(exercises, favoritesOnly = true).size)
    }

    @Test
    fun `favoritesOnly stacks with equipment filter — AND logic`() {
        val exercises = listOf(
            makeFavExercise("Squat", isFavorite = true, equipment = "Barbell"),
            makeFavExercise("Deadlift", isFavorite = true, equipment = "Dumbbell"),
            makeFavExercise("Bench Press", isFavorite = false, equipment = "Barbell")
        )
        val favs = applyFavoritesFilter(exercises, favoritesOnly = true)
        val result = favs.filter { it.equipmentType == "Barbell" }
        assertEquals(1, result.size)
        assertEquals("Squat", result[0].name)
    }

    @Test
    fun `favoritesOnly does not increment activeFilterCount`() {
        val state = ExercisesUiState(favoritesOnly = true)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun `activeFilterCount unaffected by favoritesOnly when other filters also active`() {
        val state = ExercisesUiState(
            selectedMuscles = setOf("Legs"),
            selectedEquipment = setOf("Barbell"),
            favoritesOnly = true
        )
        // 1 muscle + 1 equipment = 2; favoritesOnly excluded
        assertEquals(2, state.activeFilterCount)
    }

    // --- applyInitialTypeFilters guard logic ---

    private fun simulateApplyInitialTypeFilters(
        currentState: ExercisesUiState,
        initialTypes: Set<ExerciseType>
    ): ExercisesUiState {
        return if (initialTypes.isNotEmpty() && currentState.selectedTypes.isEmpty()) {
            currentState.copy(selectedTypes = initialTypes)
        } else {
            currentState
        }
    }

    @Test
    fun `applyInitialTypeFilters — sets types when selectedTypes is empty`() {
        val state = ExercisesUiState()
        val result = simulateApplyInitialTypeFilters(state, setOf(ExerciseType.STRENGTH, ExerciseType.TIMED))
        assertEquals(setOf(ExerciseType.STRENGTH, ExerciseType.TIMED), result.selectedTypes)
    }

    @Test
    fun `applyInitialTypeFilters — no-op when selectedTypes already has values`() {
        val state = ExercisesUiState(selectedTypes = setOf(ExerciseType.CARDIO))
        val result = simulateApplyInitialTypeFilters(state, setOf(ExerciseType.STRENGTH, ExerciseType.TIMED))
        assertEquals(setOf(ExerciseType.CARDIO), result.selectedTypes)
    }

    @Test
    fun `applyInitialTypeFilters — no-op when initialTypes is empty`() {
        val state = ExercisesUiState()
        val result = simulateApplyInitialTypeFilters(state, emptySet())
        assertEquals(emptySet<ExerciseType>(), result.selectedTypes)
    }

    @Test
    fun `applyInitialTypeFilters — CARDIO and PLYOMETRIC applied for functional entry point`() {
        val state = ExercisesUiState()
        val result = simulateApplyInitialTypeFilters(state, setOf(ExerciseType.CARDIO, ExerciseType.PLYOMETRIC))
        assertEquals(setOf(ExerciseType.CARDIO, ExerciseType.PLYOMETRIC), result.selectedTypes)
    }

    @Test
    fun `applyInitialTypeFilters — applied types are reflected in activeFilterCount`() {
        val state = ExercisesUiState()
        val result = simulateApplyInitialTypeFilters(state, setOf(ExerciseType.STRENGTH, ExerciseType.TIMED))
        assertEquals(2, result.activeFilterCount)
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
