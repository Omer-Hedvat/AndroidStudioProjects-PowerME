# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v38, 17 entities, 17 DAOs)
- Auth/Backend: Firebase Auth (email/password + Google Sign-In via Credential Manager) + Firestore
- Health: Health Connect API
- Charts: Vico (Material 3)
- Drag-and-drop: `sh.calvin.reorderable:reorderable-compose`
- Build: Gradle Kotlin DSL, KSP, min SDK 26, target SDK 35

**Package:** `com.powerme.app`

**Main Features Implemented:**
- Workout routine creation and management
- Active workout tracking (sets, reps, weight, duration)
- **Workout/Routine flow** — Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary. See **WORKOUT_SPEC.md**. Per-set-type rest: `computeRestDuration()` returns 0s DROP, 30s WARMUP→WARMUP, exercise default otherwise. `startRestTimer()` skips (returns early) if duration ≤ 0. `completeSet()` skips timer on last set unless `exercise.restAfterLastSet == true`. Configurable via "Set Rest Timers" dialog ("Rest after last set" toggle, default OFF).
- **Organize Mode** — superset grouping, ungrouping, drag-reorder in one persistent session. CAB: Done + "Organize • N" + contextual Group/Ungroup icon. See WORKOUT_SPEC.md.
- Theme mode: LIGHT/DARK/SYSTEM via `ThemeMode` enum + `AppSettingsDataStore`. See THEME_SPEC.md.
- Unit system: METRIC/IMPERIAL via `UnitSystem` enum; `UnitConverter` is single source of truth; storage always metric. See SETTINGS_SPEC.md.
- Google Fonts: `BarlowCondensed` + `Barlow` via GoogleFont.Provider; 11 M3 typography roles; fallback to system sans-serif.
- Shape system: `PowerMeShapes` (6/10/16/24/32dp); `PowerMeDefaults` in `ui/theme/Defaults.kt`. See THEME_SPEC.md §7–§9.
- Nav tab order: Workouts / History / Exercises / Tools / Trends
- Exercise library (150+ exercises, YouTube demos, muscle groups, equipment types)
- Multi-select muscle group + equipment filter chips in Exercises tab (AND-combined). See EXERCISES_SPEC.md §4.
- Firebase Auth (email/password + Google Sign-In): two-step Credential Manager strategy; account linking flow; debug SHA-1 registered. See PROFILE_SETUP_SPEC.md.
- User profile (DOB, height, weight, gender, goals, chronotype, occupation); two-step onboarding. See PROFILE_SETUP_SPEC.md + SETTINGS_SPEC.md.
- Gym profiles data layer preserved (GymProfileRepository, GymProfile entity) but gym setup navigation removed from Settings
- Health Connect sync: sleep, HRV, RHR, steps; 7 READ permissions; anomaly detection; PermissionsRationaleActivity with Android 13+14 fallbacks. See HEALTH_CONNECT_SPEC.md.
- **Health Connect Body & Vitals card** (Trends tab): 3×3 grid (Age/Weight/BMI/BodyFat/Height/Steps/Sleep/HRV/RHR); 4 states (CHECKING/UNAVAILABLE/NOT_GRANTED/GRANTED). See HEALTH_CONNECT_SPEC.md.
- Injury / medical ledger (red-list / yellow-list exercises)
- Performance metrics, trends, and charts
- State history auditing trail
- DataStore preferences (plates config, timers, language, keepScreenOn, dailyStepTarget)
- **Trends tab** — `ReadinessEngine` (z-score, HRV 0.45/Sleep 0.35/RHR 0.20), `TrendsDao` (6 aggregate queries), `TrendsRepository`, `TrendsViewModel` (11 StateFlows), `VicoChartHelpers`. See TRENDS_SPEC.md.
- **ReadinessGaugeCard** — Custom Canvas arc (240° sweep gradient), needle dot, NoData/Calibrating/Score states. See TRENDS_SPEC.md.
- Clocks tab (Stopwatch, Timer, Tabata, EMOM): centiseconds display, countdown beeps, haptics, wake lock. See TOOLS_SPEC.md.
- StatisticalEngine (Epley 1RM, Bayesian M-Estimate 1RM), WeeklyInsightsAnalyzer, AnalyticsRepository. See HISTORY_ANALYTICS_SPEC.md.
- ExerciseDetailSheet (ModalBottomSheet): Form Cues (gold banner) + YouTube TextButton. See EXERCISES_SPEC.md §5–§6.
- **Elastic search**: token-based word-order-independent; synonym expansion via `ExerciseSynonyms`. See EXERCISES_SPEC.md.
- **Firestore cloud sync**: push on finish/save/archive; LWW pull; settings + app preferences sync; "Back Up Now" + auto-restore on foreground. See SETTINGS_SPEC.md.

**Color System:** Pro Tracker v6.0. See THEME_SPEC.md for all tokens, DarkColorScheme, LightColorScheme, semantic colors, and usage rules.

