# BUG: ABOUT tab sections overlap — BodyOutlineCanvas bleeds through Training Zones and Form Cues

## Status
[x] Fixed

## Severity
P1 high
- Visible on every exercise that has muscle activation data. The body silhouette is visible through the Form Cues gold banner and Training Zones boxes, making the screen look broken.

## Description
In `AboutTab.kt`, the `MuscleActivationSection` renders a `BodyOutlineCanvas` (260dp tall) with no opaque background. When composed inside a `LazyColumn` alongside `FormCuesSection` and `SetRepZoneGuideSection`, the canvas content bleeds visually into adjacent sections:

- The body silhouette outline is visible through the semi-transparent Form Cues gold banner
- The Training Zones boxes (Strength / Hypertrophy / Endurance) appear transparent with the body outline showing through them
- The "MUSCLE ACTIVATION" section header overlaps with sections above it due to insufficient vertical spacing

Root cause: `BodyOutlineCanvas` draws on a transparent `Canvas` surface. The LazyColumn items above it do not have opaque backgrounds, so the canvas composable's draw layer renders through them. There is also insufficient `SectionDivider` height / padding between sections.

## Steps to Reproduce
1. Navigate to the **Exercises** tab
2. Open any exercise with muscle activation data — e.g. "Band Tricep Pushdown"
3. View the **ABOUT** tab
4. Observe: body silhouette is visible behind the Form Cues banner and Training Zones boxes; sections visually overlap

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt`, `app/src/main/java/com/powerme/app/ui/exercises/detail/DetailComponents.kt`

## Assets
- Related spec: `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md`

## Fix Notes
Two root causes found in `AboutTab.kt`:

1. **Double-padding on section headers and content**: Every section had `Column(modifier = Modifier.padding(horizontal = 16.dp))` wrapping both `SectionHeader` (which already has `padding(horizontal = 16.dp)`) and content items (Surface, Row, LazyRow). This doubled the horizontal inset to 32dp per side — the Form Cues banner, Training Zones boxes, and Warm-Up Ramp surface were all 32dp narrower than intended, and all section headers had 32dp padding instead of 16dp.

   Fix: Removed `padding(horizontal = 16.dp)` from all outer Columns. `SectionHeader`'s own 16dp padding is now the sole horizontal inset for headers. Content items (`Surface`, `Row`, `OutlinedTextField`) explicitly specify `padding(start = 16.dp, end = 16.dp, bottom = 12.dp)`.

2. **BodyOutlineCanvas has no opaque background and uses `nativeCanvas.drawText()`**: The canvas had no background Surface so it was visually transparent, and `nativeCanvas` drawing bypasses Compose's layer clipping — causing the body silhouette to bleed visually into adjacent sections.

   Fix: Wrapped `BodyOutlineCanvas` in a `Surface(color = MaterialTheme.colorScheme.surface)` with explicit `height(280.dp)`. `Surface` creates a layered composition with shape-based clipping, constraining all canvas drawing (including nativeCanvas) to the 280dp height. The opaque surface background eliminates the transparency bleed-through.
