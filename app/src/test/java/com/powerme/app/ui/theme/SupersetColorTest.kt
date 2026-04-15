package com.powerme.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetColorTest {

    @Test
    fun `empty list returns empty map`() {
        val map = buildSupersetColorMap(emptyList())
        assertTrue(map.isEmpty())
    }

    @Test
    fun `null entries are excluded`() {
        val map = buildSupersetColorMap(listOf(null, "A", null))
        assertEquals(1, map.size)
        assertEquals(SupersetPalette[0], map["A"])
    }

    @Test
    fun `colors assigned by insertion order`() {
        val map = buildSupersetColorMap(listOf("X", "Y", "Z"))
        assertEquals(SupersetPalette[0], map["X"])
        assertEquals(SupersetPalette[1], map["Y"])
        assertEquals(SupersetPalette[2], map["Z"])
    }

    @Test
    fun `duplicate group IDs count as one entry`() {
        val map = buildSupersetColorMap(listOf("A", "B", "A", "B"))
        assertEquals(2, map.size)
        assertEquals(SupersetPalette[0], map["A"])
        assertEquals(SupersetPalette[1], map["B"])
    }

    @Test
    fun `no two groups share a color when count is within palette size`() {
        val ids = (1..SupersetPalette.size).map { "group$it" }
        val map = buildSupersetColorMap(ids)
        assertEquals(ids.size, map.values.toSet().size)
    }

    @Test
    fun `wraps around palette for more groups than palette size`() {
        val extraCount = SupersetPalette.size + 2
        val ids = (1..extraCount).map { "group$it" }
        val map = buildSupersetColorMap(ids)
        assertEquals(extraCount, map.size)
        // Group at index SupersetPalette.size wraps to palette index 0
        assertEquals(SupersetPalette[0], map["group${SupersetPalette.size + 1}"])
    }

    @Test
    fun `two different group IDs always receive different colors within palette size`() {
        val map = buildSupersetColorMap(listOf("alpha", "beta"))
        assertNotEquals(map["alpha"], map["beta"])
    }
}
