# Smart "Add Warmups" — Equipment-Aware Warmup Set Generator

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | Warmup sets — auto-collapse after completion ✅ |
| **Blocks** | — |
| **Touches** | `WarmupCalculator.kt` (new), `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`, `ExerciseDetailRepository.kt`, `WarmupCalculatorTest.kt` (new) |

---

## Overview

Add a smart "Add Warmups" option to the exercise card ManagementHubSheet during active workouts and edit mode. Instead of prepending 3 blank WARMUP sets (current behaviour), the system computes the optimal number of warmup sets based on the gap between the equipment's starting weight and the user's working weight, using equipment-aware parameters (barbell bar = 20 kg, dumbbell min = 4 kg, etc.). Warmup sets are auto-filled with progressive weights and descending reps.

If work sets already have weight values, the working weight is derived automatically. If work sets are empty, a prompt asks the user for their target weight/reps with a toggle to fill the work sets too.

---

## Algorithm

### Equipment → Warmup Parameters

Each equipment type maps to three values: **startWeight** (lightest practical load), **maxJump** (largest acceptable gap between consecutive warmup sets), **rounding** (nearest increment for rounding).

| Equipment Type | Start (kg / lb) | Max Jump (kg / lb) | Round (kg / lb) |
|---|---|---|---|
| Barbell | 20 / 45 | 20 / 45 | 2.5 / 5 |
| EZ Bar | 10 / 25 | 15 / 35 | 2.5 / 5 |
| Smith Machine | 15 / 35 | 20 / 45 | 2.5 / 5 |
| Landmine | 10 / 25 | 15 / 35 | 2.5 / 5 |
| Dumbbell | 4 / 10 | 5 / 10 | 1 / 2 |
| Kettlebell | 8 / 15 | 6 / 15 | 2 / 5 |
| Cable | 5 / 10 | 10 / 20 | 2.5 / 5 |
| Machine | 10 / 20 | 15 / 30 | 5 / 10 |
| Bench | 10 / 20 | 15 / 30 | 5 / 10 |
| Bodyweight (weight > 0) | 0 / 0 | 10 / 20 | 2.5 / 5 |

**Skip entirely** (button hidden): Bodyweight with weight == 0, Pull-up Bar, Rings, Resistance Band, Jump Rope, Battle Ropes, Sled, Ab Wheel, Medicine Ball.

### Core Computation

```
Input: workingWeight, equipmentType, unitSystem

1. Resolve (startWeight, maxJump, rounding) from table + unitSystem
2. gap = workingWeight - startWeight
3. if gap <= 0 → 0 warmup sets
4. if gap <= maxJump → 1 warmup set at ~60% of workingWeight
5. else:
     warmupCount = ceil(gap / maxJump), capped at 5
6. For i in 1..warmupCount:
     weight = startWeight + gap * (i / (warmupCount + 1))
     round to nearest `rounding`
7. Reps (descending): [10, 8, 5, 3, 2] trimmed to warmupCount
```

### Examples (metric)

| Exercise | Work Wt | Equipment | Gap | Sets | Warmup |
|---|---|---|---|---|---|
| Back Squat | 100 kg | Barbell | 80 | 4 | 40×10, 55×8, 70×5, 85×3 |
| Back Squat | 40 kg | Barbell | 20 | 1 | 30×10 |
| OHP | 50 kg | Barbell | 30 | 2 | 30×10, 40×8 |
| Lateral Raise | 10 kg | Dumbbell | 6 | 2 | 6×10, 8×8 |
| Lateral Raise | 6 kg | Dumbbell | 2 | 0 | — |
| Lat Pulldown | 60 kg | Cable | 55 | 5 | 15×10, 25×8, 35×5, 45×3, 55×2 |
| Weighted Pull-Up | 20 kg | BW (w>0) | 20 | 2 | 7×10, 14×8 |

---

## Behaviour

