# BUG: Exercise joint indicators — full revision needed

## Status
~~Superseded~~ — absorbed into `future_devs/EXERCISE_DETAIL_SHEET_REVISION_SPEC.md`

## Severity
P2 normal
- Three distinct QA failures in the joint indicators implementation inside ExerciseDetailSheet.

## Description
The exercise joint indicators feature shipped but failed QA on three counts:

1. **No primary/secondary distinction** — the UI shows a single "Joint Involvement" label with chips in one undifferentiated group. The spec requires two clearly separated tiers: "Primary" (filled `PrimaryContainer` chips) and "Secondary" (outlined chips), each with their own label.

2. **Chips look interactive but do nothing** — joint chips are rendered with button-like styling, leading users to tap them expecting a response. The spec states chips are display-only (non-interactive). They should not use clickable chip variants and must not show a ripple or pointer cursor.

3. **Health-history warning tint is wrong** — if a joint matches a user's health history entry, the chip should show an `ErrorContainer` (red) background. The current implementation uses a subtle amber tint that is not clearly a warning.

Affected screen: Exercises tab → tap any exercise → ExerciseDetailSheet.

## Steps to Reproduce
1. Open Exercises tab → tap any compound exercise (e.g. Back Squat)
2. Observe joint row → shows "Joint Involvement" with no primary/secondary split → **Issue 1**
3. Tap any joint chip → nothing happens but chip looks tappable → **Issue 2**
4. Add a knee injury to Health History → reopen Back Squat → Knee chip tint is not clearly red → **Issue 3**

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt`, `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt`

## Assets
- Related spec: `future_devs/EXERCISE_JOINTS_SPEC.md`

## Implementation Notes
- Use **Opus + plan mode** for this fix (multi-section UI revision)
- Primary chips: filled `SuggestionChip` or `AssistChip` with `chipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)`; non-clickable
- Secondary chips: outlined `SuggestionChip` with `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)`; non-clickable
- Health-history warning chip: `containerColor = MaterialTheme.colorScheme.errorContainer`, `labelColor = MaterialTheme.colorScheme.onErrorContainer`
- Row layout: label "Primary" → primary chips, then gap/dot separator, label "Secondary" → secondary chips; if a tier is empty, omit its label and chips entirely

## Fix Notes
<!-- populated after fix is applied -->
