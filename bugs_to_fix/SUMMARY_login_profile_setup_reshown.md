# Fix Summary: Logout wipes local profile data and re-login doesn't restore it

## Root Cause

`UserSessionManager.clearUser()` deletes the Room `users` row and signs out of Firebase, but never reset `hasRestoredOnce` in `AppSettingsDataStore`. On re-login, `AuthViewModel.applyNewUserGate()` found `hasRestoredOnce = true` and skipped both `pullProfileOnly()` and `launchBackgroundSync()`. With the Room row gone and no Firestore pull, `getCurrentUser()` returned `null` → routed to Profile Setup with all profile data empty (fitness level, training age, body metrics, personal info).

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/util/UserSessionManager.kt` | Inject `AppSettingsDataStore`; call `setHasRestoredOnce(false)` in `clearUser()` before sign-out |
| `app/src/main/java/com/powerme/app/ui/auth/AuthViewModel.kt` | Defensive fallback in `applyNewUserGate()`: if `dbUser == null && alreadyRestored`, call `pullProfileOnly()` + `launchBackgroundSync()` and re-check before routing |
| `app/src/test/java/com/powerme/app/ui/auth/AuthViewModelGoogleSignInTest.kt` | Added Tests 14 and 15 covering the re-login-after-logout scenario (with and without Firestore profile) |

## Surfaces Fixed

- **Profile Setup routing**: Re-login after logout now correctly routes to the main app (or HC offer) instead of Profile Setup.
- **Profile data restore**: All User entity fields (fitness level, training age, body metrics, personal info, chronotype, etc.) are re-fetched from Firestore via `pullProfileOnly()` on re-login — visible in the Profile screen immediately after sign-in.
- **Settings + workout data**: `launchBackgroundSync()` refreshes UserSettings, app preferences, workouts, and routines from Firestore in the background after re-login.

## How to QA

1. Sign in with Google and complete full Profile Setup (set fitness level, body metrics, personal info).
2. Navigate to Profile → tap **Log Out** → confirm.
3. On the Welcome screen, sign in again with the same Google account.
4. **Expected:** App routes to the main Workouts tab (or HC offer if HC available and not yet connected) — NOT to Profile Setup.
5. Navigate to Profile. **Expected:** All profile data is present — name, fitness level, training age, body metrics (weight/body fat/height), chronotype, occupation type.
