# PowerME — Feature Roadmap

> **How to use this file:**
> This is the single source of truth for feature prioritisation and sequencing.
> - Update `Status` here when you start or finish a feature.
> - Add new features here before creating a spec file.
> - Check `Depends on` before starting anything — blocked items cannot ship first.
> - Run `/simplify` after every completed phase.

---

## Status Legend

| Symbol | Meaning |
|---|---|
| `not-started` | Ready to build (all dependencies met) |
| `in-progress` | Currently being implemented |
| `blocked` | Waiting on another feature listed in "Depends on" |
| `completed` | Dev done, tests pass, ready for QA on device |
| `wrapped` | User QA'd and ran `/wrap_task` (simplify + build + test + commit + push) |
| `rework` | QA rejected — needs fixes before re-testing |
| `done` | Legacy status — same as `wrapped` (used by older shipped items) |

## Effort Legend

| Size | Rough scope |
|---|---|
| XS | < 1 hour — single line / trivial change |
| S | 1–2 days |
| M | 3–5 days |
| L | 1–2 weeks |
| XL | 2+ weeks |

---

## Epics

Multi-task initiatives that own a root-level spec and a set of child Feature tasks. Status rolls up from children automatically. File with `/file_task epic: <description>`; file children with `/file_task epic:<parent-slug> <description>`.

| Epic | Spec | Phase | Status | Rollup | Children |
|---|---|---|---|---|---|
| Functional Training | `FUNCTIONAL_TRAINING_SPEC.md` | P8 | `in-progress` | 1/12 wrapped · 2 in-progress · 9 not-started | 12 |
| AI Workout Generation | `AI_SPEC.md` | P7 / P9 | `in-progress` | 0/3 wrapped | 3 active + §8 queue |

---

## Phase P0 — Quick Wins

