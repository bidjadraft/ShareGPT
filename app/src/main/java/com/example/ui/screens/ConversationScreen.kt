package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Conversation
import com.example.data.db.Message
import com.example.data.db.Prompt
import com.example.data.file.AttachmentProcessResult
import com.example.data.file.FileAttachmentHelper
import com.example.data.file.JsonShareHelper
import com.example.data.gemini.GeminiFileUploader
import com.example.data.gemini.GeminiUploadResult
import com.example.data.prefs.RetentionPeriod
import com.example.di.AppContainer
import com.example.ui.components.MarkdownMessageView
import com.example.ui.components.PromptIconDisplay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val conversation by container.database.conversationDao()
        .getConversationFlow(conversationId)
        .collectAsState(initial = null)

    var prompt by remember { mutableStateOf<Prompt?>(null) }
    val messages by container.database.messageDao()
        .getMessagesForConversation(conversationId)
        .collectAsState(initial = emptyList())

    val selectedModel by container.userPreferencesRepository.defaultModelFlow.collectAsState(initial = "")
    val autoExecuteOnFile by container.userPreferencesRepository.autoExecuteOnFileFlow.collectAsState(initial = true)
    val retentionPeriod by container.userPreferencesRepository.retentionPeriodFlow.collectAsState(initial = RetentionPeriod.DAYS_7)
    val isKeyVerified = container.encryptedKeyManager.isVerified()
    val cachedModels by container.database.cachedModelDao().getAllCachedModels().collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var currentStreamingChunk by remember { mutableStateOf("") }
    var streamError by remember { mutableStateOf<String?>(null) }
    var fileAccessibilityWarning by remember { mutableStateOf<String?>(null) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var hasAutoExecuted by remember { mutableStateOf(false) }
    var isModelMenuExpanded by remember { mutableStateOf(false) }

    // Attachment state
    var isProcessingAttachment by remember { mutableStateOf(false) }
    var attachmentProcessingMessage by remember { mutableStateOf<String?>(null) }
    var attachmentFeedback by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var isClearDialogOpen by remember { mutableStateOf(false) }
    var isDeleteDialogOpen by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Load initial prompt
    LaunchedEffect(conversation?.promptId) {
        val pId = conversation?.promptId
        if (pId != null) {
            prompt = container.database.promptDao().getPromptById(pId)
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Follow streaming chunks smoothly to bottom without preventing user scroll
    LaunchedEffect(currentStreamingChunk) {
        if (currentStreamingChunk.isNotBlank()) {
            val total = messages.size + 1
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= total - 3) {
                listState.scrollToItem(total - 1, scrollOffset = 100000)
            }
        }
    }

    // Retention: "Don't keep" cleans up conversation on screen exit
    DisposableEffect(conversationId) {
        onDispose {
            if (retentionPeriod == RetentionPeriod.DONT_KEEP) {
                coroutineScope.launch {
                    container.database.conversationDao().deleteConversationById(conversationId)
                }
            }
        }
    }

    // Allowed file types for current prompt (strictly single dedicated format, or empty for chat-only)
    val allowedTypesList = remember(prompt?.allowedFileTypes) {
        val raw = prompt?.allowedFileTypes?.lowercase() ?: ""
        when {
            raw.contains("pdf") -> listOf("pdf")
            raw.contains("image") -> listOf("image")
            raw.contains("audio") -> listOf("audio")
            raw.contains("doc") || raw.contains("text") -> listOf("doc", "text")
            else -> emptyList() // Chat only without files
        }
    }

    val isAutoExecuteMode = remember(prompt?.autoExecuteOnFile, autoExecuteOnFile, allowedTypesList) {
        ((prompt?.autoExecuteOnFile == true) || autoExecuteOnFile) && allowedTypesList.isNotEmpty()
    }

    // Validate persistent file against prompt allowedFileTypes (Section 3.4)
    val persistentUriString = conversation?.persistentAttachmentUri
    val persistentMime = conversation?.persistentAttachmentMimeType
    val persistentName = conversation?.persistentAttachmentName

    val isPersistentTypeAllowed = remember(persistentUriString, persistentMime, persistentName, allowedTypesList) {
        if (persistentUriString == null) true
        else {
            val cat = FileAttachmentHelper.detectCategoryFromMimeAndExt(
                persistentMime,
                persistentName?.substringAfterLast('.', "") ?: ""
            )
            allowedTypesList.contains(cat) || ((cat == "doc" || cat == "text") && (allowedTypesList.contains("doc") || allowedTypesList.contains("text")))
        }
    }

    fun executeContinuousFileAnalysis(
        fileName: String,
        mimeType: String,
        geminiUri: String?,
        binary: AttachmentProcessResult.BinaryAttachment?,
        extractedText: String?
    ) {
        val userKey = container.encryptedKeyManager.getApiKey()
        if (userKey.isNullOrBlank() || isStreaming) return

        val promptInstruction = prompt?.content?.trim()?.takeIf { it.isNotEmpty() }
        val effectivePrompt = promptInstruction ?: "Analyze: $fileName"

        streamJob = coroutineScope.launch {
            val userMsg = Message(
                conversationId = conversationId,
                role = "user",
                content = promptInstruction ?: "Attached: $fileName",
                attachmentName = fileName,
                attachmentMimeType = mimeType,
                createdAt = System.currentTimeMillis()
            )
            container.database.messageDao().insertMessage(userMsg)
            container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())

            isStreaming = true
            currentStreamingChunk = ""
            streamError = null

            var activeModel = selectedModel
            if (activeModel.isBlank()) {
                val cached = container.database.cachedModelDao().getCachedModelsList()
                activeModel = cached.firstOrNull()?.name ?: "gemini-1.5-flash"
            }

            val currentHistory = container.database.messageDao().getMessagesListForConversation(conversationId)
            val historyWithoutCurrent = currentHistory.dropLast(1)

            val systemInstruction = buildString {
                if (!prompt?.content.isNullOrBlank()) {
                    appendLine(prompt?.content)
                    appendLine()
                }
                if (!extractedText.isNullOrBlank()) {
                    appendLine("Active Document Reference: [$fileName]")
                    appendLine("=== Document Content ===")
                    appendLine(extractedText)
                    appendLine("=== End of Document ===")
                    appendLine("The user is conversing with you continuously regarding this active document. Retain full context across questions and answer concisely.")
                }
            }

            try {
                val streamFlow = container.geminiChatService.streamChatResponse(
                    apiKey = userKey,
                    modelName = activeModel,
                    promptSystemInstruction = systemInstruction,
                    history = historyWithoutCurrent,
                    currentUserMessage = effectivePrompt,
                    geminiFileUri = geminiUri,
                    geminiFileMimeType = mimeType,
                    binaryAttachment = if (geminiUri.isNullOrBlank()) binary else null
                )

                val fullResponse = StringBuilder()
                streamFlow.collect { token ->
                    fullResponse.append(token)
                    currentStreamingChunk = fullResponse.toString()
                }

                val modelMsg = Message(
                    conversationId = conversationId,
                    role = "model",
                    content = fullResponse.toString(),
                    createdAt = System.currentTimeMillis()
                )
                container.database.messageDao().insertMessage(modelMsg)
                container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val errorMsg = e.localizedMessage ?: "Failed to generate response"
                    streamError = when {
                        errorMsg.contains("401") || errorMsg.contains("403") -> "Invalid API key. Check Settings."
                        errorMsg.contains("404") -> "Model unavailable. Try another from the list."
                        errorMsg.contains("429") -> "Rate limited. Wait a moment."
                        else -> "Network error: $errorMsg"
                    }

                    if (currentStreamingChunk.isNotBlank()) {
                        val partialMsg = Message(
                            conversationId = conversationId,
                            role = "model",
                            content = currentStreamingChunk,
                            createdAt = System.currentTimeMillis()
                        )
                        container.database.messageDao().insertMessage(partialMsg)
                    }
                }
            } finally {
                isStreaming = false
                currentStreamingChunk = ""
                streamJob = null
            }
        }
    }

    // Helper to persist attachment context (Section 3.3)
    fun persistAttachmentFromUri(uri: Uri) {
        val fileName = FileAttachmentHelper.getFileName(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val detectedMime = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val category = FileAttachmentHelper.detectCategoryFromMimeAndExt(detectedMime, ext)

        // Validate prompt allowed types
        val isAllowed = allowedTypesList.contains(category) ||
            ((category == "doc" || category == "text") && (allowedTypesList.contains("doc") || allowedTypesList.contains("text")))
        if (!isAllowed) {
            attachmentFeedback = "This prompt only accepts ${allowedTypesList.joinToString(", ")}."
            return
        }

        isProcessingAttachment = true
        attachmentProcessingMessage = "Processing file..."
        attachmentFeedback = null
        fileAccessibilityWarning = null

        coroutineScope.launch {
            try {
                // Take persistable URI permission so it survives app restarts
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) {
                }

                val result = FileAttachmentHelper.processUri(context, uri)
                val userKey = container.encryptedKeyManager.getApiKey() ?: ""

                when (result) {
                    is AttachmentProcessResult.Error -> {
                        attachmentFeedback = result.message
                    }
                    is AttachmentProcessResult.TextAttachment -> {
                        var geminiUri: String? = null
                        if (userKey.isNotBlank()) {
                            val upload = GeminiFileUploader.uploadFile(context, userKey, uri, "text/plain", result.fileName)
                            if (upload is GeminiUploadResult.Success) geminiUri = upload.fileUri
                        }
                        container.database.conversationDao().updatePersistentAttachment(
                            id = conversationId,
                            uri = uri.toString(),
                            name = result.fileName,
                            mimeType = "text/plain",
                            extractedText = result.extractedRawText,
                            geminiFileUri = geminiUri
                        )
                        if ((prompt?.autoExecuteOnFile == true) || autoExecuteOnFile) {
                            executeContinuousFileAnalysis(
                                fileName = result.fileName,
                                mimeType = "text/plain",
                                geminiUri = geminiUri,
                                binary = null,
                                extractedText = result.extractedRawText
                            )
                        }
                    }
                    is AttachmentProcessResult.BinaryAttachment -> {
                        var geminiUri: String? = null
                        if (userKey.isNotBlank()) {
                            val upload = GeminiFileUploader.uploadFile(context, userKey, uri, result.mimeType, result.fileName)
                            if (upload is GeminiUploadResult.Success) geminiUri = upload.fileUri
                        }
                        container.database.conversationDao().updatePersistentAttachment(
                            id = conversationId,
                            uri = uri.toString(),
                            name = result.fileName,
                            mimeType = result.mimeType,
                            extractedText = null,
                            geminiFileUri = geminiUri
                        )
                        if ((prompt?.autoExecuteOnFile == true) || autoExecuteOnFile) {
                            executeContinuousFileAnalysis(
                                fileName = result.fileName,
                                mimeType = result.mimeType,
                                geminiUri = geminiUri,
                                binary = result,
                                extractedText = null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                attachmentFeedback = "Failed to process file: ${e.localizedMessage}"
            } finally {
                isProcessingAttachment = false
                attachmentProcessingMessage = null
            }
        }
    }

    // Launchers for attachments
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) persistAttachmentFromUri(uri)
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) persistAttachmentFromUri(uri)
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) persistAttachmentFromUri(uri)
    }

    // Direct file picker: opens system file picker directly matching prompt format without intermediate sheet
    fun launchDirectPicker() {
        if (isStreaming || isProcessingAttachment) return
        val format = prompt?.allowedFileTypes?.lowercase() ?: ""
        when {
            format.contains("image") && !format.contains("pdf") && !format.contains("doc") -> {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            format.contains("audio") -> {
                audioPickerLauncher.launch("audio/*")
            }
            format.contains("pdf") -> {
                docPickerLauncher.launch(arrayOf("application/pdf"))
            }
            format.contains("doc") || format.contains("text") -> {
                docPickerLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/json",
                        "application/xml"
                    )
                )
            }
            else -> {
                docPickerLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        val chunkToSave = currentStreamingChunk
        if (chunkToSave.isNotBlank()) {
            coroutineScope.launch {
                val partialMsg = Message(
                    conversationId = conversationId,
                    role = "model",
                    content = chunkToSave,
                    createdAt = System.currentTimeMillis()
                )
                container.database.messageDao().insertMessage(partialMsg)
                container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())
            }
        }
        isStreaming = false
        currentStreamingChunk = ""
        streamJob = null
    }

    fun sendMessage() {
        val rawInput = inputText.trim()
        if (rawInput.isEmpty()) return
        val conv = conversation ?: return
        if (!isKeyVerified || isStreaming || isProcessingAttachment) return

        val userKey = container.encryptedKeyManager.getApiKey() ?: return
        val effectiveUserPrompt = rawInput
        var binaryToSend: AttachmentProcessResult.BinaryAttachment? = null

        // Check persistent file context
        if (conv.persistentAttachmentUri != null && isPersistentTypeAllowed) {
            if (conv.persistentAttachmentMimeType != null && conv.extractedContextText == null) {
                val uri = Uri.parse(conv.persistentAttachmentUri)
                val binary = FileAttachmentHelper.loadBinaryFromCacheOrUri(
                    context = context,
                    uri = uri,
                    fileName = conv.persistentAttachmentName ?: "file",
                    mimeType = conv.persistentAttachmentMimeType
                )
                if (binary != null) {
                    binaryToSend = binary
                } else {
                    fileAccessibilityWarning = "The attached file is no longer accessible. Attach a new one if needed."
                }
            }
        }

        streamJob = coroutineScope.launch {
            val userMsg = Message(
                conversationId = conversationId,
                role = "user",
                content = rawInput,
                attachmentName = null,
                attachmentMimeType = null,
                createdAt = System.currentTimeMillis()
            )
            container.database.messageDao().insertMessage(userMsg)
            container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())

            inputText = ""
            isStreaming = true
            currentStreamingChunk = ""
            streamError = null

            // Determine active model
            var activeModel = selectedModel
            if (activeModel.isBlank()) {
                val cached = container.database.cachedModelDao().getCachedModelsList()
                activeModel = cached.firstOrNull()?.name ?: "gemini-1.5-flash"
            }

            val currentHistory = container.database.messageDao().getMessagesListForConversation(conversationId)
            val historyWithoutCurrent = currentHistory.dropLast(1)

            val systemInstruction = buildString {
                if (!prompt?.content.isNullOrBlank()) {
                    appendLine(prompt?.content)
                    appendLine()
                }
                if (conv.persistentAttachmentUri != null && isPersistentTypeAllowed && !conv.extractedContextText.isNullOrBlank()) {
                    appendLine("Active Document Reference: [${conv.persistentAttachmentName ?: "File"}]")
                    appendLine("=== Document Content ===")
                    appendLine(conv.extractedContextText)
                    appendLine("=== End of Document ===")
                    appendLine("The user is conversing with you continuously regarding this active document. Retain full context across questions and answer concisely.")
                }
            }

            try {
                val streamFlow = container.geminiChatService.streamChatResponse(
                    apiKey = userKey,
                    modelName = activeModel,
                    promptSystemInstruction = systemInstruction,
                    history = historyWithoutCurrent,
                    currentUserMessage = effectiveUserPrompt,
                    geminiFileUri = conv.geminiFileUri,
                    geminiFileMimeType = conv.persistentAttachmentMimeType,
                    binaryAttachment = if (conv.geminiFileUri.isNullOrBlank()) binaryToSend else null
                )

                val fullResponse = StringBuilder()
                streamFlow.collect { token ->
                    fullResponse.append(token)
                    currentStreamingChunk = fullResponse.toString()
                }

                val modelMsg = Message(
                    conversationId = conversationId,
                    role = "model",
                    content = fullResponse.toString(),
                    createdAt = System.currentTimeMillis()
                )
                container.database.messageDao().insertMessage(modelMsg)
                container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val errorMsg = e.localizedMessage ?: "Failed to generate response"
                    streamError = when {
                        errorMsg.contains("401") || errorMsg.contains("403") -> "Invalid API key. Check Settings."
                        errorMsg.contains("404") -> "Model unavailable. Try another from the list."
                        errorMsg.contains("429") -> "Rate limited. Wait a moment."
                        else -> "Network error: $errorMsg"
                    }

                    if (currentStreamingChunk.isNotBlank()) {
                        val partialMsg = Message(
                            conversationId = conversationId,
                            role = "model",
                            content = currentStreamingChunk,
                            createdAt = System.currentTimeMillis()
                        )
                        container.database.messageDao().insertMessage(partialMsg)
                    }
                }
            } finally {
                isStreaming = false
                currentStreamingChunk = ""
                streamJob = null
            }
        }
    }

    fun executeInitialFilePrompt(conv: Conversation) {
        val userKey = container.encryptedKeyManager.getApiKey()
        if (userKey.isNullOrBlank() || isStreaming || isProcessingAttachment) return

        val promptInstruction = prompt?.content?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveUserPrompt = promptInstruction ?: if (conv.persistentAttachmentName != null) {
            "Analyze: ${conv.persistentAttachmentName}"
        } else {
            "Analyze attached content"
        }
        var binaryToSend: AttachmentProcessResult.BinaryAttachment? = null

        if (conv.persistentAttachmentUri != null) {
            if (conv.persistentAttachmentMimeType != null && conv.extractedContextText == null) {
                val uri = Uri.parse(conv.persistentAttachmentUri)
                val binary = FileAttachmentHelper.loadBinaryFromCacheOrUri(
                    context = context,
                    uri = uri,
                    fileName = conv.persistentAttachmentName ?: "file",
                    mimeType = conv.persistentAttachmentMimeType
                )
                if (binary != null) binaryToSend = binary
            }
        }

        streamJob = coroutineScope.launch {
            val userMsg = Message(
                conversationId = conversationId,
                role = "user",
                content = promptInstruction ?: "Attached: ${conv.persistentAttachmentName ?: "File"}",
                attachmentName = conv.persistentAttachmentName,
                attachmentMimeType = conv.persistentAttachmentMimeType,
                createdAt = System.currentTimeMillis()
            )
            container.database.messageDao().insertMessage(userMsg)
            container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())

            isStreaming = true
            currentStreamingChunk = ""
            streamError = null

            var activeModel = selectedModel
            if (activeModel.isBlank()) {
                val cached = container.database.cachedModelDao().getCachedModelsList()
                activeModel = cached.firstOrNull()?.name ?: "gemini-1.5-flash"
            }

            val currentHistory = container.database.messageDao().getMessagesListForConversation(conversationId)
            val historyWithoutCurrent = currentHistory.dropLast(1)

            val systemInstruction = buildString {
                if (!prompt?.content.isNullOrBlank()) {
                    appendLine(prompt?.content)
                    appendLine()
                }
                if (conv.persistentAttachmentUri != null && !conv.extractedContextText.isNullOrBlank()) {
                    appendLine("Active Document Reference: [${conv.persistentAttachmentName ?: "File"}]")
                    appendLine("=== Document Content ===")
                    appendLine(conv.extractedContextText)
                    appendLine("=== End of Document ===")
                    appendLine("The user is conversing with you continuously regarding this active document. Retain full context across questions and answer concisely.")
                }
            }

            try {
                val streamFlow = container.geminiChatService.streamChatResponse(
                    apiKey = userKey,
                    modelName = activeModel,
                    promptSystemInstruction = systemInstruction,
                    history = historyWithoutCurrent,
                    currentUserMessage = effectiveUserPrompt,
                    geminiFileUri = conv.geminiFileUri,
                    geminiFileMimeType = conv.persistentAttachmentMimeType,
                    binaryAttachment = if (conv.geminiFileUri.isNullOrBlank()) binaryToSend else null
                )

                val fullResponse = StringBuilder()
                streamFlow.collect { token ->
                    fullResponse.append(token)
                    currentStreamingChunk = fullResponse.toString()
                }

                val modelMsg = Message(
                    conversationId = conversationId,
                    role = "model",
                    content = fullResponse.toString(),
                    createdAt = System.currentTimeMillis()
                )
                container.database.messageDao().insertMessage(modelMsg)
                container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val errorMsg = e.localizedMessage ?: "Failed to generate response"
                    streamError = when {
                        errorMsg.contains("401") || errorMsg.contains("403") -> "Invalid API key. Check Settings."
                        errorMsg.contains("404") -> "Model unavailable. Try another from the list."
                        errorMsg.contains("429") -> "Rate limited. Wait a moment."
                        else -> "Network error: $errorMsg"
                    }

                    if (currentStreamingChunk.isNotBlank()) {
                        val partialMsg = Message(
                            conversationId = conversationId,
                            role = "model",
                            content = currentStreamingChunk,
                            createdAt = System.currentTimeMillis()
                        )
                        container.database.messageDao().insertMessage(partialMsg)
                    }
                }
            } finally {
                isStreaming = false
                currentStreamingChunk = ""
                streamJob = null
            }
        }
    }

    // Auto-execute prompt when file is attached and feature is enabled
    LaunchedEffect(conversation?.id, prompt?.id, conversation?.persistentAttachmentUri) {
        val conv = conversation
        val pr = prompt
        val hasKey = !container.encryptedKeyManager.getApiKey().isNullOrBlank()
        val isAuto = (pr?.autoExecuteOnFile == true) || autoExecuteOnFile
        if (conv != null && pr != null && hasKey && isAuto && !hasAutoExecuted) {
            if (conv.persistentAttachmentUri != null && !isStreaming) {
                val existing = container.database.messageDao().getMessagesListForConversation(conversationId)
                if (existing.isEmpty()) {
                    hasAutoExecuted = true
                    executeInitialFilePrompt(conv)
                }
            }
        }
    }

    fun resendUserMessage(msg: Message) {
        if (isStreaming) return
        val userKey = container.encryptedKeyManager.getApiKey() ?: return
        if (!isKeyVerified) {
            Toast.makeText(context, "Configure your Gemini API key in Settings.", Toast.LENGTH_SHORT).show()
            return
        }

        val conv = conversation ?: return
        val effectivePrompt = msg.content
        var binaryToSend: AttachmentProcessResult.BinaryAttachment? = null

        if (conv.persistentAttachmentUri != null && isPersistentTypeAllowed) {
            if (conv.persistentAttachmentMimeType != null && conv.extractedContextText == null) {
                val uri = Uri.parse(conv.persistentAttachmentUri)
                val binary = FileAttachmentHelper.loadBinaryFromCacheOrUri(
                    context = context,
                    uri = uri,
                    fileName = conv.persistentAttachmentName ?: "file",
                    mimeType = conv.persistentAttachmentMimeType
                )
                if (binary != null) binaryToSend = binary
            }
        }

        coroutineScope.launch {
            isStreaming = true
            currentStreamingChunk = ""
            streamError = null

            var activeModel = selectedModel
            if (activeModel.isBlank()) {
                val cached = container.database.cachedModelDao().getCachedModelsList()
                activeModel = cached.firstOrNull()?.name ?: "gemini-1.5-flash"
            }

            val currentHistory = container.database.messageDao().getMessagesListForConversation(conversationId)
            val historyBeforeThis = currentHistory.filter { it.id < msg.id }

            val systemInstruction = buildString {
                if (!prompt?.content.isNullOrBlank()) {
                    appendLine(prompt?.content)
                    appendLine()
                }
                if (conv.persistentAttachmentUri != null && isPersistentTypeAllowed && !conv.extractedContextText.isNullOrBlank()) {
                    appendLine("Active Document Reference: [${conv.persistentAttachmentName ?: "File"}]")
                    appendLine("=== Document Content ===")
                    appendLine(conv.extractedContextText)
                    appendLine("=== End of Document ===")
                    appendLine("The user is conversing with you continuously regarding this active document. Retain full context across questions and answer concisely.")
                }
            }

            try {
                val streamFlow = container.geminiChatService.streamChatResponse(
                    apiKey = userKey,
                    modelName = activeModel,
                    promptSystemInstruction = systemInstruction,
                    history = historyBeforeThis,
                    currentUserMessage = effectivePrompt,
                    geminiFileUri = conv.geminiFileUri,
                    geminiFileMimeType = conv.persistentAttachmentMimeType,
                    binaryAttachment = if (conv.geminiFileUri.isNullOrBlank()) binaryToSend else null
                )

                val fullResponse = StringBuilder()
                streamFlow.collect { token ->
                    fullResponse.append(token)
                    currentStreamingChunk = fullResponse.toString()
                }

                val modelMsg = Message(
                    conversationId = conversationId,
                    role = "model",
                    content = fullResponse.toString(),
                    createdAt = System.currentTimeMillis()
                )
                container.database.messageDao().insertMessage(modelMsg)
                container.database.conversationDao().updateLastMessageAt(conversationId, System.currentTimeMillis())
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Failed to generate response"
                streamError = when {
                    errorMsg.contains("401") || errorMsg.contains("403") -> "Invalid API key. Check Settings."
                    errorMsg.contains("404") -> "Model unavailable. Try another from the list."
                    errorMsg.contains("429") -> "Rate limited. Wait a moment."
                    else -> "Network error: $errorMsg"
                }

                if (currentStreamingChunk.isNotBlank()) {
                    val partialMsg = Message(
                        conversationId = conversationId,
                        role = "model",
                        content = currentStreamingChunk,
                        createdAt = System.currentTimeMillis()
                    )
                    container.database.messageDao().insertMessage(partialMsg)
                }
            } finally {
                isStreaming = false
                currentStreamingChunk = ""
            }
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            val total = messages.size
            if (total == 0) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < total - 2
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    PromptIconDisplay(
                                        iconString = prompt?.icon ?: "icon:chat",
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = prompt?.title ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            conversation?.let { conv ->
                                if (conv.title != prompt?.title && conv.title.isNotBlank()) {
                                    Text(
                                        text = conv.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("conversation_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // In-chat model switcher button right next to the title on the right
                    Box {
                        Surface(
                            onClick = { isModelMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("in_chat_model_switcher")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activeModelDisplay = if (selectedModel.isNotBlank()) {
                                    selectedModel.replace("models/", "")
                                } else {
                                    "gemini-1.5-flash"
                                }
                                Text(
                                    text = activeModelDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch model",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isModelMenuExpanded,
                            onDismissRequest = { isModelMenuExpanded = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Select Gemini Model",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val availableModels = if (cachedModels.isEmpty()) {
                                listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash")
                            } else {
                                cachedModels.map { it.name }
                            }

                            availableModels.forEach { modelName ->
                                val cleanName = modelName.replace("models/", "")
                                val isCurrent = selectedModel == modelName || selectedModel == cleanName || (selectedModel.isBlank() && cleanName == "gemini-1.5-flash")
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cleanName,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            container.userPreferencesRepository.setDefaultModel(cleanName)
                                            Toast.makeText(context, "Switched model to $cleanName", Toast.LENGTH_SHORT).show()
                                        }
                                        isModelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val currentPrompt = prompt
                            val currentConv = conversation
                            if (currentPrompt != null && currentConv != null) {
                                val json = JsonShareHelper.createConversationJson(
                                    promptTitle = currentPrompt.title,
                                    promptContent = currentPrompt.content,
                                    promptIcon = currentPrompt.icon,
                                    conversationTitle = currentConv.title,
                                    messages = messages
                                )
                                val safeTitle = currentConv.title.lowercase().replace("[^a-zA-Z0-9_]".toRegex(), "_")
                                val filename = "chat_${safeTitle.ifBlank { "conversation" }}.json"
                                JsonShareHelper.shareJsonContent(context, filename, json)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share conversation as JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Box {
                        IconButton(onClick = { overflowMenuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = overflowMenuOpen,
                            onDismissRequest = { overflowMenuOpen = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    overflowMenuOpen = false
                                    isRenameDialogOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear history") },
                                onClick = {
                                    overflowMenuOpen = false
                                    isClearDialogOpen = true
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share conversation as JSON") },
                                onClick = {
                                    overflowMenuOpen = false
                                    val currentPrompt = prompt
                                    val currentConv = conversation
                                    if (currentPrompt != null && currentConv != null) {
                                        val json = JsonShareHelper.createConversationJson(
                                            promptTitle = currentPrompt.title,
                                            promptContent = currentPrompt.content,
                                            promptIcon = currentPrompt.icon,
                                            conversationTitle = currentConv.title,
                                            messages = messages
                                        )
                                        val filename = "chat_${currentConv.title.lowercase().replace(" ", "_")}.json"
                                        JsonShareHelper.shareJsonContent(context, filename, json)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    overflowMenuOpen = false
                                    isDeleteDialogOpen = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Warning banner if key not verified
            if (!isKeyVerified) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Configure your Gemini API key in Settings to chat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onNavigateToSettings) {
                            Text("Settings")
                        }
                    }
                }
            }

            // Inaccessible file banner
            fileAccessibilityWarning?.let { warning ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { fileAccessibilityWarning = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // STICKY PERSISTENT CONTEXT CHIP (Section 3.3)
            val currentPersistentUri = conversation?.persistentAttachmentUri
            val currentPersistentName = conversation?.persistentAttachmentName
            if (currentPersistentUri != null && currentPersistentName != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$currentPersistentName — context active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    container.database.conversationDao().clearPersistentAttachment(conversationId)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove persistent context",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Warning if persistent file type is disallowed by current prompt (Section 3.4)
            if (currentPersistentUri != null && !isPersistentTypeAllowed) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "The attached file is not in this prompt's allowed types (${allowedTypesList.joinToString(", ")}). It is excluded from requests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Messages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isStreaming) {
                    // Empty conversation state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PromptIconDisplay(
                                    iconString = prompt?.icon ?: "icon:chat",
                                    size = 30.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Start the conversation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt?.title?.let { "Chatting with $it prompt" } ?: "Type a message below to begin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                            val prevMsg = if (index > 0) messages[index - 1] else null
                            val isSameSender = prevMsg?.role == msg.role
                            val timeGapLarge = prevMsg?.let { (msg.createdAt - it.createdAt) > 5 * 60 * 1000L } ?: false

                            // If large time gap, show small centered timestamp
                            if (timeGapLarge) {
                                CenteredTimestamp(msg.createdAt)
                            }

                            val isLastMsg = index == messages.size - 1
                            val isLastUserMsgWithoutReply = isLastMsg && msg.role == "user" && !isStreaming

                            MessageBubble(
                                msg = msg,
                                isSameSender = isSameSender,
                                isDark = isDark,
                                showRetry = isLastUserMsgWithoutReply,
                                onRetry = { resendUserMessage(msg) }
                            )
                        }

                        // Streaming chunk bubble
                        if (isStreaming && currentStreamingChunk.isNotBlank()) {
                            item(key = "streaming_token_bubble") {
                                ModelStreamingBubble(text = currentStreamingChunk)
                            }
                        } else if (isStreaming && currentStreamingChunk.isBlank()) {
                            item(key = "streaming_indicator") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PulsingDot()
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "Thinking...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Stream error chip
                        streamError?.let { err ->
                            item(key = "stream_error_chip") {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = err,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                        if (lastUserMsg != null) {
                                            TextButton(
                                                onClick = { resendUserMessage(lastUserMsg) },
                                                modifier = Modifier.testTag("retry_from_error_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Retry")
                                            }
                                        }
                                        TextButton(onClick = { streamError = null }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Scroll to bottom FAB
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val total = messages.size + (if (currentStreamingChunk.isNotEmpty()) 1 else 0)
                                if (total > 0) listState.animateScrollToItem(total - 1)
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        elevation = FloatingActionButtonDefaults.elevation(2.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Attachment feedback error if any
            attachmentFeedback?.let { feedback ->
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Replacement / Attachment processing indicator
            if (isProcessingAttachment) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = attachmentProcessingMessage ?: "Replacing attachment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // FLOATING INPUT BAR (Section 7.2)
            // Single rounded container (28dp radius) with text field + trailing send button (circular, accent).
            // Attachment icon inside on the left. Floats 12dp above nav with subtle surface tint. No elevation.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment button on the LEFT - directly launches picker
                        if (allowedTypesList.isNotEmpty()) {
                            IconButton(
                                onClick = { launchDirectPicker() },
                                enabled = !isStreaming && !isProcessingAttachment,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("add_attachment_button")
                            ) {
                                if (isProcessingAttachment) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "Add attachment",
                                        tint = if (!isStreaming && !isProcessingAttachment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        val isInputArabic = remember(inputText) {
                            inputText.any {
                                it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
                                it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
                            }
                        }

                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (isInputArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            TextField(
                                value = if (isAutoExecuteMode) "" else inputText,
                                onValueChange = { if (!isAutoExecuteMode) inputText = it },
                                placeholder = {
                                    Text(
                                        if (isProcessingAttachment) "Processing file..."
                                        else if (isAutoExecuteMode) "Auto-execute active: Select a file to analyze"
                                        else "Type a message...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = if (isInputArabic) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_input_field"),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                maxLines = 4,
                                enabled = isKeyVerified && !isProcessingAttachment && !isAutoExecuteMode
                            )
                        }

                        val canSend = isKeyVerified && !isStreaming && !isProcessingAttachment && !isAutoExecuteMode && inputText.trim().isNotEmpty()

                        // Send / Stop button on the RIGHT
                        Surface(
                            onClick = {
                                if (isStreaming) {
                                    stopStreaming()
                                } else if (canSend) {
                                    sendMessage()
                                }
                            },
                            enabled = isStreaming || canSend,
                            shape = CircleShape,
                            color = when {
                                isStreaming -> MaterialTheme.colorScheme.primaryContainer
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag(if (isStreaming) "stop_streaming_button" else "send_message_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isStreaming) {
                                    // Spinning loading indicator around stop icon
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Stop generation",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Rename Dialog
    if (isRenameDialogOpen) {
        var newTitle by remember { mutableStateOf(conversation?.title ?: "") }
        AlertDialog(
            onDismissRequest = { isRenameDialogOpen = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = newTitle.trim()
                        if (clean.isNotEmpty()) {
                            coroutineScope.launch {
                                container.database.conversationDao().renameConversation(conversationId, clean)
                                isRenameDialogOpen = false
                            }
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenameDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Clear History Dialog
    if (isClearDialogOpen) {
        AlertDialog(
            onDismissRequest = { isClearDialogOpen = false },
            title = { Text("Clear chat history?") },
            text = { Text("All messages in this conversation will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            container.database.messageDao().deleteMessagesForConversation(conversationId)
                            isClearDialogOpen = false
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isClearDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Delete Conversation Dialog
    if (isDeleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteDialogOpen = false },
            title = { Text("Delete conversation?") },
            text = { Text("This will permanently remove the conversation and all of its messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            container.database.conversationDao().deleteConversationById(conversationId)
                            isDeleteDialogOpen = false
                            onBack()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun stripMarkdownBold(text: String): String {
    var cleaned = text
    // Replace ***bold italic*** with *italic* (strips bold)
    cleaned = cleaned.replace(Regex("""\*\*\*(.+?)\*\*\*"""), "*$1*")
    // Replace **bold** with plain text
    cleaned = cleaned.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    // Replace __bold__ with plain text
    cleaned = cleaned.replace(Regex("""__(.+?)__"""), "$1")
    // Remove any leftover repeated asterisks (such as **** or **)
    cleaned = cleaned.replace(Regex("""\*{2,}"""), "")
    return cleaned
}

@Composable
fun MessageBubble(
    msg: Message,
    isSameSender: Boolean,
    isDark: Boolean,
    showRetry: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val isUser = msg.role == "user"
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val userBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val bubbleColor = if (isUser) userBgColor else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isSameSender) 0.dp else 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                text = "Gemini",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else if (!isSameSender) {
            Text(
                text = "You",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (isUser && showRetry) {
                Surface(
                    onClick = onRetry,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 6.dp)
                        .testTag("retry_message_button_${msg.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry sending message",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = if (isUser) 320.dp else 360.dp)
                    .testTag("message_bubble_${msg.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!msg.attachmentName.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = msg.attachmentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (isUser) {
                        val isArabic = remember(msg.content) {
                            msg.content.any {
                                it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
                                it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
                            }
                        }
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = if (isArabic) TextDirection.Rtl else TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        MarkdownMessageView(
                            content = msg.content,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Bottom transparent copy button for Gemini message
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    val cleanContent = stripMarkdownBold(msg.content)
                                    clipboardManager.setText(AnnotatedString(cleanContent))
                                    Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(30.dp)
                                    .alpha(0.65f)
                                    .testTag("copy_message_button_${msg.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy message",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelStreamingBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .testTag("model_streaming_bubble")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                MarkdownMessageView(
                    content = text,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot()
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Responding...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
    )
}

@Composable
fun CenteredTimestamp(timeMs: Long) {
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatter.format(Date(timeMs)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}
