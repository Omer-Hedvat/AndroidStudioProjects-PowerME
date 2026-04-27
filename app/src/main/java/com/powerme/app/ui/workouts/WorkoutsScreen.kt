package com.powerme.app.ui.workouts

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.BlockType
import com.powerme.app.data.database.RoutineBlock
import com.powerme.app.data.database.RoutineExerciseWithName
import com.powerme.app.data.database.workingSets
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.buildSupersetColorMap

@Composable
fun WorkoutsScreen(
    onStartWorkout: (routineId: String) -> Unit,
    isWorkoutActive: Boolean = false,
    onResumeWorkout: () -> Unit = {},
    onCreateRoutine: () -> Unit = {},
    onEditRoutine: (String) -> Unit = {},
    onQuickStart: () -> Unit = {},
    viewModel: WorkoutsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val visibleRoutines by viewModel.visibleRoutines.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val routineDetails by viewModel.routineDetails.collectAsState()
    val routineBlocks by viewModel.routineBlocks.collectAsState()
    var selectedRoutine by remember { mutableStateOf<RoutineWithSummary?>(null) }
    var pendingExportId by remember { mutableStateOf<String?>(null) }

    // Fire Android share sheet once routine details are loaded for an export request
    LaunchedEffect(routineDetails) {
        val exportId = pendingExportId ?: return@LaunchedEffect
        if (routineDetails.isNotEmpty()) {
            val routineName = visibleRoutines
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
                    onClick = onQuickStart,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "Quick Start",
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
                        onClick = { viewModel.toggleShowArchived() },
                        label = { Text("Show Archived", fontSize = 12.sp) }
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

            if (visibleRoutines.isEmpty()) {
                item {
                    Text(
                        text = if (showArchived) "No archived routines" else "No routines yet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                items(visibleRoutines.chunked(2)) { row ->
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

    selectedRoutine?.let { summary ->
        RoutineOverviewSheet(
            summary = summary,
            exerciseDetails = routineDetails,
            blocks = routineBlocks,
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
        elevation = PowerMeDefaults.cardElevation()
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
    blocks: List<RoutineBlock> = emptyList(),
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
    val supersetColorMap = remember(exerciseDetails) {
        buildSupersetColorMap(exerciseDetails.map { it.supersetGroupId })
    }

    val recencyLabel = when (summary.daysSincePerformed) {
        null -> "Never"
        0 -> "Today"
        1 -> "Yesterday"
        else -> "${summary.daysSincePerformed} days ago"
    }

    val sections = remember(exerciseDetails, blocks) { buildRoutineSections(exerciseDetails, blocks) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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
                sections.forEach { section ->
                    val block = section.block
                    val blockType = block?.let { runCatching { BlockType.valueOf(it.type) }.getOrNull() }
                    val isFunctional = blockType != null && blockType != BlockType.STRENGTH

                    if (isFunctional && block != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column {
                                RoutineBlockPreviewHeader(block = block, blockType = blockType)
                                section.exercises.forEachIndexed { index, ex ->
                                    if (index > 0) HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${ex.workingSets} × ${ex.exerciseName}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = ex.muscleGroup,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        section.exercises.forEach { ex ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                if (ex.supersetGroupId != null) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .fillMaxHeight()
                                            .background(supersetColorMap[ex.supersetGroupId] ?: Color.Transparent)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(
                                            start = if (ex.supersetGroupId != null) 8.dp else 0.dp,
                                            top = 10.dp,
                                            bottom = 10.dp
                                        ),
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
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                        }
                    }
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

private data class RoutineSection(
    val block: RoutineBlock?,
    val exercises: List<RoutineExerciseWithName>
)

private fun buildRoutineSections(
    exercises: List<RoutineExerciseWithName>,
    blocks: List<RoutineBlock>
): List<RoutineSection> {
    if (blocks.isEmpty()) return if (exercises.isEmpty()) emptyList() else listOf(RoutineSection(null, exercises))
    val byBlockId = exercises.groupBy { it.blockId }
    val sections = mutableListOf<RoutineSection>()
    // Blocks in their defined order first (matches ActiveWorkoutScreen rendering order)
    blocks.sortedBy { it.order }.forEach { block ->
        val blockExercises = byBlockId[block.id] ?: emptyList()
        sections.add(RoutineSection(block, blockExercises))
    }
    // Unblocked exercises last (matches ActiveWorkoutScreen rendering order)
    val unblocked = byBlockId[null] ?: emptyList()
    if (unblocked.isNotEmpty()) sections.add(RoutineSection(null, unblocked))
    return sections
}

@Composable
private fun RoutineBlockPreviewHeader(block: RoutineBlock, blockType: BlockType?) {
    val paramSummary = when (blockType) {
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
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (blockType != null && blockType != BlockType.STRENGTH) {
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        blockType.displayName,
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
                text = block.name ?: blockType?.displayName ?: "",
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
    }
}
