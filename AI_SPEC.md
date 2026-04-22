# PowerME — AI Spec

> **Single source of truth for everything AI in PowerME.** If a change
> touches the AI feature — prompt, schema, matching, model, key handling,
> entry point, UX — update this file in the same PR. Do not create a
> parallel AI spec. Long-horizon brainstorm ideas live in `AI_BACKLOG.md`
> and are explicitly out of scope here until promoted.

---

## 1. Overview

AI turns free text or a photo of a routine into a ready-to-start
workout. The user types a description (e.g. `"3x8 bench press, 4x5 squat"`
or `"push day, 5 exercises"`) or snaps a photo of a whiteboard / printed
program. Gemini extracts a structured list; the app matches exercise
names to its library; the user previews, edits, and then chooses
**Start Workout** (one-off) or **Save as Routine** (reusable).

---

## 2. Status & scope

| Item | Status |
|---|---|
| Core text → workout | Shipped (P7 `wrapped`) |
| Core photo → OCR → workout | Shipped |
| Preview + edit + Start / Save as Routine | Shipped |
| Exercise matching (4-tier cascade, 0.85 threshold) | Shipped |
| Hybrid API key + Settings → AI section | Shipped (§8.1 first item) |
| Remaining enhancement roadmap (§8) | Not started |
| On-device LLM (AICore / Gemma) | Architecture decided (§12), not implemented |
| Synonym learning system (§8.8) | Designed, not implemented |

**In scope of this spec:** the entire AI feature — what exists today
(§3–§7) and the prioritized gaps (§8).

**Out of scope:** long-horizon ideas that aren't ready to build (see
`AI_BACKLOG.md`). Permanently ruled out items are listed in §9.

---

## 3. Shipped architecture

Cloud + on-device hybrid, as it actually is:

| Layer | What | Where |
|---|---|---|
| LLM | **Gemini 2.0 Flash** via `google.ai.client.generativeai` | `ai/GeminiWorkoutParser.kt` |
| API key | **`GeminiKeyResolver`**: user key (EncryptedSharedPreferences) → `BuildConfig.GEMINI_API_KEY` → `NoKey` error | `ai/GeminiKeyResolver.kt`, `data/secure/EncryptedSecurePreferencesStore.kt`, `app/build.gradle.kts` |
| OCR | **ML Kit Text Recognition** (on-device, no model download) | `ai/TextRecognitionService.kt` |
| Matching | 4-tier cascade (EXACT / SYNONYM / FUZZY ≥ 0.85 / UNMATCHED) | `util/ExerciseMatcher.kt:11`, `util/JaroWinkler.kt` |
| State | `AiWorkoutViewModel` orchestrates input, parse, match, edit | `ui/workouts/ai/AiWorkoutViewModel.kt` |
| UI | Two-step Compose screen (INPUT → PREVIEW) + Settings → AI card | `ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `ui/settings/SettingsScreen.kt` |
| Persistence | `PlanExercise` → `WorkoutBootstrap` or `Routine` + `RoutineExercise` rows | `data/repository/WorkoutRepository.kt:162`, `data/repository/RoutineRepository.kt:25` |
| On-device LLM | **Gemma 4 / Gemini Nano** via Android AICore (§12) | Not yet implemented — architecture only |

No retry, quota, cache, response validation beyond JSON-array shape, or
Firebase App Check. No Room changes — drafts live in ViewModel state
only. No Firestore sync for AI state. API key is device-local only
(`secure_ai_prefs` EncryptedSharedPreferences file, never pushed to Firestore).

---

## 4. Data contract

### 4.1 Gemini JSON response (flat array)
```json
[
  { "exerciseName": "Barbell Flat Bench Press",
    "sets": 3, "reps": 8, "weight": null, "restSeconds": 90 }
]
```
Modeled as `ParsedExercise` (`GeminiWorkoutParser.kt:11-18`) with defaults
`sets=3, reps=10, weight=null, restSeconds=null`. Parser filters out
rows with blank `exerciseName` or `sets <= 0` or `reps <= 0`.

### 4.2 Plan representation (passed to repositories)
```kotlin
data class PlanExercise(
    val exerciseId: Long,
    val sets: Int,
    val reps: Int,
    val weight: Double?,
    val restSeconds: Int?
)
```
Source: `WorkoutRepository.kt:29-35`.

### 4.3 Match result
```kotlin
data class MatchResult(
    val exercise: Exercise?,
    val confidence: Double,
    val matchType: MatchType     // EXACT | SYNONYM | FUZZY | UNMATCHED
)
```
Source: `ExerciseMatcher.kt:15-19`.

---

## 5. User flow

Entry point lives on the Workouts tab via the **Quick Start ▾** dropdown:

```
Workouts tab
  └─ Quick Start ▾  (WorkoutsScreen.kt — DropdownMenu)
       ├─ Add exercises    → empty workout, no AI
       ├─ Add from picture → navigate ai_workout?mode=photo  (PowerMeNavigation.kt)
       └─ Add from text    → navigate ai_workout?mode=text   (PowerMeNavigation.kt)
            └─ AiWorkoutGenerationScreen(initialMode = PHOTO | TEXT)
                ├─ INPUT step
                │    ├─ Text mode: multiline field
                │    └─ Photo mode: camera / gallery → ML Kit OCR → editable text
                ↓ processTextInput()
                └─ PREVIEW step
                     ├─ List of PreviewExerciseCard rows
                     │    └─ match-type chip (EXACT / SYNONYM / FUZZY / UNMATCHED)
                     │    └─ UNMATCHED rows are clickable → exercise picker
                     ├─ Edit sets / reps / weight inline
                     ├─ [Start Workout]  → createWorkoutFromPlan → Routes.WORKOUT
                     └─ [Save as Routine] → createRoutineFromPlan → dialog prompts name
