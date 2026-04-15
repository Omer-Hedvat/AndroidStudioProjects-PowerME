# HC Workout Backfill Spec

**Phase:** P4
**Status:** not-started
**Effort:** S
**Depends on:** HC Phase B (WRITE_EXERCISE permission + `writeWorkoutSession()`) ✅

---

## Overview

When the user grants the `WRITE_EXERCISE` Health Connect permission, PowerME performs a one-time
background push of all completed workouts from the last 90 days. This gives Health Connect a
full training history from day one rather than only capturing sessions going forward.

The backfill is fire-and-forget: it never blocks the UI and never surfaces errors to the user.
`clientRecordId = workout.id` (already set in `writeWorkoutSession()`) guarantees HC upserts on
any repeated push — no duplicates are possible.

---

## Trigger Condition

Run the backfill **once** when all of the following are true at the moment
`onHealthConnectPermissionResult()` resolves in `SettingsViewModel`:

1. `WRITE_EXERCISE` is in the granted permission set
2. `AppSettingsDataStore.hcWorkoutBackfillDone == false`

After the backfill coroutine is launched, immediately flip `hcWorkoutBackfillDone = true` so a
subsequent revoke + re-grant does **not** trigger a second pass. The HC-side `clientRecordId`
dedup makes a repeated push safe, but it wastes network and battery — avoid it.

---

## Data Scope

| Filter | Value |
|---|---|
| `isCompleted` | `true` only |
| `isArchived` | `false` only |
| `timestamp` | `>= now − 90 days` (epoch ms) |
| Sort | Ascending by `timestamp` (oldest first — natural HC ingestion order) |

---

## New DAO Query

Add to `WorkoutDao`:

```kotlin
@Query("""
    SELECT * FROM workouts
    WHERE isCompleted = 1
      AND isArchived = 0
      AND timestamp >= :sinceMs
    ORDER BY timestamp ASC
""")
suspend fun getCompletedWorkoutsSince(sinceMs: Long): List<Workout>
```

No Room migration needed — query-only, no schema change.

---

## New DataStore Key

Add to `AppSettingsDataStore`:

```kotlin
val hcWorkoutBackfillDone: Flow<Boolean> =
    ctx.dataStore.data.map { it[HC_WORKOUT_BACKFILL_DONE_KEY] ?: false }

suspend fun setHcWorkoutBackfillDone(value: Boolean) =
    ctx.dataStore.edit { it[HC_WORKOUT_BACKFILL_DONE_KEY] = value }

// In companion object:
val HC_WORKOUT_BACKFILL_DONE_KEY = booleanPreferencesKey("hc_workout_backfill_done")
```

---

## New `HealthConnectManager` Method

`writeWorkoutSession()` takes `List<ExerciseWithSets>` (a UI-layer type). The backfill runs at the
repository level where `ExerciseWithSets` is not available. Add an internal overload that accepts
the exercise types directly:

```kotlin
/**
 * Backfill overload: accepts a pre-resolved list of ExerciseTypes instead of
 * the full ExerciseWithSets wrapper used on the live path.
 */
suspend fun writeWorkoutSession(workout: Workout, exerciseTypes: List<ExerciseType>) {
    if (!isAvailable()) return
    if (workout.startTimeMs == 0L || workout.endTimeMs == 0L) return
    try {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in granted) return

        val startInstant = Instant.ofEpochMilli(workout.startTimeMs)
        val endInstant   = Instant.ofEpochMilli(workout.endTimeMs)
        val zoneRules    = ZoneId.systemDefault().rules
        val hcType = when {
            exerciseTypes.all { it == ExerciseType.STRETCH }  -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            exerciseTypes.any { it == ExerciseType.CARDIO }   -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            else                                               -> ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
        }

        val record = ExerciseSessionRecord(
            startTime       = startInstant,
            endTime         = endInstant,
            startZoneOffset = zoneRules.getOffset(startInstant),
            endZoneOffset   = zoneRules.getOffset(endInstant),
            exerciseType    = hcType,
            title           = workout.routineName,
            notes           = workout.notes,
            metadata        = Metadata.manualEntry(workout.id)
        )
        client.insertRecords(listOf(record))
    } catch (e: Exception) {
        android.util.Log.w("PowerME_HC", "writeWorkoutSession (backfill) failed for ${workout.id}", e)
    }
}
```

Refactor `deriveHcExerciseType()` to operate on `List<ExerciseType>` so the live path and backfill
path share the same logic without duplication.

---

## Backfill Method

Add to `HealthConnectManager`:

```kotlin
/**
 * One-time backfill: pushes all completed workouts from the last [daysBack] days to HC.
 * Fire-and-forget — caller should launch this in a background coroutine.
 */
suspend fun backfillWorkoutSessions(
    workoutDao: WorkoutDao,
    workoutSetDao: WorkoutSetDao,
    daysBack: Int = 90
) {
    if (!isAvailable()) return
    val sinceMs = System.currentTimeMillis() - daysBack.toLong() * 24 * 60 * 60 * 1000
    val workouts = workoutDao.getCompletedWorkoutsSince(sinceMs)
    for (workout in workouts) {
        val exerciseTypes = workoutSetDao
            .getSetsWithExerciseForWorkout(workout.id)
            .map { it.exerciseType }
            .distinct()
        writeWorkoutSession(workout, exerciseTypes)
    }
}
```

---

## Hook-in Point — `SettingsViewModel.onHealthConnectPermissionResult()`

Inject `WorkoutDao` and `WorkoutSetDao` into `SettingsViewModel` (both are already provided by
Hilt). Then extend the two grant paths:

```kotlin
// Both the direct-grant path and the re-query path call this helper:
private fun onWritePermissionConfirmed() {
    viewModelScope.launch {
        val alreadyDone = appSettingsDataStore.hcWorkoutBackfillDone.first()
        if (!alreadyDone) {
            appSettingsDataStore.setHcWorkoutBackfillDone(true)   // flip first — prevents double-run
            healthConnectManager.backfillWorkoutSessions(workoutDao, workoutSetDao)
        }
    }
}
```

Call `onWritePermissionConfirmed()` right after `syncHealthConnect()` in both grant paths inside
`onHealthConnectPermissionResult()`.

---

## No UI Changes

The backfill runs silently. No snackbar, no progress indicator, no blocking dialog.
Logcat tag `PowerME_HC` will emit per-workout write failures for debugging.

---

## Error Handling

| Scenario | Behaviour |
|---|---|
| HC not installed | `isAvailable()` guard in `backfillWorkoutSessions` → skip entire backfill |
| `WRITE_EXERCISE` not granted | Inner permission check in `writeWorkoutSession` → skip each record |
| Single workout write fails | Exception caught per-record, loop continues; other workouts are not affected |
| No workouts in range | Empty list → loop body never executes; `hcWorkoutBackfillDone` still flips to `true` |
| `startTimeMs == 0` | Guard in `writeWorkoutSession` → skip that workout silently |

---

## How to QA

1. Fresh install (or clear app data) so `hcWorkoutBackfillDone == false`
2. Log several workouts over the past few weeks in the app
3. Open Settings → Health Connect → tap **Connect** → grant all permissions including **Workout sessions**
4. Open the Health Connect app → Browse → Exercise — all workouts from the last 90 days should appear
5. Revoke + re-grant permission — confirm no duplicate sessions appear in HC (clientRecordId dedup)
6. On a second device / after re-install with cloud restore: `hcWorkoutBackfillDone` resets to `false` → backfill runs again on next permission grant — confirm HC sessions are correct (HC upserts, no duplicates)
