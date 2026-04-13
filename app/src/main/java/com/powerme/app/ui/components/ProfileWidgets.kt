package com.powerme.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.ui.theme.PowerMeDefaults

val TRAINING_TARGET_OPTIONS = listOf(
    "Hypertrophy", "Fat Loss", "Body Recomposition", "Strength", "Cardio", "Longevity"
)

@Composable
fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    helperText: String? = null
) {
    val isNumeric = keyboardType != KeyboardType.Text
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
fun SingleChoiceSegmented(
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
fun MultiSelectChips(
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
