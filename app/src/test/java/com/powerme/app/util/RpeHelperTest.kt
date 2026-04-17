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

    @Test
    fun rpeScale_hasExactlyNineEntries() {
        assertEquals(9, RPE_SCALE.size)
    }

    @Test
    fun rpeScale_entryCategoriesMatchRpeCategoryFunction() {
        RPE_SCALE.forEach { info ->
            assertEquals(
                "Category mismatch for value ${info.value}",
                rpeCategory(info.value),
                info.category
            )
        }
    }

    @Test
    fun rpeScale_valuesAreAscendingAndInExpectedRange() {
        val values = RPE_SCALE.map { it.value }
        assertEquals(listOf(60, 65, 70, 75, 80, 85, 90, 95, 100), values)
    }

    @Test
    fun displayLabel_returnsExpectedStringsForAllCategories() {
        assertEquals("WARM-UP ZONE",  RpeCategory.LOW.displayLabel())
        assertEquals("WORKING ZONE",  RpeCategory.MODERATE.displayLabel())
        assertEquals("GOLDEN ZONE \u2736", RpeCategory.GOLDEN.displayLabel())
        assertEquals("MAX EFFORT",    RpeCategory.MAX_EFFORT.displayLabel())
    }

    @Test
    fun rpeScale_allEntriesHaveNonBlankDisplayAndDescription() {
        RPE_SCALE.forEach { info ->
            assert(info.display.isNotBlank()) { "display blank for value ${info.value}" }
            assert(info.description.isNotBlank()) { "description blank for value ${info.value}" }
        }
    }
}
