# Fix Summary: History weights shown with 1 decimal place instead of 2

## Root Cause
`UnitConverter.formatNumber()` used `"%.1f"` in its `else` branch (values with decimals), producing `"102.5"` instead of `"102.50"`. Additionally, `WorkoutDetailViewModel.startEditMode()` had an inline `"%.1f"` format string that also skipped unit conversion for imperial users.

## Files Changed
| File | Change |
|---|---|
| `util/UnitConverter.kt` | Changed `"%.1f"` → `"%.2f"` in `formatNumber()` `else` branch — fixes all `formatWeight()` / `formatWeightDelta()` callers |
| `ui/history/WorkoutDetailViewModel.kt` | Replaced inline `"%.1f"` with `UnitConverter.formatWeightRaw(set.weight, unitSystem.value)` in `startEditMode()` — also fixes missing imperial conversion |
| `src/test/.../UnitConverterTest.kt` | Updated assertion: `formatNumber(32.25)` now expects `"32.25"` (was `"32.3"`); added `formatNumber(176.37) == "176.37"` case |

## Surfaces Fixed
- History list — all weight values now show 2 decimal places
- History detail header (total volume)
- History edit mode — pre-filled weight fields
- Insight cards that display weight deltas

## How to QA
1. Log a set with weight `102.5 kg` (or any value with a single decimal)
2. Go to **History** tab → open the workout
3. **Expected:** weight shown as `102.50 kg`
4. **Not expected:** weight shown as `102.5 kg`
5. Switch unit system to **Imperial** (Settings → Units) and repeat — verify lbs values also show 2 decimal places
