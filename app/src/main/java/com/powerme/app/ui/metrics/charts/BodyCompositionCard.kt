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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.metrics.BodyCompositionData
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProMagenta
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Displays body weight and body fat % as two independent line series over time.
 *
 * [modelProducer] is owned by TrendsViewModel so it survives tab navigation and
 * LazyColumn recycling. Data is pushed from the ViewModel — no LaunchedEffect needed here.
 * Both series are always in the producer (stable layer count); toggle chips control visibility.
 */
@Composable
fun BodyCompositionCard(
    bodyCompositionData: BodyCompositionData?,
    timeRange: TrendsTimeRange,
    unitSystem: UnitSystem,
    onTimeRangeChange: (TrendsTimeRange) -> Unit,
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val weightPoints = bodyCompositionData?.weightPoints.orEmpty()
    val bodyFatPoints = bodyCompositionData?.bodyFatPoints.orEmpty()

    // Merge timestamps from both series — avoids a separate ViewModel StateFlow and
    // the race condition that would result from two flows updating independently.
    val timestamps = remember(weightPoints, bodyFatPoints) {
        (weightPoints.map { it.timestampMs } + bodyFatPoints.map { it.timestampMs })
            .distinct().sorted()
    }
    var showWeight by remember { mutableStateOf(true) }
    var showBodyFat by remember { mutableStateOf(true) }

    // Stable reference to timestamp list — read by formatter at render time.
    val timestampsState = rememberUpdatedState(timestamps)
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val list = timestampsState.value
            // Must return " " (single space) — never "" — to avoid Vico IllegalStateException.
            if (list.isEmpty()) return@CartesianValueFormatter " "
            val idx = value.roundToInt().coerceIn(list.indices)
            dateFormat.format(Date(list[idx]))
        }
    }

    // Y-axis label is unit-aware: show "%" when only body fat is visible so the axis doesn't
    // display weight units (e.g. "25.0 kg") for body fat percentage values.
    val onlyBodyFatVisible = showBodyFat && bodyFatPoints.isNotEmpty() && !(showWeight && weightPoints.isNotEmpty())

    // Y axis shows numbers only — unit is displayed once as a label above the axis.
    val yFormatter = remember(unitSystem, onlyBodyFatVisible) {
        CartesianValueFormatter { _, value, _ ->
            if (onlyBodyFatVisible) {
                "%.1f".format(value)
            } else {
                val display = UnitConverter.displayWeight(value, unitSystem)
                "%.1f".format(display)
            }
        }
    }
    val yAxisUnit = if (onlyBodyFatVisible) "%" else UnitConverter.weightLabel(unitSystem)

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
            Text(
                text = "BODY COMPOSITION",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Line toggle chips ──────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = showWeight,
                    onClick = { showWeight = !showWeight },
                    label = { Text("Weight", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TimerGreen,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = showBodyFat,
                    onClick = { showBodyFat = !showBodyFat },
                    label = { Text("Body Fat %", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ProMagenta,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                text = yAxisUnit.uppercase(),
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
                // Lines are always present so the series count stays constant (Vico crashes on
                // series-count changes). Toggle chips and missing data both zero the alpha.
                val weightVisible = showWeight && weightPoints.isNotEmpty()
                val bodyFatVisible = showBodyFat && bodyFatPoints.isNotEmpty()
                val weightLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(
                        fill(if (weightVisible) TimerGreen else TimerGreen.copy(alpha = 0f))
                    ),
                    thickness = 2.dp
                )
                val bodyFatLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(
                        fill(if (bodyFatVisible) ProMagenta else ProMagenta.copy(alpha = 0f))
                    ),
                    thickness = 1.5.dp
                )
                val lineProvider = remember(weightLine, bodyFatLine) {
                    LineCartesianLayer.LineProvider.series(weightLine, bodyFatLine)
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(lineProvider = lineProvider),
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

            // ── Sparse state note ─────────────────────────────────────────────
            val sparseNote = when {
                weightPoints.isEmpty() && bodyFatPoints.isNotEmpty() -> "No weight data in this range"
                bodyFatPoints.isEmpty() && weightPoints.isNotEmpty() -> "No body fat data in this range"
                else -> null
            }
            if (sparseNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = sparseNote, fontSize = 11.sp, color = ProSubGrey)
            }

            // ── Legend ────────────────────────────────────────────────────────
            if (weightPoints.isNotEmpty() || bodyFatPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (weightPoints.isNotEmpty()) {
                        BodyCompLegendDot(color = TimerGreen, label = "Weight")
                    }
                    if (bodyFatPoints.isNotEmpty()) {
                        BodyCompLegendDot(color = ProMagenta, label = "Body Fat %")
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyCompLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 11.sp, color = ProSubGrey)
    }
}
