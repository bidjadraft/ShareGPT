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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.data.db.Prompt
import com.example.data.file.AttachmentProcessResult
import com.example.data.file.FileAttachmentHelper
import com.example.data.file.JsonShareHelper
import com.example.data.gemini.GeminiFileUploader
import com.example.data.gemini.GeminiUploadResult
import com.example.di.AppContainer
import com.example.ui.components.PromptIconDisplay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PromptsScreen(
    container: AppContainer,
    onOpenConversation: (conversationId: Long) -> Unit,
    onStartChatWithPrompt: ((Prompt) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prompts by container.database.promptDao().getAllPrompts().collectAsState(initial = emptyList())

    var promptToEdit by remember { mutableStateOf<Prompt?>(null) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var promptToDelete by remember { mutableStateOf<Prompt?>(null) }

    var pendingPromptForChat by remember { mutableStateOf<Prompt?>(null) }
    var isUploadingToGemini by remember { mutableStateOf(false) }
    var uploadStatusMessage by remember { mutableStateOf("Processing and uploading to Gemini...") }

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
            onOpenConversation(newConvId)
        }
    }

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
                onOpenConversation(newId)
            }
        } else {
            // Dedicated file format: immediately launch file picker
            pendingPromptForChat = prompt
            launchPickerForType(primaryType)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Prompts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreateDialogOpen = true },
                modifier = Modifier.testTag("create_prompt_fab"),
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Prompt")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (prompts.isEmpty()) {
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
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No prompts yet. Tap + to create your first prompt.",
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
                    items(prompts, key = { it.id }) { prompt ->
                        var menuExpanded by remember { mutableStateOf(false) }

                        val rawType = prompt.allowedFileTypes.lowercase()
                        val formatBadge = when {
                            rawType.contains("pdf") -> "PDF"
                            rawType.contains("image") -> "Image"
                            rawType.contains("audio") -> "Audio"
                            rawType.contains("doc") || rawType.contains("text") -> "Doc"
                            else -> "Chat (No files)"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_card_${prompt.id}")
                                .combinedClickable(
                                    onClick = { initiateChatFlowWithPrompt(prompt) },
                                    onLongClick = { promptToDelete = prompt }
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
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Large Leading Icon
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PromptIconDisplay(
                                            iconString = prompt.icon,
                                            size = 28.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prompt.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { promptToEdit = prompt },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("edit_prompt_${prompt.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit prompt",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Box {
                                                IconButton(
                                                    onClick = { menuExpanded = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = "Options",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = menuExpanded,
                                                    onDismissRequest = { menuExpanded = false },
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Start Chat") },
                                                        onClick = {
                                                            menuExpanded = false
                                                            initiateChatFlowWithPrompt(prompt)
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Edit") },
                                                        onClick = {
                                                            menuExpanded = false
                                                            promptToEdit = prompt
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Edit, contentDescription = null)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Share as file") },
                                                        onClick = {
                                                            menuExpanded = false
                                                            val json = JsonShareHelper.createPromptJson(prompt)
                                                            JsonShareHelper.shareJsonContent(
                                                                context = context,
                                                                fileName = "prompt_${prompt.title.lowercase().replace(" ", "_")}.json",
                                                                jsonContent = json
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Default.Share, contentDescription = null)
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            promptToDelete = prompt
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
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = prompt.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = formatBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
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

    // Create prompt dialog
    if (isCreateDialogOpen) {
        PromptEditorDialog(
            initialPrompt = null,
            onDismiss = { isCreateDialogOpen = false },
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
                    isCreateDialogOpen = false
                    Toast.makeText(context, "Prompt saved", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Edit prompt dialog
    promptToEdit?.let { prompt ->
        PromptEditorDialog(
            initialPrompt = prompt,
            onDismiss = { promptToEdit = null },
            onSave = { updatedTitle, updatedContent, updatedIcon, updatedAllowedFileTypes, updatedAutoExecuteOnFile ->
                coroutineScope.launch {
                    container.database.promptDao().updatePrompt(
                        prompt.copy(
                            title = updatedTitle,
                            content = updatedContent,
                            icon = updatedIcon,
                            allowedFileTypes = updatedAllowedFileTypes,
                            autoExecuteOnFile = updatedAutoExecuteOnFile,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    promptToEdit = null
                }
            }
        )
    }

    // Delete confirmation dialog
    promptToDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { promptToDelete = null },
            title = { Text("Delete prompt?") },
            text = { Text("Are you sure you want to delete \"${prompt.title}\"? Any associated chat history will also be removed.") },
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
}