Self-contained, high-impact, no new infrastructure. Ship these first.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Set row spacing (2dp → 8dp) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §2` | XS | `done` | — |
| 3-second countdown beep on all timers | `CLOCKS_SPEC.md §5` | XS | `wrapped` | — |
| Clocks warn auto half-time default | `future_devs/CLOCKS_WARN_AUTO_HALFTIME_SPEC.md` | S | `wrapped` | — |
| Timer sound options (bell, chime, click, silent) | `future_devs/TIMER_SOUND_OPTIONS_SPEC.md` | S | `wrapped` | — |
| Logout button on Profile page | `future_devs/PROFILE_LOGOUT_BUTTON_SPEC.md` | XS | `wrapped` | Profile/Settings split ✅ |
| Quick Start Workout (blank workout, no routine) | `future_devs/QUICK_START_WORKOUT_SPEC.md` | XS | `wrapped` | — |
| Observability layer — Crashlytics + Analytics + Timber (beta) | `future_devs/OBSERVABILITY_BETA_SPEC.md` | M | `wrapped` | — |
| Workouts page — Quick Start 3-way chooser (exercises / picture / text) | `future_devs/WORKOUTS_QUICK_START_CHOOSER_SPEC.md` | S | `wrapped` | Quick Start Workout ✅, AI Workout Generation ✅ |
| Gymvisual chest+barbell catalogue comparison (research report) | `future_devs/GYMVISUAL_CHEST_BARBELL_DIFF_SPEC.md` | XS | `wrapped` | — |
| Gemini API key validation — inline status after Save (valid / quota / invalid) | `future_devs/API_KEY_VALIDATION_SPEC.md` | S | `completed` | — |
| Clocks Countdown — MM:SS fill-in input (replaces roulette wheel) | `future_devs/CLOCKS_COUNTDOWN_MMS_INPUT_SPEC.md` | S | `in-progress` | — |

---

## Phase P1 — Active Workout UX

Core workout loop improvements. Independent of all other phases.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Golden RPE indicator (8–9 highlight) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §1` | S | `done` | — |
| Timed exercise countdown timer | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §3` | M | `done` | — |
| Timed exercise — half-time double beep | `future_devs/TIMED_EXERCISE_HALFTIME_BEEP_SPEC.md` | XS | `wrapped` | Timed exercise countdown timer ✅ |
| Warmup sets — auto-collapse after completion | `future_devs/WARMUP_SETS_AUTO_COLLAPSE_SPEC.md` | S | `wrapped` | — |
| Numeric keyboard ±1 increment/decrement buttons | `future_devs/NUMERIC_KEYBOARD_PLUS_MINUS_SPEC.md` | S | `wrapped` | — |
| Watch & phone workout notifications | `future_devs/WATCH_WORKOUT_NOTIFICATIONS_SPEC.md` | L | `completed` | — |
| Smart "Add Warmups" — equipment-aware warmup generator | `future_devs/SMART_ADD_WARMUPS_SPEC.md` | M | `wrapped` | Warmup sets — auto-collapse ✅ |

---

## Phase P2 — History & Profile

Post-workout experience + user identity. Can be built in any order within the phase.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| History Summary Redesign (Step A — no deep-link) | `HISTORY_SUMMARY_REDESIGN_SPEC.md` | L | `done` | — |
| Profile / Settings split (separate pages + nav icons) | `PROFILE_SETTINGS_REDESIGN_SPEC.md §1` | M | `done` | — |
| Fitness level card (Novice/Trained/Experienced/Athlete) | `PROFILE_SETTINGS_REDESIGN_SPEC.md §3` | S | `done` | Profile/Settings split |
| RPE auto-pop setting | `PROFILE_SETTINGS_REDESIGN_SPEC.md §4` | S | `wrapped` | — |
| History card set details (weights + RPE) | `future_devs/HISTORY_CARD_SET_DETAILS_SPEC.md` | S | `wrapped` | History Summary Step A ✅ |
| History cards default expanded | `future_devs/HISTORY_CARDS_DEFAULT_EXPANDED_SPEC.md` | XS | `wrapped` | History card set details ✅ |
| Summary RPE inline format (weight×reps@RPE) | `future_devs/SUMMARY_RPE_INLINE_FORMAT_SPEC.md` | S | `wrapped` | History card set details ✅ |
| Workout summary — set type labels (WU / working # / DROP / FAIL) | `future_devs/WORKOUT_SUMMARY_SET_TYPE_LABELS_SPEC.md` | S | `wrapped` | Summary RPE inline format ✅ |
| HC UX restructure — Settings Connected badge + Profile metrics card | `future_devs/HC_UX_RESTRUCTURE_SPEC.md` | S | `wrapped` | Profile/Settings split ✅ |

---

## Phase P3 — Health Foundation

AI trainer groundwork. Health history drives the red/yellow exercise lists immediately; AI trainer uses it later.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Health History ledger + auto red/yellow list mapping | `PROFILE_SETTINGS_REDESIGN_SPEC.md §2` | L | `done` | Profile/Settings split (P2) |

---

## Phase P4 — Trends Charts + HC Extended Reads

Data insights layer. Trends Steps 2–5 are independent of each other — can be built in any order.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Trends Step 2 — VolumeTrendCard | `TRENDS_CHARTS_SPEC.md §Step 2` | M | `done` | — |
| Trends Step 3 — E1RMProgressionCard | `TRENDS_CHARTS_SPEC.md §Step 3` | M | `done` | — |
| Trends Step 4 — MuscleGroupVolumeCard | `TRENDS_CHARTS_SPEC.md §Step 4` | M | `wrapped` | — |
| Trends Step 5 — EffectiveSetsCard | `TRENDS_CHARTS_SPEC.md §Step 5` | M | `wrapped` | — |
| HC Extended Reads (HR, Calories, VO₂ Max, Distance, SpO₂) | `HEALTH_CONNECT_EXTENDED_READS_SPEC.md` | M | `blocked` | External HC issue (out of PowerME scope) |
| HC Phase B — Write workouts to Health Connect | `HEALTH_CONNECT_SPEC.md §8` | S | `wrapped` | — |
| HC Backfill — Push last 90 days to Health Connect on permission grant | `future_devs/HC_BACKFILL_SPEC.md` | S | `wrapped` | HC Phase B ✅ |
| History → Trends deep-link (Step B) | `HISTORY_SUMMARY_REDESIGN_SPEC.md §Trends Integration` | S | `wrapped` | Trends Step 3 (E1RM) ✅ + History Summary Step A ✅ |
| E1RM Progression — line only (no area fill) | `future_devs/TRENDS_E1RM_LINE_ONLY_SPEC.md` | XS | `wrapped` | Trends Step 3 (E1RM) ✅ |
| Trends — hide cards with insufficient data | `future_devs/TRENDS_EMPTY_CARDS_HIDDEN_SPEC.md` | S | `wrapped` | — |
| Trends charts — Y axis values only, unit at top | `future_devs/TRENDS_CHART_Y_AXIS_UNIT_AT_TOP_SPEC.md` | XS | `wrapped` | — |
| Trends charts — zoomed out by default, pinch to zoom | `future_devs/TRENDS_CHART_ZOOM_DEFAULT_OUT_SPEC.md` | S | `wrapped` | — |

---

## Phase P5 — Trends Advanced + CSV Import + Exercise Library

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Trends Step 6 — BodyCompositionCard | `TRENDS_CHARTS_SPEC.md §Step 6` | L | `completed` | — |
| Trends Step 7 — StepsTrendCard | `TRENDS_CHARTS_SPEC.md §Step 7` | S | `blocked` | HC Extended Reads (calories) |
| Trends Step 8 — ChronotypeCard | `TRENDS_CHARTS_SPEC.md §Step 8` | L | `completed` | — |
| CSV Import (Strong, Hevy, FitBod, generic) | `CSV_IMPORT_SPEC.md` | L | `completed` | — |
| Exercise animations in ExerciseDetailSheet | `future_devs/EXERCISE_ANIMATIONS_SPEC.md` | S | `wrapped` | — |
| Exercise joint indicators in ExerciseDetailSheet | `future_devs/EXERCISE_JOINTS_SPEC.md` | M | `superseded` | Superseded by Exercise Detail Sheet Revision |
| Exercise Detail Sheet — Full Revision | `future_devs/EXERCISE_DETAIL_SHEET_REVISION_SPEC.md` | L | `wrapped` | — |
| Exercise Detail Screen — Tab-Based Redesign (v2) | `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md` | L | `wrapped` | Exercise Detail Sheet Revision v1 ✅ |
| Exercise "How to Perform" descriptions | `future_devs/EXERCISE_HOW_TO_PERFORM_SPEC.md` | L | `wrapped` | Exercise Detail Tabs v2 ✅ |
| Alternative exercise — movement-specific weight transfer | `future_devs/ALTERNATIVE_WEIGHT_TRANSFER_SPEC.md` | M | `not-started` | Exercise Detail Tabs v2 ✅ |
| DB synonym foundation — UserExerciseSynonym entity, DAO, repository | `future_devs/DB_SYNONYM_FOUNDATION_SPEC.md` | S | `wrapped` | — |

---

## Phase P6 — Body Heatmap

Flagship feature. Requires all groundwork below before any UI work begins.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Stress vectors — manual seed (top 30 exercises) | `TRENDS_SPEC.md §10` | M | `completed` | — |
| Stress vectors — Gemini expansion (remaining 120+) | `TRENDS_SPEC.md §10` | M | `completed` | Manual seed ✅ |
| Stress accumulation algorithm + DB table | `TRENDS_SPEC.md §10` | M | `completed` | — |
| SVG/Canvas body outline rendering | `TRENDS_SPEC.md §10` | L | `wrapped` | — |
| Full heatmap card (wired end-to-end) | `TRENDS_SPEC.md §10` | XL | `wrapped` | All above + Trends P4 complete |

---

## Phase P7 — AI Workout Generation

Cloud AI that turns free text or a photo into a ready-to-start workout. **Architecture resolved: Gemini Flash API (cloud) + ML Kit Text Recognition (on-device OCR).**

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| AI Workout Generation (text + photo → workout) | `AI_SPEC.md` | XL | `wrapped` | Quick Start Workout ✅ |

*Follow-ups: see `AI_SPEC.md §8 Enhancement Roadmap`. Each enhancement becomes its own roadmap row when promoted to real build work.*

---

## Phase P8 — Functional Training (Hybrid Mode)

AMRAP / RFT / EMOM alongside strength work. Tiered delivery — see `FUNCTIONAL_TRAINING_SPEC.md` and the task-tree in the plan file for full dependency structure and parallelization guidance. **Read `FUNCTIONAL_TRAINING_SPEC.md` before starting any task in this phase.**

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| WorkoutStyle enum + Settings card | `future_devs/FUNC_STYLE_PREFERENCE_SPEC.md` | S | `wrapped` | — |
| Exercise.tags column + seed ~40 functional movements + filter | `future_devs/FUNC_EXERCISE_TAGS_SEED_SPEC.md` | M | `completed` | — |
| Extract TimerEngine class + real JetBrains Mono font | `future_devs/FUNC_TIMER_ENGINE_EXTRACT_SPEC.md` | M | `wrapped` | — |
| RoutineBlock + WorkoutBlock entities + MIGRATION_49_50 backfill | `future_devs/FUNC_BLOCK_ENTITIES_MIGRATION_SPEC.md` | L | `not-started` | WorkoutStyle pref ✅, Exercise tags ✅, TimerEngine ✅ |
| Embed block arrays in Firestore push/pull | `future_devs/FUNC_FIRESTORE_SYNC_BLOCKS_SPEC.md` | M | `not-started` | Block entities migration ✅ |
| FunctionalBlockWizard + Pure Functional template builder | `future_devs/FUNC_TEMPLATE_WIZARD_SPEC.md` | L | `not-started` | Block entities migration ✅ |
| Hybrid AddBlockOrExerciseSheet + Pure Gym preserved | `future_devs/FUNC_TEMPLATE_HYBRID_SHEET_SPEC.md` | S | `not-started` | FunctionalBlockWizard ✅ |
| Block headers in active workout; STRENGTH materialisation on start | `future_devs/FUNC_ACTIVE_STRENGTH_BLOCKS_SPEC.md` | M | `not-started` | Block entities migration ✅ |
| AMRAP/RFT/EMOM overlays + FunctionalBlockRunner + foreground-service lifecycle | `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md` | XL | `not-started` | TimerEngine ✅, Firestore sync ✅, Strength block headers ✅ |
| Block-aware History rows + Trends + WorkoutSummaryScreen | `future_devs/FUNC_HISTORY_TRENDS_POLISH_SPEC.md` | M | `not-started` | Functional runner ✅ (in prod ≥1 release) |
| Exercise gap analysis — CrossFit / Hyrox / Calisthenics (research report) | `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_SPEC.md` | XS | `completed` | — |
| Expanded exercise seed — CrossFit / Hyrox / Calisthenics DB population | `future_devs/FUNC_EXERCISE_EXPANDED_SEED_SPEC.md` | M | `not-started` | gap analysis ✅, Exercise tags seed ✅ |
| Cardio exercise seed — treadmill, cycling, swimming, machines, LISS/HIIT | `future_devs/FUNC_CARDIO_EXERCISE_SEED_SPEC.md` | S | `not-started` | func_exercise_tags_seed ✅, func_exercise_expanded_seed ✅ |

---

## Phase P9 — On-Device AI (AICore / Gemma)

Parser abstraction layer + on-device inference backend. `AiWorkoutViewModel` remains unchanged throughout. **Read `AI_SPEC.md §12` before starting any task in this phase.**

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| AI parser interface layer — `WorkoutTextParser`, `WorkoutPromptUtils`, `WorkoutParserRouter`, `AiModule` | `future_devs/AI_PARSER_INTERFACE_LAYER_SPEC.md` | S | `wrapped` | AI Workout Generation (P7) ✅ |
| AI ViewModel interface wiring — wire `AiWorkoutViewModel` to `WorkoutTextParser`; update test mock type | `future_devs/AI_VIEWMODEL_INTERFACE_WIRING_SPEC.md` | XS | `wrapped` | AI parser interface layer ✅ |
| AICore on-device integration — `AiCoreAvailability`, `AiCoreDownloadManager`, `OnDeviceWorkoutParser`, router wiring, download banner, Settings status row | `future_devs/AICORE_ONDEVICE_INTEGRATION_SPEC.md` | M | `completed` | AI ViewModel interface wiring ✅ |
| Synonym learning system — migration v49, ExerciseMatcher synonym tier, save prompt UI, analytics | `future_devs/SYNONYM_LEARNING_SYSTEM_SPEC.md` | M | `completed` | DB synonym foundation ✅, AI parser interface layer ✅ |
| AICore two-tier model cascade — E4B (Gemma 4B) preferred, E2B (Gemma 2B) fallback; variant-aware availability, router, download, Settings row | `future_devs/AICORE_TWO_TIER_MODEL_CASCADE_SPEC.md` | S | `abandoned` | AICore on-device integration |

---

## Dependency Map

```
P0 (row spacing)         ──────────────────────────────► ship anytime
P1 (active workout)      ──────────────────────────────► ship anytime

