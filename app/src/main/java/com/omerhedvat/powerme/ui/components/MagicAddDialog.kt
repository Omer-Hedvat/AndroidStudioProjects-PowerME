package com.omerhedvat.powerme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.ui.theme.CyberLime
import com.omerhedvat.powerme.ui.theme.OledBlack
import com.omerhedvat.powerme.ui.theme.SlateGrey

/**
 * Magic Add Dialog — Gemini-powered exercise creation.
 *
 * User types an exercise name → Gemini returns:
 *   - Muscle group
 *   - Equipment type
 *   - YouTube video ID (from RP/Jeff Nippard/Athlean-X)
 *   - Setup notes (biomechanics-aware, tailored to user profile)
 *
 * On confirm, saves the exercise with isCustom = true and returns it
 * to the caller via onExerciseAdded callback.
 */
@Composable
fun MagicAddDialog(
    onExerciseAdded: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MagicAddViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var exerciseName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-focus the text field when the dialog opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.reset()
    }

    // Auto-dismiss after save
    LaunchedEffect(uiState) {
        if (uiState is MagicAddUiState.Saved) {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateGrey,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyberLime,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Magic Add",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberLime
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type any exercise name — AI will fill in the details.",
                    fontSize = 13.sp,
                    color = CyberLime.copy(alpha = 0.7f)
                )

                // Exercise name input
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = {
                        exerciseName = it
                        // Reset if user changes input after a result
                        if (uiState is MagicAddUiState.Found || uiState is MagicAddUiState.Error) {
                            viewModel.reset()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Exercise name", color = CyberLime.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. Cable Chest Fly", color = CyberLime.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberLime,
                        unfocusedBorderColor = CyberLime.copy(alpha = 0.4f),
                        focusedTextColor = CyberLime,
                        unfocusedTextColor = CyberLime,
                        cursorColor = CyberLime
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            viewModel.searchExercise(exerciseName)
                        }
                    ),
                    singleLine = true,
                    enabled = uiState !is MagicAddUiState.Loading
                )

                // No API key warning
                if (!viewModel.hasApiKey) {
                    Text(
                        text = "⚠ Gemini API key required. Configure it in Settings.",
                        fontSize = 12.sp,
                        color = CyberLime.copy(alpha = 0.6f)
                    )
                }

                // Loading indicator
                AnimatedVisibility(
                    visible = uiState is MagicAddUiState.Loading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = CyberLime,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Searching...",
                            fontSize = 13.sp,
                            color = CyberLime.copy(alpha = 0.8f)
                        )
                    }
                }

                // Error state
                AnimatedVisibility(
                    visible = uiState is MagicAddUiState.Error,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val error = (uiState as? MagicAddUiState.Error)?.message ?: ""
                    Text(
                        text = "✗ $error",
                        fontSize = 13.sp,
                        color = CyberLime.copy(alpha = 0.6f)
                    )
                }

                // Result card
                AnimatedVisibility(
                    visible = uiState is MagicAddUiState.Found,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val exercise = (uiState as? MagicAddUiState.Found)?.exercise
                    if (exercise != null) {
                        ExerciseResultCard(exercise = exercise)
                    }
                }
            }
        },
        confirmButton = {
            when (val state = uiState) {
                is MagicAddUiState.Found -> {
                    Button(
                        onClick = {
                            viewModel.saveExercise(state.exercise) { saved ->
                                onExerciseAdded(saved)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime,
                            contentColor = OledBlack
                        )
                    ) {
                        Text("ADD", fontWeight = FontWeight.Bold)
                    }
                }
                is MagicAddUiState.Loading -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime.copy(alpha = 0.3f),
                            contentColor = OledBlack
                        )
                    ) {
                        Text("SEARCH", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.searchExercise(exerciseName)
                        },
                        enabled = exerciseName.isNotBlank() && viewModel.hasApiKey,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberLime,
                            contentColor = OledBlack
                        )
                    ) {
                        Text("SEARCH", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CyberLime.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Card showing the exercise details returned by Gemini.
 */
@Composable
private fun ExerciseResultCard(exercise: Exercise) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CyberLime.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✓ Found",
                fontSize = 11.sp,
                color = CyberLime,
                fontWeight = FontWeight.Bold
            )

            ResultRow(label = "Muscle group", value = exercise.muscleGroup)
            ResultRow(label = "Equipment", value = exercise.equipmentType)
            ResultRow(
                label = "Rest timer",
                value = "${exercise.restDurationSeconds}s"
            )

            if (exercise.youtubeVideoId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = CyberLime,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Video found",
                        fontSize = 12.sp,
                        color = CyberLime
                    )
                }
            }

            if (exercise.setupNotes != null) {
                Text(
                    text = exercise.setupNotes,
                    fontSize = 11.sp,
                    color = CyberLime.copy(alpha = 0.7f),
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = CyberLime.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = CyberLime,
            fontWeight = FontWeight.Medium
        )
    }
}
