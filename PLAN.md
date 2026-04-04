# PowerME Full-Spec Alignment Plan

## Context

Full audit and alignment of the codebase against 4 finalized spec files:
- `WORKOUT_SPEC.md` — Active workout, edit mode, rest timers, supersets, routine sync, summary
- `THEME_SPEC.md` — Color tokens, typography, semantic color access pattern
- `HISTORY_ANALYTICS_SPEC.md` — History screen, analytics, WorkoutDetailScreen edit flow
- `NAVIGATION_SPEC.md` — Routes, transitions, ViewModel scope, MainAppScaffold

**Execution rule:** Steps run **one at a time**. After each step Claude outputs WHAT CHANGED and HOW TO QA IT. No next step until user replies "APPROVED".

---

## PHASE A — WORKOUT_SPEC.md Alignment (Steps 1–9)

---

### ✅ STEP 1 — PostWorkoutSummarySheet + Non-Blocking Routine Sync

**Status: DONE & APPROVED**

**What changed:**
- `WorkoutSummary` data class gained `pendingRoutineSync: RoutineSyncType?`
- `finishWorkout()` always sets `pendingWorkoutSummary` with embedded sync type (no blocking gate)
- Removed the blocking `when (workoutState.pendingRoutineSync)` AlertDialog from `ActiveWorkoutScreen`
- Implemented full `PostWorkoutSummarySheet` with inline sync CTAs (VALUES / STRUCTURE / BOTH)
- `DeletedSetClipboard` added, `removeExercise()` snackbar added, `SnackbarHost` added
- `startEditMode()` guards against active workout via `showEditGuard`
- `BackHandler` wired for minimize (live workout) and minimize (edit mode)
- `onCancelWorkout` TextButton added between ADD EXERCISE and FINISH WORKOUT

**Files:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

### HOW TO QA IT

1. **Post-workout summary sheet**
   - Start a workout from any routine. Log at least 1 set. Tap **FINISH WORKOUT**.
   - Verify a bottom sheet slides up (not a blocking dialog). It must show workout name, total sets logged, and a close button.

2. **Routine sync CTAs**
   - During a workout, change a weight or rep value from the template default. Finish the workout.
   - Verify the summary sheet shows one of: **Update Values**, **Update Structure**, or **Update Both** button depending on what changed. Tapping it should dismiss the sheet and save silently.

3. **Cancel workout**
   - Start a workout. Before finishing, scroll to the bottom of the exercise list.
   - Verify **CANCEL WORKOUT** text button is visible between ADD EXERCISE and FINISH WORKOUT.
   - Tap it → confirmation dialog appears → confirm → workout is discarded → navigate away.

4. **Minimize via back button**
   - Start a workout. Press the device back button.
   - Verify the workout screen minimizes (does not close). A `MinimizedWorkoutBar` appears at the bottom of the main scaffold.

5. **Edit mode guard**
   - While a workout is active and minimized, navigate to the Workouts tab and try to enter template edit mode for that routine.
   - Verify an AlertDialog appears: "A workout is in progress" with **[Cancel]** and **[Go to Workout]** options.

---

### ✅ STEP 2 — Set Type Selection: Anchored DropdownMenu + Smart-Fill Cascade Fix + Edit Mode Persistence

**Status: DONE & APPROVED**

**What changed:**
- `cycleSetType()` deleted permanently
- SET badge → anchored `DropdownMenu` with 4 types: Work Set / Warm Up / Failure / Drop Set
- Each menu row: colored badge + label + `(?)` info icon (opens info dialog; does not select type)
- "Delete Timer" `TextButton` at bottom of menu (error color)
- Cascade fix: weight cascade triggers only when `set.weight.isBlank() || set.weight == prevFirstSetWeight` — multi-character weight values now cascade correctly
- Same fix applied to reps cascade via `prevFirstSetReps`
- **Edit mode set type persistence (DB v28):** `routine_exercises.setTypesJson` column added. `startEditMode()` deserializes via `String.toEditModeSetTypes()`. `saveRoutineEdits()` serializes set types sorted by `setOrder`.
- **Edit mode weight/reps persistence (DB v29):** `routine_exercises.setWeightsJson` + `setRepsJson` columns added. `startEditMode()` deserializes via `String.toEditModeValues()`. `saveRoutineEdits()` serializes per-set weights and reps.

