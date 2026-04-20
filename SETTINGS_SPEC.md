# Settings Screen — Spec

## Overview

The Settings screen is a single scrollable `LazyColumn` of `SettingsCard` composables. It manages app preferences, Health Connect, cloud sync, and account lifecycle.

**Note:** Personal Info (name, DOB, gender, etc.) and Body Metrics (weight, height, body fat) have been moved to `ProfileScreen` (`ui/profile/ProfileScreen.kt`) as part of the Profile/Settings split (P2). See `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md §1`.

**Route:** Launched via `onNavigateToSettings` from the main scaffold (no dedicated route constant).  
**ViewModel:** `ui/settings/SettingsViewModel.kt`  
**Screen:** `ui/settings/SettingsScreen.kt`

---

## 1. Card Order

| # | Card Title | Purpose |
|---|---|---|
| 1 | Appearance | Theme mode |
| 2 | Units | Metric / Imperial |
| 3 | Health Connect | HC permissions + sync |
| 4 | Rest Timer | Audio / haptics toggles + Get Ready countdown (0–10s) |
| 5 | Display & Workout | Keep screen on, Use RPE (auto-pop picker) |
| 6 | AI | Gemini API key management (user override + status) |
| 7 | Data Export | Export DB to JSON |
| 8 | Cloud Sync | Restore from Firestore |
| 9 | Privacy | Delete account |

**Moved to ProfileScreen:** Personal Info (card 4) and Body Metrics (card 5) were removed from Settings and now live in `ProfileScreen`.

---

## 2. Card Details

### 2.1 Appearance
`SingleChoiceSegmentedButtonRow` with 3 options: Light / Dark / System → `viewModel.setThemeMode(ThemeMode)` → persists to `AppSettingsDataStore`.

### 2.2 Units
`SingleChoiceSegmentedButtonRow` with 2 options: "Metric (kg, cm)" / "Imperial (lbs, ft)" → `viewModel.setUnitSystem(UnitSystem)` → persists to `AppSettingsDataStore`. All subsequent unit-aware fields in this screen react to the stored `UnitSystem`.

### 2.3 Health Connect

**States:**
- `healthConnectChecking = true`: blank (avoids false "not available" flash)
- `!healthConnectAvailable`: informational text + Play Store prompt
- `!healthConnectPermissionsGranted + !permissionsDenied`: "Connect" button → `PermissionController.createRequestPermissionResultContract()`
- `!healthConnectPermissionsGranted + permissionsDenied`: retry/open HC Settings buttons
- `healthConnectPermissionsGranted`: connected status, `HealthMetricsSummary` grid, "Sync Now" / spinner

`HealthMetricsSummary` shows 7 metrics in a 3-row grid: Sleep, HRV | Resting HR, Steps | Weight, Body Fat, Height.

**Re-check on resume:** `DisposableEffect(lifecycleOwner)` catches `ON_RESUME` → `viewModel.recheckHealthConnectPermissions()` — auto-syncs if permissions were granted in the OS settings app.

**Permission result:** `onHealthConnectPermissionResult(granted)` re-queries actual state in case the dialog was skipped (permissions already granted at OS level — `getSynchronousResult` path).

### 2.4 Rest Timer

Two `Switch` rows: "Audio" and "Haptics". Changes persisted to `userSettingsDao.updateRestTimerAudio/Haptics()`.

**Get Ready countdown** stepper row: label "Get Ready countdown" + `[-] [N] [+]` buttons (range 0–10s). Shows "Off" + grey text when 0; shows amber `Ns` value and subtitle "Timed sets wait Ns before starting" otherwise. Calls `viewModel.setTimedSetSetupSeconds()` → `AppSettingsDataStore.setTimedSetSetupSeconds()` → syncs via `FirestoreSyncManager.pushAppPreferences()`. Default 3s. Controls the `SETUP` state in `TimedSetRow` (see WORKOUT_SPEC.md §4.8).

### 2.5 Display & Workout

"Keep screen on" `Switch` → `viewModel.toggleKeepScreenOn()` → `AppSettingsDataStore.setKeepScreenOn()`.

