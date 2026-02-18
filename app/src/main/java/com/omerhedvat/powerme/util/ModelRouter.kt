package com.omerhedvat.powerme.util

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omerhedvat.powerme.data.AppSettingsDataStore
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiModel(val id: String, val displayName: String, val tier: Int)

@Singleton
class ModelRouter @Inject constructor(
    private val appSettings: AppSettingsDataStore,
    private val securePreferencesManager: SecurePreferencesManager
) {
    // Ordered fallback chains
    // Note: enrichment chain updated to prefer Flash for lower latency structured JSON output
    private val THINKING_CHAIN = listOf(
        "gemini-2.0-flash-thinking-exp",
        "gemini-1.5-pro",
        "gemini-2.0-flash-exp"
    )
    private val ENRICHMENT_CHAIN = listOf(
        "gemini-1.5-flash",        // Primary: optimized for speed + structured JSON
        "gemini-2.0-flash-exp",    // Fallback 1
        "gemini-1.5-flash-8b"      // Fallback 2
    )

    var availableModelIds: Set<String> = emptySet()

    fun resolveWarRoomModel(userOverride: String?): String {
        val preferred = userOverride ?: THINKING_CHAIN.first()
        return if (availableModelIds.isEmpty()) preferred
        else THINKING_CHAIN.firstOrNull { it == preferred && it in availableModelIds }
            ?: THINKING_CHAIN.last()
    }

    fun resolveEnrichmentModel(userOverride: String?): String {
        val preferred = userOverride ?: ENRICHMENT_CHAIN.first()
        return if (availableModelIds.isEmpty()) preferred
        else ENRICHMENT_CHAIN.firstOrNull { it == preferred && it in availableModelIds }
            ?: ENRICHMENT_CHAIN.last()
    }

    fun buildModel(modelId: String, temperature: Float, topK: Int, topP: Float): GenerativeModel {
        val apiKey = securePreferencesManager.getApiKey() ?: error("No API key configured")
        return GenerativeModel(
            modelName = modelId,
            apiKey = apiKey,
            generationConfig = generationConfig {
                this.temperature = temperature
                this.topK = topK
                this.topP = topP
            }
        )
    }

    suspend fun fetchModels(apiKey: String): List<GeminiModel> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
        val json = (url.openConnection() as HttpURLConnection).let { conn ->
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.inputStream.bufferedReader().readText().also { conn.disconnect() }
        }
        val root = JsonParser.parseString(json).asJsonObject
        val modelsArray = root.getAsJsonArray("models") ?: return emptyList()

        return modelsArray
            .map { it.asJsonObject }
            .filter { model ->
                model.getAsJsonArray("supportedGenerationMethods")
                    ?.any { it.asString == "generateContent" } == true
            }
            .map { model ->
                val id = model["name"].asString.substringAfterLast("/")
                GeminiModel(
                    id = id,
                    displayName = model["displayName"]?.asString ?: id,
                    tier = when {
                        id.contains("thinking") -> 0
                        id.contains("pro") -> 1
                        else -> 2
                    }
                )
            }
            .sortedWith(compareBy({ it.tier }, { it.displayName }))
            .also { models ->
                availableModelIds = models.map { it.id }.toSet()
            }
    }

    suspend fun validateKey(apiKey: String): Boolean = try {
        GenerativeModel("gemini-2.0-flash-exp", apiKey)
            .generateContent("ping")
        true
    } catch (e: Exception) {
        false
    }
}
