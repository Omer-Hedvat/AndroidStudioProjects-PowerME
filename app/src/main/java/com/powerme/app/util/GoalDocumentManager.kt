package com.powerme.app.util

import android.content.Context
import android.content.SharedPreferences
import com.powerme.app.ui.chat.GoalDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalDocumentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("powerme_goals", Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val KEY_GOAL_DOCUMENT = "goal_document_json"
    }

    fun getGoalDocument(): GoalDocument? {
        val jsonStr = prefs.getString(KEY_GOAL_DOCUMENT, null) ?: return null
        return try {
            json.decodeFromString(GoalDocument.serializer(), jsonStr)
        } catch (e: Exception) {
            android.util.Log.w("GoalDocumentManager", "Failed to deserialize GoalDocument", e)
            null
        }
    }

    fun saveGoalDocument(doc: GoalDocument) {
        prefs.edit().putString(KEY_GOAL_DOCUMENT, json.encodeToString(GoalDocument.serializer(), doc)).apply()
    }

    fun hasGoalDocument(): Boolean {
        return prefs.contains(KEY_GOAL_DOCUMENT)
    }

    fun clearGoalDocument() {
        prefs.edit().remove(KEY_GOAL_DOCUMENT).apply()
    }
}
