package com.powerme.app.ui.workout.runner

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerme.app.ui.theme.NeonPurple

/**
 * Full-width tap zone for AMRAP / RFT round logging.
 *
 * Height = max(40% viewport, 280dp). 250ms debounce + spring scale-to-0.98f on tap.
 * Haptic LongPress + audio cue handled by caller via [onTap].
 */
@Composable
fun BlindTapZone(
    currentRound: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var lastTapMs by remember { mutableStateOf(0L) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(),
        label = "tapScale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val zoneHeight = (maxHeight * 0.4f).coerceAtLeast(280.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = zoneHeight)
                .scale(scale)
                .background(NeonPurple.copy(alpha = 0.12f))
                .semantics {
                    role = Role.Button
                    contentDescription = "Tap to log round; current round $currentRound"
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapMs < 250) return@clickable
                    lastTapMs = now
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTap()
                    pressed = false
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "TAP",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displayLarge,
            )
        }
    }
}
