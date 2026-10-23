package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ConversationWithPrompt(
    val id: Long,
    val promptId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
    val promptTitle: String,
    val promptContent: String,
    val promptIcon: String = "icon:chat",
    val firstMessageSnippet: String? = null
)

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY updatedAt DESC")
    fun getAllPrompts(): Flow<List<Prompt>>

    @Query("SELECT * FROM prompts ORDER BY updatedAt DESC")
    suspend fun getAllPromptsList(): List<Prompt>

    @Query("SELECT * FROM prompts WHERE id = :id LIMIT 1")
    suspend fun getPromptById(id: Long): Prompt?

    @Query("SELECT * FROM prompts WHERE title = :title LIMIT 1")
    suspend fun getPromptByTitle(title: String): Prompt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: Prompt): Long

    @Update
    suspend fun updatePrompt(prompt: Prompt)

    @Delete
    suspend fun deletePrompt(prompt: Prompt)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun deletePromptById(id: Long)
}

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT 
            c.id, c.promptId, c.title, c.createdAt, c.updatedAt, c.lastMessageAt,
            p.title AS promptTitle, p.content AS promptContent, p.icon AS promptIcon,
            (SELECT m.content FROM messages m WHERE m.conversationId = c.id ORDER BY m.createdAt ASC LIMIT 1) AS firstMessageSnippet
        FROM conversations c
        INNER JOIN prompts p ON c.promptId = p.id
        ORDER BY c.lastMessageAt DESC
        """
    )
    fun getAllConversationsWithPrompt(): Flow<List<ConversationWithPrompt>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationFlow(id: Long): Flow<Conversation?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Query("UPDATE conversations SET title = :newTitle, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET lastMessageAt = :lastMessageAt, updatedAt = :lastMessageAt WHERE id = :id")
    suspend fun updateLastMessageAt(id: Long, lastMessageAt: Long)

    @Query(
        """
        UPDATE conversations 
        SET persistentAttachmentUri = :uri, 
            persistentAttachmentName = :name, 
            persistentAttachmentMimeType = :mimeType, 
            extractedContextText = :extractedText,
            geminiFileUri = :geminiFileUri,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updatePersistentAttachment(
        id: Long,
        uri: String?,
        name: String?,
        mimeType: String?,
        extractedText: String?,
        geminiFileUri: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE conversations 
        SET persistentAttachmentUri = NULL, 
            persistentAttachmentName = NULL, 
            persistentAttachmentMimeType = NULL, 
            extractedContextText = NULL,
            geminiFileUri = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun clearPersistentAttachment(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("DELETE FROM conversations WHERE lastMessageAt < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long): Int

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesListForConversation(conversationId: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)
}

@Dao
interface CachedModelDao {
    @Query("SELECT * FROM cached_models ORDER BY fetchedAt DESC")
    fun getAllCachedModels(): Flow<List<CachedModel>>

    @Query("SELECT * FROM cached_models ORDER BY fetchedAt DESC")
    suspend fun getCachedModelsList(): List<CachedModel>

    @Query("SELECT MAX(fetchedAt) FROM cached_models")
    suspend fun getLastFetchedTime(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<CachedModel>)

    @Query("DELETE FROM cached_models")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceModels(models: List<CachedModel>) {
        clearAll()
        insertAll(models)
    }
}
