package com.example.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

class EncryptedKeyManager(context: Context) {

    private val sharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "secure_promptchat_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback to standard private shared preferences if Keystore unavailable/corrupt
        context.applicationContext.getSharedPreferences("secure_promptchat_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_IS_VERIFIED = "gemini_api_key_verified"
    }

    fun getApiKey(): String? {
        val saved = sharedPreferences.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (!saved.isNullOrBlank()) return saved
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            null
        }
        return buildConfigKey?.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
    }

    fun isVerified(): Boolean {
        if (sharedPreferences.getBoolean(KEY_IS_VERIFIED, false) && !sharedPreferences.getString(KEY_GEMINI_API_KEY, null).isNullOrBlank()) {
            return true
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            null
        }
        return !buildConfigKey.isNullOrBlank() && buildConfigKey != "MY_GEMINI_API_KEY"
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit()
            .putString(KEY_GEMINI_API_KEY, apiKey.trim())
            .putBoolean(KEY_IS_VERIFIED, true)
            .apply()
    }

    fun markInvalid() {
        sharedPreferences.edit()
            .putBoolean(KEY_IS_VERIFIED, false)
            .apply()
    }

    fun clearApiKey() {
        sharedPreferences.edit()
            .remove(KEY_GEMINI_API_KEY)
            .remove(KEY_IS_VERIFIED)
            .apply()
    }

    fun hasApiKey(): Boolean {
        val key = getApiKey()
        return !key.isNullOrBlank()
    }
}
