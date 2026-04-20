# Exercise Detail Screen — Tab-Based Redesign (v2)

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `done` |
| **Effort** | L |
| **Depends on** | Exercise Detail Sheet Revision v1 ✅ |
| **Blocks** | — |
| **Touches** | `ui/exercises/detail/ExerciseDetailScreen.kt`, `ui/exercises/detail/ExerciseDetailViewModel.kt`, `ui/exercises/detail/AboutTab.kt` (new), `ui/exercises/detail/HistoryTab.kt` (new), `ui/exercises/detail/ChartsTab.kt` (new), `ui/exercises/detail/RecordsTab.kt` (new), `ui/exercises/detail/DetailComponents.kt` (new), `EXERCISES_SPEC.md` |

---

## Overview

The v1 Exercise Detail Screen shipped as a single 1,222-line file rendering 13 sections in one `LazyColumn`. It crashes when scrolling to the bottom (5 Vico `CartesianChartHost` instances + `BodyOutlineCanvas` + paginated history all composed simultaneously), and has visual overlaps (e.g., "RPE Trend" label colliding with "FRONT/BACK" body outline labels, training zones overlapping body outline).

This v2 redesign restructures the screen into **4 tabs** with a `HorizontalPager`, separating content into logical groups so each tab is independently scrollable, shorter, and focused. Inspired by the Strong app's "About / History / Charts / Records" tab model, adapted for PowerME's richer content (muscle activation, training zones, form cues, joints, warm-up ramp, alternatives, notes, RPE).

### Absorbed Tickets

- **`BUG_exercise_detail_scroll_crash`** (Open, P1) — crash fixed structurally by tab split
- **`BUG_exercise_joints_rework`** (Superseded) — already absorbed into v1 revision, stays superseded

---

## Problems Solved

1. **Crash on scroll** — the single-column layout composes all sections eagerly (5 Vico charts + BodyOutlineCanvas + paginated history). `HorizontalPager` with `beyondBoundsPageCount = 0` only composes the current page and its immediate neighbor — CHARTS tab (index 2) is never in the composition tree when viewing ABOUT tab (index 0).
2. **Text overlap** — "RPE Trend" header and "FRONT/BACK" body outline labels are now on separate tabs (CHARTS vs ABOUT). Training zones and body outline remain on ABOUT but are no longer sandwiched between charts and history, eliminating height conflicts.
3. **Navigation confusion** — deep content (history, charts, records) buried at the bottom of a 13-section scroll is not discoverable. Tabs give top-level navigation affordance.
4. **Animation cropping** — current `ContentScale.Crop` with `aspectRatio(16f/9f)` on 400×400 square source images aggressively crops top/bottom. Fix: `ContentScale.Fit` with a more proportional aspect ratio so the full animation is visible.

---

## Screen Layout

```
┌─────────────────────────────────┐
│  ← Exercise Name           ⋮   │  TopAppBar
├─────────────────────────────────┤
│  [Exercise Animation Image]     │  Pinned hero (ContentScale.Fit)
│                                 │
├─────────────────────────────────┤
│  🏋️ Chest · Dumbbell · Strength │  Pinned header (tags, last
│  ⏱ Last: Apr 19 · 91 sessions  │  performed, session count)
├─────────────────────────────────┤
│  ABOUT  HISTORY  CHARTS  RECORDS│  SecondaryTabRow
├─────────────────────────────────┤
│                                 │
│  [Tab content — HorizontalPager]│  Each page: own LazyColumn
│                                 │
└─────────────────────────────────┘
```

**Pinned above tabs:** Hero animation image + Header section (always visible, not scrollable).
**Below tabs:** `HorizontalPager` with 4 pages, swipeable left/right.

---

## Tab Structure

### Tab 1: ABOUT — "What is this exercise?"

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Compact PR summary | `CompactPrRow` (new) | Hidden if no records |
| 2 | Joint indicators | `JointIndicatorsSection` | Hidden if no joints |
| 3 | Form cues | `FormCuesSection` | Hidden if null |
| 4 | Training zones | `SetRepZoneGuideSection` | Always rendered |
| 5 | Warm-up ramp | `WarmUpRampSection` | Hidden if bodyweight |
| 6 | Muscle activation | `MuscleActivationSection` | Hidden if no vectors |
| 7 | Alternatives | `AlternativeExercisesSection` | Hidden if < 2 |
| 8 | User notes | `UserNotesSection` | Placeholder text |

**Cross-link:** Compact PR summary row shows Best e1RM + Best Set only. Entire row is tappable → `pagerState.animateScrollToPage(3)` to switch to RECORDS tab.

### Tab 2: HISTORY — "When and how did I perform this?"

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Workout history | `LazyColumn` of `WorkoutHistoryRow` items | "No workout history yet." + icon |

- Each session row: date, routine name, set count, total volume
- Paginated: `PAGE_SIZE = 20`, "Load more" button at bottom
- Each row is a separate `LazyColumn` `item` with stable key (`workoutId`) for proper recycling (performance fix vs v1 where all rows were in a single `item`)
- Tap row → `onNavigateToWorkout(workoutId)`

### Tab 3: CHARTS — "How am I trending?"

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Time range filter | `FilterChip` row (1M/3M/6M/1Y) | Always rendered |
| 2 | e1RM chart | `MiniTrendChart` | "Not enough data" |
| 3 | Max Weight chart | `MiniTrendChart` | "Not enough data" |
| 4 | Session Volume chart | `MiniTrendChart` | "Not enough data" |
| 5 | Best Set chart | `MiniTrendChart` | "Not enough data" |
| 6 | RPE Trend chart | `MiniTrendChart` | "Not enough data" |

- Time range chips are outside the `LazyColumn` (pinned at top of tab, always visible while scrolling charts)
- Chart producers remain ViewModel-owned `CartesianChartModelProducer` instances

