# NAVIGATION_SPEC.md — PowerME Navigation

**Status:** ✅ Complete (v1.0 — March 2026)
**Domain:** NavHost · Route Contracts · Auth Flow · State Hoisting · Minimize/Maximize · Transitions

> **Living document.** Update this file whenever a route is added, removed, or its contract changes.
> This is the absolute source of truth for the NavHost, route contracts, state hoisting, and the minimize/maximize state machine.
> Cross-referenced by `CLAUDE.md`. Read this before touching `PowerMeNavigation.kt`, `MainActivity.kt`, or any composable that calls `navController`.

---

## Table of Contents

1. [Complete Route Table](#1-complete-route-table)
2. [Startup Auth Decision Tree & Process Death](#2-startup-auth-decision-tree--process-death)
3. [WorkoutViewModel Scope Contract (State Hoisting)](#3-workoutviewmodel-scope-contract-state-hoisting)
4. [Minimize / Maximize State Machine](#4-minimize--maximize-state-machine)
5. [MinimizedWorkoutBar Visibility Rules](#5-minimizedworkoutbar-visibility-rules)
6. [Edit Guard Block (Hard Block)](#6-edit-guard-block-hard-block)
7. [exercise_picker savedStateHandle Contract](#7-exercise_picker-savedstatehandle-contract)
8. [Bottom Tab Navigation Rules](#8-bottom-tab-navigation-rules)
9. [Nav Transition Animations](#9-nav-transition-animations)
10. [MainAppScaffold Contract](#10-mainappscaffold-contract)
11. [MainActivity Contract](#11-mainactivity-contract)
12. [Hidden / Archived Routes](#12-hidden--archived-routes)

---

## 1. Complete Route Table

| Route | Type | Args | ViewModel Scope | Notes |
|---|---|---|---|---|
| `auth_welcome` | Full-screen | — | — | Start destination when unauthenticated |
| `auth_profile_setup` | Full-screen | — | — | Start destination when auth OK but no DB user |
| `auth_hc_offer` | Full-screen | — | Screen-scoped `HcOfferViewModel` | Shown post-login when HC available, permissions not granted, and offer not previously dismissed. Navigates to `workouts` on connect or skip. |
| `auth_forgot_password` | Full-screen | — | Shared with `auth_welcome` | Scoped to `auth_welcome` back stack entry via `hiltViewModel(backStackEntry)` |
| `workouts` | Bottom tab | — | NavHost-scoped `WorkoutViewModel` | Default start destination post-auth |
| `history` | Bottom tab | — | Screen-scoped | — |
| `exercises` | Bottom tab | — | Screen-scoped | Fresh `ExercisesViewModel` per navigation |
| `tools` | Bottom tab | — | Screen-scoped | UI label: **Clocks** |
| `trends` | Bottom tab | — | Screen-scoped | UI label: **Trends** · Composable: `MetricsScreen` |
| `workout` | Full-screen overlay | — | NavHost-scoped `WorkoutViewModel` | Shares the same instance as all tabs |
| `settings` | Push | — | Screen-scoped | — |
| `profile` | Push | — | Screen-scoped | Accessed via AccountCircle icon in TopAppBar (left of Settings gear) |
| `workout_detail/{workoutId}` | Push | `workoutId: String` | Screen-scoped | Accessed from `WorkoutSummaryScreen` Edit button only |
| `workout_summary/{workoutId}?isPostWorkout={bool}&syncType={string}` | Push | `workoutId: String`, `isPostWorkout: Bool` (default false), `syncType: String` (default "NONE") | Screen-scoped | Post-workout: `LaunchedEffect(pendingWorkoutSummary)` in `ActiveWorkoutScreen` triggers `onWorkoutFinished()` when summary is set; `PowerMeNavigation` reads `lastFinishedWorkoutId`/`lastPendingRoutineSync` and navigates here with `popUpTo(WORKOUT){inclusive=true}`. History: navigated from `HistoryScreen` tap (no sync args). |
| `template_builder/{routineId}` | Push | `routineId: Long` | Screen-scoped | `routineId = -1` sentinel = new routine (see below) |
| `exercise_picker` | Push | — | Screen-scoped | Returns result via `savedStateHandle` |
| `quick_start_chooser` | Push | — | None (inline callbacks only) | `QuickStartChooserScreen` — full-page 3-card picker. "Add exercises" calls `workoutViewModel.startWorkout()` + popUpTo; "Add from picture"/"Add from text" navigate to `ai_workout?mode={photo\|text}` |
| `ai_workout?mode={mode}` | Push | `mode: String` (default `"text"`, values: `"text"` \| `"photo"`) | Screen-scoped `AiWorkoutViewModel` | Reached via `quick_start_chooser`; `mode` seeds `initialMode` in `AiWorkoutGenerationScreen`; on Start Workout calls `workoutViewModel.startWorkoutFromPlan(bootstrap)` then navigates to `workout` |

### `template_builder` Sentinel Value

`routineId = -1` is the sentinel for **creating a new routine**. When `TemplateBuilderViewModel` receives `-1`, it initializes an empty draft state and generates a new `routineId` on first save. Any value `> 0` is an existing routine being edited. Never pass `0` — it is not a valid routine ID.

---

## 2. Startup Auth Decision Tree & Process Death

### 2.1 Standard Flow

`AppStartupViewModel` runs on every cold launch and resolves `startRoute: StateFlow<String?>`:

```
Firebase user == null
  OR !isEmailVerified          →  auth_welcome   (Google users: isEmailVerified=true, never bounced)

Firebase user OK
  + DB user == null            →  auth_profile_setup

Firebase user OK
  + DB user exists
  + HC available + permissions not granted + offer not dismissed  →  auth_hc_offer

Firebase user OK
  + DB user exists
  + (HC unavailable OR permissions granted OR offer dismissed)    →  workouts
```

- The splash `CircularProgressIndicator` is shown until `startRoute` emits a non-null value.
- Each auth branch clears its own back stack entry before navigating forward (`popUpTo(route) { inclusive = true }`).
- **Google Sign-In note:** Google-provider users always have `isEmailVerified = true`, so they are never routed to `auth_welcome` on cold start after a successful sign-in. See §2.4.

### 2.4 Google Sign-In (Credential Manager)

The `WelcomeScreen` offers a **"Continue with Google"** button below the email/password form that works for both sign-up and sign-in. Both paths converge on the same decision tree in §2.1.

**Flow:**
```
WelcomeScreen "Continue with Google" button
  → AuthViewModel.signInWithGoogle(activityContext)
  → GoogleSignInHelper.signIn(context)
      → CredentialManager.getCredential(GetGoogleIdOption)   ← fast path, auto-select
            NoCredentialException?
          → CredentialManager.getCredential(GetSignInWithGoogleOption)  ← fallback: full bottom sheet
      → GoogleIdTokenCredential.idToken
      → GoogleAuthProvider.getCredential(idToken)
      → Firebase.auth.signInWithCredential().await()
  → UserSessionManager.getCurrentUser()
      null     → needsProfileSetup = true  → auth_profile_setup
      non-null → isSignedIn = true         → workouts
```

**Key invariants:**
- **Two-step Credential Manager strategy:** `GetGoogleIdOption` is tried first (fast, supports auto-select for returning users). On `NoCredentialException` (common on emulators and older Play Services), falls back to `GetSignInWithGoogleOption` which shows the full Google account-picker bottom sheet. Both paths extract the same `idToken` and feed into the same Firebase flow.
- The email-verification branch in `signIn()` is **skipped** for Google — Google identities are pre-verified and `isEmailVerified` is always `true`.
- New-user gate remains **Room-based** — same as email/password: if `UserDao.getCurrentUser() == null`, route to `ProfileSetupScreen`. No Firestore user document created during onboarding.
- User cancelling the account picker is **silent** — no error toast; `GetCredentialCancellationException` is caught and loading is cleared.
- `NoCredentialException` propagates to the ViewModel only if **both** `GetGoogleIdOption` and `GetSignInWithGoogleOption` fail (truly no Google accounts on device); shows a brief error message.
- **Web Client ID** is stored in `BuildConfig.GOOGLE_WEB_CLIENT_ID`, populated from `local.properties` (gitignored). Obtain it from Firebase Console → Authentication → Google provider.
- **Firebase Console prerequisites:** `google-services.json` must contain an Android OAuth client (type 1) entry for `com.powerme.app`. This requires the debug/release SHA-1 fingerprint to be registered in Firebase Console (Project Settings → Your apps → SHA certificate fingerprints) and the json re-downloaded. Without it, Credential Manager silently fires `GetCredentialCancellationException` after account selection. Debug SHA-1: `D6:EF:D7:82:62:FD:B7:8D:64:D5:D3:E9:D8:63:04:A2:38:A7:9B:62`.
- **Sign-out:** `Firebase.auth.signOut()` covers both email and Google sessions. Optionally call `CredentialManager.clearCredentialState()` to clear the cached Google credential so the account picker reappears on next login.

**Abstraction:** `GoogleSignInHelper` (interface in `ui/auth/`) wraps the Credential Manager + Firebase chain. `DefaultGoogleSignInHelper` is the production implementation. Tests mock `GoogleSignInHelper` directly — no Android instrumentation required.

#### 2.4.1 Account Linking on Credential Collision

When a user who registered with email/password taps "Continue with Google" using the same email, Firebase throws `FirebaseAuthUserCollisionException` (`ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL`).

**Collision flow:**
```
DefaultGoogleSignInHelper.signIn()
  → Firebase.auth.signInWithCredential() throws FirebaseAuthUserCollisionException
  → catch → throw AccountCollisionException(email, pendingCredential)

AuthViewModel.signInWithGoogle()
  → catch AccountCollisionException
  → pendingLinkCredential = credential (private ViewModel field, not in StateFlow)
  → uiState.pendingLinkEmail = email

WelcomeScreen
  → pendingLinkEmail != null → show inline Card (replaces form via return@Column)
  → password field + "Link Account" button + "Cancel" button

AuthViewModel.linkGoogleAfterPasswordAuth(password)
  → Firebase.auth.signInWithEmailAndPassword(email, password).await()
  → Firebase.auth.currentUser!!.linkWithCredential(pendingCredential).await()
  → clear pendingLinkCredential + pendingLinkEmail
  → userSessionManager.getCurrentUser() → isSignedIn / needsProfileSetup (normal gate)

AuthViewModel.dismissLinkPrompt()
  → clears pendingLinkCredential + pendingLinkEmail
```

**Design invariants:**
- `AccountCollisionException` is defined in `GoogleSignInHelper.kt` (carries email + `AuthCredential`); propagates through the unchanged `GoogleSignInHelper` interface.
- `AuthCredential` is stored as a private ViewModel field (not in `AuthUiState`) to avoid `equals()` issues with `StateFlow` duplicate-emission filtering.
- Wrong password during linking shows inline error in the Card; `pendingLinkEmail` remains set so the user can retry.
- Cancelling via "Cancel" calls `dismissLinkPrompt()` — restores the normal sign-in form.
- `AccountCollisionException` is caught **before** the generic `Exception` catch in `signInWithGoogle()`.

---

### 2.2 Sign-Out

Tapping **Log Out** (via `ProfileViewModel.signOut()` → `UserSessionManager.clearUser()`) currently:
1. Deletes only the `users` table row from Room.
2. Resets `hasRestoredOnce = false` in `AppSettingsDataStore` — **critical** for re-login restore.
3. Calls `Firebase.auth.signOut()`.
4. Navigates to `auth_welcome` with `popUpTo(0) { inclusive = true }` — destroys the entire back stack.

**Re-login restore flow:** On next sign-in, `AuthViewModel.applyNewUserGate()` sees `hasRestoredOnce = false` and calls `pullProfileOnly()` (blocks — restores the User entity before routing) then `launchBackgroundSync()` (background — restores settings, app prefs, workouts, routines). The defensive fallback in `applyNewUserGate()` also handles the edge case where `hasRestoredOnce` was somehow not reset: if `dbUser == null && alreadyRestored`, it calls `pullProfileOnly()` + `launchBackgroundSync()` and re-checks before routing.

**Future refactor `[Not Yet Implemented]`:** Full DB nuke (`AppDatabase.clearAllTables()`) on sign-out to prevent data leaking when a different user signs in on the same device. Currently only the `users` row is deleted; other tables (health history, metric logs, workouts, routines) remain and are associated with no user until the next sign-in overwrites them.

### 2.3 Process Death & Foreground Service Notification `[Target Architecture — Pending Implementation]`

If the app is killed while a workout is active, `WorkoutTimerService` (ForegroundService) keeps the notification alive. Tapping the notification relaunches `MainActivity` with a `START_WORKOUT_RESUME` intent extra.

**Rehydration flow:**
1. `MainActivity` receives intent → passes extra to `PowerMeApp`.
2. `PowerMeApp` passes through the Auth Gate (§2.1). If unauthenticated, boot to `auth_welcome` — never skip auth.
3. Once on `workouts`, `WorkoutViewModel.rehydrateIfNeeded()` restores session from Iron Vault.
4. `maximizeWorkout()` is fired → `LaunchedEffect(isMinimized)` navigates to `workout` route.

---

## 3. WorkoutViewModel Scope Contract (State Hoisting)

### 3.1 Scope

`WorkoutViewModel` is instantiated **once** via `hiltViewModel()` at the `PowerMeApp` composable level (NavHost scope). Every route that touches active workout state receives the same instance.

**Rule:** No composable below `PowerMeApp` may call `hiltViewModel<WorkoutViewModel>()` independently. Doing so creates a second, orphaned instance with no workout state.

### 3.2 Anti-Pattern: Parameter Drilling `[Mandatory Refactor — Current Codebase Bug]`

**Current implementation:** `WorkoutViewModel` is parameter-drilled directly into `WorkoutsScreen` and `ActiveWorkoutScreen`. This passes the entire ViewModel reference through the call tree, causing unnecessary recomposition overhead and tight coupling between the NavHost and every screen that touches workout state.

**Target architecture (mandatory refactor):** State must be hoisted at the NavHost level. Pass only the raw state object and specific lambda callbacks downward. Child composables must never hold a reference to the ViewModel itself.

**Correct pattern:** Extract state at the `PowerMeApp` level:

```
val workoutState by workoutViewModel.workoutState.collectAsState()
```

Pass **only the raw state object** and **specific lambda callbacks** downward. Child composables must never hold a reference to the ViewModel itself.

```
// ✅ Correct
WorkoutsScreen(
    isWorkoutActive = workoutState.isActive,
    onStartWorkout = { workoutViewModel.startWorkoutFromRoutine(it) }
)

// ❌ Wrong
WorkoutsScreen(viewModel = workoutViewModel)
```

### 3.3 The `workout` Route

The `workout` composable route also uses the NavHost-scoped `WorkoutViewModel`. It is passed explicitly, not resolved via `hiltViewModel()`:

```kotlin
composable(Routes.WORKOUT) {
    ActiveWorkoutScreen(
        viewModel = workoutViewModel,   // NavHost-scoped instance
        onMinimize = { navController.popBackStack() },
        onWorkoutFinished = { navController.navigate(Screen.Workouts.route) { popUpTo(Routes.WORKOUT) { inclusive = true } } }
    )
}
```

---

## 4. Minimize / Maximize State Machine

### 4.1 Minimize

1. User taps ↓ chevron (or bottom nav tab while workout active) in `ActiveWorkoutScreen`.
2. `ActiveWorkoutScreen` calls `onMinimize`.
3. `onMinimize` → `navController.popBackStack()` (pops `workout` route, returns to previous tab).
4. `workoutViewModel.minimizeWorkout()` sets `isMinimized = true`.
5. `WorkoutViewModel` (NavHost-scoped) retains full state — elapsed timer, Iron Vault, rest timers all continue.
6. `MinimizedWorkoutBar` becomes visible in `MainAppScaffold` (see §5).

**Minimizing never terminates the session.** The only valid termination paths are `cancelWorkout()` and `finishWorkout()`.

### 4.2 Maximize

1. User taps `MinimizedWorkoutBar` (or the Resume banner on Workouts screen).
2. Calls `workoutViewModel.maximizeWorkout()` → sets `isMinimized = false`.
3. A `LaunchedEffect(workoutState.isMinimized)` in `PowerMeApp` detects the transition:
   ```
   condition: !isMinimized && isActive && currentRoute != "workout"
   ```
4. Navigates: `navController.navigate(Routes.WORKOUT) { launchSingleTop = true }`.

### 4.3 Second Workout Guard

A second workout cannot be started while `isMinimized = true`. `startWorkoutFromRoutine()` must check `isMinimized` and redirect to the minimized session instead of creating a new one.

---

## 5. MinimizedWorkoutBar Visibility Rules

**Condition:** `(isActive || isEditMode) && isMinimized`

**Visible on:** All routes wrapped by `MainAppScaffold` — the 5 bottom tabs (`workouts`, `history`, `exercises`, `tools`, `trends`).

**Hidden on:** Auth routes (`auth_welcome`, `auth_profile_setup`, `auth_forgot_password`) and all push screens (`settings`, `workout_detail`, `template_builder`, `exercise_picker`). These routes do not host `MainAppScaffold`.

**Edit Mode label:** When `isEditMode = true`, the elapsed timer is replaced with the fixed text `"Edit Mode"`. This is signalled by `elapsedSeconds == -1` passed to `MinimizedWorkoutBar`. See also `WORKOUT_SPEC.md §20.1`.

---

## 6. Edit Guard Block (Hard Block)

Triggered when `workoutState.showEditGuard == true`.

**There is no read-only template view.** Attempting to edit a routine while a workout is active is a **hard block** — not a warning, not a degraded mode.

**Dialog:**
> *"You have an active workout in progress. You must finish or cancel it before editing templates."*

**Actions:**
- `[Go to Workout]` — calls `workoutViewModel.clearEditGuard()`, navigates to `workout` route.
- `[Cancel]` — calls `workoutViewModel.clearEditGuard()` only.

**Trigger path:** `WorkoutsScreen` Edit button → `workoutViewModel.startEditMode(routineId)` → ViewModel detects `isActive = true` → sets `showEditGuard = true` — navigation is suppressed. The guard dialog is rendered at the `PowerMeApp` level, not inside `WorkoutsScreen`.

---

## 7. exercise_picker savedStateHandle Contract

### 7.1 Setting the Result (Picker Side)

When the user confirms selection in `ExercisesScreen` (picker mode):
```kotlin
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("selected_exercises", ArrayList(selectedExerciseIds))
navController.popBackStack()
```

### 7.2 Consuming the Result (Caller Side)

The caller (`TemplateBuilderScreen` or `ActiveWorkoutScreen`) observes:
```kotlin
backEntry?.savedStateHandle?.getStateFlow<ArrayList<Long>?>("selected_exercises", null)
```

### 7.3 Consume-and-Clear Rule (CRITICAL)

**The caller is strictly responsible for clearing the result immediately after consuming it.**

```kotlin
val ids = backEntry?.savedStateHandle?.get<ArrayList<Long>>("selected_exercises") ?: return
processSelectedExercises(ids)
backEntry.savedStateHandle["selected_exercises"] = null   // ← MUST clear
```

Failure to clear causes **duplicate insertions** on screen rotation, because `savedStateHandle` survives configuration changes and the collector will fire again with the stale data.

---

## 8. Bottom Tab Navigation Rules

All bottom tab navigation must use:
```kotlin
navController.navigate(screen.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

- `popUpTo + saveState` — prevents back stack explosion when switching tabs repeatedly.
- `launchSingleTop` — prevents duplicate instances of the same tab.
- `restoreState` — restores scroll position and UI state when returning to a tab.

**Tab order:** Workouts · History · Exercises · Clocks (Tools) · Trends

---

## 9. Nav Transition Animations

| Route category | Enter | Exit |
|---|---|---|
| **Tab switching** (any of the 5 bottom tabs) | `fadeIn(tween(200))` | `fadeOut(tween(200))` |
| **Push screens** (`settings`, `workout_detail`, `template_builder`, `exercise_picker`) | `slideInHorizontally { fullWidth }` | `slideOutHorizontally { -fullWidth/3 }` |
| **Push back** (pop from any push screen) | `slideInHorizontally { -fullWidth/3 }` | `slideOutHorizontally { fullWidth }` |
| **workout overlay** (minimize/maximize) | `slideInVertically { fullHeight }` (tween 350ms, FastOutSlowInEasing) | `slideOutVertically { fullHeight }` (tween 350ms, FastOutSlowInEasing) |

The `workout` overlay animation is specified in `WORKOUT_SPEC.md §20.3` and reproduced here for completeness. `WORKOUT_SPEC.md §20.3` is the canonical source for the minimize/maximize animation timing and easing.

**Rule:** All transitions are implemented as `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` on the `composable()` declaration in `NavHost`. Do not implement transitions inside individual screen composables.

---

## 10. MainAppScaffold Contract

`MainAppScaffold` is the shared chrome for all routes that show the bottom navigation bar.

**Hosts:** `workouts`, `history`, `exercises`, `tools`, `trends`, `warroom`

**Does NOT host:** Auth routes, `workout`, or any push screen. These render their own chrome or no chrome.

### 10.1 TopAppBar

- **Left:** App logo image (`ic_powerme_logo_source`, 36dp height, aspect-ratio locked). No text title.
- **Right (actions, left to right):**
  1. Profile `IconButton` (`Icons.Default.AccountCircle`, `primary` tint) → navigates to `profile` route.
  2. Settings `IconButton` (Settings icon, `primary` tint) → navigates to `settings` route.
- **Container color:** `MaterialTheme.colorScheme.surface`
- No back button, no search, no other actions beyond Profile + Settings.

### 10.2 BottomNavigationBar

- **Container color:** `MaterialTheme.colorScheme.surface`
- **Selected icon color:** `MaterialTheme.colorScheme.background` (inverted — icon appears dark on primary indicator pill)
- **Selected text color:** `MaterialTheme.colorScheme.primary`
- **Indicator color:** `MaterialTheme.colorScheme.primary`
- **Unselected color:** `primary.copy(alpha = 0.4f)`
- `windowInsets = NavigationBarDefaults.windowInsets` — do not apply `navigationBarsPadding()` directly on the bar; it is handled via `windowInsets`.

### 10.3 MinimizedWorkoutBar Placement

Rendered in a `Column` wrapping the `NavigationBar`, placed **above** it:

```
Column(modifier = Modifier.navigationBarsPadding()) {
    MinimizedWorkoutBar(...)   // ← shown when (isActive || isEditMode) && isMinimized
    NavigationBar(windowInsets = WindowInsets(0)) { ... }
}
```

`navigationBarsPadding()` is applied to the outer `Column`, not to the `NavigationBar` directly, so both the bar and the workout strip are inset together.

---

## 11. MainActivity Contract

`MainActivity` owns the following responsibilities. No other file should duplicate these:

| Responsibility | API | Notes |
|---|---|---|
| Theme application | `PowerMETheme(themeMode = themeMode)` | Collects `themeMode: ThemeMode` from `AppSettingsDataStore` |
| Edge-to-edge | `enableEdgeToEdge(...)` | Called inside a `SideEffect` reacting to `themeMode` changes; sets `StatusBarStyle` and `NavigationBarStyle` appropriately for light/dark |
| Keep screen on | `window.addFlags(FLAG_KEEP_SCREEN_ON)` / `clearFlags` | Set unconditionally when user preference is enabled; cleared when disabled. No longer gated on workout state. |
| Process death intent | `intent.getStringExtra("action")` | Reads `START_WORKOUT_RESUME` extra; triggers `maximizeWorkout()` after auth gate clears. See §2.3 |

**Rule:** `MainActivity` must remain thin — it must not contain business logic, ViewModel calls, or navigation decisions beyond what is listed above.

---

## 12. Deprecated Routes

| Route | Status | Notes |
|---|---|---|
| `gym_setup` | Removed | Screen file preserved; nav route removed. Data layer (GymProfileRepository, GymProfile entity) preserved. |
| `gym_inventory/{profileId}` | Removed | Screen file preserved; nav route removed. Data layer preserved. |
