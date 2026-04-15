# BUG: History workout edit mode — weight/reps values unreadable in input fields

## Status
[x] Fixed

## Description
In the Workout History detail screen, when the user taps **Edit** (enters retroactive edit mode), the editable input fields for **Weight** and **Reps** render with a dark purple background but the text value inside is also dark, making the content invisible/unreadable. The fields show the correct values (or placeholder dashes) but they cannot be seen against the field background.

RPE values and the green checkmark buttons render correctly. The issue is isolated to the Weight and Reps `TextField` / `BasicTextField` inputs.

## Steps to Reproduce
1. Go to the **History** tab
2. Tap any completed workout to open the detail screen
3. Tap the **Edit** button (top-right or inline)
4. Observe the WEIGHT and REPS columns — values and placeholder dashes are invisible against the dark purple field background

## Assets
- Screenshot: `bugs_to_fix/assets/history_edit_unreadable_values/Screenshot_20260415_000244_PowerME.jpg`
- Related spec: `HISTORY_ANALYTICS_SPEC.md` — WorkoutDetailScreen retroactive edit flow

## Root Cause (suspected)
The input fields likely use a container color from the theme (e.g. `primaryContainer` or a hardcoded surface variant) that happens to be dark purple in dark mode, while the `textColor` / `cursorColor` resolves to a dark tint instead of `onPrimaryContainer` or `onSurface`. Check the `colors` parameter passed to `TextFieldDefaults` (or `OutlinedTextFieldDefaults`) for the weight/reps fields in the WorkoutDetailScreen composable.

## Fix Notes
Added `focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer` and `unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer` to `OutlinedTextFieldDefaults.colors()` in `BasicEditField` (`WorkoutDetailScreen.kt:554`). The `onPrimaryContainer` token (`#E0D4F0` in dark, adequate contrast in light) ensures text is legible against the `primaryContainer` (`#2D2052`) background applied to the enclosing Box in edit mode.

## Original Fix Notes
- Locate the Weight and Reps input composables in the WorkoutDetailScreen (or its sub-composable)
- Ensure `textColor` (or `colors.focusedTextColor` / `unfocusedTextColor`) is set to a contrasting token — e.g. `MaterialTheme.colorScheme.onSurface` or `MaterialTheme.colorScheme.onPrimaryContainer`
- Alternatively, change the container color to a lighter surface token so existing text color remains legible
- Verify fix in both light and dark theme
