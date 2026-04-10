package com.powerme.app.data.repository

import com.powerme.app.data.database.SetType
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.floor

/**
 * Unit tests for RoutineRepository business logic.
 *
 * The repository's `withTransaction` wrappers delegate to Room and cannot be unit-tested
 * without a real DB. These tests cover the pure algorithms — Express drop-count math,
 * JSON truncation, and naming conventions — which are the source of correctness bugs.
 */
class RoutineRepositoryTest {

    // ── Express algorithm: drop count ──────────────────────────────────────────

    @Test
    fun `express drop count - 5 exercises drops 2 (floor 5 x 0_4 = 2)`() {
        assertEquals(2, floor(5 * 0.4).toInt())
    }

    @Test
    fun `express drop count - 3 exercises drops 1 (floor 3 x 0_4 = 1)`() {
        assertEquals(1, floor(3 * 0.4).toInt())
    }

    @Test
    fun `express drop count - 2 exercises drops 0 (floor 2 x 0_4 = 0)`() {
        assertEquals(0, floor(2 * 0.4).toInt())
    }

    @Test
    fun `express drop count - 1 exercise drops 0 (floor 1 x 0_4 = 0)`() {
        assertEquals(0, floor(1 * 0.4).toInt())
    }

    @Test
    fun `express drop count - 0 exercises drops 0`() {
        assertEquals(0, floor(0 * 0.4).toInt())
    }

    @Test
    fun `express drop count - 10 exercises drops 4`() {
        assertEquals(4, floor(10 * 0.4).toInt())
    }

    // ── truncateJsonList ────────────────────────────────────────────────────────

    @Test
    fun `truncateJsonList - blank string returns blank`() {
        assertEquals("", "".truncateJsonList(2))
    }

    @Test
    fun `truncateJsonList - takes only first N entries`() {
        assertEquals("WARMUP,NORMAL", "WARMUP,NORMAL,NORMAL,NORMAL".truncateJsonList(2))
    }

    @Test
    fun `truncateJsonList - takes all when maxCount exceeds length`() {
        assertEquals("NORMAL,NORMAL", "NORMAL,NORMAL".truncateJsonList(5))
    }

    @Test
    fun `truncateJsonList - maxCount 0 returns empty string`() {
        assertEquals("", "NORMAL,NORMAL".truncateJsonList(0))
    }

    @Test
    fun `truncateJsonList - single entry maxCount 1 returns that entry`() {
        assertEquals("NORMAL", "NORMAL,DROP,FAILURE".truncateJsonList(1))
    }

    @Test
    fun `truncateJsonList - numeric weights truncated correctly`() {
        assertEquals("80,85", "80,85,90,95".truncateJsonList(2))
    }

    // ── Naming conventions ─────────────────────────────────────────────────────

    @Test
    fun `duplicate name appends (Copy) suffix`() {
        val originalName = "Push Day A"
        val duplicateName = "$originalName (Copy)"
        assertEquals("Push Day A (Copy)", duplicateName)
    }

    @Test
    fun `express name appends - Express suffix`() {
        val originalName = "Pull Day"
        val expressName = "$originalName - Express"
        assertEquals("Pull Day - Express", expressName)
    }

    // ── Express surviving count ────────────────────────────────────────────────

    @Test
    fun `express - 5 exercises yields 3 survivors`() {
        val exercises = List(5) { it }
        val dropCount = floor(exercises.size * 0.4).toInt()
        val surviving = exercises.dropLast(dropCount)
        assertEquals(3, surviving.size)
    }

    @Test
    fun `express - 1 exercise yields 1 survivor`() {
        val exercises = List(1) { it }
        val dropCount = floor(exercises.size * 0.4).toInt()
        val surviving = exercises.dropLast(dropCount)
        assertEquals(1, surviving.size)
    }

    @Test
    fun `express - 0 exercises yields 0 survivors`() {
        val exercises = emptyList<Int>()
        val dropCount = floor(exercises.size * 0.4).toInt()
        val surviving = exercises.dropLast(dropCount)
        assertEquals(0, surviving.size)
    }

    // ── Sets cap ───────────────────────────────────────────────────────────────

    @Test
    fun `express - sets capped at 2 when original is 5`() {
        assertEquals(2, 5.coerceAtMost(2))
    }

    @Test
    fun `express - sets unchanged at 1 when original is 1`() {
        assertEquals(1, 1.coerceAtMost(2))
    }

    @Test
    fun `express - sets unchanged at 2 when original is 2`() {
        assertEquals(2, 2.coerceAtMost(2))
    }

    // ── selectExpressIndices — warmup preservation ─────────────────────────────

    @Test
    fun `selectExpressIndices - all NORMAL keeps first 2`() {
        val indices = selectExpressIndices("NORMAL,NORMAL,NORMAL,NORMAL", 4)
        assertEquals(listOf(0, 1), indices)
    }

    @Test
    fun `selectExpressIndices - 2 warmups + 3 work keeps all warmups and first 2 work sets`() {
        val indices = selectExpressIndices("WARMUP,WARMUP,NORMAL,NORMAL,NORMAL", 5)
        // warmup at 0,1 + first 2 work at 2,3 → [0,1,2,3]
        assertEquals(listOf(0, 1, 2, 3), indices)
    }

    @Test
    fun `selectExpressIndices - 2 warmups + 1 work keeps all`() {
        val indices = selectExpressIndices("WARMUP,WARMUP,NORMAL", 3)
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `selectExpressIndices - all warmup keeps all warmup sets`() {
        val indices = selectExpressIndices("WARMUP,WARMUP,WARMUP", 3)
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `selectExpressIndices - blank setTypesJson treats all as NORMAL and caps to 2`() {
        val indices = selectExpressIndices("", 4)
        assertEquals(listOf(0, 1), indices)
    }

    @Test
    fun `selectExpressIndices - DROP sets count as work sets`() {
        val indices = selectExpressIndices("WARMUP,NORMAL,DROP,NORMAL,DROP", 5)
        // warmup at 0, first 2 work (NORMAL at 1, DROP at 2) → [0,1,2]
        assertEquals(listOf(0, 1, 2), indices)
    }

    // ── pickIndices ─────────────────────────────────────────────────────────────

    @Test
    fun `pickIndices selects entries at given indices`() {
        assertEquals("WARMUP,NORMAL", "WARMUP,WARMUP,NORMAL,NORMAL".pickIndices(listOf(0, 2)))
    }

    @Test
    fun `pickIndices blank string returns blank`() {
        assertEquals("", "".pickIndices(listOf(0, 1)))
    }

    @Test
    fun `pickIndices empty indices returns blank`() {
        assertEquals("", "NORMAL,NORMAL".pickIndices(emptyList()))
    }
}
