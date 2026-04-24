package com.powerme.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
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

private const val GEMINI_MODEL = "gemini-2.5-flash"
private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

@Singleton
class GeminiWorkoutParser @Inject constructor(
    private val keyResolver: GeminiKeyResolver
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult {
        if (userInput.isBlank()) return ParseResult(emptyList(), "No input provided")

        val apiKey = when (val resolution = keyResolver.resolve()) {
            is KeyResolution.UserKey -> resolution.value
            is KeyResolution.ShippedKey -> resolution.value
            is KeyResolution.NoKey -> return ParseResult(emptyList(), "API_KEY_MISSING")
        }

        val prompt = buildPrompt(userInput, exerciseNames.joinToString(", "))

        return try {
            val raw = callApi(apiKey, prompt)
            parseJsonResponse(raw)
        } catch (e: Exception) {
            ParseResult(emptyList(), e.message ?: "AI request failed")
        }
    }

    private suspend fun callApi(apiKey: String, prompt: String): String {
        val body = """{"contents":[{"parts":[{"text":${JsonPrimitive(prompt)}}]}]}"""

        val request = Request.Builder()
            .url("$API_BASE/$GEMINI_MODEL:generateContent?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: throw Exception("Empty response body")
                if (!response.isSuccessful) throw Exception("API error ${response.code}: $bodyStr")
                extractText(bodyStr)
            }
        }
    }

    private fun extractText(responseJson: String): String {
        return try {
            val root = lenientJson.parseToJsonElement(responseJson).jsonObject
            root["candidates"]!!
                .jsonArray.first()
                .jsonObject["content"]!!
                .jsonObject["parts"]!!
                .jsonArray.first()
                .jsonObject["text"]!!
                .jsonPrimitive.content
        } catch (e: Exception) {
            throw Exception("Unexpected API response structure: ${e.message}")
        }
    }

    internal fun parseJsonResponse(raw: String): ParseResult {
        val cleaned = raw
            .trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

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
