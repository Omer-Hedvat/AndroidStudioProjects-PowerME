# Profile Setup Screen — Spec

## Overview

The Profile Setup screen is shown once during onboarding, immediately after Firebase Auth sign-in/verification, when a user row does not yet exist in Room. It collects personal and fitness profile data, with optional pre-fill from Google account and Health Connect.

**Route:** `Routes.AUTH_PROFILE_SETUP`  
**ViewModel:** `ui/auth/ProfileSetupViewModel.kt`  
**Screen:** `ui/auth/ProfileSetupScreen.kt`

---

## 1. Entry Conditions

| Auth Path | Data available |
|---|---|
| Email/password sign-up → verify → sign-in | `email` only, `displayName` null |
| Google Sign-In | `email`, `displayName`, `photoUrl` |
| Cold start (Firebase user, no Room row) | Whatever Firebase persisted from original auth |

In all cases `Firebase.auth.currentUser` is non-null and verified.

---

## 2. Two-Step Flow

Both steps render inside the single `ProfileSetupScreen` composable (no separate routes). `ProfileSetupUiState.currentStep` drives which step is shown.

```
Step 1: Health Connect Offer  ← skipped if HC unavailable on device
Step 2: Profile Questionnaire ← always shown; pre-filled from Google + HC
```

**Navigation on save:** navigates to `Screen.Workouts.route`, pops `AUTH_PROFILE_SETUP` inclusively (user cannot go back to setup).

---

## 3. Step 1 — Health Connect Offer

**Shown when:** `HealthConnectManager.isAvailable() == true`  
**Skipped when:** HC unavailable — `currentStep` initialises at 2.

**If permissions already granted** (interrupted onboarding): `init` auto-reads HC data and starts at Step 2.

### Layout

- `Icons.Default.Favorite` (64dp, primary color)
- Title: "Connect Health Data"
- Subtitle: import description
- If `hcPermissionDenied`: reassurance text ("No worries — you can connect Health Connect later in Settings.")
- If NOT denied: "Connect Health Connect" button → launches `PermissionController.createRequestPermissionResultContract()`
- "Skip" / "Continue without Health Connect" TextButton → `viewModel.skipHc()`

### Permission Handling

- **Granted:** `healthConnectManager.readAllData()` (read-only — `syncAndRead()` is NOT called because no User row exists yet) → extract weight/height/bodyFat → advance to Step 2
- **Denied:** set `hcPermissionDenied = true`, stay on Step 1 with reassurance message

---

## 4. Step 2 — Profile Questionnaire

### 4.1 Pre-fill Sources

| Field | Google | Health Connect |
|---|---|---|
| Name | `Firebase.auth.currentUser.displayName` | — |
| Height | — | `HealthConnectReadResult.height` (cm) |
| Weight | — | `HealthConnectReadResult.weight` (kg) |
| Body Fat % | — | `HealthConnectReadResult.bodyFat` (%) |

HC-prefilled fields show helper text "from Health Connect" below the field. All pre-filled fields remain editable.

### 4.2 Unit System Selector

A `SingleChoiceSegmentedButtonRow` (Metric / Imperial) appears at the top of Step 2, before the Name field. Persists via `ProfileSetupViewModel.setUnitSystem()` → `AppSettingsDataStore`.

**Internal ground-truth pattern:** `heightCmInternal: Double?` and `weightKgInternal: Double?` hold the raw metric value. `LaunchedEffect(unitSystem)` reconverts display strings when the unit switches. The "Get Started" button always passes raw metric values to `saveProfile()`.

### 4.3 Field Definitions

| Field | Widget | Keyboard | Default | Optional |
|---|---|---|---|---|
| Name | `ProfileTextField` (text) | Text | Google `displayName` or `""` | Yes |
| Date of Birth | Read-only `OutlinedTextField` + `DatePickerDialog` | N/A | null | Yes |
| Height (metric) | `ProfileTextField` | Decimal | HC value or `""` | Yes |
| Height (imperial) | Two `ProfileTextField` side-by-side (ft + in) | Number | Derived from HC | Yes |
| Weight | `ProfileTextField` | Decimal | HC value (unit-converted) or `""` | Yes |
| Body Fat % | `ProfileTextField` | Decimal | HC value or `""` | Yes |
| Avg Sleep Hours | `ProfileTextField` | Decimal | `"7"` | Yes |
| Number of Children | `ProfileTextField` | Number | `"0"` | Yes |
| Gender | `SingleChoiceSegmented` chips | N/A | `""` (unselected) | Yes |
| Occupation Type | `SingleChoiceSegmented` chips | N/A | `"SEDENTARY"` | Yes |
| Chronotype | `SingleChoiceSegmented` chips | N/A | `"NEUTRAL"` | Yes |
| Training Targets | `MultiSelectChips` | N/A | empty set | Yes |

