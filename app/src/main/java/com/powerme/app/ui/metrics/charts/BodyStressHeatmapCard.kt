package com.powerme.app.ui.metrics.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.analytics.StressAccumulationEngine
import com.powerme.app.data.database.BodyRegion
import com.powerme.app.ui.metrics.BodyStressMapData
import com.powerme.app.ui.theme.HeatmapHigh
import com.powerme.app.ui.theme.HeatmapLow
import com.powerme.app.ui.theme.HeatmapModerate
import com.powerme.app.ui.theme.HeatmapVeryHigh
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey

/**
 * Redesigned Body Stress Map card — fits on one screen without internal scrolling.
 *
 * Layout:
 *  - Standard header + subtitle
 *  - Compact body canvas (aspectRatio 0.65 — less elongated than old 0.50)
 *  - Top-3 stressed regions row with intensity tier chips
 *  - 4-color intensity legend
 *  - Tapping a region reveals an inline detail panel showing intensity tier,
 *    recovery status (Ready / Recovering / Fatigued), and contributing exercises.
 *
 * Color scale (opacity-based within each tier):
 *   no stress  → baseColor (surfaceVariant)
 *   LOW        → #34D399 (emerald green)
 *   MODERATE   → #F59E0B (amber)
 *   HIGH       → #EF4444 (red)
 *   VERY HIGH  → #7C3AED (deep purple)
 */
@Composable
fun BodyStressHeatmapCard(
    stressData: BodyStressMapData?,
    selectedRegion: BodyRegion?,
    onRegionTapped: (BodyRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant

    val regionColors: Map<BodyRegion, Color> = remember(stressData) {
        if (stressData != null && stressData.regionStresses.isNotEmpty()) {
            StressColorMapper.mapToColors(
                stressData.regionStresses,
                baseColor,
                HeatmapLow, HeatmapModerate, HeatmapHigh, HeatmapVeryHigh
            )
        } else {
            BodyRegion.entries.associateWith { baseColor }
        }
    }

    // Show detail panel when user taps a region; toggles off on same tap
    var showDetail by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Text(
                text = "BODY STRESS MAP",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "21-day cumulative load · tap a region for detail",
                fontSize = 12.sp,
                color = ProSubGrey.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Canvas ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BodyOutlineCanvas(
                    regionColors = regionColors,
                    selectedRegion = selectedRegion,
                    onRegionTapped = { region ->
                        val wasSame = selectedRegion == region && showDetail
                        onRegionTapped(region)
                        showDetail = !wasSame
                    }
                )

                // Empty-state overlay when no stress data is available
                if (stressData == null || stressData.regionStresses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log workouts to\nsee your stress map",
                            fontSize = 13.sp,
                            color = ProSubGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Region detail panel (inline, shown on tap) ───────────────────
            if (showDetail && selectedRegion != null && stressData != null) {
                val detail = stressData.regionDetails.firstOrNull { it.region == selectedRegion }
                if (detail != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    RegionDetailPanel(
                        detail = detail,
                        maxStress = stressData.maxStress,
                        regionColor = regionColors[selectedRegion] ?: baseColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Top-3 region chips ───────────────────────────────────────────
            val top3 = stressData?.regionDetails?.take(3).orEmpty()
            if (top3.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    top3.forEach { d ->
                        val tier = StressAccumulationEngine.classifyIntensity(
                            d.totalStress, stressData!!.maxStress
                        )
                        RegionChip(
                            name = d.region.displayName,
                            tier = tier,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Intensity legend ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = HeatmapLow, label = "Low")
                LegendDot(color = HeatmapModerate, label = "Moderate")
                LegendDot(color = HeatmapHigh, label = "High")
                LegendDot(color = HeatmapVeryHigh, label = "Very High")
            }
        }
    }
}

// ── Private composables ───────────────────────────────────────────────────────

@Composable
private fun RegionChip(
    name: String,
    tier: StressAccumulationEngine.IntensityTier,
    modifier: Modifier = Modifier
) {
    val tierColor = tier.color
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, tierColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tier.label,
            fontSize = 10.sp,
            color = tierColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = ProSubGrey.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun RegionDetailPanel(
    detail: StressAccumulationEngine.RegionDetail,
    maxStress: Double,
    regionColor: Color
) {
    val tier = StressAccumulationEngine.classifyIntensity(detail.totalStress, maxStress)
    val tierColor = tier.color
    val recoveryColor = when (detail.recoveryStatus) {
        StressAccumulationEngine.RecoveryStatus.READY      -> HeatmapLow
        StressAccumulationEngine.RecoveryStatus.RECOVERING -> HeatmapModerate
        StressAccumulationEngine.RecoveryStatus.FATIGUED   -> HeatmapHigh
    }
    val recoveryLabel = when (detail.recoveryStatus) {
        StressAccumulationEngine.RecoveryStatus.READY      -> "Ready"
        StressAccumulationEngine.RecoveryStatus.RECOVERING -> "Recovering"
        StressAccumulationEngine.RecoveryStatus.FATIGUED   -> "Fatigued"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, regionColor.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        // Region name + intensity tier badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = detail.region.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tierColor.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = tier.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tierColor
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Recovery status with colored dot
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(recoveryColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = recoveryLabel,
                fontSize = 12.sp,
                color = recoveryColor,
                fontWeight = FontWeight.Medium
            )
        }

        // Top contributing exercises
        if (detail.topExercises.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Top exercises",
                fontSize = 11.sp,
                color = ProSubGrey.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(3.dp))
            detail.topExercises.take(3).forEach { contrib ->
                Text(
                    text = "· ${contrib.exerciseName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private val StressAccumulationEngine.IntensityTier.label: String
    get() = when (this) {
        StressAccumulationEngine.IntensityTier.LOW       -> "LOW"
        StressAccumulationEngine.IntensityTier.MODERATE  -> "MODERATE"
        StressAccumulationEngine.IntensityTier.HIGH      -> "HIGH"
        StressAccumulationEngine.IntensityTier.VERY_HIGH -> "VERY HIGH"
    }

private val StressAccumulationEngine.IntensityTier.color: Color
    get() = when (this) {
        StressAccumulationEngine.IntensityTier.LOW       -> HeatmapLow
        StressAccumulationEngine.IntensityTier.MODERATE  -> HeatmapModerate
        StressAccumulationEngine.IntensityTier.HIGH      -> HeatmapHigh
        StressAccumulationEngine.IntensityTier.VERY_HIGH -> HeatmapVeryHigh
    }
