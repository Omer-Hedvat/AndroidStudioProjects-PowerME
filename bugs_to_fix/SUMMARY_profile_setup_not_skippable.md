# Fix Summary: Profile Setup screen cannot be skipped

## Root Cause

The `ProfileSetupScreen` offered only one forward path: tapping "Get Started" to save the full profile form. There was no bypass for users who landed on the screen unexpectedly (e.g. via the logout/re-login regression). Simply navigating away without saving would have caused an infinite loop because `AppStartupViewModel` routes to profile setup whenever `userSessionManager.getCurrentUser()` returns null.

## Files Changed

| File | Change |
|---|---|
| `ui/auth/ProfileSetupViewModel.kt` | Added `skipProfileSetup()` — saves a minimal `User(email=...)` then sets `profileSaved = true` |
| `ui/auth/ProfileSetupScreen.kt` | Added `onSkip` param to `ProfileFormStep`; added "Skip for now" `TextButton` below "Get Started" |
| `PROFILE_SETUP_SPEC.md` | Documented §4.5 Skip Behavior and added edge case row |
| `ui/auth/ProfileSetupViewModelTest.kt` | Added 2 new tests for `skipProfileSetup()` (happy path + no signed-in user) |

## Surfaces Fixed

- Profile Setup screen now has a low-prominence "Skip for now" `TextButton` below "Get Started"
- Tapping it saves a minimal User row (email only) so the startup loop check passes, then navigates to the Workouts tab

## How to QA

1. Sign out of the app
2. Sign back in with a valid account — if the account has no Room data (or use the logout regression path), you'll be taken to Profile Setup
3. On Step 1 (Health Connect offer), tap "Skip" — should advance to Step 2
4. On Step 2 (profile form), tap **"Skip for now"** — should navigate directly to the Workouts tab
5. Force-close and reopen the app — should land on Workouts tab, NOT loop back to Profile Setup
6. (Optional) Navigate to Profile from settings — fields should be empty/default, ready to fill in later
