package com.powerme.app.ui.workout.runner

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powerme.app.ui.workout.RecipeRow

@Composable
fun BlockRecipeRow(row: RecipeRow, modifier: Modifier = Modifier) {
    val line = when {
        row.reps != null -> "${row.reps} ${row.exerciseName}"
        row.holdSeconds != null -> "${row.holdSeconds}s ${row.exerciseName}"
        else -> row.exerciseName
    }
    Text(
        text = line,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 20.dp),
    )
}
