package com.powerme.app.ui.theme

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// PowerME Component Defaults — use these instead of repeating inline overrides.
// Rule: No inline OutlinedTextFieldDefaults.colors() in composables. Use outlinedTextFieldColors().
// Rule: No inline CardDefaults.cardColors/cardElevation for standard cards. Use cardColors()/cardElevation().
object PowerMeDefaults {

    // Standardized OutlinedTextField colors.
    // Key change: unfocusedBorderColor uses outlineVariant (neutral grey) instead of
    // primary.copy(alpha=0.4f) — eliminates the purple-tinted unfocused border that creates
    // the "Google Forms" feel.
    @Composable
    fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor    = MaterialTheme.colorScheme.outlineVariant,
        focusedTextColor        = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
        cursorColor             = MaterialTheme.colorScheme.primary,
        focusedContainerColor   = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    // Standard surface card colors.
    @Composable
    fun cardColors() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    )

    // Standard card elevation — 4dp gives visible depth hierarchy without being heavy.
    @Composable
    fun cardElevation() = CardDefaults.cardElevation(
        defaultElevation = 4.dp,
    )

    // Subtle elevation for secondary cards (settings rows, metrics, non-primary cards).
    @Composable
    fun subtleCardElevation() = CardDefaults.cardElevation(
        defaultElevation = 2.dp,
    )
}
