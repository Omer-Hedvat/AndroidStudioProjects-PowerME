package com.powerme.app.analytics

import kotlin.math.pow
import kotlin.math.sqrt

object StatisticalEngine {

    /**
     * Calculate the mean (average) of a list of doubles
     */
    fun mean(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    /**
     * Calculate the standard deviation
     */
    fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = mean(values)
        val variance = values.map { (it - mean).pow(2) }.sum() / (values.size - 1)
        return sqrt(variance)
    }

    /**
     * Calculate Z-score for a value
     * Z = (X - μ) / σ
     */
    fun zScore(value: Double, mean: Double, stdDev: Double): Double {
        if (stdDev == 0.0) return 0.0
        return (value - mean) / stdDev
    }

    /**
     * Calculate Pearson correlation coefficient between two datasets
     * r = Σ((x - x̄)(y - ȳ)) / (σx * σy * n)
     */
    fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.size < 2) return 0.0

        val meanX = mean(x)
        val meanY = mean(y)
        val stdDevX = standardDeviation(x)
        val stdDevY = standardDeviation(y)

        if (stdDevX == 0.0 || stdDevY == 0.0) return 0.0

        val covariance = x.zip(y).map { (xi, yi) ->
            (xi - meanX) * (yi - meanY)
        }.sum() / x.size

        return covariance / (stdDevX * stdDevY)
    }

    /**
     * Calculate estimated 1RM using Epley formula
     * e1RM = weight × (1 + reps/30)
     */
    fun calculate1RM(weight: Double, reps: Int): Double {
        if (reps <= 0) return weight
        return weight * (1 + reps / 30.0)
    }

    /**
     * Bayesian M-Estimate of 1RM — reduces noise from small sample sizes.
     * μ_bayesian = (C * μ_prior + n * μ_sample) / (C + n)
     *
     * @param sampleMean         Mean e1RM across [sampleSize] recent sets (kg)
     * @param sampleSize         Number of sets in the current sample (n)
     * @param priorMean          User's all-time average e1RM for this exercise (μ_prior).
     *                           Pass 0.0 for untracked exercises; the function returns
     *                           [sampleMean] directly to avoid pulling estimates toward zero.
     * @param confidenceConstant Virtual prior set count; default = 5 sets (C)
     */
    fun calculateBayesian1RM(
        sampleMean: Double,
        sampleSize: Int,
        priorMean: Double,
        confidenceConstant: Int = 5
    ): Double {
        if (sampleSize <= 0) return priorMean
        if (priorMean == 0.0) return sampleMean          // safety: untracked exercise
        val c = confidenceConstant.toDouble()
        val n = sampleSize.toDouble()
        return (c * priorMean + n * sampleMean) / (c + n)
    }

    /**
     * Calculate rate of change (percentage)
     */
    fun rateOfChange(current: Double, previous: Double): Double {
        if (previous == 0.0) return 0.0
        return (current - previous) / previous
    }
}
