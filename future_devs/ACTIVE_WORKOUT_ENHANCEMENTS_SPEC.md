# Active Workout — Enhancements Spec

| | |
|---|---|
| **Phase** | P0 (row spacing) · P1 (golden RPE, timed timer) |
| **Status** | `done` |
| **Effort** | XS + S + M |
| **Depends on** | — |
| **Roadmap** | `ROADMAP.md §P0` and `§P1` |

**Related specs:** `WORKOUT_SPEC.md`, `TOOLS_SPEC.md`

---

## 1. Golden RPE Indicator (RPE 8–9 highlighting)

### Background — Professional Literature
RPE 8–9 (1–2 Reps in Reserve) is the evidence-backed hypertrophy sweet spot:
- **Israetel, Hoffman & Case (2019)** — "Renaissance Periodization": 8–9 RPE is optimal for accumulation blocks; below 7 is junk volume, above 9.5 risks excessive fatigue.
- **Schoenfeld (2010, J Strength Cond Res)** — proximity to failure is a primary driver of hypertrophic stimulus; 8–9 RPE balances stimulus and recovery.
- **RIR-based rating (Zourdos et al., 2016)** — RPE 8 = 2 RIR, RPE 9 = 1 RIR. This zone maximises motor unit recruitment without central nervous system overreach.
- **Practical guideline**: RPE 7 or below → undertrained set; RPE 10 (0 RIR) → failure, high fatigue cost; RPE 8–9 → "golden zone."

### What to Build
After a user logs an RPE value on a set, visually indicate whether the set falls in the golden zone (8–9), below it, or above it.

### UI Spec
- In the set row, after RPE is entered and the set is marked complete, show a small badge or colored dot next to the RPE value:
  - RPE < 7: muted grey dot — "Leave more in the tank"
  - RPE 7: amber dot — acceptable
  - **RPE 8–9: gold/yellow star or ✦ glyph** — "Golden zone" — this is the primary highlight
  - RPE 9.5–10: red dot — "Max effort / recovery cost"
- Tooltip / long-press: brief explanation e.g. "RPE 8–9 is the evidence-based sweet spot for muscle growth"
- Highlight should only appear after the set is completed (not during input)
- Color tokens to use: `ProMagenta` or a dedicated `GoldenRPE = Color(0xFFFFD700)` constant for the star glyph; use `ProError` for RPE 10

### Implementation Notes
- RPE is stored ×10 as `Int?` (`WorkoutSet.rpe`). Golden zone = `rpe in 80..90`.
- Add `fun rpeCategory(rpe: Int?): RpeCategory` enum helper in a new `RpeHelper.kt` under `util/`:
  ```kotlin
  enum class RpeCategory { NONE, LOW, MODERATE, GOLDEN, MAX_EFFORT }
  fun rpeCategory(rpe: Int?): RpeCategory = when {
      rpe == null       -> NONE
      rpe < 70          -> LOW
      rpe < 80          -> MODERATE
      rpe <= 90         -> GOLDEN
      else              -> MAX_EFFORT
  }
  ```
- Composable change: `WorkoutSetRow` — add the badge after the RPE chip when `set.isCompleted && set.rpe != null`

---

## 2. Set Row Spacing

### Current State
Only a `2.dp` Spacer between set rows (`ActiveWorkoutScreen.kt:836`) — visually cramped, no clear separation between sets.

### What to Build
Increase inter-row spacing so individual sets are easier to tap and visually distinct.

### UI Spec
- Replace the current `2.dp` Spacer with an `8.dp` Spacer between rows
- Completed rows (green background) benefit most from the extra breathing room
- Do **not** add spacing after the last set (existing guard at line 835 already handles this)

### Implementation
Single line change in `ActiveWorkoutScreen.kt:836`:
```kotlin
// Before
Spacer(modifier = Modifier.height(2.dp))
// After
Spacer(modifier = Modifier.height(8.dp))
```

---

## 3. Timed Exercise — In-Set Countdown Timer

### Background
For `ExerciseType.TIMED` exercises (e.g. Plank, Dead Hang, L-Sit), the user logs a target duration. Currently the `TimedSetRow` has fields but no actual countdown — the user must rely on a separate clock.

### What to Build
An integrated per-set countdown timer inside `TimedSetRow` that counts down from the target duration when started, with the same audio/haptic features as the Clocks tab Countdown timer.

