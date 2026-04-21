# Exercise "How to Perform" Movement Descriptions

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `done` |
| **Effort** | L |
| **Depends on** | Exercise Detail Tabs v2 |
| **Blocks** | — |
| **Touches** | `master_exercises.json`, `AboutTab.kt`, `ExerciseDetailScreen.kt`, `MasterExerciseSeeder.kt`, `Exercise.kt` |

---

## Overview

Replace the current technical "Form Cues" (`setupNotes`) in all 240 exercises with user-friendly "How to Perform" movement descriptions. The current cues use clinical/biomechanical language (e.g. "L4-L5 protection", "femur length", "scapulae retraction") that reads like a physiotherapy textbook. The new descriptions should explain movements in plain language that any gym-goer can understand.

Additionally, redesign the UI section to be one of the first things users see when opening an exercise — positioned **before** joint indicators, with larger/more prominent typography.

---

## Behaviour

- **Field reuse:** Continue using the existing `setupNotes: String?` column on the `Exercise` entity — no schema change needed. The field is simply being repopulated with better content.
- **Seeder bump:** Increment `MasterExerciseSeeder` version so existing installs get the updated text on next app launch.
- **Content style:**
  - Written for a general gym audience, not anatomy students
  - Step-by-step instructions (numbered or short paragraphs)
  - Mention common mistakes to avoid in plain language
  - No medical/anatomical jargon (e.g. "keep your back straight" not "maintain neutral spine at L4-L5")
  - Keep descriptions concise: 2-4 sentences for simple movements, 4-6 for compound lifts
- **All 240 exercises** must have a non-empty description (no stubs like "Standard jump rope.")

---

## UI Changes

- **Rename section:** "FORM CUES" → "HOW TO PERFORM" (or similar user-friendly header)
- **Reposition:** Move from position 3 (after joints) to position 1 in the About tab — before compact PR row and joints
- **Visual prominence:** Larger font size, more prominent styling (to be designed with ui-ux-pro-max)
- **Keep collapsible behaviour** for longer descriptions (Read more / Read less)
- Continue using `MaterialTheme.colorScheme.*` tokens — no hardcoded colors

---

## Files to Touch

- `app/src/main/res/raw/master_exercises.json` — rewrite all 240 `setupNotes` values
- `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt` — reposition section, rename header, update typography
- `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` — bump seeder version
- `app/src/main/java/com/powerme/app/ui/exercises/detail/ExerciseDetailScreen.kt` — if layout changes needed

---

## Implementation Notes

- `FormCuesSection` composable renamed → `HowToPerformSection`; parameter renamed `cues` → `description`
- Section moved to position 1 in `AboutTab`'s `LazyColumn` (key `"how_to_perform"`)
- Background: removed `FormCuesGold` / `primaryContainer.copy(alpha=0.15f)` — plain transparent Column matching app background
- Typography: `12.sp` → `MaterialTheme.typography.bodyMedium` (14sp), lineHeight `18.sp` → `22.sp`
- Preview threshold: 120 chars → 200 chars; collapsible Read more / Read less retained
- Seeder bumped `"1.9"` → `"2.0"` — triggers re-seed on existing installs
- All 240 `setupNotes` rewritten with plain-language descriptions (no medical/anatomical jargon), JSON version bumped to `"2.0"`

## How to QA

1. Clear app data (or fresh install) → open any exercise → About tab → "HOW TO PERFORM" should be the very first section, before the compact PR row and joint indicators
2. Open 10+ exercises across categories (barbell, bodyweight, machine, cable) — verify descriptions are plain-language ("Don't let your knees cave inward") with no jargon ("L4-L5", "scapulae retraction")
3. Open an exercise with a long description — verify **Read more / Read less** toggle works correctly
4. Verify section background matches the rest of the About tab (no tint/card background)
5. Verify text is visibly larger than before (14sp vs old 12sp)
6. Existing install (without clearing data): seeder v2.0 bump overwrites old setupNotes on next launch
