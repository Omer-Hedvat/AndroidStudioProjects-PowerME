package com.powerme.app.ui.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.ui.theme.ExercisePlyometricOrange
import com.powerme.app.ui.theme.ExerciseStretchTeal
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.ReadinessAmber
import com.powerme.app.ui.theme.TimerGreen


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExercisesScreen(
    pickerMode: Boolean = false,
    onExercisesSelected: (List<Long>) -> Unit = {},
    onExerciseClick: (Long) -> Unit = {},
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val muscleGroupFilters by viewModel.muscleGroupFilters.collectAsState()
    val equipmentFilters by viewModel.equipmentFilters.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Exercise?>(null) }
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

            // Functional filter toggle chip
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mode",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TimerGreen.copy(alpha = 0.8f)
                )
                FilterChip(
                    selected = uiState.functionalFilter,
                    onClick = viewModel::onFunctionalFilterToggled,
                    label = { Text("Functional") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TimerGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TimerGreen
                    )
                )
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
                                    onExerciseClick(exercise.id)
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

private fun exerciseTypeIcon(type: ExerciseType): ImageVector = when (type) {
    ExerciseType.STRENGTH   -> Icons.Default.FitnessCenter
    ExerciseType.CARDIO     -> Icons.Default.DirectionsRun
    ExerciseType.TIMED      -> Icons.Default.Timer
    ExerciseType.PLYOMETRIC -> Icons.Default.FlashOn
    ExerciseType.STRETCH    -> Icons.Default.SelfImprovement
}

private fun exerciseTypeColor(type: ExerciseType, primaryColor: Color): Color = when (type) {
    ExerciseType.STRENGTH   -> primaryColor
    ExerciseType.CARDIO     -> TimerGreen
    ExerciseType.TIMED      -> ReadinessAmber
    ExerciseType.PLYOMETRIC -> ExercisePlyometricOrange
    ExerciseType.STRETCH    -> ExerciseStretchTeal
}

@Composable
private fun ExerciseTagChip(label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetaItem(icon: ImageVector, label: String, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Text(text = label, fontSize = 10.sp, color = tint)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val typeColor = exerciseTypeColor(exercise.exerciseType, primaryColor)
    val typeIcon  = exerciseTypeIcon(exercise.exerciseType)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.cardElevation()
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(color = typeColor.copy(alpha = 0.15f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = exercise.exerciseType.name,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exercise.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (exercise.isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = ReadinessAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExerciseTagChip(exercise.muscleGroup, primaryColor)
                        ExerciseTagChip(exercise.equipmentType, MaterialTheme.colorScheme.secondary)
                        if (exercise.isCustom) {
                            ExerciseTagChip("Custom", MaterialTheme.colorScheme.tertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetaItem(
                            icon = Icons.Default.Timer,
                            label = "${exercise.restDurationSeconds}s rest",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(primaryColor.copy(alpha = 0.12f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = primaryColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}
