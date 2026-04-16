# Fix Summary: Workout detail has two redundant edit modes

## Root Cause
`WorkoutDetailScreen` was designed as a dual-purpose read/edit screen but is only ever reached via the edit pen icon on `WorkoutSummaryScreen`. It contained a `LaunchedEffect` that auto-entered edit mode after load, but the back arrow called `cancelEditMode()` (dropping to a useless read-only state) rather than navigating away. The read-only state then exposed an overflow menu with a redundant "Edit Session" item that re-entered the same edit mode.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailViewModel.kt` | Moved edit init into `load()` (always starts in edit mode). Removed `startEditMode()` and `cancelEditMode()`. Added `hasUnsavedChanges()`. Fixed `saveEdits()` to stay in edit mode after save. |
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` | Removed `LaunchedEffect` auto-edit hack. Added `BackHandler` + "Discard Changes?" dialog. Fixed back arrow to navigate away (with discard prompt if needed). Removed overflow menu / "Edit Session". Promoted Delete to toolbar icon. |
| `app/src/test/java/com/powerme/app/ui/history/WorkoutDetailViewModelTest.kt` | New test file: 5 tests covering `hasUnsavedChanges()`, `isEditMode` init, and `pendingEdits` population. |

## Surfaces Fixed
- WorkoutDetailScreen — no longer has a confusing read-only mode; always loads as an edit view
- Back navigation now works naturally: navigates back immediately if no changes, shows discard dialog if edits were made
- Delete is now always visible as a toolbar icon rather than buried in an overflow menu

## How to QA
1. Go to **History** tab → tap any past workout → `WorkoutSummaryScreen` opens.
2. Tap the **pen/edit icon** (top-right) → `WorkoutDetailScreen` opens directly in edit mode (weight/rep fields are editable immediately — no secondary "Edit" button or loading delay).
3. Tap **back arrow** without making changes → navigates back to `WorkoutSummaryScreen` without any dialog.
4. Make a change (e.g. edit a weight or reps value) → tap **back arrow** → "Discard Changes?" dialog appears → tap **Keep Editing** → stays on screen with edits intact.
5. Tap back again → dialog → tap **Discard** → navigates back to `WorkoutSummaryScreen`.
6. Enter edit mode again, make a change → tap **Save** → changes persist, screen stays in edit mode showing updated values.
7. Tap the **delete (trash) icon** in the toolbar → "Delete Session?" dialog appears → confirm → navigates back.
8. Verify there is **no overflow menu** (no three-dot menu) and **no "Edit Session" button** anywhere on the screen.
