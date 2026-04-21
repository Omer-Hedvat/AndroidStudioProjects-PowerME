# Fix Summary: Exercise history detail shows RPE as raw decimal (0–1) instead of scaled (1–10)

## Root Cause
Both CSV importers (`StrongCsvImporter.kt`, `CsvImportManager.kt`) stored RPE as raw 1–10 integers
while the rest of the app uses a ×10 scale (60–100 for RPE 6.0–10.0). Native workouts stored
`rpe = 80` for RPE 8.0; imported workouts stored `rpe = 8`. The display code divides by 10, so
imported sets showed "0.8" instead of "8". The inconsistency appeared between History tab (showing
a natively logged workout) and Exercise detail history (showing an imported workout).

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/csvimport/StrongCsvImporter.kt` | RPE now stored as ×10: `(it * 10).toInt().coerceIn(10, 100)` |
| `app/src/main/java/com/powerme/app/data/csvimport/CsvImportManager.kt` | Same fix |
| `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` | Added `MIGRATION_47_48` — backfills raw RPE to ×10 |
| `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` | Version bumped to 48 |
| `app/src/test/java/com/powerme/app/data/repository/ExerciseDetailRepositoryTest.kt` | New test: RPE trend avgRpe=80 → value=8.0 |
| `DB_UPGRADE.md` | v48 entry added |
| `CLAUDE.md` | Database version updated to v48 |

## Surfaces Fixed
- Workout summary RPE per-set display (imported workouts): "0.8" → "8"
- Exercise detail Charts tab RPE Trend Y-axis: 0–1 range → 8–10 range
- Average RPE header on workout summary cards for imported workouts

## How to QA
1. Import a workout via CSV (Strong or generic) that has RPE values (e.g., RPE 8.0)
2. Go to **History** tab → tap that imported workout → verify per-set RPE shows "8" not "0.8"
3. Go to **Exercises** tab → tap an exercise from the imported workout → **HISTORY** tab → tap that workout → verify RPE shows correctly
4. Go to **Exercises** → exercise → **CHARTS** tab → verify RPE Trend Y-axis shows values ~8 not ~0.8
5. Also verify that natively logged workouts (logged in-app) still show correct RPE values throughout
