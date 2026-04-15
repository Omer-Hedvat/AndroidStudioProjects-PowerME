# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v41, 17 entities, 18 DAOs)
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
- **Active workout set row UX** — Set row spacing 8dp (2dp `Spacer` between each `SetWithRestRow`). Completed rows: `TimerGreen.copy(alpha=0.12f).compositeOver(surface)` background (opaque blend applied at `SetWithRestRow` level; `compositeOver` prevents swipe-delete red bleed-through). `WorkoutInputField` selects all text on every tap via `PressInteraction.Release` collected from `interactionSource` (fires even when field already focused). Set type dropdown clears keyboard focus (`focusManager.clearFocus()`) on item selection, Delete Timer click, and dismiss. Rest separator auto-hides when timer naturally expires: both `onTimerFinish()` and in-process coroutine add `"${exerciseId}_${setOrder}"` to `hiddenRestSeparators`. Golden RPE indicator badge on completed sets (✦ gold for 8–9, amber dot for 7–7.5, grey dot for <7, red dot for ≥9.5–10); logic in `util/RpeHelper.kt`. Timed exercise countdown timer in `TimedSetRow` (IDLE→RUNNING→PAUSED→COMPLETED state machine, local Compose state, audio/haptic via `WorkoutViewModel.timerFinishedFeedback()` + `timerWarningTickFeedback()`). See WORKOUT_SPEC.md §4.4 and §4.8.
- **Organize Mode** — superset grouping, ungrouping, drag-reorder in one persistent session. CAB: Done + "Organize • N" + contextual Group/Ungroup icon. See WORKOUT_SPEC.md.
- Theme mode: LIGHT/DARK/SYSTEM via `ThemeMode` enum + `AppSettingsDataStore`. See THEME_SPEC.md.
- Unit system: METRIC/IMPERIAL via `UnitSystem` enum; `UnitConverter` is single source of truth; storage always metric. See SETTINGS_SPEC.md.
- Google Fonts: `BarlowCondensed` + `Barlow` via GoogleFont.Provider; 11 M3 typography roles; fallback to system sans-serif.
- Shape system: `PowerMeShapes` (6/10/16/24/32dp); `PowerMeDefaults` in `ui/theme/Defaults.kt`. See THEME_SPEC.md §7–§9.
- Nav tab order: Workouts / History / Exercises / Tools / Trends
- Exercise library (150+ exercises, muscle groups, equipment types)
- Multi-select muscle group + equipment filter chips in Exercises tab (AND-combined). See EXERCISES_SPEC.md §4.
- Firebase Auth (email/password + Google Sign-In): two-step Credential Manager strategy; account linking flow; debug SHA-1 registered. See PROFILE_SETUP_SPEC.md.
- User profile (DOB, height, weight, gender, goals, chronotype, occupation); two-step onboarding. See PROFILE_SETUP_SPEC.md + SETTINGS_SPEC.md.
- Gym profiles data layer preserved (GymProfileRepository, GymProfile entity) but gym setup navigation removed from Settings
- Health Connect sync: sleep, HRV, RHR, steps; 7 READ permissions; anomaly detection; PermissionsRationaleActivity with Android 13+14 fallbacks. See HEALTH_CONNECT_SPEC.md.
- **Health Connect Body & Vitals card** (Trends tab): 3×3 grid (Age/Weight/BMI/BodyFat/Height/Steps/Sleep/HRV/RHR); 4 states (CHECKING/UNAVAILABLE/NOT_GRANTED/GRANTED). See HEALTH_CONNECT_SPEC.md.
- Injury / medical ledger (red-list / yellow-list exercises)
- **Profile/Settings split** — separate `ProfileScreen` (route `profile`) and trimmed `SettingsScreen`. Profile icon (AccountCircle) + Settings gear in TopAppBar actions. `ProfileViewModel` owns Personal Info, Body Metrics, Fitness Level, Health History. See `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md`.
- **Fitness Level card** in ProfileScreen — 4 tappable cards (Novice/Trained/Experienced/Athlete) + training age slider (0–30). Persisted to `User` entity via `experienceLevel` + `trainingAgeYears` columns (v39).
- **Health History ledger** in ProfileScreen — injuries/surgeries/conditions with severity tiers; add/edit via `ModalBottomSheet`; auto-rebuilds `MedicalLedger` red/yellow lists on save. `HealthHistoryEntry` entity in `health_history_entries` table (v40).
- Performance metrics, trends, and charts
- State history auditing trail
- DataStore preferences (plates config, timers, language, keepScreenOn, dailyStepTarget)
- **Trends tab** — `ReadinessEngine` (z-score, HRV 0.45/Sleep 0.35/RHR 0.20), `TrendsDao` (6 aggregate queries), `TrendsRepository`, `TrendsViewModel` (11 StateFlows), `VicoChartHelpers`. See TRENDS_SPEC.md.
- **ReadinessGaugeCard** — Custom Canvas arc (240° sweep gradient), needle dot, NoData/Calibrating/Score states. See TRENDS_SPEC.md.
- **VolumeTrendCard** — Weekly volume bar chart (Vico `ColumnCartesianLayer`, ProViolet) + 4-week MA line overlay (TimerGreen), time range FilterChip row. `ui/metrics/charts/VolumeTrendCard.kt`. See `future_devs/TRENDS_CHARTS_SPEC.md §Step 2`.
- **E1RMProgressionCard** — Single-exercise e1RM progression line chart (raw + 3-session MA), scrollable FilterChip exercise picker, percent-change badge. `ui/metrics/charts/E1RMProgressionCard.kt`. See `future_devs/TRENDS_CHARTS_SPEC.md §Step 3`.
- Clocks tab (Stopwatch, Timer, Tabata, EMOM): centiseconds display, countdown beeps, haptics, wake lock. See TOOLS_SPEC.md.
- StatisticalEngine (Epley 1RM, Bayesian M-Estimate 1RM), WeeklyInsightsAnalyzer, AnalyticsRepository. See HISTORY_ANALYTICS_SPEC.md.
- ExerciseDetailSheet (ModalBottomSheet): Form Cues (gold banner). YouTube TextButton removed (deprecated field). See EXERCISES_SPEC.md §5–§6.
- **Elastic search**: token-based word-order-independent; synonym expansion via `ExerciseSynonyms`. See EXERCISES_SPEC.md.
- **Firestore cloud sync**: push on finish/save/archive; LWW pull; settings + app preferences sync; "Back Up Now" + auto-restore on foreground. See SETTINGS_SPEC.md.
- **WorkoutSummaryScreen** — unified post-workout + history detail view. Hero header (date/duration/volume/sets/PRs), 5-star session rating, per-exercise cards (best set, e1RM, volume delta, avg RPE, golden zone badge, PR badge, View Trend button), muscle group distribution bars, notes field. Route: `workout_summary/{workoutId}?isPostWorkout={bool}&syncType={string}`. See `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md`.
- **Health Connect Phase B (write)** — completed workouts pushed to HC as `ExerciseSessionRecord` on `finishWorkout()`. `WRITE_EXERCISE` + `READ_EXERCISE` permissions requested in Connect flow (best-effort; denied write never blocks "Connected" state). `CORE_PERMISSIONS` (7 reads) gates Connected UI; `ALL_PERMISSIONS` = CORE + exercise read/write (launcher only). `deriveHcExerciseType()` maps exercise composition → WEIGHTLIFTING / OTHER_WORKOUT / YOGA. `clientRecordId = workout.id` prevents duplicate writes. See `HEALTH_CONNECT_SPEC.md §8`.

