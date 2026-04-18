package com.powerme.app.util

/**
 * Pure Kotlin implementation of the Jaro-Winkler string similarity algorithm.
 * Returns a score in [0.0, 1.0] where 1.0 means identical.
 */
object JaroWinkler {

    /**
     * Computes Jaro-Winkler similarity between [s1] and [s2].
     * Uses prefix weight p=0.1 and maximum prefix length 4 (standard parameters).
     */
    fun similarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0

        val jaro = jaro(s1, s2)
        if (jaro == 0.0) return 0.0

        // Compute common prefix length (max 4)
        val prefixLen = minOf(4, s1.length, s2.length).let { max ->
            var l = 0
            while (l < max && s1[l] == s2[l]) l++
            l
        }

        return jaro + prefixLen * 0.1 * (1.0 - jaro)
    }

    private fun jaro(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        val matchWindow = maxOf(len1, len2) / 2 - 1
        if (matchWindow < 0) return 0.0

        val s1Matched = BooleanArray(len1)
        val s2Matched = BooleanArray(len2)
        var matches = 0

        for (i in 0 until len1) {
            val start = maxOf(0, i - matchWindow)
            val end = minOf(i + matchWindow + 1, len2)
            for (j in start until end) {
                if (s2Matched[j] || s1[i] != s2[j]) continue
                s1Matched[i] = true
                s2Matched[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matched[i]) continue
            while (!s2Matched[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0
    }
}
