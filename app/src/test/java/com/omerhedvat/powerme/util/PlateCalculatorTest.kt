package com.omerhedvat.powerme.util

import com.omerhedvat.powerme.data.database.BarType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    // --- calculatePlates ---

    @Test
    fun calculatePlates_totalLessThanBar_returnsNull() {
        // STANDARD bar is 20kg; 15kg total → negative per side
        val result = PlateCalculator.calculatePlates(15.0, BarType.STANDARD, listOf(20.0, 10.0, 5.0))
        assertNull(result)
    }

    @Test
    fun calculatePlates_exactBarWeight_returnsBarOnly() {
        val result = PlateCalculator.calculatePlates(20.0, BarType.STANDARD, listOf(20.0, 10.0, 5.0))
        assertNotNull(result)
        assertTrue(result!!.platesPerSide.isEmpty())
        assertEquals(20.0, result.totalWeight, 0.001)
        assertEquals(0.0, result.error, 0.001)
    }

    @Test
    fun calculatePlates_standardBench100kg_twoTwenties() {
        // weightPerSide = (100-20)/2 = 40; greedy with [20,10,5,2.5,1.25] → 2x20
        val result = PlateCalculator.calculatePlates(
            totalWeight = 100.0,
            barType = BarType.STANDARD,
            availablePlates = listOf(20.0, 10.0, 5.0, 2.5, 1.25)
        )
        assertNotNull(result)
        assertEquals(100.0, result!!.totalWeight, 0.001)
        assertEquals(0.0, result.error, 0.001)
        assertEquals(2, result.platesPerSide.count { it == 20.0 })
        assertEquals(2, result.platesPerSide.size)
    }

    @Test
    fun calculatePlates_fractionalTarget_correctCombo() {
        // weightPerSide = (65-20)/2 = 22.5; greedy [20,10,5,2.5] → 20+2.5
        val result = PlateCalculator.calculatePlates(
            totalWeight = 65.0,
            barType = BarType.STANDARD,
            availablePlates = listOf(20.0, 10.0, 5.0, 2.5)
        )
        assertNotNull(result)
        assertEquals(65.0, result!!.totalWeight, 0.001)
        assertEquals(0.0, result.error, 0.001)
        assertTrue(result.platesPerSide.contains(20.0))
        assertTrue(result.platesPerSide.contains(2.5))
    }

    @Test
    fun calculatePlates_unachievableWeight_returnsBreakdownWithError() {
        // total=101, bar=20, per side=40.5; only [20,10,5] → 2x20=40/side, actualTotal=100, error=1.0
        val result = PlateCalculator.calculatePlates(
            totalWeight = 101.0,
            barType = BarType.STANDARD,
            availablePlates = listOf(20.0, 10.0, 5.0)
        )
        assertNotNull(result)
        assertTrue(result!!.error > 0.5)
    }

    @Test
    fun calculatePlates_womensBar_correctResult() {
        // WOMENS bar = 15kg; total=55 → weightPerSide=20; plates=[20] → 1x20
        val result = PlateCalculator.calculatePlates(
            totalWeight = 55.0,
            barType = BarType.WOMENS,
            availablePlates = listOf(20.0)
        )
        assertNotNull(result)
        assertEquals(55.0, result!!.totalWeight, 0.001)
        assertEquals(0.0, result.error, 0.001)
        assertEquals(1, result.platesPerSide.size)
        assertEquals(20.0, result.platesPerSide.first(), 0.0)
    }

    // --- parseAvailablePlates ---

    @Test
    fun parseAvailablePlates_standardString_parsedCorrectly() {
        val result = PlateCalculator.parseAvailablePlates("0.5,1.25,2.5,5,10,20")
        assertEquals(6, result.size)
        assertTrue(result.containsAll(listOf(0.5, 1.25, 2.5, 5.0, 10.0, 20.0)))
    }

    @Test
    fun parseAvailablePlates_emptyString_returnsEmptyList() {
        val result = PlateCalculator.parseAvailablePlates("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseAvailablePlates_invalidEntries_skipped() {
        val result = PlateCalculator.parseAvailablePlates("5,abc,10,")
        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(5.0, 10.0)))
    }

    @Test
    fun parseAvailablePlates_duplicates_deduplicated() {
        val result = PlateCalculator.parseAvailablePlates("5,5,10")
        assertEquals(2, result.size)
        assertTrue(result.containsAll(listOf(5.0, 10.0)))
    }

    // --- formatPlateBreakdown ---

    @Test
    fun formatPlateBreakdown_emptyPlates_returnsBarOnly() {
        val breakdown = PlateCalculator.PlateBreakdown(
            platesPerSide = emptyList(),
            weightPerSide = 0.0,
            totalWeight = 20.0
        )
        assertEquals("Bar only", PlateCalculator.formatPlateBreakdown(breakdown))
    }

    @Test
    fun formatPlateBreakdown_twoTwentiesAndOne5_correctFormat() {
        val breakdown = PlateCalculator.PlateBreakdown(
            platesPerSide = listOf(20.0, 20.0, 5.0),
            weightPerSide = 45.0,
            totalWeight = 110.0
        )
        assertEquals("2x20kg + 1x5kg", PlateCalculator.formatPlateBreakdown(breakdown))
    }

    @Test
    fun formatPlateBreakdown_fractionalPlate_retainsDecimal() {
        val breakdown = PlateCalculator.PlateBreakdown(
            platesPerSide = listOf(2.5),
            weightPerSide = 2.5,
            totalWeight = 25.0
        )
        assertEquals("1x2.5kg", PlateCalculator.formatPlateBreakdown(breakdown))
    }
}
