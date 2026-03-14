package com.omerhedvat.powerme.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.saveable.rememberSaveable
import com.omerhedvat.powerme.ui.components.MagicAddDialog
import com.omerhedvat.powerme.ui.components.WorkoutInputField
import com.omerhedvat.powerme.ui.theme.JetBrainsMono
import com.omerhedvat.powerme.ui.theme.MedicalAmber
import com.omerhedvat.powerme.ui.theme.StremioError
import com.omerhedvat.powerme.ui.theme.StremioMagenta
import com.omerhedvat.powerme.ui.theme.TimerGreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.key
import androidx.activity.compose.BackHandler

// Shared column weight distribution — applied identically to header row and WorkoutSetRow
private const val SET_COL_WEIGHT    = 0.08f
private const val PREV_COL_WEIGHT   = 0.22f
private const val WEIGHT_COL_WEIGHT = 0.25f
private const val REPS_COL_WEIGHT   = 0.22f
private const val RPE_COL_WEIGHT    = 0.13f
private const val CHECK_COL_WEIGHT  = 0.10f

@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val medicalDoc by viewModel.medicalDoc.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showTimerControls by remember { mutableStateOf(false) }
    val view = LocalView.current
    var isReorderMode by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val exercises = workoutState.exercises
        val fromIdx = exercises.indexOfFirst { it.exercise.id == from.key as Long }
        val toIdx   = exercises.indexOfFirst { it.exercise.id == to.key as Long }
        if (fromIdx >= 0 && toIdx >= 0) viewModel.reorderExercise(fromIdx, toIdx)
    }

    // Keep screen on during active workout (respects user setting)
    DisposableEffect(workoutState.isActive, keepScreenOn) {
        view.keepScreenOn = workoutState.isActive && keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Navigate back when routine edits are saved
    LaunchedEffect(workoutState.editModeSaved) {
        if (workoutState.editModeSaved) onWorkoutFinished()
    }

    // Back press in edit mode cancels without saving
    if (workoutState.isEditMode) {
        BackHandler { viewModel.cancelEditMode() }
    }

    // Routine sync dialogs (shown before post-workout summary)
    when (workoutState.pendingRoutineSync) {
        RoutineSyncType.STRUCTURE -> AlertDialog(
            onDismissRequest = { viewModel.dismissRoutineSync() },
            title = { Text("Update Routine?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmUpdateRoutineStructure() }) { Text("Update values and routine") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRoutineSync() }) { Text("Keep Original") }
            }
        )
        RoutineSyncType.VALUES -> AlertDialog(
            onDismissRequest = { viewModel.dismissRoutineSync() },
            title = { Text("Update Routine?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmUpdateRoutineValues() }) { Text("Update values only") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRoutineSync() }) { Text("Keep Original") }
            }
        )
        RoutineSyncType.BOTH -> Dialog(onDismissRequest = { viewModel.dismissRoutineSync() }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Update Routine?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(
                        onClick = { viewModel.confirmUpdateRoutineValues() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update values only") }
                    Spacer(Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = { viewModel.confirmUpdateBoth() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update values and routine") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.dismissRoutineSync() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Keep Original") }
                }
            }
        }
        null -> Unit
    }

    // Post-workout summary sheet
    workoutState.pendingWorkoutSummary?.let { summary ->
        if (workoutState.pendingRoutineSync == null) {
            PostWorkoutSummarySheet(
                summary = summary,
                onSaveAsRoutine = { routineName -> viewModel.saveWorkoutAsRoutine(routineName) },
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            if (!workoutState.isActive && workoutState.pendingWorkoutSummary == null && !workoutState.isEditMode) {
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
                        IconButton(onClick = {
                            if (workoutState.isEditMode) viewModel.cancelEditMode()
                            else showCancelDialog = true
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = if (workoutState.isEditMode) "Cancel edit" else "Cancel workout",
                                tint = MaterialTheme.colorScheme.error
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
                            if (!workoutState.isEditMode) {
                                Text(
                                    text = formatElapsed(workoutState.elapsedSeconds),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                val activeTimerExerciseId = workoutState.restTimer.exerciseId
                val activeTimerSetOrder = workoutState.restTimer.setOrder
                val activeTimerRemainingSeconds = workoutState.restTimer.remainingSeconds

                // Active Workout Screen
                LazyColumn(
                    state = lazyListState,
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

                    items(workoutState.exercises, key = { it.exercise.id }) { exerciseWithSets ->
                        ReorderableItem(reorderState, key = exerciseWithSets.exercise.id) { isDragging ->
                            if (isReorderMode) {
                                CollapsedExerciseReorderRow(
                                    exerciseWithSets = exerciseWithSets,
                                    isDragging = isDragging,
                                    onDragStopped = { isReorderMode = false }
                                )
                            } else {
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
                                    activeTimerExerciseId = activeTimerExerciseId,
                                    activeTimerSetOrder = activeTimerSetOrder,
                                    activeTimerRemainingSeconds = activeTimerRemainingSeconds,
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
                                    onAddRest = { exerciseId -> viewModel.startRestTimer(exerciseId) },
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
                                    onSelectSetType = { setOrder, type ->
                                        viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type)
                                    },
                                    onUpdateRpe = { setOrder, rpe ->
                                        viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe)
                                    },
                                    onDeleteLocalRestTime = { setOrder ->
                                        viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder)
                                    },
                                    onUpdateLocalRestTime = { setOrder, seconds ->
                                        viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds)
                                    },
                                    restTimeOverrides = workoutState.restTimeOverrides,
                                    onTimerActiveClick = { showTimerControls = true },
                                    onDeleteRestSeparator = { setOrder ->
                                        viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder)
                                    },
                                    hiddenRestSeparators = workoutState.hiddenRestSeparators,
                                    onLongPress = { isReorderMode = true },
                                    isEditMode = workoutState.isEditMode
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { showExerciseDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Exercise")
                        }
                    }

                    // Finish button — at the bottom of the workout list
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (workoutState.isEditMode) {
                            Button(
                                onClick = { viewModel.saveRoutineEdits() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.finishWorkout() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TimerGreen,
                                    contentColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                            ) {
                                Text("Finish Workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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

    if (showTimerControls) {
        TimerControlsSheet(
            restTimer = workoutState.restTimer,
            onDismiss = { showTimerControls = false },
            onAddTime = { delta -> viewModel.addTimeToTimer(delta) },
            onPause = { viewModel.pauseRestTimer() },
            onResume = { viewModel.resumeRestTimer() },
            onSkip = { viewModel.skipRestTimer() }
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
    onSaveAsRoutine: (String) -> Unit = {},
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var routineNameInput by remember { mutableStateOf(summary.workoutName) }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save as Routine") },
            text = {
                OutlinedTextField(
                    value = routineNameInput,
                    onValueChange = { routineNameInput = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (routineNameInput.isNotBlank()) {
                            onSaveAsRoutine(routineNameInput.trim())
                            showSaveDialog = false
                            onDone()
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

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
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save as Routine")
            }
            Spacer(modifier = Modifier.height(8.dp))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    medicalDoc: MedicalRestrictionsDoc?,
    isMyTurn: Boolean = false,
    availablePartners: List<ExerciseWithSets> = emptyList(),
    availableExercises: List<Exercise> = emptyList(),
    activeTimerExerciseId: Long? = null,
    activeTimerSetOrder: Int? = null,
    activeTimerRemainingSeconds: Int = 0,
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
    onAddRest: (exerciseId: Long) -> Unit = { _ -> },
    onUpdateSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateCardioSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateTimedSet: (Int, String, String, Boolean) -> Unit,
    onDeleteSet: (Int) -> Unit,
    onUpdateSetupNotes: (String) -> Unit,
    onUpdateSetNotes: (Int, String) -> Unit,
    onWeightChanged: (Int, String) -> Unit = { _, _ -> },
    onRepsChanged: (Int, String) -> Unit = { _, _ -> },
    onCompleteSet: (Int) -> Unit = { _ -> },
    onSelectSetType: (setOrder: Int, setType: SetType) -> Unit = { _, _ -> },
    onUpdateRpe: (setOrder: Int, rpe: Int?) -> Unit = { _, _ -> },
    onDeleteLocalRestTime: (setOrder: Int) -> Unit = { _ -> },
    onUpdateLocalRestTime: (setOrder: Int, seconds: Int) -> Unit = { _, _ -> },
    restTimeOverrides: Map<String, Int> = emptyMap(),
    onTimerActiveClick: () -> Unit = {},
    onDeleteRestSeparator: (setOrder: Int) -> Unit = { _ -> },
    hiddenRestSeparators: Set<String> = emptySet(),
    onLongPress: () -> Unit = {},
    isEditMode: Boolean = false
) {
    var showSetupNotesEditor by remember { mutableStateOf(false) }
    var setupNotesText by remember { mutableStateOf(exerciseWithSets.exercise.setupNotes ?: "") }
    var showManagementHub by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var showSessionNoteDialog by remember { mutableStateOf(false) }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var showRestTimerSheet by remember { mutableStateOf(false) }
    var showUpdateRestTimersFor by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var isCollapsed by rememberSaveable { mutableStateOf(false) }

    val isInSuperset = exerciseWithSets.supersetGroupId != null
    val borderModifier = when {
        isSelectMode && isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        isInSuperset && isMyTurn   -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        else                       -> Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = exerciseWithSets.exercise.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
                    Row {
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
                        IconButton(onClick = { isCollapsed = !isCollapsed }) {
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
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
                    onUpdateRestTimer = { showManagementHub = false; showUpdateRestTimersFor = true },
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

            if (showUpdateRestTimersFor) {
                UpdateRestTimersDialog(
                    currentSeconds = exerciseWithSets.exercise.restDurationSeconds,
                    onDismiss = { showUpdateRestTimersFor = false },
                    onConfirm = { newSeconds ->
                        onUpdateRestTimer(newSeconds)
                        showUpdateRestTimersFor = false
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
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

            // Collapsed summary: show set count when card is minimized
            if (isCollapsed) {
                Text(
                    text = "${exerciseWithSets.sets.size} sets",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column {
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
                            Text(
                                "SET", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(SET_COL_WEIGHT)
                            )
                            Text(
                                "PREV", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(PREV_COL_WEIGHT)
                            )
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

                    Spacer(modifier = Modifier.height(8.dp))

                    val exerciseId = exerciseWithSets.exercise.id
                    var showRestTimePickerForSet by remember { mutableStateOf<Int?>(null) }

                    exerciseWithSets.sets.forEachIndexed { index, set ->
                        val effectiveRest = restTimeOverrides["${exerciseId}_${set.setOrder}"]
                            ?: exerciseWithSets.exercise.restDurationSeconds
                        val separatorKey = "${exerciseId}_${set.setOrder}"

                        key(set.id) {
                            val swipeState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onDeleteSet(set.setOrder)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = swipeState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    SwipeToDeleteBackground(swipeState.progress)
                                }
                            ) {
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
                                            onSelectSetType = { type -> onSelectSetType(set.setOrder, type) },
                                            onUpdateRpe = { rpe -> onUpdateRpe(set.setOrder, rpe) },
                                            onDeleteTimer = { onDeleteLocalRestTime(set.setOrder) }
                                        )
                                    }
                                }
                            }
                        }

                        val isThisTimerActive = (activeTimerExerciseId == exerciseWithSets.exercise.id) &&
                            (activeTimerSetOrder == set.setOrder)
                        val isNotLastSet = index < exerciseWithSets.sets.size - 1
                        val isSeparatorHidden = separatorKey in hiddenRestSeparators
                        if (!isEditMode && (isThisTimerActive || isNotLastSet) && !isSeparatorHidden) {
                            val restSwipeState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onDeleteRestSeparator(set.setOrder)
                                        true
                                    } else false
                                }
                            )
                            SwipeToDismissBox(
                                state = restSwipeState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    SwipeToDeleteBackground(restSwipeState.progress)
                                }
                            ) {
                                RestSeparator(
                                    restSeconds = effectiveRest,
                                    isActive = isThisTimerActive,
                                    liveRemainingSeconds = activeTimerRemainingSeconds,
                                    onActiveClick = { if (isThisTimerActive) onTimerActiveClick() },
                                    onPassiveClick = { showRestTimePickerForSet = set.setOrder }
                                )
                            }
                        } else if (isEditMode || (!isThisTimerActive && !isNotLastSet)) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (showRestTimePickerForSet != null) {
                        val setOrder = showRestTimePickerForSet!!
                        val currentRest = restTimeOverrides["${exerciseId}_${setOrder}"]
                            ?: exerciseWithSets.exercise.restDurationSeconds
                        RestTimePickerDialog(
                            currentSeconds = currentRest,
                            onDismiss = { showRestTimePickerForSet = null },
                            onConfirm = { newSeconds ->
                                onUpdateLocalRestTime(setOrder, newSeconds)
                                showRestTimePickerForSet = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Add Set — primary action
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onAddSet() },
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
                                "Add Set (%d:%02d)".format(
                                    exerciseWithSets.exercise.restDurationSeconds / 60,
                                    exerciseWithSets.exercise.restDurationSeconds % 60
                                ),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        // Add Rest — secondary muted action (TextButton style)
                        TextButton(
                            onClick = { onAddRest(exerciseWithSets.exercise.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Add Rest",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Rest",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
        } // closes Row(IntrinsicSize.Min)
    }
}

@Composable
private fun RestSeparator(
    restSeconds: Int,
    isActive: Boolean = false,
    liveRemainingSeconds: Int = 0,
    onActiveClick: () -> Unit = {},
    onPassiveClick: () -> Unit = {}
) {
    fun formatSecs(s: Int) = "%d:%02d".format(s / 60, s % 60)
    Crossfade(targetState = isActive, label = "RestTimerTransition") { active ->
        if (active) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable(onClick = onActiveClick)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSecs(liveRemainingSeconds),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(onClick = onPassiveClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = TimerGreen.copy(alpha = 0.5f)
                )
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = formatSecs(restSeconds),
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMono,
                        color = TimerGreen.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = TimerGreen.copy(alpha = 0.5f)
                )
            }
        }
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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

private fun formatRpeValue(rpe: Int): String = if (rpe % 10 == 0) "${rpe / 10}" else "${rpe / 10}.5"

private fun formatGhostLabel(ghostWeight: String?, ghostReps: String?, ghostRpe: String?): String {
    if (ghostWeight == null && ghostReps == null) return "—"
    val base = "${ghostWeight ?: "?"}kg×${ghostReps ?: "?"}"
    val rpeInt = ghostRpe?.toIntOrNull()
    return if (rpeInt != null) "$base@${formatRpeValue(rpeInt)}" else base
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SetTypePickerSheet(
    currentSetType: SetType,
    onSetTypeSelected: (SetType) -> Unit,
    onDeleteTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var showInfoFor by remember { mutableStateOf<SetType?>(null) }

    val setTypeDescriptions = mapOf(
        SetType.NORMAL  to "A standard working set counted in performance metrics.",
        SetType.WARMUP  to "A lighter set to prepare muscles and joints. Not counted in performance metrics.",
        SetType.DROP    to "Follows a working set immediately with reduced weight and no rest. Extends time under tension.",
        SetType.FAILURE to "A set taken to muscular failure — you cannot complete another rep with good form."
    )

    if (showInfoFor != null) {
        AlertDialog(
            onDismissRequest = { showInfoFor = null },
            title = { Text(showInfoFor!!.name.lowercase().replaceFirstChar { it.uppercase() }) },
            text = { Text(setTypeDescriptions[showInfoFor!!] ?: "") },
            confirmButton = {
                TextButton(onClick = { showInfoFor = null }) { Text("OK") }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Set Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SetType.entries.forEach { type ->
                val label = when (type) {
                    SetType.NORMAL  -> "Normal"
                    SetType.WARMUP  -> "Warm Up"
                    SetType.DROP    -> "Drop Set"
                    SetType.FAILURE -> "Failure"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetTypeSelected(type); onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentSetType == type,
                        onClick = { onSetTypeSelected(type); onDismiss() }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { showInfoFor = type }) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = "Info about $label",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = { onDeleteTimer(); onDismiss() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Timer")
            }
        }
    }
}

@Composable
private fun SwipeToDeleteBackground(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.error.copy(alpha = (fraction * 2f).coerceIn(0f, 1f)))
            .padding(end = 16.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onError)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RpePickerSheet(currentRpe: Int?, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {
    val values = (12..20).map { it * 5 }.reversed()  // 100, 95, …, 60
    val rpeDescriptions = mapOf(
        100 to "Max Effort. No reps left.",
        95  to "Near Max. No reps left, maybe more weight.",
        90  to "1 RIR. Could have done 1 more rep.",
        85  to "1–2 RIR. Definitely 1 more, maybe 2.",
        80  to "2 RIR. Could have done 2 more reps.",
        75  to "2–3 RIR. Definitely 2 more, maybe 3.",
        70  to "3 RIR. Fast bar speed, 3 reps left.",
        65  to "4+ RIR. Light/Warm-up weight.",
        60  to "4+ RIR. Light/Warm-up weight."
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Rate of Perceived Exertion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            values.forEach { rpeVal ->
                val isSelected = currentRpe == rpeVal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(rpeVal); onDismiss() }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatRpeValue(rpeVal),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(36.dp)
                    )
                    Text(
                        text = rpeDescriptions[rpeVal] ?: "",
                        fontSize = 13.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TextButton(
                onClick = { onSelect(null); onDismiss() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 8.dp)
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.CollapsedExerciseReorderRow(
    exerciseWithSets: ExerciseWithSets,
    isDragging: Boolean,
    onDragStopped: () -> Unit
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elev")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shadowElevation = elevation,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = Modifier.draggableHandle(onDragStopped = onDragStopped),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = exerciseWithSets.exercise.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SuggestionChip(
                onClick = {},
                label = { Text(exerciseWithSets.exercise.muscleGroup, fontSize = 11.sp) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutSetRow(
    set: ActiveSet,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onSelectSetType: (SetType) -> Unit = { _ -> },
    onUpdateRpe: (rpe: Int?) -> Unit = {},
    onDeleteTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showRpePicker by remember { mutableStateOf(false) }
    var showSetTypePicker by remember { mutableStateOf(false) }
    if (showRpePicker) {
        RpePickerSheet(
            currentRpe = set.rpeValue,
            onDismiss = { showRpePicker = false },
            onSelect = { onUpdateRpe(it) }
        )
    }
    if (showSetTypePicker) {
        SetTypePickerSheet(
            currentSetType = set.setType,
            onSetTypeSelected = { onSelectSetType(it) },
            onDeleteTimer = onDeleteTimer,
            onDismiss = { showSetTypePicker = false }
        )
    }

    val (setLabel, setColor) = when (set.setType) {
        SetType.NORMAL  -> "${set.setOrder}" to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP  -> "W"               to MedicalAmber
        SetType.FAILURE -> "F"               to StremioError
        SetType.DROP    -> "D"               to StremioMagenta
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                if (set.isCompleted) TimerGreen.copy(alpha = 0.2f)
                else Color.Transparent
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SET column — number pill, tap opens SetTypePickerSheet
        Box(
            modifier = Modifier
                .weight(SET_COL_WEIGHT)
                .fillMaxHeight()
                .clickable { showSetTypePicker = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = setLabel,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = setColor,
                textAlign = TextAlign.Center
            )
        }
        // PREV column — previous session ghost label
        Box(
            modifier = Modifier
                .weight(PREV_COL_WEIGHT)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatGhostLabel(set.ghostWeight, set.ghostReps, set.ghostRpe),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // WEIGHT column
        WorkoutInputField(
            value = set.weight, onValueChange = onWeightChanged,
            placeholder = set.ghostWeight ?: "", keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(WEIGHT_COL_WEIGHT).padding(horizontal = 2.dp)
        )
        // REPS column
        WorkoutInputField(
            value = set.reps, onValueChange = onRepsChanged,
            placeholder = set.ghostReps ?: "", keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(REPS_COL_WEIGHT).padding(horizontal = 2.dp)
        )
        // RPE column — always visible, tap opens picker
        Box(
            modifier = Modifier
                .weight(RPE_COL_WEIGHT)
                .fillMaxHeight()
                .clickable { showRpePicker = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (set.rpeValue != null) formatRpeValue(set.rpeValue!!) else "–",
                fontSize = 13.sp,
                fontWeight = if (set.rpeValue != null) FontWeight.Bold else FontWeight.Normal,
                color = if (set.rpeValue != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                textAlign = TextAlign.Center
            )
        }
        // CHECK column — ✓ button
        Box(
            modifier = Modifier
                .weight(CHECK_COL_WEIGHT)
                .fillMaxHeight()
                .background(
                    color = if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable(onClick = onCompleteSet),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (set.isCompleted) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
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
        containerColor = MaterialTheme.colorScheme.surface
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerControlsSheet(
    restTimer: RestTimerState,
    onDismiss: () -> Unit,
    onAddTime: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit
) {
    fun formatSecs(s: Int) = "%d:%02d".format(s / 60, s % 60)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = formatSecs(restTimer.remainingSeconds),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (restTimer.isPaused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primary
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(onClick = { onAddTime(-10) }) { Text("-10s") }
                IconButton(onClick = if (restTimer.isPaused) onResume else onPause) {
                    Icon(
                        imageVector = if (restTimer.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (restTimer.isPaused) "Resume" else "Pause"
                    )
                }
                FilledTonalButton(onClick = { onAddTime(10) }) { Text("+10s") }
            }
            Button(
                onClick = { onSkip(); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip")
            }
        }
    }
}

@Composable
fun RestTimePickerDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableStateOf((currentSeconds / 60).toString()) }
    var seconds by remember { mutableStateOf((currentSeconds % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set rest duration") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = seconds,
                    onValueChange = { seconds = it },
                    label = { Text("Sec") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))
                }
            ) { Text("CONFIRM") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun UpdateRestTimersDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutes by remember { mutableStateOf((currentSeconds / 60).toString()) }
    var seconds by remember { mutableStateOf((currentSeconds % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update rest timers") },
        text = {
            Column {
                Text(
                    "Completed timers will not be affected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Text("Work set", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = { Text("Sec") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))
                }
            ) { Text("UPDATE REST TIMERS") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}
