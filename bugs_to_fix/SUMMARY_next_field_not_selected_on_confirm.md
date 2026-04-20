# Fix Summary: Next input field not auto-selected when confirming with checkmark

## Root Cause

`WorkoutInputField`'s select-all logic only triggered on `PressInteraction.Release` (physical tap). When focus advanced programmatically via `focusRequester.requestFocus()` (IME Next/Done action), no press interaction was emitted, so the existing text in the newly-focused field was never selected.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/components/WorkoutInputField.kt` | Added `wasFocused` state + `.onFocusChanged` modifier to also trigger select-all on programmatic focus gain |

## Surfaces Fixed

- Active workout weight field → IME Next → reps field: text now auto-selected
- Active workout reps field → IME Done → next set's weight field: text now auto-selected

## How to QA

1. Start an active workout with at least one exercise that has a previous value in both weight and reps
2. Tap the weight field — existing text should be selected (blue highlight)
3. Type a new weight value
4. Tap IME Next (keyboard "next" button) — reps field should gain focus with **all text selected**
5. Type a new reps value
6. Tap IME Done (keyboard "done/checkmark" button) — if another set exists, its weight field should gain focus with **all text selected**
7. Tap an already-focused field (tap same field again without moving focus) — text should re-select
8. Tap a field for the first time — text should be selected (existing behaviour preserved)
