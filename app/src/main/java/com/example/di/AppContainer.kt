package com.example.di

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.gemini.GeminiChatService
import com.example.data.prefs.EncryptedKeyManager
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.remote.ApiKeyValidator
import com.example.data.remote.ModelListService

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val encryptedKeyManager: EncryptedKeyManager by lazy { EncryptedKeyManager(context) }
    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)
    val apiKeyValidator: ApiKeyValidator = ApiKeyValidator()
    val modelListService: ModelListService = ModelListService(database.cachedModelDao())
    val geminiChatService: GeminiChatService = GeminiChatService()
}
