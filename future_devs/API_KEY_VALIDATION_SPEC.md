# Gemini API Key Validation in Settings

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt` |

---

## Overview

When a user saves their Gemini API key in Settings, PowerME makes a lightweight test call to the Gemini API and shows the result inline. This gives the user immediate feedback on whether their key is working, invalid, or rate-limited — rather than discovering a bad key only when they try to use the AI Gym Scanner.

---

## Behaviour

- Validation fires automatically after the user taps **Save** — no separate "Test" button needed.
- The key is saved to `EncryptedSharedPreferences` immediately (before validation completes) so it is never lost.
- Validation states:
  - **Validating** — spinner + "Checking key…"
  - **Valid** — "✓ Key is working"
  - **Quota Exceeded** — "Key is valid (quota exceeded)" — key works but rate-limited; not treated as an error
  - **Invalid(reason)** — "✗ \<reason\>" e.g. "API key is not valid"
- Validation state resets to `Idle` when the user:
  - Edits the input field (starts typing a new key)
  - Clears the saved key

---

## UI Changes

`AiSettingsCard` in `SettingsScreen.kt` — add a validation status row between "Stored only on this device." helper text and the Save/Clear button row:

- `Idle`: nothing shown
- `Validating`: `CircularProgressIndicator` (14dp) + "Checking key…" in `onSurfaceVariant`
- `Valid`: green text (`primary`) "✓ Key is working"
- `QuotaExceeded`: amber text (`tertiary`) "Key is valid (quota exceeded)"
- `Invalid`: red text (`error`) "✗ \<reason\>"

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — add `ApiKeyValidationState` sealed class, add `apiKeyValidation` field to `SettingsUiState`, update `saveUserApiKey()` to launch validation coroutine, add `validateApiKey()` private function, reset state in `updateApiKeyInput()` and `clearUserApiKey()`
- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — update `AiSettingsCard` to render validation state row

---

## How to QA

1. Go to Settings → AI card
2. Enter a valid Gemini API key and tap Save — spinner appears, then "✓ Key is working"
3. Clear the key, enter an invalid key (e.g. "notakey") and tap Save — "✗ API key is not valid"
4. Start editing a new key after a validation result — status row disappears
5. Confirm key is saved regardless of validation outcome (valid key + quota exceeded → key persists)
