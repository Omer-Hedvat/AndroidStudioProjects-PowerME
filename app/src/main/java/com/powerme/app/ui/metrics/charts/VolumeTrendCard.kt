package com.powerme.app.ui.metrics.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.metrics.WeeklyVolumeData
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Displays weekly training volume as a bar chart with a 4-week moving average line overlay.
 *
 * [modelProducer] is owned by TrendsViewModel so it survives tab navigation and LazyColumn
 * recycling. Data is pushed directly from the ViewModel — no LaunchedEffect needed here.
 */
@Composable
fun VolumeTrendCard(
    volumeData: WeeklyVolumeData?,
    timeRange: TrendsTimeRange,
    unitSystem: UnitSystem,
    onTimeRangeChange: (TrendsTimeRange) -> Unit,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val points = volumeData?.points.orEmpty()

    // Stable reference to the current timestamp list — read by the formatter at render time.
    val weekTimestamps = rememberUpdatedState(points.map { it.weekStartMs })
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val list = weekTimestamps.value
            if (list.isEmpty()) return@CartesianValueFormatter "–"
            val ts = list[value.roundToInt().coerceIn(list.indices)]
            dateFormat.format(Date(ts))
        }
    }

    // The producer holds raw metric (kg) values — convert to display units at render time.
    val yFormatter = remember(unitSystem) {
        val label = UnitConverter.weightLabel(unitSystem)
        CartesianValueFormatter { _, value, _ ->
            val display = UnitConverter.displayWeight(value, unitSystem)
            if (display >= 1_000.0) "${"%.0f".format(display / 1_000)}K $label"
            else "${"%.0f".format(display)} $label"
        }
    }

    val axisLabel = rememberTextComponent(color = ProSubGrey, textSize = 11.sp)

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
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VOLUME TREND",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProSubGrey,
                    letterSpacing = 1.sp
                )
                volumeData?.avgWorkoutsPerWeek?.let { avg ->
                    Text(
                        text = "Avg ${"%.1f".format(avg)}/wk",
                        fontSize = 11.sp,
                        color = ProSubGrey.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Time range chips ──────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrendsTimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = range == timeRange,
                        onClick = { onTimeRangeChange(range) },
                        label = { Text(range.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Chart area — CartesianChartHost is always in the composition tree ──
            // The producer lives in TrendsViewModel and is never recreated, so the host
            // can safely attach/detach (tab switches, LazyColumn scroll) without crashing.
            // When data is insufficient the chart renders dummy data behind a surface overlay.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                val barLayer = rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            color = VicoChartHelpers.BarPrimary,
                            thickness = 8.dp
                        )
                    )
                )
                val maLayer = rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(
                                fill(VicoChartHelpers.LineSecondary.copy(alpha = 0.85f))
                            ),
                            thickness = 2.dp
                        )
                    )
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        barLayer,
                        maLayer,
                        startAxis = VerticalAxis.rememberStart(
                            label = axisLabel,
                            valueFormatter = yFormatter
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            label = axisLabel,
                            valueFormatter = xFormatter
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.matchParentSize(),
                    scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
                )

                if (points.size < 2) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log at least 2 weeks of workouts\nto see volume trends",
                            fontSize = 13.sp,
                            color = ProSubGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
