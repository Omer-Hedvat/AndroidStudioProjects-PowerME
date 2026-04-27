# BUG: Exercise options menu (kebab) in active workout overlaps Android navigation bar

## Status
[x] Fixed

## Severity
P2 normal
- The bottom option(s) in the exercise kebab dropdown are hidden behind the system nav bar on devices using gesture or 3-button navigation.

## Description
In the active workout screen, tapping the kebab (⋮) menu on an exercise card opens a `DropdownMenu` or `ModalBottomSheet` listing options (Replace, Remove, etc.). The menu does not account for system window insets, so its bottom items are clipped by the Android navigation bar on gesture-nav or 3-button-nav devices.

Likely root cause: the dropdown/sheet root composable uses a fixed bottom padding rather than `navigationBarsPadding()` or `WindowInsets.navigationBars`.

## Steps to Reproduce
1. Run the app on a device/emulator with Android gesture navigation or 3-button nav bar.
2. Start an active workout.
3. Tap the ⋮ (kebab) icon on any exercise card.
4. Observe: the bottom menu items are partially or fully hidden behind the nav bar.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `THEME_SPEC.md §9.6`

## Fix Notes
<!-- populated after fix is applied -->
