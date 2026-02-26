package com.omerhedvat.powerme.ui.gyms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val STANDARD_EQUIPMENT = setOf("Barbell", "Bench", "Pull-up Bar", "Squat Cage", "Cable Cross Machine")
private val PLATE_SUFFIXES = listOf("kg plate")

data class GymInventoryUiState(
    val profile: GymProfile? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class GymInventoryViewModel @Inject constructor(
    private val gymProfileRepository: GymProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GymInventoryUiState())
    val uiState: StateFlow<GymInventoryUiState> = _uiState.asStateFlow()

    fun loadProfile(profileId: Long) {
        viewModelScope.launch {
            val profile = gymProfileRepository.getProfileById(profileId)
            _uiState.value = GymInventoryUiState(profile = profile, isLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GymInventoryScreen(
    profileId: Long,
    onDone: () -> Unit,
    viewModel: GymInventoryViewModel = hiltViewModel()
) {
    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gym Inventory", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val profile = uiState.profile
        if (profile == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Gym profile not found.", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            }
            return@Scaffold
        }

        val equipmentList = profile.getEquipmentList()

        // Categorize equipment
        val standardItems = equipmentList.filter { item ->
            STANDARD_EQUIPMENT.any { it.equals(item.trim(), ignoreCase = true) }
        }
        val plates = equipmentList.filter { item ->
            PLATE_SUFFIXES.any { item.contains(it, ignoreCase = true) }
        }.sortedByDescending {
            it.substringBefore("kg").trim().toDoubleOrNull() ?: 0.0
        }
        val additional = equipmentList.filter { item ->
            !standardItems.contains(item) && !plates.contains(item)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gym name header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = profile.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gym profile saved",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Standard Equipment section
            if (standardItems.isNotEmpty()) {
                item {
                    InventorySection(title = "Standard Equipment") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            standardItems.forEach { item ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(item, fontSize = 12.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Available Plates section
            if (plates.isNotEmpty()) {
                item {
                    InventorySection(title = "Available Plates") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            plates.forEach { plate ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(plate, fontSize = 12.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Dumbbell Range section
            if (profile.dumbbellMinKg != null && profile.dumbbellMaxKg != null) {
                item {
                    InventorySection(title = "Dumbbell Range") {
                        Text(
                            "${"%.1f".format(profile.dumbbellMinKg)} kg – ${"%.1f".format(profile.dumbbellMaxKg)} kg",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Additional Equipment section
            if (additional.isNotEmpty()) {
                item {
                    InventorySection(title = "Additional Equipment") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            additional.forEach { item ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(item, fontSize = 12.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
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
private fun InventorySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

