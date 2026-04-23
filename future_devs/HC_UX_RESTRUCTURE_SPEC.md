# HC UX Restructure — Settings Connected Badge + Profile Metrics Card

| Field | Value |
|---|---|
| **Phase** | P2 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | Profile/Settings split ✅ |
| **Blocks** | — |
| **Touches** | `SettingsScreen.kt`, `SettingsViewModel.kt`, `ProfileScreen.kt`, `ProfileViewModel.kt`, `PowerMeNavigation.kt`, `SETTINGS_SPEC.md`, `HEALTH_CONNECT_SPEC.md` |

---

## Overview

Health Connect is currently connected in Settings, and all HC metrics (weight, body fat, height, etc.) are also displayed there. This mixes configuration with data display. The restructure moves the data display to Profile — where personal health data belongs — and leaves Settings as a pure connection-management surface.

---

## Behaviour

- **Settings — not connected:** existing Connect button/card; no change.
- **Settings — connected:** replace metric display with a "Connected" badge (e.g. a green checkmark + "Health Connect · Connected") and a single "View in Profile" button that navigates to the Profile tab.
- **Profile — connected:** HC metrics card (weight, body fat, height, last-synced timestamp) appears below existing profile cards. Hidden when HC is not connected.
- **Profile — not connected:** HC card is not shown (no empty state needed — the connect flow lives in Settings).
- Navigation: tapping "View in Profile" in Settings selects the Profile bottom-nav tab and scrolls to (or highlights) the HC card.

---

## UI Changes

- `SettingsScreen.kt` — HC card body: swap metrics rows for a `ConnectedBadge` row + `FilledTonalButton("View in Profile")`.
- `ProfileScreen.kt` — add `HcMetricsCard` composable below existing cards; shown only when `profileViewModel.hcConnected == true`.
- `ProfileViewModel.kt` — expose `hcConnected: StateFlow<Boolean>` and HC metric values already available in `SettingsViewModel` / `HealthConnectManager`.
- Navigation: `PowerMeNavigation.kt` or the Settings → Profile button handler calls `navController.navigate(ProfileRoute)` (tab-level navigation, no new back stack entry).
- Use `MaterialTheme.colorScheme.*` tokens throughout; no hardcoded colors.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — replace HC metrics rows with Connected badge + "View in Profile" button
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — expose HC connected state if not already public
- `app/src/main/java/com/powerme/app/ui/profile/ProfileScreen.kt` — add `HcMetricsCard`
- `app/src/main/java/com/powerme/app/ui/profile/ProfileViewModel.kt` — wire HC data + connected flag
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — handle navigate-to-Profile-tab callback from Settings
- `SETTINGS_SPEC.md` — update HC card spec
- `HEALTH_CONNECT_SPEC.md` — note that metrics display lives in Profile

---

## How to QA

1. Open Settings → HC card should show "Connected" badge and "View in Profile" button (not metric rows).
2. Tap "View in Profile" — app navigates to Profile tab.
3. On Profile, verify HC metrics card (weight, body fat, height, last synced) is visible.
4. Disconnect HC → return to Profile; HC card should disappear.
5. Re-connect via Settings → HC card reappears in Profile.
