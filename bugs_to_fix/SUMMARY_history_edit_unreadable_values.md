# Fix Summary: History edit mode — weight/reps values unreadable

## Root Cause
The edit-mode `BasicEditField` in `WorkoutDetailScreen` used default `OutlinedTextFieldDefaults.colors()` without specifying `focusedTextColor` / `unfocusedTextColor`. In dark theme, the field's container colour (`primaryContainer` = `#2D2052`) is dark purple, but the default text colour resolved to a dark tint, making text invisible against the background.

## Files Changed
| File | Change |
|---|---|
| `ui/history/WorkoutDetailScreen.kt` | Added `focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer` and `unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer` to `OutlinedTextFieldDefaults.colors()` in `BasicEditField` (line ~554) |

## Surfaces Fixed
- Weight and reps input fields in History workout edit mode are now legible in dark theme
- Light theme unaffected (`onPrimaryContainer` has adequate contrast in both themes)

## How to QA
1. Go to **History** tab → tap any completed workout
2. Tap **Edit** (top-right)
3. **Expected:** weight and reps values (or placeholder dashes) are clearly visible in the input fields
4. **Not expected:** fields appear as dark boxes with invisible text
5. Repeat in both **Dark** and **Light** theme (Settings → Appearance → Theme)
