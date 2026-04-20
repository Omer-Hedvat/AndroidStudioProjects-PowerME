package com.powerme.app.ui.exercises.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.Joint
import com.powerme.app.ui.metrics.charts.BodyOutlineCanvas
import com.powerme.app.ui.theme.FormCuesGold
import com.powerme.app.ui.theme.PowerMeDefaults
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutTabContent(
    uiState: ExerciseDetailUiState,
    affectedJoints: Set<Joint>,
    onNavigateToExerciseDetail: (Long) -> Unit,
    onNavigateToRecordsTab: () -> Unit,
    onUserNoteChanged: (String) -> Unit
) {
    val exercise = uiState.exercise ?: return
    val primaryJoints = remember(exercise.primaryJoints) {
        Joint.fromJsonString(exercise.primaryJoints)
    }
    val secondaryJoints = remember(exercise.secondaryJoints) {
        Joint.fromJsonString(exercise.secondaryJoints)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Compact PR summary (tappable → RECORDS tab)
        val prs = uiState.personalRecords
        if (prs != null && (prs.bestE1RM != null || prs.bestSetWeight != null)) {
            item(key = "compact_pr") {
                CompactPrRow(prs = prs, userBodyWeightKg = uiState.userBodyWeightKg, onClick = onNavigateToRecordsTab)
            }
            item(key = "about_div_pr") { SectionDivider() }
        }

        // Joint indicators
        if (primaryJoints.isNotEmpty() || secondaryJoints.isNotEmpty()) {
            item(key = "joints") {
                JointIndicatorsSection(
                    primaryJoints = primaryJoints,
                    secondaryJoints = secondaryJoints,
                    affectedJoints = affectedJoints
                )
            }
            item(key = "about_div_joints") { SectionDivider() }
        }

        // Form cues
        if (!exercise.setupNotes.isNullOrBlank()) {
            item(key = "form_cues") { FormCuesSection(cues = exercise.setupNotes!!) }
            item(key = "about_div_cues") { SectionDivider() }
        }

        // Training zones
        item(key = "zones") {
            SetRepZoneGuideSection(bestSetReps = prs?.bestSetReps)
        }
        item(key = "about_div_zones") { SectionDivider() }

        // Warm-up ramp
        if (uiState.warmUpRamp.isNotEmpty()) {
            item(key = "warmup") { WarmUpRampSection(ramp = uiState.warmUpRamp) }
            item(key = "about_div_warmup") { SectionDivider() }
        }

        // Muscle activation
        item(key = "muscle") {
            MuscleActivationSection(stressColors = uiState.stressColors)
        }
        item(key = "about_div_muscle") { SectionDivider() }

        // Alternative exercises
        if (uiState.alternatives.isNotEmpty()) {
            item(key = "alternatives") {
                AlternativeExercisesSection(
                    alternatives = uiState.alternatives,
                    onExerciseClick = onNavigateToExerciseDetail
                )
            }
            item(key = "about_div_alt") { SectionDivider() }
        }

        // User notes
        item(key = "notes") {
            UserNotesSection(
                note = exercise.userNote,
                onNoteChanged = onUserNoteChanged
            )
        }
    }
}

// ── Compact PR Row (cross-link to RECORDS tab) ─────────────────────────────

