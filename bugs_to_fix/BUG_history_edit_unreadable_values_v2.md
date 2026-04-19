# BUG: History edit values unreadable (regression)

## Status
[x] Fixed

## Description
Regression of BUG_history_edit_unreadable_values (previously fixed & committed). When editing a past workout in the history detail view, input field values are unreadable — dark text on dark background. The previous fix in `WorkoutDetailScreen.kt` either regressed or didn't cover all edit field cases (e.g., the new unified `WorkoutSummaryScreen` route may have a separate edit path that wasn't patched).

Affected screen: History detail / workout summary edit mode — likely `WorkoutSummaryScreen.kt` or `WorkoutDetailScreen.kt`.

## Severity
P1

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutDetailScreen.kt`

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
`BasicEditField` in `WorkoutDetailScreen.kt` was using `onPrimaryContainer` (#E0D4F0, muted lavender) for text color. While the contrast ratio is technically adequate, the purple-on-purple pairing (lavender text on deep purple `primaryContainer` bg) is perceptually poor — especially for small `bodyMedium` text and thin characters like dashes.

Changed `focusedTextColor` and `unfocusedTextColor` to `onSurface` (#EDEDEF, near-white), matching the project's `PowerMeDefaults.outlinedTextFieldColors()` standard. Also added the missing `cursorColor = primary`.
