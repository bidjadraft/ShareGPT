package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.db.Prompt
import com.example.ui.components.CuratedMaterialIcons
import com.example.ui.components.PromptIconDisplay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptEditorDialog(
    initialPrompt: Prompt? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, icon: String, allowedFileTypes: String, autoExecuteOnFile: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialPrompt?.title ?: "") }
    var content by remember { mutableStateOf(initialPrompt?.content ?: "") }
    var selectedIcon by remember { mutableStateOf(initialPrompt?.icon?.ifBlank { "icon:chat" } ?: "icon:chat") }
    var autoExecuteOnFile by remember { mutableStateOf(initialPrompt?.autoExecuteOnFile ?: false) }

    val initialFormat = remember {
        val raw = initialPrompt?.allowedFileTypes?.lowercase() ?: ""
        when {
            raw.contains("pdf") -> "pdf"
            raw.contains("image") -> "image"
            raw.contains("audio") -> "audio"
            raw.contains("doc") || raw.contains("text") -> "doc"
            else -> "chat"
        }
    }
    var selectedFormat by remember { mutableStateOf(initialFormat) }

    var titleError by remember { mutableStateOf<String?>(null) }
    var contentError by remember { mutableStateOf<String?>(null) }
    var typeError by remember { mutableStateOf<String?>(null) }

    var isIconPickerOpen by remember { mutableStateOf(false) }

    val isEditing = initialPrompt != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit prompt" else "Create prompt",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row with Icon Selector Button + Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tap to choose icon
                    Surface(
                        onClick = { isIconPickerOpen = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("prompt_icon_picker_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PromptIconDisplay(
                                iconString = selectedIcon,
                                size = 30.dp,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val isTitleArabic = remember(title) {
                        title.any {
                            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
                            it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
                        }
                    }

                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isTitleArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                if (it.length <= 50) {
                                    title = it
                                    if (it.isNotBlank()) titleError = null
                                }
                            },
                            label = { Text("Title") },
                            placeholder = { Text("e.g. Summarize, Translate") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = if (isTitleArabic) TextDirection.Rtl else TextDirection.Ltr
                            ),
                            isError = titleError != null,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(titleError ?: "")
                                    Text("${title.length}/50")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prompt_title_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                // Format & conversation mode (Exclusive single choice - Max 1 format)
                Column {
                    Text(
                        text = "Format & conversation mode (Max 1):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val formatOptions = listOf(
                            Triple("chat", "Chat (No files)", Icons.AutoMirrored.Filled.Chat),
                            Triple("pdf", "PDF", Icons.Default.PictureAsPdf),
                            Triple("doc", "Doc", Icons.Default.Description),
                            Triple("image", "Image", Icons.Default.Image),
                            Triple("audio", "Audio", Icons.Default.AudioFile)
                        )
                        formatOptions.forEach { (typeKey, label, icon) ->
                            val isSelected = selectedFormat == typeKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedFormat = typeKey
                                    typeError = null
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("file_type_chip_$typeKey")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (selectedFormat) {
                            "pdf" -> "Dedicated to PDF. File picker opens when tapping prompt."
                            "doc" -> "Dedicated to Word/Text docs. File picker opens when tapping prompt."
                            "image" -> "Dedicated to Images/Photos. Photo picker opens when tapping prompt."
                            "audio" -> "Dedicated to Audio files. Audio picker opens when tapping prompt."
                            else -> "Chat only. Direct entry without files."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )

                    typeError?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Auto-execute toggle when format is not chat
                if (selectedFormat != "chat") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Auto-execute on file attach",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (autoExecuteOnFile)
                                        "Runs prompt immediately when file is selected without typing."
                                    else
                                        "Opens chat with the file attached, waiting for your custom prompt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoExecuteOnFile,
                                onCheckedChange = { autoExecuteOnFile = it },
                                modifier = Modifier.testTag("prompt_auto_execute_switch")
                            )
                        }
                    }
                }

                // System prompt instructions
                val isContentArabic = remember(content) {
                    content.any {
                        it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' ||
                        it in '\u08A0'..'\u08FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
                    }
                }

                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isContentArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = {
                            if (it.length <= 5000) {
                                content = it
                                if (it.isNotBlank()) contentError = null
                            }
                        },
                        label = { Text("System prompt instructions") },
                        placeholder = { Text("Instructions passed to Gemini...") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textDirection = if (isContentArabic) TextDirection.Rtl else TextDirection.Ltr
                        ),
                        isError = contentError != null,
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(contentError ?: "")
                                Text("${content.length}/5000")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_content_input"),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanContent = content.trim()
                    var hasError = false

                    if (cleanTitle.isEmpty()) {
                        titleError = "Title is required"
                        hasError = true
                    }
                    if (cleanContent.isEmpty()) {
                        contentError = "Prompt content is required"
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(cleanTitle, cleanContent, selectedIcon, selectedFormat, autoExecuteOnFile)
                    }
                },
                modifier = Modifier.testTag("save_prompt_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_prompt_button")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(22.dp)
    )

    // Icon Picker Bottom Sheet - Material Icons Only
    if (isIconPickerOpen) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { isIconPickerOpen = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Choose prompt icon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Text(
                    text = "Select a Material icon for your prompt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(CuratedMaterialIcons) { item ->
                        val isCurrent = selectedIcon == item.id
                        Surface(
                            onClick = {
                                selectedIcon = item.id
                                isIconPickerOpen = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.height(68.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
