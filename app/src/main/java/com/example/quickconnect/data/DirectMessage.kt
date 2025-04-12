package com.example.quickconnect.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_messages")
data class DirectMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderId: String,
    val receiverId: String,
    val timestamp: Long,
    val content: String,
    val isRead: Boolean
) {
}
