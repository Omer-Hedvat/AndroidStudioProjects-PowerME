# PowerME — Gemini CLI Context & Mandates

## 1. Project Overview
- **App:** PowerME (Hypertrophy-focused Android workout tracker).
- **Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Material 3), MVVM, Hilt, Room (v25), Health Connect.
- **Visual Identity:** **Stremio Indigo "Pure Performance" Palette**.
    - **Background:** `MaterialTheme.colorScheme.background` (#0F0F1E)
    - **Surface:** `MaterialTheme.colorScheme.surface` (#191932)
    - **Chips/Rows:** `MaterialTheme.colorScheme.surfaceVariant` (#1F1F3E)
    - **Primary:** `MaterialTheme.colorScheme.primary` (#7B5BE4)
    - **Secondary/SS:** `MaterialTheme.colorScheme.secondary` (#B3478C)
    - **Text:** `MaterialTheme.colorScheme.onSurface` (#E1E1E6)
    - **Timer/Success:** `TimerGreen` (#34D399)

## 2. User Context (Safety & Biomechanics)
- **User:** Omer Hedvat (38y).
- **Height:** **181.5cm** (Longer femurs → specific torso lean and stance width cues required).
- **Injuries:** **L4-L5 (Lower Back)**, **Medial Epicondylitis (Elbow)**.
- **Mandate:** All exercise `setupNotes` or AI suggestions must prioritize spinal neutrality and elbow-friendly grips. Avoid heavy spinal compression or extreme pulling angles.

## 3. Engineering Standards
- **Data Integrity:** **ALL** numeric inputs (Weight, Reps, Height) **must** use `SurgicalValidator.kt`.
- **Database (Room):** 
    - Schema changes require a version bump and migration in `DatabaseModule.kt`.
    - `withTransaction` is mandatory for multi-table operations (e.g., instantiating a workout).
    - `isCompleted` flag is the primary gate for history visibility.
- **UI Architecture:** 
    - Strictly use `MaterialTheme.colorScheme` tokens. No hardcoded hex values in Composables.
    - High-density layouts (Compact rows, minimal padding for active workout elements).
    - Reusable components go in `ui/components/`.

## 4. Database Architecture (Template → Instance)
- **Routine** (Template) → **Workout** (Instance, `isCompleted=0` while active).
- **RoutineExercise** → **WorkoutSet** (Skeleton rows created at session start).
- **Iron Vault:** Every state change (weight/reps/completion) must sync to Room immediately (debounced if needed).

## 5. Active Sprint: UI Refinement & History Detail
- **Completed:** `WorkoutDetailScreen` expansion logic and visual alignment with `ActiveWorkoutScreen`.
- **Current Task:** Context maintenance and specification alignment.
- **Next Up:** Reviewing `plans.json` updates and ensuring consistency across all history views.

## 6. Communication
- **Directives:** Only clarify if critically underspecified; otherwise, work autonomously.
- **Inquiries:** Provide analysis and wait for a Directive before modifying code.
- **Style:** Concise, senior-engineer tone. Focus on intent and technical rationale.
