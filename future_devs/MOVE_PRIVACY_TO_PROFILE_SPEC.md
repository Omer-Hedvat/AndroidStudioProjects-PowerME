# Move Privacy Card to Profile Screen

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | settings_page_reorder |
| **Touches** | `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `ui/profile/ProfileScreen.kt`, `ui/profile/ProfileViewModel.kt`, `SETTINGS_SPEC.md` |

> Read `SETTINGS_SPEC.md §2.9` and `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md` before touching any file.

---

## Overview

"Delete Account" (the Privacy card) currently sits at the bottom of the Settings screen. Account lifecycle actions belong logically in the Profile screen alongside sign-out and personal info. This task moves the card, leaving Settings focused on app preferences and data/integrations only.

---

## Behaviour

- Remove the Privacy `SettingsCard` from `SettingsScreen.kt`.
- Add a "Delete Account" section at the bottom of `ProfileScreen` — below the existing account info and after any sign-out action.
- The deletion flow is unchanged: confirmation `AlertDialog` → `viewModel.deleteAccount()` → clears all tables, clears Gemini key, deletes Firebase user, sets language.
- All deletion state (`showDeleteAccountDialog`, `isDeletingAccount`) moves from `SettingsViewModel` / `SettingsUiState` to `ProfileViewModel` / `ProfileUiState`.

### Profile screen placement

Add at the very bottom of `ProfileScreen`, separated by a `Spacer(16dp)` and a `HorizontalDivider`:

```
─────────────────────────────────
Danger Zone
[Delete Account]   (outlined button, ErrorContainer color scheme)
```

Use `MaterialTheme.colorScheme.error` / `onError` / `errorContainer` tokens for the destructive button — no hardcoded colors.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — remove Privacy `item { }` block
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — remove `showDeleteAccountDialog`, `isDeletingAccount`, `deleteAccount()`, `confirmDeleteAccount()`, `dismissDeleteAccountDialog()` from Settings; remove corresponding `SettingsUiState` fields
- `app/src/main/java/com/powerme/app/ui/profile/ProfileScreen.kt` — add "Danger Zone" section with Delete Account button + `AlertDialog`
- `app/src/main/java/com/powerme/app/ui/profile/ProfileViewModel.kt` — add `showDeleteAccountDialog`, `isDeletingAccount`, `deleteAccount()` (same logic as before)
- `SETTINGS_SPEC.md` — remove §2.9 Privacy; remove Privacy from §1 Card Order table; update §3 ViewModel State to remove deletion fields

---

## How to QA

1. Open Settings → scroll to bottom → verify Privacy / Delete Account card is gone.
2. Open Profile → scroll to bottom → verify "Danger Zone" section with "Delete Account" button appears.
3. Tap "Delete Account" → verify confirmation dialog appears.
4. Cancel → verify nothing happens.
5. (Do not confirm — destructive action. Verify UI only.)
