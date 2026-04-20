# SUMMARY: BUG_timed_exercise_rpe_column

## What Changed

- **`ActiveWorkoutScreen.kt`** — `TimedHeader`: added "RPE" column (0.10), adjusted WEIGHT 0.25→0.22, TIME 0.25→0.20, Spacer 0.20→0.18
- **`ActiveWorkoutScreen.kt`** — `TimedSetRow`: added `onUpdateRpe: (Int?) -> Unit` parameter; fixed RPE picker to call `onUpdateRpe(value)` directly (was routing through `onUpdateSet` → `updateTimedSet(rpeValue=null)` which never updated the DB-persisted int field)
- **`ActiveWorkoutScreen.kt`** — IDLE state: inserted tappable RPE cell (0.10) between TIME and ▶ button; WEIGHT 0.25→0.22, TIME 0.25→0.20, PLAY 0.14→0.12
- **`ActiveWorkoutScreen.kt`** — COMPLETED state: replaced `Spacer(0.10)` with RPE cell (0.10) with category indicator (✦/dots); WEIGHT 0.25→0.22, TIME 0.25→0.20; Spacer(0.08) retained before CHECK
- **`ActiveWorkoutScreen.kt`** — `TimedSetRow` call site: forwarded `onUpdateRpe` from `SetRowWrapper` (was already present for strength rows, just not plumbed through)
- **`WORKOUT_SPEC.md`** — updated section 4.8 column layout table and description

## How to QA

1. Start or resume a workout containing a timed exercise (e.g. Plank, Bird-Dog)
2. **Header:** Confirm column headers read SET | PREV | WEIGHT | TIME(S) | RPE
3. **IDLE state:** Confirm the RPE cell shows "—" and is tappable — tapping opens `RpePickerSheet`; selecting a value shows it in the cell
4. **Auto-pop RPE:** With RPE auto-pop enabled in Settings, complete a set via ▶ (timer finishes) — confirm RPE sheet auto-pops; after selection the value shows in the row
5. **COMPLETED state:** Confirm RPE value displays with the category indicator (✦ for 9+, colored dot for others)
6. **Persistence:** Navigate away from the workout and back — confirm RPE value is retained
