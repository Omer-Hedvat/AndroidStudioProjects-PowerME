package com.omerhedvat.powerme.ui.metrics

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.analytics.ProgressionAnomaly
import com.omerhedvat.powerme.analytics.VolumeLoadAnomaly
import com.omerhedvat.powerme.analytics.WeeklyInsights
import com.omerhedvat.powerme.ui.theme.ElectricBlue
import com.omerhedvat.powerme.ui.theme.NavySurface
import com.omerhedvat.powerme.ui.theme.NeonBlue
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        color = NeonBlue
                    )
                    Text(
                        text = "Statistical Performance Analysis",
                        fontSize = 12.sp,
                        color = NeonBlue.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { viewModel.loadInsights() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonBlue)
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
                        CircularProgressIndicator(color = NeonBlue)
                    }
                }
            }
            uiState.error != null -> {
                item {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = NeonBlue,
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
                        colors = CardDefaults.cardColors(containerColor = NavySurface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(insights.summary, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }

                if (insights.volumeLoadAnomalies.isNotEmpty()) {
                    item {
                        Text("Volume-Load Anomalies", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    }
                    items(insights.volumeLoadAnomalies) { VolumeAnomalyCard(it) }
                }

                if (insights.progressionAnomalies.isNotEmpty()) {
                    item {
                        Text("Progression Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    }
                    items(insights.progressionAnomalies) { ProgressionAnomalyCard(it) }
                }

                insights.healthPerformanceCorrelation?.let { correlation ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NavySurface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Sleep-Performance Correlation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("r = ${"%.3f".format(correlation.correlationCoefficient)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                                Text(correlation.interpretation, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(correlation.recommendation, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                if (insights.recommendations.isNotEmpty()) {
                    item {
                        Text("Committee Recommendations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                    }
                    items(insights.recommendations) { recommendation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NavySurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(20.dp))
                                Text(recommendation, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
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
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Week: $weekStart - $weekEnd", fontSize = 12.sp, color = NeonBlue.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(insights.status, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
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
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (anomaly.type == "Positive Outlier") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = NeonBlue,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("${anomaly.type} ($date)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Volume: ${anomaly.volumeLoad.toInt()} kg", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                Text("Z-score: ${"%.2f".format(anomaly.zScore)}σ", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                anomaly.healthContext?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun ProgressionAnomalyCard(anomaly: ProgressionAnomaly) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(anomaly.exerciseName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Previous: ${"%.1f".format(anomaly.previousE1RM)} kg", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                Text("Current: ${"%.1f".format(anomaly.currentE1RM)} kg", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Change: ${if (anomaly.rateOfChange >= 0) "+" else ""}${"%.1f".format(anomaly.rateOfChange * 100)}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NeonBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = NeonBlue.copy(alpha = 0.2f))) {
                Text(anomaly.flag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonBlue, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
