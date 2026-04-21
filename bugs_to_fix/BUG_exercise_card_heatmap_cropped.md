# BUG: Exercise card muscle activation heatmap cropped — missing upper and lower body

## Status
[x] Fixed

## Severity
P2 normal
- Cosmetic but clearly visible on every exercise card that displays muscle activation

## Description
The muscle activation / body outline heatmap rendered inside exercise cards is clipped vertically.
Specifically: everything **from the chest upward** (head, neck, shoulders) is cut off at the top,
and everything **from the knees downward** (shins, calves, feet) is cut off at the bottom.
Only the torso + upper thighs region is visible.

Root cause is likely a fixed or insufficiently large `height` / `size` constraint on the
`BodyOutlineCanvas` composable (or its parent container) inside the exercise card, or the
canvas draw bounds not accounting for the full figure extent.

Affected screen: Exercises tab — exercise list cards (and possibly the About tab in ExerciseDetailSheet).

## Steps to Reproduce
1. Open the app and navigate to the Exercises tab.
2. Observe any exercise card that displays a muscle activation / heatmap body figure.
3. Observe: chest-and-above is clipped at the top; knees-and-below clipped at the bottom —
   only the torso/upper-thigh region is visible.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/exercises/ExercisesScreen.kt`, `ui/metrics/charts/BodyOutlineCanvas.kt`, `ui/exercises/detail/AboutTab.kt`

## Assets
- Screenshot: `bugs_to_fix/assets/exercise_card_heatmap_cropped/Screenshot_20260420_125629_PowerME.jpg`
- Related spec: `EXERCISES_SPEC.md`, `TRENDS_SPEC.md` (BodyOutlineCanvas)

## Fix Notes
Root cause: `MuscleActivationSection` in `AboutTab.kt` wrapped `BodyOutlineCanvas` in a `Surface`
with `.height(280.dp)`. This forced the canvas into a 268dp-tall space (280 - 12dp bottom padding).
The `BodyOutlineCanvas` uses `aspectRatio(0.65f)`, which needs height ≈ width/0.65 ≈ 554dp on a
typical 360dp-wide phone. Compose's `aspectRatio` fallback clamped the canvas to 360×268dp.
All figure path coordinates are normalized fractions of h — so the full figure (head at y=0.022h
to feet at y=0.958h) was drawn compressed into 268dp. The head (~28dp) became near-invisible
(faint outline stroke only, no fill) and the overall figure appeared to show only the midsection.

Fix: Removed `.height(280.dp)` from the Surface modifier. The Surface now wraps the canvas
at its natural `aspectRatio`-derived height (~554dp). The `LazyColumn` in `AboutTabContent`
handles scrolling. The Surface is still present to provide the opaque background clipping
that prevents the nativeCanvas bleed-through fixed in BUG_about_tab_section_overlaps.
