package com.omerhedvat.powerme.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Routine::class,
        Exercise::class,
        Workout::class,
        WorkoutSet::class,
        RoutineExerciseCrossRef::class,
        ChatMessage::class,
        HealthStats::class,
        WarmupLog::class,
        WarmupLibrary::class,
        UserSettings::class,
        HealthConnectSync::class,
        GymProfile::class,
        User::class,
        MedicalLedger::class,
        StateHistoryEntry::class,
        MetricLog::class
    ],
    version = 13,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PowerMeDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun routineExerciseCrossRefDao(): RoutineExerciseCrossRefDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun healthStatsDao(): HealthStatsDao
    abstract fun warmupLogDao(): WarmupLogDao
    abstract fun warmupLibraryDao(): WarmupLibraryDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun healthConnectSyncDao(): HealthConnectSyncDao
    abstract fun gymProfileDao(): GymProfileDao
    abstract fun userDao(): UserDao
    abstract fun medicalLedgerDao(): MedicalLedgerDao
    abstract fun stateHistoryDao(): StateHistoryDao
    abstract fun metricLogDao(): MetricLogDao
}
