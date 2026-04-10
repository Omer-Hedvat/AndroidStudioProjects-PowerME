package com.powerme.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.powerme.app.ui.theme.PowerMeDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.powerme.app.data.database.Exercise

/**
 * Magic Add Dialog — local exercise search with Gemini-powered creation fallback.
 *
 * User types a name → local DB results appear instantly (prefix-priority ranked).
 * Selecting an existing exercise adds it immediately (no Gemini call).
 * "Create new" at the bottom of results triggers Gemini enrichment for new exercises.
 *
 * On confirm (ADD), saves the Gemini-created exercise with isCustom = true and
 * returns it to the caller via onExerciseAdded callback.
 */
@Composable
fun MagicAddDialog(
    onExerciseAdded: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    viewModel: MagicAddViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Add Exercise",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Search exercises or create a new one.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )

                // Exercise name input — triggers local search on every keystroke
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = {
                        exerciseName = it
                        viewModel.onSearchChanged(it)
                        // Reset Gemini creation state if user edits after a result
                        if (uiState is MagicAddUiState.Found || uiState is MagicAddUiState.Error) {
                            viewModel.reset()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Exercise name", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. Bench Press", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) },
                    colors = PowerMeDefaults.outlinedTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboard?.hide() }
                    ),
                    singleLine = true,
                    enabled = uiState !is MagicAddUiState.Loading
                )

                // Local search results (shown while user types)
                if (exerciseName.isNotBlank() && uiState !is MagicAddUiState.Found) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(searchResults) { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onExerciseAdded(exercise)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = exercise.name,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = exercise.muscleGroup,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        }
                        // "Create new" row — shown when list is not full (< 25 results)
                        if (searchResults.size < 25 && uiState !is MagicAddUiState.Loading) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            keyboard?.hide()
                                            viewModel.searchExercise(exerciseName)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Create \"$exerciseName\"",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Loading indicator (Gemini creation in progress)
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
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Creating exercise…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Error state (Gemini creation failed)
                AnimatedVisibility(
                    visible = uiState is MagicAddUiState.Error,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val error = (uiState as? MagicAddUiState.Error)?.message ?: ""
                    Text(
                        text = "✗ $error",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }

                // Result card (Gemini enrichment returned)
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
            if (uiState is MagicAddUiState.Found) {
                val state = uiState as MagicAddUiState.Found
                Button(
                    onClick = {
                        viewModel.saveExercise(state.exercise) { saved ->
                            onExerciseAdded(saved)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("ADD", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            }
        }
    )
}

/**
 * Card showing the exercise details returned by Gemini (create new flow).
 */
@Composable
private fun ExerciseResultCard(exercise: Exercise) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✓ Ready to add",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Video found",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (exercise.setupNotes != null) {
                Text(
                    text = exercise.setupNotes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}
