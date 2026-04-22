# Observability Layer — Beta Crash Reporting & Action Trail

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `build.gradle.kts` (app), `PowerMeApplication.kt`, `WorkoutViewModel.kt`, `WorkoutSummaryViewModel.kt`, `HistoryViewModel.kt`, `TrendsViewModel.kt`, `PowerMeNavigation.kt` |

---

## Overview

Before the family/friends beta, PowerME needs two observability layers so bugs reported by external testers can be diagnosed without requiring them to share logs manually:

1. **Firebase Crashlytics** — automatically captures every unhandled exception with a full stack trace, device model, OS version, and free memory. Zero user action required. Integrates with the existing Firebase project.

2. **Firebase Analytics custom events** — logs the sequence of user actions (workout lifecycle, navigation, set confirmation) so bugs that don't crash can be reverse-engineered by replaying the event trail in the Firebase console.

Both layers ship in a single PR. Timber is added as the logging facade so debug builds log to Logcat and release builds log to Crashlytics (non-fatal errors).

---

## Behaviour

### Timber Setup
- `PowerMeApplication.kt` plants `Timber.DebugTree()` in debug builds and a custom `CrashlyticsTree` in release builds.
- `CrashlyticsTree` routes `Log.WARN` and `Log.ERROR` to `FirebaseCrashlytics.getInstance().log()` and sends `Log.ERROR` + throwables as non-fatal exceptions via `recordException()`.
- All existing `Log.*` calls in the codebase are migrated to `Timber.*`.

### Crashlytics
- Added to `build.gradle.kts` via `com.google.firebase:firebase-crashlytics-ktx`.
- `google-services.json` already present — no new Firebase project needed.
- Automatic crash reporting requires no code beyond the dependency and plugin.
- Custom keys set per session: `user_id` (hashed, not email), `workout_active` (bool), `db_version` (int).

### Firebase Analytics Events

| Event name | Trigger | Key params |
|---|---|---|
| `workout_started` | `WorkoutViewModel.startWorkout()` | `routine_id`, `exercise_count` |
| `workout_set_confirmed` | set confirmation in `WorkoutViewModel` | `exercise_id`, `set_type`, `set_index` |
| `workout_finished` | `WorkoutViewModel.finishWorkout()` | `workout_id`, `duration_minutes`, `total_sets` |
| `workout_cancelled` | `WorkoutViewModel.cancelWorkout()` | `workout_id`, `sets_logged` |
| `workout_resumed` | resume bar tap → `WorkoutViewModel` | `workout_id` |
| `rest_timer_started` | rest timer launch | `duration_seconds`, `exercise_id` |
| `rest_timer_skipped` | skip tap | `remaining_seconds` |
| `nav_tab_selected` | bottom nav tap | `tab_name` |
| `screen_viewed` | key navigation events in `PowerMeNavigation` | `screen_name` |
| `error_non_fatal` | caught exceptions routed via `CrashlyticsTree` | auto-included in Crashlytics |

### What is NOT logged
- Set weight, reps, or RPE values (privacy — beta testers are family/friends).
- User email or name.
- Any Firestore document contents.

---

## UI Changes

None. This is a pure infrastructure change — no new screens or composables.

---

## Files to Touch

- `app/build.gradle.kts` — add Crashlytics KTX, Analytics KTX, Timber dependencies; apply `com.google.firebase.crashlytics` plugin
- `build.gradle.kts` (root) — add Crashlytics Gradle plugin to classpath
- `PowerMeApplication.kt` — plant Timber trees; set Crashlytics custom keys
- `WorkoutViewModel.kt` — add Analytics events at workout lifecycle transitions; migrate `Log.*` → `Timber.*`
- `WorkoutSummaryViewModel.kt` — migrate `Log.*` → `Timber.*`
- `HistoryViewModel.kt` — add `screen_viewed` event
- `TrendsViewModel.kt` — migrate `Log.*` → `Timber.*`
- `PowerMeNavigation.kt` — add `nav_tab_selected` and `screen_viewed` events
- All other files with existing `Log.*` calls — migrate to `Timber.*`

---

