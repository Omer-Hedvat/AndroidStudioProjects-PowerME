# PowerME — AI Backlog

> Brainstorm catalog of AI / agent use cases for the app. This is the pre-spec
> document. Scope decisions and hybrid cloud/on-device placement are noted
> per item. The formal spec for in-scope items will live in `AI_SPEC.md`.
>
> **Status legend:** `[scope]` = in scope for first AI spec · `[later]` = deferred
> but kept on list · `[no]` = explicitly out of scope

---

## Existing AI surface (to build on)

- **Gemini Flash** — action-block parsing for chat-driven workout edits
  (`ActionParser`, `ActionExecutor`, `ActionBlock` sealed class)
- **ML Kit Text Recognition** — `com.google.mlkit:text-recognition:16.0.1`,
  already integrated
- **CsvFormatDetector** (+ Gemini) — classifies unknown CSV formats during import
- **ExerciseMatcher + JaroWinkler** — fuzzy exercise-name matching (13 tests)
- **StressAccumulationEngine / ReadinessEngine** — deterministic signals the
  AI layer can narrate on top of

---

## 1. Programming & Coaching

1. **Adaptive routine generator** `[scope]` — goals + experience + equipment +
   days/week → full mesocycle
2. **Next-set prescription** `[later]` — per-set weight/reps from last RPE,
   readiness, stress vector
3. **Deload & plateau detector** `[later]` — pattern-recognize stalls, propose
   deload or exercise rotation
4. **Exercise substitution agent** `[later]` — overlaps with #28; kept
   separate because this is mid-workout contextual, #28 is library-level
5. **Warm-up set generator** `[scope]` — from target working weight + exercise
   profile
6. **Superset / pairing suggester** `[later]` — antagonist or
   non-conflicting joint pairings

## 2. Form, Technique & Education

