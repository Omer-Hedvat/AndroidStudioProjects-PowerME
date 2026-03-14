package com.omerhedvat.powerme.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel

private val TRAINING_TARGET_OPTIONS = listOf(
    "Hypertrophy", "Fat Loss", "Body Recomposition", "Strength", "Cardio", "Longevity"
)

@Composable
fun ProfileSetupScreen(
    onProfileSaved: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var weightKg by remember { mutableStateOf("") }
    var bodyFatPercent by remember { mutableStateOf("") }
    var occupationType by remember { mutableStateOf("SEDENTARY") }
    var parentalLoad by remember { mutableStateOf("0") }
    var chronotype by remember { mutableStateOf("NEUTRAL") }
    var averageSleepHours by remember { mutableStateOf("7") }
    var gender by remember { mutableStateOf("") }
    var selectedTargets by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onProfileSaved()
    }

    val focusManager = LocalFocusManager.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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

            Spacer(modifier = Modifier.height(32.dp))

            ProfileTextField(
                value = name, onValueChange = { name = it }, label = "Name",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                value = age, onValueChange = { age = it }, label = "Age",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                value = heightCm, onValueChange = { heightCm = it }, label = "Height (cm)",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                value = weightKg, onValueChange = { weightKg = it }, label = "Weight (kg) — optional",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileTextField(
                value = bodyFatPercent, onValueChange = { bodyFatPercent = it }, label = "Body Fat % — optional",
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
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

            // Gender selector
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

            // Training targets multi-select
            Text("Training Targets", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            MultiSelectChips(
                options = TRAINING_TARGET_OPTIONS,
                selected = selectedTargets,
                onToggle = { option ->
                    selectedTargets = if (option in selectedTargets) {
                        selectedTargets - option
                    } else {
                        selectedTargets + option
                    }
                }
            )

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.saveProfile(
                        name = name.trim().takeIf { it.isNotBlank() },
                        age = age.toIntOrNull(),
                        heightCm = heightCm.toFloatOrNull(),
                        weightKg = weightKg.toFloatOrNull(),
                        bodyFatPercent = bodyFatPercent.toFloatOrNull(),
                        occupationType = occupationType.takeIf { it.isNotBlank() },
                        parentalLoad = parentalLoad.toIntOrNull(),
                        chronotype = chronotype.takeIf { it.isNotBlank() },
                        averageSleepHours = averageSleepHours.toFloatOrNull(),
                        gender = gender.takeIf { it.isNotBlank() },
                        trainingTargets = selectedTargets.joinToString(",").takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.surface)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.surface, strokeWidth = 2.dp)
                } else {
                    Text("Enter the War Room", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
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