## State Machine Diagnostics

In-memory `ActiveWorkoutState` transitions are invisible to Crashlytics and Analytics events — they only capture user actions, not the state machine's response to them. The zombie-state bug (April 2026) showed this gap: the DB write succeeded but the in-memory `isActive` flag was never cleared, and there was no log trail to identify where the state machine broke.

All state machine logs use the tag `"WVM"` for single-command diagnosis:

```bash
adb logcat -d | grep WVM
```

### Logged transition points (Timber.d, all builds)

| Tag message | Where |
|---|---|
| `MINIMIZE wId=…` | `minimizeWorkout()` |
| `MAXIMIZE wId=… isActive=…` | `maximizeWorkout()` |
| `REHYDRATE_STALE purging wId=… ageH=…` | `rehydrateIfNeeded()` — stale purge |
| `REHYDRATE_GHOST purging wId=…` | `rehydrateIfNeeded()` — ghost purge |
| `REHYDRATE_OK wId=… exercises=…` | `rehydrateIfNeeded()` — restored |
| `START_BLOCKED isActive=… isMin=… — maximizing` | `startWorkout()` guard hit |
| `START_BLOCKED_ROUTINE …` | `startWorkoutFromRoutine()` guard hit |
| `START_BLOCKED_PLAN …` | `startWorkoutFromPlan()` guard hit |
| `STARTED wId=…` | `startWorkout()` activated |
| `STARTED_ROUTINE wId=… exercises=…` | `startWorkoutFromRoutine()` activated |
| `STARTED_PLAN wId=… exercises=…` | `startWorkoutFromPlan()` activated |
| `EDIT_BLOCKED isActive=…` | `startEditMode()` guard hit |
| `EDIT_START routineId=…` | `startEditMode()` activated |
| `FINISH_BEGIN wId=… isActive=…` | `finishWorkout()` entry |
| `FINISH_DB_DONE wId=…` | `finishWorkout()` after DB write |
| `FINISH_SYNC wId=… syncType=…` | `finishWorkout()` sync path → `isActive=false` |
| `FINISH_OK wId=…` | `finishWorkout()` normal path → `isActive=false` |
| `DISMISS_SUMMARY lastId=…` | `dismissWorkoutSummary()` |
| `RESOLVE_SYNC` | `resolveRoutineSync()` |
| `CANCEL wId=… setsLogged=…` | `cancelWorkout()` |
| `NAV_FINISH finishedId=… syncType=…` | `onWorkoutFinished` nav callback |
| `NAV_MAXIMIZE isActive=… isMin=… route=…` | Maximize `LaunchedEffect` |
| `MIN_BAR visible=… isActive=… isMin=… isEdit=…` | Minimized bar state change |

### Reading the trail

A healthy finish looks like:
```
FINISH_BEGIN wId=8dbcd76a isActive=true
FINISH_DB_DONE wId=8dbcd76a
FINISH_OK wId=8dbcd76a         ← or FINISH_SYNC if routine diverged
NAV_FINISH finishedId=8dbcd76a syncType=NONE
DISMISS_SUMMARY lastId=8dbcd76a
```

If `FINISH_DB_DONE` appears but `FINISH_OK`/`FINISH_SYNC` is missing, the state update never ran — that's the zombie bug.
If `NAV_FINISH` fires with `finishedId=null`, the stale state was already cleared before navigation ran.
If `START_BLOCKED` appears when the user tries to start a new workout, `isActive` is still true in memory.

---

## How to QA

1. Build a debug APK and install on device.
2. Open Android Studio Logcat — confirm Timber `D/` tags appear for workout lifecycle events.
3. Trigger a test crash: add a temporary `throw RuntimeException("test crash")` in a ViewModel, run the app, confirm it appears in Firebase Crashlytics console within ~5 minutes.
4. Complete a full workout: start → log 2+ sets → finish. Open Firebase Analytics DebugView (enable with `adb shell setprop debug.firebase.analytics.app com.powerme.app`) — confirm `workout_started`, `workout_set_confirmed`, `workout_finished` events arrive.
5. Remove the test crash before shipping.
