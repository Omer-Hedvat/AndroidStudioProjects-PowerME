package com.powerme.app.ui.metrics.charts

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.DashedShape
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.metrics.ChronotypeData
import com.powerme.app.ui.metrics.SleepChartPoint
import com.powerme.app.ui.metrics.TimeOfDayChartPoint
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProError
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.ProViolet
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Displays two chronotype insights:
 * 1. **Sleep Trend** — nightly sleep duration bar chart (Vico ColumnCartesianLayer).
 *    Bars are green when ≥ 7h, red when < 7h. Fixed 30-day window regardless of time range.
 * 2. **Training Window** — custom Canvas scatter plot of workout start hour vs volume.
 *    The peak median-volume hour is highlighted in ProViolet.
 *
 * Both sub-cards show empty states independently. Sleep requires ≥ 7 HC sync nights;
 * Training Window requires ≥ 10 completed workouts with a recorded start time.
 *
 * [sleepModelProducer] is owned by TrendsViewModel and survives tab navigation.
 * Data is pushed from the ViewModel — no LaunchedEffect needed here.
 */
@Composable
fun ChronotypeCard(
    chronotypeData: ChronotypeData?,
    timeRange: TrendsTimeRange,
    unitSystem: UnitSystem,
    onTimeRangeChange: (TrendsTimeRange) -> Unit,
    sleepModelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier
) {
    val sleepPoints = chronotypeData?.sleepPoints.orEmpty()
    val workoutPoints = chronotypeData?.workoutPoints.orEmpty()
    val peakHour = chronotypeData?.peakHour
    val peakHourLabel = chronotypeData?.peakHourLabel

    val hasSleepData = sleepPoints.size >= 7
    val hasWorkoutData = workoutPoints.size >= 10

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
            // ── Card-level title ──────────────────────────────────────────────
            Text(
                text = "CHRONOTYPE",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Section A: Sleep Trend ─────────────────────────────────────────
            // Hidden when there are zero sleep records (card-level guard in MetricsScreen
            // ensures we only reach here when at least one sub-section has data).
            if (sleepPoints.isNotEmpty()) {
                SleepTrendSection(
                    sleepPoints = sleepPoints,
                    hasSleepData = hasSleepData,
                    sleepModelProducer = sleepModelProducer
                )
            }

            // Divider only when both sub-sections are visible
            if (sleepPoints.isNotEmpty() && workoutPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Section B: Training Window ─────────────────────────────────────
            // Hidden when there are zero workout-time data points.
            if (workoutPoints.isNotEmpty()) {
                TrainingWindowSection(
                    workoutPoints = workoutPoints,
                    peakHour = peakHour,
                    peakHourLabel = peakHourLabel,
                    hasWorkoutData = hasWorkoutData,
                    timeRange = timeRange,
                    unitSystem = unitSystem,
                    onTimeRangeChange = onTimeRangeChange
                )
            }
        }
    }
}

@Composable
private fun SleepTrendSection(
    sleepPoints: List<SleepChartPoint>,
    hasSleepData: Boolean,
    sleepModelProducer: CartesianChartModelProducer
) {
    val sleepPointsState = rememberUpdatedState(sleepPoints)
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val list = sleepPointsState.value
            if (list.isEmpty()) return@CartesianValueFormatter " "
            val idx = value.roundToInt().coerceIn(list.indices)
            val epochDay = list[idx].date.toEpochDay()
            dateFormat.format(Date(epochDay * 86_400_000L))
        }
    }

    val yFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            "${"%.1f".format(value)}h"
        }
    }

    val axisLabel = rememberTextComponent(color = ProSubGrey, textSize = 11.sp)

    // Two-series stacked columns — each position has a value in only one series,
    // so stacking gives a single bar coloured by the sleep threshold.
    val greenColumn = rememberLineComponent(color = TimerGreen, thickness = 8.dp)
    val redColumn = rememberLineComponent(color = ProError, thickness = 8.dp)
    val columnProvider = remember(greenColumn, redColumn) {
        ColumnCartesianLayer.ColumnProvider.series(greenColumn, redColumn)
    }

    val sevenHourRefLine = rememberLineComponent(
        color = ProSubGrey.copy(alpha = 0.5f),
        thickness = 1.dp,
        shape = DashedShape(shape = Shape.Rectangle, dashLengthDp = 6f, gapLengthDp = 4f)
    )
    val sevenHourDecoration = remember(sevenHourRefLine) {
        HorizontalLine(y = { 7.0 }, line = sevenHourRefLine)
    }

    Text(
        text = "SLEEP TREND",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = ProSubGrey,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "Last 30 nights",
        fontSize = 11.sp,
        color = ProSubGrey.copy(alpha = 0.6f)
    )

    Spacer(modifier = Modifier.height(12.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = columnProvider
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = axisLabel,
                    valueFormatter = yFormatter
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = axisLabel,
                    valueFormatter = xFormatter
                ),
                decorations = listOf(sevenHourDecoration)
            ),
            modelProducer = sleepModelProducer,
            modifier = Modifier.matchParentSize(),
            scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End)
        )

    }

    // ── Sleep legend ──────────────────────────────────────────────────────────
    if (hasSleepData) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepLegendItem(color = TimerGreen, label = "≥ 7h")
            SleepLegendItem(color = ProError, label = "< 7h")
        }
    }
}

