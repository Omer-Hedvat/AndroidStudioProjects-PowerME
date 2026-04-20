# Fix Summary: Muscle Balance + Effective Sets cards group volume by day instead of by week

## Root Cause

`getWeeklyMuscleGroupVolume` and `getWeeklyEffectiveSets` in `TrendsDao` used
`MIN(w.timestamp) AS weekStartMs` grouped by `(weekBucket, majorGroup)`.

When a user has workouts on different days of the same week (e.g. Chest on Monday, Legs on Thursday),
each muscle group gets a different `weekStartMs` — Chest gets Monday's timestamp, Legs gets Thursday's.

The chart rendering code builds the week list with:
```kotlin
val weeks = points.map { it.weekStartMs }.distinct().sorted()
```

Since Chest and Legs have different `weekStartMs` values, two distinct "week" entries are created,
and the chart renders two bars instead of one.

## Fix

Changed `MIN(w.timestamp)` to `(w.timestamp / 604800000 * 604800000)` in both SQL queries.

This computes the epoch-aligned week bucket start: integer division by the number of ms in a week,
then multiply back. The result is a constant for all rows in the same week bucket, regardless of
which day of the week the specific muscle group was trained.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/TrendsDao.kt` | `getWeeklyMuscleGroupVolume`: replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` as `weekStartMs` |
| `app/src/main/java/com/powerme/app/data/database/TrendsDao.kt` | `getWeeklyEffectiveSets`: same weekStartMs fix |
| `app/src/test/java/com/powerme/app/data/repository/TrendsRepositoryWeeklyGroupingTest.kt` | New test: verifies same weekStartMs for all muscle groups in same week; verifies sinceMs filter passed correctly |

## Surfaces Fixed

- **MUSCLE BALANCE card** (Trends tab): stacked bars now aggregate all workouts from the same calendar week into a single bar instead of one bar per workout
- **EFFECTIVE SETS card** (Trends tab): same fix; weekly RPE set counts are now properly bucketed by week

## How to QA

1. Log at least 2 workouts in the same calendar week on different days (e.g. Monday chest+back, Thursday legs)
2. Navigate to Trends tab → scroll to MUSCLE BALANCE card
3. Verify: Monday and Thursday workouts appear as **one combined bar**, not two separate bars
4. Repeat for EFFECTIVE SETS card — same verification
5. Switch time range chips (1M / 3M / 6M / 1Y) — each range should show a visually distinct number of weekly bars
