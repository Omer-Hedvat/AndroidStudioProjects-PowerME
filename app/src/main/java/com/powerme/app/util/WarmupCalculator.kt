package com.powerme.app.util

import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.exercises.detail.WarmUpSet
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Equipment-aware warmup set generator.
 *
 * Determines the optimal number of warmup sets and their weights/reps based on the
 * gap between the equipment's practical starting weight and the user's working weight.
 * Number of sets scales with the size of the gap, capped at 5, with no single jump
 * exceeding [WarmupParams.maxJump].
 */
object WarmupCalculator {

    data class WarmupParams(
        val startWeight: Double,
        val maxJump: Double,
        val rounding: Double
    )

    private val SKIP_EQUIPMENT = setOf(
        "Pull-up Bar", "Rings", "Resistance Band", "Jump Rope",
        "Battle Ropes", "Sled", "Ab Wheel", "Medicine Ball"
    )

    /**
     * Returns warmup params for the given equipment type and unit system, or null if
     * warmup sets should be skipped entirely for this equipment.
     *
     * For Bodyweight equipment: returns null (caller must check if working weight > 0
     * and supply bodyweightParams instead).
     */
    fun equipmentToWarmupParams(equipmentType: String, unitSystem: UnitSystem): WarmupParams? {
        if (equipmentType in SKIP_EQUIPMENT) return null
        val metric = unitSystem == UnitSystem.METRIC
        return when (equipmentType) {
            "Barbell"      -> if (metric) WarmupParams(20.0, 20.0, 2.5) else WarmupParams(45.0, 45.0, 5.0)
            "EZ Bar"       -> if (metric) WarmupParams(10.0, 15.0, 2.5) else WarmupParams(25.0, 35.0, 5.0)
            "Smith Machine"-> if (metric) WarmupParams(15.0, 20.0, 2.5) else WarmupParams(35.0, 45.0, 5.0)
            "Landmine"     -> if (metric) WarmupParams(10.0, 15.0, 2.5) else WarmupParams(25.0, 35.0, 5.0)
            "Dumbbell"     -> if (metric) WarmupParams(4.0, 5.0, 1.0)   else WarmupParams(10.0, 10.0, 2.0)
            "Kettlebell"   -> if (metric) WarmupParams(8.0, 6.0, 2.0)   else WarmupParams(15.0, 15.0, 5.0)
            "Cable"        -> if (metric) WarmupParams(5.0, 10.0, 2.5)  else WarmupParams(10.0, 20.0, 5.0)
            "Machine"      -> if (metric) WarmupParams(10.0, 15.0, 5.0) else WarmupParams(20.0, 30.0, 10.0)
            "Bench"        -> if (metric) WarmupParams(10.0, 15.0, 5.0) else WarmupParams(20.0, 30.0, 10.0)
            "Bodyweight"   -> null // caller decides based on working weight
            else           -> null
        }
    }

    /** Params to use for Bodyweight exercises that have a loaded weight (e.g. weighted pull-ups). */
    fun bodyweightLoadedParams(unitSystem: UnitSystem): WarmupParams =
        if (unitSystem == UnitSystem.METRIC) WarmupParams(0.0, 10.0, 2.5)
        else WarmupParams(0.0, 20.0, 5.0)

    /**
     * Computes warmup sets for a given working weight and equipment params.
     *
     * Returns an empty list when the working weight is at or below the starting weight.
     * Returns at most 5 sets; no consecutive gap exceeds [WarmupParams.maxJump].
     * Weights are distributed evenly between start and working weight.
     * Reps descend from 10 as weight increases.
     */
    fun computeWarmupSets(workingWeight: Double, params: WarmupParams): List<WarmUpSet> {
        val gap = workingWeight - params.startWeight
        if (gap <= 0) return emptyList()

        val warmupCount: Int = when {
            gap <= params.maxJump -> 1
            else -> ceil(gap / params.maxJump).toInt().coerceAtMost(5)
        }

        val repScheme = listOf(10, 8, 5, 3, 2)
        return (1..warmupCount).map { i ->
            val rawWeight = params.startWeight + gap * (i.toDouble() / (warmupCount + 1))
            val roundedWeight = roundToNearest(rawWeight, params.rounding)
            val pct = (roundedWeight / workingWeight * 100).roundToInt()
            WarmUpSet(
                weight = roundedWeight,
                reps = repScheme[i - 1],
                percentageLabel = "$pct%"
            )
        }.filter { it.weight / workingWeight < 0.90 }
    }

    fun roundToNearest(value: Double, nearest: Double): Double =
        (value / nearest).roundToInt() * nearest
}
