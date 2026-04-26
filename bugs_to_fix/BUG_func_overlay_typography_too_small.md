# BUG: Functional overlay typography too small — round number, exercise list, and reps unreadable at arm's length

## Status
[ ] Open

## Severity
P2 normal
- Not a crash, but significantly hurts usability during live workout when phone is on a surface or bench

## Description
In the RFT and AMRAP full-screen overlays, the round number (e.g. "Round 3"), the exercise names, and the reps/hold-seconds labels are all too small to read comfortably at arm's length. During an active functional block the user cannot hold the phone — it sits on a bench or rack — so all overlay text needs to be large enough to read from ~0.5–1m away.

The existing `func_overlay_exercise_font` fix bumped exercise names from `bodyLarge` → `titleMedium` but this was insufficient. The round counter and reps labels are still small, and the overall scale of the overlay needs to be reconsidered for both RFT and AMRAP.

## Steps to Reproduce
1. Create a routine with an RFT block (3 rounds) and an AMRAP block (12 min), each with 2–3 exercises.
2. Start the workout and tap ▶ START BLOCK on the RFT block.
3. Place the phone on a flat surface ~0.5m away.
4. Observe: the round counter ("Round 1 of 3"), exercise names, and reps labels are too small to read.
5. Repeat for AMRAP overlay.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/BlockRecipeRow.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `THEME_SPEC.md §9`
- Related (incomplete): `future_devs/FUNC_OVERLAY_EXERCISE_FONT_SPEC.md` — partial fix, insufficient

## Fix Notes
<!-- populated after fix is applied -->
