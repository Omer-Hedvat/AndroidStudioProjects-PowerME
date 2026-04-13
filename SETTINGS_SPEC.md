# Settings Screen — Spec

## Overview

The Settings screen is a single scrollable `LazyColumn` of `SettingsCard` composables. It manages app preferences, user profile data, Health Connect, cloud sync, and account lifecycle.

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
| 4 | Personal Info | Name, DOB, sleep, children, gender, occupation, chronotype, training goals |
| 5 | Body Metrics | Weight, body fat, height — daily tracking inputs |
| 6 | Rest Timer | Audio / haptics toggles |
| 7 | Display & Workout | Keep screen on |
| 8 | Data Export | Export DB to JSON |
| 9 | Cloud Sync | Restore from Firestore |
| 10 | Privacy | Delete account |

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

### 2.4 Personal Info

Edits the non-body-metric fields of the `User` entity. Composable: `PersonalInfoCard`.

**Fields:**

| Field | Widget | Notes |
|---|---|---|
| Name | `ProfileTextField` | Text keyboard |
| Date of Birth | Read-only `OutlinedTextField` + `DatePickerDialog` | Year range 1920..(currentYear-5), format "MMM d, yyyy" |
| Avg Sleep (h) | `ProfileTextField` | Decimal keyboard |
| Children | `ProfileTextField` | Number keyboard |
| Gender | `SingleChoiceSegmented` | MALE/FEMALE/OTHER, toggleable (selecting active clears it) |
| Occupation | `SingleChoiceSegmented` | SEDENTARY/ACTIVE/PHYSICAL |
| Chronotype | `SingleChoiceSegmented` | MORNING/NEUTRAL/NIGHT |
| Training Goals | `MultiSelectChips` | 6 options from `TRAINING_TARGET_OPTIONS` |

**Save mechanism:** Single "Save Changes" button at bottom. Shows `CircularProgressIndicator` while saving. On success: button label changes to "Saved" with green checkmark icon for 2s, then auto-resets via `viewModel.dismissPersonalInfoSaveMessage()`.

**Save path:** `viewModel.savePersonalInfo()` → reads current User → `.copy(updatedFields)` → `userSessionManager.saveUser(updated)`. No `MetricLog` writes (these are not time-series fields).

**Load on init:** `viewModel.loadPersonalInfo()` reads `userSessionManager.getCurrentUser()` and populates all state fields. `trainingTargets` is split on `","`, trimmed, and converted to `Set<String>`.

**DatePickerDialog state:** hoisted at the `PersonalInfoCard` composable level (not inside LazyColumn items) to prevent dismissal on recomposition.

### 2.5 Body Metrics

Always shown (not gated by HC status) so users can manually override values even when HC is connected.

**Fields:** Weight (unit-aware label), Body Fat (%),  Height (single cm field in metric; feet + inches in imperial).

**Labels:** "Last: X kg / Y% / Z cm" summary line shows most-recently logged values.

**Save path:** `viewModel.saveBodyMetrics()` → validates via `SurgicalValidator` → writes to both `MetricLogRepository` (WEIGHT, BODY_FAT, HEIGHT) and `User` entity via `userSessionManager.saveUser()`. Always stores in metric; converts from display units at save time.

**State feeds:** `observeMetricLogs()` in init pre-populates `weightInput` / `bodyFatInput` from the latest MetricLog entries. `loadUserHeight()` pre-populates height from the User entity.

### 2.6 Rest Timer

Two `Switch` rows: "Audio" and "Haptics". Changes persisted to `userSettingsDao.updateRestTimerAudio/Haptics()`.

### 2.7 Display & Workout

"Keep screen on" `Switch` → `viewModel.toggleKeepScreenOn()` → `AppSettingsDataStore.setKeepScreenOn()`.

### 2.8 Data Export

"Export Database to JSON" button → `viewModel.exportDatabase()` → `DatabaseExporter` writes a JSON file. Shows success path (file path) or error text. `isExporting` shows a spinner.

### 2.9 Cloud Sync

If not signed in: informational text. If signed in: "Restore from Cloud" button → `viewModel.restoreFromCloud()` → `FirestoreSyncManager.pullFromCloud()`. Result shown via `Toast`.

### 2.10 Privacy

"Delete Account" button → confirmation `AlertDialog` → `viewModel.deleteAccount()`:
1. `database.clearAllTables()`
2. `Firebase.auth.currentUser?.delete()`
3. `securePreferencesManager.clearApiKey()`
4. `appSettingsDataStore.setLanguage("Hebrew")`

---

## 3. ViewModel State (`SettingsUiState`)

### App Settings
| Field | Type | Purpose |
|---|---|---|
| `themeMode` | `ThemeMode` | Current theme selection |
| `unitSystem` | `UnitSystem` | Current unit system |
| `keepScreenOn` | `Boolean` | Workout display lock |

### Body Metrics
| Field | Type | Purpose |
|---|---|---|
| `weightInput` / `bodyFatInput` / `heightInput` | `String` | Display-unit text field values |
| `heightFeetInput` / `heightInchesInput` | `String` | Imperial height fields |
| `lastWeight` / `lastBodyFat` / `lastHeight` | `Double?` / `Double?` / `Float?` | Latest logged values for summary text |
| `isSavingMetrics` | `Boolean` | Save in progress |

### Personal Info
| Field | Type | Purpose |
|---|---|---|
| `nameInput` | `String` | Editable name |
| `dateOfBirth` | `Long?` | Epoch ms |
| `averageSleepHoursInput` / `parentalLoadInput` | `String` | Text field values |
| `gender` / `occupationType` / `chronotype` | `String` | Chip selection (empty = unset) |
| `selectedTrainingTargets` | `Set<String>` | Multi-select chip state |
| `isSavingPersonalInfo` | `Boolean` | Save in progress |
| `personalInfoSaveMessage` | `String?` | "Saved" feedback, auto-dismissed after 2s |

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

`PersonalInfoCard(uiState, viewModel)` — self-contained composable that owns `showDatePicker` state and renders the Personal Info card using `ProfileTextField`, `SingleChoiceSegmented`, `MultiSelectChips` from `ui/components/ProfileWidgets.kt`.

`HealthMetricsSummary(data, unitSystem)` — 3-row grid of `MetricCell` composables showing the last HC sync data.
