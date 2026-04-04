# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v30, 16 entities, 14 DAOs)
- AI: Google Gemini (War Room chat + action parsing)
- Auth/Backend: Firebase Auth + Firestore
- Health: Health Connect API (`androidx.health.connect:connect-client:1.1.0-rc01`)
- Charts: Vico (Material 3)
- Drag-and-drop: `sh.calvin.reorderable:reorderable-compose:2.4.3`
- Build: Gradle Kotlin DSL, KSP, min SDK 26, target SDK 35

**Package:** `com.powerme.app`

**Main Features Implemented:**
- Workout routine creation and management
- Active workout tracking (sets, reps, weight, duration)
- **Workout/Routine flow** — Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary (uses `skipPartiallyExpanded` and weighted layouts to ensure full visibility above system bars on Samsung/Android 14 devices), template builder, exercise picker. **See WORKOUT_SPEC.md.** Per-set-type rest timer logic: `computeRestDuration(completed, next, default)` in `WorkoutViewModel` returns 0s for DROP sets, 30s for WARMUP→WARMUP, exercise default otherwise.
- Theme mode: `ThemeMode` enum (LIGHT/DARK/SYSTEM) in `data/ThemeMode.kt`; `AppSettingsDataStore.themeMode: Flow<ThemeMode>` + `setThemeMode()`; `PowerMETheme(themeMode: ThemeMode)` applies `LightColorScheme` or `DarkColorScheme` (Stremio dark palette) or follows system; `MainActivity` collects themeMode, calls `enableEdgeToEdge` via `SideEffect` conditionally; Appearance card in SettingsScreen with `SingleChoiceSegmentedButtonRow` (Light/Dark/System); deprecated `darkModeEnabled` / `setDarkModeEnabled` preserved for schema stability
- Google Fonts: `BarlowCondensed` (Medium/SemiBold/Bold) + `Barlow` (Normal/Medium/SemiBold) loaded via `GoogleFont.Provider`; `res/values/font_provider_certs.xml` contains GMS cert arrays; `Typography` expanded to 11 M3 roles (displaySmall, headlineL/M/S, titleL/M, bodyL/M/S, labelMedium/Small); graceful fallback to system sans-serif offline
- Nav tab order: Workouts / History / Exercises / Tools / Trends (Workouts first)
- Exercise library (150+ exercises, YouTube demos via native Intent, muscle groups, equipment types)
- Multi-select muscle group filter + equipment filter chips in Exercises tab (AND-combined filtering). Full spec in `EXERCISES_SPEC.md §4`.
- AI "War Room" chat (Gemini) with action parsing (create routines, update weight, switch gym, update injuries)
- War Room overlay auto-dismissed via DisposableEffect on nav away; user-selected model respected (AppSettingsDataStore)
- War Room clear fully resets Gemini in-memory session (no stale context)
- Gemini model list auto-refreshes every 24h on app start; GymSetupViewModel defers AI buttons until models loaded
- Firebase Auth with email verification + full onboarding flow
- User profile (age, height, weight, gender, goals, chronotype, occupation)
- Gym profiles (multiple gyms, equipment availability, dumbbell ranges); after save → GymInventoryScreen summary
- GymSetup: Barbell toggle controls Bench + Plates visibility; single RangeSlider for dumbbell range (updateDumbbellRange() with min/max coerce guard); AI image analysis and text extraction sections removed; replaced with manual equipment entry (OutlinedTextField + Add IconButton + removable InputChip list via addEquipmentManually())
- GymInventory: high-density FlowRow+SuggestionChip grids for Standard Equipment, Plates, and Additional Equipment sections; all chip colors use MaterialTheme.colorScheme.surfaceVariant/primary/secondary tokens
- Health Connect sync: real SDK queries for sleep (SleepSessionRecord → most-recently-ended session, minutes), HRV (HeartRateVariabilityRmssdRecord → RMSSD ms), RHR (RestingHeartRateRecord → bpm), steps (StepsRecord summed from today midnight); manifest declares READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS; anomaly detection + body weight/body fat read → pre-fill Settings; permission check before sync
- Health Connect: checkPermissionsGranted() for D2; getExerciseSessions() with ExerciseSessionRecord
- Injury / medical ledger (red-list / yellow-list exercises)
- Performance metrics, trends, and charts
- State history auditing trail
- DataStore preferences (plates config, timers, language, modelsLastFetched)
- Clocks tab (Stopwatch, Timer, Tabata, EMOM) with countdown beeps, skip-last-rest, pre-finish alerts, input validation; TABATA and EMOM use persistent side-by-side Start+Reset buttons (always visible, PlayArrow/Pause icons); STOPWATCH and COUNTDOWN use icon-labeled toggle+reset layout
- StatisticalEngine (Epley 1RM, Bayesian M-Estimate 1RM), WeeklyInsightsAnalyzer, AnalyticsRepository, BoazPerformanceAnalyzer (V2 stub). Full spec in `HISTORY_ANALYTICS_SPEC.md`.
- ExercisesScreen: tap opens ExerciseDetailSheet (ModalBottomSheet) with Form Cues (muted gold banner) + YouTube TextButton. Full spec in `EXERCISES_SPEC.md §5–§6`.

