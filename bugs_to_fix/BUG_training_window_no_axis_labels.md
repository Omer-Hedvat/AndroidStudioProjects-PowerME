# BUG: Training Window scatter plot — missing axis labels

## Status
[x] Fixed

## Severity
P2 normal
- Chart is readable but axis labels are missing, making it unclear what the X and Y axes represent without prior context.

## Description
The Training Window sub-card inside ChronotypeCard (Trends tab) renders the scatter plot without axis labels. The X-axis (workout start hour, e.g. 6am, 12pm, 6pm) and Y-axis (volume in kg) have no labels, making the chart ambiguous to a new user.

Expected: X-axis should show hour labels (e.g. 6am, 9am, 12pm, 3pm, 6pm, 9pm), Y-axis should show volume units (kg or lbs).

## Steps to Reproduce
1. Navigate to Trends tab → scroll to CHRONOTYPE card
2. Observe the Training Window scatter plot
3. Note: X and Y axes have no labels

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `MetricsScreen.kt` or the ChronotypeCard composable (wherever the Training Window Canvas is drawn)

## Assets
- Related spec: `future_devs/TRENDS_CHARTS_SPEC.md §Step 8`

## Fix Notes
- Added `unitSystem: UnitSystem` parameter to `ChronotypeCard`, propagated through `TrainingWindowSection` to `ScatterPlot`
- Updated X-axis labels from `[12am, 6am, 12pm, 6pm]` to `[6am, 9am, 12pm, 3pm, 6pm, 9pm]` — more granular and starts at the typical workout window
- Added Y-axis unit label ("kg" or "lbs") centered above the Y-axis using `UnitConverter.weightLabel(unitSystem)`
- Replaced hardcoded `ProSubGrey` label color with `MaterialTheme.colorScheme.onSurfaceVariant` for correct light/dark theme support
- Replaced hardcoded `9.sp` text size with `MaterialTheme.typography.labelSmall.fontSize`
- `paddingTop` dynamically computed from `textSize + 6dp` (min 20dp) to always fit the unit label
- `MetricsScreen.kt`: passes `unitSystem = unitSystem` to `ChronotypeCard`
