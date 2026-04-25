# PowerME — Completed Feature Archive

Implementation details for shipped features. Referenced from `CLAUDE.md` Current State.
Only read this file when modifying one of these features.

---

## WorkoutStyle (P8 Tier 0, completed)

`WorkoutStyle` enum (`PURE_GYM` / `PURE_FUNCTIONAL` / `HYBRID`, default `HYBRID`) stored in `AppSettingsDataStore` under key `workout_style` (stringPreferencesKey). Exposed as `workoutStyle: Flow<WorkoutStyle>` + `setWorkoutStyle()`. Collected in `SettingsViewModel.loadAppSettings()` and exposed in `SettingsUiState`. Synced to Firestore via `pushAppPreferences()` / `pullAppPreferences()`. Settings card ("Workout Style") inserted between Units and Health Connect using `SingleChoiceSegmentedButtonRow`. No DB schema change. Gates future Template Builder "Add" UX (Tier 1+).

## Exercise.tags (P8 Tier 0, completed)

`tags TEXT NOT NULL DEFAULT '[]'` JSON column on `exercises` table (v50). `MasterExerciseSeeder` at v2.3.3 — ~387 total exercises. v2.1 seeded 25 new functional movements + tags on 27 existing. v2.2 (func_exercise_expanded_seed) adds 61 net-new CrossFit/Hyrox/calisthenics exercises and retags 28 additional existing exercises. v2.3 (func_cardio_exercise_seed) adds 29 new cardio entries (treadmill variants, cycling, swimming, air bikes, elliptical, stair climber, hiking, rucking, LISS/HIIT protocols) and retagging 9 existing exercises. v2.3.2 (func_exercise_type_retag_seed) retags 4 existing exercises to PLYOMETRIC (Box Jump, Box Jump Over, Tuck Jump, Clap Push-up) + adds 5 PLYOMETRIC (Depth Jump, Jump Squat, Plyo Push-Up, Lateral Bound, Hurdle Jump) + adds 12 STRETCH entries (Hip Flexor Stretch, Hamstring Stretch, Quad Stretch, Child's Pose, Pigeon Pose, Downward Dog, Cat-Cow, Shoulder Cross-Body Stretch, Tricep Stretch, Seated Spinal Twist, Standing Calf Stretch, Chest Opener Stretch). v2.3.3 (func_yoga_stretch_seed) adds 15 yoga-based STRETCH entries (Warrior I, Warrior II, Warrior III, Triangle Pose, Bridge Pose, Cobra Pose, Supine Spinal Twist, Lizard Pose, Reclined Butterfly, Standing Forward Fold, Low Lunge, Happy Baby, Legs Up the Wall, Seated Forward Fold, Wide-Legged Forward Fold). Tags: `"functional"`, `"hyrox"`, `"calisthenics"`, `"gymnastics"`, `"monostructural"`, `"cardio"`. New families: `planche_family`, `front_lever_family`, `back_lever_family`, `ring_skills_family`, `flag_family`, `carry_family`, `stretch_family`. `ExercisesViewModel` gains `functionalFilter: Boolean` in `ExercisesUiState` + `onFunctionalFilterToggled()`. `ExercisesScreen` has a "Functional" `FilterChip` (Mode row, `TimerGreen` colour) below the Equipment row.

## Countdown MM:SS fill-in input (P0, wrapped)

`CountdownRoulettePicker` + `WheelPicker` composables removed from `ToolsScreen.kt`. Replaced by `CountdownMmSsInput` (Column + Row with `Arrangement.Center`) containing two `CountdownDigitField` composables — 96dp fixed-width, 42sp JetBrainsMono Bold, `surfaceVariant` background with `shapes.medium` (12dp) corners, 2dp `primary` border on focus, `onSurface @ 18% alpha` placeholder `MM`/`SS` when empty. `TimerConfigField` gained `imeAction: ImeAction = ImeAction.Done`, `keyboardActions: KeyboardActions = KeyboardActions.Default`, `enabled: Boolean = true` params. `ToolsUiState` gains `countdownMinutesText: String = "01"` / `countdownSecondsText: String = "00"` (Dual-Property pattern); `setCountdownPreset` zero-pads with `"%02d".format(...)`.

## Exercise Library favourites quick-filter (P5, completed)

`ExercisesUiState` gains `favoritesOnly: Boolean = false`. `ExercisesViewModel` gains `onFavoritesFilterToggled()`; `applyFilters()` applies `exercise.isFavorite` check when `favoritesOnly == true`; `onClearAllFilters()` resets `favoritesOnly` to false. `ExercisesScreen` search bar trailing icon row: heart `IconToggleButton` (outlined/filled) inserted between the Clear button and Tune icon — `Icons.Outlined.FavoriteBorder` / `Icons.Filled.Favorite`, `onSurfaceVariant` tint off, `error` tint on, content description "Show favourites only". `favoritesOnly` is NOT counted in `activeFilterCount` badge. Filter stacks with all existing filters (AND logic).

## Alternative exercise movement transfer (P5, completed)

`MovementTransferTable` object (`data/repository/MovementTransferTable.kt`) — hardcoded per-exercise relative strength factors normalised within family (1.0 = anchor lift). 9 families seeded: squat/bench/deadlift/overhead_press/pullup/row/pushup/olympic/leg_curl (~74 factors). `ExerciseDetailRepository.estimateStartingWeight()` now computes `sourceE1RM × movementRatio × equipmentRatio × 0.80` with `coerceAtLeast(2.5)` floor. Cross-family and unseeded pairs return movement ratio 1.0 (no-op, existing behaviour preserved). `AlternativeExercise` gains `adjustedForMovement: Boolean`; `AlternativeExerciseCard` shows a 14dp `Icons.Outlined.Info` icon next to the weight when movement adjustment was applied.

## AI hybrid key (shipped)

`GeminiKeyResolver` resolves: user key (EncryptedSharedPreferences `secure_ai_prefs`) → `BuildConfig.GEMINI_API_KEY` → `ApiKeyMissing` error. User sets their own key in Settings → AI card. `SecurePreferencesStore` interface + `EncryptedSecurePreferencesStore` prod impl. Never synced to Firestore.

## AI parser abstraction layer (P9, shipped)

`WorkoutTextParser` interface (single `suspend fun parseWorkoutText`). `WorkoutParserRouter` (`@Singleton`, cloud-only for now) delegates to `@Named("cloud") WorkoutTextParser`. `AiModule` binds router as `WorkoutTextParser` and provides `GeminiWorkoutParser` adapter as `@Named("cloud")`. `WorkoutPromptUtils` holds shared `workoutPromptJson`, `buildWorkoutPrompt()`, `parseWorkoutJsonResponse()` — extracted from `GeminiWorkoutParser` (not modified). Next step: `OnDeviceWorkoutParser` + AICore routing in `WorkoutParserRouter`.

## TimerEngine (P8 Tier 0, in-progress)

`TimerSpec` sealed class (`Amrap`, `Rft`, `Emom`, `Tabata`, `Countdown`, `Stopwatch`) in `util/timer/TimerSpec.kt`. `TimerEngineState` data class + `TimerPhase` enum (`IDLE/SETUP/WORK/REST`) in `util/timer/TimerEngineState.kt`. `TimerEngine` interface + `TimerEngineImpl` class in `util/timer/TimerEngine.kt` — pure suspend-function loop with `awaitNotPaused()` pause/resume. `ToolsViewModel` refactored to delegate all timer logic to `TimerEngineImpl` (no more inline `run*` functions). `JetBrainsMono` upgraded from `FontFamily.Monospace` fallback to a real `GoogleFont("JetBrains Mono")` declaration in `Type.kt`. `TimerDigitsXL` (96sp Bold), `TimerDigitsL` (48sp Bold), `TimerDigitsM` (28sp Medium) typography roles in `ui/theme/TimerDigitsStyles.kt`. `TimerPhase` moved from `ToolsViewModel.kt` to `util/timer/TimerEngineState.kt`; `ToolsScreen.kt` imports from new location.
