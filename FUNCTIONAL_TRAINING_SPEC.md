# Functional Training Spec — AMRAP / RFT / EMOM (Hybrid Mode)

| Field | Value |
|---|---|
| **Type** | Epic |
| **Phase** | P8 |
| **Status** | `in-progress` |
| **Children** | 29 tasks — see `future_devs/FUNC_*_SPEC.md`, `future_devs/EXERCISE_FILTER_DIALOG_SPEC.md`, `future_devs/EXERCISE_FILTER_DIALOG_STICKY_ACTIONS_SPEC.md`, `future_devs/EXERCISE_TYPE_FILTER_CHIPS_SPEC.md`, `future_devs/EXERCISE_FILTER_COLLAPSE_SPEC.md`, `future_devs/EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md`, `future_devs/FUNC_ACTIVE_BLOCK_CARD_UI_SPEC.md` |
| **Rollup** | 13/29 wrapped · 6 completed · 0 in-progress · 7 not-started |

> **Depends on:** DB v48 stable (current). No other phase dependencies.

---

## 1. Overview

PowerME currently supports linear strength training only: `Workout → Sets` (exercises derived from `DISTINCT workout_sets.exerciseId`). This spec adds native **Functional Training** support — AMRAP ("As Many Rounds As Possible"), RFT ("Rounds For Time"), and EMOM ("Every Minute On the Minute") — and enables them to coexist with strength work in the same session (Hybrid mode).

### 1.1 The Four Modes

**AMRAP (As Many Rounds As Possible)**
- A prescribed list of exercises (the "recipe") is performed repeatedly until a time cap (e.g. 12 minutes) runs out.
- Score = rounds completed + extra reps into the next round (e.g. "8 rounds + 3 reps").

**RFT (Rounds For Time)**
- A prescribed number of rounds is completed as fast as possible. Optional time cap auto-finishes the block if reached.
- Score = total elapsed time (e.g. "18:42") and rounds completed (partial credit captured if the user taps Finish early or cap hits, e.g. "4 of 5 rounds @ 14:22").

**EMOM (Every Minute On the Minute — and variants)**
- A prescribed amount of work is performed at the start of every interval for N total minutes. Remaining time in each interval is rest.
- Interval length is configurable: 60s (EMOM), 90s, 120s (E2MOM), 180s (E3MOM), 300s (E5MOM), or custom.
- Score = rounds completed + completion %.

**Tabata**
- A fixed-format interval: work for `workSeconds`, rest for `restSeconds`, repeated for `rounds` total cycles.
- Default: 20s work / 10s rest × 8 rounds = 4 minutes (classic Tabata protocol).
- Fully configurable. Optional "skip last rest" flag.
- Score = rounds completed (auto-tracked) + RPE.

### 1.2 The Three Workout Styles (user preference)

| Style | "Add" behavior in Template Builder |
|---|---|
| `PURE_GYM` | Goes straight to legacy exercise library (no change). |
| `PURE_FUNCTIONAL` | Opens the Functional Block Wizard directly (4 block types). |
| `HYBRID` (default) | Opens a bottom sheet: "Add Strength Exercise" or "Add Functional Block". A single routine can hold any mix of STRENGTH, AMRAP, RFT, EMOM, and TABATA blocks. |

### 1.3 The "Block" Concept

A **Block** is the new structural unit between a Workout and its exercises. Every workout contains one or more blocks:

```
Workout (instance)
  └── WorkoutBlock [type=STRENGTH]   ← all legacy workouts have exactly one after migration
        └── WorkoutSet(s)
  └── WorkoutBlock [type=AMRAP, durationSeconds=720]
        └── WorkoutSet(s) — one per round
  └── WorkoutBlock [type=RFT, targetRounds=5]
        └── WorkoutSet(s)
```

Parallel template hierarchy:
```
Routine (template)
  └── RoutineBlock [type=STRENGTH]
        └── RoutineExercise(s)
  └── RoutineBlock [type=AMRAP, durationSeconds=720]
        └── RoutineExercise(s) — the recipe
```

---

## 2. Architectural Decisions

### A. Block storage = two new entities (mirror-structure)

**Decision:** Create `RoutineBlock` and `WorkoutBlock` as first-class Room entities. Add nullable `blockId: String?` FK to `RoutineExercise` and `WorkoutSet`.

**Rejected alternatives:**
- _Block params as columns on `RoutineExercise`_: duplicates AMRAP duration on every exercise row; no single block source of truth; block ordering is race-prone.
- _`WorkoutBlock` as embedded JSON in `Workout.blocksJson`_: breaks the Firestore embedded-child-array pattern that already works for sets/exercises; forces JSON1 queries for analytics; asymmetric with the template side.

**Backfill:** migration creates one implicit `STRENGTH` block per existing routine and per existing workout, points all existing `RoutineExercise` and `WorkoutSet` rows at it. Legacy behavior is fully preserved.

### B. Functional score = columns on `WorkoutBlock`

**Decision:** `totalRounds`, `extraReps`, `finishTimeSeconds`, `rpe`, `blockNotes`, `runStartMs` are columns on `WorkoutBlock`.

**Rejected alternative:** _Synthetic "summary" `WorkoutSet` row_: would pollute `workout_sets` aggregations (`totalVolume`, PR detection, `setCount`). Every existing query would need a filter for the fake row type.

### C. Exercise categorization = `tags` JSON column

**Decision:** Add `tags: String` (JSON array) to `Exercise`. Seed functional-specific movements with `["functional"]`; dual-use movements (Barbell Back Squat, KB Swing) get `["functional"]` or not based on common WOD appearance. Secondary grouping via `familyId` new values: `metcon_family`, `olympic_family`, `gymnastics_family`, `monostructural_family`.

**Rejected alternatives:**
- _`exerciseType = FUNCTIONAL` enum value_: mutually exclusive — a Thruster is strength AND functional.
- _`isFunctional: Boolean`_: mutually exclusive, same problem.
- _`familyId` alone_: one `familyId` per exercise; a Thruster legitimately belongs to both `olympic_family` and `metcon_family`.

### D. Timer engine = extracted `TimerEngine` class

**Decision:** Extract a plain class `TimerEngine` (not a ViewModel). `ToolsViewModel.runEmom/Tabata/Stopwatch/Countdown` delegate to it. New `FunctionalBlockRunner` (injected into `WorkoutViewModel`, not a sibling VM) also consumes it.

**Rejected alternatives:**
- _Inline into `WorkoutViewModel`_: already 2774 lines.
- _Separate `FunctionalBlockViewModel` alongside `WorkoutViewModel`_: two VMs racing over one `WorkoutTimerService` binding; REST timer state and AMRAP timer state in separate objects = split brain.
- _Reuse `ToolsViewModel` directly_: couples the Tools nav-graph scope to the Workout nav-graph scope; breaks if user runs a Clocks EMOM while an AMRAP workout is paused.

---

## 3. Schema

### 3.1 New entity: `RoutineBlock`

