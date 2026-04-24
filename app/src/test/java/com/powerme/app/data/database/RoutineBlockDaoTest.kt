package com.powerme.app.data.database

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for RoutineBlock entity and RoutineBlockDao contract.
 *
 * These tests cover:
 * - Entity field defaults and construction
 * - Block type constants expected by the migration
 * - Sync column contract (v35 pattern)
 */
class RoutineBlockDaoTest {

    // ── Entity construction tests ─────────────────────────────────────────────

    @Test
    fun `RoutineBlock STRENGTH type defaults are correct`() {
        val block = RoutineBlock(
            id = "block-1",
            routineId = "routine-1",
            order = 0,
            type = "STRENGTH"
        )
        assertEquals("block-1", block.id)
        assertEquals("routine-1", block.routineId)
        assertEquals(0, block.order)
        assertEquals("STRENGTH", block.type)
        assertNull(block.name)
        assertNull(block.durationSeconds)
        assertNull(block.targetRounds)
        assertNull(block.emomRoundSeconds)
        assertNull(block.tabataWorkSeconds)
        assertNull(block.tabataRestSeconds)
        assertNull(block.tabataSkipLastRest)
        assertNull(block.setupSecondsOverride)
        assertNull(block.warnAtSecondsOverride)
        assertEquals(0L, block.updatedAt)
    }

    @Test
    fun `RoutineBlock AMRAP type stores durationSeconds`() {
        val block = RoutineBlock(
            id = "block-2",
            routineId = "routine-1",
            order = 1,
            type = "AMRAP",
            name = "Metcon",
            durationSeconds = 720
        )
        assertEquals("AMRAP", block.type)
        assertEquals("Metcon", block.name)
        assertEquals(720, block.durationSeconds)
        assertNull(block.targetRounds)
    }

    @Test
    fun `RoutineBlock RFT type stores targetRounds and optional cap`() {
        val block = RoutineBlock(
            id = "block-3",
            routineId = "routine-1",
            order = 1,
            type = "RFT",
            targetRounds = 5,
            durationSeconds = 1500 // 25min cap
        )
        assertEquals("RFT", block.type)
        assertEquals(5, block.targetRounds)
        assertEquals(1500, block.durationSeconds)
    }

    @Test
    fun `RoutineBlock EMOM type stores emomRoundSeconds`() {
        val block = RoutineBlock(
            id = "block-4",
            routineId = "routine-1",
            order = 1,
            type = "EMOM",
            durationSeconds = 600,      // 10min total
            emomRoundSeconds = 60        // standard EMOM
        )
        assertEquals("EMOM", block.type)
        assertEquals(600, block.durationSeconds)
        assertEquals(60, block.emomRoundSeconds)
    }

    @Test
    fun `RoutineBlock TABATA type stores all tabata fields`() {
        val block = RoutineBlock(
            id = "block-5",
            routineId = "routine-1",
            order = 1,
            type = "TABATA",
            tabataWorkSeconds = 20,
            tabataRestSeconds = 10,
            tabataSkipLastRest = 0
        )
        assertEquals("TABATA", block.type)
        assertEquals(20, block.tabataWorkSeconds)
        assertEquals(10, block.tabataRestSeconds)
        assertEquals(0, block.tabataSkipLastRest)
    }

    @Test
    fun `RoutineBlock tabataSkipLastRest stores 1 for skip`() {
        val block = RoutineBlock(
            id = "block-6",
            routineId = "routine-1",
            order = 1,
            type = "TABATA",
            tabataWorkSeconds = 30,
            tabataRestSeconds = 15,
            tabataSkipLastRest = 1
        )
        assertEquals(1, block.tabataSkipLastRest)
    }

    @Test
    fun `RoutineBlock timer overrides are nullable by default`() {
        val block = RoutineBlock(
            id = "block-7",
            routineId = "routine-1",
            order = 0,
            type = "STRENGTH"
        )
        assertNull(block.setupSecondsOverride)
        assertNull(block.warnAtSecondsOverride)
    }

    @Test
    fun `RoutineBlock timer overrides can be set explicitly`() {
        val block = RoutineBlock(
            id = "block-8",
            routineId = "routine-1",
            order = 1,
            type = "AMRAP",
            durationSeconds = 480,
            setupSecondsOverride = 10,
            warnAtSecondsOverride = 30
        )
        assertEquals(10, block.setupSecondsOverride)
        assertEquals(30, block.warnAtSecondsOverride)
    }

    @Test
    fun `RoutineBlock syncId is non-empty by default`() {
        val block = RoutineBlock(
            id = "block-9",
            routineId = "routine-1",
            order = 0,
            type = "STRENGTH"
        )
        assertTrue("syncId should be non-empty UUID", block.syncId.isNotEmpty())
    }

    @Test
    fun `RoutineBlock updatedAt defaults to 0`() {
        val block = RoutineBlock(
            id = "block-10",
            routineId = "routine-1",
            order = 0,
            type = "STRENGTH"
        )
        assertEquals(0L, block.updatedAt)
    }
}
