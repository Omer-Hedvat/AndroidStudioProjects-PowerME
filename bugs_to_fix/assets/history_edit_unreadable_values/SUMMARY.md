# Fix Summary — History Edit Mode Unreadable Weight/Reps Fields

## Root Cause

`WorkoutDetailScreen.kt` — `BasicEditField` composable used `OutlinedTextField` without explicit text color overrides. The enclosing `Box` applied `primaryContainer` (`#2D2052` in dark mode) as the edit-mode background, but text color defaulted to the ambient `LocalContentColor` which did not contrast against that background. The result: invisible text/dashes in weight and reps fields.

## Change

**File:** `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` — line 554

Added two lines to `OutlinedTextFieldDefaults.colors()` inside `BasicEditField`:

```kotlin
focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
```

`onPrimaryContainer` = `#E0D4F0` (muted lavender, dark theme) — strong contrast against `primaryContainer` = `#2D2052`. Semantically correct in light theme too.

## Scope

Single composable, zero logic change, zero test impact.
