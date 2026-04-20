package com.powerme.app.data.secure

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE = "secure_ai_prefs"
private const val KEY_GEMINI_API_KEY = "user_gemini_api_key"

@Singleton
class EncryptedSecurePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurePreferencesStore {

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SecurePrefs", "Keystore init failed — falling back to no-op", e)
            null
        }
    }

    override fun getUserGeminiApiKey(): String? = prefs?.getString(KEY_GEMINI_API_KEY, null)

    override fun setUserGeminiApiKey(value: String) {
        prefs?.edit()?.putString(KEY_GEMINI_API_KEY, value)?.apply()
    }

    override fun clearUserGeminiApiKey() {
        prefs?.edit()?.remove(KEY_GEMINI_API_KEY)?.apply()
    }
}
