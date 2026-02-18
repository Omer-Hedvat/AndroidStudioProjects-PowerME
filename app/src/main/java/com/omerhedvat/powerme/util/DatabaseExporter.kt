package com.omerhedvat.powerme.util

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.omerhedvat.powerme.data.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility for exporting the PowerME database to JSON and CSV formats.
 */
class DatabaseExporter(
    private val context: Context,
    private val database: PowerMeDatabase
) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class ExportResult(
        val success: Boolean,
        val jsonFilePath: String? = null,
        val csvFolder: String? = null,
        val error: String? = null
    )

    /**
     * Export entire database to JSON and CSV files.
     * Returns the paths to the created files.
     */
    suspend fun exportDatabase(): ExportResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportFolder = File(downloadsDir, "PowerME_Export_$timestamp")

            if (!exportFolder.exists()) {
                exportFolder.mkdirs()
            }

            // Export to JSON
            val jsonFile = exportToJson(exportFolder, timestamp)

            // Export to CSV
            val csvFolder = exportToCsv(exportFolder)

            ExportResult(
                success = true,
                jsonFilePath = jsonFile.absolutePath,
                csvFolder = csvFolder.absolutePath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult(
                success = false,
                error = e.message ?: "Unknown error occurred"
            )
        }
    }

    private suspend fun exportToJson(exportFolder: File, timestamp: String): File {
        val jsonFile = File(exportFolder, "powerme_data_$timestamp.json")

        // Collect all data
        val data = mutableMapOf<String, Any>()

        // Export exercises
        database.exerciseDao().getAllExercises().let { flow ->
            flow.collect { exercises ->
                data["exercises"] = exercises
            }
        }

        // Export workouts
        database.workoutDao().getAllWorkouts().let { flow ->
            flow.collect { workouts ->
                data["workouts"] = workouts
            }
        }

        // Export workout sets
        database.workoutSetDao().getSetsForWorkout(0).let { flow ->
            // Get all sets by querying directly
            val allSets = mutableListOf<WorkoutSet>()
            // We'll need to query the database differently
            // For now, let's use a simple approach
            data["workout_sets"] = emptyList<WorkoutSet>() // Placeholder
        }

        // Export routines
        database.routineDao().getAllRoutines().let { flow ->
            flow.collect { routines ->
                data["routines"] = routines
            }
        }

        // Export health stats
        database.healthStatsDao().getAllHealthStats().let { flow ->
            flow.collect { healthStats ->
                data["health_stats"] = healthStats
            }
        }

        // Export warmup logs
        database.warmupLogDao().getAllWarmups().let { flow ->
            flow.collect { warmupLogs ->
                data["warmup_logs"] = warmupLogs
            }
        }

        // Export user settings
        database.userSettingsDao().getSettings().let { flow ->
            flow.collect { settings ->
                if (settings != null) {
                    data["user_settings"] = settings
                }
            }
        }

        // Export health connect sync
        database.healthConnectSyncDao().getRecentSyncs().let { flow ->
            flow.collect { syncs ->
                data["health_connect_syncs"] = syncs
            }
        }

        // Write to JSON file
        jsonFile.writeText(gson.toJson(data))

        return jsonFile
    }

    private suspend fun exportToCsv(exportFolder: File): File {
        val csvFolder = File(exportFolder, "csv")
        if (!csvFolder.exists()) {
            csvFolder.mkdirs()
        }

        // Export exercises to CSV
        exportExercisesToCsv(csvFolder)

        // Export workouts to CSV
        exportWorkoutsToCsv(csvFolder)

        // Export health stats to CSV
        exportHealthStatsToCsv(csvFolder)

        return csvFolder
    }

    private suspend fun exportExercisesToCsv(folder: File) {
        val file = File(folder, "exercises.csv")
        val exercises = mutableListOf<Exercise>()

        database.exerciseDao().getAllExercises().collect { exercises.addAll(it) }

        file.writeText("id,name,muscleGroup,equipmentType,exerciseType,setupNotes,barType,restDurationSeconds\n")
        exercises.forEach { exercise ->
            val line = "${exercise.id},\"${exercise.name}\",\"${exercise.muscleGroup}\"," +
                    "\"${exercise.equipmentType}\",${exercise.exerciseType}," +
                    "\"${exercise.setupNotes ?: ""}\",${exercise.barType},${exercise.restDurationSeconds}\n"
            file.appendText(line)
        }
    }

    private suspend fun exportWorkoutsToCsv(folder: File) {
        val file = File(folder, "workouts.csv")
        val workouts = mutableListOf<Workout>()

        database.workoutDao().getAllWorkouts().collect { workouts.addAll(it) }

        file.writeText("id,routineId,timestamp,durationSeconds,totalVolume,notes\n")
        workouts.forEach { workout ->
            val line = "${workout.id},${workout.routineId},${workout.timestamp}," +
                    "${workout.durationSeconds},${workout.totalVolume},\"${workout.notes ?: ""}\"\n"
            file.appendText(line)
        }
    }

    private suspend fun exportHealthStatsToCsv(folder: File) {
        val file = File(folder, "health_stats.csv")
        val healthStats = mutableListOf<HealthStats>()

        database.healthStatsDao().getAllHealthStats().collect { healthStats.addAll(it) }

        file.writeText("date,sleepDurationMinutes,heartRateVariability,restingHeartRate,steps\n")
        healthStats.forEach { stat ->
            val line = "${stat.date},${stat.sleepDurationMinutes ?: ""}," +
                    "${stat.heartRateVariability ?: ""},${stat.restingHeartRate ?: ""}," +
                    "${stat.steps ?: ""}\n"
            file.appendText(line)
        }
    }
}
