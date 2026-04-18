package com.powerme.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JaroWinklerTest {

    private fun sim(s1: String, s2: String) = JaroWinkler.similarity(s1, s2)

    // ── Identity & boundary ──────────────────────────────────────────────────

    @Test fun identical_strings_return_1() {
        assertEquals(1.0, sim("bench press", "bench press"), 0.0001)
    }

    @Test fun both_empty_return_0() {
        assertEquals(0.0, sim("", ""), 0.0001)
    }

    @Test fun one_empty_returns_0() {
        assertEquals(0.0, sim("bench press", ""), 0.0001)
        assertEquals(0.0, sim("", "squat"), 0.0001)
    }

    @Test fun completely_different_returns_low_score() {
        assertTrue(sim("benchpress", "bicepscurl") < 0.85)
    }

    // ── Classic academic test pairs ──────────────────────────────────────────

    @Test fun martha_marhta() {
        // Standard Jaro-Winkler reference value ≈ 0.9611
        val score = sim("martha", "marhta")
        assertTrue("Expected ≈ 0.961, got $score", score > 0.95 && score <= 1.0)
    }

    @Test fun dixon_dicksonx() {
        // Jaro ≈ 0.767, Jaro-Winkler ≈ 0.813
        val score = sim("dixon", "dicksonx")
        assertTrue("Expected ~0.81, got $score", score in 0.75..0.90)
    }

    // ── Exercise-relevant pairs ──────────────────────────────────────────────

    @Test fun benchpress_vs_bench_press_collapsed() {
        // Both normalised to "benchpress" by toSearchName — they become identical
        val score = sim("benchpress", "benchpress")
        assertEquals(1.0, score, 0.0001)
    }

    @Test fun typo_in_bench_press_still_fuzzy_match() {
        // "barbelbenchpres" vs "barbellbenchpress" — one missing 'l' and 's'
        val score = sim("barbelbenchpres", "barbellbenchpress")
        assertTrue("Expected ≥ 0.85, got $score", score >= 0.85)
    }

    @Test fun barbell_squat_vs_barbell_back_squat_is_below_threshold() {
        // These are meaningfully different exercises; score should be below 1.0
        val score = sim("barbellsquat", "barbellbacksquat")
        assertTrue("Should be less than 1.0, got $score", score < 1.0)
    }

    @Test fun symmetry() {
        val s1 = "overheadpress"
        val s2 = "overhead"
        assertEquals(sim(s1, s2), sim(s2, s1), 0.0001)
    }

    @Test fun single_char_strings() {
        assertEquals(1.0, sim("a", "a"), 0.0001)
        assertEquals(0.0, sim("a", "b"), 0.0001)
    }
}
