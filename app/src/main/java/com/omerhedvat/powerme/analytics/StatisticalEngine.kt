package com.omerhedvat.powerme.analytics

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
     * Calculate the first quartile (Q1) - 25th percentile
     */
    fun firstQuartile(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = (sorted.size * 0.25).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }

    /**
     * Calculate the third quartile (Q3) - 75th percentile
     */
    fun thirdQuartile(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = (sorted.size * 0.75).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }

    /**
     * Calculate the Interquartile Range (IQR)
     * IQR = Q3 - Q1
     */
    fun interquartileRange(values: List<Double>): Double {
        return thirdQuartile(values) - firstQuartile(values)
    }

    /**
     * Detect outliers using IQR method
     * Outliers are values < Q1 - 1.5*IQR or > Q3 + 1.5*IQR
     */
    fun detectOutliersIQR(values: List<Double>): OutlierAnalysis {
        if (values.size < 4) return OutlierAnalysis(emptyList(), emptyList())

        val q1 = firstQuartile(values)
        val q3 = thirdQuartile(values)
        val iqr = q3 - q1
        val lowerBound = q1 - 1.5 * iqr
        val upperBound = q3 + 1.5 * iqr

        val lowerOutliers = values.withIndex()
            .filter { it.value < lowerBound }
            .map { OutlierPoint(it.index, it.value, OutlierType.NEGATIVE) }

        val upperOutliers = values.withIndex()
            .filter { it.value > upperBound }
            .map { OutlierPoint(it.index, it.value, OutlierType.POSITIVE) }

        return OutlierAnalysis(lowerOutliers, upperOutliers)
    }

    /**
     * Detect outliers using Z-score method
     * Outliers are values with |Z| > 2
     */
    fun detectOutliersZScore(values: List<Double>, threshold: Double = 2.0): OutlierAnalysis {
        if (values.size < 3) return OutlierAnalysis(emptyList(), emptyList())

        val mean = mean(values)
        val stdDev = standardDeviation(values)

        val lowerOutliers = mutableListOf<OutlierPoint>()
        val upperOutliers = mutableListOf<OutlierPoint>()

        values.forEachIndexed { index, value ->
            val z = zScore(value, mean, stdDev)
            when {
                z < -threshold -> lowerOutliers.add(OutlierPoint(index, value, OutlierType.NEGATIVE))
                z > threshold -> upperOutliers.add(OutlierPoint(index, value, OutlierType.POSITIVE))
            }
        }

        return OutlierAnalysis(lowerOutliers, upperOutliers)
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
     * Calculate rate of change (percentage)
     */
    fun rateOfChange(current: Double, previous: Double): Double {
        if (previous == 0.0) return 0.0
        return (current - previous) / previous
    }
}

data class OutlierPoint(
    val index: Int,
    val value: Double,
    val type: OutlierType
)

data class OutlierAnalysis(
    val negativeOutliers: List<OutlierPoint>,
    val positiveOutliers: List<OutlierPoint>
) {
    val hasOutliers: Boolean
        get() = negativeOutliers.isNotEmpty() || positiveOutliers.isNotEmpty()
}

enum class OutlierType {
    NEGATIVE,
    POSITIVE
}
