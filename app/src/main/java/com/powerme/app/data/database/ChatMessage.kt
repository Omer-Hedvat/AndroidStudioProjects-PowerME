package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,
    val isUser: Boolean,
    val timestamp: Long,
    val actionData: String? = null, // JSON-serialized ActionBlock for audit trail
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString() // Stable cross-device identity for Firestore (v35)
)
