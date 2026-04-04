package com.powerme.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.powerme.app.ui.theme.JetBrainsMono
import com.powerme.app.ui.theme.MonoTextStyle
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.TimerRed
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(viewModel: ToolsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── 2×2 Mode Grid (shown when idle and no active timer) ──────
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (state.phase == TimerPhase.IDLE && !state.isRunning) {
                TimerModeGrid(
                    selectedMode = state.mode,
                    onModeSelected = viewModel::setMode
                )
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // Timer display + config inputs
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimerDisplay(state = state)
                if (!state.isRunning && state.phase == TimerPhase.IDLE) {
                    Spacer(modifier = Modifier.height(24.dp))
                    ConfigInputs(state = state, viewModel = viewModel)
                }
            }
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
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
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
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
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
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
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
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
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
        TimerPhase.IDLE -> Color.Transparent
    }
    val idlePrimaryColor = MaterialTheme.colorScheme.primary
    val textColor = when (state.phase) {
        TimerPhase.WORK -> TimerGreen
        TimerPhase.REST -> TimerRed
        TimerPhase.IDLE -> idlePrimaryColor
    }

    val displayValue = when (state.mode) {
        TimerMode.STOPWATCH -> state.elapsedSeconds
        else -> state.displaySeconds
    }
    val minutes = displayValue / 60
    val seconds = displayValue % 60

    // Compute progress (1f=full, 0f=done). Null means no progress line.
    val progress: Float? = when {
        state.phase == TimerPhase.IDLE -> null
        state.mode == TimerMode.STOPWATCH -> null
        state.mode == TimerMode.COUNTDOWN -> {
            val total = (state.countdownMinutes * 60 + state.countdownSeconds).coerceAtLeast(1)
            state.displaySeconds.toFloat() / total
        }
        state.mode == TimerMode.EMOM -> state.displaySeconds.toFloat() / state.emomRoundSeconds.coerceAtLeast(1)
        state.mode == TimerMode.TABATA && state.phase == TimerPhase.WORK ->
            state.displaySeconds.toFloat() / state.workSeconds.coerceAtLeast(1)
        state.mode == TimerMode.TABATA && state.phase == TimerPhase.REST ->
            state.displaySeconds.toFloat() / state.restSeconds.coerceAtLeast(1)
        else -> null
    }

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
            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = TimerGreen,
                    trackColor = TimerGreen.copy(alpha = 0.2f)
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
                Text("Skip last rest period", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Switch(
                    checked = state.tabataSkipLastRest,
                    onCheckedChange = { viewModel.toggleTabataSkipLastRest() },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
        TimerMode.COUNTDOWN -> {
            CountdownRoulettePicker(
                minutes = state.countdownMinutes,
                seconds = state.countdownSeconds,
                onMinutesChanged = viewModel::updateCountdownMinutes,
                onSecondsChanged = viewModel::updateCountdownSeconds,
                onPreset = viewModel::setCountdownPreset
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
private fun WheelPicker(
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeightDp = 48.dp
    val visibleItems = 3
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snapBehavior = rememberSnapFlingBehavior(listState)

    // Scroll to selected item on external state change (e.g. preset tap)
    LaunchedEffect(selected) {
        val index = (selected - range.first).coerceIn(0, range.last - range.first)
        listState.scrollToItem(index)
    }

    // Report settled center item to ViewModel
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            onSelected(range.first + centerIndex)
        }
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(itemHeightDp * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        // Highlight bar behind center item
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    MaterialTheme.shapes.small
                )
        )
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(range.last - range.first + 1) { idx ->
                val value = range.first + idx
                val isCurrent = value == selected
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(itemHeightDp)
                        .clickable {
                            coroutineScope.launch { listState.animateScrollToItem(idx) }
                            onSelected(value)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(value),
                        style = MonoTextStyle.copy(
                            fontSize = 22.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownRoulettePicker(
    minutes: Int,
    seconds: Int,
    onMinutesChanged: (Int) -> Unit,
    onSecondsChanged: (Int) -> Unit,
    onPreset: (Int) -> Unit
) {
    val presets = listOf(30 to "0:30", 60 to "1:00", 90 to "1:30", 120 to "2:00")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WheelPicker(range = 0..59, selected = minutes, onSelected = onMinutesChanged)
            Text(
                text = ":",
                style = MonoTextStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            WheelPicker(range = 0..59, selected = seconds, onSelected = onSecondsChanged)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            presets.forEach { (totalSecs, label) ->
                SuggestionChip(
                    onClick = { onPreset(totalSecs) },
                    label = { Text(label, style = MonoTextStyle.copy(fontSize = 12.sp)) },
                    modifier = Modifier.weight(1f),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
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
        label = { Text(label, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = MonoTextStyle,
        singleLine = true
    )
}
