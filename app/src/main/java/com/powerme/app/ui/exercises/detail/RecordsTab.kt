package com.powerme.app.ui.exercises.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Date

@Composable
internal fun RecordsTabContent(
    prs: PersonalRecords?,
    userBodyWeightKg: Double?,
    overloadSuggestion: OverloadSuggestion
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Personal Records — full 2×2 grid
        PersonalRecordsSection(prs = prs, userBodyWeightKg = userBodyWeightKg)

        SectionDivider()

        // Progressive Overload
        ProgressiveOverloadSection(suggestion = overloadSuggestion)

        SectionDivider()

        // Lifetime stats
        LifetimeStatsSection(prs = prs)
    }
}

// ── Personal Records (full grid) ────────────────────────────────────────────

@Composable
private fun PersonalRecordsSection(prs: PersonalRecords?, userBodyWeightKg: Double?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "PERSONAL RECORDS")

        if (prs == null || (prs.bestE1RM == null && prs.bestSetWeight == null &&
                    prs.bestSessionVolume == null && prs.bestTotalReps == null)) {
            EmptySectionPlaceholder("No records yet — complete a workout to start tracking.")
            return
        }

        Column(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrStatCard(
                    label = "Best e1RM",
                    value = prs.bestE1RM?.let { "%.1f kg".format(it) } ?: "—",
                    date = prs.bestE1RMTimestampMs?.let { dateFormat.format(Date(it)) },
                    subLine = if (prs.bestE1RM != null && userBodyWeightKg != null && userBodyWeightKg > 0) {
                        "%.2f× bodyweight".format(prs.bestE1RM / userBodyWeightKg)
                    } else null,
                    modifier = Modifier.weight(1f)
                )
                PrStatCard(
                    label = "Best Set",
                    value = if (prs.bestSetWeight != null && prs.bestSetReps != null)
                        "%.1f kg × %d".format(prs.bestSetWeight, prs.bestSetReps) else "—",
                    date = prs.bestSetTimestampMs?.let { dateFormat.format(Date(it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrStatCard(
                    label = "Best Volume",
                    value = prs.bestSessionVolume?.let {
                        if (it >= 1000) "%.1f t".format(it / 1000.0) else "%.0f kg".format(it)
                    } ?: "—",
                    date = prs.bestSessionTimestampMs?.let { dateFormat.format(Date(it)) },
                    modifier = Modifier.weight(1f)
                )
                PrStatCard(
                    label = "Most Reps",
                    value = prs.bestTotalReps?.let { "$it reps" } ?: "—",
                    date = prs.bestTotalRepsTimestampMs?.let { dateFormat.format(Date(it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Progressive Overload ────────────────────────────────────────────────────

@Composable
private fun ProgressiveOverloadSection(suggestion: OverloadSuggestion) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "NEXT STEP")

        when (suggestion) {
            is OverloadSuggestion.NoData -> {
                EmptySectionPlaceholder("Complete a workout with this exercise to get a suggestion.")
            }
            is OverloadSuggestion.IncreaseReps -> {
                OverloadCard(
                    icon = Icons.Default.AddCircleOutline,
                    message = "Last: %.1f kg × %d. Try %.1f kg × %d (%d sets)".format(
                        suggestion.currentWeight, suggestion.currentReps,
                        suggestion.currentWeight, suggestion.targetReps, suggestion.targetSets
                    )
                )
            }
            is OverloadSuggestion.IncreaseWeight -> {
                OverloadCard(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    message = "Last: %.1f kg × %d. Try %.1f kg × %d (%d sets)".format(
                        suggestion.currentWeight, suggestion.targetReps,
                        suggestion.suggestedWeight, suggestion.targetReps, suggestion.targetSets
                    )
                )
            }
        }
    }
}

// ── Lifetime Stats ──────────────────────────────────────────────────────────

@Composable
private fun LifetimeStatsSection(prs: PersonalRecords?) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "LIFETIME STATS")

        if (prs == null || (prs.bestTotalReps == null && prs.bestSessionVolume == null)) {
            EmptySectionPlaceholder("Not enough data yet.")
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (prs.bestTotalReps != null) {
                PrStatCard(
                    label = "Best Session Reps",
                    value = "${prs.bestTotalReps} reps",
                    date = prs.bestTotalRepsTimestampMs?.let { dateFormat.format(Date(it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (prs.bestSessionVolume != null) {
                PrStatCard(
                    label = "Best Session Volume",
                    value = if (prs.bestSessionVolume >= 1000)
                        "%.1f t".format(prs.bestSessionVolume / 1000.0)
                    else "%.0f kg".format(prs.bestSessionVolume),
                    date = prs.bestSessionTimestampMs?.let { dateFormat.format(Date(it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
