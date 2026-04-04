package com.powerme.app.ui.workouts

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.database.RoutineExerciseWithName
import com.powerme.app.data.database.workingSets

@Composable
fun WorkoutsScreen(
    onStartWorkout: (routineId: Long) -> Unit,
    isWorkoutActive: Boolean = false,
    onResumeWorkout: () -> Unit = {},
    onCreateRoutine: () -> Unit = {},
    onEditRoutine: (Long) -> Unit = {},
    viewModel: WorkoutsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeRoutines by viewModel.activeRoutines.collectAsState()
    val archivedRoutines by viewModel.archivedRoutines.collectAsState()
    val routineDetails by viewModel.routineDetails.collectAsState()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var selectedRoutine by remember { mutableStateOf<RoutineWithSummary?>(null) }
    var pendingExportId by remember { mutableStateOf<Long?>(null) }

    // Fire Android share sheet once routine details are loaded for an export request
    LaunchedEffect(routineDetails) {
        val exportId = pendingExportId ?: return@LaunchedEffect
        if (routineDetails.isNotEmpty()) {
            val routineName = (activeRoutines + archivedRoutines)
                .find { it.routine.id == exportId }?.routine?.name ?: "Routine"
            val text = routineDetails.joinToString("\n") { "${it.workingSets}×${it.reps} ${it.exerciseName}" }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, routineName)
            }
            context.startActivity(Intent.createChooser(intent, "Export Routine"))
            pendingExportId = null
            viewModel.clearRoutineDetails()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            item {
                Text(
                    text = "Workouts",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (isWorkoutActive) {
                item {
                    Button(
                        onClick = onResumeWorkout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text("Resume Workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onStartWorkout(0L) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Start Empty Workout",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Routines header row: label + "Show Archived" FilterChip + Add button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Routines",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = showArchived,
                        onClick = { showArchived = !showArchived },
                        label = { Text("Archived", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onCreateRoutine() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Routine",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (!showArchived) {
                // Active routines
                if (activeRoutines.isEmpty()) {
                    item {
                        Text(
                            text = "No routines yet — ask the War Room to build one",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    items(activeRoutines.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { summary ->
                                RoutineCard(
                                    summary = summary,
                                    modifier = Modifier.weight(1f),
                                    isWorkoutActive = isWorkoutActive,
                                    onCardClick = {
                                        selectedRoutine = summary
                                        viewModel.loadRoutineDetails(summary.routine.id)
                                    },
                                    onEdit = {
                                        onEditRoutine(summary.routine.id)
                                    },
                                    onRename = { newName -> viewModel.renameRoutine(summary.routine, newName) },
                                    onDuplicate = { viewModel.duplicateRoutine(summary.routine) },
                                    onExpress = { viewModel.createExpressRoutine(summary.routine) },
                                    onExportText = {
                                        viewModel.clearRoutineDetails()
                                        pendingExportId = summary.routine.id
                                        viewModel.loadRoutineDetails(summary.routine.id)
                                    },
                                    onArchive = { viewModel.archiveRoutine(summary.routine) },
                                    onUnarchive = { viewModel.unarchiveRoutine(summary.routine) },
                                    onDelete = { viewModel.deleteRoutine(summary.routine) }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Archived routines
                if (archivedRoutines.isEmpty()) {
                    item {
                        Text(
                            text = "No archived routines",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    items(archivedRoutines.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { summary ->
                                RoutineCard(
                                    summary = summary,
                                    modifier = Modifier.weight(1f),
                                    isWorkoutActive = isWorkoutActive,
                                    onCardClick = {
                                        selectedRoutine = summary
                                        viewModel.loadRoutineDetails(summary.routine.id)
                                    },
                                    onEdit = { onEditRoutine(summary.routine.id) },
                                    onRename = { newName -> viewModel.renameRoutine(summary.routine, newName) },
                                    onDuplicate = { viewModel.duplicateRoutine(summary.routine) },
                                    onExpress = { viewModel.createExpressRoutine(summary.routine) },
                                    onExportText = {
                                        viewModel.clearRoutineDetails()
                                        pendingExportId = summary.routine.id
                                        viewModel.loadRoutineDetails(summary.routine.id)
                                    },
                                    onArchive = { viewModel.archiveRoutine(summary.routine) },
                                    onUnarchive = { viewModel.unarchiveRoutine(summary.routine) },
                                    onDelete = { viewModel.deleteRoutine(summary.routine) }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    selectedRoutine?.let { summary ->
        RoutineOverviewSheet(
            summary = summary,
            exerciseDetails = routineDetails,
            isWorkoutActive = isWorkoutActive,
            onStartWorkout = {
                selectedRoutine = null
                viewModel.clearRoutineDetails()
                onStartWorkout(summary.routine.id)
            },
            onEdit = {
                selectedRoutine = null
                viewModel.clearRoutineDetails()
                onEditRoutine(summary.routine.id)
            },
            onRename = { newName ->
                viewModel.renameRoutine(summary.routine, newName)
            },
            onDuplicate = {
                viewModel.duplicateRoutine(summary.routine)
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            },
            onExpress = {
                viewModel.createExpressRoutine(summary.routine)
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            },
            onExportText = {
                // exerciseDetails already loaded in the sheet — fire immediately
                val text = routineDetails.joinToString("\n") { "${it.workingSets}×${it.reps} ${it.exerciseName}" }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, summary.routine.name)
                }
                context.startActivity(Intent.createChooser(intent, "Export Routine"))
            },
            onArchive = {
                viewModel.archiveRoutine(summary.routine)
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            },
            onUnarchive = {
                viewModel.unarchiveRoutine(summary.routine)
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            },
            onDelete = {
                viewModel.deleteRoutine(summary.routine)
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            },
            onDismiss = {
                selectedRoutine = null
                viewModel.clearRoutineDetails()
            }
        )
    }
}

@Composable
private fun RoutineCard(
    summary: RoutineWithSummary,
    modifier: Modifier = Modifier,
    isWorkoutActive: Boolean,
    onCardClick: () -> Unit,
    onEdit: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onExpress: () -> Unit,
    onExportText: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val exerciseSummary = if (summary.exerciseNames.isEmpty()) {
        "No exercises"
    } else {
        val displayed = summary.exerciseNames.take(3)
        val label = displayed.joinToString(", ") { "• $it" }
        if (summary.exerciseNames.size > 3) "$label, +" else label
    }

    val recencyLabel = when (summary.daysSincePerformed) {
        null -> "Never"
        0 -> "Today"
        1 -> "Yesterday"
        else -> "${summary.daysSincePerformed}d ago"
    }

    Card(
        modifier = modifier.clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.routine.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Routine options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 1. Edit
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit",
                                    color = if (isWorkoutActive)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { if (!isWorkoutActive) { showMenu = false; onEdit() } },
                            enabled = !isWorkoutActive
                        )
                        // 2. Rename
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                        // 3. Duplicate
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = { showMenu = false; onDuplicate() }
                        )
                        // 4. Create Express
                        DropdownMenuItem(
                            text = { Text("Create Express") },
                            onClick = { showMenu = false; onExpress() }
                        )
                        // 5. Export to Text
                        DropdownMenuItem(
                            text = { Text("Export to Text") },
                            onClick = { showMenu = false; onExportText() }
                        )
                        // 6. Archive / Unarchive
                        DropdownMenuItem(
                            text = { Text(if (summary.routine.isArchived) "Unarchive" else "Archive") },
                            onClick = {
                                showMenu = false
                                if (summary.routine.isArchived) onUnarchive() else onArchive()
                            }
                        )
                        // 7. Delete
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showDeleteConfirm = true }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = exerciseSummary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = recencyLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }

    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(summary.routine.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Routine") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Routine name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRename(renameText.trim())
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Routine") },
            text = { Text("Delete \"${summary.routine.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineOverviewSheet(
    summary: RoutineWithSummary,
    exerciseDetails: List<RoutineExerciseWithName>,
    isWorkoutActive: Boolean,
    onStartWorkout: () -> Unit,
    onEdit: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onExpress: () -> Unit,
    onExportText: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val recencyLabel = when (summary.daysSincePerformed) {
        null -> "Never"
        0 -> "Today"
        1 -> "Yesterday"
        else -> "${summary.daysSincePerformed} days ago"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header: [✕] [Routine name] [⋯]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = summary.routine.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Routine options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // 1. Edit
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Edit",
                                    color = if (isWorkoutActive)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { if (!isWorkoutActive) { showMenu = false; onEdit() } },
                            enabled = !isWorkoutActive
                        )
                        // 2. Rename
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                        // 3. Duplicate
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = { showMenu = false; onDuplicate() }
                        )
                        // 4. Create Express
                        DropdownMenuItem(
                            text = { Text("Create Express") },
                            onClick = { showMenu = false; onExpress() }
                        )
                        // 5. Export to Text (disabled while details are loading)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Export to Text",
                                    color = if (exerciseDetails.isEmpty())
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = { if (exerciseDetails.isNotEmpty()) { showMenu = false; onExportText() } },
                            enabled = exerciseDetails.isNotEmpty()
                        )
                        // 6. Archive / Unarchive
                        DropdownMenuItem(
                            text = { Text(if (summary.routine.isArchived) "Unarchive" else "Archive") },
                            onClick = {
                                showMenu = false
                                if (summary.routine.isArchived) onUnarchive() else onArchive()
                            }
                        )
                        // 7. Delete
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showDeleteConfirm = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Last Performed: $recencyLabel",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Exercise rows
            if (exerciseDetails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                exerciseDetails.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${ex.workingSets} × ${ex.exerciseName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ex.muscleGroup,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start Workout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(summary.routine.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Routine") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Routine name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRename(renameText.trim())
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Routine") },
            text = { Text("Delete \"${summary.routine.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
