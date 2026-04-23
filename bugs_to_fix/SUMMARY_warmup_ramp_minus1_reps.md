# Fix Summary: Warmup ramp shows -1 reps for last warmup set

## Root Cause
Three separate issues combined to produce a broken warmup ramp:

1. **Sentinel -1 row**: `computeWarmUpRamp` included a 100% / `reps = -1` row (meant to represent the working set target). UI rendered it verbatim, showing "-1 reps".
2. **Useless near-bar warmup sets**: `WarmupCalculator` generated a set at 98% of working weight for exercises close to bar weight (e.g. BB Shrug at 23 kg → 22.5 kg warmup). Meaningless.
3. **Wrong working weight source**: `computeWarmUpRamp` averaged ALL sets in the session (including WU sets stored as NORMAL), drastically understating the working weight (e.g. averaging 0+20+40+60+50+50 = 36.67 kg instead of using the top set at 60 kg).

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/repository/ExerciseDetailRepository.kt` | Removed -1 row; switched working weight source from session average to session max (non-zero weight) |
| `app/src/main/java/com/powerme/app/util/WarmupCalculator.kt` | Filter out any generated warmup set where weight ≥ 90% of working weight |
| `app/src/test/java/com/powerme/app/data/repository/ExerciseDetailRepositoryTest.kt` | Updated assertion from 4 rows to 3 |
| `app/src/test/java/com/powerme/app/util/WarmupCalculatorTest.kt` | Added test: barbell 23 kg returns empty (near-bar threshold) |

## Surfaces Fixed
- Exercise Detail → About tab → WARM-UP RAMP no longer shows "-1 reps" row
- Exercises with working weight close to bar weight (e.g. BB Shrug 23 kg) show no ramp instead of a useless 98% set
- Ramp now builds up to the session's heaviest set, not a diluted average skewed by warmup sets

## How to QA
1. Tap **BB Back Squat** (session: WU 0×10, WU 20×8, WU 40×4, WS 60×8, WS 50×10, WS 50×1) → About tab → WARM-UP RAMP: should show 2 sets (~54% and ~79%), NOT 1 set at 75%
2. Tap **BB Shrug** (23 kg working weight) → WARM-UP RAMP section should be absent
3. Tap any exercise with no history → WARM-UP RAMP section should be absent
4. Tap any heavy barbell exercise (100 kg+) → ramp should show 4–5 ascending sets
