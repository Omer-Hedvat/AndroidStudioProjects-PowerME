# PowerME Project Memory

## Test Infrastructure (src/test/)

- [Test infra](test_infra.md) — JUnit4 + Mockito setup, mock-maker-inline, suspend stubs in runBlocking

## WorkoutViewModel Notes

- [WorkoutViewModel test patterns](workout_viewmodel_patterns.md) — dispatcher patterns, elapsedTimerJob, cancelWorkout, advanceTimeBy off-by-one

## HistoryViewModelTest Pattern

- [HistoryViewModelTest pattern](history_viewmodel_pattern.md) — UnconfinedTestDispatcher collector + StandardTestDispatcher main pattern

## Feature Backlog

- [Feature Backlog](project_backlog.md) — Cloud Sync (Firestore, HIGH PRIORITY) + 8 other deferred items

## UI Styling Rules

- [Toggle/Switch colors](feedback_toggle_colors.md) — all switches must use `onSurface` thumb + `primary`/`surfaceVariant` track tokens; never hardcode colors

## UX Patterns (confirmed working)

- [Organize Mode feedback](feedback_organize_mode.md) — unified persistent mode (Done button, no auto-exit) preferred over separate reorder/superset modes

## Blocked Work

- [HC external issue](project_hc_blocked.md) — all HC bugs/features blocked (external platform problem, not PowerME code)

## Architecture Decisions

- [Wearable strategy](feedback_wearable_strategy.md) — HC only; Garmin/Oura direct APIs explicitly deferred (complexity not worth it)

## Task Lifecycle

- [wrap_task timing](feedback_wrap_task_timing.md) — never call /wrap_task after build passes; wait for user QA on device first
- [Batch prompts and QA sessions](feedback_batch_sessions.md) — merge tasks touching same screen/flow into one prompt and one QA pass; never one prompt per task

## Parallel Session Prompts

- [Prompt headers](feedback_prompt_headers.md) — always include "Model + plan mode" in the title of every parallel session prompt (e.g. "Sonnet, no plan mode")

## Post-Task Output Format

- [Post-task output format](feedback_post_task_output_format.md) — brief summary of what the task was + HOW TO QA IT only; no WHAT CHANGED, no why-this-fixes-it
