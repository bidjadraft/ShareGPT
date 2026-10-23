package com.example.data.gemini

import com.example.data.db.Message
import com.example.data.file.AttachmentProcessResult
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GeminiChatService {

    fun streamChatResponse(
        apiKey: String,
        modelName: String,
        promptSystemInstruction: String,
        history: List<Message>,
        currentUserMessage: String,
        geminiFileUri: String? = null,
        geminiFileMimeType: String? = null,
        binaryAttachment: AttachmentProcessResult.BinaryAttachment? = null
    ): Flow<String> = flow {
        val generativeModel = try {
            GenerativeModel(
                modelName = modelName.trim(),
                apiKey = apiKey.trim(),
                systemInstruction = if (promptSystemInstruction.isNotBlank()) {
                    content { text(promptSystemInstruction) }
                } else null
            )
        } catch (_: Exception) {
            GenerativeModel(
                modelName = modelName.trim(),
                apiKey = apiKey.trim()
            )
        }

        // Build continuous chat history
        // If a file was attached, inject the file reference (URI or blob) ONCE into
        // the initial user turn in history, so subsequent questions do not re-upload the file.
        val chatHistory = mutableListOf<Content>()
        var fileInjectedInHistory = false

        for (msg in history) {
            val role = if (msg.role == "model") "model" else "user"
            if (!fileInjectedInHistory && role == "user") {
                chatHistory.add(
                    content(role = role) {
                        if (!geminiFileUri.isNullOrBlank() && !geminiFileMimeType.isNullOrBlank()) {
                            fileData(geminiFileUri, geminiFileMimeType)
                            fileInjectedInHistory = true
                        } else if (binaryAttachment != null) {
                            blob(binaryAttachment.mimeType, binaryAttachment.bytes)
                            fileInjectedInHistory = true
                        }
                        text(msg.content)
                    }
                )
            } else {
                chatHistory.add(
                    content(role = role) {
                        text(msg.content)
                    }
                )
            }
        }

        val chat = generativeModel.startChat(history = chatHistory)

        // Only attach file in sendContent on Turn 1 (when not already injected in history)
        val sendContent: Content = if (!fileInjectedInHistory && (!geminiFileUri.isNullOrBlank() || binaryAttachment != null)) {
            content(role = "user") {
                if (!geminiFileUri.isNullOrBlank() && !geminiFileMimeType.isNullOrBlank()) {
                    fileData(geminiFileUri, geminiFileMimeType)
                } else if (binaryAttachment != null) {
                    blob(binaryAttachment.mimeType, binaryAttachment.bytes)
                }
                if (currentUserMessage.isNotBlank()) {
                    text(currentUserMessage)
                }
            }
        } else {
            content(role = "user") {
                text(currentUserMessage)
            }
        }

        val stream = chat.sendMessageStream(sendContent)
        stream.collect { response ->
            val chunk = response.text
            if (!chunk.isNullOrEmpty()) {
                emit(chunk)
            }
        }
    }
}
