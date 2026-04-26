# PowerME — Claude Working Document

## Current State

**App:** PowerME — A comprehensive Android fitness & workout tracking app with AI-powered personalization.

**Tech Stack:**
- Language: Kotlin 2.0.21
- UI: Jetpack Compose + Material Design 3
- Architecture: MVVM + Repository Pattern + Hilt DI
- Database: Room (v51, 21 entities, 23 DAOs)
- Auth/Backend: Firebase Auth (email/password + Google Sign-In via Credential Manager) + Firestore
- Health: Health Connect API
- Charts: Vico (Material 3)
- Drag-and-drop: `sh.calvin.reorderable:reorderable-compose`
- AI: Gemini API via OkHttp REST (`okhttp3:okhttp:4.12.0`, model `gemini-2.5-flash`) + ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`)
- Observability: Timber logging facade (DebugTree in debug, CrashlyticsTree in release) + Firebase Crashlytics + Firebase Analytics custom events
- Build: Gradle Kotlin DSL, KSP, min SDK 26, target SDK 35

**Package:** `com.powerme.app`

**Nav tab order:** Workouts / History / Exercises / Tools / Trends

**Color System:** Pro Tracker v6.0. See `THEME_SPEC.md` for all tokens, semantic colors, and usage rules.

**Database:** Room v51 (21 entities, 23 DAOs). See `DB_UPGRADE.md` for schema history v6→v51.
Key patterns: UUID String PKs, Firestore sync columns, soft deletes via `isArchived`, functional training blocks (5 types), stress vectors, synonym learning, exercise tags.

**SurgicalValidator:** All numeric input validation (`util/SurgicalValidator.kt`). No inline try-catch in ViewModels or Composables.

**Health Connect:** 7 READ permissions (weight, body fat, height, sleep, HRV, RHR, steps). See `HEALTH_CONNECT_SPEC.md`.

**Unit Tests:** 78 files, ~1079 tests (all passing). See `src/test/`.

**AI:** Hybrid key resolution (user key → BuildConfig fallback). Parser router: cloud-only (Gemini via OkHttp REST). See `AI_SPEC.md`.

**Shipped features** (see `COMPLETED_FEATURES.md` for implementation details):
- WorkoutStyle enum (P8 Tier 0) — PURE_GYM / PURE_FUNCTIONAL / HYBRID
- Exercise.tags functional categorisation (P8 Tier 0) — ~387 exercises, 7 tag types, 7 families
- Countdown MM:SS input (P0) — digit field composable replacing roulette picker
- Favourites quick-filter (P5) — heart toggle in exercise library search bar
- Movement transfer (P5) — cross-exercise strength estimation via family ratios
- AI key + parser abstraction (P9) — WorkoutParserRouter, cloud-only for now
- FunctionalBlockWizard (P8 Tier 3) — `FunctionalBlockWizard.kt` (2-step sheet: type → params), `DraftBlock` model, `BlockType` enum + `autoBlockName()`, block-sectioned `TemplateBuilderScreen` with `BlockHeader` + `FunctionalExerciseRow` (reps/time toggle). TemplateBuilderViewModel block CRUD + reps/holdSeconds management. RoutineRepository block-aware duplicate/express. Exercise picker pre-marks already-in-block exercises. PURE_FUNCTIONAL style opens wizard; PURE_GYM goes direct to exercise picker; HYBRID opens AddBlockOrExerciseSheet.
- Hybrid Add-Block-or-Exercise Sheet (P8 Tier 3) — `AddBlockOrExerciseSheet.kt` (24dp-radius bottom sheet, two full-width action items). `TemplateBuilderScreen` Add button dispatches via `when(workoutStyle)`: PURE_GYM → exercise picker, PURE_FUNCTIONAL → FunctionalBlockWizard, HYBRID → AddBlockOrExerciseSheet. All three per-style paths fully wired.
- Active workout block headers (P8 Tier 4) — `startWorkoutFromRoutine` materialises `WorkoutBlock` rows from `RoutineBlock` template at workout start. `ActiveWorkoutState` gains `blocks: List<WorkoutBlock>` + lazy `exercisesByBlockId`. Hybrid workouts (≥2 blocks) show `BlockHeader` composable before each block's exercise group; single-block workouts unchanged. Functional block headers show disabled "▶ START BLOCK" CTA. `ExerciseWithSets` gains `blockId`.
- FunctionalBlockRunner + AMRAP/RFT/EMOM/TABATA overlays (P8 Tier 4 XL) — `@Singleton FunctionalBlockRunner` (`ui/workout/FunctionalBlockRunner.kt`) owns the shared `TimerEngine` while a functional block is active and acts as the soft mutex (`isActive`) preventing rest-timer collisions (Invariant #4). `WorkoutBlockDao` extended with `saveResult(...perExerciseRpeJson, roundTapLogJson...)`, `getById`, `setRoundTapLog`, transactional `appendRoundTap`. `TimerEngine.resumeAt(spec, elapsedSeconds, setupSeconds)` for resume-from-kill via wall-clock `runStartMs`. Four full-screen overlays in `ui/workout/runner/`: `AmrapOverlay` (count-down + `BlindTapZone`), `RftOverlay` (count-up + ROUND ✓ split logger), `EmomOverlay` (320dp progress ring + COMPLETED/SKIP), `TabataOverlay` (WORK/REST tinted ring). `BlockFinishSheet` captures rounds/extra-reps/finish-time + Overall|Per-exercise RPE (mutually exclusive — Invariant #9). `WorkoutTimerService` observes runner state with throttled foreground notification updates. `HealthConnectManager.writeWorkoutSession` extended with `blocks: List<WorkoutBlock>` → `ExerciseSegment` per block.
- AI key connected indicator (P1) — Green `AssistChip` ("Connected" + checkmark, `TimerGreen` tint) appears in the AI Settings card when `ApiKeyValidationState.Valid`.
- Workout Style info sheet (P1) — `ℹ` `IconButton` in the Workout Style card header opens a `ModalBottomSheet` (`WorkoutStyleInfoSheet`) explaining Pure Strength / Hybrid / Pure Functional. State in `SettingsUiState.showWorkoutStyleInfoSheet`. Segmented button label renamed "Pure Gym" → "Pure Strength".
- Move Privacy to Profile (P1) — Privacy / Delete Account card removed from Settings. "Danger Zone" section (outlined error-color button + `AlertDialog`) added at bottom of `ProfileScreen`. Deletion state + logic moved to `ProfileViewModel` (injected `PowerMeDatabase` + `SecurePreferencesStore`).
- Settings Data & Backup merge (P1) — Former "Data & Backup" (export+import) and "Cloud Sync" (backup+restore) cards merged into single `DataAndBackupCard`. Signed-in users see all four rows; signed-out users see Export + Import only.
- Keep Screen On — 3-mode selector (P1) — `KeepScreenOnMode` enum (ALWAYS/DURING_WORKOUT/OFF) replaces the old boolean toggle. `SingleChoiceSegmentedButtonRow` ("Always" / "During workout" / "Off") in Display & Workout settings card. ALWAYS: `window.addFlags(FLAG_KEEP_SCREEN_ON)` in `MainActivity`. DURING_WORKOUT: `DisposableEffect` in `ActiveWorkoutScreen` sets `view.keepScreenOn` only while workout is active. DataStore migration from old boolean key. 6 new ViewModel tests.
- RPE auto-pop mode selector (P1) — `RpeMode` enum (PURE_GYM/PURE_FUNCTIONAL/HYBRID/OFF) replaces old boolean. `RadioButton` group now lives in the **Workout Style** card (co-located with workout style selector). `WorkoutViewModel` checks `rpeMode` against `currentWorkoutStyle` before emitting `rpeAutoPopTarget`. DataStore migration from old boolean. Firestore push/pull updated. 7 new ViewModel tests.
- Profile — Log Out into Danger Zone (P1) — Standalone Log Out button removed from above the Danger Zone divider; moved inside the Danger Zone block as the first action, above Delete Account, with 8dp spacer between them.
- Settings page card reorder (P1) — LazyColumn order: Appearance → Units → Workout Style → Display → Rest Timer → AI → Health Connect → Data & Backup. "Display & Workout" renamed to "Display" (Keep screen on only). Timer sound moved into Rest Timer card.
- Functional block card layout (P8) — In template builder, each functional block (AMRAP/RFT/EMOM/TABATA) is wrapped in a single `Card` (`surfaceVariant` bg, `medium` shape) containing `BlockHeader` + all exercise rows; no individual card per exercise. STRENGTH blocks / unblocked exercises keep per-exercise cards. `BlockHeader` gains `standalone: Boolean = true`; when `false` uses transparent bg + 8dp top padding. `FunctionalExerciseRow` renders as a plain `Row` (no Card wrapper).
- Functional block active workout card UI (P8) — In the active workout screen, exercises inside a functional block (AMRAP/RFT/EMOM/TABATA) render inside a single grouped `Card` with a block type badge + parameter summary at the top and exercise rows below. Functional exercise rows show only name, weight, and reps/time — no sets stepper, no PRE chip, no RPE field, no checkmark button. STRENGTH blocks and unblocked exercises retain the existing per-exercise card UI. Column header reads "TIME" for all-TIMED blocks and "REPS" otherwise.

**In-progress:**
- _(none)_

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
| Dev done, ready for QA | Mark `[x] Fixed` / set `completed` manually, update `BUG_TRACKER.md` to Completed |
| User QA passed | `/wrap_task <slug>` — build + test + commit + push + Wrapped; auto-updates Epic rollup |
| Want a dashboard | `/tasks_status` — shows Epics section at top with rollup counts |
| Planning parallel sessions | `/plan_sessions <N>` |

Non-skill documentation updates that are still manual:
- DB schema changed → increment Room version, add migration, update `DB_UPGRADE.md`
- New screen or ViewModel → register in `NAVIGATION_SPEC.md`, update `CLAUDE.md` Current State
- Spec found outdated → fix it immediately

### Archive Protocol (keep active trackers lean)

After `/wrap_task` completes:
- **Bug:** move the `✅ Wrapped` row from `bugs_to_fix/BUG_TRACKER.md` → `bugs_to_fix/ARCHIVE.md`
- **Feature:** move the `wrapped` row from `ROADMAP.md` phase table → `ROADMAP_ARCHIVE.md` (under the correct phase section)
- To search archived items: `grep -i "<keyword>" bugs_to_fix/ARCHIVE.md` or `grep -i "<keyword>" ROADMAP_ARCHIVE.md`
- Never delete archived rows — they are the permanent history.

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
