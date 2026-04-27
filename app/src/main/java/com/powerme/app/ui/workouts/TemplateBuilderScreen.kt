package com.powerme.app.ui.workouts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.powerme.app.data.BlockType
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.buildSupersetColorMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateBuilderScreen(
    navController: NavController,
    viewModel: TemplateBuilderViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState()
    val draftExercises by viewModel.draftExercises.collectAsState()
    val draftBlocks by viewModel.draftBlocks.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val supersetColorMap = remember(draftExercises) {
        buildSupersetColorMap(draftExercises.map { it.supersetGroupId })
    }
    val isOrganizeMode by viewModel.isOrganizeMode.collectAsState()
    val selectedExerciseIds by viewModel.selectedExerciseIds.collectAsState()
    val workoutStyle by viewModel.workoutStyle.collectAsState()
    val pendingBlock by viewModel.pendingBlock.collectAsState()

    var isReorderMode by remember { mutableStateOf(false) }
    var showBlockWizard by remember { mutableStateOf(false) }
    var showHybridSheet by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key
        val toKey = to.key
        when {
            fromKey is String && fromKey.startsWith("block-card-") &&
            toKey is String && toKey.startsWith("block-card-") -> {
                val fromBlockId = fromKey.removePrefix("block-card-")
                val toBlockId = toKey.removePrefix("block-card-")
                val fromIdx = draftBlocks.indexOfFirst { it.id == fromBlockId }
                val toIdx = draftBlocks.indexOfFirst { it.id == toBlockId }
                if (fromIdx >= 0 && toIdx >= 0) viewModel.reorderBlocks(fromIdx, toIdx)
            }
            fromKey is Long && toKey is Long -> {
                val fromIdx = draftExercises.indexOfFirst { it.exerciseId == fromKey }
                val toIdx   = draftExercises.indexOfFirst { it.exerciseId == toKey }
                val fromBlock = draftExercises.getOrNull(fromIdx)?.blockId
                val toBlock   = draftExercises.getOrNull(toIdx)?.blockId
                if (fromIdx >= 0 && toIdx >= 0 && fromBlock == toBlock) {
                    viewModel.reorderDraftExercise(fromIdx, toIdx)
                }
            }
        }
    }

    // Observe selected exercises passed back from the exercise picker
    val backEntry = navController.currentBackStackEntry
    val selectedIds by (backEntry
        ?.savedStateHandle
        ?.getStateFlow<ArrayList<Long>?>("selected_exercises", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) })

    LaunchedEffect(selectedIds) {
        val ids = selectedIds ?: return@LaunchedEffect
        if (ids.isNotEmpty()) {
            if (pendingBlock != null) {
                viewModel.completePendingBlock(ids)
            } else {
                viewModel.addExercises(ids)
            }
            backEntry?.savedStateHandle?.remove<ArrayList<Long>>("selected_exercises")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = routineName,
                        onValueChange = viewModel::onNameChanged,
                        placeholder = { Text("Routine name", style = MaterialTheme.typography.bodyLarge) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (routineName.isNotBlank() && !isSaving) {
                                viewModel.save { navController.popBackStack() }
                            }
                        }),
                        colors = PowerMeDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save { navController.popBackStack() } },
                        enabled = routineName.isNotBlank() && !isSaving
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Organize mode CAB
            if (isOrganizeMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.exitOrganizeMode() }) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = if (selectedExerciseIds.isEmpty()) "Organize exercises"
                                   else "Organize \u2022 ${selectedExerciseIds.size} selected",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(
                            onClick = { viewModel.commitSupersetGroup() },
                            enabled = selectedExerciseIds.size >= 2
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "Group as superset",
                                tint = if (selectedExerciseIds.size >= 2)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            }

            val hasFunctionalBlocks = remember(draftBlocks) { draftBlocks.any { it.type != BlockType.STRENGTH } }
            val showBlockHeaders = draftBlocks.size >= 2 || hasFunctionalBlocks
            val exercisesByBlock = remember(draftExercises) { draftExercises.groupBy { it.blockId } }
            val sortedBlocks = remember(draftBlocks) { draftBlocks.sortedBy { it.order } }
            val blockTypeById = remember(draftBlocks) { draftBlocks.associate { it.id to it.type } }

            if (draftExercises.isEmpty() && draftBlocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            text = "No exercises yet",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                }
            } else if (showBlockHeaders) {
                // Block-sectioned list
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Unblocked exercises first (legacy STRENGTH exercises without a blockId)
                    val unblockedExercises = exercisesByBlock[null] ?: emptyList()
                    if (unblockedExercises.isNotEmpty()) {
                        items(unblockedExercises, key = { it.exerciseId }) { draft ->
                            ReorderableItem(reorderState, key = draft.exerciseId) { isDragging ->
                                ExerciseRowContent(
                                    draft = draft,
                                    supersetColor = supersetColorMap[draft.supersetGroupId] ?: Color.Transparent,
                                    isDragging = isDragging,
                                    isOrganizeMode = isOrganizeMode,
                                    isReorderMode = isReorderMode,
                                    blockType = blockTypeById[draft.blockId],
                                    selectedExerciseIds = selectedExerciseIds,
                                    onLongPress = { isReorderMode = true },
                                    onToggleSelection = { viewModel.toggleExerciseSelection(it) },
                                    onDragStopped = { isReorderMode = false },
                                    onIncrement = { viewModel.incrementSets(draft.exerciseId) },
                                    onDecrement = { viewModel.decrementSets(draft.exerciseId) },
                                    onRemove = { viewModel.removeExercise(draft.exerciseId) },
                                    onIncrementReps = { viewModel.incrementReps(draft.exerciseId) },
                                    onDecrementReps = { viewModel.decrementReps(draft.exerciseId) },
                                    onIncrementHold = { viewModel.incrementHoldSeconds(draft.exerciseId) },
                                    onDecrementHold = { viewModel.decrementHoldSeconds(draft.exerciseId) },
                                    onToggleInputMode = { viewModel.toggleInputMode(draft.exerciseId) }
                                )
                            }
                        }
                    }

                    // Blocks in order
                    sortedBlocks.forEach { block ->
                        val blockExercises = exercisesByBlock[block.id] ?: emptyList()
                        if (block.type == BlockType.STRENGTH) {
                            item(key = "block-header-${block.id}") {
                                BlockHeader(
                                    block = block,
                                    onAddExercise = {
                                        val existing = draftExercises.filter { it.blockId == block.id }.map { it.exerciseId }
                                        navController.currentBackStackEntry?.savedStateHandle
                                            ?.set("preselected_exercises", ArrayList(existing))
                                        viewModel.setPendingBlock(block)
                                        navController.navigate("exercise_picker?functionalFilter=true&typeFilters=CARDIO,PLYOMETRIC")
                                    },
                                    onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                            items(blockExercises, key = { it.exerciseId }) { draft ->
                                ReorderableItem(reorderState, key = draft.exerciseId) { isDragging ->
                                    ExerciseRowContent(
                                        draft = draft,
                                        supersetColor = supersetColorMap[draft.supersetGroupId] ?: Color.Transparent,
                                        isDragging = isDragging,
                                        isOrganizeMode = isOrganizeMode,
                                        isReorderMode = isReorderMode,
                                        blockType = blockTypeById[draft.blockId],
                                        selectedExerciseIds = selectedExerciseIds,
                                        onLongPress = { isReorderMode = true },
                                        onToggleSelection = { viewModel.toggleExerciseSelection(it) },
                                        onDragStopped = { isReorderMode = false },
                                        onIncrement = { viewModel.incrementSets(draft.exerciseId) },
                                        onDecrement = { viewModel.decrementSets(draft.exerciseId) },
                                        onRemove = { viewModel.removeExercise(draft.exerciseId) },
                                        onIncrementReps = { viewModel.incrementReps(draft.exerciseId) },
                                        onDecrementReps = { viewModel.decrementReps(draft.exerciseId) },
                                        onIncrementHold = { viewModel.incrementHoldSeconds(draft.exerciseId) },
                                        onDecrementHold = { viewModel.decrementHoldSeconds(draft.exerciseId) },
                                        onToggleInputMode = { viewModel.toggleInputMode(draft.exerciseId) }
                                    )
                                }
                            }
                            item(key = "block-spacer-${block.id}") {
                                Spacer(Modifier.height(8.dp))
                            }
                        } else {
                            // Functional block: group everything into a single draggable Card
                            item(key = "block-card-${block.id}") {
                                ReorderableItem(reorderState, key = "block-card-${block.id}") { isDragging ->
                                    val elevation by animateDpAsState(
                                        if (isDragging) 8.dp else 1.dp,
                                        label = "block-drag-elev"
                                    )
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
                                    ) {
                                        Column {
                                            BlockHeader(
                                                block = block,
                                                onAddExercise = {
                                                    val existing = draftExercises.filter { it.blockId == block.id }.map { it.exerciseId }
                                                    navController.currentBackStackEntry?.savedStateHandle
                                                        ?.set("preselected_exercises", ArrayList(existing))
                                                    viewModel.setPendingBlock(block)
                                                    navController.navigate("exercise_picker?functionalFilter=true")
                                                },
                                                onDeleteBlock = { viewModel.deleteBlock(block.id) },
                                                standalone = false,
                                                dragHandleModifier = Modifier.draggableHandle()
                                            )
                                            blockExercises.forEachIndexed { index, draft ->
                                                FunctionalExerciseRow(
                                                    draft = draft,
                                                    blockType = block.type,
                                                    onIncrementReps = { viewModel.incrementReps(draft.exerciseId) },
                                                    onDecrementReps = { viewModel.decrementReps(draft.exerciseId) },
                                                    onIncrementHold = { viewModel.incrementHoldSeconds(draft.exerciseId) },
                                                    onDecrementHold = { viewModel.decrementHoldSeconds(draft.exerciseId) },
                                                    onToggleInputMode = { viewModel.toggleInputMode(draft.exerciseId) },
                                                    onWeightChanged = { w -> viewModel.updateFunctionalWeight(draft.exerciseId, w) },
                                                    onRemove = { viewModel.removeExercise(draft.exerciseId) }
                                                )
                                                if (index < blockExercises.lastIndex) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(horizontal = 8.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
            } else {
                // Flat list (legacy / single STRENGTH block / no blocks)
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(draftExercises, key = { it.exerciseId }) { draft ->
                        ReorderableItem(reorderState, key = draft.exerciseId) { isDragging ->
                            ExerciseRowContent(
                                draft = draft,
                                supersetColor = supersetColorMap[draft.supersetGroupId] ?: Color.Transparent,
                                isDragging = isDragging,
                                isOrganizeMode = isOrganizeMode,
                                isReorderMode = isReorderMode,
                                blockType = blockTypeById[draft.blockId],
                                selectedExerciseIds = selectedExerciseIds,
                                onLongPress = { isReorderMode = true },
                                onToggleSelection = { viewModel.toggleExerciseSelection(it) },
                                onDragStopped = { isReorderMode = false },
                                onIncrement = { viewModel.incrementSets(draft.exerciseId) },
                                onDecrement = { viewModel.decrementSets(draft.exerciseId) },
                                onRemove = { viewModel.removeExercise(draft.exerciseId) },
                                onIncrementReps = { viewModel.incrementReps(draft.exerciseId) },
                                onDecrementReps = { viewModel.decrementReps(draft.exerciseId) },
                                onIncrementHold = { viewModel.incrementHoldSeconds(draft.exerciseId) },
                                onDecrementHold = { viewModel.decrementHoldSeconds(draft.exerciseId) },
                                onToggleInputMode = { viewModel.toggleInputMode(draft.exerciseId) }
                            )
                        }
                    }
                }
            }

            // Footer buttons
            if (!isOrganizeMode) {
                if (draftExercises.size >= 2) {
                    OutlinedButton(
                        onClick = { viewModel.enterOrganizeMode() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Organize Exercises")
                    }
                }
                OutlinedButton(
                    onClick = {
                        when (workoutStyle) {
                            WorkoutStyle.PURE_FUNCTIONAL -> showBlockWizard = true
                            WorkoutStyle.HYBRID -> showHybridSheet = true
                            else -> {
                                navController.currentBackStackEntry?.savedStateHandle
                                    ?.set("preselected_exercises", ArrayList<Long>())
                                navController.navigate("exercise_picker")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(when (workoutStyle) {
                        WorkoutStyle.PURE_FUNCTIONAL -> "Add Block"
                        WorkoutStyle.HYBRID -> "Add Exercise or Block"
                        else -> "Add Exercises"
                    })
                }
            }
        }
    }

    // FunctionalBlockWizard sheet
    if (showBlockWizard) {
        FunctionalBlockWizard(
            onDismiss = { showBlockWizard = false },
            onBlockCreated = { block ->
                showBlockWizard = false
                // Clear preselected — new block has no existing exercises yet
                navController.currentBackStackEntry?.savedStateHandle
                    ?.set("preselected_exercises", ArrayList<Long>())
                viewModel.setPendingBlock(block)
                navController.navigate("exercise_picker?functionalFilter=true")
            }
        )
    }

    // Hybrid chooser sheet
    if (showHybridSheet) {
        AddBlockOrExerciseSheet(
            onDismiss = { showHybridSheet = false },
            onAddStrengthExercise = {
                navController.currentBackStackEntry?.savedStateHandle
                    ?.set("preselected_exercises", ArrayList<Long>())
                navController.navigate("exercise_picker?typeFilters=STRENGTH,TIMED")
            },
            onAddFunctionalBlock = { showBlockWizard = true }
        )
    }
}

@Composable
private fun ReorderableCollectionItemScope.ExerciseRowContent(
    draft: DraftExercise,
    supersetColor: Color,
    isDragging: Boolean,
    isOrganizeMode: Boolean,
    isReorderMode: Boolean,
    blockType: BlockType?,
    selectedExerciseIds: Set<Long>,
    onLongPress: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onDragStopped: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onIncrementReps: () -> Unit = {},
    onDecrementReps: () -> Unit = {},
    onIncrementHold: () -> Unit = {},
    onDecrementHold: () -> Unit = {},
    onToggleInputMode: () -> Unit = {},
    onWeightChanged: (String) -> Unit = {}
) {
    val isFunctionalBlock = blockType != null && blockType != BlockType.STRENGTH
    when {
        isOrganizeMode -> TemplateSupersetSelectRow(
            draft = draft,
            supersetColor = supersetColor,
            isSelected = draft.exerciseId in selectedExerciseIds,
            onToggle = { onToggleSelection(draft.exerciseId) },
            dragHandleModifier = Modifier.draggableHandle()
        )
        isReorderMode -> CollapsedTemplateDraftRow(
            draft = draft,
            isDragging = isDragging,
            onDragStopped = onDragStopped
        )
        isFunctionalBlock -> FunctionalExerciseRow(
            draft = draft,
            blockType = blockType!!,
            onIncrementReps = onIncrementReps,
            onDecrementReps = onDecrementReps,
            onIncrementHold = onIncrementHold,
            onDecrementHold = onDecrementHold,
            onToggleInputMode = onToggleInputMode,
            onWeightChanged = onWeightChanged,
            onRemove = onRemove
        )
        else -> DraftExerciseRow(
            draft = draft,
            supersetColor = supersetColor,
            onLongPress = onLongPress,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
            onRemove = onRemove
        )
    }
}

private fun formatHoldSeconds(sec: Int): String = when {
    sec < 60 -> "${sec}s"
    sec % 60 == 0 -> "${sec / 60}min"
    else -> "${sec / 60}m ${sec % 60}s"
}

@Composable
private fun FunctionalExerciseRow(
    draft: DraftExercise,
    blockType: BlockType,
    onIncrementReps: () -> Unit,
    onDecrementReps: () -> Unit,
    onIncrementHold: () -> Unit,
    onDecrementHold: () -> Unit,
    onToggleInputMode: () -> Unit,
    onWeightChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    val isTimeMode = draft.holdSeconds != null
    // Only AMRAP and RFT blocks show the [Reps][Time] toggle
    val showToggle = blockType == BlockType.AMRAP || blockType == BlockType.RFT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exercise name + muscle chip
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.exerciseName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            SuggestionChip(
                onClick = {},
                label = { Text(draft.muscleGroup, fontSize = 11.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = TimerGreen.copy(alpha = 0.12f),
                    labelColor = TimerGreen
                )
            )
        }

        // Input controls
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Weight field
            OutlinedTextField(
                value = draft.defaultWeight,
                onValueChange = onWeightChanged,
                label = { Text("Weight", fontSize = 10.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.width(88.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )
            if (showToggle) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !isTimeMode,
                        onClick = { if (isTimeMode) onToggleInputMode() },
                        label = { Text("Reps", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = isTimeMode,
                        onClick = { if (!isTimeMode) onToggleInputMode() },
                        label = { Text("Time", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = if (isTimeMode) onDecrementHold else onDecrementReps,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = if (isTimeMode) formatHoldSeconds(draft.holdSeconds ?: 30)
                           else "${draft.reps} reps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.widthIn(min = 56.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(
                    onClick = if (isTimeMode) onIncrementHold else onIncrementReps,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Remove button
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove exercise",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BlockHeader(
    block: DraftBlock,
    onAddExercise: () -> Unit,
    onDeleteBlock: () -> Unit,
    modifier: Modifier = Modifier,
    standalone: Boolean = true,
    dragHandleModifier: Modifier? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val paramSummary = when (block.type) {
        BlockType.AMRAP -> block.durationSeconds?.let { "${it / 60}min" } ?: ""
        BlockType.RFT -> buildString {
            append("${block.targetRounds ?: 0} rounds")
            block.durationSeconds?.let { append(" · ${it / 60}min cap") }
        }
        BlockType.EMOM -> buildString {
            block.durationSeconds?.let { append("${it / 60}min") }
            block.emomRoundSeconds?.let { sec ->
                val label = when {
                    sec <= 60 -> "60s/round"
                    sec % 60 == 0 -> "${sec / 60}min/round"
                    else -> "${sec}s/round"
                }
                append(" · $label")
            }
        }
        BlockType.TABATA -> buildString {
            append("${block.tabataWorkSeconds ?: 20}s / ${block.tabataRestSeconds ?: 10}s")
            block.targetRounds?.let { append(" × $it") }
        }
        BlockType.STRENGTH -> ""
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (standalone) 12.dp else 8.dp, bottom = 4.dp),
        color = if (standalone) MaterialTheme.colorScheme.background else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle (functional blocks only)
            if (dragHandleModifier != null) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder block",
                    modifier = dragHandleModifier.size(20.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            // Type badge
            if (block.type != BlockType.STRENGTH) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            block.type.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TimerGreen
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = TimerGreen.copy(alpha = 0.12f)
                    )
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (paramSummary.isNotBlank()) {
                    Text(
                        text = paramSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Block options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add exercise to block") },
                        onClick = {
                            showMenu = false
                            onAddExercise()
                        },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete block", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteBlock()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DraftExerciseRow(
    draft: DraftExercise,
    supersetColor: Color = Color.Transparent,
    onLongPress: () -> Unit = {},
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = PowerMeDefaults.subtleCardElevation()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Superset spine
            if (draft.supersetGroupId != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(supersetColor)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.exerciseName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(draft.muscleGroup, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Sets stepper
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease sets",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${draft.sets} sets",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(min = 56.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase sets",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Remove button
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.CollapsedTemplateDraftRow(
    draft: DraftExercise,
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
                text = draft.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SuggestionChip(
                onClick = {},
                label = { Text(draft.muscleGroup, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.TemplateSupersetSelectRow(
    draft: DraftExercise,
    supersetColor: Color = Color.Transparent,
    isSelected: Boolean,
    onToggle: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = PowerMeDefaults.subtleCardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragHandleModifier,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(draft.muscleGroup, fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (draft.supersetGroupId != null) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "In superset",
                    tint = supersetColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
