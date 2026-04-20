package com.powerme.app.ui.metrics.charts

import androidx.compose.ui.graphics.Color
import com.powerme.app.analytics.StressAccumulationEngine
import com.powerme.app.data.database.BodyRegion

/**
 * Maps [StressAccumulationEngine.RegionStress] values to a [Color] for every [BodyRegion]
 * using a 4-tier intensity system:
 *
 *   ratio < 0.01              → baseColor (no stress / fully recovered)
 *   ratio in [0.01, 0.25)     → lowColor    (alpha 0.30 → 0.80)
 *   ratio in [0.25, 0.50)     → moderateColor (alpha 0.40 → 0.90)
 *   ratio in [0.50, 0.75)     → highColor   (alpha 0.50 → 1.00)
 *   ratio in [0.75, 1.00]     → veryHighColor (alpha 0.60 → 1.00)
 *
 * Stress values are normalized against the highest-stress region in the current dataset,
 * so the hottest region is always at full tier saturation regardless of absolute magnitude.
 *
 * Colors are passed as parameters (resolved from MaterialTheme at the call site) to keep
 * this object pure and testable without Compose runtime.
 */
object StressColorMapper {

    /**
     * Returns a color for every [BodyRegion] (all 16 entries).
     * Regions absent from [stresses] receive [baseColor].
     *
     * @param stresses       Per-region stress values from [StressAccumulationEngine].
     * @param baseColor      Color for unstressed / no-data regions.
     * @param lowColor       Tier color for ratio in [0.01, 0.25).
     * @param moderateColor  Tier color for ratio in [0.25, 0.50).
     * @param highColor      Tier color for ratio in [0.50, 0.75).
     * @param veryHighColor  Tier color for ratio in [0.75, 1.00].
     */
    fun mapToColors(
        stresses: List<StressAccumulationEngine.RegionStress>,
        baseColor: Color,
        lowColor: Color,
        moderateColor: Color,
        highColor: Color,
        veryHighColor: Color
    ): Map<BodyRegion, Color> {
        val maxStress = stresses.maxOfOrNull { it.totalStress } ?: 0.0
        if (maxStress <= 0.0) return BodyRegion.entries.associateWith { baseColor }

        val stressMap = stresses.associate { it.region to it.totalStress }
        return BodyRegion.entries.associateWith { region ->
            val ratio = ((stressMap[region] ?: 0.0) / maxStress).toFloat().coerceIn(0f, 1f)
            mapRatioToTierColor(ratio, baseColor, lowColor, moderateColor, highColor, veryHighColor)
        }
    }

    /**
     * Maps a normalized stress ratio [0, 1] to a tier color with opacity-based intensity.
     * Exposed as `internal` for direct unit testing.
     */
    internal fun mapRatioToTierColor(
        ratio: Float,
        baseColor: Color,
        lowColor: Color,
        moderateColor: Color,
        highColor: Color,
        veryHighColor: Color
    ): Color = when {
        ratio < 0.01f  -> baseColor
        ratio < 0.25f  -> lowColor.copy(alpha = lerp(0.30f, 0.80f, (ratio - 0.01f) / 0.24f))
        ratio < 0.50f  -> moderateColor.copy(alpha = lerp(0.40f, 0.90f, (ratio - 0.25f) / 0.25f))
        ratio < 0.75f  -> highColor.copy(alpha = lerp(0.50f, 1.00f, (ratio - 0.50f) / 0.25f))
        else           -> veryHighColor.copy(alpha = lerp(0.60f, 1.00f, (ratio - 0.75f) / 0.25f))
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction.coerceIn(0f, 1f)
}
