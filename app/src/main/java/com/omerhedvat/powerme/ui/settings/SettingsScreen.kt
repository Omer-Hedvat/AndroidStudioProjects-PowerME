package com.omerhedvat.powerme.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.util.GeminiModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToGymSetup: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    // Health Connect permission launcher — requests READ_WEIGHT + READ_BODY_FAT at runtime,
    // then triggers sync once permissions are (potentially) granted
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // After the system permission dialog, attempt sync regardless — HC will return data
        // if granted, or null if still denied (handled by syncFromHealthConnect's error state)
        viewModel.syncFromHealthConnect()
    }

    // Trigger model fetch when screen opens if key exists
    LaunchedEffect(Unit) { viewModel.fetchModelsIfNeeded() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Text("SETTINGS", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            // ── API Key ──────────────────────────────────────────
            item {
                SettingsCard(title = "Gemini API Key") {
                    // API studio link
                    val linkText = buildAnnotatedString {
                        append("Get key at ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append("aistudio.google.com/app/apikey")
                        }
                    }
                    ClickableText(
                        text = linkText,
                        style = LocalTextStyle.current.copy(
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { uriHandler.openUri("https://aistudio.google.com/app/apikey") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = viewModel::updateApiKey,
                        label = { Text("API Key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide key" else "Show key",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    // Validation error
                    uiState.keyValidationError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::saveApiKey,
                            enabled = uiState.apiKey.isNotBlank() && !uiState.isValidatingKey,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.surface)
                        ) {
                            if (uiState.isValidatingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.surface, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (uiState.isValidatingKey) "Validating…" else "Save & Validate")
                        }
                        if (uiState.hasApiKey) {
                            OutlinedButton(
                                onClick = viewModel::clearApiKey,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Clear") }
                        }
                    }

                    if (uiState.showSuccessMessage) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("✓ API key saved & validated", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── Model Selection ────────────────────────────────────
            if (uiState.hasApiKey) {
                item {
                    SettingsCard(title = "AI Models") {
                        if (uiState.isFetchingModels) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                Text("Loading available models…", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            }
                        } else if (uiState.availableModels.isNotEmpty()) {
                            Text("War Room Model", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            ModelDropdown(
                                models = uiState.availableModels,
                                selected = uiState.selectedWarRoomModel,
                                onSelect = viewModel::setWarRoomModel
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Enrichment Model (Flash preferred)", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            ModelDropdown(
                                models = uiState.availableModels,
                                selected = uiState.selectedEnrichmentModel,
                                onSelect = viewModel::setEnrichmentModel
                            )
                        } else {
                            Text("Save a valid API key to load available models.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // ── Language ────────────────────────────────────────────
            item {
                SettingsCard(title = "Response Language") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Hebrew", "English").forEach { lang ->
                            val isSelected = uiState.language == lang
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Committee will respond in ${uiState.language} by default.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // ── Profile (Body Metrics) ─────────────────────────────
            item {
                SettingsCard(title = "Profile") {
                    val lastText = buildString {
                        val w = uiState.lastWeight
                        val bf = uiState.lastBodyFat
                        val h = uiState.lastHeight
                        if (w != null || bf != null || h != null) {
                            append("Last: ")
                            if (w != null) append("${"%.1f".format(w)} kg")
                            if (w != null && bf != null) append(" / ")
                            if (bf != null) append("${"%.1f".format(bf)}%")
                            if ((w != null || bf != null) && h != null) append(" / ")
                            if (h != null) append("${h.toInt()} cm")
                        }
                    }
                    if (lastText.isNotBlank()) {
                        Text(lastText, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // Sync from Health Connect button — requests permissions first, then syncs
                    OutlinedButton(
                        onClick = {
                            hcPermissionLauncher.launch(
                                arrayOf(
                                    "android.permission.health.READ_WEIGHT",
                                    "android.permission.health.READ_BODY_FAT",
                                    "android.permission.health.READ_HEIGHT"
                                )
                            )
                        },
                        enabled = !uiState.isSyncingFromHC,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.isSyncingFromHC) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing…")
                        } else {
                            Text("Sync from HealthConnect")
                        }
                    }
                    uiState.hcSyncError?.let { err ->
                        Text(err, fontSize = 11.sp, color = Color.Red.copy(alpha = 0.8f))
                    }
                    if (uiState.bodyMeasurementsFromHC) {
                        Text("(from HealthConnect)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.weightInput,
                            onValueChange = viewModel::updateWeightInput,
                            label = { Text("Weight (kg)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.bodyFatInput,
                            onValueChange = viewModel::updateBodyFatInput,
                            label = { Text("Body Fat (%)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.heightInput,
                        onValueChange = viewModel::updateHeightInput,
                        label = { Text("Height (cm)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.isSavingMetrics) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Button(
                            onClick = viewModel::saveBodyMetrics,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.surface)
                        ) { Text("Save") }
                    }
                }
            }

            // ── Rest Timer ─────────────────────────────────────────
            item {
                SettingsCard(title = "Rest Timer") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio", color = Color.White)
                        Switch(
                            checked = uiState.restTimerAudioEnabled,
                            onCheckedChange = { viewModel.toggleRestTimerAudio() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Haptics", color = Color.White)
                        Switch(
                            checked = uiState.restTimerHapticsEnabled,
                            onCheckedChange = { viewModel.toggleRestTimerHaptics() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep screen on", color = Color.White)
                            Text("Prevent display from sleeping during workout", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = uiState.keepScreenOn,
                            onCheckedChange = { viewModel.toggleKeepScreenOn() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // ── Gym Profiles ────────────────────────────────────────
            item {
                SettingsCard(title = "Active Gym") {
                    uiState.gymProfiles.forEach { gym ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(gym.name, color = Color.White)
                            RadioButton(
                                selected = gym.isActive,
                                onClick = { viewModel.switchToGym(gym.name) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToGymSetup,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Set Up GYM")
                    }
                }
            }

            // ── Database Export ─────────────────────────────────────
            item {
                SettingsCard(title = "Data Export") {
                    Button(
                        onClick = viewModel::exportDatabase,
                        enabled = !uiState.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = Color.White)
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Export Database to JSON")
                    }
                    uiState.exportSuccessMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    uiState.exportErrorMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                }
            }

            // ── Privacy / Delete Account ─────────────────────────────
            item {
                SettingsCard(title = "Privacy") {
                    Text(
                        text = "Your data is stored locally and mirrored to Firebase for account continuity. Deleting your account permanently removes all local data and your Firebase user record.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.isDeletingAccount) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                            Text("Deleting account…", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = viewModel::showDeleteAccountDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Delete Account")
                        }
                    }
                }
            }
        }
    }

    // Delete Account confirmation dialog
    if (uiState.showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteAccountDialog,
            title = { Text("Delete Account?", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete all your data, workout history, and your account. This cannot be undone.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount {} },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissDeleteAccountDialog) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    models: List<GeminiModel>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedModel = models.firstOrNull { it.id == selected }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel?.displayName ?: selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize()
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName, color = Color.White, fontSize = 14.sp)
                            Text(
                                text = when (model.tier) {
                                    0 -> "Thinking"
                                    1 -> "Pro"
                                    else -> "Flash"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    },
                    onClick = {
                        onSelect(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
