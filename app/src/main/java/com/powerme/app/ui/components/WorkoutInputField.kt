package com.powerme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.util.SurgicalValidator
import kotlinx.coroutines.delay

/**
 * Computes the new field value after applying [delta] to [text].
 *
 * @param text    Raw string from the input field.
 * @param delta   Positive to increment, negative to decrement.
 * @param isInteger  true for reps (whole numbers only), false for decimal weight.
 * @return New string value, or null if no change should be applied
 *         (e.g. decrement on an empty field, or invalid input).
 */
internal fun applyAccessoryDelta(text: String, delta: Double, isInteger: Boolean): String? {
    return if (isInteger) {
        when (val r = SurgicalValidator.parseReps(text)) {
            is SurgicalValidator.ValidationResult.Valid -> {
                val newVal = maxOf(0.0, r.value + delta)
                newVal.toInt().toString()
            }
            is SurgicalValidator.ValidationResult.Empty -> {
                if (delta > 0) "1" else null
            }
            else -> null
        }
    } else {
        when (val r = SurgicalValidator.parseDecimal(text)) {
            is SurgicalValidator.ValidationResult.Valid -> {
                val newVal = maxOf(0.0, r.value + delta)
                if (newVal % 1.0 == 0.0) newVal.toInt().toString()
                else "%.1f".format(newVal)
            }
            is SurgicalValidator.ValidationResult.Empty -> {
                if (delta > 0) {
                    if (delta % 1.0 == 0.0) delta.toInt().toString()
                    else "%.1f".format(delta)
                } else null
            }
            else -> null
        }
    }
}

/**
 * Returns a [TextFieldValue] state and a [Modifier] that selects all text when the field gains focus.
 * A short delay lets the IME set the cursor first; we then override with the full-text selection.
 *
 * Usage:
 *   val (tfv, selMod) = rememberSelectAllState(externalText)
 *   OutlinedTextField(value = tfv.value, onValueChange = { tfv.value = it }, modifier = Modifier.then(selMod))
 */
@Composable
fun rememberSelectAllState(text: String): Pair<MutableState<TextFieldValue>, Modifier> {
    val tfv = remember { mutableStateOf(TextFieldValue(text)) }
    val isFocused = remember { mutableStateOf(false) }

    // Sync when external value changes (e.g. ViewModel update)
    LaunchedEffect(text) {
        if (tfv.value.text != text) tfv.value = TextFieldValue(text)
    }

    // Wait for IME cursor placement, then select all
    LaunchedEffect(isFocused.value) {
        if (isFocused.value) {
            delay(50)
            val t = tfv.value.text
            tfv.value = tfv.value.copy(selection = TextRange(0, t.length))
        }
    }

    val modifier = Modifier.onFocusChanged { state -> isFocused.value = state.isFocused }
    return tfv to modifier
}

@Composable
fun WorkoutInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Decimal,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    /** When true, registers decrement/decrement lambdas with [LocalKeyboardAccessoryRegistrar] on focus. */
    accessoryEnabled: Boolean = false,
    /** Step size used by the accessory bar (ignored for integer/reps fields, which always use 1). */
    accessoryStep: Double = 1.0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val pillShape = MaterialTheme.shapes.small
    val textFieldValue = remember { mutableStateOf(TextFieldValue(value)) }
    val selectAllTrigger = remember { mutableStateOf(0) }
    val wasFocused = remember { mutableStateOf(false) }

    // Keep a stable reference to the latest onValueChange so accessory lambdas never go stale.
    val latestOnValueChange = rememberUpdatedState(onValueChange)

    val registrar = if (accessoryEnabled) LocalKeyboardAccessoryRegistrar.current else null

    // Keep internal state in sync when external value changes (e.g. cascade fill)
    LaunchedEffect(value) {
        if (textFieldValue.value.text != value) {
            textFieldValue.value = TextFieldValue(value)
        }
    }

    // Increment trigger on every tap (press release) so select-all fires each time the user taps
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                selectAllTrigger.value++
            }
        }
    }

    // Wait for IME cursor placement, then select all — fires on every trigger increment
    LaunchedEffect(selectAllTrigger.value) {
        if (selectAllTrigger.value > 0) {
            delay(50)
            val t = textFieldValue.value.text
            textFieldValue.value = textFieldValue.value.copy(selection = TextRange(0, t.length))
        }
    }

    val isInteger = keyboardType == KeyboardType.Number

    BasicTextField(
        value = textFieldValue.value,
        onValueChange = { newValue ->
            textFieldValue.value = newValue
            onValueChange(newValue.text)
        },
        modifier = modifier
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, pillShape)
            .then(
                if (isFocused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, pillShape)
                else Modifier
            )
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !wasFocused.value) {
                    selectAllTrigger.value++
                    // Register accessory bar callbacks when this field gains focus.
                    if (registrar != null) {
                        val step = if (isInteger) 1.0 else accessoryStep
                        registrar.register(
                            {
                                val newText = applyAccessoryDelta(textFieldValue.value.text, -step, isInteger)
                                if (newText != null) {
                                    textFieldValue.value = TextFieldValue(newText, TextRange(newText.length))
                                    latestOnValueChange.value(newText)
                                }
                            },
                            {
                                val newText = applyAccessoryDelta(textFieldValue.value.text, step, isInteger)
                                if (newText != null) {
                                    textFieldValue.value = TextFieldValue(newText, TextRange(newText.length))
                                    latestOnValueChange.value(newText)
                                }
                            }
                        )
                    }
                }
                wasFocused.value = focusState.isFocused
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (textFieldValue.value.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
                innerTextField()
            }
        }
    )
}
