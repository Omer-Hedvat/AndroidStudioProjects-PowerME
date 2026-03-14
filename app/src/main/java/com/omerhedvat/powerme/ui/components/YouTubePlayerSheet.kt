package com.omerhedvat.powerme.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Bottom sheet that displays a YouTube video player for exercise demonstrations.
 *
 * Features:
 * - Lifecycle-aware player (pauses on background)
 * - Embedded YouTube player with controls
 * - Exercise name displayed as title
 * - Clean MaterialTheme.colorScheme.primary/OLED theme
 *
 * @param videoId YouTube video ID (e.g., "ultWZbUMPL8")
 * @param exerciseName Name of exercise for display
 * @param onDismiss Callback when sheet is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubePlayerBottomSheet(
    videoId: String,
    exerciseName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Exercise name header
            Text(
                text = exerciseName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Form demonstration",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )

            // YouTube Player
            YouTubePlayerComposable(
                videoId = videoId,
                exerciseName = exerciseName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            // Close button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CLOSE",
                    fontWeight = FontWeight.Bold
                )
            }

        }
    }
}

/**
 * Composable wrapper around YouTubePlayerView with lifecycle management.
 *
 * Handles:
 * - Player initialization
 * - Video loading
 * - Lifecycle events (pause/resume)
 * - Cleanup on disposal
 * - Error/unavailable video fallback
 */
@Composable
private fun YouTubePlayerComposable(
    videoId: String,
    exerciseName: String,
    modifier: Modifier = Modifier
) {
    var youTubePlayer by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isError by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    if (isError) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("Video unavailable", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = {
                    val query = exerciseName.replace(" ", "+") + "+form"
                    uriHandler.openUri("https://www.youtube.com/results?search_query=$query")
                }) {
                    Text("Search on YouTube", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
        }
        return
    }

    // Lifecycle observer for pause/resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    youTubePlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Player will auto-resume if needed
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            youTubePlayer?.pause()
        }
    }

    AndroidView(
        factory = { context ->
            YouTubePlayerView(context).apply {
                lifecycleOwner.lifecycle.addObserver(this)

                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(player: YouTubePlayer) {
                        youTubePlayer = player
                        player.cueVideo(videoId, 0f)
                    }

                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                        isError = true
                    }
                })
            }
        },
        modifier = modifier,
        onRelease = { playerView ->
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    )
}
