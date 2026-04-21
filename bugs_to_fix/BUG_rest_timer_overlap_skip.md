# BUG: Firing a rest timer while another is already running starts both instead of skipping the current one

## Status
[x] Fixed

## Severity
P2 normal

## Description
When a rest timer is already counting down and the user completes another set (triggering a new rest timer), both timers compete. The correct behaviour is: starting a new rest timer while one is already running should implicitly skip (finish) the current timer first, then start the new one — identical to the user having tapped Skip in `TimerControlsSheet`.

## Steps to Reproduce
1. Start a workout with multiple exercises or sets.
2. Complete a set — rest timer starts (visible countdown).
3. Before the timer finishes, complete another set on the same or a different exercise.
4. Observe: both timers run concurrently (or the UI shows inconsistent state).

Expected: the first timer is skipped and the new timer starts cleanly.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §5`

## Fix Notes
Added a guard at the top of `startRestTimer()` in `WorkoutViewModel.kt`. When a timer is already active, the guard cancels the coroutine/service timer, records the outgoing timer's separator in `finishedRestSeparators` (so the separator hides immediately), then proceeds to start the new timer. Warmup-collapse is intentionally not triggered here — if the new timer is itself a warmup timer it will trigger collapse when it finishes or is skipped. Two new unit tests cover: (1) completing set 2 while set 1's timer runs → set 1's separator is skipped and set 2's timer starts fresh; (2) completing warmup 2 while warmup 1's timer runs → warmup-1 separator is skipped and warmup-2 timer starts without premature collapse.
