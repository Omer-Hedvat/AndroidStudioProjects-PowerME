package com.powerme.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.UnitSystem
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.ui.components.rememberSelectAllState
import com.powerme.app.ui.theme.PowerMeDefaults
import com.powerme.app.util.UnitConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TRAINING_TARGET_OPTIONS = listOf(
    "Hypertrophy", "Fat Loss", "Body Recomposition", "Strength", "Cardio", "Longevity"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onProfileSaved: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()

    LaunchedEffect(uiState.profileSaved) {
        if (uiState.profileSaved) onProfileSaved()
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted -> viewModel.onHcPermissionResult(granted) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (uiState.currentStep) {
            1 -> HcOfferStep(
                uiState = uiState,
                onConnect = { hcPermissionLauncher.launch(HealthConnectManager.ALL_PERMISSIONS) },
                onSkip = { viewModel.skipHc() }
            )
            else -> ProfileFormStep(
                uiState = uiState,
                unitSystem = unitSystem,
                onUnitSystemChange = { viewModel.setUnitSystem(it) },
                onSave = { name, dob, height, weight, bodyFat, occupation, children, chronotype, sleep, gender, targets ->
                    viewModel.saveProfile(
                        name = name,
                        dateOfBirth = dob,
                        heightCm = height,
                        weightKg = weight,
                        bodyFatPercent = bodyFat,
                        occupationType = occupation,
                        parentalLoad = children,
                        chronotype = chronotype,
                        averageSleepHours = sleep,
                        gender = gender,
                        trainingTargets = targets
                    )
                }
            )
        }
    }
}

// ── Step 1: Health Connect offer ──────────────────────────────────────────────

