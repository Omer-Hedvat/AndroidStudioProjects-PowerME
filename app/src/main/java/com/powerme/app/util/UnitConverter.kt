package com.powerme.app.util

import com.powerme.app.data.UnitSystem

object UnitConverter {

    // ── Weight ───────────────────────────────────────────────────────────────

    fun kgToLbs(kg: Double): Double = kg * 2.20462
    fun lbsToKg(lbs: Double): Double = lbs / 2.20462

    /** Returns the display value in the user's unit system (kg or lbs). */
    fun displayWeight(valueKg: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) kgToLbs(valueKg) else valueKg

    /** Converts a value entered in the user's unit back to kg for storage. */
    fun inputWeightToKg(displayValue: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) lbsToKg(displayValue) else displayValue

    fun weightLabel(unit: UnitSystem): String = if (unit == UnitSystem.IMPERIAL) "lbs" else "kg"

    /**
     * Formats a stored kg value for display, including the unit suffix.
     * Strips trailing decimal zeros (e.g. 80.0 → "80 kg", 80.5 → "80.5 kg").
     */
    fun formatWeight(valueKg: Double, unit: UnitSystem): String {
        val display = displayWeight(valueKg, unit)
        return "${formatNumber(display)} ${weightLabel(unit)}"
    }

    /**
     * Formats a weight difference (delta) for display. Returns e.g. "+1.2 kg" / "-0.5 lbs".
     * The delta is always stored in kg.
     */
    fun formatWeightDelta(deltaKg: Double, unit: UnitSystem): String {
        val display = displayWeight(deltaKg, unit)
        val prefix = if (display >= 0) "+" else ""
        return "$prefix${formatNumber(display)} ${weightLabel(unit)}"
    }

    // ── Height ───────────────────────────────────────────────────────────────

    fun cmToFeetInches(cm: Double): Pair<Int, Int> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        val inches = (totalInches % 12).toInt()
        return Pair(feet, inches)
    }

    fun feetInchesToCm(feet: Int, inches: Int): Double =
        (feet * 12 + inches) * 2.54

    fun cmToInches(cm: Double): Double = cm / 2.54
    fun inchesToCm(inches: Double): Double = inches * 2.54

    /**
     * Formats a stored cm value for display.
     * Metric: "175 cm", Imperial: "5'9\""
     */
    fun formatHeight(valueCm: Double, unit: UnitSystem): String {
        return if (unit == UnitSystem.IMPERIAL) {
            val (feet, inches) = cmToFeetInches(valueCm)
            "${feet}'${inches}\""
        } else {
            "${valueCm.toInt()} cm"
        }
    }

    // ── Distance ─────────────────────────────────────────────────────────────

    fun kmToMiles(km: Double): Double = km * 0.621371
    fun milesToKm(miles: Double): Double = miles / 0.621371

    fun displayDistance(valueKm: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) kmToMiles(valueKm) else valueKm

    fun inputDistanceToKm(displayValue: Double, unit: UnitSystem): Double =
        if (unit == UnitSystem.IMPERIAL) milesToKm(displayValue) else displayValue

    fun distanceLabel(unit: UnitSystem): String = if (unit == UnitSystem.IMPERIAL) "mi" else "km"

    fun formatDistance(valueKm: Double, unit: UnitSystem): String {
        val display = displayDistance(valueKm, unit)
        return "${"%.1f".format(display)} ${distanceLabel(unit)}"
    }

    // ── Numeric formatting ────────────────────────────────────────────────────

    /**
     * Formats a number to at most 2 decimal places, stripping trailing zeros only for integers:
     *   80.0   → "80"
     *   80.5   → "80.50"
     *   32.25  → "32.25"
     *   176.37 → "176.37"
     */
    fun formatNumber(value: Double): String = when {
        value == value.toLong().toDouble() -> value.toLong().toString()
        else -> "%.2f".format(value)
    }

    /**
     * Formats a raw weight value (no unit suffix) for use in workout input fields.
     * Keeps exactly 2 decimal places for non-integer values.
     */
    fun formatWeightRaw(valueKg: Double, unit: UnitSystem): String =
        formatNumber(displayWeight(valueKg, unit))
}
