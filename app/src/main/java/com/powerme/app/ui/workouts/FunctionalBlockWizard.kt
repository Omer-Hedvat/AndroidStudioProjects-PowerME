package com.powerme.app.ui.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.data.BlockType
import com.powerme.app.data.autoBlockName
import com.powerme.app.ui.theme.TimerGreen

/**
 * 2-step ModalBottomSheet wizard for creating a functional block.
 *
 * Step 1: Select block type (AMRAP, RFT, EMOM, TABATA).
 * Step 2: Configure block parameters; calls [onBlockCreated] with the built [DraftBlock].
 *
 * The caller is responsible for navigating to the exercise picker after receiving the block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalBlockWizard(
    onDismiss: () -> Unit,
    onBlockCreated: (DraftBlock) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var selectedType by remember { mutableStateOf<BlockType?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sheet drag handle area + title
            WizardHeader(
                step = step,
                onBack = {
                    if (step > 1) step-- else onDismiss()
                }
            )

            when (step) {
                1 -> BlockTypeStep(
                    onTypeSelected = { type ->
                        selectedType = type
                        step = 2
                    }
                )
                2 -> BlockParamsStep(
                    type = selectedType ?: BlockType.AMRAP,
                    onDismiss = onDismiss,
                    onBlockCreated = onBlockCreated
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(step: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(if (step == 1) "Cancel" else "Back")
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (step == 1) "Add Block" else "Configure Block",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        // Phantom spacer to center title
        Box(Modifier.width(72.dp))
    }
}

@Composable
private fun BlockTypeStep(onTypeSelected: (BlockType) -> Unit) {
    val types = listOf(BlockType.AMRAP, BlockType.RFT, BlockType.EMOM, BlockType.TABATA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Choose block type",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // 2x2 grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BlockTypeTile(
                type = types[0],
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(types[0]) }
            )
            BlockTypeTile(
                type = types[1],
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(types[1]) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BlockTypeTile(
                type = types[2],
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(types[2]) }
            )
            BlockTypeTile(
                type = types[3],
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(types[3]) }
            )
        }
    }
}

@Composable
private fun BlockTypeTile(type: BlockType, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TimerGreen
            )
            Text(
                text = type.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockParamsStep(
    type: BlockType,
    onDismiss: () -> Unit,
    onBlockCreated: (DraftBlock) -> Unit
) {
    // Shared state
    var amrapMinutes by remember { mutableIntStateOf(12) }

    var rftRounds by remember { mutableIntStateOf(5) }
    var rftCapMinutes by remember { mutableStateOf("") }

    var emomTotalRounds by remember { mutableIntStateOf(10) }
    var emomIntervalSec by remember { mutableIntStateOf(60) }
    var emomCustomText by remember { mutableStateOf("") }
    var emomCustomSelected by remember { mutableStateOf(false) }
    var emomWarnSec by remember { mutableIntStateOf(10) }

    var tabataWorkSec by remember { mutableIntStateOf(20) }
    var tabataRestSec by remember { mutableIntStateOf(10) }
    var tabataRounds by remember { mutableIntStateOf(8) }
    var tabataSkipLastRest by remember { mutableStateOf(false) }

    var setupOverride by remember { mutableStateOf("") }
    var warnOverride by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    // Auto-name computation
    val autoName = remember(type, amrapMinutes, rftRounds, rftCapMinutes, emomTotalRounds, emomIntervalSec, tabataRounds) {
        val capMins = rftCapMinutes.toIntOrNull()
        val emomDurationMinutes = (emomTotalRounds * emomIntervalSec) / 60
        autoBlockName(
            type = type,
            durationMinutes = if (type == BlockType.EMOM) emomDurationMinutes else amrapMinutes,
            rounds = if (type == BlockType.TABATA) tabataRounds else rftRounds,
            emomIntervalSec = emomIntervalSec,
            capMinutes = capMins
        )
    }
    var blockName by remember(autoName) { mutableStateOf(autoName) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Type-specific parameters
        when (type) {
            BlockType.AMRAP -> AmrapParams(
                minutes = amrapMinutes,
                onMinutesChange = { amrapMinutes = it }
            )
            BlockType.RFT -> RftParams(
                rounds = rftRounds,
                onRoundsChange = { rftRounds = it },
                capMinutes = rftCapMinutes,
                onCapChange = { rftCapMinutes = it }
            )
            BlockType.EMOM -> EmomParams(
                totalRounds = emomTotalRounds,
                onTotalRoundsChange = { emomTotalRounds = it },
                intervalSec = emomIntervalSec,
                onIntervalChange = { sec, isCustom ->
                    emomIntervalSec = sec
                    emomCustomSelected = isCustom
                },
                customText = emomCustomText,
                onCustomTextChange = { emomCustomText = it },
                warnSec = emomWarnSec,
                onWarnSecChange = { emomWarnSec = it }
            )
            BlockType.TABATA -> TabataParams(
                workSec = tabataWorkSec,
                onWorkChange = { tabataWorkSec = it },
                restSec = tabataRestSec,
                onRestChange = { tabataRestSec = it },
                rounds = tabataRounds,
                onRoundsChange = { tabataRounds = it },
                skipLastRest = tabataSkipLastRest,
                onSkipLastRestChange = { tabataSkipLastRest = it }
            )
            else -> {}
        }

        // Block name field
        OutlinedTextField(
            value = blockName,
            onValueChange = { blockName = it },
            label = { Text("Block name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Advanced section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = setupOverride,
                    onValueChange = { setupOverride = it },
                    label = { Text("Setup seconds override") },
                    placeholder = { Text("Use default") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = warnOverride,
                    onValueChange = { warnOverride = it },
                    label = { Text("Warn-at seconds override") },
                    placeholder = { Text("Use default") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Create button
        Button(
            onClick = {
                val block = when (type) {
                    BlockType.AMRAP -> DraftBlock(
                        type = type,
                        name = blockName.ifBlank { autoName },
                        durationSeconds = amrapMinutes * 60,
                        setupSecondsOverride = setupOverride.toIntOrNull(),
                        warnAtSecondsOverride = warnOverride.toIntOrNull()
                    )
                    BlockType.RFT -> DraftBlock(
                        type = type,
                        name = blockName.ifBlank { autoName },
                        targetRounds = rftRounds,
                        durationSeconds = rftCapMinutes.toIntOrNull()?.let { it * 60 },
                        setupSecondsOverride = setupOverride.toIntOrNull(),
                        warnAtSecondsOverride = warnOverride.toIntOrNull()
                    )
                    BlockType.EMOM -> DraftBlock(
                        type = type,
                        name = blockName.ifBlank { autoName },
                        durationSeconds = emomTotalRounds * emomIntervalSec,
                        emomRoundSeconds = emomIntervalSec,
                        setupSecondsOverride = setupOverride.toIntOrNull(),
                        warnAtSecondsOverride = emomWarnSec
                    )
                    BlockType.TABATA -> DraftBlock(
                        type = type,
                        name = blockName.ifBlank { autoName },
                        tabataWorkSeconds = tabataWorkSec,
                        tabataRestSeconds = tabataRestSec,
                        targetRounds = tabataRounds,
                        tabataSkipLastRest = tabataSkipLastRest,
                        setupSecondsOverride = setupOverride.toIntOrNull(),
                        warnAtSecondsOverride = warnOverride.toIntOrNull()
                    )
                    else -> DraftBlock(type = type, name = blockName.ifBlank { autoName })
                }
                onBlockCreated(block)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TimerGreen)
        ) {
            Text("Add exercises →", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun AmrapParams(minutes: Int, onMinutesChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Duration", style = MaterialTheme.typography.labelLarge)
        StepperRow(
            label = "Minutes",
            value = minutes,
            onDecrement = { if (minutes > 1) onMinutesChange(minutes - 1) },
            onIncrement = { if (minutes < 60) onMinutesChange(minutes + 1) }
        )
    }
}

@Composable
private fun RftParams(
    rounds: Int,
    onRoundsChange: (Int) -> Unit,
    capMinutes: String,
    onCapChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rounds For Time", style = MaterialTheme.typography.labelLarge)
        StepperRow(
            label = "Rounds",
            value = rounds,
            onDecrement = { if (rounds > 1) onRoundsChange(rounds - 1) },
            onIncrement = { if (rounds < 20) onRoundsChange(rounds + 1) }
        )
        OutlinedTextField(
            value = capMinutes,
            onValueChange = onCapChange,
            label = { Text("Time cap (minutes, optional)") },
            placeholder = { Text("No cap") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

private val emomPresets = listOf(60, 90, 120, 180, 300)
private val emomPresetLabels = listOf("60s", "90s", "2min", "3min", "5min")

@Composable
private fun EmomParams(
    totalRounds: Int,
    onTotalRoundsChange: (Int) -> Unit,
    intervalSec: Int,
    onIntervalChange: (Int, Boolean) -> Unit,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    warnSec: Int,
    onWarnSecChange: (Int) -> Unit,
) {
    var showCustomField by remember { mutableStateOf(false) }
    val totalSeconds = totalRounds * intervalSec
    val totalLabel = if (totalSeconds % 60 == 0) "${totalSeconds / 60}min total"
                     else "${totalSeconds}s total"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("EMOM Configuration", style = MaterialTheme.typography.labelLarge)
        StepperRow(
            label = "Rounds  ($totalLabel)",
            value = totalRounds,
            onDecrement = { if (totalRounds > 1) onTotalRoundsChange(totalRounds - 1) },
            onIncrement = { if (totalRounds < 60) onTotalRoundsChange(totalRounds + 1) }
        )
        Text("Interval", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Preset chips — horizontally scrollable so all chips (incl. Custom) are reachable
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            emomPresets.forEachIndexed { idx, presetSec ->
                FilterChip(
                    selected = intervalSec == presetSec && !showCustomField,
                    onClick = {
                        showCustomField = false
                        onIntervalChange(presetSec, false)
                    },
                    label = { Text(emomPresetLabels[idx], fontSize = 12.sp) }
                )
            }
            FilterChip(
                selected = showCustomField,
                onClick = { showCustomField = true },
                label = { Text("Custom", fontSize = 12.sp) }
            )
        }
        if (showCustomField) {
            OutlinedTextField(
                value = customText,
                onValueChange = { text ->
                    onCustomTextChange(text)
                    text.toIntOrNull()?.let { onIntervalChange(it, true) }
                },
                label = { Text("Interval (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        StepperRow(
            label = "Warn at (sec)",
            value = warnSec,
            onDecrement = { if (warnSec > 5) onWarnSecChange(warnSec - 5) },
            onIncrement = { if (warnSec < 30) onWarnSecChange(warnSec + 5) }
        )
    }
}

@Composable
private fun TabataParams(
    workSec: Int,
    onWorkChange: (Int) -> Unit,
    restSec: Int,
    onRestChange: (Int) -> Unit,
    rounds: Int,
    onRoundsChange: (Int) -> Unit,
    skipLastRest: Boolean,
    onSkipLastRestChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tabata Configuration", style = MaterialTheme.typography.labelLarge)
        StepperRow(
            label = "Work (seconds)",
            value = workSec,
            onDecrement = { if (workSec > 5) onWorkChange(workSec - 5) },
            onIncrement = { onWorkChange(workSec + 5) }
        )
        StepperRow(
            label = "Rest (seconds)",
            value = restSec,
            onDecrement = { if (restSec > 5) onRestChange(restSec - 5) },
            onIncrement = { onRestChange(restSec + 5) }
        )
        StepperRow(
            label = "Rounds",
            value = rounds,
            onDecrement = { if (rounds > 1) onRoundsChange(rounds - 1) },
            onIncrement = { onRoundsChange(rounds + 1) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Skip last rest",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = skipLastRest,
                onCheckedChange = onSkipLastRestChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(onClick = onDecrement, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            FilledTonalIconButton(onClick = onIncrement, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
        }
    }
}
