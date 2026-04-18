package com.powerme.app.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.powerme.app.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ParsedExercise(
    @SerialName("exerciseName") val name: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Double? = null,
    val restSeconds: Int? = null
)

data class ParseResult(
    val exercises: List<ParsedExercise>,
    val error: String? = null
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Singleton
class GeminiWorkoutParser @Inject constructor() {

    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult {
        if (userInput.isBlank()) return ParseResult(emptyList(), "No input provided")

        val namesContext = exerciseNames.joinToString(", ")
        val prompt = buildPrompt(userInput, namesContext)

        return try {
            val response = model.generateContent(prompt)
            val text = response.text ?: return ParseResult(emptyList(), "Empty AI response")
            parseJsonResponse(text)
        } catch (e: Exception) {
            ParseResult(emptyList(), e.message ?: "AI request failed")
        }
    }

    internal fun parseJsonResponse(raw: String): ParseResult {
        val cleaned = raw
            .trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Find the JSON array bounds in case the model prepends/appends prose
        val startIdx = cleaned.indexOf('[')
        val endIdx = cleaned.lastIndexOf(']')
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            return ParseResult(emptyList(), "AI response did not contain a JSON array")
        }
        val jsonArray = cleaned.substring(startIdx, endIdx + 1)

        return try {
            val exercises = lenientJson.decodeFromString<List<ParsedExercise>>(jsonArray)
            ParseResult(exercises.filter { it.name.isNotBlank() && it.sets > 0 && it.reps > 0 })
        } catch (e: Exception) {
            ParseResult(emptyList(), "Failed to parse AI response: ${e.message}")
        }
    }

    private fun buildPrompt(userInput: String, exerciseNames: String): String = """
        You are a fitness assistant. Parse the following workout description into a JSON array.

        Rules:
        - Each element must have: exerciseName (string), sets (int), reps (int), weight (number or null), restSeconds (int or null)
        - Use the closest exercise name from this list when possible: $exerciseNames
        - If weight is not mentioned, use null
        - If rest is not mentioned, use null
        - Return ONLY the JSON array — no markdown, no explanation, no code fences

        Workout description:
        $userInput
    """.trimIndent()
}
