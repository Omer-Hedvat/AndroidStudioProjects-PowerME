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

## Phase P0 — Quick Wins

Self-contained, high-impact, no new infrastructure. Ship these first.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Set row spacing (2dp → 8dp) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §2` | XS | `done` | — |
| 3-second countdown beep on all timers | `TOOLS_SPEC.md §5` | XS | `wrapped` | — |
| Clocks warn auto half-time default | `future_devs/CLOCKS_WARN_AUTO_HALFTIME_SPEC.md` | S | `completed` | — |
| Timer sound options (bell, chime, click, silent) | `future_devs/TIMER_SOUND_OPTIONS_SPEC.md` | S | `wrapped` | — |
| Logout button on Profile page | `future_devs/PROFILE_LOGOUT_BUTTON_SPEC.md` | XS | `completed` | Profile/Settings split ✅ |
| Quick Start Workout (blank workout, no routine) | `future_devs/QUICK_START_WORKOUT_SPEC.md` | XS | `wrapped` | — |

---

## Phase P1 — Active Workout UX

Core workout loop improvements. Independent of all other phases.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Golden RPE indicator (8–9 highlight) | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §1` | S | `done` | — |
| Timed exercise countdown timer | `ACTIVE_WORKOUT_ENHANCEMENTS_SPEC.md §3` | M | `done` | — |

---

## Phase P2 — History & Profile

Post-workout experience + user identity. Can be built in any order within the phase.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| History Summary Redesign (Step A — no deep-link) | `HISTORY_SUMMARY_REDESIGN_SPEC.md` | L | `done` | — |
| Profile / Settings split (separate pages + nav icons) | `PROFILE_SETTINGS_REDESIGN_SPEC.md §1` | M | `done` | — |
| Fitness level card (Novice/Trained/Experienced/Athlete) | `PROFILE_SETTINGS_REDESIGN_SPEC.md §3` | S | `done` | Profile/Settings split |
| RPE auto-pop setting | `PROFILE_SETTINGS_REDESIGN_SPEC.md §4` | S | `wrapped` | — |
| History card set details (weights + RPE) | `future_devs/HISTORY_CARD_SET_DETAILS_SPEC.md` | S | `completed` | History Summary Step A ✅ |

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
| Trends Step 4 — MuscleGroupVolumeCard | `TRENDS_CHARTS_SPEC.md §Step 4` | M | `completed` | — |
| Trends Step 5 — EffectiveSetsCard | `TRENDS_CHARTS_SPEC.md §Step 5` | M | `completed` | — |
| HC Extended Reads (HR, Calories, VO₂ Max, Distance, SpO₂) | `HEALTH_CONNECT_EXTENDED_READS_SPEC.md` | M | `completed` | — |
| HC Phase B — Write workouts to Health Connect | `HEALTH_CONNECT_SPEC.md §8` | S | `wrapped` | — |
| HC Backfill — Push last 90 days to Health Connect on permission grant | `future_devs/HC_BACKFILL_SPEC.md` | S | `wrapped` | HC Phase B ✅ |
| History → Trends deep-link (Step B) | `HISTORY_SUMMARY_REDESIGN_SPEC.md §Trends Integration` | S | `completed` | Trends Step 3 (E1RM) ✅ + History Summary Step A ✅ |

---

## Phase P5 — Trends Advanced + CSV Import + Exercise Library

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Trends Step 6 — BodyCompositionCard | `TRENDS_CHARTS_SPEC.md §Step 6` | L | `completed` | — |
| Trends Step 7 — StepsTrendCard | `TRENDS_CHARTS_SPEC.md §Step 7` | S | `not-started` | HC Extended Reads (calories) |
| Trends Step 8 — ChronotypeCard | `TRENDS_CHARTS_SPEC.md §Step 8` | L | `in-progress` | — |
| CSV Import (Strong, Hevy, FitBod, generic) | `CSV_IMPORT_SPEC.md` | L | `completed` | — |
| Exercise animations in ExerciseDetailSheet | `future_devs/EXERCISE_ANIMATIONS_SPEC.md` | S | `completed` | — |
| Exercise joint indicators in ExerciseDetailSheet | `future_devs/EXERCISE_JOINTS_SPEC.md` | M | `completed` | — |

---

## Phase P6 — Body Heatmap

Flagship feature. Requires all groundwork below before any UI work begins.

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| Stress vectors — manual seed (top 30 exercises) | `TRENDS_SPEC.md §10` | M | `in-progress` | — |
| Stress vectors — Gemini expansion (remaining 120+) | `TRENDS_SPEC.md §10` | M | `blocked` | Manual seed done first |
| Stress accumulation algorithm + DB table | `TRENDS_SPEC.md §10` | M | `in-progress` | — |
| SVG/Canvas body outline rendering | `TRENDS_SPEC.md §10` | L | `not-started` | — |
| Full heatmap card (wired end-to-end) | `TRENDS_SPEC.md §10` | XL | `blocked` | All above + Trends P4 complete |

---

## Phase P7 — AI Workout Generation

On-device or cloud AI that turns free text or a photo into a ready-to-start workout. Architecture (on-device Gemma 4 vs Gemini Flash API vs hybrid) is **TBD — requires discussion before implementation starts.**

| Feature | Spec | Effort | Status | Depends on |
|---|---|---|---|---|
| AI Workout Generation (text + photo → workout) | `future_devs/AI_WORKOUT_GENERATION_SPEC.md` | XL | `not-started` | Quick Start Workout ✅ |

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
