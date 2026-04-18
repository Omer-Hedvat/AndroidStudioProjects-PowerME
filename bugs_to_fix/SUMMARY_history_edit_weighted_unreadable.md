# Fix Summary: History edit mode — weighted exercise fields unreadable

## Root Cause
`BasicEditField` in `StrengthSetDetailRow` used `OutlinedTextField`, which has a **minimum intrinsic height of ~56dp** in Material3 Compose. The wrapping `Row` constrained height to 36dp, so the `OutlinedTextField` overflowed its bounds — causing unpredictable rendering where neither the `surfaceVariant` Box background nor the explicit `onSurface` text color from the previous fix actually took effect visually.

The previous fix (v2) correctly set Box background to `surfaceVariant` and text to `onSurface`, but since the `OutlinedTextField` internal sizing violated the layout constraints, the visible result was still broken.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` | Replaced `OutlinedTextField` with `BasicTextField` in `BasicEditField`; added explicit `color = onSurface` in textStyle; added `decorationBox` for centering; added `BasicTextField` import |

## Surfaces Fixed
- History tab → tap workout (edit mode): weighted exercise weight and reps fields now render with the correct `surfaceVariant` grey background and readable `onSurface` near-white text, matching timed/cardio row styling.

## How to QA
1. Open the History tab
2. Tap any completed workout that contains at least one weighted exercise (e.g. Barbell Shrug, Bench Press)
3. Verify: weight and reps fields have a grey background with clearly readable text (dashes or values)
4. Tap a field to confirm it is editable and the keyboard appears
5. Optionally, tap a workout with timed exercises and confirm those rows remain unchanged
