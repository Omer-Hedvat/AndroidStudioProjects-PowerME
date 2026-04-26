# PowerME — Feature Roadmap

> **How to use this file:**
> Active tasks only — wrapped/done/superseded/abandoned features are in `ROADMAP_ARCHIVE.md`.
> - Update `Status` here when you start or finish a feature.
> - Add new features here before creating a spec file.
> - Check `Depends on` before starting anything — blocked items cannot ship first.
> - After `/wrap_task`: move the row to `ROADMAP_ARCHIVE.md`.

---

## Status Legend

| Symbol | Meaning |
|---|---|
| `not-started` | Ready to build (all dependencies met) |
| `in-progress` | Currently being implemented |
| `blocked` | Waiting on another feature listed in "Depends on" |
| `completed` | Dev done, tests pass, ready for QA on device |
| `completed-blocked` | Dev done, but QA requires an external dependency (API key, specific device hardware, HC sync) |
| `wrapped` | User QA'd and ran `/wrap_task` → move row to `ROADMAP_ARCHIVE.md` |
| `rework` | QA rejected — needs fixes before re-testing |

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

| Epic | Spec | Phase | Status | Rollup | Children |
|---|---|---|---|---|---|
| Functional Training | `FUNCTIONAL_TRAINING_SPEC.md` | P8 | `in-progress` | 13/29 wrapped · 7 completed · 4 in-progress · 3 not-started | 29 |
| AI Workout Generation | `AI_SPEC.md` | P7 / P9 | `in-progress` | 0/4 wrapped · 1 in-progress | 4 active + §8 queue |
| Gym Profiles | `GYM_PROFILES_SPEC.md` | P10 | `not-started` | — | 0 |

---

## Phase P0 — ✅ All wrapped → `ROADMAP_ARCHIVE.md`

---

## Phase P1 — Active Workout UX

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Watch & phone workout notifications | `future_devs/WATCH_WORKOUT_NOTIFICATIONS_SPEC.md` | L | `completed` | — |

---

## Phase P2 — ✅ All wrapped → `ROADMAP_ARCHIVE.md`

---

## Phase P3 — ✅ All wrapped → `ROADMAP_ARCHIVE.md`

---

## Phase P4 — Trends Charts + HC

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| HC Extended Reads (HR, Calories, VO₂ Max, Distance, SpO₂) | `HEALTH_CONNECT_EXTENDED_READS_SPEC.md` | M | `blocked` | External HC issue (out of PowerME scope) |

---

## Phase P5 — Trends Advanced + CSV Import + Exercise Library

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Trends Step 6 — BodyCompositionCard | `TRENDS_CHARTS_SPEC.md §Step 6` | L | `completed-blocked` | — |
| Trends Step 7 — StepsTrendCard | `TRENDS_CHARTS_SPEC.md §Step 7` | S | `blocked` | HC Extended Reads (calories) |
| Trends Step 8 — ChronotypeCard | `TRENDS_CHARTS_SPEC.md §Step 8` | L | `completed` | — |
| CSV Import (Strong, Hevy, FitBod, generic) | `CSV_IMPORT_SPEC.md` | L | `completed` | — |
| Alternative exercise — movement-specific weight transfer | `future_devs/ALTERNATIVE_WEIGHT_TRANSFER_SPEC.md` | M | `wrapped` | Exercise Detail Tabs v2 ✅ |
| Exercise functional tag — user toggle on/off per exercise | `future_devs/EXERCISE_FUNCTIONAL_TAG_TOGGLE_SPEC.md` | S | `not-started` | — |

---

## Phase P6 — Body Heatmap

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Stress vectors — manual seed (top 30 exercises) | `TRENDS_SPEC.md §10` | M | `completed-blocked` | — |
| Stress vectors — Gemini expansion (remaining 120+) | `TRENDS_SPEC.md §10` | M | `completed-blocked` | Manual seed ✅ |
| Stress accumulation algorithm + DB table | `TRENDS_SPEC.md §10` | M | `completed-blocked` | — |

---

## Phase P7 — AI Workout Generation

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| AI Review — full workout management options | `future_devs/AI_REVIEW_WORKOUT_MANAGEMENT_SPEC.md` | M | `in-progress` | AI workout generation core ✅ |

