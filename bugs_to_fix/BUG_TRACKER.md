# Bug Tracker

Single source of truth for bug status across sessions.

**Statuses:** `Open` | `In Progress` | `Completed` | `Wrapped`

- `Completed` — dev done, tests pass, summary file exists, ready for QA on device
- `Wrapped` — user QA'd and ran `/wrap_task` (simplify + build + test + commit + push)

| Bug | Title | Status | Severity | Depends on | Blocks | Files Changed |
|-----|-------|--------|----------|------------|--------|---------------|
| [BUG_start_workout_after_edit](BUG_start_workout_after_edit.md) | Start workout button broken after editing a routine | ✅ Fixed & Committed | P0 | — | — | `WorkoutViewModel.kt` |
| [BUG_youtube_links_still_rendered](BUG_youtube_links_still_rendered.md) | YouTube links still shown in ExerciseDetailSheet | ✅ Fixed & Committed | P2 | — | — | `ExercisesScreen.kt` |
| [BUG_superset_color_collision](BUG_superset_color_collision.md) | Superset colors collide when >4 supersets exist | ✅ Fixed & Committed | P2 | — | — | `Color.kt`, `ActiveWorkoutScreen.kt`, `TemplateBuilderScreen.kt`, `WorkoutDetailScreen.kt`, `WorkoutsScreen.kt`, `SupersetColorTest.kt` |
| [BUG_history_weight_decimal_places](BUG_history_weight_decimal_places.md) | History weights show only 1 decimal place | ✅ Fixed & Committed | P2 | — | — | `UnitConverter.kt`, `WorkoutDetailViewModel.kt`, `UnitConverterTest.kt` |
| [BUG_history_edit_unreadable_values](BUG_history_edit_unreadable_values.md) | Edit fields unreadable in history detail (dark bg, dark text) | ✅ Fixed & Committed | P1 | — | — | `WorkoutDetailScreen.kt` |
| [BUG_keyboard_pops_on_set_type_change](BUG_keyboard_pops_on_set_type_change.md) | Keyboard pops up when changing set type in active workout | ✅ Fixed & Committed | P1 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |
| [BUG_duplicate_exercises](BUG_duplicate_exercises.md) | Duplicate exercises in master_exercises.json | ✅ Fixed & Committed | P1 | — | — | `master_exercises.json`, `MasterExerciseSeeder.kt`, `ExerciseDao.kt` |
| [BUG_edit_mode_false_discard_prompt](BUG_edit_mode_false_discard_prompt.md) | Discard Changes dialog shown even when nothing changed in edit mode | ✅ Wrapped | P2 | — | — | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt` |
| [BUG_detail_single_decimal](BUG_detail_single_decimal.md) | Workout detail (from summary) shows weights with only 1 decimal place | ✅ Wrapped | P3 | — | — | `UnitConverter.kt`, `UnitConverterTest.kt` |
| [BUG_detail_double_edit_mode](BUG_detail_double_edit_mode.md) | Workout detail has two redundant edit modes; inner edit mode has unreadable values | ✅ Wrapped | P1 | — | — | `WorkoutDetailViewModel.kt`, `WorkoutDetailScreen.kt`, `WorkoutDetailViewModelTest.kt` |
| [BUG_chart_crash_on_filter_change](BUG_chart_crash_on_filter_change.md) | App crashes when changing time filter in VolumeTrendCard or exercise in E1RMProgressionCard | ✅ Wrapped | P0 | — | — | `TrendsViewModel.kt`, `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MetricsScreen.kt` |
| [BUG_health_history_type_wrap](BUG_health_history_type_wrap.md) | Health History "Add" sheet — Type segmented buttons wrap mid-word | ✅ Wrapped | P2 | — | — | `HealthHistoryEntry.kt`, `ProfileScreen.kt` |
| [BUG_rest_timer_leaks_after_workout](BUG_rest_timer_leaks_after_workout.md) | Rest timer keeps running after workout ends; previous timer not cancelled on new start | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_timed_set_row_crowded](BUG_timed_set_row_crowded.md) | Timed set row layout is crowded in IDLE state (dual action buttons squished) | ✅ Wrapped | P2 | — | — | `ActiveWorkoutScreen.kt`, `WORKOUT_SPEC.md` |
| [BUG_timed_set_no_setup_countdown](BUG_timed_set_no_setup_countdown.md) | No setup countdown before timed exercise timer starts | ✅ Wrapped | P2 | — | — | `AppSettingsDataStore.kt`, `FirestoreSyncManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `Color.kt`, `WorkoutViewModelTest.kt`, `SettingsViewModelHealthConnectTest.kt` |
| [BUG_post_workout_triple_sync_prompt](BUG_post_workout_triple_sync_prompt.md) | Post-workout routine sync prompt appears 3 times (PostWorkoutSummarySheet + WorkoutSummaryScreen both show it) | ✅ Completed | P1 | — | — | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `WorkoutSummaryScreen.kt`, `WorkoutViewModelTest.kt` |
| [BUG_history_slow_first_load](BUG_history_slow_first_load.md) | History tab takes ~3 seconds to show records on first open | ✅ Wrapped | P1 | — | — | `WorkoutDao.kt`, `WorkoutSet.kt`, `Workout.kt`, `PowerMeDatabase.kt`, `DatabaseModule.kt`, `HistoryViewModel.kt` |
| [BUG_leftover_ui_elements](BUG_leftover_ui_elements.md) | Hebrew text on login screen + Boaz Insights card in Trends tab | ✅ Wrapped | P1 | — | — | `WelcomeScreen.kt`, `MetricsScreen.kt`, `MetricsViewModel.kt`, `MetricsViewModelBodyVitalsTest.kt` |
| [BUG_hc_offer_skipped_on_login](BUG_hc_offer_skipped_on_login.md) | Existing users skip HC connect screen on login — navigates directly to Workouts | ✅ Wrapped | P1 | — | — | `AppSettingsDataStore.kt`, `AuthViewModel.kt`, `WelcomeScreen.kt`, `PowerMeNavigation.kt`, `HcOfferScreen.kt` (new), `HcOfferViewModel.kt` (new) |
| [BUG_trends_scroll_crash](BUG_trends_scroll_crash.md) | Trends tab crashes when scrolling to VolumeTrendCard — likely MuscleGroupVolumeCard producer race | ✅ Wrapped | P0 | — | — | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `MetricsScreen.kt` |
| [BUG_timed_exercise_row_columns](BUG_timed_exercise_row_columns.md) | Timed exercise row: missing PREV column, spurious RPE column, orphaned '-' box with no header | ✅ Completed | P1 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |
| [BUG_warning_beep_gap_too_wide](BUG_warning_beep_gap_too_wide.md) | WARNING double-beep has 300ms gap — too wide, sounds like two separate events | ✅ Wrapped | P3 | — | — | `RestTimerNotifier.kt`, `TOOLS_SPEC.md` |
| [BUG_toggle_colors_inconsistent](BUG_toggle_colors_inconsistent.md) | Switch/Toggle colors inconsistent across screens | ✅ Wrapped | P3 | — | — | `SettingsScreen.kt`, `ActiveWorkoutScreen.kt` |
| [BUG_trends_card_crash_on_open](BUG_trends_card_crash_on_open.md) | Trends card crashes when opened | ✅ Completed | P0 | — | — | — |
| [BUG_next_field_not_selected_on_confirm](BUG_next_field_not_selected_on_confirm.md) | Next input field not auto-selected when confirming with checkmark | ✅ Completed | P2 | — | — | `WorkoutInputField.kt` |
| [BUG_history_edit_unreadable_values_v2](BUG_history_edit_unreadable_values_v2.md) | History edit values unreadable (regression) | ✅ Completed | P1 | — | — | `WorkoutDetailScreen.kt` |
| [BUG_prev_session_rpe_missing](BUG_prev_session_rpe_missing.md) | RPE missing from previous session values in active workout | ✅ Completed | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_fitness_level_not_persisted](BUG_fitness_level_not_persisted.md) | Fitness level selection doesn't persist after app restart | ✅ Wrapped | P1 | — | — | `FirestoreSyncManager.kt`, `ProfileViewModelPersonalInfoTest.kt` |
| [BUG_body_metrics_not_populated_from_hc](BUG_body_metrics_not_populated_from_hc.md) | Body Metrics weight/body fat not populated from Health Connect | ✅ Wrapped | P1 | — | — | `ProfileViewModel.kt`, `ProfileViewModelPersonalInfoTest.kt` |
| [BUG_post_workout_note_no_save](BUG_post_workout_note_no_save.md) | Post-workout notes field has no Save button | ✅ Completed | P1 | — | — | `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModelTest.kt` |
| [BUG_muscle_group_table_not_seeded](BUG_muscle_group_table_not_seeded.md) | exercise_muscle_groups never populated on fresh install — MuscleGroupVolumeCard + EffectiveSetsCard always empty | ✅ Wrapped | P0 | — | — | `ExerciseMuscleGroupDao.kt`, `DatabaseModule.kt`, `MasterExerciseSeeder.kt`, `StrongCsvImporter.kt`, `MasterExerciseSeederTest.kt` |
| [BUG_trends_chart_scroll_starts_oldest](BUG_trends_chart_scroll_starts_oldest.md) | Trends chart cards scroll starts at oldest data instead of most recent | ✅ Completed | P2 | — | — | `VolumeTrendCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `E1RMProgressionCard.kt`, `BodyCompositionCard.kt` |
| [BUG_login_profile_setup_reshown](BUG_login_profile_setup_reshown.md) | Logout wipes local profile data and re-login doesn't restore it (wrong routing + data loss) | ✅ Completed | P0 | — | — | `UserSessionManager.kt`, `AuthViewModel.kt`, `AuthViewModelGoogleSignInTest.kt` |
| [BUG_profile_setup_not_skippable](BUG_profile_setup_not_skippable.md) | Profile Setup screen cannot be skipped | ✅ Completed | P1 | — | — | `ProfileSetupViewModel.kt`, `ProfileSetupScreen.kt` |
| [BUG_rpe_auto_page_non_work_sets](BUG_rpe_auto_page_non_work_sets.md) | RPE auto-page fires for non-work set types (warmup, drop, failure) | ✅ Completed | P2 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_timed_exercise_rpe_column](BUG_timed_exercise_rpe_column.md) | Timed exercise rows missing RPE column | ✅ Completed | P2 | BUG_timed_exercise_row_columns ✅ | — | `ActiveWorkoutScreen.kt`, `WORKOUT_SPEC.md` |
| [BUG_post_workout_state_not_cleared](BUG_post_workout_state_not_cleared.md) | Post-workout state not cleared — Resume button loops to finished workout's summary | ✅ Completed | P0 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_history_edit_weighted_unreadable](BUG_history_edit_weighted_unreadable.md) | History edit mode — weighted exercise fields unreadable (dark text on dark purple) | ✅ Wrapped | P1 | BUG_history_edit_unreadable_values_v2 ✅ | — | `WorkoutDetailScreen.kt` |
| [BUG_history_card_sparkline_misplaced](BUG_history_card_sparkline_misplaced.md) | History exercise card — sparkline trend line not inline with Best Set / Est 1RM stats | 🔵 Open | P2 | History card set details ✅ | — | — |
| [BUG_deep_link_nav_tab_broken](BUG_deep_link_nav_tab_broken.md) | Bottom nav broken after History → Trends deep-link — History tab does nothing, then misdirects to Trends | 🔵 Open | P1 | History → Trends deep-link ✅ | — | — |
| [BUG_history_delete_nav_back](BUG_history_delete_nav_back.md) | Delete workout from History navigates back to deleted workout summary instead of History list | 🔵 Open | P1 | — | — | — |

---

## How to Use This File

- **Start of session:** Find an `Open` bug, change its status to `In Progress`, note your session context.
- **Dev done:** Update status to `Completed`. Create `SUMMARY_<slug>.md`, fill Fix Notes in `BUG_<slug>.md`. Multiple bugs can sit in `Completed` waiting for batch QA.
- **After user QA:** Run `/wrap_task <slug>` — it does simplify, build, test, commit, push, and flips status to `Wrapped`.
- **New bugs:** Add a row here AND create a `BUG_<slug>.md` file using the format below. Row format: `| [BUG_slug](BUG_slug.md) | Title | 🔵 Open | P<N> | depends on or — | blocks or — | — |`

---

## Bug File Format

**File naming:** `BUG_<short-slug>.md` (e.g. `BUG_rest_timer_flicker.md`)

```markdown
# BUG: <title>

## Status
[ ] Open / [x] Fixed

## Description
<what is broken and where>

## Steps to Reproduce
1. ...

## Assets
- Images: `bugs_to_fix/assets/<slug>/screenshot.png`
- Videos: `bugs_to_fix/assets/<slug>/recording.mp4`
- Related spec: `WORKOUT_SPEC.md §X`

## Fix Notes
<populated after the fix is applied>
```

**Summary file naming:** `SUMMARY_<short-slug>.md` (e.g. `SUMMARY_rest_timer_flicker.md`)

```markdown
# Fix Summary: <title>

## Root Cause
<what caused the bug>

## Files Changed
| File | Change |
|---|---|
| `path/to/file` | description of change |

## Surfaces Fixed
- <screen or behaviour fixed>

## How to QA
- <step-by-step manual test the user can run on device>
```
