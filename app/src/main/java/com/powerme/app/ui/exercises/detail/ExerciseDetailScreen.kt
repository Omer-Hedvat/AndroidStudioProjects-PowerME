package com.powerme.app.ui.exercises.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.ExerciseWorkoutHistoryRow
import com.powerme.app.data.database.Joint
import com.powerme.app.ui.exercises.detail.OverloadSuggestion.IncreaseReps
import com.powerme.app.ui.exercises.detail.OverloadSuggestion.IncreaseWeight
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.metrics.charts.BodyOutlineCanvas
import com.powerme.app.ui.metrics.charts.VicoChartHelpers
import com.powerme.app.ui.theme.FormCuesGold
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ReadinessAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToWorkout: (String) -> Unit = {},
    onNavigateToExerciseDetail: (Long) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val affectedJoints by viewModel.affectedJoints.collectAsState()

    val exercise = uiState.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = exercise?.name ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading || exercise == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        val primaryJoints = remember(exercise.primaryJoints) {
            Joint.fromJsonString(exercise.primaryJoints)
        }
        val secondaryJoints = remember(exercise.secondaryJoints) {
            Joint.fromJsonString(exercise.secondaryJoints)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Section 1: Hero animation
            item { ExerciseAnimationImage(exercise) }

            // Section 2: Header
            item {
                HeaderSection(
                    uiState = uiState,
                    affectedJoints = affectedJoints
                )
            }

            item { SectionDivider() }

            // Section 3: Joint Indicators
            if (primaryJoints.isNotEmpty() || secondaryJoints.isNotEmpty()) {
                item {
                    JointIndicatorsSection(
                        primaryJoints = primaryJoints,
                        secondaryJoints = secondaryJoints,
                        affectedJoints = affectedJoints
                    )
                }
                item { SectionDivider() }
            }

            // Section 4: Form Cues
            if (!exercise.setupNotes.isNullOrBlank()) {
                item { FormCuesSection(cues = exercise.setupNotes!!) }
                item { SectionDivider() }
            }

            // Section 5: Personal Records
            item {
                PersonalRecordsSection(
                    prs = uiState.personalRecords,
                    userBodyWeightKg = uiState.userBodyWeightKg
                )
            }

            item { SectionDivider() }

            // Section 6: Progressive Overload
            item { ProgressiveOverloadSection(suggestion = uiState.overloadSuggestion) }

            item { SectionDivider() }

            // Section 7: Trends
            item {
                TrendsSection(
                    trendData = uiState.trendData,
                    timeRange = uiState.timeRange,
                    onTimeRangeChanged = viewModel::onTimeRangeChanged,
                    e1rmProducer = viewModel.e1rmProducer,
                    maxWeightProducer = viewModel.maxWeightProducer,
                    volumeProducer = viewModel.volumeProducer,
                    bestSetProducer = viewModel.bestSetProducer,
                    rpeProducer = viewModel.rpeProducer
                )
            }

            item { SectionDivider() }

            // Section 8: Set/Rep Zone Guide
            item {
                SetRepZoneGuideSection(bestSetReps = uiState.personalRecords?.bestSetReps)
            }

            item { SectionDivider() }

            // Section 9: Warm-Up Ramp (hidden for bodyweight exercises)
            if (uiState.warmUpRamp.isNotEmpty()) {
                item { WarmUpRampSection(ramp = uiState.warmUpRamp) }
                item { SectionDivider() }
            }

            // Section 10: Muscle Activation
            item {
                MuscleActivationSection(stressColors = uiState.stressColors)
            }

            item { SectionDivider() }

            // Section 11: Alternative Exercises
            if (uiState.alternatives.isNotEmpty()) {
                item {
                    AlternativeExercisesSection(
                        alternatives = uiState.alternatives,
                        onExerciseClick = onNavigateToExerciseDetail
                    )
                }
                item { SectionDivider() }
            }

            // Section 12: Workout History
            item {
                WorkoutHistorySection(
                    history = uiState.workoutHistory,
                    hasMore = uiState.hasMoreHistory,
                    onLoadMore = viewModel::loadMoreHistory,
                    onWorkoutClick = onNavigateToWorkout
                )
            }

            item { SectionDivider() }

            // Section 13: User Notes
            item {
                UserNotesSection(
                    note = exercise.userNote,
                    onNoteChanged = viewModel::onUserNoteChanged
                )
            }
        }
    }
}

// ── Shared layout helpers ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

