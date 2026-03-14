package com.omerhedvat.powerme.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.data.database.ExerciseType
import com.omerhedvat.powerme.data.database.SetType
import com.omerhedvat.powerme.ui.theme.TimerGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SET_COL_WEIGHT = 0.10f
private const val WEIGHT_COL_WEIGHT = 0.30f
private const val REPS_COL_WEIGHT = 0.30f
private const val RPE_COL_WEIGHT = 0.20f
private const val CHECK_COL_WEIGHT = 0.10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.workout?.let { _ ->
                            uiState.exerciseGroups.firstOrNull()?.let { "Workout" } ?: "Workout"
                        } ?: "Workout",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val workout = uiState.workout
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Header — workout name + date
            item {
                workout?.let { w ->
                    val dateStr = SimpleDateFormat("HH:mm, EEEE, d MMM yyyy", Locale.getDefault())
                        .format(Date(w.timestamp))
                    Text(
                        text = dateStr,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                formatDetailDuration(w.durationSeconds),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FitnessCenter, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${w.totalVolume.toInt()} kg",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                        val totalSets = uiState.exerciseGroups.sumOf { it.sets.size }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.List, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "$totalSets sets",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Exercise groups
            items(uiState.exerciseGroups) { group ->
                val isExpanded = uiState.expandedExerciseIds.contains(group.exerciseId)
                ExerciseDetailCard(
                    group = group,
                    isExpanded = isExpanded,
                    onToggleExpansion = { viewModel.toggleExerciseExpansion(group.exerciseId) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    group: ExerciseGroup,
    isExpanded: Boolean,
    onToggleExpansion: () -> Unit
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpansion() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Superset spine if all sets share the same supersetGroupId
            val supersetId = group.sets.firstOrNull()?.supersetGroupId
            if (supersetId != null && group.sets.all { it.supersetGroupId == supersetId }) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                // Exercise header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.exerciseName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        group.muscleGroup?.let {
                            Text(
                                text = it,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${group.sets.size} sets",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        // Set headers based on ExerciseType
                        when (group.exerciseType) {
                            ExerciseType.CARDIO -> Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.width(40.dp))
                                Text("DIST(KM)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                                Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                                Text("PACE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(40.dp)) // Matching CHECK width
                            }
                            ExerciseType.TIMED -> Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.width(40.dp))
                                Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1.5f))
                                Text("RPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.width(40.dp)) // Matching CHECK width
                            }
                            else -> Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    "SET", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(SET_COL_WEIGHT)
                                )
                                Text(
                                    "WEIGHT", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(WEIGHT_COL_WEIGHT)
                                )
                                Text(
                                    "REPS", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(REPS_COL_WEIGHT)
                                )
                                Text(
                                    "RPE", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(RPE_COL_WEIGHT)
                                )
                                Spacer(modifier = Modifier.weight(CHECK_COL_WEIGHT))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Set rows
                        group.sets.forEach { set ->
                            when (group.exerciseType) {
                                ExerciseType.CARDIO -> CardioSetDetailRow(set = set)
                                ExerciseType.TIMED -> TimedSetDetailRow(set = set)
                                else -> StrengthSetDetailRow(set = set)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthSetDetailRow(set: SetDisplayRow) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SET column
            val (setLabel, setColor) = when (set.setType) {
                SetType.NORMAL -> "${set.setOrder}" to MaterialTheme.colorScheme.onSurface
                SetType.WARMUP -> "W" to MaterialTheme.colorScheme.tertiary
                SetType.FAILURE -> "F" to MaterialTheme.colorScheme.error
                SetType.DROP -> "D" to MaterialTheme.colorScheme.secondary
            }
            Box(
                modifier = Modifier.weight(SET_COL_WEIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = setLabel,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = setColor,
                    textAlign = TextAlign.Center
                )
            }

            // WEIGHT column
            Box(
                modifier = Modifier
                    .weight(WEIGHT_COL_WEIGHT)
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.weight.let { if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it) },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // REPS column
            Box(
                modifier = Modifier
                    .weight(REPS_COL_WEIGHT)
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.reps.toString(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // RPE column
            Box(
                modifier = Modifier.weight(RPE_COL_WEIGHT),
                contentAlignment = Alignment.Center
            ) {
                val rpeLabel = set.rpe?.let { if (it % 10 == 0) "${it / 10}" else "${it / 10}.5" }
                Text(
                    text = rpeLabel ?: "—",
                    fontSize = 13.sp,
                    fontWeight = if (set.rpe != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (set.rpe != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    textAlign = TextAlign.Center
                )
            }

            // CHECK column
            Box(
                modifier = Modifier
                    .weight(CHECK_COL_WEIGHT)
                    .fillMaxHeight()
                    .background(
                        color = TimerGreen,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background
                )
            }
        }

        // Set notes
        set.setNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 24.dp, bottom = 2.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun CardioSetDetailRow(set: SetDisplayRow) {
    val pace = if (set.distance != null && set.distance > 0 && set.timeSeconds != null) {
        val paceMinPerKm = (set.timeSeconds / 60.0) / set.distance
        String.format("%.2f", paceMinPerKm)
    } else {
        "—"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${set.setOrder}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            // DISTANCE pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.distance?.toString() ?: "—",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // TIME pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.timeSeconds?.toString() ?: "—",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // PACE
            Text(
                text = pace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            // CHECK status
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(TimerGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background
                )
            }
        }
        
        // Set notes
        set.setNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 48.dp, bottom = 2.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun TimedSetDetailRow(set: SetDisplayRow) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${set.setOrder}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            // TIME pill
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = set.timeSeconds?.toString() ?: "—",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // RPE pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val rpeLabel = set.rpe?.let { if (it % 10 == 0) "${it / 10}" else "${it / 10}.5" }
                Text(
                    text = rpeLabel ?: "—",
                    fontSize = 14.sp,
                    color = if (set.rpe != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    fontWeight = if (set.rpe != null) FontWeight.Bold else FontWeight.Normal
                )
            }

            // CHECK status
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(TimerGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background
                )
            }
        }

        // Set notes
        set.setNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 48.dp, bottom = 2.dp, top = 2.dp)
            )
        }
    }
}

private fun formatDetailDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
