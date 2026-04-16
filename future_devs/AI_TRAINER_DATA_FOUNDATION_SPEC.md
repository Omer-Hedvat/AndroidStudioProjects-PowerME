# AI Trainer Data Foundation Spec

> **Phase:** P7 (new phase — AI Trainer groundwork)
> **Status:** `not-started`
> **Effort:** L (schema + migrations + collection UI touchpoints)
> **Depends on:** Profile/Settings split (P2) done, Health History (P3) done

---

## 1. Purpose

PowerME's end-goal is a fully AI-powered personal trainer that knows the user better than a human coach could. Before building the AI agent itself, the app must collect and store the right data. This spec defines every data gap between what the app currently has and what a credible AI trainer requires — grounded in sports science literature.

**This spec covers:** What data to collect, why, where it's stored, and how it's collected.
**This spec does NOT cover:** AI decision logic, prompt engineering, or agent architecture (separate spec).

**Design principle:** Minimal user friction. Data is collected passively (Health Connect, derived from workouts) or within existing UI flows (post-workout screen, profile settings). No daily check-in prompts or pop-ups.

---

## 2. Current Data Audit — What We Already Have

| Category | What's Collected | Stored In |
|---|---|---|
| **Identity** | email, name, gender, DOB, height, weight, body fat % | `users` |
| **Lifestyle profile** | occupation, chronotype, parental load, sleep hours | `users` |
| **Training profile** | experience level, training age, training targets | `users` |
| **Per-workout** | start/end time, duration, volume, routine, notes, 1-5 star rating | `workouts` |
| **Per-set** | exercise, weight, reps, RPE (0-100 int), set type, rest duration, TUT timestamps, superset group | `workout_sets` |
| **Exercise catalog** | 150+ exercises, primary/secondary muscle groups, equipment, bar type, rest durations, exercise type, families | `exercises` + `exercise_muscle_groups` |
| **Routines** | templates with per-set weight/rep/type config, supersets, sticky notes | `routines` + `routine_exercises` |
| **Body metrics (time series)** | weight, height, body fat %, calories | `metric_log` |
| **Health (daily, from HC)** | sleep duration, HRV, RHR, daily steps | `health_connect_sync` |
| **Medical** | injuries/surgeries/conditions, severity, body region, dates, red/yellow exercise lists | `health_history_entries` + `medical_ledger` |
| **Gym equipment** | equipment lists, dumbbell ranges per gym | `gym_profiles` |
| **Computed (not stored)** | e1RM (Epley), readiness score (z-score HRV/sleep/RHR), weekly volume, muscle group volume, effective sets | `TrendsDao` + `ReadinessEngine` |

---

## 3. Data Gaps — Organized by Priority

### 3.1 Critical Gaps (Must-Have for a Credible AI Trainer)

These are data points without which the AI simply cannot perform core trainer functions like periodization, auto-regulation, or injury risk prediction.

---

#### GAP-1: Session RPE (sRPE)

**What:** A single 1-10 rating of how hard the *overall session* felt, distinct from per-set RPE (which measures proximity to failure on individual sets) and from `sessionRating` (which measures satisfaction/enjoyment).

**Why the AI needs it:**
- Session RPE is the foundation of the sRPE training load model (Foster et al., 2001): `session load = sRPE x duration (minutes)`
- This single value enables **Acute:Chronic Workload Ratio (ACWR)** — the primary validated injury risk predictor in sports science (Gabbett, 2016). ACWR between 0.8-1.3 is the "sweet spot"; spikes above 1.5 correlate with 2-4x injury risk.
- Enables **training monotony** (mean daily load / SD daily load) and **training strain** (weekly load x monotony). High strain >6000 AU correlates with illness/overtraining (Foster, 1998).
- Captures *internal load* — the same external volume feels different on fatigued vs. fresh days. Per-set RPE cannot substitute because users often skip it, and it doesn't capture CNS/cardiovascular/psychological strain.