```

Photo path prompts the user to review/edit OCR text before sending to
Gemini.

Both action buttons are enabled only when at least one exercise is
matched. Unmatched-only previews cannot be started or saved.

---

## 6. Matching behaviour

`ExerciseMatcher.matchExercise()` (`util/ExerciseMatcher.kt:33-67`),
in priority order:

1. **EXACT** — `searchName` equality after `toSearchName()` normalisation → confidence 1.0
2. **SYNONYM** — all query tokens match via `matchesSearchTokens()` (existing token/synonym mechanism) → confidence 0.95
3. **FUZZY** — Jaro-Winkler ≥ 0.85 (`FUZZY_THRESHOLD`) on `searchName` pairs → confidence = score
4. **UNMATCHED** — best score below threshold → confidence = best score, `exercise = null`

UX today shows the match type on each preview row; UNMATCHED rows are
tappable to open the library picker. Tiered UX with separate
"auto-match" vs "suggest top-3" vs "manual" bands is **not** shipped
and is in §8.

---

## 7. Files and tests

### 7.1 Code
- `app/src/main/java/com/powerme/app/ai/GeminiWorkoutParser.kt` — Gemini 2.0 Flash client + JSON parser (uses `GeminiKeyResolver` for per-call key resolution)
- `app/src/main/java/com/powerme/app/ai/GeminiKeyResolver.kt` — resolves `KeyResolution`: user key → shipped key → `NoKey`
- `app/src/main/java/com/powerme/app/data/secure/SecurePreferencesStore.kt` — interface for secure key storage
- `app/src/main/java/com/powerme/app/data/secure/EncryptedSecurePreferencesStore.kt` — prod impl (`secure_ai_prefs`, `MasterKey.Builder`, AES256_GCM/SIV). Falls back to no-op on Keystore failure.
- `app/src/main/java/com/powerme/app/di/SecurePreferencesModule.kt` — Hilt `@Binds` + `@Named("shippedGeminiKey")` provider
- `app/src/main/java/com/powerme/app/ai/TextRecognitionService.kt` — ML Kit OCR wrapper
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModel.kt` — state machine (INPUT ↔ PREVIEW), orchestration
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt` — two-step Compose UI + `PreviewExerciseCard` + `SaveRoutineDialog`
- `app/src/main/java/com/powerme/app/ui/workouts/WorkoutsScreen.kt` (lines 112–131) — entry button
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` (line 94) — `AI_WORKOUT` route
- `app/src/main/java/com/powerme/app/util/JaroWinkler.kt` — similarity primitive
- `app/src/main/java/com/powerme/app/util/ExerciseMatcher.kt` — 4-tier cascade matcher
- `app/src/main/java/com/powerme/app/data/repository/WorkoutRepository.kt` (lines 29–35, 162+) — `PlanExercise`, `createWorkoutFromPlan`
- `app/src/main/java/com/powerme/app/data/repository/RoutineRepository.kt` (lines 25–48) — `createRoutineFromPlan`
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` (lines 632–670) — `startWorkoutFromPlan`

### 7.2 Tests (unit)
- `app/src/test/java/com/powerme/app/ai/GeminiKeyResolverTest.kt` — 4 cases: user key, shipped fallback, no key, whitespace-only user key
- `app/src/test/java/com/powerme/app/ai/GeminiWorkoutParserTest.kt` — 16 cases: valid JSON, markdown fences, prose wrapping, malformed, filtering, `NoKey` path, user-key preference
- `app/src/test/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModelTest.kt` — 16 cases: initial state, text flow, error paths, edits, start/save, `API_KEY_MISSING` CTA, generic error routing
- `app/src/test/java/com/powerme/app/ui/settings/SettingsViewModelApiKeyTest.kt` — 7 cases: init with/without key, save valid, save blank rejected, clear, no Firestore push (×2)
- `app/src/test/java/com/powerme/app/util/JaroWinklerTest.kt` — 12 cases
- `app/src/test/java/com/powerme/app/util/ExerciseMatcherTest.kt` — exact / synonym / fuzzy / unmatched tiers

Note: `EncryptedSecurePreferencesStore` requires Android Keystore — not covered by unit tests. Verify via manual QA on device (instrumented tests disabled on API 36 emulator per CLAUDE.md).

---

## 8. Enhancement roadmap

Each enhancement below is a candidate for its own ROADMAP row. They're
ordered roughly by (value × readiness). Nothing here is committed until
it becomes a real roadmap item.

### 8.1 Key handling & reliability
- **Hybrid API key** — ✅ **Shipped.** User key in `EncryptedSharedPreferences`
  (`secure_ai_prefs`) via `GeminiKeyResolver`. Resolution: user key →
  shipped `BuildConfig` key → `ParseResult.error = "API_KEY_MISSING"`.
  Settings → AI card for key management. See §3 and §7.
- **Firebase App Check** — attach tokens to all shipped-key requests;
  retry-once-without on App Check failure, then surface generic error.
- **Retry policy** — transient 5xx / network timeout: 2 attempts with
  1 s → 3 s backoff. HTTP 429: single retry after 60 s. Malformed JSON:
  one retry with a stricter reminder in the user prompt.

### 8.2 Cost controls
- **Shared-key quota** — 60 generations / device / UTC day in DataStore
  (`ai_shipped_key_quota_usage_today`, `ai_shipped_key_quota_reset_epoch`).
  Banner + CTA to bring-your-own-key when exceeded.
- **Response cache** — SHA-256(prompt type + user text) → Gemini JSON,
  24 h TTL, DataStore. Shared-key only. Regenerate adds a nonce.

### 8.3 Parsing quality
- **Set-scheme parser** — expand `3x8-10`, `5/3/1`, `AMRAP`, `EMOM x N`,
  `"3 sets of 8"` into explicit sets. Ranges collapse to the **lower
  bound** (decision made this session).
- **Unit inference** — precedence: Gemini-returned unit → text
  mentions of `kg`/`lb` → `appSettings.weightUnit`.
- **Prompt refinement** — reuse synonyms and common abbreviations from
  the exercise library; ask for the lower bound of ranges; emphasise
  JSON-only output.

### 8.4 Matching UX
- **Tiered bands** — split current `FUZZY` band into:
  - ≥ 0.90 auto-match (info icon, tap to change)
  - 0.70 ≤ s < 0.90 suggest top-3 via dropdown
  - < 0.70 manual pick only
  Thresholds are provisional; tune against a labelled test set of
  ≥ 50 rows covering abbreviations, misspellings, non-English lifts.

### 8.5 UX additions
- **First-use consent sheet** — one-time modal: "AI sends your text (or
  OCR'd photo text) to Google Gemini. Images are never sent."
  Persisted in DataStore (`ai_consent_granted`).
- **Settings → AI section** — ✅ **Shipped** (key management). Consent
  toggle and telemetry toggle remain as follow-up items.
- **Routine-first entry point** — *Decided (2026-04-22):* AI is reached via
  the **Quick Start ▾ → Add from picture / Add from text** dropdown items
  (start-now accelerator). Not folded into `+ New Routine`. The `+ New Routine`
  chooser (AI vs manual) remains a future option if demand grows.
- **Regenerate action** — preview screen gains a `Regenerate` button
  that re-runs the prompt with a cache-bust nonce while preserving any
  manual match picks whose `rawName` didn't change.

### 8.6 Telemetry
- Device-local debug log (behind a developer switch) for: generation
  attempts by source, success rate, Preview → Save rate, Preview →
  Regenerate rate, manual match fixes per routine, quota-exceeded
  events, bad-JSON events.
- Remote/aggregated analytics is a separate follow-up.

### 8.7 Offline / failure UX
- Input-preserving banner when offline.
- Banner on quota exceeded with CTA to set own key.
- Specific copy for OCR-empty, zero-exercises-parsed, and
  all-unmatched outcomes.

### 8.8 Synonym learning system

Enables the matching layer to improve over time through user corrections and cross-user aggregation.

**Problem:** The hardcoded synonym list cannot anticipate every alias users type ("back squat" → Barbell Back Squat, "OHP" → Overhead Press, etc.). UNMATCHED results today require a manual library pick with no memory.

**Layer 1 — User-local synonyms (immediate, device-only)**
- When a user resolves an UNMATCHED exercise in the preview (taps to pick from library), offer: *"Always match '[raw name]' → [chosen exercise]?"*
- If accepted: insert a `user_exercise_synonyms` row — `(id UUID, rawName TEXT, exerciseId TEXT FK, useCount INT, createdAt LONG)`. New Room table, new migration required.
- `ExerciseMatcher` checks this table **before EXACT** — it becomes the highest-priority tier with confidence 1.0. `useCount` increments on each future hit.
- Updated matching priority:
  ```
  1. User synonym (local DB)     → confidence 1.0
  2. EXACT (searchName)          → confidence 1.0
  3. SYNONYM (hardcoded tokens)  → confidence 0.95
  4. FUZZY ≥ 0.85 (Jaro-Winkler)
  5. UNMATCHED → user picks → offer to save synonym
  ```

**Layer 2 — Anonymized aggregation (Firebase Analytics)**
- When a user saves a synonym, fire an Analytics event: `synonym_saved {rawName, resolvedExerciseName}`. No user ID, no workout context.
- This passively builds a corpus of real-world alias mappings across the user base over time.

**Layer 3 — Global synonym promotion (manual curation → app release)**
- Periodically review aggregated `synonym_saved` events in Firebase console.
- High-confidence, high-frequency mappings are promoted into the hardcoded synonym/token list in the next release.
- Long-term: replace manual curation with remote config delivery (no release required).

**DB impact:** new `user_exercise_synonyms` table, new Room migration. `ExerciseMatcher` gains a `UserSynonymRepository` dependency injected via Hilt. The new tier is fully optional — if the table is empty, matching behaves identically to today.

**What stays unchanged:** `ExerciseMatcher`'s existing EXACT / SYNONYM / FUZZY / UNMATCHED tiers are untouched. The user synonym tier is prepended.

---

## 9. Permanently out of scope

Never shipping — do not reopen without a new product decision:

- **#7 Form-check from video** — user scoping this session explicitly ruled it out
- **#8 Rep / RPE estimation from video** — same
- **#15 Voice logging**
- **#33 Adherence nudges** — proactive rest-day / streak / PR-forecast category
- **#34 Proactive rest-day agent**
- **#35 Next-PR forecast**

Rationale and the full 46-item brainstorm live in `AI_BACKLOG.md`.

---

## 11. Pointers

| Where | For |
|---|---|
| `AI_BACKLOG.md` | Full long-horizon brainstorm (46 items) + hybrid cloud/on-device rationale |
| `ROADMAP.md` P7 | Where AI sits in the overall phase plan; enhancement work lands as new rows when promoted |
| `bugs_to_fix/BUG_TRACKER.md` | AI-related bugs (none open at time of writing) |
| `CLAUDE.md` → Spec Index | Discovery of this file |
| `WORKOUT_SPEC.md` | How a generated plan becomes an active workout (state machine, set semantics) |
| `EXERCISES_SPEC.md` | Exercise library, search semantics that `ExerciseMatcher` leans on |
| `§12` | On-device LLM architecture (AICore / Gemma), graceful degradation, privacy |

---

## 12. On-Device LLM — Android AICore

> **Status:** Architecture decided. Not yet implemented.
> Implementation task list lives in the approved plan at `.claude/plans/partitioned-wandering-barto.md`.

---

### 12.1 Decision Summary

PowerME will use the **Android AICore API** (Google AI Edge SDK) for on-device LLM inference. This is the only approved approach for on-device AI.

Key decisions:
- **System-service model** — AICore is a native Android system service, conceptually similar to Health Connect. The app calls a platform API; it does not ship or manage model files directly.
- **No APK bundling** — `.e4b` model files will NOT be bundled into the APK. This is a hard rule (see §12.8).
- **No cross-app sandbox reads** — the app will NOT attempt to read model files from other apps' private directories (e.g. AI Test Kitchen, Edge Gallery). This is fragile, violates the Android security model, and is explicitly ruled out.
- **No user consent screens** — unlike Health Connect (which requires explicit user permission dialogs), on-device AI runs entirely locally and privately. No personal data leaves the device, so no consent flow is needed for the on-device path.

---

### 12.2 APK Footprint

**0 bytes added to the APK.**

Models are downloaded and managed by the OS via Google Play Services. PowerME only ships:
- A compile-time SDK dependency: `com.google.ai.edge:aicore` (library code only, no model weights)

This preserves a minimal download size for all users, regardless of device capability.

---

### 12.3 Supported Models

| Model | Min Device | Capability |
|---|---|---|
| **Gemma 4** (via AICore) | Pixel 8 series, recent Samsung flagships with NPU | Text generation, structured JSON output |
| **Gemini Nano** (via AICore) | Pixel 6 Pro, Pixel 7+, Samsung Galaxy S24+ | Text generation |

Exact device support is determined at runtime by AICore's availability API — PowerME does not maintain a device allowlist. If AICore reports `NotAvailable`, the app falls back to cloud (§12.4).

---

### 12.4 Availability Detection & Graceful Degradation

```
AICore available + model downloaded      → on-device LLM (Gemma / Gemini Nano)
                                           silent, private, no internet required

