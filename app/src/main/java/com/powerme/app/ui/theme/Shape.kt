package com.powerme.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// PowerME Shape System — organic, softened radii
// Use MaterialTheme.shapes.* in composables — never inline RoundedCornerShape()
val PowerMeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // chips, badges, small pills
    small      = RoundedCornerShape(10.dp),  // buttons, text fields, inputs
    medium     = RoundedCornerShape(16.dp),  // cards, dialogs
    large      = RoundedCornerShape(24.dp),  // bottom sheets, large cards
    extraLarge = RoundedCornerShape(32.dp),  // FAB, full-width action buttons
)

// Spacing scale — named tokens for consistent spatial rhythm
// Usage: Modifier.padding(Spacing.lg), Spacer(Modifier.height(Spacing.md))
object Spacing {
    val xs   = 4.dp
    val sm   = 8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 20.dp
    val xxl  = 24.dp
    val xxxl = 32.dp
}
