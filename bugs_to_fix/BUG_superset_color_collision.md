# BUG: Two supersets can receive the same color

## Status
[x] Fixed

## Description
Superset accent colors are assigned via `abs(groupId.hashCode()) % SupersetPalette.size` (`Color.kt:56`). Because `groupId` is a random UUID, two different supersets can hash to the same palette index and end up with the same color, making them visually indistinguishable. With 9 or more supersets the collision is **guaranteed** (pigeonhole — palette has 8 colors).

## Steps to Reproduce
1. Create a routine with several supersets
2. Observe that two (or more) superset groups may share the same accent color on the left border and the link icon

*(Collision is probabilistic with ≤8 supersets and certain with ≥9)*

## Root Cause
`Color.kt:56-58`:
```kotlin
fun supersetColor(groupId: String?): Color =
    if (groupId == null) Color.Transparent
    else SupersetPalette[abs(groupId.hashCode()) % SupersetPalette.size]
```
Hash-based indexing has no collision guarantee.

## Fix
Assign colors by **insertion order** instead of by hash. The ViewModel (or the composable that builds the exercise list) should maintain a `Map<String, Int>` that maps each `supersetGroupId` to its palette index in the order the groups are first seen. Pass the resolved `Color` (or index) down to the composable instead of the raw `groupId`.

**Suggested approach:**
- In `WorkoutViewModel` (or a derived state in the composable), build:
  ```kotlin
  val supersetColorIndex: Map<String, Int> = exercises
      .mapNotNull { it.supersetGroupId }
      .distinct()
      .mapIndexed { i, id -> id to (i % SupersetPalette.size) }
      .toMap()
  ```
- Replace the `supersetColor(groupId)` call sites in `ActiveWorkoutScreen.kt` (lines 680, 1316) with a lookup into this map.
- Same fix applies to `WorkoutDetailScreen.kt` and `TemplateBuilderScreen.kt` which call the same helper.

## Files to Change
- `app/src/main/java/com/powerme/app/ui/theme/Color.kt` — remove or deprecate hash-based `supersetColor()`
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — lines 680, 1316
- `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt`
- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt`
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — add ordered color map to state

## Fix Notes
Replaced hash-based `supersetColor(groupId)` in `Color.kt` with `buildSupersetColorMap(groupIds)`, which assigns colors by insertion order (first-appearance). Each screen computes a `Map<String, Color>` via `remember(exerciseList)` at its top level and passes the resolved `Color` down to child composables as a parameter. Removed the old `supersetColor()` function and the `kotlin.math.abs` import from `Color.kt`. Updated 5 files: `ActiveWorkoutScreen.kt` (ExerciseCard + SupersetSelectRow), `TemplateBuilderScreen.kt` (DraftExerciseRow + TemplateSupersetSelectRow), `WorkoutDetailScreen.kt` (ExerciseDetailCard), `WorkoutsScreen.kt` (RoutineOverviewSheet). Also added `WorkoutsScreen.kt` to the fix (was missing from the original file list). Added 7 unit tests in `SupersetColorTest.kt`. No ViewModel changes were needed — this is pure derived display state.
