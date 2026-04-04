# PowerME — Gemini CLI Context & Mandates

## 1. Project Overview
- **App:** PowerME (Hypertrophy-focused Android workout tracker).
- **Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Material 3), MVVM, Hilt, Room (v27), Health Connect.
- **Visual Identity:** **Pro Tracker v4.0 (Pure Performance)**.
    - **Background:** `MaterialTheme.colorScheme.background` (#000000 - Pure OLED Black)
    - **Surface:** `MaterialTheme.colorScheme.surface` (#121212)
    - **Chips/Rows:** `MaterialTheme.colorScheme.surfaceVariant` (#1E1E1E)
    - **Primary:** `MaterialTheme.colorScheme.primary` (#A061FF - Stremio Violet)
    - **Secondary:** `MaterialTheme.colorScheme.secondary` (#B3478C)
    - **Text:** `MaterialTheme.colorScheme.onSurface` (#FFFFFF)
    - **SubText:** `MaterialTheme.colorScheme.onSurfaceVariant` (#A0A0A0)
    - **Timer/Success:** `TimerGreen` (#34D399)

## 2. User Context (Safety & Biomechanics)
- **User:** the developer (38y).
- **Height:** **181.5cm** (Longer femurs → specific torso lean and stance width cues required).
- **Injuries:** **L4-L5 (Lower Back)**, **Medial Epicondylitis (Elbow)**.
- **Mandate:** All exercise `setupNotes` or AI suggestions must prioritize spinal neutrality and elbow-friendly grips. Avoid heavy spinal compression or extreme pulling angles.

## 3. Engineering Standards
- **Data Integrity:** **ALL** numeric inputs (Weight, Reps, Height) **must** use `SurgicalValidator.kt`.
- **Database (Room):** 
    - Schema changes require a version bump and migration in `DatabaseModule.kt`. Update `DB_UPGRADE.md`.
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

## 5. Active Sprint: Exercise Library & Hybrid Tracking
- **Completed:** Added 75+ exercises (v1.5 master exercises), implemented `TIMED` and `CARDIO` row types in `ActiveWorkoutScreen` and `WorkoutDetailScreen`.
- **Current Task:** Context maintenance and ensuring database seeding consistency.
- **Next Up:** Analytics expansion and 1RM tracking refinement using `StatisticalEngine.kt`.

## 6. Communication
- **Directives:** Only clarify if critically underspecified; otherwise, work autonomously.
- **Inquiries:** Provide analysis and wait for a Directive before modifying code.
- **Style:** Concise, senior-engineer tone. Focus on intent and technical rationale.
