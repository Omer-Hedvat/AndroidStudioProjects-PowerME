# Fix Summary: Timed exercise row has wrong columns

## Root Cause
`TimedHeader()` and `TimedSetRow` were written with ad-hoc hardcoded weights that didn't match each other — the header had TIME(S) at 0.35f while the data row had it at 0.25f, and the header had an RPE column but the data row's RPE field was at a narrower weight with an extra Play-button box having no corresponding header. This caused column drift, an orphaned "—" box, and no historical reference (PREV) visible for timed exercises.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `ghostTimeSeconds: String?` field to `ActiveSet`; populated from `ghost?.timeSeconds` in routine workout loading |
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Added `formatGhostTimedLabel()` helper; rewrote `TimedHeader()` with correct column weights; replaced RPE inline field with PREV ghost column in IDLE and COMPLETED states |
| `WORKOUT_SPEC.md` | Updated §4.8 column table and added column-layout note |

## Surfaces Fixed
- Active workout screen — timed exercise card column headers now align with data rows in IDLE and COMPLETED states
- PREV column shows previous session weight×time (e.g. "10×30s") as a ghost reference
- No orphaned "—" box; no unlabeled Play button

## How to QA
1. Open a routine that contains a timed exercise (e.g. Bird-Dog or Plank).
2. Start an active workout.
3. Scroll to the timed exercise card.
4. Verify the header shows: **SET | PREV | WEIGHT | TIME(S)** — four clearly labelled columns, no RPE column.
5. Verify the set row in IDLE state has: set number | ghost hint (or "—" if no prior session) | weight input | time input | Play button | Check button — all aligned under their headers.
6. Tap the Play button, let the timer run to completion (or tap Check to mark done directly).
7. In COMPLETED state, verify: set number | ghost hint | weight input | time input | (spacer) | filled-green Check — columns still aligned.
8. If a prior session for this exercise exists, the PREV cell should show e.g. `10×30s`.
9. If RPE auto-pop is ON (Settings → Display & Workout → Use RPE), verify the RPE picker still appears after completing a set.
