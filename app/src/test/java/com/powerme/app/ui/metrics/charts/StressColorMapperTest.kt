package com.powerme.app.ui.metrics.charts

import androidx.compose.ui.graphics.Color
import com.powerme.app.analytics.StressAccumulationEngine
import com.powerme.app.data.database.BodyRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the redesigned [StressColorMapper] 4-tier color system.
 *
 * Tier boundaries (by ratio = regionStress / maxStress):
 *   < 0.01            → base color
 *   [0.01, 0.25)      → low color  (alpha 0.30–0.80)
 *   [0.25, 0.50)      → moderate   (alpha 0.40–0.90)
 *   [0.50, 0.75)      → high       (alpha 0.50–1.00)
 *   [0.75, 1.00]      → very high  (alpha 0.60–1.00)
 */
class StressColorMapperTest {

    private val base      = Color(0xFF282828)
    private val low       = Color(0xFF34D399)
    private val moderate  = Color(0xFFF59E0B)
    private val high      = Color(0xFFEF4444)
    private val veryHigh  = Color(0xFF7C3AED)

    private fun stress(region: BodyRegion, value: Double) =
        StressAccumulationEngine.RegionStress(region, value)

    private fun mapColors(vararg entries: Pair<BodyRegion, Double>): Map<BodyRegion, Color> =
        StressColorMapper.mapToColors(
            entries.map { (r, v) -> stress(r, v) },
            base, low, moderate, high, veryHigh
        )

    // ── mapToColors: structural ───────────────────────────────────────────────

    @Test
    fun `empty input returns all 16 regions as base color`() {
        val result = StressColorMapper.mapToColors(emptyList(), base, low, moderate, high, veryHigh)
        assertEquals(16, result.size)
        assertTrue(result.values.all { it == base })
    }

    @Test
    fun `all 16 regions always present in output`() {
        val result = mapColors(BodyRegion.QUADS to 100.0)
        assertEquals(BodyRegion.entries.size, result.size)
        assertTrue(BodyRegion.entries.all { it in result })
    }

    @Test
    fun `unstressed regions get base color when others have stress`() {
        val result = mapColors(BodyRegion.QUADS to 100.0)
        assertEquals(base, result[BodyRegion.HAMSTRINGS])
        assertEquals(base, result[BodyRegion.PECS])
        assertEquals(base, result[BodyRegion.LATS])
    }

    @Test
    fun `zero totalStress returns all base colors`() {
        val result = StressColorMapper.mapToColors(
            listOf(stress(BodyRegion.QUADS, 0.0)), base, low, moderate, high, veryHigh
        )
        assertTrue(result.values.all { it == base })
    }

    // ── mapRatioToTierColor: tier boundaries ──────────────────────────────────

    @Test
    fun `ratio below 0_01 returns base color`() {
        val c = StressColorMapper.mapRatioToTierColor(0.005f, base, low, moderate, high, veryHigh)
        assertEquals(base, c)
    }

    @Test
    fun `ratio 0_01 (low tier start) is low color`() {
        val c = StressColorMapper.mapRatioToTierColor(0.01f, base, low, moderate, high, veryHigh)
        // Should be low color with alpha 0.30 (start of tier)
        assertEquals(low.copy(alpha = 0.30f), c)
    }

    @Test
    fun `ratio exactly 0_25 (moderate tier start) is moderate color`() {
        val c = StressColorMapper.mapRatioToTierColor(0.25f, base, low, moderate, high, veryHigh)
        // Start of moderate tier → alpha = 0.40
        assertEquals(moderate.copy(alpha = 0.40f), c)
    }

    @Test
    fun `ratio exactly 0_50 (high tier start) is high color`() {
        val c = StressColorMapper.mapRatioToTierColor(0.50f, base, low, moderate, high, veryHigh)
        assertEquals(high.copy(alpha = 0.50f), c)
    }

    @Test
    fun `ratio exactly 0_75 (very high tier start) is veryHigh color`() {
        val c = StressColorMapper.mapRatioToTierColor(0.75f, base, low, moderate, high, veryHigh)
        assertEquals(veryHigh.copy(alpha = 0.60f), c)
    }

    @Test
    fun `ratio 1_0 (hottest region) is veryHigh at full alpha`() {
        val c = StressColorMapper.mapRatioToTierColor(1.0f, base, low, moderate, high, veryHigh)
        assertEquals(veryHigh.copy(alpha = 1.0f), c)
    }

    // ── mapRatioToTierColor: alpha interpolation within tiers ─────────────────

    @Test
    fun `low tier mid-point has correct interpolated alpha`() {
        // Midpoint of low tier: ratio = 0.01 + (0.25-0.01)/2 = 0.13
        val c = StressColorMapper.mapRatioToTierColor(0.13f, base, low, moderate, high, veryHigh)
        // fraction = (0.13 - 0.01) / 0.24 = 0.5; alpha = lerp(0.30, 0.80, 0.5) = 0.55
        assertEquals(low.copy(alpha = 0.55f), c)
    }

    @Test
    fun `veryHigh tier mid-point has alpha between 0_60 and 1_00`() {
        // Midpoint of very high tier: ratio = 0.75 + 0.125 = 0.875
        val c = StressColorMapper.mapRatioToTierColor(0.875f, base, low, moderate, high, veryHigh)
        // fraction = (0.875 - 0.75) / 0.25 = 0.5; alpha = lerp(0.60, 1.00, 0.5) = 0.80
        assertEquals(veryHigh.copy(alpha = 0.80f), c)
    }

    // ── mapToColors: integration with stress list ─────────────────────────────

    @Test
    fun `single region at max gets very high tier color`() {
        val result = mapColors(BodyRegion.QUADS to 100.0)
        // ratio = 1.0 → veryHigh, alpha = 1.0
        assertEquals(veryHigh.copy(alpha = 1.0f), result[BodyRegion.QUADS])
    }

    @Test
    fun `region at 60pct of max falls in high tier`() {
        val result = mapColors(
            BodyRegion.QUADS to 100.0,
            BodyRegion.HAMSTRINGS to 60.0
        )
        // HAMSTRINGS ratio = 0.60 → high tier
        val expected = StressColorMapper.mapRatioToTierColor(0.60f, base, low, moderate, high, veryHigh)
        assertEquals(expected, result[BodyRegion.HAMSTRINGS])
    }

    @Test
    fun `region at 30pct of max falls in moderate tier`() {
        val result = mapColors(
            BodyRegion.QUADS to 100.0,
            BodyRegion.CORE to 30.0
        )
        val expected = StressColorMapper.mapRatioToTierColor(0.30f, base, low, moderate, high, veryHigh)
        assertEquals(expected, result[BodyRegion.CORE])
    }

    @Test
    fun `region at 10pct of max falls in low tier`() {
        val result = mapColors(
            BodyRegion.QUADS to 100.0,
            BodyRegion.CALVES to 10.0
        )
        val expected = StressColorMapper.mapRatioToTierColor(0.10f, base, low, moderate, high, veryHigh)
        assertEquals(expected, result[BodyRegion.CALVES])
    }
}
