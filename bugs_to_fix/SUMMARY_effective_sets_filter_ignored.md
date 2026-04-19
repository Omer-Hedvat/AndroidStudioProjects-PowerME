# Fix Summary: Effective Sets card — time range filter chips have no effect (second fix)

## Root Cause

Two separate bugs. Both required fixing before QA passed.

### Bug 1 (first fix — SQL grouping)
`getWeeklyEffectiveSets` used `MIN(w.timestamp)` per `(weekBucket, majorGroup)`. Each muscle group trained on a different day of the same week got a unique `weekStartMs`, causing the chart to render one bar per workout instead of one bar per week. All ranges appeared structurally identical.

### Bug 2 (second fix — push-job race condition)
Each `push<Chart>ToProducer` launches `viewModelScope.launch { }` independent of `loadJob`. When `loadJob` is cancelled (on chip switch), in-flight push coroutines from the previous load continue running. If the larger-range push (slower query) finishes after the smaller-range push, it overwrites the Vico producer with stale data. The effective-sets SQL is the most complex (3-table JOIN + GROUP BY + RPE filter), making this card the most susceptible.

## Fixes

**Bug 1 (TrendsDao.kt — already applied):** replaced `MIN(w.timestamp)` with `(w.timestamp / 604800000 * 604800000)` in `getWeeklyEffectiveSets`.

**Bug 2 (TrendsViewModel.kt):** added 6 `*PushJob: Job?` fields. Each `push*ToProducer` method cancels its prior job before launching a new one. This ensures the most-recently-requested time range always wins the producer.

**TrendsViewModelDeepLinkTest.kt:** added missing `Thread.sleep(100)` before `resetMain()` in tearDown — same guard already present in sibling test classes.

## Files Changed

| File | Change |
|---|---|
| `TrendsViewModel.kt` | Added 6 push-job tracking fields; cancel-before-launch in all 6 `push*ToProducer` methods |
| `TrendsRepositoryEffectiveSetsTest.kt` | New: 10 tests for `getWeeklyEffectiveSets`/`getEffectiveSetsCoverage` sinceMs, mapping, and edge cases |
| `TrendsViewModelDeepLinkTest.kt` | Fixed tearDown: added `Thread.sleep(100)` before `resetMain()` |

## How to QA

1. Navigate to Trends tab → scroll to **EFFECTIVE SETS** card (requires workouts with RPE logged).
2. Tap **1M** chip — note the bars shown.
3. Quickly tap **1Y** then **1M** then **3M** in rapid succession (~500ms each).
4. After chips settle, chart must show bars matching the last-selected chip — no stale data.
5. With multi-month RPE data: 1M should show fewer week-bars than 1Y; 3M intermediate.