"Use RPE" `Switch` → `viewModel.toggleUseRpeAutoPop()` → `AppSettingsDataStore.setUseRpeAutoPop()`. When enabled, `WorkoutViewModel` emits a one-shot `rpeAutoPopTarget` signal after each set completion, which `ActiveWorkoutScreen` consumes to auto-open `RpePickerSheet` for that set.

### 2.6 AI

Status line showing one of: "Using: Your key" / "Using: Default" / "No key set" (driven by `apiKeyStatus: ApiKeyStatus`).

`OutlinedTextField` for the user's Gemini API key: `PasswordVisualTransformation` with eye-toggle icon, single-line, Password keyboard type. Draft held in `userApiKeyInput: String` (never persisted until Save).

Row: "Save" button (enabled when draft non-blank), "Clear" button (enabled when `hasUserApiKey`).

Helper text: "Stored only on this device."

Key stored in `EncryptedSharedPreferences` file `"secure_ai_prefs"` via `SecurePreferencesStore`. **Never synced to Firestore** — device-local only.

ViewModel: `updateApiKeyInput(String)`, `saveUserApiKey()`, `clearUserApiKey()`.

### 2.7 Data Export

"Export Database to JSON" button → `viewModel.exportDatabase()` → `DatabaseExporter` writes a JSON file. Shows success path (file path) or error text. `isExporting` shows a spinner.

### 2.8 Cloud Sync

If not signed in: informational text. If signed in: "Restore from Cloud" button → `viewModel.restoreFromCloud()` → `FirestoreSyncManager.pullFromCloud()`. Result shown via `Toast`.

### 2.9 Privacy

"Delete Account" button → confirmation `AlertDialog` → `viewModel.deleteAccount()`:
1. `database.clearAllTables()`
2. `securePreferencesStore.clearUserGeminiApiKey()` — clears user Gemini key from EncryptedSharedPreferences
3. `Firebase.auth.currentUser?.delete()`
4. `appSettingsDataStore.setLanguage("Hebrew")`

---

## 3. ViewModel State (`SettingsUiState`)

### App Settings
| Field | Type | Purpose |
|---|---|---|
| `themeMode` | `ThemeMode` | Current theme selection |
| `unitSystem` | `UnitSystem` | Current unit system |
| `keepScreenOn` | `Boolean` | Workout display lock |
| `useRpeAutoPop` | `Boolean` | Auto-open RPE picker after set completion |

**Note:** Personal Info and Body Metrics state have been moved to `ProfileUiState` in `ProfileViewModel`. See `ui/profile/ProfileViewModel.kt`.

### AI Key
| Field | Type | Purpose |
|---|---|---|
| `hasUserApiKey` | `Boolean` | Whether a user key is stored in EncryptedSharedPreferences |
| `userApiKeyInput` | `String` | Transient draft — never persisted until `saveUserApiKey()` |
| `apiKeyStatus` | `ApiKeyStatus` | `UsingUser` / `UsingDefault` / `NoKey` |

### Health Connect
| Field | Type | Purpose |
|---|---|---|
| `healthConnectChecking` | `Boolean` | Initial status check in flight |
| `healthConnectAvailable` | `Boolean` | HC installed on device |
| `healthConnectPermissionsGranted` | `Boolean` | All permissions granted |
| `healthConnectPermissionsDenied` | `Boolean` | User denied in dialog |
| `healthConnectSyncing` | `Boolean` | Sync in progress |
| `healthConnectData` | `HealthConnectReadResult?` | Latest sync result |
| `healthConnectError` | `String?` | Sync error message |

### Cloud / Account
| Field | Type | Purpose |
|---|---|---|
| `isSignedIn` | `Boolean` | Firebase user present |
| `isRestoringFromCloud` / `cloudRestoreMessage` | `Boolean` / `String?` | Cloud restore state |
| `showDeleteAccountDialog` / `isDeletingAccount` | `Boolean` | Account deletion flow |

---

## 4. Shared Composables

`SettingsCard(title, content)` — `Card` with `PowerMeDefaults.cardColors()` + subtle elevation + title in primary color.

`HealthMetricsSummary(data, unitSystem)` — 3-row grid of `MetricCell` composables showing the last HC sync data.

**Moved to ProfileScreen:** `PersonalInfoCard` and Body Metrics UI now live in `ui/profile/ProfileScreen.kt`.
