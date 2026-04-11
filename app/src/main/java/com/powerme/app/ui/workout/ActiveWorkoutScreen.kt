package com.powerme.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.ui.components.MagicAddDialog
import com.powerme.app.ui.components.WorkoutInputField
import com.powerme.app.ui.theme.*
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import java.util.ArrayList
import kotlin.math.abs

// Superset spine/icon color palette — one stable color per supersetGroupId (hash-indexed)
private val SupersetPalette = listOf(
    Color(0xFFE91E8C),  // Pink
    Color(0xFF4CAF50),  // Green
    Color(0xFFFFEB3B),  // Yellow
    Color(0xFFFF9800),  // Orange
    Color(0xFF00BCD4),  // Cyan
    Color(0xFF9C27B0),  // Purple
    Color(0xFFFF5722),  // Deep Orange
    Color(0xFF03A9F4),  // Light Blue
)
private fun supersetColor(groupId: String?): Color =
    if (groupId == null) Color.Transparent
    else SupersetPalette[abs(groupId.hashCode()) % SupersetPalette.size]

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
    onMinimize: () -> Unit = {},
    onNavigateToTimer: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val medicalDoc by viewModel.medicalDoc.collectAsState()
    val clocksTimerState by viewModel.clocksTimerState.collectAsState()
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }
    var showTimerControls by remember { mutableStateOf(false) }
    var showStandaloneTimerConfig by remember { mutableStateOf(false) }
    val view = LocalView.current
    var isReorderMode by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(workoutState.snackbarMessage) {
        val msg = workoutState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    LaunchedEffect(workoutState.editModeSaved) {
        if (workoutState.editModeSaved) {
            onWorkoutFinished()
        }
    }

    // System back: minimize live workout instead of destroying it
    BackHandler(enabled = workoutState.isActive && !workoutState.isEditMode) {
        viewModel.minimizeWorkout()
        onMinimize()
    }

    // System back in edit mode: show discard confirmation
    BackHandler(enabled = workoutState.isEditMode) {
        showDiscardEditDialog = true
    }

    // Standalone Timer Config Sheet
    if (showStandaloneTimerConfig) {
        StandaloneTimerConfigSheet(
            onDismiss = { showStandaloneTimerConfig = false },
            onStartTimer = { seconds ->
                viewModel.startStandaloneTimer(seconds)
                showStandaloneTimerConfig = false
            }
        )
    }

    // Standalone Timer Overlay (Full Screen when active)
    if (workoutState.standaloneTimer.isActive) {
        StandaloneTimerOverlay(
            state = workoutState.standaloneTimer,
            onClose = { viewModel.stopStandaloneTimer() },
            onPause = { viewModel.pauseStandaloneTimer() },
            onResume = { viewModel.resumeStandaloneTimer() },
            onSkip = { viewModel.stopStandaloneTimer() },
            onAdd30s = { viewModel.addTimeToStandaloneTimer(30) },
            onSubtract30s = { viewModel.subtractFromStandaloneTimer(30) }
        )
    }

    // Post-workout summary sheet — shown whenever summary is available (routine sync is inside sheet)
    workoutState.pendingWorkoutSummary?.let { summary ->
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
            },
            onConfirmSyncValues = { viewModel.confirmUpdateRoutineValues() },
            onConfirmSyncStructure = { viewModel.confirmUpdateRoutineStructure() },
            onConfirmSyncBoth = { viewModel.confirmUpdateBoth() },
            onDismissSync = { viewModel.dismissRoutineSync() }
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

    // Discard edit changes confirmation dialog
    if (showDiscardEditDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardEditDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to discard the changes you've made in the routine?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardEditDialog = false
                    viewModel.cancelEditMode()
                    onWorkoutFinished()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEditDialog = false }) { Text("Keep Editing") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            if (workoutState.isActive || workoutState.isEditMode) {
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
                            viewModel.minimizeWorkout(); onMinimize()
                        }) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.primary
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontFamily = JetBrainsMono
                                )
                            }
                        }

                        if (workoutState.isEditMode) {
                            IconButton(onClick = { showDiscardEditDialog = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = { viewModel.minimizeWorkout(); onNavigateToTimer() }) {
                                Icon(Icons.Default.Timer, contentDescription = "Timer", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Clocks tab countdown progress bar — tap to jump back to Clocks
                val clocksSnap = clocksTimerState
                if (clocksSnap != null) {
                    LinearProgressIndicator(
                        progress = { clocksSnap.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clickable { viewModel.minimizeWorkout(); onNavigateToTimer() },
                        color = TimerGreen,
                        trackColor = TimerGreen.copy(alpha = 0.15f)
                    )
                }

                val activeTimerExerciseId = workoutState.restTimer.exerciseId
                val activeTimerSetOrder = workoutState.restTimer.setOrder
                val activeTimerRemainingSeconds = workoutState.restTimer.remainingSeconds
                val activeTimerTotalSeconds = workoutState.restTimer.totalSeconds

                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val exercises = workoutState.exercises
                    val fromIdx = exercises.indexOfFirst { it.exercise.id == from.key as Long }
                    val toIdx = exercises.indexOfFirst { it.exercise.id == to.key as Long }
                    if (fromIdx >= 0 && toIdx >= 0) viewModel.reorderExercise(fromIdx, toIdx)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    activeWorkoutListItems(
                        workoutState = workoutState,
                        viewModel = viewModel,
                        activeTimerExerciseId = activeTimerExerciseId,
                        activeTimerSetOrder = activeTimerSetOrder,
                        activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                        activeTimerTotalSeconds = activeTimerTotalSeconds,
                        onShowExerciseDialog = { showExerciseDialog = true },
                        onTimerActiveClick = { showTimerControls = true },
                        onCancelWorkout = { showCancelDialog = true },
                        isEditMode = workoutState.isEditMode,
                        reorderableLazyListState = reorderableLazyListState
                    )
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    )
    } // end Box

    if (showExerciseDialog) {
        MagicAddDialog(
            onDismiss = { showExerciseDialog = false },
            onExerciseAdded = { exercise ->
                viewModel.addExercise(exercise)
                showExerciseDialog = false
            }
        )
    }

    if (showTimerControls) {
        TimerControlsSheet(
            remainingSeconds = workoutState.restTimer.remainingSeconds,
            isPaused = workoutState.restTimer.isPaused,
            onDismiss = { showTimerControls = false },
            onAdjustTime = { delta -> viewModel.addTimeToTimer(delta) },
            onPauseResume = { if (workoutState.restTimer.isPaused) viewModel.resumeRestTimer() else viewModel.pauseRestTimer() },
            onSkip = {
                viewModel.skipRestTimer()
                showTimerControls = false
            }
        )
    }
}

private fun LazyListScope.activeWorkoutListItems(
    workoutState: ActiveWorkoutState,
    viewModel: WorkoutViewModel,
    activeTimerExerciseId: Long?,
    activeTimerSetOrder: Int?,
    activeTimerRemainingSeconds: Int,
    activeTimerTotalSeconds: Int,
    onShowExerciseDialog: () -> Unit,
    onTimerActiveClick: () -> Unit,
    onCancelWorkout: () -> Unit,
    isEditMode: Boolean = false,
    reorderableLazyListState: sh.calvin.reorderable.ReorderableLazyListState? = null
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

    if (workoutState.warmupCompleted && workoutState.exercises.isNotEmpty()) {
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
                        Icon(Icons.Default.Sync, contentDescription = "Commit Superset", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // Exercises
    items(
        items = workoutState.exercises,
        key = { it.exercise.id }
    ) { exerciseWithSets ->
        val isSelected = workoutState.supersetCandidateIds.contains(exerciseWithSets.exercise.id)
        val isCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedExerciseIds

        // Superset selection mode: collapsed selectable rows (with drag-to-reorder)
        if (workoutState.isSupersetSelectMode) {
            if (reorderableLazyListState != null) {
                ReorderableItem(reorderableLazyListState, key = exerciseWithSets.exercise.id) { _ ->
                    SupersetSelectRow(
                        exerciseWithSets = exerciseWithSets,
                        isSelected = isSelected,
                        onToggle = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                        dragHandleModifier = Modifier.draggableHandle()
                    )
                }
            } else {
                SupersetSelectRow(
                    exerciseWithSets = exerciseWithSets,
                    isSelected = isSelected,
                    onToggle = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) }
                )
            }
            return@items
        }

        if (reorderableLazyListState != null) {
            ReorderableItem(reorderableLazyListState, key = exerciseWithSets.exercise.id) { _ ->
                ExerciseCard(
                    exerciseWithSets = exerciseWithSets,
                    isSelectMode = workoutState.isSupersetSelectMode,
                    isSelected = isSelected,
                    activeTimerExerciseId = activeTimerExerciseId,
                    activeTimerSetOrder = activeTimerSetOrder,
                    activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                    activeTimerTotalSeconds = activeTimerTotalSeconds,
                    restTimeOverrides = workoutState.restTimeOverrides,
                    hiddenRestSeparators = workoutState.hiddenRestSeparators,
                    isEditMode = isEditMode,
                    isCollapsed = isCollapsed,
                    onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                    onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                    dragHandleModifier = Modifier.draggableHandle(onDragStarted = { viewModel.collapseAll() }),
                    onToggleSelect = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                    onAddSet = { viewModel.addSet(exerciseWithSets.exercise.id) },
                    onDeleteSet = { setOrder -> viewModel.deleteSet(exerciseWithSets.exercise.id, setOrder) },
                    onUpdateSetupNotes = { notes -> viewModel.updateSetupNotes(exerciseWithSets.exercise.id, notes) },
                    onUpdateSetNotes = { setOrder, notes -> viewModel.updateSetNotes(exerciseWithSets.exercise.id, setOrder, notes) },
                    onWeightChanged = { setOrder, weight -> viewModel.onWeightChanged(exerciseWithSets.exercise.id, setOrder, weight) },
                    onRepsChanged = { setOrder, reps -> viewModel.onRepsChanged(exerciseWithSets.exercise.id, setOrder, reps) },
                    onUpdateCardioSet = { setOrder, dist, time, rpe, completed -> viewModel.updateCardioSet(exerciseWithSets.exercise.id, setOrder, dist, time, rpe, completed = completed) },
                    onUpdateTimedSet = { setOrder, time, rpe, completed -> viewModel.updateTimedSet(exerciseWithSets.exercise.id, setOrder, "", time, rpe, completed = completed) },
                    onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                    onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                    onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                    onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                    onUpdateExerciseRestTimers = { work, warmup, drop -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop) },
                    onAddWarmupSets = { viewModel.addWarmupSetsToExercise(exerciseWithSets.exercise.id) },
                    onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                    onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                    onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                    onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                    onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                    onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                    onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                    onTimerActiveClick = onTimerActiveClick,
                    onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) }
                )
            }
        } else {
            ExerciseCard(
                exerciseWithSets = exerciseWithSets,
                isSelectMode = workoutState.isSupersetSelectMode,
                isSelected = isSelected,
                activeTimerExerciseId = activeTimerExerciseId,
                activeTimerSetOrder = activeTimerSetOrder,
                activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                activeTimerTotalSeconds = activeTimerTotalSeconds,
                restTimeOverrides = workoutState.restTimeOverrides,
                hiddenRestSeparators = workoutState.hiddenRestSeparators,
                isEditMode = isEditMode,
                isCollapsed = isCollapsed,
                onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                dragHandleModifier = null,
                onToggleSelect = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                onAddSet = { viewModel.addSet(exerciseWithSets.exercise.id) },
                onDeleteSet = { setOrder -> viewModel.deleteSet(exerciseWithSets.exercise.id, setOrder) },
                onUpdateSetupNotes = { notes -> viewModel.updateSetupNotes(exerciseWithSets.exercise.id, notes) },
                onUpdateSetNotes = { setOrder, notes -> viewModel.updateSetNotes(exerciseWithSets.exercise.id, setOrder, notes) },
                onWeightChanged = { setOrder, weight -> viewModel.onWeightChanged(exerciseWithSets.exercise.id, setOrder, weight) },
                onRepsChanged = { setOrder, reps -> viewModel.onRepsChanged(exerciseWithSets.exercise.id, setOrder, reps) },
                onUpdateCardioSet = { setOrder, dist, time, rpe, completed -> viewModel.updateCardioSet(exerciseWithSets.exercise.id, setOrder, dist, time, rpe, completed = completed) },
                onUpdateTimedSet = { setOrder, time, rpe, completed -> viewModel.updateTimedSet(exerciseWithSets.exercise.id, setOrder, "", time, rpe, completed = completed) },
                onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                onUpdateExerciseRestTimers = { work, warmup, drop -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop) },
                onAddWarmupSets = { viewModel.addWarmupSetsToExercise(exerciseWithSets.exercise.id) },
                onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                onTimerActiveClick = onTimerActiveClick,
                onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) }
            )
        }
    }

    item {
        Button(
            onClick = onShowExerciseDialog,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            Spacer(modifier = Modifier.width(8.dp))
            Text("ADD EXERCISE", fontWeight = FontWeight.Bold)
        }
    }

    if (!isEditMode) {
        item {
            TextButton(
                onClick = onCancelWorkout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CANCEL WORKOUT", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }

    item {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isEditMode) viewModel.saveRoutineEdits() else viewModel.finishWorkout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            colors = ButtonDefaults.buttonColors(
                containerColor = TimerGreen.copy(alpha = 0.15f),
                contentColor = TimerGreen
            )
        ) {
            Text(
                text = if (isEditMode) "SAVE CHANGES" else "FINISH WORKOUT",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    isSelectMode: Boolean,
    isSelected: Boolean,
    activeTimerExerciseId: Long?,
    activeTimerSetOrder: Int?,
    activeTimerRemainingSeconds: Int,
    activeTimerTotalSeconds: Int,
    onToggleSelect: () -> Unit,
    onAddSet: () -> Unit,
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
    isEditMode: Boolean = false,
    onReplaceExercise: (Exercise) -> Unit,
    onRemoveExercise: () -> Unit,
    onUpdateSessionNote: (String) -> Unit,
    onUpdateStickyNote: (String) -> Unit,
    onUpdateExerciseRestTimers: (workSeconds: Int, warmupSeconds: Int, dropSeconds: Int) -> Unit,
    onAddWarmupSets: () -> Unit,
    onEnterSupersetMode: () -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onUpdateCardioSet: (Int, String, String, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    onUpdateTimedSet: (Int, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    isCollapsed: Boolean = false,
    onCollapseAllExcept: () -> Unit = {},
    onToggleCollapsed: () -> Unit = {},
    dragHandleModifier: Modifier? = null
) {
    var showSetupNotesEditor by remember { mutableStateOf(false) }
    var setupNotesText by remember { mutableStateOf(exerciseWithSets.exercise.setupNotes ?: "") }
    var showManagementHub by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var showSessionNoteDialog by remember { mutableStateOf(false) }
    var sessionNoteText by remember { mutableStateOf(exerciseWithSets.sessionNote ?: "") }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var stickyNoteText by remember { mutableStateOf(exerciseWithSets.stickyNote ?: "") }
    var showRestTimerSheet by remember { mutableStateOf(false) }
    var showUpdateRestTimersFor by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }

    val isInSuperset = exerciseWithSets.supersetGroupId != null
    val borderModifier = when {
        isSelectMode && isSelected -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
        else -> Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .combinedClickable(
                onClick = { if (isSelectMode) onToggleSelect() },
                onLongClick = { onLongPress() }
            )
            .animateContentSize(),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (isInSuperset) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(supersetColor(exerciseWithSets.supersetGroupId))
                )
            }
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                // Exercise header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle — visible in active mode only (modifier carries draggableHandle())
                    if (dragHandleModifier != null) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = dragHandleModifier.padding(end = 4.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCollapseAllExcept() }
                    ) {
                        Text(
                            text = exerciseWithSets.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        exerciseWithSets.exercise.muscleGroup?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showManagementHub = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        IconButton(onClick = { onToggleCollapsed() }) {
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                if (!exerciseWithSets.stickyNote.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PushPin, contentDescription = "Sticky", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(text = exerciseWithSets.stickyNote!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isCollapsed) {
                    val isThisExerciseTimerActive = activeTimerExerciseId == exerciseWithSets.exercise.id
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${exerciseWithSets.sets.size} sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        if (isThisExerciseTimerActive && activeTimerRemainingSeconds > 0) {
                            Text(
                                text = "%d:%02d".format(activeTimerRemainingSeconds / 60, activeTimerRemainingSeconds % 60),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !isCollapsed) {
                    Column {
                        // Set headers
                        when (exerciseWithSets.exercise.exerciseType) {
                            ExerciseType.CARDIO -> CardioHeader()
                            ExerciseType.TIMED -> TimedHeader()
                            else -> StrengthHeader(isEditMode = isEditMode)
                        }

                        if (!exerciseWithSets.sessionNote.isNullOrBlank()) {
                            Text(
                                text = exerciseWithSets.sessionNote!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }

                        val exerciseId = exerciseWithSets.exercise.id
                        var showRestTimePickerForSet by remember { mutableStateOf<Int?>(null) }

                        val setCount = exerciseWithSets.sets.size
                        val weightFrs = remember(setCount) { List(setCount) { FocusRequester() } }
                        val repsFrs = remember(setCount) { List(setCount) { FocusRequester() } }

                        exerciseWithSets.sets.forEachIndexed { index, set ->
                            val nextSetType = exerciseWithSets.sets.getOrNull(index + 1)?.setType
                            val defaultRestForType = when (set.setType) {
                                SetType.DROP    -> exerciseWithSets.exercise.dropSetRestSeconds
                                SetType.FAILURE -> exerciseWithSets.exercise.restDurationSeconds
                                SetType.WARMUP  -> if (nextSetType == SetType.WARMUP) exerciseWithSets.exercise.warmupRestSeconds
                                                   else exerciseWithSets.exercise.restDurationSeconds
                                SetType.NORMAL  -> if (nextSetType == SetType.DROP) exerciseWithSets.exercise.dropSetRestSeconds
                                                   else exerciseWithSets.exercise.restDurationSeconds
                            }
                            val effectiveRest = restTimeOverrides["${exerciseId}_${set.setOrder}"] ?: defaultRestForType
                            val separatorKey = "${exerciseId}_${set.setOrder}"
                            val isThisTimerActive = (activeTimerExerciseId == exerciseId) && (activeTimerSetOrder == set.setOrder)
                            val isNotLastSet = index < exerciseWithSets.sets.size - 1
                            val isSeparatorHidden = separatorKey in hiddenRestSeparators
                            val shouldShowSeparator = (isThisTimerActive || isNotLastSet) && !isSeparatorHidden
                            val nextIncompleteIdx = (index + 1 until exerciseWithSets.sets.size)
                                .firstOrNull { !exerciseWithSets.sets[it].isCompleted }

                            key(set.id) {
                                SetWithRestRow(
                                    set = set,
                                    exerciseType = exerciseWithSets.exercise.exerciseType,
                                    shouldShowSeparator = shouldShowSeparator,
                                    isThisTimerActive = isThisTimerActive,
                                    effectiveRest = effectiveRest,
                                    activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                                    activeTimerTotalSeconds = activeTimerTotalSeconds,
                                    onWeightChanged = { onWeightChanged(set.setOrder, it) },
                                    onRepsChanged = { onRepsChanged(set.setOrder, it) },
                                    onCompleteSet = { onCompleteSet(set.setOrder) },
                                    onSelectSetType = { onSelectSetType(set.setOrder, it) },
                                    onUpdateRpe = { onUpdateRpe(set.setOrder, it) },
                                    onDeleteSet = { onDeleteSet(set.setOrder) },
                                    onUpdateCardioSet = { d, t, r, c -> onUpdateCardioSet(set.setOrder, d, t, r, c) },
                                    onUpdateTimedSet = { t, r, c -> onUpdateTimedSet(set.setOrder, t, r, c) },
                                    onDeleteLocalRestTime = { onDeleteLocalRestTime(set.setOrder) },
                                    onDeleteRestSeparator = { onDeleteRestSeparator(set.setOrder) },
                                    onTimerActiveClick = onTimerActiveClick,
                                    onPassiveRestClick = { showRestTimePickerForSet = set.setOrder },
                                    weightFocusRequester = weightFrs.getOrNull(index),
                                    repsFocusRequester = repsFrs.getOrNull(index),
                                    nextWeightFocusRequester = nextIncompleteIdx?.let { weightFrs.getOrNull(it) },
                                    onRepsDone = { onCompleteSet(set.setOrder) },
                                    isEditMode = isEditMode
                                )
                            }
                        }

                        if (showRestTimePickerForSet != null) {
                            val setOrder = showRestTimePickerForSet!!
                            RestTimePickerDialog(
                                currentSeconds = restTimeOverrides["${exerciseId}_${setOrder}"] ?: run {
                                    val setIdx = exerciseWithSets.sets.indexOfFirst { it.setOrder == setOrder }
                                    val theSet = exerciseWithSets.sets.getOrNull(setIdx)
                                    val nextType = exerciseWithSets.sets.getOrNull(setIdx + 1)?.setType
                                    when (theSet?.setType) {
                                        SetType.DROP    -> exerciseWithSets.exercise.dropSetRestSeconds
                                        SetType.FAILURE -> exerciseWithSets.exercise.restDurationSeconds
                                        SetType.WARMUP  -> if (nextType == SetType.WARMUP) exerciseWithSets.exercise.warmupRestSeconds
                                                           else exerciseWithSets.exercise.restDurationSeconds
                                        SetType.NORMAL  -> if (nextType == SetType.DROP) exerciseWithSets.exercise.dropSetRestSeconds
                                                           else exerciseWithSets.exercise.restDurationSeconds
                                        else -> exerciseWithSets.exercise.restDurationSeconds
                                    }
                                },
                                onDismiss = { showRestTimePickerForSet = null },
                                onConfirm = { onUpdateLocalRestTime(setOrder, it); showRestTimePickerForSet = null }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.weight(1f).height(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall).clickable { onAddSet() },
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Set (%d:%02d)".format(exerciseWithSets.exercise.restDurationSeconds / 60, exerciseWithSets.exercise.restDurationSeconds % 60), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManagementHub) {
        ManagementHubSheet(
            onDismiss = { showManagementHub = false },
            onReplace = { showReplaceDialog = true; showManagementHub = false },
            onRemove = { onRemoveExercise(); showManagementHub = false },
            onSessionNote = { showSessionNoteDialog = true; showManagementHub = false },
            onStickyNote = { showStickyNoteDialog = true; showManagementHub = false },
            onRestTimer = { showRestTimerSheet = true; showManagementHub = false },
            onSuperset = { if (isInSuperset) onRemoveFromSuperset() else onEnterSupersetMode(); showManagementHub = false },
            isInSuperset = isInSuperset
        )
    }

    if (showReplaceDialog) {
        MagicAddDialog(
            onDismiss = { showReplaceDialog = false },
            onExerciseAdded = { newExercise ->
                onReplaceExercise(newExercise)
                showReplaceDialog = false
            }
        )
    }

    if (showSessionNoteDialog) {
        AlertDialog(
            onDismissRequest = { showSessionNoteDialog = false },
            title = { Text("Session Note") },
            text = {
                OutlinedTextField(
                    value = sessionNoteText,
                    onValueChange = { sessionNoteText = it },
                    placeholder = { Text("Note for this session…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateSessionNote(sessionNoteText)
                    showSessionNoteDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSessionNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStickyNoteDialog) {
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Sticky Note") },
            text = {
                Column {
                    Text(
                        "Saved permanently with this exercise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = stickyNoteText,
                        onValueChange = { stickyNoteText = it },
                        placeholder = { Text("Form cue, injury note…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateStickyNote(stickyNoteText)
                    showStickyNoteDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showStickyNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestTimerSheet) {
        UpdateRestTimersDialog(
            workSeconds = exerciseWithSets.exercise.restDurationSeconds,
            warmupSeconds = exerciseWithSets.exercise.warmupRestSeconds,
            dropSeconds = exerciseWithSets.exercise.dropSetRestSeconds,
            onDismiss = { showRestTimerSheet = false },
            onConfirm = { work, warmup, drop ->
                onUpdateExerciseRestTimers(work, warmup, drop)
                showRestTimerSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetWithRestRow(
    set: ActiveSet,
    exerciseType: ExerciseType,
    shouldShowSeparator: Boolean,
    isThisTimerActive: Boolean,
    effectiveRest: Int,
    activeTimerRemainingSeconds: Int,
    activeTimerTotalSeconds: Int,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onSelectSetType: (SetType) -> Unit,
    onUpdateRpe: (Int?) -> Unit,
    onDeleteSet: () -> Unit,
    onUpdateCardioSet: (String, String, String, Boolean) -> Unit,
    onUpdateTimedSet: (String, String, Boolean) -> Unit,
    onDeleteLocalRestTime: () -> Unit,
    onDeleteRestSeparator: () -> Unit,
    onTimerActiveClick: () -> Unit,
    onPassiveRestClick: () -> Unit,
    weightFocusRequester: FocusRequester? = null,
    repsFocusRequester: FocusRequester? = null,
    nextWeightFocusRequester: FocusRequester? = null,
    onRepsDone: () -> Unit = {},
    isEditMode: Boolean = false
) {
    val setSwipeState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteSet(); true } else false })
    val restSwipeState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteRestSeparator(); true } else false })

    // Snap back to Settled after dismiss to prevent phantom red background lingering
    // in composition after the item is removed from the list (WORKOUT_SPEC.md §27 #24)
    LaunchedEffect(setSwipeState.currentValue) {
        if (setSwipeState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            setSwipeState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    LaunchedEffect(restSwipeState.currentValue) {
        if (restSwipeState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            restSwipeState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SwipeToDismissBox(
            state = setSwipeState,
            enableDismissFromStartToEnd = false,
            modifier = Modifier.zIndex(1f),
            backgroundContent = { SwipeToDeleteBackground(setSwipeState.progress) }
        ) {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                when (exerciseType) {
                    ExerciseType.CARDIO -> CardioSetRow(set, onUpdateCardioSet, onCompleteSet)
                    ExerciseType.TIMED -> TimedSetRow(set, onWeightChanged, onUpdateTimedSet, onCompleteSet)
                    else -> WorkoutSetRow(
                        set = set,
                        onWeightChanged = onWeightChanged,
                        onRepsChanged = onRepsChanged,
                        onCompleteSet = onCompleteSet,
                        onSelectSetType = onSelectSetType,
                        onUpdateRpe = onUpdateRpe,
                        onDeleteTimer = onDeleteLocalRestTime,
                        weightFocusRequester = weightFocusRequester,
                        repsFocusRequester = repsFocusRequester,
                        nextWeightFocusRequester = nextWeightFocusRequester,
                        onRepsDone = onRepsDone,
                        isEditMode = isEditMode
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = shouldShowSeparator,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
        ) {
            SwipeToDismissBox(
                state = restSwipeState,
                enableDismissFromStartToEnd = false,
                backgroundContent = { SwipeToDeleteBackground(restSwipeState.progress) }
            ) {
                Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    RestSeparator(
                        restSeconds = effectiveRest,
                        isActive = isThisTimerActive,
                        liveRemainingSeconds = if (isThisTimerActive) activeTimerRemainingSeconds else 0,
                        liveTotalSeconds = if (isThisTimerActive) activeTimerTotalSeconds else 0,
                        onActiveClick = onTimerActiveClick,
                        onPassiveClick = onPassiveRestClick
                    )
                }
            }
        }
    }
}

private fun formatGhostLabel(weight: String?, reps: String?, rpe: String?): String {
    if (weight == null || reps == null) return "—"
    return if (rpe != null) "${weight}\u00D7${reps}@${rpe}" else "${weight}\u00D7${reps}"
}

@Composable
private fun StrengthHeader(isEditMode: Boolean = false) {
    var showPrevTooltip by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(SET_COL_WEIGHT))
        Text(
            "PREV",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(PREV_COL_WEIGHT).clickable { showPrevTooltip = true }
        )
        Text("WEIGHT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(WEIGHT_COL_WEIGHT))
        Text("REPS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(REPS_COL_WEIGHT))
        if (!isEditMode) {
            Text("RPE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(RPE_COL_WEIGHT))
            Spacer(modifier = Modifier.weight(CHECK_COL_WEIGHT))
        }
    }
    if (showPrevTooltip) {
        AlertDialog(
            onDismissRequest = { showPrevTooltip = false },
            title = { Text("Previous") },
            text = { Text("Your last logged weight and reps for this exercise, across all sessions.") },
            confirmButton = { TextButton(onClick = { showPrevTooltip = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun UpdateRestTimersDialog(
    workSeconds: Int,
    warmupSeconds: Int,
    dropSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (workSeconds: Int, warmupSeconds: Int, dropSeconds: Int) -> Unit
) {
    val workMinsTfv = remember { mutableStateOf(TextFieldValue((workSeconds / 60).toString())) }
    val workSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(workSeconds % 60))) }
    val warmupMinsTfv = remember { mutableStateOf(TextFieldValue((warmupSeconds / 60).toString())) }
    val warmupSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(warmupSeconds % 60))) }
    val dropMinsTfv = remember { mutableStateOf(TextFieldValue((dropSeconds / 60).toString())) }
    val dropSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(dropSeconds % 60))) }
    val scope = rememberCoroutineScope()

    fun toSeconds(mins: String, secs: String): Int {
        val m = mins.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val s = secs.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return m * 60 + s
    }

    fun filteredTfv(newTfv: TextFieldValue): TextFieldValue {
        val filtered = newTfv.text.filter { c -> c.isDigit() }.take(2)
        return TextFieldValue(filtered, TextRange(filtered.length))
    }

    val mmSsBoxModifier: (MutableState<TextFieldValue>) -> Modifier = { tfvState ->
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                scope.launch {
                    delay(100)
                    val t = tfvState.value.text
                    tfvState.value = tfvState.value.copy(selection = TextRange(0, t.length))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update rest timers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Completed timers will not be affected.\nDurations will be saved for next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    Triple("Work set", workMinsTfv to workSecsTfv, Unit),
                    Triple("Warm up", warmupMinsTfv to warmupSecsTfv, Unit),
                    Triple("Drop set", dropMinsTfv to dropSecsTfv, Unit)
                ).forEach { (label, tfvPair, _) ->
                    val (minsState, secsState) = tfvPair
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = minsState.value,
                                onValueChange = { minsState.value = filteredTfv(it) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = mmSsBoxModifier(minsState),
                                decorationBox = { inner ->
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) { inner() }
                                }
                            )
                            Text(":", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
                            BasicTextField(
                                value = secsState.value,
                                onValueChange = { secsState.value = filteredTfv(it) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = mmSsBoxModifier(secsState),
                                decorationBox = { inner ->
                                    Box(
                                        modifier = Modifier
                                            .width(36.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) { inner() }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(
                    toSeconds(workMinsTfv.value.text, workSecsTfv.value.text),
                    toSeconds(warmupMinsTfv.value.text, warmupSecsTfv.value.text),
                    toSeconds(dropMinsTfv.value.text, dropSecsTfv.value.text)
                ) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("UPDATE REST TIMERS", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        },
        dismissButton = {}
    )
}

/** Collapsed row shown for each exercise during superset selection mode. */
@Composable
private fun SupersetSelectRow(
    exerciseWithSets: ExerciseWithSets,
    isSelected: Boolean,
    onToggle: () -> Unit,
    dragHandleModifier: Modifier? = null
) {
    val isInSuperset = exerciseWithSets.supersetGroupId != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dragHandleModifier != null) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = dragHandleModifier
                )
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exerciseWithSets.exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                exerciseWithSets.exercise.muscleGroup?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            if (isInSuperset) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = "In superset",
                    tint = supersetColor(exerciseWithSets.supersetGroupId),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandaloneTimerConfigSheet(onDismiss: () -> Unit, onStartTimer: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose a duration below or set your own.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(32.dp))
            Box(modifier = Modifier.size(240.dp).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(60, 120, 180, 240).forEach { seconds ->
                        TextButton(onClick = { onStartTimer(seconds) }, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("%d:%02d".format(seconds / 60, seconds % 60), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = JetBrainsMono)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = { /* Custom */ }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("CREATE CUSTOM TIMER", fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StandaloneTimerOverlay(state: RestTimerState, onClose: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onSkip: () -> Unit, onAdd30s: () -> Unit, onSubtract30s: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (state.totalSeconds > 0) state.remainingSeconds.toFloat() / state.totalSeconds.toFloat() else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "timerProgress"
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") } }
            Text("Adjust duration via the +/- buttons.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize(), strokeWidth = 4.dp, color = NeonPurple, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "%d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60), fontSize = 72.sp, fontWeight = FontWeight.Light, fontFamily = JetBrainsMono)
                    Text(text = "%d:%02d".format(state.totalSeconds / 60, state.totalSeconds % 60), fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontFamily = JetBrainsMono)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSubtract30s) { Text("-30 SEC", color = NeonPurple, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onAdd30s) { Text("+30 SEC", color = NeonPurple, fontWeight = FontWeight.Bold) }
                Button(onClick = onSkip, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple), modifier = Modifier.width(120.dp).height(48.dp)) { Text("SKIP", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteBackground(fraction: Float) {
    if (fraction <= 0.05f) return
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = (fraction * 2f).coerceIn(0f, 1f))).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
        if (fraction > 0.1f) Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onError)
    }
}

@Composable
private fun RestSeparator(restSeconds: Int, isActive: Boolean = false, liveRemainingSeconds: Int = 0, liveTotalSeconds: Int = 0, onActiveClick: () -> Unit = {}, onPassiveClick: () -> Unit = {}) {
    fun formatSecs(s: Int) = "%d:%02d".format(s / 60, s % 60)
    val flashAlpha = remember { Animatable(0.4f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            flashAlpha.snapTo(0.4f)
            flashAlpha.animateTo(0.15f, animationSpec = tween(300))
        }
    }
    // Fixed height on both states prevents layout jump during Crossfade transition
    Crossfade(targetState = isActive, label = "RestTimerTransition") { active ->
        if (active) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(color = NeonPurple.copy(alpha = flashAlpha.value), shape = MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onActiveClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = formatSecs(liveRemainingSeconds), color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = JetBrainsMono)
                // key resets animateFloatAsState on each timer activation so it starts at the
                // correct fraction (not 0), then smoothly interpolates 1s per tick backwards.
                key(liveTotalSeconds) {
                    val target = if (liveTotalSeconds > 0) liveRemainingSeconds.toFloat() / liveTotalSeconds.toFloat() else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = target,
                        animationSpec = tween(1000, easing = LinearEasing),
                        label = "restProgress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(2.dp),
                        color = NeonPurple,
                        trackColor = NeonPurple.copy(alpha = 0.15f)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onPassiveClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = TimerGreen.copy(alpha = 0.5f))
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp)) {
                    Text(text = formatSecs(restSeconds), fontSize = 11.sp, fontFamily = JetBrainsMono, color = TimerGreen.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 4.dp))
                }
                HorizontalDivider(modifier = Modifier.weight(1f), color = TimerGreen.copy(alpha = 0.5f))
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun WarmupPrescriptionCard(prescription: com.powerme.app.warmup.WarmupPrescription, onLogAsPerformed: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Warmup Prescription", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            prescription.exercises.forEach { step ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(step.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLogAsPerformed, modifier = Modifier.fillMaxWidth()) { Text("LOG AS PERFORMED") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagementHubSheet(onDismiss: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit, onSessionNote: () -> Unit, onStickyNote: () -> Unit, onRestTimer: () -> Unit, onSuperset: () -> Unit, isInSuperset: Boolean) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)) {
            val items = listOf(
                Triple("Session Note", Icons.Default.Notes, onSessionNote),
                Triple("Sticky Note", Icons.Default.PushPin, onStickyNote),
                Triple("Set Rest Timers", Icons.Default.Timer, onRestTimer),
                Triple("Replace Exercise", Icons.Default.Refresh, onReplace),
                Triple(if (isInSuperset) "Remove from Superset" else "Superset", Icons.Default.Sync, onSuperset),
                Triple("Remove Exercise", Icons.Default.Delete, onRemove)
            )
            items.forEach { (label, icon, action) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = { Icon(icon, contentDescription = null, tint = if (label.contains("Remove")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { action() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutSetRow(
    set: ActiveSet,
    onWeightChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onCompleteSet: () -> Unit,
    onSelectSetType: (SetType) -> Unit,
    onUpdateRpe: (Int?) -> Unit,
    onDeleteTimer: () -> Unit,
    weightFocusRequester: FocusRequester? = null,
    repsFocusRequester: FocusRequester? = null,
    nextWeightFocusRequester: FocusRequester? = null,
    onRepsDone: () -> Unit = {},
    isEditMode: Boolean = false
) {
    val (setLabel, setColor) = when (set.setType) {
        SetType.NORMAL -> "${set.setOrder}" to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to MaterialTheme.colorScheme.tertiary
        SetType.FAILURE -> "F" to MaterialTheme.colorScheme.error
        SetType.DROP -> "D" to MaterialTheme.colorScheme.secondary
    }
    var showSetTypeMenu by remember { mutableStateOf(false) }
    var showSetTypeInfo by remember { mutableStateOf<SetType?>(null) }
    var showRpePicker by remember { mutableStateOf(false) }
    val isTouched = (set.weight.isNotBlank() || set.reps.isNotBlank()) && !set.isCompleted
    val primaryColor = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isTouched) Modifier.drawBehind {
                    drawRect(
                        color = primaryColor.copy(alpha = 0.4f),
                        size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height)
                    )
                } else Modifier
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(SET_COL_WEIGHT).fillMaxHeight().minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxHeight().clickable { showSetTypeMenu = true }, contentAlignment = Alignment.Center) {
                    Text(text = setLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = setColor)
                }
                DropdownMenu(
                    expanded = showSetTypeMenu,
                    onDismissRequest = { showSetTypeMenu = false }
                ) {
                    listOf(
                        Triple(SetType.NORMAL, "${set.setOrder}", MaterialTheme.colorScheme.onSurface),
                        Triple(SetType.WARMUP, "W", MaterialTheme.colorScheme.tertiary),
                        Triple(SetType.FAILURE, "F", MaterialTheme.colorScheme.error),
                        Triple(SetType.DROP, "D", MaterialTheme.colorScheme.secondary)
                    ).forEach { (type, label, color) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(when (type) {
                                            SetType.NORMAL -> "Work Set"
                                            SetType.WARMUP -> "Warm Up"
                                            SetType.FAILURE -> "Failure"
                                            SetType.DROP -> "Drop Set"
                                        })
                                    }
                                    IconButton(
                                        onClick = { showSetTypeInfo = type; showSetTypeMenu = false },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            },
                            onClick = { onSelectSetType(type); showSetTypeMenu = false }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Timer", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDeleteTimer(); showSetTypeMenu = false }
                    )
                }
            }
            Box(modifier = Modifier.weight(PREV_COL_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = formatGhostLabel(set.ghostWeight, set.ghostReps, set.ghostRpe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            WorkoutInputField(
                value = set.weight,
                onValueChange = onWeightChanged,
                modifier = Modifier.weight(WEIGHT_COL_WEIGHT).padding(horizontal = 2.dp),
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                keyboardActions = KeyboardActions(onNext = { repsFocusRequester?.requestFocus() }),
                focusRequester = weightFocusRequester
            )
            WorkoutInputField(
                value = set.reps,
                onValueChange = onRepsChanged,
                modifier = Modifier.weight(REPS_COL_WEIGHT).padding(horizontal = 2.dp),
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    onRepsDone()
                    if (nextWeightFocusRequester != null) {
                        nextWeightFocusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                }),
                focusRequester = repsFocusRequester
            )
            if (!isEditMode) {
                Box(
                    modifier = Modifier.weight(RPE_COL_WEIGHT).fillMaxHeight().minimumInteractiveComponentSize().clickable { showRpePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    val rpeText = set.rpeValue?.let { v ->
                        val d = v / 10.0
                        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
                    } ?: "—"
                    Text(
                        text = rpeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (set.rpeValue != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                }
                IconButton(
                    onClick = onCompleteSet,
                    modifier = Modifier.weight(CHECK_COL_WEIGHT).fillMaxHeight().minimumInteractiveComponentSize().background(if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Complete", tint = if (set.isCompleted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                }
            }
        }
    }

    showSetTypeInfo?.let { infoType ->
        AlertDialog(
            onDismissRequest = { showSetTypeInfo = null },
            title = { Text(when (infoType) {
                SetType.NORMAL -> "Work Set"
                SetType.WARMUP -> "Warm Up Set"
                SetType.FAILURE -> "Failure Set"
                SetType.DROP -> "Drop Set"
            }) },
            text = { Text(when (infoType) {
                SetType.NORMAL -> "A standard working set. Counts toward volume and PRs."
                SetType.WARMUP -> "A warm-up set. Excluded from volume and PR calculations."
                SetType.FAILURE -> "A set taken to muscular failure. Flagged in analytics for fatigue tracking."
                SetType.DROP -> "A drop set performed at reduced weight after a working set. Counted separately in volume."
            }) },
            confirmButton = { TextButton(onClick = { showSetTypeInfo = null }) { Text("Got it") } }
        )
    }

    if (showRpePicker) {
        RpePickerSheet(
            currentRpe = set.rpeValue,
            onUpdateRpe = { onUpdateRpe(it); showRpePicker = false },
            onDismiss = { showRpePicker = false }
        )
    }
}

@Composable
private fun CardioHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Text("DIST(KM)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
        Text("TIME(S)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
        Text("PACE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
        Text("RPE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Spacer(modifier = Modifier.weight(0.10f))
    }
}

@Composable
private fun TimedHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Text("WEIGHT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
        Text("TIME(S)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.35f))
        Text("RPE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
        Spacer(modifier = Modifier.weight(0.10f))
    }
}

@Composable
fun CardioSetRow(
    set: ActiveSet,
    onUpdateSet: (String, String, String, Boolean) -> Unit,
    onCompleteSet: () -> Unit
) {
    val dist = set.distance
    val time = set.timeSeconds
    val rpe = set.rpe

    val pace = remember(dist, time) {
        val d = dist.toDoubleOrNull() ?: 0.0
        val t = time.toDoubleOrNull() ?: 0.0
        if (d > 0 && t > 0) {
            val paceMinPerKm = (t / 60.0) / d
            var min = paceMinPerKm.toInt()
            var sec = ((paceMinPerKm - min) * 60 + 0.5).toInt()
            if (sec >= 60) {
                min += 1
                sec -= 60
            }
            "%d:%02d".format(min, sec)
        } else "—"
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
            Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        WorkoutInputField(
            value = dist,
            onValueChange = { onUpdateSet(it, time, rpe, set.isCompleted) },
            modifier = Modifier.weight(0.25f).padding(horizontal = 2.dp),
            placeholder = "0"
        )
        WorkoutInputField(
            value = time,
            onValueChange = { onUpdateSet(dist, it, rpe, set.isCompleted) },
            modifier = Modifier.weight(0.25f).padding(horizontal = 2.dp),
            placeholder = "0"
        )
        Box(modifier = Modifier.weight(0.20f), contentAlignment = Alignment.Center) {
            Text(text = pace, style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMono)
        }
        WorkoutInputField(
            value = rpe,
            onValueChange = { onUpdateSet(dist, time, it, set.isCompleted) },
            modifier = Modifier.weight(0.10f).padding(horizontal = 2.dp),
            placeholder = "—"
        )
        IconButton(
            onClick = onCompleteSet,
            modifier = Modifier.weight(0.10f).fillMaxHeight().background(
                if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.extraSmall
            )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Complete",
                tint = if (set.isCompleted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun TimedSetRow(
    set: ActiveSet,
    onWeightChanged: (String) -> Unit,
    onUpdateSet: (String, String, Boolean) -> Unit,
    onCompleteSet: () -> Unit
) {
    val weight = set.weight
    val time = set.timeSeconds
    val rpe = set.rpe

    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
            Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        WorkoutInputField(
            value = weight,
            onValueChange = onWeightChanged,
            modifier = Modifier.weight(0.25f).padding(horizontal = 2.dp),
            placeholder = "0"
        )
        WorkoutInputField(
            value = time,
            onValueChange = { onUpdateSet(it, rpe, set.isCompleted) },
            modifier = Modifier.weight(0.35f).padding(horizontal = 2.dp),
            placeholder = "0"
        )
        WorkoutInputField(
            value = rpe,
            onValueChange = { onUpdateSet(time, it, set.isCompleted) },
            modifier = Modifier.weight(0.20f).padding(horizontal = 2.dp),
            placeholder = "—"
        )
        IconButton(
            onClick = onCompleteSet,
            modifier = Modifier.weight(0.10f).fillMaxHeight().background(
                if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.extraSmall
            )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Complete",
                tint = if (set.isCompleted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RpePickerSheet(
    currentRpe: Int?,
    onUpdateRpe: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val rpeValues = (60..100 step 5).toList()
    val anchorLabels = mapOf(
        60 to "Very Light",
        80 to "Hard — 2 reps in reserve",
        100 to "Maximum Effort"
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Rate of Perceived Exertion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rpeValues.forEach { v ->
                    val label = "%.1f".format(v / 10.0)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilterChip(
                            selected = currentRpe == v,
                            onClick = { onUpdateRpe(v) },
                            label = { Text(label, fontFamily = JetBrainsMono) }
                        )
                        anchorLabels[v]?.let { anchor ->
                            Text(
                                anchor,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { onUpdateRpe(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostWorkoutSummarySheet(
    summary: WorkoutSummary,
    onSaveAsRoutine: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSyncValues: () -> Unit = {},
    onConfirmSyncStructure: () -> Unit = {},
    onConfirmSyncBoth: () -> Unit = {},
    onDismissSync: () -> Unit = {}
) {
    var showSaveAsRoutineDialog by remember { mutableStateOf(false) }
    var routineNameInput by remember { mutableStateOf(summary.workoutName) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.95f) // Force to near-full screen to show buttons at bottom immediately
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Scrollable section: workout title, stats, exercise list
            // weight(1f) ensures this takes up all available space, pushing buttons to the bottom
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text(
                    text = summary.workoutName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "%d:%02d".format(summary.durationSeconds / 60, summary.durationSeconds % 60),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "%.1f kg total".format(summary.totalVolume),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${summary.setCount} sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                summary.exerciseNames.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Fixed button section — Always at the bottom of the screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // Sync CTAs
                when (summary.pendingRoutineSync) {
                    RoutineSyncType.VALUES -> {
                        Button(
                            onClick = { onConfirmSyncValues() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Update values", fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDismissSync() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Keep original routine") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    RoutineSyncType.STRUCTURE -> {
                        Button(
                            onClick = { onConfirmSyncStructure() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Update routine", fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDismissSync() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Keep original routine") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    RoutineSyncType.BOTH -> {
                        Button(
                            onClick = { onConfirmSyncValues() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) { Text("Update values only", fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onConfirmSyncBoth() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Update values and routine", fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDismissSync() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Keep original routine") }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    null -> Unit
                }
                TextButton(
                    onClick = { showSaveAsRoutineDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save as Routine")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TimerGreen.copy(alpha = 0.15f), contentColor = TimerGreen)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
                // Extra safety buffer for gesture handle
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSaveAsRoutineDialog) {
        AlertDialog(
            onDismissRequest = { showSaveAsRoutineDialog = false },
            title = { Text("Save as Routine") },
            text = {
                OutlinedTextField(
                    value = routineNameInput,
                    onValueChange = { routineNameInput = it },
                    label = { Text("Routine Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (routineNameInput.isNotBlank()) {
                        onSaveAsRoutine(routineNameInput)
                        showSaveAsRoutineDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }

}

@Composable
fun RestTimePickerDialog(currentSeconds: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val minutesTfv = remember { mutableStateOf(TextFieldValue((currentSeconds / 60).toString())) }
    val secondsTfv = remember { mutableStateOf(TextFieldValue((currentSeconds % 60).toString())) }
    val secondsFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun confirm() {
        val m = minutesTfv.value.text.toIntOrNull() ?: 0
        val s = secondsTfv.value.text.toIntOrNull() ?: 0
        val total = m * 60 + s
        if (total in 0..599) onConfirm(total)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Rest Time") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = minutesTfv.value,
                    onValueChange = { minutesTfv.value = it },
                    label = { Text("Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { secondsFocusRequester.requestFocus() }),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(100)
                                    val t = minutesTfv.value.text
                                    minutesTfv.value = minutesTfv.value.copy(selection = TextRange(0, t.length))
                                }
                            }
                        }
                )
                Text(":", style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = secondsTfv.value,
                    onValueChange = { secondsTfv.value = it },
                    label = { Text("Sec") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(secondsFocusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(100)
                                    val t = secondsTfv.value.text
                                    secondsTfv.value = secondsTfv.value.copy(selection = TextRange(0, t.length))
                                }
                            }
                        }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerControlsSheet(remainingSeconds: Int, isPaused: Boolean, onDismiss: () -> Unit, onAdjustTime: (Int) -> Unit, onPauseResume: () -> Unit, onSkip: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                fontFamily = JetBrainsMono,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onAdjustTime(-10) }) { Text("-10s") }
                IconButton(onClick = onPauseResume, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = { onAdjustTime(10) }) { Text("+10s") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text("Skip", color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
