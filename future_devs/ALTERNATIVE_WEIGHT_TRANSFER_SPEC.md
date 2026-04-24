# Alternative Exercise — Movement-Specific Weight Transfer Ratios

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | Exercise Detail Tabs v2 ✅ |
| **Blocks** | — |
| **Touches** | `ExerciseDetailRepository.kt`, `ExerciseDetailModels.kt`, `Exercise.kt` (or new table), `ExerciseDetailRepositoryTest.kt` |

---

## Overview

When suggesting an alternative exercise, the app estimates a starting weight using `equipmentTransferRatio()` (e.g. Barbell → Dumbbell = 0.35). This ignores movement-specific strength differences. A user who Back Squats 100 kg cannot Front Squat 100 kg — front squats typically land ~20–30 % lower due to the anterior rack position limiting torso angle and upper back involvement.

The feature adds a second layer of transfer coefficients scoped to exercise pairs (or exercise families), applied on top of the existing equipment ratio. The combined multiplier produces a more accurate estimated starting weight for the alternative card.

**Before implementing**, consult **Opus in plan mode** to design the data model and algorithm. Key open questions are listed below — they require careful thought before any code is written.

---

## Open Design Questions (resolve with Opus before coding)

1. **Where do coefficients live?**
   - Option A: Hardcoded lookup table keyed on `(familyId, familyId)` pairs (e.g. `squat_family → squat_family → back_squat:front_squat = 0.75`).
   - Option B: Per-exercise metadata field (e.g. `relativeStrengthFactor: Float` normalised within a family, so back squat = 1.0, front squat = 0.75, goblet squat = 0.55).
   - Option C: Derived from the user's own data (if they've logged both exercises, compute the empirical ratio from their e1RMs).

2. **Combinatorial explosion**: There are O(N²) directed exercise pairs. How do we avoid maintaining thousands of coefficients? Family-level bucketing (Option B) seems most tractable but needs validation.

3. **Interaction with equipment ratio**: Should movement ratio and equipment ratio compose multiplicatively (`movementRatio × equipmentRatio`) or should one supersede the other?

4. **Cold start**: When neither exercise has user history, we fall back to the hardcoded coefficients. When both have history, we use the empirical ratio. When only the source has history, do we blend?

5. **UI**: Does the estimated weight card need any indication that a movement-specific adjustment was applied (e.g. a tooltip or sub-label "adjusted for movement type")?

---

## Current Behaviour (baseline)

`ExerciseDetailRepository.estimateStartingWeight()`:
```kotlin
val ratio = equipmentTransferRatio(sourceExercise.equipmentType, target.equipmentType)
return roundToNearest(sourceE1RM * ratio * 0.80, 2.5)
```
Only `equipmentType` is considered. Movement identity is ignored entirely.

---

## Proposed Behaviour (sketch — finalise with Opus)

```
estimatedWeight = sourceE1RM
    × movementTransferRatio(source, target)   // NEW
    × equipmentTransferRatio(source, target)  // existing
    × 0.80                                    // conservative safety factor
```

`movementTransferRatio` defaults to 1.0 when:
- source and target are the same exercise
- no coefficient is defined for the pair
- both exercises are in different families with no known relationship

---

## Files to Touch

- `ExerciseDetailRepository.kt` — add `movementTransferRatio()` helper, wire into `estimateStartingWeight()`
- `ExerciseDetailModels.kt` — possibly extend `AlternativeExercise` if UI needs to show the adjustment
- `Exercise.kt` or new DB table — if coefficients are stored as metadata (Option B / C)
- `ExerciseDetailRepositoryTest.kt` — cover same-family different-movement case (e.g. back squat → front squat produces lower estimate than back squat → back squat)

---

## How to QA

1. Open Back Squat exercise detail → Alternatives tab
2. Find Front Squat in the alternatives list
3. Verify its estimated starting weight is meaningfully lower than the Back Squat working weight (expect ~70–80 % of back squat e1RM, not 100 %)
4. Open Barbell Curl → Alternatives: Dumbbell Curl — verify equipment-only case still works (no regression)
5. Open an exercise with no history — verify no estimate is shown (no badge at all)
