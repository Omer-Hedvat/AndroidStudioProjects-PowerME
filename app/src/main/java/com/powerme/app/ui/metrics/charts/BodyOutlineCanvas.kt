package com.powerme.app.ui.metrics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.powerme.app.data.database.BodyRegion

/**
 * Renders a stylized anterior + posterior body outline with all 16
 * [BodyRegion] shapes colored by stress intensity.
 *
 * - Anterior (front) silhouette is on the left half of the canvas.
 * - Posterior (back)  silhouette is on the right half.
 * - Each region's fill color comes from [regionColors] (see [StressColorMapper]).
 * - Tapping anywhere on the canvas hits the closest region within its radius
 *   and fires [onRegionTapped].
 * - The currently [selectedRegion] is highlighted with a primary-color stroke.
 *
 * Canvas uses aspectRatio(0.65f) — height ≈ 1.54× width — compact enough to
 * fit inside the heatmap card without internal scrolling on typical phones.
 * Each filled region gets a subtle radial depth gradient to give a slight 3D feel
 * against the OLED dark background.
 */
@Composable
fun BodyOutlineCanvas(
    regionColors: Map<BodyRegion, Color>,
    selectedRegion: BodyRegion?,
    onRegionTapped: (BodyRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val regionStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val selectedColor     = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)   // height ≈ 1.54× width (was 2× — more compact)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val hit = BodyRegionPaths.findHitRegion(
                        offset.x, offset.y, size.width.toFloat(), size.height.toFloat()
                    )
                    if (hit != null) onRegionTapped(hit)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val strokeWidth2_5dp = 2.5.dp.toPx()
        val strokeWidth1dp   = 1.dp.toPx()
        val strokeWidth3dp   = 3.dp.toPx()

        // ── Silhouette outlines ───────────────────────────────────────────
        // Drawn first as a single continuous bezier path — clean, no joins
        drawPath(
            BodyRegionPaths.anteriorOutline(w, h),
            color = outlineColor,
            style = Stroke(width = strokeWidth2_5dp)
        )
        drawPath(
            BodyRegionPaths.posteriorOutline(w, h),
            color = outlineColor,
            style = Stroke(width = strokeWidth2_5dp)
        )

        // ── Filled region shapes with depth gradient ──────────────────────
        for (shape in BodyRegionPaths.allShapes) {
            val path  = shape.pathBuilder(w, h)
            val color = regionColors[shape.region] ?: Color.Transparent

            // Flat fill
            if (color != Color.Transparent) {
                drawPath(path, color = color)

                // Subtle radial highlight for 3D depth (top-center is brighter)
                val centerX = shape.normCenterX * w
                val centerY = shape.normCenterY * h
                val radius  = shape.normHitRadius * minOf(w, h) * 1.5f
                drawPath(
                    path,
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(centerX, centerY - radius * 0.3f),
                        radius = radius
                    )
                )
            }

            // Region boundary stroke (subtle separator)
            drawPath(path, color = regionStrokeColor, style = Stroke(width = strokeWidth1dp))
        }

        // ── Selected region highlight ─────────────────────────────────────
        if (selectedRegion != null) {
            for (shape in BodyRegionPaths.allShapes) {
                if (shape.region == selectedRegion) {
                    drawPath(
                        shape.pathBuilder(w, h),
                        color = selectedColor,
                        style = Stroke(width = strokeWidth3dp)
                    )
                }
            }
        }

        // ── View labels ("FRONT" / "BACK") ────────────────────────────────
        val labelPaint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            textSize    = (h * 0.030f).coerceIn(18f, 36f)
            this.color = android.graphics.Color.argb(
                (outlineColor.alpha * 0.7f * 255).toInt(),
                (outlineColor.red   * 255).toInt(),
                (outlineColor.green * 255).toInt(),
                (outlineColor.blue  * 255).toInt()
            )
        }
        drawContext.canvas.nativeCanvas.apply {
            val frontLabel = "FRONT"
            val backLabel  = "BACK"
            val textBounds = android.graphics.Rect()
            labelPaint.getTextBounds(frontLabel, 0, frontLabel.length, textBounds)
            val textH = textBounds.height()

            drawText(frontLabel, 0.235f * w - textBounds.width() / 2f, 0.008f * h + textH, labelPaint)
            labelPaint.getTextBounds(backLabel, 0, backLabel.length, textBounds)
            drawText(backLabel,  0.765f * w - textBounds.width() / 2f, 0.008f * h + textH, labelPaint)
        }
    }
}