**Color System:** Pro Tracker v6.0. See THEME_SPEC.md for all tokens, DarkColorScheme, LightColorScheme, semantic colors, and usage rules.

**Database:** Room v41 — 17 entities, 18 DAOs (`TrendsDao` is query-only, no corresponding entity). Migrations v6→v41 covered. See `DB_UPGRADE.md` for full history.
Key facts: UUID String PKs (v31+), Firestore sync columns (v35), soft deletes via `isArchived`, `routineName` denormalized on `workouts` (v36), per-type rest timers on exercises (v32), per-set weight/reps/setTypes in `routine_exercises` (v28–v29), `restAfterLastSet` flag on exercises (v38), `experienceLevel`/`trainingAgeYears` on users (v39), `health_history_entries` table (v40), `sessionRating` on workouts (v41).

**SurgicalValidator.kt** (`util/SurgicalValidator.kt`): All real-time numeric input (Weight, Reps, Height) passes through this validator. Provides `parseDecimal()`, `parseReps()`, `isLeakedMetric()`, `MIGRATION_SQL`, `MIGRATION_SQL_V19`. No inline try-catch in ViewModels or Composables.

**Health Connect permissions:** 7 READ permissions: READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT, READ_SLEEP, READ_HEART_RATE_VARIABILITY, READ_RESTING_HEART_RATE, READ_STEPS. `MetricType` enum: WEIGHT, BODY_FAT, CALORIES, HEIGHT. Height sync via `getLatestHeight()` (365-day window); dual-sink to MetricLog + User entity.

