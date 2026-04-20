# Trends Charts — Zoomed Out by Default, Pinch to Zoom In

| Field | Value |
|---|---|
| **Phase** | P4 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `VolumeTrendCard.kt`, `E1RMProgressionCard.kt`, `MuscleGroupVolumeCard.kt`, `EffectiveSetsCard.kt`, `BodyCompositionCard.kt` |

---

## Overview

Trends chart cards currently open at a fixed zoom level that may show only a subset of bars at once, requiring the user to scroll to see the full dataset. The preferred default: charts open fully zoomed out so all data fits on screen at once. The user can then pinch to zoom in on a specific time window and scroll within it.

---

## Behaviour

- **Default state:** chart opens fully zoomed out — all bars/points in the selected time range are visible without scrolling
- **Zoom in:** user can pinch to zoom in on a region; the chart then becomes scrollable within that zoomed range
- **Zoom out:** pinch back out returns to the default full-range view
- **Scroll + zoom independence:** scrolling only activates after the user has zoomed in; at default zoom level, no horizontal scrolling (all data fits)
- Applies to **all** Trends chart cards: VolumeTrendCard, E1RMProgressionCard, MuscleGroupVolumeCard, EffectiveSetsCard, BodyCompositionCard

---

## UI Changes

In Vico, configure `rememberVicoZoomState(initialZoom = Zoom.Content)` (or equivalent) so the chart auto-scales to fit all content. Enable pinch-to-zoom via the scroll/zoom state. The existing scroll-to-end behaviour (most recent data visible) still applies when zoomed in past the full-range view.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/metrics/VolumeTrendCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/E1RMProgressionCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/MuscleGroupVolumeCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/EffectiveSetsCard.kt`
- `app/src/main/java/com/powerme/app/ui/metrics/BodyCompositionCard.kt`

---

## How to QA

1. Navigate to Trends → Volume Trend → tap 3M — all bars visible at once, no scrolling needed at default zoom
2. Pinch to zoom in — bars expand, chart becomes scrollable, most recent data shown (right side)
3. Pinch back out — returns to full-range view
4. Tap 1Y — all 52 weeks visible at once when zoomed out
5. Repeat on Muscle Balance and Effective Sets cards
