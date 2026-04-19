# Fix Summary: Workout summary set rows show RPE right-aligned instead of inline

## Root Cause
`SetDetailRow` in `WorkoutSummaryScreen.kt` used a `SpaceBetween` Row with the weight×reps text on the left and an RPE badge (or "—" placeholder) on the right. This matches no established format and is harder to read than the inline `WEIGHT × REPS @ RPE` format used in the PREV column of the active workout screen.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` | Rewrote `SetDetailRow` to use `buildAnnotatedString` with `SpanStyle`; RPE now rendered as `@ RPE` suffix in RPE category color; removed right-aligned badge, `—` placeholder, and `SpaceBetween` Row layout |

## Surfaces Fixed
- Post-workout summary screen — set detail rows now read as `LABEL   WEIGHT × REPS @ RPE`
- History detail workout summary — same fix applies (same screen)

## How to QA
1. Complete a workout where at least some sets have RPE logged.
2. Open the workout summary and expand an exercise card.
3. Confirm set rows read as `1   100 kg × 5 @ 8.5` with the `@` portion in color (green/yellow/orange/red per RPE level).
4. Expand a card where no sets have RPE — confirm rows show `1   100 kg × 5` with no suffix and no `—` placeholder.
5. Open a historical workout from the History tab — confirm the same format.
