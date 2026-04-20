# Fix Summary: Chronotype card missing top-level card title

## Root Cause
The `ChronotypeCard` composable jumped straight into its first sub-section ("SLEEP TREND") without a card-level header. All other Trends cards (Volume Trend, Strength Progression, Muscle Balance, etc.) render a bold title at the top of their `Column` before any sub-section content.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/metrics/charts/ChronotypeCard.kt` | Added "CHRONOTYPE" `Text` header + 16dp `Spacer` at the top of the card's `Column`, matching the style of all other Trends card headers |

## Surfaces Fixed
- Trends tab → Chronotype card now shows "CHRONOTYPE" as the top-level bold header above the Sleep Trend and Training Window sub-sections

## How to QA
1. Open the app and navigate to the **Trends** tab
2. Scroll to the card that contains **Sleep Trend** and **Training Window** sections
3. Verify "CHRONOTYPE" appears as a bold uppercase header at the very top of that card, above "SLEEP TREND"
4. Confirm the style matches other Trends card headers (same weight, same grey colour, same letter spacing)