AICore available + model NOT downloaded  → trigger background download via Play Services
                                           show non-blocking UI indicator:
                                             "Downloading smart performance model…"
                                           fall back to Cloud Gemini API until download completes
                                           auto-switch to on-device once download finishes

AICore NOT available (old device / OS)   → Cloud Gemini API (Free Tier) permanently
                                           no indicator shown; user experience is identical
                                           except an internet connection is required for AI features

No cloud key + no AICore available       → existing error handling (§8.7)
```

**Invariant:** every user gets AI features (e.g. Gym Scanner) regardless of device age. On-device inference is an optimization for privacy and offline capability — it is never a hard requirement.

---

### 12.5 Download UX

When AICore is available but the model has not yet been downloaded:

- PowerME calls Play Services to **initiate the background download**. The user does not need to open any other app.
- A **non-blocking indicator** appears on the AI Workout screen (INPUT step): `"Downloading smart performance model…"` — implemented as a subtle banner or snackbar. No modal, no blocking dialog.
- The user can **continue using the app normally** — type a workout description, take a photo, log sets — while the download proceeds. The cloud Gemini API handles all AI requests during this window.
- Once download completes, **future** AI requests automatically use the on-device model. No user action required.
- A read-only status row in **Settings → AI card** shows the current state: `On-device AI: Ready` / `Downloading…` / `Not available (using cloud)`. This is informational only — no toggle.

---

### 12.6 Integration Architecture

The on-device path slots into the existing AI pipeline via a **unified parser interface**. Callers (`AiWorkoutViewModel`) do not change behavior — only the underlying inference backend differs.

Updated §3 architecture (additions in bold):

| Layer | What | Where |
|---|---|---|
| LLM (cloud) | Gemini 2.0 Flash | `ai/GeminiWorkoutParser.kt` (implements `WorkoutTextParser`) |
| **LLM (on-device)** | **Gemma 4 / Gemini Nano via AICore** | **`ai/OnDeviceWorkoutParser.kt` (implements `WorkoutTextParser`)** |
| **Parser interface** | **`WorkoutTextParser`** — unified interface over both backends | **`ai/WorkoutTextParser.kt`** |
| **Backend router** | **`WorkoutParserRouter`** — selects on-device or cloud based on `AiCoreAvailability` | **`ai/WorkoutParserRouter.kt`** |
| **Availability** | **`AiCoreAvailability`** — `Ready` / `NeedsDownload` / `NotSupported` | **`ai/AiCoreAvailability.kt`** |
| API key | `GeminiKeyResolver` (cloud path only — unchanged) | `ai/GeminiKeyResolver.kt` |
| OCR | ML Kit Text Recognition (unchanged) | `ai/TextRecognitionService.kt` |
| Matching | 4-tier cascade (unchanged) | `util/ExerciseMatcher.kt` |
| State | `AiWorkoutViewModel` — injects `WorkoutTextParser` (was `GeminiWorkoutParser`) | `ui/workouts/ai/AiWorkoutViewModel.kt` |

**Prompt reuse:** the same prompt template (`buildPrompt()`) and JSON response parser (`parseJsonResponse()`) are shared between cloud and on-device implementations — extracted to a common location to avoid divergence.

---

### 12.7 Privacy

| Path | Data leaves device? | Consent required? |
|---|---|---|
| On-device (AICore / Gemma) | No | No |
| Cloud fallback (Gemini Flash) | Yes (text sent to Google) | Yes (existing §8.5 consent flow) |

The first-use consent sheet (§8.5, not yet shipped) must be updated when this is implemented:
- If on-device is available: `"AI runs on your device — your data stays private."`
- If falling back to cloud: existing copy (`"AI sends your text to Google Gemini…"`).

---

### 12.8 Ruled-Out Approaches

Never implement without a new explicit product decision:

| Approach | Reason ruled out |
|---|---|
| Bundling `.e4b` model files in APK | Adds hundreds of MB to APK download for all users, including those on unsupported devices |
| Reading model files from other apps' private directories (e.g. AI Test Kitchen, Edge Gallery) | Violates Android security model; unreliable (app may not be installed, path may change) |
| Direct MediaPipe LLM Inference (without AICore) | Requires model download management code we'd own; duplicates what AICore provides as a system service |
| On-device as the only path (removing cloud fallback) | Excludes users on older devices; AICore availability is not guaranteed |

---

## 13. How to change this spec

- **Any PR that touches AI** — prompt, schema, model, key flow, entry
  point, copy, thresholds, telemetry — **must** update this file in the
  same PR. If the change doesn't fit any existing section, add a new
  one; do not create a parallel doc.
- **Promoting an enhancement** from §8 to shipped work — move the item
  into §3–§7 (or add subsections there) after the PR that ships it, and
  cut a new ROADMAP row. Delete the item from §8 once shipped.
- **Permanent no** — to rule a backlog idea out forever, add it to §9
  with a one-line rationale. Do not silently drop from `AI_BACKLOG.md`.
