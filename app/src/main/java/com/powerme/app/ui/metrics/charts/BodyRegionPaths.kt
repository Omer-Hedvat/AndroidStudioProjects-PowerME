package com.powerme.app.ui.metrics.charts

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import com.powerme.app.data.database.BodyRegion
import kotlin.math.min
import kotlin.math.sqrt

/** Which half of the canvas a shape lives in. */
enum class BodyView { ANTERIOR, POSTERIOR }

/**
 * Defines the geometry and hit-test parameters for a single body region shape.
 *
 * @param region          The [BodyRegion] this shape represents.
 * @param view            Whether the shape lives in the anterior or posterior silhouette.
 * @param pathBuilder     Builds a [Path] in actual pixel coordinates for the given canvas size.
 * @param normCenterX     Normalized [0,1] center X for hit testing (relative to canvas width).
 * @param normCenterY     Normalized [0,1] center Y for hit testing (relative to canvas height).
 * @param normHitRadius   Hit radius as a fraction of the canvas's shorter side.
 */
data class BodyRegionShape(
    val region: BodyRegion,
    val view: BodyView,
    val pathBuilder: (w: Float, h: Float) -> Path,
    val normCenterX: Float,
    val normCenterY: Float,
    val normHitRadius: Float
)

/**
 * Normalized path geometry for all 16 [BodyRegion] values.
 *
 * Layout: two stylized body silhouettes side by side —
 *   - Anterior (front) on the left  (x ≈ 0.00–0.47)
 *   - Posterior (back)  on the right (x ≈ 0.53–1.00)
 *
 * All path coordinates are defined as fractions of the canvas width/height,
 * so shapes are resolution-independent and look correct at any pixel density.
 * Bilateral regions (left+right limbs) appear twice in [allShapes] but map
 * to the same [BodyRegion] value — tap either side to select the region.
 *
 * The silhouette outline is a single continuous bezier path (instead of
 * overlapping rectangles) for smooth, anatomically recognizable rendering.
 */
object BodyRegionPaths {

    /** Anterior silhouette body-center X (normalized). */
    private const val ACX = 0.235f

    /** Posterior silhouette body-center X (normalized). */
    private const val PCX = 0.765f

    // ── All region shapes ────────────────────────────────────────────────

    val allShapes: List<BodyRegionShape> by lazy { buildAllShapes() }

    // ── Silhouette outlines (drawn as stroke over the filled regions) ────

    fun anteriorOutline(w: Float, h: Float): Path = silhouetteOutline(w, h, ACX)
    fun posteriorOutline(w: Float, h: Float): Path = silhouetteOutline(w, h, PCX)

    // ── Hit testing ──────────────────────────────────────────────────────

