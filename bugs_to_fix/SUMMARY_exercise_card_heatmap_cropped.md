# Fix Summary: Exercise card muscle activation heatmap cropped

## Root Cause

`MuscleActivationSection` in `AboutTab.kt` wrapped `BodyOutlineCanvas` in a `Surface` with a
fixed `.height(280.dp)` modifier (left over from the `BUG_about_tab_section_overlaps` fix).
With 12dp of bottom padding also on the Surface, the canvas only received 268dp of vertical space.

`BodyOutlineCanvas` uses `aspectRatio(0.65f)` — on a typical ~360dp wide phone, the intended
canvas height is 360/0.65 ≈ 554dp. When Compose's `aspectRatio` modifier can't satisfy both the
full-width and the aspect ratio within 268dp, it falls back to the parent's max dimensions:
360×268dp.

All figure paths are defined as normalized fractions of `h`. With `h = 268dp` instead of the
intended ~554dp:
- The head (y = 0.022h to 0.128h) was only 28dp tall, rendered as a barely-visible outline stroke
- The whole figure was squished to ~48% of its intended height
- The result looked like only the torso/midsection was visible

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt` | Removed `.height(280.dp)` from the `Surface` modifier in `MuscleActivationSection`. Surface now wraps the canvas at its natural `aspectRatio(0.65f)`-derived height. |

## Surfaces Fixed

- Exercise Detail Sheet → ABOUT tab → MUSCLE ACTIVATION section: full body figure (head to feet) now renders at correct proportions

## How to QA

1. Open any exercise from the Exercises tab (or via History → exercise name)
2. Tap the **ABOUT** tab in the Exercise Detail Sheet
3. Scroll down to the **MUSCLE ACTIVATION** section
4. Verify the body heatmap shows:
   - A complete head shape at the top of each figure
   - Full torso (chest, shoulders, abs, back)
   - Full legs (quads/hamstrings, knees, calves, feet)
   - **FRONT** / **BACK** labels visible above each figure
5. Verify the Trends tab → Body Stress Heatmap card is unaffected (still renders correctly)
6. Scroll past the MUSCLE ACTIVATION section — verify no visual bleed-through to sections above/below
