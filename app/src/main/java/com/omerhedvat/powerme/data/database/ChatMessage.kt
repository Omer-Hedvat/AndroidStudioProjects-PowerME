package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,
    val isUser: Boolean,
    val timestamp: Long,
    val actionData: String? = null // JSON-serialized ActionBlock for audit trail
)
