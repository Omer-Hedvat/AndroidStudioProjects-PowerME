# Exercise Detail Sheet — Full Revision

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `done` |
| **Effort** | L |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ExercisesScreen.kt`, `ExercisesViewModel.kt`, `WorkoutDao.kt`, `TrendsDao.kt`, `TrendsRepository.kt`, `Exercise.kt`, `StatisticalEngine.kt` |

---

## Overview

The current `ExerciseDetailSheet` is a thin bottom sheet showing muscle groups, form cues, and joint indicators. This revision expands it into a comprehensive exercise profile — combining coaching content, personal history, trend charts, records, and smart recommendations in one place.

**Before implementing any UI, run `/ui-ux-pro-max`** to design the layout, section hierarchy, interaction model, and animation approach. Key open question for Opus + `/ui-ux-pro-max`: **bottom sheet vs full dedicated screen** — the content volume may justify a full screen. Let the design process decide.

This spec supersedes `EXERCISE_JOINTS_SPEC.md` (joint indicators) and absorbs its requirements.

---

## Content Sections

### 1. Header
- Exercise name (large), muscle group tags, equipment tag (barbell / dumbbell / cable / bodyweight / machine), difficulty badge (Beginner / Intermediate / Advanced)
- **Last performed** — date + summary ("5 days ago — 4×10 @ 80 kg")
- **Frequency insight** — "You train this ~2×/week" or "You haven't done this in 3 weeks"
- If exercise hits a joint in the user's health history → **prominent red warning banner** at top before the user sees anything else

### 2. Joint Indicators
- Two clearly labelled tiers: **Primary** and **Secondary**
- Primary chips: filled, `containerColor = MaterialTheme.colorScheme.primaryContainer`, `labelColor = onPrimaryContainer`
- Secondary chips: outlined, `border = BorderStroke(1.dp, outline)`, `labelColor = onSurface`
- Display-only — **not clickable**, no ripple, no button styling
- If a joint matches a health-history entry: `containerColor = errorContainer`, `labelColor = onErrorContainer`
- If both lists empty → row hidden entirely

### 3. Form Cues
- **Short explanation** — 2–3 sentence summary visible by default (key technique points)
- **Extended explanation** — full coaching breakdown, collapsed behind "Read more" / expandable
- Format: step-by-step cues (setup, execution, common mistakes, breathing)

### 4. Muscle Activation Visual
- Body outline heatmap (reuse P6 body map asset) showing which regions activate for this exercise
- Primary muscles: high intensity colour; secondary/stabiliser muscles: low intensity
- Static — no interaction needed for V1

### 5. Personal Records
- Best ever set (weight × reps), Estimated 1RM PR, Volume PR (single session), Best total reps
- Each record shows the date it was set
- Visually prominent — card or highlighted row

### 6. Trends
Show inline mini-charts for:
- Estimated 1RM over time
- Max weight over time
- Total volume per session
- Best set per session
- (Opus: suggest any additional signals that add insight here)

Time-range chips: 1M / 3M / 6M / 1Y. Charts reuse Vico setup from Trends tab.

### 7. Full Workout History
- Chronological list of every session this exercise appeared in
- Each entry: date, sets × reps × weight, RPE if logged
- Paginated or lazy-loaded (can be a long list)
- Tap on a session → navigates to that workout in History

### 8. Alternative Exercises
- List of exercises with similar primary muscle group and movement pattern
- e.g. Bicep Curl → Hammer Curl, Cable Curl, Concentration Curl
- **Recommended starting weight** — if the user has history on a similar exercise but not this one, estimate a starting weight from the similar exercise's last session (simple ratio based on exercise similarity; Opus to define the algorithm)
- Tap an alternative → opens its detail sheet

### 9. Progressive Overload Suggestion
- Based on last session, suggest next target: more weight or more reps
- e.g. "Last session: 80 kg × 10. Try 82.5 kg, or push for 80 kg × 12"
- Rule: if reps ≥ top of hypertrophy zone (12+), suggest weight increase; else suggest rep increase
- Only shown if the user has at least one prior session with this exercise

### 10. Set/Rep Zone Guide
- Visual band showing strength (1–5), hypertrophy (6–12), endurance (15+) zones
- Overlay where user has been training on this exercise (dot or highlight)
- Helps user understand if they're training in their intended zone

### 11. Warm-Up Ramp
- Suggested warm-up sets before working weight, based on last session weight
- e.g. 50% × 8 → 70% × 5 → 85% × 3 → working weight
- Only shown for exercises with a loaded working weight (not bodyweight)

### 12. User Notes
- Freetext note the user can attach to the exercise (persisted per-exercise)
- Examples: "feel it more with neutral grip", "keep elbows tight"
- Editable inline with a pencil icon; saved on blur

---

## Data Requirements

- `Exercise` entity: already has `primaryJoints`, `secondaryJoints`, muscle groups, form cues
- New field on `Exercise`: `userNote: String = ""` (DB migration required)
- `WorkoutDao`: query all sets for a given `exerciseId` across all workouts (history + records)
- `TrendsDao` / `StatisticalEngine`: per-exercise 1RM, volume, best set time series
- Alternatives: static mapping in `MasterExerciseSeeder` or derived from muscle group overlap (Opus to recommend approach)
- Recommended starting weight: derive from the user's most recent session on the most similar alternative

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — rebuild ExerciseDetailSheet (or extract to new screen)
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — new state flows for history, records, trends, alternatives
- `app/src/main/java/com/powerme/app/data/database/Exercise.kt` — add `userNote` field
- `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` — bump version, add migration
- `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` — migration object
- `app/src/main/java/com/powerme/app/data/database/WorkoutDao.kt` — query sets by exerciseId
- `app/src/main/java/com/powerme/app/data/database/TrendsDao.kt` — per-exercise trend queries
- `app/src/main/java/com/powerme/app/data/repository/TrendsRepository.kt` — per-exercise trend data
- `app/src/main/java/com/powerme/app/analytics/StatisticalEngine.kt` — per-exercise records
- `DB_UPGRADE.md` — document migration

---

## Implementation Notes

- **Run `/ui-ux-pro-max` before writing any composables.** This is a new major surface — the layout, section order, typography scale, scroll behaviour, and sheet vs screen decision must be designed first.
- Opus + plan mode is **required** for implementation — this touches DB, DAO, ViewModel, Repository, StatisticalEngine, and UI simultaneously.
- The alternatives recommendation algorithm (especially the starting weight estimate) needs an explicit design decision from Opus before implementation.
- Reuse existing chart infrastructure from Trends tab — do not rebuild Vico chart composables from scratch.

---

## How to QA

1. Open Exercises → tap Back Squat → verify header shows last performed date, joints split into Primary/Secondary, no tappable chips
2. Tap "Read more" on form cues → extended explanation expands
3. Records section shows 1RM PR and best set with dates
4. Trend charts render for 1M / 3M / 6M / 1Y chips
5. Workout history lists all prior sessions; tap one → navigates to correct workout in History
6. Alternatives section shows similar exercises; tap one → opens its detail sheet
7. User with a bicep curl history opening Hammer Curl → sees recommended starting weight
8. User has knee health history → Back Squat shows red warning banner at top, Knee chip uses ErrorContainer
9. User types a personal note → navigates away → reopens sheet → note persisted
10. Progressive overload suggestion appears if ≥1 prior session; absent for never-done exercises

---

## How to QA

1. **Navigation:** Exercises tab → tap any exercise card → `ExerciseDetailScreen` opens (full screen, not bottom sheet). Verify back button returns to library.
2. **Hero animation:** Animated WebP plays and loops. Missing asset → icon placeholder renders (no crash).
3. **Header:** Exercise name, muscle/equipment tags, last-performed summary, session count all visible.
4. **Injury banner:** Add a MODERATE/SEVERE health history entry for a joint → open an exercise using that joint → red warning banner appears at top of header.
5. **Joint indicators:** Primary joints in filled chips (primaryContainer); secondary joints outlined. Affected joint chips use errorContainer.
6. **Form cues:** Cues > 120 chars show "Read more" toggle. Tap expands; tap again collapses.
7. **Personal Records:** 2×2 grid shows Best Set / e1RM PR / Volume PR / Best Total Reps with dates. "No records" placeholder for exercises with no history.
8. **Progressive Overload:** Exercise with ≥1 session → suggestion card renders with overload recommendation. Never-done exercise → "No session data yet".
9. **Trends:** 1M/3M/6M/1Y filter chips → charts refresh. 5 mini charts render for exercises with ≥2 sessions.
10. **Warm-Up Ramp:** Barbell/dumbbell exercises with prior sessions → warmup table shown. Bodyweight-only exercises → section hidden.
11. **Muscle Activation:** Body heatmap renders with colored regions matching the exercise's primary movers.
12. **Alternatives:** LazyRow of similar exercises; tap one → navigates to that exercise's detail screen.
13. **Workout History:** List of past sessions. "Load more" button appears if > 20 sessions. Tap a row → navigates to workout summary.
14. **User Notes:** Type a note → navigate away → return → note persists (debounced 500ms DB write).
15. **DB migration:** Fresh install on v46 DB → app migrates to v47 without crash (exercises.userNote column added).
