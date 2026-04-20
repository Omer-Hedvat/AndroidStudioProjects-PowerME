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

Entry point lives on the Workouts tab:

```
Workouts tab
  └─ Quick Start
      └─ "Generate with AI"                 (WorkoutsScreen.kt:112-131)
          └─ navigate → AI_WORKOUT route    (PowerMeNavigation.kt:94)
              ├─ INPUT step
              │    ├─ Text mode: multiline field
              │    └─ Photo mode: camera / gallery → ML Kit OCR → editable text
              ↓ processTextInput()
              └─ PREVIEW step                (AiWorkoutGenerationScreen.kt:327-388)
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
- **Routine-first entry point** — on `+ New Routine`, offer
  `Build with AI` vs `Build manually`. Open question: add alongside
  the existing Quick Start "Generate with AI", or make the routine-first
  chooser the primary path. Decide when this is picked up for build.
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

## 10. Pointers

| Where | For |
|---|---|
| `AI_BACKLOG.md` | Full long-horizon brainstorm (46 items) + hybrid cloud/on-device rationale |
| `ROADMAP.md` P7 | Where AI sits in the overall phase plan; enhancement work lands as new rows when promoted |
| `bugs_to_fix/BUG_TRACKER.md` | AI-related bugs (none open at time of writing) |
| `CLAUDE.md` → Spec Index | Discovery of this file |
| `WORKOUT_SPEC.md` | How a generated plan becomes an active workout (state machine, set semantics) |
| `EXERCISES_SPEC.md` | Exercise library, search semantics that `ExerciseMatcher` leans on |

---

## 11. How to change this spec

- **Any PR that touches AI** — prompt, schema, model, key flow, entry
  point, copy, thresholds, telemetry — **must** update this file in the
  same PR. If the change doesn't fit any existing section, add a new
  one; do not create a parallel doc.
- **Promoting an enhancement** from §8 to shipped work — move the item
  into §3–§7 (or add subsections there) after the PR that ships it, and
  cut a new ROADMAP row. Delete the item from §8 once shipped.
- **Permanent no** — to rule a backlog idea out forever, add it to §9
  with a one-line rationale. Do not silently drop from `AI_BACKLOG.md`.