```kotlin
@Entity(
  tableName = "routine_blocks",
  foreignKeys = [ForeignKey(
    entity = Routine::class,
    parentColumns = ["id"],
    childColumns = ["routineId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("routineId")]
)
data class RoutineBlock(
  @PrimaryKey val id: String,                      // UUID
  val routineId: String,                           // FK → routines.id, CASCADE
  val order: Int,                                  // position within routine (0-based)
  val type: String,                                // STRENGTH | AMRAP | RFT | EMOM | TABATA
  val name: String? = null,                        // user-editable label, e.g. "Metcon", "Finisher"
  val durationSeconds: Int? = null,                // AMRAP cap / EMOM total / RFT optional cap
  val targetRounds: Int? = null,                   // RFT target round count
  val emomRoundSeconds: Int? = null,               // EMOM: duration of one interval in seconds (default 60)
  // Tabata fields
  val tabataWorkSeconds: Int? = null,              // TABATA work phase duration (default 20)
  val tabataRestSeconds: Int? = null,              // TABATA rest phase duration (default 10)
  val tabataSkipLastRest: Int? = null,             // 0 = don't skip, 1 = skip last rest (SQLite bool)
  // Per-block timer overrides (null = use AppSettings defaults)
  val setupSecondsOverride: Int? = null,           // pre-start countdown; null = timedSetSetupSeconds pref
  val warnAtSecondsOverride: Int? = null,          // mid-interval warning; null = resolveWarnAt auto-halftime
  // Sync columns (v35 pattern)
  @ColumnInfo(defaultValue = "") val syncId: String = UUID.randomUUID().toString(),
  @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)
```

### 3.2 New entity: `WorkoutBlock`

```kotlin
@Entity(
  tableName = "workout_blocks",
  foreignKeys = [ForeignKey(
    entity = Workout::class,
    parentColumns = ["id"],
    childColumns = ["workoutId"],
    onDelete = ForeignKey.CASCADE
  )],
  indices = [Index("workoutId")]
)
data class WorkoutBlock(
  @PrimaryKey val id: String,
  val workoutId: String,                            // FK → workouts.id, CASCADE
  val order: Int,
  val type: String,                                 // STRENGTH | AMRAP | RFT | EMOM
  val name: String? = null,
  // Plan fields (copied from RoutineBlock at workout start)
  val durationSeconds: Int? = null,
  val targetRounds: Int? = null,
  val emomRoundSeconds: Int? = null,
  val tabataWorkSeconds: Int? = null,
  val tabataRestSeconds: Int? = null,
  val tabataSkipLastRest: Int? = null,
  val setupSecondsOverride: Int? = null,
  val warnAtSecondsOverride: Int? = null,
  // Result fields (populated at block finish)
  val totalRounds: Int? = null,                     // AMRAP: rounds completed; RFT: rounds completed; TABATA: rounds completed
  val extraReps: Int? = null,                       // AMRAP: reps into next round
  val finishTimeSeconds: Int? = null,               // RFT: total elapsed; EMOM/TABATA: total elapsed
  val rpe: Int? = null,                             // 1–10 user self-rating (overall; mutually exclusive with perExerciseRpeJson)
  val perExerciseRpeJson: String? = null,           // JSON map exerciseId→rpe (per-exercise; mutually exclusive with rpe)
  val roundTapLogJson: String? = null,              // JSON: [{round,elapsedMs}, ...] — per-round tap timestamps for analytics
  val blockNotes: String? = null,
  // Lifecycle
  val runStartMs: Long? = null,                     // wall-clock epoch at block start; for resume-from-kill
  // Sync columns (v35 pattern)
  @ColumnInfo(defaultValue = "") val syncId: String = UUID.randomUUID().toString(),
  @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
)
```

### 3.3 Diffs on existing entities

**`Exercise.kt`** — add after `userNote`:
```kotlin
@ColumnInfo(defaultValue = "") val tags: String = "[]"  // JSON array, e.g. ["functional","olympic"]
```

**`RoutineExercise.kt`** — add after existing per-set JSON columns:
```kotlin
@ColumnInfo(defaultValue = "null") val blockId: String? = null     // FK → routine_blocks.id
@ColumnInfo(defaultValue = "null") val holdSeconds: Int? = null    // populated for any time-capped row in a non-STRENGTH block regardless of master Exercise.exerciseType; see §10 and §12 Invariant #10
```

**`WorkoutSet.kt`** — add after `supersetGroupId`:
```kotlin
@ColumnInfo(defaultValue = "null") val blockId: String? = null     // FK → workout_blocks.id
```

### 3.4 New DAO interfaces

**`RoutineBlockDao.kt`**
```kotlin
@Dao
interface RoutineBlockDao {
  @Query("SELECT * FROM routine_blocks WHERE routineId = :routineId ORDER BY `order` ASC")
  fun getBlocksForRoutine(routineId: String): Flow<List<RoutineBlock>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(block: RoutineBlock)
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(blocks: List<RoutineBlock>)
  @Delete suspend fun delete(block: RoutineBlock)
  @Query("DELETE FROM routine_blocks WHERE routineId = :routineId") suspend fun deleteAllForRoutine(routineId: String)
  @Query("UPDATE routine_blocks SET updatedAt = :ts WHERE id = :id") suspend fun touch(id: String, ts: Long)
}
```

**`WorkoutBlockDao.kt`**
```kotlin
@Dao
interface WorkoutBlockDao {
  @Query("SELECT * FROM workout_blocks WHERE workoutId = :workoutId ORDER BY `order` ASC")
  fun getBlocksForWorkout(workoutId: String): Flow<List<WorkoutBlock>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(block: WorkoutBlock)
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(blocks: List<WorkoutBlock>)
  @Query("UPDATE workout_blocks SET totalRounds = :r, extraReps = :er, finishTimeSeconds = :ft, rpe = :rpe, blockNotes = :notes, updatedAt = :ts WHERE id = :id")
  suspend fun saveResult(id: String, r: Int?, er: Int?, ft: Int?, rpe: Int?, notes: String?, ts: Long)
  @Query("UPDATE workout_blocks SET runStartMs = :ms WHERE id = :id") suspend fun setRunStart(id: String, ms: Long)
  @Query("UPDATE workout_blocks SET updatedAt = :ts WHERE id = :id") suspend fun touch(id: String, ts: Long)
}
```

---

## 4. Migrations

**DB version bumps:** v48 → v49 (Exercise.tags) → v50 (blocks + blockId FKs + holdSeconds).

### `MIGRATION_48_49` — add `Exercise.tags` column

```kotlin
private val MIGRATION_48_49 = object : Migration(48, 49) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE exercises ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
  }
}
```

### `MIGRATION_49_50` — block tables + backfill