**Unit Tests (src/test/, 20 files, ~365 tests — all passing):**
ExerciseDao, PreMigrationValidator, GymProfileRepository(12), SQLSafetyValidator(25), SurgicalValidator(18), PlateCalculator(18), UnitConverter(~40), StatisticalEngine(13), ReadinessEngine(16), HistoryViewModel(13), ExerciseFilter(24), WorkoutViewModel(58), SettingsVM-HC(7), ProfileVM-PersonalInfo(7), MetricLogRepository(4), MetricsVM-BodyVitals(6), AuthVM-GoogleSignIn(9), ProfileSetupVM(7), WorkoutSummaryVM(21), RpeHelper(5)

---

## Feature Roadmap

`ROADMAP.md` at the project root is the single source of truth for all planned features — phases P0–P6, statuses, effort sizes, and dependency map. **Check it before starting any new feature work.**

Future feature specs live in `future_devs/`. Each spec has a metadata header (Phase, Status, Effort, Depends on). Do not implement anything from `future_devs/` without first checking `ROADMAP.md` to confirm dependencies are met and updating the status to `in-progress`.

---

## Feature Specs

Read the relevant spec before touching files in that domain.

### Implemented

| Spec File | Status | Domain |
|---|---|---|
| `WORKOUT_SPEC.md` | ✅ Complete | Active workout, edit mode, rest timers, supersets, routine sync, post-workout summary, warmup, notes, minimize/maximize, Iron Vault |
| `EXERCISES_SPEC.md` | ✅ Exists | Exercise library (150+ exercises), search/filter UI, ExerciseDetailSheet, equipment display, picker mode |
| `HISTORY_ANALYTICS_SPEC.md` | ✅ Complete | HistoryScreen layout, StatisticalEngine (Epley/Bayesian 1RM, Volume, dynamic PRs), WeeklyInsightsAnalyzer, WorkoutDetailScreen data contract + retroactive edit flow, BoazPerformanceAnalyzer (V2 stub) |
| `NAVIGATION_SPEC.md` | ✅ Complete | Route map (16 routes), auth decision tree, WorkoutViewModel scope, minimize/maximize state machine, transitions, MainAppScaffold + MainActivity contracts |
| `THEME_SPEC.md` | ✅ Complete | Pro Tracker v6.0 palette (all tokens), DarkColorScheme, LightColorScheme, ThemeMode system, typography (Barlow + BarlowCondensed + JetBrainsMono), semantic color contexts, WCAG contrast audit, token rules |
| `TOOLS_SPEC.md` | ✅ Complete | Clocks tab — Stopwatch, Countdown, Tabata, EMOM timer modes, audio/haptic alerts, wake lock, input validation, phase state machine |
| `HEALTH_CONNECT_SPEC.md` | ✅ Complete | Permission declaration, sync logic (8 data types, parallel reads), dual-sink write, anomaly detection, 3 UI card states; Phase B: write completed workouts as `ExerciseSessionRecord` |
| `TRENDS_SPEC.md` | 🚧 In Progress | Trends tab — Readiness gauge, e1RM progression, volume trends, muscle group volume, effective sets, body composition overlay, NEAT guardrail, chronotype/sleep, body heatmap (future phase), ReadinessEngine algorithm, TrendsDao queries, Vico chart integration guide |
| `PROFILE_SETUP_SPEC.md` | ✅ Complete | Onboarding profile setup — two-step flow, HC offer, all 11 fields, unit selector, shared composables |
| `SETTINGS_SPEC.md` | ✅ Complete | Settings screen — all 10 cards, Personal Info edit, Body Metrics, HC sync, SettingsViewModel state |
| `DB_UPGRADE.md` | ✅ Exists | Migration history v6→v41, schema changes |
| `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md` | ✅ Step A done | WorkoutSummaryScreen — hero header, session rating, per-exercise cards, muscle group bars, notes |
| `DB_ARCHITECTURE.md` | ✅ Exists | Core entity relationships, template-to-instance pattern, UUID migration, workout lifecycle |

