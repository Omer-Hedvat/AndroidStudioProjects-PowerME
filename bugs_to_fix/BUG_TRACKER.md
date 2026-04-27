# Bug Tracker

Single source of truth for **active** bug status. Wrapped bugs are in `ARCHIVE.md`.

**Statuses:** `Open` | `In Progress` | `Completed` | `Wrapped` | `Pending` | `Blocked`

- `Completed` — dev done, tests pass, summary file exists, ready for QA on device
- `Wrapped` — user QA'd and ran `/wrap_task` (build + test + commit + push) → move to `ARCHIVE.md`
- `Pending` — intentionally deferred (external dep, future milestone, or parked)
- `Blocked` — waiting on external platform resolution (not PowerME code)

| Bug | Title | Status | Severity | Depends on | Blocks | Files Changed |
|-----|-------|--------|----------|------------|--------|---------------|
| [BUG_body_composition_ignores_hc](BUG_body_composition_ignores_hc.md) | BodyCompositionCard shows empty state — does not pull weight/body fat from HC | 🔴 Blocked (external HC issue) | P1 | — | — | `HealthConnectManager.kt`, `TrendsRepository.kt` |
| [BUG_hc_extended_reads_no_data](BUG_hc_extended_reads_no_data.md) | All HC data missing — full Health Connect sync broken | 🔴 Blocked (external HC issue) | P0 | — | — | multiple HC + Trends files |
| [BUG_nuke_hc_debug_cleanup](BUG_nuke_hc_debug_cleanup.md) | Remove HC nuke debug tooling (temporary code, must not ship) | ⏸️ Pending | P1 | BUG_write_workout_session_oversized ✅, HC lockup resolved | — | `HealthConnectManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` |
| [BUG_post_workout_rpe_redesign](BUG_post_workout_rpe_redesign.md) | Post-workout RPE auto-pops instead of offering an "Add RPE" button with RPE scale titles | 🔵 Open | P2 | — | — | — |
| [BUG_time_based_exercise_column_header](BUG_time_based_exercise_column_header.md) | Time-based exercise column header shows 'Reps' instead of 'Time' | ✅ Wrapped | P2 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutRepository.kt` |
| [BUG_func_routine_preview_no_block_ui](BUG_func_routine_preview_no_block_ui.md) | Routine preview and edit mode show flat exercise list instead of functional block structure | ✅ Wrapped | P2 | func_active_block_card_ui ✅, func_block_card_layout ✅ | — | `WorkoutsScreen.kt`, `WorkoutsViewModel.kt`, `WorkoutViewModel.kt` |
| [BUG_active_workout_kebab_nav_overlap](BUG_active_workout_kebab_nav_overlap.md) | Exercise options kebab menu overlaps Android navigation bar in active workout | ✅ Completed | P2 | — | — | `ActiveWorkoutScreen.kt` |
| [BUG_func_template_builder_weight_reorder](BUG_func_template_builder_weight_reorder.md) | Functional block template builder — no weight input + blocks not reorderable | ✅ Completed | P1 | — | — | `TemplateBuilderScreen.kt`, `TemplateBuilderViewModel.kt`, `RoutineExerciseDao.kt` |
| [BUG_active_workout_order_mismatch](BUG_active_workout_order_mismatch.md) | Active workout exercise order does not match organize workout order | ✅ Completed | P1 | — | — | `WorkoutViewModel.kt` |
| [BUG_routine_preview_no_scroll](BUG_routine_preview_no_scroll.md) | Routine preview screen cannot scroll — Start Workout button unreachable | ✅ Wrapped | P0 | — | — | `WorkoutsScreen.kt` |
| [BUG_func_picker_duplicate_exercises](BUG_func_picker_duplicate_exercises.md) | Exercise picker from functional training shows duplicate exercises (reps + time variants) | 🔵 Open | P2 | — | — | — |
| [BUG_func_block_card_header_duplicate_type](BUG_func_block_card_header_duplicate_type.md) | Functional block card header: type badge + auto-name both show the type — duplicate info, wasted space | 🔵 Open | P2 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutsScreen.kt` |


---

## How to Use This File

- **Start of session:** Find an `Open` bug, run `/start_task <slug>` to mark In Progress.
- **Dev done:** Update status to `Completed`. Create `SUMMARY_<slug>.md`, fill Fix Notes in `BUG_<slug>.md`.
- **After user QA:** Run `/wrap_task <slug>` — builds, tests, commits, pushes, then **move the row to `ARCHIVE.md`**.
- **Archived bugs:** Search with `grep -i "<keyword>" bugs_to_fix/ARCHIVE.md`
