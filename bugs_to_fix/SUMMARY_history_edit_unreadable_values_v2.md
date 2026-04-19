# Fix Summary: History edit values unreadable (regression)

## Root Cause

`BasicEditField` in `WorkoutDetailScreen.kt` used `onPrimaryContainer` (#E0D4F0, muted lavender) for text color on a `primaryContainer` (#2D2052, deep purple) background. While the computed contrast ratio is technically sufficient, the purple-on-purple pairing is perceptually poor at `bodyMedium` size — particularly for thin characters like dashes "-". The field also omitted `cursorColor`, which defaulted to an incorrect color.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` | `BasicEditField`: changed `focusedTextColor`/`unfocusedTextColor` from `onPrimaryContainer` → `onSurface`; added `cursorColor = primary` |

## Surfaces Fixed

- History → workout detail → edit mode: weight and reps input fields now show near-white (#EDEDEF) text on the deep purple `primaryContainer` background

## How to QA

1. Open PowerME and navigate to the **History** tab
2. Tap any completed workout to open the summary view
3. Tap the **Edit** (pencil) icon to enter edit mode — the screen title changes to "Workout History" with a **Save** button
4. Verify: weight and reps cells display clearly readable white/near-white text on the purple background
5. Tap a weight or reps field — verify the cursor is visible (violet color)
6. Edit a value and confirm the typed text is clearly readable
