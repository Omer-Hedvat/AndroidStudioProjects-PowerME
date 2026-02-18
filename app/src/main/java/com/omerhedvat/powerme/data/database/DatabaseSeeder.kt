package com.omerhedvat.powerme.data.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "powerme_prefs")

@Singleton
class DatabaseSeeder @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val warmupLibraryDao: WarmupLibraryDao,
    private val context: Context
) {
    companion object {
        private val IS_SEEDED_KEY = booleanPreferencesKey("is_database_seeded")
        private val WARMUP_SEEDED_KEY = booleanPreferencesKey("is_warmup_library_seeded")
    }

    suspend fun seedDatabaseIfNeeded() {
        val preferences = context.dataStore.data.first()
        val isSeeded = preferences[IS_SEEDED_KEY] ?: false
        val isWarmupSeeded = preferences[WARMUP_SEEDED_KEY] ?: false

        if (!isSeeded) {
            seedExercises()
            context.dataStore.edit { prefs ->
                prefs[IS_SEEDED_KEY] = true
            }
        }

        if (!isWarmupSeeded) {
            seedWarmupLibrary()
            context.dataStore.edit { prefs ->
                prefs[WARMUP_SEEDED_KEY] = true
            }
        }
    }

    private suspend fun seedExercises() {
        val exercises = listOf(
            Exercise(
                name = "Bench Press (Dumbbell)",
                muscleGroup = "Chest",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Arnold: Emphasis on full stretch; Dr. Schoenfeld: 8-12 reps for hypertrophy."
            ),
            Exercise(
                name = "Incline DB Press",
                muscleGroup = "Upper Chest",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Arnold: 30-degree angle to maximize upper chest pump."
            ),
            Exercise(
                name = "Cable Crossover",
                muscleGroup = "Chest",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Dr. Schoenfeld: Constant tension throughout the entire range of motion."
            ),
            Exercise(
                name = "Weighted Pull-Ups",
                muscleGroup = "Lats",
                equipmentType = "Bodyweight+",
                instructionsUrl = null,
                committeeNotes = "Boris: Add weight every time you exceed 10 reps."
            ),
            Exercise(
                name = "Incline Row",
                muscleGroup = "Back",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Noa: Chest support eliminates lumbar shear — ideal for lower back protection."
            ),
            Exercise(
                name = "Seated Cable Row",
                muscleGroup = "Back",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Coach Carter: Fatigue management - less load on core than barbell rows."
            ),
            Exercise(
                name = "Face Pulls",
                muscleGroup = "Rear Delts",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Noa: Essential for shoulder and elbow health (Elbow Pain)."
            ),
            Exercise(
                name = "Lateral Raises",
                muscleGroup = "Side Delts",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Arnold: Drop sets at the end to burn the muscle."
            ),
            Exercise(
                name = "Seated Overhead Press",
                muscleGroup = "Shoulders",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Noa: Seated prevents excessive back arching."
            ),
            Exercise(
                name = "Low Bar Squat",
                muscleGroup = "Legs",
                equipmentType = "Barbell",
                instructionsUrl = null,
                committeeNotes = "Boris: Core power exercise. Requires perfect technique."
            ),
            Exercise(
                name = "Goblet Squat",
                muscleGroup = "Legs",
                equipmentType = "Kettlebell",
                instructionsUrl = null,
                committeeNotes = "Noa: Back stays vertical - safer for your back pain."
            ),
            Exercise(
                name = "Leg Press",
                muscleGroup = "Legs",
                equipmentType = "Machine",
                instructionsUrl = null,
                committeeNotes = "Dr. Schoenfeld: Allows reaching mechanical failure safely."
            ),
            Exercise(
                name = "Romanian Deadlift (DB)",
                muscleGroup = "Hamstrings",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Noa: Go down only to below knee to maintain neutral spine position."
            ),
            Exercise(
                name = "Triceps Pushdown",
                muscleGroup = "Triceps",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Noa: Use straight bar; avoid fast movements to prevent elbow pain."
            ),
            Exercise(
                name = "Dips (Chest focus)",
                muscleGroup = "Chest/Triceps",
                equipmentType = "Bodyweight+",
                instructionsUrl = null,
                committeeNotes = "Boris: Very strong compound pushing exercise."
            ),
            Exercise(
                name = "Bicep Curl (Cable)",
                muscleGroup = "Biceps",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Dr. Schoenfeld: Even load; Noa: Neutral grip if there's pain."
            ),
            Exercise(
                name = "Hammer Curls",
                muscleGroup = "Biceps",
                equipmentType = "Dumbbells",
                instructionsUrl = null,
                committeeNotes = "Noa: Neutral/hammer grip reduces medial elbow stress — elbow-friendly option."
            ),
            Exercise(
                name = "Hanging Knee Raise",
                muscleGroup = "Abs",
                equipmentType = "Bodyweight",
                instructionsUrl = null,
                committeeNotes = "Noa: Spinal decompression at the end of workout."
            ),
            Exercise(
                name = "Cable Crunch",
                muscleGroup = "Abs",
                equipmentType = "Cable",
                instructionsUrl = null,
                committeeNotes = "Boaz: Easy to track progress with weight (Data-driven)."
            )
        )

        exercises.forEach { exercise ->
            exerciseDao.insertExercise(exercise)
        }
    }

    private suspend fun seedWarmupLibrary() {
        val warmupExercises = listOf(
            WarmupLibrary(exerciseName = "Cat-Cow", group = "Spine", targetArea = "Lower Back/Thoracic", committeeNote = "Noa: Gentle spinal mobilization — ideal before lower back exercises."),
            WarmupLibrary(exerciseName = "Bird-Dog", group = "Spine", targetArea = "Core Stability", committeeNote = "Noaa: Teaches bracing without spinal loading."),
            WarmupLibrary(exerciseName = "Dead Bug", group = "Spine", targetArea = "Deep Core", committeeNote = "Boaz: High ROI for stabilizing the heavy lifts."),
            WarmupLibrary(exerciseName = "Wall Slides", group = "Shoulder", targetArea = "Scapular Health", committeeNote = "Arnold: Opens the chest for better pump range."),
            WarmupLibrary(exerciseName = "Face Pulls (Light)", group = "Shoulder", targetArea = "Rear Delts/Rotators", committeeNote = "Noaa: Critical for shoulder longevity."),
            WarmupLibrary(exerciseName = "Band Pull-Aparts", group = "Shoulder", targetArea = "Upper Back", committeeNote = "Boris: Pre-activates the shelf for squats."),
            WarmupLibrary(exerciseName = "Shoulder Dislocates", group = "Shoulder", targetArea = "Mobility", committeeNote = "Use a PVC pipe or band for full ROM."),
            WarmupLibrary(exerciseName = "Zottman Curls (Light)", group = "Elbow", targetArea = "Forearms/Brachialis", committeeNote = "Noa: Eccentric loading for elbow tendon health and forearm recovery."),
            WarmupLibrary(exerciseName = "Wrist Circles", group = "Elbow", targetArea = "Joint Fluid", committeeNote = "Lubricates the joint for heavy DB pressing."),
            WarmupLibrary(exerciseName = "Pronation/Supination", group = "Elbow", targetArea = "Forearm", committeeNote = "Noaa: Specifically targets the elbow medial epicondyle."),
            WarmupLibrary(exerciseName = "90/90 Hip Switches", group = "Hips", targetArea = "Hip Internal Rotation", committeeNote = "Dr. Brad: Essential for deep, safe squat depth."),
            WarmupLibrary(exerciseName = "World's Greatest Stretch", group = "Hips", targetArea = "Full Chain", committeeNote = "Carter: The most efficient multi-joint warmup."),
            WarmupLibrary(exerciseName = "Cossack Squats", group = "Hips", targetArea = "Adductors/Lateral", committeeNote = "Prepares the hips for lateral stability."),
            WarmupLibrary(exerciseName = "Glute Bridges", group = "Hips", targetArea = "Posterior Chain", committeeNote = "Boris: Wakes up the glutes for the RDLs."),
            WarmupLibrary(exerciseName = "Frog Stretch", group = "Hips", targetArea = "Groin", committeeNote = "Essential for wide-stance loading."),
            WarmupLibrary(exerciseName = "Ankle Wall Mobilization", group = "Ankles", targetArea = "Dorsiflexion", committeeNote = "Improves Squat mechanics and knee health."),
            WarmupLibrary(exerciseName = "Scapular Pull-ups", group = "Movement", targetArea = "Lats/Scapula", committeeNote = "Dr. Brad: Primes the lats for Pull-up sessions."),
            WarmupLibrary(exerciseName = "Walking Spiderman", group = "Movement", targetArea = "Thoracic/Hip", committeeNote = "Dynamic flow to increase core temperature."),
            WarmupLibrary(exerciseName = "Bodyweight Squats", group = "Movement", targetArea = "Patterning", committeeNote = "Noaa: Perfect the form before the weight."),
            WarmupLibrary(exerciseName = "Plank with Shoulder Tap", group = "Movement", targetArea = "Anti-Rotation", committeeNote = "Primes the core for heavy unilateral DB rows.")
        )

        warmupLibraryDao.insertExercises(warmupExercises)
    }
}
