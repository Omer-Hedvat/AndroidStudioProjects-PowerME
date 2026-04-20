# EXERCISES_SPEC.md — PowerME Exercise Library

**Status:** ✅ Complete (v3.0 — April 2026)
**Domain:** Exercise Library · Browse Mode · Picker Mode · Exercise Detail Screen · Custom Creation · Gym Profile Soft-Lock

> **Living document.** Update this file whenever exercise library or search/filter behaviour changes.
> Cross-referenced by `CLAUDE.md`. Read this before touching `ExercisesScreen.kt`, `ExercisesViewModel.kt`, or any exercise-related composable.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Core Architecture & State](#2-core-architecture--state)
3. [Dual-Mode UI](#3-dual-mode-ui)
4. [Search, Filter & Gym Profile Engine](#4-search-filter--gym-profile-engine)
5. [Exercise Card](#5-exercise-card)
6. [Custom Exercise Creation](#6-custom-exercise-creation)
7. [Expanded Exercise Data Schema](#7-expanded-exercise-data-schema)
8. [Exercise Detail Screen](#8-exercise-detail-screen)
9. [Navigation & Route Map](#9-navigation--route-map)
10. [Technical Invariants](#10-technical-invariants)

---

## 1. Overview

The Exercise Library is a searchable, filterable catalog of 150+ exercises. It operates in two distinct modes:

| Mode | Route | Entry | Purpose |
|---|---|---|---|
| **Browse** | `exercises` | Bottom nav tab (Exercises, position 1) | View library, open detail sheet, view exercise animation |
| **Picker** | `exercise_picker` | `[+ ADD EXERCISE]` in active workout or TemplateBuilderScreen | Multi-select exercises to add to a routine or active workout |

All exercise lookups are **offline-first**. No network dependency exists for the core catalog. Exercise demo animations are bundled as Animated WebP assets loaded via Coil — zero network calls.

---

## 2. Core Architecture & State

### 2.1 ExercisesUiState

All UI-driving state lives in a single data class held by `ExercisesViewModel`. It survives configuration changes.

```kotlin
data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val activeMuscleFilters: Set<String> = emptySet(),
    val activeEquipmentFilters: Set<String> = emptySet(),
    val activeGymProfileId: Long? = null,
    val selectedExerciseIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val muscleGroupOptions: List<String> = emptyList(),
    val equipmentOptions: List<String> = emptyList()
)
```

**Field notes:**
- `activeMuscleFilters` / `activeEquipmentFilters` — currently named `selectedMuscles` / `selectedEquipment` in implementation; rename on next refactor.
- `activeGymProfileId` — `null` means no active gym profile (no soft-lock applied).
- `selectedExerciseIds` — populated only in Picker Mode. Must NOT be cleared when filters or search change.
- `muscleGroupOptions` / `equipmentOptions` — sourced from `SELECT DISTINCT` DB queries. Never hardcoded.

### 2.2 Data Flow

```
User Input (search / filter toggle / gym profile switch)
    ↓
ExercisesViewModel (normalizes, debounces 300 ms)
    ↓
ExerciseRepository.searchExercises() + getDistinctMuscleGroups() + getDistinctEquipmentTypes()
    ↓
ExerciseDao (Room — targets searchName column, LIMIT 25 for search)
    ↓
ExercisesUiState update → LazyColumn recomposes
```

### 2.3 Layer Responsibilities

| Layer | Class | Responsibility |
|---|---|---|
| UI | `ExercisesScreen` | Renders state, emits events |
| ViewModel | `ExercisesViewModel` | Holds state, applies filter/sort/soft-lock logic |
| Repository | `ExerciseRepository` | Normalizes query, wraps DAO calls |
| DAO | `ExerciseDao` | SQL queries targeting `searchName`, `muscleGroup`, `equipmentType` |

---

## 3. Dual-Mode UI

### 3.1 Browse Mode

**Entry:** Main bottom nav tab (Exercises).

**Behaviour:**
- Tapping an `ExerciseCard` navigates to `exercise_detail/{exerciseId}` — a full-screen detail page (see §8). The old `ExerciseDetailSheet` bottom sheet has been removed.
- No selection highlight or checkmark on cards.
- FAB visible: **Add Exercise** (opens MagicAddDialog). The Start Workout FAB has been removed from this tab.
- `selectedExerciseIds` is always empty in this mode.

### 3.2 Picker Mode

**Entry:** Navigating to route `exercise_picker` from TemplateBuilderScreen `[Add Exercises]` or ActiveWorkout `[+ ADD EXERCISE]` or Management Hub `[Replace Exercise]`.

**Behaviour:**
- **'Replace Exercise'** (from active workout) opens the full Exercise finder page with all filters and data.
- `ExercisesScreen` receives `pickerMode = true` and `onExercisesSelected: (List<Long>) -> Unit`.
- **Tap** on an `ExerciseCard` — toggles the exercise in `selectedExerciseIds`. Does NOT navigate to the detail screen.
- **Long press** on a custom `ExerciseCard` — shows delete confirmation dialog.
- Selected cards render with `primaryContainer` background tint and a checkmark icon overlay in the top-right corner.
- **FAB:** `[Add X Exercises]` (X = `selectedExerciseIds.size`) — visible only when `selectedExerciseIds.isNotEmpty()`. On tap: calls `onExercisesSelected(selectedExerciseIds.toList())`, sets `savedStateHandle["selected_exercises"]`, pops back stack.
- The Add Exercise FAB is **hidden** in picker mode.
- Changing search query or filter chips must NOT clear `selectedExerciseIds`.

**Return Contract:**
```kotlin
// Picker sets on confirm:
savedStateHandle.set("selected_exercises", ArrayList(selectedExerciseIds))
navController.popBackStack()

// Caller observes:
backEntry?.savedStateHandle?.getStateFlow<ArrayList<Long>?>("selected_exercises", null)

// Caller clears after consuming:
backEntry?.savedStateHandle?.remove<ArrayList<Long>>("selected_exercises")
```

---

## 4. Search, Filter & Gym Profile Engine

### 4.1 Search

- **Widget:** `OutlinedTextField` with trailing `✕` clear icon (visible when query is non-empty). Tapping clears field and resets results.
- **Tokenisation:** Query is split into whitespace-delimited tokens via `String.toSearchTokens()` (trim → lowercase → split on `\s+`). Each token is matched independently.
- **Word-order independent:** All tokens must match, but order doesn't matter. `"squat back"` finds `"Back Squat"`.
- **Synonym expansion:** Each token is expanded through `ExerciseSynonyms.expandToken()` before matching. Synonyms are phrase-level (e.g. `"military"` → `"overhead press"`) to avoid false positives from single-word expansion. Synonym dictionary lives in `data/database/ExerciseSynonyms.kt` — add entries there, no migration needed.
- **Matching per token:** An exercise matches a token if `exercise.name.contains(term, ignoreCase=true)` OR `exercise.searchName.contains(term.toSearchName())` for any term in the expanded set. The dual check handles both phrase synonyms (against `name`) and space-collapsed queries like `"facepull"` matching `"Face Pull"` (against `searchName`).
- **In-memory filtering:** `ExercisesViewModel` loads all exercises via `getAllExercises()` and filters in-memory via `applyFilters()`. No SQL search used for the main screen.
- **MagicAdd search path:** `ExerciseRepository.searchExercises()` also uses token + synonym expansion, fetches all via `getAllExercisesSync()`, filters in-memory, ranks prefix matches first, limits to 25. The legacy `ExerciseDao.searchExercises()` SQL method is no longer called.

### 4.2 Muscle Group Filter Chips

- **Source:** `ExerciseDao.getDistinctMuscleGroups()` → `SELECT DISTINCT muscleGroup FROM exercises ORDER BY muscleGroup`.
- Rendered as horizontally scrollable `FilterChip` row below the search field.
- An **"All"** chip at the start resets `activeMuscleFilters` to `emptySet()`. Shown selected when no filters are active.
- Multiple selections allowed. Active selections are AND-combined with equipment filters.
- Canonical group constants in `util/MuscleGroups.kt`: `Legs`, `Back`, `Core`, `Chest`, `Shoulders`, `Full Body`, `Arms`, `Cardio`. Chip label matches DB value exactly (e.g., `Core`, not `Abs`).

### 4.3 Equipment Filter Chips

- **Source:** `ExerciseDao.getDistinctEquipmentTypes()` → `SELECT DISTINCT equipmentType FROM exercises ORDER BY equipmentType ASC`.
- Chip ordering is applied in `ExercisesViewModel.sortEquipmentTypes()`: **Barbell, Dumbbell, Bench, Bodyweight** appear first (in that order), remaining types sorted A-Z.
- Same `FilterChip` row layout; `surfaceVariant` chip background.
- DB values are already in Title Case; no display-name mapping needed.

**Canonical equipment types (post v33 consolidation):**
`Ab Wheel`, `Barbell`, `Battle Ropes`, `Bench`, `Bodyweight`, `Cable`, `Dumbbell`, `EZ Bar`, `Jump Rope`, `Kettlebell`, `Landmine`, `Machine`, `Medicine Ball`, `Pull-up Bar`, `Resistance Band`, `Rings`, `Sled`, `Smith Machine`

**Eliminated in v33:** `Bench/Chair`, `Bench/Couch`, `Bench/Floor`, `Box/Bench`, `Couch/Bench` (merged → `Bench`); `Wall` (reassigned → `Bodyweight`).

- Comparison in ViewModel is **case-insensitive**: `exercise.equipmentType.trim().equals(filter, ignoreCase = true)`.
- An **"All"** chip resets `activeEquipmentFilters`.

### 4.4 Filter Logic (AND-Combined)

```
val tokens = searchQuery.toSearchTokens()   // word-order-independent token list

filtered = allExercises
    .filter { it.matchesSearchTokens(tokens) }  // token + synonym expansion; empty tokens = match all
    .filter { activeMuscleFilters.isEmpty() || it.muscleGroup in activeMuscleFilters }
    .filter { activeEquipmentFilters.isEmpty() || it.equipmentType.trim().equalsAny(activeEquipmentFilters, ignoreCase = true) }
```

Toggling any filter or changing the search query re-runs `applyFilters()`.

### 4.5 Favorites Filter

A **"Favorites"** `FilterChip` is rendered at the start of the muscle group filter chip row (before the "All" chip). When active, it narrows the list to `isFavorite == true` exercises only.

- Favorites filter is **independent** of muscle and equipment filters — it stacks with them (AND-combined with any active muscle/equipment filters).
- Regardless of whether the Favorites chip is active, favorited exercises are **always sorted to the top** of any search result or filtered list. The sort runs as a final pass after all other filtering.
- Sort key: `isFavorite DESC, name ASC`.
- The Favorites chip is never shown as "All" — it is a binary toggle: active or inactive.

### 4.6 Gym Profile Soft-Lock

When `activeGymProfileId` is non-null, the ViewModel loads the corresponding `GymProfile` and applies a **soft-lock sort** as a final pass after search/filter logic:

- Exercises whose `equipmentType` is **not available** in the active gym profile are pushed to the absolute bottom of the list.
- Soft-locked exercises receive:
  - **50% opacity** (`Modifier.alpha(0.5f)` on the card).
  - A **`[Missing Equipment]`** chip (`errorContainer` background) displayed below the equipment chip.
- Soft-locked exercises remain **fully tappable** — the `onClick` handler is never disabled.
- Equipment availability check uses `GymProfile.equipmentList`; matching is case-insensitive.
- If `activeGymProfileId == null` — no soft-lock, exercises sorted alphabetically.

---

## 5. Exercise Card

### 5.1 Layout

```
┌──────────────────────────────────────────────────────────────┐
│  [Type Badge]  Exercise Name                            ⭐   │
│                [Muscle Chip]  [Equipment Chip]  [Custom]     │
│                ⏱ 90s rest  •  📋 Form cues (when present)   │
│                [Missing Equipment] (soft-lock only)          │
└──────────────────────────────────────────────────────────────┘
```

- **Type Badge:** 38×38dp `Box` with `CircleShape`, `typeColor @ 0.15α` background, 20dp icon centered. Color + icon keyed on `ExerciseType`:
  - `STRENGTH` → `FitnessCenter` icon, `primary` color
  - `CARDIO` → `FlashOn` icon, `TimerGreen`
  - `TIMED` → `Timer` icon, `ReadinessAmber`
  - `PLYOMETRIC` → `FlashOn` icon, `Color(0xFFFF9800)` orange
  - `STRETCH` → `SelfImprovement` icon, `Color(0xFF4DD0E1)` teal
- **Name:** `fontSize = 15.sp`, `FontWeight.Bold`, `primary` color. Takes `weight(1f)`.
- **Favorite Star:** `Icons.Default.Star`, `ReadinessAmber` tint, 16dp, shown only when `exercise.isFavorite == true` at end of name row.
- **Muscle Tag:** compact inline `Surface(shape = extraSmall)` with `Text(fontSize = 11.sp)`, `primary @ 0.15α` background, `primary` text color.
- **Equipment Tag:** compact inline `Surface(shape = extraSmall)` with `Text(fontSize = 11.sp)`, `secondary @ 0.15α` background, `secondary` text color.
- **Custom Badge:** compact inline `Surface`, `tertiary @ 0.20α` background, `tertiary` text color. Label `"Custom"`. Shown only when `exercise.isCustom == true`.
- **Meta row:** Always shown. Contains:
  - Rest duration: `Timer` icon (11dp, `onSurfaceVariant @ 0.6α`) + `"${restDurationSeconds}s rest"` text (10sp, `onSurfaceVariant @ 0.7α`).
  - Form cues indicator: `Info` icon (11dp, `FormCuesGold @ 0.85α`) + `"Form cues"` text (10sp, `FormCuesGold @ 0.85α`). Shown only when `exercise.setupNotes?.isNotBlank() == true`.
- **Checkmark overlay:** Picker mode only, when `exercise.id in selectedExerciseIds`. Top-right corner `CheckCircle` icon, `primary` tint.
- **Missing Equipment tag:** Soft-lock only. `errorContainer` background chip.
- **Soft-lock opacity:** `Modifier.alpha(if (isSoftLocked) 0.5f else 1.0f)`.

### 5.2 Interactions

| Mode | Tap | Long Press |
|---|---|---|
| Browse | Navigates to `exercise_detail/{exerciseId}` | Deletes exercise (custom only) |
| Picker | Toggles `selectedExerciseIds` | Deletes exercise (custom only) |

### 5.3 Empty States

**No results (search + filter active):**
- Display centered text: `"No exercises found"` in `bodyMedium`, `onSurfaceVariant` color.
- Directly below: a persistent `[+] Create custom exercise "{searchQuery}"` `TextButton` (visible even when results are empty), which opens `MagicAddDialog` pre-filled with the current search query.
- Do not hide the search field or filter chips.

**Cold launch / catalog not yet seeded:**
- Display a `CircularProgressIndicator` centered in the exercise list area.
- Once `MasterExerciseSeeder` completes and the first DB query returns, the progress indicator is replaced by the list.

### 5.4 What Does NOT Appear on the Card
- Animation previews — exercise animations appear only in the detail sheet.
- Full form cues text — only in the detail sheet. The card shows a `"Form cues"` indicator label when `setupNotes` is non-blank, but the text itself is not rendered on the card.
- Set count or weight history — only in the detail sheet History tab.

---

## 6. Custom Exercise Creation

### 6.1 Path 1 — MagicAddDialog (Primary: Gemini Enrichment) ← Canonical Owner

> **Ownership:** `EXERCISES_SPEC.md §6.1` is the absolute source of truth for `MagicAddDialog`, its Gemini prompt contract, and its state machine. `WORKOUT_SPEC.md §15` cross-references here. If the prompt or state machine changes, update **only this file**.


Triggered from browse mode FAB `[+ Add Exercise]` or from ActiveWorkout `[+ ADD EXERCISE]`. A `Dialog` composable:

1. **Search field** — keystroke-triggered local DB search via `ExerciseRepository.searchExercises()`. Prefix-priority ranked, LIMIT 25.
2. **Select existing** — tapping a result calls `onExerciseAdded(exercise)` immediately. No network call.
3. **"Create new: '{query}'"** row — shown when results < 25 AND state is not Loading. Tapping triggers `MagicAddViewModel.searchExercise(name)` → Gemini enrichment.
4. **Loading state** — spinner; text field disabled.
5. **Result card** — enriched exercise preview (muscle group, equipment, rest timer, form cues).
6. **`[ADD]` button** — `MagicAddViewModel.saveExercise(exercise)` → `exerciseRepository.insertExercise(exercise)` with `isCustom = true`. Auto-dismisses on save.

**Gemini Prompt Contract:**
```
Exercise: "{exerciseName}"
Return ONLY valid JSON:
{
  "muscleGroup": "one of: Legs, Chest, Back, Shoulders, Arms, Core, Cardio, Full Body",
  "equipmentType": "one of: Barbell, Dumbbells, Cable, Machine, Bodyweight, Resistance Bands, Pull-up Bar, Kettlebell",
  "setupNotes": "3-4 sentence form cue",
  "restDurationSeconds": 90
}
If not a real exercise: {"error": "not found"}
```

**MagicAddUiState machine:**
- `Idle` → initial / reset
- `Loading` → Gemini call in-flight
- `Found(exercise)` → enriched result ready
- `Error(message)` → Gemini failed or not recognized
- `Saved` → exercise persisted, auto-dismiss

Full implementation detail: `WORKOUT_SPEC.md §15`.

### 6.2 Path 2 — CreateExerciseSheet (MVP Fallback: No-Internet)

A `ModalBottomSheet` opened from a `[+] Create custom exercise "{searchQuery}"` row at the bottom of MagicAddDialog results. Used when Gemini is unavailable.

**Fields:**

| Field | Widget | Notes |
|---|---|---|
| Name | `OutlinedTextField` | Pre-filled with `searchQuery`; non-empty required |
| Type | `ExposedDropdownMenu` | Strength, Cardio, Flexibility, Other |
| Primary Muscle | `ExposedDropdownMenu` | Options from `MuscleGroups` constants |
| Equipment | `ExposedDropdownMenu` | Options from `ExerciseDao.getDistinctEquipmentTypes()` |

**Action:** `[Save Exercise]` writes to Room with `isCustom = 1`. No Gemini call. Returns saved `Exercise`.

**Hard rules:**
- Name uniqueness is NOT enforced.
- All dropdown options are derived from the same DB sources as the filter chips.

### 6.3 Custom Exercise Edit / Delete

When `isCustom == true`, **long-pressing** an `ExerciseCard` in browse mode (instead of — or in addition to — opening the detail sheet) opens an `EditCustomExerciseSheet` (`ModalBottomSheet`).

**Fields (pre-filled from existing values):**

| Field | Widget |
|---|---|
| Name | `OutlinedTextField` |
| Primary Muscle | `ExposedDropdownMenu` (options from `MuscleGroups` constants) |
| Equipment | `ExposedDropdownMenu` (options from `ExerciseDao.getDistinctEquipmentTypes()`) |

**Actions:**
- `[Save Changes]` — writes updated fields to Room. `isCustom` remains `true`. `searchName` is re-computed via `toSearchName()` on save.
- `[Delete Exercise]` — error-color `TextButton`. Deletes the exercise row from DB. If the exercise is referenced in any `routine_exercises` or `workout_sets` row, those references must be handled (cascade delete or null-out FK) per the Room schema FK rules.

**Standard (non-custom) exercises:** Long-pressing a non-custom card opens the `ExerciseDetailSheet` only. No edit or delete option is shown. Standard exercises cannot be modified by the user.

---

## 7. Expanded Exercise Data Schema

### 7.1 Current Fields (Implemented)

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Auto-generated PK |
| `name` | `String` | Display name |
| `muscleGroup` | `String` | Primary muscle group (canonical value from `MuscleGroups.kt`) |
| `equipmentType` | `String` | Equipment required |
| `exerciseType` | `ExerciseType` | STRENGTH, CARDIO, etc. |
| `setupNotes` | `String?` | Form cues (displayed in Form Cues banner) |
| `youtubeVideoId` | `String?` | **Deprecated.** Superseded by offline Animated WebP assets. Retained for schema stability only — not rendered in UI. |
| `restDurationSeconds` | `Int` | Default rest duration (90s) |
| `barType` | `BarType` | STANDARD, EZ, etc. |
| `isFavorite` | `Boolean` | User-starred |
| `isCustom` | `Boolean` | User-created via MagicAdd or CreateExerciseSheet |
| `familyId` | `String?` | Exercise family grouping |
| `searchName` | `String` | Pre-normalized for search (DB v25+) |
| `instructionsUrl` | `String?` | **Deprecated.** Legacy field — not rendered in UI. |
| `committeeNotes` | `String?` | Internal annotation field |

### 7.2 Active Context Fields [V2 — Requires DB Migration]

Optimized for mid-workout, low-cognitive-load rendering.

| Field | Type | Notes |
|---|---|---|
| ~~`videoSnippetPath`~~ | — | **Removed.** Replaced by convention-based asset path: `assets/exercise_animations/{searchName}.webp`. No DB column needed. |
| `actionCues` | `String?` | Max 3 punchy physiological directives, newline-delimited |
| `breathingMechanics` | `String?` | Format: `Inhale: [Phase] \| Exhale: [Phase]` |

**`actionCues` format example:**
```
Retract scapulae before unracking.
Drive feet into floor throughout.
Bar touches sternum — no bounce.
```

**`breathingMechanics` example:** `Inhale: Descent (eccentric) | Exhale: Lockout (concentric peak)`

### 7.3 Static Context Fields [V2 — Requires DB Migration]

For catalog deep-dives and pre-workout study.

| Field | Type | Notes |
|---|---|---|
| `primaryMuscles` | `String?` | JSON array of primary muscles |
| `secondaryMuscles` | `String?` | JSON array of secondary muscles |
| `setupConfiguration` | `String?` | Equipment setup (e.g., `"Bench Angle: 30°"`) |
| `targetTempo` | `String?` | Tempo notation (e.g., `"3-1-1-0"`) |
| `executionSteps` | `String?` | 4-5 numbered sentences |
| `commonFaults` | `String?` | Top 2 mistakes, newline-delimited |
| `substituteExerciseIds` | `String?` | JSON array of `Long` IDs — 2-3 biomechanically equivalent exercises |

**`substituteExerciseIds` usage:** Detail sheet Info tab renders up to 3 substitute chips. Each has a `[Swap]` button replacing the current exercise in the active workout (or navigating to the substitute's detail sheet in browse mode).

**DB Migration Note:** All V2 fields require a new Room migration (v28+) via `ALTER TABLE exercises ADD COLUMN ... DEFAULT NULL`. All fields are nullable.

---

## 8. Exercise Detail Screen

Full-screen navigable route (`exercise_detail/{exerciseId}`) with a `Scaffold` + back button + **4-tab layout** (`HorizontalPager`). Replaced the v1 single-`LazyColumn` layout (which crashed on scroll due to 5 Vico charts + BodyOutlineCanvas composed simultaneously).

**File structure** (split from the original monolithic 1,222-line file):

| File | Contents |
|---|---|
| `ExerciseDetailScreen.kt` | Scaffold, pinned hero + header, `SecondaryTabRow`, `HorizontalPager` orchestration |
| `AboutTab.kt` | ABOUT tab: compact PR row, joints, form cues, training zones, warm-up, muscle activation, alternatives, notes |
| `HistoryTab.kt` | HISTORY tab: paginated workout history `LazyColumn` |
| `ChartsTab.kt` | CHARTS tab: time range chips + 5 `MiniTrendChart` composables |
| `RecordsTab.kt` | RECORDS tab: full PR grid, overload suggestion, lifetime stats |
| `DetailComponents.kt` | Shared: `SectionHeader`, `SectionDivider`, `EmptySectionPlaceholder`, `TagChip`, `PrStatCard`, `OverloadCard`, `AlternativeExerciseCard`, `WorkoutHistoryRow`, `dateFormat` |

**Crash fix:** `HorizontalPager` with `beyondViewportPageCount = 0` only composes the current page and its immediate neighbor. Charts (tab 2) and muscle activation (tab 0) are never in the composition tree simultaneously.

### 8.1 Trigger & Navigation

- **Browse mode:** Single tap on `ExerciseCard` → `navController.navigate("exercise_detail/$exerciseId")`.
- **Picker mode:** Long press still selects (no navigation).
- **Route:** `exercise_detail/{exerciseId}` with `NavType.LongType` argument.
- **Transitions:** `slideInHorizontally / slideOutHorizontally(tween(300))` — same pattern as `workout_summary`.
- **Back nav:** `onNavigateBack = { navController.popBackStack() }`.
- **Alternative tap:** Navigates to another `exercise_detail/{exerciseId}` — pushes new entry on back stack.
- **Workout history tap:** Navigates to `workout_summary/{workoutId}`.

### 8.2 Screen Layout

Pinned hero + header above tabs, 4-tab `HorizontalPager` below.

```
┌─────────────────────────────────┐
│  ← Exercise Name                │  TopAppBar
├─────────────────────────────────┤
│  [Exercise Animation]           │  Pinned hero (ContentScale.Fit)
├─────────────────────────────────┤
│  Tags · Last: date · N sessions │  Pinned header
├─────────────────────────────────┤
│  ABOUT  HISTORY  CHARTS  RECORDS│  SecondaryTabRow
├─────────────────────────────────┤
│  [Tab content]                  │  HorizontalPager
└─────────────────────────────────┘
```

**Cross-link:** Compact PR row on ABOUT tab → tap switches to RECORDS tab via `pagerState.animateScrollToPage(3)`.

### 8.3 Pinned: Hero Animation

- **Source:** `assets/exercise_animations/{searchName}.webp`. Convention-based — no DB column.
- **Loader:** Coil `SubcomposeAsyncImage`, `ImageDecoderDecoder` (API 28+) / `GifDecoder` (API 26–27).
- **Size:** `fillMaxWidth()`, `heightIn(max = 220.dp)`, `ContentScale.Fit`.
- **Fallback:** `Icons.Default.FitnessCenter` (48dp, `onSurfaceVariant`) on `surfaceVariant` background, 120dp height.

### 8.4 Pinned: Header

- `FlowRow` with muscle/equipment/type tags (`TagChip` with `Surface`).
- **Last performed:** Date from `TrendsDao.getLastPerformed()`.
- **Session count:** "X sessions" from `WorkoutSetDao.getExerciseSessionCount()`.
- **Injury warning banner:** `error.copy(alpha = 0.12f)` background, shown when any joint overlaps with active `MODERATE`/`SEVERE` health history entries.

### 8.5 Tab 1: ABOUT

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Compact PR summary | `CompactPrRow` | Hidden if no records |
| 2 | Joint indicators | `JointIndicatorsSection` | Hidden if no joints |
| 3 | Form cues | `FormCuesSection` | Hidden if null |
| 4 | Training zones | `SetRepZoneGuideSection` | Always rendered |
| 5 | Warm-up ramp | `WarmUpRampSection` | Hidden if bodyweight |
| 6 | Muscle activation | `MuscleActivationSection` | Hidden if no vectors |
| 7 | Alternatives | `AlternativeExercisesSection` | Hidden if < 2 |
| 8 | User notes | `UserNotesSection` | Placeholder text |

**Compact PR row:** Shows Best e1RM + Best Set in a single row with chevron. Tappable → switches to RECORDS tab.

**Joint indicators:** Primary → filled `AssistChip` (`primaryContainer`), secondary → outlined (`Color.Transparent`). Affected joints use `errorContainer`.

**Form cues:** Gold banner (`FormCuesGold`), expandable if > 120 chars.

**Training zones:** 3 `Surface` boxes (Strength 1–5, Hypertrophy 6–12, Endurance 13+). Active zone highlighted with `BorderStroke`.

**Warm-up ramp:** Table with 50%/70%/85%/100% of working weight. Hidden for bodyweight exercises.

**Muscle activation:** `BodyOutlineCanvas` with stress coefficient heatmap. Display-only.

**Alternatives:** `LazyRow` of 130dp `ElevatedCard` items, scored by family/muscle/equipment/type. Up to 6 shown.

**User notes:** `OutlinedTextField` (minLines = 3), debounced 500ms save.

### 8.6 Tab 2: HISTORY

Paginated `LazyColumn` (`PAGE_SIZE = 20`). Each row: date, routine name, set count, total volume. Stable keys (`workoutId_timestamp`). "Load more" button when `hasMoreHistory = true`. Tap → `onNavigateToWorkout(workoutId)`.

Empty state: centered `History` icon + "No workout history yet." text.

### 8.7 Tab 3: CHARTS

Time range filter chips (1M/3M/6M/1Y) pinned above the chart `LazyColumn`. 5 mini `CartesianChartHost` (Vico) charts, each 110dp height:

1. **e1RM (kg)** — estimated 1RM per session (Epley)
2. **Max Weight (kg)** — heaviest set per session
3. **Session Volume (kg)** — total session volume
4. **Best Set (kg×reps)** — best weight × reps per session
5. **RPE Trend** — avg RPE per session

Chart producers are ViewModel-owned `CartesianChartModelProducer` instances. Fallback: "Not enough data" placeholder (60dp `surfaceVariant` box) if < 2 data points.

### 8.8 Tab 4: RECORDS

| # | Section | Composable | Empty State |
|---|---------|-----------|-------------|
| 1 | Personal Records | `PersonalRecordsSection` (2×2 grid) | "No records yet" |
| 2 | Progressive Overload | `ProgressiveOverloadSection` | "No session data yet" |
| 3 | Lifetime stats | `LifetimeStatsSection` | "Not enough data" |

**Personal Records:** 2×2 grid of `PrStatCard`: Best e1RM (+ bodyweight ratio), Best Set, Best Volume, Most Reps — all with dates.

**Progressive Overload:** Sealed class `OverloadSuggestion` — `IncreaseReps` or `IncreaseWeight` card on `primaryContainer`.

**Lifetime stats:** Best Session Reps + Best Session Volume cards.

---

## 9. Navigation & Route Map

| Route | Entry | Composable | Mode |
|---|---|---|---|
| `exercises` | Bottom nav | `ExercisesScreen(pickerMode = false)` | Browse |
| `exercise_picker` | `navController.navigate("exercise_picker")` | `ExercisesScreen(pickerMode = true, onExercisesSelected = { ids -> ... })` | Picker |
| `exercise_detail/{exerciseId}` | Tap on `ExerciseCard` (browse) | `ExerciseDetailScreen` | Detail |

`ExercisesViewModel` is **screen-scoped** — a fresh instance per navigation to the exercises route. `ExerciseDetailViewModel` is **nav-entry-scoped** — scoped to the `exercise_detail` back-stack entry via Hilt + `SavedStateHandle`.

---

## 10. Technical Invariants

1. **Offline First** — Zero network dependency for exercise lookups. Exercise demo animations are bundled Animated WebP files loaded from `assets/exercise_animations/` via Coil (`coil-gif` decoder). No streaming, no ExoPlayer, no YouTube SDK.

2. **Debounced Search** — Strict 300 ms debounce via `debounce(300)` on a `StateFlow<String>` in `ExercisesViewModel`. Not in Compose.

3. **State Integrity in Picker Mode** — `selectedExerciseIds` must not be cleared when the user changes search query, toggles filters, or switches gym profiles. Only an explicit deselect tap or screen exit clears it.

4. **Exercise Detail is a Nav Route, Not a Sheet** — `ExerciseDetailScreen` is a full-screen route (`exercise_detail/{exerciseId}`), not a bottom sheet. The old `selectedExercise` screen-state variable has been removed. Navigation is handled via `onExerciseClick: (Long) -> Unit` callback on `ExercisesScreen`.

5. **Soft-Lock Is Sorting + Opacity Only** — Gym Profiles dictate list ordering and visual opacity. The `onClick` handler on `ExerciseCard` is never disabled.

6. **No Hardcoded Filter Lists** — `muscleGroupOptions` and `equipmentOptions` always sourced from `SELECT DISTINCT` DB queries.

7. **Search Normalization at ViewModel Boundary** — `toSearchName()` called once per query change, not per exercise in the filter loop.

8. **Equipment Matching Is Case-Insensitive** — DB may store mixed-case; comparisons use `.equals(..., ignoreCase = true)`.

9. **No Animation Preview on ExerciseCard** — Animated WebP demos appear only inside `ExerciseDetailScreen`. Cards show static data only.

10. **Coil WebP Dependency** — `io.coil-kt:coil-compose` + `io.coil-kt:coil-gif` are required dependencies for hero animations. No ExoPlayer or YouTube SDK in the dependency graph.

11. **APK Size Discipline** — All exercise animations must be Animated WebP files converted at quality 75. Target < 200 KB per animation. Deliver via Android App Bundle so only the assets for the user's device are downloaded — no hard total cap, but keep the full set under 50 MB before bundling.

---

## 11. Exercise Animation Asset Pipeline

**Status:** ✅ Complete. 240 animated WebP assets bundled in `app/src/main/assets/exercise_animations/`. UI renders them via Coil in `ExerciseDetailScreen` (§8.3); fallback placeholder shown for missing files.

### 11.1 Source

**ExerciseDB** — free, open exercise GIF library with 1,300+ exercises.

| Property | Value |
|---|---|
| URL | https://exercisedb.io |
| API | RapidAPI host `exercisedb.p.rapidapi.com` (free tier: 500 req/day) |
| Format | GIF, ~200–600 KB each |
| Coverage | ~1,300 exercises — superset of our 150+ library |
| License | Free for non-commercial use; check terms before shipping commercially |

**Alternative free source:** `github.com/wger-project/wger` — open-source fitness app with CC-licensed exercise images.

### 11.2 Naming Convention

Asset files must be named exactly `{exercise.searchName}.webp` where `searchName` is the pre-normalised field already stored in the DB:

```kotlin
// String.toSearchName() in Exercise.kt
fun String.toSearchName(): String = lowercase().replace(Regex("[\\s\\-()]"), "")

// Examples:
"Bench Press"        → "benchpress.webp"
"Romanian Deadlift"  → "romaniadeadlift.webp"   // ← note: 'n' not 'an'
"Lat Pulldown"       → "latpulldown.webp"
"Face Pull"          → "facepull.webp"
```

Place all files in: `app/src/main/assets/exercise_animations/`

The Coil loader resolves the path as `file:///android_asset/exercise_animations/{searchName}.webp`. If the file is missing, the fallback placeholder renders automatically (§8.2).

### 11.3 Conversion Pipeline

**Prerequisites (one-time setup):**
```bash
brew install ffmpeg          # macOS — handles GIF → Animated WebP
```

**Single file:**
```bash
ffmpeg -i input.gif \
  -vf "fps=12,scale=400:-1:flags=lanczos" \
  -loop 0 \
  -quality 75 \
  output.webp
```

**Batch conversion (all GIFs in a folder):**
```bash
for f in gifs/*.gif; do
  name=$(basename "$f" .gif)
  ffmpeg -i "$f" \
    -vf "fps=12,scale=400:-1:flags=lanczos" \
    -loop 0 -quality 75 \
    "assets/$name.webp" \
  done
```

**Parameter rationale:**

| Parameter | Value | Reason |
|---|---|---|
| `fps=12` | 12 frames/sec | Smooth enough for exercise demo; halving from 24fps cuts file size ~40% |
| `scale=400:-1` | 400px wide, height auto | Matches `fillMaxWidth()` at 200dp on hdpi screens; no upscaling waste |
| `lanczos` | Lanczos filter | Best quality downsample filter for motion |
| `loop=0` | Infinite loop | Required for Coil to loop the animation |
| `quality=75` | 75/100 | Good visual quality; tweak down to 65 if files are still too large |

### 11.4 ExerciseDB → PowerME Name Mapping

ExerciseDB uses full display names (e.g. `"Barbell Bench Press"`). Our `searchName` strips spaces and hyphens. The mapping step:

1. Download the ExerciseDB exercise list (one API call returns all ~1,300 names).
2. For each PowerME exercise, compute `searchName` and look for an ExerciseDB entry whose `name.toSearchName()` matches. Close matches can be resolved manually.
3. Download matching GIFs.
4. Run batch conversion from §11.3.
5. Rename output files to `{powermeName.toSearchName()}.webp`.

**Unmatched exercises** (custom or rare movements not in ExerciseDB): leave as missing — the fallback placeholder renders. Add to a `missing_animations.txt` tracking file so they can be sourced later.

### 11.5 Coverage Priority

Execute in this order to ship the most-used animations first:

| Priority | Exercises | Rationale |
|---|---|---|
| P0 | Squat, Deadlift, Bench Press, OHP, Row, Pull-up, Chin-up, Romanian Deadlift, Leg Press, Hip Thrust | Compound movements — used in nearly every programme |
| P1 | All remaining Chest, Back, Legs, Shoulders exercises | High frequency muscle groups |
| P2 | Arms, Core exercises | Isolation movements |
| P3 | Cardio, Full Body, Stretch exercises | Lower workout frequency |
| P4 | Custom user-created exercises | Cannot be sourced — always use fallback |

### 11.6 Quality Check Before Committing

After converting, verify each file:
```bash
# Check file size — flag anything over 200 KB
find app/src/main/assets/exercise_animations/ -name "*.webp" -size +200k

# Quick visual check — open in browser
open app/src/main/assets/exercise_animations/benchpress.webp
```

If a file exceeds 200 KB, re-run with `-quality 65` or `-vf "fps=10,scale=320:-1"`.

### 11.7 App Bundle Delivery

Exercise animations live in `src/main/assets/` which is included in the base APK by default. For a 150-exercise set at ~120 KB average, total ≈ 18 MB — acceptable in the base module.

If coverage grows beyond 200 exercises or average size creeps up, move animations to a **Play Asset Delivery** fast-follow pack:
```
app/src/main/play/asset-packs/exercise_animations/src/main/assets/exercise_animations/
```
This delivers assets after install, keeping the base APK lean. Not needed until the asset set exceeds ~40 MB uncompressed.
