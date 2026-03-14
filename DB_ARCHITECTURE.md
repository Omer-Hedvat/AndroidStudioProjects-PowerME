# Routine → Workout → Set: DB Architecture

## The Core Pattern: Template → Instance

```
TEMPLATE (what you plan)          INSTANCE (what you actually did)
─────────────────────────         ──────────────────────────────────
Routine                    ──1→N─→  Workout
  └── RoutineExercise (×N)  ──1→N─→    └── WorkoutSet (×N sets per exercise)
```

---

## Entity Definitions & Relationships

### `routines` table
```
id (PK autoGen) | name | lastPerformed | isCustom | isArchived
```
- `isArchived` added v20 (soft-delete; still appears in history, just hidden from active list)
- `lastPerformed` updated by `RoutineDao.updateLastPerformed()` when `finishWorkout()` is called

---

### `routine_exercises` table  (added v20, replaced `routine_exercise_cross_ref` v21)
```
id (PK) | routineId (FK→routines CASCADE) | exerciseId (FK→exercises CASCADE)
        | sets | reps | restTime | order | supersetGroupId | stickyNote
```
- **CASCADE**: deleting a Routine deletes all its RoutineExercise rows
- `stickyNote` persisted here (not in WorkoutSet) — shared context across sessions
- `order` is backtick-escaped in SQL (reserved keyword)
- Each row = one exercise slot in the routine; `sets` tells how many WorkoutSet rows to spawn

---

### `workouts` table
```
id (PK) | routineId (FK→routines SET_NULL, nullable) | timestamp
        | durationSeconds | totalVolume | notes | isCompleted
```
- **SET_NULL** (not CASCADE): if Routine is deleted, Workout row survives with `routineId = null` — orphan protection
- `isCompleted = false` during active workout; set to `true` in `finishWorkout()` — **settled-data gate**
- History tab ONLY shows rows where `isCompleted = 1`

---

### `workout_sets` table
```
id (PK) | workoutId (FK→workouts CASCADE) | exerciseId (FK→exercises CASCADE)
        | setOrder | weight | reps | rpe | setType | setNotes
        | distance | timeSeconds | startTime | endTime | restDuration
        | supersetGroupId | isCompleted
```
- **CASCADE**: deleting a Workout deletes all its WorkoutSet rows
- `isCompleted` (v22, Iron Vault): per-set completion flag, separate from `Workout.isCompleted`
- Skeleton rows created upfront with `weight=0, reps=0, isCompleted=false`
- `finishWorkout()` deletes all rows where `isCompleted=false` (cleanup of untouched skeletons)

---

## Lifecycle: Start → Active → Finish → History

### 1. User taps "Start" on a Routine card

**`WorkoutRepository.instantiateWorkoutFromRoutine(routineId)`** (atomic DB transaction):
1. Reads all `RoutineExercise` rows for the routine
2. Inserts 1 `Workout` row (`isCompleted = false`, `totalVolume = 0`)
3. For each `RoutineExercise` with `sets = N`, inserts **N** `WorkoutSet` skeleton rows (`weight=0, reps=0, isCompleted=false`)
4. Queries back the inserted sets to get DB-assigned IDs (for Iron Vault wiring)
5. Fetches "ghost" data: last 10 completed sets per exercise (`getPreviousSessionSets()`)
6. Returns `WorkoutBootstrap(workoutId, workoutSets, ghostMap)`

**`WorkoutViewModel.startWorkoutFromRoutine()`** then:
- Maps `WorkoutSet → ActiveSet` UI models, attaching ghost hints (previous weight/reps)
- Sets `_workoutState.isActive = true`
- Starts elapsed timer

---

### 2. During the Workout (Iron Vault auto-save)

Every user action writes to DB immediately:

| User action | DB write |
|-------------|----------|
| Types weight/reps | `updateWeightReps(setId, w, r)` (debounced 300ms) |
| Taps ✓ | `updateSetCompleted(setId, true)` |
| Taps set type badge | `updateSetType(setId, type)` |
| Taps RPE chip | `updateRpe(setId, rpe)` |
| Deletes a set | `deleteSet(set)` |
| Adds a set | `insertSet(WorkoutSet(...))` |

App crash / backgrounding = no data loss. On next launch, `rehydrateIfNeeded()` reads the `isCompleted=false` Workout and reconstructs UI state.

---

### 3. User taps "Finish"

**`WorkoutViewModel.finishWorkout()`**:
1. Calculates `durationSeconds` and `totalVolume` from completed sets
2. **`workoutDao.updateWorkout(... isCompleted=true ...)`** — settles the record
3. **`workoutSetDao.deleteIncompleteSetsByWorkout(workoutId)`** — removes skeleton rows user never touched
4. **`routineDao.updateLastPerformed(routineId, now)`** — updates recency on routine card
5. Boaz performance analysis runs in background (compare planned vs actual)
6. Triggers `PostWorkoutSummarySheet` via `pendingWorkoutSummary` state

**`WorkoutViewModel.cancelWorkout()`** (alternative):
1. Deletes all `WorkoutSet` rows for the workout
2. Deletes the `Workout` row itself
3. Routine is unchanged (no `lastPerformed` update)

---

### 4. History Tab

**Query** (`WorkoutDao.getAllCompletedWorkoutsWithExerciseNames()`):
```sql
SELECT w.*, e.name AS exerciseName, r.name AS routineName,
       (SELECT COUNT(*) FROM workout_sets ws2
        WHERE ws2.workoutId = w.id AND ws2.isCompleted = 1) AS setCount
FROM workouts w
LEFT JOIN (SELECT DISTINCT workoutId, exerciseId FROM workout_sets) ws ON ws.workoutId = w.id
LEFT JOIN exercises e ON ws.exerciseId = e.id
LEFT JOIN routines r ON w.routineId = r.id
WHERE w.isCompleted = 1
ORDER BY w.timestamp DESC
```
Returns one **row per exercise** per workout → `HistoryViewModel.collapseRows()` groups by `workoutId` and aggregates `exerciseNames` into a list.

**`WorkoutWithExerciseSummary`** (the collapsed UI model):
```kotlin
data class WorkoutWithExerciseSummary(
    id, routineId, timestamp, durationSeconds, totalVolume, notes,
    exerciseNames: List<String>,  // aggregated
    routineName: String?,         // from routines JOIN (null if routine deleted)
    setCount: Int                 // from COUNT subquery
)
```

Each card in HistoryScreen shows:
- Title: `routineName` ?: "Workout — <date>"
- Stats row: duration · total volume · set count
- Exercise chips: up to 4, then "+N more"

**Tapping a card** navigates to `WorkoutDetailScreen(workoutId)`:
- `WorkoutSetDao.getSetsWithExerciseForWorkout(workoutId)` — only `isCompleted=1` sets
- Groups by exerciseId → per-set breakdown with e1RM and RPE

---

## Foreign Key Summary

```
exercises ←──── routine_exercises ────→ routines
    ↑                                       ↑
    │ (CASCADE)                    (SET_NULL, nullable)
    └────── workout_sets ─────→ workouts
               (CASCADE)
```

| Delete | Effect |
|--------|--------|
| Exercise | All RoutineExercise + WorkoutSet rows deleted |
| Routine | All RoutineExercise rows deleted; `Workout.routineId → null` (history preserved) |
| Workout | All WorkoutSet rows deleted |
