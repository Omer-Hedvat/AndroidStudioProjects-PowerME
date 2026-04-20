# BUG: Muscle Balance + Effective Sets cards group volume by day instead of by week

## Status
[x] Fixed

## Severity
P1 high
- Core chart behaviour is wrong: the X-axis shows individual workout dates (e.g. Apr 14, Apr 17) instead of weekly buckets, making the "weekly volume" chart meaningless.

## Description
The MuscleGroupVolumeCard stacked bar chart is supposed to show training volume per **week** per muscle group. Instead, each workout gets its own bar on the X-axis — two workouts in the same week (e.g. Apr 14 Monday and Apr 17 Thursday) appear as separate bars rather than being combined into one weekly bar.

Root cause: the SQL queries in `TrendsDao` likely group by `date` (the workout date string or timestamp) rather than by ISO week number. Fix should group by week using SQLite's `strftime('%Y-%W', datetime(startTimeMs/1000, 'unixepoch'))` or equivalent, so all workouts within the same Mon–Sun window are summed into one bar. Affects both MuscleGroupVolumeCard and EffectiveSetsCard queries.

## Steps to Reproduce
1. Log 2+ workouts in the same calendar week on different days (e.g. Monday and Thursday)
2. Navigate to Trends tab → MUSCLE BALANCE card
3. Observe: each workout appears as a separate bar on the X-axis instead of being merged into one weekly bar
4. Repeat for EFFECTIVE SETS card — same issue

## Dependencies
- **Depends on:** BUG_muscle_group_table_not_seeded ✅
- **Blocks:** —
- **Touches:** `TrendsDao.kt`, `TrendsRepository.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`

## Assets
- Related spec: `TRENDS_SPEC.md`, `future_devs/TRENDS_CHARTS_SPEC.md §Step 4`

## Fix Notes
Root cause: `getWeeklyMuscleGroupVolume` and `getWeeklyEffectiveSets` in `TrendsDao` used
`MIN(w.timestamp) AS weekStartMs` grouped by `(weekBucket, majorGroup)`. When a user trains
Chest on Monday and Legs on Thursday of the same week, Chest gets `weekStartMs = Monday` and
Legs gets `weekStartMs = Thursday`. The chart code uses `weekStartMs` as the week key, so
it saw two distinct values and rendered two bars instead of one.

Fix: replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` — the
epoch-aligned week bucket start. This is a deterministic constant for all rows in the same
week bucket, regardless of which day the specific muscle group was trained.
