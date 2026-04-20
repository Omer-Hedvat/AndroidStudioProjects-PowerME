# Summary RPE Inline Format

| Field | Value |
|---|---|
| **Phase** | P2 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | History card set details ✅ |
| **Blocks** | — |
| **Touches** | `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` |

---

## Overview

In the Workout Summary screen, each exercise card lists its sets as rows with weight × reps on the left and the RPE value as a colored badge on the right (see screenshot). This change moves the RPE inline, appending it to the weight × reps label as `weight × reps @ RPE`, so the entire set is readable in one place. The RPE color coding (green/yellow/orange/red scale) is preserved — the `@RPE` suffix should use the same color as the current badge.

---

## Behaviour

- **Format:** `weight × reps @ RPE` — e.g. `20 × 8 @ 8` or `22 × 8 @ 8.5`
- **Color:** The `@ RPE` portion (or the whole value) keeps the same RPE color as the current badge (see `RpeHelper` for the color logic).
- **No RPE recorded:** Row shows `weight × reps` as before — no `@` suffix, no dash placeholder.
- **Warmup / drop / failure sets:** Same rule — only append `@RPE` if an RPE value exists.
- **Remove right-side badge:** The colored badge currently rendered on the trailing end of the set row is removed; its information is now carried by the inline suffix.
- **Timed sets:** If a timed set has an RPE it follows the same rule: `duration @ RPE`.

---

## UI Changes

**Screen:** `WorkoutSummaryScreen.kt` — set row composable inside the exercise expansion card.

- Combine the existing weight × reps `Text` with a trailing `@ RPE` span in a different color using `buildAnnotatedString` / `SpanStyle`.
- The RPE color should come from `RpeHelper.rpeColor(rpe)` (or equivalent already used for the badge).
- Remove the `Badge` / trailing `Text` composable that currently shows the RPE on the right.
- Ensure row alignment remains flush-left for the label; no right-side element for RPE.

Reference theme tokens: `MaterialTheme.colorScheme.*` — do not hardcode colors beyond what `RpeHelper` already returns.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` — rewrite set row to inline RPE into the label and remove the trailing badge

---

## How to QA

1. Complete a workout that includes sets with RPE values recorded.
2. Open Workout Summary for that session.
3. Expand an exercise card that has RPE values — confirm set rows read as `weight × reps @ RPE` with colored `@RPE`.
4. Expand an exercise card with no RPE — confirm rows show `weight × reps` only, no trailing dash or badge.
5. Confirm no colored badge appears on the right side of any set row.
6. Verify RPE colors match the previous badge colors (green ≈ ≤7, yellow ≈ 7.5–8, orange ≈ 8.5–9, red ≈ 9.5+).
