# Functional Training — CrossFit Exercise List Verification

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `completed` |
| **Effort** | XS |
| **Depends on** | func_exercise_gap_analysis ✅ |
| **Blocks** | func_exercise_expanded_seed |
| **Touches** | No code changes — research output only |

---

## Overview

A second research pass that verifies the gap analysis results against three authoritative CrossFit sources and identifies any movements the first pass missed. Deliverable is `FUNC_CROSSFIT_VERIFICATION_RESULTS.md`, which feeds directly into `func_exercise_expanded_seed`.

Sources used:
- https://www.crossfit.com/crossfit-movements (official CF movement library)
- https://gym-mikolo.com/blogs/home-gym/the-ultimate-crossfit-exercises-list-master-every-movement-in-your-training-toolbox
- https://thefitnessphantom.com/wp-content/uploads/2022/01/CrossFit-Workout-List-PDF.pdf

---

## Output

`future_devs/FUNC_CROSSFIT_VERIFICATION_RESULTS.md` — three sections:
1. **Confirmed** — gap analysis List B items validated by source pages
2. **Additional gaps** — 19 movements missed by the first pass (with pre-filled JSON)
3. **Not found** — List B items not on source pages (with keep/drop recommendation)

**Key finding:** 19 additional exercises identified including Butterfly Pull-Up, Burpee Box Jump-Over, all major DB-variant Olympic lifts (DB Hang Power Clean, DB Front Squat, DB Push Press, DB Push Jerk, DB Front-Rack Lunge, DB Overhead Squat, DB Power Clean), Snatch Balance, Muscle Snatch, Hang Snatch, Strict Bar Muscle-Up, Chest-to-Wall HSPU, Zercher Squat, Slam Ball, Tire Flip, Man Maker, Yoke Walk.

Total across both passes: **61 net-new exercises + 41 retagging operations**.
