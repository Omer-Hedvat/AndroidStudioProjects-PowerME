# BUG: Functional overlay mid-round alert timing wrong — EMOM needs 30s+10s alerts; RFT needs mid-cap alert

## Status
[ ] Open

## Severity
P1 high
- Athletes rely on audio/haptic cues to manage pacing; wrong or missing alerts break the core functional training experience

## Description
The current mid-round alert implementation fires only once per interval at the 10-second mark. User feedback and spec alignment require:

**EMOM:**
- Alert at **30 seconds remaining** in the current interval (half-warning — athlete knows to wrap up the current movement)
- Alert at **10 seconds remaining** (final warning — transition cue)
- This applies regardless of interval length (60s, 90s, 120s, etc.)

**RFT:**
- Alert at **mid-cap time** (i.e. when exactly half the time cap has elapsed — e.g. 1:30 into a 3:00 cap)
- The existing 10s alert at cap expiry is correct and should be kept

**AMRAP:**
- Alert at **half-time** (e.g. 6:00 into a 12:00 block) — athlete knows they're halfway
- The existing 10s alert at cap expiry is correct and should be kept

**TABATA:**
- No change needed — work/rest transitions are already cued by the ring colour change

The existing `BUG_func_overlay_mid_round_alert` fix wired `warnAtSeconds` in `FunctionalBlockRunner.mapToTimerSpec()` but set a single threshold. The engine needs to support two alert thresholds per interval (EMOM) and a mid-cap alert (RFT/AMRAP).

## Steps to Reproduce
1. Create an EMOM block with 60-second intervals.
2. Start the block and watch the ring count down.
3. Observe: alert fires only at 10s remaining — no alert at 30s.
4. Create an RFT block with a 3-minute cap.
5. Start the block and let it run.
6. Observe: no alert fires at 1:30 (mid-cap).

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `util/timer/TimerEngine.kt`, `ui/workout/FunctionalBlockRunner.kt`, `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
