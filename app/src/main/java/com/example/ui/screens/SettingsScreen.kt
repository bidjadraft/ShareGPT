package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Prompt
import com.example.data.file.ConversationExportData
import com.example.data.file.ImportJsonResult
import com.example.data.file.JsonShareHelper
import com.example.data.prefs.AppFontSize
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.RetentionPeriod
import com.example.data.remote.KeyValidationResult
import com.example.data.remote.ModelFetchResult
import com.example.di.AppContainer
import com.example.ui.theme.getThemePreviewColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    // Preferences states
    val themeMode by container.userPreferencesRepository.themeModeFlow.collectAsState(initial = AppThemeMode.WARM)
    val fontSize by container.userPreferencesRepository.fontSizeFlow.collectAsState(initial = AppFontSize.MEDIUM)
    val defaultModel by container.userPreferencesRepository.defaultModelFlow.collectAsState(initial = "")
    val retentionPeriod by container.userPreferencesRepository.retentionPeriodFlow.collectAsState(initial = RetentionPeriod.DAYS_7)
    val autoExecuteOnFile by container.userPreferencesRepository.autoExecuteOnFileFlow.collectAsState(initial = true)

    // Cached models
    val cachedModels by container.database.cachedModelDao().getAllCachedModels().collectAsState(initial = emptyList())

    // API Key states
    var apiKeyInput by remember { mutableStateOf(container.encryptedKeyManager.getApiKey() ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isVerifyingKey by remember { mutableStateOf(false) }
    var keyValidationMessage by remember { mutableStateOf<String?>(null) }
    var isKeyValidSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Model refresh state
    var isRefreshingModels by remember { mutableStateOf(false) }
    var modelRefreshFeedback by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var isClearConversationsDialogOpen by remember { mutableStateOf(false) }
    var isOflDialogOpen by remember { mutableStateOf(false) }
    var importSummaryMessage by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    // Launcher for importing prompts JSON
    val importPromptsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                when (val result = JsonShareHelper.parseJsonFromUri(context, uri)) {
                    is ImportJsonResult.PromptImport -> {
                        val existing = container.database.promptDao().getPromptByTitle(result.title)
                        val title = if (existing != null) "${result.title} (imported)" else result.title
                        container.database.promptDao().insertPrompt(
                            Prompt(
                                title = title,
                                content = result.content,
                                icon = result.icon,
                                allowedFileTypes = result.allowedFileTypes,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        importSummaryMessage = "1 prompt imported successfully."
                    }
                    is ImportJsonResult.BatchPromptImport -> {
                        var importedCount = 0
                        for (p in result.prompts) {
                            val existing = container.database.promptDao().getPromptByTitle(p.title)
                            val title = if (existing != null) "${p.title} (imported)" else p.title
                            container.database.promptDao().insertPrompt(
                                Prompt(
                                    title = title,
                                    content = p.content,
                                    icon = p.icon,
                                    allowedFileTypes = p.allowedFileTypes,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            importedCount++
                        }
                        importSummaryMessage = "$importedCount imported, ${result.skippedCount} skipped."
                    }
                    is ImportJsonResult.Error -> {
                        importSummaryMessage = "Import failed: ${result.message}"
                    }
                    else -> {
                        importSummaryMessage = "File format not supported for prompts."
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ==========================================
            // 1. SECTION "APPEARANCE"
            // ==========================================
            SectionContainer(title = "Appearance") {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val preview = getThemePreviewColors(mode, isDark = isDark)
                        ThemeColorPreviewCard(
                            mode = mode,
                            isSelected = themeMode == mode,
                            preview = preview,
                            onClick = {
                                coroutineScope.launch {
                                    container.userPreferencesRepository.setThemeMode(mode)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Font size - Small / Medium / Large / XL
                Text(
                    text = "Font size",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppFontSize.entries.forEach { size ->
                        FilterChip(
                            selected = fontSize == size,
                            onClick = {
                                coroutineScope.launch {
                                    container.userPreferencesRepository.setFontSize(size)
                                }
                            },
                            label = { Text(size.displayName) },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("font_size_chip_${size.name.lowercase()}")
                        )
                    }
                }
            }

            // ==========================================
            // 2. SECTION "PROMPT & EXECUTION"
            // ==========================================
            SectionContainer(title = "Prompt & Execution") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Auto-execute on file selection",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Automatically apply prompt when starting chat with an attached file (e.g. PDF, doc, audio, image). When disabled, opens chat with attached file ready for typing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoExecuteOnFile,
                        onCheckedChange = { checked ->
                            coroutineScope.launch {
                                container.userPreferencesRepository.setAutoExecuteOnFile(checked)
                            }
                        },
                        modifier = Modifier.testTag("auto_execute_switch")
                    )
                }
            }

            // ==========================================
            // 3. SECTION "GEMINI & MODELS"
            // ==========================================
            SectionContainer(title = "Gemini & Models") {
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        keyValidationMessage = null
                        isKeyValidSuccess = null
                    },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isVerifyingKey -> {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Verifying...", style = MaterialTheme.typography.bodySmall)
                        }
                        isKeyValidSuccess == true -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        isKeyValidSuccess == false -> {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text(keyValidationMessage ?: "Invalid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        container.encryptedKeyManager.isVerified() -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("Verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        container.encryptedKeyManager.hasApiKey() -> {
                            Text("Not verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Not verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val candidateKey = apiKeyInput.trim()
                            if (candidateKey.isBlank()) {
                                keyValidationMessage = "Please enter an API key"
                                isKeyValidSuccess = false
                                return@Button
                            }
                            isVerifyingKey = true
                            keyValidationMessage = null

                            coroutineScope.launch {
                                when (val result = container.apiKeyValidator.validateKey(candidateKey)) {
                                    is KeyValidationResult.Valid -> {
                                        container.encryptedKeyManager.saveApiKey(candidateKey)
                                        isKeyValidSuccess = true
                                        keyValidationMessage = "Verified"
                                    }
                                    is KeyValidationResult.InvalidKey -> {
                                        container.encryptedKeyManager.markInvalid()
                                        isKeyValidSuccess = false
                                        keyValidationMessage = "Invalid API key"
                                    }
                                    is KeyValidationResult.NoModelsAvailable -> {
                                        container.encryptedKeyManager.markInvalid()
                                        isKeyValidSuccess = false
                                        keyValidationMessage = "No models available for key"
                                    }
                                    is KeyValidationResult.ConnectionOrServerError -> {
                                        isKeyValidSuccess = false
                                        keyValidationMessage = result.message
                                    }
                                }
                                isVerifyingKey = false
                            }
                        },
                        enabled = !isVerifyingKey && apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("verify_and_save_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Verify and Save")
                    }

                    if (container.encryptedKeyManager.hasApiKey()) {
                        OutlinedButton(
                            onClick = {
                                container.encryptedKeyManager.clearApiKey()
                                apiKeyInput = ""
                                isKeyValidSuccess = null
                                keyValidationMessage = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("clear_key_button")
                        ) {
                            Text("Clear key")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Get a free key button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Google AI Studio")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create a free key in AI Studio, then paste it above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Available models — RADIO ROWS
                Text(
                    text = "Available models",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (cachedModels.isEmpty()) {
                    Text(
                        text = "No models available. Check key/connection and tap refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val activeModel = defaultModel.ifBlank { cachedModels.firstOrNull()?.name ?: "" }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cachedModels.forEachIndexed { index, model ->
                            val isSelected = activeModel == model.name
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            coroutineScope.launch {
                                                container.userPreferencesRepository.setDefaultModel(model.name)
                                            }
                                        },
                                        role = Role.RadioButton
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (index == 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Recommended",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        isRefreshingModels = true
                        modelRefreshFeedback = null
                        coroutineScope.launch {
                            val result = container.modelListService.fetchFromRemote()
                            when (result) {
                                is ModelFetchResult.Success -> {
                                    modelRefreshFeedback = "Updated ${result.count} models"
                                    if (defaultModel.isBlank() && result.models.isNotEmpty()) {
                                        container.userPreferencesRepository.setDefaultModel(result.models.first())
                                    }
                                }
                                is ModelFetchResult.Error -> {
                                    modelRefreshFeedback = "Failed to refresh: ${result.message}"
                                }
                            }
                            isRefreshingModels = false
                        }
                    },
                    enabled = !isRefreshingModels,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("refresh_models_button")
                ) {
                    if (isRefreshingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Refresh model list")
                }

                val lastFetched = cachedModels.firstOrNull()?.fetchedAt
                if (lastFetched != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last updated: ${dateFormatter.format(Date(lastFetched))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                modelRefreshFeedback?.let { feedback ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedback.startsWith("Updated")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // ==========================================
            // 3. SECTION "IMPORT & EXPORT"
            // ==========================================
            SectionContainer(title = "Import & Export") {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val prompts = container.database.promptDao().getAllPromptsList()
                            if (prompts.isEmpty()) {
                                Toast.makeText(context, "No prompts to export", Toast.LENGTH_SHORT).show()
                            } else {
                                val json = JsonShareHelper.createAllPromptsJson(prompts)
                                val savedPath = JsonShareHelper.saveJsonToDownloads(context, "promptchat_all_prompts.json", json)
                                Toast.makeText(
                                    context,
                                    if (savedPath != null) "Saved to $savedPath" else "Export ready",
                                    Toast.LENGTH_LONG
                                ).show()
                                JsonShareHelper.shareJsonContent(context, "promptchat_all_prompts.json", json)
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export all prompts")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        importPromptsLauncher.launch(arrayOf("application/json"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import prompts")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val json = JsonShareHelper.createAllConversationsJson(emptyList())
                            val savedPath = JsonShareHelper.saveJsonToDownloads(context, "promptchat_all_chats.json", json)
                            Toast.makeText(
                                context,
                                if (savedPath != null) "Saved to $savedPath" else "Export ready",
                                Toast.LENGTH_LONG
                            ).show()
                            JsonShareHelper.shareJsonContent(context, "promptchat_all_chats.json", json)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export all conversations")
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "You can also share individual prompts or conversations from their menus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ==========================================
            // 4. SECTION "CHAT HISTORY"
            // ==========================================
            SectionContainer(title = "Chat History") {
                Text(
                    text = "Keep conversations",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RetentionPeriod.entries.forEach { period ->
                        val isSelected = retentionPeriod == period
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        coroutineScope.launch {
                                            container.userPreferencesRepository.setRetentionPeriod(period)
                                        }
                                    },
                                    role = Role.RadioButton
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = period.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = period.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { isClearConversationsDialogOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_conversations_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear all conversations now", color = MaterialTheme.colorScheme.error)
                }
            }

            // ==========================================
            // 5. SECTION "ABOUT"
            // ==========================================
            SectionContainer(title = "About") {
                Text(
                    text = "ShareGPT 2.5.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "What's new in 2.5: Immediate auto-analysis for initial attached file upon selection according to prompt instructions, followed by seamless continuous analysis for subsequent files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://bidjadraft.github.io"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Developer website",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Privacy: All data stays on your device. No accounts, no analytics, no ads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { isOflDialogOpen = true },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("Open source licenses")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    // Confirmation dialog to clear all conversations
    if (isClearConversationsDialogOpen) {
        AlertDialog(
            onDismissRequest = { isClearConversationsDialogOpen = false },
            title = { Text("Clear all conversations?") },
            text = { Text("This will permanently delete all chat history. Your prompts will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            container.database.conversationDao().deleteAllConversations()
                            isClearConversationsDialogOpen = false
                        }
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isClearConversationsDialogOpen = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Summary dialog for import
    importSummaryMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { importSummaryMessage = null },
            title = { Text("Import summary") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importSummaryMessage = null }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Open source licenses dialog
    if (isOflDialogOpen) {
        AlertDialog(
            onDismissRequest = { isOflDialogOpen = false },
            title = { Text("Open Source Licenses") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Bundled Fonts License (SIL Open Font License 1.1):\n\n" +
                                "• Bricolage Grotesque (Copyright 2023 The Bricolage Grotesque Project Authors)\n" +
                                "• Figtree (Copyright 2022 The Figtree Project Authors)\n\n" +
                                "Permission is hereby granted, free of charge, to any person obtaining a copy of the Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell modified and unmodified copies of the Font Software, subject to the conditions of the SIL Open Font License v1.1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { isOflDialogOpen = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun SectionContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(Locale.ENGLISH),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ThemeColorPreviewCard(
    mode: AppThemeMode,
    isSelected: Boolean,
    preview: com.example.ui.theme.ThemePreviewColors,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(preview.bg)
                .clickable(onClick = onClick)
                .then(
                    if (isSelected) {
                        Modifier.background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = preview.bg,
                border = if (isSelected) BorderStroke(2.5.dp, preview.accent)
                else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(preview.accent, CircleShape)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(preview.text)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(preview.textMuted)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = mode.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}
