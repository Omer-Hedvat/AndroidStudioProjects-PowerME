# History — Workout Summary Redesign Spec

| | |
|---|---|
| **Phase** | P2 (Step A — summary UI) · P4 (Step B — deep-link wiring) |
| **Status** | `done` (Step A) · `done` (Step B) |
| **Effort** | L (Step A) · S (Step B) |
| **Depends on** | Step B depends on Trends Step 3 (E1RMProgressionCard) |
| **Roadmap** | `ROADMAP.md §P2` and `§P4` |

**Related specs:** `HISTORY_ANALYTICS_SPEC.md`, `TRENDS_CHARTS_SPEC.md`

---

## Problem Statement

The current post-workout summary screen mirrors the active workout layout — a scrollable list of exercise cards with sets/reps/weight. This is the wrong mental model for a summary: the user already lived through that workout; what they want on review is **"how did I do?"**, not a replica of what they just did.

---

## Design Goals

1. **At-a-glance scorecard** — top-line stats visible without scrolling
2. **Per-exercise highlights** — best set, volume, PRs, trend vs last session
3. **Clickable movement cards** — tap an exercise → see its 1RM / volume trend (routes to Trends tab pre-filtered to that exercise)
4. **Emotionally resonant** — the summary should feel like a reward, not a spreadsheet

---

## New Layout

### Section 1 — Hero Header
Full-width card at the top:
- Date + time of workout (e.g. "Tuesday, 14 Apr · 09:32")
- Duration (e.g. "1h 5m")
- Total volume (e.g. "6,548 kg")
- Total sets (e.g. "29 sets")
- Estimated calories (from StatisticalEngine if available)
- **Workout name / routine name** prominently displayed
- Optional: PRs earned this session — small gold badge row: "🏆 3 PRs today"

### Section 2 — Session Rating (optional, swipeable)
A single RPE-style slider or 5-star tap: "How did this session feel?"  
Saves to `Workout.sessionRating` (new nullable `Int?` column).

### Section 3 — Exercise Summary Cards (replaces the set list)
One compact card per exercise (not expandable by default):

```
┌─────────────────────────────────────────────┐
│ Dumbbell Flat Bench Press         Chest     │
│                                             │
│  Best set:    40 kg × 8   (= 53.3 kg e1RM) │
│  Volume:      960 kg      ▲ +8% vs last     │
│  Sets:        3 / 3                         │
│  Avg RPE:     8.5          ✦ Golden zone    │
│                                             │
│  [View Trend →]                             │
└─────────────────────────────────────────────┘
```

- **Best set**: heaviest single set this session (weight × reps)
- **e1RM**: Epley estimate next to the best set
- **Volume delta**: compare to last session for same exercise; show ▲ / ▼ / — with colour
- **Avg RPE**: mean RPE across completed sets (only if RPE was logged for ≥ 50% of sets)
- **Golden zone badge**: shown if avg RPE 8–9 (ties into `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §1`)
- **[View Trend →]**: `TextButton` — navigates to Trends tab with `E1RMProgressionCard` pre-selected to this exercise. This is the future clickable movements feature.
- Supersets: show grouped with a coloured left-border (matching superset accent color)

### Section 4 — Muscle Group Summary
Horizontal bar chart (or `LinearProgressIndicator` row) showing muscle group distribution for this session. Simple — no Vico, just proportional bars using `muscleGroupColors` from `VicoChartHelpers`.

### Section 5 — Notes
Free text notes field for the session. Pre-populated if notes were entered during the workout.

---

## Navigation

- **Shown immediately after finishing an active workout** (replaces the current post-workout summary route — `onWorkoutFinished()` navigates here instead of back to Workouts tab)
- **Also shown from History tab** → tap any completed workout (same screen, same layout, read-only mode by default)
- "View Trend" button deep-links: `navController.navigate(Routes.TRENDS + "?exerciseId=$id")`
  - `TrendsViewModel.selectExercise(id)` is called on screen entry if `exerciseId` query param is present
  - `E1RMProgressionCard` auto-scrolls into view

---

## DB Changes Required

