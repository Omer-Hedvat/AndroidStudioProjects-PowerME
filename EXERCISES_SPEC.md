# EXERCISES_SPEC.md — PowerME Exercise Library

**Status:** ✅ Complete (v2.0 — March 2026)
**Domain:** Exercise Library · Browse Mode · Picker Mode · Exercise Detail · Custom Creation · Gym Profile Soft-Lock

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
8. [Exercise Detail Sheet](#8-exercise-detail-sheet)
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
- Tapping an `ExerciseCard` opens `ExerciseDetailSheet` as a `ModalBottomSheet` (`skipPartiallyExpanded = true`). Driven by `var selectedExercise by remember { mutableStateOf<Exercise?>(null) }` at the `ExercisesScreen` composable level (screen-scoped — see §10.4).
- No selection highlight or checkmark on cards.
- FAB visible: **Add Exercise** (opens MagicAddDialog). The Start Workout FAB has been removed from this tab.
- `selectedExerciseIds` is always empty in this mode.

### 3.2 Picker Mode

**Entry:** Navigating to route `exercise_picker` from TemplateBuilderScreen `[Add Exercises]` or ActiveWorkout `[+ ADD EXERCISE]` or Management Hub `[Replace Exercise]`.

**Behaviour:**
- **'Replace Exercise'** (from active workout) opens the full Exercise finder page with all filters and data.
- `ExercisesScreen` receives `pickerMode = true` and `onExercisesSelected: (List<Long>) -> Unit`.
- **Tap** on an `ExerciseCard` — toggles the exercise in `selectedExerciseIds`. Does NOT open the detail sheet.
- **Long press** on an `ExerciseCard` — opens `ExerciseDetailSheet` for inspection without altering selection.
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
┌─────────────────────────────────────────────┐
│  [✓] (picker only)   Exercise Name          │
│       [Muscle Chip]  [Equipment Chip]        │
│       [Missing Equipment] (soft-lock only)   │
└─────────────────────────────────────────────┘
```

- **Name:** `titleMedium`, `onSurface` color.
- **Muscle Tag:** compact inline `Surface(shape = RoundedCornerShape(4.dp))` with `Text(fontSize = 11.sp)`, `primary @ 0.15α` background, `primary` text color.
- **Equipment Tag:** compact inline `Surface(shape = RoundedCornerShape(4.dp))` with `Text(fontSize = 11.sp)`, `secondary @ 0.15α` background, `secondary` text color. Label uses `toEquipmentDisplayName()`.
- **Checkmark overlay:** Picker mode only, when `exercise.id in selectedExerciseIds`. Top-right corner. Background: `primaryContainer`, icon: `Icons.Default.Check`.
- **Missing Equipment tag:** Soft-lock only. `errorContainer` background chip.
- **Soft-lock opacity:** `Modifier.alpha(if (isSoftLocked) 0.5f else 1.0f)`.

### 5.2 Interactions

| Mode | Tap | Long Press |
|---|---|---|
| Browse | Opens `ExerciseDetailSheet` | — |
| Picker | Toggles `selectedExerciseIds` | Opens `ExerciseDetailSheet` |

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
- Form cues — only in the detail sheet.
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

## 8. Exercise Detail Sheet

### 8.1 Trigger

`ModalBottomSheet` (`skipPartiallyExpanded = true`) opened when:
- **Browse mode:** Single tap on `ExerciseCard`.
- **Picker mode:** Long press on `ExerciseCard`.

Sheet visibility driven by `var selectedExercise by remember { mutableStateOf<Exercise?>(null) }` at the `ExercisesScreen` composable level (screen-scoped — see §10.4).

### 8.2 Hero Animation

```
┌──────────────────────────────────────────────────┐
│                                                  │
│          [Looping Animated WebP]                 │
│          (aspect ratio: 4:3, rounded 12.dp)      │
│                                                  │
└──────────────────────────────────────────────────┘
```

- **Source:** Local asset at `assets/exercise_animations/{searchName}.webp`. Convention-based path — no DB column needed.
- **Loader:** Coil `AsyncImage` composable with an `ImageLoader` configured with `coil-gif` decoder (provides Animated WebP support). Zero network calls.
- **Looping:** Coil's GIF/WebP decoder loops by default. No manual replay logic needed.
- **Fallback:** If the asset file is missing, display a muted placeholder illustration (`Icons.Default.FitnessCenter` centered on a `surfaceVariant` background, 200.dp height).
- **Size constraint:** `fillMaxWidth()`, fixed height 200.dp, `ContentScale.Fit`, `clip(RoundedCornerShape(12.dp))`.

### 8.3 Title & Tags

```
┌──────────────────────────────────────────────────┐
│  Exercise Name (headlineMedium, primary)         │
│  [Muscle Chip]  [Equipment Chip]  ☆ (Favorite)  │
│                            [Replace] or [Add]    │
└──────────────────────────────────────────────────┘
```

- **Exercise Name:** `headlineMedium`, `MaterialTheme.colorScheme.primary`.
- **Muscle Chip:** `SuggestionChip`, `primaryContainer` background.
- **Equipment Chip:** `SuggestionChip`, `secondaryContainer` background. Label uses `toEquipmentDisplayName()`.
- **Favorite Toggle:** `IconButton` with `Icons.Default.Star` (filled, `primaryContainer`) / `Icons.Default.StarBorder`. Calls `ExercisesViewModel.toggleFavorite(exercise)` → `ExerciseDao.updateFavorite()`.
- **Contextual Action Button:** Shown only when opened from an active workout context. **Replace** or **Add** action. Hidden in browse-only and picker contexts.

### 8.4 Form Cues Banner

- Gold banner background (`Color(0xFF5A4D1A)`), pin icon, `setupNotes` text.
- Visible by default when `setupNotes != null`.
- Toggled via Info icon in the header area.

### 8.5 Recent History

Compact list of the last 3–5 completed sessions for this exercise.

```
┌──────────────────────────────────────────────────┐
│  Recent History                                  │
│  ─────────────────────────────────────────────── │
│  Mar 28  ·  3×8 @ 80 kg                         │
│  Mar 25  ·  4×6 @ 85 kg                         │
│  Mar 21  ·  3×10 @ 75 kg                        │
│                                                  │
│  [View All History →]                            │
└──────────────────────────────────────────────────┘
```

- **Data source:** `WorkoutSetDao` joining `workout_sets` → `workouts` (filter `isCompleted = 1`), grouped by `workoutId`, ordered by date descending, LIMIT 5.
- **Row format:** Date (`bodyMedium`, `onSurfaceVariant`) · Sets×Reps @ Weight (`bodyMedium`, `onSurface`).
- **"View All History":** `TextButton` navigating to a dedicated exercise history screen showing all sessions with per-set detail and e1RM calculations.
- **Empty state:** "No history yet" text in `bodySmall`, `onSurfaceVariant`.

### 8.6 Alternatives Card

```
┌──────────────────────────────────────────────────┐
│  Alternatives                   (surfaceVariant) │
│  ─────────────────────────────────────────────── │
│  [Incline DB Press]  [Cable Fly]  [Push-Up]     │
└──────────────────────────────────────────────────┘
```

- **Container:** `Card` with `surfaceVariant` background, 12.dp corner radius, 16.dp padding.
- **Data source:** `ExerciseDao` query: `SELECT * FROM exercises WHERE muscleGroup = :muscleGroup AND id != :currentId ORDER BY RANDOM() LIMIT 3`.
- **Rendering:** Each alternative as a clickable `AssistChip` showing the exercise name.
- **Tap behaviour:** Tapping an alternative chip updates `selectedExercise` to that exercise, swapping the entire detail sheet context in-place. The sheet does NOT close and reopen — it re-renders with the new exercise data.
- **UX rationale:** Critical for gym environments where equipment is occupied — users can quickly find an alternative without leaving the sheet.
- **Empty state:** If fewer than 2 alternatives exist for the muscle group, hide the entire card.

---

## 9. Navigation & Route Map

| Route | Entry | Composable | Mode |
|---|---|---|---|
| `exercises` | Bottom nav | `ExercisesScreen(pickerMode = false)` | Browse |
| `exercise_picker` | `navController.navigate("exercise_picker")` | `ExercisesScreen(pickerMode = true, onExercisesSelected = { ids -> ... })` | Picker |

`ExercisesViewModel` is **screen-scoped** — a fresh instance per navigation to the exercises route.

---

## 10. Technical Invariants

1. **Offline First** — Zero network dependency for exercise lookups. Exercise demo animations are bundled Animated WebP files loaded from `assets/exercise_animations/` via Coil (`coil-gif` decoder). No streaming, no ExoPlayer, no YouTube SDK.

2. **Debounced Search** — Strict 300 ms debounce via `debounce(300)` on a `StateFlow<String>` in `ExercisesViewModel`. Not in Compose.

3. **State Integrity in Picker Mode** — `selectedExerciseIds` must not be cleared when the user changes search query, toggles filters, or switches gym profiles. Only an explicit deselect tap or screen exit clears it.

4. **`selectedExercise` (Detail Sheet Trigger) is Screen-Scoped** — `var selectedExercise by remember { mutableStateOf<Exercise?>(null) }` at `ExercisesScreen` composable level. Do NOT hoist to ViewModel. (`selectedExerciseIds` is in ViewModel — these are different variables.)

5. **Soft-Lock Is Sorting + Opacity Only** — Gym Profiles dictate list ordering and visual opacity. The `onClick` handler on `ExerciseCard` is never disabled.

6. **No Hardcoded Filter Lists** — `muscleGroupOptions` and `equipmentOptions` always sourced from `SELECT DISTINCT` DB queries.

7. **Search Normalization at ViewModel Boundary** — `toSearchName()` called once per query change, not per exercise in the filter loop.

8. **Equipment Matching Is Case-Insensitive** — DB may store mixed-case; comparisons use `.equals(..., ignoreCase = true)`.

9. **No Animation Preview on ExerciseCard** — Animated WebP demos appear only inside `ExerciseDetailSheet`. Cards show static data only.

10. **Coil WebP Dependency** — `io.coil-kt:coil-compose` + `io.coil-kt:coil-gif` are required dependencies for hero animations. No ExoPlayer or YouTube SDK in the dependency graph.

11. **APK Size Discipline** — All exercise animations must be highly compressed Animated WebP files. Target < 150 KB per animation. Total animation asset budget < 25 MB to prevent APK bloat.
