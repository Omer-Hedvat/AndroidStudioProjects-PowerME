# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v47, 18 entities, 20 DAOs)
- Auth/Backend: Firebase Auth (email/password + Google Sign-In via Credential Manager) + Firestore
- Health: Health Connect API
- Charts: Vico (Material 3)
- Drag-and-drop: `sh.calvin.reorderable:reorderable-compose`
- AI: Gemini Flash API (`com.google.ai.client.generativeai:generativeai:0.9.0`) + ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`)
- Observability: Timber logging facade (DebugTree in debug, CrashlyticsTree in release) + Firebase Crashlytics + Firebase Analytics custom events
- Build: Gradle Kotlin DSL, KSP, min SDK 26, target SDK 35

**Package:** `com.powerme.app`

**Nav tab order:** Workouts / History / Exercises / Tools / Trends

**Color System:** Pro Tracker v6.0. See THEME_SPEC.md for all tokens, DarkColorScheme, LightColorScheme, semantic colors, and usage rules.

**Database:** Room v50 — 19 entities, 21 DAOs (`TrendsDao` and `ExerciseDetailRepository` are query-only aggregators, no corresponding entities). Migrations v6→v50 covered. See `DB_UPGRADE.md` for full history.
Key facts: UUID String PKs (v31+), Firestore sync columns (v35), soft deletes via `isArchived`, `routineName` denormalized on `workouts` (v36), per-type rest timers on exercises (v32), per-set weight/reps/setTypes in `routine_exercises` (v28–v29), `restAfterLastSet` flag on exercises (v38), `experienceLevel`/`trainingAgeYears` on users (v39), `health_history_entries` table (v40), `sessionRating` on workouts (v41), HC extended reads columns on `health_connect_sync` (v43), `primaryJoints`/`secondaryJoints` on `exercises` (v44), `source`/`importBatchId` on `workouts` (v45), `exercise_stress_vectors` table with `BodyRegion` enum (16 values) + `StressAccumulationEngine` (v46), `userNote TEXT NOT NULL DEFAULT ''` on `exercises` (v47), CSV-imported RPE data-fix (v48), `user_exercise_synonyms` table (v49) for AI exercise name synonym learning, `tags TEXT NOT NULL DEFAULT '[]'` on `exercises` (v50) for functional training categorisation.

**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides `parseDecimal()`, `parseReps()`, `isLeakedMetric()`, `MIGRATION_SQL`, `MIGRATION_SQL_V19`. No inline try-catch in ViewModels or Composables.

**Health Connect permissions:** 7 READ permissions: READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. `MetricType` enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT. Height sync via `getLatestHeight()` (365-day window); dual-sink to MetricLog + User entity.

**Unit Tests (src/test/, 70 files, ~994 tests — all passing):**
ExerciseDao, PreMigrationValidator, GymProfileRepository(12), SQLSafetyValidator(25), SurgicalValidator(18), PlateCalculator(18), UnitConverter(~40), StatisticalEngine(13), ReadinessEngine(16), HistoryViewModel(13), ExerciseFilter(24), ExerciseTagsFilter(8), WorkoutViewModel(148), SettingsVM-HC(7), SettingsVM-TimerSound(5), SettingsVM-ApiKey(7), SettingsVM-WorkoutStyle(7), SettingsVM-PersonalInfo, ProfileVM-PersonalInfo(7), ProfileVM-Logout(1), MetricLogRepository(4), MetricsVM-BodyVitals(6), AuthVM-GoogleSignIn(9), ProfileSetupVM(7), WorkoutSummaryVM(28), WorkoutDetailVM, RpeHelper(5), Joint(14), CsvFormatDetector(~21), CsvRowParser(~20), StrongCsvParser, StressAccumulationEngine(11), StressAccumulationEngineDetail(11), StressRecomputeGate(6), ExerciseStressVectorSeedData(10), TrendsRepositoryChronotype(~10), TrendsRepositoryBodyComposition, TrendsRepositoryEffectiveSets, TrendsRepositoryWeeklyGrouping, TrendsViewModelChronotype(7), TrendsViewModelEffectiveSetsFilter(10), TrendsViewModelBodyStressRefresh(4), TrendsViewModelDataFlags(~16), TrendsViewModelBodyComposition, TrendsViewModelDeepLink, JaroWinkler(10), ExerciseMatcher(13), ExerciseMatcherSynonym(4), GeminiKeyResolver(4), GeminiWorkoutParser(14), AiWorkoutViewModel(13), WorkoutParserRouter(5), UserSynonymRepository(10), StressColorMapper(12), KeyboardAccessory(~20), HrZoneCalculator, SleepStageCalculator, WorkoutNotificationManager, HcOfferViewModel, ExerciseDetailRepository, RoutineRepository, RoutineExerciseWithName, MasterExerciseSeeder(8), SupersetColor, ResolveWarnAt, WarmupCalculator(28), TimerEngine(~18)

**AI (hybrid key, shipped):** `GeminiKeyResolver` resolves: user key (EncryptedSharedPreferences `secure_ai_prefs`) → `BuildConfig.GEMINI_API_KEY` → `ApiKeyMissing` error. User sets their own key in Settings → AI card. `SecurePreferencesStore` interface + `EncryptedSecurePreferencesStore` prod impl. Never synced to Firestore.

**AI parser abstraction layer (P9, shipped):** `WorkoutTextParser` interface (single `suspend fun parseWorkoutText`). `WorkoutParserRouter` (`@Singleton`, cloud-only for now) delegates to `@Named("cloud") WorkoutTextParser`. `AiModule` binds router as `WorkoutTextParser` and provides `GeminiWorkoutParser` adapter as `@Named("cloud")`. `WorkoutPromptUtils` holds shared `workoutPromptJson`, `buildWorkoutPrompt()`, `parseWorkoutJsonResponse()` — extracted from `GeminiWorkoutParser` (not modified). Next step: `OnDeviceWorkoutParser` + AICore routing in `WorkoutParserRouter`.

**WorkoutStyle (P8 Tier 0, completed):** `WorkoutStyle` enum (`PURE_GYM` / `PURE_FUNCTIONAL` / `HYBRID`, default `HYBRID`) stored in `AppSettingsDataStore` under key `workout_style` (stringPreferencesKey). Exposed as `workoutStyle: Flow<WorkoutStyle>` + `setWorkoutStyle()`. Collected in `SettingsViewModel.loadAppSettings()` and exposed in `SettingsUiState`. Synced to Firestore via `pushAppPreferences()` / `pullAppPreferences()`. Settings card ("Workout Style") inserted between Units and Health Connect using `SingleChoiceSegmentedButtonRow`. No DB schema change. Gates future Template Builder "Add" UX (Tier 1+).

**Exercise.tags (P8 Tier 0, completed):** `tags TEXT NOT NULL DEFAULT '[]'` JSON column on `exercises` table (v50). `MasterExerciseSeeder` at v2.3.2 — ~372 total exercises. v2.1 seeded 25 new functional movements + tags on 27 existing. v2.2 (func_exercise_expanded_seed) adds 61 net-new CrossFit/Hyrox/calisthenics exercises and retags 28 additional existing exercises. v2.3 (func_cardio_exercise_seed) adds 29 new cardio entries (treadmill variants, cycling, swimming, air bikes, elliptical, stair climber, hiking, rucking, LISS/HIIT protocols) and retagging 9 existing exercises. v2.3.2 (func_exercise_type_retag_seed) retags 4 existing exercises to PLYOMETRIC (Box Jump, Box Jump Over, Tuck Jump, Clap Push-up) + adds 5 PLYOMETRIC (Depth Jump, Jump Squat, Plyo Push-Up, Lateral Bound, Hurdle Jump) + adds 12 STRETCH entries (Hip Flexor Stretch, Hamstring Stretch, Quad Stretch, Child's Pose, Pigeon Pose, Downward Dog, Cat-Cow, Shoulder Cross-Body Stretch, Tricep Stretch, Seated Spinal Twist, Standing Calf Stretch, Chest Opener Stretch). Tags: `"functional"`, `"hyrox"`, `"calisthenics"`, `"gymnastics"`, `"monostructural"`, `"cardio"`. New families: `planche_family`, `front_lever_family`, `back_lever_family`, `ring_skills_family`, `flag_family`, `carry_family`, `stretch_family`. `ExercisesViewModel` gains `functionalFilter: Boolean` in `ExercisesUiState` + `onFunctionalFilterToggled()`. `ExercisesScreen` has a "Functional" `FilterChip` (Mode row, `TimerGreen` colour) below the Equipment row.

**Countdown MM:SS fill-in input (P0, wrapped):** `CountdownRoulettePicker` + `WheelPicker` composables removed from `ToolsScreen.kt`. Replaced by `CountdownMmSsInput` (Column + Row with `Arrangement.Center`) containing two `CountdownDigitField` composables — 96dp fixed-width, 42sp JetBrainsMono Bold, `surfaceVariant` background with `shapes.medium` (12dp) corners, 2dp `primary` border on focus, `onSurface @ 18% alpha` placeholder `MM`/`SS` when empty. `TimerConfigField` gained `imeAction: ImeAction = ImeAction.Done`, `keyboardActions: KeyboardActions = KeyboardActions.Default`, `enabled: Boolean = true` params. `ToolsUiState` gains `countdownMinutesText: String = "01"` / `countdownSecondsText: String = "00"` (Dual-Property pattern); `setCountdownPreset` zero-pads with `"%02d".format(...)`.

**TimerEngine (P8 Tier 0, in-progress):** `TimerSpec` sealed class (`Amrap`, `Rft`, `Emom`, `Tabata`, `Countdown`, `Stopwatch`) in `util/timer/TimerSpec.kt`. `TimerEngineState` data class + `TimerPhase` enum (`IDLE/SETUP/WORK/REST`) in `util/timer/TimerEngineState.kt`. `TimerEngine` interface + `TimerEngineImpl` class in `util/timer/TimerEngine.kt` — pure suspend-function loop with `awaitNotPaused()` pause/resume. `ToolsViewModel` refactored to delegate all timer logic to `TimerEngineImpl` (no more inline `run*` functions). `JetBrainsMono` upgraded from `FontFamily.Monospace` fallback to a real `GoogleFont("JetBrains Mono")` declaration in `Type.kt`. `TimerDigitsXL` (96sp Bold), `TimerDigitsL` (48sp Bold), `TimerDigitsM` (28sp Medium) typography roles in `ui/theme/TimerDigitsStyles.kt`. `TimerPhase` moved from `ToolsViewModel.kt` to `util/timer/TimerEngineState.kt`; `ToolsScreen.kt` imports from new location.

---

## Feature Roadmap & Spec Index

See `ROADMAP.md` for the complete feature roadmap (phases P0–P9, statuses, effort sizes, dependency map) and the full spec index (domain → spec file routing table). **Check it before starting any new feature work.** Do not implement anything from `future_devs/` without confirming dependencies are met in `ROADMAP.md` and setting status to `in-progress`.

---

## Bug Tracking

Bugs are tracked in `bugs_to_fix/`. **`bugs_to_fix/BUG_TRACKER.md` is the single source of truth** — check it first to see what is Open, In Progress, or Fixed. File formats, templates, and lifecycle instructions are all in that file.

**Bug lifecycle:** `Open` → `In Progress` → `Completed` → `Wrapped`
- `/start_task <slug>` — marks In Progress (do this before writing any code)
- `/wrap_task <slug>` — runs build + test + commit + push, flips to Wrapped (run after user QA)

### Task Types

| Type | Scope | Spec location | Tracker |
|---|---|---|---|
| **Bug** | One defect, one fix | `bugs_to_fix/BUG_*.md` | `bugs_to_fix/BUG_TRACKER.md` |
| **Feature** | One deliverable, one session (XS–M) | `future_devs/*.md` | `ROADMAP.md` phase tables |
| **Epic** | Multi-task initiative (L–XL) with a root-level spec and N child Features | Root `<NAME>_SPEC.md` + children in `future_devs/` | `ROADMAP.md` Epics section |

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

### Task Lifecycle (mandatory for every task)
1. **Before starting** — read the relevant spec file(s) for the domain being touched (see Spec Index in `ROADMAP.md`).
2. **After completing** — update every spec file that was affected by the change. Also update the "Current State" section above for any schema change, new feature, removed feature, or architectural decision.

### Session Orientation
At the start of a session, orient yourself in this order:
1. Read `ROADMAP.md` — understand current phase, what's in-progress, and what's blocked
2. Read `bugs_to_fix/BUG_TRACKER.md` — check for any Open bugs before starting feature work
3. Run `git log --oneline -10` — see what was recently committed
4. Read the relevant spec file(s) for the domain you're about to touch

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
- **Firestore sync:** Any new entity that participates in cloud sync must include two columns (added in v35 pattern): `syncId: String = UUID.randomUUID().toString()` (stable cross-device identity) and `updatedAt: Long = 0L` (epoch ms, set on every mutation). Without these, LWW conflict resolution and push/pull logic will silently skip the entity.

### AI / Gemini (Action Parsing)
- The `ui/chat/` package contains only `UserContext.kt` (domain types used by ActionExecutor, WorkoutViewModel, StatePatchManager).
- New action types go in the `ActionBlock` sealed class, `ActionParser`, and `ActionExecutor` — all three must stay in sync.
- Keep system prompts focused. Do not bloat the context sent to Gemini.

### Health Connect
- Any new health data types must be declared in the manifest with the correct `<uses-permission>` and Health Connect permissions.
- Sync logic lives in the health package. Keep it isolated from UI logic.

### UI / Compose
- Use the project's existing theme tokens (colors, typography, shapes) from the `ui/theme` package. Do not hardcode colors or font sizes.
- New screens get their own ViewModel and are registered in the navigation graph.
- Reusable UI elements go in `ui/components`.
- Component conventions are defined in `THEME_SPEC.md §9` — read it before adding any UI component. Subsections: §9.1 Switch colors · §9.2 Keyboard navigation · §9.3 Input fields · §9.4 Dialogs & bottom sheets · §9.5 Loading/empty/FAB · §9.6 Screen chrome & layout · §9.7 Shape & spacing tokens.

### Specs & Documentation
- **Mandatory Alignment:** Before initiating any change to functionality, UX, or UI, you MUST verify alignment with the relevant `.md` specification files. These documents are the authoritative source of truth for system behavior and design standards.
- **Continuous Documentation:** Every addition, modification, or removal of app functionality, UI elements, or UX patterns must be immediately recorded in the corresponding `.md` file to maintain an accurate system blueprint.
- **Conflict Resolution:** If a user request directly contradicts an established specification in an `.md` file, you MUST stop and explicitly ask for clarification before proceeding with any code changes. Never silently override documented logic or invariants.
- **Technical Invariants:**
    - `WORKOUT_SPEC.md` is mandatory reading before any workout or routine change. It defines the state machine, rendering priority, and technical invariants that prevent regressions.
    - After implementing any feature defined by a spec, update that spec to reflect the final implementation details, state transitions, or UI components introduced.
    - If a spec is found to be outdated relative to the current implementation, note it and update it immediately.

### Task Lifecycle Skills

Use these skills to move tasks through their lifecycle — do not manually edit status fields:

| When | Skill |
|---|---|
| Discovered a bug or feature to build | `/file_task <description>` |
| Filing a new Epic | `/file_task epic: <description>` |
| Filing a child of an existing Epic | `/file_task epic:<parent-slug> <description>` |
| Starting work on a task | `/start_task <slug>` — marks In Progress, prints Touches files; auto-advances parent Epic to `in-progress` |
| Dev done, ready for QA | Mark `[x] Fixed` / set `completed` manually, create `SUMMARY_<slug>.md` for bugs |
| User QA passed | `/wrap_task <slug>` — build + test + commit + push + Wrapped; auto-updates Epic rollup |
| Want a dashboard | `/tasks_status` — shows Epics section at top with rollup counts |
| Planning parallel sessions | `/plan_sessions <N>` |

Non-skill documentation updates that are still manual:
- DB schema changed → increment Room version, add migration, update `DB_UPGRADE.md`
- New screen or ViewModel → register in `NAVIGATION_SPEC.md`, update `CLAUDE.md` Current State
- Spec found outdated → fix it immediately

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
- A brief 1–2 sentence summary of what the task was and what it does (no file-by-file breakdown, no "why this fixes it")
- `### HOW TO QA IT` — manual checklist for the user to verify on device

Do not advance to the next step until the user replies **APPROVED**.

### Security
- Never hardcode API keys or secrets. Use `local.properties` + `BuildConfig`.
- Use `EncryptedSharedPreferences` (Security Crypto) for any sensitive stored values.
- Validate all user input at the UI boundary before passing to the data layer.

### Code Quality
- **Before marking any feature or fix as `Completed` — run `/simplify`** to review changed code for reuse, quality, and efficiency while context is fresh.
- Optionally focus the review: `/simplify focus on Compose recomposition` or `/simplify focus on ViewModel state management`.

### UI / UX Reviews
- For any **new screen or open-ended visual design** where layout, color, spacing, or interaction patterns are not already defined by a spec file — invoke the `ui-ux-pro-max` skill.
- Do **not** invoke it for spec-driven implementations: if a `*_SPEC.md` file already defines the exact tokens, typography, layout, and animation parameters, the spec is the design authority and `ui-ux-pro-max` adds no value.
- Stack context for this project: Jetpack Compose + Material Design 3, Pro Tracker v6.0 palette (see THEME_SPEC.md).
- Always use `MaterialTheme.colorScheme.*` tokens — no hardcoded colors.

### MCP Servers
- **`mobile`** (user-scoped, stdio): `npx -y claude-in-mobile` — provides mobile development tools (Android/iOS). Use this MCP server for device interaction, app inspection, and mobile-specific tasks in this project.
