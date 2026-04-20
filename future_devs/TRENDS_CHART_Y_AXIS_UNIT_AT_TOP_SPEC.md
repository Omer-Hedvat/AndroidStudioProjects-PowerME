# Trends Charts — Y Axis Values Only, Unit Label at Top

| Field | Value |
|---|---|
| **Phase** | P4 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `BodyCompositionCard.kt` |

---

## Overview

The Y axis on Trends chart cards currently appends the unit to every tick label (e.g. "80 kg", "100 kg", "120 kg"). This is visually noisy and wastes horizontal space. The preferred format: numeric values only on the tick marks, with a single unit label ("kg" or "lbs") placed once at the top of the Y axis.

---

## Behaviour

- Y axis tick labels show **numbers only** — no unit suffix per tick (e.g. `80`, `100`, `120`)
- A single unit label appears at the **top of the Y axis**, e.g. `kg` or `lbs`, using the user's preferred unit from settings
- If the user is in imperial mode, the label shows `lbs`
- Applies to **all** Trends chart cards with a numeric Y axis: VolumeTrendCard, E1RMProgressionCard, MuscleGroupVolumeCard, EffectiveSetsCard, BodyCompositionCard

---

## UI Changes

In Vico, configure the Y axis `VerticalAxis` formatter to strip the unit suffix from tick labels, and add a standalone `Text` composable above the Y axis showing the unit. Use `MaterialTheme.typography.labelSmall` and `MaterialTheme.colorScheme.onSurfaceVariant` for the unit label.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/metrics/VolumeTrendCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/E1RMProgressionCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/MuscleGroupVolumeCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/EffectiveSetsCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/BodyCompositionCard.kt`

---

## How to QA

1. Navigate to Trends → Volume Trend card — Y axis shows `80`, `100`, `120` (no "kg" per tick); "kg" appears once at top of Y axis
2. Switch to imperial in Settings — Y axis unit label updates to "lbs"
3. Repeat on Strength Progression, Muscle Balance, Effective Sets cards
