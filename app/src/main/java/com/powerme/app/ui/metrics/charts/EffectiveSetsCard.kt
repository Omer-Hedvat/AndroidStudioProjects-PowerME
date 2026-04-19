package com.powerme.app.ui.metrics.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.powerme.app.ui.metrics.EffectiveSetsChartPoint
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val AmberColor = Color(0xFFFFB74D)

/**
 * Displays weekly effective set counts (RPE ≥ 7.0) per muscle group as a stacked bar chart.
 *
 * Follows the same fixed-8-series pattern as [MuscleGroupVolumeCard]: the ViewModel always
 * pushes 8 series in [VicoChartHelpers.muscleGroupOrder] order, and this card creates 8
 * LineComponents in the same order, preventing Vico layer/producer mismatches on time range
 * changes.
 *
 * [modelProducer] is owned by TrendsViewModel so it survives tab navigation and LazyColumn
 * recycling.
 *
 * @param coveragePct 0f–100f fraction of qualifying sets where the user logged RPE.
 *   0f means no sets in the range at all (or no RPE ever logged).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectiveSetsCard(
    effectiveSetsData: List<EffectiveSetsChartPoint>,
    coveragePct: Float,
    timeRange: TrendsTimeRange,
    onTimeRangeChange: (TrendsTimeRange) -> Unit,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val weeks = remember(effectiveSetsData) {
        effectiveSetsData.map { it.weekStartMs }.distinct().sorted()
    }

    val activeGroups = remember(effectiveSetsData) {
        effectiveSetsData.groupBy { it.majorGroup }
            .filter { it.value.sumOf { p -> p.setCount } > 0 }
            .entries.sortedByDescending { it.value.sumOf { p -> p.setCount } }
            .map { it.key }
    }

    val weekTimestamps = rememberUpdatedState(weeks)
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val list = weekTimestamps.value
            if (list.isEmpty()) return@CartesianValueFormatter "–"
            val ts = list[value.roundToInt().coerceIn(list.indices)]
            dateFormat.format(Date(ts))
        }
    }

    // Y axis shows integer set counts — no unit suffix needed per tick.
    val yFormatter = remember {
        CartesianValueFormatter { _, value, _ -> "%.0f".format(value) }
    }

    val axisLabel = rememberTextComponent(color = ProSubGrey, textSize = 11.sp)

    // 8 LineComponents matching VicoChartHelpers.muscleGroupOrder — never changes
    val col0 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Legs"), thickness = 8.dp)
    val col1 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Back"), thickness = 8.dp)
    val col2 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Chest"), thickness = 8.dp)
    val col3 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Shoulders"), thickness = 8.dp)
    val col4 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Arms"), thickness = 8.dp)
    val col5 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Core"), thickness = 8.dp)
    val col6 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Full Body"), thickness = 8.dp)
    val col7 = rememberLineComponent(color = VicoChartHelpers.muscleGroupColor("Cardio"), thickness = 8.dp)

    val hasData = weeks.isNotEmpty()
    val isSparse = coveragePct > 0f && coveragePct < 30f

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
            Text(
                text = "EFFECTIVE SETS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )

            // Coverage subtitle
            if (coveragePct > 0f) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Based on ${coveragePct.roundToInt()}% of sets with RPE logged",
                    fontSize = 11.sp,
                    color = if (coveragePct < 50f) AmberColor else ProSubGrey
                )
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

            Spacer(modifier = Modifier.height(8.dp))

            // ── Y axis unit label (shown once at the top of the axis) ─────────
            Text(
                text = "SETS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Chart area ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                val stackedBarLayer = rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        col0, col1, col2, col3, col4, col5, col6, col7
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked }
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        stackedBarLayer,
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
                    scrollState = rememberVicoScrollState(
                        initialScroll = Scroll.Absolute.End,
                        autoScroll = Scroll.Absolute.End,
                        autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased
                    ),
                    zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)
                )

            }

            // Sparse coverage warning (shown below the chart when data exists but coverage < 30%)
            if (isSparse) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Low RPE coverage — results may not be representative",
                    fontSize = 11.sp,
                    color = AmberColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Legend (only when data exists) ────────────────────────────────
            if (activeGroups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    activeGroups.forEach { group ->
                        EffectiveSetsLegendDot(
                            color = VicoChartHelpers.muscleGroupColor(group),
                            label = group
                        )
                    }
                }
            }

            // ── Distribution row: current week effective sets ──────────────────
            if (hasData) {
                val currentWeekPoints = effectiveSetsData
                    .filter { it.weekStartMs == weeks.last() && it.setCount > 0 }
                    .sortedByDescending { it.setCount }
                val totalEffective = currentWeekPoints.sumOf { it.setCount }

                if (currentWeekPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "THIS WEEK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ProSubGrey,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    currentWeekPoints.forEach { point ->
                        val fraction = if (totalEffective > 0) {
                            point.setCount.toFloat() / totalEffective.toFloat()
                        } else 0f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = VicoChartHelpers.muscleGroupColor(point.majorGroup),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = point.majorGroup,
                                fontSize = 11.sp,
                                color = ProSubGrey,
                                modifier = Modifier.width(80.dp),
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = VicoChartHelpers.muscleGroupColor(point.majorGroup),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${point.setCount}",
                                fontSize = 11.sp,
                                color = ProSubGrey,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectiveSetsLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(text = label, fontSize = 11.sp, color = ProSubGrey)
    }
}
