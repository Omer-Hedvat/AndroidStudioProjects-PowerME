package com.powerme.app.ui.metrics.charts

import androidx.compose.ui.graphics.Color
import com.powerme.app.ui.theme.ProMagenta
import com.powerme.app.ui.theme.ProOutlineSoft
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.ProViolet
import com.powerme.app.ui.theme.ReadinessAmber
import com.powerme.app.ui.theme.TimerGreen

/**
 * Centralized chart color and configuration constants for Vico charts.
 *
 * All chart cards use these palette constants for visual consistency with
 * the Pro Tracker v6.0 design system.
 *
 * See TRENDS_SPEC.md §7 for the full Vico integration guide.
 */
object VicoChartHelpers {

    // ── Chart line / bar colors ──────────────────────────────────────────────

    val LinePrimary = ProViolet
    val LineSecondary = TimerGreen
    val LineTertiary = ProMagenta
    val FillPrimary = ProViolet.copy(alpha = 0.15f)

    val BarPrimary = ProViolet
    val BarSecondary = ProMagenta

    // ── Axis and grid ────────────────────────────────────────────────────────

    val AxisLabelColor = ProSubGrey
    val GridLineColor = ProOutlineSoft

    // ── Multi-line exercise colors (e1RM chart, up to 4 lines) ──────────────

    val exerciseLineColors = listOf(ProViolet, TimerGreen, ProMagenta, ReadinessAmber)

    // ── Muscle group chart palette (fixed, not from theme) ──────────────────

    /** Fixed series order — must match the push order in TrendsViewModel.pushMuscleGroupToProducer(). */
    val muscleGroupOrder = listOf("Legs", "Back", "Chest", "Shoulders", "Arms", "Core", "Full Body", "Cardio")

    val muscleGroupColors = mapOf(
        "Legs" to Color(0xFF7B68EE),       // Medium slate blue
        "Back" to Color(0xFF4CC990),        // TimerGreen
        "Chest" to Color(0xFFE05555),       // ProError
        "Shoulders" to Color(0xFFFFB74D),   // ReadinessAmber
        "Arms" to Color(0xFF9B7DDB),        // ProViolet
        "Core" to Color(0xFF9E6B8A),        // ProMagenta
        "Full Body" to Color(0xFFA0A0A0),   // ProSubGrey
        "Cardio" to Color(0xFF80CBC4)        // Teal
    )

    /** Get chart color for a muscle group, falling back to ProSubGrey. */
    fun muscleGroupColor(group: String): Color = muscleGroupColors[group] ?: ProSubGrey
}
