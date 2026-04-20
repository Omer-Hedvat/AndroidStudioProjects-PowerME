package com.powerme.app.ai

import com.powerme.app.data.secure.SecurePreferencesStore
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface KeyResolution {
    object NoKey : KeyResolution
    data class ShippedKey(val value: String) : KeyResolution
    data class UserKey(val value: String) : KeyResolution
}

@Singleton
class GeminiKeyResolver @Inject constructor(
    private val store: SecurePreferencesStore,
    @Named("shippedGeminiKey") private val shippedKey: String
) {
    fun resolve(): KeyResolution {
        val userKey = store.getUserGeminiApiKey()?.trim()
        if (!userKey.isNullOrBlank()) return KeyResolution.UserKey(userKey)
        val shipped = shippedKey.trim()
        if (shipped.isNotBlank()) return KeyResolution.ShippedKey(shipped)
        return KeyResolution.NoKey
    }
}
