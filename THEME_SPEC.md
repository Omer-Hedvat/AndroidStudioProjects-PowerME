# THEME_SPEC.md — PowerME Theme System

**Status:** ✅ Complete (v2.2 — April 2026)
**Domain:** Color Tokens · ThemeMode · Typography · Semantic Colors · Shape System · Spacing Tokens · Component Defaults · Token Rules

> **Living document.** Update this file whenever a color token, font, or theme rule changes.
> This is the canonical source of truth for all visual design decisions.
> Cross-referenced by `CLAUDE.md`. Read this before touching `Color.kt`, `Theme.kt`, `Type.kt`, or any composable that sets a color or text style.

---

## Table of Contents

1. [Color Token Reference — Pro Tracker v4.0 Dark Palette](#1-color-token-reference--pro-tracker-v40-dark-palette)
2. [Semantic Color Contexts](#2-semantic-color-contexts)
3. [ThemeMode System](#3-thememode-system)
4. [Typography System](#4-typography-system)
5. [LightColorScheme](#5-lightcolorscheme)
6. [Technical Invariants & Token Rules](#6-technical-invariants--token-rules)

---

## 1. Color Token Reference — Pro Tracker v6.0 Dark Palette

### 1.1 DarkColorScheme — Core Token Map

| Token Name | Hex | M3 Role | Notes |
|---|---|---|---|
| `ProBackground` | `#101010` | `background`, `surfaceTint` | True neutral near-black (equal RGB). Not pure black — reduces eye fatigue. Also used as `surfaceTint` to suppress M3 purple elevation overlay (see §6.3) |
| `ProSurface` | `#1C1C1C` | `surface` | Neutral dark grey — cards, TopAppBar, NavigationBar container |
| `ProSurfaceVar` | `#282828` | `surfaceVariant` | Neutral lifted surface — chips, row backgrounds, input backgrounds |
| `ProViolet` | `#9B7DDB` | `primary` | FABs, focus borders, active toggle indicators, exercise name text, nav indicator pill. Desaturated lavender-violet — not neon. |
| `ProMagenta` | `#9E6B8A` | `secondary` | Superset spine (4dp left border), secondary chips. Dusty rose/mauve. |
| `ProCloudGrey` | `#EDEDEF` | `onSurface`, `onBackground` | High-emphasis text — neutral near-white (+2 blue prevents OLED yellow cast). Contrast ~18.2:1 on `ProBackground` (AAA) |
| `ProSubGrey` | `#A0A0A0` | `onSurfaceVariant` | Medium-emphasis text, subtitles, timestamps. Neutral grey. |
| `ProError` | `#E05555` | `error` | Destructive actions (delete, cancel edit), error states. Desaturated from neon red. |
| `ProOutline` | `#383838` | `outline` | Neutral unfocused `OutlinedTextField` borders, dividers |
| *(unnamed)* | `#2D2052` | `primaryContainer` | Muted deep purple container (e.g. selected chip background, FAB container) |
| *(unnamed)* | `#E0D4F0` | `onPrimaryContainer` | Muted lavender — text/icon on `primaryContainer` surfaces |

### 1.2 Derived / Implicit Tokens

The following are computed by the M3 engine and not explicitly set — document here for awareness:

| M3 Role | Effective Value | How resolved |
|---|---|---|
| `onPrimary` | `#101010` | Explicitly set to `ProBackground` |
| `onSecondary` | `#101010` | Explicitly set to `ProBackground` |
| `onError` | `#101010` | Explicitly set to `ProBackground` |
| `errorContainer` | *(not explicitly set)* | M3 default tonal — avoid in composables, use `error` directly |

---

## 2. Semantic Color Contexts

Semantic colors are domain-specific and exist **outside the M3 token system**. They must be accessed via the `LocalPowerMeColors` composition local extension — never as raw `Color.kt` constants in composables.

| Token | Hex | Exclusive use — nowhere else |
|---|---|---|
| `TimerGreen` | `#4CC990` | **[FINISH WORKOUT]** button (text + `BorderStroke`) · Completed set checkmarks. Desaturated emerald. |
| `TimerRed` | `#E04458` | Rest state indicator. Desaturated from neon red. |
| `NeonPurple` | `#BB86FC` | Active rest timer accent — `CircularProgressIndicator` + `RestSeparator` active background + `-30/+30 SEC` buttons. Bright neon purple; distinct from `ProViolet` (#9B7DDB). |
| `FormCuesGold` | `#5A4D1A` | Form Cues persistent banner background in `ExerciseDetailSheet` only |
| `ReadinessAmber` | `#FFB74D` | Readiness gauge arc — moderate tier (40–69) + `CalibratingContent` spinner and progress text |

### 2.1 Semantic Color Access Pattern

Semantic colors must not be accessed by importing `Color.kt` constants in composables. The correct pattern is a `MaterialTheme` extension backed by a `CompositionLocal`:

- Define a custom color holder class (e.g. `PowerMeSemanticColors`) containing the semantic tokens.
- Provide it via `staticCompositionLocalOf` and supply it inside `PowerMETheme`.
- Access in composables via `MaterialTheme.semanticColors.timerGreen`.

This ensures:
1. Semantic colors participate in the theme hierarchy and can be overridden per test/preview.
2. No raw hex values leak into composable files.
3. The compiler will fail if a semantic color is used outside its provided scope.

### 2.2 TimerGreen Restrictions

`TimerGreen` is **strictly reserved** for two use sites:
1. `[FINISH WORKOUT]` `OutlinedButton` — text color + `BorderStroke(1.dp, TimerGreen)`. No other button uses this color.
2. Completed set checkmark icon tint in `WorkoutSetRow`.

Do not use `TimerGreen` for: success states, health metrics, progress bars, status indicators, or any other "positive" UI element. Those use `primary` or contextual `onSurface` tokens.

---

## 3. ThemeMode System

### 3.1 ThemeMode Enum

```
LIGHT   — Force LightColorScheme
DARK    — Force DarkColorScheme (default Pro Tracker palette)
SYSTEM  — Follow the OS dark/light setting
```

Defined in `data/ThemeMode.kt`. Stored via `AppSettingsDataStore.themeMode: Flow<ThemeMode>`.

### 3.2 Implementation Chain

```
AppSettingsDataStore.themeMode (Flow<ThemeMode>)
    ↓ collected in MainActivity
PowerMETheme(themeMode: ThemeMode)
    ↓ selects ColorScheme
MaterialTheme(colorScheme = ...)
    ↓ all composables consume via MaterialTheme.colorScheme.*
```

**`MainActivity` responsibilities** (owned by `NAVIGATION_SPEC.md §11`):
- Collects `themeMode` as `State<ThemeMode>`.
- Calls `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` inside a `SideEffect` that reacts to `themeMode`. Light mode uses light status bar style; dark mode uses dark style.

### 3.3 UI Control

`SettingsScreen` → **Appearance** card → `SingleChoiceSegmentedButtonRow` with three segments: **Light · Dark · System**.

Calls `appSettingsDataStore.setThemeMode(selected)` on selection. Change is reflected immediately via the `Flow` without restart.

### 3.4 Deprecated API

`darkModeEnabled: Boolean` and `setDarkModeEnabled()` are **deprecated** and preserved for schema stability only. They must not be used in new code. All new theme logic must go through `ThemeMode`.

---

## 4. Typography System

### 4.1 Font Families

| Family | Weights loaded | Usage |
|---|---|---|
| `BarlowCondensed` | Medium (500) · SemiBold (600) · Bold (700) | Display and headline roles only. Minimum size: **20sp**. Never use below 20sp — legibility degrades rapidly at small sizes. |
| `Barlow` | Normal (400) · Medium (500) · SemiBold (600) | Body, title, and label roles |
| `JetBrainsMono` | Regular (400) | **Timer displays only** — elapsed session timer, rest countdown, stopwatch. No other use. |

All fonts loaded via `GoogleFont.Provider`. Offline fallback: system sans-serif for Barlow/BarlowCondensed, system monospace for JetBrainsMono. Certificate arrays in `res/values/font_provider_certs.xml` (GMS).

**Rationale (from UI/UX analysis):** BarlowCondensed + Barlow is the top-ranked pairing for fitness/sports/athletic contexts. Condensed headlines allow more content per line in data-dense workout views. `JetBrainsMono` uses tabular figures — digits are fixed-width, preventing timer display from shifting layout as values change (WCAG `number-tabular` rule).

### 4.2 M3 Type Role Assignments

| M3 Role | Family | Weight | Size | Primary use |
|---|---|---|---|---|
| `displaySmall` | BarlowCondensed | Bold (700) | 36sp | Large stat readouts (e.g. 1RM value, volume total) |
| `headlineLarge` | BarlowCondensed | Bold (700) | 32sp | Section headers, screen titles |
| `headlineMedium` | BarlowCondensed | SemiBold (600) | 28sp | Exercise name in ExerciseDetailSheet header |
| `headlineSmall` | BarlowCondensed | SemiBold (600) | 24sp | Card group headers |
| `titleLarge` | BarlowCondensed | Bold (700) | 22sp | TopAppBar title (if text shown) |
| `titleMedium` | Barlow | SemiBold (600) | 16sp | Exercise name on ExerciseCard, HistoryCard workout name |
| `bodyLarge` | Barlow | Normal (400) | 16sp | Primary body content, set row data |
| `bodyMedium` | Barlow | Normal (400) | **15sp** | Secondary body content, metric values |
| `bodySmall` | Barlow | Normal (400) | **13sp** | Timestamps, dates, subtitles |
| `labelMedium` | Barlow | Medium (500) | **13sp** | Chip labels, button text |
| `labelSmall` | Barlow | Medium (500) | 12sp | Badge text, set type indicators (W / D / F) |

### 4.3 JetBrainsMono Usage Rules

`JetBrainsMono` is used **exclusively** at these sites:

- Elapsed session timer (`mm:ss`) in `ActiveWorkoutScreen` TopAppBar
- Rest countdown timer display
- Stopwatch and countdown in the Clocks tab
- Elapsed timer in `MinimizedWorkoutBar`
- Session duration on `HistoryCard`

It must not be used for: weight values, rep counts, set numbers, volume figures, or any non-timer data. Those use `Barlow` with `bodyMedium` or `bodyLarge`.

---

## 5. LightColorScheme

### Status: Implemented (v2.0)

The light palette uses dedicated tokens that pass WCAG AA on white backgrounds.

| Token | Hex | M3 Role | Contrast on white |
|---|---|---|---|
| `LightPrimary` | `#6B3FA0` | `primary` | ~6.5:1 — WCAG AA ✓ |
| `LightSecondary` | `#7D3B65` | `secondary` | ~6:1 — WCAG AA ✓ |
| `LightError` | `#B3261E` | `error` | ~5.8:1 — WCAG AA ✓ |
| `Slate50` | `#F8FAFC` | `background` | — |
| `Slate100` | `#F1F5F9` | `surface` | — |
| `Slate900` | `#0F172A` | `onBackground`, `onSurface` | ~16:1 — WCAG AAA ✓ |
| `Color(0xFFEDE7F6)` | `#EDE7F6` | `surfaceVariant` | Light violet tint for chips/rows |

---

## 6. Technical Invariants & Token Rules

1. **No raw hex in composables.** All composables must use `MaterialTheme.colorScheme.*` tokens or `MaterialTheme.semanticColors.*` extensions. Never hardcode `Color(0xFF...)` or import static color constants inside UI files. Violations are a code review block.

2. **Legacy aliases are `Color.kt`-only.** `DeepNavy`, `OledBlack`, `NeonBlue`, `ElectricBlue`, `SlateGrey`, `NavySurface`, `Slate200` must never be referenced in any composable or ViewModel. They exist only in `Color.kt` for schema stability. Future cleanup is permitted once all callers are confirmed zero.

3. **`surfaceTint = ProBackground` is intentional and mandatory.** M3 applies a primary-colored tonal overlay on elevated surfaces (Dialogs, `ModalBottomSheet`, elevated `Card`) by default. This creates a purple tint over dark backgrounds that conflicts with the OLED Pro Tracker aesthetic. Setting `surfaceTint = ProBackground (#101010)` globally in `DarkColorScheme` suppresses this overlay. Do not remove or override this setting. Do not use `tonalElevation = 0.dp` as a per-component workaround — it is verbose, fragile, and will miss new components.

4. **`MinimizedWorkoutBar` background must be `surfaceVariant`.** `WORKOUT_SPEC.md §20.1` specifies `surfaceVariant` background with a 4dp `primary` left border. The current codebase (`PowerMeNavigation.kt`) incorrectly uses `color = MaterialTheme.colorScheme.primary` for the `Surface`. **The spec is the source of truth. The code must be refactored.** Using `primary` as a full background color competes visually with the bottom navigation indicator and fails the low-emphasis requirement of a passive persistent bar. Note: `surfaceVariant` is now `#282828` in the v6.0 palette.

5. **WCAG contrast compliance — audit (v6.0 palette):**
   - `ProCloudGrey (#EDEDEF)` on `ProBackground (#101010)`: ~18.2:1 — WCAG AAA ✓ (brighter and neutral vs prior lavender-tinted #E8E4F0)
   - `ProSubGrey (#A0A0A0)` on `ProBackground (#101010)`: ~7.9:1 — WCAG AAA ✓
   - `ProViolet (#9B7DDB)` on `ProBackground (#101010)`: ~7.6:1 — WCAG AAA ✓
   - `ProViolet (#9B7DDB)` on `ProSurfaceVar (#282828)`: ~4.5:1 — WCAG AA ✓ (passes normal text threshold)
   - `TimerGreen (#4CC990)` on `ProBackground (#101010)`: ~9.2:1 — WCAG AAA ✓

6. **`MainAppScaffold` color assignments** are owned by `NAVIGATION_SPEC.md §10`. Do not re-specify TopAppBar or NavigationBar colors here — cross-reference that section.

7. **BarlowCondensed minimum size is 20sp.** Below 20sp, condensed letterforms lose legibility — particularly under physical exertion. Any headline or label smaller than 20sp must use `Barlow` (regular width), not `BarlowCondensed`.

8. **`TimerGreen` and `FormCuesGold` have exactly one use site each.** See §2.2 for `TimerGreen` restrictions. `FormCuesGold` is restricted to the Form Cues banner background in `ExerciseDetailSheet`. If a second use site is proposed, it requires a spec update and explicit approval — it is not a generic "success" or "highlight" color.

9. **No inline `RoundedCornerShape()` in composables.** All corner radii are defined in `ui/theme/Shape.kt` via the `PowerMeShapes` object. Use `MaterialTheme.shapes.extraSmall|small|medium|large|extraLarge`. Hardcoded inline shape values will drift from the design system.

10. **No inline `OutlinedTextFieldDefaults.colors()` overrides** (with the standard 4-property block). Use `PowerMeDefaults.outlinedTextFieldColors()` from `ui/theme/Defaults.kt`. Exceptions: fields with non-standard container colors (e.g. `surfaceVariant` background) or intentionally transparent borders (inline editing) may use explicit overrides.

11. **No inline `CardDefaults.cardColors/cardElevation`** for standard surface cards. Use `PowerMeDefaults.cardColors()` / `PowerMeDefaults.cardElevation()` (4dp) for primary cards, or `PowerMeDefaults.subtleCardElevation()` (2dp) for secondary/settings cards. Cards with a non-surface container color are exempt.

---

## §7 Shape System

**File:** `app/src/main/java/com/powerme/app/ui/theme/Shape.kt`

Passed to `MaterialTheme(shapes = PowerMeShapes)` in `Theme.kt`. Applies automatically to all Material 3 components.

| Token | Radius | Use |
|---|---|---|
| `extraSmall` | 6dp | chips, badges, small pill backgrounds, completion buttons |
| `small` | 10dp | primary buttons, text fields, `WorkoutInputField` |
| `medium` | 16dp | cards, dialogs, bottom sheet handles |
| `large` | 24dp | `ModalBottomSheet`, large card variants |
| `extraLarge` | 32dp | FAB, full-width action buttons |

**M3 defaults (old):** 4/8/12/16/28dp. The new values are intentionally rounder for a more organic feel.

---

## §8 Spacing Tokens

**File:** `app/src/main/java/com/powerme/app/ui/theme/Shape.kt` (bottom of file)

```kotlin
object Spacing {
    val xs   = 4.dp    // tight gaps, icon padding
    val sm   = 8.dp    // standard between-item gap
    val md   = 12.dp   // card inner padding (compact)
    val lg   = 16.dp   // standard screen/card padding
    val xl   = 20.dp   // section separation
    val xxl  = 24.dp   // large section / modal padding
    val xxxl = 32.dp   // full-page padding
}
```

Migration is incremental — hardcoded `.dp` literals and `Spacing.*` tokens are both acceptable. Use `Spacing.*` for new code.

---

## §9 Component Defaults

**File:** `app/src/main/java/com/powerme/app/ui/theme/Defaults.kt`

`PowerMeDefaults` provides `@Composable` factory functions for common component configurations:

| Function | Returns | Notes |
|---|---|---|
| `outlinedTextFieldColors()` | `OutlinedTextFieldDefaults.colors(...)` | Unfocused border uses `outlineVariant` (neutral) instead of `primary.copy(0.4f)` — eliminates purple-tinted unfocused borders |
| `cardColors()` | `CardDefaults.cardColors(surface)` | Standard surface card container |
| `cardElevation()` | `CardDefaults.cardElevation(4dp)` | Primary card depth (exercise, history, routine cards) |
| `subtleCardElevation()` | `CardDefaults.cardElevation(2dp)` | Secondary card depth (settings rows, template builder rows) |

### §9.1 Switch / Toggle — mandatory color pattern

Every `Switch(...)` in the app must use this exact colors block. Never hardcode `Color.White`/`Color.Black` or branch on `isSystemInDarkTheme()` for switch colors — those break in dark/light mode.

```kotlin
colors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
)
```

Result: near-white thumb in dark mode / near-black in light mode; violet checked track; dark-grey/lavender unchecked track.

### §9.2 Multi-field keyboard navigation — mandatory

Whenever a screen has two or more text input fields in sequence, the Enter/Next key must advance focus to the next field. Apply `ImeAction.Next` + `focusManager.moveFocus(FocusDirection.Down)` on every non-final field, and `ImeAction.Done` on the last. For horizontally adjacent fields (e.g. ft / in), use `FocusDirection.Right` instead.

Reference implementation: `ProfileSetupScreen.kt` + `ProfileTextField`.

```kotlin
// Non-final field
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

// Final field
keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
```

### §9.3 Input Field Conventions

1. **Keyboard type matrix.**
   - `KeyboardType.Decimal` — weight, body-fat %, height-cm, sleep hours, any fractional measurement.
   - `KeyboardType.Number` — reps, sets, seconds, ages, children, inches, any integer count.
   - `KeyboardType.Email` — email fields.
   - `KeyboardType.Password` + `PasswordVisualTransformation()` — all password fields.
   - `KeyboardType.Text` (default) — names, notes, labels.

2. **`SurgicalValidator` is mandatory** for weight/reps/height/body-fat inputs that round-trip through a ViewModel. Inline `toDoubleOrNull()` / `toIntOrNull()` is reserved for ephemeral, never-persisted conversions.

3. **`OutlinedTextField` is the exclusive M3 text primitive.** `BasicTextField` only for custom pill/accessory widgets (e.g. `WorkoutInputField`). `TextField` is forbidden.

4. **`OutlinedTextField` colors MUST use `PowerMeDefaults.outlinedTextFieldColors()`.** Never inline `OutlinedTextFieldDefaults.colors(...)`.

5. **Units inside the label as a parenthesized suffix** — `"Weight (kg)"`, `"Height (ft)"`, `"Body Fat %"`. Never in `trailingIcon`, `supportingText`, `prefix`, or `suffix`.

6. **Helper text** — below the field, `fontSize = 11.sp`, `color = primary.copy(alpha = 0.5f)`, `padding(start = 4.dp, top = 2.dp)`. Reference: `ProfileTextField` in `ProfileWidgets.kt:64-70`.

7. **Error text** — below the field, `fontSize = 12.sp`, `color = colorScheme.error`.

8. **Field width** — standalone: `Modifier.fillMaxWidth()`. Row-paired (e.g. ft/in): `Row(horizontalArrangement = Arrangement.spacedBy(8.dp))` with each field `Modifier.weight(1f)`.

9. **`ImeAction.Done` must not be a no-op.** The last field MUST either submit the form or `focusManager.clearFocus()`. (Extends §9.2.)

### §9.4 Dialog & Transient Surface Conventions

1. **AlertDialog layout** — `confirmButton` and `dismissButton` are both `TextButton`s. Confirm = affirmative (right), dismiss = cancel (left). `onDismissRequest` is never suppressed.

2. **Destructive confirmation — tier 1** (delete routine/exercise/workout, discard changes): confirm is a `TextButton` whose `Text` sets `color = MaterialTheme.colorScheme.error`; title ends with `?`; dismiss label is non-committal (`"Cancel"`, `"Keep Editing"`, `"Keep Going"`); body states what is lost ("This cannot be undone.", "will not be saved.").

3. **Destructive confirmation — tier 2** (account deletion and equivalents only): confirm escalates to a filled `Button(colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error))`; title text also error-tinted.

4. **Unsaved-changes guard** — screens with editable state expose `hasUnsavedChanges()`; `BackHandler` routes to a dialog with exact wording: title `"Discard Changes?"`, confirm `"Discard"` (error-tinted TextButton), dismiss `"Keep Editing"`. Wording is verbatim.

5. **`ModalBottomSheet`** — keep the default drag handle (never override). Content `Column` uses `horizontal = 16–24.dp` padding plus `.navigationBarsPadding()`. Do not override `containerColor`. For half-screen+ content use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.

6. **Snackbar scope** — snackbars confirm user-initiated actions only: no `actionLabel`, default duration, no success/error variants. Errors are shown **inline** as `Text(fontSize = 12.sp, color = colorScheme.error)` adjacent to the failing field — never in a snackbar.

### §9.5 State Presentation

1. **Screen-level loading** — `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }`. No shimmer, no skeletons.

2. **Inline loading (inside a button)** — `CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)`.

3. **`LinearProgressIndicator`** is reserved for determinate timed progress (rest timers, multi-step imports): `Modifier.fillMaxWidth().height(3.dp)`.

4. **Empty state** — use `EmptySectionPlaceholder` (bodySmall, `onSurfaceVariant.copy(alpha = 0.5f)`, centered, `padding(horizontal = 16.dp, vertical = 16.dp)`). Currently `internal` to `ui/exercises/detail/DetailComponents.kt:49-64`; to be promoted to `ui/components/`.

5. **FABs are not used.** Primary actions go in TopAppBar actions or inline in the content area.

### §9.6 Screen Chrome & Layout

1. **Tab screens are chromeless** — `WorkoutsScreen`, `HistoryScreen`, `ExercisesScreen`, `ToolsScreen`, `TrendsScreen` have no `Scaffold` or `TopAppBar`; the parent host owns chrome.

2. **Push/modal screens use `Scaffold` + plain `TopAppBar`** — never `CenterAlignedTopAppBar`, `LargeTopAppBar`, or `MediumTopAppBar`. Always include:
   ```kotlin
   colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
   windowInsets = TopAppBarDefaults.windowInsets
   ```

3. **Screen horizontal padding = `16.dp`** at the screen root. Nested `LazyColumn` inherits — do not re-pad.

4. **Divider/Spacer rule** — `HorizontalDivider` (prefer `outlineVariant` or `outlineVariant.copy(alpha = 0.4f)`) inside cards and grouped rows. `Spacer(Modifier.height(...))` between standalone cards. No dividers between peer cards in a `LazyColumn`.

5. **Section headers** — use `SectionHeader` composable: `typography.labelMedium`, `FontWeight.SemiBold`, `color = onSurfaceVariant`, `letterSpacing = 0.8.sp`, `padding(horizontal = 16.dp, vertical = 12.dp)`. Currently `internal` to `DetailComponents.kt:29-39`; to be promoted to `ui/components/`. All existing `titleSmall` section headers migrate over time.

6. **Section dividers** (inside grouped sections) — use `SectionDivider` composable: `outline.copy(alpha = 0.3f)`, `padding(horizontal = 16.dp)`. Currently `internal` to `DetailComponents.kt:41-47`; to be promoted alongside `SectionHeader`.

### §9.7 Shape & Spacing Token Enforcement

1. **Shape tokens are mandatory.** Use `MaterialTheme.shapes.extraSmall/small/medium/large/extraLarge` (`theme/Shape.kt:9-15`). Inline `RoundedCornerShape(n.dp)` is banned on `Card`, `Surface`, `Button`, `OutlinedTextField`, and sheets. (`Shape.kt:8` already declares this rule as a code comment; this section promotes it to spec.)

2. **Spacing tokens are mandatory in new code.** Use `Spacing.xs/sm/md/lg/xl/xxl/xxxl` (`theme/Shape.kt:19-27`) for all `padding`, `Spacer`, and `Arrangement.spacedBy` values. Magic `.dp` literals (`7.dp`, `11.dp`, `14.dp`, `19.dp`, `22.dp`) are forbidden in new code.

3. **Icon size literals are exempt** — `18.dp` and `22.dp` are pervasive; an `IconSize` token is out of scope for now.
