package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class KeyValidationResult {
    data class Valid(val modelCount: Int) : KeyValidationResult()
    object InvalidKey : KeyValidationResult() // 401 or 403
    object NoModelsAvailable : KeyValidationResult() // 200 but empty models
    data class ConnectionOrServerError(val message: String) : KeyValidationResult() // 429, 5xx, network
}

class ApiKeyValidator {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun validateKey(apiKey: String): KeyValidationResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext KeyValidationResult.InvalidKey
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            when {
                code == 200 -> {
                    try {
                        val json = JSONObject(bodyString)
                        val modelsArray = json.optJSONArray("models")
                        if (modelsArray != null && modelsArray.length() > 0) {
                            KeyValidationResult.Valid(modelsArray.length())
                        } else {
                            KeyValidationResult.NoModelsAvailable
                        }
                    } catch (e: Exception) {
                        KeyValidationResult.NoModelsAvailable
                    }
                }
                code == 401 || code == 403 -> {
                    KeyValidationResult.InvalidKey
                }
                code == 429 -> {
                    KeyValidationResult.ConnectionOrServerError("Rate limited (HTTP 429). Wait a moment and try again.")
                }
                code in 500..599 -> {
                    KeyValidationResult.ConnectionOrServerError("Google API server error ($code). Try again later.")
                }
                else -> {
                    KeyValidationResult.ConnectionOrServerError("Verification failed (HTTP $code). Check connection.")
                }
            }
        } catch (e: Exception) {
            KeyValidationResult.ConnectionOrServerError("Could not verify key. Check connection and try again.")
        }
    }
}
