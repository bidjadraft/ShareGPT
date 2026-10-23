package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "prompts")
data class Prompt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val icon: String = "icon:chat",
    val allowedFileTypes: String = "chat,doc,pdf,image,audio",
    val autoExecuteOnFile: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = Prompt::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("promptId")]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val promptId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis(),
    val persistentAttachmentUri: String? = null,
    val persistentAttachmentName: String? = null,
    val persistentAttachmentMimeType: String? = null,
    val extractedContextText: String? = null,
    val geminiFileUri: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" or "model"
    val content: String,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_models")
data class CachedModel(
    @PrimaryKey
    val name: String,
    val fetchedAt: Long = System.currentTimeMillis()
)
