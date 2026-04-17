package com.powerme.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.UnitSystem
import com.powerme.app.ui.theme.FormCuesGold
import com.powerme.app.ui.theme.ProError
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.ReadinessAmber
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.buildSupersetColorMap
import com.powerme.app.ui.metrics.charts.VicoChartHelpers
import com.powerme.app.ui.workout.RoutineSyncType
import com.powerme.app.util.UnitConverter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToTrends: (Long) -> Unit,
    onConfirmSyncValues: () -> Unit = {},
    onConfirmSyncStructure: () -> Unit = {},
    onConfirmSyncBoth: () -> Unit = {},
    onDismissSync: () -> Unit = {},
    onSaveAsRoutine: (String) -> Unit = {},
    viewModel: WorkoutSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val workout = uiState.workout

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val supersetColorMap = remember(uiState.exerciseCards) {
        buildSupersetColorMap(uiState.exerciseCards.map { it.supersetGroupId })
    }

    var showSaveAsRoutineDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (workout != null) {
                        IconButton(onClick = { onNavigateToEdit(workout.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Session")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading || workout == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Hero Header ──────────────────────────────────────────────────────
            item {
                HeroHeaderCard(
                    workout = workout,
                    totalSets = uiState.totalSets,
                    prCount = uiState.prCount,
                    unitSystem = unitSystem
                )
            }

            // ── Session Rating ───────────────────────────────────────────────────
            item {
                SessionRatingRow(
                    currentRating = workout.sessionRating,
                    onRatingSelected = { viewModel.setSessionRating(it) }
                )
            }

            // ── Routine Sync CTAs (post-workout only) ────────────────────────────
            val pendingRoutineSync = uiState.pendingRoutineSync
            if (uiState.isPostWorkout && pendingRoutineSync != null) {
                item {
                    RoutineSyncCard(
                        syncType = pendingRoutineSync,
                        onConfirmValues = onConfirmSyncValues,
                        onConfirmStructure = onConfirmSyncStructure,
                        onConfirmBoth = onConfirmSyncBoth,
                        onDismiss = onDismissSync
                    )
                }
            }

            // ── Exercise Summary Cards ───────────────────────────────────────────
            if (uiState.exerciseCards.isNotEmpty()) {
                item {
                    SectionHeader("Exercises")
                }
                items(uiState.exerciseCards, key = { it.exerciseId }) { card ->
                    val borderColor = card.supersetGroupId?.let { supersetColorMap[it] }
                    ExerciseSummaryCard(
                        card = card,
                        unitSystem = unitSystem,
                        supersetBorderColor = borderColor,
                        onViewTrend = { onNavigateToTrends(card.exerciseId) }
                    )
                }
            }

            // ── Muscle Group Distribution ────────────────────────────────────────
            if (uiState.muscleGroupBars.isNotEmpty()) {
                item {
                    SectionHeader("Muscle Groups")
                }
                item {
                    MuscleGroupDistributionCard(bars = uiState.muscleGroupBars, unitSystem = unitSystem)
                }
            }

            // ── Notes ────────────────────────────────────────────────────────────
            item {
                SectionHeader("Notes")
            }
            item {
                var notesText by remember(workout.notes) { mutableStateOf(workout.notes ?: "") }
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add session notes…", color = ProSubGrey) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    maxLines = 6
                )
                // Debounce save: persist notes 800ms after the user stops typing
                LaunchedEffect(notesText) {
                    kotlinx.coroutines.delay(800)
                    if (notesText != (workout.notes ?: "")) {
                        viewModel.updateNotes(notesText)
                    }
                }
            }

            // ── Post-workout Done button ─────────────────────────────────────────
            if (uiState.isPostWorkout) {
                item {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { showSaveAsRoutineDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save as Routine")
                    }
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TimerGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showSaveAsRoutineDialog) {
        SaveAsRoutineDialog(
            onConfirm = { name ->
                onSaveAsRoutine(name)
                showSaveAsRoutineDialog = false
            },
            onDismiss = { showSaveAsRoutineDialog = false }
        )
    }
}

// ── Hero Header ──────────────────────────────────────────────────────────────

@Composable
private fun HeroHeaderCard(
    workout: com.powerme.app.data.database.Workout,
    totalSets: Int,
    prCount: Int,
    unitSystem: UnitSystem
) {
    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }
    val dateString = dateFormatter.format(Date(workout.timestamp))

    val durationText = remember(workout.durationSeconds) {
        val h = workout.durationSeconds / 3600
        val m = (workout.durationSeconds % 3600) / 60
        val s = workout.durationSeconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Workout name + PR badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = workout.routineName ?: "Workout",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (prCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = ReadinessAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "$prCount PR${if (prCount > 1) "s" else ""}",
                                    color = ReadinessAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                }
            }

            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = ProSubGrey
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Duration", value = durationText)
                StatItem(
                    label = "Volume",
                    value = UnitConverter.formatWeight(workout.totalVolume, unitSystem)
                )
                StatItem(label = "Sets", value = totalSets.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = ProSubGrey)
    }
}

// ── Session Rating ────────────────────────────────────────────────────────────

@Composable
private fun SessionRatingRow(
    currentRating: Int?,
    onRatingSelected: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Session Rating",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row {
                for (star in 1..5) {
                    IconButton(
                        onClick = { onRatingSelected(star) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        val filled = currentRating != null && star <= currentRating
                        Icon(
                            imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star star",
                            tint = if (filled) ReadinessAmber else ProSubGrey,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Routine Sync CTAs ─────────────────────────────────────────────────────────

@Composable
private fun RoutineSyncCard(
    syncType: RoutineSyncType,
    onConfirmValues: () -> Unit,
    onConfirmStructure: () -> Unit,
    onConfirmBoth: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Update Routine?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            when (syncType) {
                RoutineSyncType.VALUES -> {
                    Text(
                        "You used different weights or reps. Update your routine defaults?",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProSubGrey
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Keep Original") }
                        Button(onClick = onConfirmValues) { Text("Update Values") }
                    }
                }
                RoutineSyncType.STRUCTURE -> {
                    Text(
                        "You changed the exercise structure. Update your routine?",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProSubGrey
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Keep Original") }
                        Button(onClick = onConfirmStructure) { Text("Update Routine") }
                    }
                }
                RoutineSyncType.BOTH -> {
                    Text(
                        "You changed the structure and values. How would you like to update your routine?",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProSubGrey
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onConfirmBoth, modifier = Modifier.fillMaxWidth()) {
                            Text("Update Values & Structure")
                        }
                        OutlinedButton(onClick = onConfirmValues, modifier = Modifier.fillMaxWidth()) {
                            Text("Update Values Only")
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Keep Original")
                        }
                    }
                }
            }
        }
    }
}

// ── Exercise Summary Card ─────────────────────────────────────────────────────

@Composable
private fun ExerciseSummaryCard(
    card: ExerciseSummaryCard,
    unitSystem: UnitSystem,
    supersetBorderColor: Color?,
    onViewTrend: () -> Unit
) {
    val bestWeightText = UnitConverter.formatWeightRaw(card.bestSetWeight, unitSystem)
    val e1RMText = UnitConverter.formatWeight(card.e1RM, unitSystem)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Superset left border
            if (supersetBorderColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(supersetBorderColor)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Exercise name + muscle group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = card.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!card.muscleGroup.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = card.muscleGroup,
                            style = MaterialTheme.typography.labelSmall,
                            color = ProSubGrey
                        )
                    }
                }

                // Best set + e1RM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text("Best Set", style = MaterialTheme.typography.labelSmall, color = ProSubGrey)
                        Text(
                            "$bestWeightText × ${card.bestSetReps}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column {
                        Text("Est. 1RM", style = MaterialTheme.typography.labelSmall, color = ProSubGrey)
                        Text(
                            e1RMText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Badges row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Volume delta
                    if (card.volumeDeltaPercent != null) {
                        VolumeDeltaBadge(percent = card.volumeDeltaPercent)
                    }

                    // Avg RPE
                    if (card.avgRpe != null) {
                        Text(
                            "RPE %.1f".format(card.avgRpe),
                            style = MaterialTheme.typography.labelSmall,
                            color = ProSubGrey
                        )
                    }

                    // Golden zone badge
                    if (card.isGoldenZone) {
                        Surface(
                            color = ReadinessAmber.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "RPE 8–9 ✦",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = ReadinessAmber
                            )
                        }
                    }

                    // PR badge
                    if (card.isPR) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = "Personal Record",
                                tint = ReadinessAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "PR",
                                style = MaterialTheme.typography.labelSmall,
                                color = ReadinessAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // View Trend button
                TextButton(
                    onClick = onViewTrend,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "View Trend →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeDeltaBadge(percent: Double) {
    val isPositive = percent >= 0
    val isFlat = abs(percent) < 1.0
    val color = when {
        isFlat -> ProSubGrey
        isPositive -> TimerGreen
        else -> ProError
    }
    val icon = when {
        isFlat -> Icons.Default.TrendingFlat
        isPositive -> Icons.Default.TrendingUp
        else -> Icons.Default.TrendingDown
    }
    val label = if (isFlat) "±0%" else "${if (isPositive) "+" else ""}${"%.1f".format(percent)}%"

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ── Muscle Group Distribution ─────────────────────────────────────────────────

@Composable
private fun MuscleGroupDistributionCard(bars: List<MuscleGroupBar>, unitSystem: UnitSystem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            bars.forEach { bar ->
                val barColor = VicoChartHelpers.muscleGroupColor(bar.group)
                val volumeText = UnitConverter.formatWeight(bar.volume, unitSystem)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        bar.group,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(80.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { bar.fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.15f)
                    )
                    Text(
                        volumeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ProSubGrey,
                        modifier = Modifier.width(70.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = ProSubGrey,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SaveAsRoutineDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as Routine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
