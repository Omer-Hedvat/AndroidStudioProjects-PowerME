# BUG: Chronotype card missing top-level card title

## Status
[x] Fixed

## Severity
P3 low
- Cosmetic: card is functional but inconsistent with all other Trends cards which have a bold card-level header.

## Description
The card containing Sleep Trend and Training Window sections has no top-level card title. Every other Trends card has a bold header (VOLUME TREND, STRENGTH PROGRESSION, MUSCLE BALANCE, EFFECTIVE SETS, BODY COMPOSITION, etc.). This card jumps straight into the "SLEEP TREND" sub-section without a card-level title such as "CHRONOTYPE".

## Steps to Reproduce
1. Navigate to Trends tab → scroll to the card containing Sleep Trend and Training Window
2. Observe: no card-level title above "SLEEP TREND"

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ChronotypeCard.kt` (or wherever the card container is rendered in `MetricsScreen.kt`)

## Assets
- Related spec: `TRENDS_SPEC.md`, `future_devs/TRENDS_CHARTS_SPEC.md §Step 8`

## Fix Notes
Added a "CHRONOTYPE" card-level title at the top of the `ChronotypeCard` composable's `Column`, before the Sleep Trend section. Styled to match all other Trends card headers: `fontSize = 13.sp`, `FontWeight.SemiBold`, `ProSubGrey`, `letterSpacing = 1.sp`. Followed by a 16dp `Spacer` before the first sub-section.
