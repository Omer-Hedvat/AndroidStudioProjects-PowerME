# Fix Summary: Firing a rest timer while another is running starts both instead of skipping the current

## Root Cause
`startRestTimer()` already cancelled the coroutine job and service timer when called with an active timer, but it never updated `finishedRestSeparators` or fired the warmup-collapse path. As a result, the previous timer's rest separator stayed visible on screen while the new timer started, producing overlapping UI state.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added guard at top of `startRestTimer()`: when a timer is already active, cancels job/service and adds the outgoing timer's separator key to `finishedRestSeparators` before starting the new timer. Warmup-collapse stagger is deliberately skipped here to avoid premature collapse when the incoming timer is also a warmup timer. |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added two new tests: `startRestTimer while timer active skips first timer before starting new one` and `startRestTimer while warmup timer active skips warmup and collapses after stagger`. |

## Surfaces Fixed
- Active workout screen: completing a set while a rest timer is counting down now implicitly skips the old timer (hides its separator) and starts the new timer cleanly with a fresh duration.

## How to QA
1. Open a workout with at least 2 sets on one exercise (or two exercises).
2. Complete set 1 — rest timer countdown starts.
3. **Before the timer reaches 0**, complete set 2.
4. Verify: the set-1 rest separator immediately disappears and the set-2 rest timer starts from the full duration.
5. Repeat with 3 rapid set completions — each new timer should replace the previous one, no duplicate countdowns.
6. For warmup variant: add 2 warmup sets + 1 normal set. Complete warmup 1 → rest timer starts. Complete warmup 2 before timer ends → warmup-1 separator hides, warmup-2 timer starts. Let warmup-2 timer finish → warmup rows collapse 500ms after the separator hides.