---

## Phase P8 — Functional Training (Hybrid Mode)

Read `FUNCTIONAL_TRAINING_SPEC.md` before starting any task in this phase.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Embed block arrays in Firestore push/pull | `future_devs/FUNC_FIRESTORE_SYNC_BLOCKS_SPEC.md` | M | `wrapped` | Block entities migration ✅ |
| Hybrid AddBlockOrExerciseSheet + Pure Gym preserved | `future_devs/FUNC_TEMPLATE_HYBRID_SHEET_SPEC.md` | S | `wrapped` | FunctionalBlockWizard ✅ |
| Block headers in active workout; STRENGTH materialisation on start | `future_devs/FUNC_ACTIVE_STRENGTH_BLOCKS_SPEC.md` | M | `wrapped` | Block entities migration ✅ |
| AMRAP/RFT/EMOM/TABATA overlays + FunctionalBlockRunner | `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md` | XL | `rework` | TimerEngine ✅, Firestore sync ✅, Strength block headers ✅ |
| Block-aware History rows + Trends + WorkoutSummaryScreen | `future_devs/FUNC_HISTORY_TRENDS_POLISH_SPEC.md` | M | `not-started` | Functional runner ✅ (in prod ≥1 release) |
| Exercise gap analysis — CrossFit / Hyrox / Calisthenics | `future_devs/FUNC_EXERCISE_GAP_ANALYSIS_SPEC.md` | XS | `completed` | — |
| CrossFit exercise list verification | `future_devs/FUNC_CROSSFIT_VERIFICATION_SPEC.md` | XS | `completed` | gap analysis ✅ |
| Expanded exercise seed — CrossFit / Hyrox / Calisthenics | `future_devs/FUNC_EXERCISE_EXPANDED_SEED_SPEC.md` | M | `completed` | gap analysis ✅, CrossFit verification ✅, Exercise tags seed ✅ |
| Functional block card layout (single card per block in template builder) | `future_devs/FUNC_BLOCK_CARD_LAYOUT_SPEC.md` | S | `completed` | func_template_wizard ✅ |
| Exercise picker UI consistency (standardise all entry points) | `future_devs/EXERCISE_PICKER_UI_CONSISTENCY_SPEC.md` | S | `completed` | func_template_wizard ✅ |
| Exercise picker — ExerciseType pre-filter by entry point | `future_devs/EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md` | S | `completed` | func_template_hybrid_sheet ✅ |
| Functional block active workout card UI (grouped card, no sets/PRE/RPE/V) | `future_devs/FUNC_ACTIVE_BLOCK_CARD_UI_SPEC.md` | M | `completed` | func_active_strength_blocks ✅ |
| Functional template builder polish — block view, weights, reorder, edit, supersets | `future_devs/FUNC_TEMPLATE_BUILDER_POLISH_SPEC.md` | M | `not-started` | func_active_block_card_ui ✅, func_block_card_layout ✅ |
| Functional blocks — adjust time cap from within active workout | `future_devs/FUNC_TIMECAP_ADJUST_IN_WORKOUT_SPEC.md` | S | `not-started` | BUG_func_start_block_noop, BUG_func_timecap_no_alert |
| Functional blocks — inter-round rest timer | `future_devs/FUNC_INTER_ROUND_REST_SPEC.md` | M | `not-started` | BUG_func_start_block_noop |
| Functional overlays — larger exercise name typography | `future_devs/FUNC_OVERLAY_EXERCISE_FONT_SPEC.md` | XS | `completed` | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| EMOM setup — configurable warning threshold | `future_devs/FUNC_EMOM_WARN_CONFIG_SPEC.md` | XS | `in-progress` | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| Functional blocks — setup countdown (3s default) | `future_devs/FUNC_BLOCK_SETUP_TIME_SPEC.md` | XS | `in-progress` | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| Tabata block — full config parity with Clocks tab | `future_devs/FUNC_TABATA_FULL_CONFIG_SPEC.md` | S | `in-progress` | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| Functional block exercises — weight targets & lock during workout | `future_devs/FUNC_BLOCK_WEIGHT_CONFIG_SPEC.md` | M | `in-progress` | func_active_block_card_ui ✅ |

---

## Phase P9 — On-Device AI (AICore / Gemma)

