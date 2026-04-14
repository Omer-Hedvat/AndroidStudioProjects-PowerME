# CSV Import — Feature Spec

## Overview

A universal workout history importer that reads CSV exports from any major fitness app and loads them into PowerME's database. The importer auto-detects known formats (Strong, Hevy, FitBod, etc.) and falls back to an interactive column-mapping UI for unknown formats.

**Goal:** Let users bring their full workout history into PowerME regardless of where they trained before, without requiring a format-specific integration per app.

---

## Supported Source Formats (Auto-Detected)

| App | Detection Heuristic | Key Columns |
|-----|---------------------|-------------|
| Strong | Header contains `"Workout Name"` + `"Set Order"` | Date, Workout Name, Exercise Name, Set Order, Weight, Reps, RPE, Notes, Workout Notes, Duration |
| Hevy | Header contains `"exercise_title"` + `"start_time"` | start_time, title, exercise_title, set_index, weight_kg, reps, rpe, notes |
| FitBod | Header contains `"bodyweight"` + `"iswarmup"` | Date, Exercise, Sets, Reps, Weight(lbs), Distance, Duration, IsWarmup, bodyweight |
| Jefit | Header contains `"log_date"` + `"e_id"` | log_date, ename, weight, reps, set_id |
| Generic | Fallback — any CSV with date + exercise + reps columns | User maps manually |

Detection is header-only (case-insensitive, order-independent). No network calls.

---

## User Flow

### Entry Point
Settings screen → "Data & Backup" card → **"Import Workout History"** button.

### Step 1 — File Picker
- Opens Android `ActivityResultContracts.GetContent` with `text/csv` + `text/*` MIME types.
- User picks the CSV from Files, Google Drive, etc.

### Step 2 — Format Detection
- App reads the header row.
- **Known format detected:** Show confirmation card:
  > "Strong export detected — 247 workouts found. Import?"
  > [Preview sample] [Import] [Cancel]
- **Unknown format:** Navigate to column mapping screen (Step 2b).

### Step 2b — Column Mapping (unknown formats only)
- Shows the first 3 data rows as a preview table.
- User assigns each CSV column to a PowerME field via dropdown:
  - **Required:** Date, Exercise Name, Reps
  - **Optional:** Weight, Sets, Duration, Notes, Workout Name, RPE, Set Type
- "Import" button enabled once required fields are mapped.
- User can save the mapping as a named profile ("My Custom App") for reuse.

### Step 3 — Import Options
Before importing, the user can configure:
- **Duplicate handling:** Skip duplicates / Overwrite / Ask per conflict
  - Duplicate detection: same date + exercise name + set order
