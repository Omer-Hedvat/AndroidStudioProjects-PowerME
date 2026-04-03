package com.omerhedvat.powerme.data.database

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineExerciseWithNameTest {

    private fun makeRow(sets: Int, setTypesJson: String) = RoutineExerciseWithName(
        exerciseId = 1L,
        exerciseName = "Bench Press",
        muscleGroup = "Chest",
        equipmentType = "Barbell",
        sets = sets,
        reps = 10,
        order = 0,
        supersetGroupId = null,
        setTypesJson = setTypesJson
    )

    @Test
    fun `workingSets returns raw sets when setTypesJson is blank (legacy rows)`() {
        val row = makeRow(sets = 4, setTypesJson = "")
        assertEquals(4, row.workingSets)
    }

    @Test
    fun `workingSets excludes WARMUP sets`() {
        // 1 WARMUP + 3 NORMAL = 3 working
        val row = makeRow(sets = 4, setTypesJson = "WARMUP,NORMAL,NORMAL,NORMAL")
        assertEquals(3, row.workingSets)
    }

    @Test
    fun `workingSets returns 0 when all sets are WARMUP`() {
        val row = makeRow(sets = 2, setTypesJson = "WARMUP,WARMUP")
        assertEquals(0, row.workingSets)
    }

    @Test
    fun `workingSets counts DROP and FAILURE as working sets`() {
        val row = makeRow(sets = 3, setTypesJson = "NORMAL,DROP,FAILURE")
        assertEquals(3, row.workingSets)
    }

    @Test
    fun `workingSets handles malformed enum strings gracefully`() {
        // Unknown string "INVALID" → runCatching returns null → null != WARMUP → counted as working
        // This is intentional: unknown types are treated as working sets to avoid silently losing sets
        val row = makeRow(sets = 3, setTypesJson = "NORMAL,INVALID,NORMAL")
        assertEquals(3, row.workingSets)
    }

    @Test
    fun `workingSets handles single WARMUP entry`() {
        val row = makeRow(sets = 1, setTypesJson = "WARMUP")
        assertEquals(0, row.workingSets)
    }

    @Test
    fun `workingSets handles single NORMAL entry`() {
        val row = makeRow(sets = 1, setTypesJson = "NORMAL")
        assertEquals(1, row.workingSets)
    }
}
