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
import androidx.compose.ui.graphics.compositeOver
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
import com.powerme.app.data.KeepScreenOnMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.WorkoutBlock
import com.powerme.app.ui.components.KeyboardAccessoryBar
import com.powerme.app.ui.components.KeyboardAccessoryRegistrar
import com.powerme.app.ui.components.LocalKeyboardAccessoryRegistrar
import com.powerme.app.ui.components.WorkoutInputField
import com.powerme.app.ui.exercises.ExercisesScreen
import com.powerme.app.util.UnitConverter
import com.powerme.app.util.WarmupCalculator
import com.powerme.app.util.RpeCategory
import com.powerme.app.util.RpeInfo
import com.powerme.app.util.RPE_SCALE
import com.powerme.app.util.displayLabel
import com.powerme.app.util.rpeCategory
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import java.util.ArrayList
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.ui.workouts.AddBlockOrExerciseSheet
import com.powerme.app.ui.workouts.FunctionalBlockWizard

private enum class TimedSetState { IDLE, SETUP, RUNNING, PAUSED, COMPLETED }

// Shared column weight distribution — applied identically to header row and WorkoutSetRow
private const val SET_COL_WEIGHT    = 0.08f
private const val PREV_COL_WEIGHT   = 0.22f
private const val WEIGHT_COL_WEIGHT = 0.25f
private const val REPS_COL_WEIGHT   = 0.22f
private const val RPE_COL_WEIGHT    = 0.13f
private const val CHECK_COL_WEIGHT  = 0.10f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActiveWorkoutScreen(
    onWorkoutFinished: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onNavigateToTimer: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val clocksTimerState by viewModel.clocksTimerState.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val rpeAutoPopTarget by viewModel.rpeAutoPopTarget.collectAsState()
    val setupSeconds by viewModel.timedSetSetupSeconds.collectAsState()
    val keepScreenOnMode by viewModel.keepScreenOnMode.collectAsState()
    val supersetColorMap = remember(workoutState.exercises) {
        buildSupersetColorMap(workoutState.exercises.map { it.supersetGroupId })
    }
    val workoutStyle by viewModel.workoutStyle.collectAsState()
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showBlockWizard by remember { mutableStateOf(false) }
    var showHybridSheet by remember { mutableStateOf(false) }
    var pendingDraftBlock by remember { mutableStateOf<com.powerme.app.ui.workouts.DraftBlock?>(null) }
    var pendingBlockId by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }
    var showTimerControls by remember { mutableStateOf(false) }
    var showStandaloneTimerConfig by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Keep screen on based on the user's mode setting.
    // ALWAYS  → screen always on (view-level; MainActivity window flag is belt-and-suspenders).
    // DURING_WORKOUT → on only while a workout is active.
    // OFF / other → normal system behaviour.
    // onDispose skips the clear for ALWAYS so the window flag in MainActivity is not clobbered.
    DisposableEffect(view, keepScreenOnMode, workoutState.isActive) {
        val shouldKeep = when (keepScreenOnMode) {
            KeepScreenOnMode.ALWAYS -> true
            KeepScreenOnMode.DURING_WORKOUT -> workoutState.isActive
            else -> false
        }
        view.keepScreenOn = shouldKeep
        onDispose {
            if (keepScreenOnMode != KeepScreenOnMode.ALWAYS) {
                view.keepScreenOn = false
            }
        }
    }

    var isReorderMode by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Keyboard accessory bar (±1 buttons pinned above the IME).
    var accessoryCallbacks by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val keyboardAccessoryRegistrar = remember {
        KeyboardAccessoryRegistrar(
            register = { dec, inc -> accessoryCallbacks = dec to inc },
            unregister = { accessoryCallbacks = null }
        )
    }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) accessoryCallbacks = null
    }

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

    val handleEditClose = {
        if (viewModel.editModeHasChanges()) showDiscardEditDialog = true
        else {
            val wasLiveEdit = workoutState.workoutId != null
            viewModel.cancelEditMode()
            if (!wasLiveEdit) onWorkoutFinished()
        }
    }

    BackHandler(enabled = workoutState.isEditMode) { handleEditClose() }

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

    // Functional Block Overlay state — declared here so it's accessible inside the Box below
    val fb = workoutState.functionalBlockState
    var showBlockFinishSheet by remember { mutableStateOf(false) }

    // Auto-navigate to WorkoutSummaryScreen when finishWorkout() completes
    LaunchedEffect(workoutState.pendingWorkoutSummary) {
        if (workoutState.pendingWorkoutSummary != null) {
            onWorkoutFinished()
        }
    }

    // Auto-present BlockFinishSheet when the functional block timer expires
    LaunchedEffect(workoutState.blockAutoFinished) {
        if (workoutState.blockAutoFinished) {
            showBlockFinishSheet = true
            viewModel.consumeBlockAutoFinished()
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

    // Discard edit changes confirmation dialog
    if (showDiscardEditDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardEditDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("Are you sure you want to discard the changes you've made in the routine?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardEditDialog = false
                    val wasLiveEdit = workoutState.workoutId != null
                    viewModel.cancelEditMode()
                    if (!wasLiveEdit) onWorkoutFinished()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardEditDialog = false }) { Text("Keep Editing") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalKeyboardAccessoryRegistrar provides keyboardAccessoryRegistrar) {
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
                            if (!workoutState.isEditMode || workoutState.workoutId != null) {
                                Text(
                                    text = formatElapsed(workoutState.elapsedSeconds),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontFamily = JetBrainsMono
                                )
                            }
                        }

                        if (workoutState.isEditMode) {
                            IconButton(onClick = handleEditClose) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            if (workoutState.isActive && workoutState.workoutId != null) {
                                IconButton(onClick = { viewModel.enterLiveWorkoutEditMode() }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit routine", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
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
                    viewModel.reorderOrganizeItem(from.key, to.key)
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
                        onShowExerciseDialog = {
                            when (workoutStyle) {
                                WorkoutStyle.PURE_FUNCTIONAL -> showBlockWizard = true
                                WorkoutStyle.HYBRID -> showHybridSheet = true
                                else -> showExerciseDialog = true
                            }
                        },
                        onTimerActiveClick = { showTimerControls = true },
                        onCancelWorkout = { showCancelDialog = true },
                        isEditMode = workoutState.isEditMode,
                        reorderableLazyListState = reorderableLazyListState,
                        unitSystem = unitSystem,
                        supersetColorMap = supersetColorMap,
                        rpeAutoPopTarget = rpeAutoPopTarget,
                        onConsumeRpeAutoPop = { viewModel.consumeRpeAutoPop() },
                        setupSeconds = setupSeconds,
                        onSetupCountdownTick = { viewModel.setupCountdownTickFeedback() },
                        workoutStyle = workoutStyle,
                        onAddExerciseToBlock = { blockId ->
                            pendingBlockId = blockId
                            showExerciseDialog = true
                        },
                    )
                }
            }
        }
    }
    } // end CompositionLocalProvider
    if (imeVisible && accessoryCallbacks != null) {
        val (dec, inc) = accessoryCallbacks!!
        KeyboardAccessoryBar(
            onDecrement = dec,
            onIncrement = inc,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .zIndex(10f)
        )
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    )
    // Functional Block Overlay (AMRAP / RFT / EMOM / TABATA — full-screen when active).
    // Must be the last child in this Box so it draws on top of all workout content.
    if (fb != null) {
        when (fb.blockType) {
            "AMRAP" -> com.powerme.app.ui.workout.runner.AmrapOverlay(
                state = fb,
                onTap = {
                    viewModel.appendBlockRoundTap(
                        round = fb.roundTapCount + 1,
                        elapsedMs = fb.elapsedSeconds * 1000L,
                        completed = true,
                    )
                },
                onFinishClick = { showBlockFinishSheet = true },
                onAbandonClick = { viewModel.abandonFunctionalBlock() },
            )
            "RFT" -> com.powerme.app.ui.workout.runner.RftOverlay(
                state = fb,
                onRoundTap = {
                    viewModel.appendBlockRoundTap(
                        round = fb.roundTapCount + 1,
                        elapsedMs = fb.elapsedSeconds * 1000L,
                        completed = true,
                    )
                },
                onFinishClick = { showBlockFinishSheet = true },
                onAbandonClick = { viewModel.abandonFunctionalBlock() },
            )
            "EMOM" -> com.powerme.app.ui.workout.runner.EmomOverlay(
                state = fb,
                onRoundSkipped = { viewModel.skipEmomRound() },
                onFinishClick = { showBlockFinishSheet = true },
                onAbandonClick = { viewModel.abandonFunctionalBlock() },
            )
            "TABATA" -> com.powerme.app.ui.workout.runner.TabataOverlay(
                state = fb,
                onFinishClick = { showBlockFinishSheet = true },
                onAbandonClick = { viewModel.abandonFunctionalBlock() },
            )
        }
        if (showBlockFinishSheet) {
            com.powerme.app.ui.workout.runner.BlockFinishSheet(
                blockType = fb.blockType,
                state = fb,
                onDismiss = { showBlockFinishSheet = false },
                onSave = { result ->
                    viewModel.finishFunctionalBlock(
                        rounds = result.rounds,
                        extraReps = result.extraReps,
                        finishSeconds = result.finishSeconds,
                        rpe = result.rpe,
                        perExerciseRpeJson = result.perExerciseRpeJson,
                        notes = result.notes,
                    )
                    showBlockFinishSheet = false
                },
            )
        }
    }
    } // end Box

    if (showExerciseDialog) {
        val draft = pendingDraftBlock
        val targetBlockId = pendingBlockId
        ModalBottomSheet(
            onDismissRequest = {
                showExerciseDialog = false
                pendingDraftBlock = null
                pendingBlockId = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ExercisesScreen(
                pickerMode = true,
                isModal = true,
                initialFunctionalFilter = draft != null,
                initialSelectedIds = if (targetBlockId != null) {
                    workoutState.exercises
                        .filter { it.blockId == targetBlockId }
                        .map { it.exercise.id }
                        .toSet()
                } else emptySet(),
                onExercisesSelected = { ids ->
                    val selectedExercises = ids.mapNotNull { id -> workoutState.availableExercises.find { it.id == id } }
                    when {
                        draft != null -> {
                            viewModel.addFunctionalBlock(draft, selectedExercises)
                            pendingDraftBlock = null
                        }
                        targetBlockId != null -> {
                            selectedExercises.forEach { viewModel.addExercise(it, blockId = targetBlockId) }
                            pendingBlockId = null
                        }
                        else -> selectedExercises.forEach { viewModel.addExercise(it) }
                    }
                    showExerciseDialog = false
                }
            )
        }
    }

    if (showBlockWizard) {
        FunctionalBlockWizard(
            onDismiss = { showBlockWizard = false },
            onBlockCreated = { draft ->
                pendingDraftBlock = draft
                showBlockWizard = false
                showExerciseDialog = true  // step 2: pick exercises for this block
            }
        )
    }

    if (showHybridSheet) {
        AddBlockOrExerciseSheet(
            onDismiss = { showHybridSheet = false },
            onAddStrengthExercise = {
                showHybridSheet = false
                showExerciseDialog = true
            },
            onAddFunctionalBlock = {
                showHybridSheet = false
                showBlockWizard = true
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

/**
 * Single grouped card for a functional block (AMRAP / RFT / EMOM / TABATA).
 * Shows block type badge + params at the top, then one plain row per exercise.
 * No sets column, no PRE, no RPE, no checkmark — those concepts don't apply here.
 */
@Composable
private fun FunctionalBlockActiveCard(
    block: WorkoutBlock,
    exercises: List<ExerciseWithSets>,
    unitSystem: UnitSystem,
    isEditMode: Boolean,
    onStartBlock: ((String) -> Unit)? = null,
    onWeightChanged: (exId: Long, setOrder: Int, value: String) -> Unit = { _, _, _ -> },
    onRepsChanged: (exId: Long, setOrder: Int, value: String) -> Unit = { _, _, _ -> },
    onTimeChanged: (exId: Long, setOrder: Int, value: String) -> Unit = { _, _, _ -> },
    onRemoveExercise: (exId: Long) -> Unit = {},
    onAddExercise: (() -> Unit)? = null,
    onDeleteBlock: (() -> Unit)? = null,
    onEditBlock: ((durationSeconds: Int?, targetRounds: Int?, emomRoundSeconds: Int?, tabataWorkSeconds: Int?, tabataRestSeconds: Int?) -> Unit)? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    val blockType = runCatching { com.powerme.app.data.BlockType.valueOf(block.type) }
        .getOrDefault(com.powerme.app.data.BlockType.STRENGTH)
    val badgeColor = when (blockType) {
        com.powerme.app.data.BlockType.AMRAP   -> TimerGreen
        com.powerme.app.data.BlockType.RFT     -> NeonPurple
        com.powerme.app.data.BlockType.EMOM    -> ReadinessAmber
        com.powerme.app.data.BlockType.TABATA  -> TimerRed
        else                                   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val paramsSummary = when (blockType) {
        com.powerme.app.data.BlockType.AMRAP   -> block.durationSeconds?.let { "${it / 60} min cap" }
        com.powerme.app.data.BlockType.RFT     -> block.targetRounds?.let { "$it rounds" }
        com.powerme.app.data.BlockType.EMOM    -> block.emomRoundSeconds?.let { interval ->
            block.durationSeconds?.let { dur ->
                val prefix = when {
                    interval <= 60 -> "EMOM"
                    interval % 60 == 0 -> "E${interval / 60}MOM"
                    else -> "E${interval}sMOM"
                }
                "$prefix ${dur / 60} min"
            }
        } ?: block.durationSeconds?.let { "${it / 60} min" }
        com.powerme.app.data.BlockType.TABATA  -> block.tabataWorkSeconds?.let { ws ->
            block.tabataRestSeconds?.let { rs -> "${ws}s / ${rs}s" }
        }
        else -> null
    }
    val alreadyRun = block.runStartMs != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Block header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = blockType.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (block.name != null) {
                    Text(
                        text = block.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (paramsSummary != null) {
                    Text(
                        text = paramsSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isEditMode) {
                    OutlinedButton(
                        onClick = { onStartBlock?.invoke(block.id) },
                        enabled = onStartBlock != null && !alreadyRun,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (alreadyRun) "BLOCK DONE" else "START BLOCK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!alreadyRun && onEditBlock != null) {
                    IconButton(
                        onClick = { showEditSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit block parameters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDeleteBlock != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove block",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val allTimed = exercises.all { it.exercise.exerciseType == ExerciseType.TIMED }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allTimed) "TIME(S)" else "REPS",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            // Exercise rows — editable prescription
            exercises.forEachIndexed { index, exWithSets ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
                FunctionalExerciseRow(
                    exerciseWithSets = exWithSets,
                    onRepsChanged = onRepsChanged,
                    onTimeChanged = onTimeChanged,
                    onRemove = if (!alreadyRun) ({ onRemoveExercise(exWithSets.exercise.id) }) else null,
                )
            }

            // Add exercise button — only show before block has started
            if (!alreadyRun && onAddExercise != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                TextButton(
                    onClick = onAddExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add exercise", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove block?") },
            text = { Text("This will remove the ${blockType.displayName} block and all its exercises from the workout.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteBlock?.invoke() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditSheet && onEditBlock != null) {
        EditBlockParamsSheet(
            block = block,
            onDismiss = { showEditSheet = false },
            onConfirm = { dur, rounds, emomInterval, workSecs, restSecs ->
                showEditSheet = false
                onEditBlock(dur, rounds, emomInterval, workSecs, restSecs)
            }
        )
    }
}

/**
 * Single exercise row inside a [FunctionalBlockActiveCard].
 * Shows an inline editable prescription:
 *  - Reps exercise: [10] Air Squat  reps
 *  - Timed exercise: [30] Plank  sec
 */
@Composable
private fun FunctionalExerciseRow(
    exerciseWithSets: ExerciseWithSets,
    onRepsChanged: (exId: Long, setOrder: Int, value: String) -> Unit = { _, _, _ -> },
    onTimeChanged: (exId: Long, setOrder: Int, value: String) -> Unit = { _, _, _ -> },
    onRemove: (() -> Unit)? = null,
) {
    val exercise = exerciseWithSets.exercise
    val set = exerciseWithSets.sets.firstOrNull()
    val isTimed = exercise.exerciseType == ExerciseType.TIMED
    val setOrder = set?.setOrder ?: 1
    val value = if (isTimed) {
        set?.timeSeconds?.takeIf { it.isNotBlank() } ?: "30"
    } else {
        set?.reps?.takeIf { it.isNotBlank() && it != "0" } ?: "10"
    }

    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (tfv.text != value) tfv = TextFieldValue(value)
    }
    var selectAllTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectAllTrigger) {
        if (selectAllTrigger > 0) {
            delay(50)
            tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(52.dp)
        ) {
            BasicTextField(
                value = tfv,
                onValueChange = { new ->
                    val filteredText = new.text.filter { it.isDigit() }
                    tfv = new.copy(text = filteredText)
                    if (isTimed) onTimeChanged(exercise.id, setOrder, filteredText)
                    else onRepsChanged(exercise.id, setOrder, filteredText)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .onFocusChanged { if (it.isFocused) selectAllTrigger++ }
            )
        }
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove exercise",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockHeader(
    block: WorkoutBlock,
    onStartBlock: ((String) -> Unit)? = null,
) {
    val blockType = runCatching { com.powerme.app.data.BlockType.valueOf(block.type) }
        .getOrDefault(com.powerme.app.data.BlockType.STRENGTH)
    val isFunctional = blockType != com.powerme.app.data.BlockType.STRENGTH
    val badgeColor = when (blockType) {
        com.powerme.app.data.BlockType.AMRAP   -> TimerGreen
        com.powerme.app.data.BlockType.RFT     -> NeonPurple
        com.powerme.app.data.BlockType.EMOM    -> ReadinessAmber
        com.powerme.app.data.BlockType.TABATA  -> TimerRed
        else                                   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val paramsSummary = when (blockType) {
        com.powerme.app.data.BlockType.AMRAP   -> block.durationSeconds?.let { "${it / 60} min cap" }
        com.powerme.app.data.BlockType.RFT     -> block.targetRounds?.let { "$it rounds" }
        com.powerme.app.data.BlockType.EMOM    -> block.durationSeconds?.let { "${it / 60} min" }
        com.powerme.app.data.BlockType.TABATA  -> block.tabataWorkSeconds?.let { ws ->
            block.tabataRestSeconds?.let { rs -> "${ws}s work / ${rs}s rest" }
        }
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = blockType.displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontWeight = FontWeight.Bold
            )
        }
        if (block.name != null) {
            Text(
                text = block.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (paramsSummary != null) {
            Text(
                text = paramsSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isFunctional) {
            val alreadyRun = block.runStartMs != null
            OutlinedButton(
                onClick = { onStartBlock?.invoke(block.id) },
                enabled = onStartBlock != null && !alreadyRun,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (alreadyRun) "BLOCK DONE" else "START BLOCK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
    reorderableLazyListState: sh.calvin.reorderable.ReorderableLazyListState? = null,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    supersetColorMap: Map<String, Color> = emptyMap(),
    rpeAutoPopTarget: String? = null,
    onConsumeRpeAutoPop: () -> Unit = {},
    setupSeconds: Int = 0,
    onSetupCountdownTick: () -> Unit = {},
    workoutStyle: WorkoutStyle = WorkoutStyle.PURE_GYM,
    onAddExerciseToBlock: (blockId: String) -> Unit = {},
) {
    // Organize Mode CAB — persistent; user exits only via Done
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
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                    val candidateCount = workoutState.supersetCandidateIds.size
                    Text(
                        text = if (candidateCount > 0) "Organize • $candidateCount selected" else "Organize exercises",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    val candidates = workoutState.supersetCandidateIds
                    val selectedExs = workoutState.exercises.filter { it.exercise.id in candidates }
                    val allInSameSuperset = candidates.isNotEmpty() &&
                        selectedExs.all { it.supersetGroupId != null } &&
                        selectedExs.map { it.supersetGroupId }.toSet().size == 1
                    if (allInSameSuperset) {
                        IconButton(onClick = { viewModel.ungroupSelectedExercises() }) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Ungroup selected exercises", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.commitSupersetSelection() },
                            enabled = candidates.size >= 2
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Group selected exercises", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Exercises — multi-block or any functional block uses grouped rendering; single STRENGTH block is flat.
    val showBlockHeaders = !workoutState.isSupersetSelectMode &&
        (workoutState.blocks.size > 1 || workoutState.blocks.any { it.type != "STRENGTH" })
    if (showBlockHeaders) {
        workoutState.blocks.forEach { block ->
            val blockType = runCatching { com.powerme.app.data.BlockType.valueOf(block.type) }
                .getOrDefault(com.powerme.app.data.BlockType.STRENGTH)
            if (blockType == com.powerme.app.data.BlockType.STRENGTH) {
                item(key = "block_header_${block.id}") {
                    BlockHeader(
                        block = block,
                        onStartBlock = { blockId -> viewModel.startFunctionalBlock(blockId) },
                    )
                }
                items(items = workoutState.exercisesByBlockId[block.id] ?: emptyList(), key = { it.exercise.id }) { exerciseWithSets ->
                    val isCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedExerciseIds
                    val warmupsCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedWarmupExerciseIds
                    ExerciseCard(
                        exerciseWithSets = exerciseWithSets,
                        supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent,
                        isSelectMode = false,
                        isSelected = false,
                        activeTimerExerciseId = activeTimerExerciseId,
                        activeTimerSetOrder = activeTimerSetOrder,
                        activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                        activeTimerTotalSeconds = activeTimerTotalSeconds,
                        unitSystem = unitSystem,
                        availableExercises = workoutState.availableExercises,
                        restTimeOverrides = workoutState.restTimeOverrides,
                        hiddenRestSeparators = workoutState.hiddenRestSeparators + workoutState.finishedRestSeparators,
                        isEditMode = isEditMode,
                        isCollapsed = isCollapsed,
                        warmupsCollapsed = warmupsCollapsed,
                        onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                        onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                        onToggleWarmupsCollapsed = { viewModel.toggleWarmupsCollapsed(exerciseWithSets.exercise.id) },
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
                        onTimeChanged = { setOrder, time -> viewModel.onTimeChanged(exerciseWithSets.exercise.id, setOrder, time) },
                        onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                        onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                        onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                        onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                        onUpdateExerciseRestTimers = { work, warmup, drop, restAfterLast -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop, restAfterLast) },
                        onAddWarmupSets = { w, r, fill -> viewModel.addSmartWarmups(exerciseWithSets.exercise.id, w, r, fill) },
                        onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                        onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                        onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                        onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                        onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                        onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                        onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                        onTimerActiveClick = onTimerActiveClick,
                        onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) },
                        onTimerFinished = { viewModel.timerFinishedFeedback() },
                        onTimerWarningTick = { viewModel.timerWarningTickFeedback() },
                        rpeAutoPopTarget = rpeAutoPopTarget,
                        onConsumeRpeAutoPop = onConsumeRpeAutoPop,
                        setupSeconds = setupSeconds,
                        onSetupCountdownTick = onSetupCountdownTick,
                        onTimerHalftimeTick = { viewModel.timerHalftimeTickFeedback() },
                        onStopRestTimer = { viewModel.stopRestTimer() }
                    )
                }
            } else {
                item(key = "func_block_card_${block.id}") {
                    FunctionalBlockActiveCard(
                        block = block,
                        exercises = workoutState.exercisesByBlockId[block.id] ?: emptyList(),
                        unitSystem = unitSystem,
                        isEditMode = isEditMode,
                        onStartBlock = { blockId -> viewModel.startFunctionalBlock(blockId) },
                        onWeightChanged = { exId, setOrder, v -> viewModel.onWeightChanged(exId, setOrder, v) },
                        onRepsChanged = { exId, setOrder, v -> viewModel.onRepsChanged(exId, setOrder, v) },
                        onTimeChanged = { exId, setOrder, v -> viewModel.onTimeChanged(exId, setOrder, v) },
                        onRemoveExercise = { exId -> viewModel.removeExercise(exId) },
                        onAddExercise = { onAddExerciseToBlock(block.id) },
                        onDeleteBlock = { viewModel.removeBlock(block.id) },
                        onEditBlock = { dur, rounds, emomInt, workSecs, restSecs ->
                            viewModel.updateBlock(block.id, dur, rounds, emomInt, workSecs, restSecs)
                        },
                    )
                }
            }
        }
        // Render unblocked exercises (null blockId) as regular strength-style cards.
        // These exist when strength exercises were added to a HYBRID routine without an
        // explicit STRENGTH block assignment (AddBlockOrExerciseSheet → exercise picker path).
        val unblockedExercises = workoutState.exercisesByBlockId[null] ?: emptyList()
        items(items = unblockedExercises, key = { it.exercise.id }) { exerciseWithSets ->
            val isCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedExerciseIds
            val warmupsCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedWarmupExerciseIds
            ExerciseCard(
                exerciseWithSets = exerciseWithSets,
                supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent,
                isSelectMode = false,
                isSelected = false,
                activeTimerExerciseId = activeTimerExerciseId,
                activeTimerSetOrder = activeTimerSetOrder,
                activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                activeTimerTotalSeconds = activeTimerTotalSeconds,
                unitSystem = unitSystem,
                availableExercises = workoutState.availableExercises,
                restTimeOverrides = workoutState.restTimeOverrides,
                hiddenRestSeparators = workoutState.hiddenRestSeparators + workoutState.finishedRestSeparators,
                isEditMode = isEditMode,
                isCollapsed = isCollapsed,
                warmupsCollapsed = warmupsCollapsed,
                onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                onToggleWarmupsCollapsed = { viewModel.toggleWarmupsCollapsed(exerciseWithSets.exercise.id) },
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
                onTimeChanged = { setOrder, time -> viewModel.onTimeChanged(exerciseWithSets.exercise.id, setOrder, time) },
                onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                onUpdateExerciseRestTimers = { work, warmup, drop, restAfterLast -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop, restAfterLast) },
                onAddWarmupSets = { w, r, fill -> viewModel.addSmartWarmups(exerciseWithSets.exercise.id, w, r, fill) },
                onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                onTimerActiveClick = onTimerActiveClick,
                onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) },
                onTimerFinished = { viewModel.timerFinishedFeedback() },
                onTimerWarningTick = { viewModel.timerWarningTickFeedback() },
                rpeAutoPopTarget = rpeAutoPopTarget,
                onConsumeRpeAutoPop = onConsumeRpeAutoPop,
                setupSeconds = setupSeconds,
                onSetupCountdownTick = onSetupCountdownTick,
                onTimerHalftimeTick = { viewModel.timerHalftimeTickFeedback() },
                onStopRestTimer = { viewModel.stopRestTimer() }
            )
        }
    } else {
        val hasFunctionalBlocks = workoutState.blocks.any { it.type != "STRENGTH" }

        // Block-aware organize mode: functional blocks drag as atomic units; strength exercises drag individually
        if (workoutState.isSupersetSelectMode && hasFunctionalBlocks) {
            val seenBlockIds = mutableSetOf<String>()
            workoutState.exercises.forEach { ex ->
                val blockId = ex.blockId
                val block = workoutState.blocks.find { it.id == blockId }
                val isFunctionalBlock = block != null && block.type != "STRENGTH"
                if (isFunctionalBlock && blockId != null) {
                    if (blockId !in seenBlockIds) {
                        seenBlockIds.add(blockId)
                        val orgKey = "org_block_$blockId"
                        val blockExerciseCount = workoutState.exercisesByBlockId[blockId]?.size ?: 0
                        item(key = orgKey) {
                            if (reorderableLazyListState != null) {
                                ReorderableItem(reorderableLazyListState, key = orgKey) { _ ->
                                    FunctionalBlockOrganizeRow(
                                        block = block,
                                        exerciseCount = blockExerciseCount,
                                        dragHandleModifier = Modifier.draggableHandle()
                                    )
                                }
                            } else {
                                FunctionalBlockOrganizeRow(block = block, exerciseCount = blockExerciseCount)
                            }
                        }
                    }
                    // Skip non-first exercises of functional blocks — represented by block row
                } else {
                    item(key = ex.exercise.id) {
                        val isSelected = ex.exercise.id in workoutState.supersetCandidateIds
                        if (reorderableLazyListState != null) {
                            ReorderableItem(reorderableLazyListState, key = ex.exercise.id) { _ ->
                                SupersetSelectRow(
                                    exerciseWithSets = ex,
                                    isSelected = isSelected,
                                    onToggle = { viewModel.toggleSupersetCandidate(ex.exercise.id) },
                                    dragHandleModifier = Modifier.draggableHandle(),
                                    supersetColor = supersetColorMap[ex.supersetGroupId] ?: Color.Transparent
                                )
                            }
                        } else {
                            SupersetSelectRow(
                                exerciseWithSets = ex,
                                isSelected = isSelected,
                                onToggle = { viewModel.toggleSupersetCandidate(ex.exercise.id) },
                                supersetColor = supersetColorMap[ex.supersetGroupId] ?: Color.Transparent
                            )
                        }
                    }
                }
            }
        } else {
        // Single-block or organize mode (no functional blocks): flat list with full reorder / superset support
        items(
            items = workoutState.exercises,
            key = { it.exercise.id }
        ) { exerciseWithSets ->
            val isSelected = workoutState.supersetCandidateIds.contains(exerciseWithSets.exercise.id)
            val isCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedExerciseIds
            val warmupsCollapsed = exerciseWithSets.exercise.id in workoutState.collapsedWarmupExerciseIds

            // Superset selection mode: collapsed selectable rows (with drag-to-reorder)
            if (workoutState.isSupersetSelectMode) {
                if (reorderableLazyListState != null) {
                    ReorderableItem(reorderableLazyListState, key = exerciseWithSets.exercise.id) { _ ->
                        SupersetSelectRow(
                            exerciseWithSets = exerciseWithSets,
                            isSelected = isSelected,
                            onToggle = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                            dragHandleModifier = Modifier.draggableHandle(),
                            supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent
                        )
                    }
                } else {
                    SupersetSelectRow(
                        exerciseWithSets = exerciseWithSets,
                        isSelected = isSelected,
                        onToggle = { viewModel.toggleSupersetCandidate(exerciseWithSets.exercise.id) },
                        supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent
                    )
                }
                return@items
            }

            if (reorderableLazyListState != null) {
                ReorderableItem(reorderableLazyListState, key = exerciseWithSets.exercise.id) { _ ->
                    ExerciseCard(
                        exerciseWithSets = exerciseWithSets,
                        supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent,
                        isSelectMode = workoutState.isSupersetSelectMode,
                        isSelected = isSelected,
                        activeTimerExerciseId = activeTimerExerciseId,
                        activeTimerSetOrder = activeTimerSetOrder,
                        activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                        activeTimerTotalSeconds = activeTimerTotalSeconds,
                        unitSystem = unitSystem,
                        availableExercises = workoutState.availableExercises,
                        restTimeOverrides = workoutState.restTimeOverrides,
                        hiddenRestSeparators = workoutState.hiddenRestSeparators + workoutState.finishedRestSeparators,
                        isEditMode = isEditMode,
                        isCollapsed = isCollapsed,
                        warmupsCollapsed = warmupsCollapsed,
                        onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                        onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                        onToggleWarmupsCollapsed = { viewModel.toggleWarmupsCollapsed(exerciseWithSets.exercise.id) },
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
                        onTimeChanged = { setOrder, time -> viewModel.onTimeChanged(exerciseWithSets.exercise.id, setOrder, time) },
                        onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                        onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                        onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                        onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                        onUpdateExerciseRestTimers = { work, warmup, drop, restAfterLast -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop, restAfterLast) },
                        onAddWarmupSets = { w, r, fill -> viewModel.addSmartWarmups(exerciseWithSets.exercise.id, w, r, fill) },
                        onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                        onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                        onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                        onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                        onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                        onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                        onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                        onTimerActiveClick = onTimerActiveClick,
                        onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) },
                        onTimerFinished = { viewModel.timerFinishedFeedback() },
                        onTimerWarningTick = { viewModel.timerWarningTickFeedback() },
                        rpeAutoPopTarget = rpeAutoPopTarget,
                        onConsumeRpeAutoPop = onConsumeRpeAutoPop,
                        setupSeconds = setupSeconds,
                        onSetupCountdownTick = onSetupCountdownTick,
                        onTimerHalftimeTick = { viewModel.timerHalftimeTickFeedback() },
                        onStopRestTimer = { viewModel.stopRestTimer() }
                    )
                }
            } else {
                ExerciseCard(
                    exerciseWithSets = exerciseWithSets,
                    supersetColor = supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent,
                    isSelectMode = workoutState.isSupersetSelectMode,
                    isSelected = isSelected,
                    activeTimerExerciseId = activeTimerExerciseId,
                    activeTimerSetOrder = activeTimerSetOrder,
                    activeTimerRemainingSeconds = activeTimerRemainingSeconds,
                    activeTimerTotalSeconds = activeTimerTotalSeconds,
                    unitSystem = unitSystem,
                    availableExercises = workoutState.availableExercises,
                    restTimeOverrides = workoutState.restTimeOverrides,
                    hiddenRestSeparators = workoutState.hiddenRestSeparators + workoutState.finishedRestSeparators,
                    isEditMode = isEditMode,
                    isCollapsed = isCollapsed,
                    warmupsCollapsed = warmupsCollapsed,
                    onCollapseAllExcept = { viewModel.collapseAllExcept(exerciseWithSets.exercise.id) },
                    onToggleCollapsed = { viewModel.toggleCollapsed(exerciseWithSets.exercise.id) },
                    onToggleWarmupsCollapsed = { viewModel.toggleWarmupsCollapsed(exerciseWithSets.exercise.id) },
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
                    onTimeChanged = { setOrder, time -> viewModel.onTimeChanged(exerciseWithSets.exercise.id, setOrder, time) },
                    onReplaceExercise = { newExercise -> viewModel.replaceExercise(exerciseWithSets.exercise.id, newExercise) },
                    onRemoveExercise = { viewModel.removeExercise(exerciseWithSets.exercise.id) },
                    onUpdateSessionNote = { note -> viewModel.updateExerciseSessionNote(exerciseWithSets.exercise.id, note) },
                    onUpdateStickyNote = { note -> viewModel.updateExerciseStickyNote(exerciseWithSets.exercise.id, note) },
                    onUpdateExerciseRestTimers = { work, warmup, drop, restAfterLast -> viewModel.updateExerciseRestTimers(exerciseWithSets.exercise.id, work, warmup, drop, restAfterLast) },
                    onAddWarmupSets = { w, r, fill -> viewModel.addSmartWarmups(exerciseWithSets.exercise.id, w, r, fill) },
                    onEnterSupersetMode = { viewModel.enterSupersetSelectMode(exerciseWithSets.exercise.id) },
                    onRemoveFromSuperset = { viewModel.removeFromSuperset(exerciseWithSets.exercise.id) },
                    onCompleteSet = { setOrder -> viewModel.completeSet(exerciseWithSets.exercise.id, setOrder) },
                    onSelectSetType = { setOrder, type -> viewModel.selectSetType(exerciseWithSets.exercise.id, setOrder, type) },
                    onUpdateRpe = { setOrder, rpe -> viewModel.updateRpe(exerciseWithSets.exercise.id, setOrder, rpe) },
                    onDeleteLocalRestTime = { setOrder -> viewModel.deleteLocalRestTime(exerciseWithSets.exercise.id, setOrder) },
                    onUpdateLocalRestTime = { setOrder, seconds -> viewModel.updateLocalRestTime(exerciseWithSets.exercise.id, setOrder, seconds) },
                    onTimerActiveClick = onTimerActiveClick,
                    onDeleteRestSeparator = { setOrder -> viewModel.deleteRestSeparator(exerciseWithSets.exercise.id, setOrder) },
                    onTimerFinished = { viewModel.timerFinishedFeedback() },
                    onTimerWarningTick = { viewModel.timerWarningTickFeedback() },
                    rpeAutoPopTarget = rpeAutoPopTarget,
                    onConsumeRpeAutoPop = onConsumeRpeAutoPop,
                    setupSeconds = setupSeconds,
                    onSetupCountdownTick = onSetupCountdownTick,
                    onTimerHalftimeTick = { viewModel.timerHalftimeTickFeedback() },
                    onStopRestTimer = { viewModel.stopRestTimer() }
                )
            }
        }
        } // end inner else (flat list)
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
            Icon(Icons.Default.Add, contentDescription = "Add")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when (workoutStyle) {
                    WorkoutStyle.PURE_FUNCTIONAL -> "ADD BLOCK"
                    WorkoutStyle.HYBRID -> "ADD BLOCK OR EXERCISE"
                    else -> "ADD EXERCISE"
                },
                fontWeight = FontWeight.Bold
            )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    supersetColor: Color = Color.Transparent,
    isSelectMode: Boolean,
    isSelected: Boolean,
    activeTimerExerciseId: Long?,
    activeTimerSetOrder: Int?,
    activeTimerRemainingSeconds: Int,
    activeTimerTotalSeconds: Int,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    availableExercises: List<Exercise> = emptyList(),
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
    onUpdateExerciseRestTimers: (workSeconds: Int, warmupSeconds: Int, dropSeconds: Int, restAfterLastSet: Boolean) -> Unit,
    onAddWarmupSets: (workingWeight: Double?, workingReps: Int?, fillWorkSets: Boolean) -> Unit,
    onEnterSupersetMode: () -> Unit,
    onRemoveFromSuperset: () -> Unit,
    onUpdateCardioSet: (Int, String, String, String, Boolean) -> Unit = { _, _, _, _, _ -> },
    onUpdateTimedSet: (Int, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onTimeChanged: (Int, String) -> Unit = { _, _ -> },
    isCollapsed: Boolean = false,
    warmupsCollapsed: Boolean = false,
    onCollapseAllExcept: () -> Unit = {},
    onToggleCollapsed: () -> Unit = {},
    onToggleWarmupsCollapsed: () -> Unit = {},
    dragHandleModifier: Modifier? = null,
    onTimerFinished: () -> Unit = {},
    onTimerWarningTick: () -> Unit = {},
    rpeAutoPopTarget: String? = null,
    onConsumeRpeAutoPop: () -> Unit = {},
    setupSeconds: Int = 0,
    onSetupCountdownTick: () -> Unit = {},
    onTimerHalftimeTick: () -> Unit = {},
    onStopRestTimer: () -> Unit = {}
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
    var showWarmupPrompt by remember { mutableStateOf(false) }
    var showReplaceWarmupsDialog by remember { mutableStateOf(false) }

    val isInSuperset = exerciseWithSets.supersetGroupId != null

    // Warmup eligibility checks
    val equip = exerciseWithSets.exercise.equipmentType
    val isStrengthExercise = exerciseWithSets.exercise.exerciseType == ExerciseType.STRENGTH
    val warmupParamsAvailable = if (equip == "Bodyweight") {
        exerciseWithSets.sets.any { it.weight.toDoubleOrNull()?.let { w -> w > 0 } == true }
    } else {
        WarmupCalculator.equipmentToWarmupParams(equip, unitSystem) != null
    }
    val canAddWarmups = isStrengthExercise && warmupParamsAvailable
    val alreadyHasWarmups = exerciseWithSets.sets.any { it.setType == SetType.WARMUP }
    val workSetHasWeight = exerciseWithSets.sets
        .filter { it.setType == SetType.NORMAL }
        .any { it.weight.toDoubleOrNull()?.let { w -> w > 0 } == true }
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
                        .background(supersetColor)
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
                            ExerciseType.CARDIO -> CardioHeader(unitSystem = unitSystem)
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

                        val warmupSets = exerciseWithSets.sets.filter { it.setType == SetType.WARMUP }
                        val hasWarmups = warmupSets.isNotEmpty()

                        // Collapsed warmup summary row
                        if (hasWarmups) {
                            AnimatedVisibility(
                                visible = warmupsCollapsed,
                                enter = fadeIn(animationSpec = tween(200)),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                Column {
                                    CollapsedWarmupRow(count = warmupSets.size, onClick = onToggleWarmupsCollapsed)
                                    if (exerciseWithSets.sets.any { it.setType != SetType.WARMUP }) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }

                        // Animated warmup rows
                        if (hasWarmups) {
                            AnimatedVisibility(
                                visible = !warmupsCollapsed,
                                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                            ) {
                                Column {
                                    warmupSets.forEach { set ->
                                        val index = exerciseWithSets.sets.indexOf(set)
                                        val nextSetType = exerciseWithSets.sets.getOrNull(index + 1)?.setType
                                        val defaultRestForType = if (nextSetType == SetType.WARMUP)
                                            exerciseWithSets.exercise.warmupRestSeconds
                                        else
                                            exerciseWithSets.exercise.restDurationSeconds
                                        val effectiveRest = restTimeOverrides["${exerciseId}_${set.setOrder}"] ?: defaultRestForType
                                        val separatorKey = "${exerciseId}_${set.setOrder}"
                                        val isThisTimerActive = (activeTimerExerciseId == exerciseId) && (activeTimerSetOrder == set.setOrder)
                                        val isNotLastSet = index < exerciseWithSets.sets.size - 1
                                        val isSeparatorHidden = separatorKey in hiddenRestSeparators
                                        val shouldShowSeparator = (isThisTimerActive || (isNotLastSet && effectiveRest > 0)) && !isSeparatorHidden
                                        val nextIncompleteIdx = (index + 1 until exerciseWithSets.sets.size)
                                            .firstOrNull { !exerciseWithSets.sets[it].isCompleted }
                                        val shouldAutoPopRpe = rpeAutoPopTarget == "${exerciseId}_${set.setOrder}"

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
                                                onTimeChanged = { onTimeChanged(set.setOrder, it) },
                                                onDeleteLocalRestTime = { onDeleteLocalRestTime(set.setOrder) },
                                                onDeleteRestSeparator = { onDeleteRestSeparator(set.setOrder) },
                                                onTimerActiveClick = onTimerActiveClick,
                                                onPassiveRestClick = { showRestTimePickerForSet = set.setOrder },
                                                weightFocusRequester = weightFrs.getOrNull(index),
                                                repsFocusRequester = repsFrs.getOrNull(index),
                                                nextWeightFocusRequester = nextIncompleteIdx?.let { weightFrs.getOrNull(it) },
                                                isEditMode = isEditMode,
                                                onTimerFinished = onTimerFinished,
                                                onTimerWarningTick = onTimerWarningTick,
                                                shouldAutoPopRpe = shouldAutoPopRpe,
                                                onAutoPopRpeConsumed = onConsumeRpeAutoPop,
                                                setupSeconds = setupSeconds,
                                                onSetupCountdownTick = onSetupCountdownTick,
                                                onTimerHalftimeTick = onTimerHalftimeTick,
                                                onStopRestTimer = onStopRestTimer
                                            )
                                        }
                                        if (index < exerciseWithSets.sets.lastIndex) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Non-warmup sets (always visible)
                        exerciseWithSets.sets.forEachIndexed { index, set ->
                            if (set.setType == SetType.WARMUP) return@forEachIndexed

                            val nextSetType = exerciseWithSets.sets.getOrNull(index + 1)?.setType
                            val defaultRestForType = when (set.setType) {
                                SetType.DROP    -> exerciseWithSets.exercise.dropSetRestSeconds
                                SetType.FAILURE -> exerciseWithSets.exercise.restDurationSeconds
                                else            -> if (nextSetType == SetType.DROP) exerciseWithSets.exercise.dropSetRestSeconds
                                                   else exerciseWithSets.exercise.restDurationSeconds
                            }
                            val effectiveRest = restTimeOverrides["${exerciseId}_${set.setOrder}"] ?: defaultRestForType
                            val separatorKey = "${exerciseId}_${set.setOrder}"
                            val isThisTimerActive = (activeTimerExerciseId == exerciseId) && (activeTimerSetOrder == set.setOrder)
                            val isNotLastSet = index < exerciseWithSets.sets.size - 1
                            val isSeparatorHidden = separatorKey in hiddenRestSeparators
                            val shouldShowSeparator = (isThisTimerActive || (isNotLastSet && effectiveRest > 0)) && !isSeparatorHidden
                            val nextIncompleteIdx = (index + 1 until exerciseWithSets.sets.size)
                                .firstOrNull { !exerciseWithSets.sets[it].isCompleted }
                            val shouldAutoPopRpe = rpeAutoPopTarget == "${exerciseId}_${set.setOrder}"

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
                                    onTimeChanged = { onTimeChanged(set.setOrder, it) },
                                    onDeleteLocalRestTime = { onDeleteLocalRestTime(set.setOrder) },
                                    onDeleteRestSeparator = { onDeleteRestSeparator(set.setOrder) },
                                    onTimerActiveClick = onTimerActiveClick,
                                    onPassiveRestClick = { showRestTimePickerForSet = set.setOrder },
                                    weightFocusRequester = weightFrs.getOrNull(index),
                                    repsFocusRequester = repsFrs.getOrNull(index),
                                    nextWeightFocusRequester = nextIncompleteIdx?.let { weightFrs.getOrNull(it) },
                                    isEditMode = isEditMode,
                                    onTimerFinished = onTimerFinished,
                                    onTimerWarningTick = onTimerWarningTick,
                                    shouldAutoPopRpe = shouldAutoPopRpe,
                                    onAutoPopRpeConsumed = onConsumeRpeAutoPop,
                                    setupSeconds = setupSeconds,
                                    onSetupCountdownTick = onSetupCountdownTick,
                                    onTimerHalftimeTick = onTimerHalftimeTick,
                                    onStopRestTimer = onStopRestTimer
                                )
                            }
                            if (index < exerciseWithSets.sets.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
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
            isInSuperset = isInSuperset,
            showAddWarmups = canAddWarmups,
            onAddWarmups = {
                showManagementHub = false
                if (alreadyHasWarmups) {
                    showReplaceWarmupsDialog = true
                } else if (workSetHasWeight) {
                    onAddWarmupSets(null, null, false)
                } else {
                    showWarmupPrompt = true
                }
            }
        )
    }

    if (showReplaceWarmupsDialog) {
        ReplaceWarmupsDialog(
            onDismiss = { showReplaceWarmupsDialog = false },
            onConfirm = {
                showReplaceWarmupsDialog = false
                if (workSetHasWeight) {
                    onAddWarmupSets(null, null, false)
                } else {
                    showWarmupPrompt = true
                }
            }
        )
    }

    if (showWarmupPrompt) {
        WarmupPromptDialog(
            unitSystem = unitSystem,
            onDismiss = { showWarmupPrompt = false },
            onConfirm = { weight, reps, fillWorkSets ->
                showWarmupPrompt = false
                onAddWarmupSets(weight, reps, fillWorkSets)
            }
        )
    }

    if (showReplaceDialog) {
        ModalBottomSheet(
            onDismissRequest = { showReplaceDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ExercisesScreen(
                pickerMode = true,
                isModal = true,
                onExercisesSelected = { ids ->
                    ids.firstOrNull()?.let { id ->
                        availableExercises.find { it.id == id }?.let { onReplaceExercise(it) }
                    }
                    showReplaceDialog = false
                }
            )
        }
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
            restAfterLastSet = exerciseWithSets.exercise.restAfterLastSet,
            onDismiss = { showRestTimerSheet = false },
            onConfirm = { work, warmup, drop, restAfterLast ->
                onUpdateExerciseRestTimers(work, warmup, drop, restAfterLast)
                showRestTimerSheet = false
            }
        )
    }
}

@Composable
private fun CollapsedWarmupRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "W ×$count ✓",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = "Expand warmup sets",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
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
    onTimeChanged: (String) -> Unit = {},
    onDeleteLocalRestTime: () -> Unit,
    onDeleteRestSeparator: () -> Unit,
    onTimerActiveClick: () -> Unit,
    onPassiveRestClick: () -> Unit,
    weightFocusRequester: FocusRequester? = null,
    repsFocusRequester: FocusRequester? = null,
    nextWeightFocusRequester: FocusRequester? = null,
    isEditMode: Boolean = false,
    onTimerFinished: () -> Unit = {},
    onTimerWarningTick: () -> Unit = {},
    shouldAutoPopRpe: Boolean = false,
    onAutoPopRpeConsumed: () -> Unit = {},
    setupSeconds: Int = 0,
    onSetupCountdownTick: () -> Unit = {},
    onTimerHalftimeTick: () -> Unit = {},
    onStopRestTimer: () -> Unit = {}
) {
    val setSwipeState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteSet(); true } else false })
    val restSwipeState = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteRestSeparator(); true } else it == SwipeToDismissBoxValue.Settled })

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
            val rowBg = if (set.isCompleted)
                TimerGreen.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
            else MaterialTheme.colorScheme.surface
            Box(modifier = Modifier.fillMaxWidth().background(rowBg)) {
                when (exerciseType) {
                    ExerciseType.CARDIO -> CardioSetRow(set, onUpdateCardioSet, onCompleteSet, shouldAutoPopRpe, onAutoPopRpeConsumed)
                    ExerciseType.TIMED -> TimedSetRow(set, onWeightChanged, onUpdateTimedSet, onCompleteSet, onTimeChanged, onTimerFinished, onTimerWarningTick, shouldAutoPopRpe, onAutoPopRpeConsumed, onUpdateRpe, setupSeconds, onSetupCountdownTick, onStopRestTimer, onTimerHalftimeTick)
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
                        isEditMode = isEditMode,
                        shouldAutoPopRpe = shouldAutoPopRpe,
                        onAutoPopRpeConsumed = onAutoPopRpeConsumed
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

private fun formatGhostTimedLabel(weight: String?, timeSeconds: String?, rpe: String?): String {
    val base = when {
        weight != null && timeSeconds != null -> "${weight}\u00D7${timeSeconds}s"
        weight != null -> weight
        timeSeconds != null -> "${timeSeconds}s"
        else -> return "—"
    }
    return if (rpe != null) "$base@$rpe" else base
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
    restAfterLastSet: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (workSeconds: Int, warmupSeconds: Int, dropSeconds: Int, restAfterLastSet: Boolean) -> Unit
) {
    val workMinsTfv = remember { mutableStateOf(TextFieldValue((workSeconds / 60).toString())) }
    val workSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(workSeconds % 60))) }
    val warmupMinsTfv = remember { mutableStateOf(TextFieldValue((warmupSeconds / 60).toString())) }
    val warmupSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(warmupSeconds % 60))) }
    val dropMinsTfv = remember { mutableStateOf(TextFieldValue((dropSeconds / 60).toString())) }
    val dropSecsTfv = remember { mutableStateOf(TextFieldValue("%02d".format(dropSeconds % 60))) }
    var restAfterLastSetState by remember { mutableStateOf(restAfterLastSet) }
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rest after last set", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = restAfterLastSetState,
                        onCheckedChange = { restAfterLastSetState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(
                    toSeconds(workMinsTfv.value.text, workSecsTfv.value.text),
                    toSeconds(warmupMinsTfv.value.text, warmupSecsTfv.value.text),
                    toSeconds(dropMinsTfv.value.text, dropSecsTfv.value.text),
                    restAfterLastSetState
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
    supersetColor: Color = Color.Transparent,
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
                    tint = supersetColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Single row shown for a functional block in organize mode. Drag handle only — no checkbox (blocks are superset-immune). */
@Composable
private fun FunctionalBlockOrganizeRow(
    block: WorkoutBlock,
    exerciseCount: Int,
    dragHandleModifier: Modifier? = null
) {
    val blockType = runCatching { com.powerme.app.data.BlockType.valueOf(block.type) }
        .getOrDefault(com.powerme.app.data.BlockType.STRENGTH)
    val badgeColor = when (blockType) {
        com.powerme.app.data.BlockType.AMRAP   -> TimerGreen
        com.powerme.app.data.BlockType.RFT     -> NeonPurple
        com.powerme.app.data.BlockType.EMOM    -> ReadinessAmber
        com.powerme.app.data.BlockType.TABATA  -> TimerRed
        else                                   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    contentDescription = "Drag block to reorder",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = dragHandleModifier
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = blockType.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (block.name != null) {
                    Text(
                        text = block.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "$exerciseCount exercise${if (exerciseCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Block cannot be supersetted",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Bottom sheet to edit plan parameters of a functional block mid-workout (before it has started). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBlockParamsSheet(
    block: WorkoutBlock,
    onDismiss: () -> Unit,
    onConfirm: (durationSeconds: Int?, targetRounds: Int?, emomRoundSeconds: Int?, tabataWorkSeconds: Int?, tabataRestSeconds: Int?) -> Unit
) {
    val blockType = runCatching { com.powerme.app.data.BlockType.valueOf(block.type) }
        .getOrDefault(com.powerme.app.data.BlockType.STRENGTH)

    var durationMins by remember { mutableStateOf(block.durationSeconds?.let { (it / 60).toString() } ?: "") }
    var targetRounds by remember { mutableStateOf(block.targetRounds?.toString() ?: "") }
    var emomInterval by remember { mutableStateOf(block.emomRoundSeconds?.toString() ?: "") }
    var tabataWork by remember { mutableStateOf(block.tabataWorkSeconds?.toString() ?: "") }
    var tabataRest by remember { mutableStateOf(block.tabataRestSeconds?.toString() ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Edit ${blockType.displayName} parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (blockType) {
                com.powerme.app.data.BlockType.AMRAP -> {
                    OutlinedTextField(
                        value = durationMins,
                        onValueChange = { durationMins = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                com.powerme.app.data.BlockType.RFT -> {
                    OutlinedTextField(
                        value = targetRounds,
                        onValueChange = { targetRounds = it.filter { c -> c.isDigit() } },
                        label = { Text("Target rounds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                com.powerme.app.data.BlockType.EMOM -> {
                    OutlinedTextField(
                        value = emomInterval,
                        onValueChange = { emomInterval = it.filter { c -> c.isDigit() } },
                        label = { Text("Interval (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = durationMins,
                        onValueChange = { durationMins = it.filter { c -> c.isDigit() } },
                        label = { Text("Total duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                com.powerme.app.data.BlockType.TABATA -> {
                    OutlinedTextField(
                        value = tabataWork,
                        onValueChange = { tabataWork = it.filter { c -> c.isDigit() } },
                        label = { Text("Work (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tabataRest,
                        onValueChange = { tabataRest = it.filter { c -> c.isDigit() } },
                        label = { Text("Rest (seconds)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {}
            }
            Button(
                onClick = {
                    onConfirm(
                        durationMins.toIntOrNull()?.let { it * 60 },
                        targetRounds.toIntOrNull(),
                        emomInterval.toIntOrNull(),
                        tabataWork.toIntOrNull(),
                        tabataRest.toIntOrNull()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
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
    val flashAlpha = remember { Animatable(0.45f) }
    LaunchedEffect(isActive) {
        if (isActive) {
            flashAlpha.snapTo(0.45f)
            flashAlpha.animateTo(0.28f, animationSpec = tween(300))
        }
    }
    // Fixed height on both states prevents layout jump during Crossfade transition
    Crossfade(targetState = isActive, label = "RestTimerTransition") { active ->
        if (active) {
            // Outer container fills the full row; the pill inside shrinks as time drains
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clickable(onClick = onActiveClick),
            ) {
                // key resets animateFloatAsState on each timer activation so it starts at the
                // correct fraction (not 0), then smoothly interpolates 1s per tick backwards.
                key(liveTotalSeconds) {
                    val rawProgress = if (liveTotalSeconds > 0) liveRemainingSeconds.toFloat() / liveTotalSeconds.toFloat() else 0f
                    // Width fraction: 1.0 at timer start → 0.0 when timer reaches 0
                    val animatedWidth by animateFloatAsState(
                        targetValue = rawProgress,
                        animationSpec = tween(1000, easing = LinearEasing),
                        label = "restWidth"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedWidth)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = flashAlpha.value))
                            .align(Alignment.CenterStart)
                    )
                }
                // Text stays centered over the full row regardless of pill width
                Text(
                    text = formatSecs(liveRemainingSeconds),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagementHubSheet(
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onSessionNote: () -> Unit,
    onStickyNote: () -> Unit,
    onRestTimer: () -> Unit,
    onSuperset: () -> Unit,
    isInSuperset: Boolean,
    showAddWarmups: Boolean = false,
    onAddWarmups: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 24.dp)) {
            val items = buildList {
                add(Triple("Session Note", Icons.Default.Notes, onSessionNote))
                add(Triple("Sticky Note", Icons.Default.PushPin, onStickyNote))
                add(Triple("Set Rest Timers", Icons.Default.Timer, onRestTimer))
                if (showAddWarmups) add(Triple("Add Warmups", Icons.Default.FitnessCenter, onAddWarmups))
                add(Triple("Replace Exercise", Icons.Default.Refresh, onReplace))
                add(Triple(if (isInSuperset) "Remove from Superset" else "Organize Exercises", Icons.Default.Sync, onSuperset))
                add(Triple("Remove Exercise", Icons.Default.Delete, onRemove))
            }
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

@Composable
private fun ReplaceWarmupsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace warmup sets?") },
        text = { Text("This will remove your current warmup sets and generate new ones.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WarmupPromptDialog(
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    onConfirm: (weight: Double, reps: Int, fillWorkSets: Boolean) -> Unit
) {
    val unitLabel = if (unitSystem == UnitSystem.METRIC) "kg" else "lb"
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var fillWorkSets by remember { mutableStateOf(false) }
    val repsFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Working Weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Working weight ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { repsFocusRequester.requestFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text("Working reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(repsFocusRequester)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fill work sets", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = fillWorkSets,
                        onCheckedChange = { fillWorkSets = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        },
        confirmButton = {
            val weight = weightText.toDoubleOrNull()
            val reps = repsText.toIntOrNull()
            TextButton(
                onClick = {
                    if (weight != null && weight > 0) {
                        onConfirm(weight, reps ?: 0, fillWorkSets)
                    }
                },
                enabled = weightText.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Add Warmups") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
    isEditMode: Boolean = false,
    shouldAutoPopRpe: Boolean = false,
    onAutoPopRpeConsumed: () -> Unit = {}
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
    LaunchedEffect(shouldAutoPopRpe) {
        if (shouldAutoPopRpe && !isEditMode) {
            showRpePicker = true
            onAutoPopRpeConsumed()
        }
    }
    val isTouched = (set.weight.isNotBlank() || set.reps.isNotBlank()) && !set.isCompleted
    val primaryColor = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current
    LaunchedEffect(showSetTypeMenu) {
        if (!showSetTypeMenu) {
            delay(100)
            focusManager.clearFocus()
        }
    }
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
        Row(modifier = Modifier.fillMaxWidth().height(35.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
                focusRequester = weightFocusRequester,
                accessoryEnabled = true
            )
            WorkoutInputField(
                value = set.reps,
                onValueChange = onRepsChanged,
                modifier = Modifier.weight(REPS_COL_WEIGHT).padding(horizontal = 2.dp),
                placeholder = "0",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onCompleteSet()
                }),
                focusRequester = repsFocusRequester,
                accessoryEnabled = true
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rpeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (set.rpeValue != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        if (set.isCompleted && set.rpeValue != null) {
                            Spacer(modifier = Modifier.width(2.dp))
                            when (rpeCategory(set.rpeValue)) {
                                RpeCategory.GOLDEN -> Text("✦", fontSize = 10.sp, color = GoldenRPE)
                                RpeCategory.MAX_EFFORT -> Box(modifier = Modifier.size(6.dp).background(ProError, CircleShape))
                                RpeCategory.MODERATE -> Box(modifier = Modifier.size(6.dp).background(ReadinessAmber, CircleShape))
                                RpeCategory.LOW -> Box(modifier = Modifier.size(6.dp).background(Color.Gray, CircleShape))
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(CHECK_COL_WEIGHT)
                        .fillMaxHeight()
                        .background(if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                        .clickable { focusManager.clearFocus(); onCompleteSet() },
                    contentAlignment = Alignment.Center
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
private fun CardioHeader(unitSystem: UnitSystem = UnitSystem.METRIC) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Text("DIST(${UnitConverter.distanceLabel(unitSystem).uppercase()})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
        Text("TIME(S)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.25f))
        Text("PACE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
        Text("RPE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Spacer(modifier = Modifier.weight(0.10f))
    }
}

@Composable
private fun TimedHeader() {
    var showPrevTooltip by remember { mutableStateOf(false) }
    if (showPrevTooltip) {
        AlertDialog(
            onDismissRequest = { showPrevTooltip = false },
            title = { Text("Previous Session") },
            text = { Text("Shows your weight × time from the last time you performed this exercise.") },
            confirmButton = { TextButton(onClick = { showPrevTooltip = false }) { Text("Got it") } }
        )
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("SET", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(SET_COL_WEIGHT))
        Text("PREV", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(PREV_COL_WEIGHT).clickable { showPrevTooltip = true })
        Text("WEIGHT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.22f))
        Text("TIME(S)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.20f))
        Text("RPE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.weight(0.10f))
        Spacer(modifier = Modifier.weight(0.18f))
    }
}

@Composable
fun CardioSetRow(
    set: ActiveSet,
    onUpdateSet: (String, String, String, Boolean) -> Unit,
    onCompleteSet: () -> Unit,
    shouldAutoPopRpe: Boolean = false,
    onAutoPopRpeConsumed: () -> Unit = {}
) {
    val dist = set.distance
    val time = set.timeSeconds
    val rpe = set.rpe
    var showRpePicker by remember { mutableStateOf(false) }
    LaunchedEffect(shouldAutoPopRpe) {
        if (shouldAutoPopRpe) {
            showRpePicker = true
            onAutoPopRpeConsumed()
        }
    }
    if (showRpePicker) {
        RpePickerSheet(
            currentRpe = set.rpeValue,
            onUpdateRpe = { value ->
                val rpeText = value?.let { "%.1f".format(it / 10.0) } ?: ""
                onUpdateSet(dist, time, rpeText, set.isCompleted)
                showRpePicker = false
            },
            onDismiss = { showRpePicker = false }
        )
    }

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
        modifier = Modifier.fillMaxWidth().height(35.dp).padding(vertical = 2.dp),
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
        Box(
            modifier = Modifier
                .weight(0.10f)
                .fillMaxHeight()
                .background(if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                .clickable(onClick = onCompleteSet),
            contentAlignment = Alignment.Center
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
    onCompleteSet: () -> Unit,
    onTimeChanged: (String) -> Unit = {},
    onTimerFinished: () -> Unit = {},
    onTimerWarningTick: () -> Unit = {},
    shouldAutoPopRpe: Boolean = false,
    onAutoPopRpeConsumed: () -> Unit = {},
    onUpdateRpe: (Int?) -> Unit = {},
    setupSeconds: Int = 0,
    onSetupCountdownTick: () -> Unit = {},
    onStopRestTimer: () -> Unit = {},
    onTimerHalftimeTick: () -> Unit = {}
) {
    val totalSeconds = set.timeSeconds.toIntOrNull() ?: 0
    var timerState by remember(set.id) { mutableStateOf(if (set.isCompleted) TimedSetState.COMPLETED else TimedSetState.IDLE) }
    var remainingSeconds by remember(set.id) { mutableIntStateOf(totalSeconds) }
    var setupRemaining by remember(set.id) { mutableIntStateOf(setupSeconds) }

    // Sync remaining time when the target duration changes while IDLE
    LaunchedEffect(set.timeSeconds) {
        if (timerState == TimedSetState.IDLE) {
            remainingSeconds = set.timeSeconds.toIntOrNull() ?: 0
        }
    }

    // Handle external completion (e.g. user taps checkbox while timer is running)
    LaunchedEffect(set.isCompleted) {
        if (set.isCompleted && (timerState == TimedSetState.RUNNING || timerState == TimedSetState.SETUP)) {
            timerState = TimedSetState.COMPLETED
        }
    }

    // Countdown loop — handles SETUP (get ready) then RUNNING; cancels on state change
    LaunchedEffect(timerState) {
        when (timerState) {
            TimedSetState.SETUP -> {
                setupRemaining = setupSeconds
                while (setupRemaining > 0) {
                    onSetupCountdownTick()
                    delay(1000L)
                    setupRemaining--
                }
                if (timerState == TimedSetState.SETUP) {
                    remainingSeconds = set.timeSeconds.toIntOrNull() ?: 0
                    timerState = TimedSetState.RUNNING
                }
            }
            TimedSetState.RUNNING -> {
                while (remainingSeconds > 0) {
                    delay(1000L)
                    remainingSeconds--
                    if (remainingSeconds in 1..3) {
                        onTimerWarningTick()
                    }
                    if (totalSeconds > 1 && remainingSeconds == totalSeconds / 2) {
                        onTimerHalftimeTick()
                    }
                }
                if (timerState == TimedSetState.RUNNING) {
                    timerState = TimedSetState.COMPLETED
                    onTimerFinished()
                    onCompleteSet()
                }
            }
            else -> {}
        }
    }

    val weight = set.weight
    val time = set.timeSeconds
    val rpe = set.rpe

    var showRpePicker by remember { mutableStateOf(false) }
    LaunchedEffect(shouldAutoPopRpe) {
        if (shouldAutoPopRpe) {
            showRpePicker = true
            onAutoPopRpeConsumed()
        }
    }
    if (showRpePicker) {
        RpePickerSheet(
            currentRpe = set.rpeValue,
            onUpdateRpe = { value ->
                onUpdateRpe(value)
                showRpePicker = false
            },
            onDismiss = { showRpePicker = false }
        )
    }

    when (timerState) {
        TimedSetState.IDLE -> {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(SET_COL_WEIGHT), contentAlignment = Alignment.Center) {
                    Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.weight(PREV_COL_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = formatGhostTimedLabel(set.ghostWeight, set.ghostTimeSeconds, set.ghostRpe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WorkoutInputField(
                    value = weight,
                    onValueChange = onWeightChanged,
                    modifier = Modifier.weight(0.22f).padding(horizontal = 2.dp),
                    placeholder = "0"
                )
                WorkoutInputField(
                    value = time,
                    onValueChange = { onTimeChanged(it) },
                    modifier = Modifier.weight(0.20f).padding(horizontal = 2.dp),
                    placeholder = "0"
                )
                Box(
                    modifier = Modifier
                        .weight(0.10f)
                        .fillMaxHeight()
                        .clickable { showRpePicker = true },
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
                Box(
                    modifier = Modifier
                        .weight(0.12f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                        .clickable {
                            val secs = time.toIntOrNull() ?: 0
                            if (secs > 0) {
                                onStopRestTimer()
                                if (setupSeconds > 0) {
                                    timerState = TimedSetState.SETUP
                                } else {
                                    remainingSeconds = secs
                                    timerState = TimedSetState.RUNNING
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(0.06f)
                        .fillMaxHeight()
                        .clickable(onClick = onCompleteSet),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Complete", tint = if (set.isCompleted) TimerGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
                }
            }
        }

        TimedSetState.SETUP -> {
            val setupProgress = setupRemaining.toFloat() / setupSeconds.toFloat().coerceAtLeast(1f)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
                        Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Get Ready",
                        modifier = Modifier.weight(0.35f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SetupAmber,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "$setupRemaining",
                        modifier = Modifier.weight(0.20f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SetupAmber,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.20f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                            .clickable {
                                timerState = TimedSetState.IDLE
                                remainingSeconds = set.timeSeconds.toIntOrNull() ?: 0
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.weight(0.15f))
                }
                LinearProgressIndicator(
                    progress = { setupProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = SetupAmber,
                    trackColor = SetupAmber.copy(alpha = 0.15f)
                )
            }
        }

        TimedSetState.RUNNING -> {
            val total = (time.toIntOrNull() ?: 1).coerceAtLeast(1)
            val progress = remainingSeconds.toFloat() / total.toFloat()
            val mm = remainingSeconds / 60
            val ss = remainingSeconds % 60
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
                        Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "%d:%02d".format(mm, ss),
                        modifier = Modifier.weight(0.45f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TimerGreen,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.20f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                            .clickable { timerState = TimedSetState.PAUSED },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.weight(0.25f))
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = TimerGreen,
                    trackColor = TimerGreen.copy(alpha = 0.15f)
                )
            }
        }

        TimedSetState.PAUSED -> {
            val mm = remainingSeconds / 60
            val ss = remainingSeconds % 60
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(0.10f), contentAlignment = Alignment.Center) {
                        Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "%d:%02d".format(mm, ss),
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    // Resume
                    Box(
                        modifier = Modifier
                            .weight(0.18f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                            .clickable { timerState = TimedSetState.RUNNING },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                    // Mark Done
                    Box(
                        modifier = Modifier
                            .weight(0.18f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .background(TimerGreen.copy(alpha = 0.2f), MaterialTheme.shapes.extraSmall)
                            .clickable { timerState = TimedSetState.COMPLETED; onCompleteSet() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = TimerGreen, modifier = Modifier.size(16.dp))
                    }
                    // Reset
                    Box(
                        modifier = Modifier
                            .weight(0.18f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                            .clickable {
                                remainingSeconds = time.toIntOrNull() ?: 0
                                timerState = TimedSetState.IDLE
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.weight(0.11f))
                }
            }
        }

        TimedSetState.COMPLETED -> {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(SET_COL_WEIGHT), contentAlignment = Alignment.Center) {
                    Text(text = "${set.setOrder}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.weight(PREV_COL_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = formatGhostTimedLabel(set.ghostWeight, set.ghostTimeSeconds, set.ghostRpe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WorkoutInputField(
                    value = weight,
                    onValueChange = onWeightChanged,
                    modifier = Modifier.weight(0.22f).padding(horizontal = 2.dp),
                    placeholder = "0"
                )
                WorkoutInputField(
                    value = time,
                    onValueChange = { onTimeChanged(it) },
                    modifier = Modifier.weight(0.20f).padding(horizontal = 2.dp),
                    placeholder = "0"
                )
                Box(
                    modifier = Modifier
                        .weight(0.10f)
                        .fillMaxHeight()
                        .clickable { showRpePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    val rpeText = set.rpeValue?.let { v ->
                        val d = v / 10.0
                        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
                    } ?: "—"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rpeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (set.rpeValue != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        if (set.rpeValue != null) {
                            Spacer(modifier = Modifier.width(2.dp))
                            when (rpeCategory(set.rpeValue)) {
                                RpeCategory.GOLDEN -> Text("✦", fontSize = 10.sp, color = GoldenRPE)
                                RpeCategory.MAX_EFFORT -> Box(modifier = Modifier.size(6.dp).background(ProError, CircleShape))
                                RpeCategory.MODERATE -> Box(modifier = Modifier.size(6.dp).background(ReadinessAmber, CircleShape))
                                RpeCategory.LOW -> Box(modifier = Modifier.size(6.dp).background(Color.Gray, CircleShape))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(0.08f))
                Box(
                    modifier = Modifier
                        .weight(0.10f)
                        .fillMaxHeight()
                        .background(TimerGreen, MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onCompleteSet),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Complete", tint = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpePickerSheet(
    currentRpe: Int?,
    onUpdateRpe: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedScale = RPE_SCALE.groupBy { it.category }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 12.dp)
        ) {
            Text(
                "Rate of Perceived Exertion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "How many reps could you still do?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            listOf(
                RpeCategory.LOW,
                RpeCategory.MODERATE,
                RpeCategory.GOLDEN,
                RpeCategory.MAX_EFFORT
            ).forEach { category ->
                val entries = groupedScale[category] ?: return@forEach
                RpeCategoryGroup(
                    category = category,
                    entries = entries,
                    currentRpe = currentRpe,
                    onSelect = { onUpdateRpe(it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            TextButton(
                onClick = { onUpdateRpe(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun RpeCategoryGroup(
    category: RpeCategory,
    entries: List<RpeInfo>,
    currentRpe: Int?,
    onSelect: (Int) -> Unit
) {
    val headerColor = when (category) {
        RpeCategory.LOW        -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        RpeCategory.MODERATE   -> ReadinessAmber
        RpeCategory.GOLDEN     -> GoldenRPE
        RpeCategory.MAX_EFFORT -> ProError
    }

    val isGolden = category == RpeCategory.GOLDEN

    @Composable
    fun groupContent() {
        Column(modifier = Modifier.padding(if (isGolden) 12.dp else 0.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = if (isGolden) 2.dp else 4.dp)
            ) {
                Text(
                    category.displayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = headerColor
                )
            }
            if (isGolden) {
                Text(
                    "Target zone for hypertrophy",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldenRPE.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            entries.forEach { info ->
                RpeRow(
                    info = info,
                    isSelected = currentRpe == info.value,
                    numberColor = when (category) {
                        RpeCategory.GOLDEN     -> GoldenRPE
                        RpeCategory.MAX_EFFORT -> ProError
                        else                   -> MaterialTheme.colorScheme.onSurface
                    },
                    selectedBackground = if (isGolden)
                        GoldenRPE.copy(alpha = 0.18f)
                    else
                        MaterialTheme.colorScheme.primaryContainer,
                    onClick = { onSelect(info.value) }
                )
            }
        }
    }

    if (isGolden) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = GoldenRPE.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = GoldenRPE.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium
                )
        ) {
            groupContent()
        }
    } else {
        groupContent()
    }
}

@Composable
private fun RpeRow(
    info: RpeInfo,
    isSelected: Boolean,
    numberColor: Color,
    selectedBackground: Color,
    onClick: () -> Unit
) {
    val background = if (isSelected) selectedBackground else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = info.display,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMono,
            color = numberColor,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = info.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = numberColor.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
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
                Text("Next", color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
