# PowerME — Workout & Routine Spec

> **Living document.** Update this file whenever workout or routine behaviour changes.
> Cross-referenced by `CLAUDE.md`. Read this before touching `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`, or any workout/routine screen.

---

## 1. Visual Identity

Theme: **"Pro-Tracker" Dark Mode** — high-contrast, OLED-optimised.

| Token | Hex | Usage |
|---|---|---|
| Background | `#000000` | Screen background (pure OLED black) |
| Surface | `#121212` | Cards, top bar |
| SurfaceVariant | `#1E1E1E` | Completed set row, chips, input backgrounds |
| Primary (StremioViolet) | `#A061FF` | Crystalline purple — FABs, focus borders, active toggles, exercise name |
| Secondary (StremioMagenta) | `#B3478C` | Superset spine |
| TimerGreen | `#34D399` | **Strictly** for FINISH WORKOUT button and completed set checkmarks |
| Error | `#FF4444` | Delete actions, cancel edit, failure set type |

**Logo logic:**
- **External (launcher):** Full-colour crystalline "P" with radial purple gradient background.
- **Internal (TopAppBar):** Monochrome vector "P" (`ic_app_logo_mono`) dynamically tinted to `onSurface`.

**Rule:** No hardcoded colors in composables. Always use `MaterialTheme.colorScheme.*` tokens.

---

## 2. State Machine

`ActiveWorkoutState` in `WorkoutViewModel` drives everything. The screen renders exactly **one** state at a time. The priority hierarchy is:

```
Loading > Edit Mode > Active Workout > Summary > Idle
```

| Priority | State | Condition |
|---|---|---|
| 1 | **Loading** | Data not yet ready (future: skeleton UI) |
| 2 | **Edit Mode** | `isEditMode = true` |
| 3 | **Active Workout** | `isActive = true` |
| 4 | **Summary** | `isActive=false`, `pendingWorkoutSummary ≠ null` |
| 5 | **Idle** | All flags false/null |

**Concurrent modifier flags (do not occupy a priority slot):**
- `isSupersetSelectMode: Boolean = false` — **Organize Mode** sub-state. Can be `true` only while `isActive = true`. When active, all `ExerciseCard` rows switch to compact `SupersetSelectRow` rows with checkboxes and drag handles. A CAB at the top shows **Done** + **Group** (Sync icon) buttons. The mode persists across `commitSupersetSelection()` calls; it exits only when the user taps **Done** (calls `exitSupersetSelectMode()`). `commitSupersetSelection()` clears `supersetCandidateIds` but does NOT clear `isSupersetSelectMode`.

**Rendering order in `ActiveWorkoutScreen`** (overlays checked first):
1. `StandaloneTimerSheet` — `ModalBottomSheet` opened via timer icon; does NOT block main content
2. `PostWorkoutSummarySheet` — when `pendingWorkoutSummary != null` (contains inline sync CTAs)
3. `LaunchedEffect(editModeSaved)` — fires `onWorkoutFinished()` when `editModeSaved=true`
4. Main content `when` branch — IDLE / LIVE+EDIT

**Hard rule: Never add a new `ActiveWorkoutState` flag without updating this table and the rendering order above.**

---

## 3. User Flow

### Phase A — Entry: The Fork

User taps a routine on the Workouts screen → Routine Overview Sheet → **two paths:**

| CTA | ViewModel call | State set | Iron Vault |
|---|---|---|---|
| **Start Workout** | `startWorkoutFromRoutine(routineId)` | `isActive=true`, `workoutId` assigned | ✅ On |
| **Edit Routine** | `startEditMode(routineId)` | `isEditMode=true`, `workoutId=null` | ❌ Off |

Both paths navigate to the same `ActiveWorkoutScreen`. The fork is invisible to the user — the UI adapts.

**Resume:** If Iron Vault detects an incomplete workout on ViewModel init, a Resume banner appears on the Workouts screen.

### Phase B — Execution (Live Workout)

Exercises loaded from `WorkoutBootstrap` — a repository-level mapper function that takes a `Routine` entity, instantiates its exercises into memory as `ActiveExercise` objects, and generates fresh `workout_sets` rows with correct `setOrder` indices. `WorkoutBootstrap` runs inside `withTransaction` and is the sole path from routine template → live session. Iron Vault writes every subsequent change to DB in real-time.

Key interactions:
- **PREV column** — shows `{weight}×{reps}@{rpe}` from the last chronological completed session for that specific exercise, **regardless of which routine it belonged to** (global scope — do not filter by `routineId`). Displays `—` if no prior data.
- **Complete a set** → checkmark turns TimerGreen → rest timer starts automatically.
- **FINISH WORKOUT** → runs the Diff Engine → summary sheet with inline sync CTAs.

### Phase B′ — Edit Mode (Template Modification)

Same `ActiveWorkoutScreen` UI, `isEditMode=true`. Differences from Live:

| Behaviour | Live | Edit |
|---|---|---|
| Elapsed timer | Shown | Hidden |
| Rest timers | Start on complete | Skipped |
| Footer CTA | FINISH WORKOUT | SAVE CHANGES |
| Iron Vault | Active | Disabled |
| Top bar left icon | ↓ Minimize | ✕ Cancel (error tint) |
| Back press | Minimize | Confirmation dialog |
| Bottom nav bar | Visible | **Hidden / disabled** |

**Per-Set Data in Edit Mode:** Individual set values are fully supported in edit mode and persist across save/reload:

| Data | Column | Default when empty |
|---|---|---|
| Set type (NORMAL/WARMUP/FAILURE/DROP) | `routine_exercises.setTypesJson` | NORMAL |
| Weight per set | `routine_exercises.setWeightsJson` | `defaultWeight` |
| Reps per set | `routine_exercises.setRepsJson` | `reps` |

All three are stored as comma-separated ordered strings (e.g. `"80,85,90"`). `startEditMode()` deserializes them and applies values to each `ActiveSet`. `saveRoutineEdits()` serializes all values sorted by `setOrder`. The `defaultWeight` and `reps` columns are kept in sync with set 1's values for Diff Engine compatibility.

**Rest timer invariant:** `completeSet()` does not call `startRestTimer()` when `isEditMode = true`. This is enforced at the ViewModel level — no rest timer side-effects occur in edit mode.

**Edit Mode is strictly modal.** The user cannot navigate away without an explicit decision:
- Bottom navigation bar must be hidden or disabled while `isEditMode=true`.
- System back and the TopAppBar Cancel (✕) both trigger a confirmation dialog: **[Discard Edits]** · **[Keep Editing]**. Navigation is only permitted after the user explicitly saves or discards.
- There is no silent exit from edit mode.

Save flow: SAVE CHANGES → `saveRoutineEdits()` → atomic `withTransaction` → `editModeSaved=true` → `LaunchedEffect` → `onWorkoutFinished()`.

Discard flow: Confirmation dialog → [Discard Edits] → `cancelEditMode()` → resets `ActiveWorkoutState` → `onWorkoutFinished()`. Must also be called via `DisposableEffect` on screen disposal to prevent stale state leaking into the next session.

### Phase C — Post-Workout: The Diff Engine

See Section 7 for full detail. Summary:

| Delta detected | Action |
|---|---|
| None | History save → summary sheet |
| Values only (weight/reps) | [Update Values] or Skip |
| Structure only (sets/exercises/rest) | [Update Routine] or Skip |
| Both | Triple-action prompt (see §7.2) |

### Phase D — Persistence

| Action | Writes to |
|---|---|
| Weight/reps (live) | `workout_sets` (Iron Vault, debounced 300ms) |
| Set completion | `workout_sets.isCompleted = 1` (immediate) |
| Finish workout | `workouts.isCompleted = 1` + deletes incomplete sets |
| Cancel workout | Deletes all orphaned `workout_sets` rows |
| Save routine edits | `routine_exercises` (atomic `withTransaction`) — includes `setTypesJson` |
| Set type (edit mode) | `routine_exercises.setTypesJson` (serialized on SAVE CHANGES) |
| Weight per set (edit mode) | `routine_exercises.setWeightsJson` (serialized on SAVE CHANGES) |
| Reps per set (edit mode) | `routine_exercises.setRepsJson` (serialized on SAVE CHANGES) |
| Set type (live workout) | `workout_sets.setType` (immediate, via Iron Vault) |
| Routine sync confirm | `routine_exercises` (values and/or structure) |

---

## 4. Screen Layout

### 4.1 Top Bar

```
[↓ minimize | ✕ cancel-edit]  [Workout Name]  [elapsed mm:ss]  [⏱ timer icon]
─────────────── LinearProgressIndicator (TimerGreen, 2–4dp) ───────────────
```

- **Minimize (↓):** Routes user back to Workouts tab. Does NOT discard the workout. `WorkoutViewModel` stays alive (shared at NavHost scope).
- **Cancel Edit (✕):** Only in edit mode. Calls `cancelEditMode()` → resets state.
- **Elapsed Timer:** Hidden in edit mode.
- **Timer Icon:** Opens `StandaloneTimerSheet` — a `ModalBottomSheet` containing the full Countdown UI. The user never leaves the active workout context.

#### StandaloneTimerSheet

The sheet mirrors the Countdown UI from the Clocks tab (TOOLS_SPEC.md §7):

```
┌──────────────────────────────┐
│            (  )              │  ← Handle at top (0:00)
│         /        \           │
│        |  01:00   |          │  ← Time display in center (mm:ss)
│         \        /           │
│            ____              │
│                              │
│  [0:30] [1:00] [1:30] [2:00] │  ← SuggestionChip presets
└──────────────────────────────┘
```

- **Interactive Circular Dial:** Identical spec to TOOLS_SPEC.md §7. One full 360° rotation equals 6 minutes (360 seconds). Drag handle around the circle to set duration. Value snaps to 5-second intervals.
- **Dial Center:** Displays the configured time in `mm:ss` format using monospace font.
- **Presets:** Same 4 `SuggestionChip`s (`0:30`, `1:00`, `1:30`, `2:00`). Snap & Wait — tapping a preset instantly snaps the dial to the corresponding value but does **NOT** auto-start the timer.
- **Controls:**
  - IDLE: `[START]` (primary) + disabled Cancel.
  - Running: `[PAUSE]` (secondary) + `[CANCEL]` (error outlined).
  - Paused: `[RESUME]` (primary) + `[CANCEL]`.
- **Sheet dismissal while running:** The sheet can be dismissed (swiped down) without stopping the timer. The progress line on the TopAppBar continues to show countdown status.

#### Strict Independence

`standaloneTimerState` is **completely independent** of per-set rest timers (`restTimerState`). They run in parallel:

| Action | Effect on Standalone Timer | Effect on Rest Timer |
|--------|---------------------------|---------------------|
| Start standalone timer | Starts standalone countdown | No effect |
| Complete a set | No effect | Starts rest countdown |
| Cancel standalone timer | Stops standalone countdown | No effect |
| Rest timer finishes | No effect | Clears rest state |

Neither timer cancels, pauses, or otherwise affects the other. They are separate coroutine jobs with separate state fields.

#### Global Progress Line

A `LinearProgressIndicator` is rendered directly beneath the TopAppBar to show active timer progress.

- **Shared Behavior:** This line is shared between the **Standalone timer** and per-set **Rest Timers**.
- **Priority Rule:** If both timers are running simultaneously, the **Rest Timer** takes priority and its progress is displayed on the bar. Once the priority timer finishes or is cancelled, the bar reverts to showing the secondary timer's progress (if active).
- **Height:** 3dp.
- **Color:** `TimerGreen` (`#34D399`).
- **Track color:** `surfaceVariant` at 0.3α.
- **Animation:** Starts at `progress = 1.0f` (full bar) and linearly decreases to `0.0f` over the total countdown duration. Formula: `progress = remainingSeconds.toFloat() / totalSeconds.toFloat()`. Animated via `animateFloatAsState` with `LinearEasing` tween per tick.
- **Visibility:** The progress line is invisible when no timer (Standalone or Rest) is running. It appears instantly when a priority timer starts and disappears when all active timers are finished or cancelled.

#### Completion Feedback

When the standalone timer reaches `00:00`:

