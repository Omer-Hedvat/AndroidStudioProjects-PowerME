# Warmup Sets — Auto-Collapse After Completion

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `completed` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |

---

## Overview

Warmup sets are preparatory — once completed, they don't need to remain visually prominent. After all warmup sets for an exercise are done, they auto-collapse into a compact summary row, keeping the screen focused on the upcoming working sets.

---

## Behaviour

- When the **last warmup set** of an exercise is confirmed, the warmup rows animate closed and collapse into a single compact row (e.g. "3 warmup sets ✓")
- Collapsed state shows: count of warmup sets, a checkmark, and a chevron to re-expand
- Tapping the collapsed row expands warmup sets back to full view
- If a warmup set is partially done (some confirmed, some not), no collapse occurs
- Only warmup sets collapse — drop sets, failure sets, and working sets are unaffected
- Collapse is per-exercise, not global

---

## UI Changes

- Add a `warmupsCollapsed: Boolean` flag per exercise in `ActiveWorkoutState`
- Collapsed warmup row: single `ListItem` with "W ×N ✓" label, `onSurfaceVariant` text, trailing `ExpandMore`/`ExpandLess` icon
- Animate collapse/expand using `AnimatedVisibility`

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — collapsed warmup row composable + AnimatedVisibility
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — `warmupsCollapsed` state, trigger on last warmup confirm

---

## How to QA

1. Start a workout with 3 warmup sets + 4 working sets
2. Complete all 3 warmup sets → they collapse into "W ×3 ✓" row automatically
3. Tap the collapsed row → expands back to individual warmup rows
4. Complete only 2 of 3 warmup sets → no auto-collapse
5. Working sets are unaffected throughout