Read `AI_SPEC.md §12` before starting any task in this phase.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| AICore on-device integration | `future_devs/AICORE_ONDEVICE_INTEGRATION_SPEC.md` | M | `completed-blocked` | AI ViewModel interface wiring ✅ |
| Synonym learning system | `future_devs/SYNONYM_LEARNING_SYSTEM_SPEC.md` | M | `completed` | DB synonym foundation ✅, AI parser interface layer ✅ |

---

## Phase P10 — Gym Profiles

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Gym Profiles Epic | `GYM_PROFILES_SPEC.md` | XL | `not-started` | — |

---

## Dependency Map

```
P8 Tier 1 (block migration) ─────────────────────────────► ✅ WRAPPED
P8 Tier 2 (firestore sync)  ── requires ─────────────────► Tier 1 ✅
P8 Tier 3 (template wizard, hybrid sheet) ── requires ───► Tier 1 ✅
P8 Tier 4 (strength headers) ── requires ────────────────► Tier 1 ✅
P8 Tier 4 (functional runner) ── requires ───────────────► Tier 2 + Tier 4 strength headers
P8 Tier 5 (history/trends)  ── requires ─────────────────► Tier 4 runner in prod ≥1 release

P5 (steps trend card)    ── requires ──────────────────► HC extended reads (calories)
P6 (gemini expansion)    ── requires ──────────────────► manual seed
```

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
| `HEALTH_CONNECT_SPEC.md` | HC permissions, sync logic, Phase B writes, backfill |
| `TRENDS_SPEC.md` | Trends tab — all chart cards, ReadinessEngine, TrendsDao, body heatmap |
| `PROFILE_SETUP_SPEC.md` | Onboarding, two-step flow, HC offer |
| `SETTINGS_SPEC.md` | Settings screen, all cards, SettingsViewModel |
| `AI_SPEC.md` | AI workout generation — text/photo → routine, Gemini + OCR, matching, enhancement roadmap |
| `AI_SPEC.md §12` | On-device AI — Android AICore (Gemma 4 / Gemini Nano), graceful degradation |
| `AI_BACKLOG.md` | Long-horizon AI brainstorm (46 items). Companion to `AI_SPEC.md`. |
| `DB_UPGRADE.md` | Migration history, schema changes |
| `DB_ARCHITECTURE.md` | Entity relationships, template-to-instance, UUID migration |
| `FUNCTIONAL_TRAINING_SPEC.md` | Functional Training — AMRAP / RFT / EMOM Hybrid mode, block concept, timer engine |
| `GYM_PROFILES_SPEC.md` | P10 — Epic — Gym Profiles: create/join gyms, share equipment + routines |
| `future_devs/FUNC_FIRESTORE_SYNC_BLOCKS_SPEC.md` | P8 Tier 2 — Block arrays in Firestore workout/routine docs |
| `future_devs/FUNC_TEMPLATE_WIZARD_SPEC.md` | P8 Tier 3 — FunctionalBlockWizard 3-step sheet + block-sectioned TemplateBuilderScreen |
| `future_devs/FUNC_TEMPLATE_HYBRID_SHEET_SPEC.md` | P8 Tier 3 — Hybrid AddBlockOrExerciseSheet + per-style dispatch |
| `future_devs/FUNC_ACTIVE_STRENGTH_BLOCKS_SPEC.md` | P8 Tier 4 — Block headers in ActiveWorkoutScreen; WorkoutViewModel block materialisation |
| `future_devs/FUNC_ACTIVE_FUNCTIONAL_RUNNER_SPEC.md` | P8 Tier 4 — AMRAP/RFT/EMOM overlays + BlindTapZone + BlockFinishSheet |
| `future_devs/FUNC_HISTORY_TRENDS_POLISH_SPEC.md` | P8 Tier 5 — Block-aware History rows + BlockSummaryCard + Trends |
| `future_devs/AICORE_ONDEVICE_INTEGRATION_SPEC.md` | P9 — AICore on-device inference: availability, download, OnDeviceWorkoutParser |
| `future_devs/SYNONYM_LEARNING_SYSTEM_SPEC.md` | P9 — Synonym learning — ExerciseMatcher user-synonym tier, save prompt |
| `future_devs/ALTERNATIVE_WEIGHT_TRANSFER_SPEC.md` | P5 — Movement-specific weight transfer ratios for alternative exercise suggestions |
| `future_devs/EXERCISE_FUNCTIONAL_TAG_TOGGLE_SPEC.md` | P5 — Exercise functional tag: user can toggle ⚡ Functional filter inclusion per exercise |
| `future_devs/WATCH_WORKOUT_NOTIFICATIONS_SPEC.md` | P1 — Watch & phone workout notifications |
| `future_devs/FUNC_BLOCK_CARD_LAYOUT_SPEC.md` | P8 — Functional block card layout — single card per block in template builder |
| `future_devs/EXERCISE_PICKER_UI_CONSISTENCY_SPEC.md` | P8 — Exercise picker UI consistency — standardise all entry points to same UI |
| `future_devs/EXERCISE_PICKER_TYPE_PREFILTER_SPEC.md` | P8 — Exercise picker pre-filter by ExerciseType based on entry point (strength vs functional) |
| `future_devs/FUNC_ACTIVE_BLOCK_CARD_UI_SPEC.md` | P8 — Functional block active workout card: grouped card, no sets/PRE/RPE/V for functional exercises |
| `future_devs/FUNC_TEMPLATE_BUILDER_POLISH_SPEC.md` | P8 — Functional template builder polish: block view, weight targets, reorder, edit block params, disable supersets |
| `future_devs/FUNC_TIMECAP_ADJUST_IN_WORKOUT_SPEC.md` | P8 — Adjust AMRAP/RFT time cap from within the active workout overlay |
| `future_devs/FUNC_INTER_ROUND_REST_SPEC.md` | P8 — Optional inter-round rest timer for RFT and AMRAP functional blocks |
| `future_devs/FUNC_OVERLAY_EXERCISE_FONT_SPEC.md` | P8 — Functional overlays: bump exercise name typography from bodyLarge → titleMedium |
| `future_devs/FUNC_EMOM_WARN_CONFIG_SPEC.md` | P8 — EMOM setup: configurable warning threshold (default 10s) |
| `future_devs/FUNC_BLOCK_SETUP_TIME_SPEC.md` | P8 — Functional blocks: 3s GET READY setup countdown before timer starts |
| `future_devs/FUNC_TABATA_FULL_CONFIG_SPEC.md` | P8 — Tabata block: full config parity with Clocks tab (work/rest/rounds/skip-last-rest) |
| `future_devs/FUNC_BLOCK_WEIGHT_CONFIG_SPEC.md` | P8 — Functional block exercises: weight targets in template builder, locked during workout |
| `future_devs/AI_REVIEW_WORKOUT_MANAGEMENT_SPEC.md` | P7 — AI Review — supersets, reorder, replace, rest times, notes in PREVIEW step |
| `future_devs/KEEP_SCREEN_ON_MODE_SPEC.md` | P1 — Keep screen on: Always / During workout / Off selector |
| `future_devs/RPE_MODE_SELECTOR_SPEC.md` | P1 — RPE auto-pop: Gym only / Functional / All workouts / Off selector |
| `future_devs/SETTINGS_RPE_INTO_WORKOUT_STYLE_SPEC.md` | P1 — Settings: move RPE mode selector from Display & Workout card into Workout Style card |
| `future_devs/AI_KEY_CONNECTED_INDICATOR_SPEC.md` | P1 — AI card: green "Connected" chip when user API key is validated |
| `future_devs/WORKOUT_STYLE_TOOLTIPS_SPEC.md` | P1 — Workout Style: ℹ info button + explanation sheet per style |
| `future_devs/MOVE_PRIVACY_TO_PROFILE_SPEC.md` | P1 — Move Delete Account / Privacy card from Settings to Profile |
| `future_devs/SETTINGS_DATA_BACKUP_MERGE_SPEC.md` | P1 — Merge Data Export + Cloud Sync into single "Data & Backup" card |
| `future_devs/SETTINGS_PAGE_REORDER_SPEC.md` | P1 — Settings page card reorder (workout settings up, HC down) |
| `future_devs/PROFILE_LOGOUT_INTO_DANGER_ZONE_SPEC.md` | P1 — Profile: move Log Out button inside Danger Zone, above Delete Account |
