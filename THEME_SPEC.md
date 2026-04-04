# THEME_SPEC.md — PowerME Theme System

**Status:** ✅ Complete (v1.0 — March 2026)
**Domain:** Color Tokens · ThemeMode · Typography · Semantic Colors · Token Rules

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

## 1. Color Token Reference — Pro Tracker v4.0 Dark Palette

### 1.1 DarkColorScheme — Core Token Map

| Token Name | Hex | M3 Role | Notes |
|---|---|---|---|
| `StremioBackground` | `#000000` | `background`, `surfaceTint` | Pure OLED black. Also used as `surfaceTint` to suppress M3 purple elevation overlay (see §6.3) |
| `StremioSurface` | `#252525` | `surface` | Cards, TopAppBar, NavigationBar container |
| `StremioSurfaceVar` | `#303030` | `surfaceVariant` | Neutral chips, row backgrounds, input backgrounds |
| `StremioInputPill` | `#2A2A2A` | *(unmapped — custom)* | Input field backgrounds only. Too close to `surfaceVariant` to occupy a distinct M3 role; kept separate for input context clarity |
| `StremioViolet` | `#A061FF` | `primary` | FABs, focus borders, active toggle indicators, exercise name text, nav indicator pill |
| `StremioMagenta` | `#B3478C` | `secondary` | Superset spine (4dp left border), secondary chips |
| `StremioCloudGrey` | `#FFFFFF` | `onSurface`, `onBackground` | High-emphasis text on all dark surfaces |
| `StremioSubGrey` | `#A0A0A0` | `onSurfaceVariant` | Medium-emphasis text, subtitles, timestamps |
| `StremioError` | `#FF4444` | `error` | Destructive actions (delete, cancel edit), error states |
| `ProOutline` | `#3D3D3D` | `outline` | Unfocused `OutlinedTextField` borders, dividers |
| *(unnamed)* | `#3D1A8F` | `primaryContainer` | Deep violet container (e.g. selected chip background, FAB container) |
| *(unnamed)* | `#EDD9FF` | `onPrimaryContainer` | Text/icon on `primaryContainer` surfaces |

### 1.2 Derived / Implicit Tokens

The following are computed by the M3 engine and not explicitly set — document here for awareness:

| M3 Role | Effective Value | How resolved |
|---|---|---|
| `onPrimary` | `#000000` or `#FFFFFF` | M3 selects contrast pair for `StremioViolet` |
| `onSecondary` | `#FFFFFF` | M3 contrast pair for `StremioMagenta` |
| `onError` | `#FFFFFF` | M3 contrast pair for `StremioError` |
| `errorContainer` | *(not explicitly set)* | M3 default tonal — avoid in composables, use `error` directly |

### 1.3 Legacy Aliases (Preserved — Do Not Use in Composables)

These aliases exist in `Color.kt` solely for schema stability. They must never be referenced in any composable file.

`DeepNavy` · `NavySurface` · `SlateGrey` · `OledBlack` · `NeonBlue` · `ElectricBlue` · `Slate200`

---

## 2. Semantic Color Contexts

Semantic colors are domain-specific and exist **outside the M3 token system**. They must be accessed via the `LocalPowerMeColors` composition local extension — never as raw `Color.kt` constants in composables.

| Token | Hex | Exclusive use — nowhere else |
|---|---|---|
| `TimerGreen` *(Emerald400)* | `#34D399` | **[FINISH WORKOUT]** button (text + `BorderStroke`) · Completed set checkmarks |
| `FormCuesGold` | `#5A4D1A` | Form Cues persistent banner background in `ExerciseDetailSheet` only |
| `MedicalAmber` | *(see Color.kt)* | Injury ledger UI — yellow-list exercise indicators |
| `MedicalAmberContainer` | *(see Color.kt)* | Injury ledger container backgrounds |

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

| M3 Role | Family | Weight | Primary use |
|---|---|---|---|
| `displaySmall` | BarlowCondensed | Bold (700) | Large stat readouts (e.g. 1RM value, volume total) |
| `headlineLarge` | BarlowCondensed | SemiBold (600) | Section headers, screen titles |
| `headlineMedium` | BarlowCondensed | SemiBold (600) | Exercise name in ExerciseDetailSheet header |
| `headlineSmall` | BarlowCondensed | Medium (500) | Card group headers |
| `titleLarge` | Barlow | SemiBold (600) | TopAppBar title (if text shown) |
| `titleMedium` | Barlow | SemiBold (600) | Exercise name on ExerciseCard, HistoryCard workout name |
| `bodyLarge` | Barlow | Normal (400) | Primary body content, set row data |
| `bodyMedium` | Barlow | Normal (400) | Secondary body content, metric values |
| `bodySmall` | Barlow | Normal (400) | Timestamps, dates, subtitles |
| `labelMedium` | Barlow | Medium (500) | Chip labels, button text |
| `labelSmall` | Barlow | Medium (500) | Badge text, set type indicators (W / D / F) |

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

### ⚠️ Status: Incomplete — Known Gap

