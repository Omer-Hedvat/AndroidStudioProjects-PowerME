package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    /** Returns the timestamp of the most recent message, or null if no messages exist. */
    @Query("SELECT MAX(timestamp) FROM chat_messages")
    suspend fun getLastMessageTimestamp(): Long?

    /** Returns all messages as a one-shot list (not a Flow) for summarization. */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesOnce(): List<ChatMessage>
}
