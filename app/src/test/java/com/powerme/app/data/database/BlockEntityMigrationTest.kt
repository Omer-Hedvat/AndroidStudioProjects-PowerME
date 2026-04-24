package com.powerme.app.data.database

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests that verify the v51 migration field additions on RoutineExercise and WorkoutSet.
 *
 * These tests confirm:
 * - RoutineExercise gains nullable blockId (FK → routine_blocks.id)
 * - RoutineExercise gains nullable holdSeconds (per-prescription time cap)
 * - WorkoutSet gains nullable blockId (FK → workout_blocks.id)
 * - All three new fields default to null (safe for backfill + existing rows)
 * - holdSeconds semantics: non-null = time-capped in AMRAP/RFT; null = rep-capped or STRENGTH
 */
class BlockEntityMigrationTest {

    // ── RoutineExercise.blockId ───────────────────────────────────────────────

    @Test
    fun `RoutineExercise blockId defaults to null`() {
        val re = RoutineExercise(
            id = "re-1",
            routineId = "routine-1",
            exerciseId = 42L
        )
        assertNull(re.blockId)
    }

    @Test
    fun `RoutineExercise blockId can reference a block`() {
        val re = RoutineExercise(
            id = "re-2",
            routineId = "routine-1",
            exerciseId = 42L,
            blockId = "block-uuid-1"
        )
        assertEquals("block-uuid-1", re.blockId)
    }

    // ── RoutineExercise.holdSeconds ───────────────────────────────────────────

    @Test
    fun `RoutineExercise holdSeconds defaults to null (rep-capped or STRENGTH)`() {
        val re = RoutineExercise(
            id = "re-3",
            routineId = "routine-1",
            exerciseId = 10L
        )
        assertNull(re.holdSeconds)
    }

    @Test
    fun `RoutineExercise holdSeconds stores time in seconds for time-capped prescription`() {
        val re = RoutineExercise(
            id = "re-4",
            routineId = "routine-1",
            exerciseId = 10L,
            blockId = "block-amrap-1",
            holdSeconds = 30   // "30s Wall Ball"
        )
        assertEquals(30, re.holdSeconds)
    }

    @Test
    fun `RoutineExercise holdSeconds can store minute-aligned values`() {
        val re = RoutineExercise(
            id = "re-5",
            routineId = "routine-1",
            exerciseId = 15L,
            blockId = "block-amrap-1",
            holdSeconds = 60   // "1min Double Unders"
        )
        assertEquals(60, re.holdSeconds)
    }

    @Test
    fun `RoutineExercise with blockId null and holdSeconds null is valid STRENGTH exercise`() {
        val re = RoutineExercise(
            id = "re-6",
            routineId = "routine-1",
            exerciseId = 20L,
            sets = 5,
            reps = 5
        )
        assertNull(re.blockId)
        assertNull(re.holdSeconds)
        assertEquals(5, re.sets)
        assertEquals(5, re.reps)
    }

    @Test
    fun `RoutineExercise existing fields still work after migration fields added`() {
        val re = RoutineExercise(
            id = "re-7",
            routineId = "routine-1",
            exerciseId = 7L,
            sets = 3,
            reps = 10,
            restTime = 90,
            order = 2,
            supersetGroupId = "superset-abc",
            stickyNote = "Focus on form",
            defaultWeight = "80",
            setTypesJson = "NORMAL,WARMUP,NORMAL",
            setWeightsJson = "80,85,90",
            setRepsJson = "10,8,6"
        )
        assertEquals(3, re.sets)
        assertEquals(10, re.reps)
        assertEquals(90, re.restTime)
        assertEquals(2, re.order)
        assertEquals("superset-abc", re.supersetGroupId)
        assertEquals("Focus on form", re.stickyNote)
        assertEquals("80", re.defaultWeight)
        assertNull(re.blockId)
        assertNull(re.holdSeconds)
    }

    // ── WorkoutSet.blockId ────────────────────────────────────────────────────

    @Test
    fun `WorkoutSet blockId defaults to null`() {
        val ws = WorkoutSet(
            id = "ws-1",
            workoutId = "workout-1",
            exerciseId = 42L,
            setOrder = 0,
            weight = 80.0,
            reps = 10
        )
        assertNull(ws.blockId)
    }

    @Test
    fun `WorkoutSet blockId can reference a workout block`() {
        val ws = WorkoutSet(
            id = "ws-2",
            workoutId = "workout-1",
            exerciseId = 42L,
            setOrder = 0,
            weight = 80.0,
            reps = 10,
            blockId = "wb-uuid-1"
        )
        assertEquals("wb-uuid-1", ws.blockId)
    }

    @Test
    fun `WorkoutSet existing fields unaffected by blockId addition`() {
        val ws = WorkoutSet(
            id = "ws-3",
            workoutId = "workout-1",
            exerciseId = 5L,
            setOrder = 1,
            weight = 100.0,
            reps = 5,
            rpe = 80,
            setType = SetType.NORMAL,
            supersetGroupId = "superset-xyz",
            isCompleted = true
        )
        assertEquals(100.0, ws.weight, 0.001)
        assertEquals(5, ws.reps)
        assertEquals(80, ws.rpe)
        assertEquals(SetType.NORMAL, ws.setType)
        assertEquals("superset-xyz", ws.supersetGroupId)
        assertTrue(ws.isCompleted)
        assertNull(ws.blockId)
    }
}
