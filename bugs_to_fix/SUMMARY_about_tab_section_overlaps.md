# Fix Summary: ABOUT tab sections overlap — BodyOutlineCanvas bleeds through Training Zones and Form Cues

## Root Cause

Two independent issues in `AboutTab.kt`:

1. **Double horizontal padding on section content:** Every section wrapped both `SectionHeader` (which already has `padding(horizontal = 16.dp)`) and content items in an outer `Column(Modifier.padding(horizontal = 16.dp))`, doubling the inset to 32dp per side. Form Cues banners, Training Zones boxes, and Warm-Up Ramp surfaces were all 32dp narrower than intended.

2. **BodyOutlineCanvas transparent and unclipped:** The canvas had no opaque background `Surface`, making it visually transparent. `nativeCanvas.drawText()` bypasses Compose's layer clipping, so the body silhouette bled through adjacent sections (Form Cues gold banner, Training Zones boxes).

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt` | Removed `padding(horizontal = 16.dp)` from all outer section Columns; content items now explicitly carry `padding(start = 16.dp, end = 16.dp, bottom = 12.dp)`; wrapped `BodyOutlineCanvas` in `Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.height(280.dp))` to create an opaque, clipped layer |

## Surfaces Fixed
- Exercise Detail → ABOUT tab: body silhouette no longer bleeds through Form Cues banner or Training Zones boxes
- All section content renders at correct 16dp horizontal inset (not 32dp)

## How to QA
1. Open the **Exercises** tab → tap any exercise with muscle activation data (e.g. "Band Tricep Pushdown")
2. Tap the **ABOUT** tab
3. Verify: Form Cues gold banner is fully opaque — no body silhouette visible through it
4. Verify: Training Zones boxes (Strength / Hypertrophy / Endurance) are fully opaque
5. Verify: section content is inset ~16dp from screen edges (not 32dp)
6. Verify: "MUSCLE ACTIVATION" header does not overlap sections above it
