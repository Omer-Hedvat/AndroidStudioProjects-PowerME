package com.powerme.app.util

import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.BarType

/**
 * Utility for calculating plate combinations needed for barbell exercises.
 * Uses greedy algorithm to find optimal plate breakdown.
 *
 * All inputs are in the user's display unit (kg for METRIC, lbs for IMPERIAL).
 * Callers must pass appropriate bar weights and plate values for the active unit system.
 */
object PlateCalculator {

    /** Default plate sets for each unit system. */
    val METRIC_DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
    val IMPERIAL_DEFAULT_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    data class PlateBreakdown(
        val platesPerSide: List<Double>,
        val weightPerSide: Double,
        val totalWeight: Double,
        val error: Double = 0.0
    )

    /**
     * Calculate the plates needed per side for a given total weight.
     *
     * @param totalWeight Total weight on the bar (including bar weight), in display units
     * @param barType Type of bar being used (determines bar weight)
     * @param availablePlates List of available plate weights in display units (sorted descending for greedy algorithm)
     * @param unitSystem Active unit system (used for bar weight and error threshold)
     * @return PlateBreakdown with plates needed per side, or null if impossible
     */
    fun calculatePlates(
        totalWeight: Double,
        barType: BarType,
        availablePlates: List<Double>,
        unitSystem: UnitSystem = UnitSystem.METRIC
    ): PlateBreakdown? {
        val barWeight = barType.displayWeight(unitSystem)

        // Calculate weight needed per side
        val weightPerSide = (totalWeight - barWeight) / 2.0

        // Can't have negative weight per side
        if (weightPerSide < 0) {
            return null
        }

        // If no plates needed (bar only)
        if (weightPerSide < 0.01) {
            return PlateBreakdown(
                platesPerSide = emptyList(),
                weightPerSide = 0.0,
                totalWeight = barWeight
            )
        }

        // Sort plates descending for greedy algorithm
        val sortedPlates = availablePlates.sortedDescending()

        // Greedy algorithm to find plate combination
        val platesNeeded = mutableListOf<Double>()
        var remainingWeight = weightPerSide

        for (plateWeight in sortedPlates) {
            while (remainingWeight >= plateWeight - 0.01) { // Small epsilon for floating point
                platesNeeded.add(plateWeight)
                remainingWeight -= plateWeight
            }
        }

        // Calculate actual weight achieved
        val actualWeightPerSide = platesNeeded.sum()
        val actualTotalWeight = barWeight + (actualWeightPerSide * 2)
        val error = kotlin.math.abs(totalWeight - actualTotalWeight)

        // Error threshold: 0.5 kg (metric) or 1.0 lbs (imperial)
        val errorThreshold = if (unitSystem == UnitSystem.IMPERIAL) 1.0 else 0.5
        if (error > errorThreshold) {
            return PlateBreakdown(
                platesPerSide = platesNeeded,
                weightPerSide = actualWeightPerSide,
                totalWeight = actualTotalWeight,
                error = error
            )
        }

        return PlateBreakdown(
            platesPerSide = platesNeeded,
            weightPerSide = actualWeightPerSide,
            totalWeight = actualTotalWeight,
            error = error
        )
    }

    /**
     * Parse available plates from comma-separated string (e.g., "0.5,1.25,2.5,5,10,15,20,25")
     */
    fun parseAvailablePlates(platesString: String): List<Double> {
        return platesString.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it > 0 }
            .distinct()
    }

    /**
     * Format plate breakdown as human-readable string.
     * Example (metric): "2x20kg + 1x5kg + 2x2.5kg"
     * Example (imperial): "2x45lbs + 1x10lbs"
     */
    fun formatPlateBreakdown(
        breakdown: PlateBreakdown,
        unitSystem: UnitSystem = UnitSystem.METRIC
    ): String {
        if (breakdown.platesPerSide.isEmpty()) {
            return "Bar only"
        }

        val unitLabel = UnitConverter.weightLabel(unitSystem)
        val plateGroups = breakdown.platesPerSide
            .groupBy { it }
            .map { (weight, plates) ->
                "${plates.size}x${formatWeight(weight)}$unitLabel"
            }
            .joinToString(" + ")

        return plateGroups
    }

    /**
     * Format weight, removing unnecessary decimals
     */
    private fun formatWeight(weight: Double): String {
        return if (weight % 1.0 == 0.0) {
            "${weight.toInt()}"
        } else {
            "$weight"
        }
    }
}