**Without it:** The AI has no way to compute internal training load, ACWR, or strain. It's blind to whether the user is in an injury risk zone.

**Collection point:** Post-workout summary screen (already shows `sessionRating` 1-5 stars). Add a 1-10 RPE slider directly below or adjacent.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `workouts` | `sessionRpe` | `Int?` | `null` | 1-10 scale. Nullable because older workouts won't have it. |

**Migration:** `ALTER TABLE workouts ADD COLUMN sessionRpe INTEGER DEFAULT NULL`

---

#### GAP-2: Training Schedule Intent

**What:** The user's intended training frequency (days/week) and preferred training days.

**Why the AI needs it:**
- Every periodization model (linear, DUP, block) requires knowing sessions per week to structure microcycles and mesocycles
- Volume landmarks (MEV, MAV, MRV per Israetel/Renaissance Periodization) are defined relative to frequency — 12 sets/week for chest is adequate at 3x/week but low at 6x/week
- Enables **missed-session detection** — distinguishing "planned rest day" from "skipped workout" (critical behavioral adherence signal)
- Enables split-type inference (PPL, Upper/Lower, Full Body) and session distribution optimization
- Prilepin's chart recommendations specify volume per *session* given a rep/intensity range — requires knowing session count

**Without it:** The AI cannot prescribe a mesocycle, detect missed sessions, or compute per-session volume targets.

**Collection point:** Profile settings (new "Training Schedule" section) or onboarding flow.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `users` | `trainingDaysPerWeek` | `Int?` | `null` | Range: 1-7 |
| `users` | `preferredTrainingDays` | `String?` | `null` | Comma-separated: "MONDAY,WEDNESDAY,FRIDAY,SATURDAY" |

**Migration:** Two `ALTER TABLE users ADD COLUMN` statements.

---

#### GAP-3: Prescribed vs. Actual (Per-Set Intent)

**What:** For each set in a workout, the *prescribed* weight, reps, and RPE target alongside the *actual* values. Currently, `RoutineExercise` stores template values and `WorkoutSet` stores actuals, but there is no direct pairing within a single workout session.

**Why the AI needs it:**
- Auto-regulation requires a feedback loop: the AI prescribes "3x8 @ 80kg RPE 7-8", the user does 80/8/8, 80/7/8, 75/8/9 — the AI needs to see the gap between intent and execution
- Enables **RPE creep detection** — fatigue accumulating faster than expected (prescribed RPE 7, actual RPE 9 = underfatigue or overambitious prescription)
- Enables **compliance rate** per exercise — how often the user hits prescribed targets (behavioral signal)
- Enables **RPE accuracy tracking** — does the user's RPE calibration drift over time? (important for auto-regulation reliability, see Helms et al., 2018)
- Required for implementing RIR-based progression: "last session you hit RPE 8 at 80kg, prescribing 82.5kg targeting RPE 8"

**Without it:** The AI cannot distinguish "user chose to lift less" from "AI prescribed too much." No feedback loop = no learning.

**Collection point:** Automatic — when a workout is instantiated from a routine, copy template values into prescribed columns. When the AI generates a program, it writes prescribed values. User fills in actuals as they do today.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `workout_sets` | `prescribedWeight` | `Double?` | `null` | Weight the AI/routine prescribed |
| `workout_sets` | `prescribedReps` | `Int?` | `null` | Reps prescribed |
| `workout_sets` | `prescribedRpe` | `Int?` | `null` | Target RPE (0-100 scale, matching existing `rpe` encoding) |

**Migration:** Three `ALTER TABLE workout_sets ADD COLUMN` statements.

**Backfill note:** Existing workouts will have null prescribed values. The AI treats null as "no prescription existed" (user was self-programming).

---

#### GAP-4: Mesocycle / Training Phase Tracking

**What:** An entity representing where the user is in their training block — accumulation, intensification, deload, peaking, or transition.

