# Fix Summary: No setup countdown before timed exercise timer starts

## Root Cause
Two-part root cause:

1. **Original:** The `TimedSetRow` composable had no "get ready" phase — tapping ▶ immediately transitioned from `IDLE` to `RUNNING` with no preparation window. There was no `setupSeconds` concept anywhere in the data model or UI.

2. **Follow-up regression (QA-found):** After the initial fix, SETUP still didn't appear — it skipped straight to RUNNING. The cause was a Compose snapshot visibility race: the click handler wrote `setupRemaining = setupSeconds` in one snapshot scope, and the `LaunchedEffect(timerState)` coroutine read `setupRemaining` in a new scope after recomposition. When the effect read the stale initial value of 0, the `while (setupRemaining > 0)` loop exited immediately. Fix: moved `setupRemaining = setupSeconds` to be the first statement inside the `SETUP` branch of the `LaunchedEffect`, ensuring write and read are in the same coroutine context.

## Files Changed
| File | Change |
|------|--------|
| `app/…/data/AppSettingsDataStore.kt` | Added `timedSetSetupSeconds` flow (default 3), key, and setter (`coerceIn(0, 10)`) |
| `app/…/data/sync/FirestoreSyncManager.kt` | Added `timedSetSetupSeconds` to both push maps and both restore paths (4 touch points) |
| `app/…/ui/settings/SettingsViewModel.kt` | Added field to `SettingsUiState`, collector in `loadAppSettings()`, and `setTimedSetSetupSeconds()` method |
| `app/…/ui/settings/SettingsScreen.kt` | Added `[-] [N] [+]` stepper row inside Rest Timer card; shows "Off" at 0, amber color when active |
| `app/…/ui/workout/WorkoutViewModel.kt` | Added `timedSetSetupSeconds: StateFlow<Int>` + `setupCountdownTickFeedback()` |
| `app/…/ui/workout/ActiveWorkoutScreen.kt` | Added `SETUP` to enum; threaded `setupSeconds`/`onSetupCountdownTick` through 4 composable levels; added SETUP LaunchedEffect and UI (amber "Get Ready" + countdown + cancel + progress bar) |
| `app/…/ui/theme/Color.kt` | Added `val SetupAmber = Color(0xFFFFB74D)` |
| `app/…/test/…/WorkoutViewModelTest.kt` | Added `timedSetSetupSeconds` stub to `mockAppSettingsDataStore` |
| `app/…/test/…/SettingsViewModelHealthConnectTest.kt` | Added `timedSetSetupSeconds` stub to `mockAppSettingsDataStore` |

## Surfaces Fixed
- Active workout timed set row: tapping ▶ now shows an amber "Get Ready" countdown (default 3 seconds) before the main exercise timer begins
- Settings screen Rest Timer card: new stepper to configure 0–10 second Get Ready countdown
- When set to 0 (Off), behavior is identical to before (no regression)

## How to QA
1. Open the app → Settings → scroll to "Rest Timer" card
2. Verify a new "Get Ready countdown" row appears with `[-]` `3s` `[+]` (default 3s, amber text)
3. Tap `[-]` to decrement to 0 — value shows "Off" in grey and subtitle reads "Off — timed sets start immediately"
4. Set it back to 3 via `[+]`
5. Navigate to a workout with a timed exercise (e.g. Plank, 30s)
6. Tap ▶ — should NOT start the main timer immediately; instead shows amber "Get Ready" countdown with 3… 2… 1… (with beep+haptic each second if audio/haptics enabled)
7. After countdown reaches 0 the main green exercise timer starts automatically
8. Repeat: tap ▶, then tap the X (cancel) during "Get Ready" → should return to IDLE state with original time
9. Set countdown to 0 in Settings, tap ▶ again → timer starts immediately (no regression)
