# Exercise Library — Filter Dialog (Tune Icon + Centered Dialog)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | func_exercise_tags_seed ✅ |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExercisesViewModel.kt`, `ui/exercises/ExerciseFilterDialog.kt` |

---

## Overview

Replaces the three inline filter rows (Muscle Group, Equipment, Functional chip — ~230dp of vertical space) with a **filter icon** (`Icons.Default.Tune`) in the search bar's trailing slot. Tapping the icon opens a **centered Dialog** with all filters organized into three sections: Exercise Type, Muscle Group, Equipment.

This task **supersedes** two previously filed tasks:
- `EXERCISE_TYPE_FILTER_CHIPS_SPEC.md` (superseded)
- `EXERCISE_FILTER_COLLAPSE_SPEC.md` (superseded)

---

## Design Decisions

| Decision | Choice |
|---|---|
| Functional placement | Inside "Exercise Type" section (alongside STRENGTH/CARDIO/TIMED/PLYOMETRIC/STRETCH) |
| Active filter indicator | `BadgedBox` count badge on the Tune icon only — no summary chips row |
| Panel type | Centered Dialog (not ModalBottomSheet) |
| Default state | All filters deselected → show all exercises |
| Live re-render | List re-renders instantly behind the dialog on each chip toggle |

---

## Behaviour

- Tapping the Tune icon in the search bar opens the filter dialog.
- The dialog is **centered** on screen (`fillMaxWidth(0.92f)`, `fillMaxHeight(0.82f)`), scrollable vertically.
- Three sections, each with a "Select All" / "Deselect All" TextButton:
  1. **Exercise Type** — chips for STRENGTH, CARDIO, TIMED, PLYOMETRIC, STRETCH (ExerciseType enum) + a Functional chip (tag-based, TimerGreen)
  2. **Muscle Group** — chips from `muscleGroupFilters` (DB-driven), primary color
  3. **Equipment** — chips from `equipmentFilters` (DB-driven, priority-sorted), secondary color
- "Clear All Filters" TextButton at the bottom in `colorScheme.error`.
- Tapping outside the dialog (backdrop) or the X button dismisses without clearing filters.
- Badge count on Tune icon reflects total active filter count (`selectedTypes.size + selectedMuscles.size + selectedEquipment.size + if (functionalFilter) 1 else 0`).
- Filter state is session-local (not persisted).

---

## UI Changes

- **Search bar trailing icon**: Always-visible `IconButton` with `Icons.Default.Tune`; `BadgedBox` shows numeric badge when `activeFilterCount > 0`. Clear (X) button still appears when search query is non-empty.
- **ExerciseFilterDialog.kt** (new file): Dialog composable with scrollable Column, three `FilterSectionHeader` + `FlowRow<FilterChip>` sections.
- **ExercisesScreen.kt**: Inline filter block removed (previously ~135 lines of muscle/equipment/functional rows); dialog invocation added.

---

## Files Touched

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — added `selectedTypes: Set<ExerciseType>`, `showFilterDialog: Boolean`, `activeFilterCount: Int`, type filter toggle + select/deselect/clear handlers; updated `applyFilters()` to AND-compose type predicate
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — removed inline filter rows; added Tune icon + BadgedBox to search bar; added dialog invocation; changed `exerciseTypeIcon`/`exerciseTypeColor` to `internal`
- `app/src/main/java/com/powerme/app/ui/exercises/ExerciseFilterDialog.kt` — new file: `ExerciseFilterDialog` composable + private `FilterSectionHeader`
- `app/src/test/java/com/powerme/app/ui/exercises/ExerciseFilterTest.kt` — added exerciseType predicate tests + `activeFilterCount` tests

---

## How to QA

1. Open Exercise Library — no inline filter rows visible; search bar shows Tune icon with no badge.
2. Tap Tune icon → centered dialog appears with 3 sections (Exercise Type / Muscle Group / Equipment).
3. Toggle individual chips → list re-renders live behind the dialog.
4. Tap "Select All" in Muscle Group → all muscle chips selected; "Deselect All" → chips cleared.
5. Select Functional chip in Exercise Type section → only functional-tagged exercises show.
6. Badge count on Tune icon shows total active filter count after closing dialog.
7. Tap "Clear All Filters" → all filters reset, badge disappears.
8. Tap X or backdrop → dialog closes without clearing filters.
9. Combine type chip + equipment chip → AND logic applies (both conditions must match).
