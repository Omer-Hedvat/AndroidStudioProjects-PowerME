package com.powerme.app.ui.exercises

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.ui.theme.TimerGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseFilterDialog(
    selectedTypes: Set<ExerciseType>,
    selectedMuscles: Set<String>,
    selectedEquipment: Set<String>,
    functionalFilter: Boolean,
    muscleOptions: List<String>,
    equipmentOptions: List<String>,
    onTypeToggled: (ExerciseType) -> Unit,
    onMuscleToggled: (String) -> Unit,
    onEquipmentToggled: (String) -> Unit,
    onFunctionalToggled: () -> Unit,
    onSelectAllTypes: () -> Unit,
    onDeselectAllTypes: () -> Unit,
    onSelectAllMuscles: () -> Unit,
    onDeselectAllMuscles: () -> Unit,
    onSelectAllEquipment: () -> Unit,
    onDeselectAllEquipment: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val typeChips = ExerciseType.entries
    val allTypesSelected = selectedTypes.containsAll(typeChips) && functionalFilter
    val allMusclesSelected = muscleOptions.isNotEmpty() && selectedMuscles.containsAll(muscleOptions.toSet())
    val allEquipmentSelected = equipmentOptions.isNotEmpty() && selectedEquipment.containsAll(equipmentOptions.toSet())
    val primaryColor = MaterialTheme.colorScheme.primary
    val activeFilterCount = selectedTypes.size + selectedMuscles.size + selectedEquipment.size +
        (if (functionalFilter) 1 else 0)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Scrollable chip sections
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close filters",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Exercise Type section
                    FilterSectionHeader(
                        title = "Exercise Type",
                        allSelected = allTypesSelected,
                        onSelectAll = onSelectAllTypes,
                        onDeselectAll = onDeselectAllTypes
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        typeChips.forEach { type ->
                            val typeColor = exerciseTypeColor(type, primaryColor)
                            FilterChip(
                                selected = type in selectedTypes,
                                onClick = { onTypeToggled(type) },
                                label = {
                                    Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = exerciseTypeIcon(type),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = typeColor,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.surface,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = typeColor,
                                    iconColor = typeColor
                                )
                            )
                        }
                        // Functional chip (tag-based, not ExerciseType)
                        FilterChip(
                            selected = functionalFilter,
                            onClick = onFunctionalToggled,
                            label = { Text("Functional") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TimerGreen,
                                selectedLabelColor = MaterialTheme.colorScheme.surface,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.surface,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = TimerGreen,
                                iconColor = TimerGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Muscle Group section
                    FilterSectionHeader(
                        title = "Muscle Group",
                        allSelected = allMusclesSelected,
                        onSelectAll = onSelectAllMuscles,
                        onDeselectAll = onDeselectAllMuscles
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        muscleOptions.forEach { muscle ->
                            FilterChip(
                                selected = muscle in selectedMuscles,
                                onClick = { onMuscleToggled(muscle) },
                                label = { Text(muscle) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryColor,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = primaryColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Equipment section
                    FilterSectionHeader(
                        title = "Equipment",
                        allSelected = allEquipmentSelected,
                        onSelectAll = onSelectAllEquipment,
                        onDeselectAll = onDeselectAllEquipment
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        equipmentOptions.forEach { equipment ->
                            FilterChip(
                                selected = equipment in selectedEquipment,
                                onClick = { onEquipmentToggled(equipment) },
                                label = { Text(equipment) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Sticky bottom action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onClearAll(); onDismiss() },
                        enabled = activeFilterCount > 0,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (activeFilterCount > 0)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                        )
                    ) {
                        Text("Reset")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(
    title: String,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = if (allSelected) onDeselectAll else onSelectAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = if (allSelected) "Deselect All" else "Select All",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