- **Weight unit:** Auto-detect from column name (`weight_kg` vs `weight_lbs`) / Force Metric / Force Imperial
  - All weights stored in kg (PowerME's internal unit); conversion applied at import time.
- **New exercises:** Auto-add to library / Match to existing only (skip unmatched)
- **Date range filter:** Import all / Import from date / Import between dates

### Step 4 — Import Progress
- `LinearProgressIndicator` + live counter: "Importing workout 47 of 247..."
- Runs in a background coroutine (`viewModelScope.launch(Dispatchers.IO)`).
- Non-blocking: user can navigate away; import continues.
- On completion: Snackbar → "Import complete — 247 workouts added."
- On error: Error card with row number and reason; option to skip errors and continue.

### Step 5 — Import Summary
- Full-screen summary sheet (ModalBottomSheet):
  - Workouts imported: N
  - Sets imported: N
  - New exercises added to library: N (list of names)
  - Duplicates skipped: N
  - Errors: N (expandable list)
  - [Done] [Undo Import]
- **Undo:** soft-delete all workouts/sets inserted in this import batch (tagged with `importBatchId`).

---

## Data Mapping

### Workout
| PowerME Field | Mapped From |
|---------------|-------------|
| `name` | Workout Name column (or "Imported Workout" if absent) |
| `startTimeMs` | Date column (parsed to epoch ms) |
| `endTimeMs` | startTimeMs + Duration if available, else startTimeMs |
| `isCompleted` | `true` (all imported workouts are historical) |
| `routineId` | `null` (imported workouts are ad-hoc) |
| `routineName` | Workout Name column (snapshot) |
| `source` | `"import"` (new column — see DB changes) |
| `importBatchId` | UUID generated per import session (new column) |

### WorkoutSet
| PowerME Field | Mapped From |
|---------------|-------------|
| `exerciseId` | Looked up or created from Exercise Name |
| `setOrder` | Set Order column or row sequence within exercise |
| `weight` | Weight column (converted to kg if needed) |
| `reps` | Reps column |
| `setType` | IsWarmup → `WARMUP`; RPE < 6 → `WARMUP`; else `NORMAL` |
| `isCompleted` | `true` |
| `supersetGroupId` | `null` |

### Exercise
- Lookup: exact match on `name` (case-insensitive) in `exercises` table.
- No match: create new exercise with `isCustom = true`, `muscleGroup = "Unknown"`, `equipmentType = "Unknown"`.
- Fuzzy matching (Levenshtein distance ≤ 2) offered as a suggestion before creating new.

---

## Database Changes

Requires a new migration (v37):

```sql
-- Tag imported workouts for undo and filtering
ALTER TABLE workouts ADD COLUMN source TEXT DEFAULT NULL;
ALTER TABLE workouts ADD COLUMN importBatchId TEXT DEFAULT NULL;
```

- `source`: `null` = normal workout, `"import"` = imported from CSV.
- `importBatchId`: UUID string shared by all workouts in one import session. Enables bulk undo.
- No changes to `workout_sets` — batch identity derived via workout FK.
- Add index on `importBatchId` for efficient undo queries.

---

## Architecture

### New Files
| File | Purpose |
|------|---------|
| `data/import/CsvImportManager.kt` | Orchestrates the full import pipeline |
| `data/import/CsvFormatDetector.kt` | Header-based format detection + column mapping |
| `data/import/CsvRowParser.kt` | Converts a raw CSV row to domain objects per format |
| `data/import/ImportColumnProfile.kt` | Saved user column-mapping profiles (DataStore) |
| `ui/settings/ImportWorkoutsScreen.kt` | File picker → format confirmation → options → progress |
| `ui/settings/ColumnMappingScreen.kt` | Manual column assignment UI for unknown formats |
| `ui/settings/ImportSummarySheet.kt` | Post-import summary + undo |
| `ui/settings/ImportViewModel.kt` | StateFlow-driven VM for the import flow |

### Modified Files
| File | Change |
|------|--------|
| `data/database/Workout.kt` | Add `source`, `importBatchId` columns |
| `data/database/WorkoutDao.kt` | Add `getByImportBatch(batchId)`, `softDeleteBatch(batchId)` |
| `data/repository/WorkoutRepository.kt` | Add `importWorkouts(batch)`, `undoImport(batchId)` |
| `ui/settings/SettingsScreen.kt` | Add "Import Workout History" button to Data & Backup card |
| `PowerMEDatabase.kt` | Bump version 36→37, add Migration_36_37 |

---

## Error Handling

| Error | Behavior |
|-------|----------|
| File unreadable / not UTF-8 | Show error dialog before import starts |
| Missing required column | Block import, highlight missing column in mapping UI |
| Unparseable date | Skip row, log to error list |
| Unparseable weight/reps | Import row with `weight=0` / `reps=0`, flag in summary |
| Duplicate set | Honor user's duplicate-handling preference |
| DB write failure | Rollback entire batch via transaction; show error |

All imports run inside a `withTransaction` block — partial imports are not committed.

---

## Edge Cases

- **Empty CSV / header-only file:** Detected before import starts; show "No data found."
- **Very large files (10,000+ rows):** Chunked processing (500 rows/batch) with progress updates. Memory-safe.
- **Multiple exercises per workout row (FitBod style):** Parser groups rows by date + workout name to reconstruct workout entities.
- **Timezone handling:** Dates without timezone treated as local device timezone.
- **Same exercise imported twice (different name casing):** Normalized to lowercase for matching; single canonical exercise created.
- **Weight = 0 (bodyweight exercises):** Imported as-is; not filtered out.

---

## Out of Scope (v1)

- Import from URL / cloud link (Google Drive, Dropbox API)
- Nutrition data (calories, macros)
- Cardio-only data (running, cycling pace/distance without sets)
- Routine/template import (only completed workout history is imported)
- Conflict resolution UI per individual workout (only global policy)

---

## Success Metrics

- User can import a 2-year Strong export (1,000+ workouts) in under 30 seconds
- Zero data loss on round-trip (export PowerME → reimport same CSV)
- Auto-detect rate: 100% for Strong/Hevy (the two most common apps)
- Undo removes all and only the imported batch
