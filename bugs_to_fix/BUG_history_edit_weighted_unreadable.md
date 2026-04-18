# BUG: History edit mode — weighted exercise fields unreadable (dark text on dark purple)

## Status
[x] Fixed

## Severity
P1 high
- Daily-use regression: users cannot read or edit weight/reps values for regular exercises in workout history.

## Description
In the History edit screen (Workout History → pencil icon), weighted exercise rows (e.g. Barbell Shrug) show weight and reps input fields with a dark purple/indigo background and unreadable text (dashes `-` and `- -` appear on same-shade background). Timed exercise rows (e.g. Bird-Dog) are unaffected — their fields use a grey/lighter background with clearly readable values.

This is a partial fix regression from BUG_history_edit_unreadable_values_v2: the fix applied correct text/background colors only to timed exercise input fields but left weighted exercise fields unstyled/broken.

Screenshot: `bugs_to_fix/assets/history_edit_weighted_unreadable/screenshot.png`

## Steps to Reproduce
1. Open History tab, tap any completed workout containing at least one weighted exercise
2. Tap the Edit (pencil) icon to enter edit mode
3. Observe: weight and reps fields on weighted exercise rows are dark purple with unreadable content
4. Compare: timed exercise rows (if present) are readable with grey field backgrounds

## Dependencies
- **Depends on:** BUG_history_edit_unreadable_values_v2 ✅ (partial fix)
- **Blocks:** —
- **Touches:** `WorkoutDetailScreen.kt`

## Assets
- Related spec: `HISTORY_ANALYTICS_SPEC.md`

## Fix Notes
Changed the Box background color tokens for weight and reps input fields in `StrengthSetDetailRow` from `primaryContainer` (dark purple `#2D2052`) to `surfaceVariant` (grey). This matches the pattern used by `TimedSetDetailRow` and `CardioSetDetailRow`. The `BasicEditField` text already used `onSurface` (from v2 fix), so this background change makes the fields fully readable.
