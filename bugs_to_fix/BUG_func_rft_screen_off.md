# BUG: Screen turns off during RFT overlay despite "Keep Screen On — Always" setting

## Status
[x] Fixed

## Severity
P1 high
- Screen going dark mid-block is a major usability issue — the user cannot see the timer or tap ROUND ✓

## Description
When "Keep Screen On" is set to **Always** in Settings, the screen should never go off while the app is in the foreground. During an active RFT functional block overlay, the screen still turns off after the standard system timeout. The `FLAG_KEEP_SCREEN_ON` flag added in `MainActivity` for ALWAYS mode appears not to be respected inside the RFT full-screen overlay.

This may be because:
- The overlay composable or its host `Activity` window loses focus / transitions to a new window layer that doesn't inherit the flag.
- The `WorkoutTimerService` foreground notification causes an activity-pause that clears the flag.

The EMOM and AMRAP overlays should be verified for the same issue.

## Steps to Reproduce
1. In Settings → Display & Workout, set **Keep Screen On** to **Always**.
2. Create an RFT block.
3. Start the workout and tap ▶ START BLOCK.
4. Leave the phone on a flat surface without touching it.
5. Observe: screen dims and turns off after the system timeout (30–60 seconds).

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`, `ui/MainActivity.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `future_devs/KEEP_SCREEN_ON_MODE_SPEC.md`

## Fix Notes
Root cause (two-part): (1) `ActiveWorkoutScreen`'s `DisposableEffect` never actively set `view.keepScreenOn = true` for ALWAYS mode — it only passively avoided setting it to false. (2) The `onDispose` block called `view.keepScreenOn = false` when the mode changed from DURING_WORKOUT to ALWAYS (using the old captured value), which cleared the `FLAG_KEEP_SCREEN_ON` set by `MainActivity`. Fix: comprehensive `DisposableEffect` that sets `view.keepScreenOn` for all three modes (ALWAYS→true, DURING_WORKOUT→isActive, else→false), and `onDispose` skips the clear when mode is ALWAYS so the window-level flag in `MainActivity` is not clobbered. Scope: affects both overlay screens (RFT/AMRAP/EMOM/TABATA) and plain gym workouts.
