package com.omerhedvat.powerme.util

import com.omerhedvat.powerme.data.database.BarType

/**
 * Utility for calculating plate combinations needed for barbell exercises.
 * Uses greedy algorithm to find optimal plate breakdown.
 */
object PlateCalculator {

    data class PlateBreakdown(
        val platesPerSide: List<Double>,
        val weightPerSide: Double,
        val totalWeight: Double,
        val error: Double = 0.0
    )

    /**
     * Calculate the plates needed per side for a given total weight.
     *
     * @param totalWeight Total weight on the bar (including bar weight)
     * @param barType Type of bar being used (determines bar weight)
     * @param availablePlates List of available plate weights in kg (sorted descending for greedy algorithm)
     * @return PlateBreakdown with plates needed per side, or null if impossible
     */
    fun calculatePlates(
        totalWeight: Double,
        barType: BarType,
        availablePlates: List<Double>
    ): PlateBreakdown? {
        val barWeight = barType.weightKg

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

        // If error is too large (more than 0.5kg), the weight can't be loaded with available plates
        if (error > 0.5) {
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
     * Format plate breakdown as human-readable string
     * Example: "2x20kg + 1x5kg + 2x2.5kg"
     */
    fun formatPlateBreakdown(breakdown: PlateBreakdown): String {
        if (breakdown.platesPerSide.isEmpty()) {
            return "Bar only"
        }

        // Group plates by weight
        val plateGroups = breakdown.platesPerSide
            .groupBy { it }
            .map { (weight, plates) ->
                "${plates.size}x${formatWeight(weight)}"
            }
            .joinToString(" + ")

        return plateGroups
    }

    /**
     * Format weight, removing unnecessary decimals
     */
    private fun formatWeight(weight: Double): String {
        return if (weight % 1.0 == 0.0) {
            "${weight.toInt()}kg"
        } else {
            "${weight}kg"
        }
    }
}
