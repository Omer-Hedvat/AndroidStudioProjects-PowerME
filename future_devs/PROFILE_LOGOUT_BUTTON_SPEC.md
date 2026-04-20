# Profile Page — Logout Button

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | Profile/Settings split ✅ |

---

## Overview

The Profile screen currently has no way to sign out. Users who want to log out must uninstall the app or dig into Settings. A prominent **Log Out** button in the Profile screen completes the account management story.

---

## Behaviour

- **Log Out button** appears at the bottom of `ProfileScreen`, below all profile cards.
- Tapping it shows a confirmation `AlertDialog` ("Log out?", "You'll need to sign in again." / **Cancel** + **Log Out**).
- On confirm: calls `AuthViewModel.signOut()` (or a delegated function in `ProfileViewModel`) → navigates to `WelcomeScreen` and clears the back stack.
- The existing `AuthViewModel.signOut()` already handles Firebase sign-out + Google credential revocation. Wire through `ProfileViewModel` or call `AuthViewModel` directly from the screen.
- No data is deleted locally — the user can sign back in and restore from Firestore.

---

## UI Changes

- **`ProfileScreen.kt`** — add a `Spacer(Modifier.height(24.dp))` then an `OutlinedButton` (or `TextButton`) labelled **"Log Out"** at the bottom of the `LazyColumn` content, full-width, using `MaterialTheme.colorScheme.error` for the text/border color to signal a destructive action.
- Confirmation `AlertDialog`: title "Log out?", body "You'll need to sign in again to access your data.", buttons Cancel + Log Out (error color).

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/profile/ProfileScreen.kt` — add Log Out button + AlertDialog
- `app/src/main/java/com/powerme/app/ui/profile/ProfileViewModel.kt` — add `signOut()` that delegates to `AuthViewModel` or calls Firebase Auth directly

---

## How to QA

1. Open the app and sign in
2. Navigate to Profile tab (person icon in top bar)
3. Scroll to the bottom — a red-tinted **Log Out** button should be visible
4. Tap it — a confirmation dialog should appear
5. Tap **Cancel** — dialog dismisses, user stays on Profile
6. Tap **Log Out** again → **Log Out** in dialog — app navigates to Welcome/sign-in screen with no back stack
7. Confirm the user cannot press Back to return to the app content
