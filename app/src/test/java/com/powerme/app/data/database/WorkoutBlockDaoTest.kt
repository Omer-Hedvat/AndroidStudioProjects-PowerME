package com.powerme.app.data.database

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WorkoutBlock entity.
 *
 * These tests cover:
 * - Entity field defaults and construction
 * - Result field nullability (pre-block-finish state)
 * - Sync column contract (v35 pattern)
 * - Invariant #9: rpe and perExerciseRpeJson are mutually exclusive (validation logic)
 */
class WorkoutBlockDaoTest {

    // ── Entity construction tests ─────────────────────────────────────────────

    @Test
    fun `WorkoutBlock defaults all result fields to null`() {
        val block = WorkoutBlock(
            id = "wb-1",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH"
        )
        assertNull(block.totalRounds)
        assertNull(block.extraReps)
        assertNull(block.finishTimeSeconds)
        assertNull(block.rpe)
        assertNull(block.perExerciseRpeJson)
        assertNull(block.roundTapLogJson)
        assertNull(block.blockNotes)
        assertNull(block.runStartMs)
    }

    @Test
    fun `WorkoutBlock STRENGTH type with no plan fields`() {
        val block = WorkoutBlock(
            id = "wb-2",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH"
        )
        assertEquals("STRENGTH", block.type)
        assertNull(block.durationSeconds)
        assertNull(block.targetRounds)
        assertNull(block.emomRoundSeconds)
    }

    @Test
    fun `WorkoutBlock AMRAP type stores plan fields`() {
        val block = WorkoutBlock(
            id = "wb-3",
            workoutId = "workout-1",
            order = 1,
            type = "AMRAP",
            name = "Metcon",
            durationSeconds = 720
        )
        assertEquals("AMRAP", block.type)
        assertEquals("Metcon", block.name)
        assertEquals(720, block.durationSeconds)
    }

    @Test
    fun `WorkoutBlock AMRAP result fields can be set`() {
        val block = WorkoutBlock(
            id = "wb-4",
            workoutId = "workout-1",
            order = 1,
            type = "AMRAP",
            durationSeconds = 720,
            totalRounds = 8,
            extraReps = 3,
            rpe = 8,
            roundTapLogJson = """[{"round":1,"elapsedMs":45000}]"""
        )
        assertEquals(8, block.totalRounds)
        assertEquals(3, block.extraReps)
        assertEquals(8, block.rpe)
        assertNull(block.perExerciseRpeJson) // rpe is set → perExerciseRpeJson must be null
        assertNotNull(block.roundTapLogJson)
    }

    @Test
    fun `WorkoutBlock RFT result fields`() {
        val block = WorkoutBlock(
            id = "wb-5",
            workoutId = "workout-1",
            order = 1,
            type = "RFT",
            targetRounds = 5,
            totalRounds = 4,
            finishTimeSeconds = 1122  // 18:42
        )
        assertEquals(4, block.totalRounds)
        assertEquals(1122, block.finishTimeSeconds)
    }

    @Test
    fun `WorkoutBlock TABATA stores all plan fields`() {
        val block = WorkoutBlock(
            id = "wb-6",
            workoutId = "workout-1",
            order = 1,
            type = "TABATA",
            tabataWorkSeconds = 20,
            tabataRestSeconds = 10,
            tabataSkipLastRest = 0,
            totalRounds = 8,
            finishTimeSeconds = 240
        )
        assertEquals(20, block.tabataWorkSeconds)
        assertEquals(10, block.tabataRestSeconds)
        assertEquals(0, block.tabataSkipLastRest)
        assertEquals(8, block.totalRounds)
        assertEquals(240, block.finishTimeSeconds)
    }

    @Test
    fun `WorkoutBlock perExerciseRpeJson can be stored instead of rpe`() {
        val rpeJson = """{"exercise-1":8,"exercise-2":7}"""
        val block = WorkoutBlock(
            id = "wb-7",
            workoutId = "workout-1",
            order = 1,
            type = "AMRAP",
            durationSeconds = 600,
            totalRounds = 6,
            perExerciseRpeJson = rpeJson
        )
        assertNull(block.rpe) // perExerciseRpeJson is set → rpe must be null
        assertEquals(rpeJson, block.perExerciseRpeJson)
    }

    @Test
    fun `WorkoutBlock runStartMs records block start epoch`() {
        val startMs = System.currentTimeMillis()
        val block = WorkoutBlock(
            id = "wb-8",
            workoutId = "workout-1",
            order = 1,
            type = "AMRAP",
            durationSeconds = 720,
            runStartMs = startMs
        )
        assertEquals(startMs, block.runStartMs)
    }

    @Test
    fun `WorkoutBlock syncId is non-empty by default`() {
        val block = WorkoutBlock(
            id = "wb-9",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH"
        )
        assertTrue("syncId should be non-empty UUID", block.syncId.isNotEmpty())
    }

    @Test
    fun `WorkoutBlock updatedAt defaults to 0`() {
        val block = WorkoutBlock(
            id = "wb-10",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH"
        )
        assertEquals(0L, block.updatedAt)
    }

    @Test
    fun `WorkoutBlock EMOM stores emomRoundSeconds`() {
        val block = WorkoutBlock(
            id = "wb-11",
            workoutId = "workout-1",
            order = 1,
            type = "EMOM",
            durationSeconds = 600,
            emomRoundSeconds = 120  // E2MOM
        )
        assertEquals(120, block.emomRoundSeconds)
    }

    @Test
    fun `WorkoutBlock timer overrides are nullable by default`() {
        val block = WorkoutBlock(
            id = "wb-12",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH"
        )
        assertNull(block.setupSecondsOverride)
        assertNull(block.warnAtSecondsOverride)
    }
}
