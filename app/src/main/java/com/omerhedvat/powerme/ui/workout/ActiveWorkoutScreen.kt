package com.omerhedvat.powerme.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import com.omerhedvat.powerme.ui.components.MagicAddDialog
import com.omerhedvat.powerme.ui.components.WorkoutInputField
import com.omerhedvat.powerme.ui.theme.MedicalAmber
import com.omerhedvat.powerme.ui.theme.StremioError
import com.omerhedvat.powerme.ui.theme.StremioMagenta
import com.omerhedvat.powerme.ui.theme.TimerGreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.database.ExerciseType
import com.omerhedvat.powerme.data.database.SetType
import com.omerhedvat.powerme.ui.chat.MedicalRestrictionsDoc
import com.omerhedvat.powerme.ui.components.YouTubePlayerBottomSheet
import com.omerhedvat.powerme.ui.theme.MedicalAmberContainer
import com.omerhedvat.powerme.util.PlateCalculator

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val medicalDoc by viewModel.medicalDoc.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val availablePlates by viewModel.availablePlates.collectAsState()
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Keep screen on during active workout (respects user setting)
    DisposableEffect(workoutState.isActive, keepScreenOn) {
        view.keepScreenOn = workoutState.isActive && keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Post-workout summary sheet
    workoutState.pendingWorkoutSummary?.let { summary ->
        PostWorkoutSummarySheet(
            summary = summary,
            onDone = {
                viewModel.dismissWorkoutSummary()
                onWorkoutFinished()
            },
            onDismiss = {
                viewModel.dismissWorkoutSummary()
                onWorkoutFinished()
            }
        )
    }

    // Cancel confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Workout?") },
            text = { Text("This workout will not be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    viewModel.cancelWorkout()
                    onWorkoutFinished()
                }) { Text("Cancel Workout", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep Going") }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            if (!workoutState.isActive && workoutState.pendingWorkoutSummary == null) {
                // Start Workout Screen
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.startWorkout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(60.dp)
                    ) {
                        Text(
                            text = "START WORKOUT",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (workoutState.isActive) {
                // Compact top bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel workout",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = workoutState.workoutName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatElapsed(workoutState.elapsedSeconds),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Button(
                            onClick = { viewModel.finishWorkout() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text("Finish", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Rest Timer Bar
                if (workoutState.restTimer.isActive) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        RestTimerBar(
                            restTimer = workoutState.restTimer,
                            onSkip = { viewModel.skipRestTimer() },
                            onAddTime = { viewModel.addTimeToTimer(30) }
                        )
                    }
                }

                // Active Workout Screen
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {

                    // Warmup section
                    if (!workoutState.warmupCompleted && workoutState.exercises.isEmpty()) {
                        item {
                            Button(
                                onClick = { viewModel.requestWarmup() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !workoutState.isLoadingWarmup
                            ) {
                                if (workoutState.isLoadingWarmup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.background,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Getting Warmup...")
                                } else {
                                    Icon(Icons.Default.FitnessCenter, contentDescription = "Get Warmup")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("GET WARMUP", fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Display warmup prescription
                    workoutState.warmupPrescription?.let { prescription ->
                        item {
                            WarmupPrescriptionCard(
                                prescription = prescription,
                                onLogAsPerformed = { viewModel.logWarmupAsPerformed() },
                                onDismiss = { viewModel.dismissWarmup() }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (workoutState.warmupCompleted) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✓ Warmup completed",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Superset multi-select CAB
                    if (workoutState.isSupersetSelectMode) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { viewModel.exitSupersetSelectMode() }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        text = "Select exercises for superset",
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = { viewModel.commitSupersetSelection() },
                                        enabled = workoutState.supersetCandidateIds.size >= 2
                                    ) {
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = "Confirm superset",
                                            tint = if (workoutState.supersetCandidateIds.size >= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(workoutState.exercises) { exerciseWithSets ->
                        val exerciseIndex = workoutState.exercises.indexOf(exerciseWithSets)
                        val prevExercise = workoutState.exercises.getOrNull(exerciseIndex - 1)
                        val isFirstInSupersetGroup = exerciseWithSets.supersetGroupId != null &&
                            prevExercise?.supersetGroupId != exerciseWithSets.supersetGroupId
                        if (isFirstInSupersetGroup) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "⇌ SUPERSET",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        val isMyTurn = workoutState.activeSupersetExerciseId == exerciseWithSets.exercise.id
                        val availablePartners = workoutState.exercises.filter {
                            it.supersetGroupId == null && it.exercise.id != exerciseWithSets.exercise.id
                        }
                        ExerciseCard(
                            exerciseWithSets = exerciseWithSets,
                            medicalDoc = medicalDoc,
                            isMyTurn = isMyTurn,
                            availablePartners = availablePartners,
                            availableExercises = workoutState.availableExercises,
                            availablePlates = availablePlates,
                            isSelectMode = workoutState.isSupersetSelectMode,
                            isSelected = exerciseWithSets.exercise.id in workoutState.supersetCandidateIds,
                            onToggleSelect = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                            onPairAsSuperset = { partnerId ->
                                viewModel.pairAsSuperset(exerciseWithSets.exercise.id, partnerId)
                            },
                            onRemoveFromSuperset = {
                                viewModel.removeFromSuperset(exerciseWithSets.exercise.id)
                            },
                            onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                            onReplaceExercise = { newExercise ->
                                viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise)
                            },
                            onUpdateSessionNote = { note ->
                                viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note)
                            },
                            onUpdateStickyNote = { note ->
                                viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note)
                            },
                            onUpdateRestTimer = { seconds ->
                                viewModel.updateExerciseRestTimer(exerciseWithSets.exercise.id, seconds)
                            },
                            onAddWarmupSets = { viewModel.addWarmupSetsToExercise(exerciseWithSets.exercise.id) },
                            onEnterSupersetSelectMode = { viewModel.enterSupersetSelectMode() },
                            onAddSet = { viewModel.addSet(exerciseWithSets.exercise.id) },
                            onUpdateSet = { setOrder, weight, reps, rpe, wasCompleted ->
                                viewModel.updateSet(
                                    exerciseWithSets.exercise.id,
                                    setOrder,
                                    weight,
                                    reps,
                                    rpe
                                )
                                if (!wasCompleted && weight.isNotBlank() && reps.isNotBlank()) {
                                    if (exerciseWithSets.supersetGroupId != null) {
                                        viewModel.advanceSupersetTurn(exerciseWithSets.exercise.id)
                                    } else {
                                        viewModel.startRestTimer(exerciseWithSets.exercise.id)
                                    }
                                }
                            },
                            onUpdateCardioSet = { setOrder, distance, timeSeconds, rpe, wasCompleted ->
                                viewModel.updateCardioSet(
                                    exerciseWithSets.exercise.id,
                                    setOrder,
                                    distance,
                                    timeSeconds,
                                    rpe
                                )
                                if (!wasCompleted && distance.isNotBlank() && timeSeconds.isNotBlank()) {
                                    if (exerciseWithSets.supersetGroupId != null) {
                                        viewModel.advanceSupersetTurn(exerciseWithSets.exercise.id)
                                    } else {
                                        viewModel.startRestTimer(exerciseWithSets.exercise.id)
                                    }
                                }
                            },
                            onUpdateTimedSet = { setOrder, timeSeconds, rpe, wasCompleted ->
                                viewModel.updateTimedSet(
                                    exerciseWithSets.exercise.id,
                                    setOrder,
                                    timeSeconds,
                                    rpe
                                )
                                if (!wasCompleted && timeSeconds.isNotBlank()) {
                                    if (exerciseWithSets.supersetGroupId != null) {
                                        viewModel.advanceSupersetTurn(exerciseWithSets.exercise.id)
                                    } else {
                                        viewModel.startRestTimer(exerciseWithSets.exercise.id)
                                    }
                                }
                            },
                            onDeleteSet = { setOrder ->
                                viewModel.deleteSet(exerciseWithSets.exercise.id, setOrder)
                            },
                            onUpdateSetupNotes = { notes ->
                                viewModel.updateSetupNotes(exerciseWithSets.exercise.id, notes)
                            },
                            onUpdateSetNotes = { setOrder, notes ->
                                viewModel.updateSetNotes(exerciseWithSets.exercise.id, setOrder, notes)
                            },
                            onWeightChanged = { setOrder, raw ->
                                val wasCompleted = viewModel.workoutState.value.exercises
                                    .find { it.exercise.id == exerciseWithSets.exercise.id }
                                    ?.sets?.find { it.setOrder == setOrder }?.isCompleted ?: false
                                viewModel.onWeightChanged(exerciseWithSets.exercise.id, setOrder, raw)
                                if (!wasCompleted) {
                                    val nowCompleted = viewModel.workoutState.value.exercises
                                        .find { it.exercise.id == exerciseWithSets.exercise.id }
                                        ?.sets?.find { it.setOrder == setOrder }?.isCompleted ?: false
                                    if (nowCompleted) {
                                        if (exerciseWithSets.supersetGroupId != null)
                                            viewModel.advanceSupersetTurn(exerciseWithSets.exercise.id)
                                        else
                                            viewModel.startRestTimer(exerciseWithSets.exercise.id)
                                    }
                                }
                            },
                            onRepsChanged = { setOrder, raw ->
                                val wasCompleted = viewModel.workoutState.value.exercises
                                    .find { it.exercise.id == exerciseWithSets.exercise.id }
                                    ?.sets?.find { it.setOrder == setOrder }?.isCompleted ?: false
                                viewModel.onRepsChanged(exerciseWithSets.exercise.id, setOrder, raw)
                                if (!wasCompleted) {
                                    val nowCompleted = viewModel.workoutState.value.exercises
                                        .find { it.exercise.id == exerciseWithSets.exercise.id }
                                        ?.sets?.find { it.setOrder == setOrder }?.isCompleted ?: false
                                    if (nowCompleted) {
                                        if (exerciseWithSets.supersetGroupId != null)
                                            viewModel.advanceSupersetTurn(exerciseWithSets.exercise.id)
                                        else
                                            viewModel.startRestTimer(exerciseWithSets.exercise.id)
                                    }
                                }
                            },
                            onCompleteSet = { setOrder ->
                                viewModel.completeSet(exerciseWithSets.exercise.id, setOrder)
                            },
                            onCycleSetType = { setOrder ->
                                viewModel.cycleSetType(exerciseWithSets.exercise.id, setOrder)
                            }
                        )
                    }

                    item {
                        Button(
                            onClick = { showExerciseDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Exercise")
                        }
                    }
                }
            }
        }
    }

    if (showExerciseDialog) {
        ExerciseSelectionDialog(
            exercises = workoutState.availableExercises,
            onExerciseSelected = { exercise ->
                viewModel.addExercise(exercise)
                showExerciseDialog = false
            },
            onDismiss = { showExerciseDialog = false }
        )
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%dh %dm".format(h, m) else "%dm %ds".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostWorkoutSummarySheet(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = summary.workoutName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(summary.durationSeconds),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Timer, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatDuration(summary.durationSeconds),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FitnessCenter, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${summary.totalVolume.toInt()} kg",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.List, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${summary.setCount} sets",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            summary.exerciseNames.forEach { name ->
                Text(
                    "• $name",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    medicalDoc: MedicalRestrictionsDoc?,
    isMyTurn: Boolean = false,
    availablePartners: List<ExerciseWithSets> = emptyList(),
    availableExercises: List<Exercise> = emptyList(),
    availablePlates: List<Double> = emptyList(),
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPairAsSuperset: (Long) -> Unit = {},
    onRemoveFromSuperset: () -> Unit = {},
    onRemoveExercise: () -> Unit = {},
    onReplaceExercise: (Exercise) -> Unit = {},
    onUpdateSessionNote: (String?) -> Unit = {},
    onUpdateStickyNote: (String?) -> Unit = {},
    onUpdateRestTimer: (Int) -> Unit = {},
    onAddWarmupSets: () -> Unit = {},
    onEnterSupersetSelectMode: () -> Unit = {},
    onAddSet: () -> Unit,
    onUpdateSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateCardioSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateTimedSet: (Int, String, String, Boolean) -> Unit,
    onDeleteSet: (Int) -> Unit,
    onUpdateSetupNotes: (String) -> Unit,
    onUpdateSetNotes: (Int, String) -> Unit,
    onWeightChanged: (Int, String) -> Unit = { _, _ -> },
    onRepsChanged: (Int, String) -> Unit = { _, _ -> },
    onCompleteSet: (Int) -> Unit = { _ -> },
    onCycleSetType: (Int) -> Unit = { _ -> }
) {
    var showPlateCalc by remember { mutableStateOf(false) }
    var calcTargetWeight by remember { mutableStateOf("") }
    var showSetupNotesEditor by remember { mutableStateOf(false) }
    var setupNotesText by remember { mutableStateOf(exerciseWithSets.exercise.setupNotes ?: "") }
    var showManagementHub by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var showSessionNoteDialog by remember { mutableStateOf(false) }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var showRestTimerSheet by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }

    val isInSuperset = exerciseWithSets.supersetGroupId != null
    val borderModifier = when {
        isSelectMode && isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        isInSuperset && isMyTurn   -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        else                       -> Modifier
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(borderModifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        // IntrinsicSize.Min is required so the spine Box's fillMaxHeight()
        // derives its height from the Column's intrinsic content height.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (isInSuperset) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary)
                )
            }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exerciseWithSets.exercise.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isInSuperset) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "SS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = exerciseWithSets.exercise.muscleGroup,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                } else {
                    IconButton(onClick = { showSetupNotesEditor = !showSetupNotesEditor }) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = "Setup Notes",
                            tint = if (exerciseWithSets.exercise.setupNotes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { showManagementHub = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Exercise options", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (showSupersetPicker) {
                SupersetPartnerPickerDialog(
                    partners = availablePartners,
                    onPartnerSelected = { partnerId ->
                        showSupersetPicker = false
                        onPairAsSuperset(partnerId)
                    },
                    onDismiss = { showSupersetPicker = false }
                )
            }

            if (showManagementHub) {
                ManagementHubSheet(
                    exerciseWithSets = exerciseWithSets,
                    isInSuperset = isInSuperset,
                    onDismiss = { showManagementHub = false },
                    onAddNote = { showManagementHub = false; showSessionNoteDialog = true },
                    onAddStickyNote = { showManagementHub = false; showStickyNoteDialog = true },
                    onAddWarmupSets = { showManagementHub = false; onAddWarmupSets() },
                    onUpdateRestTimer = { showManagementHub = false; showRestTimerSheet = true },
                    onReplaceExercise = { showManagementHub = false; showReplaceDialog = true },
                    onCreateSuperset = { showManagementHub = false; onEnterSupersetSelectMode() },
                    onPreferences = { showManagementHub = false; showSetupNotesEditor = true },
                    onRemoveExercise = { showManagementHub = false; onRemoveExercise() }
                )
            }

            if (showRestTimerSheet) {
                RestTimerSheet(
                    currentSeconds = exerciseWithSets.exercise.restDurationSeconds,
                    onDismiss = { showRestTimerSheet = false },
                    onSave = { newSeconds ->
                        onUpdateRestTimer(newSeconds)
                        showRestTimerSheet = false
                    }
                )
            }

            if (showSessionNoteDialog) {
                ExerciseNoteDialog(
                    title = "Session Note",
                    initialText = exerciseWithSets.sessionNote ?: "",
                    placeholder = "e.g., Felt strong today, keep RPE ≤ 8",
                    onDismiss = { showSessionNoteDialog = false },
                    onSave = { note ->
                        onUpdateSessionNote(note.ifBlank { null })
                        showSessionNoteDialog = false
                    }
                )
            }

            if (showStickyNoteDialog) {
                ExerciseNoteDialog(
                    title = "Sticky Note",
                    initialText = exerciseWithSets.stickyNote ?: "",
                    placeholder = "e.g., Use belt, keep elbows tucked — saved across sessions",
                    onDismiss = { showStickyNoteDialog = false },
                    onSave = { note ->
                        onUpdateStickyNote(note.ifBlank { null })
                        showStickyNoteDialog = false
                    }
                )
            }

            if (showReplaceDialog) {
                ExerciseSelectionDialog(
                    exercises = availableExercises,
                    onExerciseSelected = { exercise ->
                        onReplaceExercise(exercise)
                        showReplaceDialog = false
                    },
                    onDismiss = { showReplaceDialog = false }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Noa Medical Banner — RED STOP or YELLOW modification cue
            NoaCueBanner(
                exerciseName = exerciseWithSets.exercise.name,
                medicalDoc = medicalDoc
            )

            // Setup Notes Display
            if (!exerciseWithSets.exercise.setupNotes.isNullOrBlank() && !showSetupNotesEditor) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Form Cues:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exerciseWithSets.exercise.setupNotes!!,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Setup Notes Editor
            if (showSetupNotesEditor) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Setup Notes (Form Cues)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = setupNotesText,
                            onValueChange = { setupNotesText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., Chest up, elbows at 45°, drive through heels") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                focusedTextColor = MaterialTheme.colorScheme.primary,
                                unfocusedTextColor = MaterialTheme.colorScheme.primary,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ),
                            minLines = 2,
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    setupNotesText = exerciseWithSets.exercise.setupNotes ?: ""
                                    showSetupNotesEditor = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    onUpdateSetupNotes(setupNotesText)
                                    showSetupNotesEditor = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Per-set plate calculator modal bottom sheet
            if (showPlateCalc) {
                PlateCalculatorSheet(
                    targetWeight = calcTargetWeight,
                    barType = exerciseWithSets.exercise.barType,
                    availablePlates = availablePlates,
                    onDismiss = { showPlateCalc = false }
                )
            }

            // Sticky note display (persisted)
            if (!exerciseWithSets.stickyNote.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = "Sticky note", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(text = exerciseWithSets.stickyNote!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Session note display (volatile)
            if (!exerciseWithSets.sessionNote.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Notes, contentDescription = "Session note", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        Text(text = exerciseWithSets.sessionNote!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Set headers
            when (exerciseWithSets.exercise.exerciseType) {
                ExerciseType.CARDIO -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.width(40.dp))
                    Text("DIST(KM)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                    Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                    Text("PACE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(80.dp))
                }
                ExerciseType.TIMED -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("SET", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.width(40.dp))
                    Text("TIME(S)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1.5f))
                    Text("RPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(80.dp))
                }
                else -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.width(32.dp)) {
                        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.width(88.dp)) {
                        Text("PREVIOUS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        Text("KG", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Box(modifier = Modifier.width(30.dp)) {} // calc icon spacer
                    Box(modifier = Modifier.weight(1f)) {
                        Text("REPS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Box(modifier = Modifier.width(42.dp)) {} // ✓ spacer
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            exerciseWithSets.sets.forEach { set ->
                when (exerciseWithSets.exercise.exerciseType) {
                    ExerciseType.CARDIO -> {
                        CardioSetRow(
                            set = set,
                            onUpdateSet = { setOrder, distance, timeSeconds, rpe ->
                                onUpdateCardioSet(setOrder, distance, timeSeconds, rpe, set.isCompleted)
                            },
                            onDeleteSet = { onDeleteSet(set.setOrder) },
                            onUpdateSetNotes = { notes ->
                                onUpdateSetNotes(set.setOrder, notes)
                            }
                        )
                    }
                    ExerciseType.TIMED -> {
                        TimedSetRow(
                            set = set,
                            onUpdateSet = { setOrder, timeSeconds, rpe ->
                                onUpdateTimedSet(setOrder, timeSeconds, rpe, set.isCompleted)
                            },
                            onDeleteSet = { onDeleteSet(set.setOrder) },
                            onUpdateSetNotes = { notes ->
                                onUpdateSetNotes(set.setOrder, notes)
                            }
                        )
                    }
                    else -> {
                        WorkoutSetRow(
                            set = set,
                            onWeightChanged = { weight -> onWeightChanged(set.setOrder, weight) },
                            onRepsChanged = { reps -> onRepsChanged(set.setOrder, reps) },
                            onCompleteSet = { onCompleteSet(set.setOrder) },
                            onCycleSetType = { onCycleSetType(set.setOrder) },
                            onShowPlateCalculator = { weight ->
                                calcTargetWeight = weight
                                showPlateCalc = true
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddSet() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Add Set",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        } // closes Row(IntrinsicSize.Min)
    }
}

@Composable
fun SetRow(
    set: ActiveSet,
    onUpdateSet: (Int, String, String, String) -> Unit,
    onDeleteSet: () -> Unit,
    onUpdateSetNotes: (String) -> Unit,
    onWeightChanged: (String) -> Unit = { weight -> onUpdateSet(set.setOrder, weight, set.reps, set.rpe) },
    onRepsChanged: (String) -> Unit = { reps -> onUpdateSet(set.setOrder, set.weight, reps, set.rpe) }
) {
    var showNotesDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${set.setOrder}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )

        OutlinedTextField(
            value = set.weight,
            onValueChange = { weight -> onWeightChanged(weight) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                focusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostWeight?.let { ghost ->
                { Text(ghost, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) }
            },
            singleLine = true
        )

        OutlinedTextField(
            value = set.reps,
            onValueChange = { reps -> onRepsChanged(reps) },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                focusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostReps?.let { ghost ->
                { Text(ghost, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) }
            },
            singleLine = true
        )

        OutlinedTextField(
            value = set.rpe,
            onValueChange = { rpe ->
                onUpdateSet(set.setOrder, set.weight, set.reps, rpe)
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                focusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = MaterialTheme.colorScheme.primary,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostRpe?.let { ghost ->
                { Text(ghost, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) }
            },
            singleLine = true
        )

            IconButton(
                onClick = { showNotesDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Set Notes",
                    tint = if (set.setNotes.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Display set notes if present
        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
        }
    }

    // Set Notes Dialog
    if (showNotesDialog) {
        var notesText by remember { mutableStateOf(set.setNotes) }

        Dialog(onDismissRequest = { showNotesDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Set ${set.setOrder} Notes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Felt strong, used belt, slight discomfort") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showNotesDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onUpdateSetNotes(notesText)
                                showNotesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutSetRow(
    set: ActiveSet,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onCycleSetType: () -> Unit = {},
    onShowPlateCalculator: (weight: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val previousLabel = if (set.ghostWeight != null && set.ghostReps != null)
        "${set.ghostWeight}kg × ${set.ghostReps}" else "–"

    val (setLabel, setColor) = when (set.setType) {
        SetType.NORMAL  -> "${set.setOrder}" to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP  -> "W"               to MedicalAmber
        SetType.FAILURE -> "F"               to StremioError
        SetType.DROP    -> "D"               to StremioMagenta
    }

    Row(
        modifier = modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.width(32.dp).clickable(onClick = onCycleSetType),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = setLabel,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = setColor,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = previousLabel,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.width(88.dp),
            maxLines = 1
        )
        WorkoutInputField(
            value = set.weight, onValueChange = onWeightChanged,
            placeholder = set.ghostWeight ?: "", keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier.size(24.dp).clickable { onShowPlateCalculator(set.weight) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Calculate, contentDescription = "Plate calc",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
        WorkoutInputField(
            value = set.reps, onValueChange = onRepsChanged,
            placeholder = set.ghostReps ?: "", keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onCompleteSet),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = if (set.isCompleted) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateCalculatorSheet(
    targetWeight: String,
    barType: com.omerhedvat.powerme.data.database.BarType,
    availablePlates: List<Double>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var inputWeight by remember { mutableStateOf(targetWeight) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Plate Calculator",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            WorkoutInputField(
                value = inputWeight,
                onValueChange = { inputWeight = it },
                placeholder = "Target kg",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            val weight = inputWeight.toDoubleOrNull()
            if (weight != null && weight > 0) {
                val breakdown = PlateCalculator.calculatePlates(weight, barType, availablePlates)
                if (breakdown != null) {
                    Text(
                        "Per side: ${PlateCalculator.formatPlateBreakdown(breakdown)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (breakdown.error > 0.01) {
                        Text(
                            "≈ ${breakdown.totalWeight}kg (closest loadable)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                Text(
                    "Enter a target weight above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CardioSetRow(
    set: ActiveSet,
    onUpdateSet: (Int, String, String, String) -> Unit,
    onDeleteSet: () -> Unit,
    onUpdateSetNotes: (String) -> Unit
) {
    var showNotesDialog by remember { mutableStateOf(false) }

    // Calculate pace (min/km)
    val pace = if (set.distance.isNotBlank() && set.timeSeconds.isNotBlank()) {
        val dist = set.distance.toDoubleOrNull()
        val time = set.timeSeconds.toIntOrNull()
        if (dist != null && time != null && dist > 0) {
            val paceMinPerKm = (time / 60.0) / dist
            String.format("%.2f", paceMinPerKm)
        } else {
            "-"
        }
    } else {
        "-"
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${set.setOrder}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )

            OutlinedTextField(
                value = set.distance,
                onValueChange = { distance ->
                    onUpdateSet(set.setOrder, distance, set.timeSeconds, "")
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = set.timeSeconds,
                onValueChange = { timeSeconds ->
                    onUpdateSet(set.setOrder, set.distance, timeSeconds, "")
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )

            Text(
                text = pace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showNotesDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Set Notes",
                    tint = if (set.setNotes.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
        }
    }

    if (showNotesDialog) {
        var notesText by remember { mutableStateOf(set.setNotes) }

        Dialog(onDismissRequest = { showNotesDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Set ${set.setOrder} Notes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Felt strong, good pace") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showNotesDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onUpdateSetNotes(notesText)
                                showNotesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimedSetRow(
    set: ActiveSet,
    onUpdateSet: (Int, String, String) -> Unit,
    onDeleteSet: () -> Unit,
    onUpdateSetNotes: (String) -> Unit
) {
    var showNotesDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${set.setOrder}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )

            OutlinedTextField(
                value = set.timeSeconds,
                onValueChange = { timeSeconds ->
                    onUpdateSet(set.setOrder, timeSeconds, "")
                },
                modifier = Modifier.weight(1.5f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                placeholder = { Text("Seconds") }
            )

            OutlinedTextField(
                value = set.rpe,
                onValueChange = { rpe ->
                    onUpdateSet(set.setOrder, set.timeSeconds, rpe)
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.primary,
                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )

            IconButton(
                onClick = { showNotesDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Set Notes",
                    tint = if (set.setNotes.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
        }
    }

    if (showNotesDialog) {
        var notesText by remember { mutableStateOf(set.setNotes) }

        Dialog(onDismissRequest = { showNotesDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Set ${set.setOrder} Notes",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Held until failure") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedTextColor = MaterialTheme.colorScheme.primary,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showNotesDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onUpdateSetNotes(notesText)
                                showNotesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupersetPartnerPickerDialog(
    partners: List<ExerciseWithSets>,
    onPartnerSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Superset Partner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                partners.forEach { partner ->
                    TextButton(
                        onClick = { onPartnerSelected(partner.exercise.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = partner.exercise.name,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ExerciseSelectionDialog(
    exercises: List<Exercise>,
    onExerciseSelected: (Exercise) -> Unit,
    onDismiss: () -> Unit
) {
    var showVideoPlayer by remember { mutableStateOf(false) }
    var selectedVideoExercise by remember { mutableStateOf<Exercise?>(null) }
    var showMagicAdd by remember { mutableStateOf(false) }

    // Show MagicAddDialog on top when triggered
    if (showMagicAdd) {
        MagicAddDialog(
            onExerciseAdded = { newExercise ->
                showMagicAdd = false
                onExerciseSelected(newExercise)
            },
            onDismiss = { showMagicAdd = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Exercise",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showMagicAdd = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Magic Add",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exercises) { exercise ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onExerciseSelected(exercise) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = exercise.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = exercise.muscleGroup,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }

                                // Play icon for exercises with YouTube videos
                                if (exercise.youtubeVideoId != null) {
                                    IconButton(
                                        onClick = {
                                            selectedVideoExercise = exercise
                                            showVideoPlayer = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = "Watch video",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // YouTube Player Bottom Sheet
    if (showVideoPlayer && selectedVideoExercise != null) {
        YouTubePlayerBottomSheet(
            videoId = selectedVideoExercise!!.youtubeVideoId!!,
            exerciseName = selectedVideoExercise!!.name,
            onDismiss = {
                showVideoPlayer = false
                selectedVideoExercise = null
            }
        )
    }
}

@Composable
fun WarmupPrescriptionCard(
    prescription: com.omerhedvat.powerme.warmup.WarmupPrescription,
    onLogAsPerformed: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "COMMITTEE PRESCRIPTION",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Noa & Coach Carter",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reasoning
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Why These Exercises:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = prescription.reasoning,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exercises
            prescription.exercises.forEachIndexed { index, exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${index + 1}. ${exercise.name}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = exercise.duration?.let { "${it}s" }
                                    ?: exercise.reps?.let { "${it} reps" }
                                    ?: "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target: ${exercise.targetJoint}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exercise.instructions,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
                if (index < prescription.exercises.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expert Notes
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Noa (Physio):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = prescription.noaaNote,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Coach Carter:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = prescription.carterNote,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log as Performed Button
            Button(
                onClick = onLogAsPerformed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "LOG AS PERFORMED",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementHubSheet(
    exerciseWithSets: ExerciseWithSets,
    isInSuperset: Boolean,
    onDismiss: () -> Unit,
    onAddNote: () -> Unit,
    onAddStickyNote: () -> Unit,
    onAddWarmupSets: () -> Unit,
    onUpdateRestTimer: () -> Unit,
    onReplaceExercise: () -> Unit,
    onCreateSuperset: () -> Unit,
    onPreferences: () -> Unit,
    onRemoveExercise: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = exerciseWithSets.exercise.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

            val hubItems = buildList {
                add(Triple(Icons.Default.Notes,    "Add note",            onAddNote))
                add(Triple(Icons.Default.PushPin,  "Add sticky note",     onAddStickyNote))
                add(Triple(Icons.Default.Add,      "Add warm-up sets",    onAddWarmupSets))
                add(Triple(Icons.Default.Timer,    "Update rest timer",   onUpdateRestTimer))
                add(Triple(Icons.Default.Refresh,  "Replace exercise",    onReplaceExercise))
                if (!isInSuperset) {
                    add(Triple(Icons.Default.List, "Create superset",     onCreateSuperset))
                }
                add(Triple(Icons.Default.Settings, "Preferences",         onPreferences))
            }
            hubItems.forEach { (icon, label, action) ->
                ListItem(
                    headlineContent = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                    leadingContent = { Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { action() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            // Remove exercise (destructive — red)
            ListItem(
                headlineContent = {
                    Text("Remove exercise", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onRemoveExercise() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerSheet(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var seconds by remember { mutableStateOf(currentSeconds) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Update Rest Timer",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(
                    onClick = { if (seconds > 30) seconds -= 30 },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease 30s",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${seconds}s",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { seconds += 30 },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase 30s",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Button(
                onClick = { onSave(seconds) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ExerciseNoteDialog(
    title: String,
    initialText: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                minLines = 2,
                maxLines = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun RestTimerBar(
    restTimer: com.omerhedvat.powerme.ui.workout.RestTimerState,
    onSkip: () -> Unit,
    onAddTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REST: ${restTimer.remainingSeconds}s",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddTime,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("+30s", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Skip", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = restTimer.remainingSeconds.toFloat() / restTimer.totalSeconds.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.background,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Displays a Noa medical restriction banner for the given exercise.
 *
 * 🔴 RED list → STOP banner (exercise is forbidden)
 * 🟡 YELLOW list → modification cue banner (exercise allowed with constraints)
 *
 * Non-dismissable by design — safety-critical information.
 */
@Composable
fun NoaCueBanner(
    exerciseName: String,
    medicalDoc: MedicalRestrictionsDoc?
) {
    if (medicalDoc == null) return

    val exerciseNameLower = exerciseName.lowercase()

    // Check RED list first (higher priority)
    val isRedListed = medicalDoc.redList.any { it.lowercase() == exerciseNameLower }
    if (isRedListed) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🔴", fontSize = 16.sp)
                Column {
                    Text(
                        text = "Noa: Exercise Flagged",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "This exercise is on your RED list. Consider an alternative.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
        return
    }

    // Check YELLOW list
    val yellowEntry = medicalDoc.yellowList.firstOrNull {
        it.exercise.lowercase() == exerciseNameLower
    }
    if (yellowEntry != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MedicalAmberContainer.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🟡", fontSize = 16.sp)
                Column {
                    Text(
                        text = "Noa:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedicalAmber
                    )
                    Text(
                        text = yellowEntry.requiredCue,
                        fontSize = 12.sp,
                        color = MedicalAmber.copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
