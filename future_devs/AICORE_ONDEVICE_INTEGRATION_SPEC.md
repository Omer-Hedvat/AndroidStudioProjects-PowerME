# AICore On-Device Integration (Session B)

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Status** | `completed` |
| **Effort** | M |
| **Depends on** | AI parser interface layer ✅, AI ViewModel interface wiring ✅ |
| **Blocks** | — |
| **Touches** | `app/build.gradle.kts`, `libs.versions.toml`, `ai/AiCoreAvailability.kt`, `ai/AiCoreDownloadManager.kt`, `ai/OnDeviceWorkoutParser.kt`, `ai/WorkoutParserRouter.kt`, `di/AiModule.kt`, `ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `ui/workouts/ai/AiWorkoutViewModel.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `AI_SPEC.md` |

---

## Overview

Adds the on-device LLM inference path using the ML Kit GenAI Prompt API (backed by Gemini Nano / AICore system service). On supported devices (Pixel 8+, Samsung Galaxy S24+), workout parsing runs entirely on-device — no internet required, no API key consumed. On unsupported devices, the router falls back transparently to cloud Gemini.

When the model needs to be downloaded (AICore available but model not yet present), the router triggers a background Play Services download and falls back to cloud during the download window. A non-blocking banner in the AI Workout screen shows download progress.

---

## Behaviour

- `AiCoreAvailability` checks model status: `Ready` / `NeedsDownload` / `NotSupported`
- `WorkoutParserRouter` routing:
  - `Ready` → `OnDeviceWorkoutParser`
  - `NeedsDownload` → trigger download (guarded by `AtomicBoolean`) + cloud fallback
  - `NotSupported` → cloud fallback (silent, no indicator)
- `AiCoreDownloadManager` manages `StateFlow<DownloadState>`: Idle / Downloading(progress) / Complete / Failed
- Banner on AI Workout screen auto-dismisses when download completes
- Settings → AI card shows read-only status row: "On-device AI: Ready / Downloading… / Not available"
- Analytics: `logAiGeneration(backend, exerciseCount)` fires on each parse with backend = "on_device" or "cloud"

---

## UI Changes

- `AiWorkoutGenerationScreen.kt` — non-blocking top banner when `DownloadState.Downloading`; auto-hides on `Complete`
- `SettingsScreen.kt` — new row in AI card below API key section: "On-device AI: Ready / Downloading… / Not available (using cloud)"
- Colors: `MaterialTheme.colorScheme.primary` for Ready, `MaterialTheme.colorScheme.onSurfaceVariant` for Downloading/NotAvailable

---

## Files to Touch

- `app/build.gradle.kts` — add `com.google.mlkit:genai-prompt:1.0.0-beta2`
- `libs.versions.toml` — add `mlkitGenaiPrompt` version entry
- `ai/AiCoreAvailability.kt` — new: `@Singleton`, `AiCoreStatus` sealed class, `fun check()`
- `ai/AiCoreDownloadManager.kt` — new: `@Singleton`, `DownloadState` sealed class, `StateFlow`, `AtomicBoolean` guard
- `ai/OnDeviceWorkoutParser.kt` — new: implements `WorkoutTextParser`, uses ML Kit + `WorkoutPromptUtils`
- `ai/WorkoutParserRouter.kt` — edit: inject new components, add routing logic
- `di/AiModule.kt` — edit: provide `AiCoreAvailability`, `AiCoreDownloadManager`, `OnDeviceWorkoutParser`
- `ui/workouts/ai/AiWorkoutViewModel.kt` — edit: expose `downloadState` flow
- `ui/workouts/ai/AiWorkoutGenerationScreen.kt` — edit: download banner composable
- `ui/settings/SettingsViewModel.kt` — edit: inject `AiCoreAvailability`, add `onDeviceAiStatus` to state
- `ui/settings/SettingsScreen.kt` — edit: status row in AI card
- `AI_SPEC.md` — edit: §12 status → Shipped, §2 status table

---

## How to QA

1. On-device path (Pixel 8+ with Gemma downloaded): AI generates workout → confirm Analytics logs `backend=on_device`
2. Download path: Fresh Pixel 8+ without model → banner appears, cloud fallback works, banner dismisses after download
3. Fallback path (emulator / old device): No banner, cloud Gemini used silently, Analytics logs `backend=cloud`
4. Settings → AI card shows correct status for each device state
