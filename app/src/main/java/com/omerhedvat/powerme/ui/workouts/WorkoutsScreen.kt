package com.omerhedvat.powerme.ui.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun WorkoutsScreen(
    onStartWorkout: (routineId: Long) -> Unit,
    isWorkoutActive: Boolean = false,
    onResumeWorkout: () -> Unit = {},
    viewModel: WorkoutsViewModel = hiltViewModel()
) {
    val activeRoutines by viewModel.activeRoutines.collectAsState()
    val archivedRoutines by viewModel.archivedRoutines.collectAsState()
    var showArchived by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
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
                Button(
                    onClick = { onStartWorkout(0L) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("Start Empty Workout", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(
                    text = "My Routines",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (activeRoutines.isEmpty()) {
                item {
                    Text(
                        text = "No routines yet — ask the War Room to build one",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(activeRoutines) { summary ->
                    RoutineCard(
                        summary = summary,
                        onArchive = { viewModel.archiveRoutine(summary.routine) },
                        onUnarchive = { viewModel.unarchiveRoutine(summary.routine) },
                        onDelete = { viewModel.deleteRoutine(summary.routine) },
                        onRename = { newName -> viewModel.renameRoutine(summary.routine, newName) },
                        onStartWorkout = { onStartWorkout(summary.routine.id) }
                    )
                }
            }

            if (archivedRoutines.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(
                            text = if (showArchived) "Hide Archived" else "Show Archived (${archivedRoutines.size})",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                }
                item {
                    AnimatedVisibility(visible = showArchived) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            archivedRoutines.forEach { summary ->
                                RoutineCard(
                                    summary = summary,
                                    onArchive = { viewModel.archiveRoutine(summary.routine) },
                                    onUnarchive = { viewModel.unarchiveRoutine(summary.routine) },
                                    onDelete = { viewModel.deleteRoutine(summary.routine) },
                                    onRename = { newName -> viewModel.renameRoutine(summary.routine, newName) },
                                    onStartWorkout = { onStartWorkout(summary.routine.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    summary: RoutineWithSummary,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onStartWorkout: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val exerciseSummary = if (summary.exerciseNames.isEmpty()) {
        "No exercises"
    } else {
        val displayed = summary.exerciseNames.take(3)
        val label = displayed.joinToString(", ") { "• $it" }
        if (summary.exerciseNames.size > 3) "$label, +" else label
    }

    val recencyLabel = when (summary.daysSincePerformed) {
        null -> "Never performed"
        0 -> "Last performed today"
        1 -> "Last performed yesterday"
        else -> "Last performed ${summary.daysSincePerformed} days ago"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.routine.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = exerciseSummary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recencyLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
            Button(
                onClick = onStartWorkout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text("Start", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
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
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; showRenameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (summary.routine.isArchived) "Unarchive" else "Archive") },
                        onClick = {
                            showMenu = false
                            if (summary.routine.isArchived) onUnarchive() else onArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
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
                    if (renameText.isNotBlank()) {
                        onRename(renameText.trim())
                    }
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
