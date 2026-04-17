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
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.powerme.app.ui.metrics.MuscleGroupVolumePoint
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Displays weekly volume distribution across muscle groups as a stacked bar chart.
 *
 * Series order is fixed to [VicoChartHelpers.muscleGroupOrder] — the ViewModel always
 * pushes 8 series in that order, and this card always creates 8 LineComponents in the
 * same order. This ensures the Vico layer and producer never disagree on series count
 * when the time range changes and different muscle groups appear or disappear.
 *
 * [modelProducer] is owned by TrendsViewModel so it survives tab navigation and LazyColumn
 * recycling. Data is pushed directly from the ViewModel — no LaunchedEffect needed here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleGroupVolumeCard(
    muscleGroupData: List<MuscleGroupVolumePoint>,
    timeRange: TrendsTimeRange,
    onTimeRangeChange: (TrendsTimeRange) -> Unit,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val weeks = remember(muscleGroupData) {
        muscleGroupData.map { it.weekStartMs }.distinct().sorted()
    }

    // Groups that have any volume in the selected range (for legend + distribution row)
    val activeGroups = remember(muscleGroupData) {
        muscleGroupData.groupBy { it.majorGroup }
            .filter { it.value.sumOf { p -> p.volume } > 0 }
            .entries.sortedByDescending { it.value.sumOf { p -> p.volume } }
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
                text = "MUSCLE BALANCE",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )

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
            // When data is insufficient, dummy data renders behind a surface overlay.
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
                        startAxis = VerticalAxis.rememberStart(label = axisLabel),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            label = axisLabel,
                            valueFormatter = xFormatter
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier.matchParentSize()
                )

                if (weeks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log at least 1 week of workouts\nto see muscle breakdown",
                            fontSize = 13.sp,
                            color = ProSubGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Legend (only when data exists) ────────────────────────────────
            if (activeGroups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    activeGroups.forEach { group ->
                        MuscleGroupLegendDot(
                            color = VicoChartHelpers.muscleGroupColor(group),
                            label = group
                        )
                    }
                }
            }

            // ── Distribution row: current week breakdown ──────────────────────
            if (weeks.isNotEmpty()) {
                val currentWeekPoints = muscleGroupData
                    .filter { it.weekStartMs == weeks.last() && it.volume > 0 }
                    .sortedByDescending { it.volume }
                val totalVolume = currentWeekPoints.sumOf { it.volume }

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
                        val fraction = if (totalVolume > 0) (point.volume / totalVolume).toFloat() else 0f
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
                                text = "${"%.0f".format(fraction * 100)}%",
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
private fun MuscleGroupLegendDot(color: Color, label: String) {
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
