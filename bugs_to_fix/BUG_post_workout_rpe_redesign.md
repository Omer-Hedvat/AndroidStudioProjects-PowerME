# BUG: Post-workout RPE auto-pops instead of offering an "Add RPE" button

## Status
[ ] Open

## Severity
P2 normal
- UX friction; the auto-pop is surprising and the sheet doesn't show RPE label descriptions (e.g. "Hard", "Very Hard") that help the user rate accurately

## Description
After finishing a workout the RPE sheet auto-pops over the summary screen. The expected UX is:
1. Land on the post-workout summary screen.
2. See a clearly labelled **"Add RPE"** button (not an auto-pop sheet).
3. Tapping it opens the standard RPE rating screen that includes the textual RPE scale titles (e.g. 6 = Very Light, 10 = Maximal) so the user understands the scale before rating.

The current auto-pop is disruptive and provides no scale context. The RPE label titles that appear on the regular RPE screen are missing from the auto-pop sheet, so users don't know what each number means.

## Steps to Reproduce
1. Start and complete any workout (tap Finish Workout).
2. Observe: an RPE bottom sheet auto-pops over the summary screen immediately.
3. The sheet shows only a numeric slider or buttons with no textual descriptions of each RPE level.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/WorkoutSummaryScreen.kt`, `ui/workout/WorkoutSummaryViewModel.kt`, `ui/workout/WorkoutViewModel.kt`, `ui/components/RpeRatingSheet.kt` (or equivalent)

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
