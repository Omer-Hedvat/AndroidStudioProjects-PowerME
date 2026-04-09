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
| `auth_forgot_password` | Full-screen | — | Shared with `auth_welcome` | Scoped to `auth_welcome` back stack entry via `hiltViewModel(backStackEntry)` |
| `workouts` | Bottom tab | — | NavHost-scoped `WorkoutViewModel` | Default start destination post-auth |
| `history` | Bottom tab | — | Screen-scoped | — |
| `exercises` | Bottom tab | — | Screen-scoped | Fresh `ExercisesViewModel` per navigation |
| `tools` | Bottom tab | — | Screen-scoped | UI label: **Clocks** |
| `trends` | Bottom tab | — | Screen-scoped | UI label: **Trends** · Composable: `MetricsScreen` |
| `workout` | Full-screen overlay | — | NavHost-scoped `WorkoutViewModel` | Shares the same instance as all tabs |
| `settings` | Push | — | Screen-scoped | — |
| `gym_setup` | Push | — | Screen-scoped | — |
| `gym_inventory/{profileId}` | Push | `profileId: Long` | Screen-scoped | Entered after GymSetup save |
| `workout_detail/{workoutId}` | Push | `workoutId: Long` | Screen-scoped | Accessed from History tab |
| `template_builder/{routineId}` | Push | `routineId: Long` | Screen-scoped | `routineId = -1` sentinel = new routine (see below) |
| `exercise_picker` | Push | — | Screen-scoped | Returns result via `savedStateHandle` |

### `template_builder` Sentinel Value

`routineId = -1` is the sentinel for **creating a new routine**. When `TemplateBuilderViewModel` receives `-1`, it initializes an empty draft state and generates a new `routineId` on first save. Any value `> 0` is an existing routine being edited. Never pass `0` — it is not a valid routine ID.

---

## 2. Startup Auth Decision Tree & Process Death

### 2.1 Standard Flow

`AppStartupViewModel` runs on every cold launch and resolves `startRoute: StateFlow<String?>`:

```
Firebase user == null
  OR !isEmailVerified          →  auth_welcome

Firebase user OK
  + DB user == null            →  auth_profile_setup

Firebase user OK
  + DB user exists             →  workouts
```

- The splash `CircularProgressIndicator` is shown until `startRoute` emits a non-null value.
- Each auth branch clears its own back stack entry before navigating forward (`popUpTo(route) { inclusive = true }`).

### 2.2 Sign-Out Nuke `[Mandatory Future Refactor — Not Yet Implemented]`

Tapping **Log Out** must:
1. Execute `popUpTo(0) { inclusive = true }` — destroys the **entire** back stack.
2. Wipe the local Room database immediately to prevent data leaking between accounts.
3. Navigate to `auth_welcome`.

Failure to wipe the DB on sign-out is a data-leak bug. The sign-out handler must call `AppDatabase.clearAllTables()` (or equivalent) before navigating.

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

**Hidden on:** Auth routes (`auth_welcome`, `auth_profile_setup`, `auth_forgot_password`) and all push screens (`settings`, `gym_setup`, `gym_inventory`, `workout_detail`, `template_builder`, `exercise_picker`). These routes do not host `MainAppScaffold`.

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
| **Push screens** (`settings`, `gym_setup`, `gym_inventory`, `workout_detail`, `template_builder`, `exercise_picker`) | `slideInHorizontally { fullWidth }` | `slideOutHorizontally { -fullWidth/3 }` |
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
- **Right:** Settings `IconButton` (Settings icon, `primary` tint) → navigates to `settings` route.
- **Container color:** `MaterialTheme.colorScheme.surface`
- No back button, no search, no other actions.

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
| Keep screen on | `window.addFlags(FLAG_KEEP_SCREEN_ON)` / `clearFlags` | Set when workout is active per user preference; cleared unconditionally on `finishWorkout()` or `cancelWorkout()`. Full rules in `WORKOUT_SPEC.md §27 invariant #17` |
| Process death intent | `intent.getStringExtra("action")` | Reads `START_WORKOUT_RESUME` extra; triggers `maximizeWorkout()` after auth gate clears. See §2.3 |

**Rule:** `MainActivity` must remain thin — it must not contain business logic, ViewModel calls, or navigation decisions beyond what is listed above.

---

## 12. Deprecated Routes

No currently deprecated routes.
