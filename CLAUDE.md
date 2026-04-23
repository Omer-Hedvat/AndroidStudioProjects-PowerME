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

**Database:** Room v48 — 18 entities, 20 DAOs (`TrendsDao` and `ExerciseDetailRepository` are query-only aggregators, no corresponding entities). Migrations v6→v48 covered. See `DB_UPGRADE.md` for full history.
Key facts: UUID String PKs (v31+), Firestore sync columns (v35), soft deletes via `isArchived`, `routineName` denormalized on `workouts` (v36), per-type rest timers on exercises (v32), per-set weight/reps/setTypes in `routine_exercises` (v28–v29), `restAfterLastSet` flag on exercises (v38), `experienceLevel`/`trainingAgeYears` on users (v39), `health_history_entries` table (v40), `sessionRating` on workouts (v41), HC extended reads columns on `health_connect_sync` (v43), `primaryJoints`/`secondaryJoints` on `exercises` (v44), `source`/`importBatchId` on `workouts` (v45), `exercise_stress_vectors` table with `BodyRegion` enum (16 values) + `StressAccumulationEngine` (v46), `userNote TEXT NOT NULL DEFAULT ''` on `exercises` (v47), CSV-imported RPE data-fix (v48).

**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides `parseDecimal()`, `parseReps()`, `isLeakedMetric()`, `MIGRATION_SQL`, `MIGRATION_SQL_V19`. No inline try-catch in ViewModels or Composables.

**Health Connect permissions:** 7 READ permissions: READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. `MetricType` enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT. Height sync via `getLatestHeight()` (365-day window); dual-sink to MetricLog + User entity.

**Unit Tests (src/test/, 62 files, ~937 tests — all passing):**
ExerciseDao, PreMigrationValidator, GymProfileRepository(12), SQLSafetyValidator(25), SurgicalValidator(18), PlateCalculator(18), UnitConverter(~40), StatisticalEngine(13), ReadinessEngine(16), HistoryViewModel(13), ExerciseFilter(24), WorkoutViewModel(148), SettingsVM-HC(7), SettingsVM-TimerSound(5), SettingsVM-ApiKey(7), SettingsVM-PersonalInfo, ProfileVM-PersonalInfo(7), ProfileVM-Logout(1), MetricLogRepository(4), MetricsVM-BodyVitals(6), AuthVM-GoogleSignIn(9), ProfileSetupVM(7), WorkoutSummaryVM(28), WorkoutDetailVM, RpeHelper(5), Joint(14), CsvFormatDetector(~21), CsvRowParser(~20), StrongCsvParser, StressAccumulationEngine(11), StressAccumulationEngineDetail(11), StressRecomputeGate(6), ExerciseStressVectorSeedData(10), TrendsRepositoryChronotype(~10), TrendsRepositoryBodyComposition, TrendsRepositoryEffectiveSets, TrendsRepositoryWeeklyGrouping, TrendsViewModelChronotype(7), TrendsViewModelEffectiveSetsFilter(10), TrendsViewModelBodyStressRefresh(4), TrendsViewModelDataFlags(~16), TrendsViewModelBodyComposition, TrendsViewModelDeepLink, JaroWinkler(10), ExerciseMatcher(13), GeminiKeyResolver(4), GeminiWorkoutParser(16), AiWorkoutViewModel(16), StressColorMapper(12), KeyboardAccessory(~20), HrZoneCalculator, SleepStageCalculator, WorkoutNotificationManager, HcOfferViewModel, ExerciseDetailRepository, RoutineRepository, RoutineExerciseWithName, MasterExerciseSeeder, SupersetColor, ResolveWarnAt, WarmupCalculator(28)

**AI (hybrid key, shipped):** `GeminiKeyResolver` resolves: user key (EncryptedSharedPreferences `secure_ai_prefs`) → `BuildConfig.GEMINI_API_KEY` → `ApiKeyMissing` error. User sets their own key in Settings → AI card. `SecurePreferencesStore` interface + `EncryptedSecurePreferencesStore` prod impl. Never synced to Firestore.

**AI parser abstraction layer (P9, shipped):** `WorkoutTextParser` interface (single `suspend fun parseWorkoutText`). `WorkoutParserRouter` (`@Singleton`, cloud-only for now) delegates to `@Named("cloud") WorkoutTextParser`. `AiModule` binds router as `WorkoutTextParser` and provides `GeminiWorkoutParser` adapter as `@Named("cloud")`. `WorkoutPromptUtils` holds shared `workoutPromptJson`, `buildWorkoutPrompt()`, `parseWorkoutJsonResponse()` — extracted from `GeminiWorkoutParser` (not modified). Next step: `OnDeviceWorkoutParser` + AICore routing in `WorkoutParserRouter`.

