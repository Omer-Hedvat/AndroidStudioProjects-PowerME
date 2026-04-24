package com.powerme.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.powerme.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.powerme.app.ui.components.rememberSelectAllState
import com.powerme.app.ui.theme.PowerMeDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.util.timer.TimerPhase
import com.powerme.app.ui.theme.JetBrainsMono
import com.powerme.app.ui.theme.MonoTextStyle
import com.powerme.app.ui.theme.ProSubGrey
import com.powerme.app.ui.theme.ReadinessAmber
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.theme.TimerRed
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel = hiltViewModel(),
    onTimerStarted: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Content area — mode grid + timer display + config inputs
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 2×2 Mode Grid (shown when idle and no active timer) ──────
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

            Spacer(modifier = Modifier.height(8.dp))

            // Timer display + config inputs
            TimerDisplay(state = state)
            if (!state.isRunning && state.phase == TimerPhase.IDLE) {
                Spacer(modifier = Modifier.height(8.dp))
                ConfigInputs(state = state, viewModel = viewModel)
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
                    .padding(top = 12.dp)
            ) {
                Button(
                    onClick = {
                        if (state.isRunning) viewModel.pauseTimer() else { viewModel.startTimer(); onTimerStarted() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
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
                    modifier = Modifier.weight(1f).height(48.dp)
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
                        if (state.isRunning) viewModel.pauseTimer() else { viewModel.startTimer(); onTimerStarted() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.width(140.dp).height(48.dp)
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
                    modifier = Modifier.width(120.dp).height(48.dp)
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
        Triple(TimerMode.STOPWATCH, painterResource(R.drawable.ic_clock_stopwatch), "Count Up"),
        Triple(TimerMode.COUNTDOWN, painterResource(R.drawable.ic_clock_countdown), "Count Down"),
        Triple(TimerMode.TABATA,    painterResource(R.drawable.ic_clock_tabata),    "Work / Rest"),
        Triple(TimerMode.EMOM,      painterResource(R.drawable.ic_clock_emom),      "Every Minute")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modeInfo.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (mode, icon, desc) ->
                    ModeCard(
                        mode = mode,
                        painter = icon,
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
    painter: Painter,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(52.dp)
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
                painter = painter,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
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
        TimerPhase.SETUP -> ReadinessAmber.copy(alpha = 0.2f)
        TimerPhase.WORK -> TimerGreen.copy(alpha = 0.2f)
        TimerPhase.REST -> TimerRed.copy(alpha = 0.2f)
        TimerPhase.IDLE -> Color.Transparent
    }
    val idlePrimaryColor = MaterialTheme.colorScheme.primary
    val textColor = when (state.phase) {
        TimerPhase.SETUP -> ReadinessAmber
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

    // Centiseconds: shown only while actively running (not during setup/idle/paused)
    val showCentiseconds = state.isRunning
        && state.phase != TimerPhase.IDLE
        && state.phase != TimerPhase.SETUP
        && state.tickEpochMs > 0L
    val isCountingUp = state.mode == TimerMode.STOPWATCH

    // Wall-clock elapsed ms since the current second started; updates at ~60fps
    var elapsedSinceTickMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.tickEpochMs) {
        if (!showCentiseconds) return@LaunchedEffect
        while (true) {
            elapsedSinceTickMs = (System.currentTimeMillis() - state.tickEpochMs).coerceAtLeast(0L)
            kotlinx.coroutines.delay(16L)
        }
    }

    val centiseconds: Int? = if (showCentiseconds) {
        val raw = ((elapsedSinceTickMs % 1000) / 10).toInt()
        if (isCountingUp) raw else (99 - raw).coerceIn(0, 99)
    } else null

    // Compute progress (1f=full, 0f=done). Null means no progress line.
    // When centiseconds are active, interpolate smoothly within the current second.
    val progressTotal: Int? = when {
        state.phase == TimerPhase.SETUP -> state.setupSeconds.coerceAtLeast(1)
        state.phase == TimerPhase.IDLE -> null
        state.mode == TimerMode.STOPWATCH -> null
        state.mode == TimerMode.COUNTDOWN -> (state.countdownMinutes * 60 + state.countdownSeconds).coerceAtLeast(1)
        state.mode == TimerMode.EMOM -> state.emomRoundSeconds.coerceAtLeast(1)
        state.mode == TimerMode.TABATA && state.phase == TimerPhase.WORK -> state.workSeconds.coerceAtLeast(1)
        state.mode == TimerMode.TABATA && state.phase == TimerPhase.REST -> state.restSeconds.coerceAtLeast(1)
        else -> null
    }
    val progress: Float? = progressTotal?.let { total ->
        val base = displayValue.toFloat()
        val smooth = if (centiseconds != null) elapsedSinceTickMs / 1000f else 0f
        ((base - smooth) / total).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, MaterialTheme.shapes.large)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    style = MonoTextStyle.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    ),
                    textAlign = TextAlign.Center
                )
                if (centiseconds != null) {
                    Text(
                        text = ".%02d".format(centiseconds),
                        style = MonoTextStyle.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal,
                            color = textColor.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
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
            // Round Duration and Number of Rounds side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerConfigField(
                    label = "Round (sec)",
                    value = state.emomRoundSecondsText,
                    onValueChange = viewModel::updateEmomRoundSecondsText,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Rounds",
                    value = state.emomTotalRoundsText,
                    onValueChange = viewModel::updateEmomTotalRoundsText,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WarnBeforeFinishField(
                    label = "Warn (sec)",
                    value = state.emomWarnText,
                    autoValue = state.emomRoundSeconds.takeIf { it > 0 }?.div(2),
                    onValueChange = viewModel::updateEmomWarnText,
                    onReset = viewModel::resetEmomWarn,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Setup time (sec)",
                    value = state.setupSecondsText,
                    onValueChange = viewModel::updateSetupSecondsText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        TimerMode.TABATA -> {
            // Work / Rest / Rounds in a single row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerConfigField(
                    label = "Work (s)",
                    value = state.workSecondsText,
                    onValueChange = viewModel::updateWorkSecondsText,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Rest (s)",
                    value = state.restSecondsText,
                    onValueChange = viewModel::updateRestSecondsText,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Rounds",
                    value = state.totalRoundsText,
                    onValueChange = viewModel::updateTotalRoundsText,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WarnBeforeFinishField(
                    label = "Warn – Work",
                    value = state.tabataWorkWarnText,
                    autoValue = state.workSeconds.takeIf { it > 0 }?.div(2),
                    onValueChange = viewModel::updateTabataWorkWarnText,
                    onReset = viewModel::resetTabataWorkWarn,
                    modifier = Modifier.weight(1f)
                )
                WarnBeforeFinishField(
                    label = "Warn – Rest",
                    value = state.tabataRestWarnText,
                    autoValue = state.restSeconds.takeIf { it > 0 }?.div(2),
                    onValueChange = viewModel::updateTabataRestWarnText,
                    onReset = viewModel::resetTabataRestWarn,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Setup (sec)",
                    value = state.setupSecondsText,
                    onValueChange = viewModel::updateSetupSecondsText,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skip last rest", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Text(
                        "End after last work interval",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = state.tabataSkipLastRest,
                    onCheckedChange = { viewModel.toggleTabataSkipLastRest() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WarnBeforeFinishField(
                    label = "Warn (sec)",
                    value = state.countdownWarnText,
                    autoValue = (state.countdownMinutes * 60 + state.countdownSeconds).takeIf { it > 0 }?.div(2),
                    onValueChange = viewModel::updateCountdownWarnText,
                    onReset = viewModel::resetCountdownWarn,
                    modifier = Modifier.weight(1f)
                )
                TimerConfigField(
                    label = "Setup time (sec)",
                    value = state.setupSecondsText,
                    onValueChange = viewModel::updateSetupSecondsText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        TimerMode.STOPWATCH -> {
            TimerConfigField(
                label = "Setup time (sec)",
                value = state.setupSecondsText,
                onValueChange = viewModel::updateSetupSecondsText
            )
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
    val itemHeightDp = 36.dp
    val visibleItems = 3
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snapBehavior = rememberSnapFlingBehavior(listState)

    // Padding items at top/bottom allow the first and last real values to reach the center slot.
    // scrollToItem(k) makes list item k first visible → center slot holds item k+1 (the real value).
    // So scrollToItem(selected - range.first) centers the selected value.
    // After scrolling, firstVisibleItemIndex == (selected - range.first), so reporting
    // range.first + firstVisibleItemIndex correctly recovers the centered value.

    // Scroll so selected item lands in center slot on external change (e.g. preset tap)
    LaunchedEffect(selected) {
        val index = (selected - range.first).coerceIn(0, range.last - range.first)
        listState.scrollToItem(index)
    }

    // Report settled center item to ViewModel
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
                .coerceIn(0, range.last - range.first)
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
            // Top padding item so the first real value can scroll to center
            item { Box(modifier = Modifier.width(64.dp).height(itemHeightDp)) }
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
            // Bottom padding item so the last real value can scroll to center
            item { Box(modifier = Modifier.width(64.dp).height(itemHeightDp)) }
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
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val (tfv, selectAllMod) = rememberSelectAllState(value)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor, shape = MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = tfv.value,
                onValueChange = { newTfv ->
                    val newText = newTfv.text
                    if (newText.isEmpty() || newText.all { it.isDigit() }) {
                        tfv.value = newTfv
                        onValueChange(newText)
                    }
                },
                textStyle = MonoTextStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                modifier = selectAllMod.fillMaxWidth()
            )
        }
        // Focused accent line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = if (isFocused) accentColor else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}

@Composable
private fun WarnBeforeFinishField(
    label: String,
    value: String,
    autoValue: Int?,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (tfv, selectAllMod) = rememberSelectAllState(value)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val isManual = value.isNotBlank()

    val placeholderText: String? = if (isManual) null else when {
        autoValue == null -> "Auto"
        autoValue <= 3    -> "Auto (off)"
        else              -> "Auto (${autoValue}s)"
    }

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor, shape = MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (placeholderText != null) {
                        Text(
                            text = placeholderText,
                            style = MonoTextStyle.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = ProSubGrey,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                    BasicTextField(
                        value = tfv.value,
                        onValueChange = { newTfv ->
                            val newText = newTfv.text
                            if (newText.isEmpty() || newText.all { it.isDigit() }) {
                                tfv.value = newTfv
                                onValueChange(newText)
                            }
                        },
                        textStyle = MonoTextStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isManual) MaterialTheme.colorScheme.onSurface else Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        interactionSource = interactionSource,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                        modifier = selectAllMod.fillMaxWidth()
                    )
                }
                if (isManual) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to auto",
                            tint = ProSubGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = if (isFocused) accentColor else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        )
    }
}