P2 (history summary A)   ──────────────────────────────► ship anytime
P2 (profile/settings)    ──────────────────────────────► ship anytime
P2 (fitness level)       ── requires ──────────────────► profile/settings split
P2 (RPE auto-pop)        ──────────────────────────────► ship anytime

P3 (health history)      ── requires ──────────────────► profile/settings split

P4 (trends steps 2-5)    ──────────────────────────────► ship anytime (independent)
P4 (HC extended reads)   ──────────────────────────────► ship anytime
P4 (HC write workouts)   ──────────────────────────────► ship anytime
P4 (HC backfill)         ── requires ──────────────────► HC Phase B ✅
P4 (deep-link step B)    ── requires ──────────────────► trends step 3 + history summary A

P5 (steps trend card)    ── requires ──────────────────► HC extended reads (calories)
P5 (body composition)    ──────────────────────────────► ship anytime
P5 (chronotype)          ──────────────────────────────► ship anytime
P5 (CSV import)          ──────────────────────────────► ship anytime
P5 (exercise animations) ──────────────────────────────► ship anytime (assets already generated)
P5 (exercise joints)     ──────────────────────────────► ship anytime

P6 (gemini expansion)    ── requires ──────────────────► manual seed
P6 (heatmap card)        ── requires ──────────────────► all P6 sub-tasks + P4 complete