**Color System:** Pro Tracker v4.0 palette. Current dark surface values: `StremioBackground=#000000`, `StremioSurface=#252525`, `StremioSurfaceVar=#303030`, `StremioInputPill=#2A2A2A`. Full token reference, ThemeMode system, typography, semantic colors, and token usage rules in `THEME_SPEC.md`.

**Navigation Structure:**
- Auth flow: Welcome → Profile Setup
- Main scaffold: 5 bottom tabs (**Exercises**, **History**, **Workouts**, **Clocks**, **Trends**)
- Overlays: Settings, Gym Setup → Gym Inventory, Active Workout
- War Room route exists but the TopAppBar Forum button is hidden (AI de-coupling phase)

**Database:** Room v30 — migrations covered from v6 → v30. Seeded on startup with 150+ master exercises.
- v16 adds `supersetGroupId TEXT` column to `workout_sets`
- v17 adds `stickyNote TEXT` column to `routine_exercise_cross_ref`
- v18 data-only migration: clears leaked profile metrics from `exercises.setupNotes` (SurgicalValidator.MIGRATION_SQL)
- v19 data-only migration: strips `"181.5 cm:"` prefix from `exercises.setupNotes` that bypassed v18 guard (SurgicalValidator.MIGRATION_SQL_V19)
- v20 schema migration: adds `isArchived INTEGER NOT NULL DEFAULT 0` to `routines`; creates new `routine_exercises` table (id, routineId FK, exerciseId FK, sets, reps, restTime, `order`, supersetGroupId) with indices on routineId+exerciseId
- v21 Data Hardening: `workouts.routineId` made nullable; FK changed to `SET_NULL` (orphan protection); `workouts.isCompleted INTEGER NOT NULL DEFAULT 0` added (settled-data gate); `routine_exercises.stickyNote TEXT` added; `routine_exercise_cross_ref` table dropped (data merged); `RoutineExerciseCrossRef.kt` + `RoutineExerciseCrossRefDao.kt` deleted; sticky-note queries moved to `RoutineExerciseDao`; `getPreviousSessionSets` filters `AND w.isCompleted = 1`; `instantiateWorkoutFromRoutine` wrapped in `withTransaction`; `finishWorkout()` sets `isCompleted=true` + calls `updateLastPerformed`; History tab now shows exercise name chips via `getAllCompletedWorkoutsWithExerciseNames` query
- v22 Iron Vault Sprint: `workout_sets.isCompleted INTEGER NOT NULL DEFAULT 0` added; Iron Vault auto-save wiring in `WorkoutViewModel` (rehydration on init, `startWorkoutFromRoutine()`, debounced weight/reps saves, discrete set-completion + setType DB writes); `cancelWorkout()` deletes orphaned DB records; `finishWorkout()` updates existing Workout row + deletes incomplete sets; SetType uses anchored `DropdownMenu` in `WorkoutSetRow` (see WORKOUT_SPEC.md §10.2); `cycleSetType()` is **hard-deleted** — do not reintroduce; per-set `PlateCalculatorSheet` ModalBottomSheet reading from `UserSettings.availablePlates`; `WorkoutsScreen` gains Resume Workout banner + RoutineCard Start button; shared `WorkoutViewModel` at NavHost level
- v23 Power Grid: adds `defaultWeight TEXT NOT NULL DEFAULT ''` to `routine_exercises` table; supports routine sync change detection on workout finish
- v24 Exercise Normalization: MIGRATION_23_24 — (1) 11 MERGE dedup passes (re-point routine_exercises+workout_sets FK → canonical exercise, delete legacy row); (2) 4 KEEP-BOTH renames (Romanian Deadlift RDL-BB/RDL-DB split, Weighted Pull-Up, Incline Dumbbell Row); (3) equipment normalization (Dumbbells→Dumbbell, Bodyweight+→Bodyweight); (4) muscleGroup normalization (Rear Delts→Shoulders, Lats→Back, Hamstrings→Legs, Triceps/Biceps→Arms, Abs→Core, Upper Chest/Side Delts→Shoulders/Chest, Chest/Triceps→Chest); (5) new `exercise_muscle_groups` table (exerciseId FK, majorGroup, subGroup, isPrimary) populated from muscleGroup + secondary rows for compound exercises; (6) partial UNIQUE index on master exercises (name, equipmentType WHERE isCustom=0)
- DatabaseSeeder reduced to 6 unique legacy entries (13 merge-duplicates removed); MasterExerciseSeeder bumped to v1.2 to force reseed of renamed exercises
- v25 Search Normalization: MIGRATION_24_25 — adds `searchName TEXT NOT NULL DEFAULT ''` to `exercises`; back-fills via SQLite `LOWER(REPLACE(...))` for all existing rows; `Exercise.searchName` field + `fun String.toSearchName()` extension (lowercase, strips hyphens/spaces/parens); `MasterExerciseSeeder` bumped v1.2→v1.3 (populates searchName on insert/update); `DatabaseSeeder` also populates searchName; `ExerciseDao.searchExercises()` targets `searchName` column; `ExerciseRepository.searchExercises()` normalizes query via `toSearchName()`; `ExercisesViewModel.applyFilters()` normalizes once + matches `exercise.searchName`; ExercisesScreen search field gains trailing ✕ clear icon
- v26 Schema Mismatch Fix: MIGRATION_25_26 — drops `idx_exercises_master_unique` partial UNIQUE index (Room cannot represent partial indexes; was causing `IllegalStateException: Migration didn't properly handle: exercises` crash on every cold launch after v24); `Exercise.searchName` gains `@ColumnInfo(defaultValue = "")` annotation to match SQL default set in MIGRATION_24_25
- v27 Index Name Fix: MIGRATION_26_27 — drops `idx_emg_exerciseId` on `exercise_muscle_groups` (created by v24 with wrong name) and recreates as `index_exercise_muscle_groups_exerciseId` (Room's expected convention); fixes `IllegalStateException: Migration didn't properly handle: exercise_muscle_groups` crash on every cold launch since v24
- v28 Set Type Persistence: MIGRATION_27_28 — adds `setTypesJson TEXT NOT NULL DEFAULT ''` to `routine_exercises`; stores per-set types as comma-separated SetType names (e.g. `"NORMAL,WARMUP,NORMAL"`); `startEditMode()` deserializes via `String.toEditModeSetTypes()`; `saveRoutineEdits()` serializes all set types sorted by `setOrder`
- v29 Per-Set Weight/Reps Persistence: MIGRATION_28_29 — adds `setWeightsJson TEXT NOT NULL DEFAULT ''` and `setRepsJson TEXT NOT NULL DEFAULT ''` to `routine_exercises`; stores per-set weights and reps as comma-separated strings (e.g. `"80,85,90"`, `"10,8,6"`); `startEditMode()` deserializes via `String.toEditModeValues()`; `saveRoutineEdits()` serializes sorted by `setOrder`; `defaultWeight`+`reps` kept in sync with set 1 for Diff Engine compatibility
- v30 Workout Timestamps: MIGRATION_29_30 — adds `startTimeMs INTEGER NOT NULL DEFAULT 0` and `endTimeMs INTEGER NOT NULL DEFAULT 0` to `workouts`; `WorkoutRepository.createEmptyWorkout()` + `instantiateWorkoutFromRoutine()` set `startTimeMs = System.currentTimeMillis()`; `WorkoutViewModel.finishWorkout()` sets `endTimeMs`; `HistoryCard` uses `durationMsComputed` (precise ms diff or fallback to `durationSeconds`)
- ExercisesScreen filter chips now DB-driven (SELECT DISTINCT muscleGroup/equipmentType via ExerciseDao + ExerciseRepository + ExercisesViewModel.muscleGroupFilters/equipmentFilters StateFlows); hardcoded MUSCLE_GROUPS/EQUIPMENT_FILTERS constants removed
- Canonical muscle groups (8): Legs, Back, Core, Chest, Shoulders, Full Body, Arms, Cardio (MuscleGroups.kt deleted — constants were unused; DB queries provide filter data)


**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides parseDecimal() (locale-aware, accepts commas+periods), parseReps() (integer only), isLeakedMetric() for runtime checks, MIGRATION_SQL const val for Room @Query and Migration (v17→v18), and MIGRATION_SQL_V19 for the "181.5 cm:" prefix cleanse (v18→v19). No inline try-catch in ViewModels or Composables (ProjectMap §3).

**Health Connect permissions:** READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_EXERCISE, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. Height sync: getLatestHeight() in HealthConnectManager (365-day window); SettingsViewModel saves to both MetricLog (MetricType.HEIGHT) and User entity (dual-sink per ProjectMap §5). MetricType enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT.

**Unit Test Coverage (src/test/, 12 files, ~210 tests total — all passing):**
- `actions/ActionParserTest.kt` — 11 tests
- `actions/ActionExecutorTest.kt` — 10 tests
- `data/ExerciseDaoTest.kt` — DAO tests
- `data/PreMigrationValidatorTest.kt`
- `data/GymProfileRepositoryTest.kt` — 12 tests
- `util/SQLSafetyValidatorTest.kt` — 25 tests
- `util/GeminiResponseLoggerTest.kt`
- `util/SurgicalValidatorTest.kt` — 18 tests (parseDecimal, parseReps, isLeakedMetric)
- `util/PlateCalculatorTest.kt` — 13 tests (calculatePlates, parseAvailablePlates, formatPlateBreakdown)
- `analytics/StatisticalEngineTest.kt` — 13 tests (mean, stdDev, zScore, Pearson, 1RM, Bayesian 1RM, rateOfChange — outlier/quartile tests removed with dead code)
- `ui/history/HistoryViewModelTest.kt` — 12 tests
- `ui/exercises/ExerciseFilterTest.kt` — 7 tests (canonical equipment/muscle-group validation, no-duplicates, legacy value exclusion)
- `ui/workout/WorkoutViewModelTest.kt` — 32 tests (includes 2 completeSet toggle tests + 5 rest timer/override tests + 5 per-set-type rest timer tests + 5 cascade/routine-sync tests + 1 selectSetType + 2 deleteSet timer cancel + 2 deleteRestSeparator + 4 edit mode + 1 helper)

---

## Feature Specs

Read the relevant spec before touching files in that domain.

| Spec File | Status | Domain |
|---|---|---|
| `WORKOUT_SPEC.md` | ✅ Complete | Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary, warmup, notes, minimize/maximize, Iron Vault |
| `EXERCISES_SPEC.md` | ✅ Exists | Exercise library (150+ exercises), search/filter UI, ExerciseDetailSheet, MagicAddDialog, equipment display, YouTube demo intent, picker mode |
| `HISTORY_ANALYTICS_SPEC.md` | ✅ Complete | HistoryScreen layout, StatisticalEngine (Epley/Bayesian 1RM, Volume, dynamic PRs), WeeklyInsightsAnalyzer, WorkoutDetailScreen data contract + retroactive edit flow, BoazPerformanceAnalyzer (V2 stub) |
| `NAVIGATION_SPEC.md` | ✅ Complete | Route map (16 routes), auth decision tree, WorkoutViewModel scope, minimize/maximize state machine, transitions, MainAppScaffold + MainActivity contracts |
| `THEME_SPEC.md` | ✅ Complete | Pro Tracker v4.0 palette (all tokens), DarkColorScheme, LightColorScheme (draft), ThemeMode system, typography (Barlow + BarlowCondensed + JetBrainsMono), semantic color contexts, WCAG contrast audit, token rules |
| `HEALTH_CONNECT_SPEC.md` | 📋 Planned | Permission declaration, sync logic (sleep, HRV, RHR, steps, weight, body fat, height), dual-sink write pattern, anomaly detection |
| `WAR_ROOM_ACTIONS_SPEC.md` | ⚠️ Archive only | Gemini action parsing, ActionBlock, ActionParser, ActionExecutor — stub in `archive/docs/`; no root-level spec yet |
| `GYM_PROFILES_SPEC.md` | ⚠️ Archive only | Gym setup, equipment entry, dumbbell range slider, GymInventoryScreen — stub in `archive/docs/`; no root-level spec yet |
| `DB_UPGRADE.md` | ✅ Exists | Migration history v6→v30, schema changes |
| `HANDOFF.md` | ✅ Session handoff | Phase 2 completion status, Steps E+F full spec, build/test commands, test patterns |

---

## Instructions

### Build & Test Commands

`gradle-wrapper.jar` is missing — `./gradlew` does NOT work. Always use the hardcoded binary path:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Run unit tests
/Users/omerhedvat/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle \
  -p /Users/omerhedvat/git/AndroidStudioProjects-PowerME :app:testDebugUnitTest

# Build debug APK
/Users/omerhedvat/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle \
  -p /Users/omerhedvat/git/AndroidStudioProjects-PowerME :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
Install on emulator: use `mcp__mobile__install_app` tool.

---

### Remaining Work — Phase 2

**Read `HANDOFF.md` for the full spec of each step before starting.**

#### Step E — Exercise Reorder (next up)

**ViewModel (`WorkoutViewModel.kt`):**
- Add `collapsedExerciseIds: Set<Long>` to `ActiveWorkoutState`
- Add `collapseAllExcept(exerciseId: Long)` — collapses all exercises except the given one
- Add `toggleCollapsed(exerciseId: Long)` — toggles a single exercise's collapsed state
- Add `reorderExercises(fromIndex: Int, toIndex: Int)` — reorders the exercises list in state

**UI (`ActiveWorkoutScreen.kt`):**
- Replace `LazyColumn` with `ReorderableLazyColumn` from `sh.calvin.reorderable` (dep already in `app/build.gradle.kts` as `sh.calvin.reorderable:reorderable-compose:2.4.3`)
- Drag handle (`DragHandle` icon) in ExerciseCard header — visible in active mode only, not edit mode
- Tapping an exercise header calls `collapseAllExcept()` — only tapped exercise shows its sets
- Hide sets section when `exerciseId in collapsedExerciseIds`

**Tests (2 new in `WorkoutViewModelTest.kt`):**
- `collapseAllExcept collapses all other exercises and expands the target`
- `reorderExercises moves exercise from one index to another`

#### Step F — Post-Workout Routine Sync Fix

Fix `RoutineSyncType` diff engine in `WorkoutViewModel.finishWorkout()` / `WorkoutRepository.kt`:
- `null` → ad-hoc workout
- `VALUES` → only weight/reps changed
- `STRUCTURE` → exercise list or order changed
- `BOTH` → both structure and values differ

---

### Keeping This Document Current
- **After every change to the project — update the "Current State" section above.** Reflect new features added, features removed, schema changes (Room version bumps), new libraries, architecture decisions, or anything that affects the overall state of the app.

### Plans Tracking
- A `plans.json` file lives at the project root. It holds a JSON array of every implementation plan that has been executed.
- **After completing any implementation plan — append a new entry to `plans.json`** with the following shape:
  ```json
  { "plan": "<human-readable summary of what was planned and implemented>", "timestamp": "<ISO-8601 UTC timestamp>" }
  ```
- Keep plan summaries concise but complete enough to reconstruct what was changed (feature names, files touched, DB version bumps, etc.).
- Do **not** remove or overwrite existing entries — always append.

### General Development
- Always read existing code before modifying it. Never assume what a file contains.
- Follow existing naming conventions — Kotlin idiomatic style, PascalCase for classes, camelCase for functions/properties.
- Follow the established MVVM pattern: Composable → ViewModel → Repository → DAO. Do not skip layers.
- Use `StateFlow` (not `LiveData`) for reactive state in ViewModels.
- Use Hilt for all dependency injection. Do not manually instantiate repositories or DAOs.
- Prefer editing existing files over creating new ones.
- Do not add unused imports, dead code, or commented-out code blocks.

### Database (Room)
- Any schema change **requires** a migration. Increment the database version and add a `Migration(from, to)` object.
- Update `DB_UPGRADE.md` when the schema changes.
- Add new entities to `DatabaseModule.kt` and the `@Database` annotation entity list.
- Use `@TypeConverter` for complex types (lists, enums, custom objects stored as JSON).

### AI / Gemini (War Room)
- New AI capabilities should be reflected in `WAR_ROOM_ACTIONS_SPEC.md`.
- New action types go in the `ActionBlock` sealed class, `ActionParser`, and `ActionExecutor` — all three must stay in sync.
- Keep system prompts focused. Do not bloat the context sent to Gemini.

### Health Connect
- Any new health data types must be declared in the manifest with the correct `<uses-permission>` and Health Connect permissions.
- Sync logic lives in the health package. Keep it isolated from UI logic.

### UI / Compose
- Use the project's existing theme tokens (colors, typography, shapes) from the `ui/theme` package. Do not hardcode colors or font sizes.
- New screens get their own ViewModel and are registered in the navigation graph.
- Reusable UI elements go in `ui/components`.

### Specs & Documentation
- **Mandatory Alignment:** Before initiating any change to functionality, UX, or UI, you MUST verify alignment with the relevant `.md` specification files. These documents are the authoritative source of truth for system behavior and design standards.
- **Continuous Documentation:** Every addition, modification, or removal of app functionality, UI elements, or UX patterns must be immediately recorded in the corresponding `.md` file to maintain an accurate system blueprint.
- **Conflict Resolution:** If a user request directly contradicts an established specification in an `.md` file, you MUST stop and explicitly ask for clarification before proceeding with any code changes. Never silently override documented logic or invariants.
- **Technical Invariants:**
    - `WORKOUT_SPEC.md` is mandatory reading before any workout or routine change. It defines the state machine, rendering priority, and technical invariants that prevent regressions.
    - After implementing any feature defined by a spec, update that spec to reflect the final implementation details, state transitions, or UI components introduced.
    - If a spec is found to be outdated relative to the current implementation, note it and update it immediately.

### Testing
- **Writing and running tests is a mandatory step after any business-logic change — not optional, not deferred.** Do not consider a feature or fix complete until tests are written AND pass.

**WorkoutViewModelTest dispatcher pattern** — use `StandardTestDispatcher` for both main and test. Single dispatcher instance:
```kotlin
private val testDispatcher = StandardTestDispatcher()
@Before fun setup() { Dispatchers.setMain(testDispatcher) }
@After fun tearDown() { Dispatchers.resetMain() }
// Each test: runTest(testDispatcher) { ... runCurrent() ... }
```

**`startEditMode()` uses `withContext(Dispatchers.IO)`** — IO runs on a real thread, not the test dispatcher. After calling it, drain with:
```kotlin
viewModel.startEditMode(routineId)
runCurrent()        // drains to the withContext(IO) boundary
Thread.sleep(100)   // let IO thread schedule continuation back
runCurrent()        // drain the re-queued continuation
```

**`advanceTimeBy` off-by-one** — processes tasks where `time < currentTime + N`. To tick a 1000ms timer N times, use `advanceTimeBy(N * 1000 + 1)`.

**Cancel-before-assert** — always call `cancelWorkout()` + `runCurrent()` BEFORE any assertion that could fail, to prevent timer loops blocking `advanceUntilIdle()` cleanup.
- After implementing changes to ViewModels, Repositories, DAOs, or utility classes: immediately write unit tests covering the new behavior, then run them using the hardcoded Gradle command in the **Build & Test Commands** section above before closing the task.
- New business logic should have unit tests (JUnit + Mockito).
- New UI flows should have Compose UI tests where feasible.
- Run existing tests before committing changes to catch regressions.

### QA Protocol (mandatory after every implementation step)

The QA gate for each step is: **build ✅ + unit tests ✅ + screenshot**.

Steps in order:
1. **Build** — Run `:app:assembleDebug` using the hardcoded Gradle path (see **Build & Test Commands** section). Must succeed with zero errors.
2. **Unit tests** — Run `:app:testDebugUnitTest` using the hardcoded Gradle path. Must pass. Fix any regressions before continuing.
3. **Install + screenshot** — Install the debug APK on the connected Android emulator via `mcp__mobile__install_app`, launch the app, and take at least one screenshot confirming the relevant screen renders without a crash. Navigate to the changed screen if possible.

**Emulator constraints:**
- Firebase Auth requires network access which is unavailable on the test emulator — sign-in will fail with a network error. This is expected and does not block QA.
- Connected Android Tests (`connectedAndroidTest`) are skipped because the emulator runs API 36, which is incompatible with the current test runner setup.
- Screenshot QA is therefore scoped to: app launches without crash, logo/welcome screen renders, and any screen reachable without authentication (e.g. welcome, sign-in form).

**After QA passes**, output:
- `### WHAT CHANGED` — bullet summary of every file and behaviour modified
- `### HOW TO QA IT` — manual checklist for the user to verify on device

Do not advance to the next step until the user replies **APPROVED**.

### Security
- Never hardcode API keys or secrets. Use `local.properties` + `BuildConfig`.
- Use `EncryptedSharedPreferences` (Security Crypto) for any sensitive stored values.
- Validate all user input at the UI boundary before passing to the data layer.

### Code Quality
- **After completing any feature, fix, or refactor — run `/simplify`** to review changed code for reuse, quality, and efficiency before closing the task.
- Optionally focus the review: `/simplify focus on Compose recomposition` or `/simplify focus on ViewModel state management`.

### UI / UX Reviews
- For any **new screen or open-ended visual design** where layout, color, spacing, or interaction patterns are not already defined by a spec file — invoke the `ui-ux-pro-max` skill.
- Do **not** invoke it for spec-driven implementations: if a `*_SPEC.md` file already defines the exact tokens, typography, layout, and animation parameters, the spec is the design authority and `ui-ux-pro-max` adds no value.
- Stack context for this project: Jetpack Compose + Material Design 3, Stremio Indigo palette (see ProjectMap §1).
- Always use `MaterialTheme.colorScheme.*` tokens — no hardcoded colors.

### Superpowers — Structured Dev Workflow
- Use for large, multi-step features: brainstorm → spec → plan → subagent execution → review → merge.
- Invoke at the start of any non-trivial feature (new screen, DB migration, multi-file refactor) to keep work structured.
- **Install:** `/plugin marketplace add obra/superpowers-marketplace` then `/plugin install superpowers@superpowers-marketplace`

### Security Audits (Shannon — pending install)
- Once Shannon: Autonomous AI Pentester is installed, run it on any change touching: Firebase Auth/Firestore rules, API keys, EncryptedSharedPreferences, Health Connect data, or Android Intent handling.
- Do not run on every commit — scope to security-sensitive changes only.

### MCP Servers
- **`mobile`** (user-scoped, stdio): `npx -y claude-in-mobile` — provides mobile development tools (Android/iOS). Use this MCP server for device interaction, app inspection, and mobile-specific tasks in this project.

