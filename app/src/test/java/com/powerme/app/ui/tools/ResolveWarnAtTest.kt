package com.powerme.app.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveWarnAtTest {

    // ── Auto mode (blank text) ──────────────────────────────────────────

    @Test
    fun `auto mode - duration 60 - returns 30`() {
        assertEquals(30, resolveWarnAt("", 60))
    }

    @Test
    fun `auto mode - duration 8 - returns 4`() {
        assertEquals(4, resolveWarnAt("", 8))
    }

    @Test
    fun `auto mode - duration 7 - floor is 3 suppressed`() {
        assertNull(resolveWarnAt("", 7))
    }

    @Test
    fun `auto mode - duration 6 - floor is 3 suppressed`() {
        assertNull(resolveWarnAt("", 6))
    }

    @Test
    fun `auto mode - duration 0 - returns null`() {
        assertNull(resolveWarnAt("", 0))
    }

    @Test
    fun `auto mode - negative duration - returns null`() {
        assertNull(resolveWarnAt("", -1))
    }

    @Test
    fun `auto mode - whitespace text treated as blank`() {
        assertEquals(30, resolveWarnAt("  ", 60))
    }

    // ── Manual mode (non-blank text) ────────────────────────────────────

    @Test
    fun `manual - valid value within range`() {
        assertEquals(10, resolveWarnAt("10", 60))
    }

    @Test
    fun `manual - value 3 valid when duration is 10`() {
        assertEquals(3, resolveWarnAt("3", 10))
    }

    @Test
    fun `manual - value equals duration disabled`() {
        assertNull(resolveWarnAt("60", 60))
    }

    @Test
    fun `manual - value exceeds duration disabled`() {
        assertNull(resolveWarnAt("61", 60))
    }

    @Test
    fun `manual - value zero disabled`() {
        assertNull(resolveWarnAt("0", 60))
    }

    @Test
    fun `manual - non-numeric text returns null`() {
        assertNull(resolveWarnAt("abc", 60))
    }
}