All fields are optional — subtitle reads "All fields are optional — skip what you don't know".

**Date of Birth picker:** Year range `1920..(currentYear - 5)`, format `"MMM d, yyyy"`. Uses `MutableInteractionSource + collectIsPressedAsState` to detect taps on the read-only field.

**Gender chip:** toggleable — selecting the active value deselects it (passes `null` to save).

**Training Targets:** `TRAINING_TARGET_OPTIONS = ["Hypertrophy", "Fat Loss", "Body Recomposition", "Strength", "Cardio", "Longevity"]`, rendered in rows of 3 via `MultiSelectChips`.

### 4.4 Submit Behavior

"Get Started" button calls `viewModel.saveProfile(...)`:
1. `Firebase.auth.currentUser.email` → used as Room PK
2. Constructs `User` entity with `dateOfBirth: Long?` (not `age`)
3. `userSessionManager.saveUser(user)` (Room upsert)
4. `profileSaved = true` → `LaunchedEffect` → `onProfileSaved()` → navigate to Workouts

### 4.5 Skip Behavior

A low-prominence "Skip for now" `TextButton` appears below the "Get Started" button. It is disabled while a save is in progress.

**On tap:** `viewModel.skipProfileSetup()` saves a minimal `User(email = FirebaseAuth.email)` with all other fields at null/default. This sets `profileSaved = true`, triggering the same `LaunchedEffect` → `onProfileSaved()` → navigate to Workouts path.

**Why a User row is required on skip:** `AppStartupViewModel` checks `userSessionManager.getCurrentUser()`. If null, the user is routed back to `AUTH_PROFILE_SETUP`. The minimal User row prevents this loop.

**Later completion:** Profile fields can be filled in at any time from the Profile screen.

---

## 5. Shared Composables

Composables shared with `SettingsScreen.kt` live in `ui/components/ProfileWidgets.kt`:

| Composable | Description |
|---|---|
| `ProfileTextField` | OutlinedTextField with optional `helperText`, select-all-on-focus for numeric fields |
| `SingleChoiceSegmented` | Row of `FilterChip` for single selection |
| `MultiSelectChips` | Grid of `FilterChip` (chunked in rows of 3) for multi-select |
| `TRAINING_TARGET_OPTIONS` | Shared constant list of 6 training target strings |

---

## 6. ViewModel State (`ProfileSetupUiState`)

| Field | Type | Purpose |
|---|---|---|
| `currentStep` | `Int` | 1 = HC offer, 2 = profile form |
| `hcAvailable` | `Boolean` | Whether HC is installed |
| `hcConnected` | `Boolean` | Whether HC permissions granted |
| `hcPermissionDenied` | `Boolean` | Whether user denied HC permissions |
| `hcWeight` | `Double?` | Weight pre-filled from HC (kg) |
| `hcHeight` | `Float?` | Height pre-filled from HC (cm) |
| `hcBodyFat` | `Double?` | Body fat pre-filled from HC (%) |
| `googleDisplayName` | `String?` | Name from Firebase Auth |
| `isSaving` | `Boolean` | Save in progress |
| `saveError` | `String?` | Error message if save fails |
| `profileSaved` | `Boolean` | True when save completes → triggers navigation |

---

## 7. Edge Cases

| Scenario | Behavior |
|---|---|
| HC available, no data in HC | Permissions granted, `readAllData()` returns nulls. Fields empty, no HC badges. |
| HC unavailable | `currentStep` starts at 2. Step 1 never shown. |
| Google account with no `displayName` | Name field empty. |
| HC permissions already granted | Auto-read on init, start at Step 2 with pre-filled values. |
| Existing users with `age` but no `dateOfBirth` | `User.ageYears` extension falls back to `age`. |
| User skips profile setup | Minimal `User(email=...)` saved. All nullable fields null. Profile screen allows later completion. |
