package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.Conversation
import com.example.data.db.ConversationWithPrompt
import com.example.data.db.Prompt
import com.example.data.file.AttachmentProcessResult
import com.example.data.file.FileAttachmentHelper
import com.example.data.file.JsonShareHelper
import com.example.data.gemini.GeminiFileUploader
import com.example.data.gemini.GeminiUploadResult
import com.example.di.AppContainer
import com.example.ui.components.PromptIconDisplay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    container: AppContainer,
    onOpenConversation: (conversationId: Long) -> Unit,
    onNavigateToPrompts: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // initial = null prevents showing "No chats yet" before Room query resolves
    val conversationsState by container.database.conversationDao()
        .getAllConversationsWithPrompt()
        .collectAsState(initial = null)

    val prompts by container.database.promptDao().getAllPrompts().collectAsState(initial = emptyList())

    var isBottomSheetOpen by remember { mutableStateOf(false) }
    var isCreatePromptDialogOpen by remember { mutableStateOf(false) }
    var promptToEdit by remember { mutableStateOf<Prompt?>(null) }
    var promptToDelete by remember { mutableStateOf<Prompt?>(null) }
    var conversationToDelete by remember { mutableStateOf<ConversationWithPrompt?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // File selection & upload states for prompt-initiated chat
    var pendingPromptForChat by remember { mutableStateOf<Prompt?>(null) }
    var isUploadingToGemini by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf("Uploading file to Gemini...") }

    val timeFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    // Helper to process picked file and start chat
    fun onAttachmentSelected(uri: Uri?) {
        val targetPrompt = pendingPromptForChat ?: return
        if (uri == null) {
            pendingPromptForChat = null
            return
        }

        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }

        isUploadingToGemini = true
        uploadStatusMessage = "Processing and uploading to Gemini..."

        coroutineScope.launch {
            val fileName = FileAttachmentHelper.getFileName(context, uri)
            val detectedMime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val processResult = FileAttachmentHelper.processUri(context, uri)
            val extractedText = if (processResult is AttachmentProcessResult.TextAttachment) {
                processResult.extractedRawText
            } else null

            val userApiKey = container.encryptedKeyManager.getApiKey() ?: ""
            var geminiFileUri: String? = null

            if (userApiKey.isNotBlank()) {
                val uploadResult = GeminiFileUploader.uploadFile(
                    context = context,
                    apiKey = userApiKey,
                    uri = uri,
                    mimeType = detectedMime,
                    displayName = fileName
                )
                if (uploadResult is GeminiUploadResult.Success) {
                    geminiFileUri = uploadResult.fileUri
                }
            }

            val newConv = Conversation(
                promptId = targetPrompt.id,
                title = targetPrompt.title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastMessageAt = System.currentTimeMillis(),
                persistentAttachmentUri = uri.toString(),
                persistentAttachmentName = fileName,
                persistentAttachmentMimeType = detectedMime,
                extractedContextText = extractedText,
                geminiFileUri = geminiFileUri
            )

            val newConvId = container.database.conversationDao().insertConversation(newConv)

            isUploadingToGemini = false
            pendingPromptForChat = null
            isBottomSheetOpen = false
            onOpenConversation(newConvId)
        }
    }

    // Launchers for picking files
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> onAttachmentSelected(uri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onAttachmentSelected(uri) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onAttachmentSelected(uri) }

    fun launchPickerForType(type: String) {
        when (type.lowercase()) {
            "image" -> photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            "audio" -> audioPickerLauncher.launch("audio/*")
            "pdf" -> docPickerLauncher.launch(arrayOf("application/pdf"))
            "doc", "text" -> docPickerLauncher.launch(
                arrayOf(
                    "text/*",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/json",
                    "application/rtf",
                    "application/xml",
                    "application/pdf"
                )
            )
            else -> docPickerLauncher.launch(arrayOf("*/*"))
        }
    }

    fun initiateChatFlowWithPrompt(prompt: Prompt) {
        val raw = prompt.allowedFileTypes.lowercase()
        val primaryType = when {
            raw.contains("pdf") -> "pdf"
            raw.contains("image") -> "image"
            raw.contains("audio") -> "audio"
            raw.contains("doc") || raw.contains("text") -> "doc"
            else -> "chat"
        }

        if (primaryType == "chat") {
            // Direct chat without files ("ادخل عادي")
            coroutineScope.launch {
                val newConv = Conversation(
                    promptId = prompt.id,
                    title = prompt.title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastMessageAt = System.currentTimeMillis()
                )
                val newId = container.database.conversationDao().insertConversation(newConv)
                isBottomSheetOpen = false
                onOpenConversation(newId)
            }
        } else {
            // Dedicated file format: immediately launch file picker
            pendingPromptForChat = prompt
            isBottomSheetOpen = false
            launchPickerForType(primaryType)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PromptChat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButtonPosition = FabPosition.Start,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isBottomSheetOpen = true },
                modifier = Modifier.testTag("start_chat_fab"),
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Start new chat")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val conversations = conversationsState

            if (conversations == null) {
                // Subtle loading state while Room query resolves (prevents flash of empty state)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {}
                    }
                }
            } else if (conversations.isEmpty()) {
                // Actual empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No chats yet. Start one from a prompt.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conversation_item_${conv.id}")
                                .combinedClickable(
                                    onClick = { onOpenConversation(conv.id) },
                                    onLongClick = { conversationToDelete = conv }
                                ),
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PromptIconDisplay(
                                            iconString = conv.promptIcon,
                                            size = 26.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = conv.promptTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = timeFormatter.format(Date(conv.lastMessageAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val previewText = conv.firstMessageSnippet?.takeIf { it.isNotBlank() } ?: conv.title
                                    Text(
                                        text = previewText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val msgs = container.database.messageDao().getMessagesListForConversation(conv.id)
                                                val json = JsonShareHelper.createConversationJson(
                                                    promptTitle = conv.promptTitle,
                                                    promptContent = conv.promptContent,
                                                    promptIcon = conv.promptIcon,
                                                    conversationTitle = conv.title,
                                                    messages = msgs
                                                )
                                                val safeTitle = conv.title.lowercase().replace("[^a-zA-Z0-9_]".toRegex(), "_")
                                                val filename = "chat_${safeTitle.ifBlank { "conversation" }}.json"
                                                JsonShareHelper.shareJsonContent(context, filename, json)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed to share conversation: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share conversation as JSON",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }

                                IconButton(
                                    onClick = { conversationToDelete = conv },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete conversation",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet for selecting a prompt to start a chat, with prompt creation, sharing, and deletion
    if (isBottomSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isBottomSheetOpen = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start chat with prompt",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a prompt to begin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { isCreatePromptDialogOpen = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("create_prompt_in_sheet_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (prompts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "You don't have any prompts yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { isCreatePromptDialogOpen = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("create_first_prompt_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Prompt")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(prompts, key = { it.id }) { prompt ->
                            Card(
                                onClick = { initiateChatFlowWithPrompt(prompt) },
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_select_item_${prompt.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            PromptIconDisplay(
                                                iconString = prompt.icon,
                                                size = 24.dp,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prompt.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = prompt.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        val rawType = prompt.allowedFileTypes.lowercase()
                                        val formatBadge = when {
                                            rawType.contains("pdf") -> "PDF"
                                            rawType.contains("image") -> "Image"
                                            rawType.contains("audio") -> "Audio"
                                            rawType.contains("doc") || rawType.contains("text") -> "Doc"
                                            else -> "Chat (No files)"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = formatBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Edit prompt button
                                    IconButton(
                                        onClick = { promptToEdit = prompt },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("edit_prompt_${prompt.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit prompt",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Share prompt JSON button next to Delete
                                    IconButton(
                                        onClick = {
                                            JsonShareHelper.shareJsonContent(
                                                context = context,
                                                fileName = "${prompt.title}.promptchat.json",
                                                jsonContent = JsonShareHelper.createPromptJson(prompt)
                                            )
                                            Toast.makeText(context, "Sharing prompt JSON", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("share_prompt_${prompt.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share prompt",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Delete prompt directly from sheet
                                    IconButton(
                                        onClick = { promptToDelete = prompt },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("delete_prompt_${prompt.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete prompt",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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



    // Uploading / Preparing File Dialog
    if (isUploadingToGemini) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        strokeWidth = 3.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = uploadStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Setting up continuous conversation session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Create Prompt Dialog directly opened from + sheet
    if (isCreatePromptDialogOpen) {
        PromptEditorDialog(
            onDismiss = { isCreatePromptDialogOpen = false },
            onSave = { title, content, icon, allowedFileTypes, autoExecuteOnFile ->
                coroutineScope.launch {
                    val newPrompt = Prompt(
                        title = title,
                        content = content,
                        icon = icon,
                        allowedFileTypes = allowedFileTypes,
                        autoExecuteOnFile = autoExecuteOnFile,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    container.database.promptDao().insertPrompt(newPrompt)
                    isCreatePromptDialogOpen = false
                    Toast.makeText(context, "Prompt saved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete Prompt confirmation dialog
    promptToDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text("Delete prompt?") },
            text = { Text("Are you sure you want to delete \"${prompt.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            container.database.promptDao().deletePrompt(prompt)
                            promptToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { promptToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Edit prompt dialog
    promptToEdit?.let { prompt ->
        PromptEditorDialog(
            initialPrompt = prompt,
            onDismiss = { promptToEdit = null },
            onSave = { title, content, icon, allowedFileTypes, autoExecuteOnFile ->
                coroutineScope.launch {
                    val updated = prompt.copy(
                        title = title,
                        content = content,
                        icon = icon,
                        allowedFileTypes = allowedFileTypes,
                        autoExecuteOnFile = autoExecuteOnFile,
                        updatedAt = System.currentTimeMillis()
                    )
                    container.database.promptDao().updatePrompt(updated)
                    promptToEdit = null
                    Toast.makeText(context, "Prompt updated", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete single conversation confirmation
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("Are you sure you want to delete this conversation with \"${conv.promptTitle}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            container.database.conversationDao().deleteConversationById(conv.id)
                            conversationToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}
