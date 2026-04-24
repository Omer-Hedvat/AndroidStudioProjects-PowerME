package com.powerme.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import timber.log.Timber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerme.app.BuildConfig
import com.powerme.app.ai.AiCoreStatus
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.util.TimerSound
import com.powerme.app.ui.theme.SetupAmber
import com.powerme.app.ui.theme.TimerGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Health Connect permission launcher — must be at top level, not inside a conditional.
    var healthConnectLaunchError by remember { mutableStateOf<String?>(null) }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onHealthConnectPermissionResult(granted) }

    // Re-check HC permissions when returning from Health Connect settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.recheckHealthConnectPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            // ── DEBUG: HC Nuke ─────────────────────────────────────────────────
            item {
                val nukeInProgress = uiState.nukeHcInProgress
                val nukeResult = uiState.nukeHcResult
                val writePermission = androidx.health.connect.client.permission.HealthPermission
                    .getWritePermission(androidx.health.connect.client.records.ExerciseSessionRecord::class)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            // Request write permission first; permission result auto-fires nuke.
                            viewModel.prepareNukePermissionRequest()
                            healthConnectPermissionLauncher.launch(setOf(writePermission))
                        },
                        enabled = !nukeInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (nukeInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("NUKING HC DATA…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("DEBUG: NUKE POWERME HC DATA", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (nukeResult != null) {
                        Text(
                            text = nukeResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

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

            // ── Units ─────────────────────────────────────────────
            item {
                SettingsCard(title = "Units") {
                    val unitModes = UnitSystem.entries
                    val unitLabels = listOf("Metric (kg, cm)", "Imperial (lbs, ft)")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        unitModes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.unitSystem == mode,
                                onClick = { viewModel.setUnitSystem(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = unitModes.size)
                            ) {
                                Text(unitLabels[index], fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Workout Style ─────────────────────────────────────
            item {
                SettingsCard(title = "Workout Style") {
                    val styles = WorkoutStyle.entries
                    val styleLabels = listOf("Pure Gym", "Pure Functional", "Hybrid")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        styles.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = uiState.workoutStyle == style,
                                onClick = { viewModel.setWorkoutStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size)
                            ) {
                                Text(styleLabels[index], fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ── Health Connect ────────────────────────────────────
            item {
                SettingsCard(title = "Health Connect") {
                    when {
                        uiState.healthConnectChecking -> {
                            // blank while status check runs (fast, avoids false "not available" flash)
                        }
                        !uiState.healthConnectAvailable -> {
                            Text(
                                "Health Connect is not available on this device. Install it from the Play Store to sync sleep, heart rate, steps, and body metrics.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        !uiState.healthConnectPermissionsGranted -> {
                            if (uiState.healthConnectPermissionsDenied) {
                                // Permissions were denied (or permanently denied — dialog skipped).
                                // Direct user to Health Connect Settings to grant manually.
                                Text(
                                    "Health Connect permissions were not granted.\n\n1. Tap \"Open HC Settings\" below\n2. Enable all permissions for PowerME\n3. Come back here — the app will connect automatically.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            healthConnectLaunchError = null
                                            try {
                                                healthConnectPermissionLauncher.launch(HealthConnectManager.ALL_PERMISSIONS)
                                            } catch (e: Exception) {
                                                healthConnectLaunchError = "Could not open permissions: ${e.message}"
                                            }
                                        }
                                    ) { Text("Retry") }
                                    Button(
                                        onClick = {
                                            healthConnectLaunchError = null
                                            try {
                                                context.startActivity(
                                                    HealthConnectClient.getHealthConnectManageDataIntent(context)
                                                )
                                            } catch (e: Exception) {
                                                healthConnectLaunchError = "Could not open Health Connect: ${e.message}"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) { Text("Open HC Settings") }
                                }
                            } else {
                                Text(
                                    "Connect to Health Connect to sync your sleep duration, heart rate variability, resting heart rate, step count, weight, body fat, and height.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        healthConnectLaunchError = null
                                        Timber.d("Connect tapped, launching permissions: ${HealthConnectManager.ALL_PERMISSIONS}")
                                        try {
                                            healthConnectPermissionLauncher.launch(HealthConnectManager.ALL_PERMISSIONS)
                                            Timber.d("launch() returned without exception")
                                        } catch (e: Exception) {
                                            Timber.e(e, "launch() threw: ${e.message}")
                                            healthConnectLaunchError = "Could not open Health Connect permissions: ${e.message}"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                ) { Text("Connect") }
                            }
                            healthConnectLaunchError?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
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

                            Spacer(modifier = Modifier.height(12.dp))

                            FilledTonalButton(
                                onClick = onNavigateToProfile,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View in Profile")
                            }

                            uiState.healthConnectError?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
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
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
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
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifications", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Watch & lock screen alerts",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = { viewModel.toggleNotificationsEnabled() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Get Ready countdown", color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                if (uiState.timedSetSetupSeconds == 0) "Off — timed sets start immediately"
                                else "Timed sets wait ${uiState.timedSetSetupSeconds}s before starting",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.setTimedSetSetupSeconds(uiState.timedSetSetupSeconds - 1) },
                                enabled = uiState.timedSetSetupSeconds > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = if (uiState.timedSetSetupSeconds == 0) "Off" else "${uiState.timedSetSetupSeconds}s",
                                color = if (uiState.timedSetSetupSeconds > 0) SetupAmber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 32.dp),
                                textAlign = TextAlign.Center
                            )
                            FilledTonalIconButton(
                                onClick = { viewModel.setTimedSetSetupSeconds(uiState.timedSetSetupSeconds + 1) },
                                enabled = uiState.timedSetSetupSeconds < 10,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Use RPE", color = MaterialTheme.colorScheme.onSurface)
                            Text("Automatically open the RPE picker after completing each set", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Switch(
                            checked = uiState.useRpeAutoPop,
                            onCheckedChange = { viewModel.toggleUseRpeAutoPop() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Timer sound dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Timer sound", color = MaterialTheme.colorScheme.onSurface)
                            Text("Alert tone for rest timers and clocks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        var timerSoundExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = timerSoundExpanded,
                            onExpandedChange = { timerSoundExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = uiState.timerSound.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timerSoundExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .width(120.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true
                            )
                            ExposedDropdownMenu(
                                expanded = timerSoundExpanded,
                                onDismissRequest = { timerSoundExpanded = false }
                            ) {
                                TimerSound.entries.forEach { sound ->
                                    DropdownMenuItem(
                                        text = { Text(sound.displayName) },
                                        onClick = {
                                            viewModel.setTimerSound(sound)
                                            timerSoundExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── AI ──────────────────────────────────────────────────
            item {
                AiSettingsCard(
                    uiState = uiState,
                    onApiKeyInputChange = viewModel::updateApiKeyInput,
                    onSaveApiKey = viewModel::saveUserApiKey,
                    onClearApiKey = viewModel::clearUserApiKey
                )
            }

            // ── Data & Backup ────────────────────────────────────────
            item {
                SettingsCard(title = "Data & Backup") {
                    Button(
                        onClick = viewModel::exportDatabase,
                        enabled = !uiState.isExporting,
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateToImport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Workout History")
                    }
                }
            }

            // ── Cloud Sync ───────────────────────────────────────────
            item {
                SettingsCard(title = "Cloud Sync") {
                    if (!uiState.isSignedIn) {
                        Text(
                            text = "Sign in to enable cloud sync",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        val busy = uiState.isBackingUpToCloud || uiState.isRestoringFromCloud
                        // Back Up Now
                        Text(
                            text = "Upload all local workouts, routines, and settings to your cloud account.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.isBackingUpToCloud) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Backing up…")
                            }
                        } else {
                            Button(
                                onClick = viewModel::backupToCloud,
                                enabled = !busy
                            ) {
                                Text("Back Up Now")
                            }
                        }
                        uiState.backupMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Restore from Cloud
                        Text(
                            text = "Download and restore workouts and routines from your cloud backup.",
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
                            OutlinedButton(
                                onClick = viewModel::restoreFromCloud,
                                enabled = !busy
                            ) {
                                Text("Restore from Cloud")
                            }
                        }
                        uiState.cloudRestoreMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── Feedback ─────────────────────────────────────────────
            item {
                SettingsCard(title = "Feedback") {
                    Text(
                        text = "Found a bug or have a suggestion? Tap below to send an email with your device info pre-filled.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val subject = "PowerME Feedback — v${BuildConfig.VERSION_NAME}"
                            val body = buildString {
                                appendLine("Hi,")
                                appendLine()
                                appendLine("[Describe your bug or suggestion here]")
                                appendLine()
                                appendLine("---")
                                appendLine("App: PowerME v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                            }
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("powerme.support@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, body)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send feedback"))
                            } catch (e: Exception) {
                                Timber.e(e, "Could not open email client")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("Send Feedback")
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

            // ── Version ───────────────────────────────────────────────
            item {
                Text(
                    text = "PowerME v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun AiSettingsCard(
    uiState: SettingsUiState,
    onApiKeyInputChange: (String) -> Unit,
    onSaveApiKey: () -> Unit,
    onClearApiKey: () -> Unit
) {
    SettingsCard(title = "AI") {
        val statusText = when (uiState.apiKeyStatus) {
            ApiKeyStatus.UsingUser -> "Using: Your key"
            ApiKeyStatus.UsingDefault -> "Using: Default"
            ApiKeyStatus.NoKey -> "No key set"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        val onDeviceText = when (uiState.onDeviceAiStatus) {
            AiCoreStatus.Ready -> "On-device AI: Ready"
            AiCoreStatus.NeedsDownload -> "On-device AI: Downloading…"
            AiCoreStatus.NotSupported -> "On-device AI: Not available (using cloud)"
        }
        val onDeviceColor = when (uiState.onDeviceAiStatus) {
            AiCoreStatus.Ready -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = onDeviceText,
            style = MaterialTheme.typography.bodySmall,
            color = onDeviceColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        var keyVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = uiState.userApiKeyInput,
            onValueChange = onApiKeyInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your Gemini API key") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key"
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Stored only on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (val validation = uiState.apiKeyValidation) {
            is ApiKeyValidationState.Idle -> {}
            is ApiKeyValidationState.Validating -> {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Checking key…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is ApiKeyValidationState.Valid -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✓ Key is working",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is ApiKeyValidationState.QuotaExceeded -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Key is valid (quota exceeded)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SetupAmber
                )
            }
            is ApiKeyValidationState.Invalid -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✗ ${validation.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSaveApiKey,
                enabled = uiState.userApiKeyInput.isNotBlank()
            ) { Text("Save") }
            OutlinedButton(
                onClick = onClearApiKey,
                enabled = uiState.hasUserApiKey
            ) { Text("Clear") }
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

