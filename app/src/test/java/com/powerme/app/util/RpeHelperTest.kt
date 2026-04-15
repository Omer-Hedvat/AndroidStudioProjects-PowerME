package com.powerme.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RpeHelperTest {

    @Test
    fun rpeCategory_below70_returnsLow() {
        assertEquals(RpeCategory.LOW, rpeCategory(60))
        assertEquals(RpeCategory.LOW, rpeCategory(65))
        assertEquals(RpeCategory.LOW, rpeCategory(69))
    }

    @Test
    fun rpeCategory_70to79_returnsModerate() {
        assertEquals(RpeCategory.MODERATE, rpeCategory(70))
        assertEquals(RpeCategory.MODERATE, rpeCategory(75))
        assertEquals(RpeCategory.MODERATE, rpeCategory(79))
    }

    @Test
    fun rpeCategory_80to90_returnsGolden() {
        assertEquals(RpeCategory.GOLDEN, rpeCategory(80))
        assertEquals(RpeCategory.GOLDEN, rpeCategory(85))
        assertEquals(RpeCategory.GOLDEN, rpeCategory(90))
    }

    @Test
    fun rpeCategory_above90_returnsMaxEffort() {
        assertEquals(RpeCategory.MAX_EFFORT, rpeCategory(91))
        assertEquals(RpeCategory.MAX_EFFORT, rpeCategory(95))
        assertEquals(RpeCategory.MAX_EFFORT, rpeCategory(100))
    }

    @Test
    fun rpeCategory_boundaryValues() {
        assertEquals(RpeCategory.LOW,        rpeCategory(69))
        assertEquals(RpeCategory.MODERATE,   rpeCategory(70))
        assertEquals(RpeCategory.MODERATE,   rpeCategory(79))
        assertEquals(RpeCategory.GOLDEN,     rpeCategory(80))
        assertEquals(RpeCategory.GOLDEN,     rpeCategory(90))
        assertEquals(RpeCategory.MAX_EFFORT, rpeCategory(91))
    }
}
