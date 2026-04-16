# BUG: Timed exercise row has wrong columns (no PREV, spurious RPE field)

## Status
[ ] Open

## Description
In the Active Workout screen, timed (time-based) exercises render incorrect columns in their set rows:

1. **Missing PREV column** — regular exercises show a PREV column to display the previous session's performance. Timed exercise rows have no such column, so there is no historical reference while working out.
2. **RPE column present but shouldn't be** — timed exercises do not require an RPE rating. The RPE column is shown in the header but serves no purpose for this exercise type.
3. **Orphaned '-' box with no header** — there is a box showing only a dash (`-`) that has no corresponding column header above it. This appears to be a misaligned or leftover element in the row layout.

## Steps to Reproduce
1. Create or open a routine that contains at least one timed exercise (e.g. Bird-Dog).
2. Start an active workout.
3. Scroll to the timed exercise card.
4. Observe the column headers (SET, WEIGHT, TIME(S), RPE) and the set row contents.

## Assets
- Screenshot: provided by user (Bird-Dog card in active workout, headers SET | WEIGHT | TIME(S) | RPE with a '-' box and play/check buttons in the row)

## Fix Notes
<!-- populated after the fix is applied -->