```kotlin
private val MIGRATION_49_50 = object : Migration(49, 50) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // 1. Create routine_blocks
    db.execSQL("""
      CREATE TABLE IF NOT EXISTS routine_blocks (
        id TEXT NOT NULL PRIMARY KEY,
        routineId TEXT NOT NULL,
        `order` INTEGER NOT NULL,
        type TEXT NOT NULL,
        name TEXT,
        durationSeconds INTEGER,
        targetRounds INTEGER,
        emomRoundSeconds INTEGER,
        tabataWorkSeconds INTEGER,
        tabataRestSeconds INTEGER,
        tabataSkipLastRest INTEGER,
        setupSecondsOverride INTEGER,
        warnAtSecondsOverride INTEGER,
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (routineId) REFERENCES routines(id) ON DELETE CASCADE
      )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_blocks_routineId ON routine_blocks(routineId)")

    // 2. Create workout_blocks
    db.execSQL("""
      CREATE TABLE IF NOT EXISTS workout_blocks (
        id TEXT NOT NULL PRIMARY KEY,
        workoutId TEXT NOT NULL,
        `order` INTEGER NOT NULL,
        type TEXT NOT NULL,
        name TEXT,
        durationSeconds INTEGER,
        targetRounds INTEGER,
        emomRoundSeconds INTEGER,
        tabataWorkSeconds INTEGER,
        tabataRestSeconds INTEGER,
        tabataSkipLastRest INTEGER,
        setupSecondsOverride INTEGER,
        warnAtSecondsOverride INTEGER,
        totalRounds INTEGER,
        extraReps INTEGER,
        finishTimeSeconds INTEGER,
        rpe INTEGER,
        perExerciseRpeJson TEXT,
        roundTapLogJson TEXT,
        blockNotes TEXT,
        runStartMs INTEGER,
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL DEFAULT 0,
        FOREIGN KEY (workoutId) REFERENCES workouts(id) ON DELETE CASCADE
      )
    """.trimIndent())
    db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_blocks_workoutId ON workout_blocks(workoutId)")

    // 3. Add blockId + holdSeconds to routine_exercises
    db.execSQL("ALTER TABLE routine_exercises ADD COLUMN blockId TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE routine_exercises ADD COLUMN holdSeconds INTEGER DEFAULT NULL")

    // 4. Add blockId to workout_sets
    db.execSQL("ALTER TABLE workout_sets ADD COLUMN blockId TEXT DEFAULT NULL")

    // 5. Backfill: one STRENGTH block per routine → point all existing routine_exercises at it
    //    Use lower(hex(randomblob(4)))||'-'||... would be platform-specific; use the routineId-derived UUID idiom
    //    (See DatabaseModule.kt:454 for the sqlUuid expression used in v34→v35)
    db.execSQL("""
      INSERT INTO routine_blocks (id, routineId, `order`, type, syncId, updatedAt)
      SELECT lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||
             substr(lower(hex(randomblob(2))),2)||'-'||
             substr('89ab', abs(random()) % 4 + 1, 1)||
             substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))),
             id, 0, 'STRENGTH', '', 0
      FROM routines
    """.trimIndent())
    db.execSQL("""
      UPDATE routine_exercises
      SET blockId = (
        SELECT rb.id FROM routine_blocks rb WHERE rb.routineId = routine_exercises.routineId LIMIT 1
      )
      WHERE blockId IS NULL
    """.trimIndent())

    // 6. Backfill: one STRENGTH block per workout → point all existing workout_sets at it
    db.execSQL("""
      INSERT INTO workout_blocks (id, workoutId, `order`, type, syncId, updatedAt)
      SELECT lower(hex(randomblob(4)))||'-'||lower(hex(randomblob(2)))||'-4'||
             substr(lower(hex(randomblob(2))),2)||'-'||
             substr('89ab', abs(random()) % 4 + 1, 1)||
             substr(lower(hex(randomblob(2))),2)||'-'||lower(hex(randomblob(6))),
             id, 0, 'STRENGTH', '', 0
      FROM workouts
    """.trimIndent())
    db.execSQL("""
      UPDATE workout_sets
      SET blockId = (
        SELECT wb.id FROM workout_blocks wb WHERE wb.workoutId = workout_sets.workoutId LIMIT 1
      )
      WHERE blockId IS NULL
    """.trimIndent())
  }
}
```

**Pre-migration validation required** (add to `PreMigrationValidator`):
- Count `routine_exercises`; assert same count after migration.
- Count `workout_sets`; assert same count after migration.
- Assert every `routine_exercises.blockId IS NOT NULL` after migration.
- Assert every `workout_sets.blockId IS NOT NULL` after migration.

---

## 5. Firestore Sync

### 5.1 Push shape

Blocks are **embedded as child arrays** in their parent doc — not sub-collections. This mirrors how sets are embedded in workout docs today.

`workouts/{workoutId}` doc gains a `blocks: List<Map>` field:
```json
{
  "id": "...",
  "blocks": [
    {
      "id": "...",
      "order": 0,
      "type": "STRENGTH",
      "syncId": "...",
      "updatedAt": 1714000000000
    },
    {
      "id": "...",
      "order": 1,
      "type": "AMRAP",
      "durationSeconds": 720,
      "totalRounds": 8,
      "extraReps": 3,
      "rpe": 8,
      "roundTapLogJson": "[{\"round\":1,\"elapsedMs\":45000},{\"round\":2,\"elapsedMs\":87000}]",
      "syncId": "...",
      "updatedAt": 1714000100000
    },
    {
      "id": "...",
      "order": 2,
      "type": "TABATA",
      "tabataWorkSeconds": 20,
      "tabataRestSeconds": 10,
      "tabataSkipLastRest": 0,
      "totalRounds": 8,
      "finishTimeSeconds": 240,
      "perExerciseRpeJson": "{\"exerciseId_abc\": 8}",
      "roundTapLogJson": "[{\"round\":1,\"phase\":\"WORK\",\"elapsedMs\":0},...]",
      "syncId": "...",
      "updatedAt": 1714000200000
    }
  ],
  "sets": [...]
}
```

Each set in the embedded `sets` array gains a `blockId` field referencing the block.

`routines/{routineId}` doc similarly gains a `blocks: List<Map>` field; each element in `exercises` array gains `blockId` and `holdSeconds`.

### 5.2 Pull back-compat

On pull, if a remote workout doc lacks the `blocks` field (pre-v50 cloud doc):
- **Do not synthesize** a new block locally — the local DB already has the backfill block from migration.
- Skip block-reconstruction for that doc entirely; treat its sets as belonging to the single existing local STRENGTH block.

If the remote doc **has** `blocks`: reconstruct all blocks + update `blockId` FK on each local `WorkoutSet`.

**LWW** on blocks: use `block.updatedAt >= local.updatedAt` (same as entity-level LWW already in `pullFromCloud()`).

---

## 6. Exercise DB Expansion

### 6.1 Tags strategy

`Exercise.tags` is a JSON array of strings. Canonical tag values (Phase 2 seed):

