package com.powerme.app.ui.metrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerme.app.analytics.ProgressionAnomaly
import com.powerme.app.analytics.VolumeLoadAnomaly
import com.powerme.app.analytics.WeeklyInsights
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetricsScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: MetricsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-check HC permissions every time the tab becomes visible.
    // Needed because saveState/restoreState keeps the ViewModel alive across tab switches,
    // so init{} only runs once — permissions granted on another tab would otherwise be missed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadBodyVitals()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Body & Vitals ──────────────────────────────
        item {
            BodyVitalsCard(
                state = uiState.bodyVitals,
                onSyncClick = { viewModel.syncHealthConnect() },
                onConnectClick = onNavigateToSettings
            )
        }

        // ── Boaz Insights ─────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BOAZ'S INSIGHTS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Statistical Performance Analysis",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { viewModel.loadInsights() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        when {
            uiState.isLoading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            uiState.error != null -> {
                item {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            uiState.weeklyInsights != null -> {
                val insights = uiState.weeklyInsights!!

                item { StatusCard(insights = insights) }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(insights.summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                        }
                    }
                }

                if (insights.volumeLoadAnomalies.isNotEmpty()) {
                    item {
                        Text("Volume-Load Anomalies", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    items(insights.volumeLoadAnomalies) { VolumeAnomalyCard(it) }
                }

                if (insights.progressionAnomalies.isNotEmpty()) {
                    item {
                        Text("Progression Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    items(insights.progressionAnomalies) { ProgressionAnomalyCard(it) }
                }

                insights.healthPerformanceCorrelation?.let { correlation ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Sleep-Performance Correlation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("r = ${"%.3f".format(correlation.correlationCoefficient)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(correlation.interpretation, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(correlation.recommendation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                if (insights.recommendations.isNotEmpty()) {
                    item {
                        Text("Committee Recommendations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    items(insights.recommendations) { recommendation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(recommendation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(insights: WeeklyInsights) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val weekStart = dateFormat.format(Date(insights.weekStartDate))
    val weekEnd = dateFormat.format(Date(insights.weekEndDate))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Week: $weekStart - $weekEnd", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(insights.status, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun VolumeAnomalyCard(anomaly: VolumeLoadAnomaly) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.US)
    val date = dateFormat.format(Date(anomaly.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (anomaly.type == "Positive Outlier") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("${anomaly.type} ($date)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Volume: ${anomaly.volumeLoad.toInt()} kg", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                Text("Z-score: ${"%.2f".format(anomaly.zScore)}σ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                anomaly.healthContext?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun ProgressionAnomalyCard(anomaly: ProgressionAnomaly) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(anomaly.exerciseName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Previous: ${"%.1f".format(anomaly.previousE1RM)} kg", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Text("Current: ${"%.1f".format(anomaly.currentE1RM)} kg", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Change: ${if (anomaly.rateOfChange >= 0) "+" else ""}${"%.1f".format(anomaly.rateOfChange * 100)}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
                Text(anomaly.flag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
