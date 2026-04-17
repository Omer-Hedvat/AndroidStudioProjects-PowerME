# Fix Summary: Timed set row layout crowded in IDLE state

## Root Cause
The IDLE branch of `TimedSetRow` split its 0.20f action area equally between Play (0.10f) and Check (0.10f), both rendered as filled background boxes with centered icons. Two identically-styled buttons at the same size created visual clutter with no hierarchy.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Redistributed IDLE action weights: Play 0.10f->0.14f (icon 18->20dp), Check 0.10f->0.06f (removed background, icon 16dp, alpha 0.25f) |
| `WORKOUT_SPEC.md` | Updated section 4.8 IDLE row column description |

## Surfaces Fixed
- Active workout screen: timed exercise IDLE set rows (e.g. Bird-Dog, Plank)

## How to QA
- Open a routine containing a timed exercise (e.g. Bird-Dog, Plank)
- Start an active workout session
- Observe IDLE (uncompleted) timed set rows:
  - Play button should be clearly prominent (colored box, wider)
  - Check icon should be subtle (no background, faded grey)
- Tap Play button -> timer should start normally (SETUP or RUNNING)
- Tap the ghost Check icon -> set should mark as complete (green checkmark, same as before)
- Verify COMPLETED rows still show the clean green checkmark unchanged
- Verify column headers (SET/PREV/WEIGHT/TIME) still align with row data
