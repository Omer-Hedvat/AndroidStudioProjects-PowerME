package com.omerhedvat.powerme.ui.gyms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val PLATE_WEIGHTS = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

// Dumbbell range: 0.5–55 kg in 0.5 kg steps
// Number of 0.5-step values = (55.0 - 0.5) / 0.5 + 1 = 110
// Slider steps param = total_values - 2 = 108
private const val DUMBBELL_MIN = 0.5f
private const val DUMBBELL_MAX = 55f
private const val DUMBBELL_STEPS = 108

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymSetupScreen(
    viewModel: GymSetupViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var manualEquipmentInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState.savedProfileId) {
        uiState.savedProfileId?.let { id -> onSaved(id) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Create GYM", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Gym Name ──────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = uiState.gymName,
                    onValueChange = viewModel::updateGymName,
                    label = { Text("Gym Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // ── Standard Equipment ────────────────────────────────
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Standard Equipment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Toggle off equipment not available at your gym", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        // Build items list — Bench only shown when Barbell is ON
                        val standardItems = buildList {
                            add(Triple("Barbell", uiState.hasBarbell, viewModel::toggleBarbell))
                            add(Triple("Bench", uiState.hasBench, viewModel::toggleBench))
                            add(Triple("Pull-up Bar", uiState.hasPullUpBar, viewModel::togglePullUpBar))
                            add(Triple("Squat Cage", uiState.hasSquatCage, viewModel::toggleSquatCage))
                            add(Triple("Cable Cross", uiState.hasCableCross, viewModel::toggleCableCross))
                        }
                        standardItems.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (label, isChecked, toggle) ->
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = toggle,
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.background,
                                            labelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // ── Plate Checkboxes — always visible, greyed out when none selected ────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (uiState.selectedPlates.isNotEmpty()) 1f else 0.45f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Available Plates", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        PLATE_WEIGHTS.chunked(4).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { weight ->
                                    FilterChip(
                                        selected = weight in uiState.selectedPlates,
                                        onClick = { viewModel.togglePlate(weight) },
                                        label = { Text("${weight}kg", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            containerColor = MaterialTheme.colorScheme.background,
                                            labelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // ── Dumbbell Range Slider ─────────────────────────────
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Dumbbell Range", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${"%.1f".format(uiState.dumbbellMinKg)} kg – ${"%.1f".format(uiState.dumbbellMaxKg)} kg",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RangeSlider(
                            value = uiState.dumbbellMinKg..uiState.dumbbellMaxKg,
                            onValueChange = viewModel::updateDumbbellRange,
                            valueRange = DUMBBELL_MIN..DUMBBELL_MAX,
                            steps = DUMBBELL_STEPS
                        )
                    }
                }
            }

            // ── Additional Equipment (manual input) ───────────────
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Additional Equipment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Add any extra equipment at your gym", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualEquipmentInput,
                                onValueChange = { manualEquipmentInput = it },
                                label = { Text("e.g. Kettlebell, Battle Ropes…") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = manualEquipmentInput.trim()
                                    if (trimmed.isNotBlank()) {
                                        viewModel.addEquipmentManually(trimmed)
                                        manualEquipmentInput = ""
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (uiState.detectedEquipment.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            uiState.detectedEquipment.forEach { item ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(item, fontSize = 12.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeEquipmentChip(item) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Error message ─────────────────────────────────────
            uiState.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            // ── Save Button ───────────────────────────────────────
            item {
                Button(
                    onClick = viewModel::saveGym,
                    enabled = !uiState.isSaving && uiState.gymName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save GYM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
