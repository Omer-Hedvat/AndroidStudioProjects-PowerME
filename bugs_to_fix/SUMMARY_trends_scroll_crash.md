# Fix Summary: Trends tab crashes when scrolling to VolumeTrendCard

## Root Cause

Vico 2.x's `CartesianChartModelProducer.runTransaction` is a **suspend function that blocks until a `CartesianChartHost` is registered**. All three chart cards (Volume, E1RM, MuscleGroup) had their `push*ToProducer()` functions declared as `suspend fun` and called inline inside `loadAll()`'s `coroutineScope {}`.

MuscleGroupVolumeCard is the **5th item** in the Trends LazyColumn (off-screen on initial load). Its `CartesianChartHost` never attaches during `init {}`, so `pushMuscleGroupToProducer()`'s `runTransaction` call suspended indefinitely. This had two effects:

1. **Blocked `loadAll()` completion** — `coroutineScope {}` waits for ALL child coroutines; the suspended MuscleGroup push prevented the `finally { _isLoading = false }` block from ever running.
2. **Crashed on scroll** — when the user scrolled down and the host attached, the queued transaction resumed mid-composition, causing the crash.

Volume and E1RM cards are items #2–3 (pre-fetched), so their hosts typically attached before the coroutines ran — hiding the same latent bug.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/TrendsViewModel.kt` | Changed `pushE1rmToProducer()`, `pushMuscleGroupToProducer()` from `suspend fun` to regular `fun` wrapped in `viewModelScope.launch {}`. Extracted `pushVolumeToProducer()` from `loadWeeklyVolume()` with same pattern. |

## Surfaces Fixed

- Trends tab → no longer crashes when scrolling down to VolumeTrendCard / MuscleGroupVolumeCard
- `_isLoading` now clears correctly (was permanently stuck `true` when MuscleGroup data loaded before scroll)
- All three chart cards now push data safely, independent of whether their host is attached at call time

## How to QA

1. Sign in and navigate to the Trends tab
2. **Scroll down** past the Readiness Gauge card toward "VOLUME TREND" — no crash
3. Continue scrolling to "MUSCLE BALANCE" — no crash, chart renders
4. Tap each time-range chip (1M / 3M / 6M / 1Y) on all three chart cards rapidly — no crash
5. Navigate away to another tab and back to Trends — charts still render, no crash
6. Scroll all chart cards off-screen and back — no crash
7. Confirm the loading spinner disappears after initial load (was permanently stuck before the fix)
