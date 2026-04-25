# Settings Page — Card Reorder

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | keep_screen_on_mode ✅ |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt`, `SETTINGS_SPEC.md` |

> Read `SETTINGS_SPEC.md §1` before touching any file.

---

## Overview

The current settings card order is not optimised for how users actually use the screen. Workout-related settings (Display & Workout, Rest Timer) are buried below Health Connect. AI is below Display & Workout despite being a primary feature. This task reorders the cards to put the most frequently accessed settings first and group related cards together.

---

## New Card Order

| # | Card | Rationale |
|---|---|---|
| 1 | **Appearance** | Set once; visual — stays first |
| 2 | **Units** | Set once; affects all numeric display |
| 3 | **Display & Workout** | Used per-session; moved up next to other workout settings |
| 4 | **Rest Timer** | Used per-session; grouped with Display & Workout |
| 5 | **AI** | Core feature; surfaces before the integrations |
| 6 | **Health Connect** | Integration; less frequently changed |
| 7 | **Cloud Sync** | Utility; rarely accessed |
| 8 | **Data Export** | Utility; rarely accessed |
| 9 | **Privacy** | Destructive action; stays at bottom |

**Changes from current order:**
- Display & Workout: 5 → 3
- Rest Timer: 4 → 4 (no change in relative group)
- AI: 6 → 5
- Health Connect: 3 → 6
- Cloud Sync: 8 → 7
- Data Export: 7 → 8

---

## Implementation

Reorder the `item { }` blocks inside the `LazyColumn` in `SettingsScreen.kt` to match the new order above. No logic changes — pure reorder.

Update `SETTINGS_SPEC.md §1 Card Order` table to reflect the new sequence.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — reorder `item { }` blocks in the `LazyColumn`
- `SETTINGS_SPEC.md` — update §1 Card Order table

---

## How to QA

1. Open Settings. Verify card order top-to-bottom: Appearance → Units → Display & Workout → Rest Timer → AI → Health Connect → Cloud Sync → Data Export → Privacy.
2. Verify all cards still render correctly and their controls work after the reorder.
