package com.powerme.app.ui.workout.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powerme.app.ui.theme.TimerDigitsXL
import com.powerme.app.ui.theme.TimerGreen
import com.powerme.app.ui.workout.FunctionalBlockRunnerState

@Composable
fun RftOverlay(
    state: FunctionalBlockRunnerState,
    onRoundTap: () -> Unit,
    onFinishClick: () -> Unit,
    onAbandonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    val target = state.plan.targetRounds ?: 0
    val capRemaining = state.capRemainingSeconds

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.blockName ?: "RFT",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMmSs(state.elapsedSeconds),
                style = TimerDigitsXL,
                color = TimerGreen,
            )
            Spacer(Modifier.height(4.dp))
            if (capRemaining != null) {
                Text(
                    text = "Cap: ${formatMmSs(capRemaining)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Round ${state.roundTapCount} / $target",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                state.plan.recipe.forEach { row -> BlockRecipeRow(row) }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRoundTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TimerGreen),
                ) { Text("ROUND ✓") }
                Button(
                    onClick = { showFinishConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("FINISH WOD") }
                OutlinedButton(
                    onClick = { showAbandonConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { Text("Abandon Block") }
            }
        }
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finish WOD?") },
            text = { Text("Captured time: ${formatMmSs(state.elapsedSeconds)}.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    onFinishClick()
                }) { Text("Finish") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) { Text("Keep Going") }
            },
        )
    }
    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text("Abandon block?") },
            text = { Text("The block result won't be saved. You'll return to the workout.") },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirm = false
                    onAbandonClick()
                }) { Text("Abandon Block") }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }) { Text("Keep Going") }
            },
        )
    }
}

private fun formatMmSs(secs: Int): String {
    val m = secs / 60
    val s = secs % 60
    return "%d:%02d".format(m, s)
}
