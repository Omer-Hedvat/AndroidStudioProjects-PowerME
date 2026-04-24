package com.powerme.app.data.sync

import com.powerme.app.data.database.RoutineBlock
import com.powerme.app.data.database.WorkoutBlock
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Firestore block serialization / deserialization helpers
 * introduced in func_firestore_sync_blocks (P8 Tier 2).
 *
 * These tests cover:
 * - WorkoutBlock and RoutineBlock are serialized with the expected Firestore map keys
 * - Blocks are deserialized correctly from a raw map (simulating a pull response)
 * - Back-compat: legacy doc without `blocks` field produces null (handled by callers as no-op)
 * - Maps with missing `id` are skipped gracefully
 */
class FirestoreSyncBlocksTest {

    // ── WorkoutBlock serialization ────────────────────────────────────────────

    @Test
    fun `WorkoutBlock STRENGTH toFirestoreMap contains required fields`() {
        val block = WorkoutBlock(
            id = "wb-1",
            workoutId = "workout-1",
            order = 0,
            type = "STRENGTH",
            syncId = "sync-abc",
            updatedAt = 1_000L
        )
        val map = block.toFirestoreMapPublic()

        assertEquals("wb-1", map["id"])
        assertEquals("workout-1", map["workoutId"])
        assertEquals(0, map["order"])
        assertEquals("STRENGTH", map["type"])
        assertEquals("sync-abc", map["syncId"])
        assertEquals(1_000L, map["updatedAt"])
    }

    @Test
    fun `WorkoutBlock AMRAP toFirestoreMap includes optional result fields`() {
        val block = WorkoutBlock(
            id = "wb-2",
            workoutId = "workout-2",
            order = 1,
            type = "AMRAP",
            durationSeconds = 720,
            totalRounds = 8,
            extraReps = 3,
            rpe = 8,
            roundTapLogJson = "[{\"round\":1,\"elapsedMs\":45000}]",
            syncId = "sync-xyz",
            updatedAt = 2_000L
        )
        val map = block.toFirestoreMapPublic()

        assertEquals("AMRAP", map["type"])
        assertEquals(720, map["durationSeconds"])
        assertEquals(8, map["totalRounds"])
        assertEquals(3, map["extraReps"])
        assertEquals(8, map["rpe"])
        assertEquals("[{\"round\":1,\"elapsedMs\":45000}]", map["roundTapLogJson"])
    }

    @Test
    fun `WorkoutBlock null optional fields are absent from Firestore map`() {
        val block = WorkoutBlock(
            id = "wb-3",
            workoutId = "workout-3",
            order = 0,
            type = "STRENGTH"
        )
        val map = block.toFirestoreMapPublic()

        assertFalse(map.containsKey("name"))
        assertFalse(map.containsKey("durationSeconds"))
        assertFalse(map.containsKey("totalRounds"))
        assertFalse(map.containsKey("extraReps"))
        assertFalse(map.containsKey("rpe"))
        assertFalse(map.containsKey("blockNotes"))
    }

    @Test
    fun `WorkoutBlock TABATA toFirestoreMap includes tabata fields`() {
        val block = WorkoutBlock(
            id = "wb-4",
            workoutId = "workout-4",
            order = 2,
            type = "TABATA",
            tabataWorkSeconds = 20,
            tabataRestSeconds = 10,
            tabataSkipLastRest = 0,
            totalRounds = 8,
            finishTimeSeconds = 240
        )
        val map = block.toFirestoreMapPublic()

        assertEquals("TABATA", map["type"])
        assertEquals(20, map["tabataWorkSeconds"])
        assertEquals(10, map["tabataRestSeconds"])
        assertEquals(0, map["tabataSkipLastRest"])
        assertEquals(8, map["totalRounds"])
        assertEquals(240, map["finishTimeSeconds"])
    }

    // ── RoutineBlock serialization ────────────────────────────────────────────