### UI Spec

**Setup phase (IDLE state):**
- Show the target duration as an editable field (pre-filled from `set.time` / exercise default)
- User can tap the duration to edit it (same inline number-picker pattern as the Clocks tab Countdown timer setup)
- **▶ Start** button below the duration — tapping it transitions to RUNNING
- Small label: "Hold to set duration" or tap-to-edit affordance

**Running phase:**
- Replace the duration field with a large countdown display (`MM:SS` or `M:SS`)
- `LinearProgressIndicator` sweeping left-to-right showing elapsed / total
- **■ Stop** button — pauses at current remaining time

**Paused phase:**
- Shows remaining time (static)
- **▶ Resume** button + **✓ Mark Done** button (marks set complete without waiting for zero)
- **↺ Reset** button — returns to IDLE with original duration

**Completion (reaches 0):**
- Play completion beep (same sound as Clocks tab countdown)
- Haptic feedback (same pattern as rest timer completion)
- Auto-call `onCompleteSet` → set marked complete, rest timer starts as normal
- Brief "Done!" flash on the row

The timer runs per-set — each set has independent state; completing one does not auto-start the next.

### State Machine
```
IDLE (editable duration) → RUNNING → PAUSED → RUNNING
                                    ↓          ↓
                                 COMPLETED  MARK DONE (→ COMPLETED)
                                    ↓
                               (auto-complete set)
       PAUSED → RESET → IDLE
```

### Implementation Notes
- Add `TimedSetTimerState` to `ActiveWorkoutState` (or as local Compose state in `TimedSetRow` — prefer local state since it doesn't need to survive navigation)
- Reuse the `CountdownBeepPlayer` / audio utility already used by the Clocks tab (`TOOLS_SPEC.md`)
- Target duration comes from `set.time` (stored as seconds `Int`)
- Use `LaunchedEffect(timerRunning)` + `delay(1000L)` loop (same pattern as `WorkoutViewModel.startRestTimer()`)
- Wake lock: not required for per-set timers (rest timer already handles this)

### Files to Change
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — `TimedSetRow` composable
- Potentially extract a `SetCountdownTimer` composable into `ui/components/`

---

## How to QA

### §2 — Row Spacing
1. Open an active workout with at least 3 sets on one exercise.
2. Verify the gap between set rows is visually wider than before (8dp spacing).

### §1 — Golden RPE Indicator
1. Start an active workout (STRENGTH exercise).
2. Complete a set, then tap the RPE column and select **RPE 8.5** → confirm a gold **✦** star appears next to `8.5`.
3. Complete another set, select **RPE 7** → confirm a small **amber dot** appears.
4. Complete another set, select **RPE 6** → confirm a small **grey dot** appears.
5. Complete another set, select **RPE 10** → confirm a small **red dot** appears.
6. On an incomplete (unchecked) set, select any RPE → confirm **no badge** is shown.
7. Confirm tapping the RPE cell still opens `RpePickerSheet` normally.

### §3 — Timed Exercise Countdown Timer
1. Add a TIMED exercise (e.g. Plank, Dead Hang) to a workout.
2. **IDLE state:** Verify the row shows weight / time / RPE input fields + a purple **▶** button + checkbox.
3. Enter `30` in the time field and tap **▶** → timer transitions to **RUNNING**.
4. **RUNNING state:** Verify `0:30` countdown in TimerGreen, a progress bar sweeping left, and a **■ Stop** button. Wait a few seconds and confirm the number decrements.
5. Tap **■ Stop** → timer transitions to **PAUSED**.
6. **PAUSED state:** Verify remaining time shown (static), and **▶ Resume**, **✓ Done**, and **↺ Reset** buttons are all visible.
7. Tap **▶ Resume** → countdown resumes from where it paused.
8. Tap **■ Stop** again, then tap **↺ Reset** → timer returns to **IDLE** with the original time restored.
9. Start the timer again and tap **✓ Done** from PAUSED → set is immediately marked complete (green check), no countdown needed.
10. Start a fresh timer and let it count all the way to `0:00` → verify: beep + haptic fires, set auto-completes (green check row background), rest timer starts as normal.
11. Start a timer, then manually tap the checkbox while it is **RUNNING** → verify the timer stops and the set is marked complete.

---

*Written April 2026. QA section added April 2026.*
