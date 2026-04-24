# Functional Training — TimerEngine Extraction + JetBrains Mono Font

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | — |
| **Blocks** | func_active_functional_runner |
| **Touches** | `ui/tools/ToolsViewModel.kt`, `util/timer/TimerEngine.kt` (new), `util/timer/TimerSpec.kt` (new), `util/timer/TimerEngineState.kt` (new), `ui/theme/Type.kt`, `ui/theme/TimerDigitsStyles.kt` (new) |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §2.D`, `§9.2` — read before touching any file in this task.

---

## Overview

Two independent sub-tasks shipped together:

**Sub-task A — Extract `TimerEngine`:** The four `run*` suspend functions in `ToolsViewModel` (EMOM, Tabata, Stopwatch, Countdown) are inlined and duplicated. Extract a reusable `TimerEngine` plain class that `ToolsViewModel` delegates to. The future `FunctionalBlockRunner` (task `func_active_functional_runner`) will consume the same class.

**Sub-task B — Real JetBrains Mono font:** `Type.kt:32` currently maps `JetBrainsMono` to `FontFamily.Monospace` (system fallback). Add the actual `GoogleFont("JetBrains Mono")` declaration with `isLoadingPlaceholderEnabled = true`. Also add `TimerDigitsXL` (96sp), `TimerDigitsL` (48sp), `TimerDigitsM` (28sp) typography roles to avoid duplicating `MonoTextStyle.copy(...)` at every use site.

This task is Tier 0 — no user-visible changes; the Clocks tab continues working identically. High risk: any regression to the daily-use Clocks tab is unacceptable. Mandatory regression test across all 4 Clocks modes before merge.

---

## `TimerEngine` contract

```kotlin
sealed class TimerSpec {
  data class Amrap(val durationSeconds: Int) : TimerSpec()
  data class Rft(val targetRounds: Int, val capSeconds: Int? = null) : TimerSpec()
  data class Emom(val totalDurationSeconds: Int, val intervalSeconds: Int) : TimerSpec()
  data class Tabata(val workSeconds: Int, val restSeconds: Int, val rounds: Int) : TimerSpec()
  data class Countdown(val durationSeconds: Int) : TimerSpec()
  object Stopwatch : TimerSpec()
}

data class TimerEngineState(
  val phase: TimerPhase,          // IDLE | SETUP | WORK | REST
  val currentRound: Int,
  val totalRounds: Int,
  val displaySeconds: Int,        // remaining (countdown) or elapsed (stopwatch)
  val elapsedSeconds: Int,
  val tickEpochMs: Long,
  val isRunning: Boolean
)

interface TimerEngine {
  val state: StateFlow<TimerEngineState>
  suspend fun run(spec: TimerSpec, setupSeconds: Int = 0)
  fun pause()
  fun resume()
  fun stop()
  fun addSeconds(delta: Int)
}
```

`ToolsViewModel` refactors to: create `TimerEngine`, call `timerJob = scope.launch { engine.run(spec) }`, observe `engine.state`. All alert logic (RestTimerNotifier calls) moves into `TimerEngine`.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/util/timer/TimerEngine.kt` (NEW)
- `app/src/main/java/com/powerme/app/util/timer/TimerSpec.kt` (NEW)
- `app/src/main/java/com/powerme/app/util/timer/TimerEngineState.kt` (NEW)
- `app/src/main/java/com/powerme/app/ui/tools/ToolsViewModel.kt` — delegate to `TimerEngine`
- `app/src/main/java/com/powerme/app/ui/theme/Type.kt` — add GoogleFont JetBrains Mono + `TimerDigitsXL/L/M`
- `app/src/main/java/com/powerme/app/ui/theme/TimerDigitsStyles.kt` (NEW) — export the three roles

---

## How to QA

1. Open Clocks tab. Run all 4 modes (Stopwatch, Countdown, Tabata, EMOM) to completion. Verify behaviour, sounds, and haptics are identical to before this change.
2. Verify `TimerEngine` unit tests all pass (`TimerEngineTest`): EMOM 3-round run, warn-at, pause/resume, stop mid-phase.
3. Verify Clocks timer digits now use JetBrains Mono glyphs (visually distinct tabular numerals — especially noticeable at large sizes).
4. Verify reduced-motion: animations should crossfade not scale when accessibility reduced-motion is enabled in Android settings.
