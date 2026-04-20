package com.powerme.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the ±1 keyboard accessory bar delta logic.
 * Tests [applyAccessoryDelta] directly — no Compose, no ViewModel needed.
 */
class KeyboardAccessoryTest {

    // ── Reps (isInteger = true) ────────────────────────────────────────────────

    @Test
    fun `reps increment adds 1`() {
        assertEquals("6", applyAccessoryDelta("5", 1.0, isInteger = true))
    }

    @Test
    fun `reps decrement subtracts 1`() {
        assertEquals("4", applyAccessoryDelta("5", -1.0, isInteger = true))
    }

    @Test
    fun `reps decrement at 1 goes to 0`() {
        assertEquals("0", applyAccessoryDelta("1", -1.0, isInteger = true))
    }

    @Test
    fun `reps decrement at 0 stays at 0`() {
        assertEquals("0", applyAccessoryDelta("0", -1.0, isInteger = true))
    }

    @Test
    fun `reps increment on empty starts at 1`() {
        assertEquals("1", applyAccessoryDelta("", 1.0, isInteger = true))
    }

    @Test
    fun `reps decrement on empty returns null`() {
        assertNull(applyAccessoryDelta("", -1.0, isInteger = true))
    }

    @Test
    fun `reps decrement on invalid input returns null`() {
        assertNull(applyAccessoryDelta("abc", -1.0, isInteger = true))
    }

    @Test
    fun `reps increment on invalid input returns null`() {
        assertNull(applyAccessoryDelta("xyz", 1.0, isInteger = true))
    }

    @Test
    fun `reps increment handles large value`() {
        assertEquals("100", applyAccessoryDelta("99", 1.0, isInteger = true))
    }

    // ── Weight (isInteger = false, decimal) ───────────────────────────────────

    @Test
    fun `weight increment adds step as whole number`() {
        assertEquals("81", applyAccessoryDelta("80", 1.0, isInteger = false))
    }

    @Test
    fun `weight decrement subtracts step`() {
        assertEquals("79", applyAccessoryDelta("80", -1.0, isInteger = false))
    }

    @Test
    fun `weight increment on decimal value preserves precision`() {
        assertEquals("81.5", applyAccessoryDelta("80.5", 1.0, isInteger = false))
    }

    @Test
    fun `weight decrement at 0 stays at 0`() {
        assertEquals("0", applyAccessoryDelta("0", -1.0, isInteger = false))
    }

    @Test
    fun `weight decrement below 0 clamps to 0`() {
        assertEquals("0", applyAccessoryDelta("0.5", -1.0, isInteger = false))
    }

    @Test
    fun `weight increment on empty starts at step value`() {
        assertEquals("1", applyAccessoryDelta("", 1.0, isInteger = false))
    }

    @Test
    fun `weight decrement on empty returns null`() {
        assertNull(applyAccessoryDelta("", -1.0, isInteger = false))
    }

    @Test
    fun `weight decrement on invalid input returns null`() {
        assertNull(applyAccessoryDelta("--", -1.0, isInteger = false))
    }

    @Test
    fun `weight increment with decimal step formats correctly`() {
        // e.g. 2.5 lb step: 100 + 2.5 = 102.5
        assertEquals("102.5", applyAccessoryDelta("100", 2.5, isInteger = false))
    }

    @Test
    fun `weight result strips unnecessary decimal when whole`() {
        // 100.5 - 0.5 = 100.0 → should format as "100" not "100.0"
        assertEquals("100", applyAccessoryDelta("100.5", -0.5, isInteger = false))
    }

    @Test
    fun `weight comma separator is accepted`() {
        // Locale-aware parsing: "80,5" → 80.5 + 1.0 = 81.5
        assertEquals("81.5", applyAccessoryDelta("80,5", 1.0, isInteger = false))
    }
}
