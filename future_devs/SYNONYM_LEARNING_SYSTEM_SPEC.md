# Synonym Learning System

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Status** | `completed` |
| **Effort** | M |
| **Depends on** | DB synonym foundation ✅, AI parser interface layer ✅ |
| **Blocks** | — |
| **Touches** | `data/database/PowerMeDatabase.kt`, `di/DatabaseModule.kt`, `util/ExerciseMatcher.kt`, `ui/workouts/ai/AiWorkoutViewModel.kt`, `ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `analytics/AnalyticsTracker.kt`, `DB_UPGRADE.md`, `AI_SPEC.md` |

---

## Overview

Wires the already-created `UserExerciseSynonym` entity + DAO + repository into the live app. After this feature ships, the AI preview screen offers to save a user's manual exercise selection as a persistent alias ("Always match 'BB Squat' → 'Barbell Back Squat'?"). Future AI parses will match that raw name instantly via a new `EXACT_USER_SYNONYM` tier in `ExerciseMatcher` — higher priority than even EXACT — so the app learns from user corrections over time.

This is Session C of the On-Device AI + Synonym Learning implementation plan (`partitioned-wandering-barto.md`). It is independent of Session B (AICore on-device) and can ship before or after it.

---

## Behaviour

- **Room migration 48→49** — creates `user_exercise_synonyms` table with unique index on `rawName` (normalised lowercase) and secondary index on `exerciseId`.
- **`EXACT_USER_SYNONYM` match tier** — inserted before `EXACT` in `MatchType` enum. Returned when `UserSynonymRepository.findExercise(rawName)` hits.
- **`matchExercise()` is now `suspend`** — Room query added at the top of the method; sole caller `AiWorkoutViewModel` already runs inside `viewModelScope.launch`.
- **Save prompt** — fires after `swapExercise()` / `swapExerciseById()` when the original `matchType` was `UNMATCHED` or `FUZZY`. Shown as a Snackbar with a **Save** action. Dismissing without tapping Save is a no-op.
- **Analytics** — `synonym_saved` event fires via `AnalyticsTracker.logSynonymSaved()` on every successful save.
- **No user-visible "manage synonyms" screen** — synonyms are saved silently per user correction; this is intentionally opaque in v1.

---

## UI Changes

- `AiWorkoutGenerationScreen.kt` — new chip label "Your match" (using `MaterialTheme.colorScheme.primary`) for `EXACT_USER_SYNONYM` rows. Snackbar offer on manual swap of UNMATCHED/FUZZY rows.
- No new screens. No new navigation routes.

---

## Files to Touch

- `data/database/PowerMeDatabase.kt` — bump version to 49, add `UserExerciseSynonym::class` to `entities` list
- `di/DatabaseModule.kt` — add `MIGRATION_48_49` SQL, add to `addMigrations()`, add `@Provides UserSynonymDao`, add `@Provides UserSynonymRepository`
- `util/ExerciseMatcher.kt` — add `EXACT_USER_SYNONYM` to `MatchType`, inject `UserSynonymRepository`, change `matchExercise` to `suspend fun`, prepend synonym lookup
- `ui/workouts/ai/AiWorkoutViewModel.kt` — inject `UserSynonymRepository` + `AnalyticsTracker`, add save-synonym flow after swap
- `ui/workouts/ai/AiWorkoutGenerationScreen.kt` — handle new `EXACT_USER_SYNONYM` chip, wire Snackbar save prompt
- `analytics/AnalyticsTracker.kt` — add `logSynonymSaved()`
- `DB_UPGRADE.md` — document v49
- `AI_SPEC.md` — update §8.8 status to shipped
- `CLAUDE.md` — update unit test count
- `test/.../util/ExerciseMatcherTest.kt` — mock new repo param, wrap in `runTest`
- `test/.../util/ExerciseMatcherSynonymTest.kt` — new (4 cases)
- `test/.../data/repository/UserSynonymRepositoryTest.kt` — already exists (created in foundation)

---

## How to QA

1. Fresh install (or clear app data) on device — open AI workout flow, generate from text
2. For an UNMATCHED or fuzzy-matched exercise, tap the row chip and select a different exercise manually
3. Snackbar appears: "Always match '[raw]' → '[selected]'?" with **Save** button — tap Save
4. Re-run the same AI text input — the exercise now matches via the "Your match" chip (green/primary)
5. Verify `synonym_saved` event in Firebase Analytics DebugView
