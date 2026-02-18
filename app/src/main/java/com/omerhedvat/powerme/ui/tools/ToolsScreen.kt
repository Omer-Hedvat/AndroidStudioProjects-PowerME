package com.omerhedvat.powerme.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.ui.theme.DeepNavy
import com.omerhedvat.powerme.ui.theme.ElectricBlue
import com.omerhedvat.powerme.ui.theme.JetBrainsMono
import com.omerhedvat.powerme.ui.theme.MonoTextStyle
import com.omerhedvat.powerme.ui.theme.NeonBlue
import com.omerhedvat.powerme.ui.theme.TimerGreen
import com.omerhedvat.powerme.ui.theme.TimerRed

@Composable
fun ToolsScreen(viewModel: ToolsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode selector
        ModeSelector(
            selectedMode = state.mode,
            isRunning = state.isRunning,
            onModeSelected = viewModel::setMode
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Timer display
        TimerDisplay(state = state)

        Spacer(modifier = Modifier.height(24.dp))

        // Config inputs (only when idle)
        if (!state.isRunning && state.phase == TimerPhase.IDLE) {
            ConfigInputs(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (state.isRunning) viewModel.pauseTimer() else viewModel.startTimer()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRunning) ElectricBlue else NeonBlue,
                    contentColor = DeepNavy
                ),
                modifier = Modifier.width(140.dp).height(56.dp)
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isRunning) "Pause" else "Start"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isRunning) "Pause" else "Start",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            OutlinedButton(
                onClick = viewModel::resetTimer,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue),
                modifier = Modifier.width(100.dp).height(56.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: TimerMode,
    isRunning: Boolean,
    onModeSelected: (TimerMode) -> Unit
) {
    val modes = TimerMode.values()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selectedMode
            FilterChip(
                selected = isSelected,
                onClick = { if (!isRunning) onModeSelected(mode) },
                label = {
                    Text(
                        text = mode.name,
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMono
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonBlue,
                    selectedLabelColor = DeepNavy,
                    containerColor = DeepNavy,
                    labelColor = NeonBlue
                )
            )
        }
    }
}

@Composable
private fun TimerDisplay(state: ToolsUiState) {
    val backgroundColor = when (state.phase) {
        TimerPhase.WORK -> TimerGreen.copy(alpha = 0.2f)
        TimerPhase.REST -> TimerRed.copy(alpha = 0.2f)
        TimerPhase.IDLE -> DeepNavy
    }
    val textColor = when (state.phase) {
        TimerPhase.WORK -> TimerGreen
        TimerPhase.REST -> TimerRed
        TimerPhase.IDLE -> NeonBlue
    }

    val displayValue = when (state.mode) {
        TimerMode.STOPWATCH -> state.elapsedSeconds
        else -> state.displaySeconds
    }
    val minutes = displayValue / 60
    val seconds = displayValue % 60

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, MaterialTheme.shapes.large)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MonoTextStyle.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                ),
                textAlign = TextAlign.Center
            )
            if (state.mode == TimerMode.TABATA || state.mode == TimerMode.EMOM) {
                if (state.currentRound > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (state.mode) {
                            TimerMode.TABATA -> "Round ${state.currentRound} / ${state.totalRounds}"
                            TimerMode.EMOM -> "Minute ${state.currentRound} / ${state.emomMinutes}"
                            else -> ""
                        },
                        style = MonoTextStyle.copy(fontSize = 18.sp, color = textColor.copy(alpha = 0.8f))
                    )
                }
            }
            if (state.phase != TimerPhase.IDLE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.phase.name,
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.7f),
                    fontFamily = JetBrainsMono
                )
            }
        }
    }
}

@Composable
private fun ConfigInputs(state: ToolsUiState, viewModel: ToolsViewModel) {
    when (state.mode) {
        TimerMode.EMOM -> {
            TimerConfigField(
                label = "Duration (minutes)",
                value = state.emomMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateEmomMinutes) }
            )
        }
        TimerMode.TABATA -> {
            TimerConfigField(
                label = "Work (seconds)",
                value = state.workSeconds.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateWorkSeconds) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Rest (seconds)",
                value = state.restSeconds.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateRestSeconds) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Rounds",
                value = state.totalRounds.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateTotalRounds) }
            )
        }
        TimerMode.COUNTDOWN -> {
            TimerConfigField(
                label = "Duration (seconds)",
                value = state.countdownInputSeconds.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateCountdownSeconds) }
            )
        }
        TimerMode.STOPWATCH -> {
            // No config needed for stopwatch
        }
    }
}

@Composable
private fun TimerConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = NeonBlue.copy(alpha = 0.7f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonBlue,
            unfocusedBorderColor = NeonBlue.copy(alpha = 0.4f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NeonBlue
        ),
        textStyle = MonoTextStyle,
        singleLine = true
    )
}
