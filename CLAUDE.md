# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v23, 16 entities, 15 DAOs)
- AI: Google Gemini (War Room chat + action parsing)
- Auth/Backend: Firebase Auth + Firestore
- Health: Health Connect API (`androidx.health.connect:connect-client:1.1.0-rc01`)
- Charts: Vico (Material 3)
- Drag-and-drop: `sh.calvin.reorderable:reorderable-compose:2.4.3`
- Build: Gradle Kotlin DSL, KSP, min SDK 26, target SDK 35

**Package:** `com.omerhedvat.powerme`

**Main Features Implemented:**
- Workout routine creation and management
- Active workout tracking (sets, reps, weight, duration)
- Superset support (pair exercises via multi-select CAB, SS badge, turn-based alternation, border glow)
- Exercise Management Hub (ModalBottomSheet, 8 actions: session note, sticky note, warmup sets, rest timer, replace, superset, preferences, remove)
- Session notes (volatile, per-session) + sticky notes (persisted per routine-exercise via `routine_exercises.stickyNote` DB column)
- Rest timer sheet (in-workout per-exercise rest duration adjustment)
- Workout history with timestamps and notes; tapping a history card navigates to WorkoutDetailScreen (per-exercise, per-set breakdown with e1RM column, set type prefix W/D/F, set notes, superset spine)
- WorkoutDetailScreen + WorkoutDetailViewModel: new files in `ui/history/`; `WorkoutSetWithExercise` data class + `getSetsWithExerciseForWorkout()` query in `WorkoutSetDao`; uses `StatisticalEngine.calculate1RM()` per set
- Theme mode: `ThemeMode` enum (LIGHT/DARK/SYSTEM) in `data/ThemeMode.kt`; `AppSettingsDataStore.themeMode: Flow<ThemeMode>` + `setThemeMode()`; `PowerMETheme(themeMode: ThemeMode)` applies `LightColorScheme` or `DarkColorScheme` (Stremio dark palette) or follows system; `MainActivity` collects themeMode, calls `enableEdgeToEdge` via `SideEffect` conditionally; Appearance card in SettingsScreen with `SingleChoiceSegmentedButtonRow` (Light/Dark/System); deprecated `darkModeEnabled` / `setDarkModeEnabled` preserved for schema stability
- Google Fonts: `BarlowCondensed` (Medium/SemiBold/Bold) + `Barlow` (Normal/Medium/SemiBold) loaded via `GoogleFont.Provider`; `res/values/font_provider_certs.xml` contains GMS cert arrays; `Typography` expanded to 11 M3 roles (displaySmall, headlineL/M/S, titleL/M, bodyL/M/S, labelMedium/Small); graceful fallback to system sans-serif offline
- Nav tab order: Workouts / History / Exercises / Tools / Trends (Workouts first)
- Save as Routine: PostWorkoutSummarySheet "Save as Routine" button → AlertDialog → `WorkoutViewModel.saveWorkoutAsRoutine()` creates Routine + RoutineExercise rows from completed sets
- Auto-copy weight/reps: `addSet()` pre-fills new set with last set's weight and reps strings
- Rest timer (audio + haptic) — 2s/1s warning beeps + 800ms end beep at 0s
- WorkoutTimerService (ForegroundService wrapping countdown timer coroutine with persistent notification); bound to WorkoutViewModel via ServiceConnection — timer survives backgrounding; `timerJob?.cancel()` guard prevents double-beep race; `onTimerTick`/`onTimerFinish` callbacks drive audio+haptic
- Exercise library (150+ exercises, YouTube demos via native Intent, muscle groups, equipment types)
- Multi-select muscle group filter + equipment filter chips in Exercises tab (AND-combined filtering); equipment chips use canonical taxonomy [All, Barbell, Dumbbell, Machine, Cable, Bodyweight]; case-insensitive matching against DB uppercase values; equipment row wrapped in surfaceVariant Surface (Slate800) for visual distinction from muscle row
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
- Clocks tab (Stopwatch, Timer, Tabata, EMOM) with countdown beeps, skip-last-rest, pre-finish alerts, input validation
- Drag-and-drop exercise reorder (reorderable-compose library; `reorderExercise()` in WorkoutViewModel)
- Bayesian M-Estimate 1RM (`calculateBayesian1RM()` in StatisticalEngine.kt): smooths e1RM readings from small sample sets via μ_bayesian=(C*μ_prior+n*μ_sample)/(C+n), C=5 default
- WeeklyInsightsAnalyzer: 100% local/deterministic; receives bayesian1RMs Map<Long,Double> from AnalyticsRepository — no AI calls
- ExercisesScreen: equipmentType chip now uses `toEquipmentDisplayName()` extension (DB UPPER_CASE → Title Case, e.g. CABLE → Cable); all static color imports replaced with MaterialTheme.colorScheme tokens (§1 compliant); VideoLibrary play icon removed from ExerciseCard; tap opens ExerciseDetailSheet (ModalBottomSheet) with Form Cues (muted gold banner) + YouTube TextButton; `selectedExercise` state at screen scope drives the sheet
- ExerciseDetailSheet: new @Composable in ExercisesScreen.kt — ModalBottomSheet showing exercise name, muscle/equipment chips, setupNotes in gold banner (Color 0xFF5A4D1A), YouTube link via vnd.youtube: intent + browser fallback
- ActiveWorkoutScreen: LazyColumn contentPadding = PaddingValues(bottom=8.dp) added; imePadding() on outer Column ensures set fields stay visible when keyboard is active
- WorkoutViewModel: `onWeightChanged`/`onRepsChanged` — early return on Invalid (no state mutation), removed `isCompleted` side-effect from both; `completeSet(exerciseId, setOrder)` now **toggles** `isCompleted` (no SurgicalValidator gate) — first tap completes + starts rest timer, second tap un-completes + calls `stopRestTimer()`; `stopRestTimer()` cancels `timerJob`, stops bound service, clears `RestTimerState`
- WorkoutInputField: new reusable composable (ui/components/WorkoutInputField.kt) — BasicTextField with StremioInputPill (#2C2C4E) background, RoundedCornerShape(4.dp), 34dp height, 1dp primary border on focus, centered text; used in WorkoutSetRow
- Strong-Inspired UI Overhaul: ActiveWorkoutScreen compact top bar (Close icon → workoutName + elapsed mm:ss → Finish button); PostWorkoutSummarySheet triggered by `pendingWorkoutSummary` state (name, duration, volume, set count, exercise list); STRENGTH column headers match WorkoutSetRow widths; Add Set replaced with minimal clickable Row; exercise name/muscle group colors use `onSurface` tokens; WorkoutsScreen gains "Workouts" title, `OutlinedButton` for empty start, "Routines" label, RoutineCard with left primary accent bar + exercise count SuggestionChip + AccessTime recency icon + FilledTonalButton; HistoryScreen redesigned (routineName title fallback, stats row with Timer/FitnessCenter/List icons, FlowRow exercise chips, setCount from DAO); WorkoutViewModel: `elapsedTimerJob` + `startElapsedTimer()`, `workoutName`/`elapsedSeconds`/`pendingWorkoutSummary` in state, `dismissWorkoutSummary()`, `finishWorkout()` no longer takes callback; WorkoutDao: `WorkoutExerciseNameRow` + SQL query add `routineName` (LEFT JOIN routines) and `setCount` (COUNT subquery); HistoryViewModel: `WorkoutWithExerciseSummary` gains `routineName` + `setCount`
- WorkoutSetRow: new composable in ActiveWorkoutScreen.kt — high-density row: Set# 24dp | Previous 72dp | KG pill | REPS pill | ✓ button (TimerGreen when completed) + tappable RPE SuggestionChip badge after ✓ (visible only when isCompleted); replaces SetRow in the STRENGTH else branch; ExerciseCard gains onCompleteSet + onUpdateRpe params wired to viewModel
- RPE integration: scale 6.0–10.0 in 0.5 steps, stored as Int×10 in existing `WorkoutSet.rpe` DB column (no migration); `ActiveSet.rpeValue: Int?` holds in-memory state; `WorkoutViewModel.updateRpe()` updates memory + DB via `WorkoutSetDao.updateRpe()`; `RpePickerSheet` ModalBottomSheet (replaces old `RpePickerDialog` AlertDialog) with `navigationBarsPadding()` + FlowRow of FilterChips + Clear; triggered by long-press on Reps `BadgedBox` (shows `@RPE` badge when rpeValue set, no post-checkmark chip); `WorkoutSetWithExercise.rpe` + `SetDisplayRow.rpe` carry RPE into WorkoutDetailScreen (RPE column header + per-set label next to e1RM)
- Superset Spine: ExerciseCard restructured to Card{Row(IntrinsicSize.Min){Box(4dp secondary spine if isInSuperset) + Column(weight(1f))}} — spine spans full card height
- RoutineExercise entity: new Room entity (routine_exercises table) — routineId FK, exerciseId FK, sets, reps, restTime, order, supersetGroupId; RoutineExerciseDao with getForRoutine (backtick-escaped `order`), insert, insertAll, delete, deleteByRoutineAndExercise, updateOrder
- Routine.isArchived: new soft-archive field (Boolean, default false)
- RoutineDao: RoutineExerciseNameRow projection model; getAllActiveRoutinesWithExerciseNames() + getAllArchivedRoutinesWithExerciseNames() Flow queries with LEFT JOIN
- WorkoutsViewModel: RoutineWithSummary(routine, exerciseNames, daysSincePerformed?); activeRoutines+archivedRoutines StateFlows; archiveRoutine, unarchiveRoutine, deleteRoutine, renameRoutine; now injects RoutineExerciseDao; routineDetails StateFlow + loadRoutineDetails()/clearRoutineDetails() for RoutineOverviewSheet
- WorkoutsScreen: 2-column chunked grid of compact clickable RoutineCards (tap card → RoutineOverviewSheet; ⋯ top-right → Rename/Archive/Delete dropdown); RoutineOverviewSheet ModalBottomSheet: ✕ | name | Edit no-op, recency label, N × Exercise rows, Start Workout button; archived routines also 2-col under AnimatedVisibility
- RoutineExerciseDao: added RoutineExerciseWithName data class + getExercisesWithNamesForRoutine() JOIN query
- ActiveWorkoutScreen: RestSeparator private composable — Crossfade between active (primary-tinted block with live mm:ss countdown) and passive (TimerGreen HorizontalDividers + monospace rest-time label) states; sets loop checks isThisTimerActive so last set also shows separator while timer is live; global RestTimerBar banner removed; sets loop changed to forEachIndexed; Add Set button label updated to 'Add Set (M:SS)'
- WorkoutViewModel: RestTimerState gains exerciseId: Long? + setOrder: Int? fields; startRestTimer() accepts optional setOrder param and stamps identity on timer start; service mirror preserves identity via state.restTimer.copy(); completeSet() passes setOrder to startRestTimer()
- WorkoutRepository: added RoutineExerciseDao; instantiateWorkoutFromRoutine(routineId) → WorkoutBootstrap (creates Workout + WorkoutSets from RoutineExercise, returns ghostMap)
- MagicAddDialog/MagicAddViewModel: Gemini removed from the search path; `onSearchChanged(query)` drives local DB search via `ExerciseRepository.searchExercises()`; results show as clickable LazyColumn rows (prefix-priority ranked, LIMIT 25); "Create new" row at bottom triggers existing Gemini enrichment flow for exercises not in DB; `searchResults: StateFlow<List<Exercise>>` exposed from ViewModel; `reset()` also clears searchResults
- ExerciseDao: `searchExercises(query)` @Query added — case-insensitive LIKE with prefix-first ORDER BY, LIMIT 25; `getByIds(ids)` @Query added for bulk exercise fetch by ID list
- ExerciseRepository: `searchExercises(query)` + `getExercisesByIds(ids)` exposed as suspend funs
- RoutineExerciseDao: `deleteAllForRoutine(routineId)` added
- ToolsScreen (Clocks): TABATA and EMOM modes now use persistent side-by-side Start+Reset buttons (weight(1f) each, always visible regardless of timer phase) with PlayArrow/Pause icons matching Stopwatch/Timer layout; STOPWATCH and COUNTDOWN keep existing icon-labeled toggle+reset layout
- TemplateBuilderScreen + TemplateBuilderViewModel (new files in `ui/workouts/`): full-screen routine editor — TopAppBar (back / name OutlinedTextField / Save TextButton), LazyColumn of DraftExerciseRows (exercise name + muscle chip, sets stepper −/N/+, delete icon), empty state, footer "Add Exercises" OutlinedButton; navigates to EXERCISE_PICKER route and receives selected IDs via savedStateHandle "selected_exercises" key; `DraftExercise` data class; `TemplateBuilderViewModel` injects `RoutineDao`, `RoutineExerciseDao`, `ExerciseRepository`, `PowerMeDatabase`, `SavedStateHandle`; `save()` uses `database.withTransaction { deleteAllForRoutine + insertAll }`; supports both create (`routineId=-1`) and edit flows
- ExercisesScreen: `pickerMode: Boolean` param — when true: shows picker header with "Add (N)" confirm button, tapping cards toggles selection (selectedIds Set<Long>), selected cards show primary overlay + CheckCircle icon at top-end, FABs hidden, ExerciseDetailSheet disabled; `onExercisesSelected: (List<Long>) -> Unit` callback
- Navigation: `TEMPLATE_BUILDER = "template_builder/{routineId}"` + `EXERCISE_PICKER = "exercise_picker"` routes added; EXERCISE_PICKER is full-screen (no bottom nav scaffold); WorkoutsScreen wired with `onCreateRoutine` + `onEditRoutine` lambdas
- WorkoutsScreen: `showCreateDialog` AlertDialog removed; "+" icon calls `onCreateRoutine()`; RoutineOverviewSheet Edit button calls `onEditRoutine(routineId)`; Edit button disabled when `isWorkoutActive=true`
- Routine Edit Mode: `WorkoutViewModel.startEditMode(routineId)` loads RoutineExercise rows into `ActiveWorkoutState` with `isEditMode=true`, `workoutId=null`, no elapsed timer; `saveRoutineEdits()` batch-writes changes to `routine_exercises` and sets `editModeSaved=true` (navigation trigger); `cancelEditMode()` resets state; `ActiveWorkoutState` gains `isEditMode` + `editModeSaved` fields; `addSet()` uses negative fake ID in edit mode; `onWeightChanged`/`onRepsChanged` skip Iron Vault debounce in edit mode; `completeSet()` skips rest timer in edit mode; `ActiveWorkoutScreen` branches on `isEditMode`: BackHandler, Close button, elapsed timer hidden, footer shows "Save Changes" vs "Finish Workout", RestSeparators hidden; `PowerMeNavigation.onEditRoutine` now calls `startEditMode(routineId)` + navigates to workout route instead of TemplateBuilder
- WorkoutSetRow: row background is `MaterialTheme.colorScheme.surfaceVariant` (`Slate200` in light mode) when `isCompleted`, `Color.Transparent` otherwise
- WorkoutSetRow: set number pill tap opens DropdownMenu with "Change Type" and "Delete Timer" (error-colored) items; `onDeleteTimer` param wired through ExerciseCard → screen → `viewModel.deleteLocalRestTime()`
- Per-set rest time overrides: `ActiveWorkoutState.restTimeOverrides: Map<String, Int>` keyed by `"${exerciseId}_${setOrder}"`; `updateLocalRestTime()` / `deleteLocalRestTime()` in ViewModel; passive RestSeparator tap → `RestTimePickerDialog` (min/sec OutlinedTextFields, CONFIRM/CANCEL) → `updateLocalRestTime`; effectiveRest computed per set in ExerciseCard
- RestTimerState gains `isPaused: Boolean = false`; `pauseRestTimer()` / `resumeRestTimer()` / `startRestTimerWithDuration()` added to WorkoutViewModel; `WorkoutTimerService.pauseTimer()` returns remaining seconds and stops the coroutine
- TimerControlsSheet (ModalBottomSheet): shows remaining time, -10s / Pause-or-Play / +10s controls, Skip button; triggered by tapping active RestSeparator (primary-tinted box); wired at screen level via `showTimerControls` state
- UpdateRestTimersDialog: AlertDialog with min/sec fields replacing old RestTimerSheet flow for the Management Hub "Update rest timer" action; `updateExerciseRestTimer()` now calls `ExerciseDao.updateRestDuration()` directly (new @Query) instead of fetching full Exercise via repository
- ExerciseDao: `updateRestDuration(exerciseId, seconds)` @Query added
- WorkoutViewModel: `ExerciseDao` injected directly (new constructor param) for `updateRestDuration` call
- WorkoutInputField: optional `focusRequester: FocusRequester?` param added; appended via `.then(Modifier.focusRequester(...))` if non-null
- RestSeparator: `onActiveClick` and `onPassiveClick` callbacks added; active box has `.clickable(onClick = onActiveClick)`, passive row has `.clickable(onClick = onPassiveClick)`
- Power Grid Efficiency Update (v23 refactor): collapsible ExerciseCards (`isCollapsed by rememberSaveable`, `AnimatedVisibility`, chevron `IconButton`); weight-based column grid (SET=0.10f, WEIGHT=0.30f, REPS=0.30f, RPE=0.20f, CHECK=0.10f) shared between header `Row` and `WorkoutSetRow` for perfect alignment; RPE column always-visible in `WorkoutSetRow` (tap → `RpePickerSheet`), `BadgedBox` + post-completion RPE chip removed; "Add Rest" `TextButton` (muted, Timer icon) alongside "Add Set" in exercise card footer; Finish button moved from TopAppBar to LazyColumn footer item with `navigationBarsPadding()`; smart-fill cascade in `onWeightChanged`/`onRepsChanged` (first set → blank+uncompleted sets only); Routine sync prompts on `finishWorkout()`: `RoutineExerciseSnapshot` data class, `RoutineSyncType` enum (STRUCTURE/VALUES), `pendingRoutineSync` in `ActiveWorkoutState`, two `AlertDialog` composables in screen, `confirmUpdateRoutineStructure()`/`confirmUpdateRoutineValues()`/`dismissRoutineSync()` in ViewModel; `RoutineExercise.defaultWeight` field + `RoutineExerciseDao.updateSets()`/`updateRepsAndWeight()` queries; DB v22→v23 migration adds `defaultWeight TEXT NOT NULL DEFAULT ''` to `routine_exercises`
- Set Row UI & Interaction (Items 8, 9, 10, 11): PREV column added to WorkoutSetRow and ExerciseCard header (PREV_COL_WEIGHT=0.22f, `formatGhostLabel()` helper shows previous session ghostWeight×ghostReps@RPE or "—"); column weights updated to SET=0.08/PREV=0.22/WEIGHT=0.25/REPS=0.22/RPE=0.13/CHECK=0.10; `SetTypePickerSheet` ModalBottomSheet replaces DropdownMenu — 4 RadioButton rows (Normal/Warm Up/Drop Set/Failure) + Info icon (AlertDialog description per type) + Delete Timer TextButton in error color; `selectSetType(exerciseId, setOrder, setType)` added to WorkoutViewModel (updates state + writes DB via `workoutSetDao.updateSetType()`), `cycleSetType` preserved; `SwipeToDismissBox` (EndToStart) wrapping WorkoutSetRow (keyed by `set.id`) and RestSeparator rows, `SwipeToDeleteBackground` composable (error color fade + Delete icon); `deleteSet()` extended to cancel rest timer if it matches (exerciseId, setOrder), call `workoutSetDao.deleteSetById()`, and clean restTimeOverrides/hiddenRestSeparators; `deleteRestSeparator(exerciseId, setOrder)` added; `hiddenRestSeparators: Set<String>` added to `ActiveWorkoutState`; `deleteSetById @Query` added to `WorkoutSetDao`; 5 new tests in WorkoutViewModelTest; ExerciseCard `onCycleSetType` → `onSelectSetType: (Int, SetType) -> Unit`, `onDeleteRestSeparator` + `hiddenRestSeparators` params added

**Color System:** v3.0 Stremio 'Pure Performance' palette (ProjectMap §1 compliant). Core tokens: StremioBackground #0F0F1E (page bg), StremioSurface #191932 (cards/bars), StremioSurfaceVar #1F1F3E (chips/rows), StremioViolet #7B5BE4 (primary), StremioMagenta #B3478C (secondary), StremioCloudGrey #E1E1E6 (on-surface text), StremioError #FF4444. Legacy aliases (DeepNavy, NavySurface, SlateGrey, OledBlack, NeonBlue, ElectricBlue, Slate200) defined in Color.kt only. All composables use `MaterialTheme.colorScheme.*` tokens — no static color imports in composables. MedicalAmber/MedicalAmberContainer preserved. TimerGreen = Emerald400 (#34D399).

**Navigation Structure:**
- Auth flow: Welcome → Profile Setup
- Main scaffold: 5 bottom tabs (**Exercises**, **History**, **Workouts**, **Clocks**, **Trends**)
- Overlays: Settings, Gym Setup → Gym Inventory, Active Workout
- War Room route exists but the TopAppBar Forum button is hidden (AI de-coupling phase)

**Database:** Room v27 — migrations covered from v6 → v27. Seeded on startup with 150+ master exercises.
- v16 adds `supersetGroupId TEXT` column to `workout_sets`
- v17 adds `stickyNote TEXT` column to `routine_exercise_cross_ref`
- v18 data-only migration: clears leaked profile metrics from `exercises.setupNotes` (SurgicalValidator.MIGRATION_SQL)
- v19 data-only migration: strips `"181.5 cm:"` prefix from `exercises.setupNotes` that bypassed v18 guard (SurgicalValidator.MIGRATION_SQL_V19)
- v20 schema migration: adds `isArchived INTEGER NOT NULL DEFAULT 0` to `routines`; creates new `routine_exercises` table (id, routineId FK, exerciseId FK, sets, reps, restTime, `order`, supersetGroupId) with indices on routineId+exerciseId
- v21 Data Hardening: `workouts.routineId` made nullable; FK changed to `SET_NULL` (orphan protection); `workouts.isCompleted INTEGER NOT NULL DEFAULT 0` added (settled-data gate); `routine_exercises.stickyNote TEXT` added; `routine_exercise_cross_ref` table dropped (data merged); `RoutineExerciseCrossRef.kt` + `RoutineExerciseCrossRefDao.kt` deleted; sticky-note queries moved to `RoutineExerciseDao`; `getPreviousSessionSets` filters `AND w.isCompleted = 1`; `instantiateWorkoutFromRoutine` wrapped in `withTransaction`; `finishWorkout()` sets `isCompleted=true` + calls `updateLastPerformed`; History tab now shows exercise name chips via `getAllCompletedWorkoutsWithExerciseNames` query
- v22 Iron Vault Sprint: `workout_sets.isCompleted INTEGER NOT NULL DEFAULT 0` added; Iron Vault auto-save wiring in `WorkoutViewModel` (rehydration on init, `startWorkoutFromRoutine()`, debounced weight/reps saves, discrete set-completion + setType DB writes); `cancelWorkout()` deletes orphaned DB records; `finishWorkout()` updates existing Workout row + deletes incomplete sets; SetType tap-to-cycle badge in `WorkoutSetRow` (NORMAL→WARMUP→FAILURE→DROP); per-set `PlateCalculatorSheet` ModalBottomSheet reading from `UserSettings.availablePlates`; `WorkoutsScreen` gains Resume Workout banner + RoutineCard Start button; shared `WorkoutViewModel` at NavHost level
- v23 Power Grid: adds `defaultWeight TEXT NOT NULL DEFAULT ''` to `routine_exercises` table; supports routine sync change detection on workout finish
- v24 Exercise Normalization: MIGRATION_23_24 — (1) 11 MERGE dedup passes (re-point routine_exercises+workout_sets FK → canonical exercise, delete legacy row); (2) 4 KEEP-BOTH renames (Romanian Deadlift RDL-BB/RDL-DB split, Weighted Pull-Up, Incline Dumbbell Row); (3) equipment normalization (Dumbbells→Dumbbell, Bodyweight+→Bodyweight); (4) muscleGroup normalization (Rear Delts→Shoulders, Lats→Back, Hamstrings→Legs, Triceps/Biceps→Arms, Abs→Core, Upper Chest/Side Delts→Shoulders/Chest, Chest/Triceps→Chest); (5) new `exercise_muscle_groups` table (exerciseId FK, majorGroup, subGroup, isPrimary) populated from muscleGroup + secondary rows for compound exercises; (6) partial UNIQUE index on master exercises (name, equipmentType WHERE isCustom=0)
- DatabaseSeeder reduced to 6 unique legacy entries (13 merge-duplicates removed); MasterExerciseSeeder bumped to v1.2 to force reseed of renamed exercises
- v25 Search Normalization: MIGRATION_24_25 — adds `searchName TEXT NOT NULL DEFAULT ''` to `exercises`; back-fills via SQLite `LOWER(REPLACE(...))` for all existing rows; `Exercise.searchName` field + `fun String.toSearchName()` extension (lowercase, strips hyphens/spaces/parens); `MasterExerciseSeeder` bumped v1.2→v1.3 (populates searchName on insert/update); `DatabaseSeeder` also populates searchName; `ExerciseDao.searchExercises()` targets `searchName` column; `ExerciseRepository.searchExercises()` normalizes query via `toSearchName()`; `ExercisesViewModel.applyFilters()` normalizes once + matches `exercise.searchName`; ExercisesScreen search field gains trailing ✕ clear icon
- v26 Schema Mismatch Fix: MIGRATION_25_26 — drops `idx_exercises_master_unique` partial UNIQUE index (Room cannot represent partial indexes; was causing `IllegalStateException: Migration didn't properly handle: exercises` crash on every cold launch after v24); `Exercise.searchName` gains `@ColumnInfo(defaultValue = "")` annotation to match SQL default set in MIGRATION_24_25
- v27 Index Name Fix: MIGRATION_26_27 — drops `idx_emg_exerciseId` on `exercise_muscle_groups` (created by v24 with wrong name) and recreates as `index_exercise_muscle_groups_exerciseId` (Room's expected convention); fixes `IllegalStateException: Migration didn't properly handle: exercise_muscle_groups` crash on every cold launch since v24
- ExercisesScreen filter chips now DB-driven (SELECT DISTINCT muscleGroup/equipmentType via ExerciseDao + ExerciseRepository + ExercisesViewModel.muscleGroupFilters/equipmentFilters StateFlows); hardcoded MUSCLE_GROUPS/EQUIPMENT_FILTERS constants removed
- util/MuscleGroups.kt: new object with 8 canonical group string constants (LEGS, BACK, CORE, CHEST, SHOULDERS, FULL_BODY, ARMS, CARDIO)


**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides parseDecimal() (locale-aware, accepts commas+periods), parseReps() (integer only), isLeakedMetric() for runtime checks, MIGRATION_SQL const val for Room @Query and Migration (v17→v18), and MIGRATION_SQL_V19 for the "181.5 cm:" prefix cleanse (v18→v19). No inline try-catch in ViewModels or Composables (ProjectMap §3).

**Health Connect permissions:** READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_EXERCISE, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. Height sync: getLatestHeight() in HealthConnectManager (365-day window); SettingsViewModel saves to both MetricLog (MetricType.HEIGHT) and User entity (dual-sink per ProjectMap §5). MetricType enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT.

**Unit Test Coverage (src/test/, 12 files, ~95 tests total):**
- `actions/ActionParserTest.kt` — 11 tests
- `actions/ActionExecutorTest.kt` — 10 tests
- `data/ExerciseDaoTest.kt` — DAO tests
- `data/PreMigrationValidatorTest.kt`
- `data/GymProfileRepositoryTest.kt` — 12 tests
- `util/SQLSafetyValidatorTest.kt` — 25 tests
- `util/GeminiResponseLoggerTest.kt`
- `util/SurgicalValidatorTest.kt` — 18 tests (parseDecimal, parseReps, isLeakedMetric)
- `util/PlateCalculatorTest.kt` — 13 tests (calculatePlates, parseAvailablePlates, formatPlateBreakdown)
- `analytics/StatisticalEngineTest.kt` — 22 tests (mean, stdDev, zScore, quartiles, IQR, outliers, Pearson, 1RM, Bayesian 1RM, rateOfChange)
- `ui/history/HistoryViewModelTest.kt` — 5 tests
- `ui/workout/WorkoutViewModelTest.kt` — 27 tests (includes 2 completeSet toggle tests + 5 rest timer/override tests + 5 cascade/routine-sync tests + 1 selectSetType + 2 deleteSet timer cancel + 2 deleteRestSeparator + 4 edit mode)

**Known Specs / Design Docs in repo:**
- `CARDIO_TIMED_SPEC.md`, `DS_POWER_TOOLS_SPEC.md`, `EQUIPMENT_SPEC.md`, `GYM_PROFILES_SPEC.md`
- `INJURY_CONTEXT_SPEC.md`, `INJURY_TRACKER_SPEC.md`, `METRICS_ALGORITHM.md`
- `NOAA_WARMUP_SPEC.md`, `WAR_ROOM_ACTIONS_SPEC.md`, `UX_UTILITIES.md`
- `DB_UPGRADE.md`, `EALTH_CONNECT_PROTO.md`, `committee_manifest.md`

---

## Instructions

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
- If implementing a feature that has a corresponding `*_SPEC.md` file, read it before writing any code.
- If a spec is outdated relative to the implementation, note it in the spec file.

### Testing
- **Writing and running tests is a mandatory step after any business-logic change — not optional, not deferred.** Do not consider a feature or fix complete until tests are written AND pass.
- After implementing changes to ViewModels, Repositories, DAOs, or utility classes: immediately write unit tests covering the new behavior, then run them with `./gradlew :app:testDebugUnitTest` before closing the task.
- New business logic should have unit tests (JUnit + Mockito).
- New UI flows should have Compose UI tests where feasible.
- Run existing tests before committing changes to catch regressions.

### Security
- Never hardcode API keys or secrets. Use `local.properties` + `BuildConfig`.
- Use `EncryptedSharedPreferences` (Security Crypto) for any sensitive stored values.
- Validate all user input at the UI boundary before passing to the data layer.

### Code Quality
- **After completing any feature, fix, or refactor — run `/simplify`** to review changed code for reuse, quality, and efficiency before closing the task.
- Optionally focus the review: `/simplify focus on Compose recomposition` or `/simplify focus on ViewModel state management`.

### UI / UX Reviews
- For any new screen, composable, or visual redesign — invoke the `ui-ux-pro-max` skill.
- Stack context for this project: Jetpack Compose + Material Design 3, Stremio Indigo palette (see ProjectMap §1).
- Always use `MaterialTheme.colorScheme.*` tokens — no hardcoded colors.

### Superpowers — Structured Dev Workflow
- Use for large, multi-step features: brainstorm → spec → plan → subagent execution → review → merge.
- Invoke at the start of any non-trivial feature (new screen, DB migration, multi-file refactor) to keep work structured.
- **Install:** `/plugin marketplace add obra/superpowers-marketplace` then `/plugin install superpowers@superpowers-marketplace`

### Security Audits (Shannon — pending install)
- Once Shannon: Autonomous AI Pentester is installed, run it on any change touching: Firebase Auth/Firestore rules, API keys, EncryptedSharedPreferences, Health Connect data, or Android Intent handling.
- Do not run on every commit — scope to security-sensitive changes only.