1. **Haptic:** Two short vibrations (50ms on, 100ms gap, 50ms on) via `VibrationEffect.createWaveform`.
2. **Audio:** A distinct finish chime routed through `RestTimerNotifier` using `AlertType.FINISH`.
3. **UI:** The `TimerGreen` progress line disappears. The `StandaloneTimerSheet` (if open) resets wheels to the last-used duration and shows IDLE controls.

### 4.2 LazyColumn Content

```
[Warmup Button / Warmup Card]        ← hidden once warmup complete
[Warmup Completed Banner]            ← shown after warmup logged
[Superset CAB]                       ← shown during superset select mode
[ExerciseCard × N]
[+ ADD EXERCISE button]              ← solid/filled, surfaceVariant container, primary content
[FINISH WORKOUT / SAVE CHANGES]      ← OutlinedButton (see below), with navigationBarsPadding()
```

**FINISH WORKOUT button:** Must be rendered as an `OutlinedButton` (transparent background, `TimerGreen` text, `TimerGreen` `BorderStroke(1.dp)`). This visually separates the high-stakes termination action from the solid **[+ ADD EXERCISE]** button immediately above it. The two buttons must not look like siblings of equal weight — ADD EXERCISE is "primary in this group", FINISH WORKOUT is "consequential but distinct".

**SAVE CHANGES button (edit mode):** Same `OutlinedButton` treatment, using `primary` colour for text and border.

### 4.3 Exercise Card

```
┌────────────────────────────────────────────────┐
│ [SS spine]  Exercise Name          [⋮ hub] [^] │
│             muscle group                        │
│─────────────────────────────────────────────────│
│  SET | PREV       | WEIGHT | REPS | RPE | ✓    │  ← header row
│  [SetWithRestRow × N]                           │
│─────────────────────────────────────────────────│
│  [Add Rest]                        [Add Set]    │
└────────────────────────────────────────────────┘
```

- **SS spine:** 4dp colored vertical bar on left edge, only when `supersetGroupId != null`. Color is determined by `buildSupersetColorMap(groupIds)` — an insertion-order map of distinct group IDs to an 8-color palette (Pink, Green, Yellow, Orange, Cyan, Purple, Deep Orange, Light Blue). Colors are assigned by first-appearance order, guaranteeing that different superset groups always get distinct colors (within palette size of 8).
- **[^] chevron:** Collapses/expands the set list (`isCollapsed` via `rememberSaveable`).
- **[⋮ hub]:** Opens `ManagementHubSheet` (8 actions).
- **Collapsed timer badge:** When `isCollapsed = true` AND this exercise's rest timer is active (`activeTimerExerciseId == exerciseId`), inject a small live `mm:ss` countdown chip/badge into the card header row — placed after the sets-count label. This ensures the user sees the running countdown even when the card is collapsed. The badge uses primary color text on a `primaryContainer` background, monospace font. It disappears when the timer finishes or the card is expanded.
- **Exercise reorder (drag handle):** A `DragHandle` icon is always visible in the `ExerciseCard` header. Uses `Modifier.draggableHandle(onDragStarted = { collapseAll() })` from `reorderable-compose`; calls `reorderExercise(fromIdx, toIdx)` on drop. Available in **both live workout and edit mode**. When drag starts, `collapseAll()` fires — all cards collapse to compact rows automatically. Superset selection mode also uses `ReorderableItem` + `draggableHandle` so exercises can be reordered while selecting superset members.

### 4.4 Set Row (Strength — `WorkoutSetRow`)

Column weights (shared between header and row for pixel-perfect alignment):

| Column | Weight | Content |
|---|---|---|
| SET | 0.08 | Set type badge (tap → inline `DropdownMenu`, see §10.2) |
| PREV | 0.17 | Ghost label (see §4.4.1) |
| WEIGHT | 0.22 | `WorkoutInputField` — `KeyboardType.Decimal` |
| REPS | 0.22 | `WorkoutInputField` — `KeyboardType.Number` |
| RPE | 0.13 | Tappable cell → `RpePickerSheet` (see §14.3); shows `—` when null, formatted value (e.g. `8.5`) when set |
| CHECK | 0.18 | Completion toggle (TimerGreen when done) |

**RPE visual treatment:** RPE is a standalone tappable cell — not a `BadgedBox` overlapping the Reps field. The RPE column occupies its own column slot (weight 0.13). When null: displays a muted `—` placeholder. When set: displays the value (e.g. `8.5`). Tapping the cell opens `RpePickerSheet` (§14.3).

**Golden RPE indicator:** When a set is completed (`isCompleted == true`) and has an RPE value, a small badge appears immediately after the RPE text inside the RPE column cell:

| Category | Condition (stored × 10) | Badge |
|---|---|---|
| LOW | `rpe < 70` | 6dp grey circle |
| MODERATE | `70 ≤ rpe < 80` | 6dp `ReadinessAmber` circle |
| GOLDEN | `80 ≤ rpe ≤ 90` | gold `✦` glyph (10sp, `GoldenRPE = Color(0xFFFFD700)`) |
| MAX_EFFORT | `rpe > 90` | 6dp `ProError` circle |

Logic lives in `util/RpeHelper.kt` (`RpeCategory` enum + `rpeCategory(Int): RpeCategory`). Badge is not shown during RPE input (only on completed sets) and not shown in edit mode.

**Column header row:** A fixed-height (20dp) `Row` is inserted **above the first `WorkoutSetRow`** inside each `ExerciseCard`, after the sticky-note and session-note rows. Header labels are centered within each column and use the same column weights as the data rows:

```
SET | PREV | WEIGHT | REPS | RPE | ✓
```

Style: `labelSmall`, `onSurfaceVariant` color, no background. The header does NOT scroll — it renders inside `ExerciseCard`, not as a `LazyColumn` `stickyHeader()`. One header per exercise card.

Row background: `TimerGreen.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)` when completed (`set.isCompleted`), `surface` otherwise. Applied at the `SetWithRestRow` wrapper level so it covers all exercise types (strength, cardio, timed). Using `compositeOver` produces a fully opaque color so the swipe-to-delete red background never bleeds through.

**Touch target minimums:** The SET badge `Box` and the CHECK `IconButton` must both use `Modifier.minimumInteractiveComponentSize()` (48dp minimum). The RPE column `Box` must be padded so its clickable area fills the full 44dp row height and the entire column width — never smaller.

**Keyboard types:** `WorkoutInputField` for weight must always pass `keyboardType = KeyboardType.Decimal`. `WorkoutInputField` for reps must always pass `keyboardType = KeyboardType.Number`. These are not defaults — they must be set explicitly at the call site in `WorkoutSetRow`.

**Select-all on tap:** `WorkoutInputField` selects all existing text on every tap — including taps that occur while the field is already focused. Implemented by collecting `PressInteraction.Release` from the `MutableInteractionSource` and incrementing a counter that keys a `LaunchedEffect`. The effect waits 50ms (to let the IME place its cursor first) then replaces the selection with `TextRange(0, text.length)`. This fires on the first focus tap and on every subsequent tap into the same field.

**Row spacing:** An 8dp `Spacer` is inserted between consecutive set rows (between `SetWithRestRow` items) within the `forEachIndexed` loop, excluding after the last row. This does not affect the rest separator — the `RestSeparator` within `SetWithRestRow` is not affected.

**"Touched" set indicator:** If a set has partial data entered (`weight.isNotBlank() || reps.isNotBlank()`) **and** `isCompleted == false`, apply a subtle 2dp left-edge border in `primary.copy(alpha = 0.4f)` to the row. This visually distinguishes a partially-filled set from a completely empty, untouched row. No indicator when the set is fully blank, and no indicator when it is completed (completed rows use the green `TimerGreen.copy(alpha = 0.08f)` background instead).

#### 4.4.1 PREV Column — Ghost Label Format

| Condition | Display |
|---|---|
| Weight + reps + RPE logged | `100kg × 5 @ 8.5` |
| Weight + reps, no RPE | `100kg × 5` |
| No prior session | `—` (em dash) |

**Implementation rules for `formatGhostLabel()`:**
- Use the true multiplication sign `×` (not the letter `x`).
- `maxLines = 1`, `overflow = TextOverflow.Ellipsis` — never wrap or clip without ellipsis.
- If space is tight, prefer the compact form `100kg×5@8.5` (no spaces around `×` and `@`).

**PREV column header — onboarding tooltip:** The "PREV" text in `StrengthHeader` must be tappable. Tapping it shows a brief informational tooltip or `AlertDialog` stating: *"Your last logged weight and reps for this exercise, across all sessions."* This prevents users from ignoring or misunderstanding the ghost data on first use.

### 4.5 SetWithRestRow & Swipe-to-Delete

Each set is wrapped in `SetWithRestRow` which contains **two independent** `SwipeToDismissBox` instances:

```
Column {
    SwipeToDismissBox(setSwipeState)  { WorkoutSetRow }
    SwipeToDismissBox(restSwipeState) { RestSeparator }   ← only if shouldShowSeparator
    OR
    Spacer(8dp)                                           ← no separator
}
```

- Swipe the **set row** → deletes the set AND automatically removes the rest separator immediately below it (the `deleteSet()` callback adds the key to `hiddenRestSeparators`). The two `SwipeToDismissBox` instances remain independent at the Compose level — the coupling is in the `deleteSet()` callback, not by merging the boxes.
- Swipe the **rest separator** → deletes only the separator (`deleteRestSeparator()`), set remains.
- **Rest timer naturally expires** → the separator auto-hides: both `onTimerFinish()` (service path) and the in-process fallback coroutine add `"${exerciseId}_${setOrder}"` to `hiddenRestSeparators` after resetting `restTimer` to `RestTimerState()`. The user does not need to manually swipe or tap to dismiss it.
- These must remain **independent** at the Compose level. Do not re-group them into a single `SwipeToDismissBox`.

**Partial swipe snap-back:** A `LaunchedEffect(swipeState.currentValue)` must call `swipeState.snapTo(Default)` if the row is still in composition after `confirmValueChange` returns `true`. This prevents the red delete background from persisting as a ghost after deletion.

**Clipboard Recovery (replaces Snackbar undo):** When a set is deleted via swipe, its `weight`, `reps`, and `setType` values are saved to a single-item clipboard scoped to that `exerciseId` in `ActiveWorkoutState`:

```
deletedSetClipboard: Map<Long, DeletedSetClipboard>   // keyed by exerciseId
data class DeletedSetClipboard(weight: String, reps: String, setType: SetType)
```

- Only one clipboard entry exists per exercise at a time. A second swipe on a different set overwrites the previous clipboard entry for that exercise.
- When the user taps **[Add Set]** on that same exercise: if a clipboard entry exists for that `exerciseId`, paste its `weight`, `reps`, and `setType` into the new set (instead of inheriting from the last surviving set). Then **clear** the clipboard entry.
- If the clipboard is empty, fall back to standard last-set inheritance.
- There is **no Snackbar** for set deletion. The clipboard is the recovery mechanism. Do not add a "Set deleted — Undo" toast for this action.

### 4.6 Rest Separator

Crossfades between two states:

