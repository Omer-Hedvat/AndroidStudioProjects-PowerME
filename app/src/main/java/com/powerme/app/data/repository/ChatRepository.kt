package com.powerme.app.data.repository

import com.powerme.app.data.database.ChatMessage
import com.powerme.app.data.database.ChatMessageDao
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
