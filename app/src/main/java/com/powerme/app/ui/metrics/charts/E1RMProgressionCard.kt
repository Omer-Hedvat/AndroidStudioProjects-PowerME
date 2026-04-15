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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.patrykandpatrick.vico.compose.cartesian.axis.*
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseWithHistory
import com.powerme.app.ui.metrics.E1RMProgressionData
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Displays estimated 1-rep max progression for the selected exercise,
 * with a 3-session moving average overlay and a scrollable exercise picker.
 *
 * Consumes [TrendsViewModel.e1rmData], [TrendsViewModel.exercisePickerItems],
 * and [TrendsViewModel.selectedExerciseId] StateFlows.
 */
@Composable
fun E1RMProgressionCard(
    e1rmData: E1RMProgressionData?,
    exercisePickerItems: List<ExerciseWithHistory>,
    selectedExerciseId: Long?,
    unitSystem: UnitSystem,
    onExerciseSelected: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val rawPoints = e1rmData?.points.orEmpty()
    val maPoints = e1rmData?.movingAverage.orEmpty()

    // Stable reference to timestamps — read by the x-axis formatter at draw time.
    val timestampsState = rememberUpdatedState(rawPoints.map { it.timestampMs })
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }

    val xFormatter = remember {
        CartesianValueFormatter { _, value, _ ->
            val ts = timestampsState.value.getOrNull(value.roundToInt())
                ?: return@CartesianValueFormatter ""
            dateFormat.format(Date(ts))
        }
    }

    val yFormatter = remember(unitSystem) {
        val label = UnitConverter.weightLabel(unitSystem)
        CartesianValueFormatter { _, value, _ -> "${"%.0f".format(value)} $label" }
    }

    val axisLabel = rememberTextComponent(color = ProSubGrey, textSize = 11.sp)

    // Push series to Vico whenever source data or unit system changes.
    // Always call runTransaction — even when data is insufficient — to clear any stale model
    // that remains from a previously selected exercise. Without this, CartesianChartHost may
    // leave composition while the producer still holds old data, causing a Vico crash.
    LaunchedEffect(e1rmData, unitSystem) {
        if (rawPoints.size >= 2) {
            val rawValues = rawPoints.map { UnitConverter.displayWeight(it.e1rm, unitSystem) }
            val maValues = maPoints.map { UnitConverter.displayWeight(it.e1rm, unitSystem) }
            modelProducer.runTransaction {
                lineSeries {
                    series(rawValues)
                    if (maValues.isNotEmpty()) series(maValues)
                }
            }
        } else {
            modelProducer.runTransaction { }
        }
    }

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
                    text = "STRENGTH PROGRESSION",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ProSubGrey,
                    letterSpacing = 1.sp
                )
                e1rmData?.percentChange?.let { pct ->
                    val positive = pct >= 0
                    Text(
                        text = "${if (positive) "+" else ""}${"%.1f".format(pct)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (positive) TimerGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Exercise picker chip row ──────────────────────────────────────
            if (exercisePickerItems.isEmpty()) {
                Text(
                    text = "Complete workouts with weighted sets to track strength",
                    fontSize = 12.sp,
                    color = ProSubGrey.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(exercisePickerItems, key = { it.id }) { exercise ->
                        FilterChip(
                            selected = exercise.id == selectedExerciseId,
                            onClick = { onExerciseSelected(exercise.id, exercise.name) },
                            label = { Text(exercise.name.take(22), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Chart or empty state ──────────────────────────────────────────
            when {
                exercisePickerItems.isEmpty() -> {
                    // No picker items — empty state text already shown above
                }
                rawPoints.size < 2 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val exerciseName = e1rmData?.exerciseName
                            ?: exercisePickerItems.firstOrNull { it.id == selectedExerciseId }?.name
                        Text(
                            text = if (exerciseName != null)
                                "Log at least 2 sessions of $exerciseName\nto see strength progression"
                            else
                                "Select an exercise above to see progression",
                            fontSize = 13.sp,
                            color = ProSubGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    val hasMa = maPoints.isNotEmpty()

                    val rawLine = LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(
                            fill(VicoChartHelpers.LinePrimary)
                        ),
                        thickness = 2.dp
                    )
                    val maLine = LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(
                            fill(VicoChartHelpers.LineSecondary.copy(alpha = 0.8f))
                        ),
                        thickness = 1.5.dp
                    )

                    val lineList = remember(hasMa, rawLine, maLine) {
                        if (hasMa) listOf(rawLine, maLine) else listOf(rawLine)
                    }

                    CartesianChartHost(
                        chart = rememberCartesianChart(
                            rememberLineCartesianLayer(
                                lineProvider = LineCartesianLayer.LineProvider.series(lineList)
                            ),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    // ── Legend ────────────────────────────────────────────────
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChartLegendDot(color = VicoChartHelpers.LinePrimary, label = "Raw e1RM")
                        if (hasMa) {
                            ChartLegendDot(color = VicoChartHelpers.LineSecondary, label = "3-session avg")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
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
