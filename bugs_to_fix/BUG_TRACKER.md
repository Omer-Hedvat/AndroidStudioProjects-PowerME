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
| [BUG_post_workout_triple_sync_prompt](BUG_post_workout_triple_sync_prompt.md) | Post-workout routine sync prompt appears 3 times (PostWorkoutSummarySheet + WorkoutSummaryScreen both show it) | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `WorkoutSummaryScreen.kt`, `WorkoutViewModelTest.kt` |
| [BUG_history_slow_first_load](BUG_history_slow_first_load.md) | History tab takes ~3 seconds to show records on first open | ✅ Wrapped | P1 | — | — | `WorkoutDao.kt`, `WorkoutSet.kt`, `Workout.kt`, `PowerMeDatabase.kt`, `DatabaseModule.kt`, `HistoryViewModel.kt` |
| [BUG_leftover_ui_elements](BUG_leftover_ui_elements.md) | Hebrew text on login screen + Boaz Insights card in Trends tab | ✅ Wrapped | P1 | — | — | `WelcomeScreen.kt`, `MetricsScreen.kt`, `MetricsViewModel.kt`, `MetricsViewModelBodyVitalsTest.kt` |
| [BUG_hc_offer_skipped_on_login](BUG_hc_offer_skipped_on_login.md) | Existing users skip HC connect screen on login — navigates directly to Workouts | ✅ Wrapped | P1 | — | — | `AppSettingsDataStore.kt`, `AuthViewModel.kt`, `WelcomeScreen.kt`, `PowerMeNavigation.kt`, `HcOfferScreen.kt` (new), `HcOfferViewModel.kt` (new) |
| [BUG_trends_scroll_crash](BUG_trends_scroll_crash.md) | Trends tab crashes when scrolling to VolumeTrendCard — likely MuscleGroupVolumeCard producer race | ✅ Wrapped | P0 | — | — | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `MetricsScreen.kt` |
| [BUG_timed_exercise_row_columns](BUG_timed_exercise_row_columns.md) | Timed exercise row: missing PREV column, spurious RPE column, orphaned '-' box with no header | ✅ Wrapped | P1 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |
| [BUG_warning_beep_gap_too_wide](BUG_warning_beep_gap_too_wide.md) | WARNING double-beep has 300ms gap — too wide, sounds like two separate events | ✅ Wrapped | P3 | — | — | `RestTimerNotifier.kt`, `CLOCKS_SPEC.md` |
| [BUG_toggle_colors_inconsistent](BUG_toggle_colors_inconsistent.md) | Switch/Toggle colors inconsistent across screens | ✅ Wrapped | P3 | — | — | `SettingsScreen.kt`, `ActiveWorkoutScreen.kt` |
| [BUG_trends_card_crash_on_open](BUG_trends_card_crash_on_open.md) | Trends card crashes when opened | ✅ Wrapped | P0 | — | — | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt` |
| [BUG_next_field_not_selected_on_confirm](BUG_next_field_not_selected_on_confirm.md) | Next input field not auto-selected when confirming with checkmark | ✅ Wrapped | P2 | — | — | `WorkoutInputField.kt` |
| [BUG_history_edit_unreadable_values_v2](BUG_history_edit_unreadable_values_v2.md) | History edit values unreadable (regression) | ✅ Wrapped | P1 | — | — | `WorkoutDetailScreen.kt` |
| [BUG_prev_session_rpe_missing](BUG_prev_session_rpe_missing.md) | RPE missing from previous session values in active workout | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_fitness_level_not_persisted](BUG_fitness_level_not_persisted.md) | Fitness level selection doesn't persist after app restart | ✅ Wrapped | P1 | — | — | `FirestoreSyncManager.kt`, `ProfileViewModelPersonalInfoTest.kt` |
| [BUG_body_metrics_not_populated_from_hc](BUG_body_metrics_not_populated_from_hc.md) | Body Metrics weight/body fat not populated from Health Connect | ✅ Wrapped | P1 | — | — | `ProfileViewModel.kt`, `ProfileViewModelPersonalInfoTest.kt` |
| [BUG_post_workout_note_no_save](BUG_post_workout_note_no_save.md) | Post-workout notes field has no Save button | ✅ Wrapped | P1 | — | — | `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModelTest.kt` |
| [BUG_muscle_group_table_not_seeded](BUG_muscle_group_table_not_seeded.md) | exercise_muscle_groups never populated on fresh install — MuscleGroupVolumeCard + EffectiveSetsCard always empty | ✅ Wrapped | P0 | — | — | `ExerciseMuscleGroupDao.kt`, `DatabaseModule.kt`, `MasterExerciseSeeder.kt`, `StrongCsvImporter.kt`, `MasterExerciseSeederTest.kt` |
| [BUG_trends_chart_scroll_starts_oldest](BUG_trends_chart_scroll_starts_oldest.md) | Trends chart cards scroll starts at oldest data instead of most recent | ✅ Wrapped | P2 | — | — | `VolumeTrendCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `E1RMProgressionCard.kt`, `BodyCompositionCard.kt` |
| [BUG_login_profile_setup_reshown](BUG_login_profile_setup_reshown.md) | Logout wipes local profile data and re-login doesn't restore it (wrong routing + data loss) | ✅ Wrapped | P0 | — | — | `UserSessionManager.kt`, `AuthViewModel.kt`, `AuthViewModelGoogleSignInTest.kt` |
| [BUG_profile_setup_not_skippable](BUG_profile_setup_not_skippable.md) | Profile Setup screen cannot be skipped | ✅ Wrapped | P1 | — | — | `ProfileSetupViewModel.kt`, `ProfileSetupScreen.kt` |
| [BUG_rpe_auto_page_non_work_sets](BUG_rpe_auto_page_non_work_sets.md) | RPE auto-page fires for non-work set types (warmup, drop, failure) | ✅ Wrapped | P2 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_timed_exercise_rpe_column](BUG_timed_exercise_rpe_column.md) | Timed exercise rows missing RPE column | ✅ Wrapped | P2 | BUG_timed_exercise_row_columns ✅ | — | `ActiveWorkoutScreen.kt`, `WORKOUT_SPEC.md` |
| [BUG_post_workout_state_not_cleared](BUG_post_workout_state_not_cleared.md) | Post-workout state not cleared — Resume button loops to finished workout's summary | ✅ Wrapped | P0 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_history_edit_weighted_unreadable](BUG_history_edit_weighted_unreadable.md) | History edit mode — weighted exercise fields unreadable (dark text on dark purple) | ✅ Wrapped | P1 | BUG_history_edit_unreadable_values_v2 ✅ | — | `WorkoutDetailScreen.kt` |
| [BUG_history_card_sparkline_misplaced](BUG_history_card_sparkline_misplaced.md) | History exercise card — sparkline trend line not inline with Best Set / Est 1RM stats | ✅ Wrapped | P2 | History card set details ✅ | — | `WorkoutSummaryScreen.kt` |
| [BUG_deep_link_nav_tab_broken](BUG_deep_link_nav_tab_broken.md) | Bottom nav broken after History → Trends deep-link — History tab does nothing, then misdirects to Trends | ✅ Wrapped | P1 | History → Trends deep-link ✅ | — | `PowerMeNavigation.kt` |
| [BUG_history_delete_nav_back](BUG_history_delete_nav_back.md) | Delete workout from History navigates back to deleted workout summary instead of History list | ✅ Wrapped | P1 | — | — | `WorkoutDetailScreen.kt`, `PowerMeNavigation.kt`, `WorkoutDetailViewModelTest.kt` |
| [BUG_muscle_balance_groups_by_day](BUG_muscle_balance_groups_by_day.md) | Muscle Balance + Effective Sets cards group volume by day instead of by week | ✅ Wrapped | P1 | BUG_muscle_group_table_not_seeded ✅ | — | `TrendsDao.kt`, `TrendsRepositoryWeeklyGroupingTest.kt` |
| [BUG_effective_sets_filter_ignored](BUG_effective_sets_filter_ignored.md) | Effective Sets card — time range filter chips have no effect, same data shown for all ranges | ✅ Wrapped | P1 | BUG_muscle_group_table_not_seeded ✅ | — | `TrendsDao.kt`, `EffectiveSetsCard.kt`, `TrendsViewModelEffectiveSetsFilterTest.kt` |
| [BUG_body_composition_ignores_hc](BUG_body_composition_ignores_hc.md) | BodyCompositionCard shows empty state — does not pull weight/body fat from Health Connect | ✅ Completed | P1 | — | — | `HealthConnectManager.kt`, `TrendsRepository.kt`, `TrendsRepositoryBodyCompositionTest.kt` (new), `TrendsRepositoryChronotypeTest.kt`, `TrendsRepositoryWeeklyGroupingTest.kt` |
| [BUG_training_window_no_axis_labels](BUG_training_window_no_axis_labels.md) | Training Window scatter plot missing axis labels (X = hour, Y = volume) | ✅ Completed | P2 | — | — | `ChronotypeCard.kt`, `MetricsScreen.kt` |
| [BUG_hc_extended_reads_no_data](BUG_hc_extended_reads_no_data.md) | All HC data missing — full Health Connect sync broken (weight, body fat, HR, VO₂, SpO₂, Calories) | ✅ Completed | P0 | — | — | `DatabaseModule.kt`, `PowerMeDatabase.kt`, `HealthConnectSync.kt`, `HealthConnectManager.kt`, `MetricLog.kt`, `HealthConnectSyncDao.kt`, `AndroidManifest.xml`, `MetricsViewModel.kt`, `BodyVitalsCard.kt`, `ReadinessEngine.kt`, `TrendsDao.kt`, `TrendsModels.kt`, `TrendsRepository.kt`, `TrendsViewModel.kt`, `PowerMeApplication.kt` + 7 new source files + 9 new test files |
| [BUG_chronotype_card_missing_title](BUG_chronotype_card_missing_title.md) | Chronotype card missing top-level card title | ✅ Completed | P3 | — | — | `ChronotypeCard.kt` |
| [BUG_timed_exercise_prev_rpe_missing](BUG_timed_exercise_prev_rpe_missing.md) | RPE missing from PREV column for timed exercises | ✅ Wrapped | P2 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt` |
| [BUG_timed_exercise_prev_only_first_set](BUG_timed_exercise_prev_only_first_set.md) | Timed exercise PREV data only shown for first set, not subsequent sets | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt` |
| [BUG_timed_exercise_time_not_persisted](BUG_timed_exercise_time_not_persisted.md) | Timed exercise target duration resets to 0 between workouts | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_routine_sync_card_no_dismiss](BUG_routine_sync_card_no_dismiss.md) | Routine sync card doesn't dismiss after tapping Update/Keep on post-workout summary | ✅ Wrapped | P1 | BUG_post_workout_triple_sync_prompt ✅ | — | `WorkoutSummaryViewModel.kt`, `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModelTest.kt` |
| [BUG_body_stress_map_no_time_decay](BUG_body_stress_map_no_time_decay.md) | Body stress heatmap does not update stress decay on resume | ✅ Completed | P2 | — | — | `TrendsViewModel.kt`, `MetricsScreen.kt`, `TrendsViewModelBodyStressRefreshTest.kt` |
| [BUG_summary_set_rpe_format](BUG_summary_set_rpe_format.md) | Workout summary set rows show RPE right-aligned instead of inline (REPSxWEIGHT@RPE) | ✅ Wrapped | P2 | History card set details ✅ | — | `WorkoutSummaryScreen.kt` |
| [BUG_exercise_joints_rework](BUG_exercise_joints_rework.md) | Exercise joint indicators — no primary/secondary split, chips look interactive, health-history tint wrong | ~~Superseded~~ | P2 | — | — | Absorbed into `EXERCISE_DETAIL_SHEET_REVISION_SPEC.md` |
| [BUG_timed_exercise_parallel_timers](BUG_timed_exercise_parallel_timers.md) | Timed exercise — rest timer and setup timer run in parallel when set fires | ✅ Wrapped | P1 | — | — | `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt` |
| [BUG_deleted_rest_timer_returns](BUG_deleted_rest_timer_returns.md) | Deleted rest timers reappear after app reopen or workout restart | ✅ Completed | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutsScreen.kt`, `WorkoutDao.kt`, `FirestoreSyncManager.kt` |
| [BUG_prev_results_mixed_set_types](BUG_prev_results_mixed_set_types.md) | PREV column shows wrong data — warmup rows show working set PREV and vice versa | ✅ Wrapped | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_prev_rpe_multiplied](BUG_prev_rpe_multiplied.md) | PREV RPE shown as 10× actual value (9 shows as 90) | ✅ Wrapped | P2 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_warmup_rest_no_collapse_on_skip](BUG_warmup_rest_no_collapse_on_skip.md) | Warmup rest timer doesn't collapse when set is skipped | ✅ Completed | P2 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_confirm_set_after_reps](BUG_confirm_set_after_reps.md) | Tapping ✓ after entering reps does not confirm the set | ✅ Completed | P1 | — | — | `ActiveWorkoutScreen.kt` |
| [BUG_rest_timer_end_beep_missing](BUG_rest_timer_end_beep_missing.md) | Long beep at end of rest timer is missing | ✅ Completed | P2 | — | — | `RestTimerNotifier.kt`, `WorkoutViewModel.kt` |
| [BUG_exercise_detail_scroll_crash](BUG_exercise_detail_scroll_crash.md) | Exercise Detail Screen crashes on scroll | ✅ Completed | P1 | — | Exercise Detail Tabs v2 | `ExerciseDetailScreen.kt` |
| [BUG_summary_avg_rpe_multiplied](BUG_summary_avg_rpe_multiplied.md) | Workout summary avg RPE shown as 10× actual value (86.3 instead of 8.6) | ✅ Completed | P2 | — | — | `WorkoutSummaryViewModel.kt`, `WorkoutSummaryViewModelTest.kt` |
| [BUG_warmup_ramp_format_crash](BUG_warmup_ramp_format_crash.md) | WarmUpRampSection crashes with UnknownFormatConversionException when percentageLabel contains % | ✅ Completed | P0 | — | — | `AboutTab.kt` |
| [BUG_about_tab_section_overlaps](BUG_about_tab_section_overlaps.md) | ABOUT tab sections overlap — BodyOutlineCanvas bleeds through Training Zones and Form Cues | ✅ Completed | P1 | — | — | `AboutTab.kt` |
| [BUG_rest_timer_delete_clears_all](BUG_rest_timer_delete_clears_all.md) | Deleting one rest timer removes all rest timers for that exercise | ✅ Completed | P1 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |
| [BUG_edit_mode_x_saves_changes](BUG_edit_mode_x_saves_changes.md) | Edit mode 'X' (discard) button saves changes instead of discarding | ✅ Completed | P0 | — | — | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |

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
