package com.powerme.app.util

import com.powerme.app.util.SurgicalValidator.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurgicalValidatorTest {

    // --- parseDecimal ---

    @Test
    fun parseDecimal_dotSeparator_returnsValid() {
        val result = SurgicalValidator.parseDecimal("80.5")
        assertEquals(ValidationResult.Valid(80.5), result)
    }

    @Test
    fun parseDecimal_commaSeparator_returnsValid() {
        val result = SurgicalValidator.parseDecimal("80,5")
        assertEquals(ValidationResult.Valid(80.5), result)
    }

    @Test
    fun parseDecimal_integer_returnsValidAsDouble() {
        val result = SurgicalValidator.parseDecimal("80")
        assertEquals(ValidationResult.Valid(80.0), result)
    }

    @Test
    fun parseDecimal_emptyString_returnsEmpty() {
        val result = SurgicalValidator.parseDecimal("")
        assertEquals(ValidationResult.Empty, result)
    }

    @Test
    fun parseDecimal_blankString_returnsEmpty() {
        val result = SurgicalValidator.parseDecimal("   ")
        assertEquals(ValidationResult.Empty, result)
    }

    @Test
    fun parseDecimal_letters_returnsInvalid() {
        val result = SurgicalValidator.parseDecimal("abc")
        assertEquals(ValidationResult.Invalid, result)
    }

    @Test
    fun parseDecimal_dash_returnsInvalid() {
        val result = SurgicalValidator.parseDecimal("-")
        assertEquals(ValidationResult.Invalid, result)
    }

    @Test
    fun parseDecimal_barePeriod_returnsInvalid() {
        val result = SurgicalValidator.parseDecimal(".")
        assertEquals(ValidationResult.Invalid, result)
    }

    @Test
    fun parseDecimal_bareComma_returnsInvalid() {
        // comma normalises to "." which is the bare-separator guard
        val result = SurgicalValidator.parseDecimal(",")
        assertEquals(ValidationResult.Invalid, result)
    }

    @Test
    fun parseDecimal_negativeValue_returnsValid() {
        // SurgicalValidator doesn't restrict sign at this layer
        val result = SurgicalValidator.parseDecimal("-5.0")
        assertEquals(ValidationResult.Valid(-5.0), result)
    }

    // --- parseReps ---

    @Test
    fun parseReps_integer_returnsValid() {
        val result = SurgicalValidator.parseReps("10")
        assertEquals(ValidationResult.Valid(10.0), result)
    }

    @Test
    fun parseReps_decimal_returnsInvalid() {
        val result = SurgicalValidator.parseReps("10.5")
        assertEquals(ValidationResult.Invalid, result)
    }

    @Test
    fun parseReps_empty_returnsEmpty() {
        val result = SurgicalValidator.parseReps("")
        assertEquals(ValidationResult.Empty, result)
    }

    @Test
    fun parseReps_zero_returnsValid() {
        val result = SurgicalValidator.parseReps("0")
        assertEquals(ValidationResult.Valid(0.0), result)
    }

    // --- isLeakedMetric ---

    @Test
    fun isLeakedMetric_null_returnsFalse() {
        assertFalse(SurgicalValidator.isLeakedMetric(null))
    }

    @Test
    fun isLeakedMetric_emptyString_returnsFalse() {
        assertFalse(SurgicalValidator.isLeakedMetric(""))
    }

    @Test
    fun isLeakedMetric_cmSuffix_returnsTrue() {
        assertTrue(SurgicalValidator.isLeakedMetric("180cm"))
    }

    @Test
    fun isLeakedMetric_kgWithSpace_returnsTrue() {
        assertTrue(SurgicalValidator.isLeakedMetric("72 kg"))
    }

    @Test
    fun isLeakedMetric_lbsSuffix_returnsTrue() {
        assertTrue(SurgicalValidator.isLeakedMetric("180lbs"))
    }

    @Test
    fun isLeakedMetric_lbSuffix_returnsTrue() {
        assertTrue(SurgicalValidator.isLeakedMetric("90lb"))
    }

    @Test
    fun isLeakedMetric_longCoachingCue_returnsFalse() {
        // > 20 chars → safely passes through even though no unit suffix
        assertFalse(SurgicalValidator.isLeakedMetric("Keep chest up, brace core, drive through heels"))
    }

    @Test
    fun isLeakedMetric_noUnitSuffix_returnsFalse() {
        assertFalse(SurgicalValidator.isLeakedMetric("UPPER CHEST"))
    }
}
