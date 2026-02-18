package com.omerhedvat.powerme.actions

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Parses ActionBlock JSON from Gemini responses.
 *
 * Handles multiple response formats:
 * 1. Plain JSON: {"action": "update_weight", ...}
 * 2. Markdown code blocks: ```json\n{...}\n```
 * 3. Multiple actions in a single response
 */
class ActionParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "action"
    }

    /**
     * Extracts ActionBlock JSON from Gemini response text.
     *
     * @param responseText The full text response from Gemini
     * @return List of parsed ActionBlock instances
     */
    fun extractActions(responseText: String): List<ActionBlock> {
        val actions = mutableListOf<ActionBlock>()

        // Pattern 1: JSON code blocks (```json ... ```)
        val codeBlockPattern = "```json\\s*([\\s\\S]*?)\\s*```".toRegex()
        codeBlockPattern.findAll(responseText).forEach { match ->
            val jsonString = match.groupValues[1].trim()
            parseAction(jsonString)?.let { actions.add(it) }
        }

        // Pattern 2: Plain JSON objects (if no code blocks found)
        if (actions.isEmpty()) {
            val jsonPattern = "\\{[\\s\\S]*?\"action\"[\\s\\S]*?\\}".toRegex()
            jsonPattern.findAll(responseText).forEach { match ->
                parseAction(match.value)?.let { actions.add(it) }
            }
        }

        return actions
    }

    /**
     * Attempts to parse a JSON string into an ActionBlock.
     *
     * @param jsonString The JSON string to parse
     * @return Parsed ActionBlock or null if parsing fails
     */
    private fun parseAction(jsonString: String): ActionBlock? {
        return try {
            json.decodeFromString<ActionBlock>(jsonString)
        } catch (e: Exception) {
            Log.w("ActionParser", "Failed to parse action: $jsonString", e)
            null
        }
    }
}
