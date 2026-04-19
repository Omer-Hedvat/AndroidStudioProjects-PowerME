# Exercise Joints — Stressed Joint Indicators

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `completed` |
| **Effort** | M |
| **Depends on** | — |

---

## Overview

Show which joints bear load during an exercise inside `ExerciseDetailSheet`. Two tiers:

- **Primary joints** — joints under direct, significant load (e.g. knee + hip for Squat)
- **Secondary joints** — joints stabilising or under minor stress (e.g. ankle + spine for Squat)

This gives users with injuries or joint concerns an immediate visual cue before selecting an exercise.

---

## Data Model

### Joint enum

```kotlin
enum class Joint(val displayName: String) {
    CERVICAL_SPINE("Cervical Spine"),
    THORACIC_SPINE("Thoracic Spine"),
    LUMBAR_SPINE("Lumbar Spine"),
    SHOULDER("Shoulder"),
    ELBOW("Elbow"),
    WRIST("Wrist"),
    HIP("Hip"),
    KNEE("Knee"),
    ANKLE("Ankle"),
    FOOT("Foot")
}
```

### Exercise entity additions

Add to `Exercise` entity (new DB migration):

```kotlin
@ColumnInfo(defaultValue = "")
val primaryJoints: String = "",   // JSON array of Joint enum names, e.g. ["KNEE","HIP"]

@ColumnInfo(defaultValue = "")
val secondaryJoints: String = ""  // JSON array of Joint enum names
```

Stored as JSON strings via `@TypeConverter` (reuse the existing list converter pattern).

### Seeding

Joint data is seeded via `master_exercises.json` — add `primaryJoints` and `secondaryJoints` string arrays to each exercise entry. Use a Gemini prompt (similar to the animations prompt) to bulk-populate the JSON. Seed via `MasterExerciseSeeder` on next version bump.

Prioritise the top 50 most-used exercises first; remainder can default to empty (no joint indicators shown).

---

## UI

### Placement

Inside `ExerciseDetailSheet`, below the muscle group chip row and above the form cues banner.

### Layout

A single horizontal row: a small label `"Joints:"` followed by chips.

- **Primary joint chips** — filled `PrimaryContainer` background, `onPrimaryContainer` text
- **Secondary joint chips** — outlined, `outline` border, `onSurface` text, slightly smaller font

```
Joints:  [Knee] [Hip]  ·  [Ankle]  [Lumbar Spine]
          ↑ primary         ↑ secondary (outlined)
```

If both lists are empty, the row is hidden entirely.

### Interaction

Chips are non-interactive (display only). No tap action needed for V1.

---

## Health History integration

If the user has a `HealthHistoryEntry` that red-lists or yellow-lists a joint (e.g. knee surgery), the matching primary joint chip shows a warning tint (`ErrorContainer` / `TertiaryContainer`) instead of the default primary colour. This surfaces the risk at a glance without requiring the user to check their health history manually.

Mapping: `HealthHistoryEntry.affectedBodyPart` → `Joint` (approximate; "knee" → `KNEE`, "shoulder" → `SHOULDER`, etc.).

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/database/Exercise.kt` — add `primaryJoints` / `secondaryJoints` fields + `Joint` enum
- `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` — bump DB version, add migration
- `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` — migration object
- `app/src/main/res/raw/master_exercises.json` — add joint arrays to each exercise
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — read new fields
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — `ExerciseDetailSheet`: add joint chip row
- `DB_UPGRADE.md` — document migration

---

## How to QA

1. Open Exercises → tap "Barbell Back Squat" → joint row shows `[Knee] [Hip]` (primary filled) + `[Ankle] [Lumbar Spine]` (secondary outlined)
2. Tap "Bench Press" → shows `[Shoulder] [Elbow]` primary + `[Wrist]` secondary
3. Tap a custom exercise with no joint data → joint row is hidden, no empty space
4. Add a knee-related health history entry → reopen Back Squat → Knee chip shows warning tint
5. Exercises with only primary joints (no secondary) render correctly (no trailing dot separator)
