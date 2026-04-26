# BUG: Tapping the progress ring area in functional overlays triggers the keyboard

## Status
[ ] Open

## Severity
P1 high
- The keyboard popping up mid-workout blocks the overlay UI and breaks the touch flow — the ring/tap zone should never be focusable

## Description
In the functional block overlays (AMRAP, RFT, EMOM, TABATA), tapping anywhere on or near the circular progress ring causes the soft keyboard to appear. This is because one of the composables in the overlay — likely a weight or reps input field — is gaining focus when the ring area receives a touch event.

During an active block the user must be able to tap the ring area freely (e.g. AMRAP BlindTapZone, EMOM progress ring) without triggering keyboard focus. All input fields inside the overlay must have `keyboardOptions` configured to not auto-focus on touch, and the ring/tap zone must consume touch events without forwarding them to focusable children.

## Steps to Reproduce
1. Start any functional block overlay (e.g. EMOM with Kettlebell Swing).
2. Tap anywhere on or near the circular progress ring.
3. Observe: the soft keyboard slides up over the overlay UI.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/EmomOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`, `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/TabataOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`, `THEME_SPEC.md §9.3`

## Fix Notes
<!-- populated after fix is applied -->
