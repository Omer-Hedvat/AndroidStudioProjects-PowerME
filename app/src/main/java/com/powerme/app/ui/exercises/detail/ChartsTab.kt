package com.powerme.app.ui.exercises.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.metrics.charts.VicoChartHelpers

@Composable
internal fun ChartsTabContent(
    trendData: ExerciseTrendData?,
    timeRange: TrendsTimeRange,
    onTimeRangeChanged: (TrendsTimeRange) -> Unit,
    e1rmProducer: CartesianChartModelProducer,
    maxWeightProducer: CartesianChartModelProducer,
    volumeProducer: CartesianChartModelProducer,
    bestSetProducer: CartesianChartModelProducer,
    rpeProducer: CartesianChartModelProducer
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Time range filter chips — pinned above scroll
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrendsTimeRange.entries.forEach { range ->
                FilterChip(
                    selected = range == timeRange,
                    onClick = { onTimeRangeChanged(range) },
                    label = { Text(range.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        // Charts
        val charts = listOf(
            Triple("e1RM (kg)", e1rmProducer, trendData?.e1rmPoints?.size),
            Triple("Max Weight (kg)", maxWeightProducer, trendData?.maxWeightPoints?.size),
            Triple("Session Volume (kg)", volumeProducer, trendData?.volumePoints?.size),
            Triple("Best Set (kg×reps)", bestSetProducer, trendData?.bestSetPoints?.size),
            Triple("RPE Trend", rpeProducer, trendData?.rpePoints?.size)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            items(
                items = charts,
                key = { it.first }
            ) { (label, producer, pointCount) ->
                MiniTrendChart(label = label, producer = producer, hasData = (pointCount ?: 0) >= 2)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MiniTrendChart(
    label: String,
    producer: CartesianChartModelProducer,
    hasData: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (hasData) {
            val axisLabel = rememberTextComponent(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 9.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(
                                        fill(VicoChartHelpers.LinePrimary)
                                    ),
                                    areaFill = LineCartesianLayer.AreaFill.single(
                                        fill(VicoChartHelpers.FillPrimary)
                                    ),
                                    thickness = 2.dp
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(label = axisLabel),
                        bottomAxis = HorizontalAxis.rememberBottom(label = axisLabel)
                    ),
                    modelProducer = producer,
                    modifier = Modifier.matchParentSize(),
                    scrollState = rememberVicoScrollState(
                        initialScroll = Scroll.Absolute.End,
                        autoScroll = Scroll.Absolute.End,
                        autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased
                    ),
                    zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not enough data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
