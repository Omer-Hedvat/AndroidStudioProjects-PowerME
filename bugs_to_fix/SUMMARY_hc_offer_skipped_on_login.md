# Fix Summary: Existing users skip HC connect screen on login

## Root Cause

`AuthViewModel.applyNewUserGate()` set `isSignedIn = true` unconditionally for existing users (dbUser != null), bypassing the HC offer entirely. The HC offer screen (`HcOfferStep`) only existed as step 1 of `ProfileSetupScreen`, which is only reached for new users via `needsProfileSetup`.

## Files Changed

| File | Change |
|---|---|
| `app/.../data/AppSettingsDataStore.kt` | Added `hcOfferDismissed` DataStore flag + getter + setter |
| `app/.../ui/auth/AuthViewModel.kt` | Injected `HealthConnectManager`; added `needsHcOffer` to `AuthUiState`; gated on HC availability, permission status, and dismissed flag in `applyNewUserGate()` |
| `app/.../ui/auth/WelcomeScreen.kt` | Added `onNeedsHcOffer` callback param + `LaunchedEffect` |
| `app/.../navigation/PowerMeNavigation.kt` | Added `AUTH_HC_OFFER` route, wired `onNeedsHcOffer`, registered `HcOfferScreen` composable |
| `app/.../ui/auth/HcOfferScreen.kt` (**new**) | Standalone HC offer screen composable |
| `app/.../ui/auth/HcOfferViewModel.kt` (**new**) | ViewModel: checks HC availability, handles permission result, writes `hcOfferDismissed` on skip/connect |
| `app/src/test/.../AuthViewModelGoogleSignInTest.kt` | Injected mock `HealthConnectManager`; added tests 11–13 for `needsHcOffer` paths |
| `app/src/test/.../HcOfferViewModelTest.kt` (**new**) | 5 unit tests covering all `HcOfferViewModel` paths |

## Surfaces Fixed

- Existing users who have never connected Health Connect now see the HC offer screen after logging in
- The offer is permanently dismissed after skip or successful connect (never shown again)
- Users who already have HC permissions connected, or who have previously dismissed the offer, are routed directly to Workouts as before

## How to QA

1. Use an existing account that has **not** connected Health Connect (or revoke HC permissions for PowerME in device settings)
2. Log out of the app
3. Log back in (email/password or Google Sign-In)
4. **Expected:** HC connect offer screen appears before landing on Workouts
5. Tap **Skip**
6. **Expected:** Lands on Workouts tab; offer is never shown again on future logins
7. (Optional) Log out and log back in again — **Expected:** goes directly to Workouts (offer dismissed)
8. (Optional) Connect HC in Settings, log out, log back in — **Expected:** goes directly to Workouts (permissions already granted)
