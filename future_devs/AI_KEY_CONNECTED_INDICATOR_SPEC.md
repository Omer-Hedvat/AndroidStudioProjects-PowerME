# AI — API Key Connected Indicator

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | API key validation ✅ |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt` |

> Read `SETTINGS_SPEC.md §2.6` and `future_devs/API_KEY_VALIDATION_SPEC.md` before touching any file.

---

## Overview

The AI card in Settings shows a status line ("Using: Your key" / "Using: Default" / "No key set") but gives no visual confirmation that the user's key has been validated and is actually working. Add a green "Connected" chip — mirroring the Health Connect pattern — when the user key has been validated successfully, so users get clear positive feedback.

---

## Behaviour

### States

The existing `ApiKeyValidationState` sealed class drives the indicator:

| State | Indicator |
|---|---|
| `Validated` (user key confirmed working) | `🟢 Connected` — green `AssistChip` or `SuggestionChip` with a checkmark icon, `MaterialTheme.colorScheme.primary` tint |
| `UsingDefault` (no user key; using the built-in key) | No indicator — the status line already says "Using: Default" |
| `NoKey` | No indicator |
| `Validating` | Small `CircularProgressIndicator` (16dp) inline next to the Save button, replacing the connected chip if previously shown |
| `Error` | Existing error display (no change) |

### Placement

The "Connected" chip appears directly below the status line and above the API key text field, left-aligned. It replaces any existing validation result text in that area.

### Persistence

The `Validated` state is already persisted across launches by `ApiKeyValidationState` in `SettingsUiState`. The chip renders whenever `apiKeyValidation` is `Validated` on composition.

---

## UI Changes

In `SettingsScreen` AI card:

```kotlin
if (uiState.apiKeyValidation is ApiKeyValidationState.Validated) {
    AssistChip(
        onClick = {},
        label = { Text("Connected") },
        leadingIcon = {
            Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    )
}
```

Use `MaterialTheme.colorScheme` tokens — no hardcoded colors. Match the visual weight of the HC "Connected" indicator.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — add `AssistChip` for `Validated` state in the AI card section

---

## How to QA

1. Open Settings → AI. Enter a valid Gemini API key → tap Save.
2. Verify a green "Connected" chip with a checkmark icon appears below the status line.
3. Clear the key → verify the chip disappears and status returns to "Using: Default" or "No key set".
4. Enter an invalid key → save → verify the chip does NOT appear (error state shown instead).
5. Relaunch the app with a saved valid key → verify the chip is shown immediately on open (persisted state).
