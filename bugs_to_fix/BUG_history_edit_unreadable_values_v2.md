# BUG: History edit values unreadable (regression)

## Status
[ ] Open

## Description
Regression of BUG_history_edit_unreadable_values (previously fixed & committed). When editing a past workout in the history detail view, input field values are unreadable — dark text on dark background. The previous fix in `WorkoutDetailScreen.kt` either regressed or didn't cover all edit field cases (e.g., the new unified `WorkoutSummaryScreen` route may have a separate edit path that wasn't patched).

Affected screen: History detail / workout summary edit mode — likely `WorkoutSummaryScreen.kt` or `WorkoutDetailScreen.kt`.

## Steps to Reproduce
1. Open the app and navigate to the History tab
2. Tap on a completed workout to open its detail/summary view
3. Enter edit mode (tap edit icon)
4. Observe: input field values (weight, reps) are unreadable — dark text blends into dark background

## Assets
- Screenshot provided by user (see conversation)
- Related spec: `HISTORY_ANALYTICS_SPEC.md`, `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md`
- Previous fix: `BUG_history_edit_unreadable_values.md`

## Fix Notes
<!-- populated after fix is applied -->
