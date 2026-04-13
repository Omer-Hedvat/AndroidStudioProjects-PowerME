package com.powerme.app.ui.metrics

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProError
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.ReadinessAmber
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.TimerRed
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hero readiness gauge — 240° arc with score needle.
 * See TRENDS_SPEC.md §3.2.
 */
@Composable
fun ReadinessGaugeCard(
    readinessScore: ReadinessEngine.ReadinessScore,
    hcAvailability: HcAvailability,
    hrvDelta: Double?,
    rhrDelta: Double?,
    sleepMinutes: Int?,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = "READINESS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (readinessScore) {
                is ReadinessEngine.ReadinessScore.NoData -> NoDataContent(hcAvailability)
                is ReadinessEngine.ReadinessScore.Calibrating -> CalibratingContent(readinessScore)
                is ReadinessEngine.ReadinessScore.Score -> ScoreContent(
                    score = readinessScore,
                    hrvDelta = hrvDelta,
                    rhrDelta = rhrDelta,
                    sleepMinutes = sleepMinutes
                )
            }
        }
    }
}

@Composable
private fun NoDataContent(hcAvailability: HcAvailability) {
    val message = when (hcAvailability) {
        HcAvailability.AVAILABLE_GRANTED ->
            "Sync Health Connect to start tracking readiness. Needs HRV, RHR, or sleep data."
        HcAvailability.AVAILABLE_NOT_GRANTED ->
            "Connect Health Connect in Settings to unlock Readiness"
        HcAvailability.UNAVAILABLE ->
            "Health Connect is not available on this device"
        HcAvailability.CHECKING ->
            "Checking Health Connect…"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = ProSubGrey,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun CalibratingContent(state: ReadinessEngine.ReadinessScore.Calibrating) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = ReadinessAmber
        )
        Text(
            text = "Calibrating… sync for ${state.daysRequired - state.daysCollected} more days",
            fontSize = 14.sp,
            color = ReadinessAmber
        )
        Text(
            text = "${state.daysCollected} / ${state.daysRequired} days collected",
            fontSize = 12.sp,
            color = ProSubGrey
        )
    }
}

@Composable
private fun ScoreContent(
    score: ReadinessEngine.ReadinessScore.Score,
    hrvDelta: Double?,
    rhrDelta: Double?,
    sleepMinutes: Int?
) {
    val tierColor = when (score.tier) {
        ReadinessEngine.Tier.RECOVERED -> TimerGreen
        ReadinessEngine.Tier.MODERATE -> ReadinessAmber
        ReadinessEngine.Tier.FATIGUED -> ProError
    }
    val tierLabel = when (score.tier) {
        ReadinessEngine.Tier.RECOVERED -> "RECOVERED"
        ReadinessEngine.Tier.MODERATE -> "MODERATE"
        ReadinessEngine.Tier.FATIGUED -> "FATIGUED"
    }

    // Gauge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        ReadinessArc(
            score = score.value,
            modifier = Modifier.size(180.dp)
        )
        // Score text centered inside the arc
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${score.value}",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = tierColor
            )
            Text(
                text = tierLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tierColor,
                letterSpacing = 1.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Sub-metrics row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SubMetric(
            label = "HRV",
            delta = hrvDelta,
            suffix = "ms"
        )
        SubMetric(
            label = "RHR",
            delta = rhrDelta,
            suffix = "bpm",
            invertColor = true // lower RHR is better
        )
        SubMetric(
            label = "Sleep",
            value = sleepMinutes?.let { formatSleepDuration(it) },
            isGood = sleepMinutes != null && sleepMinutes >= 420 // 7h threshold
        )
    }
}

@Composable
private fun ReadinessArc(
    score: Int,
    modifier: Modifier = Modifier
) {
    val sweepAngle = 240f
    val startAngle = 150f // Start at bottom-left (150° from 3 o'clock)
    val scoreAngle = startAngle + (score / 100f) * sweepAngle

    Canvas(modifier = modifier) {
        val strokeWidth = 16.dp.toPx()
        val padding = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(padding, padding)

        // Background arc (dim)
        drawArc(
            color = Color(0xFF2A2A2A),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Gradient arc (colored portion up to score)
        val scoreSweep = (score / 100f) * sweepAngle
        drawArc(
            brush = Brush.sweepGradient(
                0f to ProError,
                0.4f to ReadinessAmber,
                0.7f to TimerGreen,
                1f to TimerGreen
            ),
            startAngle = startAngle,
            sweepAngle = scoreSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Needle dot at the score position
        val needleRadius = 6.dp.toPx()
        val arcRadius = arcSize.width / 2
        val centerX = size.width / 2
        val centerY = size.height / 2
        val angleRad = Math.toRadians(scoreAngle.toDouble())
        val dotX = centerX + arcRadius * cos(angleRad).toFloat()
        val dotY = centerY + arcRadius * sin(angleRad).toFloat()

        drawCircle(
            color = Color.White,
            radius = needleRadius,
            center = Offset(dotX, dotY)
        )
    }
}

@Composable
private fun SubMetric(
    label: String,
    delta: Double? = null,
    suffix: String = "",
    invertColor: Boolean = false,
    value: String? = null,
    isGood: Boolean? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = ProSubGrey
        )

        if (value != null) {
            // Direct value display (e.g., Sleep)
            val color = when (isGood) {
                true -> TimerGreen
                false -> TimerRed
                null -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        } else if (delta != null) {
            // Delta display with arrow
            val isPositive = delta > 0
            val isGoodDelta = if (invertColor) !isPositive else isPositive
            val color = if (isGoodDelta) TimerGreen else TimerRed
            val arrow = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
            val sign = if (isPositive) "+" else ""

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = arrow,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "$sign${"%.1f".format(delta)} $suffix",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
        } else {
            Text(
                text = "--",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ProSubGrey
            )
        }
    }
}

private fun formatSleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "${hours}h ${mins}m"
}