---

## Feature Roadmap

`ROADMAP.md` at the project root is the single source of truth for all planned features — phases P0–P6, statuses, effort sizes, and dependency map. **Check it before starting any new feature work.**

Future feature specs live in `future_devs/`. Each spec has a metadata header (Phase, Status, Effort, Depends on). Do not implement anything from `future_devs/` without first checking `ROADMAP.md` to confirm dependencies are met and updating the status to `in-progress`.

## Spec Index

Read the relevant spec before touching files in that domain.

| Spec File | Domain |
|---|---|
| `WORKOUT_SPEC.md` | Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary |
| `EXERCISES_SPEC.md` | Exercise library, search/filter, ExerciseDetailSheet |
| `HISTORY_ANALYTICS_SPEC.md` | History screen, StatisticalEngine (1RM, Volume, PRs), WorkoutDetailScreen |
| `NAVIGATION_SPEC.md` | Route map, auth decision tree, WorkoutViewModel scope, transitions |
| `THEME_SPEC.md` | Color palette, typography, shapes, dark/light scheme |
| `CLOCKS_SPEC.md` | Clocks tab — Stopwatch, Countdown, Tabata, EMOM |
| `HEALTH_CONNECT_SPEC.md` | HC permissions, sync logic, Phase B writes, backfill |
| `TRENDS_SPEC.md` | Trends tab — all chart cards, ReadinessEngine, TrendsDao, body heatmap |
| `PROFILE_SETUP_SPEC.md` | Onboarding, two-step flow, HC offer |
| `SETTINGS_SPEC.md` | Settings screen, all cards, SettingsViewModel |
| `AI_SPEC.md` | AI workout generation — text/photo → routine, Gemini + OCR, matching, enhancement roadmap. Single source of truth for AI. |
| `AI_SPEC.md §12` | On-device AI — Android AICore (Gemma 4 / Gemini Nano), graceful degradation, 0-byte APK footprint, privacy model |
| `AI_BACKLOG.md` | Long-horizon AI brainstorm (46 items with scope tags). Companion to `AI_SPEC.md`. |
| `DB_UPGRADE.md` | Migration history, schema changes |
| `DB_ARCHITECTURE.md` | Entity relationships, template-to-instance, UUID migration |
| `future_devs/OBSERVABILITY_BETA_SPEC.md` | Observability — Crashlytics crash reporting + Firebase Analytics action trail + Timber logging facade |
| `future_devs/WORKOUTS_QUICK_START_CHOOSER_SPEC.md` | Workouts page restructure — Quick Start becomes a 3-way chooser (exercises / picture / text); removes standalone AI button |
| `future_devs/HC_UX_RESTRUCTURE_SPEC.md` | P2 | HC UX — Settings shows Connected badge + "View in Profile" button; HC metrics card moves to Profile screen |
| `future_devs/DB_SYNONYM_FOUNDATION_SPEC.md` | P5 | DB synonym layer — UserExerciseSynonym entity + DAO + repository for exercise name learning |
| `FUNCTIONAL_TRAINING_SPEC.md` | Functional Training — AMRAP / RFT / EMOM Hybrid mode. Block concept, WorkoutStyle preference, timer engine, live UX (blind-tap zone), exercise tags, foreground-service lifecycle |
| `future_devs/AI_PARSER_INTERFACE_LAYER_SPEC.md` | P9 | On-device AI prerequisite — `WorkoutTextParser` interface + `WorkoutParserRouter` cloud-only router + `AiModule` Hilt bindings |
| `future_devs/` | All planned features — see `ROADMAP.md` for phase/status |

---

## Bug Tracking

Bugs are tracked in `bugs_to_fix/`. **`bugs_to_fix/BUG_TRACKER.md` is the single source of truth** — check it first to see what is Open, In Progress, or Fixed. File formats, templates, and lifecycle instructions are all in that file.

**Bug lifecycle:** `Open` → `In Progress` → `Completed` → `Wrapped`
- `/start_task <slug>` — marks In Progress (do this before writing any code)
- `/wrap_task <slug>` — runs build + test + commit + push, flips to Wrapped (run after user QA)

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
1. **Before starting** — read the relevant spec file(s) for the domain being touched (see Feature Specs table above).
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
| Starting work on a task | `/start_task <slug>` — marks In Progress, prints Touches files |
| Dev done, ready for QA | Mark `[x] Fixed` / set `completed` manually, create `SUMMARY_<slug>.md` for bugs |
| User QA passed | `/wrap_task <slug>` — build + test + commit + push + Wrapped |
| Want a dashboard | `/tasks_status` |
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
- `### WHAT CHANGED` — bullet summary of every file and behaviour modified
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
