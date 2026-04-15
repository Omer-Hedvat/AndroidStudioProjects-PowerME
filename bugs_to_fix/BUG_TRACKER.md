# Bug Tracker

Single source of truth for bug status across sessions.

**Statuses:** `Open` | `In Progress` | `Completed` | `Wrapped`

- `Completed` — dev done, tests pass, summary file exists, ready for QA on device
- `Wrapped` — user QA'd and ran `/wrap_bugfix` (simplify + build + test + commit + push)

| Bug | Title | Status | Files Changed |
|-----|-------|--------|---------------|
| [BUG_start_workout_after_edit](BUG_start_workout_after_edit.md) | Start workout button broken after editing a routine | ✅ Fixed & Committed | `WorkoutViewModel.kt` |
| [BUG_youtube_links_still_rendered](BUG_youtube_links_still_rendered.md) | YouTube links still shown in ExerciseDetailSheet | ✅ Fixed & Committed | `ExercisesScreen.kt` |
| [BUG_superset_color_collision](BUG_superset_color_collision.md) | Superset colors collide when >4 supersets exist | ✅ Fixed & Committed | `Color.kt`, `ActiveWorkoutScreen.kt`, `TemplateBuilderScreen.kt`, `WorkoutDetailScreen.kt`, `WorkoutsScreen.kt`, `SupersetColorTest.kt` |
| [BUG_history_weight_decimal_places](BUG_history_weight_decimal_places.md) | History weights show only 1 decimal place | ✅ Fixed & Committed | `UnitConverter.kt`, `WorkoutDetailViewModel.kt`, `UnitConverterTest.kt` |
| [BUG_history_edit_unreadable_values](BUG_history_edit_unreadable_values.md) | Edit fields unreadable in history detail (dark bg, dark text) | ✅ Fixed & Committed | `WorkoutDetailScreen.kt` |
| [BUG_keyboard_pops_on_set_type_change](BUG_keyboard_pops_on_set_type_change.md) | Keyboard pops up when changing set type in active workout | ✅ Fixed & Committed | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |
| [BUG_duplicate_exercises](BUG_duplicate_exercises.md) | Duplicate exercises in master_exercises.json | ✅ Fixed & Committed | `master_exercises.json`, `MasterExerciseSeeder.kt`, `ExerciseDao.kt` |
| [BUG_edit_mode_false_discard_prompt](BUG_edit_mode_false_discard_prompt.md) | Discard Changes dialog shown even when nothing changed in edit mode | ✅ Wrapped | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt` |
| [BUG_detail_single_decimal](BUG_detail_single_decimal.md) | Workout detail (from summary) shows weights with only 1 decimal place | ✅ Fixed & Committed | `UnitConverter.kt`, `UnitConverterTest.kt` |
| [BUG_detail_double_edit_mode](BUG_detail_double_edit_mode.md) | Workout detail has two redundant edit modes; inner edit mode has unreadable values | 🔴 Open | — |
| [BUG_chart_crash_on_filter_change](BUG_chart_crash_on_filter_change.md) | App crashes when changing time filter in VolumeTrendCard or exercise in E1RMProgressionCard | ✅ Completed | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt` |
| [BUG_health_history_type_wrap](BUG_health_history_type_wrap.md) | Health History "Add" sheet — Type segmented buttons wrap mid-word | ✅ Wrapped | `HealthHistoryEntry.kt`, `ProfileScreen.kt` |

---

## How to Use This File

- **Start of session:** Find an `Open` bug, change its status to `In Progress`, note your session context.
- **Dev done:** Update status to `Completed`. Create `SUMMARY_<slug>.md`, fill Fix Notes in `BUG_<slug>.md`. Multiple bugs can sit in `Completed` waiting for batch QA.
- **After user QA:** Run `/wrap_bugfix <slug>` — it does simplify, build, test, commit, push, and flips status to `Wrapped`.
- **New bugs:** Add a row here AND create a `BUG_<slug>.md` file using the standard format in `CLAUDE.md`.
