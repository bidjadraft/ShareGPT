package com.example

import android.app.Application
import com.example.data.worker.AutoDeletionWorker
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PromptChatApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Schedule periodic 24h retention cleanup worker
        AutoDeletionWorker.schedule(this)

        // Pre-warm database cache on IO dispatcher
        applicationScope.launch(Dispatchers.IO) {
            try {
                container.database.conversationDao().getConversationById(-1)
            } catch (_: Exception) {}
        }

        // Fetch all models list on launch from remote link
        applicationScope.launch(Dispatchers.IO) {
            container.modelListService.fetchFromRemote()
            val currentDefault = container.userPreferencesRepository.defaultModelFlow.first()
            val cached = container.database.cachedModelDao().getCachedModelsList()
            if (cached.isNotEmpty()) {
                if (currentDefault.isBlank() || cached.none { it.name == currentDefault }) {
                    container.userPreferencesRepository.setDefaultModel(cached.first().name)
                }
            }
        }
    }
}
