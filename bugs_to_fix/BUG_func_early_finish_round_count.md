# BUG: "Finish Block" mid-block writes wrong round count to history

## Status
[x] Fixed

## Severity
P1 high

## Description
When the user taps "FINISH BLOCK" before the block naturally completes (e.g. tapping out of a 10-min EMOM after round 4), the `BlockFinishSheet` round count field may default to the block's total planned rounds (10) rather than the number of rounds actually completed (4). This means history records inflated or incorrect round data.

The correct behavior: `BlockFinishSheet` pre-fills with the number of completed rounds at the moment "Finish Block" was tapped, letting the user adjust if needed before saving. Partial completions are valid history entries and must reflect reality.

## Steps to Reproduce
1. Create a 10-round EMOM block.
2. Start the workout, launch the block.
3. After completing 4 rounds, tap "FINISH BLOCK".
4. In `BlockFinishSheet`, observe the rounds field — it may show 10 instead of 4.
5. Save and check history — wrong round count is stored.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/BlockFinishSheet.kt`, `ui/workout/WorkoutViewModel.kt`, `ui/workout/FunctionalBlockRunner.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`

## Fix Notes
`BlockFinishSheet` was initialising `rounds` with `state.roundTapCount.coerceAtLeast(state.currentRound)`. For EMOM, `currentRound` is the round currently executing (e.g. 8 of 10), which inflated the pre-fill to `currentRound` even when only `roundTapCount` rounds were logged via COMPLETED/SKIP. Fixed to `if (blockType == "TABATA") state.currentRound else state.roundTapCount` — Tabata is auto-run (no tap log) so `currentRound` remains the right source; all other types use the confirmed tap count.
