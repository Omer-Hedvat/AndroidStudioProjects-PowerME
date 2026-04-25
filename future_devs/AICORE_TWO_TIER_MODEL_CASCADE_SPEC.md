# AICore Two-Tier Model Cascade: E4B Preferred, E2B Fallback

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Status** | `abandoned` |
| **Effort** | S |
| **Depends on** | AICore on-device integration (Session B) ✅ |
| **Blocks** | — |
| **Touches** | `ai/AiCoreAvailability.kt`, `ai/OnDeviceWorkoutParser.kt`, `ai/WorkoutParserRouter.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `AI_SPEC.md` |

---

## Why Abandoned

Field testing on Samsung Galaxy S25 Ultra revealed that Samsung's Galaxy AI stack does **not** register Android AICore Feature IDs 636 (Gemma 4B / E4B) or 645 (Gemma 2B / E2B). Samsung routes on-device LLM requests through a proprietary OS layer that is not exposed via the standard `ModelPreference`/`ModelVariant` API surface.

Attempting to select variants explicitly at the app level caused `FEATURE_NOT_FOUND` exceptions on Samsung flagships — devices that do have capable on-device AI hardware and models. The only viable abstraction is `Generation.getClient()` with no arguments, which delegates routing to the OS bridge appropriate for each manufacturer.

App-level variant selection is therefore moot until upstream fragmentation resolves (Google/Samsung aligning on a common AICore feature ID surface). This spec is abandoned in favour of the current single-tier + cloud-fallback architecture described in `AI_SPEC.md §12`.

---

## Original Overview (for reference)

The Session B AICore integration treated on-device inference as a single-model path (`AiCoreStatus.Ready / NeedsDownload / NotSupported`). Android AICore actually surfaces two Gemma variants at runtime — the 4B (higher quality, capable devices) and the 2B (broader compatibility, lower-end devices). This feature extends `AiCoreAvailability` to check E4B first and fall back to E2B, giving every capable device the best model it can run while still reaching devices that can only run the smaller model.

The cascade is transparent to `AiWorkoutViewModel` — the `WorkoutTextParser` interface is unchanged. Only `AiCoreAvailability`, `OnDeviceWorkoutParser`, `WorkoutParserRouter`, and the Settings status row need to change.

---

## Behaviour

### Availability state

`AiCoreStatus` gains a variant discriminator:

```kotlin
sealed class AiCoreStatus {
    object NotSupported : AiCoreStatus()
    data class NeedsDownload(val variant: ModelVariant) : AiCoreStatus()
    data class Ready(val variant: ModelVariant) : AiCoreStatus()
}

enum class ModelVariant { E4B, E2B }
```

### `AiCoreAvailability.check()` cascade

```
1. Check E4B (Gemma 4B)
   → AVAILABLE     → Ready(E4B)
   → DOWNLOADABLE  → NeedsDownload(E4B)
   → DOWNLOADING   → NeedsDownload(E4B)
   → else          → fall through

2. Check E2B (Gemma 2B)
   → AVAILABLE     → Ready(E2B)
   → DOWNLOADABLE  → NeedsDownload(E2B)
   → DOWNLOADING   → NeedsDownload(E2B)
   → else          → NotSupported
```

If `createModel()` throws for E4B (unsupported hardware), catch and proceed to E2B. If E2B also throws, return `NotSupported`.

### `WorkoutParserRouter` cascade

```
Ready(E4B)          → OnDeviceWorkoutParser(E4B) + log "on_device_e4b"
NeedsDownload(E4B)  → triggerDownload(E4B) + cloud fallback + log "cloud"
Ready(E2B)          → OnDeviceWorkoutParser(E2B) + log "on_device_e2b"
NeedsDownload(E2B)  → triggerDownload(E2B) + cloud fallback + log "cloud"
NotSupported        → cloud fallback (silent) + log "cloud"
```

### `OnDeviceWorkoutParser`

Accepts a `ModelVariant` parameter (or receives a pre-built `GenerativeModel` from the router). The same inference logic applies to both variants — only the model client differs.

### `AiCoreDownloadManager`

`triggerDownload(variant: ModelVariant)` — starts the download for the specific variant. `DownloadState` and `downloadState` flow are unchanged (shared, last-writer-wins if both trigger simultaneously).

### Analytics

`logAiGeneration(backend, exerciseCount)` — backend values updated to `"on_device_e4b"`, `"on_device_e2b"`, `"cloud"` (backward-compatible — existing `"on_device"` value is retired by this change).

---

## UI Changes

**Settings → AI card status row** — shows which model is active:

| State | Display string |
|---|---|
| `Ready(E4B)` | `On-device AI: Gemma 4B` |
| `Ready(E2B)` | `On-device AI: Gemma 2B` |
| `NeedsDownload(*)` | `On-device AI: Downloading…` |
| `NotSupported` | `On-device AI: Not available (using cloud)` |

Color tokens unchanged: `primary` for Ready, `onSurfaceVariant` for others.

No changes to the `AiWorkoutGenerationScreen` download banner — it still observes `DownloadState` from `AiCoreDownloadManager`.

---

## Files to Touch

- `ai/AiCoreAvailability.kt` — extend `AiCoreStatus` with `ModelVariant`; two-pass `check()` trying E4B then E2B
- `ai/OnDeviceWorkoutParser.kt` — accept `ModelVariant` (or `GenerativeModel`) so a single class handles both variants
- `ai/WorkoutParserRouter.kt` — update routing `when` expression for new `AiCoreStatus` subtypes; pass variant to download trigger
- `ui/settings/SettingsViewModel.kt` — display string derivation for new status values
- `ui/settings/SettingsScreen.kt` — confirm status row wording matches table above
- `AI_SPEC.md` — update §12.3 and §12.4 to reflect E4B/E2B cascade; mark §12 status as Shipped once this lands

---

## Tests to Add / Update

- `WorkoutParserRouterTest.kt` — add cases: E4B Ready → on-device E4B; E4B NeedsDownload → cloud + trigger E4B download; E2B fallback when E4B NotSupported; E2B NeedsDownload → cloud + trigger E2B download; analytics backend strings correct
- `AiCoreAvailabilityTest.kt` (new) — cases: E4B available; E4B needs download; E4B not supported falls through to E2B; E2B not supported → NotSupported; both throw → NotSupported

---

## How to QA

1. Pixel 8+ with Gemma 4B downloaded → Settings → AI shows "Gemma 4B"; AI generation logs `on_device_e4b`
2. Device that supports only 2B → Settings → AI shows "Gemma 2B"; AI generation logs `on_device_e2b`
3. E4B needs download → banner shows; cloud used; Settings shows "Downloading…"; after download shows "Gemma 4B"
4. Old device → no banner; Settings shows "Not available (using cloud)"; cloud used silently
