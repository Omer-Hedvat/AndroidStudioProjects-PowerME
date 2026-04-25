package com.powerme.app.ui.workout.runner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powerme.app.ui.workout.FunctionalBlockRunnerState
import com.powerme.app.ui.workout.RecipeRow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BlockFinishResult(
    val rounds: Int?,
    val extraReps: Int?,
    val finishSeconds: Int?,
    val rpe: Int?,
    val perExerciseRpeJson: String?,
    val notes: String?,
)

private enum class RpeMode { OVERALL, PER_EXERCISE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockFinishSheet(
    blockType: String,
    state: FunctionalBlockRunnerState,
    onDismiss: () -> Unit,
    onSave: (BlockFinishResult) -> Unit,
) {
    var rounds by remember { mutableStateOf(state.roundTapCount.coerceAtLeast(state.currentRound)) }
    var extraReps by remember { mutableStateOf(0) }
    var rpeMode by remember { mutableStateOf(RpeMode.OVERALL) }
    var overallRpe by remember { mutableStateOf<Int?>(null) }
    val perExerciseRpe = remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var notes by remember { mutableStateOf("") }
    var showSwitchConfirm by remember { mutableStateOf<RpeMode?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val finishSeconds = state.elapsedSeconds

    val saveValid = when (rpeMode) {
        RpeMode.OVERALL -> true
        RpeMode.PER_EXERCISE -> perExerciseRpe.value.isNotEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            Text(
                text = "Finish $blockType block",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            // Block-specific top section
            when (blockType) {
                "AMRAP" -> {
                    StepperRow("Rounds", rounds) { rounds = it.coerceAtLeast(0) }
                    Spacer(Modifier.height(8.dp))
                    StepperRow("Extra reps", extraReps) { extraReps = it.coerceAtLeast(0) }
                }
                "RFT" -> {
                    Text("Time: ${formatMmSs(finishSeconds)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StepperRow("Rounds completed", rounds) { rounds = it.coerceAtLeast(0) }
                }
                "EMOM" -> {
                    Text(
                        "Rounds: $rounds / ${state.totalRounds}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val pct = if (state.totalRounds > 0) (rounds * 100) / state.totalRounds else 0
                    Text("Completion: $pct%", style = MaterialTheme.typography.bodyMedium)
                }
                "TABATA" -> {
                    Text("Elapsed: ${formatMmSs(finishSeconds)}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StepperRow("Rounds completed", rounds) { rounds = it.coerceAtLeast(0) }
                }
            }

            Spacer(Modifier.height(16.dp))

            // RPE mode toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = rpeMode == RpeMode.OVERALL,
                    onClick = {
                        if (rpeMode != RpeMode.OVERALL && perExerciseRpe.value.isNotEmpty()) {
                            showSwitchConfirm = RpeMode.OVERALL
                        } else {
                            rpeMode = RpeMode.OVERALL
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Overall") }
                SegmentedButton(
                    selected = rpeMode == RpeMode.PER_EXERCISE,
                    onClick = {
                        if (rpeMode != RpeMode.PER_EXERCISE && overallRpe != null) {
                            showSwitchConfirm = RpeMode.PER_EXERCISE
                        } else {
                            rpeMode = RpeMode.PER_EXERCISE
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Per exercise") }
            }

            Spacer(Modifier.height(12.dp))

            when (rpeMode) {
                RpeMode.OVERALL -> RpeChipRow(
                    selected = overallRpe,
                    onSelect = { overallRpe = it },
                )
                RpeMode.PER_EXERCISE -> {
                    state.plan.recipe.forEach { row ->
                        Text(row.exerciseName, style = MaterialTheme.typography.bodyMedium)
                        RpeChipRow(
                            selected = perExerciseRpe.value[row.exerciseId],
                            onSelect = { v ->
                                perExerciseRpe.value = if (v == null) {
                                    perExerciseRpe.value - row.exerciseId
                                } else {
                                    perExerciseRpe.value + (row.exerciseId to v)
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val rpe = if (rpeMode == RpeMode.OVERALL) overallRpe else null
                    val perJson = if (rpeMode == RpeMode.PER_EXERCISE && perExerciseRpe.value.isNotEmpty()) {
                        Json.encodeToString(perExerciseRpe.value.mapKeys { it.key.toString() })
                    } else null
                    onSave(
                        BlockFinishResult(
                            rounds = rounds.takeIf { it > 0 },
                            extraReps = if (blockType == "AMRAP") extraReps.takeIf { it > 0 } else null,
                            finishSeconds = if (blockType in listOf("RFT", "TABATA")) finishSeconds else null,
                            rpe = rpe,
                            perExerciseRpeJson = perJson,
                            notes = notes.takeIf { it.isNotBlank() },
                        )
                    )
                },
                enabled = saveValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }

    showSwitchConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showSwitchConfirm = null },
            title = { Text("Switch RPE mode?") },
            text = { Text("This will clear the values you've already entered.") },
            confirmButton = {
                TextButton(onClick = {
                    if (target == RpeMode.OVERALL) perExerciseRpe.value = emptyMap() else overallRpe = null
                    rpeMode = target
                    showSwitchConfirm = null
                }) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - 1) }) { Text("−", fontWeight = FontWeight.Bold) }
            Text("$value", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onChange(value + 1) }) { Text("+", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun RpeChipRow(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(60, 70, 80, 90, 95, 100).forEach { rpe ->
            FilterChip(
                selected = selected == rpe,
                onClick = { onSelect(if (selected == rpe) null else rpe) },
                label = { Text("${rpe / 10}${if (rpe % 10 != 0) ".${rpe % 10}" else ""}") },
            )
        }
    }
}

private fun formatMmSs(secs: Int): String {
    val m = secs / 60
    val s = secs % 60
    return "%d:%02d".format(m, s)
}