| Tag | Meaning |
|---|---|
| `"functional"` | Appears in Functional pickers; hidden from Gym Picker's hard filter |
| `"functional-only"` | Like `"functional"` but ALSO excluded from the dual-use Gym Picker; for movements exclusively used in WODs (Rope Climb, Muscle-Up, Handstand Walk) |
| `"olympic"` | Olympic weightlifting movement (subset of `"functional"`) |
| `"gymnastics"` | Gymnastics/calisthenics WOD movement |
| `"monostructural"` | Cardio conditioning — rowing, biking, running, jump rope |

`familyId` new values: `metcon_family`, `olympic_family`, `gymnastics_family`, `monostructural_family`.

### 6.2 Picker filter rules

| Context | Default filter | Can toggle? |
|---|---|---|
| Exercise Library (browse tab) | None — shows all | Yes: Functional chip narrows to `"functional" in tags` |
| Gym Picker (Strength block add) | Exclude `"functional-only" in tags` | Fixed |
| Functional Picker (Functional block add) | Functional chip **ON** (`"functional" in tags`) | **Yes** — toggling OFF exposes all exercises (Back Squat, Bench Press, Treadmill, etc.) |

The Functional Picker reuses `ExercisesScreen` with `functionalFilter = true` pre-set via nav arg. Toggling the chip off triggers `onFunctionalFilterToggled()` exactly as on the Library screen — no new ViewModel logic needed.

### 6.3 New movements (~40 entries)

**Olympic Lifts** (`exerciseType=STRENGTH`, `familyId=olympic_family`, `tags=["functional","olympic"]`)

- Power Snatch
- Power Clean
- Squat Clean
- Clean & Jerk
- Split Jerk
- Push Jerk
- Hang Power Clean
- Hang Power Snatch
- Clean High Pull

**MetCon Staples** (`tags=["functional"]`)

