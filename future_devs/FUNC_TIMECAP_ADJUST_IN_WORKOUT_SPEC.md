# Functional Blocks — Adjust Time Cap from Within Active Workout

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | S |
| **Depends on** | BUG_func_start_block_noop (START BLOCK must work first), BUG_func_timecap_no_alert (cap must fire before UI to change it makes sense) |
| **Blocks** | — |
| **Touches** | `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/WorkoutViewModel.kt`, `data/database/WorkoutBlock.kt`, `data/database/WorkoutBlockDao.kt` |

---

## Overview

Once a functional block is running, the only way to change the time cap is to stop the block and re-edit the template. Athletes often decide mid-WOD to extend or shorten a cap based on how the session is going. This feature adds an in-workout control to adjust the time cap without interrupting the timer.

Applies to block types that support a time cap: **AMRAP** (always has a cap) and **RFT** (optional cap).

---

## Behaviour

- **AMRAP overlay:** A small `⏱ [MM:SS]` chip is displayed in the top bar next to the block name badge. Tapping it opens a compact bottom sheet with a `MM:SS` digit input (same countdown input pattern used in the rest of the app). Saving replaces the active cap on the running timer without resetting elapsed time. The remaining duration is recalculated as `newCapSeconds - elapsedSeconds`.
- **RFT overlay:** Same `⏱ chip` appears only when the block has a time cap set. If no cap was originally set, a `+ Add cap` text button appears instead — tapping opens the same sheet and adds a cap mid-workout. Removing the cap (clearing the field to blank) is also allowed and disables the countdown.
- **Minimum cap:** new cap must be ≥ `elapsedSeconds + 30` (cannot set a cap that has already passed + a 30s buffer). Validated client-side; `SurgicalValidator` enforces it.
- **Persistence:** the updated `durationSeconds` is written to `WorkoutBlock` immediately so resume-from-kill picks up the correct value.
- **No confirmation dialog** for cap changes — the chip's label shows the live countdown so any mistake is immediately visible.

---

## UI Changes

- `⏱ [MM:SS]` chip: `AssistChip` in the overlay top bar, `labelSmall` text, `timerGreen` tint while running, `timerAmber` tint when ≤ 60s remaining.
- Bottom sheet: same `ModalBottomSheet` (24dp corner) + `MM:SS` digit field as used elsewhere in the app. "Save" primary action, "Remove cap" secondary text button (RFT only).
- All color tokens from `MaterialTheme.colorScheme.*` + semantic timer tokens.

---

## Files to Touch

- `ui/workout/runner/RftOverlay.kt` — add cap chip + sheet, wire to ViewModel
- `ui/workout/runner/AmrapOverlay.kt` — add cap chip + sheet, wire to ViewModel
- `ui/workout/WorkoutViewModel.kt` — `updateBlockTimeCap(blockId, newCapSeconds)` action
- `data/database/WorkoutBlockDao.kt` — `updateDurationSeconds(id, newSeconds)` query
- `data/database/WorkoutBlock.kt` — no schema change needed (`durationSeconds` already exists)

---

## How to QA

1. Start an RFT block with a 5-minute cap. While running, tap the cap chip → change to 8 minutes → confirm remaining time updates correctly.
2. Start an RFT block with no cap. Tap `+ Add cap` → set 4 minutes → confirm cap countdown appears.
3. With 1 minute left in an AMRAP, try setting cap to 30 seconds earlier than current elapsed → confirm validation rejects it.
4. Kill and reopen the app mid-WOD → confirm the updated cap is restored (resume-from-kill).