**Files:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`, `RoutineExercise.kt`, `RoutineExerciseDao.kt`, `DatabaseModule.kt`, `PowerMeDatabase.kt`

### HOW TO QA IT

1. **DropdownMenu opens on SET badge tap**
   - Start a workout. Tap the SET badge (e.g. "1") on any set row.
   - Verify a dropdown menu appears with 4 rows: Work Set, Warm Up, Failure, Drop Set.
   - Each row has a colored badge on the left and a `(?)` icon on the right.

2. **Info dialog does not select**
   - Tap `(?)` next to any type. Verify an info dialog appears with a description.
   - Dismiss the dialog. Verify the set type did **not** change.

3. **Type selection works**
   - Tap SET badge → select Warm Up. Verify the badge label and color change.
   - Tap SET badge again → select Work Set. Verify it reverts.

4. **Smart-fill cascade — single character**
   - Start a workout with 3+ sets. Clear all weights. Type "8" in set 1 weight field.
   - Verify sets 2 and 3 auto-fill to "8".

5. **Smart-fill cascade — multi-character**
   - After the above, type "80" in set 1 weight (backspace "8", type "80"). Verify sets 2 and 3 update to "80".
   - Type "100". Verify cascade updates to "100".

6. **Cascade stops after manual override**
   - Type a different value in set 2. Change set 1 again. Verify only set 3 (untouched) still cascades — set 2 stays at your value.

7. **Edit mode: set type persistence**
   - Open a routine template in edit mode. Change set 2 to Warm Up. Tap Save.
   - Re-enter edit mode. Verify set 2 still shows Warm Up.

8. **Edit mode: per-set weight persistence**
   - In edit mode, set different weights per set (e.g. 60, 70, 80). Tap Save.
   - Re-enter edit mode. Verify each set shows the weight you entered.

9. **Edit mode: per-set reps persistence**
   - In edit mode, set different reps per set (e.g. 12, 10, 8). Tap Save.
   - Re-enter edit mode. Verify each set shows the reps you entered.

---

### STEP 3 — RPE Column: Wire Picker + RpePickerSheet

**Status: Implemented — QA pending**

**What changed:**
1. RPE column `Box` now has `fillMaxHeight()` + `.clickable { showRpePicker = true }`
2. Displays `"6.0"`–`"10.0"` from live `set.rpeValue`; shows muted `"—"` when null
3. New `RpePickerSheet` composable: `FlowRow` of `FilterChip` covering 6.0–10.0 in 0.5 increments, anchor labels at 6.0 ("Very Light") / 8.0 ("Hard — 2 reps in reserve") / 10.0 ("Maximum Effort"), Clear button

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **RPE column shows "—" by default**
   - Start a workout. Verify the RPE column for each set shows a muted `—`.

2. **Tapping opens RpePickerSheet**
   - Tap anywhere in the RPE column of set 1. Verify a bottom sheet slides up.
   - Sheet must contain chips from 6.0 to 10.0 in 0.5 increments (6.0, 6.5, 7.0 … 10.0).

3. **Anchor labels appear**
   - Verify the labels "Very Light" (near 6.0), "Hard — 2 reps in reserve" (near 8.0), and "Maximum Effort" (near 10.0) appear in the sheet.

4. **Selecting an RPE value**
   - Tap chip "8.5". Sheet dismisses. Verify the RPE column for set 1 now shows `8.5`.

5. **Clear button**
   - Open RPE picker again. Tap **Clear**. Verify RPE column returns to muted `—`.

6. **RPE persists to history**
   - Select RPE 9.0 on set 1, complete the set, finish the workout.
   - Go to History → tap the session → WorkoutDetailScreen shows 9.0 for that set.

---

### STEP 4 — PREV Column: Ghost Label + Tappable Header Tooltip

**Status: Implemented — QA pending**

**What changed:**
1. `formatGhostLabel(weight, reps, rpe)` helper: renders `{weight}×{reps}@{rpe}` / `{weight}×{reps}` / `"—"`
2. PREV column reads `set.ghostWeight / ghostReps / ghostRpe` from `ActiveSet`
3. "PREV" header in `StrengthHeader` is tappable → opens `AlertDialog` tooltip

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **PREV shows "—" on first-ever session for an exercise**
   - Start a workout with an exercise you've never logged before.
   - Verify the PREV column for all sets shows `—`.

2. **PREV populates from the previous session**
   - Log exercise X with set 1: 80 kg × 10 reps. Finish the workout.
   - Start a new workout with exercise X. Verify set 1 PREV column shows `80×10`.

3. **PREV with RPE**
   - Log exercise X with RPE 8.0 on set 1. Finish. Start new session.
   - Verify PREV shows `80×10@8.0`.

4. **PREV header tooltip**
   - Tap the **PREV** column header text. Verify an `AlertDialog` appears explaining what the column shows.
   - Dismiss it.

---

### STEP 5 — WorkoutSetRow Polish: Touched Indicator, Keyboard Types, Touch Targets

**Status: Implemented — QA pending**

**What changed:**
1. Touched-set left-border indicator: 2dp `primary.copy(alpha=0.4f)` on the set row when weight or reps is entered but the set is not yet checked complete
2. Weight `WorkoutInputField` uses `KeyboardType.Decimal`
3. Reps `WorkoutInputField` uses `KeyboardType.Number`
4. SET badge `Box` + CHECK `IconButton` both have `.minimumInteractiveComponentSize()`

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **Decimal keyboard for weight**
   - Tap the weight input field of any set. Verify the keyboard shows a decimal/numeric pad (with a `.` key).

2. **Number keyboard for reps**
   - Tap the reps input field. Verify the keyboard shows a pure integer numeric pad (no decimal key).

3. **Touched indicator appears**
   - Type a weight value for a set but do not check it complete.
   - Verify a subtle 2dp colored left border appears on that row.

4. **Touched indicator disappears on completion**
   - Check the set complete (tap the checkmark). Verify the left border indicator is gone.

5. **No indicator on untouched sets**
   - Sets with no weight/reps entered and unchecked should have no left border.

6. **Touch targets**
   - Tap the SET badge and the CHECK icon. Verify taps register reliably even near the edges of the badge/icon (minimum 48dp touch area).

---

### STEP 6 — DeletedSetClipboard + Snackbar Rules

**Status: Implemented — QA pending**
*(DeletedSetClipboard and SnackbarHost were implemented as part of Step 1)*

**What changed:**
1. Swipe-to-delete a set → set removed, snackbar "Set removed" with UNDO action
2. UNDO → set restored at original position
3. Remove exercise → snackbar "Exercise removed" with UNDO action
4. `SnackbarHost` wired at scaffold level

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **Swipe-delete a set**
   - During a workout, swipe a set row left or right. Verify the set disappears and a snackbar appears: `"Set removed"` with an **UNDO** button.

2. **UNDO restores the set**
   - While the snackbar is visible, tap **UNDO**. Verify the set reappears at the same position.

3. **Auto-dismiss removes permanently**
   - Swipe-delete a set. Wait for the snackbar to auto-dismiss (3–5s). Verify the set is gone permanently.

4. **Remove exercise snackbar**
   - Long-press or use the remove button on an exercise. Verify a snackbar appears: `"Exercise removed"` with **UNDO**.
   - Tap UNDO → exercise reappears with all its sets intact.

5. **Only one snackbar at a time**
   - Delete a set then immediately delete another. Verify the second snackbar replaces the first (no stacking).

---

### STEP 7 — TimerControlsSheet + RestTimePickerDialog

**Status: Implemented — QA pending**

**What changed:**
1. `TimerControlsSheet`: large mm:ss display, **-10s** / **Pause/Play** / **+10s** buttons, **Skip** TextButton
2. `RestTimePickerDialog`: minutes + seconds `OutlinedTextField` inputs, 0–599s validation

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **TimerControlsSheet opens on timer tap**
   - Complete a set that has a rest timer configured. Wait for the `RestSeparator` to appear.
   - Tap the timer countdown display. Verify `TimerControlsSheet` slides up as a bottom sheet.

2. **Timer display**
   - Sheet shows remaining time in large `mm:ss` format (e.g. `01:30`).

3. **+10s and -10s buttons**
   - Tap **+10s**. Verify timer increases by 10 seconds.
   - Tap **-10s**. Verify timer decreases by 10 seconds (floor at 0, does not go negative).

4. **Pause and resume**
   - Tap the **Pause** button. Verify the timer stops counting and the icon changes to Play.
   - Tap **Play**. Verify the timer resumes.

5. **Skip**
   - While the timer is running, tap **Skip**. Verify the sheet closes and the rest separator disappears immediately.

6. **RestTimePickerDialog**
   - Long-press on the **REST** header of a rest separator (or access via exercise options).
   - Verify a dialog appears with two fields: **Minutes** and **Seconds**.
   - Enter `2` minutes `30` seconds → OK → new rest time is `2:30`. Verify the timer shows this value next time a set is completed.

7. **Validation**
   - Try entering a total time > 599s (e.g. 10 minutes 0 seconds = 600s). Verify an error message or the OK button is disabled.

---

### STEP 8 — MinimizedWorkoutBar: Edit Mode Support + Edit Guard

**Status: Implemented — QA pending**

**What changed:**
1. `MinimizedWorkoutBar` is visible when `(isActive || isEditMode) && isMinimized`
2. Edit mode bar shows "Edit Mode" text instead of elapsed timer
3. `startEditMode()` checks `isActive` → sets `showEditGuard = true` if a live workout is running
4. Edit guard `AlertDialog`: **[Cancel]** + **[Go to Workout]**

**Files:** `PowerMeNavigation.kt`, `WorkoutViewModel.kt`

### HOW TO QA IT

1. **MinimizedWorkoutBar in edit mode**
   - Open a routine template in edit mode. Press the device back button.
   - Verify a `MinimizedWorkoutBar` appears at the bottom of the screen showing **"Edit Mode"** (not a timer).

2. **Maximize from edit mode bar**
   - Tap the `MinimizedWorkoutBar` while in edit mode. Verify the template edit screen reopens.

3. **Edit guard — live workout active**
   - Start a workout and minimize it. Navigate to the Workouts tab.
   - Tap **Edit** on the same routine that is currently active.
   - Verify an AlertDialog appears (e.g. "A workout is in progress").

4. **Edit guard — [Cancel]**
   - When the guard dialog is shown, tap **Cancel**. Verify the dialog dismisses and nothing changes.

5. **Edit guard — [Go to Workout]**
   - When the guard dialog is shown, tap **Go to Workout**. Verify the screen navigates to the active workout screen.

---

### STEP 9 — Rest Separator Entrance Animation

**Status: Implemented — QA pending**

**What changed:**
1. `AnimatedVisibility` enter: `slideInVertically(tween(200)) { -it }` (slides down from above)
2. `AnimatedVisibility` exit: `shrinkVertically(tween(150)) + fadeOut(tween(150))`
3. Active `RestSeparator` flashes: `LaunchedEffect(Unit)` with `Animatable` background alpha 0.4f → 0.15f over 300ms

**Files:** `ActiveWorkoutScreen.kt`

### HOW TO QA IT

1. **Entrance animation**
   - Complete a set with a rest timer. Watch the `RestSeparator` row appear.
   - Verify it slides in smoothly from above (not a hard snap/pop).

2. **Background flash**
   - When the separator first appears, verify the row background briefly flashes brighter then settles to a muted tint. The flash should last ~300ms.

3. **Exit animation**
   - When the rest timer reaches 0 (or is skipped), verify the separator shrinks vertically and fades out smoothly (not a hard disappear).

4. **No animation when already visible**
   - Re-enter a screen that already has a rest separator visible. Verify no re-entrance animation plays.

---

## PHASE B — THEME_SPEC.md Alignment

---

### STEP 10 — MinimizedWorkoutBar Background Token Fix

**Status: Planned**

**What's wrong:** `MinimizedWorkoutBar` container uses `MaterialTheme.colorScheme.primary` as full background. Per `THEME_SPEC.md §6.4` it must be `surfaceVariant` with a 4dp `primary` left border accent.

**What to implement:**
1. `PowerMeNavigation.kt`: Change `MinimizedWorkoutBar` `Surface` color from `primary` to `surfaceVariant`
2. Add `Modifier.drawBehind { drawRect(color = primary, size = Size(4.dp.toPx(), size.height)) }` for the 4dp left border

**Files:** `PowerMeNavigation.kt`

### HOW TO QA IT

1. **Background is surfaceVariant, not primary**
   - Start a workout and minimize it. Look at the `MinimizedWorkoutBar` at the bottom of the screen.
   - Verify the bar background matches the app's surface variant color (dark grey/navy in dark mode) — NOT the bright indigo/primary color.

2. **Left border accent**
   - Verify a thin (~4dp) bright indigo/primary colored strip is visible on the **left edge** of the bar.

3. **Dark mode and light mode**
   - Toggle between dark and light theme (Settings → Appearance). Verify the bar background and left border look correct in both modes.

4. **Bar legibility**
   - Verify the exercise name, "Edit Mode" text (or timer), and tap target are clearly readable against the `surfaceVariant` background.

---

### STEP 11 — JetBrainsMono for Timer Displays

**Status: Planned**

**What's wrong:** Per `THEME_SPEC.md §4.3`, `JetBrainsMono` must be used exclusively for all timer displays. Current code may use default Barlow.

**What to implement:**
1. Audit all timer `Text` composables: elapsed timer in `ActiveWorkoutScreen`, rest countdown in `RestSeparator`, elapsed in `MinimizedWorkoutBar`, remaining time in `TimerControlsSheet`, Clocks tab (stopwatch/countdown/tabata/EMOM)
2. Add `fontFamily = JetBrainsMono` to each timer `Text`
3. Verify `JetBrainsMono` is defined in `Type.kt` (add if missing)
4. `HistoryCard` session duration (Step 14) must also use `JetBrainsMono`

**Files:** `ActiveWorkoutScreen.kt`, `PowerMeNavigation.kt`, `ClocksScreen.kt`, `Type.kt`

### HOW TO QA IT

1. **Active workout elapsed timer**
   - Start a workout. Look at the elapsed time in the workout header (e.g. `00:05:32`).
   - Verify the font is monospace (digits are evenly spaced, does not shift width as time changes).

2. **Rest countdown**
   - Complete a set. Look at the rest countdown in the `RestSeparator`.
   - Verify it uses monospace font.

3. **MinimizedWorkoutBar timer**
   - Minimize a workout. Look at the elapsed timer in the bar.
   - Verify it uses monospace font.

4. **TimerControlsSheet**
   - Open the `TimerControlsSheet` (tap a running rest timer). Look at the large mm:ss display.
   - Verify it uses monospace font.

5. **Clocks tab**
   - Navigate to the Clocks tab. Start the stopwatch and the countdown timer.
   - Verify both use monospace font.

6. **Visual quality check**
   - Watch a timer tick for 5–10 seconds. Verify the surrounding text does not shift or jump as digit widths change (this is the purpose of monospace for timers).

---

## PHASE C — HISTORY_ANALYTICS_SPEC.md Alignment

---

### STEP 12 — DB Migration v30: startTimeMs + endTimeMs

**Status: Planned**

> Note: v28 = `setTypesJson`, v29 = `setWeightsJson`/`setRepsJson`. This step is v30.

**What's wrong:** `workouts` table has no `startTimeMs` or `endTimeMs` columns. Duration cannot be computed for HistoryCard or WorkoutDetailScreen.

**What to implement:**
1. `PowerMeDatabase.kt`: bump version to 30. Add `MIGRATION_29_30` with two `ALTER TABLE workouts ADD COLUMN` statements
2. `Workout.kt` entity: add `val startTimeMs: Long = 0L` and `val endTimeMs: Long = 0L`
3. `WorkoutDao.kt`: add `updateWorkoutTimestamps(id, startTimeMs, endTimeMs)` query
4. `WorkoutViewModel.kt` `startWorkoutFromRoutine()`: record `startTimeMs = System.currentTimeMillis()`
5. `WorkoutViewModel.kt` `finishWorkout()`: record `endTimeMs = System.currentTimeMillis()`
6. `DB_UPGRADE.md`: add v30 entry
7. `CLAUDE.md`: update DB version

**Files:** `PowerMeDatabase.kt`, `DatabaseModule.kt`, `Workout.kt`, `WorkoutDao.kt`, `WorkoutViewModel.kt`, `DB_UPGRADE.md`, `CLAUDE.md`

### HOW TO QA IT

1. **App survives DB upgrade**
   - Build and install on a device/emulator that already has the app with v29 DB.
   - Verify the app launches without a crash. No `IllegalStateException` about missing migration.

2. **startTimeMs is recorded**
   - Start a new workout. Immediately finish it (0 sets is fine for this test).
   - Go to History → tap the session.
   - Verify the session has a non-zero start time (session header shows a time of day or date, not "0").

3. **endTimeMs is recorded**
   - Complete a workout that takes ~30 seconds. Go to History.
   - The `HistoryCard` for that session should show a duration > 0 (e.g. `00:32`).

4. **Duration is correct**
   - Start a workout, wait exactly 1 minute, finish. The HistoryCard duration should be close to `01:00`.

5. **Old sessions have no duration**
   - Workouts logged before this migration have `startTimeMs = 0` and `endTimeMs = 0`.
   - Verify the HistoryCard for those sessions does **not** show a duration (field hidden, not showing `00:00`).

---

### STEP 13 — HistoryScreen: Month/Year Grouping + Empty State

**Status: Planned**

**What's wrong:**
- History shows a flat list; needs calendar month/year section headers
- Empty state is absent or incorrect

**What to implement:**
1. `HistoryViewModel.kt`: group workouts by `(year, month)` into `List<HistoryGroup(label, workouts)>`
2. `HistoryScreen.kt`: render section headers (e.g. "March 2026") above each group
3. `HistoryScreen.kt`: empty state — centered `FitnessCenter` icon (64dp, `onSurfaceVariant`) + "No workouts logged yet." + "Start your first session from the Workouts tab."

**Files:** `HistoryViewModel.kt`, `HistoryScreen.kt`

### HOW TO QA IT

1. **Month/year section headers**
   - Go to the History tab (with 2+ sessions across different months).
   - Verify section headers appear, e.g. **"March 2026"** above the March sessions and **"February 2026"** above the February sessions.

2. **Newest month first**
   - Verify the most recent month/year group appears at the top of the list.

3. **Sessions within a month**
   - Within a month group, verify sessions are ordered newest-first.

4. **Empty state — no workouts**
   - Log out or use a fresh account with no workouts.
   - Go to the History tab. Verify:
     - A `FitnessCenter` icon (gym/dumbbell) appears centered, 64dp
     - Text: **"No workouts logged yet."**
     - Text: **"Start your first session from the Workouts tab."**

5. **Empty state disappears after first workout**
   - Complete a workout. Return to History. Verify the empty state is gone and the session appears grouped under the current month.

---

### STEP 14 — HistoryCard Full Redesign

**Status: Planned**

**What's wrong:** `HistoryCard` is missing spec-required fields: volume, working set count, exercise preview, PR badge, duration.

**What to implement:**
1. `HistoryViewModel.kt`: compute `hasPR`, `volume`, `workingSetCount` per session; bundle into `HistoryCardData`
2. `HistoryScreen.kt` `HistoryCard()`: redesign to show:
   - Workout name (`titleMedium`) + optional PR badge (`SuggestionChip`, gold bg, "🏆 PR")
   - Date (`bodySmall`, `EEE, MMM d`) · Duration (mm:ss, `JetBrainsMono`, hidden if `endTimeMs == 0`)
   - Volume + Working sets count
   - Exercise preview: first 3 names + "+N more"
3. `AnalyticsRepository.kt`: implement `hasPRInSession()` if stub

**Files:** `HistoryScreen.kt`, `HistoryViewModel.kt`, `AnalyticsRepository.kt`

### HOW TO QA IT

1. **Workout name and date**
   - Go to History. Verify each card shows the routine name and a formatted date like `"Wed, Mar 18"`.

2. **Duration (visible)**
   - For sessions completed after Step 12 (with timestamps), verify duration shows in monospace `mm:ss` format, e.g. `"04:23"`.

3. **Duration (hidden for old sessions)**
   - For sessions without timestamps, verify no duration field appears (not `"00:00"`).

4. **Volume**
   - Log a session: 3 sets × 80 kg × 10 reps = 2400 kg volume.
   - Verify the card shows the volume (e.g. `"2,400 kg"` or similar formatted value).

5. **Working sets count**
   - Log 1 Warm Up + 3 Work Sets, all completed. Verify the card shows `"3 sets"` (warm-up excluded).

6. **Exercise preview**
   - Log a session with 5 exercises. Verify the card shows the first 3 exercise names followed by `"+2 more"`.

7. **PR badge**
   - Set a personal record on any exercise (higher estimated 1RM than any previous session).
   - Verify that session's `HistoryCard` shows a gold `"🏆 PR"` chip.

8. **No PR badge by default**
   - Most cards should NOT show the PR badge (only when a PR was actually set).

---

### STEP 15 — WeeklyInsightsAnalyzer Carousel

**Status: Planned**

**What's wrong:** `WeeklyInsightsAnalyzer` exists but produces no visible UI in `HistoryScreen`.

**What to implement:**
1. `WeeklyInsightsAnalyzer.kt`: implement 4 metrics — total weekly volume Δ, workout count Δ, most-trained muscle group, top exercise Bayesian 1RM trend. Returns `List<InsightCard>` (empty list if < 7 days data)
2. `HistoryViewModel.kt`: expose `insightCards: StateFlow<List<InsightCard>>`
3. `HistoryScreen.kt`: horizontal `LazyRow` of insight cards at top of screen when list non-empty. Card bg `surfaceVariant`, 8dp corners, delta arrows: `↑` (primary) / `↓` (error) / `→` (onSurfaceVariant)

**Files:** `WeeklyInsightsAnalyzer.kt`, `HistoryViewModel.kt`, `HistoryScreen.kt`

### HOW TO QA IT

1. **Carousel hidden with < 7 days of data**
   - Use an account with fewer than 7 days of workout history.
   - Go to History. Verify no insight card carousel appears at the top.

2. **Carousel appears with ≥ 7 days of data**
   - With 7+ days of history, go to History. Verify a horizontal scrollable row of cards appears above the workout list.

3. **Volume delta card**
   - The first card should show something like: `"Volume"` with a number and a `↑` or `↓` arrow comparing this week vs last week.

4. **Workout count delta**
   - Another card shows the number of workouts this week vs last week (e.g. `"4 vs 3"` with `↑`).

5. **Most-trained muscle**
   - Another card shows the most-trained muscle group this week (e.g. `"Back"` with sessions count).

6. **Top exercise 1RM trend**
   - Another card shows the top exercise with its Bayesian 1RM trend.

7. **Cards omitted when insufficient data (not shown empty)**
   - If a metric cannot be computed (e.g. no 1RM data), that specific card should be absent — not displayed with empty/zero values.

8. **Delta arrows**
   - Positive delta → `↑` in primary color (indigo)
   - Negative delta → `↓` in error color (red)
   - No change → `→` in muted onSurfaceVariant color

---

### STEP 16 — WorkoutDetailScreen: Full Edit Flow + Session Deletion

**Status: Planned**

**What's wrong:** `WorkoutDetailScreen` is read-only; no edit flow or session deletion.

**What to implement:**
1. `WorkoutDetailViewModel.kt`: in-memory `pendingEdits: Map<setId, EditedSet>`; `save()` commits via `withTransaction {}`
2. `WorkoutDetailScreen.kt`: pencil icon in TopAppBar → edit mode; `WorkoutInputField` for weight/reps; Add Set / Delete Set
3. `WorkoutDetailScreen.kt`: overflow ⋮ → "Delete Session" (error color) → confirmation dialog → delete + `popBackStack()`

**Files:** `WorkoutDetailScreen.kt`, `WorkoutDetailViewModel.kt`

### HOW TO QA IT

1. **Pencil icon in TopAppBar**
   - Go to History → tap a session. Verify a pencil ✏️ icon appears in the top-right of the TopAppBar.

2. **Edit mode activates**
   - Tap the pencil. Verify weight and reps fields for each set become editable text fields (not read-only labels).

3. **Edit and save**
   - Change the weight of set 1 from 80 to 85. Tap **Save**. Verify the screen returns to read-only and shows `85` for set 1.

4. **Atomic save (no partial updates)**
   - Edit 3 sets simultaneously. Tap Save. Verify all 3 changes appear at once (not one by one).

5. **Cancel discards changes**
   - Enter edit mode. Change a value. Tap **Cancel** (or back). Verify original values are still shown.

6. **Add set**
   - In edit mode, tap **Add Set**. Verify a new empty set row appears at the bottom.

7. **Delete set**
   - In edit mode, delete a set. Verify it disappears. Save. Verify it's gone on reload.

8. **Delete session — access**
   - From the workout detail screen, tap the ⋮ overflow menu. Verify **"Delete Session"** appears in red/error color.

9. **Delete session — confirmation dialog**
   - Tap "Delete Session". Verify a confirmation dialog appears (e.g. "This will permanently delete this session.") with a **Delete** button in error color and a **Cancel** button.

10. **Delete session — confirmed**
    - Tap **Delete**. Verify the screen navigates back to History. Verify the session no longer appears in the list.

11. **Delete session — cancelled**
    - Open the dialog, tap **Cancel**. Verify the session still exists.

---

## PHASE D — NAVIGATION_SPEC.md Alignment

---

### STEP 17 — Nav Transition Animations

**Status: Planned**

**What's wrong:** No custom nav transitions are applied — all routes use default or no animation.

**What to implement:**
Per `NAVIGATION_SPEC.md §9`, add `enterTransition / exitTransition / popEnterTransition / popExitTransition` to every `composable()` in `NavHost`:
- **Tab switching:** `fadeIn(tween(200))` / `fadeOut(tween(200))`
- **Push screens** (settings, gym_setup, gym_inventory, workout_detail, template_builder, exercise_picker): `slideInHorizontally { fullWidth }` enter / `slideOutHorizontally { -fullWidth/3 }` exit
- **Push back (pop):** `slideInHorizontally { -fullWidth/3 }` enter / `slideOutHorizontally { fullWidth }` exit
- **Workout overlay:** `slideInVertically { fullHeight }` (tween 350ms, FastOutSlowInEasing) / `slideOutVertically { fullHeight }`

**Files:** `PowerMeNavigation.kt`

### HOW TO QA IT

1. **Tab switching — fade**
   - Tap between any two bottom nav tabs (e.g. Workouts → History).
   - Verify the transition is a smooth **fade** (~200ms). No slide. No hard cut.

2. **Push screen — slide in from right**
   - Tap **Settings** (or any push route: Gym Setup, Workout Detail, Template Builder, Exercise Picker).
   - Verify the screen slides in from the **right**.

3. **Push screen — parent slides left**
   - When the push screen slides in, the parent screen slides **left** (to ~1/3 of screen width) underneath.

4. **Pop — back swipe or back button**
   - From Settings, press back. Verify Settings slides out to the **right** and the parent slides back in from the **left**.

5. **Workout overlay — slides up**
   - Start a workout from the Workouts tab. Verify the workout screen slides up from the **bottom** with a slightly slower, spring-like 350ms animation.

6. **Workout dismiss — slides down**
   - Minimize the workout (back button). Verify the workout screen slides down and off the bottom.

7. **No animation regression**
   - Navigate through all 5 tabs, push and pop settings, open and close workout. Verify no screen flickers, hard cuts, or wrong-direction animations.

---

### STEP 18 — MainAppScaffold TopAppBar: Logo Image Only

**Status: Planned**

**What's wrong:** TopAppBar shows a text title. Per `NAVIGATION_SPEC.md §10.1` it must show the app logo image (`ic_powerme_logo_source`, 36dp height) — no text.

**What to implement:**
1. `PowerMeNavigation.kt` (or wherever `MainAppScaffold` TopAppBar is composed): replace text title with `Image(painter = painterResource(R.drawable.ic_powerme_logo_source), contentDescription = "PowerME", modifier = Modifier.height(36.dp))`
2. Verify `ic_powerme_logo_source` drawable exists (confirmed in untracked files)

**Files:** `PowerMeNavigation.kt`

### HOW TO QA IT

1. **Logo image in TopAppBar**
   - Open the app to any main tab. Verify the top-left of the TopAppBar shows the PowerME **logo image** (not the text "PowerME" or any other text title).

2. **No text title**
   - Verify there is no plain text title in the TopAppBar center or left.

3. **Correct size**
   - The logo should be approximately 36dp tall, with width proportional (aspect-ratio locked). It should not appear stretched or squished.

4. **Dark and light mode**
   - Toggle theme. Verify the logo is readable in both dark and light mode (it may need to be a vector with adaptive colors, or a separate light/dark drawable).

5. **Logo on all tabs**
   - Navigate through all 5 tabs. Verify the logo remains consistent across all of them.

---

## PHASE E — EXERCISES_SPEC.md Alignment (Deferred Sprint)

These are tracked but not yet sequenced into numbered steps. Implement after Phases A–D are complete.

| Gap | Spec Section | Notes |
|---|---|---|
| Favorites filter chip + sort-to-top | §4.5 | `isFavorite` toggle in `ExerciseDetailSheet`; chip in filter row |
| Gym Profile soft-lock | §4.6 | Missing equipment chip (amber), opacity 0.5 on locked exercises |
| MagicAddDialog full impl | §6.1 | Smart add: search, select, configure sets/reps/rest, add to workout/template |
| CreateExerciseSheet | §6.2 | Name, muscle group, equipment, type — DB insert + immediate availability |
| Custom exercise edit/delete | §6.3 | Long-press on custom exercise → edit/delete options |
| ExerciseDetailSheet History tab | §8.3 | `getCompletedSetsForExercise()` — table of past sets per session |
| ExerciseDetailSheet Records tab | §8.3 | `getMaxE1RMForExercise()` — all-time PR display |
| V2 schema fields | §V2 | `videoSnippet`, `actionCues`, `breathingMechanics`, etc. — DB migration v30+ |

---

## Deferred — Navigation (Post-MVP)

- **ViewModel anti-pattern refactor (§3.2):** Stop passing `WorkoutViewModel` directly to composables — hoist to state + lambdas.
- **Sign-out DB wipe (§2.2):** `popUpTo(0) { inclusive = true }` + `AppDatabase.clearAllTables()` on logout.
- **Process death rehydration (§2.3):** `WorkoutTimerService` notification → `START_WORKOUT_RESUME` intent → `rehydrateIfNeeded()`.

---

## Critical Files Summary

| File | Phases |
|---|---|
| `ActiveWorkoutScreen.kt` | A (Steps 3–9), B (Step 11) |
| `WorkoutViewModel.kt` | A (Steps 1–2), C (Step 12) |
| `PowerMeNavigation.kt` | A (Step 8), B (Step 10), D (Steps 17–18) |
| `PowerMeDatabase.kt` + `Workout.kt` | C (Step 12) |
| `HistoryScreen.kt` + `HistoryViewModel.kt` | C (Steps 13–15) |
| `WorkoutDetailScreen.kt` + `WorkoutDetailViewModel.kt` | C (Step 16) |
| `Type.kt` | B (Step 11) |
| `WeeklyInsightsAnalyzer.kt` | C (Step 15) |
| `AnalyticsRepository.kt` | C (Step 14) |

---

## Progress Tracker

| Step | Title | Status |
|---|---|---|
| 1 | PostWorkoutSummarySheet + Non-Blocking Routine Sync | ✅ APPROVED |
| 2 | Set Type DropdownMenu + Cascade Fix + Edit Mode Persistence | ✅ APPROVED |
| 3 | RPE Column: Wire Picker + RpePickerSheet | ⏳ QA pending |
| 4 | PREV Column: Ghost Label + Tappable Header Tooltip | ⏳ QA pending |
| 5 | WorkoutSetRow Polish: Touched Indicator, Keyboard Types, Touch Targets | ⏳ QA pending |
| 6 | DeletedSetClipboard + Snackbar Rules | ⏳ QA pending |
| 7 | TimerControlsSheet + RestTimePickerDialog | ⏳ QA pending |
| 8 | MinimizedWorkoutBar: Edit Mode Support + Edit Guard | ⏳ QA pending |
| 9 | Rest Separator Entrance Animation | ⏳ QA pending |
| 10 | MinimizedWorkoutBar Background Token Fix | 📋 Planned |
| 11 | JetBrainsMono for Timer Displays | 📋 Planned |
| 12 | DB Migration v30: startTimeMs + endTimeMs | 📋 Planned |
| 13 | HistoryScreen: Month/Year Grouping + Empty State | 📋 Planned |
| 14 | HistoryCard Full Redesign | 📋 Planned |
| 15 | WeeklyInsightsAnalyzer Carousel | 📋 Planned |
| 16 | WorkoutDetailScreen: Full Edit Flow + Session Deletion | 📋 Planned |
| 17 | Nav Transition Animations | 📋 Planned |
| 18 | MainAppScaffold TopAppBar: Logo Image Only | 📋 Planned |