- Thruster (Barbell + Dumbbell variants) — `familyId=metcon_family`
- Wall Ball — `familyId=metcon_family`
- Box Jump (20", 24", 30") — `exerciseType=PLYOMETRIC`, `familyId=metcon_family`
- Box Step-Up — `exerciseType=PLYOMETRIC`, `familyId=metcon_family`
- Burpee-Over-Bar — `familyId=cardio_family` (Burpee already exists — add tags)
- DB/KB Snatch — `familyId=olympic_family`
- KB Turkish Get-Up — `familyId=metcon_family`
- Medicine Ball Clean — `familyId=metcon_family`
- Sledgehammer Strike — `familyId=metcon_family`, `equipmentType=Other`
- Sled Push (distance-based, `exerciseType=CARDIO`) — `familyId=metcon_family`
- Sled Pull — `familyId=metcon_family`
- Battle Rope Wave (already exists — add tags)
- GHD Sit-Up — `familyId=core_family`
- GHD Hip Extension — `familyId=core_family`

**Gymnastics** (`tags=["functional","gymnastics"]`, `familyId=gymnastics_family`, `equipmentType=Bodyweight`)

- Pull-Up (Strict) — already exists; add `tags=["functional","gymnastics"]`
- Kipping Pull-Up
- Chest-to-Bar Pull-Up
- Bar Muscle-Up
- Ring Muscle-Up
- Ring Dip
- Handstand Push-Up (strict + kipping variants)
- Handstand Walk
- Toes-to-Bar
- Knees-to-Elbows
- L-Sit Hold (`exerciseType=TIMED`)
- Rope Climb (`tags=["functional-only","gymnastics"]`)
- Pistol Squat
- Hollow Hold (`exerciseType=TIMED`, already exists in TIMED entries — add tags)

**Monostructural** (`tags=["functional","monostructural"]`, `exerciseType=CARDIO`, `familyId=monostructural_family`)

- Rowing (meters) — `equipmentType=Machine`
- Rowing (calories) — `equipmentType=Machine`
- Assault Bike (calories) — `equipmentType=Machine`
- Assault Bike (meters) — `equipmentType=Machine`
- Ski Erg (calories) — `equipmentType=Machine`
- Run (400m, 800m, 1600m variants — or one "Run" exercise with distance field)
- Double-Under — already exists; add `tags=["functional","monostructural"]`
- Single-Under — add `tags=["functional","monostructural"]`

**Existing exercises to retag** (add `tags=["functional"]`, keep all other fields):
- Burpee — `+["functional"]`
- KB Swing (Russian + American) — `+["functional"]`
- Barbell Back Squat — **no tag added** (strength-only in standard programming)
- KB Goblet Squat — `+["functional"]`

### 6.4 Seeder bump

`MasterExerciseSeeder.CURRENT_VERSION` increments from `"2.0"` → `"2.1"`. The seeder's "preserve isFavorite/isCustom/syncId on existing rows" path must verify it does not bump `updatedAt` on unchanged rows (would cause a Firestore push storm for all 240 exercises).

---

## 7. Settings — `WorkoutStyle` preference

### 7.1 Enum

```kotlin
// app/src/main/java/com/powerme/app/data/WorkoutStyle.kt
enum class WorkoutStyle { PURE_GYM, PURE_FUNCTIONAL, HYBRID }
```

### 7.2 DataStore (follow `ThemeMode`/`UnitSystem` pattern exactly)

In `AppSettingsDataStore.kt`:
```kotlin
private val WORKOUT_STYLE_KEY = stringPreferencesKey("workout_style")

val workoutStyle: Flow<WorkoutStyle> = ctx.dataStore.data.map {
  it[WORKOUT_STYLE_KEY]?.let { name -> WorkoutStyle.entries.firstOrNull { e -> e.name == name } }
    ?: WorkoutStyle.HYBRID
}

suspend fun setWorkoutStyle(value: WorkoutStyle) =
  ctx.dataStore.edit { it[WORKOUT_STYLE_KEY] = value.name }
```

### 7.3 SettingsViewModel

Add `val workoutStyle: WorkoutStyle = WorkoutStyle.HYBRID` to `SettingsUiState`. Collect in `loadAppSettings()`. Setter:
```kotlin
fun setWorkoutStyle(style: WorkoutStyle) {
  viewModelScope.launch {
    appSettingsDataStore.setWorkoutStyle(style)
    _uiState.update { it.copy(workoutStyle = style) }
    firestoreSyncManager.pushAppPreferences()
  }
}
```

### 7.4 Settings screen

Insert a `SettingsCard(title = "Workout Style")` between the **Units** card and the **Health Connect** card. Use `SingleChoiceSegmentedButtonRow` with three options: "Pure Gym", "Pure Functional", "Hybrid". Match the visual pattern of the Appearance and Units cards.

---

## 8. Template Builder UX

### 8.1 Per-mode dispatch (in `TemplateBuilderScreen.kt`)

When the user taps the "+" / "Add" button:

```
workoutStyle == PURE_GYM
  → navController.navigate("exercise_picker")   [unchanged legacy path]

workoutStyle == PURE_FUNCTIONAL
  → show FunctionalBlockWizard bottom sheet

workoutStyle == HYBRID
  → show AddBlockOrExerciseSheet bottom sheet
    ├── "Add Strength Exercise" → navController.navigate("exercise_picker")
    └── "Add Functional Block" → show FunctionalBlockWizard bottom sheet
```

### 8.2 `FunctionalBlockWizard` — 3-step bottom sheet

**Step 1 — Block type**
Four large tappable tiles: `AMRAP`, `RFT`, `EMOM`, `TABATA` with icon + one-line description.

**Step 2 — Block parameters**
- AMRAP: time-cap picker (minutes wheel, 1–60 min). Auto-name: "AMRAP 12min".
- RFT: target rounds stepper (1–20 rounds) + optional "Time cap [mm:ss]" field (empty = no cap; stored as `durationSeconds`). Auto-name: "5RFT"; with cap: "5RFT / 25min cap".
- EMOM: total duration picker (minutes) + interval-length preset chips `[60s (EMOM)] [90s] [2min] [3min] [5min]` + "Other [mm:ss]". Auto-name: "EMOM 10min", "E2MOM 10min", "E3MOM 15min". Stored as `emomRoundSeconds`.
- TABATA: Work stepper (default 20s) + Rest stepper (default 10s) + Rounds stepper (default 8) + "Skip last rest" switch. Auto-name: "Tabata 8rds". Defaults from `ToolsUiState` (`ToolsViewModel.kt:56–71`).
- Block name field (optional, auto-filled from above).
- **Advanced ▾ (collapsible row):** "Setup [N]s" + "Warn at [N]s". Blank = use settings defaults (`timedSetSetupSeconds` + `resolveWarnAt`). Stored as `setupSecondsOverride?` / `warnAtSecondsOverride?` on `RoutineBlock`.

**Step 3 — Add exercises (Functional Picker)**
Multi-select `ExercisesScreen` entered with `functionalFilter = true` pre-set (Functional chip ON). User can toggle the chip OFF to expose **any** exercise including Back Squat, Bench Press, Treadmill, or any non-tagged exercise. Grouped by `familyId`. Selected exercises become `RoutineExercise` rows with `blockId` pointing at the new block.

Each exercise row in the template shows a quantity stepper alongside a small `[Reps] [Time]` segmented toggle (right-aligned, `bodySmall`, 32dp height). Toggle behaviour varies by block type:
- **AMRAP / RFT:** `[Reps]` selected → qty stepper (saves to `RoutineExercise.reps`, sets `holdSeconds = null`). `[Time]` selected → `[mm:ss]` picker (saves to `RoutineExercise.holdSeconds`, ignores `reps`). Default chosen by heuristic: `TIMED`-typed exercises default to `[Time]` with `holdSeconds = Exercise.restDurationSeconds ?: 30`; all other types (`STRENGTH`, `PLYOMETRIC`, `CARDIO`) default to `[Reps]`.
- **EMOM / TABATA:** toggle is hidden entirely; only the reps stepper is shown. `holdSeconds` is forced to `null` on save. If an exercise row is drag-copied from an AMRAP/RFT block into an EMOM/TABATA block, its `holdSeconds` is reset to `null` automatically.

Switching `[Reps] ↔ [Time]` with a value already entered triggers a confirmation dialog before discarding the other field.

The template list shows `QTY × Exercise Name` (rep-capped) or `Xs/Xmin Exercise Name` (time-capped) per §10 rendering rules.

### 8.3 Block sections in `TemplateBuilderScreen`

The exercise list becomes a block-sectioned list:
- `BlockHeader` item: block name + type badge + parameters summary ("12min cap", "5 rounds", "10min · 60s/round").
- Per-exercise rows below (reordering allowed within a block; reordering blocks themselves via a block-level drag handle).
- Per-block "Add exercise" + "Delete block" actions in the block header overflow menu.
- STRENGTH blocks behave identically to the legacy template — no changes for Pure Gym users.

### 8.4 Mixed Block Routine Example (Hybrid)

A single routine can interleave STRENGTH and functional blocks in any order:

```
Routine: "Leg Day + Metcon Finisher"
  Block 1 [STRENGTH]
    - Barbell Back Squat  5×5
    - Romanian Deadlift   3×8
    - Bulgarian Split Squat 3×10

  Block 2 [AMRAP 8:00]
    - 15 × Wall Ball
    - 10 × Box Jump 24"
    - 200m Row

  Block 3 [TABATA 20/10 ×8]
    - Burpees
```

In `TemplateBuilderScreen`: Block 1 renders the legacy `RoutineBuilderCard` style (no block header if it's the only STRENGTH block). Blocks 2 and 3 each render a `BlockHeader` with badge + parameter summary. Drag handles allow reordering blocks; exercise reordering works within each block.

In `ActiveWorkoutScreen`: Block 1 exercises use the existing `ActiveWorkoutExerciseCard`. Blocks 2 and 3 render `BlockHeader` with `[▶ START BLOCK]` button → overlay.

---

## 9. Live Workout UX — The Four Screens

### 9.1 Shared chassis (`FunctionalBlockOverlay`)

Full-screen overlay rendered above `ActiveWorkoutScreen`. Three vertically-stacked zones:

```
┌─────────────────────────────────────────────────┐
│ Top bar (56dp + status-bar inset)               │
│  [←] Block name badge   [⏸ PAUSE]   [✕ CLOSE]  │
├─────────────────────────────────────────────────┤
│                                                 │
│   TIMER ZONE  (~28% viewport height)            │
│   Phase-tinted background; big digits           │
│                                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│   RECIPE ZONE  (~28% viewport height)           │
│   Scrollable list of exercise rows; no input    │
│                                                 │
├─────────────────────────────────────────────────┤
│                                                 │
│   ACTION ZONE  (~40-44% viewport height)        │
│   AMRAP: BlindTapZone                           │
│   RFT:   [FINISH WOD] button                   │
│   EMOM:  Round ring + Complete/Skip             │
│                                                 │
│   [16dp bottom safe-area inset]                 │
└─────────────────────────────────────────────────┘
```

### 9.2 Typography roles (add to `Type.kt`)

```kotlin
// In Type.kt, alongside MonoTextStyle:
val TimerDigitsXL = MonoTextStyle.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
val TimerDigitsL  = MonoTextStyle.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold)
val TimerDigitsM  = MonoTextStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium)
```

`MonoTextStyle` already sets `fontFeatureSettings = "tnum"` for tabular figures. All three inherit it.

**JetBrains Mono font:** Add `GoogleFont("JetBrains Mono")` in `Type.kt` where `JetBrainsMono` is currently mapped to `FontFamily.Monospace` (line 32). Use `isLoadingPlaceholderEnabled = true` to prevent FOIT. This affects every timer in the app (Clocks tab, rest timers, standalone timer overlay) — a welcome consistency upgrade.

### 9.3 Color + haptic mapping

| Context | Background tint | Digit color | Alert |
|---|---|---|---|
| SETUP (get ready countdown) | `ReadinessAmber.copy(alpha=0.12f)` | `ReadinessAmber` | none |
| WORK (AMRAP, RFT, EMOM interval, TABATA WORK) | `TimerGreen.copy(alpha=0.08f)` | `TimerGreen` | — |
| REST (TABATA REST phase) | `ReadinessAmber.copy(alpha=0.12f)` | `ReadinessAmber` | — |
| AMRAP last 10s | `TimerRed.copy(alpha=0.12f)` | `TimerRed` | `COUNTDOWN_TICK` per second |
| EMOM last 3s of interval | unchanged | unchanged | `COUNTDOWN_TICK` per second |
| Blind tap (each tap) | flash `NeonPurple.copy(alpha=0.22f)` | — | `ROUND_START` waveform (600ms beep + `[0,150,150,150,150,500]` haptic) |
| FINISH WOD press | scale 0.98f | — | `FINISH` waveform |
| EMOM interval boundary | phase color swap | — | `ROUND_START` alert |

### 9.4 Screen A — AMRAP Live

**Timer zone:** `TimerDigitsXL` mm:ss countdown. Transitions `TimerGreen → TimerRed` at `remaining ≤ 10s`.

**Recipe zone:** static list of `BlockRecipeRow(qty, exerciseName, holdSeconds?)` — read-only. `bodyLarge` Barlow Regular.

**Action zone — `BlindTapZone`:**
- Min height: `max(40% viewport, 280dp)`.
- Background: `NeonPurple.copy(alpha=0.12f)` on `surface`.
- Content (centered): `"ROUND"` label (`labelMedium`) → current round number (`TimerDigitsL`) → `"TAP ANYWHERE"` hint (`labelSmall`, 60% alpha).
- Visual: `+1 ROUND` button overlay (visible affordance; entire zone is also clickable — satisfies `gesture-alternative`).
- Interaction: `Modifier.combinedClickable` → scale 0.98f spring animation (150ms) + NeonPurple flash. Ripple disabled (scale feels better here).
- Debounce: 250ms between consecutive taps (accidental protection without requiring long-press).
- Undo: if last tap ≤3s ago, an "UNDO" pill appears at top-right of zone (3s timeout, then fades).
- Accessibility: `role = Button`, `contentDescription = "Tap to log one round. Currently $rounds rounds."`. Each tap fires `announceForAccessibility("Round $newRound logged")`.

**00:00 finish:** stop runner → present `BlockFinishSheet`.

**`BlockFinishSheet`** (24dp large shape, 50% scrim):
- "Final tally" header (`headlineSmall`).
- Two steppers side-by-side: "Rounds [N ±]" and "Extra reps [N ±]" — 48dp ± buttons, numbers in `TimerDigitsL`.
- **RPE section** — segmented control `[Overall] [Per exercise]`:
  - Overall (default): 10 chips in two rows (reuse `RpePickerSheet` from `ActiveWorkoutScreen.kt:2590`). Saves to `WorkoutBlock.rpe`.
  - Per exercise: one chip row per recipe exercise. Saves to `WorkoutBlock.perExerciseRpeJson`. Switching clears the other field.
- Optional `OutlinedTextField` for notes.
- Primary CTA "Save Block" + secondary "Discard".
- Dismiss-with-unsaved confirm dialog.
- Each blind-tap appends `{round: N, elapsedMs}` to `WorkoutBlock.roundTapLogJson` in real-time.

### 9.5 Screen B — RFT Live

**Timer zone:** `TimerDigitsXL` count-up stopwatch. `TimerDigitsM` centiseconds with 55% alpha (same pattern as `ToolsScreen.kt:353–358`). Background: `TimerGreen.copy(alpha=0.08f)`.

**Recipe zone:** shows target round count at top ("5 ROUNDS"). Live pill "Round 2 of 5" updates as user taps rounds complete.

**Action zone:**
- Full-width `[FINISH WOD]` button. Height: 72dp. BarlowCondensed Bold, `headlineSmall`. `TimerGreen` background, `onPrimary` text.
- Subtitle below button: "Stops clock at 08:24" (live-updating — motion meaning).
- Press → confirmation dialog: "End workout at 08:24?" (prevents fat-finger stops).
- Secondary `[ROUND ✓]` button (48dp, `outlineVariant`, below FINISH WOD) — records a round split in `roundTapLogJson` without ending the block. Pre-fills rounds completed in `BlockFinishSheet`.

**Optional time cap:** When `WorkoutBlock.durationSeconds` is set, the timer zone shows the count-up stopwatch + a smaller remaining-to-cap counter below it (warning tint at ≤10s to cap). At cap expiry the `BlockFinishSheet` is auto-presented with elapsed = cap and rounds = count of `[ROUND ✓]` taps. Cap auto-finish fires the same `FINISH` alert (sound + haptic) as manual finish.

**`BlockFinishSheet` (RFT variant):**
- Captured time displayed at top ("18:42").
- Rounds stepper ("4 of 5 rounds") — pre-filled with `[ROUND ✓]` tap count; user can adjust for partial credit.
- **RPE section** — `[Overall] [Per exercise]` toggle (same pattern as AMRAP finish sheet above).
- Notes field.

### 9.6 Screen C — EMOM Live

**Timer zone:**
- `CircularProgressIndicator` ring (320dp diameter, 8dp stroke, `TimerGreen`) — drains left-to-right within each interval. Uses `transform` only (no `height`/`width` animation — `transform-performance` rule).
- Inside ring: `TimerDigitsXL` remaining seconds of current interval.
- Below ring: `"Round X of Y — [emomRoundSeconds]s interval"` (`titleMedium` BarlowCondensed). E.g. "Round 3 of 10 — 2min interval" for E2MOM.

**Recipe zone:** shows prescribed work for the current round, highlighted. Next round's work at 60% alpha.

**Action zone** (smaller: ~28% height — EMOM is hands-on, no blind tap needed):
- `[COMPLETED ✓]` button (`TimerGreen`, 56dp height) — logs round as done; appends `{round, elapsedMs, completed: true}` to `roundTapLogJson`.
- `[SKIP ROUND]` button (secondary, `outlineVariant`, 48dp) — marks round skipped; appends `{round, elapsedMs, completed: false}`.

**Interval boundary:** `ROUND_START` alert (inherited from `TimerEngine.runEmom` — fires for each `emomRoundSeconds` interval automatically). Supports variable intervals (60s, 90s, 120s, etc.).

**`BlockFinishSheet` (EMOM variant):** rounds completed + skip rate (display-only) + **RPE section** with `[Overall] [Per exercise]` toggle + notes.

### 9.7 Screen D — Tabata Live

**Timer zone:**
- `CircularProgressIndicator` ring (320dp/8dp) — drains per phase; resets fully at each phase boundary.
  - WORK phase: ring + background `TimerGreen.copy(alpha=0.08f)`.
  - REST phase: ring + background `ReadinessAmber.copy(alpha=0.12f)`.
- Inside ring: `TimerDigitsXL` remaining seconds of current phase.
- Below ring: phase label `"WORK"` / `"REST"` + `"Round X of Y"` (`titleMedium` BarlowCondensed).

**Recipe zone:** prescribed work shown during WORK; `"Rest"` during REST.

**Action zone:** No blind tap, no finish button — Tabata is auto-run. Optional `[SKIP REMAINING]` (small, tertiary) available during WORK for early abort.

**Alerts (all via TimerEngine, no extra code):**
- `ROUND_START` at each WORK phase start.
- `COUNTDOWN_TICK` last 3s of each phase.
- `WARNING` at configured threshold.
- `FINISH` on last round completion.

Each phase boundary auto-appends `{round: N, phase: "WORK"|"REST", elapsedMs}` to `roundTapLogJson`.

**`BlockFinishSheet` (Tabata):** elapsed time (pre-filled, display-only) + rounds-completed stepper (pre-filled with `totalRounds`) + **RPE section** with `[Overall] [Per exercise]` toggle + notes.

### 9.8 Pre-start state (all four screens)

Block header in `ActiveWorkoutScreen` shows a `▶ START BLOCK` button. Tapping opens the overlay in `SETUP` phase.

**Setup countdown length** = `setupSecondsOverride` if set on the block, otherwise `timedSetSetupSeconds` from `AppSettingsDataStore` (the same pref used by timed-set pre-start). If the resolved value is 0, the SETUP phase is skipped entirely and the timer goes directly to WORK. Uses `TimerDigitsXL` digits in `ReadinessAmber`.

### 9.9 Paused state

Overlay dims the action zone to 60% alpha. Tap zone disabled. Center shows `"PAUSED"` (`titleLarge`). Tapping anywhere outside the block recipe → `WorkoutViewModel` confirms with edit guard if applicable.

### 9.10 Summary card (post-block, inline)

After `BlockFinishSheet` saved, the block header in `ActiveWorkoutScreen` becomes a summary card:
- AMRAP: "AMRAP 12:00 — 8 rounds + 3 reps · RPE 8"
- RFT: "5 Rounds — 18:42 · RPE 7"
- EMOM: "E2MOM 10min — 10/10 rounds · RPE 8"
- TABATA: "Tabata 8rds — 20/10 · RPE 7"

When `perExerciseRpeJson` was used, headline shows avg RPE: "RPE avg 7.5"; tap the card to see per-exercise breakdown.

User can tap to re-edit the score (opens `BlockFinishSheet` again, pre-filled).

### 9.11 Accessibility

- Blind tap zone: `role = Button` + live `contentDescription` + `announceForAccessibility` per tap.
- All haptics respect `AccessibilityManager.isTouchExplorationEnabled()` — do not suppress haptics for TalkBack users (haptic is informational, not decorative).
- Dynamic Type: `TimerDigitsXL` at AX5 (~144sp) may clip. Use `Modifier.wrapContentHeight` + `Modifier.fillMaxWidth()` with `AutoSize` fallback at AX4+.
- Reduced-motion: scale animations → crossfade; ring drain animation → stepped bar. Color transitions preserved (they carry phase meaning, not decoration).
- Safe-area: top bar respects status bar inset via `WindowInsets.statusBars`. Bottom action zone reserves `16dp + WindowInsets.navigationBars` at the bottom.

---

## 10. Mixed Reps/Time in a Block

`RoutineExercise.holdSeconds` takes precedence in rendering — it is a **per-prescription** decision, not a property of the master `Exercise.exerciseType`. Rendering rule:

```
if (holdSeconds != null)       → "${formatSeconds(holdSeconds)} $exerciseName"     // e.g. "1min Double Unders", "30s Plank"
else if exerciseType == CARDIO → "$qty $unit $exerciseName"                         // e.g. "500m Row", "20 cal Bike"
else                           → "$qty × $exerciseName"                             // e.g. "15 × Wall Ball"
```

`formatSeconds(n: Int): String` helper: `n < 60 → "${n}s"` · `n >= 60 && n % 60 == 0 → "${n/60}min"` · else `"${mm}:${ss}"`.

`RoutineExercise.holdSeconds: Int?` stores the prescribed hold duration for one exercise instance in a block. This is the per-prescription value; `Exercise.restDurationSeconds` is the master default and is unrelated.

**v1 limitation:** the in-block timer does NOT auto-advance when a time-capped exercise's `holdSeconds` elapses. The prescription is descriptive — the user self-paces (same as a coached CrossFit class). Execution-side enforcement (automatic advance after `holdSeconds`) is a v1.1 candidate.

---

## 11. Lifecycle — Foreground-Service Parity

### 11.1 Architecture

`WorkoutTimerService` (existing bound foreground service) hosts `FunctionalBlockRunner` alongside the existing rest-timer logic. No new service needed.

`FunctionalBlockRunner` is a plain class injected via Hilt into `WorkoutViewModel`. It:
1. Receives a `TimerSpec.Amrap / Rft / Emom / Tabata` from `WorkoutViewModel.startFunctionalBlock(blockId)`. Setup seconds come from `block.setupSecondsOverride ?: appSettingsDataStore.timedSetSetupSeconds`.
2. Calls `WakeLockManager.acquire()`.
3. Records `WorkoutBlockDao.setRunStart(blockId, System.currentTimeMillis())`.
4. Runs the `TimerEngine.run(spec)` coroutine.
5. On finish: persists the score via `WorkoutBlockDao.saveResult(...)`, calls `WakeLockManager.release()`, and signals `WorkoutViewModel` to show `BlockFinishSheet`.
6. On pause: cancels the `TimerEngine` job, calls `WakeLockManager.release()`.
7. On resume: re-enters `TimerEngine.run(spec)` with the appropriate remaining seconds.

### 11.2 Resume-from-kill

If the process is killed mid-AMRAP, `WorkoutBlock.runStartMs` is non-null and `totalRounds` is null. On the next app launch, `WorkoutViewModel` detects this "orphaned" block and:
- Reconstructs elapsed seconds from `System.currentTimeMillis() - runStartMs`.
- For AMRAP: if `elapsed < durationSeconds`, resumes with remaining = `durationSeconds - elapsed`. If elapsed ≥ durationSeconds, shows the finish sheet immediately.
- For RFT: resumes the count-up from `elapsed`.
- For EMOM: resumes at the correct round based on `elapsed ÷ emomRoundSeconds`.
- For TABATA: `cycleSeconds = tabataWorkSeconds + tabataRestSeconds`. `round = elapsed ÷ cycleSeconds`. Phase = `(elapsed mod cycleSeconds) < tabataWorkSeconds ? WORK : REST`. Remaining phase seconds computed accordingly.

### 11.3 Ongoing notification

Same channel as the existing rest-timer notification. While a functional block runs:
- AMRAP: "AMRAP — 08:24 remaining · Round 3"
- RFT: "RFT — 05:12 elapsed · 2 of 5 rounds"
- EMOM: "EMOM — Round 4 of 10 · 35s remaining"
- TABATA: "Tabata — Round 5 of 8 · WORK · 14s remaining"

Tapping the notification opens the active workout screen (existing foreground-notification tap handler).

### 11.4 Mutual exclusion with rest timers

`FunctionalBlockRunner.isActive` gates `WorkoutViewModel.startRestTimer()` — rest timers do not fire during an active functional block. On block finish, the gate lifts.

---

## 12. Technical Invariants

These must hold across all implementations. Violating any is a regression.

1. **STRENGTH blocks behave exactly like legacy.** Any workout with only STRENGTH blocks must look and function identically to today. No regressions for Pure Gym users.
2. **Supersets are orthogonal to blocks.** `supersetGroupId` remains on `RoutineExercise` and `WorkoutSet` unchanged. Exercises in a STRENGTH block can still be supersetted.
3. **`workout_sets` aggregations are never polluted.** No synthetic "summary set" rows. All functional scores live on `WorkoutBlock` columns only.
4. **Rest timers and functional block timers are mutually exclusive.** `WorkoutViewModel.startRestTimer` is a no-op when `FunctionalBlockRunner.isActive`.
5. **`ActiveWorkoutState` nesting.** Add `functionalBlockState: FunctionalBlockRunnerState?` as a nested type, not flattened fields. The existing 25-field state struct is already large.
6. **Wake lock discipline.** `WakeLockManager.acquire()` on block start; `release()` on finish, pause, or `ViewModel.onCleared()`. Leaked wake locks drain battery.
7. **Backfill blocks are transparent.** After migration, existing users must see zero UI change. The new block header for a STRENGTH block is only rendered if the workout has ≥2 blocks of mixed types. A single STRENGTH block = no header (legacy visual).
8. **`blockId IS NULL` rows are handled gracefully.** If a `WorkoutSet` or `RoutineExercise` has `blockId = NULL` (corrupt migration, external import, etc.), it renders as part of an implicit unnamed STRENGTH block rather than crashing.
9. **Exactly one RPE field is non-null per block.** `WorkoutBlock.rpe` and `WorkoutBlock.perExerciseRpeJson` are mutually exclusive. Both `NULL` = user skipped RPE. Both populated = rejected by `WorkoutBlockDao.saveResult`. In Trends/summary, when only `perExerciseRpeJson` is present, headline RPE = mean of its values.
10. **Exactly one quantity mode per exercise row inside non-STRENGTH blocks.** For any `RoutineExercise` row in an AMRAP or RFT block: either `holdSeconds != null` (time-capped, `reps` ignored) or `holdSeconds == null` and `reps > 0` (rep-capped). Both null or both populated = validator rejects. Inside EMOM and TABATA blocks, `holdSeconds` is always `null` (wizard enforces; validator asserts). Inside STRENGTH blocks, this invariant does not apply — legacy per-set reps/weights pattern governs.

---

## 13. Out of Scope for v1

These are explicitly deferred. Do not implement them during P8 execution.

- **Per-movement AMRAP rep tracking.** User enters total rounds + extra reps only. No "how many Wall Balls did you do."
- **EMOM custom work/rest split within an interval.** v1 EMOM and Tabata use the built-in work/rest fields. A full custom ":40s work / :20s rest" per-minute split beyond Tabata's fixed W/R is deferred.
- **Partial-round tally.** AMRAP extra-reps counts reps into the next partial round — it does NOT track which specific exercises were completed in that partial round.
- **Leaderboards / social comparison.** Scores are personal.
- **AI functional block generation.** `AiWorkoutViewModel` can be extended in a later phase. For now, functional blocks are author-only.
- **RX vs scaled tracking.** Users adjust weight/rep targets manually. No "RX / Scaled" flag.
- **Execution-side enforcement of per-exercise `holdSeconds`.** v1 renders the time prescription in the `BlockRecipeRow` but does not auto-advance the in-block timer when that exercise's `holdSeconds` elapses. Full auto-advance is a v1.1 candidate.
- **`[Reps][Time]` toggle on STRENGTH-block rows for non-TIMED masters.** Users who want a 15-min light treadmill run inside a gym day can use a `TIMED`-typed master exercise (e.g. "Treadmill Walk"), which already supports `holdSeconds`. Extending the toggle to STRENGTH-block rows for non-TIMED exercise types is a future extension.
- **Rest between functional blocks.** Each block in a Hybrid workout starts when the user manually taps "Start Block." There is no automatic inter-block rest timer.
- **AMRAP team mode (relay).** Single-user only.
- **Cardio GPS / HC distance capture during a block.** Round count is the scoring primitive. Actual distance/pace sync for running/rowing within a block is a future phase.
- **Per-exercise RPE time-series charts.** Data is captured in `perExerciseRpeJson`; visualisation in Trends is a v1.1 follow-up.
- **Pacing-curve chart.** `roundTapLogJson` data is stored; AMRAP pace-over-sessions visualisation is P8 Tier 5 or later.

---

## 14. Phasing Index

| Tier | Task slug | One-liner | Risk |
|---|---|---|---|
| 0 | `func_style_preference` | WorkoutStyle enum + Settings card | LOW |
| 0 | `func_exercise_tags_seed` | Exercise.tags column + seed + filter | MEDIUM |
| 0 | `func_timer_engine_extract` | Extract TimerEngine, real JetBrains Mono font | HIGH |
| 1 | `func_block_entities_migration` | RoutineBlock + WorkoutBlock + backfill migration | HIGHEST |
| 2 | `func_firestore_sync_blocks` | Firestore block arrays + legacy fallback | MEDIUM |
| 3 | `func_template_wizard` | FunctionalBlockWizard + Pure Functional builder | MEDIUM |
| 3 | `func_template_hybrid_sheet` ✅ | Hybrid AddBlockOrExerciseSheet + Pure Gym preserved | LOW |
| 4 | `func_active_strength_blocks` | Block headers in active workout; STRENGTH materializes | MEDIUM |
| 4 | `func_active_functional_runner` | AMRAP/RFT/EMOM/TABATA overlays + per-exercise RPE + round-tap log + HC block segments + foreground-service lifecycle | HIGHEST |
| 5 | `func_history_trends_polish` | Block-aware History + Trends + SummaryScreen + round-split display | LOW |
| 0 | `func_exercise_gap_analysis` | CrossFit / Hyrox / Calisthenics exercise gap research (no code) | LOW |
| 0 | `func_exercise_expanded_seed` | Seed expanded exercise list from gap analysis | MEDIUM |

**See plan file** `/Users/omerhedvat/.claude/plans/hello-claude-we-are-iterative-wand.md` for the full task-tree dependency structure, gate criteria, and parallelization guidance.

---

*Created: April 2026. Spec owner: Lead Architect session. Implementation has not started.*
*Updated: April 2026 — added Tabata block type, flexible RPE (per-block choice), any-exercise picker (Functional chip toggle-off), all-cardio pickable, setup/warn prefs reuse, RFT optional cap, variable EMOM interval (E2MOM/E3MOM/E5MOM), hybrid routine example, per-round timestamp log, HC block segments + laps.*
