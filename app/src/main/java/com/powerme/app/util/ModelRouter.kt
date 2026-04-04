package com.powerme.app.util

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.powerme.app.data.AppSettingsDataStore
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

    // Safety last-resort: always available, low cost
    private val FALLBACK_MODEL = "gemini-1.5-flash-8b"

    var availableModelIds: Set<String> = emptySet()

    fun resolveEnrichmentModel(userOverride: String?): String {
        val preferred = userOverride ?: ENRICHMENT_CHAIN.first()
        return if (availableModelIds.isEmpty()) preferred
        else ENRICHMENT_CHAIN.firstOrNull { it == preferred && it in availableModelIds }
            ?: ENRICHMENT_CHAIN.firstOrNull { it in availableModelIds }
            ?: FALLBACK_MODEL
    }

    /**
     * Returns the best available Flash-tier model for enrichment/utility calls.
     * Walks ENRICHMENT_CHAIN against the fetched model list; falls back to
     * [FALLBACK_MODEL] as a hardcoded last resort so the app never crashes on
     * model unavailability.
     */
    fun getBestFlashModel(): String {
        return if (availableModelIds.isEmpty()) ENRICHMENT_CHAIN.first()
        else ENRICHMENT_CHAIN.firstOrNull { it in availableModelIds } ?: FALLBACK_MODEL
    }

    /**
     * Fetches available Gemini models using the stored API key.
     * On failure, logs the error and leaves [availableModelIds] empty so callers
     * fall back to [FALLBACK_MODEL] automatically.
     */
    suspend fun fetchAvailableModels(): List<GeminiModel> {
        val apiKey = securePreferencesManager.getApiKey() ?: return emptyList()
        return try {
            fetchModels(apiKey)
        } catch (e: Exception) {
            android.util.Log.w("ModelRouter", "fetchAvailableModels failed — using fallback chain", e)
            emptyList()
        }
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

    suspend fun validateKey(apiKey: String): Boolean {
        val testModels = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        for (modelId in testModels) {
            try {
                GenerativeModel(modelId, apiKey).generateContent("hi")
                return true  // success
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                    msg.contains("INVALID_ARGUMENT", ignoreCase = true) ||
                    msg.contains("API key not valid", ignoreCase = true)) {
                    return false  // definitively invalid
                }
                // model unavailable, quota, or other transient error — try next
            }
        }
        return true  // exhausted models but no key-invalid signal → assume valid
    }
}
