package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.data.database.ChatMessage
import com.omerhedvat.powerme.data.database.ChatMessageDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages()
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return chatMessageDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        chatMessageDao.deleteAllMessages()
    }

    suspend fun getLastMessageTimestamp(): Long? = chatMessageDao.getLastMessageTimestamp()

    suspend fun getAllMessagesOnce(): List<ChatMessage> = chatMessageDao.getAllMessagesOnce()
}
