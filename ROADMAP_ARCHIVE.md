# Roadmap Archive

All features with status `wrapped`, `done`, `superseded`, or `abandoned`.
Moved here from `ROADMAP.md` to keep the active roadmap lean.

> To search: `grep -i "<keyword>" ROADMAP_ARCHIVE.md`

---

## Phase P0 — Quick Wins (all wrapped)

| Feature | Spec | Status |
|---|---|---|
| Set row spacing (2dp → 8dp) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §2` | `done` |
| 3-second countdown beep on all timers | `CLOCKS_SPEC.md §5` | `wrapped` |
| Clocks warn auto half-time default | `future_devs/CLOCKS_WARN_AUTO_HALFTIME_SPEC.md` | `wrapped` |
| Timer sound options (bell, chime, click, silent) | `future_devs/TIMER_SOUND_OPTIONS_SPEC.md` | `wrapped` |
| Logout button on Profile page | `future_devs/PROFILE_LOGOUT_BUTTON_SPEC.md` | `wrapped` |
| Quick Start Workout (blank workout, no routine) | `future_devs/QUICK_START_WORKOUT_SPEC.md` | `wrapped` |
| Observability layer — Crashlytics + Analytics + Timber | `future_devs/OBSERVABILITY_BETA_SPEC.md` | `wrapped` |
| Workouts page — Quick Start 3-way chooser | `future_devs/WORKOUTS_QUICK_START_CHOOSER_SPEC.md` | `wrapped` |
| Gymvisual chest+barbell catalogue comparison | `future_devs/GYMVISUAL_CHEST_BARBELL_DIFF_SPEC.md` | `wrapped` |
| Gemini API key validation | `future_devs/API_KEY_VALIDATION_SPEC.md` | `wrapped` |
| Clocks Countdown — MM:SS fill-in input | `future_devs/CLOCKS_COUNTDOWN_MMS_INPUT_SPEC.md` | `wrapped` |

---

## Phase P1 — Active Workout UX (mostly wrapped)

| Feature | Spec | Status |
|---|---|---|
| Golden RPE indicator (8–9 highlight) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §1` | `done` |
| Timed exercise countdown timer | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §3` | `done` |
| Timed exercise — half-time double beep | `future_devs/TIMED_EXERCISE_HALFTIME_BEEP_SPEC.md` | `wrapped` |
| Warmup sets — auto-collapse after completion | `future_devs/WARMUP_SETS_AUTO_COLLAPSE_SPEC.md` | `wrapped` |
| Numeric keyboard ±1 increment/decrement buttons | `future_devs/NUMERIC_KEYBOARD_PLUS_MINUS_SPEC.md` | `wrapped` |
| Smart "Add Warmups" — equipment-aware warmup generator | `future_devs/SMART_ADD_WARMUPS_SPEC.md` | `wrapped` |
| AI — API key connected indicator (green chip when validated) | `future_devs/AI_KEY_CONNECTED_INDICATOR_SPEC.md` | `wrapped` |
| Workout style — contextual explanation (ℹ info sheet) | `future_devs/WORKOUT_STYLE_TOOLTIPS_SPEC.md` | `wrapped` |
| Move Privacy (Delete Account) card to Profile screen | `future_devs/MOVE_PRIVACY_TO_PROFILE_SPEC.md` | `wrapped` |
| Settings — merge Data Export + Cloud Sync into "Data & Backup" | `future_devs/SETTINGS_DATA_BACKUP_MERGE_SPEC.md` | `wrapped` |
| Keep screen on — 3-mode selector (Always / During workout / Off) | `future_devs/KEEP_SCREEN_ON_MODE_SPEC.md` | `wrapped` |
| RPE auto-pop — workout style selector (Strength only / Functional only / All workouts / Off) | `future_devs/RPE_MODE_SELECTOR_SPEC.md` | `wrapped` |
| Profile — move Log Out into Danger Zone | `future_devs/PROFILE_LOGOUT_INTO_DANGER_ZONE_SPEC.md` | `wrapped` |
| Settings page — card reorder | `future_devs/SETTINGS_PAGE_REORDER_SPEC.md` | `wrapped` |
| Settings — move RPE mode selector into Workout Style card | `future_devs/SETTINGS_RPE_INTO_WORKOUT_STYLE_SPEC.md` | `wrapped` |
| Settings — move Timer sound to Rest Timer card + rename Display card | `future_devs/SETTINGS_TIMER_SOUND_TO_REST_TIMER_SPEC.md` | `wrapped` |

---

## Phase P2 — History & Profile (all wrapped)

| Feature | Spec | Status |
|---|---|---|
| History Summary Redesign (Step A) | `HISTORY_SUMMARY_REDESIGN_SPEC.md` | `done` |
| Profile / Settings split | `PROFILE_SETTINGS_REDESIGN_SPEC.md §1` | `done` |
| Fitness level card | `PROFILE_SETTINGS_REDESIGN_SPEC.md §3` | `done` |
| RPE auto-pop setting | `PROFILE_SETTINGS_REDESIGN_SPEC.md §4` | `wrapped` |
| History card set details (weights + RPE) | `future_devs/HISTORY_CARD_SET_DETAILS_SPEC.md` | `wrapped` |
| History cards default expanded | `future_devs/HISTORY_CARDS_DEFAULT_EXPANDED_SPEC.md` | `wrapped` |
| Summary RPE inline format | `future_devs/SUMMARY_RPE_INLINE_FORMAT_SPEC.md` | `wrapped` |
| Workout summary — set type labels | `future_devs/WORKOUT_SUMMARY_SET_TYPE_LABELS_SPEC.md` | `wrapped` |
| HC UX restructure | `future_devs/HC_UX_RESTRUCTURE_SPEC.md` | `wrapped` |

---

## Phase P3 — Health Foundation (all wrapped)

| Feature | Spec | Status |
|---|---|---|
| Health History ledger + auto red/yellow list mapping | `PROFILE_SETTINGS_REDESIGN_SPEC.md §2` | `done` |

---

## Phase P4 — Trends Charts (mostly wrapped)

| Feature | Spec | Status |
|---|---|---|
| Trends Step 2 — VolumeTrendCard | `TRENDS_CHARTS_SPEC.md §Step 2` | `done` |
| Trends Step 3 — E1RMProgressionCard | `TRENDS_CHARTS_SPEC.md §Step 3` | `done` |
| Trends Step 4 — MuscleGroupVolumeCard | `TRENDS_CHARTS_SPEC.md §Step 4` | `wrapped` |
| Trends Step 5 — EffectiveSetsCard | `TRENDS_CHARTS_SPEC.md §Step 5` | `wrapped` |
| HC Phase B — Write workouts to Health Connect | `HEALTH_CONNECT_SPEC.md §8` | `wrapped` |
| HC Backfill — Push last 90 days | `future_devs/HC_BACKFILL_SPEC.md` | `wrapped` |
| History → Trends deep-link (Step B) | `HISTORY_SUMMARY_REDESIGN_SPEC.md §Trends Integration` | `wrapped` |
| E1RM Progression — line only | `future_devs/TRENDS_E1RM_LINE_ONLY_SPEC.md` | `wrapped` |
| Trends — hide cards with insufficient data | `future_devs/TRENDS_EMPTY_CARDS_HIDDEN_SPEC.md` | `wrapped` |
| Trends charts — Y axis values only, unit at top | `future_devs/TRENDS_CHART_Y_AXIS_UNIT_AT_TOP_SPEC.md` | `wrapped` |
| Trends charts — zoomed out by default, pinch to zoom | `future_devs/TRENDS_CHART_ZOOM_DEFAULT_OUT_SPEC.md` | `wrapped` |

---

## Phase P5 — Trends Advanced + Exercise Library (mostly wrapped)

| Feature | Spec | Status |
|---|---|---|
| Exercise animations in ExerciseDetailSheet | `future_devs/EXERCISE_ANIMATIONS_SPEC.md` | `wrapped` |
| Exercise joint indicators | `future_devs/EXERCISE_JOINTS_SPEC.md` | `superseded` |
| Exercise Detail Sheet — Full Revision | `future_devs/EXERCISE_DETAIL_SHEET_REVISION_SPEC.md` | `wrapped` |
| Exercise Detail Screen — Tab-Based Redesign (v2) | `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md` | `wrapped` |
| Exercise "How to Perform" descriptions | `future_devs/EXERCISE_HOW_TO_PERFORM_SPEC.md` | `wrapped` |
| DB synonym foundation | `future_devs/DB_SYNONYM_FOUNDATION_SPEC.md` | `wrapped` |
| Exercise Library — favourites quick-filter button | `future_devs/EXERCISE_FAVORITES_FILTER_SPEC.md` | `wrapped` |
| Alternative exercise — movement-specific weight transfer | `future_devs/ALTERNATIVE_WEIGHT_TRANSFER_SPEC.md` | `wrapped` |

---

## Phase P6 — Body Heatmap (partially wrapped)

| Feature | Spec | Status |
|---|---|---|
| SVG/Canvas body outline rendering | `TRENDS_SPEC.md §10` | `wrapped` |
| Full heatmap card (wired end-to-end) | `TRENDS_SPEC.md §10` | `wrapped` |

---

## Phase P7 — AI Workout Generation (all wrapped)

| Feature | Spec | Status |
|---|---|---|
| AI Workout Generation (text + photo → workout) | `AI_SPEC.md` | `wrapped` |

---

## Phase P8 — Functional Training (wrapped items)

| Feature | Spec | Status |
|---|---|---|
| WorkoutStyle enum + Settings card | `future_devs/FUNC_STYLE_PREFERENCE_SPEC.md` | `wrapped` |
| Exercise.tags column + seed + filter | `future_devs/FUNC_EXERCISE_TAGS_SEED_SPEC.md` | `wrapped` |
| Extract TimerEngine class + real JetBrains Mono font | `future_devs/FUNC_TIMER_ENGINE_EXTRACT_SPEC.md` | `wrapped` |
| RoutineBlock + WorkoutBlock entities + MIGRATION_50_51 | `future_devs/FUNC_BLOCK_ENTITIES_MIGRATION_SPEC.md` | `wrapped` |
| Cardio exercise seed | `future_devs/FUNC_CARDIO_EXERCISE_SEED_SPEC.md` | `wrapped` |
| Exercise Library — exerciseType filter chips | `future_devs/EXERCISE_TYPE_FILTER_CHIPS_SPEC.md` | `superseded` |
| Exercise Library — collapsible filter panel | `future_devs/EXERCISE_FILTER_COLLAPSE_SPEC.md` | `superseded` |
| Exercise Library — filter dialog (Tune icon) | `future_devs/EXERCISE_FILTER_DIALOG_SPEC.md` | `wrapped` |
| exerciseType seed gap fix — PLYOMETRIC + STRETCH retag | `future_devs/FUNC_EXERCISE_TYPE_RETAG_SEED_SPEC.md` | `wrapped` |
| Yoga stretch seed — 15 yoga poses | `future_devs/FUNC_YOGA_STRETCH_SEED_SPEC.md` | `wrapped` |
| Exercise filter dialog — sticky bottom action bar | `future_devs/EXERCISE_FILTER_DIALOG_STICKY_ACTIONS_SPEC.md` | `wrapped` |
| FunctionalBlockWizard + Pure Functional template builder | `future_devs/FUNC_TEMPLATE_WIZARD_SPEC.md` | `wrapped` |

---

## Phase P9 — On-Device AI (wrapped/abandoned items)

| Feature | Spec | Status |
|---|---|---|
| AI parser interface layer | `future_devs/AI_PARSER_INTERFACE_LAYER_SPEC.md` | `wrapped` |
| AI ViewModel interface wiring | `future_devs/AI_VIEWMODEL_INTERFACE_WIRING_SPEC.md` | `wrapped` |
| AICore two-tier model cascade | `future_devs/AICORE_TWO_TIER_MODEL_CASCADE_SPEC.md` | `abandoned` |
| Firebase AI Logic SDK migration | `future_devs/FIREBASE_AI_SDK_MIGRATION_SPEC.md` | `wrapped` |
