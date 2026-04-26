# BUG: Functional overlay bottom buttons overlap Android navigation bar

## Status
[x] Fixed

## Severity
P2 normal

## Description
All four functional block overlays (AMRAP, RFT, EMOM, TABATA) use a fixed `padding(bottom = 24.dp)` on the root `Column`. This does not account for system window insets, so on devices with a gesture navigation bar or a 3-button nav bar the bottom action buttons (especially "Abandon") are partially or fully hidden behind the system UI.

Visible in screenshot: the "Abandon" button overlaps the Android nav bar area.

Fix: apply `WindowInsets.navigationBars` (or `Modifier.navigationBarsPadding()`) to the bottom of the root `Column` in all four overlays, or use `Modifier.safeDrawingPadding()`.

## Steps to Reproduce
1. Run the app on a device with Android gesture navigation (or 3-button nav).
2. Start any functional block workout and launch the overlay.
3. Observe: the bottom button(s) are clipped by the navigation bar.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `THEME_SPEC.md §9.6`

## Fix Notes
Replaced hardcoded `padding(bottom = 24.dp)` with `navigationBarsPadding()` on the root `Column` in all four overlays (`AmrapOverlay`, `RftOverlay`, `EmomOverlay`, `TabataOverlay`). Modifier chain is now `.padding(top = 24.dp).navigationBarsPadding()`.