### Tab 4: RECORDS — "What are my best numbers?"

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Personal Records | `PersonalRecordsSection` (2×2 grid) | "No records yet" |
| 2 | Progressive Overload | `ProgressiveOverloadSection` | "No session data yet" |
| 3 | Lifetime stats | `LifetimeStatsRow` (new) | Hidden if no records |

- Lifetime stats: Total Reps + Total Volume derived from `PersonalRecords`

---

## File Structure

Split the monolithic 1,222-line `ExerciseDetailScreen.kt` into 6 files:

| File | Contents | Approx Lines |
|---|---|---|
| `ExerciseDetailScreen.kt` | Scaffold, pinned hero + header, `SecondaryTabRow`, `HorizontalPager` orchestration | ~150 |
| `AboutTab.kt` | `AboutTabContent` composable: compact PR row, joints, form cues, zones, warm-up, muscle activation, alternatives, notes | ~350 |
| `HistoryTab.kt` | `HistoryTabContent` composable: paginated workout history `LazyColumn` with proper item keys, empty state | ~120 |
| `ChartsTab.kt` | `ChartsTabContent` composable: time range chips, 5 `MiniTrendChart` composables | ~200 |
| `RecordsTab.kt` | `RecordsTabContent` composable: full PR grid, overload suggestion, lifetime stats | ~200 |
| `DetailComponents.kt` | Shared composables: `SectionHeader`, `SectionDivider`, `EmptySectionPlaceholder`, `TagChip`, `PrStatCard`, `OverloadCard`, `AlternativeExerciseCard`, `WorkoutHistoryRow`, `dateFormat` | ~200 |

### Visibility

- Tab content composables: `internal` (used only within `detail` package)
- Shared components: `internal`
- `ExerciseDetailScreen`: `public` (registered in nav graph)

### ViewModel & Models — No Changes

- `ExerciseDetailViewModel.kt` — `loadAll()` continues loading everything concurrently on init. Tabs just show/hide content; no per-tab lazy loading needed.
- `ExerciseDetailModels.kt` — `ExerciseDetailUiState` unchanged. Tab selection is UI-local state managed by `rememberPagerState`, not ViewModel state.

---

## UI Implementation Details

### Tab Row

- Use Material 3 `SecondaryTabRow` (content-level tabs, not navigation-level)
- 4 `Tab` composables with text labels: "ABOUT", "HISTORY", "CHARTS", "RECORDS"
- Tab clicks: `coroutineScope.launch { pagerState.animateScrollToPage(index) }`

### HorizontalPager

- `beyondBoundsPageCount = 0` (default) — **critical for crash fix**
- Each page receives the full `uiState` + relevant callbacks
- Scroll state per tab is naturally preserved by `HorizontalPager` while page is in composition

### Hero Animation Fix

Change from:
```kotlin
contentScale = ContentScale.Crop,
modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
```
To:
```kotlin
contentScale = ContentScale.Fit,
modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
```
This shows the full 400×400 animation without aggressive cropping. `surfaceVariant` background fills any letterbox areas.

### Cross-Tab Navigation

```kotlin
val coroutineScope = rememberCoroutineScope()

// In AboutTabContent:
CompactPrRow(
    prs = uiState.personalRecords,
    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }
)
```

---

## Implementation Steps

1. **Start task** — `/start_task exercise_detail_tabs_v2`, update `BUG_exercise_detail_scroll_crash` to In Progress
2. **Fix hero animation cropping** — change `ContentScale` and aspect ratio
3. **Create `DetailComponents.kt`** — extract shared composables, change `private` → `internal`
4. **Create tab content files** — `AboutTab.kt`, `HistoryTab.kt`, `ChartsTab.kt`, `RecordsTab.kt`
5. **Rewrite `ExerciseDetailScreen.kt`** — orchestrator with pinned hero + header + tabs + pager
6. **Add compact PR summary row** on ABOUT tab with cross-link to RECORDS
7. **History tab performance fix** — each row as separate `LazyColumn` `item` with stable key
8. **Build + test + QA** — `:app:assembleDebug` + `:app:testDebugUnitTest` + emulator screenshots
9. **Update specs & close tickets** — `EXERCISES_SPEC.md` §8, this spec → `done`, bug tracker

---

## No New Dependencies

- `SecondaryTabRow` — Material 3 1.2.0+ (already in Compose BOM)
- `HorizontalPager` + `rememberPagerState` — `androidx.compose.foundation.pager` 1.4.0+ (already in Compose BOM)

---

## How to QA

1. Open any exercise → persistent header (name + tags) visible above tabs
2. Hero animation shows full exercise demonstration without cropping
3. Tabs: ABOUT / HISTORY / CHARTS / RECORDS all present and tappable
4. Swipe left/right between tabs — smooth `HorizontalPager` animation
5. **ABOUT tab:** compact PR row, joints, form cues, training zones, warm-up ramp, muscle activation, alternatives, notes all render correctly
6. **ABOUT tab:** tap compact PR row → switches to RECORDS tab
7. **HISTORY tab:** chronological session list, "Load more" after 20 items, tap row → `WorkoutDetailScreen`
8. **CHARTS tab:** 5 mini-charts for exercises with ≥2 sessions; time-range chips (1M/3M/6M/1Y) refresh data
9. **RECORDS tab:** Best Set, e1RM PR, Volume PR, Best Total Reps with dates; progressive overload if ≥1 session
10. Exercises with large history (50+ sessions) — **no crash** on any tab
11. Exercises with no history → HISTORY and RECORDS tabs show appropriate empty states
12. No text overlaps between any sections on any tab
13. Tab state (scroll position) preserved when switching between adjacent tabs
