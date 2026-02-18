package com.omerhedvat.powerme.warmup

import com.omerhedvat.powerme.data.database.TargetJoint
import com.omerhedvat.powerme.data.database.WarmupLog
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import com.omerhedvat.powerme.util.UserSessionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WarmupExercise(
    val name: String,
    val targetJoint: String,
    val duration: Int? = null, // in seconds
    val reps: Int? = null,
    val instructions: String
)

@Serializable
data class WarmupPrescription(
    val exercises: List<WarmupExercise>,
    val reasoning: String,
    val noaaNote: String,
    val carterNote: String
)

@Singleton
class WarmupService @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val userSessionManager: UserSessionManager,
    private val modelRouter: ModelRouter
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun generateWarmup(
        routineName: String,
        injuryProfile: List<String>,
        recentWarmups: List<WarmupLog>,
        mainExercises: List<String>
    ): WarmupPrescription? {
        if (!securePreferencesManager.hasApiKey()) return null
        val user = userSessionManager.getCurrentUser()

        val model = modelRouter.buildModel(
            modelId = modelRouter.resolveEnrichmentModel(null),
            temperature = 0.7f,
            topK = 40,
            topP = 0.95f
        )

        val systemPrompt = """
You are Noa (Physiotherapist) and Coach Carter working together to prescribe a personalized warmup routine.

USER PROFILE:
- Age: ${user?.age ?: "unknown"}
- Injuries: ${injuryProfile.joinToString(", ")}

CURRENT SESSION:
- Routine: $routineName
- Main exercises: ${mainExercises.joinToString(", ")}

RECENT WARMUPS (last 10 sessions):
${recentWarmups.joinToString("\n") { "- ${it.exerciseName} (${it.targetJoint})" }}

WARMUP LIBRARY:
Shoulders: Face Pulls (light), Wall Slides, Shoulder Dislocates (PVC), Band Pull-Aparts
Elbow: Zottman Curls (light), Wrist Rotations, Triceps Stretch (dynamic), Elbow Circles
Lower Back: Cat-Cow, Bird-Dog, Dead Bug, McGill Curl-Up
Hips: World's Greatest Stretch, 90/90 Hip Switches, Hip CARs, Cossack Squats

RULES:
1. Provide exactly 3 mobility/warmup exercises
2. DO NOT repeat exercises from the last 2 sessions in the recent warmups list
3. If routine involves pressing (Bench, Overhead Press): Include 1 Elbow mobility + 1 Shoulder stability
4. If routine involves squats/deadlifts: Include 1 Lower Back decompression + 1 Hip opener
5. Focus on the user's injury areas: Elbow and Lower Back
6. Coach Carter should set appropriate duration (30-60 seconds) OR reps (8-15)
7. Noa should explain WHY each exercise helps prevent injury

Return ONLY valid JSON in this exact format:
{
  "exercises": [
    {
      "name": "Exercise name",
      "targetJoint": "SHOULDER|ELBOW|LOWER_BACK|HIP|KNEE",
      "duration": 45,
      "reps": null,
      "instructions": "Brief how-to"
    }
  ],
  "reasoning": "Noa's explanation of why these exercises were chosen",
  "noaaNote": "Noa's specific injury prevention note",
  "carterNote": "Coach Carter's note on intensity/timing"
}
        """.trimIndent()

        try {
            val response = model.generateContent(systemPrompt)
            val responseText = response.text ?: return null

            // Extract JSON from response (remove markdown code blocks if present)
            val jsonText = responseText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            return json.decodeFromString<WarmupPrescription>(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseTargetJoint(joint: String): TargetJoint {
        return try {
            TargetJoint.valueOf(joint.uppercase())
        } catch (e: Exception) {
            TargetJoint.SHOULDER // Default fallback
        }
    }
}