### Trigger
- New option **"Add Warmups"** in `ManagementHubSheet`, between "Set Rest Timers" and "Replace Exercise".
- **Visible only when:** `exerciseType == STRENGTH` AND (equipment is not in the skip list, OR work sets have weight > 0 for bodyweight equipment).

### Flow A — Work sets already have weight
1. User taps "Add Warmups".
2. **If warmup sets already exist** → confirmation dialog: "Replace existing warmup sets?" → Yes removes old warmups and computes new; No dismisses.
3. **If no warmup sets exist** → compute directly.
4. Working weight = average weight of non-empty NORMAL sets.
5. Compute warmup sets via algorithm.
6. Prepend warmup sets with filled weight + reps + `SetType.WARMUP`.

### Flow B — Work sets are empty
1. User taps "Add Warmups".
2. **If warmup sets already exist** → same replace confirmation.
3. Show bottom sheet / dialog:
   - **Working Weight** numeric input (respects unit system, uses SurgicalValidator).
   - **Working Reps** numeric input.
   - **Toggle**: "Fill work sets with these values" (default OFF).
4. On confirm:
   - If toggle ON → fill all NORMAL sets with entered weight + reps.
   - Compute warmup sets from entered working weight.
   - Prepend warmup sets.

### Edge Cases
- Exercise with 0 sets → "Add Warmups" still available; adds warmups only (user adds work sets separately).
- Working weight resolves to ≤ startWeight → show snackbar "Working weight too light for warmup sets".
- Unit system change mid-workout: warmup weights are stored as raw numbers (same as work sets); unit display adapts.

---

## UI Changes

### ManagementHubSheet
- New row: icon + "Add Warmups" label. Icon: `Icons.Default.FitnessCenter` or similar.
- Row hidden when exercise doesn't qualify (see Trigger rules above).

### WarmupPromptDialog (new composable)
- Title: "Set Working Weight"
- Weight input field (with unit label from user settings)
- Reps input field
- "Fill work sets" toggle row
- Cancel / Confirm buttons
- Uses `MaterialTheme.colorScheme.*` tokens, no hardcoded colors.

### Replace Confirmation Dialog
- Standard `AlertDialog`: "Replace existing warmup sets?" / "This will remove your current warmup sets and generate new ones." / Cancel + Replace buttons.

---

## Files to Touch

- **New** `app/src/main/java/com/powerme/app/util/WarmupCalculator.kt` — pure computation: `WarmupParams`, `equipmentToWarmupParams()`, `computeWarmupSets()`
- **New** `app/src/test/java/com/powerme/app/util/WarmupCalculatorTest.kt` — algorithm tests
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — ManagementHubSheet option + WarmupPromptDialog + replace dialog
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — rewrite `addWarmupSetsToExercise()` → new `addSmartWarmups()` that uses `WarmupCalculator`
- `app/src/main/java/com/powerme/app/data/repository/ExerciseDetailRepository.kt` — replace hardcoded `computeWarmUpRamp()` with `WarmupCalculator` call so About tab and active workout share the same algorithm

---

## How to QA

1. Start a workout with a Barbell exercise (e.g. Back Squat), set work sets to 100 kg → tap ⋮ → "Add Warmups" → verify 4 warmup sets with correct weights and descending reps.
2. Start a workout with empty work sets → "Add Warmups" → verify prompt appears → enter 60 kg × 8 → toggle "Fill work sets" ON → verify work sets filled AND warmup sets added.
3. With warmup sets already present → "Add Warmups" → verify replace confirmation dialog.
4. Dumbbell exercise at 10 kg → verify 2 warmup sets.
5. Bodyweight exercise with no weight → verify "Add Warmups" is hidden.
6. Bodyweight exercise with weight > 0 (e.g. Weighted Pull-Up at 20 kg) → verify warmup sets generated.
7. Switch to imperial units → verify lb-appropriate start weights (45 lb bar) and rounding.
8. Exercise Detail About tab → verify warmup ramp matches the new algorithm output.
