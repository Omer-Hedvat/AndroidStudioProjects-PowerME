package com.powerme.app.ui.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.TimerRed
import com.powerme.app.util.UnitConverter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class HcAvailability { CHECKING, UNAVAILABLE, AVAILABLE_NOT_GRANTED, AVAILABLE_GRANTED }

data class BodyVitalsState(
    val hcAvailability: HcAvailability = HcAvailability.CHECKING,
    val age: Int? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val heightCm: Double? = null,
    val bmi: Double? = null,
    val weightDelta7d: Double? = null,
    val bodyFatDelta7d: Double? = null,
    val sleepMinutes: Int? = null,
    val hrvMs: Double? = null,
    val rhrBpm: Int? = null,
    val stepsToday: Int? = null,
    val lastSyncTimestamp: Long? = null,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    // Extended reads (v43+)
    val sleepScore: Int? = null,
    val avgHeartRateBpm: Int? = null,
    val vo2MaxMlKgMin: Double? = null,
    val spo2Percent: Double? = null,
    val lowSpO2Flag: Boolean = false,
    val activeCaloriesKcal: Double? = null,
    val distanceMetres: Double? = null
)

@Composable
fun BodyVitalsCard(
    state: BodyVitalsState,
    onSyncClick: () -> Unit,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier,
    unitSystem: UnitSystem = UnitSystem.METRIC
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        when (state.hcAvailability) {
            HcAvailability.CHECKING -> CheckingContent()
            HcAvailability.UNAVAILABLE -> UnavailableContent()
            HcAvailability.AVAILABLE_NOT_GRANTED -> NotConnectedContent(onConnectClick = onConnectClick)
            HcAvailability.AVAILABLE_GRANTED -> ConnectedContent(state = state, onSyncClick = onSyncClick, unitSystem = unitSystem)
        }
    }
}

@Composable
private fun CheckingContent() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            text = "Checking Health Connect…",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun UnavailableContent() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "BODY & VITALS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Install Health Connect to track body & recovery metrics.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NotConnectedContent(onConnectClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "BODY & VITALS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Connect Health Connect to see your body composition, recovery metrics, and daily activity.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onConnectClick,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun ConnectedContent(state: BodyVitalsState, onSyncClick: () -> Unit, unitSystem: UnitSystem = UnitSystem.METRIC) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BODY & VITALS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = lastSyncLabel(state.lastSyncTimestamp),
                    fontSize = 11.sp,
                    color = ProSubGrey
                )
            }
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSyncClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Sync Health Connect",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (state.syncError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.syncError,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 1: Age | Weight | BMI
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Age",
                value = state.age?.let { "$it yr" } ?: "--"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Weight",
                value = state.weightKg?.let { UnitConverter.formatWeight(it, unitSystem) } ?: "--",
                delta = state.weightDelta7d?.let { if (unitSystem == UnitSystem.IMPERIAL) UnitConverter.kgToLbs(it) else it }
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "BMI",
                value = state.bmi?.let { "%.1f".format(it) } ?: "--",
                subValue = state.bmi?.let { bmiLabel(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: Body Fat | Height | Steps (distance sub-label when available)
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Body Fat",
                value = state.bodyFatPct?.let { "${"%.1f".format(it)}%" } ?: "--",
                delta = state.bodyFatDelta7d
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Height",
                value = state.heightCm?.let { UnitConverter.formatHeight(it, unitSystem) } ?: "--"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Steps",
                value = state.stepsToday?.let { String.format(Locale.US, "%,d", it) } ?: "--",
                subValue = state.distanceMetres?.let { formatDistance(it, unitSystem) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: Sleep (score sub-label) | HRV | RHR
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "Sleep",
                value = state.sleepMinutes?.let { formatSleep(it) } ?: "--",
                subValue = state.sleepScore?.let { "Score: $it" }
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "HRV",
                value = state.hrvMs?.let { "${"%.0f".format(it)} ms" } ?: "--"
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "RHR",
                value = state.rhrBpm?.let { "$it bpm" } ?: "--"
            )
        }

        // Row 5: Avg HR | VO2 Max | SpO2 — shown only when at least one value is present
        if (state.avgHeartRateBpm != null || state.vo2MaxMlKgMin != null || state.spo2Percent != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Avg HR",
                    value = state.avgHeartRateBpm?.let { "$it bpm" } ?: "--"
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "VO\u2082 Max",
                    value = state.vo2MaxMlKgMin?.let { "%.1f".format(it) } ?: "--",
                    subValue = state.vo2MaxMlKgMin?.let { vo2MaxTier(it) }
                )
                SpO2Tile(
                    modifier = Modifier.weight(1f),
                    spo2Percent = state.spo2Percent,
                    lowFlag = state.lowSpO2Flag
                )
            }
        }

        // Row 6: Active Calories — shown only when present
        if (state.activeCaloriesKcal != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Active Cal",
                    value = String.format(Locale.US, "%,.0f kcal", state.activeCaloriesKcal),
                    subValue = state.stepsToday?.let { steps ->
                        val neat = state.activeCaloriesKcal + steps * 0.04
                        "NEAT: ${String.format(Locale.US, "%,.0f", neat)} kcal"
                    }
                )
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: Double? = null,
    subValue: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = ProSubGrey,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        when {
            delta != null && abs(delta) >= 0.05 -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val isPositive = delta > 0
                    val icon = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                    val tint = if (isPositive) TimerRed else TimerGreen
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "%.1f".format(abs(delta)),
                        fontSize = 10.sp,
                        color = tint
                    )
                }
            }
            subValue != null -> {
                Text(
                    text = subValue,
                    fontSize = 10.sp,
                    color = ProSubGrey
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SpO2Tile(modifier: Modifier, spo2Percent: Double?, lowFlag: Boolean) {
    val dotColor = when {
        spo2Percent == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        spo2Percent < 92.0 -> TimerRed
        spo2Percent < 95.0 -> MaterialTheme.colorScheme.tertiary
        else -> TimerGreen
    }
    Column(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SpO\u2082", fontSize = 10.sp, color = ProSubGrey, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = spo2Percent?.let { "${"%.0f".format(it)}%" } ?: "--",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
    }
}

private fun vo2MaxTier(value: Double): String = when {
    value > 62 -> "Superior"
    value > 52 -> "Excellent"
    value > 42 -> "Good"
    value > 33 -> "Fair"
    value > 25 -> "Poor"
    else -> "Very Poor"
}

private fun formatDistance(metres: Double, unitSystem: UnitSystem): String =
    UnitConverter.formatDistance(metres / 1000.0, unitSystem)

private fun bmiLabel(bmi: Double): String = when {
    bmi < 18.5 -> "underweight"
    bmi < 25.0 -> "healthy"
    bmi < 30.0 -> "overweight"
    else -> "obese"
}

private fun formatSleep(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun lastSyncLabel(timestamp: Long?): String {
    if (timestamp == null) return "No sync yet"
    val diffMs = System.currentTimeMillis() - timestamp
    return when {
        diffMs < TimeUnit.MINUTES.toMillis(1) -> "Just synced"
        diffMs < TimeUnit.HOURS.toMillis(1) -> {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
            "Last sync: ${mins}m ago"
        }
        diffMs < TimeUnit.HOURS.toMillis(24) -> {
            val hrs = TimeUnit.MILLISECONDS.toHours(diffMs)
            "Last sync: ${hrs}h ago"
        }
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(diffMs)
            "Last sync: ${days}d ago"
        }
    }
}
