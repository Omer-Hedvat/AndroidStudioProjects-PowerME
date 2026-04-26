# BUG: "Abandon" button in functional overlays should be renamed to "Cancel Workout"

## Status
[ ] In Progress

## Severity
P3 low
- Cosmetic consistency — "Abandon" is non-standard; all other workout flows use "Cancel Workout"

## Description
All four functional block overlays (AMRAP, RFT, EMOM, TABATA) show an "ABANDON" button that cancels the entire workout session. The rest of the app uses "Cancel Workout" for this action. The label should be renamed for consistency.

## Steps to Reproduce
1. Start any functional block overlay (AMRAP / RFT / EMOM / TABATA).
2. Observe the bottom-left button labeled "ABANDON".
3. Compare with the rest timer cancel flow — it says "Cancel Workout".

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
