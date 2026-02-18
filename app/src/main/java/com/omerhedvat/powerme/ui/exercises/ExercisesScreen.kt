package com.omerhedvat.powerme.ui.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.ui.components.MagicAddDialog
import com.omerhedvat.powerme.ui.components.YouTubePlayerSheet
import com.omerhedvat.powerme.ui.theme.ElectricBlue
import com.omerhedvat.powerme.ui.theme.NavySurface
import com.omerhedvat.powerme.ui.theme.NeonBlue

private val MUSCLE_GROUPS = listOf("All", "Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExercisesScreen(
    onStartWorkout: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMagicAddDialog by remember { mutableStateOf(false) }
    var youtubeVideoId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Exercise?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Search exercises…", color = NeonBlue.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = NeonBlue)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = NeonBlue.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            // Muscle group filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MUSCLE_GROUPS.forEach { muscle ->
                    val isSelected = if (muscle == "All") uiState.selectedMuscle == null
                    else uiState.selectedMuscle == muscle

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.onMuscleFilterChanged(if (muscle == "All") null else muscle)
                        },
                        label = { Text(muscle) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonBlue,
                            selectedLabelColor = NavySurface,
                            containerColor = NavySurface,
                            labelColor = NeonBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Exercise list
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonBlue)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            onYouTubeClick = { youtubeVideoId = it },
                            onLongPress = {
                                if (exercise.isCustom) showDeleteDialog = exercise
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FABs
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start Workout FAB
            FloatingActionButton(
                onClick = onStartWorkout,
                containerColor = ElectricBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start Workout")
            }
            // Add Exercise FAB
            FloatingActionButton(
                onClick = { showMagicAddDialog = true },
                containerColor = NeonBlue,
                contentColor = NavySurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Exercise")
            }
        }
    }

    // MagicAdd Dialog
    if (showMagicAddDialog) {
        MagicAddDialog(
            onExerciseAdded = { showMagicAddDialog = false },
            onDismiss = { showMagicAddDialog = false }
        )
    }

    // YouTube Sheet
    youtubeVideoId?.let { videoId ->
        YouTubePlayerSheet(
            videoId = videoId,
            onDismiss = { youtubeVideoId = null }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { exercise ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Exercise") },
            text = { Text("Delete \"${exercise.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomExercise(exercise)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    onYouTubeClick: (String) -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(exercise.muscleGroup, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = NeonBlue.copy(alpha = 0.15f),
                            labelColor = NeonBlue
                        )
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text(exercise.equipmentType, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = ElectricBlue.copy(alpha = 0.15f),
                            labelColor = ElectricBlue
                        )
                    )
                }
            }
            if (!exercise.youtubeVideoId.isNullOrBlank()) {
                IconButton(onClick = { onYouTubeClick(exercise.youtubeVideoId!!) }) {
                    Icon(
                        Icons.Default.VideoLibrary,
                        contentDescription = "Watch video",
                        tint = NeonBlue
                    )
                }
            }
        }
    }
}
