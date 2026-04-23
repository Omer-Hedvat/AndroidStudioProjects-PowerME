package com.powerme.app.ai

import kotlinx.serialization.json.Json

internal val workoutPromptJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun buildWorkoutPrompt(userInput: String, exerciseNames: String): String = """
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

internal fun parseWorkoutJsonResponse(raw: String): ParseResult {
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
        val exercises = workoutPromptJson.decodeFromString<List<ParsedExercise>>(jsonArray)
        ParseResult(exercises.filter { it.name.isNotBlank() && it.sets > 0 && it.reps > 0 })
    } catch (e: Exception) {
        ParseResult(emptyList(), "Failed to parse AI response: ${e.message}")
    }
}