@Composable
private fun CompactPrRow(
    prs: PersonalRecords,
    userBodyWeightKg: Double?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Best e1RM
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Best e1RM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = prs.bestE1RM?.let { "%.1f kg".format(it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Best Set
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Best Set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (prs.bestSetWeight != null && prs.bestSetReps != null)
                        "%.1f kg × %d".format(prs.bestSetWeight, prs.bestSetReps) else "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View records",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Joint Indicators ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JointIndicatorsSection(
    primaryJoints: List<Joint>,
    secondaryJoints: List<Joint>,
    affectedJoints: Set<Joint>
) {
    // No outer horizontal padding — SectionHeader handles its own 16dp inset
    Column {
        SectionHeader(title = "JOINTS")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            primaryJoints.forEach { joint ->
                val isAffected = joint in affectedJoints
                AssistChip(
                    onClick = {},
                    label = { Text(joint.displayName, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isAffected)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        labelColor = if (isAffected)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
            }
            if (primaryJoints.isNotEmpty() && secondaryJoints.isNotEmpty()) {
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                )
            }
            secondaryJoints.forEach { joint ->
                val isAffected = joint in affectedJoints
                AssistChip(
                    onClick = {},
                    label = { Text(joint.displayName, fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isAffected)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            Color.Transparent,
                        labelColor = if (isAffected)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

// ── Form Cues ───────────────────────────────────────────────────────────────

@Composable
private fun FormCuesSection(cues: String) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(cues) {
        if (cues.length > 120) cues.take(120) + "…" else cues
    }

    // No outer horizontal padding — SectionHeader handles its own 16dp inset
    Column {
        SectionHeader(title = "FORM CUES")
        Surface(
            color = FormCuesGold,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Text(
                        text = cues,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
                if (!expanded) {
                    Text(
                        text = preview,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
                if (cues.length > 120) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.align(Alignment.End).height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (expanded) "Read less" else "Read more",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// ── Training Zones ──────────────────────────────────────────────────────────

@Composable
private fun SetRepZoneGuideSection(bestSetReps: Int?) {
    val userZone = remember(bestSetReps) {
        when {
            bestSetReps == null -> null
            bestSetReps <= 5 -> "Strength"
            bestSetReps <= 12 -> "Hypertrophy"
            else -> "Endurance"
        }
    }

    // No outer horizontal padding — SectionHeader handles its own 16dp inset
    Column {
        SectionHeader(title = "TRAINING ZONES")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple("Strength", "1–5 reps", MaterialTheme.colorScheme.primary),
                Triple("Hypertrophy", "6–12 reps", MaterialTheme.colorScheme.secondary),
                Triple("Endurance", "13+ reps", MaterialTheme.colorScheme.tertiary)
            ).forEach { (zone, label, color) ->
                val isActive = zone == userZone
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isActive) color.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    border = if (isActive)
                        BorderStroke(1.5.dp, color)
                    else null
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = zone,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Warm-Up Ramp ────────────────────────────────────────────────────────────

@Composable
private fun WarmUpRampSection(ramp: List<WarmUpSet>) {
    // No outer horizontal padding — SectionHeader handles its own 16dp inset
    Column {
        SectionHeader(title = "WARM-UP RAMP")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Set", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.5f))
                    Text("Weight", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Text("Reps", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                ramp.forEachIndexed { index, set ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.5f))
                        Text("${"%.1f".format(set.weight)} kg (${set.percentageLabel})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f))
                        Text("${set.reps}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

// ── Muscle Activation ───────────────────────────────────────────────────────

@Composable
private fun MuscleActivationSection(stressColors: Map<String, Float>) {
    // No outer horizontal padding — canvas is full-width; SectionHeader handles its own 16dp
    Column {
        SectionHeader(title = "MUSCLE ACTIVATION")

        if (stressColors.isEmpty()) {
            EmptySectionPlaceholder("Activation data not available for this exercise.")
            return
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val regionColors = remember(stressColors, primaryColor) {
            stressColors.mapNotNull { (regionName, coeff) ->
                val region = BodyRegion.entries.find { it.name == regionName }
                    ?: return@mapNotNull null
                val color = lerp(Color.Transparent, primaryColor.copy(alpha = 0.85f), coeff)
                region to color
            }.toMap()
        }

        // Surface provides: (1) opaque background that isolates the canvas from sections
        // above/below, (2) shape-based clipping that constrains nativeCanvas drawing to
        // the 280dp height — fixes the visual bleed-through reported in BUG_about_tab_section_overlaps.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(bottom = 12.dp)
        ) {
            BodyOutlineCanvas(
                regionColors = regionColors,
                selectedRegion = null,
                onRegionTapped = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Alternative Exercises ───────────────────────────────────────────────────

@Composable
private fun AlternativeExercisesSection(
    alternatives: List<AlternativeExercise>,
    onExerciseClick: (Long) -> Unit
) {
    Column {
        SectionHeader(title = "ALTERNATIVES")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            items(alternatives) { alt ->
                AlternativeExerciseCard(
                    alternative = alt,
                    onClick = { onExerciseClick(alt.exercise.id) }
                )
            }
        }
    }
}

// ── User Notes ──────────────────────────────────────────────────────────────

@Composable
private fun UserNotesSection(note: String, onNoteChanged: (String) -> Unit) {
    // No outer horizontal padding — SectionHeader handles its own 16dp inset
    Column {
        SectionHeader(title = "MY NOTES")
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            placeholder = {
                Text(
                    "Add notes, tips, or reminders for this exercise…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            minLines = 3,
            colors = PowerMeDefaults.outlinedTextFieldColors(),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