    @Test
    fun `RoutineBlock STRENGTH toFirestoreMap contains required fields`() {
        val block = RoutineBlock(
            id = "rb-1",
            routineId = "routine-1",
            order = 0,
            type = "STRENGTH",
            syncId = "sync-rb",
            updatedAt = 500L
        )
        val map = block.toFirestoreMapPublic()

        assertEquals("rb-1", map["id"])
        assertEquals("routine-1", map["routineId"])
        assertEquals(0, map["order"])
        assertEquals("STRENGTH", map["type"])
        assertEquals("sync-rb", map["syncId"])
        assertEquals(500L, map["updatedAt"])
    }

    @Test
    fun `RoutineBlock AMRAP toFirestoreMap includes durationSeconds`() {
        val block = RoutineBlock(
            id = "rb-2",
            routineId = "routine-2",
            order = 1,
            type = "AMRAP",
            durationSeconds = 600,
            name = "Metcon"
        )
        val map = block.toFirestoreMapPublic()

        assertEquals("AMRAP", map["type"])
        assertEquals(600, map["durationSeconds"])
        assertEquals("Metcon", map["name"])
    }

    @Test
    fun `RoutineBlock null optional fields absent from Firestore map`() {
        val block = RoutineBlock(
            id = "rb-3",
            routineId = "routine-3",
            order = 0,
            type = "STRENGTH"
        )
        val map = block.toFirestoreMapPublic()

        assertFalse(map.containsKey("name"))
        assertFalse(map.containsKey("durationSeconds"))
        assertFalse(map.containsKey("targetRounds"))
    }

    // ── WorkoutBlock deserialization ──────────────────────────────────────────

    @Test
    fun `parseWorkoutBlockMaps reconstructs STRENGTH block correctly`() {
        val rawMaps = listOf(
            mapOf(
                "id" to "wb-1",
                "order" to 0L,
                "type" to "STRENGTH",
                "syncId" to "sync-a",
                "updatedAt" to 1000L
            )
        )
        val blocks = parseWorkoutBlockMaps("workout-1", rawMaps)

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("wb-1", block.id)
        assertEquals("workout-1", block.workoutId)
        assertEquals(0, block.order)
        assertEquals("STRENGTH", block.type)
        assertEquals("sync-a", block.syncId)
        assertEquals(1000L, block.updatedAt)
    }

    @Test
    fun `parseWorkoutBlockMaps reads AMRAP optional fields`() {
        val rawMaps = listOf(
            mapOf(
                "id" to "wb-2",
                "order" to 1L,
                "type" to "AMRAP",
                "durationSeconds" to 720L,
                "totalRounds" to 8L,
                "extraReps" to 3L,
                "rpe" to 8L,
                "syncId" to "sync-b",
                "updatedAt" to 2000L
            )
        )
        val blocks = parseWorkoutBlockMaps("workout-2", rawMaps)

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("AMRAP", block.type)
        assertEquals(720, block.durationSeconds)
        assertEquals(8, block.totalRounds)
        assertEquals(3, block.extraReps)
        assertEquals(8, block.rpe)
    }

    @Test
    fun `parseWorkoutBlockMaps skips entry missing id field`() {
        val rawMaps = listOf(
            mapOf("order" to 0L, "type" to "STRENGTH"),  // missing "id"
            mapOf("id" to "wb-2", "order" to 1L, "type" to "STRENGTH", "syncId" to "", "updatedAt" to 0L)
        )
        val blocks = parseWorkoutBlockMaps("workout-x", rawMaps)

        assertEquals(1, blocks.size)
        assertEquals("wb-2", blocks[0].id)
    }

    @Test
    fun `parseWorkoutBlockMaps returns empty list for empty input`() {
        val blocks = parseWorkoutBlockMaps("workout-y", emptyList())
        assertTrue(blocks.isEmpty())
    }

    // ── RoutineBlock deserialization ──────────────────────────────────────────

