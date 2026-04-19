# Trends — Hide Cards With Insufficient Data

| Field | Value |
|---|---|
| **Phase** | P4 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `MetricsScreen.kt`, `MetricsViewModel.kt`, `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `BodyCompositionCard.kt`, `ChronotypeCard.kt` |

---

## Overview

Currently, Trends cards with no data show a placeholder empty state ("Log body weight…", "No workouts in this period", etc.), which creates visual noise — especially for new users or users who haven't connected Health Connect.

The fix: a card with truly no underlying data (zero rows from the DB/HC query) is **not rendered at all** in the Trends scroll list. Cards appear only once they have at least one meaningful data point. This keeps the Trends tab concise and data-driven.

---

## Behaviour

### Hide threshold (per card)

| Card | Hidden when |
|---|---|
| VolumeTrendCard | 0 volume data points for the selected time range |
| E1RMProgressionCard | 0 sessions for the selected exercise |
| MuscleGroupVolumeCard | 0 muscle-group volume rows |
| EffectiveSetsCard | 0 effective-set rows |
| BodyCompositionCard | 0 weight AND 0 body fat records |
| ChronotypeCard — Sleep Trend | 0 sleep records (sub-section hidden; card remains if Training Window has data) |
| ChronotypeCard — Training Window | 0 workout-time data points (sub-section hidden; card remains if Sleep Trend has data) |
| ChronotypeCard | Hide entire card only if BOTH sub-sections have no data |

### Ordering

Cards with sufficient data appear first (existing order). Cards that are temporarily hidden do not leave blank gaps — the list reflows naturally.

No "move to end" design: hiding is cleaner than reordering, avoids layout jumps as data accumulates, and is consistent with the "data-first" philosophy.

### No empty states

Remove all per-card placeholder messages (the "Log body weight…" style copy). These become dead code once cards are conditionally rendered. Retain the existing loading spinner while data is being fetched.

### First-use discovery

Add a single `InfoCard` at the bottom of the Trends list when **3 or more cards are hidden**. Static copy:

> "Some charts are hidden — they appear once you have enough data logged."

This fires only if ≥3 cards are hidden; disappears once enough data exists. It does not list which cards are hidden.

---

## UI Changes

- `MetricsScreen.kt` — wrap each card composable in a `if (viewModel.hasXData)` guard
- `MetricsViewModel.kt` — expose a `Boolean` `StateFlow` per card: `hasVolumeData`, `hasE1rmData`, `hasMuscleGroupData`, `hasEffectiveSetsData`, `hasBodyCompositionData`, `hasChronotypeData`; derived from existing data flows (no new DB queries needed)
- Derive each flag as: `data.isNotEmpty()` (or `!= null` for single-value HC reads)
- InfoCard: simple `SurfaceCard` with body text, shown at list bottom when `hiddenCardCount >= 3`

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/metrics/MetricsScreen.kt` — conditional rendering per card
- `app/src/main/java/com/powerme/app/ui/metrics/MetricsViewModel.kt` — `hasXData` boolean flows
- Individual card files — remove empty-state `Box`/`Text` composables

---

## How to QA

1. Fresh install with no workouts and no HC data → Trends tab shows only the InfoCard (all chart cards hidden)
2. Log one workout → VolumeTrendCard appears; other HC-dependent cards still hidden
3. Connect HC with weight data → BodyCompositionCard appears
4. When ≥3 cards are hidden, InfoCard is visible at the bottom; when <3 hidden, InfoCard is gone
5. Changing time-range filter to a range with no data hides the card immediately (no empty state shown)
6. No blank gaps or layout shifts when cards hide/show
