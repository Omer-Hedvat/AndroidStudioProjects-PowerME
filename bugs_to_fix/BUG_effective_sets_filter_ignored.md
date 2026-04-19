# BUG: Effective Sets card — time range filter chips have no effect

## Status
[x] Fixed

## Severity
P1 high
- Filter chips are non-functional: all time ranges (1M, 3M, 6M, 1Y) show the same data.

## Description
In the EffectiveSetsCard (Trends tab → EFFECTIVE SETS), tapping the time range chips (1M, 3M, 6M, 1Y) does not change the displayed data. The same bars appear regardless of which chip is selected.

Root cause likely: the selected time range is not being passed to the TrendsDao query, or the ViewModel is not re-fetching data when the chip selection changes.

## Steps to Reproduce
1. Navigate to Trends tab → EFFECTIVE SETS card
2. Note the bars shown on the default chip (e.g. 1M)
3. Tap 3M — same bars shown
4. Tap 6M — same bars shown
5. Tap 1Y — same bars shown

## Dependencies
- **Depends on:** BUG_muscle_group_table_not_seeded ✅
- **Blocks:** —
- **Touches:** `EffectiveSetsCard.kt`, `TrendsRepository.kt`, `TrendsDao.kt`, `MetricsViewModel.kt`

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md §Step 5`

## Fix Notes

### First fix (SQL grouping — landed, QA failed)
Root cause identified: `getWeeklyEffectiveSets` SQL used `MIN(w.timestamp)` per `(weekBucket, majorGroup)`, giving each muscle group a different `weekStartMs` for the same ISO week. Every workout appeared as a separate bar, making all time ranges look structurally identical.

Fix: replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` so all muscle groups trained in the same week share the same epoch-aligned `weekStartMs`. ViewModel wiring and SQL `w.timestamp >= :sinceMs` filter were already correct.

QA result: still showed "same data" for all chips after this fix.

### Second fix (push-job race condition — resolves QA failure)
Root cause: Each `push<Chart>ToProducer` launches `viewModelScope.launch { ... }` **outside** the `loadJob` scope. When the user taps a chip while a previous `loadAll()` is still running:
1. `loadJob.cancel()` stops the in-flight DAO query, but
2. any already-launched push coroutine continues to run in `viewModelScope`.

If the slower (larger-range) push's `runTransaction` completes **after** the newer push, stale data overwrites the chart. The effective-sets query (3 JOINs + GROUP BY + RPE filter) is the slowest, making this card most susceptible.

Fix: added six `*PushJob: Job?` fields to `TrendsViewModel` — one per producer. Each `push*ToProducer` method cancels its prior job before launching a new one (`effectiveSetsPushJob?.cancel(); effectiveSetsPushJob = viewModelScope.launch { … }`). Applied symmetrically to all six push methods to eliminate the same latent race on other charts.

Also fixed `TrendsViewModelDeepLinkTest.tearDown()` — was missing `Thread.sleep(100)` before `resetMain()`, same guard that `TrendsViewModelEffectiveSetsFilterTest` already had for Vico's real background thread.

New test file: `TrendsRepositoryEffectiveSetsTest.kt` — 10 tests verifying correct `sinceMs` per range for `getWeeklyEffectiveSets` and `getEffectiveSetsCoverage`, plus data mapping and edge cases. All 10 pass.
