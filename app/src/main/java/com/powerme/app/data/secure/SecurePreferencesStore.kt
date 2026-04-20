package com.powerme.app.data.secure

interface SecurePreferencesStore {
    fun getUserGeminiApiKey(): String?
    fun setUserGeminiApiKey(value: String)
    fun clearUserGeminiApiKey()
    fun hasUserGeminiApiKey(): Boolean = getUserGeminiApiKey() != null
}
