# BUG: Profile Setup screen cannot be skipped

## Status
[x] Fixed

## Severity
P1 high
- Blocks users who are re-shown Profile Setup (e.g. after logout regression) and cannot bypass it to access the app.

## Description
The Profile Setup screen has no Skip or Continue button. If a user is shown the setup screen and does not want to re-enter their information (e.g. they already have a profile), there is no way to proceed to the main app without completing the form.

Profile Setup should have a visible **Skip** or **Continue** option that allows the user to bypass setup and go directly to the main app.

## Steps to Reproduce
1. Reach the Profile Setup screen (e.g. via the logout → re-login regression)
2. Observe: no Skip button — setup form is mandatory with no escape

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ProfileSetupScreen.kt`, `ProfileSetupViewModel.kt`, `PowerMeNavigation.kt`

## Assets
- Related spec: `PROFILE_SETUP_SPEC.md`, `NAVIGATION_SPEC.md`

## Fix Notes
Added `skipProfileSetup()` to `ProfileSetupViewModel` — saves a minimal `User(email=...)` so `AppStartupViewModel`'s startup check passes, then sets `profileSaved = true` to trigger the existing `onProfileSaved()` navigation to Workouts. Added a low-prominence "Skip for now" `TextButton` below "Get Started" in `ProfileFormStep`. Button is disabled while saving to prevent races.
