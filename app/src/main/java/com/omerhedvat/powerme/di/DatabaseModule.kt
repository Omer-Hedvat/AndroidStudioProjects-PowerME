package com.omerhedvat.powerme.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.*
import com.omerhedvat.powerme.data.repository.HealthDataRepository
import com.omerhedvat.powerme.data.repository.HealthDataRepositoryImpl
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.StateHistoryRepository
import com.omerhedvat.powerme.util.GeminiResponseLogger
import com.omerhedvat.powerme.util.GoalDocumentManager
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.StatePatchManager
import com.omerhedvat.powerme.util.UserSessionManager
import com.omerhedvat.powerme.util.WakeLockManager
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
                MIGRATION_12_13
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
    fun provideRoutineExerciseCrossRefDao(database: PowerMeDatabase): RoutineExerciseCrossRefDao {
        return database.routineExerciseCrossRefDao()
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
    fun provideHealthDataRepository(impl: HealthDataRepositoryImpl): HealthDataRepository {
        return impl
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
        securePreferencesManager: com.omerhedvat.powerme.util.SecurePreferencesManager
    ): ModelRouter {
        return ModelRouter(appSettings, securePreferencesManager)
    }
}
