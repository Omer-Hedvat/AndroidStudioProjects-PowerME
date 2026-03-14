package com.omerhedvat.powerme.ui.workouts

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateBuilderScreen(
    navController: NavController,
    viewModel: TemplateBuilderViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState()
    val draftExercises by viewModel.draftExercises.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var isReorderMode by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = draftExercises.indexOfFirst { it.exerciseId == from.key as Long }
        val toIdx   = draftExercises.indexOfFirst { it.exerciseId == to.key as Long }
        if (fromIdx >= 0 && toIdx >= 0) viewModel.reorderDraftExercise(fromIdx, toIdx)
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
            viewModel.addExercises(ids)
            backEntry?.savedStateHandle?.remove<ArrayList<Long>>("selected_exercises")
        }
    }

    Scaffold(
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                        ),
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
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (draftExercises.isEmpty()) {
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
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(draftExercises, key = { it.exerciseId }) { draft ->
                        ReorderableItem(reorderState, key = draft.exerciseId) { isDragging ->
                            if (isReorderMode) {
                                CollapsedTemplateDraftRow(
                                    draft = draft,
                                    isDragging = isDragging,
                                    onDragStopped = { isReorderMode = false }
                                )
                            } else {
                                DraftExerciseRow(
                                    draft = draft,
                                    onLongPress = { isReorderMode = true },
                                    onIncrement = { viewModel.incrementSets(draft.exerciseId) },
                                    onDecrement = { viewModel.decrementSets(draft.exerciseId) },
                                    onRemove = { viewModel.removeExercise(draft.exerciseId) }
                                )
                            }
                        }
                    }
                }
            }

            // Footer: Add Exercises button
            OutlinedButton(
                onClick = { navController.navigate("exercise_picker") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Exercises")
            }
        }
    }
}

@Composable
private fun DraftExerciseRow(
    draft: DraftExercise,
    onLongPress: () -> Unit = {},
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