    /**
     * Returns the [BodyRegion] closest to the tap point, or null if no shape
     * is within its hit radius.
     */
    fun findHitRegion(tapX: Float, tapY: Float, canvasW: Float, canvasH: Float): BodyRegion? {
        val nx = tapX / canvasW
        val ny = tapY / canvasH
        val minDim = min(canvasW, canvasH)
        return allShapes.mapNotNull { shape ->
            val dx = nx - shape.normCenterX
            val dy = ny - shape.normCenterY
            val hitFrac = shape.normHitRadius * minDim / canvasW
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= hitFrac) shape.region to dist else null
        }.minByOrNull { it.second }?.first
    }

    // ── Private builders ─────────────────────────────────────────────────

    private fun buildAllShapes(): List<BodyRegionShape> = buildList {

        // ── Anterior (front) ─────────────────────────────────────────────

        // NECK_CERVICAL — small oval at base of head
        add(oval(BodyRegion.NECK_CERVICAL, BodyView.ANTERIOR,
            ACX - 0.022f, 0.128f, ACX + 0.022f, 0.168f,
            ACX, 0.148f, 0.028f))

        // ANTERIOR_DELTOID — bilateral rounded shoulder caps
        add(oval(BodyRegion.ANTERIOR_DELTOID, BodyView.ANTERIOR,
            ACX - 0.120f, 0.152f, ACX - 0.076f, 0.232f,
            ACX - 0.098f, 0.192f, 0.028f))
        add(oval(BodyRegion.ANTERIOR_DELTOID, BodyView.ANTERIOR,
            ACX + 0.076f, 0.152f, ACX + 0.120f, 0.232f,
            ACX + 0.098f, 0.192f, 0.028f))

        // PECS — bilateral pectoral shapes (left/right separately for anatomical accuracy)
        add(roundRect(BodyRegion.PECS, BodyView.ANTERIOR,
            ACX - 0.088f, 0.178f, ACX - 0.010f, 0.305f,
            ACX - 0.049f, 0.242f, 0.060f))
        add(roundRect(BodyRegion.PECS, BodyView.ANTERIOR,
            ACX + 0.010f, 0.178f, ACX + 0.088f, 0.305f,
            ACX + 0.049f, 0.242f, 0.060f))

        // CORE — midsection (slightly tapered)
        add(roundRect(BodyRegion.CORE, BodyView.ANTERIOR,
            ACX - 0.068f, 0.308f, ACX + 0.068f, 0.472f,
            ACX, 0.390f, 0.060f))

        // HIP_JOINT — bilateral hip joint circles
        add(oval(BodyRegion.HIP_JOINT, BodyView.ANTERIOR,
            ACX - 0.082f, 0.470f, ACX - 0.038f, 0.534f,
            ACX - 0.060f, 0.502f, 0.024f))
        add(oval(BodyRegion.HIP_JOINT, BodyView.ANTERIOR,
            ACX + 0.038f, 0.470f, ACX + 0.082f, 0.534f,
            ACX + 0.060f, 0.502f, 0.024f))

        // QUADS — bilateral front thigh (wider at top, spindle shape)
        add(oval(BodyRegion.QUADS, BodyView.ANTERIOR,
            ACX - 0.080f, 0.532f, ACX - 0.020f, 0.718f,
            ACX - 0.050f, 0.625f, 0.034f))
        add(oval(BodyRegion.QUADS, BodyView.ANTERIOR,
            ACX + 0.020f, 0.532f, ACX + 0.080f, 0.718f,
            ACX + 0.050f, 0.625f, 0.034f))

        // KNEE_JOINT — bilateral anterior knee (small circles)
        add(oval(BodyRegion.KNEE_JOINT, BodyView.ANTERIOR,
            ACX - 0.073f, 0.714f, ACX - 0.020f, 0.770f,
            ACX - 0.047f, 0.742f, 0.024f))
        add(oval(BodyRegion.KNEE_JOINT, BodyView.ANTERIOR,
            ACX + 0.020f, 0.714f, ACX + 0.073f, 0.770f,
            ACX + 0.047f, 0.742f, 0.024f))

        // CALVES — bilateral anterior lower leg (spindle / gastrocnemius shape)
        add(oval(BodyRegion.CALVES, BodyView.ANTERIOR,
            ACX - 0.070f, 0.770f, ACX - 0.018f, 0.930f,
            ACX - 0.044f, 0.850f, 0.030f))
        add(oval(BodyRegion.CALVES, BodyView.ANTERIOR,
            ACX + 0.018f, 0.770f, ACX + 0.070f, 0.930f,
            ACX + 0.044f, 0.850f, 0.030f))

        // ELBOW_JOINT — bilateral anterior elbow (small circles)
        add(oval(BodyRegion.ELBOW_JOINT, BodyView.ANTERIOR,
            ACX - 0.126f, 0.350f, ACX - 0.080f, 0.408f,
            ACX - 0.103f, 0.379f, 0.024f))
        add(oval(BodyRegion.ELBOW_JOINT, BodyView.ANTERIOR,
            ACX + 0.080f, 0.350f, ACX + 0.126f, 0.408f,
            ACX + 0.103f, 0.379f, 0.024f))

        // WRIST_JOINT — bilateral anterior wrist
        add(oval(BodyRegion.WRIST_JOINT, BodyView.ANTERIOR,
            ACX - 0.124f, 0.502f, ACX - 0.078f, 0.550f,
            ACX - 0.101f, 0.526f, 0.022f))
        add(oval(BodyRegion.WRIST_JOINT, BodyView.ANTERIOR,
            ACX + 0.078f, 0.502f, ACX + 0.124f, 0.550f,
            ACX + 0.101f, 0.526f, 0.022f))

        // ── Posterior (back) ─────────────────────────────────────────────

        // NECK_CERVICAL — back view
        add(oval(BodyRegion.NECK_CERVICAL, BodyView.POSTERIOR,
            PCX - 0.022f, 0.128f, PCX + 0.022f, 0.168f,
            PCX, 0.148f, 0.028f))

        // POSTERIOR_DELTOID — bilateral rear shoulder caps
        add(oval(BodyRegion.POSTERIOR_DELTOID, BodyView.POSTERIOR,
            PCX - 0.120f, 0.152f, PCX - 0.076f, 0.232f,
            PCX - 0.098f, 0.192f, 0.028f))
        add(oval(BodyRegion.POSTERIOR_DELTOID, BodyView.POSTERIOR,
            PCX + 0.076f, 0.152f, PCX + 0.120f, 0.232f,
            PCX + 0.098f, 0.192f, 0.028f))

        // UPPER_BACK — wide trapezoid upper torso (traps + rhomboids)
        add(roundRect(BodyRegion.UPPER_BACK, BodyView.POSTERIOR,
            PCX - 0.090f, 0.178f, PCX + 0.090f, 0.298f,
            PCX, 0.238f, 0.072f))

        // LATS — bilateral wing-shaped lats flanking spine
        add(roundRect(BodyRegion.LATS, BodyView.POSTERIOR,
            PCX - 0.094f, 0.292f, PCX - 0.044f, 0.435f,
            PCX - 0.069f, 0.364f, 0.032f))
        add(roundRect(BodyRegion.LATS, BodyView.POSTERIOR,
            PCX + 0.044f, 0.292f, PCX + 0.094f, 0.435f,
            PCX + 0.069f, 0.364f, 0.032f))

        // LOWER_BACK — lumbar region
        add(roundRect(BodyRegion.LOWER_BACK, BodyView.POSTERIOR,
            PCX - 0.058f, 0.435f, PCX + 0.058f, 0.516f,
            PCX, 0.476f, 0.048f))

        // HIP_JOINT — bilateral posterior hip
        add(oval(BodyRegion.HIP_JOINT, BodyView.POSTERIOR,
            PCX - 0.082f, 0.470f, PCX - 0.038f, 0.534f,
            PCX - 0.060f, 0.502f, 0.024f))
        add(oval(BodyRegion.HIP_JOINT, BodyView.POSTERIOR,
            PCX + 0.038f, 0.470f, PCX + 0.082f, 0.534f,
            PCX + 0.060f, 0.502f, 0.024f))

        // GLUTES — bilateral glute shapes (rounded, fuller)
        add(oval(BodyRegion.GLUTES, BodyView.POSTERIOR,
            PCX - 0.082f, 0.508f, PCX - 0.008f, 0.625f,
            PCX - 0.045f, 0.567f, 0.040f))
        add(oval(BodyRegion.GLUTES, BodyView.POSTERIOR,
            PCX + 0.008f, 0.508f, PCX + 0.082f, 0.625f,
            PCX + 0.045f, 0.567f, 0.040f))

        // HAMSTRINGS — bilateral rear thigh (spindle shape)
        add(oval(BodyRegion.HAMSTRINGS, BodyView.POSTERIOR,
            PCX - 0.080f, 0.622f, PCX - 0.020f, 0.718f,
            PCX - 0.050f, 0.670f, 0.034f))
        add(oval(BodyRegion.HAMSTRINGS, BodyView.POSTERIOR,
            PCX + 0.020f, 0.622f, PCX + 0.080f, 0.718f,
            PCX + 0.050f, 0.670f, 0.034f))

        // KNEE_JOINT — bilateral posterior knee
        add(oval(BodyRegion.KNEE_JOINT, BodyView.POSTERIOR,
            PCX - 0.073f, 0.714f, PCX - 0.020f, 0.770f,
            PCX - 0.047f, 0.742f, 0.024f))
        add(oval(BodyRegion.KNEE_JOINT, BodyView.POSTERIOR,
            PCX + 0.020f, 0.714f, PCX + 0.073f, 0.770f,
            PCX + 0.047f, 0.742f, 0.024f))

        // CALVES — bilateral posterior lower leg (gastrocnemius spindle)
        add(oval(BodyRegion.CALVES, BodyView.POSTERIOR,
            PCX - 0.070f, 0.770f, PCX - 0.018f, 0.930f,
            PCX - 0.044f, 0.850f, 0.030f))
        add(oval(BodyRegion.CALVES, BodyView.POSTERIOR,
            PCX + 0.018f, 0.770f, PCX + 0.070f, 0.930f,
            PCX + 0.044f, 0.850f, 0.030f))

        // ELBOW_JOINT — bilateral posterior elbow
        add(oval(BodyRegion.ELBOW_JOINT, BodyView.POSTERIOR,
            PCX - 0.126f, 0.350f, PCX - 0.080f, 0.408f,
            PCX - 0.103f, 0.379f, 0.024f))
        add(oval(BodyRegion.ELBOW_JOINT, BodyView.POSTERIOR,
            PCX + 0.080f, 0.350f, PCX + 0.126f, 0.408f,
            PCX + 0.103f, 0.379f, 0.024f))

        // WRIST_JOINT — bilateral posterior wrist
        add(oval(BodyRegion.WRIST_JOINT, BodyView.POSTERIOR,
            PCX - 0.124f, 0.502f, PCX - 0.078f, 0.550f,
            PCX - 0.101f, 0.526f, 0.022f))
        add(oval(BodyRegion.WRIST_JOINT, BodyView.POSTERIOR,
            PCX + 0.078f, 0.502f, PCX + 0.124f, 0.550f,
            PCX + 0.101f, 0.526f, 0.022f))
    }

    // ── Silhouette outline ────────────────────────────────────────────────

    /**
     * Builds a smooth, anatomically-recognizable body silhouette as a single
     * continuous closed bezier path. Uses cubic bezier curves at shoulders,
     * armpits, waist, hips, and joints for organic, natural-looking transitions.
     * Far superior visually to the old approach of overlapping disconnected rectangles.
     */
    private fun silhouetteOutline(w: Float, h: Float, cx: Float): Path = Path().apply {
        // Trace clockwise in screen coordinates (Y increases downward).
        // === TOP OF HEAD ===
        moveTo(cx * w, 0.022f * h)

        // === RIGHT SIDE OF HEAD (cubic arc) ===
        cubicTo((cx + 0.062f) * w, 0.022f * h,
                (cx + 0.064f) * w, 0.128f * h,
                (cx + 0.040f) * w, 0.130f * h)

        // === RIGHT NECK ===
        lineTo((cx + 0.026f) * w, 0.130f * h)
        lineTo((cx + 0.026f) * w, 0.162f * h)

        // === RIGHT SHOULDER (slope out from neck to deltoid) ===
        cubicTo((cx + 0.038f) * w, 0.160f * h,
                (cx + 0.102f) * w, 0.158f * h,
                (cx + 0.124f) * w, 0.184f * h)

        // === RIGHT OUTER ARM (down to hand) ===
        cubicTo((cx + 0.133f) * w, 0.204f * h,
                (cx + 0.133f) * w, 0.368f * h,
                (cx + 0.128f) * w, 0.395f * h)
        lineTo((cx + 0.123f) * w, 0.558f * h)

        // === RIGHT HAND (rounded turn) ===
        cubicTo((cx + 0.123f) * w, 0.576f * h,
                (cx + 0.096f) * w, 0.590f * h,
                (cx + 0.080f) * w, 0.574f * h)

        // === RIGHT INNER ARM (back up to armpit) ===
        lineTo((cx + 0.080f) * w, 0.400f * h)
        lineTo((cx + 0.080f) * w, 0.278f * h)

        // === RIGHT TORSO (armpit → waist, with concave curve) ===
        cubicTo((cx + 0.093f) * w, 0.286f * h,
                (cx + 0.090f) * w, 0.362f * h,
                (cx + 0.073f) * w, 0.445f * h)

        // === RIGHT HIP (waist → outer hip, convex curve) ===
        cubicTo((cx + 0.078f) * w, 0.466f * h,
                (cx + 0.090f) * w, 0.490f * h,
                (cx + 0.088f) * w, 0.516f * h)
        lineTo((cx + 0.082f) * w, 0.530f * h)

        // === RIGHT OUTER LEG (thigh → knee → ankle) ===
        lineTo((cx + 0.079f) * w, 0.718f * h)
        lineTo((cx + 0.072f) * w, 0.936f * h)

        // === RIGHT FOOT (outer → inner, curve goes down then up) ===
        cubicTo((cx + 0.072f) * w, 0.952f * h,
                (cx + 0.054f) * w, 0.958f * h,
                (cx + 0.017f) * w, 0.955f * h)

        // === RIGHT INNER LEG (ankle → crotch) ===
        lineTo((cx + 0.017f) * w, 0.934f * h)
        lineTo((cx + 0.022f) * w, 0.718f * h)
        cubicTo((cx + 0.022f) * w, 0.596f * h,
                (cx + 0.020f) * w, 0.546f * h,
                cx * w,            0.540f * h)

        // === LEFT INNER LEG (crotch → ankle) ===
        cubicTo((cx - 0.020f) * w, 0.546f * h,
                (cx - 0.022f) * w, 0.596f * h,
                (cx - 0.022f) * w, 0.718f * h)
        lineTo((cx - 0.017f) * w, 0.934f * h)

        // === LEFT FOOT (inner → outer) ===
        cubicTo((cx - 0.017f) * w, 0.955f * h,
                (cx - 0.054f) * w, 0.958f * h,
                (cx - 0.072f) * w, 0.952f * h)
        lineTo((cx - 0.072f) * w, 0.936f * h)

        // === LEFT OUTER LEG (ankle → knee → thigh) ===
        lineTo((cx - 0.079f) * w, 0.718f * h)
        lineTo((cx - 0.082f) * w, 0.530f * h)

        // === LEFT HIP (outer hip → waist) ===
        lineTo((cx - 0.088f) * w, 0.516f * h)
        cubicTo((cx - 0.090f) * w, 0.490f * h,
                (cx - 0.078f) * w, 0.466f * h,
                (cx - 0.073f) * w, 0.445f * h)

        // === LEFT TORSO (waist → armpit) ===
        cubicTo((cx - 0.090f) * w, 0.362f * h,
                (cx - 0.093f) * w, 0.286f * h,
                (cx - 0.080f) * w, 0.278f * h)

        // === LEFT INNER ARM (armpit → hand) ===
        lineTo((cx - 0.080f) * w, 0.400f * h)
        lineTo((cx - 0.080f) * w, 0.574f * h)

        // === LEFT HAND (rounded turn) ===
        cubicTo((cx - 0.080f) * w, 0.590f * h,
                (cx - 0.096f) * w, 0.590f * h,
                (cx - 0.123f) * w, 0.574f * h)

        // === LEFT OUTER ARM (hand → shoulder) ===
        lineTo((cx - 0.123f) * w, 0.558f * h)
        lineTo((cx - 0.128f) * w, 0.395f * h)
        cubicTo((cx - 0.133f) * w, 0.368f * h,
                (cx - 0.133f) * w, 0.204f * h,
                (cx - 0.124f) * w, 0.184f * h)

        // === LEFT SHOULDER (slope in to neck) ===
        cubicTo((cx - 0.102f) * w, 0.158f * h,
                (cx - 0.038f) * w, 0.160f * h,
                (cx - 0.026f) * w, 0.162f * h)

        // === LEFT NECK ===
        lineTo((cx - 0.026f) * w, 0.130f * h)

        // === LEFT SIDE OF HEAD (cubic arc back to top) ===
        cubicTo((cx - 0.040f) * w, 0.130f * h,
                (cx - 0.064f) * w, 0.022f * h,
                cx * w,            0.022f * h)

        close()
    }

    // ── Shape factory helpers ─────────────────────────────────────────────

    private fun oval(
        region: BodyRegion, view: BodyView,
        nx1: Float, ny1: Float, nx2: Float, ny2: Float,
        ncx: Float, ncy: Float, normHitRadius: Float
    ) = BodyRegionShape(
        region = region, view = view,
        pathBuilder = { w, h ->
            Path().apply { addOval(Rect(nx1 * w, ny1 * h, nx2 * w, ny2 * h)) }
        },
        normCenterX = ncx, normCenterY = ncy, normHitRadius = normHitRadius
    )

    private fun roundRect(
        region: BodyRegion, view: BodyView,
        nx1: Float, ny1: Float, nx2: Float, ny2: Float,
        ncx: Float, ncy: Float, normHitRadius: Float,
        cornerFrac: Float = 0.30f
    ) = BodyRegionShape(
        region = region, view = view,
        pathBuilder = { w, h ->
            val rw = (nx2 - nx1) * w * cornerFrac
            val rh = (ny2 - ny1) * h * cornerFrac
            Path().apply { addRoundRect(RoundRect(nx1 * w, ny1 * h, nx2 * w, ny2 * h, rw, rh)) }
        },
        normCenterX = ncx, normCenterY = ncy, normHitRadius = normHitRadius
    )
}