@Composable
private fun SleepLegendItem(color: Color, label: String) {
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

@Composable
private fun TrainingWindowSection(
    workoutPoints: List<TimeOfDayChartPoint>,
    peakHour: Int?,
    peakHourLabel: String?,
    hasWorkoutData: Boolean,
    timeRange: TrendsTimeRange,
    unitSystem: UnitSystem,
    onTimeRangeChange: (TrendsTimeRange) -> Unit
) {
    Text(
        text = "TRAINING WINDOW",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = ProSubGrey,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    // ── Time range chips ──────────────────────────────────────────────────────
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasWorkoutData) {
            ScatterPlot(
                workoutPoints = workoutPoints,
                peakHour = peakHour,
                unitLabel = UnitConverter.weightLabel(unitSystem),
                modifier = Modifier.matchParentSize()
            )
        }
    }

    // ── Peak hour summary ─────────────────────────────────────────────────────
    if (peakHourLabel != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your best sessions tend to start around $peakHourLabel",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ScatterPlot(
    workoutPoints: List<TimeOfDayChartPoint>,
    peakHour: Int?,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val dotColor = ProViolet
    val dotColorOther = ProSubGrey.copy(alpha = 0.5f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val labelColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val labelTextSize = MaterialTheme.typography.labelSmall.fontSize

    val maxVolume = remember(workoutPoints) {
        workoutPoints.maxOfOrNull { it.totalVolume } ?: 1.0
    }

    Canvas(modifier = modifier) {
        val textSize = labelTextSize.toPx()
        val paddingLeft = 48.dp.toPx()
        val paddingBottom = 28.dp.toPx()
        val paddingTop = (textSize + 6.dp.toPx()).coerceAtLeast(20.dp.toPx())
        val paddingRight = 8.dp.toPx()

        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        // ── Draw axes ─────────────────────────────────────────────────────────
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, paddingTop),
            end = Offset(paddingLeft, size.height - paddingBottom),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = axisColor,
            start = Offset(paddingLeft, size.height - paddingBottom),
            end = Offset(size.width - paddingRight, size.height - paddingBottom),
            strokeWidth = 1.dp.toPx()
        )

        val textPaint = android.graphics.Paint().apply {
            color = labelColorArgb
            this.textSize = textSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // ── X-axis labels: 6am, 9am, 12pm, 3pm, 6pm, 9pm ────────────────────
        val xLabels = listOf(6 to "6am", 9 to "9am", 12 to "12pm", 15 to "3pm", 18 to "6pm", 21 to "9pm")
        xLabels.forEach { (hour, label) ->
            val x = paddingLeft + (hour / 23f) * chartWidth
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - paddingBottom + textSize + 4.dp.toPx(),
                textPaint
            )
        }

        // ── Y-axis labels: 0, max, and unit ──────────────────────────────────
        val yLabelPaint = android.graphics.Paint().apply {
            color = labelColorArgb
            this.textSize = textSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val yMaxLabel = if (maxVolume >= 1_000.0) {
            "${"%.0f".format(maxVolume / 1_000)}K"
        } else {
            "${"%.0f".format(maxVolume)}"
        }
        drawContext.canvas.nativeCanvas.drawText(
            yMaxLabel,
            paddingLeft - 4.dp.toPx(),
            paddingTop + textSize,
            yLabelPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "0",
            paddingLeft - 4.dp.toPx(),
            size.height - paddingBottom,
            yLabelPaint
        )

        // ── Y-axis unit label above Y-axis ────────────────────────────────────
        val unitLabelPaint = android.graphics.Paint().apply {
            color = labelColorArgb
            this.textSize = textSize
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(
            unitLabel.uppercase(),
            paddingLeft / 2f,
            textSize,
            unitLabelPaint
        )

        // ── Scatter dots ──────────────────────────────────────────────────────
        workoutPoints.forEach { point ->
            val x = paddingLeft + (point.startHour / 23f) * chartWidth
            val y = paddingTop + chartHeight - (point.totalVolume / maxVolume * chartHeight).toFloat()
            val isPeak = point.startHour == peakHour
            val color = if (isPeak) dotColor else dotColorOther
            val radius = if (isPeak) 7.dp.toPx() else 5.dp.toPx()
            drawCircle(color = color, radius = radius, center = Offset(x, y.toFloat()))
        }
    }
}
