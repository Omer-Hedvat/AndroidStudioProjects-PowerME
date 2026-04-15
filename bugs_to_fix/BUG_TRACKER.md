# Bug Tracker

Single source of truth for bug status across sessions.

**Statuses:** `Open` | `In Progress` | `Fixed (uncommitted)` | `Fixed & Committed`

| Bug | Title | Status | Files Changed |
|-----|-------|--------|---------------|
| [BUG_start_workout_after_edit](BUG_start_workout_after_edit.md) | Start workout button broken after editing a routine | ✅ Fixed & Committed | `WorkoutViewModel.kt` |
| [BUG_youtube_links_still_rendered](BUG_youtube_links_still_rendered.md) | YouTube links still shown in ExerciseDetailSheet | ✅ Fixed & Committed | `ExercisesScreen.kt` |
| [BUG_superset_color_collision](BUG_superset_color_collision.md) | Superset colors collide when >4 supersets exist | ✅ Fixed & Committed | `Color.kt`, `ActiveWorkoutScreen.kt`, `TemplateBuilderScreen.kt`, `WorkoutDetailScreen.kt`, `WorkoutsScreen.kt`, `SupersetColorTest.kt` |
| [BUG_history_weight_decimal_places](BUG_history_weight_decimal_places.md) | History weights show only 1 decimal place | ✅ Fixed & Committed | `UnitConverter.kt`, `WorkoutDetailViewModel.kt`, `UnitConverterTest.kt` |
| [BUG_history_edit_unreadable_values](BUG_history_edit_unreadable_values.md) | Edit fields unreadable in history detail (dark bg, dark text) | ✅ Fixed & Committed | `WorkoutDetailScreen.kt` |
| [BUG_keyboard_pops_on_set_type_change](BUG_keyboard_pops_on_set_type_change.md) | Keyboard pops up when changing set type in active workout | ✅ Fixed & Committed | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |
| [BUG_duplicate_exercises](BUG_duplicate_exercises.md) | Duplicate exercises in master_exercises.json | ✅ Fixed & Committed | `master_exercises.json`, `MasterExerciseSeeder.kt`, `ExerciseDao.kt` |
| [BUG_edit_mode_false_discard_prompt](BUG_edit_mode_false_discard_prompt.md) | Discard Changes dialog shown even when nothing changed in edit mode | ✅ Fixed & Committed | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt` |

---

## How to Use This File

- **Start of session:** Find an `Open` bug, change its status to `In Progress`, note your session context.
- **End of session:** Update status to `Fixed (uncommitted)` or `Fixed & Committed`. Add files changed.
- **After committing:** Bump status from `Fixed (uncommitted)` → `Fixed & Committed`.
- **New bugs:** Add a row here AND create a `BUG_<slug>.md` file using the standard format in `CLAUDE.md`.
