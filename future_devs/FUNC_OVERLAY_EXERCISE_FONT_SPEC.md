# Functional Overlay — Larger Exercise Name Typography

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Status** | `completed` |
| **Effort** | XS |
| **Depends on** | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| **Blocks** | — |
| **Touches** | `ui/workout/runner/BlockRecipeRow.kt` |

---

## Overview

In all four functional training overlays (AMRAP, RFT, EMOM, TABATA), exercise names are currently rendered at `bodyLarge`. During an active block the user is reading exercise names at a glance — often at arm's length — so they need to be larger and easier to scan.

---

## Behaviour

- Exercise name text in `BlockRecipeRow` changes from `bodyLarge` → `titleMedium`.
- The reps/hold-seconds label (`× N` / `Ns`) stays at `bodyLarge` (secondary info, de-emphasised is fine).
- No layout or logic changes — typography only.
- Applies everywhere `BlockRecipeRow` is used: AmrapOverlay, RftOverlay, EmomOverlay, TabataOverlay.

---

## UI Changes

**File:** `app/src/main/java/com/powerme/app/ui/workout/runner/BlockRecipeRow.kt`

Change the exercise name `Text` style:
```kotlin
// Before
style = MaterialTheme.typography.bodyLarge,

// After
style = MaterialTheme.typography.titleMedium,
```

No other files need changing — all four overlays reuse this composable.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/runner/BlockRecipeRow.kt` — bump exercise name style from `bodyLarge` → `titleMedium`

---

## How to QA

1. Open a routine with a functional block (AMRAP, RFT, EMOM, or TABATA).
2. Start the workout and tap "Start Block" to launch the overlay.
3. Verify the exercise names appear visibly larger than before.
4. Verify the reps/hold-seconds label on the right remains smaller (unchanged).
5. Repeat for at least two overlay types (e.g. EMOM + AMRAP).
