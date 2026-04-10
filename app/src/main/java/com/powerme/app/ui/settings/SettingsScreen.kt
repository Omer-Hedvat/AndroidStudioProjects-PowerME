package com.powerme.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
import com.powerme.app.ui.components.rememberSelectAllState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.ui.theme.TimerGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Health Connect permission launcher — must be at top level, not inside a conditional
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onHealthConnectPermissionResult(granted) }

    // Cloud restore toast
    LaunchedEffect(uiState.cloudRestoreMessage) {
        val msg = uiState.cloudRestoreMessage ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        viewModel.dismissCloudRestoreMessage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Header removed as it is now in TopAppBar

            // ── Appearance ───────────────────────────────────────
            item {
                SettingsCard(title = "Appearance") {
                    val themeModes = com.powerme.app.data.ThemeMode.entries
                    val labels = listOf("Light", "Dark", "System")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeModes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size)
                            ) {
                                Text(labels[index])
                            }
                        }
                    }
                }
            }

            // ── Health Connect ────────────────────────────────────
            item {
                SettingsCard(title = "Health Connect") {
                    when {
                        !uiState.healthConnectAvailable -> {
                            Text(
                                "Health Connect is not available on this device. Install it from the Play Store to sync sleep, heart rate, steps, and body metrics.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        !uiState.healthConnectPermissionsGranted -> {
                            Text(
                                "Connect to Health Connect to sync your sleep duration, heart rate variability, resting heart rate, step count, weight, body fat, and height.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { healthConnectPermissionLauncher.launch(HealthConnectManager.ALL_PERMISSIONS) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) { Text("Connect") }
                        }
                        else -> {
                            // Status line
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = TimerGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Connected",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TimerGreen
                                )
                                val lastSyncText = uiState.healthConnectData?.lastSyncTimestamp
                                    ?.let { " · ${formatRelativeTime(it)}" }
                                    ?: " · No sync yet"
                                Text(
                                    lastSyncText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            uiState.healthConnectData?.let { data ->
                                Spacer(modifier = Modifier.height(10.dp))
                                HealthMetricsSummary(data)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (uiState.healthConnectSyncing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Syncing…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            } else {
                                Button(
                                    onClick = viewModel::syncHealthConnect,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                ) { Text("Sync Now") }
                            }

                            uiState.healthConnectError?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // ── Profile (Body Metrics) ─────────────────────────────
            item {
                SettingsCard(title = "Profile") {
                    val bodyFatFocusRequester = remember { FocusRequester() }
                    val heightFocusRequester = remember { FocusRequester() }
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
                        Text(lastText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (weightTfv, weightSelectMod) = rememberSelectAllState(uiState.weightInput)
                        OutlinedTextField(
                            value = weightTfv.value,
                            onValueChange = { newTfv -> weightTfv.value = newTfv; viewModel.updateWeightInput(newTfv.text) },
                            label = { Text("Weight (kg)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { bodyFatFocusRequester.requestFocus() }),
                            modifier = Modifier.weight(1f).then(weightSelectMod),
                            colors = PowerMeDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )
                        val (bodyFatTfv, bodyFatSelectMod) = rememberSelectAllState(uiState.bodyFatInput)
                        OutlinedTextField(
                            value = bodyFatTfv.value,
                            onValueChange = { newTfv -> bodyFatTfv.value = newTfv; viewModel.updateBodyFatInput(newTfv.text) },
                            label = { Text("Body Fat (%)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { heightFocusRequester.requestFocus() }),
                            modifier = Modifier.weight(1f).focusRequester(bodyFatFocusRequester).then(bodyFatSelectMod),
                            colors = PowerMeDefaults.outlinedTextFieldColors(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val (heightTfv, heightSelectMod) = rememberSelectAllState(uiState.heightInput)
                    OutlinedTextField(
                        value = heightTfv.value,
                        onValueChange = { newTfv -> heightTfv.value = newTfv; viewModel.updateHeightInput(newTfv.text) },
                        label = { Text("Height (cm)", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.saveBodyMetrics() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(heightFocusRequester).then(heightSelectMod),
                        colors = PowerMeDefaults.outlinedTextFieldColors(),
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
                        Text("Audio", color = MaterialTheme.colorScheme.onSurface)
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
                        Text("Haptics", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = uiState.restTimerHapticsEnabled,
                            onCheckedChange = { viewModel.toggleRestTimerHaptics() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // ── Display & Workout ──────────────────────────────────────
            item {
                SettingsCard(title = "Display & Workout") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep screen on", color = MaterialTheme.colorScheme.onSurface)
                            Text("Prevent display from sleeping", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = uiState.keepScreenOn,
                            onCheckedChange = { viewModel.toggleKeepScreenOn() },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.surface, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // ── Database Export ─────────────────────────────────────
            item {
                SettingsCard(title = "Data Export") {
                    Button(
                        onClick = viewModel::exportDatabase,
                        enabled = !uiState.isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Export Database to JSON")
                    }
                    uiState.exportSuccessMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
                    uiState.exportErrorMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                }
            }

            // ── Cloud Sync ───────────────────────────────────────────
            item {
                SettingsCard(title = "Cloud Sync") {
                    if (!uiState.isSignedIn) {
                        Text(
                            text = "Sign in to enable cloud restore",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "Restore workouts and routines from your cloud backup. Uses last-write-wins conflict resolution.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.isRestoringFromCloud) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Restoring from cloud…")
                            }
                        } else {
                            Button(
                                onClick = viewModel::restoreFromCloud,
                                enabled = !uiState.isRestoringFromCloud
                            ) {
                                Text("Restore from Cloud")
                            }
                        }
                    }
                }
            }

            // ── Privacy / Delete Account ─────────────────────────────
            item {
                SettingsCard(title = "Privacy") {
                    Text(
                        text = "Your data is stored locally and mirrored to Firebase for account continuity. Deleting your account permanently removes all local data and your Firebase user record.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                                contentColor = MaterialTheme.colorScheme.onError
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount {} },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
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
private fun HealthMetricsSummary(data: HealthConnectReadResult) {
    val subText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val valueText = MaterialTheme.colorScheme.onSurface

    @Composable
    fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, fontSize = 10.sp, color = subText)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueText)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCell(
                "Sleep",
                data.sleepMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                "HRV",
                data.hrv?.let { "${"%.0f".format(it)} ms" } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCell(
                "Resting HR",
                data.rhr?.let { "$it bpm" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                "Steps",
                data.steps?.let { "%,d".format(it) } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCell(
                "Weight",
                data.weight?.let { "${"%.1f".format(it)} kg" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                "Body Fat",
                data.bodyFat?.let { "${"%.1f".format(it)}%" } ?: "--",
                modifier = Modifier.weight(1f)
            )
            MetricCell(
                "Height",
                data.height?.let { "${it.toInt()} cm" } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = Instant.now()
    val then = Instant.ofEpochMilli(timestampMs)
    val minutesAgo = ChronoUnit.MINUTES.between(then, now)
    return when {
        minutesAgo < 1 -> "Just now"
        minutesAgo < 60 -> "${minutesAgo}m ago"
        minutesAgo < 1440 -> "${minutesAgo / 60}h ago"
        minutesAgo < 2880 -> "Yesterday"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
            formatter.format(then)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = PowerMeDefaults.cardColors(),
        elevation = PowerMeDefaults.subtleCardElevation()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

