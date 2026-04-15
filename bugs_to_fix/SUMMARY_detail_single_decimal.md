# Fix Summary: Workout detail (from summary) shows weights with only 1 decimal place

## Root Cause
`UnitConverter.formatNumber()` and `UnitConverter.formatWeightRaw()` both had a three-branch `when` expression: (1) integer values → no decimal, (2) exactly one decimal digit → `"%.1f"`, (3) else → `"%.2f"`. The prior fix (`BUG_history_weight_decimal_places`) only corrected the `else` branch of `formatNumber()`. The middle branch — triggered whenever `value * 10` is a whole number (e.g. `80.5`, `100.5`) — still produced `"80.5"` instead of `"80.50"` through both functions. `WorkoutDetailScreen` uses `formatWeightRaw()` for the display value in `StrengthSetDetailRow`, so any weight with exactly one decimal place showed 1 digit.

## Files Changed
| File | Change |
|---|---|
| `util/UnitConverter.kt` | Removed middle branch from both `formatNumber()` and `formatWeightRaw()`; all non-integer values now fall through to `"%.2f"` — consistent 2 decimal places everywhere |
| `src/test/.../UnitConverterTest.kt` | Updated 3 assertions: `formatWeight(80.5)` → `"80.50 kg"`, `formatNumber(80.5)` → `"80.50"`, `formatWeightRaw(80.5)` → `"80.50"`; renamed two test functions to reflect the new invariant |

## Surfaces Fixed
- `WorkoutDetailScreen` — strength set rows now show `"80.50"` instead of `"80.5"` for single-decimal weights
- `WorkoutDetailScreen` — edit-mode pre-filled fields (via `startEditMode()` → `formatWeightRaw`) also consistent
- All callers of `formatWeight()` / `formatWeightDelta()` (history cards, volume totals, insight cards) now consistently show 2 decimal places for non-integer values

## How to QA
1. Log a set with weight `80.5 kg` (or any weight with a single decimal place).
2. Finish the workout → go to **History** tab.
3. Tap the workout → **WorkoutSummaryScreen** opens.
4. Tap the **pen icon** (top-right) → **WorkoutDetailScreen** opens.
5. **Expected:** weight shown as `80.50` in the set row.
6. **Not expected:** weight shown as `80.5`.
7. Tap **⋮ → Edit Session** and verify the pre-filled weight field also shows `80.50`.
8. Switch to **Imperial** in Settings and repeat — lbs values with one decimal digit should also show 2 places (e.g. `177.47` not `177.5`).
