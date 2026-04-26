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
import com.powerme.app.ui.theme.TimerRed
import com.powerme.app.ui.workout.FunctionalBlockRunnerState

@Composable
fun AmrapOverlay(
    state: FunctionalBlockRunnerState,
    onTap: () -> Unit,
    onFinishClick: () -> Unit,
    onAbandonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbandonConfirm by remember { mutableStateOf(false) }

    val remaining = state.displaySeconds.coerceAtLeast(0)
    val danger = remaining <= 10

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
                text = state.blockName ?: "AMRAP",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatMmSs(remaining),
                style = TimerDigitsXL,
                color = if (danger) TimerRed else TimerGreen,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Round ${state.roundTapCount + 1}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                state.plan.recipe.forEach { row -> BlockRecipeRow(row) }
            }
            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                BlindTapZone(
                    currentRound = state.roundTapCount + 1,
                    onTap = onTap,
                    label = "ROUND ✓",
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onFinishClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text("FINISH BLOCK") }
                OutlinedButton(
                    onClick = { showAbandonConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) { Text("Abandon Block") }
            }
        }
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
