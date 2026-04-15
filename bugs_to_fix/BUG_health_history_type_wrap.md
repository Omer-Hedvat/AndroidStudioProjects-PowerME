# BUG: Health History "Add" sheet — Type segmented buttons wrap mid-word

## Status
[x] Fixed

## Description
In the "Add Health Entry" bottom sheet (Profile → Health History → "+" button), the **Type** segmented button row has 5 options (Injury / Surgery / Condition / Restriction / Other). At standard phone width the row divides available space equally across 5 segments, leaving too little room for the longer labels — "Condition" renders as "Conditio n" and "Restriction" renders as "Restricti on" (mid-word line-break).

## Steps to Reproduce
1. Open the app → Profile tab.
2. Scroll to Health History card.
3. Tap the "+" button to open the Add Health Entry sheet.
4. Observe the top segmented button row labelled "Type".

## Assets
- Screenshot: `bugs_to_fix/assets/health_history_type_wrap/Screenshot_20260415_115010_PowerME.jpg`
- Related spec: `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md`

## Fix Notes
Added a `shortLabel: String` property to the `HealthHistoryType` enum:
- `CONDITION` → `"Cond."`
- `RESTRICTION` → `"Restrict."`
- All others keep their full name.

The `HealthHistoryBottomSheet` composable now uses `type.shortLabel` instead of `type.displayName` for the segmented button text, so labels always fit on one line. The full `displayName` is still used everywhere else (entry rows, edit title, etc.).
