package com.powerme.app.ui.exercises

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val searchFocusRequester = remember { FocusRequester() }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.onSearchQueryChanged("")
                                searchFocusRequester.requestFocus()
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconToggleButton(
                            checked = uiState.favoritesOnly,
                            onCheckedChange = { viewModel.onFavoritesFilterToggled() }
                        ) {
                            Icon(
                                imageVector = if (uiState.favoritesOnly) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Show favourites only",
                                tint = if (uiState.favoritesOnly) ReadinessAmber else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = viewModel::onFilterDialogToggled) {
                            BadgedBox(
                                badge = {
                                    if (uiState.activeFilterCount > 0) {
                                        Badge { Text("${uiState.activeFilterCount}") }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Filters",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .focusRequester(searchFocusRequester),
                colors = PowerMeDefaults.outlinedTextFieldColors(),
                singleLine = true
            )

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
                            },
                            onFavoriteToggled = { viewModel.toggleFavorite(exercise) }
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

    // Filter dialog
    if (uiState.showFilterDialog) {
        ExerciseFilterDialog(
            selectedTypes = uiState.selectedTypes,
            selectedMuscles = uiState.selectedMuscles,
            selectedEquipment = uiState.selectedEquipment,
            functionalFilter = uiState.functionalFilter,
            muscleOptions = muscleGroupFilters,
            equipmentOptions = equipmentFilters,
            onTypeToggled = viewModel::onTypeFilterToggled,
            onMuscleToggled = viewModel::onMuscleFilterToggled,
            onEquipmentToggled = viewModel::onEquipmentFilterToggled,
            onFunctionalToggled = viewModel::onFunctionalFilterToggled,
            onSelectAllTypes = viewModel::onSelectAllTypes,
            onDeselectAllTypes = viewModel::onDeselectAllTypes,
            onSelectAllMuscles = viewModel::onSelectAllMuscles,
            onDeselectAllMuscles = viewModel::onDeselectAllMuscles,
            onSelectAllEquipment = viewModel::onSelectAllEquipment,
            onDeselectAllEquipment = viewModel::onDeselectAllEquipment,
            onClearAll = viewModel::onClearAllFilters,
            onDismiss = viewModel::onFilterDialogToggled
        )
    }
}

internal fun exerciseTypeIcon(type: ExerciseType): ImageVector = when (type) {
    ExerciseType.STRENGTH   -> Icons.Default.FitnessCenter
    ExerciseType.CARDIO     -> Icons.Default.DirectionsRun
    ExerciseType.TIMED      -> Icons.Default.Timer
    ExerciseType.PLYOMETRIC -> Icons.Default.FlashOn
    ExerciseType.STRETCH    -> Icons.Default.SelfImprovement
}

internal fun exerciseTypeColor(type: ExerciseType, primaryColor: Color): Color = when (type) {
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
    onFavoriteToggled: () -> Unit,
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
                        IconButton(
                            onClick = onFavoriteToggled,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (exercise.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (exercise.isFavorite) "Remove from favourites" else "Add to favourites",
                                tint = if (exercise.isFavorite) ReadinessAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
