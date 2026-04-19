# BUG: Post-workout notes field has no Save button

## Status
[x] Fixed

## Description
The WorkoutSummaryScreen includes a notes text field for the user to add post-workout notes, but there is no explicit "Save Note" button or save action. The user can type a note but has no clear way to persist it. The note may silently discard on navigation, or it may auto-save but give no feedback — either way the UX is broken because the user has no confidence their note was saved. A visible "Save Note" button should appear (enabled only when the note text differs from the stored value) and persist the note to the workout entity in the database.

Affected screen: `WorkoutSummaryScreen.kt` — notes section.

## Severity
P1

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModel.kt`

## Steps to Reproduce
1. Complete a workout (or open a past workout from History)
2. Scroll to the notes field at the bottom of the WorkoutSummaryScreen
3. Type a note
4. Observe: no Save button or save confirmation — unclear if the note will persist
5. Navigate away and return — note may be lost

## Assets
- Related spec: `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md`

## Fix Notes
Removed the invisible 800ms `LaunchedEffect` debounce auto-save. Replaced with an explicit "Save Note" `TextButton` that appears (via `AnimatedVisibility`) only when `notesText` differs from the stored `workout.notes`. Tapping it calls `viewModel.updateNotes()`, which persists to Room and pushes to Firestore; once the ViewModel emits the updated `workout.notes`, the `remember` key resets `notesText` and the button hides — serving as implicit confirmation. Added a `DisposableEffect` safety net to flush any unsaved text if the user navigates away before tapping Save.
