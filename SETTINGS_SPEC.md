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
| 3 | Workout Style | Pure Gym / Pure Functional / Hybrid (with ℹ info sheet) + RPE prompts selector |
| 4 | Health Connect | HC permissions + sync |
| 5 | Rest Timer | Audio / haptics toggles + Get Ready countdown (0–10s) |
| 6 | Display | Keep screen on |
| 7 | AI | Gemini API key management (user override + status + Connected chip) |
| 8 | Data & Backup | Export JSON + Import History + (signed-in) Back Up Now + Restore |
| 9 | Feedback | Email feedback |

**Moved to ProfileScreen:** Personal Info and Body Metrics were removed from Settings. Privacy / Delete Account has been moved to `ProfileScreen` → Danger Zone section (bottom of profile).

**Note:** The "Workout Style" card is currently listed between Units and Health Connect but order may change after the settings-page-reorder task.

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

**Timer sound** `ExposedDropdownMenuBox` row: alert tone selection for rest timers and clocks. `viewModel.setTimerSound(TimerSound)` → `AppSettingsDataStore.setTimerSound()`.

### 2.5 Workout Style

`SingleChoiceSegmentedButtonRow` with 3 options: Pure Gym / Pure Functional / Hybrid → `viewModel.setWorkoutStyle(WorkoutStyle)` → persists to `AppSettingsDataStore`.

**Info sheet:** An `IconButton(Icons.Outlined.Info)` appears to the right of the "Workout Style" card title. Tapping it opens a `ModalBottomSheet` (`WorkoutStyleInfoSheet`) with three rows explaining each style (Pure Gym / Hybrid / Pure Functional), separated by `HorizontalDivider`. Sheet state is `showWorkoutStyleInfoSheet: Boolean` in `SettingsUiState`, toggled by `showWorkoutStyleInfo()` / `dismissWorkoutStyleInfo()` in `SettingsViewModel`.

**RPE prompts selector:** Below a `HorizontalDivider`, a `RadioButton` group labelled "RPE prompts" lets the user pick `RpeMode`: Strength only / Functional only / All workouts / Off → `viewModel.setRpeMode(RpeMode)` → `AppSettingsDataStore.setRpeMode()`. Co-located here because the options mirror workout style choices.

### 2.6 Display

"Keep screen on" `KeepScreenOnMode` segmented row (Always / During workout / Off) → `viewModel.setKeepScreenOnMode()` → `AppSettingsDataStore.setKeepScreenOnMode()`.

### 2.7 AI

Status line showing one of: "Using: Your key" / "Using: Default" / "No key set" (driven by `apiKeyStatus: ApiKeyStatus`).

**Connected chip:** When `apiKeyValidation` is `ApiKeyValidationState.Valid`, a green `AssistChip` with checkmark icon appears below the status line (above the text field), using `primaryContainer` / `onPrimaryContainer` color tokens.

`OutlinedTextField` for the user's Gemini API key: `PasswordVisualTransformation` with eye-toggle icon, single-line, Password keyboard type. Draft held in `userApiKeyInput: String` (never persisted until Save).

Row: "Save" button (enabled when draft non-blank), "Clear" button (enabled when `hasUserApiKey`).

Helper text: "Stored only on this device."

Key stored in `EncryptedSharedPreferences` file `"secure_ai_prefs"` via `SecurePreferencesStore`. **Never synced to Firestore** — device-local only.

ViewModel: `updateApiKeyInput(String)`, `saveUserApiKey()`, `clearUserApiKey()`.

### 2.8 Data & Backup

Single `SettingsCard` replacing the former separate Data Export (§2.7) and Cloud Sync (§2.8) cards. Rows:

1. **Export to JSON** (always visible) — `OutlinedButton` → `viewModel.exportDatabase()`. `isExporting` shows spinner. Success/error text below.
2. **Import Workout History** (always visible) — `OutlinedButton` → `onNavigateToImport`.
3. **Back Up Now** (signed-in only) — `OutlinedButton` → `viewModel.backupToCloud()`. `isBackingUpToCloud` spinner. `backupMessage` feedback below.
4. **Restore from Cloud** (signed-in only) — `OutlinedButton` → `viewModel.restoreFromCloud()`. `isRestoringFromCloud` spinner. `cloudRestoreMessage` shown via `LaunchedEffect` toast.

Rows 1–2 always shown. Rows 3–4 hidden when `!isSignedIn`. All rows separated by `HorizontalDivider`.

### 2.9 Privacy / Danger Zone → Moved to ProfileScreen

The Privacy card has been removed from Settings. "Delete Account" now lives in `ProfileScreen` at the very bottom, below the Log Out button, in a "Danger Zone" section separated by a `HorizontalDivider`. Uses `OutlinedButton` with `error` color tokens. Confirmation `AlertDialog` → `viewModel.deleteAccount()`:
1. `database.clearAllTables()`
2. `securePreferencesStore.clearUserGeminiApiKey()`
3. `Firebase.auth.currentUser?.delete()`
4. `appSettingsDataStore.setLanguage("Hebrew")`

State: `showDeleteAccountDialog`, `isDeletingAccount` in `ProfileUiState` / `ProfileViewModel`.

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

### Workout Style Info Sheet
| Field | Type | Purpose |
|---|---|---|
| `showWorkoutStyleInfoSheet` | `Boolean` | Controls `WorkoutStyleInfoSheet` visibility |

### Cloud / Account
| Field | Type | Purpose |
|---|---|---|
| `isSignedIn` | `Boolean` | Firebase user present |
| `isRestoringFromCloud` / `cloudRestoreMessage` | `Boolean` / `String?` | Cloud restore state |
| `isBackingUpToCloud` / `backupMessage` | `Boolean` / `String?` | Cloud backup state |

**Removed:** `showDeleteAccountDialog` / `isDeletingAccount` moved to `ProfileUiState`.

---

## 4. Shared Composables

`SettingsCard(title, content)` — `Card` with `PowerMeDefaults.cardColors()` + subtle elevation + title in primary color.

`HealthMetricsSummary(data, unitSystem)` — 3-row grid of `MetricCell` composables showing the last HC sync data.

**Moved to ProfileScreen:** `PersonalInfoCard` and Body Metrics UI now live in `ui/profile/ProfileScreen.kt`.