| State | Visual | Interaction |
|---|---|---|
| **Active** (this set's timer is running) | Primary-tinted block with live `mm:ss` | Tap → `TimerControlsSheet` |
| **Passive** | TimerGreen divider lines + duration label | Tap → `RestTimePickerDialog` |

**Entrance animation:** When a rest separator transitions from hidden to visible (i.e., the set above it is just completed and the timer starts), it must animate in using `AnimatedVisibility` with a 200ms `expandVertically`. Simultaneously, the separator's background flashes briefly from `primary.copy(alpha = 0.4f)` back to its resting `primary.copy(alpha = 0.15f)` over 300ms. This draws the user's eye downward to confirm the timer has started without requiring them to scroll.

**Progress bar:** The `LinearProgressIndicator` fraction uses `RestTimerState.totalSeconds` (not the exercise's configured rest) as the denominator, so the bar remains accurate when the user adds or subtracts time via `TimerControlsSheet`. The first frame snaps to the initial fraction (no forward animation); subsequent second-ticks animate backwards over 1000ms with `LinearEasing` via `Animatable`.

### 4.6.1 TimerControlsSheet

Opened by tapping the **active** (primary-tinted) rest separator only. It is a `ModalBottomSheet` with the following layout:

- **Large time display:** Remaining time in `mm:ss` format, monospace font, centered. Updates every second.
- **Controls row (left to right):** `−10s` button · `⏸ Pause / ▶ Resume` toggle · `+10s` button.
- **Skip button:** Skips the rest immediately (dismisses sheet + fires `onTimerFinish()`).

**Pause / Resume behaviour:**
- Tapping `⏸ Pause` calls `pauseRestTimer()` — freezes the countdown, sets `RestTimerState.isPaused = true`.
- Tapping `▶ Resume` calls `resumeRestTimer()` — restarts the countdown from the paused remaining time.

**−10s / +10s behaviour:** Adjusts the remaining time by ±10 seconds. Clamped to minimum 1 second (cannot go to 0 or negative) and maximum 599 seconds.

### 4.7 Cardio Set Row (`CardioSetRow`)

Used when `ExerciseType == CARDIO`.

Columns: SET | DISTANCE (km) | TIME | PACE | CHECK

Data updated via `updateCardioSet(exerciseId, setOrder, distance, timeSeconds, rpe, completed)`.

**Time input — MM:SS formatter:** The TIME field in `CardioSetRow` must use a live `MM:SS` formatter. The user types raw digits; the field formats them into `MM:SS` as they type (e.g. typing `145` displays `1:45`). The underlying stored value remains an integer seconds count. Keyboard type: `KeyboardType.Number`.

### 4.8 Timed Set Row (`TimedSetRow`)

Used when `ExerciseType == TIMED`.

**Weight input** routes through `onWeightChanged(exerciseId, setOrder, raw)` — same cascade logic as STRENGTH sets.
**Time input** routes through `onTimeChanged(exerciseId, setOrder, raw)` — cascades to subsequent sets (see §13.1).
**RPE + completion** updates route through `updateTimedSet(exerciseId, setOrder, weight, timeSeconds, rpe, completed)`.

**Countdown timer state machine:** `TimedSetRow` contains a local-state countdown timer. The timer state is ephemeral (not persisted across navigation). State enum: `TimedSetState { IDLE, SETUP, RUNNING, PAUSED, COMPLETED }`.

```
IDLE → (setupSeconds > 0) → SETUP → RUNNING → PAUSED → RUNNING
IDLE → (setupSeconds == 0) → RUNNING → PAUSED → RUNNING
                                          ↓           ↓
                                       COMPLETED  MARK DONE (→ COMPLETED)
                                       PAUSED → RESET → IDLE
SETUP → (cancel) → IDLE
```

| State | Columns / Controls |
|---|---|
| IDLE | SET \| WEIGHT input \| TIME input (editable) \| RPE input \| ▶ Start button \| CHECK |
| SETUP | SET \| "Get Ready" label (SetupAmber) \| remaining seconds (large, SetupAmber) \| ✕ Cancel button \| amber `LinearProgressIndicator` below |
| RUNNING | SET \| MM:SS countdown (TimerGreen, `titleMedium`) \| `LinearProgressIndicator` below row \| ■ Stop button |
| PAUSED | SET \| remaining MM:SS (muted) \| ▶ Resume \| ✓ Mark Done \| ↺ Reset |
| COMPLETED | SET \| WEIGHT input \| TIME input \| RPE input \| (empty spacer) \| CHECK (TimerGreen filled) |

**Get Ready countdown (SETUP state):** Controlled by the `timedSetSetupSeconds` global preference (0–10s, default 3s, configurable in Settings → Rest Timer). When Play is tapped and `setupSeconds > 0`, the row enters SETUP state showing an amber countdown. Each second fires `onSetupCountdownTick()` (same beep/haptic as warning tick). At 0, automatically transitions to RUNNING. Tapping the ✕ cancel button returns to IDLE with original time restored.

**Countdown implementation:** Single `LaunchedEffect(timerState)` using a `when` block. SETUP branch: loops decrementing `setupRemaining` with `delay(1000L)`, calls `onSetupCountdownTick()` each iteration, then sets `timerState = RUNNING` (the effect restarts for RUNNING). RUNNING branch: loops decrementing `remainingSeconds`, calls `onTimerWarningTick()` at 3/2/1s remaining, transitions to COMPLETED at 0.

**External completion:** A `LaunchedEffect(set.isCompleted)` watches for the user tapping the checkbox directly while the timer is in SETUP or RUNNING state and transitions to `COMPLETED`, cancelling the countdown coroutine.

**Audio/haptic:** Three ViewModel functions gate these behind user settings:
- `WorkoutViewModel.timerFinishedFeedback()` — calls `restTimerNotifier.notifyEnd()` (600ms beep + double-pulse haptic)
- `WorkoutViewModel.timerWarningTickFeedback()` — calls `restTimerNotifier.playWarningBeep()` + `hapticShortPulse()`
- `WorkoutViewModel.setupCountdownTickFeedback()` — same as warning tick; fires once per setup countdown second

**PR score:** For TIMED sets, the PR comparator is `Weight × TimeSeconds` (see §10.1). The WEIGHT column is displayed and editable (supports bodyweight exercises where weight = 0, in which case `TimeSeconds` alone is the comparator).

### 4.9 Organize Mode (Active Workout)

Organize Mode is the single unified mode for reordering and grouping exercises in the active workout. It replaces the previous separate "Reorder Mode" spec (which was never implemented). There is no `isReorderMode` flag on `ActiveWorkoutState`; `isSupersetSelectMode` is the only mode flag.

**Entry:** Management Hub kebab → **Organize Exercises** → calls `enterSupersetSelectMode(fromExerciseId)` → sets `isSupersetSelectMode = true`. If the triggering exercise is already in a superset, all group members are pre-selected.

**While active (`isSupersetSelectMode = true`):**
- All `ExerciseCard` items are replaced by compact `SupersetSelectRow` rows (checkbox + exercise name + muscle chip + drag handle). Exercises already in a superset show a colored `Link` icon.
- A Contextual Action Bar at the top of the list shows:
  - Left: **Done** `TextButton` — calls `exitSupersetSelectMode()`; exits organize mode.
  - Center: title `"Organize exercises"` (empty selection) or `"Organize • N selected"` (N ≥ 1).
  - Right: **contextual icon button** — behavior depends on selection:
    - **Group** (Sync icon) — shown when selected exercises are NOT all in the same superset; enabled when ≥ 2 selected; calls `commitSupersetSelection()`.
    - **Ungroup** (LinkOff icon) — shown when all selected exercises share the same `supersetGroupId`; always enabled when visible; calls `ungroupSelectedExercises()`.
- Tapping a `SupersetSelectRow` toggles the exercise in `supersetCandidateIds`.
- Drag handles (`Modifier.draggableHandle`) on each row enable reorder at any time within the mode via `reorderExercise(fromIdx, toIdx)`.

**Commit (Group) behaviour:**
- `commitSupersetSelection()` assigns a shared `supersetGroupId` UUID to all selected exercises.
- After commit: `supersetCandidateIds` is cleared, but `isSupersetSelectMode` remains `true`.
- The user can immediately select another set of exercises and commit another superset, or drag to reorder — all within the same Organize Mode session.
- If < 2 candidates are selected, the commit is a no-op; the mode stays active.

**Ungroup behaviour:**
- `ungroupSelectedExercises()` sets `supersetGroupId = null` on all selected exercises.
- If removing the selected exercises from a group would leave fewer than 2 members, the entire group is dissolved (all remaining members also cleared).
- After ungroup: `supersetCandidateIds` is cleared, `isSupersetSelectMode` remains `true`.

**Exit:** Only via the **Done** button (or `cancelWorkout()`/`finishWorkout()` lifecycle teardown). There is no auto-exit on any action.

**Drag handles outside Organize Mode:** Drag handles on regular `ExerciseCard` rows remain always-visible for quick one-off reorders without entering the mode. `onDragStarted` collapses all cards via `collapseAll()`.

**Availability:** Active workout only (`isActive = true`). Not available in edit mode (`isEditMode = true`).

### 4.10 Collapsible Exercise Cards

Each `ExerciseCard` header row includes a **chevron toggle** (▲/▼) to collapse or expand the card body.

**State:** `var isCollapsed by rememberSaveable { mutableStateOf(false) }` — per-card local state. Cards start **expanded** by default.

**Collapsed state:**
- Only the header row is visible: drag-handle icon | exercise name | muscle chip | sets-summary chip (e.g., `3 / 4 sets done`) | active timer badge (if applicable) | ▲ expand icon.
- All set rows, rest separators, add-set/add-rest buttons, column headers, and notes are hidden inside `AnimatedVisibility(visible = !isCollapsed, enter = expandVertically(), exit = shrinkVertically())`.

**Active timer badge (collapsed):** When `isCollapsed = true` AND this exercise's rest timer is active (`activeTimerExerciseId == exerciseId`), a small `mm:ss` countdown badge is shown in the collapsed header — placed after the sets-summary chip. This is the only visible indicator of a running timer when the card is collapsed. Style: `primary` text on `primaryContainer` background, monospace font. Disappears when the timer finishes or the card is expanded.

**Invariant:** Collapsing a card must **never** stop or reset the rest timer. Timer state is entirely independent of card collapse state.

---

## 5. Rest Timer System

Rest timers are per-set. The system has three layers:

1. **Exercise defaults (3 fields, v32):**
   - `Exercise.restDurationSeconds` — work/normal set rest
   - `Exercise.warmupRestSeconds` — warmup set rest (default: 30s)
   - `Exercise.dropSetRestSeconds` — drop set rest (default: 0s = no rest)
   Set via Management Hub → "Set Rest Timers" → `UpdateRestTimersDialog`. Persisted to `exercises` table via `ExerciseDao.updateRestTimers(exerciseId, workSeconds, warmupSeconds, dropSeconds)`.
2. **Per-set override** (`restTimeOverrides: Map<"${exerciseId}_${setOrder}", Int>`) — set by tapping the passive separator.
3. **Effective rest** = override if present, else exercise default for the set type.

Timer lifecycle:
- `completeSet()` → resolves duration: checks `restTimeOverrides["${exerciseId}_${setOrder}"]` first; falls back to `computeRestDuration(completedSet.setType, nextSet?.setType, exercise)` → calls `startRestTimer(exerciseId, setOrder, override)` → stamps `RestTimerState.exerciseId/setOrder`.
- `WorkoutTimerService` (ForegroundService) runs the countdown; survives backgrounding.
- **ForegroundService lifecycle:** Starts when the first set is completed (or a manual standalone timer is started). Stops — calling `stopForeground(true)` — exclusively when `finishWorkout()` or `cancelWorkout()` resolves. No other code path stops the service. Communication from the service back to `WorkoutViewModel` uses a bound-service pattern: `WorkoutViewModel` binds to the service on workout start and receives timer tick broadcasts via a shared `StateFlow` bridge. The service never holds a direct ViewModel reference.
- `RestTimerState.isPaused` — paused via `TimerControlsSheet` (-10s / ⏸ / +10s / Skip).
- Second tap on completed set → un-completes + `stopRestTimer()`.
- Deleting a set that owns the active timer → `stopRestTimer()` automatically.

### 5.1 Rest Logic & Types

There are 3 types of rest periods:
- **Warmup rest (WUR):** Used between warmup sets.
- **Work rest (WR):** Used between work sets (Normal, Failure).
- **Drop rest (DR):** Used between drop sets.

**Rest Sequence Logic:**
- Between two Warmup sets (W > W) → **WUR**
- Between the last Warmup set and the first Work set (W > R) → **WR** (Warmup-to-working rest is always 2:00 / WR default)
- Between two Work sets (R > R) → **WR**
- Between the last Work set and the first Drop set (R > D) → **DR**
- Between two Drop sets (D > D) → **DR**

**Example:**
A sequence of `W > W > R > R > R > D > D` results in:
`W > WUR > W > WR > R > WR > R > WR > R > DR > D > DR > D`

### 5.2 RestTimePickerDialog

Triggered by tapping the **passive** (TimerGreen divider) rest separator. It is an `AlertDialog` with the following layout:

- **Input:** Two numeric input boxes: **[MM]** : **[SS]**. Users type the minutes and seconds explicitly.
- **Visuals:** Simple numeric fields. The 'Interactive Circular Dial' is **DEFERRED / CLOCKS-ONLY** and must not be used for exercise rest configuration.
- **Presets:** No fixed presets are provided in this dialog.
- Validation: total duration must be between 0 and 599 seconds inclusive. Invalid input prevents confirmation.
- Buttons: **CONFIRM** · **CANCEL**.
- On CONFIRM: calls `updateLocalRestTime(exerciseId, setOrder, totalSeconds)` → stored in `restTimeOverrides: Map<"${exerciseId}_${setOrder}", Int>`. This per-set override takes precedence over the exercise-level default for that specific set.
- On CANCEL or dismiss: no change.

**Delete Timer:** Accessible via the "Delete Timer" `TextButton` at the bottom of the Set Type `DropdownMenu` (§10.2) — calls `deleteLocalRestTime(exerciseId, setOrder)`, removing the per-set override and falling back to the exercise default.

---

## 6. Superset System

### 6.1 Pairing

- Enter superset mode via Management Hub → "Create Superset" → `enterSupersetSelectMode(fromExerciseId)`.
- **Interaction:** All exercise cards collapse to compact `SupersetSelectRow` rows (checkbox + name + muscle chip + Link icon if already in a superset). A CAB at the top shows Cancel + title + Sync button (enabled ≥ 2 selected).
- If the triggering exercise is already in a superset, its group members are pre-selected (enabling modification or break).
- User selects 2+ exercises and taps Sync.
- `commitSupersetSelection()` assigns a shared `supersetGroupId` (UUID). Exercises deselected from a prior group lose their groupId.
- **Sync Defaults:**
    a. Work/drop rests are set to **0** for all exercises in the superset except for the **last one** (which retains its default or override rest).
    b. Exercises are ordered sequentially as they appear in the superset.
- `supersetGroupId` stored on both `ActiveExercise` (in-memory) and `workout_sets` (DB).
- Visual: 4dp colored spine on left edge of card. Color is from `buildSupersetColorMap(groupIds)` — insertion-order assignment guarantees distinct palette colors (not hardcoded secondary).
- Remove: Management Hub → "Remove from Superset" → `removeFromSuperset(exerciseId)`. If only one partner remains after removal, that partner's groupId is also cleared.

### 6.2 Turn-Based Alternation

- `activeSupersetExerciseId` in `ActiveWorkoutState` tracks whose "turn" it is within a superset pair.
- `advanceSupersetTurn(exerciseId)` flips `activeSupersetExerciseId` to the partner exercise.
- The active-turn card receives a border glow (primary color) to guide the user through alternating sets.
- Completing a set on the active exercise automatically advances the turn to the partner.

---

## 7. Routine Sync — The Diff Engine (Post-Workout)

On `finishWorkout()`, the ViewModel diffs the completed session against the `routineSnapshot` captured at workout start and prompts the user to optionally update the routine template.

### 7.1 Change Detection

| Scenario | `RoutineSyncType` | What changed |
|---|---|---|
| No difference | `null` | **Skip post-workout sync bar entirely.** Go straight to summary. |
| Weight or reps differ from `routine_exercises.defaultWeight`/`reps` | `VALUES` | User lifted more/less than the template default; detected by comparing the session's completed-set average weight/reps vs stored defaults |
| Exercise set, order, or rest changed | `STRUCTURE` | User added/removed a set, swapped exercise, changed rest duration |
| Both | `BOTH` | Structure and values both differ |

Detection runs in `finishWorkout()` by comparing `ActiveExercise` state against `RoutineExerciseSnapshot`.

### 7.2 Dialog Decisions

| `RoutineSyncType` | Options presented |
|---|---|
| `VALUES` | **[Update Values]** · [Keep Original] |
| `STRUCTURE` | **[Update Routine]** · [Keep Original] |
| `BOTH` | **[Update Values and Routine]** · **[Update Values]** · [Keep Original] |

**`BOTH` — three distinct outcomes:**

| Option | What it does |
|---|---|
| **[Update Values and Routine]** | Overwrites the entire template structure and its default values with the exact session blueprint. This is the "smart" default — today's workout becomes the new template. |
| **[Update Values]** | Updates only the default weights/reps for existing exercises. Structural additions (new exercises, extra sets) are discarded. Template structure is preserved. |
| **[Keep Original]** | Saves to history only. Template remains completely untouched. |

> **Current implementation note:** `confirmUpdateBoth()` currently performs structure + values in one call. The triple-action dialog (with the separate "Update Values" option) is the intended UX target and should be implemented when the dialog is next touched.

### 7.3 UX Flow (Inline Sync CTA)

The sync process is fully integrated into the `PostWorkoutSummarySheet`. There are no blocking pre-summary dialogs or secondary sync buttons. The Diff Engine result is presented as a set of **Inline Sync CTAs** at the bottom of the summary sheet, appearing only if changes were detected.

```
PostWorkoutSummarySheet (always shown immediately after tapping FINISH WORKOUT)
    │
    ├─ No changes detected ─── [Done] (closes summary)
    │
    └─ Changes detected ────── Shows Inline Sync CTAs based on diff:
           │
           ├─ VALUES only     ─── [Update Values] · [Keep Original]
           ├─ STRUCTURE only  ─── [Update Routine] · [Keep Original]
           └─ BOTH            ─── [Update Both] · [Update Values] · [Keep Original]
```

**Key principle:** The user resolves the sync directly within the summary view. Tapping a sync button (e.g., [Update Both]) performs the write and then updates the UI to show a "Routine Updated" confirmation state (or simply hides the buttons). Tapping [Done] without tapping a sync button is equivalent to "Keep Original" — the routine template is left untouched.

> **`WorkoutSummary` gains a `pendingRoutineSync: RoutineSyncType?` field.** `finishWorkout()` passes the detected sync type into the summary object. The summary sheet renders the corresponding buttons in its footer area, above the final [Done] button.

### 7.3b Value-Change Inline Prompt

When the Diff Engine detects `RoutineSyncType.VALUES` (weight/reps differ from stored defaults), the **[Update Values]** CTA is shown at the bottom of the `PostWorkoutSummarySheet`.

- Tapping **[Update Values]** immediately writes the new defaults to the routine template.
- This prompt is **non-blocking** — the user can tap **[Done]** on the summary sheet without ever engaging with the sync buttons.
- This is the standard path for all sync types: inline, optional, and integrated into the summary footer.

### 7.4 ViewModel Methods

| Method | Effect |
|---|---|
| `confirmUpdateRoutineValues()` | Writes weight/reps defaults, updates summary UI state |
| `confirmUpdateRoutineStructure()` | Rewrites exercise/set structure, updates summary UI state |
| `confirmUpdateBoth()` | Does both above in sequence, updates summary UI state |

**Snackbar feedback:** After each `confirm*()` call completes, a snackbar message is emitted: "Routine updated".

### 7.5 Implementation Invariant

The `PostWorkoutSummarySheet` is the sole owner of the sync UI. No `AlertDialog`s or intermediate screens should be used to resolve the routine sync. The logic for determining which buttons to show lives in the ViewModel and is passed to the sheet via the `WorkoutSummary` state.

---

## 8. Post-Workout Summary Sheet

Shown after routine sync is resolved. Displays:
- Workout name, duration, total volume, set count, exercise list.
- **Set count shown excludes WARMUP sets.** Total = count of NORMAL + FAILURE + DROP completed sets only. WARMUP sets are excluded from the displayed count. Implementation: `finishWorkout()` filters `it.isCompleted && it.setType != SetType.WARMUP`.
- **Volume excludes WARMUP sets.** WARMUP set volume is not counted in `totalVolume`. Same filter applied.
- **Exercise list excludes exercises with 0 completed work sets.** An exercise only appears in `exerciseNames` if it has at least 1 completed non-warmup set.
- **Save as Routine** button → `AlertDialog` (name input) → `saveWorkoutAsRoutine(name)` — creates a new `Routine` + `RoutineExercise` rows from all completed sets.
- **Done** → `dismissWorkoutSummary()` + navigate away.

---

## 9. Management Hub (Exercise Card Overflow Menu)

Opened via the `⋮` (`MoreVert`) `IconButton` in the top-right corner of the `ExerciseCard` header. Renders as a `ManagementHubSheet` (ModalBottomSheet).

### 9.1 Actions

**Add Session Note**
- Opens an inline text field injected **between the column headers and the first `WorkoutSetRow`**.
- When a non-blank session note is saved, it is displayed as **italic muted text** (`bodySmall`, `onSurface.copy(alpha=0.6f)`) between the column headers and the first set row.
- Session notes appear in **history only**, NOT in the routine template.
- ViewModel: `updateExerciseSessionNote(exerciseId, note)`

**Add Sticky Note**
- Opens an inline text field injected **below the exercise name/muscle group header and above the column headers**.
- Persistent — saved to `routine_exercises.stickyNote`. Appears in the **routine template** and displayed as a gold banner (FormCuesGold `#5A4D1A`) with a pin icon on every future session.
- ViewModel: `updateExerciseStickyNote(exerciseId, note)`

**Set Rest Timers** *(previously "Add / Replace Rest Timer")*
- Opens `UpdateRestTimersDialog` — an `AlertDialog` with three labeled rows:
  - **Work set** — rest after NORMAL/FAILURE sets
  - **Warm up** — rest after WARMUP sets (default: 0:30)
  - **Drop set** — rest after DROP sets (default: 0:00)
- Each row has a `M:SS` text input (BasicTextField styled as a pill).
- Title: "Update rest timers". Subtitle: "Completed timers will not be affected.\nDurations will be saved for next time."
- Confirm button: **UPDATE REST TIMERS** → `updateExerciseRestTimers(exerciseId, workSeconds, warmupSeconds, dropSeconds)`.
- Persists all three values to `exercises` table (v32 DB fields: `restDurationSeconds`, `warmupRestSeconds`, `dropSetRestSeconds`).
- **Restores hidden separators:** Confirming the dialog also clears all `hiddenRestSeparators` entries for this exercise, so any rest separators previously swiped away reappear with the new duration.
- Per-set overrides (via tapping the passive separator) take precedence over these defaults.

**Replace Exercise**
- Navigates to `ExercisesScreen` in selection mode (or opens `MagicAddDialog`).
- On selection: swaps `exerciseId` in `ActiveWorkoutState` but **retains the existing set/rep structure** of the replaced exercise — minimises data loss.
- ViewModel: `replaceExercise(oldExerciseId, newExercise)`

**Create Superset**
- Management Hub item is labelled **"Organize Exercises"** for exercises not currently in a superset, or **"Remove from Superset"** for exercises already in one.
- Tapping **"Organize Exercises"** calls `enterSupersetSelectMode(fromExerciseId)` → enters **Organize Mode** (see §4.9). The triggering exercise is pre-selected; if it is already in a superset group, all members of that group are pre-selected.
- **Organize Mode UI** and full commit behaviour: see §4.9.
- The UI renders superset exercises' sets interleaved: Ex A Set 1, Ex B Set 1, Ex A Set 2, Ex B Set 2 (turn-based alternation via `activeSupersetExerciseId`).
- When already in a superset: option changes to **Remove from Superset** → `removeFromSuperset(exerciseId)`.

**Remove Exercise**
- Immediately drops the entire exercise block from `ActiveWorkoutState`.
- **Cascading:** cancels any active rest timer associated with this `exerciseId`, removes all child `ActiveSet` rows from memory, deletes `workout_sets` rows from DB.
- ViewModel: `removeExercise(exerciseId)`

> **Note:** The "Preferences" action that previously appeared in this hub was a placeholder stub with no implementation. It has been removed. Do not re-add it without a concrete spec for what "Preferences" means at the exercise level.

---

## 10. Set Type System

Set type is the primary metadata on every log entry. It controls how the set is treated in analytics, volume calculations, and PR tracking.

### 10.1 Types & Analytics Impact

| Type | Badge | Color | Counted in Volume | Eligible for PR |
|---|---|---|---|---|
| NORMAL | `1`, `2`, … | onSurface | ✅ Yes | ✅ Yes |
| WARMUP | `W` | tertiary | ❌ No | ❌ No |
| FAILURE | `F` | error | ✅ Yes | ✅ Yes |
| DROP | `D` | secondary | ✅ Yes | ❌ No |

**Volume calculation:**

```
Volume = Σ (Weight × Reps)   for all NORMAL, FAILURE, and DROP sets of type STRENGTH
```

The following are strictly excluded from all volume aggregates:
- **WARMUP sets** — A 20kg warm-up must never inflate the "Total Volume" card.
- **TIMED exercise sets** — Volume is not meaningful for isometric/duration-based exercises.
- **CARDIO exercise sets** — Distance-based exercises use a separate metric (distance, pace) and must not contribute to weight-based volume.

**PR eligibility by exercise type:**

| Exercise Type | Formula | Eligible Set Types |
|---|---|---|
| STRENGTH | Epley e1RM: `Weight × (1 + Reps / 30)` | NORMAL, FAILURE only |
| TIMED | Performance score: `Weight × TimeSeconds` | NORMAL, FAILURE only |
| CARDIO | Not tracked as a PR | — |

For **STRENGTH**: A PR fires when the logged set's e1RM strictly exceeds the historical maximum e1RM for that exercise.

For **TIMED**: A PR fires when `Weight × TimeSeconds` strictly exceeds the historical maximum for that exercise (heavier load held longer = better performance). If weight is 0 (bodyweight), use `TimeSeconds` alone as the comparator.

DROP sets are never PR candidates for any exercise type — the weight is intentionally reduced.

### 10.2 Selection UI

Tapping the SET badge opens an **anchored `DropdownMenu`** positioned directly over the badge — not a bottom sheet. The dropdown lists all 4 set types in order:

| Item | Badge | Label | Right element |
|---|---|---|---|
| Normal | `N` / set# | Normal | `(?)` icon button |
| Warm Up | `W` | Warm Up | `(?)` icon button |
| Drop Set | `D` | Drop Set | `(?)` icon button |
| Failure | `F` | Failure | `(?)` icon button |

- Each row shows the **visual badge** (coloured, same style as the set row badge) + the **label text** + a **(?) icon button** right-aligned.
- Tapping a type row: selects that type + dismisses the dropdown + calls `focusManager.clearFocus()` to prevent the keyboard from re-appearing.
- Tapping the **(?)** icon: opens a small `AlertDialog` explaining what that set type means and how it affects volume calculations and PR eligibility. Does not select the type.
- **"Delete Timer"** `TextButton` (error color) at the bottom of the dropdown — removes the per-set rest override for this row. Also calls `focusManager.clearFocus()` on click.
- Dismissing the dropdown via back/tap-outside also calls `focusManager.clearFocus()`.

> **Legacy `SetTypePickerSheet` is deleted.** The `ModalBottomSheet`-based picker has been replaced by the anchored `DropdownMenu` approach described above. Do not reintroduce the sheet.

### 10.3 Storage

`SetType` enum stored in `workout_sets.setType` column. Written immediately on change via `workoutSetDao.updateSetType()` (no debounce — discrete event).

### 10.4 `cycleSetType()` — Deleted

`cycleSetType()` (the tap-to-cycle badge from an earlier iteration) has been **hard-deleted** from `WorkoutViewModel`. The only supported path for changing a set's type is `selectSetType(exerciseId, setOrder, setType)` called from `SetTypePickerSheet`. Do not re-introduce `cycleSetType()` or any other shortcut that bypasses the picker sheet.

---

## 11. Navigation & Safety Rules

- **Minimize (↓):** Navigates back to Workouts tab. `WorkoutViewModel` is scoped to the NavHost — state is preserved. Never discards data.
- **System back (live workout):** Same as Minimize. Never discards data.
- **Cancel Workout:** Only reachable via a secondary action (`showCancelDialog`). Shows confirmation dialog → `cancelWorkout()` → deletes orphaned DB records → navigates away.
- **Edit mode cancel (✕ or back):** Shows **[Discard Edits] · [Keep Editing]** confirmation dialog (no silent exit). On [Discard Edits]: `cancelEditMode()` resets state.
- **Edit mode save:** `saveRoutineEdits()` → sets `editModeSaved=true` → `LaunchedEffect(editModeSaved)` fires `onWorkoutFinished()`. The `LaunchedEffect` is the **only** valid navigation trigger for a successful save — do not call `onWorkoutFinished()` directly from the ViewModel or a button onClick.
- **Screen disposal:** `cancelEditMode()` must be called via `DisposableEffect` on screen exit to prevent stale `isEditMode=true` state from bleeding into the next session.

### 11.1 Pre-Workout Edit Guard

If the user attempts to enter edit mode (`startEditMode(routineId)`) while a **live workout is already active** (`isActive=true`), the app must show a guard dialog:

> **"You have an active workout in progress."**
> [Cancel Edit] · [Finish Workout First]

- **[Cancel Edit]:** Dismisses dialog. No state change.
- **[Finish Workout First]:** Navigates to `ActiveWorkoutScreen` to resolve the active workout (finish or cancel it). Edit mode is not entered.

Edit mode must never run concurrently with a live workout. `workoutId = null` in edit mode and `workoutId ≠ null` in live mode are mutually exclusive — enforce this at the ViewModel level.

**Implementation note:** This guard dialog is rendered in `PowerMeNavigation` (not inside `ActiveWorkoutScreen`) by observing a `showEditGuard: StateFlow<Boolean>` flag exposed from `WorkoutViewModel`. `clearEditGuard()` is called on dialog dismiss. The ViewModel sets `showEditGuard=true` and returns early from `startEditMode()` if `isActive=true`.

### 11.2 Maximization LaunchedEffect Contract

When `WorkoutViewModel` signals that a minimized workout should come back to the foreground (e.g. the user taps the `MinimizedWorkoutBar` or the Resume banner), the navigation must happen via a `LaunchedEffect` observing `isMinimized`:

```kotlin
LaunchedEffect(workoutState.isMinimized) {
    if (!workoutState.isMinimized && navController.currentDestination?.route != ACTIVE_WORKOUT) {
        navController.navigate(ACTIVE_WORKOUT) {
            launchSingleTop = true
        }
    }
}
```

This is the **only** valid path for programmatic forward-navigation to `ActiveWorkoutScreen`. Do not trigger it from button `onClick` lambdas directly.

---

## 12. Iron Vault (Auto-Save)

All in-workout changes are written to the DB in real-time to survive process death:

| Change | Trigger | Mechanism |
|---|---|---|
| Weight / Reps | `onWeightChanged` / `onRepsChanged` | Debounced coroutine (300ms) |
| Set completion | `completeSet()` | Immediate `workoutSetDao.markCompleted()` |
| Set type | `selectSetType()` | Immediate `workoutSetDao.updateSetType()` |
| RPE | `updateRpe()` | Immediate `workoutSetDao.updateRpe()` |

Iron Vault is **disabled in edit mode** (no `workoutId` exists; writes go to `routine_exercises` only on Save).

### 12.1 Rehydration (Crash Recovery)

On `WorkoutViewModel` init, `rehydrateIfNeeded()` checks the DB for any incomplete workout (`workouts.isCompleted = 0`). If found:
- Restores the full `ActiveWorkoutState` from DB rows (exercises, sets, ghost data, rest overrides).
- Sets `isActive=true` — the workout resumes as if the app never closed.
- The **Resume Workout** banner on the Workouts screen is driven by this rehydrated state.

This is how Iron Vault fulfils its promise: a user who loses the app mid-workout never loses their session.

---

## 13. Smart Fill — Pre-Fill & Forward Cascade

### 13.0 Pre-Fill on Workout Start (from Previous Session)

When `startWorkoutFromRoutine()` builds `ExerciseWithSets`, each set's initial `weight` and `reps` are populated as follows (priority order):

| Priority | Source | Condition |
|---|---|---|
| 1 | `getPreviousSessionSets(exerciseId)` — last completed set | Exercise has prior history in any routine |
| 2 | `routine_exercises.defaultWeight` / `routine_exercises.reps` | No prior history for this exercise |
| 3 | Empty / zero | Neither source has data |

The PREV ghost label in the PREV column continues to show the last-session data (unchanged — this is informational). The input field itself is **also pre-filled** with this same value, so the user doesn't need to re-enter a number they expect to match.

**New exercise, first set filled → cascade down:** When a user logs values in Set 1 of an exercise they have no history for (PREV is empty), and there are 2+ uncompleted sets below Set 1, the cascading fill described in §13.1 applies immediately.

### 13.1 Cascading Fill (on Set 1 Edit) — Type-Group Isolated

This cascade **only fires when the changed set is the first set of its type group**. Two groups exist:
- **WARMUP group** — sets with `setType == SetType.WARMUP`
- **WORK group** — sets with `setType != SetType.WARMUP` (NORMAL, DROP, FAILURE)

**Type isolation rule:** Changing a WARMUP set cascades only to other WARMUP sets. Changing the first WORK set cascades only to WORK sets (NORMAL + DROP + FAILURE). Values never cross group boundaries.

**Trigger:** `onWeightChanged`, `onRepsChanged`, or `onTimeChanged` in `WorkoutViewModel`, but only when editing the **first set in its type group** (lowest `setOrder` within the same group). `onTimeChanged` applies to TIMED exercises only.

**Cascade target:** All sets in the same exercise AND same type group where:
1. `isCompleted == false`
2. The field's current value has **not** been manually edited by the user in this session (i.e., `isDirtyWeight == false` for the weight cascade, `isDirtyReps == false` for the reps cascade).

**Dirty flag mechanism:** `ActiveSet` gains two additional fields:
- `isDirtyWeight: Boolean = false` — set to `true` the first time the user manually edits the weight field for that set.
- `isDirtyReps: Boolean = false` — set to `true` the first time the user manually edits the reps field for that set.

Cascade never overwrites a set where `isDirtyWeight == true` (weight cascade) or `isDirtyReps == true` (reps cascade). Sets that were pre-filled on workout start via §13.0 have `isDirty* = false` until the user explicitly edits them.

**Algorithm:**
1. Identify whether the changed set is the first set (`setOrder == minOrder`). If not, no cascade occurs.
2. Mark the changed set's dirty flag as `true`.
3. Iterate forward through all subsequent sets in the exercise.
4. For each subsequent set: if `isCompleted == false` AND `isDirtyWeight/Reps == false` → fill with the new value. If `isDirtyWeight/Reps == true` or `isCompleted == true` → skip it (do not stop, continue checking later sets).

**Example:**

```
Initial (pre-filled from last session):  Set1=100  Set2=100  Set3=100  (all isDirty=false)
User changes Set2 to 105 manually (isDirtyWeight[Set2]=true):
User changes Set1 to 110:
Result:   Set1=110    Set2=105    Set3=110    ← Set2 skipped (isDirty=true), Set3 updated
```

### 13.2 Disabled in Edit Mode

Smart Fill does not run when `isEditMode = true`. Edit mode is for precise template construction — auto-fill would corrupt intentional routine structures.

---

## 14. RPE, Add Rest & Plate Calculator

### 14.1 FINISH WORKOUT Button

**Placement:** The FINISH WORKOUT button is the **last item** in the `LazyColumn` of `ActiveWorkoutScreen`, rendered as:
```kotlin
item {
    FinishWorkoutButton(
        modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)
    )
}
```
It is **not** placed in the `TopAppBar`. The TopAppBar contains only: minimize/cancel icon, workout name, elapsed timer, and standalone timer icon.

**Style:** `OutlinedButton` with `TimerGreen` border (`BorderStroke(1.dp, TimerGreen)`) and `TimerGreen` text color on a transparent background. This signals "this ends something" rather than "this continues something."

**Visibility:** Only shown when `isActive = true`. Hidden in edit mode (SAVE CHANGES is the CTA in edit mode).

**SAVE CHANGES button (edit mode):** Same `OutlinedButton` treatment, using `primary` colour for text and border.

### 14.2 Add Set Button

**Placement:** Bottom of every `ExerciseCard`, immediately after the last `WorkoutSetRow` (and its rest separator, if present).

**Visual style:** `FilledTonalButton` — the dominant action in the card footer.

**Behaviour on tap:**
1. **Index:** New `ActiveSet` appended with `setOrder = currentMax + 1`.
2. **Clipboard check:** If a `DeletedSetClipboard` entry exists for this `exerciseId`, paste its `weight`, `reps`, and `setType` into the new set (instead of inheriting from the last surviving set). Then clear the clipboard entry.
3. **Smart Inheritance:** If no clipboard entry, inherits weight and reps from the immediately preceding set (Set N−1).
4. **Type:** Always defaults to `NORMAL`, regardless of the previous set's type (clipboard paste restores the original type).
5. **Rest injection:** A `RestSeparator` is automatically inserted between the old final set and the new set.

### 14.2b Add Rest Button

**Placement:** Bottom of every `ExerciseCard`, alongside Add Set.

**Visual style:** `TextButton` (ghost style, `error.copy(alpha = 0.7f)` text color) — secondary to Add Set. Only visible when no rest separator follows the last set in this exercise.

**Behaviour:** Appends a `RestSeparator` below the last `WorkoutSetRow` in the exercise block.

**Guard:** If a separator already exists for the last set, the button is hidden — no duplicate separators.

**Visual hierarchy:** Add Set is always the dominant action. Add Rest must not appear visually equal in weight to Add Set.

### 14.3 RPE System

- **Scale:** 6.0 – 10.0 in 0.5 increments.
- **Storage:** Integer × 10 (e.g. 8.5 → stored as `85`) in `workout_sets.rpe`.
- **UI:** Tapping the RPE column cell opens `RpePickerSheet` (ModalBottomSheet with FilterChip grid + Clear button).
- **Display:** RPE column shows the formatted value (e.g. `8.5`) or `—` if unset.
- **Analytics:** RPE is surfaced in the PREV ghost label: `{weight}×{reps}@{rpe}`. Included in e1RM context but does not alter the Epley formula directly.

**RPE Picker anchor labels:** The `RpePickerSheet` must include descriptive context labels anchored at three points in the chip grid to explain what the scale means in practical terms:

| Value | Label |
|---|---|
| 6.0 | "Very Light" |
| 8.0 | "Hard — 2 reps in reserve" |
| 10.0 | "Maximum Effort" |

These labels are rendered as small muted text below or beside the corresponding chip, not as separate rows. They are informational only — they do not change the selection behaviour.

### 14.4 Plate Calculator

- **Access:** `PlateCalculatorSheet` (ModalBottomSheet), triggered per set from the set row.
- **Maths:** Standard barbell calculation — 20kg bar + pairs of plates: 25 / 20 / 15 / 10 / 5 / 2.5 / 1.25 kg.
- **Input:** Reads the set's current weight value. Displays the plate breakdown required per side.
- **User plates:** Reads from `UserSettings.availablePlates` to only suggest plates the user owns.

---

## 15. Smart Search (MagicAddDialog)

`MagicAddDialog` is fully specified in **`EXERCISES_SPEC.md §6.1`**, which owns the Gemini prompt contract, state machine (`Idle → Loading → Found → Error → Saved`), and search behavior. Do not duplicate or diverge from that spec here.

**Summary (mid-workout context only):** When `[+ ADD EXERCISE]` is tapped during an active workout, `MagicAddDialog` opens. On exercise selection or creation, the result is passed via `onExerciseAdded(exercise)` callback to `WorkoutViewModel.addExerciseToWorkout()`. The Gemini enrichment flow and DB write are handled inside `MagicAddViewModel` — `WorkoutViewModel` only processes the returned `Exercise` entity.

---

## 16. Workouts Screen

The entry point for the workout journey. Manages routine browsing, workout resume, and routine lifecycle actions.

### 16.1 Layout

```
[Workouts title]
[Resume Workout banner]           ← only when isWorkoutActive
[Start Empty Workout]             ← OutlinedButton
[Routines label + Add (+) icon]
["Show Archived" FilterChip]      ← top-right of the Routines row; default OFF
[Empty state]                     ← "No routines yet"
[2-column RoutineCard grid]       ← filtered by chip state (see §16.2a)
[RoutineOverviewSheet]            ← shown when a card is tapped
```

### 16.2a Archive Filter

A `FilterChip` labeled **"Show Archived"** sits in the top-right of the Routines row header.

| Chip State | List contents |
|---|---|
| OFF (default) | Only routines where `isArchived == false` |
| ON | Only routines where `isArchived == true` |

**Data model:** `Routine.isArchived: Boolean` maps to `isArchived INTEGER NOT NULL DEFAULT 0` in Room (added in v20 migration — no further migration needed). `WorkoutsViewModel` exposes a `showArchived: StateFlow<Boolean>` toggle; the repository query filters by this flag.

### 16.2 RoutineCard

Compact card in a 2-column grid:

- **Routine name** (max 2 lines, ellipsis)
- **Exercise preview:** first 3 exercise names separated by `•`, then `+N more` if exceeds 3
- **Recency chip:** `Never` / `Today` / `Yesterday` / `Nd ago` with `AccessTime` icon
- **⋯ menu (`MoreVert` `IconButton` → `DropdownMenu`):** 7 actions in this order:

  1. **Edit** — Navigates directly to `template_builder/{routineId}`. Disabled when `isWorkoutActive=true` (same guard as RoutineOverviewSheet).
  2. **Rename** — Opens a lightweight `AlertDialog` with an `OutlinedTextField`. On confirm, calls `WorkoutsViewModel.renameRoutine()` without loading the template builder.
  3. **Duplicate** — Deep-copies the `Routine` entity and all associated `routine_exercises` rows atomically (`withTransaction`). Saved name: `[Original Name] (Copy)`. New: `WorkoutsViewModel.duplicateRoutine(routine)` → `RoutineRepository.duplicateRoutine(routineId)`.
  4. **Create Express** *(Deterministic Truncation, no AI)* — Generates a time-optimized version:
     1. Deep-copy the original routine (same path as Duplicate).
     2. Drop the bottom `floor(exercises.size × 0.4)` exercises (cuts isolation/accessory work).
     3. **Warmup-aware set cap:** Keep ALL warmup sets + cap work sets (NORMAL/DROP/FAILURE) to a maximum of `2`. Uses `selectExpressIndices()` to pick kept indices, then `pickIndices()` to extract corresponding entries from `setTypesJson`/`setWeightsJson`/`setRepsJson`.
     - Saved name: `[Original Name] - Express`. New: `WorkoutsViewModel.createExpressRoutine(routine, exercises)`.
     - **Why:** Naively capping to `coerceAtMost(2)` would keep only warmup sets if a routine has 2+ warmups at the start, losing all work sets.
  5. **Export to Text** — Formats exercises as `{sets}×{reps} {name}` (one per line) and fires `Intent(Intent.ACTION_SEND).setType("text/plain")` (Android Share Sheet). No new ViewModel method required.
  6. **Archive / Unarchive** — Label switches dynamically based on `routine.isArchived`. Calls `WorkoutsViewModel.archiveRoutine()` / `unarchiveRoutine()` (existing methods). Archived routines are hidden from the default list view; visible only when "Show Archived" FilterChip is ON (§16.2a).
  7. **Delete** — `error` tint. Shows a confirmation dialog before calling `WorkoutsViewModel.deleteRoutine()`.

### 16.3 Routine Overview Sheet (`RoutineOverviewSheet`)

ModalBottomSheet opened on card tap:

- Header row: **Close (✕)** button | **Routine name** (weight=1f, max 2 lines) | **⋯ `MoreVert` IconButton**
- `Last Performed: {recencyLabel}`
- Exercise list rows: `{sets} × {exerciseName}` | `{muscleGroup}` — `{sets}` is working sets only (NORMAL, FAILURE, DROP). WARMUP-type slots are excluded from this count.
- **Superset spine:** Each row is wrapped in `Row(IntrinsicSize.Min)`. When `supersetGroupId != null`, a 4dp vertical `Box` is rendered on the left edge using `buildSupersetColorMap` (insertion-order, same palette as `ActiveWorkoutScreen`). An 8dp start padding separates the bar from the exercise text.
- `CircularProgressIndicator` while exercise details are loading
- **Start Workout** button (primary, full width)

**⋯ menu** — same 7 actions as RoutineCard (§16.2), in the same order. Edit disabled when `isWorkoutActive=true`. Export to Text uses the already-loaded `exerciseDetails` list (no pending export pattern needed — details are always loaded when the sheet is open).

### 16.4 Resume Workout Banner

Shown at the top of the screen when `rehydrateIfNeeded()` restores an incomplete workout. Tapping it navigates to `ActiveWorkoutScreen` and maximizes the existing session.

---

## 17. Template Builder Screen

Full-screen routine editor. Accessible via the `+` icon (create new, `routineId=-1`) or the **Edit** action in the `RoutineOverviewSheet` / `RoutineCard` ⋯ menu (edit existing, navigates to `template_builder/{routineId}`).

### 17.1 Layout

```
TopAppBar:
  [← Back]  [Routine Name OutlinedTextField]  [Save TextButton]

LazyColumn (reorderable):
  [DraftExerciseRow × N]
  [Empty state: FitnessCenter icon + "No exercises yet"]

Footer:
  [Add Exercises OutlinedButton]  → navigates to exercise_picker route
```

### 17.2 DraftExerciseRow (normal mode)

- Exercise name (SemiBold) + muscle group `SuggestionChip` (primary-tinted)
- **Sets stepper:** `−` | `N sets` | `+` (via `decrementSets` / `incrementSets`)
- **Delete** button (error color, `removeExercise`)
- Long-press anywhere on the row → enters **Reorder Mode**

### 17.3 Reorder Mode

Triggered by long-pressing any `DraftExerciseRow`. While active:
- All rows switch to `CollapsedTemplateDraftRow`: drag handle icon + exercise name + muscle chip (56dp height)
- The dragged row gets 8dp elevated `Surface`
- Drag handle uses `Modifier.draggableHandle` from `reorderable-compose`
- Releasing completes the reorder via `reorderDraftExercise(fromIdx, toIdx)`
- Reorder mode exits automatically when the drag is released

### 17.4 Exercise Picker Integration

- "Add Exercises" button navigates to the `exercise_picker` route (full-screen `ExercisesScreen` in picker mode).
- Selected exercise IDs are returned via `savedStateHandle["selected_exercises"]` as `ArrayList<Long>`.
- `TemplateBuilderViewModel.addExercises(ids)` fetches full exercise objects and appends them as `DraftExercise` entries.

### 17.5 Save Flow

- **Enabled when:** routine name is not blank and `!isSaving`.
- `save()` runs inside `database.withTransaction { deleteAllForRoutine + insertAll }`.
- Supports both create (`routineId=-1`, inserts new `Routine` row first) and edit (overwrites existing `routine_exercises`).
- On completion calls `onDone()` → navigates back.

> **Spec Debt (DB v29):** The current delete-all + insert-all approach wipes per-set sync data (`setTypesJson`, `setWeightsJson`, `setRepsJson`, `defaultWeight`) for exercises that are structurally unchanged during a re-save. These fields are populated by the Routine Sync diff engine at workout completion. A future update should carry these fields through `DraftExercise` and round-trip them on save for exercises that existed in the previous revision.

---

## 18. Warmup System

### 18.1 Overview

The warmup system generates a personalised pre-workout prescription using the user's planned exercises and injury profile. It is optional — the user can skip it and go straight to logging sets.

### 18.2 Flow

```
[GET WARMUP button] (shown when no exercises added yet)
    │
    ▼
viewModel.requestWarmup()
    │
    ├─ isLoadingWarmup = true (shows spinner)
    │
    ▼
WarmupService.generateWarmup(exercises, injuryProfile)
    │
    ▼
WarmupPrescriptionCard displayed
    │
    ├─ [Log as Performed] → logWarmupAsPerformed()
    │      Inserts WarmupLog row per prescribed exercise
    │      Sets warmupCompleted = true
    │      Shows "✓ Warmup completed" banner
    │
    └─ [Dismiss] → dismissWarmup()
           Clears warmupPrescription (user skipped)
```

### 18.3 `addWarmupSetsToExercise(exerciseId)`

Separate from the warmup prescription. **Not accessible via the Management Hub** (the "Warmup Sets" item was removed from the hub). Available via code/other paths:
- Prepends **3 WARMUP-type sets** to the exercise.
- Shifts all existing working sets down by 3 (re-sequences `setOrder`).
- Iron Vault persists the new sets immediately.

---

## 19. Notes System

Three distinct note types exist across the workout system:

| Note Type | Scope | Persisted | ViewModel Method |
|---|---|---|---|
| **Session Note** (exercise) | Per-exercise, per-session | ❌ In-history only (not in routine template) | `updateExerciseSessionNote` |
| **Sticky Note** (exercise) | Per routine-exercise | ✅ `routine_exercises.stickyNote` (appears in routine template) | `updateExerciseStickyNote` |
| **Set Note** | Per set, per-session | ❌ In-history only (not in routine template) | `updateSetNotes` |
| **Workout Note** | Per workout | ✅ `workouts.notes` | `updateNotes` |

- Session notes and set notes reset each workout.
- Sticky notes are displayed as a gold banner (FormCuesGold `#5A4D1A`) below the exercise header when set.
- Workout-level notes are not currently surfaced in the UI (field exists in DB, write path exists in ViewModel).

---

## 20. Workout Minimize / Maximize

`isMinimized` is a flag in `ActiveWorkoutState` distinct from navigation. It indicates that the workout is active but the screen is not in the foreground.

- `minimizeWorkout()` — sets `isMinimized=true`. Called by the ↓ chevron in both live workout and edit mode. The NavController pops back to the Workouts tab while `WorkoutViewModel` (NavHost-scoped) retains full state.
- `maximizeWorkout()` — sets `isMinimized=false`. Called when the user taps the `MinimizedWorkoutBar` or the Resume banner. The maximization `LaunchedEffect` (§11.2) then navigates forward to `ActiveWorkoutScreen`.
- A second workout cannot be started while `isMinimized=true` — the start flow must check this flag and redirect to the minimized session instead.

**Minimizing never terminates the session.** The workout remains fully active — elapsed timer running, Iron Vault active, rest timers ticking — while minimized. The only valid ways to terminate a session are `cancelWorkout()` (explicit user cancel) and `finishWorkout()`. There is no implicit termination path.

### 20.1 MinimizedWorkoutBar

When `isMinimized=true` (and `isActive=true` or `isEditMode=true`), a **56dp** persistent bar is rendered **above the bottom navigation bar** in the main scaffold. It remains visible regardless of which tab the user is on.

```
┌─────────────────────────────────────────────────────┐
│▌ [Workout Name]  ·  [mm:ss elapsed — monospace]  [↑] │  ← 56dp, surfaceVariant bg, primary left border
└─────────────────────────────────────────────────────┘
```

- **Background:** `surfaceVariant` — neutral, low-emphasis. The bar must **not** use the loud `primary` or `primaryContainer` colour, which competes visually with the bottom navigation tabs. ⚠️ **Code conflict:** The current `PowerMeNavigation.kt` incorrectly uses `color = MaterialTheme.colorScheme.primary` for the `Surface`. This is wrong and must be refactored to `surfaceVariant` with the 4dp primary left border described here.
- **Left edge accent:** A **4dp thick** solid `primary`-coloured vertical border on the left side only. This is the sole indicator that an active workout is in progress without dominating the UI.
- **Workout name:** `onSurface` text, truncated with ellipsis (`maxLines=1`).
- **Elapsed timer:** monospace font, `primary` colour, continues ticking from `WorkoutViewModel`'s `elapsedTimerJob`.
- **Up-arrow (↑):** `IconButton` (primary tint) → calls `maximizeWorkout()`.
- **Tap anywhere on bar:** also calls `maximizeWorkout()`.
- In **edit mode**, show the routine name being edited in place of the workout name. The elapsed timer is hidden (edit mode has no elapsed timer). The status/subtitle slot where the elapsed timer normally appears shows the fixed text `"Edit Mode"`.

### 20.2 Edit Mode Minimization Contract

Edit mode supports the same minimization flow as live workout:

| Event | Action |
|---|---|
| ↓ chevron tapped in edit mode | `minimizeWorkout()` — NavController pops to Workouts tab, edit state preserved |
| System back pressed in edit mode | Same as ↓ chevron (minimize, not cancel) |
| `MinimizedWorkoutBar` tapped while in edit mode | `maximizeWorkout()` → `LaunchedEffect` navigates back to `ActiveWorkoutScreen` in edit mode |
| Bottom nav tab tapped while in edit mode | Same as ↓ chevron — minimize and navigate to tapped tab |

**Edit mode is still "active" while minimized.** The [Discard Edits] confirmation dialog only appears if the user explicitly taps the ✕ Cancel button inside `ActiveWorkoutScreen`, not during minimize.

### 20.3 Minimize / Maximize Animation

All minimize and maximize transitions use a **slide-only** animation (no fade or alpha change). Duration: **350ms**, `FastOutSlowInEasing`.

**Minimize (full screen → bar):**
- `ActiveWorkoutScreen` exits with: `slideOutVertically(tween(350, FastOutSlowInEasing)) { fullHeight }` — slides down off screen.
- `MinimizedWorkoutBar` enters with: `AnimatedVisibility(enter = slideInVertically(tween(350)) { fullHeight })` — slides up from bottom.

**Maximize (bar → full screen):**
- `ActiveWorkoutScreen` enters with: `slideInVertically(tween(350, FastOutSlowInEasing)) { fullHeight }` — slides up from below.
- `MinimizedWorkoutBar` exits with: `AnimatedVisibility(exit = slideOutVertically(tween(350)) { fullHeight })` — slides down out of view.

**Implementation:** `exitTransition` and `enterTransition` on the `Routes.WORKOUT` `NavHost` destination + `AnimatedVisibility` wrapping `MinimizedWorkoutBar` in the scaffold.

---

## 21. Post-Workout Performance Analysis, Telemetry & Notifications

### 21.0 Workout Foreground Notification

`WorkoutTimerService` runs as a `ForegroundService` during an active workout and must show a persistent notification with the following content:

| Element | Content |
|---|---|
| **Title** | Routine name (e.g. "Push Day") |
| **Line 1** | Elapsed session time: `⏱ 42:17` (monospace-style) |
| **Line 2** (when rest timer active) | Live rest countdown: `⏸ 0:48 remaining` |
| **Line 2** (when no rest timer) | `Active — tap to return` |

The notification must update every second while the session is active. It must be cleared (`stopForeground(true)`) when `finishWorkout()` or `cancelWorkout()` resolves.

The ViewModel must pass `workoutName` and `elapsedSeconds` to the service (or to a notification helper) on each elapsed-timer tick. The service mirrors the rest timer state and is already responsible for the rest countdown.

### 21.1 `BoazPerformanceAnalyzer`

`finishWorkout()` calls `BoazPerformanceAnalyzer` after writing history to DB. This hook runs analytics on the completed session (performance trends, weekly insights, 1RM progression). It is a post-write side-effect — failure does not affect the workout save. Full specification lives in the analytics layer, not here.

### 21.2 TelemetryEngine (formerly `stateHistoryRepository`)

`TelemetryEngine` is the canonical name for the component previously called `stateHistoryRepository`. All new code must use the `TelemetryEngine` identifier; the old name is deprecated.

**Responsibilities:**
- Record discrete workout events (set completed, rest started, exercise added, workout finished, etc.) with **event-level timestamps** (`Instant`, not just session-level).
- Enforce a **30-day rolling TTL window** — events older than 30 days are pruned automatically (scheduled or on-write cleanup).

**Architecture (future / aspirational):**
- Events are buffered locally in Room (event log table).
- A background sync job forwards the buffered log to **GCP / Firebase ML Pipeline** for model training and personalization.
- The sync job is non-critical — local data is always the source of truth. Network failures do not block the workout.
- Privacy: events must be stripped of free-text user content (set notes, exercise names searched but not selected) before leaving the device.

**Current implementation note:** The local event log and 30-day TTL exist but GCP sync is not yet wired. Do not block features on this pipeline being available.

---

## 22. Keep Screen On

`keepScreenOn: StateFlow<Boolean>` is sourced from `AppSettingsDataStore` (user preference, toggled in Settings).

**Behaviour during a workout:**
- When `keepScreenOn=true` **and** a workout is active (`isActive=true` or `isEditMode=true`): the `FLAG_KEEP_SCREEN_ON` window flag must be set on the host `Activity` window.
- When `keepScreenOn=false`, or when the workout ends/is cancelled: the flag must be cleared.

**Implementation pattern** (in `MainActivity` or a dedicated `SideEffect` in the composable):
```kotlin
SideEffect {
    val window = (context as Activity).window
    if (keepScreenOn && workoutIsActive) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

The flag must be cleared promptly when the workout ends — never leave `FLAG_KEEP_SCREEN_ON` set outside of an active workout session.

---

## 23. Snackbar Feedback

`snackbarMessage: StateFlow<String?>` is exposed from `WorkoutViewModel` (or via a `SharedFlow<SnackbarEvent>` if action support is needed).

**Placement:** A `SnackbarHost` is anchored to the bottom of the scaffold, sitting **above** the `MinimizedWorkoutBar` (if visible) and above the bottom navigation bar.

**Standard messages:**

| Trigger | Message | Action |
|---|---|---|
| Set swiped-to-delete | "Set deleted" | **Undo** (restores the set) |
| Rest separator swiped-to-delete | "Rest separator removed" | — |
| Routine values updated | "Routine defaults updated" | — |
| Routine structure updated | "Routine structure updated" | — |
| Exercise removed | "Exercise removed" | **Undo** |

**Undo for swipe-delete:** The ViewModel must retain the deleted item in memory until the snackbar times out (3–5 seconds). If the user taps Undo before dismissal, the item is re-inserted at its original position. After timeout, the DB delete is committed (or if Iron Vault has already persisted the deletion, the undo issues a re-insert).

**Rule:** Every destructive swipe action in the workout screen must produce a snackbar with an Undo option. This is a firm UX commitment surfaced by the UI/UX review.

---

## 24. Available Exercises Cache

`availableExercises: List<Exercise>` is preloaded into `ActiveWorkoutState` (or held as a separate `StateFlow`) on `WorkoutViewModel` init.

**Purpose:** Powers the `MagicAddDialog` local search path and the Replace Exercise flow without requiring a DB query on every keystroke.

**Rules:**
- Load the **full exercise catalog** (all exercises, no filters).
- **Do not deduplicate** based on exercises already in the workout. The search should show all exercises — the user may want to add the same exercise twice (e.g. Bench Press as a superset partner).
- The cache is read-only from the UI layer. Mutations (adding new exercises via Gemini enrichment) must go through the repository and trigger a cache refresh.
- The cache is invalidated and reloaded if the user creates a new exercise during the session.

---

## 25. Medical / Injury Restrictions

`WorkoutViewModel` loads `MedicalRestrictionsDoc` via `MedicalLedgerRepository` on init and exposes it as `medicalDoc: StateFlow<MedicalRestrictionsDoc?>`.

Current usage in the workout flow:
- Passed to `WarmupService.generateWarmup()` as the injury profile, so the warmup prescription avoids contraindicated movements.
- Available to `ActiveWorkoutScreen` for potential future exercise-level restriction warnings (not yet wired to UI blocking).

Full injury ledger specification lives in `INJURY_CONTEXT_SPEC.md` and `INJURY_TRACKER_SPEC.md`.

---

## 26. Workout Detail Screen

**Canonical ownership:** This section (`WORKOUT_SPEC.md §26`) is the single source of truth for `WorkoutDetailScreen`. It shares the same `ExerciseCard` and `WorkoutSetRow` composables with `ActiveWorkoutScreen`, which is why it lives here rather than in `HISTORY_ANALYTICS_SPEC.md`. Future history/analytics specs must cross-reference this section and must not re-specify or duplicate this screen's layout or editability rules.

`WorkoutDetailScreen` is the read (and optionally edit) view for a completed workout accessed from the History tab.

### 26.1 Layout

Mirrors the `ActiveWorkoutScreen` ExerciseCard structure:
- Same column grid (SET | PREV→**e1RM** | WEIGHT | REPS | RPE | ✓)
- Superset spine (4dp palette-colored bar, via `buildSupersetColorMap`) rendered where applicable; color is assigned by insertion order — different groups always get distinct palette colors (never hardcoded `secondary`)
- **Exercise ordering:** `getSetsWithExerciseForWorkout` orders by `ws.rowid ASC, ws.setOrder ASC`. SQLite `rowid` auto-increments on insertion; since sets are inserted exercise-by-exercise in routine order, `rowid` preserves original workout sequence without a dedicated `exerciseOrder` column.
- Set type badge (W / D / F prefix) shown in SET column
- Set notes rendered inline below each set row if present
- **Set count shown in the header/totals row excludes WARMUP sets.** Total = NORMAL + FAILURE + DROP completed sets only.

The **PREV column is replaced by an e1RM column** in this screen. Each set's e1RM is computed via `StatisticalEngine.calculate1RM(weight, reps)` and displayed. This is the one screen where PREV→e1RM substitution is correct — do not apply it to `ActiveWorkoutScreen`.

### 26.2 Editability ("Edit Session")

`WorkoutDetailScreen` is read-only by default. An **"Edit Session"** action (accessible via a TopAppBar menu or overflow) unlocks inline editing:

- Weight and reps for any set become editable `WorkoutInputField` widgets.
- Set type can be changed via `SetTypePickerSheet`.
- Sets can be added or deleted.
- On save: changes are committed retroactively to the existing `workout_sets` rows in DB (`workoutId` is known). There is no Iron Vault debounce — all changes are written atomically on explicit Save.
- Analytics (e1RM, volume, weekly insights) must be recalculated for this workout after a retroactive edit.

**Rule:** Retroactive edits write to the historical record only. They do not trigger the Routine Sync Diff Engine — that engine runs only on `finishWorkout()` for live sessions.

---

## 27. Technical Invariants (Claude must not violate)

1. **State priority:** UI renders one state at a time per the hierarchy `Loading > EditMode > ActiveWorkout > Summary > Idle`. Never allow overlapping states.
2. **Swipe coupling — set deletes both, rest deletes only rest:** The set row swipe deletes the set AND hides the rest separator below it (coupling via `deleteSet()` callback adding the rest key to `hiddenRestSeparators`). The rest separator swipe deletes only the rest separator. Both still use **separate `SwipeToDismissBox` instances** — never group into a single shared box. The coupling is in the callback, not the Compose tree.
3. **Navigation via `LaunchedEffect`:** Any `ActiveWorkoutState` flag that triggers navigation must do so through a `LaunchedEffect`, not inside a composable branch that might not render.
4. **Sync UI in summary:** The routine sync process must be handled exclusively within the `PostWorkoutSummarySheet` using inline CTAs. No separate blocking `AlertDialog`s should be used for this flow.
5. **Atomic routine writes:** `saveRoutineEdits()` must use `database.withTransaction { }` — never bare sequential DAO calls.
6. **State cleanup on disposal:** `cancelEditMode()` must be invoked in a `DisposableEffect` on `ActiveWorkoutScreen` exit to prevent `isEditMode=true` leaking into the next session.
7. **Iron Vault off in edit mode:** Never write to `workout_sets` when `isEditMode=true`. Only `routine_exercises` is a valid write target in edit mode, and only on explicit Save.
8. **`key(set.id)` on set rows:** Required in `LazyColumn` to prevent swipe states jumping after a set is deleted and the list is reindexed.
9. **Haptic feedback:** Set completion → `LongPress`. Swipe-to-delete → `LongPress`. Do not skip haptics on these interactions.
10. **Edit mode is strictly modal (with minimization exception):** While `isEditMode=true`, explicit exit paths (back press, ✕ cancel icon) check `editModeHasChanges()`. If dirty (`editModeDirty=true`), show [Discard Edits] / [Keep Editing] confirmation dialog. If clean, exit silently via `cancelEditMode()`. The ↓ chevron and bottom nav taps always minimize (not cancel) edit mode. Dirty state is tracked via `editModeDirty: Boolean` in `ActiveWorkoutState`, set by `markDirtyIfEditing()` chained onto all 26 exercise-mutating `_workoutState.update` calls in `WorkoutViewModel`.
11. **Volume and set counts exclude WARMUP, TIMED, and CARDIO:** Any volume aggregation OR displayed set count (summary sheet, history screen, RoutineOverviewSheet, WorkoutDetailScreen header) must filter to STRENGTH exercises only, and within those only NORMAL, FAILURE, and DROP sets. WARMUP sets, all TIMED exercise sets, and all CARDIO exercise sets must never contribute to weight-based volume totals or displayed set counts.
12. **PR formula is type-dependent:** For STRENGTH: `e1RM = weight × (1 + reps/30)` (Epley), NORMAL+FAILURE only. For TIMED: `score = weight × timeSeconds` (or `timeSeconds` alone if weight = 0), NORMAL+FAILURE only. For CARDIO: no PR tracking. DROP and WARMUP sets are never PR candidates regardless of type.
13. **PREV column is exercise-global:** Query must not be filtered by `routineId`. Always return data from the last completed session containing that exercise, across all routines.
14. **Warmup-to-working rest is always 2:00:** When `addWarmupSetsToExercise()` is called, the rest separator injected between the last WARMUP set and the first NORMAL set must be hardcoded to 120 seconds, ignoring the exercise's global rest default.
15. **No concurrent live + edit mode:** `isActive=true` and `isEditMode=true` must never be true at the same time. `startEditMode()` must check for an active workout and abort with the pre-workout edit guard dialog (§11.1) if found.
16. **`cycleSetType()` is deleted:** The only path for changing a set's type is `selectSetType()` via the anchored `DropdownMenu` (§10.2). Do not reintroduce a tap-to-cycle shortcut, and do not reintroduce `SetTypePickerSheet`.
17. **`FLAG_KEEP_SCREEN_ON` must be cleared on session end:** When `finishWorkout()` or `cancelWorkout()` resolves, the window flag must be cleared regardless of the `keepScreenOn` user preference.
18. **Set deletion uses clipboard, not Snackbar:** Swipe-to-delete a set row saves the set's values to `deletedSetClipboard[exerciseId]`. There is no "Undo" snackbar for this action. Recovery is via [Add Set] which pastes clipboard values. Do not add a toast or snackbar for set deletion.
19. **`TelemetryEngine` is the canonical name:** Do not use `stateHistoryRepository` in new code. The component is `TelemetryEngine`; the old identifier is deprecated pending a rename refactor.
20. **Routine sync is not blocking:** `finishWorkout()` must always navigate directly to `PostWorkoutSummarySheet`. The Diff Engine result (`RoutineSyncType`) is embedded in `WorkoutSummary.pendingRoutineSync` and surfaced as an opt-in button in the sheet. The old pre-summary `AlertDialog` pattern must not be restored.
21. **No hidden gestures:** Every interaction in the workout screen must be initiated through a visible, explicit UI element. Double-taps, long-presses (that are the sole path to an action), and swipe-only flows without visible affordance are prohibited. All set type changes go through the DropdownMenu (§10.2). All RPE changes go through `RpePickerSheet` (§14.3).
22. **Touch targets ≥ 48dp:** SET badge column and CHECK column in `WorkoutSetRow` must use `Modifier.minimumInteractiveComponentSize()`. RPE column clickable area must fill the full row height and column width.
23. **Keyboard types are explicit at call site:** Weight inputs pass `KeyboardType.Decimal`; reps inputs pass `KeyboardType.Number`. These are set at the `WorkoutSetRow` call site, not as defaults in `WorkoutInputField`.
24. **SwipeToDismissBox state reset:** Each `SwipeToDismissBox` (set and rest) must have a `LaunchedEffect(swipeState.currentValue)` that calls `swipeState.snapTo(Default)` when `currentValue == EndToStart` and the composable is still in composition. This prevents the red delete background from lingering as a ghost after deletion.
25. **Organize Mode persists across commits and exits only on Done:** When `isSupersetSelectMode = true`, set rows and card footers are hidden; exercises render as compact `SupersetSelectRow` rows with checkboxes and drag handles. `commitSupersetSelection()` applies the superset group and clears `supersetCandidateIds` but does **not** clear `isSupersetSelectMode`. The mode exits only when the user taps the **Done** button, which calls `exitSupersetSelectMode()`. Lifecycle events `cancelWorkout()` and `finishWorkout()` also clear the mode (implicit via full state reset). Do not navigate away or change `isActive` state while in organize mode.
26. **Collapsed card timer badge:** When a card is collapsed (`isCollapsed = true`) and its rest timer is active (`activeTimerExerciseId == exerciseId`), the collapsed header must show the live `mm:ss` countdown badge. Collapsing a card must **never** stop, reset, or interfere with the rest timer in any way.
27. **Pre-fill priority order for workout start:** When `startWorkoutFromRoutine()` builds the set list, each set's initial weight and reps follow: (1) previous session data via `getPreviousSessionSets()`, (2) `routine_exercises.defaultWeight`/`reps`, (3) empty. Never pre-fill from a source lower in priority when a higher-priority source has data.
28. **Global Progress Line priority:** The Rest Timer takes precedence over the Standalone Timer on the TopAppBar progress line. Reverts to showing the secondary timer's progress if it remains active after the priority timer finishes.

---

## 28. Routine Per-Set Slot Configuration (DB v28 — Future)

> **Status:** Spec defined, not yet implemented. Do not implement until this section is marked ✅.

Currently `routine_exercises` stores aggregate set count (`.sets: Int`) and shared defaults (`.defaultWeight`, `.reps`, `.restTime`). All sets in a routine exercise share the same weight/rep target. This section specifies a future upgrade to **per-set slot configuration**.

### 28.1 Schema Change (DB v28)

New table: **`routine_set_slots`**

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER PK AUTOINCREMENT` | |
| `routineExerciseId` | `INTEGER FK` | References `routine_exercises.id` ON DELETE CASCADE |
| `slotOrder` | `INTEGER NOT NULL` | 1-indexed position within the exercise |
| `setType` | `TEXT NOT NULL DEFAULT 'NORMAL'` | NORMAL / WARMUP / FAILURE / DROP |
| `defaultWeight` | `TEXT NOT NULL DEFAULT ''` | Per-slot default weight |
| `reps` | `INTEGER NOT NULL DEFAULT 0` | Per-slot rep target |
| `restTimeSeconds` | `INTEGER NOT NULL DEFAULT 90` | Per-slot rest duration |

**Migration v28:** Populate `routine_set_slots` by expanding each existing `routine_exercises` row into N slot rows (one per `routine_exercises.sets` count), copying `.defaultWeight`, `.reps`, and `.restTime` into every slot. After population, `routine_exercises.sets`, `.defaultWeight`, `.reps`, and `.restTime` columns become redundant — they are deprecated (not dropped in v28 for backwards compatibility).

### 28.2 Behaviour Change

Once v28 is active:
- `startWorkoutFromRoutine()` reads from `routine_set_slots` to build the initial `ActiveSet` list. Each slot maps to one `ActiveSet` with its own `defaultWeight`, `reps`, `setType`, and `restTimeSeconds`.
- The Diff Engine (`finishWorkout()`) compares session sets against `routine_set_slots` rows (not aggregate `.sets` count).
- Edit mode writes back to `routine_set_slots` on Save — per-slot granularity.
- The `addWarmupSetsToExercise()` function populates slot type `WARMUP` for the new leading slots. This action is no longer accessible from the Management Hub directly (removed in QA round 1).

### 28.3 Migration Notes for DB_UPGRADE.md

- Migration v28 must be documented in `DB_UPGRADE.md` when implemented.
- Add `RoutineSetSlot` entity + `RoutineSetSlotDao` + `@Database` annotation update.
- `RoutineExerciseDao` must be updated or joined queries added.
