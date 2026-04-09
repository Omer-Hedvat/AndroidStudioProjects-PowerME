# THEME_SPEC.md — PowerME Theme System

**Status:** ✅ Complete (v2.0 — April 2026)
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

## 1. Color Token Reference — Pro Tracker v5.0 Dark Palette

### 1.1 DarkColorScheme — Core Token Map

| Token Name | Hex | M3 Role | Notes |
|---|---|---|---|
| `ProBackground` | `#0F0D13` | `background`, `surfaceTint` | Near-black with subtle purple warmth. Not pure black — reduces eye fatigue. Also used as `surfaceTint` to suppress M3 purple elevation overlay (see §6.3) |
| `ProSurface` | `#1C1A24` | `surface` | Cards, TopAppBar, NavigationBar container |
| `ProSurfaceVar` | `#28253A` | `surfaceVariant` | Purple-tinted chips, row backgrounds, input backgrounds |
| `ProInputPill` | `#221F30` | *(unmapped — custom)* | Input field backgrounds only. Too close to `surfaceVariant` to occupy a distinct M3 role; kept separate for input context clarity |
| `ProViolet` | `#9B7DDB` | `primary` | FABs, focus borders, active toggle indicators, exercise name text, nav indicator pill. Desaturated lavender-violet — not neon. |
| `ProMagenta` | `#9E6B8A` | `secondary` | Superset spine (4dp left border), secondary chips. Dusty rose/mauve. |
| `ProCloudGrey` | `#E8E4F0` | `onSurface`, `onBackground` | High-emphasis text — lavender-tinted off-white. Contrast ~15.5:1 on `ProBackground` (AAA, non-fatiguing) |
| `ProSubGrey` | `#9E99AB` | `onSurfaceVariant` | Medium-emphasis text, subtitles, timestamps. Purple-tinted. |
| `ProError` | `#E05555` | `error` | Destructive actions (delete, cancel edit), error states. Desaturated from neon red. |
| `ProOutline` | `#3A3650` | `outline` | Purple-tinted unfocused `OutlinedTextField` borders, dividers |
| *(unnamed)* | `#2D2052` | `primaryContainer` | Muted deep purple container (e.g. selected chip background, FAB container) |
| *(unnamed)* | `#E0D4F0` | `onPrimaryContainer` | Muted lavender — text/icon on `primaryContainer` surfaces |

### 1.2 Derived / Implicit Tokens

The following are computed by the M3 engine and not explicitly set — document here for awareness:

| M3 Role | Effective Value | How resolved |
|---|---|---|
| `onPrimary` | `#0F0D13` | Explicitly set to `ProBackground` |
| `onSecondary` | `#0F0D13` | Explicitly set to `ProBackground` |
| `onError` | `#0F0D13` | Explicitly set to `ProBackground` |
| `errorContainer` | *(not explicitly set)* | M3 default tonal — avoid in composables, use `error` directly |

---

## 2. Semantic Color Contexts

Semantic colors are domain-specific and exist **outside the M3 token system**. They must be accessed via the `LocalPowerMeColors` composition local extension — never as raw `Color.kt` constants in composables.

| Token | Hex | Exclusive use — nowhere else |
|---|---|---|
| `TimerGreen` | `#4CC990` | **[FINISH WORKOUT]** button (text + `BorderStroke`) · Completed set checkmarks. Desaturated emerald. |
| `TimerRed` | `#E04458` | Rest state indicator. Desaturated from neon red. |
| `FormCuesGold` | `#5A4D1A` | Form Cues persistent banner background in `ExerciseDetailSheet` only |

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

3. **`surfaceTint = ProBackground` is intentional and mandatory.** M3 applies a primary-colored tonal overlay on elevated surfaces (Dialogs, `ModalBottomSheet`, elevated `Card`) by default. This creates a purple tint over dark backgrounds that conflicts with the OLED Pro Tracker aesthetic. Setting `surfaceTint = ProBackground (#0F0D13)` globally in `DarkColorScheme` suppresses this overlay. Do not remove or override this setting. Do not use `tonalElevation = 0.dp` as a per-component workaround — it is verbose, fragile, and will miss new components.

4. **`MinimizedWorkoutBar` background must be `surfaceVariant`.** `WORKOUT_SPEC.md §20.1` specifies `surfaceVariant` background with a 4dp `primary` left border. The current codebase (`PowerMeNavigation.kt`) incorrectly uses `color = MaterialTheme.colorScheme.primary` for the `Surface`. **The spec is the source of truth. The code must be refactored.** Using `primary` as a full background color competes visually with the bottom navigation indicator and fails the low-emphasis requirement of a passive persistent bar. Note: `surfaceVariant` is now `#28253A` in the v5.0 palette.

5. **WCAG contrast compliance — audit (v5.0 palette):**
   - `ProCloudGrey (#E8E4F0)` on `ProBackground (#0F0D13)`: ~15.5:1 — WCAG AAA ✓ (comfortable, non-fatiguing vs pure 21:1)
   - `ProSubGrey (#9E99AB)` on `ProBackground (#0F0D13)`: ~7.3:1 — WCAG AAA ✓
   - `ProViolet (#9B7DDB)` on `ProBackground (#0F0D13)`: ~7.5:1 — WCAG AAA ✓
   - `ProViolet (#9B7DDB)` on `ProSurfaceVar (#28253A)`: ~4.6:1 — WCAG AA ✓ (passes normal text threshold)
   - `TimerGreen (#4CC990)` on `ProBackground (#0F0D13)`: ~9:1 — WCAG AAA ✓

6. **`MainAppScaffold` color assignments** are owned by `NAVIGATION_SPEC.md §10`. Do not re-specify TopAppBar or NavigationBar colors here — cross-reference that section.

7. **BarlowCondensed minimum size is 20sp.** Below 20sp, condensed letterforms lose legibility — particularly under physical exertion. Any headline or label smaller than 20sp must use `Barlow` (regular width), not `BarlowCondensed`.

8. **`TimerGreen` and `FormCuesGold` have exactly one use site each.** See §2.2 for `TimerGreen` restrictions. `FormCuesGold` is restricted to the Form Cues banner background in `ExerciseDetailSheet`. If a second use site is proposed, it requires a spec update and explicit approval — it is not a generic "success" or "highlight" color.
