package com.omerhedvat.powerme.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.omerhedvat.powerme.ui.theme.NavySurface
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 2×2 Mode Grid (shown when idle and no active timer) ──────
        if (state.phase == TimerPhase.IDLE && !state.isRunning) {
            TimerModeGrid(
                selectedMode = state.mode,
                onModeSelected = viewModel::setMode
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Show compact mode indicator during active timer
            val activeLabel = when (state.mode) {
                TimerMode.STOPWATCH -> "Stopwatch"
                TimerMode.COUNTDOWN -> "Timer"
                TimerMode.TABATA    -> "Tabata"
                TimerMode.EMOM      -> "EMOM"
            }
            Text(
                text = activeLabel,
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                color = NeonBlue.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Timer display
        TimerDisplay(state = state)

        Spacer(modifier = Modifier.height(24.dp))

        // Config inputs (only when idle)
        if (!state.isRunning && state.phase == TimerPhase.IDLE) {
            ConfigInputs(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Control buttons — persistent side-by-side layout for Tabata/EMOM;
        // toggle behaviour preserved for Stopwatch and Countdown.
        if (state.mode == TimerMode.TABATA || state.mode == TimerMode.EMOM) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Button(
                    onClick = {
                        if (state.isRunning) viewModel.pauseTimer() else viewModel.startTimer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) ElectricBlue else NeonBlue,
                        contentColor = DeepNavy
                    ),
                    modifier = Modifier.weight(1f).height(56.dp)
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
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", maxLines = 1)
                }
            }
        } else {
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
                    modifier = Modifier.width(120.dp).height(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TimerModeGrid(
    selectedMode: TimerMode,
    onModeSelected: (TimerMode) -> Unit
) {
    val modeInfo = listOf(
        Triple(TimerMode.STOPWATCH, Icons.Default.PlayCircle, "Count Up"),
        Triple(TimerMode.COUNTDOWN, Icons.Default.HourglassBottom, "Count Down"),
        Triple(TimerMode.TABATA,    Icons.Default.Timer,       "Work / Rest"),
        Triple(TimerMode.EMOM,      Icons.Default.Repeat,      "Every Minute")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modeInfo.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (mode, icon, desc) ->
                    ModeCard(
                        mode = mode,
                        icon = icon,
                        description = desc,
                        isSelected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: TimerMode,
    icon: ImageVector,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NeonBlue else NavySurface
        )
    ) {
        val label = when (mode) {
            TimerMode.STOPWATCH -> "Stopwatch"
            TimerMode.COUNTDOWN -> "Timer"
            TimerMode.TABATA    -> "Tabata"
            TimerMode.EMOM      -> "EMOM"
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) DeepNavy else NeonBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) DeepNavy else NeonBlue,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = if (isSelected) DeepNavy.copy(alpha = 0.7f) else NeonBlue.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1
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
                            TimerMode.EMOM -> "Round ${state.currentRound} / ${state.emomTotalRounds}"
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
                label = "Round Duration (sec)",
                value = state.emomRoundSecondsText,
                onValueChange = viewModel::updateEmomRoundSecondsText
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Number of Rounds",
                value = state.emomTotalRoundsText,
                onValueChange = viewModel::updateEmomTotalRoundsText
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Warn at X seconds remaining (optional)",
                value = state.emomWarnAtSecondsText,
                onValueChange = viewModel::updateEmomWarnAtSecondsText
            )
        }
        TimerMode.TABATA -> {
            TimerConfigField(
                label = "Work (seconds)",
                value = state.workSecondsText,
                onValueChange = viewModel::updateWorkSecondsText
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Rest (seconds)",
                value = state.restSecondsText,
                onValueChange = viewModel::updateRestSecondsText
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Rounds",
                value = state.totalRoundsText,
                onValueChange = viewModel::updateTotalRoundsText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Skip last rest period", color = Color.White, fontSize = 14.sp)
                Switch(
                    checked = state.tabataSkipLastRest,
                    onCheckedChange = { viewModel.toggleTabataSkipLastRest() },
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepNavy, checkedTrackColor = NeonBlue)
                )
            }
        }
        TimerMode.COUNTDOWN -> {
            TimerConfigField(
                label = "Duration (seconds)",
                value = state.countdownText,
                onValueChange = viewModel::updateCountdownText
            )
            Spacer(modifier = Modifier.height(8.dp))
            TimerConfigField(
                label = "Alert before finish (sec, optional)",
                value = state.countdownWarnAtSecondsText,
                onValueChange = viewModel::updateCountdownWarnAtSecondsText
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
        onValueChange = { newText ->
            if (newText.isEmpty() || newText.all { it.isDigit() }) onValueChange(newText)
        },
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