**Database:** Room v38 — 16 entities, 16 DAOs. Migrations v6→v38 covered. See `DB_UPGRADE.md` for full history.
Key facts: UUID String PKs (v31+), Firestore sync columns (v35), soft deletes via `isArchived`, `routineName` denormalized on `workouts` (v36), per-type rest timers on exercises (v32), per-set weight/reps/setTypes in `routine_exercises` (v28–v29), `restAfterLastSet` flag on exercises (v38).

**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides `parseDecimal()`, `parseReps()`, `isLeakedMetric()`, `MIGRATION_SQL`, `MIGRATION_SQL_V19`. No inline try-catch in ViewModels or Composables.

**Health Connect permissions:** 7 READ permissions: READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. `MetricType` enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT. Height sync via `getLatestHeight()` (365-day window); dual-sink to MetricLog + User entity.

**Unit Tests (src/test/, 17 files, ~332 tests — all passing):**
ExerciseDao, PreMigrationValidator, GymProfileRepository(12), SQLSafetyValidator(25), SurgicalValidator(18), PlateCalculator(18), UnitConverter(~40), StatisticalEngine(13), ReadinessEngine(16), HistoryViewModel(13), ExerciseFilter(24), WorkoutViewModel(58), SettingsVM-HC(7), SettingsVM-PersonalInfo(8), MetricLogRepository(4), MetricsVM-BodyVitals(6), AuthVM-GoogleSignIn(9), ProfileSetupVM(7)

---

## Feature Specs

Read the relevant spec before touching files in that domain.

| Spec File | Status | Domain |
|---|---|---|
| `WORKOUT_SPEC.md` | ✅ Complete | Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary, warmup, notes, minimize/maximize, Iron Vault |
| `EXERCISES_SPEC.md` | ✅ Exists | Exercise library (150+ exercises), search/filter UI, ExerciseDetailSheet, equipment display, YouTube demo intent, picker mode |
| `HISTORY_ANALYTICS_SPEC.md` | ✅ Complete | HistoryScreen layout, StatisticalEngine (Epley/Bayesian 1RM, Volume, dynamic PRs), WeeklyInsightsAnalyzer, WorkoutDetailScreen data contract + retroactive edit flow, BoazPerformanceAnalyzer (V2 stub) |
| `NAVIGATION_SPEC.md` | ✅ Complete | Route map (16 routes), auth decision tree, WorkoutViewModel scope, minimize/maximize state machine, transitions, MainAppScaffold + MainActivity contracts |
| `THEME_SPEC.md` | ✅ Complete | Pro Tracker v6.0 palette (all tokens), DarkColorScheme, LightColorScheme, ThemeMode system, typography (Barlow + BarlowCondensed + JetBrainsMono), semantic color contexts, WCAG contrast audit, token rules |
| `TOOLS_SPEC.md` | ✅ Complete | Clocks tab — Stopwatch, Countdown, Tabata, EMOM timer modes, audio/haptic alerts, wake lock, input validation, phase state machine |
| `HEALTH_CONNECT_SPEC.md` | ✅ Complete | Permission declaration, sync logic (8 data types, parallel reads), dual-sink write, anomaly detection, 3 UI card states |
| `TRENDS_SPEC.md` | 🚧 In Progress | Trends tab — Readiness gauge, e1RM progression, volume trends, muscle group volume, effective sets, body composition overlay, NEAT guardrail, chronotype/sleep, body heatmap (future phase), ReadinessEngine algorithm, TrendsDao queries, Vico chart integration guide |
| `PROFILE_SETUP_SPEC.md` | ✅ Complete | Onboarding profile setup — two-step flow, HC offer, all 11 fields, unit selector, shared composables |
| `SETTINGS_SPEC.md` | ✅ Complete | Settings screen — all 10 cards, Personal Info edit, Body Metrics, HC sync, SettingsViewModel state |
| `DB_UPGRADE.md` | ✅ Exists | Migration history v6→v38, schema changes |
| `DB_ARCHITECTURE.md` | ✅ Exists | Core entity relationships, template-to-instance pattern, UUID migration, workout lifecycle |

---

## Bug Tracking

Bugs are tracked in `bugs_to_fix/`. Each bug is a separate `.md` file.

**File naming:** `BUG_<short-slug>.md` (e.g. `BUG_rest_timer_flicker.md`)

**File format:**
```markdown
# BUG: <title>

## Status
[ ] Open / [x] Fixed

## Description
<what is broken and where>

## Steps to Reproduce
1. ...

## Assets
- Images: `bugs_to_fix/assets/<slug>/screenshot.png`
- Videos: `bugs_to_fix/assets/<slug>/recording.mp4`
- Related spec: `WORKOUT_SPEC.md §X`

## Fix Notes
<populated after the fix is applied>
```

When assigned a bug from this folder: read the `.md` file, check linked assets, fix the issue, mark **Status** as `[x] Fixed`, fill **Fix Notes**, then run the QA protocol (build → tests → screenshot).

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
- Stack context for this project: Jetpack Compose + Material Design 3, Pro Tracker v6.0 palette (see THEME_SPEC.md).
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
