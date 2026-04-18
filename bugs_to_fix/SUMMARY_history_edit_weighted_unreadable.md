# Fix Summary: History edit mode — weighted exercise fields unreadable

## Root Cause
`StrengthSetDetailRow` used `MaterialTheme.colorScheme.primaryContainer` (dark purple `#2D2052` in dark theme) as the Box background for weight and reps input fields. The previous v2 fix corrected text colors inside `BasicEditField` but left the parent container background unchanged. Timed/cardio rows already used `surfaceVariant` (grey), which is the correct readable token.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` | Lines 443, 454: changed Box background from `primaryContainer` → `surfaceVariant` on weight and reps fields in `StrengthSetDetailRow` |

## Surfaces Fixed
- History tab → tap workout → pencil icon (edit mode): weighted exercise weight and reps fields now render with a grey (`surfaceVariant`) background and readable `onSurface` text, matching timed/cardio exercise row styling.

## How to QA
1. Open the History tab
2. Tap any completed workout that contains at least one weighted exercise (e.g. Barbell Shrug, Bench Press)
3. Tap the pencil icon to enter edit mode
4. Verify: weight and reps fields have a grey background with clearly readable text (dashes or actual values)
5. Optionally, also tap a workout with timed exercises and confirm those rows remain unchanged