7. **Form-check from video** `[no]` — explicitly out
8. **Rep & RPE estimator from video** `[no]` — out (depends on #7)
9. **Exercise explainer / cue coach** `[later]` — "how do I RDL?" + 3
   actionable cues
10. **Safety / asymmetry warnings** `[later]`

## 3. Readiness, Recovery, Stress  *(wellbeing — deferred as a block)*

11. **Readiness narrative** `[later]`
12. **Stress-vector overload warnings** `[later]`
13. **Recovery / mobility suggester** `[later]`
14. **Sleep / HRV impact coach** `[later]`

## 4. Frictionless Logging

15. **Voice logging** `[no]` — explicitly out
16. **Photo-of-notebook logging for past workouts** `[later]` — related to
    #46 but targets history import, not routine creation
17. **Smart autofill for next set** `[later]` — one-tap accept of predicted
    weight/reps
18. **Natural-language routine builder** `[scope]` — "give me a 4-day PPL,
    bench-focused"
19. **Chat-driven live edits** `[later]` — extend existing action parser

## 5. Analysis & Insights

20. **Weekly / monthly recap narrative** `[later]`
21. **PR context explainer** `[later]`
22. **Anomaly flagger** `[later]`
23. **Cross-metric correlation finder** `[later]`
24. **Muscle-group balance audit** `[later]`
25. **Chronotype coach** `[later]`

## 6. Exercise Library Intelligence

26. **Semantic exercise search** `[scope]` — "that rear-delt cable thing"
27. **Target-muscle recommendations** `[later]`
28. **Injury-safe alternatives** `[scope]` — "hurt my shoulder, replace
    bench"
29. **Duplicate / variant detector** `[later]`

## 7. Onboarding & Data Hygiene

30. **History summary on CSV import** `[in progress]` — feature currently on
    `feature/history-summary` branch
31. **Conversational profile setup** `[later]`
32. **Goal inference from history** `[later]`

## 8. Motivation, Adherence & Agent Behaviors

33. **Adherence nudges** `[no]` — explicitly out
34. **Proactive rest-day agent** `[no]` — explicitly out
35. **Next-PR forecast** `[no]` — explicitly out
36. **Coach persona selector** `[later]`

## 9. Conversational Surface / In-App Agent

37. **Pre-workout briefing** `[scope]` — today's plan, expected duration,
    heavy-set warnings
38. **Mid-rest chat** `[later]`
39. **Post-workout debrief** `[later]`

## 10. Cross-cutting Platform Concerns

40. **Model routing** — Flash for quick/cheap, Pro for heavy reasoning, Nano
    on-device where possible
41. **Offline vs online** — define which features must work offline
42. **Privacy & on-device** — what data leaves the device, per-feature opt-in
43. **Cost controls** — per-user token budget, response caching
44. **Failure UX** — behavior when Gemini is down or rate-limited
45. **Evaluation** — how we measure whether AI features actually help

## 11. Routine Ingestion (new)

46. **Routine ingestion from photo or free text** `[scope]` — user snaps a
    whiteboard / notebook / PDF / screenshot, or pastes text, and gets a
    formal `Routine` mapped to the app's exercise library. Covers:
    - OCR (local, ML Kit)
    - Structured extraction (cloud, Gemini Flash with JSON schema)
    - Exercise matching (local, `ExerciseMatcher`)
    - Set-scheme parsing (`3x8–10`, `5/3/1`, `AMRAP`, `EMOM`, etc.)
    - Unit inference (kg vs lb)

---

## First-spec scope — hybrid cloud / on-device split

| # | Feature | Placement | Rationale |
|---|---|---|---|
| 1 | Adaptive routine generator | **Cloud (Gemini Pro)** | Multi-week periodization, volume/intensity balance, long user history in context |
| 5 | Warm-up generator | **On-device** (rules + optional Nano polish) | Deterministic math on working weight; no cloud needed |
| 18 | NL routine builder | **Cloud (Gemini Flash)** | Structured JSON output from messy input; Nano fallback for simple cases |
| 26 | Semantic exercise search | **On-device embeddings** | Static small library, instant + offline + private |
| 28 | Injury-safe alternatives | **On-device rules first, cloud fallback** | Joint overlap is rule-based (`primaryJoints`/`secondaryJoints`); cloud only for nuanced user notes |
| 37 | Pre-workout briefing | **On-device** (template + Nano polish) | Data is deterministic; Nano only for natural phrasing |
| 46 | Routine ingestion | **Hybrid** | OCR local · JSON extraction cloud · matching local |

### On-device LLM options to evaluate
- **Gemini Nano via AICore** — Android-native, free on supported devices (Pixel 8+/9, newer Samsungs)
- **MediaPipe LLM Inference** — Gemma 2B / 3-1B, broader device support, heavier
- **MediaPipe TextEmbedder** or **MiniLM tflite** (~50 MB) — embeddings for #26 on nearly all devices

### Rule of thumb
- Creative, long-context, or structured output from messy input → cloud
- Deterministic, latency-sensitive, privacy-sensitive, or offline-required → on-device
- If on-device works "good enough", prefer it — save cloud budget for hard tasks

---

## Resolved decisions (inputs to `AI_SPEC.md`)

1. **Routine generator scope (#1)** — **one-shot**. Generate once, user edits
   manually afterwards. Iterative regeneration / targeted swaps move to
   `[later]`.
2. **Routine ingestion (#46)** — **future routines only**. Importing past
   training history from photos stays as `[later]` under #16.
3. **Device floor** — **support all devices**. Weaker devices get a lesser
   experience (e.g. no Nano polish, slower embedding search, no local
   LLM fallback). Every in-scope feature must therefore have a working
   cloud path; on-device is an optimization, not a requirement.
4. **Cost model** — **free tier first**. Spec must assume aggressive caching,
   Nano/on-device preferred where available, cloud only when necessary, and
   a per-user token budget with graceful failure when exceeded.
5. **Routine-builder invocation** — on `+ New Routine` the user is shown a
   **chooser: "Build with AI" vs "Build manually"**. No separate chat tab
   for this flow.
6. **API key strategy** — **hybrid**. App ships with a default Gemini key
   (via `BuildConfig` from `local.properties`, protected by Firebase App
   Check). User can override with their own key in Settings (stored in
   `EncryptedSharedPreferences`) to lift rate limits and remove shared
   quota pressure.
7. **Generator output flow** — **preview-then-save** for both #1/#18
   (routine builder) and #46 (routine ingestion). AI produces a read-only
   preview; user can `Save`, `Regenerate`, or `Edit` before the routine is
   persisted. Nothing is written to the `routines` table until the user
   confirms.

## Implications for the spec

- **All-device support + free tier** together mean the architecture is
  cloud-first with on-device acceleration, not on-device-first with cloud
  fallback. The default code path for every feature goes through the cloud
  client; on-device paths are opportunistic.
- **Semantic exercise search (#26)** can still be on-device for everyone
  because MediaPipe TextEmbedder / MiniLM tflite runs on min-SDK-26 devices
  via CPU. No cloud fallback needed for #26.
- **Free tier** means the spec needs a concrete answer for: where the API
  key lives, rate-limit handling, response caching keys (esp. for #1 and
  #18 routine generation where the same prompt is likely regenerated),
  and the UX when a user hits their quota.
- **One-shot #1** simplifies the generator prompt: we emit a full routine
  once, the user takes ownership via normal edit UI. No diff-apply logic
  needed in v1.
- **Hybrid key** means the data layer needs a `GeminiClient` that resolves
  the key at call time in this order: user-provided key (if set) →
  app-shipped key (default) → error state. Quotas are tracked separately
  per key source so the shared quota is protected. Firebase App Check
  tokens are attached to all requests that use the shipped key.
- **Preview-then-save** means the AI generator returns a *draft* domain
  object (in-memory `RoutineDraft`) that is NOT written to Room until the
  user confirms. The preview screen is reusable between #1, #18, and #46
  (same draft shape, different upstream source). `Regenerate` re-runs the
  prompt; `Edit` converts the draft into a real `Routine` row and opens
  the existing edit screen, letting the user take over.

---

## MVP-1 — Routine creation from text or photo

The first AI milestone is the **routine builder**. Scope is narrowed to
features #18 (NL routine builder) and #46 (routine ingestion from
photo/text), plus the shared preview-save flow and chooser UX. Everything
else in the in-scope list (#1, #5, #26, #28, #37) is deferred to MVP-2 and
beyond.

### Resolved forks for MVP-1

- **Exercise resolution** — Gemini returns **raw exercise names**, not IDs.
  Matching is done client-side via the existing `ExerciseMatcher` after
  the response lands. Library size and growth rate make name-based
  resolution the right call (avoids bloating prompts, avoids breaking on
  library updates).
- **Rep-range expansion** — A range like `3x8-10` is expanded on draft
  creation to `3 sets of 8 reps` (the lower bound). User edits the
  routine afterwards if they want different targets. Same rule for
  weight ranges.

### Implementation sequence

1. **M1 — Data contract.** `RoutineDraft`, `DraftExercise`, `DraftSet`
   in-memory shapes; JSON response schema; `RoutineDraft → Routine +
   RoutineExercise` mapper (only runs on Save); set-scheme parser
   (`3x8-10`, `5/3/1`, `AMRAP`, etc.); unit inference (kg vs lb). Pure
   Kotlin + unit tests.
2. **M2 — `GeminiClient` infrastructure.** Hybrid key resolution
   (user key → shipped key → error), Firebase App Check on shipped-key
   requests, structured output via `responseSchema`, per-source quota
   tracking, caching keyed on prompt hash.
3. **M3 — Matching layer.** `ExerciseMatcher` consumes Gemini output;
   unmatched names surface a "pick from library" slot in the draft.
4. **M4 — Text path (#18) end-to-end.** Debug entry point → text input →
   Gemini → `RoutineDraft` → preview screen (`Save` / `Regenerate` /
   `Edit`).
5. **M5 — Photo path (#46).** ML Kit OCR → same Gemini pipeline with an
   OCR-aware prompt variant → same preview screen.
6. **M6 — Real invocation UX.** `+ New Routine` chooser: manual vs AI;
   AI sub-chooser: text vs photo.
7. **M7 — Polish & eval.** Empty/failure/quota UX, user-key override in
   Settings, telemetry (save rate, regenerate count, match accuracy).
