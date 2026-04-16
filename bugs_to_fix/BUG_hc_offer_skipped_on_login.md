# BUG: Existing users skip HC connect screen on login

## Status
[x] Fixed

## Description
When an existing user logs in (email/password or Google), `onSignedIn` in `PowerMeNavigation.kt:178`
navigates directly to `Screen.Workouts.route`. The Health Connect offer screen is only shown as step 1
of `ProfileSetupScreen` — which is only reached for new users via `onNeedsProfile`. Existing users who
have never connected Health Connect never see the offer.

Expected: all users should be routed through the HC connect offer on login if they have not yet
connected Health Connect.

## Steps to Reproduce
1. Create an account (or use an existing one that has not connected Health Connect)
2. Log out
3. Log back in (email/password or Google)
4. Observe: lands on Workouts tab immediately — no HC connect offer shown

## Assets
- Related spec: `HEALTH_CONNECT_SPEC.md`
- Related spec: `PROFILE_SETUP_SPEC.md`
- Navigation entry point: `PowerMeNavigation.kt:178` — `onSignedIn` lambda
- HC offer composable: `ProfileSetupScreen.kt:88` — `HcOfferStep`
- HC state: `HealthConnectManager.kt`, `AppSettingsDataStore.kt`

## Fix Notes
Added a `needsHcOffer` gate to `AuthViewModel.applyNewUserGate()`: when an existing user (dbUser != null) logs in, we check if HC is available, permissions are not yet granted, and the offer has not been previously dismissed. If all three are true, `needsHcOffer = true` is set instead of `isSignedIn = true`.

Added a new `hcOfferDismissed` DataStore flag (set to true on skip or successful connect, permanently suppresses the offer on future logins).

Created a standalone `HcOfferScreen` + `HcOfferViewModel` to handle the offer flow. Added `AUTH_HC_OFFER` route to the nav graph. `WelcomeScreen` now has an `onNeedsHcOffer` callback wired to navigate to this route.
