# Firebase AI Logic SDK Migration

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Epic** | [AI Workout Generation](../AI_SPEC.md) |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | gemini-2.5-flash parity, any future model upgrades |
| **Touches** | `app/build.gradle.kts`, `app/src/main/java/com/powerme/app/ai/GeminiWorkoutParser.kt`, `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` |

---

## Overview

`com.google.ai.client.generativeai:generativeai:0.9.0` was the final release of the deprecated Google AI Android SDK (July 2024, archived December 2025). It was unaware of models released after that date including `gemini-2.5-flash`.

**Actual implementation:** Rather than migrate to the Firebase AI Logic SDK (which does not support user-provided API keys), the fix replaces the deprecated SDK with direct OkHttp REST calls to `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`. OkHttp was already a transitive dependency via Firebase BOM 33.0.0; it is now declared explicitly at `4.12.0`. This approach supports any Gemini model string and preserves the `GeminiKeyResolver` / `EncryptedSharedPreferences` user-key flow unchanged.

---

## Behaviour

- `GeminiWorkoutParser` makes a direct POST to the Gemini v1beta endpoint with the API key as a URL query param.
- `SettingsViewModel.callGeminiForValidation` does the same for key validation; the test mock boundary (`override suspend fun callGeminiForValidation`) is unchanged.
- Model name `"gemini-2.5-flash"` resolves correctly. Future model names require only a constant change.
- All prompt logic, JSON parsing, and error handling unchanged.

---

## UI Changes

None.

---

## Files Touched

- `app/build.gradle.kts` — removed `generativeai:0.9.0`; added `okhttp3:okhttp:4.12.0`
- `app/src/main/java/com/powerme/app/ai/GeminiWorkoutParser.kt` — rewritten to use OkHttp + kotlinx.serialization.json REST call; `GenerativeModel` removed
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — removed `GenerativeModel` import; `callGeminiForValidation` now uses OkHttp REST; `createGenerativeModel` removed

---

## How to QA

1. Build `:app:assembleDebug` — zero errors.
2. Run `:app:testDebugUnitTest` — all passing.
3. Settings → AI card → enter Gemini API key → Save → confirm ✅ Valid badge appears.
4. Workouts → AI tab → enter `"4x5 bench press, 3x8 squat"` → Parse → confirm two exercises returned with correct sets/reps.
