# Fix Summary: Training Window scatter plot — missing axis labels

## Root Cause
The `ScatterPlot` Canvas composable in `ChronotypeCard.kt` drew only 4 coarse X-axis labels (`12am, 6am, 12pm, 6pm`) and no Y-axis unit label. Label colors and font sizes were also hardcoded (`ProSubGrey`, `9.sp`) rather than sourced from the Material theme.

## Files Changed
| File | Change |
|---|---|
| `ui/metrics/charts/ChronotypeCard.kt` | Added `unitSystem: UnitSystem` param to `ChronotypeCard` and `TrainingWindowSection`; added `unitLabel: String` to `ScatterPlot`; updated X-axis labels to 6am/9am/12pm/3pm/6pm/9pm; added Y-axis unit label centered above Y-axis; switched to `MaterialTheme.colorScheme.onSurfaceVariant` + `MaterialTheme.typography.labelSmall.fontSize` |
| `ui/metrics/MetricsScreen.kt` | Pass `unitSystem = unitSystem` to `ChronotypeCard` |

## Surfaces Fixed
- Trends tab → CHRONOTYPE card → Training Window scatter plot now shows six X-axis hour labels (6am, 9am, 12pm, 3pm, 6pm, 9pm) and a small unit label ("kg" / "lbs") above the Y-axis

## How to QA
1. Open the Trends tab and scroll to the CHRONOTYPE card
2. Check the Training Window scatter plot (requires ≥ 10 workouts with `startTimeMs > 0` — use a seeded account or populate test data)
3. Confirm X-axis shows: **6am · 9am · 12pm · 3pm · 6pm · 9pm**
4. Confirm a small **"kg"** label appears above the Y-axis (top-left corner of the scatter plot)
5. Switch to Imperial in Settings — the label should update to **"lbs"**
6. Verify labels are readable in both light and dark themes
