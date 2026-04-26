# Settings — Move Timer Sound into Rest Timer Card + Rename Display Card

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `completed` |
| **Effort** | XS |
| **Depends on** | Settings page reorder ✅ |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt`, `SETTINGS_SPEC.md` |

---

## Overview

"Timer sound" (alert tone for rest timers and clocks) currently lives in the Display & Workout card. It is semantically a Rest Timer setting and belongs alongside Audio / Haptics / Get Ready countdown in the Rest Timer card. After the move, the card formerly named "Display & Workout" contains only "Keep screen on" and is renamed to "Display".

---

## Behaviour

- Remove the Timer sound `ExposedDropdownMenuBox` row from the Display & Workout card.
- Add it to the Rest Timer card, below the Audio/Haptics switches (after a divider).
- Rename the "Display & Workout" card title to "Display".
- No logic changes — ViewModel and DataStore wiring unchanged.

---

## Files to Touch

- `ui/settings/SettingsScreen.kt` — move Timer sound row; rename card title
- `SETTINGS_SPEC.md` — update card content descriptions

---

## How to QA

1. Open Settings → confirm "Display" card shows only "Keep screen on".
2. Confirm "Rest Timer" card now shows Timer sound below the Audio/Haptics switches.
3. Change Timer sound selection → confirm it persists across Settings close/reopen.
