# Trends E1RM Progression — Line Only (No Area Fill)

| Field | Value |
|---|---|
| **Phase** | P4 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | Trends Step 3 — E1RMProgressionCard ✅ |
| **Blocks** | — |
| **Touches** | `E1RMProgressionCard.kt` |

---

## Overview

The Strength Progression card (E1RMProgressionCard) currently renders an area chart (line + filled area beneath it). The user prefers a clean trend line with no area fill — the fill adds visual noise without adding information in this context.

---

## Behaviour

- The E1RM chart renders as a **line chart only** — no shaded area beneath the line
- All other chart behaviour is unchanged: time-range chips, exercise picker, empty state, tooltips

---

## UI Changes

In `E1RMProgressionCard.kt`, update the Vico `LineCartesianLayer` configuration to remove the area fill. In Vico, this means setting `areaFill = null` (or equivalent) on the line spec.

Use existing `MaterialTheme.colorScheme.*` line color tokens — no hardcoded colors.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/metrics/E1RMProgressionCard.kt`

---

## How to QA

1. Navigate to Trends tab, scroll to **STRENGTH PROGRESSION** card
2. Verify the chart shows a **line only** — no shaded/filled area beneath it
3. Switch exercises and time-range chips — line still renders correctly
4. Verify no visual regressions on other chart cards
