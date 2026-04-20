package com.powerme.app.ui.exercises.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powerme.app.data.database.ExerciseWorkoutHistoryRow

@Composable
internal fun HistoryTabContent(
    history: List<ExerciseWorkoutHistoryRow>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onWorkoutClick: (String) -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No workout history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        items(
            items = history,
            key = { row -> "${row.workoutId}_${row.timestamp}" }
        ) { row ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                WorkoutHistoryRow(
                    row = row,
                    onClick = { onWorkoutClick(row.workoutId) }
                )
            }
        }

        item(key = "history_footer") {
            if (hasMore) {
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Load more")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
