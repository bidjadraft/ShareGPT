package com.example.data.file

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.db.Message
import com.example.data.db.Prompt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

sealed class ImportJsonResult {
    data class PromptImport(
        val title: String,
        val content: String,
        val icon: String = "icon:chat",
        val allowedFileTypes: String = "chat,doc,pdf,image,audio",
        val autoExecuteOnFile: Boolean = false
    ) : ImportJsonResult()

    data class ConversationImport(
        val promptTitle: String,
        val promptContent: String,
        val promptIcon: String = "icon:chat",
        val conversationTitle: String,
        val messages: List<MessageDraft>
    ) : ImportJsonResult()

    data class BatchPromptImport(
        val prompts: List<PromptImport>,
        val skippedCount: Int
    ) : ImportJsonResult()

    data class BatchConversationImport(
        val conversations: List<ConversationImport>,
        val skippedCount: Int
    ) : ImportJsonResult()

    data class Error(val message: String) : ImportJsonResult()
}

data class MessageDraft(
    val role: String,
    val content: String,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ConversationExportData(
    val promptTitle: String,
    val promptContent: String,
    val promptIcon: String,
    val conversationTitle: String,
    val messages: List<Message>
)

object JsonShareHelper {

    fun createPromptJson(prompt: Prompt): String {
        val json = JSONObject()
        json.put("type", "promptchat_prompt")
        json.put("version", 2)
        json.put("title", prompt.title)
        json.put("content", prompt.content)
        json.put("icon", prompt.icon.ifBlank { "icon:chat" })
        json.put("autoExecuteOnFile", prompt.autoExecuteOnFile)

        val allowedArray = JSONArray()
        val types = prompt.allowedFileTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (t in types) {
            allowedArray.put(t)
        }
        json.put("allowedFileTypes", allowedArray)
        return json.toString(2)
    }

    fun createAllPromptsJson(prompts: List<Prompt>): String {
        val root = JSONObject()
        root.put("type", "promptchat_all_prompts")
        root.put("version", 2)

        val array = JSONArray()
        for (prompt in prompts) {
            val pJson = JSONObject()
            pJson.put("type", "promptchat_prompt")
            pJson.put("version", 2)
            pJson.put("title", prompt.title)
            pJson.put("content", prompt.content)
            pJson.put("icon", prompt.icon.ifBlank { "icon:chat" })
            pJson.put("autoExecuteOnFile", prompt.autoExecuteOnFile)
            val allowedArray = JSONArray()
            val types = prompt.allowedFileTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            for (t in types) {
                allowedArray.put(t)
            }
            pJson.put("allowedFileTypes", allowedArray)
            array.put(pJson)
        }
        root.put("prompts", array)
        return root.toString(2)
    }

    fun createConversationJson(
        promptTitle: String,
        promptContent: String,
        promptIcon: String,
        conversationTitle: String,
        messages: List<Message>
    ): String {
        val json = JSONObject()
        json.put("type", "promptchat_conversation")
        json.put("version", 2)
        json.put("promptTitle", promptTitle)
        json.put("promptContent", promptContent)
        json.put("promptIcon", promptIcon)
        json.put("conversationTitle", conversationTitle)

        val messagesArray = JSONArray()
        for (msg in messages) {
            val m = JSONObject()
            m.put("role", msg.role)
            m.put("content", msg.content)
            if (msg.attachmentName != null) m.put("attachmentName", msg.attachmentName)
            if (msg.attachmentMimeType != null) m.put("attachmentMimeType", msg.attachmentMimeType)
            m.put("createdAt", msg.createdAt)
            messagesArray.put(m)
        }
        json.put("messages", messagesArray)
        return json.toString(2)
    }

    fun createAllConversationsJson(conversations: List<ConversationExportData>): String {
        val root = JSONObject()
        root.put("type", "promptchat_all_conversations")
        root.put("version", 2)

        val convArray = JSONArray()
        for (conv in conversations) {
            val json = JSONObject()
            json.put("type", "promptchat_conversation")
            json.put("version", 2)
            json.put("promptTitle", conv.promptTitle)
            json.put("promptContent", conv.promptContent)
            json.put("promptIcon", conv.promptIcon)
            json.put("conversationTitle", conv.conversationTitle)

            val messagesArray = JSONArray()
            for (msg in conv.messages) {
                val m = JSONObject()
                m.put("role", msg.role)
                m.put("content", msg.content)
                if (msg.attachmentName != null) m.put("attachmentName", msg.attachmentName)
                if (msg.attachmentMimeType != null) m.put("attachmentMimeType", msg.attachmentMimeType)
                m.put("createdAt", msg.createdAt)
                messagesArray.put(m)
            }
            json.put("messages", messagesArray)
            convArray.put(json)
        }
        root.put("conversations", convArray)
        return root.toString(2)
    }

    /**
     * Saves JSON file to Downloads directory via MediaStore on Android 10+ (API 29+) or legacy storage.
     */
    fun saveJsonToDownloads(context: Context, filename: String, jsonContent: String): String? {
        return try {
            val bytes = jsonContent.toByteArray(StandardCharsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    "Downloads/$filename"
                } else null
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloads.mkdirs()
                val targetFile = File(downloads, filename)
                FileOutputStream(targetFile).use { it.write(bytes) }
                targetFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareJsonContent(context: Context, fileName: String, jsonContent: String) {
        try {
            val cacheDir = context.cacheDir
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(cacheDir, safeFileName)
            FileOutputStream(targetFile).use { it.write(jsonContent.toByteArray(StandardCharsets.UTF_8)) }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share JSON file"))
        } catch (_: Exception) {
        }
    }

    fun parseJsonFromUri(context: Context, uri: Uri): ImportJsonResult {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            } ?: return ImportJsonResult.Error("Could not read file from URI.")

            parseJsonString(text)
        } catch (e: Exception) {
            ImportJsonResult.Error("Failed to parse file: ${e.localizedMessage ?: "Invalid JSON"}")
        }
    }

    fun parseJsonString(text: String): ImportJsonResult {
        return try {
            val trimmed = text.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val type = json.optString("type")
                val version = json.optInt("version", 1)

                when (type) {
                    "promptchat_prompt" -> parseSinglePrompt(json, version)
                    "promptchat_all_prompts" -> parsePromptBatch(json)
                    "promptchat_conversation" -> parseSingleConversation(json)
                    "promptchat_all_conversations" -> parseConversationBatch(json)
                    else -> {
                        // Check if it's a prompt or conversation without type
                        if (json.has("title") && json.has("content")) {
                            parseSinglePrompt(json, version)
                        } else if (json.has("conversationTitle") || json.has("messages")) {
                            parseSingleConversation(json)
                        } else {
                            ImportJsonResult.Error("Unknown PromptChat file type: $type")
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                // Array of prompts
                val array = JSONArray(trimmed)
                val list = mutableListOf<ImportJsonResult.PromptImport>()
                var skipped = 0
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        val title = obj.optString("title")
                        val content = obj.optString("content")
                        if (title.isNotBlank() && content.isNotBlank()) {
                            val icon = obj.optString("icon", "icon:chat")
                            val allowed = parseAllowedTypes(obj)
                            val autoExec = obj.optBoolean("autoExecuteOnFile", false)
                            list.add(ImportJsonResult.PromptImport(title, content, icon, allowed, autoExec))
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }
                }
                if (list.isEmpty()) {
                    ImportJsonResult.Error("No valid prompts found in JSON file.")
                } else {
                    ImportJsonResult.BatchPromptImport(list, skipped)
                }
            } else {
                ImportJsonResult.Error("Not a valid PromptChat JSON document.")
            }
        } catch (e: Exception) {
            ImportJsonResult.Error("Not a valid PromptChat file: ${e.message}")
        }
    }

    private fun parseAllowedTypes(json: JSONObject): String {
        val array = json.optJSONArray("allowedFileTypes")
        return if (array != null && array.length() > 0) {
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val t = array.optString(i).trim().lowercase()
                if (t.isNotBlank()) {
                    list.add(if (t == "text") "doc" else t)
                }
            }
            if (!list.contains("chat")) list.add(0, "chat")
            list.joinToString(",")
        } else {
            val raw = json.optString("allowedFileTypes", "chat,doc,pdf,image,audio").ifBlank { "chat,doc,pdf,image,audio" }
            val list = raw.split(",").map { it.trim().lowercase() }.map { if (it == "text") "doc" else it }.toMutableList()
            if (!list.contains("chat")) list.add(0, "chat")
            list.joinToString(",")
        }
    }

    private fun parseSinglePrompt(json: JSONObject, version: Int): ImportJsonResult {
        val title = json.optString("title")
        val content = json.optString("content")
        if (title.isBlank() || content.isBlank()) {
            return ImportJsonResult.Error("This file is not a valid PromptChat prompt.")
        }
        val icon = if (version >= 2 && json.has("icon")) json.optString("icon", "icon:chat") else "icon:chat"
        val allowed = if (version >= 2 && json.has("allowedFileTypes")) {
            parseAllowedTypes(json)
        } else {
            "chat,doc,pdf,image,audio"
        }
        val autoExec = json.optBoolean("autoExecuteOnFile", false)
        return ImportJsonResult.PromptImport(title, content, icon, allowed, autoExec)
    }

    private fun parsePromptBatch(json: JSONObject): ImportJsonResult {
        val array = json.optJSONArray("prompts") ?: return ImportJsonResult.Error("No prompts array found.")
        val list = mutableListOf<ImportJsonResult.PromptImport>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item != null) {
                val title = item.optString("title")
                val content = item.optString("content")
                if (title.isNotBlank() && content.isNotBlank()) {
                    val icon = item.optString("icon", "icon:chat")
                    val allowed = parseAllowedTypes(item)
                    val autoExec = item.optBoolean("autoExecuteOnFile", false)
                    list.add(ImportJsonResult.PromptImport(title, content, icon, allowed, autoExec))
                } else {
                    skipped++
                }
            } else {
                skipped++
            }
        }
        return ImportJsonResult.BatchPromptImport(list, skipped)
    }

    private fun parseSingleConversation(json: JSONObject): ImportJsonResult {
        val promptTitle = json.optString("promptTitle")
        val promptContent = json.optString("promptContent")
        val promptIcon = json.optString("promptIcon", "icon:chat")
        val conversationTitle = json.optString("conversationTitle")
        val messagesArray = json.optJSONArray("messages")

        if (promptTitle.isBlank() || messagesArray == null) {
            return ImportJsonResult.Error("This file is not a valid PromptChat conversation.")
        }
        val draftMessages = mutableListOf<MessageDraft>()
        for (i in 0 until messagesArray.length()) {
            val item = messagesArray.getJSONObject(i)
            draftMessages.add(
                MessageDraft(
                    role = item.optString("role", "user"),
                    content = item.optString("content", ""),
                    attachmentName = if (item.has("attachmentName")) item.optString("attachmentName") else null,
                    attachmentMimeType = if (item.has("attachmentMimeType")) item.optString("attachmentMimeType") else null,
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return ImportJsonResult.ConversationImport(
            promptTitle = promptTitle,
            promptContent = promptContent,
            promptIcon = promptIcon,
            conversationTitle = conversationTitle.ifBlank { promptTitle },
            messages = draftMessages
        )
    }

    private fun parseConversationBatch(json: JSONObject): ImportJsonResult {
        val array = json.optJSONArray("conversations") ?: return ImportJsonResult.Error("No conversations found.")
        val list = mutableListOf<ImportJsonResult.ConversationImport>()
        var skipped = 0
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i)
            if (item != null) {
                val single = parseSingleConversation(item)
                if (single is ImportJsonResult.ConversationImport) {
                    list.add(single)
                } else {
                    skipped++
                }
            } else {
                skipped++
            }
        }
        return ImportJsonResult.BatchConversationImport(list, skipped)
    }
}
