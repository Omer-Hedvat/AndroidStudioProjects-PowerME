# Functional Exercise Rename (KB Swing) + Abbreviation Display

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Status** | `wrapped` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `master_exercises.json`, `ExerciseStressVectorSeedData.kt`, `PowerMeDatabase.kt`, `ActiveWorkoutScreen.kt`, `BlockRecipeRow.kt`, new `ExerciseAbbreviations.kt` |

---

## Overview

Two related improvements to how functional exercises are named and displayed.

**Scope 1 — Exercise renames:** Correct the naming convention for kettlebell swings to match CrossFit/functional training standards. In CrossFit, "Kettlebell Swing" (unqualified) refers to the Russian variation (hip height). The full overhead swing is the "American Kettlebell Swing". The current DB has this backwards.
- "American Kettlebell Swing" → **"Kettlebell Swing"** (it becomes the canonical/default name)
- "Kettlebell Swing" → **"Russian Kettlebell Swing"** (explicitly named)

**Scope 2 — Abbreviation display in functional block rows:** Functional block exercise rows in the active workout screen (`FunctionalExerciseRow`) and in the block runner overlays (`BlockRecipeRow`) show exercise names that can be long and hard to read at a glance during a WOD. Show a short common abbreviation alongside each exercise name where one exists (e.g. `Kettlebell Swing ~ KBS`).

---

## Scope 1 — Exercise Renames

### Changes Required

1. **`master_exercises.json`** — find and rename:
   - `"name": "Kettlebell Swing"` → `"name": "Russian Kettlebell Swing"` (keep all other fields including `setupNotes`)
   - `"name": "American Kettlebell Swing"` → `"name": "Kettlebell Swing"` (keep all other fields)

2. **`ExerciseStressVectorSeedData.kt`** — rename the map keys:
   - `"Kettlebell Swing"` → `"Russian Kettlebell Swing"`
   - `"American Kettlebell Swing"` → `"Kettlebell Swing"`

