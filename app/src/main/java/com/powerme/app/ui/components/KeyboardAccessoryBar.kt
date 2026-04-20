package com.powerme.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared state for the keyboard accessory bar: the currently-focused workout input
 * field registers its increment/decrement callbacks here so the screen-level bar can
 * invoke them without prop-drilling through the full composable tree.
 */
data class KeyboardAccessoryRegistrar(
    val register: (decrement: () -> Unit, increment: () -> Unit) -> Unit,
    val unregister: () -> Unit
)

val LocalKeyboardAccessoryRegistrar = compositionLocalOf<KeyboardAccessoryRegistrar?> { null }

/**
 * Horizontal bar with − and + buttons, pinned above the system keyboard.
 * Callers are responsible for positioning (use `imePadding()` + `Alignment.BottomCenter`
 * inside a `Box` so the bar floats above the IME).
 */
@Composable
fun KeyboardAccessoryBar(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrement by 1")
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increment by 1")
            }
        }
    }
}