@Composable
private fun HcOfferStep(
    uiState: ProfileSetupUiState,
    onConnect: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect Health Data",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PowerME can import your weight, height, and body composition from Health Connect to save you time.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (uiState.hcPermissionDenied) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No worries — you can connect Health Connect later in Settings.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        if (!uiState.hcPermissionDenied) {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text("Connect Health Connect", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        TextButton(onClick = onSkip) {
            Text(
                text = if (uiState.hcPermissionDenied) "Continue without Health Connect" else "Skip",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Step 2: Profile form ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFormStep(
    uiState: ProfileSetupUiState,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    onUnitSystemChange: (UnitSystem) -> Unit = {},
    onSave: (
        name: String?,
        dob: Long?,
        height: Float?,
        weight: Float?,
        bodyFat: Float?,
        occupation: String?,
        children: Int?,
        chronotype: String?,
        sleep: Float?,
        gender: String?,
        targets: String?
    ) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Pre-fill from Google and HC
    var name by remember { mutableStateOf(uiState.googleDisplayName ?: "") }
    var dateOfBirth by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Internal ground-truth in metric units — used to recompute display strings on unit switch.
    var heightCmInternal by remember(uiState.hcHeight) {
        mutableStateOf(uiState.hcHeight?.toDouble())
    }
    var weightKgInternal by remember(uiState.hcWeight) {
        mutableStateOf(uiState.hcWeight)
    }

    // Height display strings — recomputed when unit changes or HC pre-fill arrives.
    var heightCm by remember(uiState.hcHeight) {
        mutableStateOf(uiState.hcHeight?.let { "%.1f".format(it) } ?: "")
    }
    var heightFeet by remember(uiState.hcHeight) {
        mutableStateOf(uiState.hcHeight?.let {
            UnitConverter.cmToFeetInches(it.toDouble()).first.toString()
        } ?: "")
    }
    var heightInches by remember(uiState.hcHeight) {
        mutableStateOf(uiState.hcHeight?.let {
            UnitConverter.cmToFeetInches(it.toDouble()).second.toString()
        } ?: "")
    }

    // Weight display string — recomputed when unit changes or HC pre-fill arrives.
    var weightDisplay by remember(uiState.hcWeight) {
        mutableStateOf(uiState.hcWeight?.let {
            if (unitSystem == UnitSystem.IMPERIAL) "%.1f".format(UnitConverter.kgToLbs(it))
            else "%.1f".format(it)
        } ?: "")
    }

    // When unit system changes, convert whatever value is currently entered.
    LaunchedEffect(unitSystem) {
        heightCmInternal?.let { cm ->
            if (unitSystem == UnitSystem.IMPERIAL) {
                val (ft, ins) = UnitConverter.cmToFeetInches(cm)
                heightFeet = ft.toString()
                heightInches = ins.toString()
            } else {
                heightCm = "%.1f".format(cm)
            }
        }
        weightKgInternal?.let { kg ->
            weightDisplay = if (unitSystem == UnitSystem.IMPERIAL)
                "%.1f".format(UnitConverter.kgToLbs(kg))
            else
                "%.1f".format(kg)
        }
    }

    var bodyFatPercent by remember(uiState.hcBodyFat) {
        mutableStateOf(uiState.hcBodyFat?.let { "%.1f".format(it) } ?: "")
    }

    var averageSleepHours by remember { mutableStateOf("7") }
    var parentalLoad by remember { mutableStateOf("0") }
    var gender by remember { mutableStateOf("") }
    var occupationType by remember { mutableStateOf("SEDENTARY") }
    var chronotype by remember { mutableStateOf("NEUTRAL") }
    var selectedTargets by remember { mutableStateOf(setOf<String>()) }

    // DOB formatted display
    val dobDisplay = dateOfBirth?.let { epochMs ->
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } ?: ""

    if (showDatePicker) {
        val today = LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateOfBirth,
            yearRange = 1920..(today.year - 5)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateOfBirth = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Profile Setup",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "All fields are optional — skip what you don't know",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Unit system selector ──────────────────────────────────────────────
        Text(
            text = "Units",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(UnitSystem.METRIC to "Metric", UnitSystem.IMPERIAL to "Imperial")
                .forEachIndexed { index, (unit, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        onClick = { onUnitSystemChange(unit) },
                        selected = unitSystem == unit,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.surface,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text(label, fontSize = 13.sp) }
                }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileTextField(
            value = name, onValueChange = { name = it }, label = "Name",
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Date of Birth — the interactionSource pattern is the correct Compose way to
        // detect taps on a read-only OutlinedTextField without the field consuming the touch.
        val dobInteractionSource = remember { MutableInteractionSource() }
        val dobPressed by dobInteractionSource.collectIsPressedAsState()
        LaunchedEffect(dobPressed) {
            if (dobPressed) showDatePicker = true
        }
        OutlinedTextField(
            value = dobDisplay,
            onValueChange = {},
            label = { Text("Date of Birth", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            interactionSource = dobInteractionSource,
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            },
            colors = PowerMeDefaults.outlinedTextFieldColors()
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (unitSystem == UnitSystem.IMPERIAL) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    ProfileTextField(
                        value = heightFeet,
                        onValueChange = {
                            heightFeet = it
                            val ft = it.toIntOrNull() ?: 0
                            val ins = heightInches.toIntOrNull() ?: 0
                            if (ft > 0 || ins > 0) heightCmInternal = UnitConverter.feetInchesToCm(ft, ins)
                        },
                        label = "Height (ft)",
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Right) },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        helperText = if (uiState.hcHeight != null) "from Health Connect" else null
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    ProfileTextField(
                        value = heightInches,
                        onValueChange = {
                            heightInches = it
                            val ft = heightFeet.toIntOrNull() ?: 0
                            val ins = it.toIntOrNull() ?: 0
                            if (ft > 0 || ins > 0) heightCmInternal = UnitConverter.feetInchesToCm(ft, ins)
                        },
                        label = "Height (in)",
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                }
            }
        } else {
            ProfileTextField(
                value = heightCm,
                onValueChange = {
                    heightCm = it
                    heightCmInternal = it.toDoubleOrNull()
                },
                label = "Height (cm)",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                helperText = if (uiState.hcHeight != null) "from Health Connect" else null
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ProfileTextField(
            value = weightDisplay,
            onValueChange = {
                weightDisplay = it
                weightKgInternal = it.toDoubleOrNull()?.let { v ->
                    if (unitSystem == UnitSystem.IMPERIAL) UnitConverter.lbsToKg(v) else v
                }
            },
            label = "Weight (${UnitConverter.weightLabel(unitSystem)})",
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            helperText = if (uiState.hcWeight != null) "from Health Connect" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileTextField(
            value = bodyFatPercent, onValueChange = { bodyFatPercent = it }, label = "Body Fat % — optional",
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            helperText = if (uiState.hcBodyFat != null) "from Health Connect" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileTextField(
            value = averageSleepHours, onValueChange = { averageSleepHours = it }, label = "Avg Sleep Hours",
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileTextField(
            value = parentalLoad, onValueChange = { parentalLoad = it }, label = "Number of Children",
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Gender", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("MALE", "FEMALE", "OTHER"),
            selected = gender,
            onSelect = { gender = if (gender == it) "" else it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Occupation Type", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("SEDENTARY", "ACTIVE", "PHYSICAL"),
            selected = occupationType,
            onSelect = { occupationType = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Chronotype", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmented(
            options = listOf("MORNING", "NEUTRAL", "NIGHT"),
            selected = chronotype,
            onSelect = { chronotype = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Training Targets", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        MultiSelectChips(
            options = TRAINING_TARGET_OPTIONS,
            selected = selectedTargets,
            onToggle = { option ->
                selectedTargets = if (option in selectedTargets) selectedTargets - option else selectedTargets + option
            }
        )

        uiState.saveError?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val heightCmFinal: Float? = heightCmInternal?.toFloat()
                val weightKgFinal: Float? = weightKgInternal?.toFloat()
                onSave(
                    name.trim().takeIf { it.isNotBlank() },
                    dateOfBirth,
                    heightCmFinal,
                    weightKgFinal,
                    bodyFatPercent.toFloatOrNull(),
                    occupationType.takeIf { it.isNotBlank() },
                    parentalLoad.toIntOrNull(),
                    chronotype.takeIf { it.isNotBlank() },
                    averageSleepHours.toFloatOrNull(),
                    gender.takeIf { it.isNotBlank() },
                    selectedTargets.joinToString(",").takeIf { it.isNotBlank() }
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.surface
            )
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.surface, strokeWidth = 2.dp)
            } else {
                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Shared UI components ──────────────────────────────────────────────────────

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    helperText: String? = null
) {
    val isNumeric = keyboardType != androidx.compose.ui.text.input.KeyboardType.Text
    val (tfv, selectAllMod) = rememberSelectAllState(value)
    val fieldModifier = if (isNumeric) Modifier.fillMaxWidth().then(selectAllMod) else Modifier.fillMaxWidth()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isNumeric) {
            OutlinedTextField(
                value = tfv.value,
                onValueChange = { newTfv ->
                    tfv.value = newTfv
                    onValueChange(newTfv.text)
                },
                label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
                modifier = fieldModifier,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() }
                ),
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
                modifier = fieldModifier,
                keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() }
                ),
                colors = PowerMeDefaults.outlinedTextFieldColors()
            )
        }
        if (helperText != null) {
            Text(
                text = helperText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun SingleChoiceSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            )
        }
    }
}

@Composable
private fun MultiSelectChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    options.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(option, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