P0 (quick start workout) ──────────────────────────────► ship anytime
P7 (AI workout gen)      ── requires ──────────────────► Quick Start Workout

P8 Tier 0 (style pref, tags, timer engine) ─────────► ship anytime (parallelizable)
P8 Tier 1 (block migration) ── requires ─────────────► ALL Tier 0 wrapped
P8 Tier 2 (firestore sync)  ── requires ─────────────► Tier 1 wrapped
P8 Tier 3 (template wizard, hybrid sheet) ── requires ► Tier 1 wrapped (parallelizable within tier)
P8 Tier 4 (strength headers) ── requires ────────────► Tier 1 wrapped
P8 Tier 4 (functional runner) ── requires ───────────► Tier 0 timer extract + Tier 2 + Tier 4 strength headers
P8 Tier 5 (history/trends)  ── requires ─────────────► Tier 4 runner in prod ≥1 release
```

---

## Completed Bugs

All tracked bugs are resolved. Listed for reference.

| Bug | File | Fix summary |
|---|---|---|
| Start workout after edit navigates to wrong tab | `BUG_start_workout_after_edit.md` | Clear `editModeSaved` synchronously before coroutine launch |
| Duplicate exercises in master_exercises.json | `BUG_duplicate_exercises.md` | Removed 4 lowercase-"up" dupes, bumped seeder to v1.7 |
| History edit mode — values unreadable | `BUG_history_edit_unreadable_values.md` | Fixed `focusedTextColor`/`unfocusedTextColor` to `onPrimaryContainer` |
| History weight shown with 1 decimal place | `BUG_history_weight_decimal_places.md` | Changed `"%.1f"` → `"%.2f"` in `UnitConverter.formatNumber()` |
| Superset color collision | `BUG_superset_color_collision.md` | Replaced hash-based color with insertion-order map |
| Keyboard pops on set type change | `BUG_keyboard_pops_on_set_type_change.md` | Deferred `clearFocus()` via `LaunchedEffect` after menu dismiss |
| YouTube button still rendered | `BUG_youtube_links_still_rendered.md` | Removed YouTube `TextButton` block from `ExercisesScreen.kt` |

---

*Last updated: April 2026*

---

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
| `future_devs/CLOCKS_COUNTDOWN_MMS_INPUT_SPEC.md` | P0 — Clocks Countdown: replace roulette wheel with MM:SS fill-in input boxes |
| `HEALTH_CONNECT_SPEC.md` | HC permissions, sync logic, Phase B writes, backfill |
| `TRENDS_SPEC.md` | Trends tab — all chart cards, ReadinessEngine, TrendsDao, body heatmap |
| `PROFILE_SETUP_SPEC.md` | Onboarding, two-step flow, HC offer |
| `SETTINGS_SPEC.md` | Settings screen, all cards, SettingsViewModel |
| `AI_SPEC.md` | AI workout generation — text/photo → routine, Gemini + OCR, matching, enhancement roadmap. Single source of truth for AI. |
| `AI_SPEC.md §12` | On-device AI — Android AICore (Gemma 4 / Gemini Nano), graceful degradation, 0-byte APK footprint, privacy model |
| `AI_BACKLOG.md` | Long-horizon AI brainstorm (46 items with scope tags). Companion to `AI_SPEC.md`. |
| `DB_UPGRADE.md` | Migration history, schema changes |
| `DB_ARCHITECTURE.md` | Entity relationships, template-to-instance, UUID migration |
| `FUNCTIONAL_TRAINING_SPEC.md` | Functional Training — AMRAP / RFT / EMOM Hybrid mode. Block concept, WorkoutStyle preference, timer engine, live UX (blind-tap zone), exercise tags, foreground-service lifecycle |
| `future_devs/OBSERVABILITY_BETA_SPEC.md` | Observability — Crashlytics crash reporting + Firebase Analytics action trail + Timber logging facade |
| `future_devs/WORKOUTS_QUICK_START_CHOOSER_SPEC.md` | Workouts page restructure — Quick Start becomes a 3-way chooser (exercises / picture / text); removes standalone AI button |
| `future_devs/HC_UX_RESTRUCTURE_SPEC.md` | HC UX — Settings shows Connected badge + "View in Profile" button; HC metrics card in Profile screen |
| `future_devs/DB_SYNONYM_FOUNDATION_SPEC.md` | DB synonym layer — UserExerciseSynonym entity + DAO + repository for exercise name learning |
| `future_devs/FUNC_STYLE_PREFERENCE_SPEC.md` | P8 Tier 0 — WorkoutStyle enum (PURE_GYM / PURE_FUNCTIONAL / HYBRID) + Settings card |
| `future_devs/FUNC_EXERCISE_TAGS_SEED_SPEC.md` | P8 Tier 0 — Exercise.tags JSON column + ~40 new functional movements + Functional filter chip |
| `future_devs/FUNC_TIMER_ENGINE_EXTRACT_SPEC.md` | P8 Tier 0 — Extract TimerEngine class + real JetBrains Mono font + TimerDigitsXL/L/M typography roles |
| `future_devs/FUNC_BLOCK_ENTITIES_MIGRATION_SPEC.md` | P8 Tier 1 — RoutineBlock + WorkoutBlock entities + DAOs + MIGRATION_49_50 backfill |
| `future_devs/FUNC_FIRESTORE_SYNC_BLOCKS_SPEC.md` | P8 Tier 2 — Block arrays embedded in Firestore workout/routine docs; back-compat for legacy docs |
| `future_devs/FUNC_TEMPLATE_WIZARD_SPEC.md` | P8 Tier 3 — FunctionalBlockWizard 3-step sheet + block-sectioned TemplateBuilderScreen |
| `future_devs/FUNC_TEMPLATE_HYBRID_SHEET_SPEC.md` | P8 Tier 3 — Hybrid AddBlockOrExerciseSheet + per-style dispatch |
| `future_devs/FUNC_ACTIVE_STRENGTH_BLOCKS_SPEC.md` | P8 Tier 4 — Block headers in ActiveWorkoutScreen; WorkoutViewModel block materialisation |
| `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md` | P8 Tier 4 — AMRAP/RFT/EMOM overlays + BlindTapZone + BlockFinishSheet + foreground-service lifecycle |
| `future_devs/FUNC_HISTORY_TRENDS_POLISH_SPEC.md` | P8 Tier 5 — Block-aware History rows + BlockSummaryCard in WorkoutSummaryScreen + Trends |
| `future_devs/AI_PARSER_INTERFACE_LAYER_SPEC.md` | P9 — On-device AI prerequisite — `WorkoutTextParser` interface + `WorkoutParserRouter` cloud-only router + `AiModule` Hilt bindings |
| `future_devs/AI_VIEWMODEL_INTERFACE_WIRING_SPEC.md` | P9 — Wire `AiWorkoutViewModel` to `WorkoutTextParser` interface; update `AiWorkoutViewModelTest` mock type |
| `future_devs/AICORE_ONDEVICE_INTEGRATION_SPEC.md` | P9 — AICore on-device inference: availability check, model download, `OnDeviceWorkoutParser`, router wiring, download UX |
| `future_devs/SYNONYM_LEARNING_SYSTEM_SPEC.md` | P9 — Synonym learning — ExerciseMatcher user-synonym tier, migration v49, save prompt, analytics |
| `future_devs/AICORE_TWO_TIER_MODEL_CASCADE_SPEC.md` | P9 — AICore E4B/E2B two-tier model cascade; variant-aware availability, router, Settings status row |
| `future_devs/GYMVISUAL_CHEST_BARBELL_DIFF_SPEC.md` | P0 — Research: gymvisual.com chest+barbell catalogue vs our exercise DB; no GIF download, no DB changes |
| `future_devs/API_KEY_VALIDATION_SPEC.md` | P0 — Gemini API key inline validation after Save in Settings AI card |
| `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_SPEC.md` | P8 — Research: CrossFit / Hyrox / Calisthenics exercise gap analysis; no code changes |
| `future_devs/FUNC_EXERCISE_EXPANDED_SEED_SPEC.md` | P8 — Seed expanded functional exercise list (CrossFit WODs, Hyrox 8 stations, calisthenics progressions) |
| `future_devs/FUNC_CARDIO_EXERCISE_SEED_SPEC.md` | P8 — Cardio exercise seed — treadmill, cycling, swimming, machines, LISS/HIIT protocols |
