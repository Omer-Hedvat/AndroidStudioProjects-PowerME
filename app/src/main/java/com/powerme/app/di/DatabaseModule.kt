package com.powerme.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.database.*
import com.powerme.app.data.repository.MedicalLedgerRepository
import com.powerme.app.data.repository.RoutineRepository
import com.powerme.app.data.repository.StateHistoryRepository
import com.powerme.app.util.GeminiResponseLogger
import com.powerme.app.util.GoalDocumentManager
import com.powerme.app.util.ModelRouter
import com.powerme.app.util.StatePatchManager
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.UserSessionManager
import com.powerme.app.util.WakeLockManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Creates a validated migration that captures snapshots before/after migration.
     *
     * @param fromVersion Source database version
     * @param toVersion Target database version
     * @param context Application context for validator
     * @param migrationLogic The actual migration SQL logic
     */
    private fun createValidatedMigration(
        fromVersion: Int,
        toVersion: Int,
        context: Context,
        migrationLogic: (SupportSQLiteDatabase) -> Unit
    ): Migration {
        return object : Migration(fromVersion, toVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val validator = PreMigrationValidator(context)

                // Step 1: Capture pre-migration snapshot
                val snapshot = validator.captureSnapshot(db, fromVersion)

                try {
                    // Step 2: Execute migration
                    migrationLogic(db)

                    // Step 3: Validate post-migration
                    val isValid = validator.validatePostMigration(db, snapshot, toVersion)

                    if (!isValid) {
                        android.util.Log.e("DatabaseModule", "❌ Migration validation failed!")
                        android.util.Log.e("DatabaseModule", validator.generateMigrationReport(snapshot, db, toVersion))
                        throw IllegalStateException("Migration validation failed: Data loss detected")
                    } else {
                        android.util.Log.i("DatabaseModule", "✅ Migration validated successfully")
                        android.util.Log.i("DatabaseModule", validator.generateMigrationReport(snapshot, db, toVersion))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DatabaseModule", "Migration error", e)
                    throw e
                }
            }
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns to exercises table
            db.execSQL("ALTER TABLE exercises ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'STRENGTH'")
            db.execSQL("ALTER TABLE exercises ADD COLUMN setupNotes TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN barType TEXT NOT NULL DEFAULT 'STANDARD'")

            // Add new columns to workout_sets table
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN setNotes TEXT")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN distance REAL")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN timeSeconds INTEGER")

            // Create new user_settings table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_settings (
                    id INTEGER PRIMARY KEY NOT NULL,
                    availablePlates TEXT NOT NULL DEFAULT '0.5,1.25,2.5,5,10,15,20,25',
                    restTimerAudioEnabled INTEGER NOT NULL DEFAULT 1,
                    restTimerHapticsEnabled INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())

            // Create new health_connect_sync table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS health_connect_sync (
                    date TEXT PRIMARY KEY NOT NULL,
                    sleepDurationMinutes INTEGER,
                    hrv REAL,
                    rhr INTEGER,
                    steps INTEGER,
                    highFatigueFlag INTEGER NOT NULL DEFAULT 0,
                    anomalousRecoveryFlag INTEGER NOT NULL DEFAULT 0,
                    syncTimestamp INTEGER NOT NULL
                )
            """.trimIndent())

            // Insert default user settings
            db.execSQL("""
                INSERT OR IGNORE INTO user_settings (id, availablePlates, restTimerAudioEnabled, restTimerHapticsEnabled)
                VALUES (1, '0.5,1.25,2.5,5,10,15,20,25', 1, 1)
            """.trimIndent())
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns to exercises table for YouTube integration and categorization
            db.execSQL("ALTER TABLE exercises ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE exercises ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE exercises ADD COLUMN youtubeVideoId TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN familyId TEXT")

            // Add actionData column to chat_messages for storing executed actions
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN actionData TEXT")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS users (
                email TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL, age INTEGER NOT NULL, heightCm REAL NOT NULL,
                occupationType TEXT NOT NULL DEFAULT 'SEDENTARY',
                averageSleepHours REAL NOT NULL DEFAULT 7.0,
                chronotype TEXT NOT NULL DEFAULT 'NEUTRAL',
                parentalLoad INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS medical_ledger (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                redListJson TEXT NOT NULL, yellowListJson TEXT NOT NULL,
                injuryHistorySummary TEXT NOT NULL,
                createdAt INTEGER NOT NULL, lastUpdated INTEGER NOT NULL
            )""")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN startTime INTEGER")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN endTime INTEGER")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN restDuration INTEGER")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS state_history (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                documentType TEXT NOT NULL,
                operation TEXT NOT NULL,
                previousValueJson TEXT NOT NULL,
                newValueJson TEXT NOT NULL,
                changeReason TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )""")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE gym_profiles ADD COLUMN dumbbellMinKg REAL")
            db.execSQL("ALTER TABLE gym_profiles ADD COLUMN dumbbellMaxKg REAL")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN weightKg REAL")
            db.execSQL("ALTER TABLE users ADD COLUMN bodyFatPercent REAL")
            db.execSQL("ALTER TABLE users ADD COLUMN gender TEXT")
            db.execSQL("ALTER TABLE users ADD COLUMN trainingTargets TEXT")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN supersetGroupId TEXT")
        }
    }

    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE routine_exercise_cross_ref ADD COLUMN stickyNote TEXT")
        }
    }

    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SurgicalValidator.MIGRATION_SQL)
        }
    }

    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SurgicalValidator.MIGRATION_SQL_V19)
        }
    }

    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE routines ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS routine_exercises (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routineId INTEGER NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    sets INTEGER NOT NULL DEFAULT 3,
                    reps INTEGER NOT NULL DEFAULT 10,
                    restTime INTEGER NOT NULL DEFAULT 90,
                    `order` INTEGER NOT NULL DEFAULT 0,
                    supersetGroupId TEXT,
                    FOREIGN KEY (routineId) REFERENCES routines(id) ON DELETE CASCADE,
                    FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_routineId ON routine_exercises(routineId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_exerciseId ON routine_exercises(exerciseId)")
        }
    }

    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN defaultWeight TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add per-set type storage to routine templates (v28)
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN setTypesJson TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add per-set weight and reps storage to routine templates (v29)
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN setWeightsJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN setRepsJson TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add session timestamps to workouts table for duration display and analytics (v30)
            db.execSQL("ALTER TABLE workouts ADD COLUMN startTimeMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workouts ADD COLUMN endTimeMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // UUID refactor: change PKs from INTEGER autoGenerate to TEXT (UUID) across
            // routines, workouts, workout_sets, routine_exercises, and warmup_log.
            // Also adds updatedAt (LWW timestamp) to routines and workouts, and
            // isArchived (soft-delete flag) to workouts.
            // Existing Long IDs are preserved as their TEXT equivalents ("1", "2", …)
            // to maintain all FK relationships across tables.
            // Order: parent tables first (routines), then children.
            db.execSQL("PRAGMA foreign_keys = OFF")

            // ── 1. routines ──────────────────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE routines_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    lastPerformed INTEGER,
                    isCustom INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO routines_new (id, name, lastPerformed, isCustom, isArchived, updatedAt)
                SELECT CAST(id AS TEXT), name, lastPerformed, isCustom, isArchived, 0
                FROM routines
            """.trimIndent())
            db.execSQL("DROP TABLE routines")
            db.execSQL("ALTER TABLE routines_new RENAME TO routines")

            // ── 2. workouts ──────────────────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE workouts_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    routineId TEXT,
                    timestamp INTEGER NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    totalVolume REAL NOT NULL,
                    notes TEXT,
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    startTimeMs INTEGER NOT NULL DEFAULT 0,
                    endTimeMs INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO workouts_new
                    (id, routineId, timestamp, durationSeconds, totalVolume, notes,
                     isCompleted, startTimeMs, endTimeMs, updatedAt, isArchived)
                SELECT CAST(id AS TEXT), CAST(routineId AS TEXT),
                    timestamp, durationSeconds, totalVolume, notes,
                    isCompleted, startTimeMs, endTimeMs, 0, 0
                FROM workouts
            """.trimIndent())
            db.execSQL("DROP TABLE workouts")
            db.execSQL("ALTER TABLE workouts_new RENAME TO workouts")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_routineId ON workouts(routineId)")

            // ── 3. workout_sets ──────────────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE workout_sets_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    workoutId TEXT NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    setOrder INTEGER NOT NULL,
                    weight REAL NOT NULL,
                    reps INTEGER NOT NULL,
                    rpe INTEGER,
                    setType TEXT NOT NULL DEFAULT 'NORMAL',
                    setNotes TEXT,
                    distance REAL,
                    timeSeconds INTEGER,
                    startTime INTEGER,
                    endTime INTEGER,
                    restDuration INTEGER,
                    supersetGroupId TEXT,
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO workout_sets_new
                    (id, workoutId, exerciseId, setOrder, weight, reps, rpe, setType,
                     setNotes, distance, timeSeconds, startTime, endTime, restDuration,
                     supersetGroupId, isCompleted)
                SELECT CAST(id AS TEXT), CAST(workoutId AS TEXT),
                    exerciseId, setOrder, weight, reps, rpe, setType,
                    setNotes, distance, timeSeconds, startTime, endTime, restDuration,
                    supersetGroupId, isCompleted
                FROM workout_sets
            """.trimIndent())
            db.execSQL("DROP TABLE workout_sets")
            db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_workoutId ON workout_sets(workoutId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_exerciseId ON workout_sets(exerciseId)")

            // ── 4. routine_exercises ─────────────────────────────────────────────────
            db.execSQL("""
                CREATE TABLE routine_exercises_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    routineId TEXT NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    sets INTEGER NOT NULL DEFAULT 3,
                    reps INTEGER NOT NULL DEFAULT 10,
                    restTime INTEGER NOT NULL DEFAULT 90,
                    `order` INTEGER NOT NULL DEFAULT 0,
                    supersetGroupId TEXT,
                    stickyNote TEXT,
                    defaultWeight TEXT NOT NULL DEFAULT '',
                    setTypesJson TEXT NOT NULL DEFAULT '',
                    setWeightsJson TEXT NOT NULL DEFAULT '',
                    setRepsJson TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE,
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO routine_exercises_new
                    (id, routineId, exerciseId, sets, reps, restTime, `order`, supersetGroupId,
                     stickyNote, defaultWeight, setTypesJson, setWeightsJson, setRepsJson)
                SELECT CAST(id AS TEXT), CAST(routineId AS TEXT),
                    exerciseId, sets, reps, restTime, `order`, supersetGroupId,
                    stickyNote, defaultWeight, setTypesJson, setWeightsJson, setRepsJson
                FROM routine_exercises
            """.trimIndent())
            db.execSQL("DROP TABLE routine_exercises")
            db.execSQL("ALTER TABLE routine_exercises_new RENAME TO routine_exercises")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_routineId ON routine_exercises(routineId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_exerciseId ON routine_exercises(exerciseId)")

            // ── 5. warmup_log (FK side-effect: workoutId TEXT) ───────────────────────
            db.execSQL("""
                CREATE TABLE warmup_log_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    workoutId TEXT,
                    exerciseName TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    targetJoint TEXT NOT NULL,
                    durationSeconds INTEGER,
                    reps INTEGER,
                    FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO warmup_log_new
                    (id, workoutId, exerciseName, timestamp, targetJoint, durationSeconds, reps)
                SELECT id, CAST(workoutId AS TEXT),
                    exerciseName, timestamp, targetJoint, durationSeconds, reps
                FROM warmup_log
            """.trimIndent())
            db.execSQL("DROP TABLE warmup_log")
            db.execSQL("ALTER TABLE warmup_log_new RENAME TO warmup_log")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_warmup_log_workoutId ON warmup_log(workoutId)")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN warmupRestSeconds INTEGER NOT NULL DEFAULT 30")
            db.execSQL("ALTER TABLE exercises ADD COLUMN dropSetRestSeconds INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Consolidate compound bench synonyms → "Bench"
            db.execSQL("UPDATE exercises SET equipmentType = 'Bench' WHERE equipmentType IN ('Bench/Chair', 'Bench/Couch', 'Bench/Floor', 'Box/Bench', 'Couch/Bench')")
            // Reassign Wall → Bodyweight (no dedicated equipment)
            db.execSQL("UPDATE exercises SET equipmentType = 'Bodyweight' WHERE equipmentType = 'Wall'")
        }
    }

    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Fix index name mismatch on exercise_muscle_groups:
            // v24 created idx_emg_exerciseId but Room expects index_exercise_muscle_groups_exerciseId
            db.execSQL("DROP INDEX IF EXISTS idx_emg_exerciseId")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_muscle_groups_exerciseId ON exercise_muscle_groups(exerciseId)")
        }
    }

    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Remove the unmanaged partial unique index that Room can't represent via
            // @Entity(indices=[...]) annotations. Uniqueness is enforced at the app layer
            // via OnConflictStrategy.REPLACE in MasterExerciseSeeder.
            db.execSQL("DROP INDEX IF EXISTS idx_exercises_master_unique")
        }
    }

    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN searchName TEXT NOT NULL DEFAULT ''")
            db.execSQL("""
                UPDATE exercises SET searchName = LOWER(
                    REPLACE(REPLACE(REPLACE(REPLACE(name, '-', ''), ' ', ''), '(', ''), ')', '')
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── PART 1: MERGE — re-point FK references then delete legacy exercise ──
            // Pattern per exercise:
            //   UPDATE routine_exercises → point to canonical B (if B exists)
            //   UPDATE workout_sets → same
            //   DELETE A (guard: only when B exists to avoid orphaning data)

            val merges = listOf(
                "Face Pulls" to "Face Pull",
                "Hammer Curls" to "Hammer Curl",
                "Lateral Raises" to "Lateral Raise",
                "Triceps Pushdown" to "Tricep Pushdown",
                "Bicep Curl (Cable)" to "Cable Curl",
                "Seated Overhead Press" to "Seated Dumbbell Overhead Press",
                "Incline DB Press" to "Incline Dumbbell Bench Press",
                "Bench Press (Dumbbell)" to "Dumbbell Flat Bench Press",
                "Seated Cable Row" to "Cable Row",
                "Cable Crossover" to "Cable Chest Fly",
                "Dips (Chest focus)" to "Dips"
            )

            for ((legacyName, canonicalName) in merges) {
                // Only act when the canonical (B) exercise exists — guard against missing JSON seed
                db.execSQL("""
                    UPDATE routine_exercises
                    SET exerciseId = (SELECT id FROM exercises WHERE name = '$canonicalName' AND isCustom = 0 LIMIT 1)
                    WHERE exerciseId IN (SELECT id FROM exercises WHERE name = '$legacyName' AND isCustom = 0)
                      AND (SELECT id FROM exercises WHERE name = '$canonicalName' AND isCustom = 0 LIMIT 1) IS NOT NULL
                """.trimIndent())

                db.execSQL("""
                    UPDATE workout_sets
                    SET exerciseId = (SELECT id FROM exercises WHERE name = '$canonicalName' AND isCustom = 0 LIMIT 1)
                    WHERE exerciseId IN (SELECT id FROM exercises WHERE name = '$legacyName' AND isCustom = 0)
                      AND (SELECT id FROM exercises WHERE name = '$canonicalName' AND isCustom = 0 LIMIT 1) IS NOT NULL
                """.trimIndent())

                db.execSQL("DELETE FROM exercises WHERE name = '$legacyName' AND isCustom = 0")
            }

            // ── PART 2: KEEP-BOTH renames ──
            db.execSQL("UPDATE exercises SET name = 'Romanian Deadlift (RDL) - BB' WHERE name = 'Romanian Deadlift (RDL)' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET name = 'Romanian Deadlift (RDL) - DB' WHERE name = 'Romanian Deadlift (DB)' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET name = 'Weighted Pull-Up' WHERE name = 'Weighted Pull-Ups' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET name = 'Incline Dumbbell Row' WHERE name = 'Incline Row' AND isCustom = 0")

            // ── PART 3: Equipment type normalization ──
            db.execSQL("UPDATE exercises SET equipmentType = 'Dumbbell' WHERE equipmentType = 'Dumbbells' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET equipmentType = 'Bodyweight' WHERE equipmentType = 'Bodyweight+' AND isCustom = 0")

            // ── PART 4: MuscleGroup normalization (non-standard → canonical) ──
            db.execSQL("UPDATE exercises SET muscleGroup = 'Shoulders' WHERE muscleGroup = 'Rear Delts' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Back' WHERE muscleGroup = 'Lats' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Legs' WHERE muscleGroup = 'Hamstrings' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Arms' WHERE muscleGroup = 'Triceps' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Arms' WHERE muscleGroup = 'Biceps' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Core' WHERE muscleGroup = 'Abs' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Chest' WHERE muscleGroup = 'Upper Chest' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Shoulders' WHERE muscleGroup = 'Side Delts' AND isCustom = 0")
            db.execSQL("UPDATE exercises SET muscleGroup = 'Chest' WHERE muscleGroup = 'Chest/Triceps' AND isCustom = 0")

            // ── PART 5: Create exercise_muscle_groups table ──
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS exercise_muscle_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    exerciseId INTEGER NOT NULL,
                    majorGroup TEXT NOT NULL,
                    subGroup TEXT,
                    isPrimary INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_emg_exerciseId ON exercise_muscle_groups(exerciseId)")

            // ── PART 6: Populate exercise_muscle_groups from exercises.muscleGroup (one primary row each) ──
            db.execSQL("""
                INSERT INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, muscleGroup, NULL, 1 FROM exercises
            """.trimIndent())

            // ── PART 7: Secondary muscle group rows for key compound exercises ──
            // Conventional Deadlift: primary=Back, secondary=Legs(Hamstrings), secondary=Legs(Glutes)
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Legs', 'Hamstrings', 0 FROM exercises WHERE name = 'Conventional Deadlift' AND isCustom = 0
            """.trimIndent())
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Legs', 'Glutes', 0 FROM exercises WHERE name = 'Conventional Deadlift' AND isCustom = 0
            """.trimIndent())

            // Romanian Deadlift (RDL) - BB: primary=Legs, secondary=Back
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Back', 'Erector Spinae', 0 FROM exercises WHERE name = 'Romanian Deadlift (RDL) - BB' AND isCustom = 0
            """.trimIndent())

            // Romanian Deadlift (RDL) - DB: primary=Legs, secondary=Back
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Back', 'Erector Spinae', 0 FROM exercises WHERE name = 'Romanian Deadlift (RDL) - DB' AND isCustom = 0
            """.trimIndent())

            // Barbell Row: primary=Back, secondary=Arms(Biceps)
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Arms', 'Biceps', 0 FROM exercises WHERE name = 'Barbell Row' AND isCustom = 0
            """.trimIndent())

            // Barbell Flat Bench Press: primary=Chest, secondary=Arms(Triceps), secondary=Shoulders
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Arms', 'Triceps', 0 FROM exercises WHERE name = 'Barbell Flat Bench Press' AND isCustom = 0
            """.trimIndent())
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Shoulders', 'Front Delts', 0 FROM exercises WHERE name = 'Barbell Flat Bench Press' AND isCustom = 0
            """.trimIndent())

            // Pull-Up: primary=Back, secondary=Arms(Biceps)
            db.execSQL("""
                INSERT OR IGNORE INTO exercise_muscle_groups (exerciseId, majorGroup, subGroup, isPrimary)
                SELECT id, 'Arms', 'Biceps', 0 FROM exercises WHERE name = 'Pull-Up' AND isCustom = 0
            """.trimIndent())

            // ── PART 8: Partial UNIQUE index on master exercises (prevents seeder re-collisions) ──
            db.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_exercises_master_unique
                ON exercises(name, equipmentType)
                WHERE isCustom = 0
            """.trimIndent())
        }
    }

    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Recreate workouts table: nullable routineId + SET_NULL FK + isCompleted column
            db.execSQL("""
                CREATE TABLE workouts_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    routineId INTEGER,
                    timestamp INTEGER NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    totalVolume REAL NOT NULL,
                    notes TEXT,
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE SET NULL
                )
            """.trimIndent())
            // Existing records get isCompleted=1 to preserve History visibility
            db.execSQL("INSERT INTO workouts_new SELECT id, routineId, timestamp, durationSeconds, totalVolume, notes, 1 FROM workouts")
            db.execSQL("DROP TABLE workouts")
            db.execSQL("ALTER TABLE workouts_new RENAME TO workouts")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_routineId ON workouts(routineId)")

            // 2. Merge stickyNote into routine_exercises and drop cross_ref table
            db.execSQL("ALTER TABLE routine_exercises ADD COLUMN stickyNote TEXT")
            db.execSQL("""
                UPDATE routine_exercises SET stickyNote = (
                    SELECT cr.stickyNote FROM routine_exercise_cross_ref cr
                    WHERE cr.routineId = routine_exercises.routineId
                      AND cr.exerciseId = routine_exercises.exerciseId
                )
            """.trimIndent())
            db.execSQL("DROP TABLE routine_exercise_cross_ref")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS metric_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    value REAL NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metric_log_timestamp ON metric_log(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_metric_log_type ON metric_log(type)")
            db.execSQL("ALTER TABLE user_settings ADD COLUMN language TEXT NOT NULL DEFAULT 'Hebrew'")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create gym_profiles table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gym_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 0,
                    notes TEXT
                )
            """.trimIndent())

            // Insert default gym profiles
            db.execSQL("""
                INSERT INTO gym_profiles (name, equipment, isActive, notes) VALUES
                    ('Home', 'Dumbbells,Resistance Bands,Pull-up Bar,Bodyweight', 0, 'Home gym with basic equipment'),
                    ('Work', 'Barbell,Dumbbells,Cable,Machine,Bodyweight,Bench,Squat Rack,Leg Press,Smith Machine,Pull-up Bar', 1, 'Full commercial gym equipment')
            """.trimIndent())
        }
    }

    @Provides
    @Singleton
    fun providePowerMeDatabase(
        @ApplicationContext context: Context
    ): PowerMeDatabase {
        // Create validated migrations
        val validatedMigration6_7 = createValidatedMigration(6, 7, context) { db ->
            // Add new columns to exercises table
            db.execSQL("ALTER TABLE exercises ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'STRENGTH'")
            db.execSQL("ALTER TABLE exercises ADD COLUMN setupNotes TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN barType TEXT NOT NULL DEFAULT 'STANDARD'")

            // Add new columns to workout_sets table
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN setNotes TEXT")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN distance REAL")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN timeSeconds INTEGER")

            // Create new user_settings table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS user_settings (
                    id INTEGER PRIMARY KEY NOT NULL,
                    availablePlates TEXT NOT NULL DEFAULT '0.5,1.25,2.5,5,10,15,20,25',
                    restTimerAudioEnabled INTEGER NOT NULL DEFAULT 1,
                    restTimerHapticsEnabled INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())

            // Create new health_connect_sync table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS health_connect_sync (
                    date TEXT PRIMARY KEY NOT NULL,
                    sleepDurationMinutes INTEGER,
                    hrv REAL,
                    rhr INTEGER,
                    steps INTEGER,
                    highFatigueFlag INTEGER NOT NULL DEFAULT 0,
                    anomalousRecoveryFlag INTEGER NOT NULL DEFAULT 0,
                    syncTimestamp INTEGER NOT NULL
                )
            """.trimIndent())

            // Insert default user settings
            db.execSQL("""
                INSERT OR IGNORE INTO user_settings (id, availablePlates, restTimerAudioEnabled, restTimerHapticsEnabled)
                VALUES (1, '0.5,1.25,2.5,5,10,15,20,25', 1, 1)
            """.trimIndent())
        }

        val validatedMigration7_8 = createValidatedMigration(7, 8, context) { db ->
            // Add new columns to exercises table for YouTube integration and categorization
            db.execSQL("ALTER TABLE exercises ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE exercises ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE exercises ADD COLUMN youtubeVideoId TEXT")
            db.execSQL("ALTER TABLE exercises ADD COLUMN familyId TEXT")

            // Add actionData column to chat_messages for storing executed actions
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN actionData TEXT")
        }

        val validatedMigration8_9 = createValidatedMigration(8, 9, context) { db ->
            // Create gym_profiles table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gym_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 0,
                    notes TEXT
                )
            """.trimIndent())

            // Insert default gym profiles
            db.execSQL("""
                INSERT INTO gym_profiles (name, equipment, isActive, notes) VALUES
                    ('Home', 'Dumbbells,Resistance Bands,Pull-up Bar,Bodyweight', 0, 'Home gym with basic equipment'),
                    ('Work', 'Barbell,Dumbbells,Cable,Machine,Bodyweight,Bench,Squat Rack,Leg Press,Smith Machine,Pull-up Bar', 1, 'Full commercial gym equipment')
            """.trimIndent())
        }

        return Room.databaseBuilder(
            context,
            PowerMeDatabase::class.java,
            "powerme_database"
        )
            .addMigrations(
                validatedMigration6_7,
                validatedMigration7_8,
                validatedMigration8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
                MIGRATION_18_19,
                MIGRATION_19_20,
                MIGRATION_20_21,
                MIGRATION_21_22,
                MIGRATION_22_23,
                MIGRATION_23_24,
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_26_27,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRoutineDao(database: PowerMeDatabase): RoutineDao {
        return database.routineDao()
    }

    @Provides
    @Singleton
    fun provideExerciseDao(database: PowerMeDatabase): ExerciseDao {
        return database.exerciseDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutDao(database: PowerMeDatabase): WorkoutDao {
        return database.workoutDao()
    }

    @Provides
    @Singleton
    fun provideWorkoutSetDao(database: PowerMeDatabase): WorkoutSetDao {
        return database.workoutSetDao()
    }

    @Provides
    @Singleton
    fun provideDatabaseSeeder(
        exerciseDao: ExerciseDao,
        warmupLibraryDao: WarmupLibraryDao,
        @ApplicationContext context: Context
    ): DatabaseSeeder {
        return DatabaseSeeder(exerciseDao, warmupLibraryDao, context)
    }

    @Provides
    @Singleton
    fun provideChatMessageDao(database: PowerMeDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideHealthStatsDao(database: PowerMeDatabase): HealthStatsDao {
        return database.healthStatsDao()
    }

    @Provides
    @Singleton
    fun provideWarmupLogDao(database: PowerMeDatabase): WarmupLogDao {
        return database.warmupLogDao()
    }

    @Provides
    @Singleton
    fun provideWarmupLibraryDao(database: PowerMeDatabase): WarmupLibraryDao {
        return database.warmupLibraryDao()
    }

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: PowerMeDatabase): UserSettingsDao {
        return database.userSettingsDao()
    }

    @Provides
    @Singleton
    fun provideHealthConnectSyncDao(database: PowerMeDatabase): HealthConnectSyncDao {
        return database.healthConnectSyncDao()
    }

    @Provides
    @Singleton
    fun provideGymProfileDao(database: PowerMeDatabase): GymProfileDao {
        return database.gymProfileDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: PowerMeDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideMedicalLedgerDao(database: PowerMeDatabase): MedicalLedgerDao {
        return database.medicalLedgerDao()
    }

    @Provides
    @Singleton
    fun provideStateHistoryDao(database: PowerMeDatabase): StateHistoryDao {
        return database.stateHistoryDao()
    }

    @Provides
    @Singleton
    fun provideMetricLogDao(database: PowerMeDatabase): MetricLogDao {
        return database.metricLogDao()
    }

    @Provides
    @Singleton
    fun providePreMigrationValidator(
        @ApplicationContext context: Context
    ): PreMigrationValidator {
        return PreMigrationValidator(context)
    }

    @Provides
    @Singleton
    fun provideGeminiResponseLogger(
        @ApplicationContext context: Context
    ): GeminiResponseLogger {
        return GeminiResponseLogger(context)
    }

    @Provides
    @Singleton
    fun provideMasterExerciseSeeder(
        @ApplicationContext context: Context,
        exerciseDao: ExerciseDao
    ): MasterExerciseSeeder {
        return MasterExerciseSeeder(context, exerciseDao)
    }

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): AppSettingsDataStore {
        return AppSettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideWakeLockManager(
        @ApplicationContext context: Context
    ): WakeLockManager {
        return WakeLockManager(context)
    }

    @Provides
    @Singleton
    fun provideModelRouter(
        appSettings: AppSettingsDataStore,
        securePreferencesManager: com.powerme.app.util.SecurePreferencesManager
    ): ModelRouter {
        return ModelRouter(appSettings, securePreferencesManager)
    }

    @Provides
    @Singleton
    fun provideRoutineExerciseDao(db: PowerMeDatabase): RoutineExerciseDao = db.routineExerciseDao()

    @Provides
    @Singleton
    fun provideRoutineRepository(
        routineDao: RoutineDao,
        routineExerciseDao: RoutineExerciseDao,
        database: PowerMeDatabase
    ): RoutineRepository = RoutineRepository(routineDao, routineExerciseDao, database)

    @Provides
    @Singleton
    fun provideFirestoreSyncManager(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        database: PowerMeDatabase,
        workoutDao: WorkoutDao,
        workoutSetDao: WorkoutSetDao,
        routineDao: RoutineDao,
        routineExerciseDao: RoutineExerciseDao
    ): com.powerme.app.data.sync.FirestoreSyncManager =
        com.powerme.app.data.sync.FirestoreSyncManager(
            firestore, auth, database, workoutDao, workoutSetDao, routineDao, routineExerciseDao
        )

    @Provides
    @Singleton
    fun provideGoogleSignInHelper(
        @ApplicationContext context: Context
    ): com.powerme.app.ui.auth.GoogleSignInHelper =
        com.powerme.app.ui.auth.DefaultGoogleSignInHelper(context)
}
