package com.powerme.app.ui.workouts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Two-option bottom sheet shown when workoutStyle == HYBRID.
 * Routes the user to either the strength exercise picker or the FunctionalBlockWizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockOrExerciseSheet(
    onDismiss: () -> Unit,
    onAddStrengthExercise: () -> Unit,
    onAddFunctionalBlock: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add to workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onAddStrengthExercise()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Add Strength Exercise", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onAddFunctionalBlock()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Add Functional Block", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