**Current DB is v38 — assign next available version at implementation time.**

```sql
ALTER TABLE workouts ADD COLUMN sessionRating INTEGER DEFAULT NULL;
```

- `Workout.sessionRating: Int?` — nullable 1–5 star rating logged on the summary screen
- `WorkoutDao` — add `updateSessionRating(workoutId: String, rating: Int)` query

## Files to Change

### New Files
| File | Purpose |
|---|---|
| `ui/history/WorkoutSummaryScreen.kt` | Full summary UI — hero header, exercise cards, muscle group bars, notes, session rating |
| `ui/history/WorkoutSummaryViewModel.kt` | Loads workout + sets, computes per-exercise deltas (vs last session), muscle group distribution |

### Modified Files
| File | Change |
|---|---|
| `data/database/Workout.kt` | Add `sessionRating: Int?` column |
| `data/database/WorkoutDao.kt` | Add `updateSessionRating(workoutId, rating)` |
| `data/database/PowerMeDatabase.kt` | Bump version, add migration |
| `di/DatabaseModule.kt` | No change if DAO already provided; verify |
| `ui/navigation/PowerMeNavigation.kt` | Add `Routes.WORKOUT_SUMMARY`; wire `WorkoutSummaryScreen`; accept optional `?exerciseId=` query param (Step B only) |
| `ui/workout/WorkoutViewModel.kt` | Update `onWorkoutFinished()` to navigate to `Routes.WORKOUT_SUMMARY` instead of popping to Workouts tab |
| `ui/history/HistoryScreen.kt` | Tap on completed workout navigates to `Routes.WORKOUT_SUMMARY` instead of existing detail screen |

---

## Trends Integration — Implementation Order

The **[View Trend →]** button is a two-step feature. Implement in separate steps:

### Step A — History Summary (this spec)
Build the full summary redesign. Include the **[View Trend →]** `TextButton` on each exercise card, but in this step it navigates to the Trends tab **without pre-selection** (just `navController.navigate(Routes.TRENDS)`). The button still provides value — it takes the user to the right tab. No routing changes needed yet.

### Step B — Deep-link wiring (after `E1RMProgressionCard` is built, Trends Step 3)
Once `E1RMProgressionCard` exists, wire the full deep-link:
1. `Routes.TRENDS` accepts optional `?exerciseId=` query parameter
2. `MetricsScreen` reads the arg on entry, calls `trendsViewModel.selectExercise(id)`
3. `E1RMProgressionCard` `LazyRow` scrolls to and highlights the pre-selected chip
4. Update the **[View Trend →]** call site to pass `?exerciseId=$id`

**Why this order:** The history summary is a high-value standalone improvement that should ship independently. Coupling it to Trends Step 3 delays both. Step A is shippable in days; Step B is a small wiring task that follows naturally once the chart card exists.

This deep-link contract is also documented in `TRENDS_CHARTS_SPEC.md §3`.

#### Step B — How to QA

1. Complete a workout (or open any past workout from History).
2. On the `WorkoutSummaryScreen`, tap **View Trend →** on any exercise card.
3. **Verify:** The Trends screen opens and the `LazyColumn` auto-scrolls so the **STRENGTH PROGRESSION** card is in view.
4. **Verify:** The exercise chip row inside the card auto-scrolls to and highlights the tapped exercise's chip.
5. **Verify:** The E1RM chart loads data for that exercise (or shows the "Log at least 2 sessions" empty state if the exercise has fewer than 2 logged sessions).
6. Navigate away to another tab, then return to Trends via the bottom nav — **verify** no unexpected auto-scroll occurs (the one-shot flag is consumed).

---

## What the Current Summary Does (to be replaced / kept)

| Current | Action |
|---|---|
| Scrollable exercise card list with full set table | Replace with compact per-exercise summary cards (§3 above) |
| Top stats bar (duration, volume, sets) | Keep — move into hero header (§1) |
| Edit mode (retroactive set editing) | Keep — accessible via Edit button in top app bar; opens existing `WorkoutDetailScreen` retroactive edit flow |

---

*Written April 2026.*
