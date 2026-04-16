# BUG: No setup countdown before timed exercise timer starts

## Status
[x] Fixed

## Description
There is no way for a user to configure a "setup time" — a brief countdown (e.g. 3 seconds) that plays before the main exercise timer begins. When a user taps ▶ on a timed set (e.g. 60-second Plank), the exercise timer starts immediately, giving no time to get into position.

This affects any isometric or bodyweight timed exercise where the user needs a moment to assume the starting position. Without a setup countdown, the recorded hold time is effectively shorter than intended.

**Expected UX:**
- Per-exercise setting: `setupSeconds` (default 0, range 0–10s), editable alongside the exercise duration in the routine builder and optionally in the active workout set row.
- When ▶ is tapped and `setupSeconds > 0`: show a "Get Ready" countdown overlay (3… 2… 1…) with distinct audio/haptic (different from the end-of-set beeps), then automatically start the main exercise timer when it reaches 0.
- When `setupSeconds == 0`: behavior unchanged — timer starts immediately (no regression).

## Steps to Reproduce
1. Add a timed exercise (e.g. Plank, 60 seconds) to a routine.
2. Start an active workout, navigate to that exercise set.
3. Tap ▶ — the 60-second countdown begins immediately with no setup window.

## Assets
- Screenshot: `bugs_to_fix/assets/timed_set_no_setup_countdown/Screenshot_20260415_153532_PowerME.jpg`
- Related spec: `WORKOUT_SPEC.md §4.8` (TimedSetRow), `WORKOUT_SPEC.md §4.4` (audio/haptic feedback)

## Fix Notes
Added a global "Get Ready" countdown setting (default 3s, range 0–10s) that plays before timed exercise timers start:

- New `SETUP` state added to `TimedSetState` enum (`IDLE → SETUP → RUNNING → PAUSED → COMPLETED`)
- When Play is tapped and `setupSeconds > 0`, the timer enters SETUP state showing an amber "Get Ready" countdown with the number of seconds remaining and a cancel button (X)
- An amber progress bar depletes during setup, then the main green exercise timer starts automatically
- Setup plays the same beep+haptic as the warning tick feedback (distinctive from the end-of-set double pulse)
- Setting `setupSeconds = 0` disables setup entirely — timer starts immediately (no regression)
- Setting is configurable in Settings → Rest Timer card via a `[-] [N] [+]` stepper
- Syncs to Firestore via push/restore maps

**Follow-up fix (QA regression):** SETUP countdown was skipping straight to RUNNING due to a Compose snapshot visibility race. The click handler wrote `setupRemaining = setupSeconds` in one snapshot scope; the `LaunchedEffect(timerState)` coroutine read `setupRemaining` in a different scope after recomposition. Fix: moved `setupRemaining = setupSeconds` to be the first line inside the `SETUP` branch of the `LaunchedEffect`, eliminating the cross-scope read. The click handler now only sets `timerState = SETUP`.
