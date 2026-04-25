package com.powerme.app.ai

import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceWorkoutParser @Inject constructor() : WorkoutTextParser {

    override suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult {
        return try {
            val model = Generation.getClient()
            val prompt = buildWorkoutPrompt(userInput, exerciseNames.joinToString(", "))
            val request = generateContentRequest(TextPart(prompt)) {
                temperature = 0.1f
                topK = 10
                candidateCount = 1
            }
            val response = model.generateContent(request)
            val rawText = response.candidates.firstOrNull()?.text ?: ""
            Timber.d("OnDeviceWorkoutParser: raw response: $rawText")
            parseWorkoutJsonResponse(rawText)
        } catch (e: Throwable) {
            Timber.e(e, "OnDeviceWorkoutParser: inference failed")
            ParseResult(emptyList(), "On-device AI inference failed: ${e.message}")
        }
    }
}
