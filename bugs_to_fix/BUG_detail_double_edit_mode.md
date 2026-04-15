# BUG: Workout detail has two redundant edit modes; inner edit mode has unreadable values

## Status
[ ] Open

## Description
The workout detail view (reached via pen/edit icon on WorkoutSummaryScreen) exposes two separate edit modes:

1. **Outer edit mode** — the view itself behaves like an editable active workout screen (pen icon from WorkoutSummaryScreen).
2. **Inner edit mode** — a second **Edit** button inside this view opens *another* edit mode.

Having two edit modes on the same screen is confusing and redundant. Additionally, in the **inner** edit mode, numeric input field values are unreadable (likely dark text on dark background — same class of issue as `BUG_history_edit_unreadable_values` which was fixed in `WorkoutDetailScreen.kt`). The inner edit mode's text fields appear not to have received the `focusedTextColor`/`unfocusedTextColor` fix applied previously.

## Steps to Reproduce
1. Open the History tab.
2. Tap on any past workout → WorkoutSummaryScreen opens.
3. Tap the **pen icon** (top-right) to open the workout detail/edit view.
4. Observe the **Edit** button — this opens a second, nested edit mode.
5. In this second edit mode, observe that weight/rep values in input fields are unreadable (dark text on dark background).

## Assets
- Related spec: `HISTORY_ANALYTICS_SPEC.md §WorkoutDetailScreen`
- Related prior fix: `bugs_to_fix/BUG_history_edit_unreadable_values.md`

## Fix Notes
<!-- populated after fix -->
