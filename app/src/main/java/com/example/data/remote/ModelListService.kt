package com.example.data.remote

import com.example.data.db.CachedModel
import com.example.data.db.CachedModelDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class ModelFetchResult {
    data class Success(val models: List<String>, val count: Int) : ModelFetchResult()
    data class Error(val message: String, val cachedCount: Int) : ModelFetchResult()
}

class ModelListService(
    private val cachedModelDao: CachedModelDao
) {
    companion object {
        const val MODELS_URL =
            "https://raw.githubusercontent.com/bidjadraft/Free-Models/refs/heads/main/free_pdf_models.txt"
        private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModelsIfNeeded(force: Boolean = false): ModelFetchResult = withContext(Dispatchers.IO) {
        val lastFetched = cachedModelDao.getLastFetchedTime()
        val now = System.currentTimeMillis()

        if (!force && lastFetched != null && (now - lastFetched < TWELVE_HOURS_MS)) {
            val cached = cachedModelDao.getCachedModelsList()
            if (cached.isNotEmpty()) {
                return@withContext ModelFetchResult.Success(
                    models = cached.map { it.name },
                    count = cached.size
                )
            }
        }

        fetchFromRemote()
    }

    suspend fun fetchFromRemote(): ModelFetchResult = withContext(Dispatchers.IO) {
        val cached = cachedModelDao.getCachedModelsList()
        val request = Request.Builder()
            .url(MODELS_URL)
            .get()
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ModelFetchResult.Error(
                    message = "Failed to fetch model list (HTTP ${response.code})",
                    cachedCount = cached.size
                )
            }

            val body = response.body?.string() ?: ""
            val parsedModels = body.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .distinct()

            if (parsedModels.isEmpty()) {
                return@withContext ModelFetchResult.Error(
                    message = "Fetched model list was empty.",
                    cachedCount = cached.size
                )
            }

            val now = System.currentTimeMillis()
            val entities = parsedModels.map { CachedModel(name = it, fetchedAt = now) }
            cachedModelDao.replaceModels(entities)

            ModelFetchResult.Success(
                models = parsedModels,
                count = parsedModels.size
            )
        } catch (e: Exception) {
            ModelFetchResult.Error(
                message = e.localizedMessage ?: "Network connection error",
                cachedCount = cached.size
            )
        }
    }
}
