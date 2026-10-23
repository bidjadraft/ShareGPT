package com.example.data.gemini

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed class GeminiUploadResult {
    data class Success(
        val fileUri: String,
        val mimeType: String,
        val name: String
    ) : GeminiUploadResult()

    data class Error(val message: String) : GeminiUploadResult()
}

object GeminiFileUploader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(
        context: Context,
        apiKey: String,
        uri: Uri,
        mimeType: String,
        displayName: String
    ): GeminiUploadResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiUploadResult.Error("API Key is missing. Please set your Gemini API key in Settings.")
        }

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read: Int
                var total = 0L
                val maxLimit = 20 * 1024 * 1024L // 20 MB limit
                while (stream.read(chunk).also { read = it } != -1) {
                    total += read
                    if (total > maxLimit) {
                        return@withContext GeminiUploadResult.Error("File exceeds 20 MB limit.")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext GeminiUploadResult.Error("Could not read file from device.")
        } catch (e: Exception) {
            return@withContext GeminiUploadResult.Error("Error reading file: ${e.localizedMessage}")
        }

        val safeDisplayName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cleanMimeType = if (mimeType.isBlank()) "application/octet-stream" else mimeType

        try {
            // Step 1: Initialize Resumable Upload
            val startUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey"
            val metadataJson = JSONObject().apply {
                put("file", JSONObject().apply {
                    put("display_name", safeDisplayName)
                })
            }

            val startRequest = Request.Builder()
                .url(startUrl)
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Header-Content-Length", bytes.size.toString())
                .addHeader("X-Goog-Upload-Header-Content-Type", cleanMimeType)
                .post(metadataJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val startResponse = client.newCall(startRequest).execute()
            if (!startResponse.isSuccessful) {
                val errBody = startResponse.body?.string() ?: ""
                return@withContext GeminiUploadResult.Error("Upload init failed (${startResponse.code}): $errBody")
            }

            val uploadUrl = startResponse.header("x-goog-upload-url")
                ?: startResponse.header("X-Goog-Upload-URL")
                ?: return@withContext GeminiUploadResult.Error("Missing upload URL in Gemini File API response.")

            // Step 2: Upload raw file bytes
            val uploadBody = bytes.toRequestBody(cleanMimeType.toMediaTypeOrNull())
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .addHeader("Content-Length", bytes.size.toString())
                .addHeader("X-Goog-Upload-Offset", "0")
                .addHeader("X-Goog-Upload-Command", "upload, finalize")
                .post(uploadBody)
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            val uploadRespBody = uploadResponse.body?.string() ?: ""
            if (!uploadResponse.isSuccessful) {
                return@withContext GeminiUploadResult.Error("Upload bytes failed (${uploadResponse.code}): $uploadRespBody")
            }

            val responseJson = JSONObject(uploadRespBody)
            val fileObj = responseJson.optJSONObject("file")
                ?: return@withContext GeminiUploadResult.Error("Invalid response from Gemini File API: $uploadRespBody")

            val fileUri = fileObj.getString("uri")
            val fileName = fileObj.optString("name", "")

            GeminiUploadResult.Success(
                fileUri = fileUri,
                mimeType = cleanMimeType,
                name = fileName
            )
        } catch (e: Exception) {
            GeminiUploadResult.Error("Upload network error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
