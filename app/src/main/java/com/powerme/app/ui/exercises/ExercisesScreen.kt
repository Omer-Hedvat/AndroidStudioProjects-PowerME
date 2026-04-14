package com.powerme.app.ui.exercises

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.database.Exercise
import com.powerme.app.ui.theme.FormCuesGold
import com.powerme.app.ui.theme.PowerMeDefaults


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    pickerMode: Boolean = false,
    onExercisesSelected: (List<Long>) -> Unit = {},
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val muscleGroupFilters by viewModel.muscleGroupFilters.collectAsState()
    val equipmentFilters by viewModel.equipmentFilters.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Exercise?>(null) }
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier
            .fillMaxSize()
            .then(if (pickerMode) Modifier.statusBarsPadding() else Modifier)
        ) {
            // Picker mode header
            if (pickerMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Exercises",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { onExercisesSelected(selectedIds.toList()) }) {
                            Text("Add (${selectedIds.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Search exercises…", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = PowerMeDefaults.outlinedTextFieldColors(),
                singleLine = true
            )

            // Muscle group label
            Text(
                text = "Muscle",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )

            // Muscle group filter chips
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    muscleGroupFilters.forEach { muscle ->
                        val isSelected = if (muscle == "All") uiState.selectedMuscles.isEmpty()
                        else muscle in uiState.selectedMuscles

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onMuscleFilterToggled(muscle) },
                            label = { Text(muscle) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.surface,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                val bgColor = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(40.dp)
                        .height(40.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color.Transparent, bgColor))
                        )
                )
            }

            // Divider between chip rows
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            // Equipment label + chips in surfaceVariant background
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Equipment",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            equipmentFilters.forEach { equipment ->
                                val isSelected = if (equipment == "All") uiState.selectedEquipment.isEmpty()
                                else equipment in uiState.selectedEquipment

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onEquipmentFilterToggled(equipment) },
                                    label = { Text(equipment) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                        val svColor = MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(40.dp)
                                .height(40.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(Color.Transparent, svColor))
                                )
                        )
                    }
                }
            }

            // Exercise list
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            isSelected = pickerMode && exercise.id in selectedIds,
                            onClick = {
                                if (pickerMode) {
                                    selectedIds = if (exercise.id in selectedIds)
                                        selectedIds - exercise.id
                                    else
                                        selectedIds + exercise.id
                                } else {
                                    selectedExercise = exercise
                                }
                            },
                            onLongPress = {
                                if (!pickerMode && exercise.isCustom) showDeleteDialog = exercise
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

    }

    // Exercise detail sheet — shown when user taps an exercise card
    selectedExercise?.let { ex ->
        ExerciseDetailSheet(exercise = ex, onDismiss = { selectedExercise = null })
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
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isSelected: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = exercise.muscleGroup,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = exercise.equipmentType,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showFormCues by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(onClick = {}, label = { Text(exercise.muscleGroup) })
                SuggestionChip(onClick = {}, label = {
                    Text(exercise.equipmentType)
                })
                if (exercise.setupNotes?.isNotBlank() == true) {
                    IconButton(onClick = { showFormCues = !showFormCues }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Toggle Form Cues",
                            tint = if (showFormCues) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            // Form Cues (setupNotes) — shown only when toggled via Info icon
            val cues = exercise.setupNotes
            if (showFormCues && !cues.isNullOrBlank()) {
                Surface(
                    color = FormCuesGold,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cues,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            // YouTube link
            exercise.youtubeVideoId?.takeIf { it.isNotBlank() }?.let { videoId ->
                TextButton(
                    onClick = {
                        val uri = Uri.parse("vnd.youtube:$videoId")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                            )
                        }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Watch on YouTube")
                }
            }
        }
    }
}
