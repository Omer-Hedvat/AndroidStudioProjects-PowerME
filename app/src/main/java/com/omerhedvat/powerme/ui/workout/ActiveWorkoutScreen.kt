package com.omerhedvat.powerme.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayCircle
import com.omerhedvat.powerme.ui.components.MagicAddDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.Color
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.database.ExerciseType
import com.omerhedvat.powerme.ui.chat.MedicalRestrictionsDoc
import com.omerhedvat.powerme.ui.components.YouTubePlayerBottomSheet
import com.omerhedvat.powerme.ui.theme.CyberLime
import com.omerhedvat.powerme.ui.theme.OledBlack
import com.omerhedvat.powerme.ui.theme.SlateGrey
import com.omerhedvat.powerme.util.PlateCalculator

@Composable
fun ActiveWorkoutScreen(
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val medicalDoc by viewModel.medicalDoc.collectAsState()
    var showExerciseDialog by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Keep screen on during active workout
    DisposableEffect(workoutState.isActive) {
        if (workoutState.isActive) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = OledBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (!workoutState.isActive) {
                // Start Workout Screen
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.startWorkout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime,
                            contentColor = OledBlack
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
            } else {
                // Rest Timer Bar
                if (workoutState.restTimer.isActive) {
                    RestTimerBar(
                        restTimer = workoutState.restTimer,
                        onSkip = { viewModel.skipRestTimer() },
                        onAddTime = { viewModel.addTimeToTimer(30) }
                    )
                }

                // Active Workout Screen
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "ACTIVE WORKOUT",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Warmup section
                    if (!workoutState.warmupCompleted && workoutState.exercises.isEmpty()) {
                        item {
                            Button(
                                onClick = { viewModel.requestWarmup() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberLime,
                                    contentColor = OledBlack
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !workoutState.isLoadingWarmup
                            ) {
                                if (workoutState.isLoadingWarmup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = OledBlack,
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
                                    containerColor = CyberLime.copy(alpha = 0.2f)
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
                                        color = CyberLime
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    items(workoutState.exercises) { exerciseWithSets ->
                        ExerciseCard(
                            exerciseWithSets = exerciseWithSets,
                            medicalDoc = medicalDoc,
                            onAddSet = { viewModel.addSet(exerciseWithSets.exercise.id) },
                            onUpdateSet = { setOrder, weight, reps, rpe, wasCompleted ->
                                viewModel.updateSet(
                                    exerciseWithSets.exercise.id,
                                    setOrder,
                                    weight,
                                    reps,
                                    rpe
                                )
                                // Start rest timer if set just became completed
                                if (!wasCompleted && weight.isNotBlank() && reps.isNotBlank()) {
                                    viewModel.startRestTimer(exerciseWithSets.exercise.id)
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
                                    viewModel.startRestTimer(exerciseWithSets.exercise.id)
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
                                    viewModel.startRestTimer(exerciseWithSets.exercise.id)
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
                            }
                        )
                    }

                    item {
                        Button(
                            onClick = { showExerciseDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SlateGrey,
                                contentColor = CyberLime
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Exercise")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Exercise")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.cancelWorkout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateGrey,
                            contentColor = CyberLime
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.finishWorkout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime,
                            contentColor = OledBlack
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "FINISH WORKOUT",
                            fontWeight = FontWeight.Bold
                        )
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

@Composable
fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    medicalDoc: MedicalRestrictionsDoc?,
    onAddSet: () -> Unit,
    onUpdateSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateCardioSet: (Int, String, String, String, Boolean) -> Unit,
    onUpdateTimedSet: (Int, String, String, Boolean) -> Unit,
    onDeleteSet: (Int) -> Unit,
    onUpdateSetupNotes: (String) -> Unit,
    onUpdateSetNotes: (Int, String) -> Unit
) {
    var showPlateCalculator by remember { mutableStateOf(false) }
    var showSetupNotesEditor by remember { mutableStateOf(false) }
    var setupNotesText by remember { mutableStateOf(exerciseWithSets.exercise.setupNotes ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SlateGrey
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseWithSets.exercise.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberLime
                    )
                    Text(
                        text = exerciseWithSets.exercise.muscleGroup,
                        fontSize = 14.sp,
                        color = CyberLime.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { showSetupNotesEditor = !showSetupNotesEditor }) {
                    Icon(
                        Icons.Default.Notes,
                        contentDescription = "Setup Notes",
                        tint = if (exerciseWithSets.exercise.setupNotes != null) CyberLime else CyberLime.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { showPlateCalculator = !showPlateCalculator }) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = "Plate Calculator",
                        tint = CyberLime
                    )
                }
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
                        containerColor = CyberLime.copy(alpha = 0.2f)
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
                            color = CyberLime
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exerciseWithSets.exercise.setupNotes!!,
                            fontSize = 14.sp,
                            color = CyberLime,
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
                        containerColor = OledBlack
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
                            color = CyberLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = setupNotesText,
                            onValueChange = { setupNotesText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., Chest up, elbows at 45°, drive through heels") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberLime,
                                unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                                focusedTextColor = CyberLime,
                                unfocusedTextColor = CyberLime,
                                unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                                focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
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
                                    containerColor = SlateGrey,
                                    contentColor = CyberLime
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
                                    containerColor = CyberLime,
                                    contentColor = OledBlack
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

            // Plate Calculator Display
            if (showPlateCalculator) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = OledBlack
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Plate Calculator",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        exerciseWithSets.sets.filter { it.isCompleted }.forEach { set ->
                            val weight = set.weight.toDoubleOrNull()
                            if (weight != null && weight > 0) {
                                val breakdown = PlateCalculator.calculatePlates(
                                    totalWeight = weight,
                                    barType = exerciseWithSets.exercise.barType,
                                    availablePlates = PlateCalculator.parseAvailablePlates("0.5,1.25,2.5,5,10,15,20,25")
                                )

                                if (breakdown != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Set ${set.setOrder}: ${weight}kg",
                                            fontSize = 12.sp,
                                            color = CyberLime
                                        )
                                        Text(
                                            text = PlateCalculator.formatPlateBreakdown(breakdown),
                                            fontSize = 12.sp,
                                            color = CyberLime.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Set headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Set", fontSize = 12.sp, color = CyberLime, modifier = Modifier.width(40.dp))
                when (exerciseWithSets.exercise.exerciseType) {
                    ExerciseType.CARDIO -> {
                        Text("Dist(km)", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                        Text("Time(s)", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                        Text("Pace", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                    }
                    ExerciseType.TIMED -> {
                        Text("Time(s)", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1.5f))
                        Text("RPE", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                    }
                    else -> {
                        Text("Weight", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                        Text("Reps", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                        Text("RPE", fontSize = 12.sp, color = CyberLime, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.width(80.dp))
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
                        SetRow(
                            set = set,
                            onUpdateSet = { setOrder, weight, reps, rpe ->
                                onUpdateSet(setOrder, weight, reps, rpe, set.isCompleted)
                            },
                            onDeleteSet = { onDeleteSet(set.setOrder) },
                            onUpdateSetNotes = { notes ->
                                onUpdateSetNotes(set.setOrder, notes)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = onAddSet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledBlack,
                    contentColor = CyberLime
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Set")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Set")
            }
        }
    }
}

@Composable
fun SetRow(
    set: ActiveSet,
    onUpdateSet: (Int, String, String, String) -> Unit,
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
                color = CyberLime,
                modifier = Modifier.width(40.dp)
            )

        OutlinedTextField(
            value = set.weight,
            onValueChange = { weight ->
                onUpdateSet(set.setOrder, weight, set.reps, set.rpe)
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberLime,
                unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                focusedTextColor = CyberLime,
                unfocusedTextColor = CyberLime,
                unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostWeight?.let { ghost ->
                { Text(ghost, color = CyberLime.copy(alpha = 0.3f)) }
            },
            singleLine = true
        )

        OutlinedTextField(
            value = set.reps,
            onValueChange = { reps ->
                onUpdateSet(set.setOrder, set.weight, reps, set.rpe)
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberLime,
                unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                focusedTextColor = CyberLime,
                unfocusedTextColor = CyberLime,
                unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostReps?.let { ghost ->
                { Text(ghost, color = CyberLime.copy(alpha = 0.3f)) }
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
                focusedBorderColor = CyberLime,
                unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                focusedTextColor = CyberLime,
                unfocusedTextColor = CyberLime,
                unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
            ),
            placeholder = set.ghostRpe?.let { ghost ->
                { Text(ghost, color = CyberLime.copy(alpha = 0.3f)) }
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
                    tint = if (set.setNotes.isNotBlank()) CyberLime else CyberLime.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = CyberLime
                )
            }
        }

        // Display set notes if present
        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = CyberLime.copy(alpha = 0.7f),
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
                    containerColor = SlateGrey
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
                        color = CyberLime
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Felt strong, used belt, slight discomfort") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberLime,
                            unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                            focusedTextColor = CyberLime,
                            unfocusedTextColor = CyberLime,
                            unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                            focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
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
                                containerColor = SlateGrey.copy(alpha = 0.5f),
                                contentColor = CyberLime
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
                                containerColor = CyberLime,
                                contentColor = OledBlack
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
                color = CyberLime,
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
                    focusedBorderColor = CyberLime,
                    unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                    focusedTextColor = CyberLime,
                    unfocusedTextColor = CyberLime
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
                    focusedBorderColor = CyberLime,
                    unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                    focusedTextColor = CyberLime,
                    unfocusedTextColor = CyberLime
                ),
                singleLine = true
            )

            Text(
                text = pace,
                fontSize = 14.sp,
                color = CyberLime,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { showNotesDialog = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Set Notes",
                    tint = if (set.setNotes.isNotBlank()) CyberLime else CyberLime.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = CyberLime
                )
            }
        }

        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = CyberLime.copy(alpha = 0.7f),
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
                    containerColor = SlateGrey
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
                        color = CyberLime
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Felt strong, good pace") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberLime,
                            unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                            focusedTextColor = CyberLime,
                            unfocusedTextColor = CyberLime,
                            unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                            focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
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
                                containerColor = SlateGrey.copy(alpha = 0.5f),
                                contentColor = CyberLime
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
                                containerColor = CyberLime,
                                contentColor = OledBlack
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
                color = CyberLime,
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
                    focusedBorderColor = CyberLime,
                    unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                    focusedTextColor = CyberLime,
                    unfocusedTextColor = CyberLime
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
                    focusedBorderColor = CyberLime,
                    unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                    focusedTextColor = CyberLime,
                    unfocusedTextColor = CyberLime
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
                    tint = if (set.setNotes.isNotBlank()) CyberLime else CyberLime.copy(alpha = 0.3f)
                )
            }

            IconButton(
                onClick = onDeleteSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Set",
                    tint = CyberLime
                )
            }
        }

        if (set.setNotes.isNotBlank()) {
            Text(
                text = "Note: ${set.setNotes}",
                fontSize = 12.sp,
                color = CyberLime.copy(alpha = 0.7f),
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
                    containerColor = SlateGrey
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
                        color = CyberLime
                    )

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Held until failure") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberLime,
                            unfocusedBorderColor = CyberLime.copy(alpha = 0.5f),
                            focusedTextColor = CyberLime,
                            unfocusedTextColor = CyberLime,
                            unfocusedPlaceholderColor = CyberLime.copy(alpha = 0.3f),
                            focusedPlaceholderColor = CyberLime.copy(alpha = 0.3f)
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
                                containerColor = SlateGrey.copy(alpha = 0.5f),
                                contentColor = CyberLime
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
                                containerColor = CyberLime,
                                contentColor = OledBlack
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
                containerColor = SlateGrey
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
                        color = CyberLime
                    )
                    Button(
                        onClick = { showMagicAdd = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime.copy(alpha = 0.15f),
                            contentColor = CyberLime
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
                                containerColor = OledBlack
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
                                        color = CyberLime
                                    )
                                    Text(
                                        text = exercise.muscleGroup,
                                        fontSize = 14.sp,
                                        color = CyberLime.copy(alpha = 0.7f)
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
                                            tint = CyberLime,
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
            containerColor = SlateGrey
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
                        color = CyberLime
                    )
                    Text(
                        text = "Noa & Coach Carter",
                        fontSize = 12.sp,
                        color = CyberLime.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Dismiss",
                        tint = CyberLime
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reasoning
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = OledBlack
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
                        color = CyberLime
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = prescription.reasoning,
                        fontSize = 12.sp,
                        color = CyberLime.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exercises
            prescription.exercises.forEachIndexed { index, exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = OledBlack
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
                                color = CyberLime,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = exercise.duration?.let { "${it}s" }
                                    ?: exercise.reps?.let { "${it} reps" }
                                    ?: "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberLime
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target: ${exercise.targetJoint}",
                            fontSize = 11.sp,
                            color = CyberLime.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = exercise.instructions,
                            fontSize = 12.sp,
                            color = CyberLime.copy(alpha = 0.8f)
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
                    containerColor = CyberLime.copy(alpha = 0.2f)
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
                        color = CyberLime
                    )
                    Text(
                        text = prescription.noaaNote,
                        fontSize = 11.sp,
                        color = CyberLime.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Coach Carter:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberLime
                    )
                    Text(
                        text = prescription.carterNote,
                        fontSize = 11.sp,
                        color = CyberLime.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log as Performed Button
            Button(
                onClick = onLogAsPerformed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberLime,
                    contentColor = OledBlack
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

@Composable
fun RestTimerBar(
    restTimer: com.omerhedvat.powerme.ui.workout.RestTimerState,
    onSkip: () -> Unit,
    onAddTime: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CyberLime
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
                    color = OledBlack
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddTime,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledBlack,
                            contentColor = CyberLime
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("+30s", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateGrey,
                            contentColor = CyberLime
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
                color = OledBlack,
                trackColor = CyberLime.copy(alpha = 0.3f)
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
                containerColor = Color(0xFFB00020).copy(alpha = 0.15f)
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
                        color = Color(0xFFFF6B6B)
                    )
                    Text(
                        text = "This exercise is on your RED list. Consider an alternative.",
                        fontSize = 12.sp,
                        color = Color(0xFFFF6B6B).copy(alpha = 0.85f),
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
                containerColor = Color(0xFFFFA000).copy(alpha = 0.12f)
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
                        color = Color(0xFFFFC107)
                    )
                    Text(
                        text = yellowEntry.requiredCue,
                        fontSize = 12.sp,
                        color = Color(0xFFFFC107).copy(alpha = 0.9f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
