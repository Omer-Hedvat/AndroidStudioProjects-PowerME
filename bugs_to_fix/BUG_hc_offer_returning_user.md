# BUG: Returning users skip Health Connect offer on Google sign-in

## Status
[ ] Open

## Description
An already-registered user who signs in via Google (or any provider) is never shown the Health Connect offer screen. The gate in `AuthViewModel.applyNewUserGate()` sets `needsProfileSetup = dbUser == null`, which is `false` for any returning user — so they skip straight to Workouts without ever being offered the HC integration.

HC permissions are never granted for returning users unless they manually navigate to Settings → Health Connect.

## Steps to Reproduce
1. Create an account and complete profile setup (skip or deny HC during onboarding)
2. Sign out
3. Sign back in via Google
4. **Expected:** HC offer screen shown (if HC available and not yet granted)
5. **Actual:** Goes straight to Workouts tab

## Root Cause
`AuthViewModel.kt` line ~141:
```kotlin
needsProfileSetup = dbUser == null
```
`HealthConnectManager` is not injected into `AuthViewModel`, so there is no HC check at the sign-in gate.

## Fix Notes
