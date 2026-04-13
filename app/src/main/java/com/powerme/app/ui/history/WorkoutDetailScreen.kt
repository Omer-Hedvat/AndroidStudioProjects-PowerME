package com.powerme.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import com.powerme.app.ui.theme.PowerMeDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.powerme.app.ui.components.rememberSelectAllState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.util.UnitConverter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared column weight distribution — synchronized with ActiveWorkoutScreen
private const val SET_COL_WEIGHT    = 0.08f
private const val PREV_SPACE_WEIGHT = 0.22f // Space for PREV (not shown in history)
private const val WEIGHT_COL_WEIGHT = 0.25f
private const val REPS_COL_WEIGHT   = 0.22f
private const val RPE_COL_WEIGHT    = 0.13f
private const val CHECK_COL_WEIGHT  = 0.10f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session?") },
            text = { Text("This workout and all its sets will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSession { onNavigateBack() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Workout History",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isEditMode) viewModel.cancelEditMode() else onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = { viewModel.saveEdits() },
                            enabled = !uiState.isSaving
                        ) { Text("Save", fontWeight = FontWeight.Bold) }
                    } else {
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit Session") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.startEditMode()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Session", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
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
                        style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodySmall,
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
                                UnitConverter.formatWeight(w.totalVolume, unitSystem),
                                style = MaterialTheme.typography.bodySmall,
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
                                style = MaterialTheme.typography.bodySmall,
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
                    unitSystem = unitSystem,
                    isEditMode = uiState.isEditMode,
                    pendingEdits = uiState.pendingEdits,
                    onToggleExpansion = { viewModel.toggleExerciseExpansion(group.exerciseId) },
                    onWeightChanged = { setId, w -> viewModel.updatePendingWeight(setId, w) },
                    onRepsChanged = { setId, r -> viewModel.updatePendingReps(setId, r) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    group: ExerciseGroup,
    isExpanded: Boolean,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    isEditMode: Boolean = false,
    pendingEdits: Map<String, PendingEdit> = emptyMap(),
    onToggleExpansion: () -> Unit,
    onWeightChanged: (String, String) -> Unit = { _, _ -> },
    onRepsChanged: (String, String) -> Unit = { _, _ -> }
) {
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "rotation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpansion() },
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
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
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        group.muscleGroup?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodySmall,
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
                                Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                Text("DIST(${UnitConverter.distanceLabel(unitSystem).uppercase()})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
                                Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
                                Text("PACE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
                                Text("RPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
                                Spacer(modifier = Modifier.width(40.dp))
                            }
                            ExerciseType.TIMED -> Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
                                Text("WEIGHT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
                                Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.35f))
                                Text("RPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
                                Spacer(modifier = Modifier.width(40.dp))
                            }
                            else -> Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(
                                    "SET", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(SET_COL_WEIGHT)
                                )
                                Spacer(modifier = Modifier.weight(PREV_SPACE_WEIGHT))
                                Text(
                                    "WEIGHT", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(WEIGHT_COL_WEIGHT)
                                )
                                Text(
                                    "REPS", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(REPS_COL_WEIGHT)
                                )
                                Text(
                                    "RPE", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
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
                                ExerciseType.CARDIO -> CardioSetDetailRow(set = set, unitSystem = unitSystem)
                                ExerciseType.TIMED -> TimedSetDetailRow(set = set, unitSystem = unitSystem)
                                else -> StrengthSetDetailRow(
                                    set = set,
                                    unitSystem = unitSystem,
                                    isEditMode = isEditMode,
                                    pendingEdit = pendingEdits[set.id],
                                    onWeightChanged = { w -> onWeightChanged(set.id, w) },
                                    onRepsChanged = { r -> onRepsChanged(set.id, r) }
                                )
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
private fun StrengthSetDetailRow(
    set: SetDisplayRow,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    isEditMode: Boolean = false,
    pendingEdit: PendingEdit? = null,
    onWeightChanged: (String) -> Unit = {},
    onRepsChanged: (String) -> Unit = {}
) {
    val weightDisplay = pendingEdit?.weight
        ?: UnitConverter.formatWeightRaw(set.weight, unitSystem)
    val repsDisplay = pendingEdit?.reps ?: set.reps.toString()

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

            Spacer(modifier = Modifier.weight(PREV_SPACE_WEIGHT))

            // WEIGHT column
            Box(
                modifier = Modifier
                    .weight(WEIGHT_COL_WEIGHT)
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (isEditMode) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isEditMode) {
                    BasicEditField(value = weightDisplay, onValueChange = onWeightChanged, keyboardType = KeyboardType.Decimal)
                } else {
                    Text(
                        text = weightDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // REPS column
            Box(
                modifier = Modifier
                    .weight(REPS_COL_WEIGHT)
                    .padding(horizontal = 2.dp)
                    .fillMaxHeight()
                    .background(
                        color = if (isEditMode) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isEditMode) {
                    BasicEditField(value = repsDisplay, onValueChange = onRepsChanged, keyboardType = KeyboardType.Number)
                } else {
                    Text(
                        text = repsDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                        shape = MaterialTheme.shapes.small
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
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(start = 24.dp, bottom = 2.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun BasicEditField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    val (tfv, selectAllMod) = rememberSelectAllState(value)
    OutlinedTextField(
        value = tfv.value,
        onValueChange = { newTfv ->
            tfv.value = newTfv
            onValueChange(newTfv.text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().then(selectAllMod)
    )
}

@Composable
private fun CardioSetDetailRow(set: SetDisplayRow, unitSystem: UnitSystem = UnitSystem.METRIC) {
    val distanceDisplay = set.distance?.let {
        val displayDist = if (unitSystem == UnitSystem.IMPERIAL) UnitConverter.kmToMiles(it) else it
        if (displayDist == displayDist.toLong().toDouble()) displayDist.toLong().toString() else "%.1f".format(displayDist)
    } ?: "—"
    val pace = if (set.distance != null && set.distance > 0 && set.timeSeconds != null) {
        val distKm = set.distance
        val paceMinPerUnit = (set.timeSeconds / 60.0) / if (unitSystem == UnitSystem.IMPERIAL) UnitConverter.kmToMiles(distKm) else distKm
        String.format("%.2f", paceMinPerUnit)
    } else {
        "—"
    }

    val timeFormatted = set.timeSeconds?.let {
        val m = it / 60
        val s = it % 60
        "%d:%02d".format(m, s)
    } ?: "—"

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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            // DISTANCE pill
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = distanceDisplay,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // TIME pill
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeFormatted,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // PACE pill
            Box(
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // RPE pill
            Box(
                modifier = Modifier
                    .weight(0.10f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
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

            // CHECK status
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(TimerGreen, MaterialTheme.shapes.small),
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
private fun TimedSetDetailRow(set: SetDisplayRow, unitSystem: UnitSystem = UnitSystem.METRIC) {
    val timeFormatted = set.timeSeconds?.let {
        val m = it / 60
        val s = it % 60
        "%d:%02d".format(m, s)
    } ?: "—"

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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )

            // WEIGHT pill
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = UnitConverter.formatWeightRaw(set.weight, unitSystem),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // TIME pill
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeFormatted,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            // RPE pill
            Box(
                modifier = Modifier
                    .weight(0.20f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
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

            // CHECK status
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(TimerGreen, MaterialTheme.shapes.small),
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
