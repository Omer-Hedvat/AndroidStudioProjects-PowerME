# Fix Summary: Superset colors collide when multiple supersets exist

## Root Cause
`supersetColor(groupId)` in `Color.kt` used `abs(groupId.hashCode()) % 8` to pick a palette color. Because `groupId` is a random UUID, two different supersets could hash to the same index and receive the same color. With 9+ supersets, collision was guaranteed (pigeonhole principle).

## Files Changed
| File | Change |
|---|---|
| `ui/theme/Color.kt` | Replaced hash-based `supersetColor(groupId)` with `buildSupersetColorMap(groupIds)` which assigns colors by insertion order (`i % palette.size`). Removed `kotlin.math.abs` import. |
| `ui/workout/ActiveWorkoutScreen.kt` | `ExerciseCard` and `SupersetSelectRow` now receive a `supersetColorMap: Map<String, Color>` parameter computed via `remember(exerciseList)` at screen level |
| `ui/workouts/TemplateBuilderScreen.kt` | Same pattern — `DraftExerciseRow` and `TemplateSupersetSelectRow` receive resolved color |
| `ui/history/WorkoutDetailScreen.kt` | `ExerciseDetailCard` receives resolved color |
| `ui/workouts/WorkoutsScreen.kt` | `RoutineOverviewSheet` receives resolved color (was missing from original bug description) |
| `src/test/.../SupersetColorTest.kt` | New file — 7 unit tests covering: first group gets first color, second gets second, wraps at palette size, null returns Transparent, single group, empty list |

## Surfaces Fixed
- Active workout — superset left-border and link icon colors are always distinct per group
- Template builder — same
- History detail — same
- Routine overview sheet — same

## How to QA
1. Create a routine with at least 3 superset groups (pair A+B, pair C+D, pair E+F)
2. Open the routine in active workout or template builder
3. **Expected:** each superset group has a visually distinct accent color on the left border and link icon
4. **Not expected:** two groups share the same color
5. Add a 9th+ superset group to confirm wrapping works (9th group reuses first color — this is expected and acceptable)
