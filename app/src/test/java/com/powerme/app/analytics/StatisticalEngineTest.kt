package com.powerme.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StatisticalEngineTest {

    // --- mean ---

    @Test
    fun mean_emptyList_returnsZero() {
        assertEquals(0.0, StatisticalEngine.mean(emptyList()), 0.0)
    }

    @Test
    fun mean_threeValues_returnsAverage() {
        assertEquals(20.0, StatisticalEngine.mean(listOf(10.0, 20.0, 30.0)), 0.0)
    }

    // --- standardDeviation ---

    @Test
    fun standardDeviation_singleElement_returnsZero() {
        assertEquals(0.0, StatisticalEngine.standardDeviation(listOf(42.0)), 0.0)
    }

    @Test
    fun standardDeviation_sampleFormula_correctValue() {
        // [2,4,4,4,5,5,7,9]: mean=5, sum sq diffs=32, sample variance=32/7
        val expected = sqrt(32.0 / 7.0)
        val result = StatisticalEngine.standardDeviation(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))
        assertEquals(expected, result, 0.001)
    }

    // --- zScore ---

    @Test
    fun zScore_zeroStdDev_returnsZero() {
        assertEquals(0.0, StatisticalEngine.zScore(5.0, 5.0, 0.0), 0.0)
    }

    @Test
    fun zScore_normalInput_correctValue() {
        assertEquals(1.0, StatisticalEngine.zScore(5.0, 3.0, 2.0), 0.0001)
    }

    // --- pearsonCorrelation ---

    @Test
    fun pearsonCorrelation_sizeMismatch_returnsZero() {
        assertEquals(0.0, StatisticalEngine.pearsonCorrelation(listOf(1.0, 2.0), listOf(1.0)), 0.0)
    }

    @Test
    fun pearsonCorrelation_perfectPositiveLinear_returnsPositive() {
        // [1,2,3] vs [2,4,6]: covariance(n)/stdDev_x(n-1)/stdDev_y(n-1) = (4/3)/(1.0*2.0) = 2/3
        val result = StatisticalEngine.pearsonCorrelation(listOf(1.0, 2.0, 3.0), listOf(2.0, 4.0, 6.0))
        assertEquals(2.0 / 3.0, result, 0.001)
        assertTrue(result > 0.0)
    }

    // --- calculate1RM ---

    @Test
    fun calculate1RM_zeroReps_returnsWeight() {
        assertEquals(100.0, StatisticalEngine.calculate1RM(100.0, 0), 0.0)
    }

    @Test
    fun calculate1RM_tenReps_epleyFormula() {
        // 100 * (1 + 10/30.0) = 133.333...
        val expected = 100.0 * (1 + 10 / 30.0)
        assertEquals(expected, StatisticalEngine.calculate1RM(100.0, 10), 0.001)
    }

    // --- calculateBayesian1RM ---

    @Test
    fun calculateBayesian1RM_zeroSampleSize_returnsPriorMean() {
        assertEquals(90.0, StatisticalEngine.calculateBayesian1RM(100.0, 0, 90.0), 0.0)
    }

    @Test
    fun calculateBayesian1RM_zeroPriorMean_returnsSampleMean() {
        assertEquals(100.0, StatisticalEngine.calculateBayesian1RM(100.0, 5, 0.0), 0.0)
    }

    @Test
    fun calculateBayesian1RM_standard_correctBlend() {
        // (5*90 + 5*100) / (5+5) = 950/10 = 95.0
        val result = StatisticalEngine.calculateBayesian1RM(
            sampleMean = 100.0,
            sampleSize = 5,
            priorMean = 90.0,
            confidenceConstant = 5
        )
        assertEquals(95.0, result, 0.001)
    }

    // --- rateOfChange ---

    @Test
    fun rateOfChange_zeroPrevious_returnsZero() {
        assertEquals(0.0, StatisticalEngine.rateOfChange(110.0, 0.0), 0.0)
    }

    @Test
    fun rateOfChange_tenPercentGain_returnsCorrectRate() {
        assertEquals(0.1, StatisticalEngine.rateOfChange(110.0, 100.0), 0.0001)
    }
}