### Future (not yet implemented — see `ROADMAP.md` for phase/status)

| Spec File | Phase | Domain |
|---|---|---|
| `future_devs/ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md` | P0 / P1 | Row spacing, golden RPE indicator, timed exercise countdown timer |
| `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md` | P4 (Step B only) | Trends deep-link (`?exerciseId=`) — requires E1RMProgressionCard |
| `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md` | P2 / P3 | Profile/Settings split, health history ledger, fitness level, RPE auto-pop |
| `future_devs/TRENDS_CHARTS_SPEC.md` | P4 / P5 | Trends chart cards Steps 2–8 (Volume, E1RM, Muscle Balance, Effective Sets, Body Composition, Steps, Chronotype) |
| `future_devs/HEALTH_CONNECT_EXTENDED_READS_SPEC.md` | P4 | HC extended reads — HR, Calories, VO₂ Max, Distance, SpO₂ |
| `future_devs/HC_BACKFILL_SPEC.md` | P4 | HC Backfill — one-time push of last 90 days on permission grant |
| `future_devs/CSV_IMPORT_SPEC.md` | P5 | CSV workout history import (Strong, Hevy, FitBod, generic) |

---

## Bug Tracking

Bugs are tracked in `bugs_to_fix/`. **`bugs_to_fix/BUG_TRACKER.md` is the single source of truth** — check it first to see what is Open, In Progress, or Fixed. Update it at the start and end of every bug-fix session.

Each bug also has its own `.md` file.

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

**Summary file naming:** `SUMMARY_<short-slug>.md` (e.g. `SUMMARY_rest_timer_flicker.md`)

**Summary file format:**
```markdown
# Fix Summary: <title>

## Root Cause
<what caused the bug>

## Files Changed
| File | Change |
|---|---|
| `path/to/file` | description of change |

## Surfaces Fixed
- <screen or behaviour fixed>

## How to QA
- <step-by-step manual test the user can run on device>
```

**Bug lifecycle:** `Open` → `In Progress` → `Completed` → `Wrapped`

When assigned a bug: read the `.md` file, check linked assets, fix the issue, mark **Status** as `[x] Fixed`, fill **Fix Notes**, create a `SUMMARY_<slug>.md` file, update `BUG_TRACKER.md` status to `Completed`. The user QAs on device, then runs `/wrap_bugfix <slug>` to flip it to `Wrapped` (simplify + build + test + commit + push). Multiple bugs can sit in `Completed` waiting for batch QA.

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

### Mandatory Documentation Checklist (run at end of every task)

No task is complete until ALL applicable items below are done:

| What happened | What to update |
|---|---|
| Bug discovered | Add row to `bugs_to_fix/BUG_TRACKER.md` + create `bugs_to_fix/BUG_<slug>.md` |
| Bug dev done | Mark `[x] Fixed` in `BUG_<slug>.md`, fill Fix Notes, create `bugs_to_fix/SUMMARY_<slug>.md` (root cause, files changed, surfaces fixed, How to QA), update `BUG_TRACKER.md` status to `Completed` |
| Bug QA'd by user | Run `/wrap_bugfix <slug>` → flips status to `Wrapped`, runs simplify + build + test + commit + push |
| Feature started | Set status `in-progress` in `ROADMAP.md` + spec file header |
| Feature dev done | Set status `completed` in `ROADMAP.md`; update the relevant implemented spec (e.g. `WORKOUT_SPEC.md`); append a **How to QA** section to the feature's `future_devs/<NAME>_SPEC.md`; update "Current State" in `CLAUDE.md` if schema/architecture changed |
| Feature QA'd by user | Run `/wrap_feature <name>` → flips status to `wrapped`, runs simplify + build + test + commit + push |
| New feature conceived (not yet built) | Create `future_devs/<NAME>_SPEC.md` with standardized header + add row to `ROADMAP.md` under the correct phase |
| DB schema changed | Increment Room version, add migration, update `DB_UPGRADE.md` |
| New screen or ViewModel added | Register in `NAVIGATION_SPEC.md` route map and update `CLAUDE.md` Current State |
| Spec found to be outdated | Fix the spec immediately — do not leave stale documentation |

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

### MCP Servers
- **`mobile`** (user-scoped, stdio): `npx -y claude-in-mobile` — provides mobile development tools (Android/iOS). Use this MCP server for device interaction, app inspection, and mobile-specific tasks in this project.
