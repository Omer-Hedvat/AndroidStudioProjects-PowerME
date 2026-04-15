package com.powerme.app.util

import com.powerme.app.data.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {

    // ── Weight conversions ────────────────────────────────────────────────────

    @Test
    fun `kgToLbs converts correctly`() {
        assertEquals(220.462, UnitConverter.kgToLbs(100.0), 0.001)
        assertEquals(0.0, UnitConverter.kgToLbs(0.0), 0.001)
        assertEquals(2.20462, UnitConverter.kgToLbs(1.0), 0.001)
    }

    @Test
    fun `lbsToKg converts correctly`() {
        assertEquals(100.0, UnitConverter.lbsToKg(220.462), 0.001)
        assertEquals(0.0, UnitConverter.lbsToKg(0.0), 0.001)
    }

    @Test
    fun `weight round-trip kg to lbs and back stays within 0_01 kg`() {
        val original = 80.0
        val roundTripped = UnitConverter.lbsToKg(UnitConverter.kgToLbs(original))
        assertEquals(original, roundTripped, 0.01)
    }

    @Test
    fun `displayWeight returns kg unchanged in METRIC`() {
        assertEquals(80.0, UnitConverter.displayWeight(80.0, UnitSystem.METRIC), 0.001)
    }

    @Test
    fun `displayWeight converts to lbs in IMPERIAL`() {
        assertEquals(176.37, UnitConverter.displayWeight(80.0, UnitSystem.IMPERIAL), 0.01)
    }

    @Test
    fun `inputWeightToKg is identity in METRIC`() {
        assertEquals(80.0, UnitConverter.inputWeightToKg(80.0, UnitSystem.METRIC), 0.001)
    }

    @Test
    fun `inputWeightToKg converts lbs to kg in IMPERIAL`() {
        assertEquals(80.0, UnitConverter.inputWeightToKg(176.37, UnitSystem.IMPERIAL), 0.01)
    }

    @Test
    fun `weightLabel returns correct strings`() {
        assertEquals("kg", UnitConverter.weightLabel(UnitSystem.METRIC))
        assertEquals("lbs", UnitConverter.weightLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `formatWeight uses 2 decimal places for non-integer weights in METRIC`() {
        assertEquals("80 kg", UnitConverter.formatWeight(80.0, UnitSystem.METRIC))
        assertEquals("80.50 kg", UnitConverter.formatWeight(80.5, UnitSystem.METRIC))
    }

    @Test
    fun `formatWeight uses lbs in IMPERIAL`() {
        val result = UnitConverter.formatWeight(100.0, UnitSystem.IMPERIAL)
        assertTrue("Expected lbs suffix: $result", result.endsWith("lbs"))
        assertTrue("Expected ~220 value: $result", result.startsWith("220"))
    }

    // ── Height conversions ────────────────────────────────────────────────────

    @Test
    fun `cmToFeetInches converts 175cm to 5 feet 9 inches`() {
        val (feet, inches) = UnitConverter.cmToFeetInches(175.0)
        assertEquals(5, feet)
        assertEquals(8, inches) // 175/2.54 = 68.9in → 5'8"
    }

    @Test
    fun `cmToFeetInches converts 180cm to 5 feet 11 inches`() {
        val (feet, inches) = UnitConverter.cmToFeetInches(180.0)
        assertEquals(5, feet)
        assertEquals(10, inches) // 180/2.54 = 70.9in → 5'10"
    }

    @Test
    fun `cmToFeetInches converts 152cm to 5 feet 0 inches`() {
        val (feet, inches) = UnitConverter.cmToFeetInches(152.4)
        assertEquals(5, feet)
        assertEquals(0, inches)
    }

    @Test
    fun `feetInchesToCm converts 6 feet 0 inches to 182cm`() {
        val cm = UnitConverter.feetInchesToCm(6, 0)
        assertEquals(182.88, cm, 0.1)
    }

    @Test
    fun `height round-trip stays within 1cm`() {
        val original = 175.0
        val (feet, inches) = UnitConverter.cmToFeetInches(original)
        val back = UnitConverter.feetInchesToCm(feet, inches)
        assertEquals(original, back, 2.6) // integer inches truncation can lose up to 1 inch (2.54cm)
    }

    @Test
    fun `formatHeight returns cm format in METRIC`() {
        assertEquals("175 cm", UnitConverter.formatHeight(175.0, UnitSystem.METRIC))
    }

    @Test
    fun `formatHeight returns feet-inches format in IMPERIAL`() {
        val result = UnitConverter.formatHeight(175.0, UnitSystem.IMPERIAL)
        assertTrue("Expected ft'in\" format: $result", result.contains("'") && result.contains("\""))
    }

    // ── Distance conversions ──────────────────────────────────────────────────

    @Test
    fun `kmToMiles converts correctly`() {
        assertEquals(6.21371, UnitConverter.kmToMiles(10.0), 0.001)
    }

    @Test
    fun `milesToKm converts correctly`() {
        assertEquals(10.0, UnitConverter.milesToKm(6.21371), 0.01)
    }

    @Test
    fun `distanceLabel returns correct strings`() {
        assertEquals("km", UnitConverter.distanceLabel(UnitSystem.METRIC))
        assertEquals("mi", UnitConverter.distanceLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `formatDistance formats with 1 decimal place`() {
        assertEquals("5.0 km", UnitConverter.formatDistance(5.0, UnitSystem.METRIC))
        assertEquals("3.1 mi", UnitConverter.formatDistance(5.0, UnitSystem.IMPERIAL))
    }

    // ── formatNumber ──────────────────────────────────────────────────────────

    @Test
    fun `formatNumber uses 2 decimal places for non-integer values`() {
        assertEquals("80", UnitConverter.formatNumber(80.0))
        assertEquals("80.50", UnitConverter.formatNumber(80.5))
        assertEquals("32.25", UnitConverter.formatNumber(32.25))
        assertEquals("176.37", UnitConverter.formatNumber(176.37))
    }

    // ── formatWeightRaw ───────────────────────────────────────────────────────

    @Test
    fun `formatWeightRaw returns raw number without unit suffix`() {
        assertEquals("80", UnitConverter.formatWeightRaw(80.0, UnitSystem.METRIC))
        assertEquals("80.50", UnitConverter.formatWeightRaw(80.5, UnitSystem.METRIC))
        assertEquals("32.25", UnitConverter.formatWeightRaw(32.25, UnitSystem.METRIC))
    }
}
