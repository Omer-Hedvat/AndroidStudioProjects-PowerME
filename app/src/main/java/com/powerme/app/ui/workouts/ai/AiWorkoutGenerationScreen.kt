package com.powerme.app.ui.workouts.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.powerme.app.ai.DownloadState
import com.powerme.app.ui.theme.buildSupersetColorMap
import com.powerme.app.util.MatchType
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

private const val KEY_SELECTED_EXERCISES = "selected_exercises"

private fun createCameraUri(context: android.content.Context): Uri {
    val file = File(context.cacheDir, "ai_workout_photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiWorkoutGenerationScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onStartWorkout: (AiWorkoutEvent.WorkoutStarted) -> Unit,
    onPickExercise: () -> Unit,
    initialMode: InputMode = InputMode.TEXT,
    viewModel: AiWorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.events.collectAsState()
    val synonymPrompt by viewModel.synonymPrompt.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.setInputMode(initialMode) }

    // Index of the exercise card waiting for a picker result
    var pickingForIndex by remember { mutableStateOf<Int?>(null) }
    var pickingForReplace by remember { mutableStateOf(false) }

    // Observe selected exercises passed back from the exercise picker
    val backEntry = navController.currentBackStackEntry
    val selectedIds by (backEntry
        ?.savedStateHandle
        ?.getStateFlow<ArrayList<Long>?>(KEY_SELECTED_EXERCISES, null)
        ?.collectAsState() ?: remember { mutableStateOf(null) })

    LaunchedEffect(selectedIds) {
        val ids = selectedIds ?: return@LaunchedEffect
        val idx = pickingForIndex
        if (ids.isNotEmpty() && idx != null) {
            if (pickingForReplace) {
                viewModel.replaceExerciseById(idx, ids.first())
            } else {
                viewModel.swapExerciseById(idx, ids.first())
            }
            pickingForIndex = null
            pickingForReplace = false
            backEntry?.savedStateHandle?.remove<ArrayList<Long>>(KEY_SELECTED_EXERCISES)
        }
    }

    // Consume navigation events
    LaunchedEffect(event) {
        when (val e = event) {
            is AiWorkoutEvent.WorkoutStarted -> {
                viewModel.consumeEvent()
                onStartWorkout(e)
            }
            is AiWorkoutEvent.RoutineSaved -> {
                viewModel.consumeEvent()
                onNavigateBack()
            }
            null -> Unit
        }
    }

    // Show synonym save snackbar when user manually picks for any row
    LaunchedEffect(synonymPrompt) {
        val prompt = synonymPrompt ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Always match \"${prompt.rawName}\" → \"${prompt.exerciseName}\"?",
            actionLabel = "Save",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.saveSynonym()
        } else {
            viewModel.dismissSynonymPrompt()
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { viewModel.processPhoto(it, context) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.processPhoto(it, context) }
    }

    var showSaveRoutineDialog by remember { mutableStateOf(false) }

    if (showSaveRoutineDialog) {
        SaveRoutineDialog(
            onDismiss = { showSaveRoutineDialog = false },
            onConfirm = { name ->
                showSaveRoutineDialog = false
                viewModel.saveAsRoutine(name)
            }
        )
    }

    // Management sheet
    val sheetIndex = uiState.managementSheetIndex
    if (sheetIndex != null) {
        val exercise = uiState.previewExercises.getOrNull(sheetIndex)
        if (exercise != null) {
            PreviewManagementSheet(
                exercise = exercise,
                onDismiss = { viewModel.closeManagementSheet() },
                onReorder = {
                    viewModel.closeManagementSheet()
                    viewModel.enterOrganizeMode()
                },
                onSuperset = {
                    viewModel.closeManagementSheet()
                    if (exercise.supersetGroupId != null) {
                        viewModel.dissolveSuperset(sheetIndex)
                    } else {
                        viewModel.enterSupersetSelectMode(sheetIndex)
                    }
                },
                onReplace = {
                    viewModel.closeManagementSheet()
                    pickingForIndex = sheetIndex
                    pickingForReplace = true
                    onPickExercise()
                },
                onSetRestTime = {
                    viewModel.closeManagementSheet()
                    viewModel.openRestTimeDialog(sheetIndex)
                },
                onNotes = {
                    viewModel.closeManagementSheet()
                    viewModel.openNotesDialog(sheetIndex)
                }
            )
        }
    }

    // Rest time dialog
    val restDialogIdx = uiState.restTimeDialogIndex
    if (restDialogIdx != null) {
        val exercise = uiState.previewExercises.getOrNull(restDialogIdx)
        if (exercise != null) {
            RestTimeDialog(
                currentSeconds = exercise.restSeconds,
                onDismiss = { viewModel.closeRestTimeDialog() },
                onConfirm = { seconds ->
                    viewModel.setRestTime(restDialogIdx, seconds)
                    viewModel.closeRestTimeDialog()
                }
            )
        }
    }

    // Notes dialog
    val notesDialogIdx = uiState.notesDialogIndex
    if (notesDialogIdx != null) {
        val exercise = uiState.previewExercises.getOrNull(notesDialogIdx)
        if (exercise != null) {
            NotesDialog(
                currentNotes = exercise.notes,
                onDismiss = { viewModel.closeNotesDialog() },
                onConfirm = { text ->
                    viewModel.setExerciseNote(notesDialogIdx, text)
                    viewModel.closeNotesDialog()
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.step == AiWorkoutStep.PREVIEW) "Review Workout" else "Generate Workout",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.step == AiWorkoutStep.PREVIEW) {
                            when {
                                uiState.isSupersetSelectMode -> viewModel.exitSupersetSelectMode()
                                uiState.isOrganizeMode -> viewModel.exitOrganizeMode()
                                else -> viewModel.goBackToInput()
                            }
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.step == AiWorkoutStep.PREVIEW && !uiState.isSupersetSelectMode) {
                        IconButton(onClick = {
                            if (uiState.isOrganizeMode) viewModel.exitOrganizeMode()
                            else viewModel.enterOrganizeMode()
                        }) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = if (uiState.isOrganizeMode) "Exit organize" else "Organize",
                                tint = if (uiState.isOrganizeMode)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val downloadState by viewModel.downloadState.collectAsState()
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DownloadBanner(downloadState = downloadState)
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.step) {
                    AiWorkoutStep.INPUT -> InputStep(
                        uiState = uiState,
                        onTextChange = viewModel::updateInputText,
                        onModeChange = viewModel::setInputMode,
                        onOcrTextChange = viewModel::updateOcrText,
                        onOpenCamera = {
                            val uri = createCameraUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onOpenGallery = { galleryLauncher.launch("image/*") },
                        onGenerate = viewModel::processTextInput
                    )
                    AiWorkoutStep.PREVIEW -> PreviewStep(
                        uiState = uiState,
                        onPickExercise = { index ->
                            pickingForIndex = index
                            pickingForReplace = false
                            onPickExercise()
                        },
                        onRemoveExercise = viewModel::removeExercise,
                        onStartWorkout = viewModel::startWorkout,
                        onSaveAsRoutine = { showSaveRoutineDialog = true },
                        onReorderExercise = viewModel::reorderExercise,
                        onOverflowClick = viewModel::openManagementSheet,
                        onToggleSupersetCandidate = viewModel::toggleSupersetCandidate,
                        onCommitSuperset = viewModel::commitSupersetSelection,
                        onCancelSupersetSelect = viewModel::exitSupersetSelectMode
                    )
                }

                if (uiState.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Analyzing your workout...", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadBanner(downloadState: DownloadState) {
    val downloading = downloadState as? DownloadState.Downloading ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (downloading.progressPercent > 0) {
            CircularProgressIndicator(
                progress = { downloading.progressPercent / 100f },
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 2.dp
            )
        }
        val label = if (downloading.progressPercent > 0) {
            "Downloading smart performance model… ${downloading.progressPercent}%"
        } else {
            "Downloading smart performance model…"
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun InputStep(
    uiState: AiWorkoutUiState,
    onTextChange: (String) -> Unit,
    onModeChange: (InputMode) -> Unit,
    onOcrTextChange: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InputMode.entries.forEach { mode ->
                val selected = uiState.inputMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onModeChange(mode) },
                    label = { Text(if (mode == InputMode.TEXT) "Text" else "Photo") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (mode == InputMode.TEXT) Icons.Default.Edit else Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        if (uiState.inputMode == InputMode.TEXT) {
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = {
                    Text(
                        "e.g. 3x8 barbell bench press, 4x5 squat, 3x12 lat pulldown\n\nor: upper body push day, 5 exercises",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                label = { Text("Workout description") },
                minLines = 6
            )
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenCamera, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Camera")
                    }
                    OutlinedButton(onClick = onOpenGallery, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }

                if (uiState.ocrText != null) {
                    Text(
                        "Extracted text (edit if needed):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = onOcrTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        minLines = 4
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Take a photo of your workout plan",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.inputText.isNotBlank()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Generate", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewStep(
    uiState: AiWorkoutUiState,
    onPickExercise: (Int) -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onStartWorkout: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onReorderExercise: (Int, Int) -> Unit,
    onOverflowClick: (Int) -> Unit,
    onToggleSupersetCandidate: (Int) -> Unit,
    onCommitSuperset: () -> Unit,
    onCancelSupersetSelect: () -> Unit
) {
    val supersetColorMap = remember(uiState.previewExercises) {
        buildSupersetColorMap(uiState.previewExercises.map { it.supersetGroupId })
    }
    val supersetLabelMap = remember(supersetColorMap) {
        supersetColorMap.keys.mapIndexed { idx, id -> id to ('A' + idx).toString() }.toMap()
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onReorderExercise(from.key as Int, to.key as Int)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Superset select mode banner
        if (uiState.isSupersetSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Select exercises to group (2–4)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancelSupersetSelect) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                val canCommit = uiState.supersetCandidateIndices.size in 2..4
                Button(
                    onClick = onCommitSuperset,
                    enabled = canCommit,
                    modifier = Modifier.padding(start = 4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Group")
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(uiState.previewExercises, key = { index, _ -> index }) { index, pe ->
                ReorderableItem(reorderState, key = index) { _ ->
                    val ssColor = pe.supersetGroupId?.let { supersetColorMap[it] } ?: Color.Transparent
                    val ssLabel = pe.supersetGroupId?.let { supersetLabelMap[it] }
                    PreviewExerciseCard(
                        previewExercise = pe,
                        isOrganizeMode = uiState.isOrganizeMode,
                        isSupersetSelectMode = uiState.isSupersetSelectMode,
                        isSelectedAsCandidate = index in uiState.supersetCandidateIndices,
                        supersetColor = ssColor,
                        supersetLabel = ssLabel,
                        onTapUnmatched = { onPickExercise(index) },
                        onRemove = { onRemoveExercise(index) },
                        onOverflowClick = { onOverflowClick(index) },
                        onSelectAsCandidate = { onToggleSupersetCandidate(index) },
                        dragHandleModifier = Modifier.draggableHandle()
                    )
                }
            }

            if (uiState.previewExercises.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No exercises generated. Go back and try a different description.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.previewExercises.any { it.matchedExercise != null }
            ) {
                Text("Start Workout", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSaveAsRoutine,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.previewExercises.any { it.matchedExercise != null }
            ) {
                Text("Save as Routine")
            }
        }
    }
}

@Composable
private fun PreviewExerciseCard(
    previewExercise: PreviewExercise,
    isOrganizeMode: Boolean,
    isSupersetSelectMode: Boolean,
    isSelectedAsCandidate: Boolean,
    supersetColor: Color,
    supersetLabel: String?,
    onTapUnmatched: () -> Unit,
    onRemove: () -> Unit,
    onOverflowClick: () -> Unit,
    onSelectAsCandidate: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val isUnmatched = previewExercise.matchType == MatchType.UNMATCHED
    val borderColor = when (previewExercise.matchType) {
        MatchType.EXACT_USER_SYNONYM, MatchType.EXACT, MatchType.SYNONYM, MatchType.MANUAL ->
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        MatchType.FUZZY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        MatchType.UNMATCHED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isUnmatched && !isSupersetSelectMode) Modifier.clickable(onClick = onTapUnmatched) else Modifier)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnmatched)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Superset left-border accent (4dp)
            if (supersetColor != Color.Transparent) {
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle (visible only in organize mode)
                if (isOrganizeMode) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = dragHandleModifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Match-type icon
                Icon(
                    imageVector = when (previewExercise.matchType) {
                        MatchType.EXACT_USER_SYNONYM, MatchType.EXACT, MatchType.SYNONYM, MatchType.MANUAL ->
                            Icons.Default.CheckCircle
                        MatchType.FUZZY -> Icons.Default.Info
                        MatchType.UNMATCHED -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when (previewExercise.matchType) {
                        MatchType.EXACT_USER_SYNONYM, MatchType.EXACT, MatchType.SYNONYM, MatchType.MANUAL ->
                            MaterialTheme.colorScheme.primary
                        MatchType.FUZZY -> MaterialTheme.colorScheme.tertiary
                        MatchType.UNMATCHED -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = previewExercise.matchedExercise?.name ?: previewExercise.originalName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (isUnmatched) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                    // Superset label
                    if (supersetLabel != null) {
                        Text(
                            text = "Superset $supersetLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    previewExercise.matchedExercise?.let {
                        Text(
                            text = it.muscleGroup,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (previewExercise.matchType == MatchType.EXACT_USER_SYNONYM) {
                        Text(
                            "Your match",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (isUnmatched) {
                        Text(
                            "Tap to select from library",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${previewExercise.sets} × ${previewExercise.reps}")
                            previewExercise.weight?.let { append("  @${it.toInt()} kg") }
                            previewExercise.restSeconds?.let { append("  · ${it}s rest") }
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    // Notes
                    previewExercise.notes?.let { note ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = note,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                }

                // Trailing: superset-select checkbox or overflow + remove
                when {
                    isSupersetSelectMode -> {
                        Checkbox(
                            checked = isSelectedAsCandidate,
                            onCheckedChange = { onSelectAsCandidate() }
                        )
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onOverflowClick, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewManagementSheet(
    exercise: PreviewExercise,
    onDismiss: () -> Unit,
    onReorder: () -> Unit,
    onSuperset: () -> Unit,
    onReplace: () -> Unit,
    onSetRestTime: () -> Unit,
    onNotes: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = exercise.matchedExercise?.name ?: exercise.originalName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            ManagementSheetRow(Icons.Default.DragHandle, "Reorder", onReorder)
            ManagementSheetRow(
                icon = if (exercise.supersetGroupId != null) Icons.Default.LinkOff else Icons.Default.Link,
                label = if (exercise.supersetGroupId != null) "Remove from Superset" else "Create Superset",
                onClick = onSuperset
            )
            ManagementSheetRow(Icons.Default.SwapHoriz, "Replace Exercise", onReplace)
            ManagementSheetRow(
                icon = Icons.Default.Timer,
                label = buildString {
                    append("Set Rest Time")
                    exercise.restSeconds?.let { append(" (${it}s)") }
                },
                onClick = onSetRestTime
            )
            ManagementSheetRow(
                icon = Icons.Default.Notes,
                label = if (exercise.notes != null) "Edit Notes" else "Add Notes",
                onClick = onNotes
            )
        }
    }
}

@Composable
private fun ManagementSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RestTimeDialog(
    currentSeconds: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentSeconds?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Rest Time", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val seconds = text.toIntOrNull()
                if (seconds != null && seconds >= 0) onConfirm(seconds)
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NotesDialog(
    currentNotes: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentNotes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exercise Notes", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Notes (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SaveRoutineDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as Routine", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