@Composable
private fun EmptySectionPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// ── Section 1: Hero ──────────────────────────────────────────────────────────

@Composable
private fun ExerciseAnimationImage(exercise: Exercise) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/exercise_animations/${exercise.searchName}.webp")
            .crossfade(true)
            .build(),
        contentDescription = "${exercise.name} demonstration",
        imageLoader = context.imageLoader,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ── Section 2: Header ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderSection(uiState: ExerciseDetailUiState, affectedJoints: Set<Joint>) {
    val exercise = uiState.exercise ?: return
    val hasInjuryWarning = remember(exercise, affectedJoints) {
        (Joint.fromJsonString(exercise.primaryJoints) +
            Joint.fromJsonString(exercise.secondaryJoints)).any { it in affectedJoints }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Injury warning banner
        if (hasInjuryWarning) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "You have an active health note for a joint used in this exercise.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Exercise type + metadata tags
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TagChip(exercise.muscleGroup, MaterialTheme.colorScheme.primary)
            TagChip(exercise.equipmentType, MaterialTheme.colorScheme.secondary)
            TagChip(exercise.exerciseType.name.lowercase().replaceFirstChar { it.uppercase() },
                MaterialTheme.colorScheme.tertiary)
        }

        // Last performed + session frequency
        val lastPerformed = uiState.lastPerformed
        val sessionCount = uiState.sessionCount
        if (lastPerformed != null || sessionCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lastPerformed != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Last: ${dateFormat.format(Date(lastPerformed.timestampMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (sessionCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$sessionCount session${if (sessionCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.13f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ── Section 3: Joint Indicators ──────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JointIndicatorsSection(
    primaryJoints: List<Joint>,
    secondaryJoints: List<Joint>,
    affectedJoints: Set<Joint>
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "JOINTS")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(bottom = 12.dp)
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

// ── Section 4: Form Cues ─────────────────────────────────────────────────────

@Composable
private fun FormCuesSection(cues: String) {
    var expanded by remember { mutableStateOf(false) }
    val preview = remember(cues) {
        if (cues.length > 120) cues.take(120) + "…" else cues
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "FORM CUES")
        Surface(
            color = FormCuesGold,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
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

// ── Section 5: Personal Records ──────────────────────────────────────────────

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

@Composable
private fun PrStatCard(
    label: String,
    value: String,
    date: String?,
    subLine: String? = null,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subLine != null) {
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (date != null) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── Section 6: Progressive Overload ─────────────────────────────────────────

@Composable
private fun ProgressiveOverloadSection(suggestion: OverloadSuggestion) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "NEXT STEP")

        when (suggestion) {
            is OverloadSuggestion.NoData -> {
                EmptySectionPlaceholder("Complete a workout with this exercise to get a suggestion.")
            }
            is IncreaseReps -> {
                OverloadCard(
                    icon = Icons.Default.AddCircleOutline,
                    message = "Last: %.1f kg × %d. Try %.1f kg × %d (%d sets)".format(
                        suggestion.currentWeight, suggestion.currentReps,
                        suggestion.currentWeight, suggestion.targetReps, suggestion.targetSets
                    )
                )
            }
            is IncreaseWeight -> {
                OverloadCard(
                    icon = Icons.Default.TrendingUp,
                    message = "Last: %.1f kg × %d. Try %.1f kg × %d (%d sets)".format(
                        suggestion.currentWeight, suggestion.targetReps,
                        suggestion.suggestedWeight, suggestion.targetReps, suggestion.targetSets
                    )
                )
            }
        }
    }
}

@Composable
private fun OverloadCard(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ── Section 7: Trends ────────────────────────────────────────────────────────

@Composable
private fun TrendsSection(
    trendData: ExerciseTrendData?,
    timeRange: TrendsTimeRange,
    onTimeRangeChanged: (TrendsTimeRange) -> Unit,
    e1rmProducer: CartesianChartModelProducer,
    maxWeightProducer: CartesianChartModelProducer,
    volumeProducer: CartesianChartModelProducer,
    bestSetProducer: CartesianChartModelProducer,
    rpeProducer: CartesianChartModelProducer
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "TRENDS")

        // Time range filter chips
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TrendsTimeRange.entries.forEach { range ->
                FilterChip(
                    selected = range == timeRange,
                    onClick = { onTimeRangeChanged(range) },
                    label = { Text(range.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }

        val charts = listOf(
            Triple("e1RM (kg)", e1rmProducer, trendData?.e1rmPoints?.size),
            Triple("Max Weight (kg)", maxWeightProducer, trendData?.maxWeightPoints?.size),
            Triple("Session Volume (kg)", volumeProducer, trendData?.volumePoints?.size),
            Triple("Best Set (kg×reps)", bestSetProducer, trendData?.bestSetPoints?.size),
            Triple("RPE Trend", rpeProducer, trendData?.rpePoints?.size)
        )

        charts.forEach { (label, producer, pointCount) ->
            MiniTrendChart(label = label, producer = producer, hasData = (pointCount ?: 0) >= 2)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun MiniTrendChart(
    label: String,
    producer: CartesianChartModelProducer,
    hasData: Boolean
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (hasData) {
            val axisLabel = rememberTextComponent(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textSize = 9.sp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(
                                        fill(VicoChartHelpers.LinePrimary)
                                    ),
                                    areaFill = LineCartesianLayer.AreaFill.single(
                                        fill(VicoChartHelpers.FillPrimary)
                                    ),
                                    thickness = 2.dp
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(label = axisLabel),
                        bottomAxis = HorizontalAxis.rememberBottom(label = axisLabel)
                    ),
                    modelProducer = producer,
                    modifier = Modifier.matchParentSize(),
                    scrollState = rememberVicoScrollState(
                        initialScroll = Scroll.Absolute.End,
                        autoScroll = Scroll.Absolute.End,
                        autoScrollCondition = AutoScrollCondition.OnModelSizeIncreased
                    ),
                    zoomState = rememberVicoZoomState(initialZoom = Zoom.Content)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not enough data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ── Section 8: Set/Rep Zone Guide ────────────────────────────────────────────

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

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "TRAINING ZONES")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
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
                        androidx.compose.foundation.BorderStroke(1.5.dp, color)
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

// ── Section 9: Warm-Up Ramp ──────────────────────────────────────────────────

@Composable
private fun WarmUpRampSection(ramp: List<WarmUpSet>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "WARM-UP RAMP")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Set",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.5f)
                    )
                    Text(
                        "Weight",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.8f),
                        textAlign = TextAlign.End
                    )
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
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            "%.1f kg (${set.percentageLabel})".format(set.weight),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${set.reps}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.8f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ── Section 10: Muscle Activation ────────────────────────────────────────────

@Composable
private fun MuscleActivationSection(stressColors: Map<String, Float>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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

        BodyOutlineCanvas(
            regionColors = regionColors,
            selectedRegion = null,
            onRegionTapped = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(bottom = 12.dp)
        )
    }
}

// ── Section 11: Alternative Exercises ────────────────────────────────────────

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

@Composable
private fun AlternativeExerciseCard(alternative: AlternativeExercise, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (alternative.exercise.exerciseType) {
                        ExerciseType.STRENGTH -> Icons.Default.FitnessCenter
                        ExerciseType.CARDIO -> Icons.Default.DirectionsRun
                        ExerciseType.TIMED -> Icons.Default.Timer
                        ExerciseType.PLYOMETRIC -> Icons.Default.FlashOn
                        ExerciseType.STRETCH -> Icons.Default.SelfImprovement
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = alternative.exercise.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 16.sp
            )
            if (alternative.estimatedStartingWeight != null) {
                Text(
                    text = "Start: ~%.1f kg".format(alternative.estimatedStartingWeight),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    text = "You've done this",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── Section 12: Workout History ──────────────────────────────────────────────

@Composable
private fun WorkoutHistorySection(
    history: List<ExerciseWorkoutHistoryRow>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onWorkoutClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "WORKOUT HISTORY")

        if (history.isEmpty()) {
            EmptySectionPlaceholder("No workout history yet.")
        } else {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                history.forEach { row ->
                    WorkoutHistoryRow(row = row, onClick = { onWorkoutClick(row.workoutId) })
                }
            }
            if (hasMore) {
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Load more")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WorkoutHistoryRow(row: ExerciseWorkoutHistoryRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormat.format(Date(row.timestamp)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (row.routineName.isNotBlank()) {
                    Text(
                        text = row.routineName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${row.setCount} sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val vol = row.totalVolume
                Text(
                    text = if (vol >= 1000) "%.1f t".format(vol / 1000.0)
                           else "%.0f kg".format(vol),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Section 13: User Notes ───────────────────────────────────────────────────

@Composable
private fun UserNotesSection(note: String, onNoteChanged: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(title = "MY NOTES")
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
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