3. **DB migration v51 → v52** — update existing exercise rows in users' databases:
   ```sql
   UPDATE exercises SET name = 'Russian Kettlebell Swing' WHERE name = 'Kettlebell Swing';
   UPDATE exercises SET name = 'Kettlebell Swing' WHERE name = 'American Kettlebell Swing';
   ```
   (Order matters: rename KB Swing first to avoid overwriting American KB Swing before it's renamed.)
   Update `DB_UPGRADE.md` with the new migration.

4. **`GEMINI_EXERCISE_ANIMATIONS_PROMPT.md`** — update exercise name mappings if present.

---

## Scope 2 — Abbreviation Display

### Abbreviation Map

Create `app/src/main/java/com/powerme/app/ui/workout/ExerciseAbbreviations.kt`:

```kotlin
object ExerciseAbbreviations {
    // Returns the abbreviation for a given exercise name, or null if none.
    fun get(exerciseName: String): String? = ABBREVS[exerciseName.lowercase().trim()]

    private val ABBREVS = mapOf(
        // Kettlebell
        "kettlebell swing"               to "KBS",
        "russian kettlebell swing"       to "KBS",
        "american kettlebell swing"      to "AKS",
        "turkish get-up"                 to "TGU",
        "kettlebell snatch"              to "KSN",
        "kettlebell clean"               to "KC",
        "kettlebell press"               to "KP",
        "kettlebell thruster"            to "KT",
        // Barbell
        "deadlift"                       to "DL",
        "romanian deadlift"              to "RDL",
        "sumo deadlift high pull"        to "SDHP",
        "overhead press"                 to "OHP",
        "push press"                     to "PP",
        "back squat"                     to "BS",
        "front squat"                    to "FS",
        "overhead squat"                 to "OHS",
        "power clean"                    to "PC",
        "hang power clean"               to "HPC",
        "squat clean"                    to "SC",
        "power snatch"                   to "PS",
        "hang power snatch"              to "HPS",
        "clean and jerk"                 to "C&J",
        "push jerk"                      to "PJ",
        "split jerk"                     to "SJ",
        "thruster"                       to "T",
        "good morning"                   to "GM",
        // Gymnastics / bodyweight
        "pull-up"                        to "PU",
        "chest-to-bar pull-up"           to "C2B",
        "toes to bar"                    to "T2B",
        "knees to elbows"                to "K2E",
        "handstand push-up"              to "HSPU",
        "handstand walk"                 to "HSW",
        "muscle-up"                      to "MU",
        "bar muscle-up"                  to "BMU",
        "ring muscle-up"                 to "RMU",
        "ring dip"                       to "RD",
        "push-up"                        to "PU",
        "burpee box jump over"           to "BBJO",
        "box jump over"                  to "BJO",
        "box jump"                       to "BJ",
        "double under"                   to "DU",
        "single under"                   to "SU",
        "wall ball"                      to "WB",
        "ghd sit-up"                     to "GHD",
        "glute ham raise"                to "GHR",
        // Cardio / machines
        "assault bike"                   to "AB",
        "row"                            to "Row",
        "ski erg"                        to "Ski",
        // Dumbbell
        "dumbbell snatch"                to "DB Snatch",
        "dumbbell thruster"              to "DB T",
        "dumbbell clean and jerk"        to "DB C&J",
        // Carries / misc
        "farmers carry"                  to "FC",
        "farmers walk"                   to "FC",
        "sandbag carry"                  to "SB Carry",
    )
}
```

### UI Changes

**`FunctionalExerciseRow` in `ActiveWorkoutScreen.kt`** (line ~878):

Replace:
```kotlin
Text(
    text = exercise.name,
    ...
)
```
With: display `exercise.name` on the first line and, if `ExerciseAbbreviations.get(exercise.name) != null`, show `~ ABBR` as a secondary caption in `onSurfaceVariant` below (or inline as `bodySmall` text). Keep layout tight — one `Column` with `bodyMedium` name + `labelSmall` abbreviation.

Format: `Kettlebell Swing` (bodyMedium) with `~ KBS` below in `labelSmall`, `onSurfaceVariant` color.

**`BlockRecipeRow.kt`** (in runner overlays):

Append the abbreviation inline after the exercise name if present:
- Current: `"10 Kettlebell Swing Reps"`
- New: `"10 Kettlebell Swing (KBS) Reps"` — or use tilde format: `"10 Kettlebell Swing ~ KBS Reps"`
- Use the tilde format to match the UX pattern the user established.

Actually — for BlockRecipeRow the text is displayed large (`headlineMedium`) during a live WOD. The abbreviation should be shown after the exercise name, separated by `·` or `~`, in a slightly smaller style. Consider splitting into a `Column` with two `Text` lines if the Row composable allows it.

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — rename two entries
- `app/src/main/java/com/powerme/app/data/database/ExerciseStressVectorSeedData.kt` — update map keys
- `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` — add Migration(51, 52)
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — update FunctionalExerciseRow to show abbreviation
- `app/src/main/java/com/powerme/app/ui/workout/runner/BlockRecipeRow.kt` — show abbreviation inline
- New: `app/src/main/java/com/powerme/app/ui/workout/ExerciseAbbreviations.kt`
- `DB_UPGRADE.md` — log v51 → v52 migration

---

## How to QA

1. Open the exercise library — search "Kettlebell Swing". Confirm "Russian Kettlebell Swing" appears; confirm "Kettlebell Swing" is the canonical (overhead) swing.
2. Start a functional block workout (AMRAP or EMOM) containing "Kettlebell Swing". Confirm the active workout card shows the name with `~ KBS` annotation.
3. Start the block runner (tap Start Block). Confirm the `BlockRecipeRow` shows the abbreviation inline.
4. Verify an exercise without an abbreviation (e.g. "Plank Hold") shows no annotation — no tilde, no empty label.