The Light palette is **not fully specified**. `SYSTEM` mode on a device with light OS theme will fall back to M3 defaults, which may produce inconsistent results.

**Known issue:** `StremioViolet` (`#A061FF`) achieves only **3.5:1 contrast on a white background** — a WCAG AA fail for text and most interactive elements. It cannot be used as the light mode `primary` without adjustment.

### 5.1 Recommended Starting Point (Pending Formal Design)

The following tokens are recommended as a starting point for LightColorScheme, harmonised with the dark palette's violet/magenta identity. **These are not yet implemented** — treat as a draft for the next design sprint.

| M3 Role | Recommended Hex | Rationale |
|---|---|---|
| `background` | `#FAFAFA` | Off-white; avoids pure white harshness on OLED/LCD |
| `surface` | `#FFFFFF` | Cards and sheets |
| `surfaceVariant` | `#EDE7F6` | Light violet tint for chips and row backgrounds |
| `primary` | `#5C00CE` | Darkened violet — achieves ~7.2:1 on white (WCAG AAA) |
| `onPrimary` | `#FFFFFF` | — |
| `primaryContainer` | `#EDE0FF` | Light violet container |
| `onPrimaryContainer` | `#21005D` | Deep violet for text on light container |
| `secondary` | `#7D2B6B` | Darkened magenta for light mode legibility |
| `onSurface` | `#1C1B1F` | Near-black body text |
| `onSurfaceVariant` | `#49454F` | Medium-emphasis text on light surfaces |
| `outline` | `#79747E` | Light mode border token |
| `error` | `#B3261E` | M3 standard light error |

**Action required:** These recommendations must be validated against a device in light mode and signed off before `LightColorScheme` is formally implemented. `THEME_SPEC.md` must be updated when that work is done.

---

## 6. Technical Invariants & Token Rules

1. **No raw hex in composables.** All composables must use `MaterialTheme.colorScheme.*` tokens or `MaterialTheme.semanticColors.*` extensions. Never hardcode `Color(0xFF...)` or import static color constants inside UI files. Violations are a code review block.

2. **Legacy aliases are `Color.kt`-only.** `DeepNavy`, `OledBlack`, `NeonBlue`, `ElectricBlue`, `SlateGrey`, `NavySurface`, `Slate200` must never be referenced in any composable or ViewModel. They exist only in `Color.kt` for schema stability. Future cleanup is permitted once all callers are confirmed zero.

3. **`surfaceTint = StremioBackground` is intentional and mandatory.** M3 applies a primary-colored tonal overlay on elevated surfaces (Dialogs, `ModalBottomSheet`, elevated `Card`) by default. This creates a purple tint over dark backgrounds that conflicts with the OLED Pro Tracker aesthetic. Setting `surfaceTint = StremioBackground (#000000)` globally in `DarkColorScheme` suppresses this overlay. Do not remove or override this setting. Do not use `tonalElevation = 0.dp` as a per-component workaround — it is verbose, fragile, and will miss new components.

4. **`MinimizedWorkoutBar` background must be `surfaceVariant`.** `WORKOUT_SPEC.md §20.1` specifies `surfaceVariant` background with a 4dp `primary` left border. The current codebase (`PowerMeNavigation.kt`) incorrectly uses `color = MaterialTheme.colorScheme.primary` for the `Surface`. **The spec is the source of truth. The code must be refactored.** Using `primary` as a full background color competes visually with the bottom navigation indicator and fails the low-emphasis requirement of a passive persistent bar.

5. **WCAG contrast compliance — known exceptions:**
   - `#FFFFFF` on `#000000`: 21:1 — WCAG AAA ✓
   - `StremioSubGrey (#A0A0A0)` on `#000000`: ~8.0:1 — WCAG AAA ✓
   - `StremioViolet (#A061FF)` on `#000000`: ~5.65:1 — WCAG AA ✓ (fails AAA 7:1)
   - `StremioViolet (#A061FF)` on `StremioSurfaceVar (#1E1E1E)`: ~4.47:1 — **MARGINAL AA FAIL for normal text** (threshold 4.5:1). For UI components and large text (threshold 3:1) it passes. Chip labels using primary text on `surfaceVariant` are compliant as UI components. Do not use `StremioViolet` as body/paragraph text on `StremioSurfaceVar` backgrounds.

6. **`MainAppScaffold` color assignments** are owned by `NAVIGATION_SPEC.md §10`. Do not re-specify TopAppBar or NavigationBar colors here — cross-reference that section.

7. **BarlowCondensed minimum size is 20sp.** Below 20sp, condensed letterforms lose legibility — particularly under physical exertion. Any headline or label smaller than 20sp must use `Barlow` (regular width), not `BarlowCondensed`.

8. **`TimerGreen` and `FormCuesGold` have exactly one use site each.** See §2.2 for `TimerGreen` restrictions. `FormCuesGold` is restricted to the Form Cues banner background in `ExerciseDetailSheet`. If a second use site is proposed, it requires a spec update and explicit approval — it is not a generic "success" or "highlight" color.
