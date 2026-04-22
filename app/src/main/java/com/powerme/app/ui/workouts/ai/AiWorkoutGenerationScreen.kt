package com.powerme.app.ui.workouts.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.powerme.app.util.MatchType
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
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.setInputMode(initialMode) }

    // Index of the exercise card waiting for a picker result
    var pickingForIndex by remember { mutableStateOf<Int?>(null) }

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
            viewModel.swapExerciseById(idx, ids.first())
            pickingForIndex = null
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

    // Camera URI holder
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { viewModel.processPhoto(it, context) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.processPhoto(it, context) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
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

    Scaffold(
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
                        if (uiState.step == AiWorkoutStep.PREVIEW) viewModel.goBackToInput()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.step) {
                AiWorkoutStep.INPUT -> InputStep(
                    uiState = uiState,
                    onTextChange = viewModel::updateInputText,
                    onModeChange = viewModel::setInputMode,
                    onOcrTextChange = viewModel::updateOcrText,
                    onOpenCamera = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            val uri = createCameraUri(context)
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenGallery = { galleryLauncher.launch("image/*") },
                    onGenerate = viewModel::processTextInput
                )
                AiWorkoutStep.PREVIEW -> PreviewStep(
                    uiState = uiState,
                    onPickExercise = { index ->
                        pickingForIndex = index
                        onPickExercise()
                    },
                    onRemoveExercise = viewModel::removeExercise,
                    onStartWorkout = viewModel::startWorkout,
                    onSaveAsRoutine = { showSaveRoutineDialog = true }
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
        // Mode toggle
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
    onSaveAsRoutine: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(uiState.previewExercises) { index, pe ->
                PreviewExerciseCard(
                    previewExercise = pe,
                    onTapUnmatched = { onPickExercise(index) },
                    onRemove = { onRemoveExercise(index) }
                )
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
    onTapUnmatched: () -> Unit,
    onRemove: () -> Unit
) {
    val isUnmatched = previewExercise.matchType == MatchType.UNMATCHED
    val borderColor = when (previewExercise.matchType) {
        MatchType.EXACT, MatchType.SYNONYM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        MatchType.FUZZY -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        MatchType.UNMATCHED -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isUnmatched) Modifier.clickable(onClick = onTapUnmatched) else Modifier)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnmatched)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (previewExercise.matchType) {
                    MatchType.EXACT, MatchType.SYNONYM -> Icons.Default.CheckCircle
                    MatchType.FUZZY -> Icons.Default.Info
                    MatchType.UNMATCHED -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = when (previewExercise.matchType) {
                    MatchType.EXACT, MatchType.SYNONYM -> MaterialTheme.colorScheme.primary
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
                previewExercise.matchedExercise?.let {
                    Text(
                        text = it.muscleGroup,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
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
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