**Why the AI needs it:**
- Periodization is the core function of an AI trainer. Without phase tracking, the app is a log book, not a coach.
- Block periodization (Issurin, 2010) requires explicit phase transitions between hypertrophy, strength, and peaking blocks
- Supercompensation theory (Zatsiorsky & Kraemer) requires planned overreaching followed by recovery — without phase markers, the AI cannot distinguish intentional overreach from accidental overtraining
- Volume targets change by phase: accumulation weeks ramp toward MRV, deload weeks drop to MEV or lower
- The AI needs to know "this is week 3 of a 4-week accumulation block" to make correct intensity/volume prescriptions

**Without it:** The AI cannot implement any periodization model. It can only react to the last session, not plan across weeks.

**Collection point:** AI-managed. The AI generates phases based on user goals, experience, and schedule. User can view and override in a "My Program" screen (future UI spec).

**Storage — new entity:**

```kotlin
@Entity(tableName = "training_phases")
data class TrainingPhase(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,                          // FK to users.email
    val programName: String,                     // e.g. "Hypertrophy Block Q2 2026"
    val phaseType: String,                       // ACCUMULATION / INTENSIFICATION / DELOAD / PEAKING / TRANSITION
    val weekNumber: Int,                         // Current week within this phase (1-indexed)
    val totalWeeks: Int,                         // Planned duration of this phase
    val startDate: Long,                         // Epoch ms
    val endDate: Long?,                          // Epoch ms, null if open-ended
    val targetVolumePercent: Int?,               // % of estimated MRV (e.g., 60 for deload, 90 for peak accumulation)
    val targetIntensityPercent: Int?,            // % of 1RM range target
    val notes: String? = null,                   // AI or user notes
    val source: String = "AI",                   // AI / USER_OVERRIDE
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

**Migration:** `CREATE TABLE training_phases (...)` + add to `@Database` entity list.

---

#### GAP-5: Training Program Entity

**What:** A grouping entity above routines — "these 3 routines form a PPL split, running for 8 weeks as part of a hypertrophy block."

**Why the AI needs it:**
- Currently routines are independent templates. The AI needs to know that Routine A (Push), Routine B (Pull), and Routine C (Legs) form a single program with a rotation schedule.
- Enables **workout-to-workout sequencing** — "this is session 2 of 3 this week" and "yesterday was heavy pull, today should be moderate push"
- Required for split-type management (PPL, Upper/Lower, Full Body, Arnold, Bro split)
- Required for progressive overload across a program — the AI needs to see the full program to distribute volume and intensity correctly

**Without it:** The AI treats each workout in isolation. Cannot implement a training split or multi-week program.

**Storage — new entity:**

```kotlin
@Entity(tableName = "training_programs")
data class TrainingProgram(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,                          // FK to users.email
    val name: String,                            // e.g. "PPL Hypertrophy"
    val splitType: String?,                      // PPL / UPPER_LOWER / FULL_BODY / ARNOLD / CUSTOM
    val isActive: Boolean = true,                // Only one active program per user
    val startDate: Long,
    val endDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

**Junction entity — program-to-routine mapping:**

```kotlin
@Entity(
    tableName = "program_routines",
    foreignKeys = [
        ForeignKey(entity = TrainingProgram::class, parentColumns = ["id"], childColumns = ["programId"]),
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"])
    ]
)
data class ProgramRoutine(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val programId: String,
    val routineId: String,
    val dayOfWeek: String?,                      // MONDAY, TUESDAY, etc. (nullable for flexible scheduling)
    val orderInRotation: Int,                    // 1, 2, 3... for rotation-based splits
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

**Link workouts to programs:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `workouts` | `programId` | `String?` | `null` | FK to training_programs.id |
| `workouts` | `phaseId` | `String?` | `null` | FK to training_phases.id |

---

#### GAP-6: Nutrition Context (Lightweight)

**What:** Three fields capturing the user's current nutritional situation — not macro tracking, just context.

**Why the AI needs it:**
- Training recommendations change dramatically based on nutrition: in a caloric deficit, MRV drops significantly (Helms et al., 2014) — the AI must reduce volume
- In a surplus, recovery is enhanced and more volume can be tolerated
- During a cut, the AI should prioritize strength maintenance, reduce volume by ~30-50%, and maintain intensity
- Protein adequacy affects muscle protein synthesis — a user in a surplus not progressing may have insufficient protein

**Without it:** The AI programs the same volume for someone in a deep cut as someone bulking. This is a recipe for overtraining or undertraining.

**Collection point:** Profile settings, under a "Nutrition" section. Updated when goals change.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `users` | `nutritionPhase` | `String?` | `null` | BULK / CUT / MAINTAIN / RECOMP |
| `users` | `proteinTargetGPerKg` | `Float?` | `null` | Grams of protein per kg bodyweight per day (e.g. 2.0) |
| `users` | `caloricIntent` | `String?` | `null` | SURPLUS / MAINTENANCE / DEFICIT |

**Migration:** Three `ALTER TABLE users ADD COLUMN` statements.

---

#### GAP-7: Gym Profile Linked to Workouts

**What:** Which gym the user trained at for each workout session.

**Why the AI needs it:**
- Exercise selection must respect available equipment. If the user's home gym has only dumbbells, the AI should not prescribe barbell squats.
- `GymProfile` already exists with equipment lists, but no workout-level FK connects them.
- Enables the AI to build equipment-aware alternatives: "You're at your home gym today — substituting Goblet Squat for Barbell Back Squat."

**Without it:** The AI either ignores equipment constraints (bad prescriptions) or only uses the "active" gym profile (wrong when user alternates gyms).

**Collection point:** Automatic — set based on active gym profile at workout start. User can change via a gym selector on the active workout screen if they have multiple profiles.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `workouts` | `gymProfileId` | `Long?` | `null` | FK to gym_profiles.id |

**Migration:** `ALTER TABLE workouts ADD COLUMN gymProfileId INTEGER DEFAULT NULL`

---

### 3.2 High-Value Additions (Significantly Improve AI Quality)

These aren't strictly required but make the difference between a good AI trainer and a great one.

---

#### GAP-8: Volume Landmarks Per Muscle Group (MEV / MAV / MRV)

**What:** Per-muscle-group weekly set thresholds:
- **MV** (Maintenance Volume): minimum sets to not lose size
- **MEV** (Minimum Effective Volume): minimum to drive adaptation
- **MAV** (Maximum Adaptive Volume): sweet-spot range
- **MRV** (Maximum Recoverable Volume): ceiling before recovery fails

Concepts from Israetel et al. (Renaissance Periodization).

**Why the AI needs it:**
- The app already computes weekly effective sets per muscle group (`TrendsDao.getWeeklyEffectiveSets`). Without landmarks, those numbers are meaningless — is 12 sets/week for chest good or bad?
- For a novice, 12 sets might be MRV. For an experienced lifter, barely MEV.
- Required for intelligent volume progression across mesocycles and for deload prescription.

**Collection point:** AI-managed. Initial defaults based on experience level + training age (population heuristics). Refined over time by the AI based on performance data (e1RM plateaus, persistent soreness beyond 72h, performance degradation).

**Storage — new entity:**

```kotlin
@Entity(tableName = "volume_landmarks")
data class VolumeLandmark(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val muscleGroup: String,                     // e.g. "CHEST", "QUADS", "BACK"
    val mv: Int?,                                // Maintenance Volume (sets/week)
    val mev: Int?,                               // Minimum Effective Volume
    val mav: Int?,                               // Maximum Adaptive Volume
    val mrv: Int?,                               // Maximum Recoverable Volume
    val source: String = "DEFAULT",              // DEFAULT / AI_ESTIMATED / USER_OVERRIDE
    val confidence: Float = 0.5f,                // 0.0-1.0, increases as AI gathers more data
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

---

#### GAP-9: Exercise Stimulus-to-Fatigue Ratio (SFR)

**What:** A per-exercise attribute indicating systemic fatigue cost. Heavy barbell squats generate far more systemic fatigue than leg extensions at equal set counts.

**Why the AI needs it:**
- Two workouts with identical set counts and muscle group distribution can have wildly different recovery demands
- Enables fatigue budgeting: "Your weekly fatigue budget is nearly spent due to heavy compounds — swapping barbell rows for chest-supported rows (lower fatigue, similar stimulus)"
- Enables intelligent exercise substitution beyond just muscle group matching

**Collection point:** Metadata seeded on exercises. The AI refines per-user over time.

**Storage:**

| Entity | Column | Type | Default | Notes |
|---|---|---|---|---|
| `exercises` | `fatigueFactor` | `Float` | `0.5` | 0.0 (very low fatigue, e.g. cable flye) to 1.0 (very high, e.g. barbell deadlift). Seeded defaults for all 150+ exercises. |

**Seeding heuristic:** Compound + free weight + axial loading = high (0.7-1.0). Isolation + machine + no axial loading = low (0.1-0.4). Compound + machine = medium (0.4-0.6).

---

#### GAP-10: Structured Goal Parameters

**What:** The user's `trainingTargets` is currently a flat comma-separated string ("Hypertrophy,Strength"). The AI needs more structure.

**Why the AI needs it:**
- "Hypertrophy" and "Strength" as unranked tags don't tell the AI whether this is a powerlifter wanting accessory hypertrophy or a bodybuilder wanting to get stronger — the answer changes everything
- Goal timelines drive block periodization: "200kg deadlift by September" → 16-week peaking program
- Body composition targets drive nutrition-aware programming

**Collection point:** Enhanced goal-setting UI in profile. Could be part of onboarding or a "My Goals" section.

**Storage — new entity:**

```kotlin
@Entity(tableName = "training_goals")
data class TrainingGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val goalType: String,                        // STRENGTH_TARGET / BODY_COMP / GENERAL_FITNESS / SPORT_PERFORMANCE / REHABILITATION
    val priority: Int,                           // 1 = primary, 2 = secondary, etc.
    val title: String,                           // User-facing: "Hit 200kg deadlift"
    val exerciseId: Long? = null,                // For strength targets — FK to exercises
    val targetValue: Double? = null,             // e.g. 200.0 (kg) or 12.0 (% body fat)
    val targetUnit: String? = null,              // KG / LBS / PERCENT / REPS
    val targetDate: Long? = null,               // Deadline epoch ms (nullable for open-ended goals)
    val currentValue: Double? = null,            // AI-updated: latest relevant measurement
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

---

#### GAP-11: Menstrual Cycle Phase (Female Users Only)

**What:** Cycle phase tracking for female users — follicular, ovulatory, luteal, menstrual.

**Why the AI needs it:**
- Estrogen and progesterone fluctuations affect strength, recovery, and injury risk across the menstrual cycle
- During the **late luteal phase**, women may experience reduced strength and increased perceived effort — the AI should lower intensity expectations
- **ACL injury risk** is elevated during certain phases (Hewett et al., 2007) — the AI should flag high-risk exercises
- The **follicular phase** (high estrogen) is generally favorable for high-intensity training — the AI can push harder
- Without this, the AI gives the same advice regardless of cycle phase, which is suboptimal for ~50% of users

**Visibility:** This data and any related UI elements appear **only** when `user.gender == "FEMALE"`. Male and OTHER gender users never see cycle-related fields, prompts, or recommendations.

**Collection points (two sources, user chooses):**
1. **Health Connect** — read `MenstruationPeriodRecord` and `MenstruationFlowRecord` (passive, if user grants permission)
2. **Manual input** — simple "Log period start" button in profile or a minimal tracker

The AI infers cycle phase from period dates using standard cycle length (default 28 days, personalized over time).

**Storage — new entity:**

```kotlin
@Entity(tableName = "cycle_log")
data class CycleLogEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val date: Long,                              // Calendar date (epoch ms, midnight UTC)
    val eventType: String,                       // PERIOD_START / PERIOD_END / SYMPTOM
    val flowIntensity: String? = null,           // LIGHT / MEDIUM / HEAVY (from HC or manual)
    val notes: String? = null,
    val source: String = "MANUAL",               // MANUAL / HEALTH_CONNECT
    val createdAt: Long = System.currentTimeMillis(),
    val syncId: String = UUID.randomUUID().toString(),
    val updatedAt: Long = 0L
)
```

**Computed (not stored):** Cycle phase is derived on-the-fly from `CycleLogEntry` data:
- Days 1-5: MENSTRUAL
- Days 6-13: FOLLICULAR
- Day 14: OVULATORY
- Days 15-28: LUTEAL

(Adjusted as the AI learns the user's actual cycle length from historical data.)

**Health Connect permissions (new, female-only):**
- `READ_MENSTRUATION_PERIODS`
- `READ_MENSTRUATION_FLOW` (optional)

These permissions are only requested when `user.gender == "FEMALE"`.

---

### 3.3 Nice-to-Have (Can Be Approximated or Deferred)

These appear in elite training systems but can be approximated from existing data or deferred without crippling the AI.

---

#### GAP-12: Velocity-Based Training (VBT) Data

**What:** Bar speed (m/s) during lifts. The most objective measure of neuromuscular readiness.

**Why:** A 10-15% velocity loss at a given load predicts proximity to failure (Gonzalez-Badillo & Sanchez-Medina, 2010). Enables autoregulation without subjective RPE.

**Approximation:** The existing per-set RPE + reps-in-reserve (RPE 10 = 0 RIR, RPE 9 = 1 RIR) provides a reasonable proxy. Could add phone accelerometer-based estimation in a future phase.

**Decision:** Defer. RPE-based autoregulation is sufficient for v1 of the AI trainer.

---

#### GAP-13: Joint Mobility / Range of Motion Scores

**What:** Periodic assessment of key joint ranges (shoulder flexion, hip flexion, ankle dorsiflexion, thoracic rotation).

**Why:** Limited mobility constrains exercise selection and increases injury risk. Poor ankle dorsiflexion → excessive forward lean in squat → lower back risk.

**Approximation:** Partially inferred from `HealthHistoryEntry` — injuries/surgeries in specific body regions imply mobility limitations. The red/yellow exercise lists already encode some of this.

**Decision:** Defer. The medical history system provides a workable proxy.

---

#### GAP-14: Grip Strength / CNS Readiness Proxy

**What:** Periodic maximal grip strength test. A 10%+ drop from baseline correlates with CNS fatigue.

**Approximation:** Can be inferred from performance trends on pulling exercises — if deadlift and row numbers drop while push exercises hold, grip/CNS fatigue is likely.

**Decision:** Defer. Performance trends + readiness score provide adequate CNS insight.

---

#### GAP-15: VO2 Max Time Series

**What:** Already planned in `HEALTH_CONNECT_EXTENDED_READS_SPEC.md` as a single-point read. The gap is trending it over time in `metric_log`.

**Decision:** Addressed when HC Extended Reads ships (P4). Add `MetricType.VO2_MAX` to `metric_log` at that time.

---

#### GAP-16: Respiratory Rate

**What:** Overnight respiratory rate from wearables. Early marker of illness/overtraining.

**Approximation:** HRV and RHR already provide decent autonomic insight via `ReadinessEngine`.

**Decision:** Defer. Can be added as part of HC Extended Reads if `RespiratoryRateRecord` is supported.

---

## 4. Computed Metrics Unlocked by New Data

These are derived from the new data above and do NOT require additional user input. They represent the AI's analytical capabilities enabled by the data foundation.

| Metric | Formula / Source | Requires | Purpose |
|---|---|---|---|
| **Session Load** | `sessionRpe x durationMinutes` | GAP-1 | Internal training load quantification |
| **Weekly Load** | `SUM(session loads)` per 7-day window | GAP-1 | Training volume in arbitrary units |
| **ACWR** | `acute load (7d) / chronic load (28d rolling avg)` | GAP-1 | Injury risk prediction — sweet spot 0.8-1.3, danger >1.5 |
| **EWMA-ACWR** | Exponentially weighted version (Williams et al., 2017) | GAP-1 | More robust ACWR, less sensitive to outlier sessions |
| **Training Monotony** | `mean(daily load) / SD(daily load)` per week | GAP-1 | Training variety — high monotony (>2.0) = illness risk |
| **Training Strain** | `weekly load x monotony` | GAP-1 | Composite overtraining signal — >6000 AU = danger zone |
| **Compliance Rate** | `% of sets where actual ≈ prescribed` | GAP-3 | Behavioral adherence signal per exercise |
| **RPE Accuracy** | `mean(actual RPE - prescribed RPE)` over time | GAP-3 | User's RPE calibration drift |
| **Missed Session Rate** | `(intended sessions - actual sessions) / intended` per week | GAP-2 | Behavioral adherence at schedule level |
| **Volume vs. Landmark** | `actual weekly sets / estimated MRV` per muscle group | GAP-8 | Over/under-training detection per muscle |
| **Fatigue Budget** | `SUM(sets x fatigueFactor)` per week | GAP-9 | Systemic recovery demand estimation |
| **Cycle-Adjusted Readiness** | Readiness score modified by cycle phase factor | GAP-11 | Female-specific training readiness |

---

## 5. Schema Change Summary

### New Tables (5)

| Table | Entity | Purpose |
|---|---|---|
| `training_programs` | `TrainingProgram` | Groups routines into structured program/split |
| `program_routines` | `ProgramRoutine` | Junction: program ↔ routine with day/rotation mapping |
| `training_phases` | `TrainingPhase` | Mesocycle phase tracking (accumulation, deload, etc.) |
| `volume_landmarks` | `VolumeLandmark` | Per-muscle-group volume thresholds (MEV/MAV/MRV) |
| `training_goals` | `TrainingGoal` | Structured goal tracking with targets and deadlines |
| `cycle_log` | `CycleLogEntry` | Menstrual cycle tracking (female users only) |

### Modified Tables (3)

| Table | New Columns | Count |
|---|---|---|
| `users` | `trainingDaysPerWeek`, `preferredTrainingDays`, `nutritionPhase`, `proteinTargetGPerKg`, `caloricIntent` | +5 |
| `workouts` | `sessionRpe`, `programId`, `phaseId`, `gymProfileId` | +4 |
| `workout_sets` | `prescribedWeight`, `prescribedReps`, `prescribedRpe` | +3 |
| `exercises` | `fatigueFactor` | +1 |

### New Health Connect Permissions (female-only)

| Permission | Record Type | Condition |
|---|---|---|
| `READ_MENSTRUATION_PERIODS` | `MenstruationPeriodRecord` | Only requested when `user.gender == "FEMALE"` |

### Total Migration Delta

- 6 new tables
- 13 new columns across 4 existing tables
- 1 conditional HC permission
- Estimated DB version: v42 (or batch as single migration if implemented together)

---

## 6. Collection Point Summary — Where Each Data Point Enters the App

| Data Point | Where Collected | Friction Level |
|---|---|---|
| Session RPE (GAP-1) | Post-workout summary screen — 1-10 slider | Near-zero (already on this screen) |
| Training schedule (GAP-2) | Profile settings — "Training Schedule" section | One-time setup |
| Prescribed sets (GAP-3) | Automatic — copied from routine/AI when workout starts | Zero (invisible to user) |
| Mesocycle phases (GAP-4) | AI-managed, viewable in "My Program" screen | Zero (AI generates) |
| Training program (GAP-5) | AI-managed or user-created via routine grouping | Low |
| Nutrition context (GAP-6) | Profile settings — "Nutrition" section (3 fields) | One-time setup + update on phase change |
| Gym link (GAP-7) | Automatic — active gym profile at workout start | Zero |
| Volume landmarks (GAP-8) | AI-managed, seeded from experience level defaults | Zero |
| Fatigue factor (GAP-9) | Seeded metadata on exercises, AI-refined | Zero |
| Structured goals (GAP-10) | Profile — "My Goals" section or onboarding | One-time setup |
| Cycle tracking (GAP-11) | HC passive read OR manual "Log period" button (female only) | Near-zero if HC, low if manual |

**Total new user-facing inputs:** Session RPE slider (per workout), training schedule (one-time), nutrition phase (occasional), goals (one-time), cycle log (monthly, female only). All others are automatic or AI-managed.

---

## 7. Implementation Phasing Suggestion

This spec is large. Suggested sub-phasing within P7:

| Sub-phase | Gaps | Effort | Unlocks |
|---|---|---|---|
| **P7a — Core Signals** | GAP-1 (sRPE), GAP-2 (schedule), GAP-6 (nutrition), GAP-7 (gym link) | S | ACWR, training load, missed-session detection, nutrition-aware volume |
| **P7b — Program Layer** | GAP-4 (phases), GAP-5 (programs), GAP-3 (prescribed sets) | M | Periodization, auto-regulation feedback loop, compliance tracking |
| **P7c — AI Refinement Data** | GAP-8 (volume landmarks), GAP-9 (fatigue factor), GAP-10 (goals) | M | Volume optimization, fatigue budgeting, goal-driven programming |
| **P7d — Female Health** | GAP-11 (cycle tracking) | S | Cycle-adjusted readiness and programming |

P7a and P7d are independent and can be built in parallel. P7b depends on P7a (session load needs sRPE). P7c depends on P7b (volume landmarks reference programs/phases).

---

## 8. Literature References

| Citation | Concept | Relevant Gap |
|---|---|---|
| Foster et al., 2001 | sRPE training load model | GAP-1 |
| Gabbett, 2016 | ACWR injury risk | GAP-1 |
| Foster, 1998 | Training monotony and strain | GAP-1 |
| Williams et al., 2017 | EWMA-ACWR | GAP-1 |
| Israetel et al. (Renaissance Periodization) | MEV/MAV/MRV volume landmarks | GAP-8 |
| Issurin, 2010 | Block periodization | GAP-4 |
| Zatsiorsky & Kraemer | Supercompensation theory | GAP-4 |
| Helms et al., 2018 | RIR-based progression, RPE accuracy | GAP-3 |
| Helms et al., 2014 | Volume adjustment during caloric deficit | GAP-6 |
| Schoenfeld, 2010 | Mechanisms of hypertrophy (mechanical tension, metabolic stress, muscle damage) | GAP-9 |
| Gonzalez-Badillo & Sanchez-Medina, 2010 | Velocity-based training | GAP-12 (deferred) |
| Hewett et al., 2007 | ACL injury risk across menstrual cycle | GAP-11 |
| Saw et al., 2016 | Subjective wellness vs. HRV sensitivity | Design decision (minimal friction) |
| McEwen, 1998 | Allostatic load theory | Design decision (minimal friction) |
| Damas et al., 2018 | Soreness as SRA ground truth | Design decision (deferred) |

---

## 9. What This Spec Does NOT Cover (Future Specs)

| Topic | Why Separate |
|---|---|
| AI Agent Architecture | Decision logic, prompt engineering, model selection, tool use — separate spec |
| "My Program" UI | Screen design for viewing/editing programs and phases — separate UI spec |
| Readiness Engine v2 | Incorporating sRPE, ACWR, cycle phase into existing ReadinessEngine — separate spec |
| Exercise Substitution Engine | Using fatigue factor + equipment + medical data for smart swaps — separate spec |
| Onboarding v2 | Collecting training schedule, goals, nutrition during signup — separate UX spec |

---

*Spec created: 2026-04-15*
