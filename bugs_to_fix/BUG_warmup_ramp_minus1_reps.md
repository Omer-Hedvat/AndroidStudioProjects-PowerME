# BUG: Warmup ramp shows -1 reps for last warmup set

## Status
[x] Fixed

## Severity
P2 normal
- Warmup ramp in Exercise Detail → About tab displays "-1" as the rep count for the last warmup set row, which is meaningless to the user.

## Description
In the Exercise Detail screen → About tab → Warmup Ramp section, the last warmup set row shows `-1` as the rep count (e.g. 50%/8, 70%/5, 85%/3, 100%/-1). The `-1` is a sentinel/default value in the warmup ramp seed data that is leaking through to the display instead of being hidden or shown as `--` or omitted.

The 100% row at `-1` reps likely represents the working set itself (not a warmup), which should either not be shown in the warmup ramp at all, or displayed as the actual working set rep target.

Note: warmup ramp weights are derived from the user's **last logged working weight** for that exercise. The percentages (50%, 70%, 85%, 100%) are applied to that value.

## Steps to Reproduce
1. Go to Exercises tab
2. Tap any barbell compound exercise (e.g. Barbell Back Squat)
3. Open Exercise Detail screen → About tab
4. Observe Warmup Ramp section — last row shows "-1" reps

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `AboutTab.kt`, warmup ramp seed data / model

## Assets
- Related spec: `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md`

## Fix Notes
Removed the 100% row from `computeWarmUpRamp` in `ExerciseDetailRepository.kt`. That row used `reps = -1` as a sentinel meaning "working set target" — not an actual warmup set — and the UI was rendering the raw `-1` value. The ramp now returns only the three genuine warmup sets (50%/8, 70%/5, 85%/3). Updated `ExerciseDetailRepositoryTest` to expect 3 rows instead of 4.
