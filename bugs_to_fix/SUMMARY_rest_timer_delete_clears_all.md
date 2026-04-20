# Fix Summary: Deleting one rest timer removes all rest timers for that exercise

## Root Cause
`deleteRestSeparator()` in `WorkoutViewModel` mirrored `restDurationSeconds = 0` into the in-memory exercise entity after the DAO write. Since `restDurationSeconds` is a single value shared by all working sets on an exercise, zeroing it in memory made `effectiveRest` fall through to `0` for every other working-set separator on the same exercise — instantly hiding all of them in the current session, not just the swiped one.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Removed the `ex.copy(exercise = ex.exercise.copy(restDurationSeconds = 0))` state update from the passive-separator block in `deleteRestSeparator`. Per-set hiding is now handled exclusively by `hiddenRestSeparators` (keyed by `"${exerciseId}_${setOrder}"`), which is correct and per-set. |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Updated `deleteRestSeparator on passive separator updates in-memory exercise restDurationSeconds to zero` → renamed and inverted: now asserts `restDurationSeconds` stays at `90` in memory while the swiped set's key appears in `hiddenRestSeparators`. |

## Surfaces Fixed
- Active workout (non-edit mode): swiping a single rest separator hides only that separator for the current session; adjacent sets of the same exercise keep their separators visible
- Live-workout edit mode (Phase B′ regression fence): same per-set scoping holds in live edit; the DAO is not called at all in live edit (guarded by `isLiveEdit()`)

## How to QA
1. Start a workout with a routine that has an exercise with 3+ working sets (all with rest timers, e.g. 90 s)
2. Complete Set 1 — the rest separator appears
3. Swipe left on the rest separator after Set 1 to dismiss it
4. Verify: ONLY the Set 1→Set 2 separator is gone; the Set 2→Set 3 separator is still present
5. (Regression check) In a fresh workout, verify rest separators still appear after completing sets
6. (Live-edit regression check) Enter live-workout edit mode → swipe a rest separator → tap ✕ → verify all rest separators are restored
