# BUG: Logout wipes local profile data and re-login doesn't restore it

## Status
[x] Fixed

## Severity
P0 blocker
- Data loss: fitness level, health history, body metrics, and personal info are all missing after logout + re-login. Core user identity is lost.

## Description
After logging out (via the new logout button on Profile) and signing back in with Google, the app:
1. Routes to Profile Setup instead of the main app (wrong navigation)
2. Shows all profile data as empty — fitness level, health history, body metrics, and personal info are gone

Both issues stem from the same logout → re-login flow being broken. Likely causes:
- Logout clears `AppSettingsDataStore` (including `hasCompletedSetup`) and possibly local Room data
- On re-login, Firestore pull either doesn't trigger or doesn't re-populate local state before the UI loads
- `AuthViewModel` routes based on the now-cleared local flag without checking Firestore for an existing profile

The fix must ensure: (a) routing correctly skips Profile Setup for existing accounts, and (b) all profile data (fitness level, health history, body metrics, personal info) is fully restored from Firestore after re-login.

## Steps to Reproduce
1. Sign in with Google, complete full profile setup (fitness level, body metrics, personal info, health history entries)
2. Navigate to Profile, tap **Log Out** → confirm
3. Sign in again with the same Google account
4. Observe: routed to Profile Setup (wrong); after bypassing, all profile data is empty

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `AuthViewModel.kt`, `AppSettingsDataStore.kt`, `PowerMeNavigation.kt`, `FirestoreSyncManager.kt`

## Assets
- Related spec: `PROFILE_SETUP_SPEC.md`, `NAVIGATION_SPEC.md`

## Fix Notes
**Root cause:** `UserSessionManager.clearUser()` deleted the Room `users` row on logout but never reset `hasRestoredOnce` in `AppSettingsDataStore`. On re-login, `AuthViewModel.applyNewUserGate()` saw `hasRestoredOnce = true` and skipped both `pullProfileOnly()` (restores the User entity from Firestore) and `launchBackgroundSync()`. With the Room row gone and no Firestore pull, `getCurrentUser()` returned null → routed to Profile Setup with all data empty.

**Fix (two-layer):**
1. `UserSessionManager.clearUser()` now injects `AppSettingsDataStore` and calls `setHasRestoredOnce(false)` before signing out — ensures the full restore path runs on next sign-in.
2. `AuthViewModel.applyNewUserGate()` now has a defensive fallback: if `dbUser == null && alreadyRestored` (edge case where the flag reset failed), it calls `pullProfileOnly()` + `launchBackgroundSync()` and re-checks before routing.