    @Test
    fun `parseRoutineBlockMaps reconstructs STRENGTH block correctly`() {
        val rawMaps = listOf(
            mapOf(
                "id" to "rb-1",
                "order" to 0L,
                "type" to "STRENGTH",
                "syncId" to "sync-r",
                "updatedAt" to 500L
            )
        )
        val blocks = parseRoutineBlockMaps("routine-1", rawMaps)

        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals("rb-1", block.id)
        assertEquals("routine-1", block.routineId)
        assertEquals(0, block.order)
        assertEquals("STRENGTH", block.type)
        assertEquals("sync-r", block.syncId)
        assertEquals(500L, block.updatedAt)
    }

    @Test
    fun `parseRoutineBlockMaps reads RFT optional fields`() {
        val rawMaps = listOf(
            mapOf(
                "id" to "rb-2",
                "order" to 1L,
                "type" to "RFT",
                "targetRounds" to 5L,
                "durationSeconds" to 900L,
                "syncId" to "",
                "updatedAt" to 0L
            )
        )
        val blocks = parseRoutineBlockMaps("routine-2", rawMaps)

        assertEquals(1, blocks.size)
        assertEquals("RFT", blocks[0].type)
        assertEquals(5, blocks[0].targetRounds)
        assertEquals(900, blocks[0].durationSeconds)
    }

    @Test
    fun `parseRoutineBlockMaps skips entry missing id field`() {
        val rawMaps = listOf(
            mapOf("order" to 0L, "type" to "STRENGTH"),  // missing "id"
            mapOf("id" to "rb-ok", "order" to 1L, "type" to "EMOM", "syncId" to "", "updatedAt" to 0L)
        )
        val blocks = parseRoutineBlockMaps("routine-z", rawMaps)

        assertEquals(1, blocks.size)
        assertEquals("rb-ok", blocks[0].id)
    }

    // ── Back-compat: legacy doc handling (null = no blocks field) ─────────────

    @Test
    fun `parseWorkoutBlockMaps called with empty list returns empty — no crash`() {
        // Simulates pull with blocks=[] (present but empty) — not null (absent)
        val blocks = parseWorkoutBlockMaps("workout-legacy", emptyList())
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `parseRoutineBlockMaps called with empty list returns empty — no crash`() {
        val blocks = parseRoutineBlockMaps("routine-legacy", emptyList())
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `parseWorkoutBlockMaps with multiple blocks preserves order field`() {
        val rawMaps = listOf(
            mapOf("id" to "wb-a", "order" to 0L, "type" to "STRENGTH", "syncId" to "", "updatedAt" to 0L),
            mapOf("id" to "wb-b", "order" to 1L, "type" to "AMRAP", "syncId" to "", "updatedAt" to 0L)
        )
        val blocks = parseWorkoutBlockMaps("workout-multi", rawMaps)

        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].order)
        assertEquals(1, blocks[1].order)
        assertEquals("STRENGTH", blocks[0].type)
        assertEquals("AMRAP", blocks[1].type)
    }

    @Test
    fun `WorkoutBlock toFirestoreMap round-trip through parseWorkoutBlockMaps`() {
        val original = WorkoutBlock(
            id = "wb-rt",
            workoutId = "workout-rt",
            order = 0,
            type = "EMOM",
            emomRoundSeconds = 60,
            durationSeconds = 1200,
            totalRounds = 20,
            syncId = "sync-rt",
            updatedAt = 9999L
        )
        val map = original.toFirestoreMapPublic()
        // Firestore returns Long for numbers; convert Int values to Long to simulate the round-trip
        val roundTrippedMap = map.mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        val blocks = parseWorkoutBlockMaps("workout-rt", listOf(roundTrippedMap))

        assertEquals(1, blocks.size)
        val restored = blocks[0]
        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
        assertEquals(original.emomRoundSeconds, restored.emomRoundSeconds)
        assertEquals(original.durationSeconds, restored.durationSeconds)
        assertEquals(original.totalRounds, restored.totalRounds)
        assertEquals(original.syncId, restored.syncId)
        assertEquals(original.updatedAt, restored.updatedAt)
    }
}
