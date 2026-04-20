# Fix Summary: History exercise card — sparkline trend line not inline with stats

## Root Cause
The "View Trend →" `TextButton` was placed as a standalone child in the exercise card's `Column`, below the set details table. It should be inline with the Best Set and Est 1RM stats on a single row per the spec.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` | Moved "Trend →" button into the stats `Row` alongside Best Set and Est 1RM; removed standalone `TextButton` at bottom |

## Surfaces Fixed
- History detail exercise card: stats row now shows `[ Best Set ] [ Est 1RM ] [ Trend → ]` on a single line
- Post-workout summary exercise card: same fix applies

## How to QA
1. Open History tab, tap any completed workout
2. Expand (or observe, if post-workout) any exercise card
3. Verify "Trend →" button appears on the same horizontal line as "Best Set" and "Est. 1RM"
4. Tap "Trend →" and confirm it navigates to the exercise trend screen
5. Confirm there is no standalone trend button below the set details table
